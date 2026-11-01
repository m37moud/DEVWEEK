# Attendance MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved personal attendance MVP for Android and iOS, with durable local records, English/Arabic support, native check-in/widget flows, and optional Google Sheets reporting.

**Architecture:** One shared Compose Multiplatform library contains feature-oriented UI, pure attendance/calendar rules, Room persistence, and the report publisher. Thin Android and iOS hosts own permissions, scheduling, native Google authorization, and widgets. Room is authoritative; Sheets is an outgoing, eventually reconciled report.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, Material 3, Navigation 3, Room 3/KSP/bundled SQLite, coroutines, kotlinx.serialization/datetime, Ktor, WorkManager/Glance, and Core Location/UserNotifications/WidgetKit. Exact initial pins and compatibility gates appear below.

**Spec:** Approved [promoted design v2](../reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md), incorporating the complete immutable [v1 specification](../reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md), SHA-256 `99843eeb6012ddb80a51c114daf5cd82054d99735a768bf278cc98a9d4e20e53`. Read both before implementation. The [senior review](../reviews/2026-10-27-attendance-mvp/03-senior-review.md) explains the v2 amendments.

**Status:** Approved for Native execution by the user on 31 October 2026. Implementation is in progress in the isolated `feature/attendance-mvp` worktree. Completion requires the recorded native/test gates and one independent whole-branch review; unchecked steps are unfinished.

## Global Constraints

- Each installation has one active tracker, one registered place, and one weekly schedule; one phone at a time.
- Android 8/API 26 and iOS 16 or newer; current stable target/compile SDKs at implementation time. Initial Android compile/target SDK is 37; use arm64 iOS targets.
- Android attempts one automatic location check per scheduled day, subject to permissions and system restrictions.
- iOS sends an arrival-time reminder; its home-screen widget opens a foreground, location-verified check.
- A successful confirmation within the configured grace period is Present; confirmation afterward is Late. The default grace period is five minutes.
- The deadline is inclusive. At 09:05:00 a valid confirmation is Present; after that instant it is Late.
- Default 100 m radius, adjustable from 100–500 m; require a fresh fix with reported accuracy of 50 m or better.
- At most 30 seconds for one location request; sample age at confirmation no more than 30 seconds; discard raw check coordinates after computing the verdict.
- Show the upcoming check and disable the normal check action until scheduled arrival; historical manual corrections remain available.
- An Android automatic attempt is only eligible during the arrival-to-deadline window; automatic checks never create Late records.
- A claimed automatic attempt remains consumed after cancellation or process death. Network retries never re-run GPS.
- No continuous tracking, geofencing, periodic GPS retry, exact-alarm special access, or persistent location service.
- Ordinary schedule/place/category changes take effect on the next attendance-zone calendar day. Explicit corrections/time-off overlays may change historical outcomes without rewriting their original expectations.
- Users may add, edit, or remove holidays and vacations for past, current, and future tracked dates.
- Work has one whole-day vacation balance; holidays and weekly days off do not consume it. Future vacations reserve balance immediately. No automatic annual reset/accrual/carryover or automatic conversion of absence into vacation.
- A time-off overlay preserves underlying attendance; removing it restores that outcome or derives Absent. Never obtain GPS for a past date.
- Support System, English, and العربية; English fallback; Gregorian attendance dates; native/widget/notification localization and AR/RTL.
- Keep the approved light/dark visual direction. Appearance offers System/Light/Dark. Use text/icons as well as status colors.
- No login is required to track attendance. Authorize only `https://www.googleapis.com/auth/drive.file` for the optional report connection.
- User text/canonical report strings use `RAW`; app-owned formulas are separate. Never put tokens, precise check positions, or personal notes in logs.
- A local transaction commits each authoritative change and dirty report revisions together. One `(trackerId, attendanceLocalDate)` record exists per date.
- iOS uploads automatically at foreground execution opportunities; interrupted uploads remain pending until a later opportunity. No closed-app freshness deadline is promised.
- Journal batches before dispatch; reconcile uncertain writes and previously acknowledged rows from local truth at subsequent sync sessions. Fence callbacks by connection generation.
- Maintain iOS reminders for the next 30 calendar days; show the horizon and refresh on foreground/configuration changes.
- Use free redistributable dependencies and native frameworks. No paid service, custom backend, advertising SDK, broad Drive scope, mobile client secret, or service account.
- Preserve existing Google reports on disconnect/reinstall/local deletion. Full app backup/restore and spreadsheet import are deferred.
- No destructive database migration/downgrade fallback. No publication, account enrollment, billing enablement, or distribution action is authorized by this plan.

## Review Focus

1. **RF1 — Unusable sensor data:** NaN/infinite coordinates, negative/unknown age, future timestamps, approximate-only fixes, and cancellation must never create a successful confirmation (Tasks 2, 4, 5).
2. **RF2 — Competing actions:** duplicate taps, process death after an automatic claim, and time-off/correction commits during acquisition must preserve one record, one deduction, and the user's final correction (Tasks 5, 6, 10).
3. **RF3 — Calendar/environment changes:** midnight, travel, DST gaps/overlaps, and an app-language change must not move an existing record to another date or schedule a second automatic request (Tasks 4, 7, 8, 11, 12).
4. **RF4 — Text and clearing:** Arabic numerals/bidirectional names, long notes, formula-like text, and removal of optional values must remain readable locally and literal/current in the report (Tasks 9, 10, 13).
5. **RF5 — Obsolete network results:** callbacks after disconnect/account change and an older ambiguous write applied after a newer acknowledgement must not mark the new binding clean; a later session must repair the report (Tasks 3, 13, 14).

---

## Scope and execution order

This is one dependency-ordered product plan: native bridges, local rules, and reporting share the same date/revision contracts. Optional Google reporting is an isolated phase, but not an independent application. Tasks 1–3 establish feasibility; 4–6 establish authoritative rules/storage; 7–12 complete the local cross-platform app; 13–14 complete reporting/lifecycle; 15 verifies a release candidate. A failed external prerequisite blocks only its dependent work; record the gate as incomplete and continue independent local tasks, never label a fake/simulator result as physical-device evidence.

Use working title `Daymark`, Kotlin package `dev.daymark`, provisional Android/bundle ID `dev.daymark.app`, and provisional App Group `group.dev.daymark.app`. These are local-development identifiers, not a final name/ownership claim. Replace them before OAuth registration/signing/publication if the user supplies final identifiers; record all replacements in the provisioning guide.

### Initial dependency and machine baseline

Published versions were read from Google Maven/Maven Central metadata and official release pages on 27 October 2026. **These are initial exact candidates, not a compiled compatibility claim.** Task 1 must resolve and compile them before feature work; a hard incompatibility stops that gate and gets a documented stable-version correction.

| Dependency/tool | Initial pin |
| --- | --- |
| Kotlin / Compose compiler plugin | `2.4.21` / `2.4.21` |
| CMP Gradle plugin | `1.12.2` |
| `org.jetbrains.compose.material3:material3` | `1.9.0` |
| `org.jetbrains.androidx.navigation3:navigation3-ui` | `1.2.0` |
| JetBrains lifecycle Compose/ViewModel family | `2.11.0` |
| kotlinx-coroutines core/test | `1.11.0` |
| kotlinx-serialization-json | `1.12.0` |
| kotlinx-datetime | `0.8.0` |
| Ktor client core/Android/Darwin/content-negotiation/serialization-json/mock | `3.6.0` |
| KSP plugin | `2.3.12` |
| `androidx.room3` runtime/compiler/Gradle plugin | `3.0.3` |
| `androidx.sqlite:sqlite-bundled` | `2.7.1` |
| WorkManager runtime/testing | `2.12.0` |
| Glance AppWidget/Material 3 | `1.2.0` |
| Play services location / authorization | `21.4.0` / `22.0.0` |
| Android Activity Compose | `1.13.0` |
| Android test runner / ext JUnit / JUnit | `1.7.0` / `1.3.0` / `4.13.2` |
| Google Sign-In iOS via Swift Package Manager | exact `10.1.0`; commit `Package.resolved` |
| AGP application + Android KMP library plugin | `9.3.1`, within Kotlin's documented AGP range |
| Gradle wrapper | `9.8.2`, including wrapper checksum verification |
| Gradle daemon/toolchain / JVM target | JDK `21` / `21` |
| Xcode selected for the compatibility gate | `26.4`; iOS deployment target `16.0` |
| Android compile/target / minimum / build tools | `37` / `26` / `36.0.0` |

Kotlin's published tested range currently ends at Gradle 9.7.0 and Xcode 26.4. Gradle 9.8.2 is chosen because the free 9.8.1+ line carries the October security fixes; do not quietly choose an affected 9.7 wrapper or a paid patch subscription. This Gradle/Kotlin combination is an explicit Task-1 compilation gate, not asserted official matrix coverage. Do not disable compatibility checks to hide a failure. See [Kotlin compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [Gradle 9.8.2](https://docs.gradle.org/9.8.2/release-notes.html), and [Gradle advisory](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8), fetched 27 October 2026.

This machine is arm64, has JDK 21 installed, and currently selects JDK 24 and Xcode 27.1. An iOS 18.5 simulator is available; a 26.5 simulator runtime is listed unavailable. Select a supported side-by-side Xcode 26.4/runtime for the first gate, or return with current official compatibility evidence and a revised stable pin. Do not alter global `xcode-select` or default Java settings. All Gradle commands below assume per-command JDK 21 and the selected Xcode developer directory. No runtime or dependency has been installed during plan writing.

