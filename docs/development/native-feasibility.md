# Native feasibility record

Task 2 checkpoint, 2026-10-31. This is a developer probe, not an attendance implementation or release acceptance. No raw coordinates, tokens, profile or attendance records are saved by these probes. Native fixes stay in memory; the Android background probe stores only an opaque work ID and accuracy/age/delay text in app-private debug preferences.

## Automated evidence

The adapters own one native request, bound it to at most 30 seconds, cancel native work on success/timeout/coroutine cancellation, and ignore late callbacks. Native timestamp conversion preserves Android monotonic freshness and iOS sub-millisecond capture time. Unknown freshness remains unknown for the attendance rules to reject. Permission bridges keep foreground/background requests separate; iOS never requests background/Always access.

30 tests passed: 11 shared Android, 13 shared iOS, and 6 Android activity permission tests. Tests cover timeout, cancellation and late callbacks, denial without retry, malformed samples, coordinate-safe string output, clocks, capabilities, separate background consent, and activity rebinding. Test harnesses inject the callback source; this does not prove physical GPS behavior.

Commands (JDK 21, shared Gradle cache, strict checksum verification and locks):

```sh
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest \
  :shared:iosSimulatorArm64Test --device=779285A4-F1C5-4274-9899-B38357C3B824 \
  :androidApp:assembleDebug --continue --console=plain
xcodebuild -project iosApp/Daymark.xcodeproj -scheme Daymark \
  -destination 'platform=iOS Simulator,id=779285A4-F1C5-4274-9899-B38357C3B824' \
  CODE_SIGNING_ALLOWED=NO build
xcodebuild -project iosApp/Daymark.xcodeproj -scheme DaymarkProbeWidget \
  -configuration Debug \
  -destination 'platform=iOS Simulator,id=779285A4-F1C5-4274-9899-B38357C3B824' \
  CODE_SIGNING_ALLOWED=NO build
```

Both Swift host and widget compile. The simulator launched without a debugger and displays the location probe and reminder button. A `simctl openurl daymark://native-probe` invocation succeeded; observing the URL-specific message and an actual widget tap remain open. Neither a successful compile nor a URL invocation proves reminder delivery or a widget tap.

## Physical Android observations

Device: Huawei JKM-LX1, Android 9/API 28, Google location services available. Debug application `dev.daymark.app`.

| Scenario | Observed result | Verdict |
| --- | --- | --- |
| Foreground check before permission | `PERMISSION_DENIED`; no attendance saved | Expected denial |
| User-initiated permission request | Native consent dialog; capability state becomes foreground/precise/background true, services enabled | Expected API 28 behavior; this OS has no separate background grant |
| One foreground check after consent | Accuracy 17.103 m, monotonic age 287.088303 ms | Meets sample age/accuracy limits; no place/radius or attendance outcome exists yet |
| One delayed background job; app backgrounded and screen put to sleep | One durable worker claim; minimum delay 60 s; observed scheduling delay 460 ms; accuracy 17.103 m, age 96.532028913 s | **Stale: cannot confirm attendance** |

The background worker makes at most one gateway invocation for its claimed work ID and returns success even when location fails, so WorkManager does not retry GPS. The claim records an attempted run, not a successful fix. There is no continuous location, foreground service, exact alarm or location retry. The screen was put to sleep; secure keyguard state was not separately verified. This was not a forced-Doze or battery-saver test. API 28 cannot validate the newer separate background permission flow.

Native `getCurrentLocation` was configured with zero cached age, yet the physical background result was stale. The caller must still validate the returned native age. Do not treat receipt time or requested cache policy as proof of freshness. Retain the approved best-effort mode and manual fallback; this observation does not authorize additional GPS attempts.

## iOS probe packaging

`DaymarkProbeWidget` is a separate development scheme and is never an embedded dependency of the shipping `Daymark` target. For simulator-only probe installation, build both Debug schemes into the same derived-data directory, copy `DaymarkProbeWidget.appex` into the built debug `Daymark.app/PlugIns/`, then install the resulting test bundle using `simctl`. Do not archive/distribute that modified test bundle. Task 12 replaces this probe with the actual widget target and signing setup.

The Swift reminder probe asks permission only after its button is pressed and schedules one non-repeating 60-second local notification with a stable identifier. Delivery requests no location. The widget has a static timeline with no polling/network/location and opens `daymark://native-probe`; checking location still requires an explicit button press in the foreground app. iOS 16 has a widget background fallback; iOS 17+ uses the widget container background API.

## Open gates

- Physical Android secure-lock, denied background access on API 29+, battery saver and Doze observations. The Huawei test covers the non-Google-manufacturer requirement, not every power restriction.
- Physical iPhone foreground precise/denied/unavailable GPS, one actual reminder delivery, and an actual home-screen widget tap into the foreground check without debugger dependence. Only an iOS simulator is currently connected. Signing credentials are not provisioned by this task.
- Simulator reminder delivery and actual widget-gallery/tap observation. Computer-use could not bind the Simulator app; CLI build/launch evidence is retained without claiming these interactions passed.
- Xcode 27.1 is empirically usable locally; the official Kotlin support matrix qualification and older-OS/physical-device acceptance remain open as recorded in the toolchain document.