Primary version sources: [CMP](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), [Room](https://developer.android.com/jetpack/androidx/releases/room3), [AGP](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [KSP](https://github.com/google/ksp/releases), [Ktor](https://ktor.io/docs/releases.html), [Google Sign-In iOS](https://developers.google.com/identity/sign-in/ios/release), and published metadata under `https://repo.maven.apache.org/maven2/` and `https://dl.google.com/dl/android/maven2/` using the exact artifact coordinates above. Material 3 and Navigation 3 have versions independent of the CMP plugin; do not substitute the Android-only artifacts in shared code.

**Execution ruling, 31 October 2026:** Xcode 27.1 is the observed local-development toolchain after both arm64 frameworks compiled, the Swift host built with strict dependency verification, and Compose rendered in the iOS 27.0 simulator. This replaces obtaining 26.4 before feature work; official compatibility qualification and older-OS/physical-device evidence remain release gates. Android/iOS support floors are unchanged. See `docs/development/toolchain.md` and the execution ledger for evidence and the cost of this ruling.

### File map

Paths below are repository-relative. Feature tests mirror their production packages; all new production Kotlin uses `dev.daymark`. No module-per-layer framework or DI container is added.

```text
settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat
gradle/libs.versions.toml, gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
shared/build.gradle.kts
shared/src/commonMain/kotlin/dev/daymark/
  app/{App.kt,AppGraph.kt,SetupService.kt,AppLifecycleCoordinator.kt,DataLifecycleService.kt}
  location/{LocationModels.kt,LocationGateway.kt,LocationPermissions.kt}
  auth/{GoogleAuthPort.kt,GoogleAccess.kt}
  attendance/{AttendanceModels.kt,AttendanceRules.kt,AttendanceRepository.kt,RoomAttendanceRepository.kt,AttendanceService.kt}
  plan/{ScheduleRules.kt,ZoneResolution.kt,NextAttendance.kt,RoomPlanReader.kt,TimeOffModels.kt,TimeOffRules.kt,TimeOffService.kt,PlanService.kt}
  data/{DaymarkDatabase.kt,AttendanceEntities.kt,PlanEntities.kt,ReportEntities.kt,DaymarkDao.kt}
  reporting/{GoogleReportTransport.kt,ReportWireModels.kt,ReportModels.kt,ReportProjection.kt,ReportStore.kt,RoomReportStore.kt,SyncCoordinator.kt,SyncRetryPolicy.kt,ConnectionService.kt}
  settings/{AppSettings.kt,LocaleController.kt,DisplayFormats.kt}
  reminders/{ReminderModels.kt,ReminderPlanner.kt,ReminderPort.kt}
  widgets/{WidgetSink.kt,QuickCheckRoute.kt,WidgetSnapshot.kt,WidgetPublisher.kt}
  ui/{AppNavigation.kt,DaymarkTheme.kt,SetupScreen.kt,SetupViewModel.kt,TodayScreen.kt,TodayViewModel.kt,HistoryScreen.kt,HistoryViewModel.kt,PlanScreen.kt,PlanViewModel.kt,SettingsScreen.kt,SettingsViewModel.kt,GoogleConnectionSection.kt}
shared/src/commonMain/composeResources/{values,values-ar}/strings.xml
shared/src/commonTest/kotlin/dev/daymark/          matching feature tests and TestFixtures.kt
shared/src/{androidMain,iosMain}/kotlin/dev/daymark/ platform adapters and database factories
shared/src/{androidHostTest,iosTest}/kotlin/dev/daymark/ native/database integration tests
androidApp/{build.gradle.kts,src/main/AndroidManifest.xml}
androidApp/src/main/kotlin/dev/daymark/android/{DaymarkApplication.kt,MainActivity.kt,AndroidAppGraph.kt,AndroidGoogleAuth.kt,AndroidLocationPermissions.kt,AndroidAttendanceScheduler.kt,AttendanceWorker.kt,ScheduleRecoveryReceiver.kt,DaymarkWidget.kt,DaymarkWidgetReceiver.kt,AndroidWidgetPublisher.kt,ReportSyncWorker.kt}
androidApp/src/{test,androidTest}/kotlin/dev/daymark/android/ Android tests/journeys
androidApp/src/debug/kotlin/dev/daymark/android/  explicitly debug-only native probes
iosApp/Daymark.xcodeproj/                        project, shared scheme, Package.resolved
iosApp/Daymark/{DaymarkApp.swift,ContentView.swift,NativeGoogleAuth.swift,ReminderAdapter.swift,WidgetSnapshotWriter.swift,Info.plist,Daymark.entitlements,PrivacyInfo.xcprivacy,en.lproj/InfoPlist.strings,ar.lproj/InfoPlist.strings}
iosApp/DaymarkWidget/{DaymarkWidget.swift,WidgetSnapshot.swift,Localizable.xcstrings,DaymarkWidget.entitlements}
iosApp/DaymarkProbeWidget/DaymarkProbeWidget.swift development-only feasibility target, retired in Task 12
iosApp/{DaymarkTests,DaymarkUITests}/             XCTest integration and UI journeys
docs/development/{toolchain.md,provisioning.md,native-feasibility.md,release-verification.md}
```

### Commands and handoff conventions

Use `com.android.kotlin.multiplatform.library` and the current `kotlin.android {}` DSL for `:shared`, with host/device tests explicitly enabled. Use `androidHostTest` and `androidDeviceTest`, not legacy shared `src/test`/`androidUnitTest`. Task 1 must confirm the generated task names with `./gradlew :shared:tasks --all`; these expected names come from the new plugin's model. [Official plugin guidance](https://developer.android.com/kotlin/multiplatform/plugin).

For shared rule tests use `./gradlew :shared:testAndroidHostTest --tests 'dev.daymark.<feature>.<TestClass>'` and repeat platform-sensitive cases with `./gradlew :shared:iosSimulatorArm64Test`. Android-host-specific tests do not run on iOS. Android app unit/device commands are `./gradlew :androidApp:testDebugUnitTest` and `./gradlew :androidApp:connectedDebugAndroidTest`.

The iOS project/scheme name is `Daymark`. Simulator verification uses `xcodebuild -project iosApp/Daymark.xcodeproj -scheme Daymark -destination 'platform=iOS Simulator,id=<verified-available-UDID>' CODE_SIGNING_ALLOWED=NO build` or `test`. Replace the destination with an actual available simulator from the selected Xcode; never label simulator output as physical-device evidence. Physical signing/App Group/OAuth provisioning needs the user's developer credentials and explicit consent flow. Record unavailable prerequisites rather than creating accounts or pretending the probe passed.

Every commit below stages only its listed files and generated lock/schema changes; inspect `git diff --cached --check` and the staged diff before committing. Commit messages are suggestions. Shared interfaces declared by each task are the names later tasks must use; private helpers remain implementation choices.

Test code below gives named assertion excerpts. Each named test owns its arranged fixtures/fakes and observed values in the listed test file; these are test-local names, not additional production APIs. Run every named class using the commands above. Add the assertions before production behavior, observe the intended failure, then implement and verify green.

---

### Task 1: Runnable Android/iOS hosts and a compatible pinned build

**Files:**
- Create: root build/wrapper/catalog files from the file map; `shared/build.gradle.kts`; `androidApp/build.gradle.kts`; `androidApp/src/main/AndroidManifest.xml`.
- Create: `shared/src/commonMain/kotlin/dev/daymark/app/BootstrapScreen.kt`; `shared/src/iosMain/kotlin/dev/daymark/app/MainViewController.kt`; Android `DaymarkApplication.kt`/`MainActivity.kt`; the iOS project/shared scheme/`DaymarkApp.swift`/`ContentView.swift`/Info.plist.
- Modify: `.gitignore`; create `docs/development/toolchain.md`.

**Interfaces:**
- Consumes: approved pins, platform floors, and per-command runtime selection above.
- Produces: `@Composable fun BootstrapScreen()` and `fun MainViewController(): UIViewController` for the temporary bootstrap host; a static `DaymarkShared` framework for `iosArm64` and `iosSimulatorArm64`. Task 9 explicitly replaces this bootstrap entry with its injected production entry.

- [x] **Step 1: Run the prerequisite check.** Inspect JDK/Xcode/simulator/Android SDK availability and record the matrix, including the known active Xcode 27.1 mismatch. Obtain/select the supported runtime only during authorized execution. Missing prerequisites fail this gate; do not suppress version checks.
- [x] **Step 2: Create the two hosts and exact version catalog.** Use Android's KMP library plugin for shared and the application plugin/built-in Kotlin integration for the Android host. Enable Compose resources, KSP targets, host/device tests, JVM 21, SDK 37/minimum 26, and iOS 16. Use SPM rather than CocoaPods. Add dependency verification/checksums and exclude local credentials/build outputs from Git.
- [x] **Step 3: Compile both routes into the bootstrap screen.** Run `./gradlew :shared:compileAndroidMain :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64 :androidApp:assembleDebug`, then the simulator `xcodebuild ... build` command above. Expected: all commands exit 0, the shared framework links, and both apps show the bootstrap screen. Configuration work uses compile/run checks, not an artificial unit test of literal build constants.
- [x] **Step 4: Freeze the proven matrix and task names.** Record compiler/runtime warnings, resolved direct/transitive versions, wrapper checksum, test task names, and actual simulator in `toolchain.md`. A hard incompatibility stops dependent work; correct the stable pin with primary-source evidence and repeat Step 3 before moving on. No support-floor or workflow change is implicit.
- [x] **Step 5: Commit the runnable foundation.** Commit listed build/host files and lockfiles as `build: establish Android and iOS CMP hosts`.

### Task 2: Fresh native location adapters and the background feasibility probe

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/location/LocationModels.kt`, `LocationGateway.kt`, `LocationPermissions.kt`; `androidApp/src/main/kotlin/dev/daymark/android/AndroidLocationPermissions.kt`.
- Create: `shared/src/androidMain/kotlin/dev/daymark/location/AndroidLocationGateway.kt`; `shared/src/iosMain/kotlin/dev/daymark/location/IosLocationGateway.kt`.
- Create: `shared/src/androidHostTest/kotlin/dev/daymark/location/AndroidLocationGatewayTest.kt`; `shared/src/iosTest/kotlin/dev/daymark/location/IosLocationGatewayTest.kt`.
- Create: `androidApp/src/debug/kotlin/dev/daymark/android/NativeProbeScreen.kt`, `LocationProbeWorker.kt`; `iosApp/Daymark/NativeProbeView.swift` and `ReminderProbe.swift` guarded by `#if DEBUG`; a development-only `iosApp/DaymarkProbeWidget/DaymarkProbeWidget.swift` target; `docs/development/native-feasibility.md`.
- Modify: host permission declarations and bootstrap debug entry.

**Interfaces:**
- Consumes: native permission state and runtime clocks; no attendance repository yet.
- Produces: `interface LocationGateway { suspend fun requestFix(budget: Duration): LocationResult }`; `LocationResult.Fix(latitude: Double, longitude: Double, horizontalAccuracyMetres: Double, capturedAt: Instant, receivedAt: Instant, ageAtReceipt: Duration?)` and `Failure(reason: LocationFailure)` with `PERMISSION_DENIED`, `SERVICES_DISABLED`, `TIMEOUT`, `UNAVAILABLE`, `CANCELLED`.
- Produces: `LocationCapabilities(foregroundGranted: Boolean, backgroundGranted: Boolean, preciseGranted: Boolean, servicesEnabled: Boolean)` and `LocationPermissionPort.current(): LocationCapabilities`, `requestForeground(): LocationCapabilities`, `requestBackground(): LocationCapabilities` (both requests suspend), and `openSystemSettings(): Unit`. Request UI is user-initiated; iOS background access is never requested. The iOS implementation lives alongside its gateway and Android activity-result implementation lives in the host.

- [x] **Step 1: Add adapter tests with controlled native callbacks.** Assert one request is cancelled at 30 seconds, denial returns `PERMISSION_DENIED`, cancellation never retries, and malformed coordinates/timestamps yield `UNAVAILABLE` (RF1). Test a callback arriving after cancellation is ignored and no raw position reaches captured logs.

```kotlin
// AndroidLocationGatewayTest.timeout_cancels_once_and_ignores_late_fix
assertEquals(LocationFailure.TIMEOUT, assertIs<LocationResult.Failure>(result).reason)
assertEquals(1, nativeRequestCount)
assertEquals(1, nativeCancellationCount)
// IosLocationGatewayTest.invalid_fix_is_not_success
assertEquals(LocationFailure.UNAVAILABLE, assertIs<LocationResult.Failure>(result).reason)
assertFalse(logsContainCoordinates)
```
- [x] **Step 2: Run the tests before the adapter implementation.** Use the Android host command for `AndroidLocationGatewayTest` and `:shared:iosSimulatorArm64Test`. Expected: unresolved adapter/API or behavior failure, not a broken test harness.
- [x] **Step 3: Implement the adapters.** Android uses fused `getCurrentLocation` with an explicit cache-age budget and cancellation; iOS uses a one-shot Core Location delegate and When In Use capability. Calculate native freshness at receipt; unknown age is represented explicitly. Map disabled/denied/timeout states and discard raw positions after the caller finishes.
- [ ] **Step 4: Run passing tests and physical probes.** On Android exercise foreground, locked-screen/background, denied background access, battery saver/Doze, and one non-Google manufacturer. On iOS exercise foreground precise/denied/unavailable cases without relying on debugger execution, one native local reminder, and a development widget URL opening the foreground probe. These minimal reminder/widget probes store no attendance and are replaced by Tasks 8/12; do not postpone their feasibility until full feature implementation. Record request count, delay, fix age/accuracy verdict, and outcome without coordinates. A missed background fix is an observed limitation, not permission to add retries/FGS/exact alarms. Return to design review if the chosen mode needs a different mechanism.
- [x] **Step 5: Commit.** `feat: add bounded native location adapters and feasibility probes`.

### Task 3: Native Google authorization and a literal report connectivity probe

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/auth/GoogleAuthPort.kt`, `GoogleAccess.kt`; `shared/src/commonMain/kotlin/dev/daymark/reporting/GoogleReportTransport.kt`, `ReportWireModels.kt`.
- Create: `androidApp/src/main/kotlin/dev/daymark/android/AndroidGoogleAuth.kt`; `iosApp/Daymark/NativeGoogleAuth.swift`; `iosApp/Daymark/Configuration.example.xcconfig`; `docs/development/provisioning.md`.
- Test: `shared/src/commonTest/kotlin/dev/daymark/auth/GoogleAccessTest.kt`, `shared/src/commonTest/kotlin/dev/daymark/reporting/GoogleReportTransportTest.kt`; `iosApp/DaymarkTests/GoogleAuthBridgeTests.swift`.
- Modify: Android activity-result wiring, iOS SPM/URL callback configuration, debug probes, and `.gitignore` for real local configuration.

**Interfaces:**
- Produces: `NativeGoogleAuth.authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit)` and `forget(completion: (Boolean) -> Unit)`. `NativeAuthReply(status: NativeAuthStatus, accessToken: String?, errorKey: String?)` uses `GRANTED/NEEDS_INTERACTION/CANCELLED/FAILED`; it is not serializable/persistable and its string representation redacts tokens. Concrete adapters are Kotlin `AndroidGoogleAuth` and Swift `IosGoogleAuthAdapter: DaymarkShared.NativeGoogleAuth` in `NativeGoogleAuth.swift`.
- Produces: `GoogleAccess.accessToken(interactive: Boolean): AccessResult` and `forget(): Unit` as suspend wrappers. `AccessResult` is `Granted(token: String)/NeedsInteraction/Cancelled/Failed(errorKey: String)` with redacted string output. `GoogleReportTransport` produces suspend methods `accountReference(token: String): String`, `discover(trackerId: String, token: String): ReportDiscovery`, `create(title: String, trackerId: String, token: String): ReportCreation`, `read(report: RemoteReport, ranges: List<String>, token: String): RemoteSnapshot`, and `write(report: RemoteReport, patch: RawPatch, token: String): RemoteWriteOutcome`.
- Wire DTOs: `RemoteReport(id: String, tabIds: Map<String, Int>, schemaVersion: Int?)`; `RawPatch(ranges: List<RawRange>)`; `RawRange(a1: String, cells: List<List<JsonPrimitive>>)`; `RemoteSnapshot(ranges: Map<String, List<List<JsonPrimitive>>>)`. `ReportDiscovery` is `None/Found(report: RemoteReport)/Multiple(reports: List<RemoteReport>)`; `ReportCreation` is `Ready(report: RemoteReport)/Ambiguous/Unavailable`; write outcomes are `Acknowledged/Retryable(retryAfter: Duration?)/Ambiguous/AuthorizationRequired/SchemaConflict/ReportUnavailable`.

- [x] **Step 1: Write auth/HTTP tests using callbacks and Ktor MockEngine.** Assert `interactive=false` never opens account UI, cancellation remains cancellation, tokens are redacted, only `drive.file` is requested, HTTP 401 pauses for authorization, and a note `=1+2` is sent as `RAW`. Assert account A/B are distinct and callback completion is delivered once (RF5).

```kotlin
// GoogleAccessTest.silent_access_cannot_launch_consent
assertIs<AccessResult.NeedsInteraction>(result)
assertEquals(0, consentLaunchCount)
assertFalse(tokenWasLogged)
// GoogleReportTransportTest.literal_write_and_account_identity
assertEquals("RAW", requestValueInputOption)
assertEquals("=1+2", sentNote)
assertNotEquals(accountAReference, accountBReference)
assertEquals(setOf("https://www.googleapis.com/auth/drive.file"), requestedScopes)
```
- [x] **Step 2: Run the two shared test classes and Swift bridge test.** Expected: missing implementations or failed behavioral assertions. A missing developer credential is not a unit-test failure or a passed live probe.
- [x] **Step 3: Implement authorization and minimal transport.** Use AuthorizationClient on Android and Google Sign-In 10.1.0 through SPM on iOS; present native consent on the UI thread/main actor and deliver a completion at most once. Never request a server auth code or store a refresh token in Room. Obtain the opaque account reference with Drive `about.get?fields=user(permissionId)` under the same scope; do not assume AuthorizationResult supplies an account or add profile scopes. Use Drive file app-properties (`daymarkTrackerId`) for discovery/creation of app-owned spreadsheets, and Sheets REST v4 for values. An ambiguous create is discovered later, not blindly repeated; multiple matches need an explicit report selection.
- [ ] **Step 4: Verify a real connect/create/write/read on each platform.** With the user's OAuth configuration and consent, create an explicitly named developer test report, write/read a literal test row, and cancel/reconnect. Retain the test report for the user to remove; no silent cloud deletion. Record success or the exact provisioning blocker. See [Android authorization/account behavior](https://developer.android.com/identity/authorization), [Drive about scope](https://developers.google.com/workspace/drive/api/reference/rest/v3/about/get), and [Drive permission ID](https://developers.google.com/workspace/drive/api/reference/rest/v3/User).
- [x] **Step 5: Commit adapters, mocks, and provisioning instructions.** `feat: add narrow Google authorization and report transport`; exclude credentials and live token/report dumps.

**Execution ruling:** iOS uses exact AppAuth 3.0.0 (revision `a972daac82d449d58ab119e91c68153e29ddac33`) instead of the initially proposed Google Sign-In SDK, which unconditionally adds email/profile scopes. The approved `drive.file` limit stands. See `docs/development/provisioning.md`. Native/mock gates pass; Step 4 remains open without OAuth configuration and live consent.

### Task 4: Calendar resolution and pure attendance rules

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/attendance/AttendanceModels.kt`, `AttendanceRules.kt`; `shared/src/commonMain/kotlin/dev/daymark/plan/ScheduleRules.kt`, `ZoneResolution.kt`.
- Create: Android/iOS `plan/ZoneResolution.android.kt` and `ZoneResolution.ios.kt` in their platform source sets.
- Test: `shared/src/commonTest/kotlin/dev/daymark/TestFixtures.kt`, `attendance/AttendanceRulesTest.kt`, `plan/ScheduleRulesTest.kt`.

**Interfaces:**
- Consumes: Task-2 `LocationResult.Fix`; `kotlin.time.Instant/Duration/Clock` and kotlinx local date/time/zone types.
- Produces: `DayKey(trackerId: String, date: LocalDate)`, `WeeklySchedule(days: Set<DayOfWeek>, arrival: LocalTime, overrides: Map<DayOfWeek, LocalTime>, graceMinutes: Int, zoneId: String)`, `RegisteredPlace(latitude: Double, longitude: Double, radiusMetres: Int, version: Long)`, and immutable `DayPlan(key: DayKey, category: TrackerCategory, arrival: Instant, deadline: Instant, zoneId: String, scheduleVersion: Long, place: RegisteredPlace, isScheduled: Boolean)`.
- Produces: `AttendanceStatus` (`SCHEDULED/AWAITING_CHECK/PRESENT/LATE/ABSENT/DAY_OFF/HOLIDAY/VACATION`), `AttendanceSource` (`NONE/AUTOMATIC_GPS/MANUAL_GPS/MANUAL_CORRECTION/MANUAL_TIME_OFF`), `CheckMode` (`AUTOMATIC/USER_INITIATED`), `AttendanceEvidence(status: AttendanceStatus, source: AttendanceSource, confirmedAt: Instant?, note: String?, updatedAt: Instant)`, and `DayOutcome(status: AttendanceStatus, source: AttendanceSource, confirmedAt: Instant?, offReason: TimeOffKind?)`; `TimeOffKind` is `HOLIDAY/VACATION`.
- Produces: `resolveLocalArrival(date: LocalDate, time: LocalTime, zoneId: String): Instant`, `resolveDay(key: DayKey, schedule: WeeklySchedule, category: TrackerCategory, place: RegisteredPlace, scheduleVersion: Long): DayPlan`, `evaluateDay(plan: DayPlan, evidence: AttendanceEvidence?, off: TimeOffKind?, now: Instant): DayOutcome`, and `classifyFix(plan: DayPlan, fix: LocationResult.Fix, mode: CheckMode, now: Instant): FixVerdict`. `FixVerdict` is `Accepted(status: AttendanceStatus, confirmedAt: Instant)` or `Rejected(reason: CheckRejection)`; `TrackerCategory` is `WORK/SCHOOL/COURSE`. Define `CheckRejection` for `BEFORE_WINDOW/AFTER_AUTOMATIC_WINDOW/STALE_FIX/INVALID_FIX/INACCURATE_FIX/OUTSIDE_AREA/NO_TRACKER/DAY_OFF/LOCATION_DENIED/LOCATION_DISABLED/LOCATION_TIMEOUT/UNAVAILABLE/CANCELLED/STATE_CHANGED`.