Task 2 is **partially verified**, not complete. The user explicitly chose continued local development with physical iPhone testing retained as an open gate. Missing external hardware gates remain explicit while independent local work continues under the plan's prerequisite rule. No product-mode change is made from this single-device result.

Primary API references: [Google current-location request](https://developers.google.com/android/reference/com/google/android/gms/location/CurrentLocationRequest), [Apple local notifications](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app), and [WidgetKit extensions and URL links](https://developer.apple.com/documentation/widgetkit/creating-a-widget-extension).

## Production Android scheduling checkpoint

The application uses `AndroidAttendanceScheduler`, `AttendanceWorker`, and shared `RoomPlanReader`/`nextAttendance`. It schedules ordinary one-time work with a tracker/date unique name, no network requirement, and zero delay when an unclaimed window is still open. Pending changes replace pending work; a worker already returning can append its corrected window without releasing the durable claim. Startup, foreground resume, boot, clock/zone, and package-replacement recovery recompute the next occurrence. Foreground, precise, and background permission must all be present; revocation disables automation while preserving records. Worker callbacks never request permission. Sensor denial/timeout/ineligibility finishes successfully; local-storage failure may retry, with the durable claim preventing a second GPS sample.

A shared planning pass reads one Room snapshot, scans at most 3660 calendar dates, and does not materialize future attendance rows. Frozen history and future-effective versions use the same projection as balance reservations. Device travel cannot change the stored attendance zone. The old native-only background probe worker is retired; its debug button now refreshes the production schedule.

Observed on Huawei JKM-LX1, Android 9/API28: four WorkManager testing-library instrumentation tests pass against temporary private databases. They cover a consumed attempt across new worker instances, late dispatch with no additional sample and derived absence, denied capability retaining records, duplicate recovery yielding one pending date without a network constraint, and the installed manifest's absence of exact-alarm/foreground-service capabilities. Shared/host tests cover inclusive grace, setup after arrival, all excluded states, native denial races, timeout without retry, and correct tracker identity for disablement. The merged manifest removes WorkManager's unused generic foreground service/permission. No foreground location service is declared.

These tests control dispatch and inject location results: they do **not** establish real wake delay or secure-lock/Doze/battery-saver delivery. The earlier native background fix was stale and correctly rejected. Physical Android 10+ separate background revocation, actual reboot recovery, secure-lock/Doze delivery, and device-zone-change journeys remain open acceptance checks. Do not label these as passing from the deterministic worker tests. Physical iPhone testing remains explicitly deferred by the user; local implementation continues.

## Production iOS reminder checkpoint

The shared planner emits at most 30 calendar dates beginning with today in the attendance zone, independent of Android automation being enabled. It excludes leave, completed/corrected dates, arrivals before tracking begins, and past trigger instants. Weekday overrides are resolved with the same zone rules as attendance. Language changes preserve IDs and planned instants. An explicit JSON owner envelope identifies which tracker's requests to replace even when its horizon is empty.

`IosReminderAdapter` owns only identifiers prefixed with that tracker. It validates the envelope before touching the queue, serializes native replace/cancel operations, and fences older operations across awaited notification-center calls. Native scheduling failure reports false separately from attendance saving. New reminders use nonrepeating `UNCalendarNotificationTrigger` requests with an explicit Gregorian calendar and UTC components derived from the planned instant. App-owned titles/bodies come from the shared localized copy (wired with onboarding); no grace-deadline reminder is added. The old 60-second development reminder probe is retired and its one owned pending identifier is removed.

Notification taps enqueue a current foreground quick-check intent, with no historical date or attendance evidence taken from the notification. The delegate performs no location acquisition or database write. The shared production navigation consumes this entry boundary in the onboarding/Today tasks. No Always location usage description or background location mode was added.

Observed local evidence: eight Swift XCTest tests pass on iOS 27 simulator (five new adapter tests plus three existing OAuth tests). The injected native-center tests check request ownership, EN→AR replacement, UTC/Gregorian nonrepeating triggers, denied permission without scheduling, invalid input without queue deletion, and cancellation while a native pending-queue read is suspended. Shared tests check the inclusive 30-date horizon, Thursday override, leave suppression, no past-arrival duplicate, identity/instant preservation across localized copy, empty-owner envelope, and a queued foreground intent.

The user explicitly chose continued local development with physical iPhone testing left open. Actual delivery on a signed iPhone, termination/denial presentation behavior, physical foreground GPS, and native widget tapping remain acceptance gates. A simulated pending queue is not physical delivery evidence. The finite horizon must remain visible in Settings; returning to the app refreshes it through the later lifecycle coordinator.

Primary API reference: [Apple calendar notification triggers](https://developer.apple.com/documentation/usernotifications/uncalendarnotificationtrigger), [Apple local notification scheduling and owned cancellation](https://developer.apple.com/library/archive/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/SchedulingandHandlingLocalNotifications.html).