- [x] **Step 1: Add explicit boundary assertions.** `fixturePlan()` uses 27 October 2026, UTC, 09:00, grace 5, radius 100, and a fixed tracker UUID; `fixtureFix()` defaults to the registered center, accuracy 10 m, and age zero. Pin the central rule:

```kotlin
// AttendanceRulesTest.inclusive_grace_and_freshness
assertEquals(AttendanceStatus.PRESENT, acceptedStatus(p, "09:05:00", CheckMode.USER_INITIATED))
assertEquals(AttendanceStatus.LATE, acceptedStatus(p, "09:05:00.001", CheckMode.USER_INITIATED))
assertEquals(AttendanceStatus.ABSENT, evaluateDay(p, null, null, at("09:05:00.001")).status)
val stale = classifyFix(p, fixtureFix(ageSeconds = 31), CheckMode.USER_INITIATED, at("09:02:31"))
assertEquals(CheckRejection.STALE_FIX, assertIs<FixVerdict.Rejected>(stale).reason)
```

Also assert radius 100/accuracy 50 are inclusive, 50.001 accuracy fails, negative/NaN/infinite/unknown/future sample data fails, pre-arrival capture fails, and an automatic post-deadline sample cannot yield Late (RF1). Define `at(localTime: String): Instant` and `acceptedStatus(plan, localTime, mode)` only as test fixtures. `fixtureFix` defaults its capture to 09:02 and receipt to capture plus `ageSeconds`, keeping stale-age tests internally consistent.
- [x] **Step 2: Run both test classes red.** Expected: missing pure rule/resolution functions or their behavioral failures.
- [x] **Step 3: Implement pure rules and native zone resolution.** Use distance-to-registered-center plus the separate accuracy threshold. Resolve a DST gap to the first valid instant and an overlap to the earlier instant, using Java zone rules and Foundation calendar matching policies; do not rely on a default resolver that shifts a nonexistent 02:30 to 03:30.
- [x] **Step 4: Verify host and iOS tests.** Pin `America/New_York` 8 March 2026 02:30 → 07:00Z and 1 November 2026 01:30 → 05:30Z; Thursday override 09:30; zero grace; empty weekdays rejection; fixed attendance-zone behavior after travel; and Gregorian date identity after locale change (RF3). Expected: identical results on both targets.
- [x] **Step 5: Commit.** `feat: define deterministic attendance and schedule rules`.

**Execution ruling:** reject a resolved arrival/grace window crossing the attendance-zone calendar date; overnight shifts are excluded and GPS confirmation belongs to one date. Near-midnight users must choose a grace ending on that date.

### Task 5: Room transactions, automatic claims, and local attendance service

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/data/DaymarkDatabase.kt`, `AttendanceEntities.kt`, `PlanEntities.kt`, `ReportEntities.kt`, `DaymarkDao.kt`; platform `data/DatabaseFactory.android.kt`/`DatabaseFactory.ios.kt`.
- Create: `shared/src/commonMain/kotlin/dev/daymark/attendance/AttendanceRepository.kt`, `RoomAttendanceRepository.kt`, `AttendanceService.kt`.
- Test: `shared/src/androidHostTest/kotlin/dev/daymark/attendance/AttendanceTransactionTest.kt`; `shared/src/iosTest/kotlin/dev/daymark/data/DatabaseReopenTest.kt`; `shared/src/commonTest/kotlin/dev/daymark/attendance/AttendanceServiceTest.kt`.
- Create: `shared/schemas/` exported Room schemas, tracked when generated.

**Interfaces:**
- Consumes: Task-4 models/rules and Task-2 gateway; inject `Clock`.
- Produces: interface `AttendanceRepository` and concrete `RoomAttendanceRepository(db: DaymarkDatabase, clock: Clock)` with `observeDay(key: DayKey): Flow<DayOutcome?>`, `observeMonth(year: Int, month: Int): Flow<HistoryMonth>`, `loadCurrentDay(now: Instant): StoredDay?`, `loadDay(key: DayKey): StoredDay?`, `claimAutomatic(key: DayKey, attemptId: String, now: Instant): ClaimResult`, `completeCheck(request: CheckCommit): CheckOutcome`, `correct(command: Correction): DayOutcome`, and `reconcile(now: Instant): Unit`; all methods suspend except observations. `HistoryMonth(records: List<HistoryRecord>, present: Int, late: Int, absent: Int)` and `HistoryRecord(plan: DayPlan, evidence: AttendanceEvidence?, outcome: DayOutcome)` support local history without UI SQL. Common service tests use a fake implementation of this interface; transaction tests use the concrete Room implementation.
- `StoredDay(plan: DayPlan, evidence: AttendanceEvidence?, off: TimeOffKind?, automaticState: AutomaticState, revision: Long)` uses `UNCLAIMED/CLAIMED/FINISHED/INTERRUPTED`. `ClaimResult` is `Claimed(day: StoredDay, attemptId: String)/AlreadyConsumed/Ineligible`; `Correction(key: DayKey, status: AttendanceStatus, arrivalTime: LocalTime?, note: String?)`; `CheckCommit(key: DayKey, attemptId: String?, expectedScheduleVersion: Long, expectedPlaceVersion: Long, verdict: FixVerdict, mode: CheckMode, updatedAt: Instant)`; `CheckOutcome` is `Recorded/Rejected/AlreadyRecorded/StateChanged/Busy`, carrying `currentDay: DayOutcome?` and an optional `CheckRejection` when relevant.
- Produces: `AttendanceService.manualCheck(): CheckOutcome` and `runAutomatic(key: DayKey): CheckOutcome`. These load/claim, acquire outside the DB transaction, and commit only after revalidation.

- [x] **Step 1: Write actual database/service failure tests.** Unique `(trackerId,date)` survives concurrent inserts; two automatic claims yield one `Claimed`; kill/reopen after claim consumes it; a manual correction or time-off overlay during acquisition wins; failure cannot downgrade Present; earlier successful evidence wins; local record and dirty date/Overview revisions commit or roll back together (RF2). The service test injects a fake location gateway and asserts zero requests for stale/claimed/off days.

```kotlin
// AttendanceTransactionTest.claim_is_unique_and_remains_consumed_after_reopen
assertEquals(1, claimResults.count { it is ClaimResult.Claimed })
assertIs<ClaimResult.AlreadyConsumed>(claimAfterReopen)
assertEquals(1, rowsForDay)
// AttendanceServiceTest.correction_wins_during_location_acquisition
assertEquals(AttendanceSource.MANUAL_CORRECTION, outcome.source)
assertEquals(AttendanceStatus.ABSENT, outcome.status)
// AttendanceTransactionTest.failed_transaction_cannot_leave_partial_outbox
assertEquals(beforeAttendanceRevision, afterAttendanceRevision)
assertEquals(beforeDirtyRevisions, afterDirtyRevisions)
```
- [x] **Step 2: Run red on host plus iOS reopen tests.** Use a real temporary bundled SQLite database for persistence tests, not an in-memory fake of transaction semantics.
- [x] **Step 3: Implement the database/repository/service.** Tables cover settings/setup draft, tracker/versioned expectations, attendance/claim/underlying evidence, time-off entries, balance adjustments, report binding, dirty projections, and in-flight batches. Store dates/instants canonically; preserve row/connection revisions. Use Room transactions for claims and revalidation. Persist only verdict metadata, not raw check coordinates. Cancellation after claim cannot release it.
- [x] **Step 4: Verify passing tests and cold reopen.** Include setup after today's deadline creating no retroactive automatic absence; explicit today correction still works. Reconcile missed dates using historical versions, never GPS. Database failures expose a local-save error, not a success message. No destructive migration fallback is configured.
- [x] **Step 5: Commit schema and transactional behavior.** `feat: persist authoritative attendance and consumed automatic attempts`.

**Execution rulings:** real persistence tests use the supported JVM Room target plus iOS files, with Android wiring retained for device acceptance. `StoredDay.userEditRevision` and `CheckCommit.expectedUserEditRevision` fence user-edit ABA without rejecting earlier concurrent GPS evidence. `CheckRejection.LOCAL_SAVE_FAILED` distinguishes unsuccessful storage from sensor failures. See `docs/development/persistence.md`.

### Task 6: Retrospective time off, balance adjustments, and future-effective plan changes

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/plan/TimeOffModels.kt`, `TimeOffRules.kt`, `TimeOffService.kt`, `PlanService.kt`.
- Modify: `PlanEntities.kt`, `DaymarkDao.kt`, `AttendanceRepository.kt`, generated schemas if required.
- Test: `shared/src/commonTest/kotlin/dev/daymark/plan/TimeOffRulesTest.kt`; `shared/src/androidHostTest/kotlin/dev/daymark/plan/TimeOffTransactionTest.kt`.

**Interfaces:**
- Consumes: frozen `DayPlan`, underlying evidence, database revisions, and `evaluateDay`.
- Produces: `TimeOffCommand.Add(kind: TimeOffKind, from: LocalDate, through: LocalDate)`, `Replace(entryId: String, kind: TimeOffKind, from: LocalDate, through: LocalDate)`, `Remove(entryId: String)`; `TimeOffPreview(expectedRevision: Long, affectedDays: List<DayKey>, resultingOutcomes: List<DayOutcome>, balanceDelta: Int, availableAfter: Int, rejection: PlanRejection?, effectiveFrom: LocalDate? = null)`. `PlanRejection` is `INVALID_RANGE/INVALID_VALUE/OUTSIDE_TRACKED_PERIOD/INSUFFICIENT_BALANCE`; import the Task-4 `TimeOffKind` instead of defining it twice.
- Produces: `TimeOffService.preview(command: TimeOffCommand): TimeOffPreview`, `apply(command: TimeOffCommand, expectedRevision: Long): PlanMutationResult`, `setAvailableBalance(targetDays: Int, note: String?): PlanMutationResult`; `PlanMutationResult` is `Applied/StalePreview/Invalid/InsufficientBalance`. It also exposes `observeEntries(): Flow<List<TimeOffEntry>>` and `observeAvailableBalance(): Flow<Int?>`; `TimeOffEntry(id: String, kind: TimeOffKind, from: LocalDate, through: LocalDate, updatedAt: Instant)` is defined in `TimeOffModels.kt`.
- Produces: suspend `PlanService.previewChange(command: PlanChange): TimeOffPreview` and `applyChange(command: PlanChange, expectedRevision: Long): PlanMutationResult`, plus `observePlan(): Flow<PlanState?>`. `PlanState(current: PlanChange, pending: PlanChange?, effectiveFrom: LocalDate?)`; `PlanChange(schedule: WeeklySchedule, category: TrackerCategory, place: RegisteredPlace)` applies next local day; the preview's `effectiveFrom` contains that date. Use a tracker-level change revision to fence stale previews. TimeOffService's preview/apply/balance methods are also suspend database operations.

- [ ] **Step 1: Add pure and transaction assertions.** For Monday–Friday, opening 12, vacation 2–3 November 2026 → available 10; add a holiday on the 3rd → 11; remove that holiday → 10; repeat saves → unchanged. Overlapping vacation entries deduct distinct eligible dates once; weekends consume zero. Add a past vacation over Absent, observe Vacation, remove it, observe Absent. Preserve a prior Present/manual correction underneath (RF2).

```kotlin
// TimeOffTransactionTest.holiday_refund_and_repeat_save_are_idempotent
assertEquals(10, afterVacation)
assertEquals(11, afterHoliday)
assertEquals(10, afterHolidayRemoval)
assertEquals(10, afterRepeatedSave)
// TimeOffTransactionTest.removing_retrospective_leave_restores_underlying_evidence
assertEquals(AttendanceStatus.VACATION, covered.status)
assertEquals(AttendanceStatus.ABSENT, restored.status)
assertEquals(AttendanceSource.MANUAL_CORRECTION, restoredCorrection.source)
```
- [ ] **Step 2: Run red.** Expected: missing preview/apply/ledger behavior; distinguish whole-day input validation from storage failure.
- [ ] **Step 3: Implement previews and atomic differences.** Use historical category/schedule expectations for past dates and projected next-day expectations for future changes. Reject an increasing deduction that exceeds the available balance. Keep manual adjustment history; never auto-reset annually. Update affected date/Time off/Overview dirty revisions in the same transaction. Invalid dates, negative/fractional targets, and stale preview versions do not partially mutate state.
- [ ] **Step 4: Verify all mutation paths.** Include edit/delete/current-day overlay while a check runs; removing today's overlay never resets a consumed claim; future schedule/category changes recalculate future reservations while preserving historical deductions. Show a stale preview instead of silently replacing an outcome the user did not review.
- [ ] **Step 5: Commit.** `feat: support historical time off and transactional vacation balance`.

### Task 7: Production Android scheduling and permission recovery

**Files:**
- Create: `androidApp/src/main/kotlin/dev/daymark/android/AndroidAttendanceScheduler.kt`, `AttendanceWorker.kt`, `ScheduleRecoveryReceiver.kt`.
- Create: `shared/src/commonMain/kotlin/dev/daymark/plan/NextAttendance.kt`, `RoomPlanReader.kt`.
- Test: `androidApp/src/test/kotlin/dev/daymark/android/AttendanceWorkerTest.kt`; `androidApp/src/androidTest/kotlin/dev/daymark/android/AutomationJourneyTest.kt`.
- Modify: Android manifest, app graph/bootstrap, debug probe to call the production adapter; `native-feasibility.md`.

**Interfaces:**
- Consumes: `AttendanceService.runAutomatic(key)`, stored schedule versions, and permission capabilities.
- Produces: `ScheduledCheck(key: DayKey, arrival: Instant, deadline: Instant)` and suspend `nextAttendance(now: Instant, plans: PlanReader): ScheduledCheck?`; `AndroidAttendanceScheduler.replaceNext(check: ScheduledCheck?): Unit` and `cancelTracker(trackerId: String): Unit`.
- Produces: `ScheduleContext(trackerId: String, zoneId: String, trackingStartsAt: Instant, automaticEnabled: Boolean)`; `PlannedDay(plan: DayPlan, off: TimeOffKind?, hasSuccessOrCorrection: Boolean, automaticConsumed: Boolean)`; interface `PlanReader.context(): ScheduleContext?` and `day(date: LocalDate): PlannedDay` (both suspend), implemented by `RoomPlanReader`. `nextAttendance` chooses the earliest eligible window that has not ended, including today during grace, with `plan.arrival >= trackingStartsAt`; the host uses `max(now, arrival)` as the trigger. Disabled/unconfigured/no available occurrence returns null. Projecting future dates must not freeze them against subsequent approved changes.

- [ ] **Step 1: Write worker tests with a counting gateway.** A worker at 09:06 for a 09:05 deadline requests location zero times; retry after a consumed claim requests zero; denial disables automation without deleting records; successful/failed completion schedules the next eligible date. Reboot/zone recovery does not duplicate a date's claim (RF3). Recovery at 09:02 retains today's unclaimed window with zero delay; recovery after 09:05:00 skips it.

```kotlin
// AttendanceWorkerTest.late_dispatch_never_requests_location
assertEquals(0, locationRequestCount)
assertEquals(AttendanceStatus.ABSENT, reconciled.status)
// AttendanceWorkerTest.recovery_during_grace_keeps_today
// Arrange tracking started yesterday; an installation at 09:02 must skip this arrival.
assertEquals(LocalDate(2026, 10, 27), requireNotNull(nextAt0902).key.date)
assertEquals(Duration.ZERO, delayAt0902)
assertEquals(LocalDate(2026, 10, 28), requireNotNull(nextAfterDeadline).key.date)
```
- [ ] **Step 2: Run red with WorkManager testing utilities.** Expected: scheduling/worker assertions fail; use fake clock and controlled worker dispatch, not sleep-based timing tests.
- [ ] **Step 3: Implement one-time unique work.** One key per tracker/date; no network constraint for attendance. Recompute the next eligible occurrence, recover after restart/configuration changes, and use successful completion rather than GPS retry for denied/timeout/ineligible outcomes. Transaction failures before claim may recover; claimed work never takes a second location sample. Request background access only through the explicit UI flow, not from the Worker.
- [ ] **Step 4: Verify host/device journeys.** Check merged manifest excludes exact-alarm permissions/continuous location service; test foreground/background permission revocation, locked screen, Doze, restart, and changed device zone. Record observed scheduling delay and false-absence/manual-recovery behavior; do not assert precise wake guarantees.
- [ ] **Step 5: Commit.** `feat: schedule one eligible Android attendance attempt per date`.

### Task 8: iOS reminder planning and native notification entry

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/reminders/ReminderModels.kt`, `ReminderPlanner.kt`, `ReminderPort.kt`; `iosApp/Daymark/ReminderAdapter.swift`.
- Test: `shared/src/commonTest/kotlin/dev/daymark/reminders/ReminderPlannerTest.kt`; `iosApp/DaymarkTests/ReminderAdapterTests.swift`.
- Modify: iOS app delegate/host callbacks, Info.plist, `native-feasibility.md`.

**Interfaces:**
- Produces: `ReminderSpec(id: String, trackerId: String, attendanceDate: String, fireAtEpochMillis: Long, title: String, body: String, localeTag: String)` and `ReminderCopy(localeTag: String, title: String, body: String)`; suspend `planReminders(now: Instant, reader: PlanReader, copy: ReminderCopy): List<ReminderSpec>` for the next 30 calendar days, excluding past trigger instants/off days. Task 9 supplies localized copy; this task does not depend on its later AppLanguage type.
- Produces: callback-based native `ReminderPort.requestPermission(completion: (Boolean) -> Unit)`, `replace(remindersJson: String, completion: (Boolean) -> Unit)`, and `cancelTracker(trackerId: String, completion: (Boolean) -> Unit)`. Notification opening emits a current quick-check intent; it does not record attendance.

- [ ] **Step 1: Add planning and adapter tests.** Assert the inclusive 30-calendar-day horizon, Thursday override, holiday/vacation suppression, no immediate duplicate of a past trigger, deterministic IDs, and replacement after language/schedule change (RF3). A denied notification grant leaves manual location checking available.

```kotlin
// ReminderPlannerTest.horizon_is_thirty_calendar_dates_not_thirty_occurrences
// Arrange every weekday, UTC, 27 Oct 2026 08:00; no leave.
assertEquals(30, reminders.size)
assertEquals("2026-10-27", reminders.first().attendanceDate)
assertEquals("2026-11-25", reminders.last().attendanceDate)
assertEquals(reminders.size, reminders.map { it.id }.toSet().size)
// ReminderPlannerTest.travel_and_language_change_preserve_planned_instants
assertEquals(beforeEpochs, afterTravelAndArabicEpochs)
assertTrue(arabicReminders.all { it.localeTag == "ar" })
```
- [ ] **Step 2: Run shared/XCTest tests red.** Native pending-request assertions use an injected notification-center wrapper; delivery is a separate physical test.
- [ ] **Step 3: Implement plan/replace/cancel.** Use `UNUserNotificationCenter`; remove only this tracker's owned future requests. Construct nonrepeating triggers from UTC date components of the planned epoch instant, explicitly setting their calendar/time zone, so device travel cannot move an already planned attendance instant. No background location mode/Always permission and no notification at the grace deadline. Route notification taps through the host's normal foreground check destination; retire the Task-2 reminder probe.
- [ ] **Step 4: Verify notification delivery on a signed physical iPhone and pending queue contents.** Test app termination and denied presentation, record delivery/health limitations, and expose the finite reminder horizon to the later Settings screen. Simulator-only success leaves the physical gate incomplete.
- [ ] **Step 5: Commit.** `feat: add bounded iOS attendance reminders`.

### Task 9: EN/AR theme, locale infrastructure, and durable onboarding

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/settings/AppSettings.kt`, `LocaleController.kt`, `DisplayFormats.kt`; platform `settings/DisplayFormats.android.kt`/`DisplayFormats.ios.kt` and locale adapters.
- Create: `shared/src/commonMain/kotlin/dev/daymark/app/App.kt`, `AppGraph.kt`, `SetupService.kt`; UI `AppNavigation.kt`, `DaymarkTheme.kt`, `SetupScreen.kt`, `SetupViewModel.kt`; both shared `strings.xml` resources.
- Create: `shared/src/commonMain/kotlin/dev/daymark/widgets/WidgetSink.kt` for the native snapshot boundary.
- Create: `iosApp/Daymark/en.lproj/InfoPlist.strings`, `ar.lproj/InfoPlist.strings`; Android native strings/locale configuration.
- Test: `shared/src/commonTest/kotlin/dev/daymark/settings/LocaleControllerTest.kt`; `androidApp/src/androidTest/kotlin/dev/daymark/android/SetupJourneyTest.kt`; `iosApp/DaymarkUITests/SetupJourneyTests.swift`.
- Modify: native composition roots/hosts, `DaymarkDao.kt`/settings entities; retire `BootstrapScreen.kt` from production navigation.

**Interfaces:**
- Produces: `LanguagePreference` (`SYSTEM/EN/AR`), `AppLanguage` (`EN/AR`), `ThemePreference` (`SYSTEM/LIGHT/DARK`); `effectiveLanguage(preference: LanguagePreference, systemTags: List<String>): AppLanguage`; `parseWholeDays(text: String, language: AppLanguage): Int?`; the `DisplayFormats` entry points below use explicit attendance zone and Gregorian dates.
- Produces: Room-backed `SettingsRepository.observe(): Flow<AppSettings>` and suspend `update(language: LanguagePreference? = null, theme: ThemePreference? = null, displayName: String? = null): Unit`; `AppSettings(displayName: String, language: LanguagePreference, theme: ThemePreference)`. `DisplayFormats.date(date: LocalDate, language: AppLanguage): String`, `time(at: Instant, zoneId: String, language: AppLanguage): String`, and `number(value: Int, language: AppLanguage): String` are the exact formatter entry names. Localized `ReminderCopy` comes from the same resources, outside Compose when refreshing native requests.
- Produces: `SetupDraft(displayName: String, category: TrackerCategory?, placeName: String, place: RegisteredPlace?, schedule: WeeklySchedule?, openingBalanceText: String)` and complete `SetupCommand(displayName: String, category: TrackerCategory, placeName: String, place: RegisteredPlace, schedule: WeeklySchedule, openingBalance: Int)`; `SetupService.observeDraft(): Flow<SetupDraft>`, suspend `saveDraft(draft: SetupDraft): Unit` and `complete(command: SetupCommand): SetupResult` (`Completed/Invalid`). `SetupViewModel.uiState: StateFlow<SetupUiState>`, `useCurrentLocation()`, and `submit()`; a denied location leaves an incomplete draft, never invented coordinates.
- Produces: `AppGraph(db: DaymarkDatabase, repository: AttendanceRepository, attendance: AttendanceService, timeOff: TimeOffService, plans: PlanService, planReader: PlanReader, settings: SettingsRepository, location: LocationGateway, permissions: LocationPermissionPort, auth: GoogleAccess, reminders: ReminderPort, widgetSink: WidgetSink, clock: Clock)` and `@Composable fun App(graph: AppGraph)`; production `fun MainViewController(auth: NativeGoogleAuth, reminders: ReminderPort, widget: WidgetSink): UIViewController`. Declare native `WidgetSink.publish(json: String): Boolean`/`clear(): Boolean` in `widgets/WidgetSink.kt`; Task 12 implements its atomic native sinks and reports publication failure separately from local attendance success. Update the Swift host in this task to use the new entry; the Task-1 no-argument bootstrap is retired. Before native widgets exist, use an explicit development-only no-op sink, not a simulated success UI.

- [ ] **Step 1: Add language/format/setup tests.** Assert unsupported system language falls back to English, explicit AR produces RTL, all required resources have EN/AR entries, Arabic `١٢` and English `12` parse as 12, fractions/negative balance reject, and locale change preserves canonical dates (RF4). Setup denial retains the draft and cannot complete a fake location; Google/notifications can be skipped.

```kotlin
// LocaleControllerTest.fallback_and_whole_day_input
assertEquals(AppLanguage.EN, effectiveLanguage(LanguagePreference.SYSTEM, listOf("fr-FR")))
assertEquals(AppLanguage.AR, effectiveLanguage(LanguagePreference.AR, listOf("en-US")))
assertEquals(12, parseWholeDays("١٢", AppLanguage.AR))
assertEquals(12, parseWholeDays("12", AppLanguage.EN))
assertNull(parseWholeDays("١٫٥", AppLanguage.AR))
assertNull(parseWholeDays("-1", AppLanguage.EN))
// SetupJourneyTest.denied_location_retains_draft_without_creating_tracker
assertEquals("أحمد / Ahmed", reopenedDraftName)
assertEquals(0, completedTrackerCount)
```
- [ ] **Step 2: Run the tests red.** Native UI assertions must inspect visible Arabic strings/direction and saved/reopened draft behavior, not just resource-file existence.
- [ ] **Step 3: Implement the locale/theme/onboarding slice.** Follow the approved palette through Material 3 tokens, use shared parameterized/plural resources and start/end alignment, isolate numeric/bidirectional content, and persist preferences in Room. Use platform locale bridges without modifying global device language. OS-owned permission/account dialogs follow platform language; ship both native usage-description localizations and keep all app-owned rationale/widget/notification copy localized. Setup records the fixed IANA zone and next eligible automatic start; no pre-installation absence backfill.
- [ ] **Step 4: Verify setup in EN/LTR and AR/RTL, light/dark, large text, restart, denied location, and skipped Google.** Use Navigation 3's serializable shared routes and current entry decorators; no Android reflection-only route serialization. Check system/explicit locale switching and correct Arabic glyph shaping on iOS.
- [ ] **Step 5: Commit.** `feat: add bilingual themed onboarding and shared app composition`.

### Task 10: Today, History, foreground check, and manual corrections

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/ui/TodayScreen.kt`, `TodayViewModel.kt`, `HistoryScreen.kt`, `HistoryViewModel.kt`.
- Test: `shared/src/commonTest/kotlin/dev/daymark/attendance/TodayViewModelTest.kt`; `androidApp/src/androidTest/kotlin/dev/daymark/android/AttendanceJourneyTest.kt`; `iosApp/DaymarkUITests/AttendanceJourneyTests.swift`.
- Modify: app navigation/graph and shared EN/AR resources.

**Interfaces:**
- Consumes: `AttendanceRepository.observeDay`, `AttendanceService.manualCheck`, `correct(Correction)`, `DisplayFormats`, and setup state.
- Produces: `TodayViewModel.uiState: StateFlow<TodayUiState>` and `checkAttendance()`; `HistoryViewModel.uiState: StateFlow<HistoryUiState>`, `selectMonth(year: Int, month: Int)`, and `saveCorrection(command: Correction)`. `TodayUiState` includes `outcome: DayOutcome?`, `busy: Boolean`, `canCheck: Boolean`, current date, permission/error state, formatted arrival/deadline, and source label; history state exposes monthly counts and visible records.

- [ ] **Step 1: Write controller/journey assertions.** At 09:02 show a check action and 09:05 deadline; successful fresh fix → Present; at 09:18 → Late; outside/unavailable → no successful record. Before 09:00 the action is disabled. Double tap calls `manualCheck()` once while busy (RF2). Advance the foreground clock past the deadline and observe one Absent reconciliation without another GPS request.

```kotlin
// TodayViewModelTest.duplicate_taps_share_one_check
assertTrue(duringCheck.busy)
assertEquals(1, locationRequestCount)
assertEquals(AttendanceStatus.PRESENT, requireNotNull(afterCheck.outcome).status)
// TodayViewModelTest.deadline_reconciles_without_polling_gps
assertFalse(beforeArrival.canCheck)
assertEquals(AttendanceStatus.ABSENT, requireNotNull(afterDeadline.outcome).status)
assertEquals(0, locationRequestCountDuringRollover)
// AttendanceJourneyTest.manual_late_and_mixed_direction_note_survive_reopen
assertEquals(AttendanceStatus.LATE, reopenedOutcome.status)
assertEquals("اجتماع / Meeting =1+2", reopenedNote)
```
- [ ] **Step 2: Run red.** Use the task-specific host test and both native journey suites; timing tests use a fake clock/test scheduler.
- [ ] **Step 3: Implement Today/History and correction sheets.** Observe local truth, render status with text/icons, allow all tracked months/years, and label manual corrections separately. Require a time for Present/Late correction but preserve its explicitly chosen status. Off-day records open time-off editing rather than hiding an active overlay with an attendance edit. Coalesce UI taps; stop the date/deadline ticker when inactive and never poll GPS.
- [ ] **Step 4: Verify reopen, correction persistence, offline operation, and long mixed Arabic/Latin names/notes (RF4).** Move to a different device zone and cross midnight while the app is active; refresh the current day without modifying past keys. Check monthly counts exclude future/off dates and source labels match actual saved operations.
- [ ] **Step 5: Commit.** `feat: add daily attendance history and explicit corrections`.

### Task 11: Plan, time-off editing, and profile/locale/permission settings

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/ui/PlanScreen.kt`, `PlanViewModel.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`.
- Test: `shared/src/androidHostTest/kotlin/dev/daymark/plan/PlanViewModelTest.kt` using the real temporary database; Android/iOS `PlanJourneyTest.kt`/`PlanJourneyTests.swift` and `LocaleJourneyTest.kt`/`LocaleJourneyTests.swift` in the existing native journey directories.
- Modify: shared resources/navigation, graph and native locale/permission adapters.

**Interfaces:**
- Consumes: `PlanService.previewChange/applyChange`, `TimeOffService.preview/apply/setAvailableBalance`, and settings store.
- Produces: `PlanViewModel.preview(command: PlanChange)`, `confirm(expectedRevision: Long)`, `previewTimeOff(command: TimeOffCommand)`, `confirmTimeOff(expectedRevision: Long)`; `SettingsViewModel.setLanguage(language: LanguagePreference)`, `setTheme(theme: ThemePreference)`, `setDisplayName(name: String)`, and explicit permission-setup actions. State exposes effective date, balance delta, affected outcomes, pending reminder horizon, and stale-preview/error messages.

- [ ] **Step 1: Add UI/model tests.** Toggle any weekday override; reject zero selected days; grace 0/5/120 works; invalid radius outside 100–500 rejects. Save a 09:30 future default and verify today/past still use 09:00. Edit/remove a historical vacation and display/commit the exact balance/outcome difference. A stale preview asks for a refreshed review instead of partially saving.

```kotlin
// PlanViewModelTest.schedule_change_preserves_today_and_stale_preview_cannot_commit
assertEquals(LocalDate(2026, 10, 28), preview.effectiveFrom)
assertEquals(LocalTime(9, 0), storedTodayArrival)
assertEquals(LocalTime(9, 30), projectedTomorrowArrival)
assertIs<PlanMutationResult.StalePreview>(staleApplyResult)
assertEquals(beforeStaleApplyRevision, afterStaleApplyRevision)
// LocaleJourneyTest.arabic_switch_republishes_copy_without_changing_day_identity
assertEquals(beforeDayKey, afterDayKey)
assertEquals("ar", publishedWidgetLocale)
assertTrue(pendingNotificationLocales.all { it == "ar" })
```
- [ ] **Step 2: Run red.** Verify both native locales using persisted real data rather than prototype selectors.
- [ ] **Step 3: Implement Plan and settings.** Include registration/radius updates, next-day schedule/category changes, historical/current/future time off, whole-day balance adjustment, system/EN/AR language and theme preference, and separate Android automation consent. Hide the balance for school/course. Add notification/automation health and the 30-day iOS horizon. Google connection and destructive local deletion are wired in Task 14, not simulated as successful actions here.
- [ ] **Step 4: Verify configuration side effects.** Refresh pending reminders/scheduling and widget publication hooks after effective mutations; language changes leave dates/status codes untouched and replace future notification copy (RF3). Denied/revoked permissions retain manual operation and local history.
- [ ] **Step 5: Commit.** `feat: add editable plans time off and localized settings`.

### Task 12: Native home-screen widgets and safe foreground routing

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/widgets/QuickCheckRoute.kt`, `WidgetSnapshot.kt`, `WidgetPublisher.kt`.
- Create: Android `DaymarkWidget.kt`, `DaymarkWidgetReceiver.kt`, `AndroidWidgetPublisher.kt`; native widget resources/manifest receiver declaration.
- Create: `iosApp/Daymark/WidgetSnapshotWriter.swift`; `iosApp/DaymarkWidget/DaymarkWidget.swift`, `WidgetSnapshot.swift`, `Localizable.xcstrings`, `DaymarkWidget.entitlements`; host `Daymark.entitlements` and WidgetKit extension target.
- Test: `shared/src/commonTest/kotlin/dev/daymark/widgets/QuickCheckRouteTest.kt`; `iosApp/DaymarkTests/WidgetSnapshotTests.swift`; Android `WidgetJourneyTest.kt` and iOS `WidgetJourneyTests.swift`.

**Interfaces:**
- Consumes: current day/settings, `WidgetSink`, and the production host entry.
- Produces: `QuickCheckRoute(schemaVersion: Int = 1, trackerId: String)`, `encodeQuickCheckRoute(route: QuickCheckRoute): String`, and `parseQuickCheckRoute(raw: String): QuickCheckRoute?`; use `daymark://quick-check?v=1&trackerId=<UUID>` and reject malformed/unknown versions. `WidgetSnapshot(schemaVersion: Int = 1, trackerId: String, generatedAtEpochMillis: Long, attendanceDate: String, effectiveLocale: String, displayStatus: String, statusCode: String, placeLabel: String, deadlineDisplay: String?, deadlineEpochMillis: Long?, zoneId: String, quickCheckRoute: String)`.
- Produces: `WidgetPublisher.refresh(): Unit` and `clear(): Unit` as suspend operations; native sinks implement the Task-9 Boolean-result contract. The status code/epoch/zone supplement the v2 display snapshot to support native formatting without a DB/token.

- [ ] **Step 1: Add route/snapshot tests.** Missing/invalid tracker, invalid JSON/version, deleted tracker, yesterday's snapshot, and duplicate opens fall back to current Today/setup or coalesce the normal action; none directly writes attendance. Assert snapshots contain no token, DB path, registered coordinates, or raw fix. Native EN/AR decoding preserves dates (RF3).

```kotlin
// QuickCheckRouteTest.rejects_untrusted_route_shape
assertNull(parseQuickCheckRoute("daymark://quick-check?v=2&trackerId=$trackerId"))
assertNull(parseQuickCheckRoute("daymark://quick-check?v=1&trackerId=bad"))
assertEquals(route, parseQuickCheckRoute(encodeQuickCheckRoute(route)))
// WidgetJourneyTest.stale_snapshot_resolves_today_before_running_normal_check
assertEquals(LocalDate(2026, 10, 28), resolvedDayKey.date)
assertEquals(0, directAttendanceWritesFromRoute)
assertFalse(snapshotContainsCredentialsOrCoordinates)
```
- [ ] **Step 2: Run red.** Use shared host tests and Swift Codable round trips, then native widget journeys.
- [ ] **Step 3: Implement Glance and WidgetKit.** Publish small JSON snapshots atomically; iOS uses the provisioned App Group and native strings. Show a last-updated cue, place/deadline/status and one foreground quick-check action. Resolve current tracker/date and early-check/permission rules in the host; a route is not confirmation evidence. Retire the Task-2 development widget target once its production replacement passes.
- [ ] **Step 4: Verify on both physical platforms.** Tap from home screen, check Present/Late/off/denied states, switch EN/AR and light/dark, recreate the widget from stale data, and clear it on tracker deletion. Missing App Group signing/provisioning remains an explicit incomplete gate.
- [ ] **Step 5: Commit.** `feat: add localized native attendance widgets`.

### Task 13: Report projections, durable batches, and ambiguous-write reconciliation

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/reporting/ReportModels.kt`, `ReportProjection.kt`, `ReportStore.kt`, `SyncCoordinator.kt`, `SyncRetryPolicy.kt`.
- Create: `shared/src/commonMain/kotlin/dev/daymark/reporting/RoomReportStore.kt`.
- Modify: `GoogleReportTransport.kt`, `ReportWireModels.kt`, `ReportEntities.kt`, `DaymarkDao.kt`, and exported schemas.
- Test: `shared/src/commonTest/kotlin/dev/daymark/reporting/ReportProjectionTest.kt`, `SyncCoordinatorTest.kt`, `SyncRetryPolicyTest.kt`; `shared/src/androidHostTest/kotlin/dev/daymark/reporting/ReportJournalTest.kt`.

**Interfaces:**
- Consumes: authoritative day/leave/profile state, dirty revisions, Task-3 transport and native token access.
- Produces: `ReportBinding(trackerId: String, connectionGeneration: String, accountReference: String, spreadsheetId: String, schemaVersion: Int = 1, annualTabIds: Map<Int, Int>)`; `ProjectionKey` (`Day(key: DayKey)/TimeOff/Overview`); `ReportProjection(key: ProjectionKey, revision: Long, ranges: List<RawRange>)`; `ReportBatch(batchIdentity: String, binding: ReportBinding, projections: List<ReportProjection>)`.
- Produces: interface `ReportStore` and concrete `RoomReportStore` with suspend `activeBinding(): ReportBinding?`, `bind(binding: ReportBinding): Unit`, `buildBatch(binding: ReportBinding, maxRows: Int): ReportBatch?`, `journalBeforeDispatch(batch: ReportBatch): Unit`, `markDispatched(batch: ReportBatch): Unit`, `acknowledge(batch: ReportBatch): Unit`, `markAmbiguous(batch: ReportBatch): Unit`, and `invalidateBinding(): Unit`. It also exposes suspend `localProjections(binding: ReportBinding): List<ReportProjection>` and `markDirty(keys: Set<ProjectionKey>): Unit` for full-session read/repair. Journal status is `PREPARED/DISPATCHED/AMBIGUOUS/ACKNOWLEDGED`; mark dispatched durably before the network call, and treat unacknowledged in-flight work as ambiguous after restart.
- Extend Task-3 transport with suspend `prepareTemplate(report: RemoteReport, trackerId: String, zoneId: String, years: Set<Int>, token: String): RemoteReport`, validating/creating only app-owned tabs and returning their stable IDs. Annual titles are `Attendance YYYY`. Define schema-1 machine columns A:O as `trackerId,date,category,scheduledArrival,graceDeadline,status,statusLabel,confirmedArrival,source,note,updatedAt,revision,offReason,attendanceZoneId,confirmedAtUtc`; row 2 has bilingual human labels. Arrival/deadline display fields are local ISO time strings with the row's zone; UTC instants remain canonical where indicated.
- Produces: `SyncCoordinator.runPass(trigger: SyncTrigger): SyncPassResult`; trigger is `LAUNCH/RESUME/LOCAL_CHANGE/CONNECTIVITY/RECONNECT/RETRY/ANDROID_WORK`; result is `Idle/Synced/Pending/AuthorizationRequired/SchemaConflict/ReportUnavailable/StaleBinding`. `SyncRetryPolicy.nextDelay(attempt: Int, retryAfter: Duration?): Duration?` uses 2-second exponential base, 60-second base cap, jitter 0–1 second, at most six transient dispatch attempts per active session, and honors a longer server Retry-After.

- [ ] **Step 1: Add projection/journal assertions.** Annual row = `dayOfYear + 2` including 29 February; bilingual headings and stable status/source codes; optional-field removal sends empty strings, not null/skipped cells; `=1+2` stays literal; time-off removal clears old report cells and updates Overview. Duplicate writes target one date row (RF4).

```kotlin
// ReportProjectionTest.leap_day_row_and_explicit_optional_clearing
// Arrange 29 Feb 2028, one projection row; note column J is zero-based 9.
assertEquals("'Attendance 2028'!A62:O62", row.a1)
assertEquals(15, row.cells.single().size)
assertEquals(JsonPrimitive("=1+2"), withNote.cells.single()[9])
assertEquals(JsonPrimitive(""), withoutNote.cells.single()[9])
assertTrue(clearedTimeOffCells.all { it == JsonPrimitive("") })
```
- [ ] **Step 2: Add failure-order tests and run red.** Write N becomes ambiguous; write N+1 is acknowledged; fake server later applies N; a new session reads even clean rows and repairs N+1. A correction during upload remains dirty; process death after dispatch retains uncertainty; generation A callbacks cannot acknowledge generation B (RF5). Use MockEngine plus a stateful fake server and real journal reopen tests, not only canned HTTP responses.

```kotlin
// SyncCoordinatorTest.later_session_repairs_old_write_after_new_ack
assertEquals(7L, remoteRevisionAfterOldWrite)
assertEquals(8L, remoteRevisionAfterNextSession)
assertEquals(8L, localRevision)
// SyncCoordinatorTest.obsolete_callback_cannot_clean_new_binding
assertEquals("generation-B", currentBindingGeneration)
assertTrue(newBindingProjectionStillDirty)
// SyncRetryPolicyTest.retry_is_bounded_and_honors_server_delay
assertNull(policy.nextDelay(attempt = 6, retryAfter = null))
assertTrue(requireNotNull(policy.nextDelay(0, 120.seconds)) >= 120.seconds)
```
- [ ] **Step 3: Implement complete projections and schema validation.** Create Overview, annual Attendance, and Time off tabs with app-owned tracker/schema metadata. Use full `RAW` matrices with explicit blank cells for clearing old optional/trailing values; formulas are app-authored template operations. Reconcile managed tab identity/date/revisions before writing. Structural damage returns `SchemaConflict`; unrelated tabs are untouched. Cap one dispatch batch below 2 MB and split by complete projection/date rows.
- [ ] **Step 4: Implement a serialized drain with durable uncertainty.** Journal before dispatch, read the latest local projections for retry, conditionally acknowledge only matching current revisions/generation, and read/repair previously acknowledged rows at new foreground/reconnect sessions. Do not claim remote compare-and-swap, monotonic reads, or immediate remote cancellation. Persist retry timing/pending errors; stop on auth/schema/unavailable states instead of looping.
- [ ] **Step 5: Run all reporting tests green and inject the ambiguity sequence across process reopen.** Verify six-attempt bounded backoff, long-history quota batching, no GPS calls, no paid quota escalation, and recovery to the latest local projection after outstanding writes settle. No record is lost if a journal/write fails.
- [ ] **Step 6: Commit.** `feat: publish and reconcile durable app-managed attendance reports`.

### Task 14: Reporting lifecycle, connection UI, account changes, and local deletion

**Files:**
- Create: `shared/src/commonMain/kotlin/dev/daymark/app/AppLifecycleCoordinator.kt`, `DataLifecycleService.kt`; `shared/src/commonMain/kotlin/dev/daymark/reporting/ConnectionService.kt`; `shared/src/commonMain/kotlin/dev/daymark/ui/GoogleConnectionSection.kt`; Android `ReportSyncWorker.kt`.
- Modify: AppGraph, native lifecycle callbacks, SettingsViewModel/Screen, scheduling/widget hooks and native Google adapters.
- Test: `shared/src/commonTest/kotlin/dev/daymark/reporting/ConnectionServiceTest.kt`; `shared/src/androidHostTest/kotlin/dev/daymark/app/AppLifecycleCoordinatorTest.kt`, `DataLifecycleTest.kt` with the real temporary database; Android/iOS `GoogleConnectionJourneyTest.kt`/`GoogleConnectionJourneyTests.swift`.

**Interfaces:**
- Produces: suspend `ConnectionService.connect(): ConnectionResult`, `reconnect(): ConnectionResult`, `selectReport(reportId: String): ConnectionResult`, `rebuildReport(): ConnectionResult`, `createReplacementReport(): ConnectionResult`, and `disconnect(): Unit`; native access is interactive only through user actions. Connection results are `Connected/PendingCreation/SelectionRequired/Cancelled/AuthorizationRequired/Failed`, carrying the binding or offered choices/error key where relevant.
- Produces: `AppLifecycleCoordinator.onActive(active: Boolean): Unit`, `onLocalChange(): Unit`, and `retryReport(): Unit`; it owns foreground jobs, not the database. Android network work invokes `runPass(ANDROID_WORK)` separately.
- Produces: `DataLifecycleService.deleteLocalData(): DeletionResult` (`Deleted/Failed`), called only after the UI confirmation. It invalidates the connection generation, stops external work, clears authoritative local data transactionally, forgets native credentials, clears widget snapshots, and returns to setup.
- Extend the Task-9 graph/composition roots with `connection: ConnectionService`, `sync: SyncCoordinator`, `lifecycle: AppLifecycleCoordinator`, and `dataLifecycle: DataLifecycleService`; update every native host and test factory in this task. These are explicit additions to the constructor, not a second incompatible graph API.

- [ ] **Step 1: Add lifecycle/binding assertions and run red.** Closing iOS during a dispatched upload preserves an ambiguous journal; reopening automatically drains/verifies it. Non-interactive token refresh cannot show account UI. Disconnect invalidates the generation before cancellation; late callbacks cannot mutate/acknowledge a new connection. Reconnect to account B does not overwrite account A's report (RF5).

```kotlin
// AppLifecycleCoordinatorTest.resume_recovers_interrupted_upload_without_upload_tap
assertTrue(journalNeedsReconciliationAfterReopen)
assertEquals(0, foregroundDispatchesWhileInactive)
assertEquals(latestLocalRevision, remoteRevisionAfterResume)
assertEquals(0, consentLaunchesFromResume)
// ConnectionServiceTest.account_switch_preserves_previous_report
assertNotEquals(accountAReportId, accountBReportId)
assertEquals(0, writesToAccountAAfterSwitch)
assertEquals(0, googleDeleteRequests)
```
- [ ] **Step 2: Implement connection/discovery and health UI.** Confirm the account reference using the same narrow scope, discover/create only the current tracker's report, persist each new binding generation, and enqueue all local projections. Uncertain creation stays pending and is searched later rather than blindly repeated. Surface last sync/pending count/error/Retry and explicit rebuild/new-report choices; no incoming record import. Preserve old reports on disconnect/reinstall.
- [ ] **Step 3: Wire execution ownership.** Launch/resume, local commits while active, reconnect, active network recovery, and Retry request foreground drains. Backgrounding cancels only the foreground-owned operation and leaves durable uncertainty. Android additionally enqueues separate network-constrained report work; this worker never invokes location. Do not introduce iOS background location/refresh modes to hide pending uploads.
- [ ] **Step 4: Implement/test confirmed local deletion.** Assert cancellation of attendance/reminders/report work, invalidated generations, no restoration by stale callbacks, empty local records and widget/setup state, and zero Google delete requests. An unconfirmed dialog does nothing; a database failure reports failure and never displays “deleted” after a partial/noncommitted clear. Keep the schema and preserve existing remote reports.
- [ ] **Step 5: Verify live EN/AR connection journeys on both platforms.** Connect, cancel consent, go offline, edit, close/reopen iOS, reconnect/revoke, change account, retry a damaged/missing report, and disconnect. The UI must distinguish saved locally/pending upload from observed sync. Complete any previously blocked Task-3 live probe before marking this task done.
- [ ] **Step 6: Commit.** `feat: integrate lifecycle-safe reporting and local data controls`.

### Task 15: Complete acceptance, non-destructive upgrade checks, and release handoff

**Files:**
- Create: `docs/development/release-verification.md`; `iosApp/Daymark/PrivacyInfo.xcprivacy`; Android `data_extraction_rules.xml`/`backup_rules.xml` as required by the selected platform behavior.
- Test: `androidApp/src/androidTest/kotlin/dev/daymark/android/UpgradeJourneyTest.kt`, `LocaleMatrixJourneyTest.kt`; `iosApp/DaymarkUITests/LocaleMatrixJourneyTests.swift`; `shared/src/androidHostTest/kotlin/dev/daymark/data/MigrationSafetyTest.kt`.
- Modify: final native privacy/permission metadata and provisioning/toolchain/feasibility documentation; lock/schema files only when an audited correction requires them.

**Interfaces:**
- Consumes: all prior task interfaces and the complete v1 section 13/v2 milestone gates.
- Produces: a local release-candidate verification record with actual commands/device/OS/app versions, observed limitations, evidence locations, missing prerequisites, and an explicit pass/fail per acceptance gate. This is not a publication step.

- [ ] **Step 1: Add the remaining upgrade/locale regression tests and run red.** Seed representative records/leave/journal in an older schema fixture, upgrade without loss, and reject an incompatible downgrade rather than recreating the database. Exercise EN/AR × light/dark × Android/iOS, 200% text, mixed-direction names, Arabic numeral entry, and native widget/notification copy. Never fabricate an older shipped version; use an explicitly test-only prior-schema fixture if no migration has occurred.

```kotlin
// MigrationSafetyTest.upgrade_preserves_records_and_downgrade_refuses_to_erase
assertEquals(seedRecords, upgradedRecords)
assertEquals(seedBalance, upgradedBalance)
assertEquals(seedPendingRevisions, upgradedPendingRevisions)
assertTrue(downgradeOpenFailed)
assertEquals(seedRecords, recordsAfterReopeningWithCurrentVersion)
// LocaleMatrixJourneyTest.all_eight_platform_language_theme_combinations
assertEquals(8, verifiedConfigurations.size)
assertTrue(verifiedConfigurations.all { it.noClipping && it.coreActionsReachable })
```
- [ ] **Step 2: Run the complete automated gate.** `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug`, Android connected journeys, and iOS `xcodebuild ... test`. Expected: exit 0 and executed tests with no skipped core suite. Source-only checks, zero-test runs, simulator location, and the HTML concept cannot substitute for native device results.
- [ ] **Step 3: Perform physical release-build journeys.** Use Android including a non-Google manufacturer, battery saver/Doze/reboot, and a signed iPhone with the real App Group. Verify reminder horizon, foreground sync after suspension, denied/revoked permissions, claims after process death, absence/manual Late, historical leave correction, account change, and pending/error recovery. Present measured Android missed-check behavior for the product decision required by v2 before release.
- [ ] **Step 4: Verify privacy/cost/provisioning boundaries.** Review license and transitive advisory results, merged permissions, token/position/note-free logs, appropriate backup exclusions for credentials/widget state, and documented local-data loss/reinstall behavior. Confirm the final identifiers/accounts/store privacy disclosures and privacy-policy hosting as release inputs; do not enroll, enable billing, upload to stores, or publish.
- [ ] **Step 5: Record rollback and handoff evidence.** A failing integration pauses its side effects and preserves local records/dirty revisions. Prefer a forward-compatible fix; never downgrade a newer database destructively. Missing physical hardware, signing, OAuth, or a failed native feasibility result remains an explicit incomplete gate. Request design review if meeting it needs changed permissions/workflow/support floors.
- [ ] **Step 6: Commit the verified candidate and evidence.** `test: verify bilingual cross-platform attendance release candidate`. Run the execution method's required final independent review, resolve actionable findings, and report precisely which native/release gates passed. Distribution still needs a separate user instruction.

---

## Self-review and coverage map

This plan was checked against all fourteen incorporated v1 sections and all six v2 sections without dispatching another reviewer. Each row states the owning tasks; every task inherits Global Constraints.

| Requirement group | Owning tasks |
| --- | --- |
| Compatible hosts, stable/free dependencies, native risk probes | 1–3, 15 |
| Status/grace/freshness/zone/DST/early-check rules | 2, 4, 5, 10 |
| Durable records, claims/process death, historical snapshots | 5–7, 15 |
| Retrospective/current/future time off and balance differences | 6, 11, 13 |
| Android one-attempt scheduling/permissions/recovery | 2, 7, 15 |
| iOS foreground location, 30-day reminders, native entry | 2, 8, 12, 15 |
| Approved UI/setup/history/plan/settings, EN/AR/RTL | 9–12, 14–15 |
| Optional OAuth, account identity, schema/template/literal values | 3, 13–14 |
| Journaling, dirty revisions, ambiguous/older-write repair | 5, 13–14 |
| Foreground iOS publishing and separate Android report worker | 14 |
| Confirmed deletion, reinstall isolation, privacy and rollback | 5, 12, 14–15 |
| RF1 / RF2 / RF3 / RF4 / RF5 | 2/4/5 · 5/6/10 · 4/7/8/11/12 · 9/10/13 · 3/13/14 |

No native implementation tests have run while writing this plan. The self-review checked task ownership, the exported interface names/types used by later tasks, exact pinned business values, per-task red/green or compile/run verification, and scope. Private algorithms and routine UI bodies remain with the implementer; none of the code blocks above implements the application.

## Execution handoff

Review this plan before implementation and choose an execution method:

1. **Subagent-driven — Recommended.** A fresh implementer and reviewer handle each task, followed by whole-branch review. These 15 tasks cross native permission, Swift/Kotlin, transaction, and synchronization boundaries; checking each deliverable before later tasks depend on it reduces rework. This uses more fresh contexts.
2. **Native.** One implementer executes the plan in this chat, followed by a fresh whole-branch reviewer on the most capable available model. This is cheaper/faster but has no independent review between tasks.

On approval of the plan and method, invoke the matching required execution skill and establish any necessary isolated worktree at execution time. Do not infer an execution choice from the user's approval of the design or earlier delegation of a design review.
