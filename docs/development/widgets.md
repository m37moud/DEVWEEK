# Native attendance widgets

The shared publisher creates a small, display-only JSON snapshot from local Room truth. It includes the saved attendance-local date, public place label, localized status/deadline, update time, theme and strict quick-check route. It contains no credential, database path, registered coordinates or raw fix. Native writers decode and re-encode recognized fields before atomic publication, stripping unknown input. Very long place labels are shortened for display only.

Routes use `daymark://quick-check?v=1&trackerId=<UUID>`. Unknown versions, malformed shapes and missing/deleted trackers navigate to live Today/setup without starting a check. A valid route carries no attendance date or evidence: the foreground host reconciles the current day, verifies tracker identity and invokes the ordinary manual service. Android widget check-in now uses the bounded service described below instead of launching that host; external quick-check links and iOS keep their existing foreground routes. Early taps are consumed immediately rather than queued until arrival. Repeated opens coalesce while busy. Permission, off-day, grace, storage and concurrent-edit rules remain authoritative.

Android uses Glance 1.2 and an AtomicFile in credential-protected `noBackupFilesDir`. Its cache changes feed the active Glance session; application/scheduled-check recovery requests widget rendering. There is no periodic widget job or extra location request. The visible status/update cue describes the cached record; a rendered widget can remain old until the app or OS updates it. Tapping always resolves current truth. System uses Glance day/night color providers; explicit light/dark preferences are in the snapshot.

WidgetKit uses native timeline entries at arrival, the first millisecond after the inclusive deadline, and attendance-zone midnight. A snapshot from another date cannot appear as today's confirmation. An untracked setup baseline cannot become a widget-derived absence. Missing data uses a nonchecking app-open action; empty timelines do not repeatedly poll. The extension has no database, token or location access.

## iOS configuration and open gates

The production `DaymarkWidget` target is embedded by the host. Both targets need the same provisioned App Group. Copy `iosApp/Daymark/Configuration.example.xcconfig` to the ignored `Configuration.xcconfig` and supply registered group/widget identifiers and signing configuration. The checked-in defaults are development identifiers, not proof of provisioning. No developer enrollment, paid account, signing-profile creation or live registration was performed.

The native writer uses a dedicated App Group subdirectory with complete file protection and backup exclusion established before writing. It returns failure when the group is unavailable; it never falls back to an unprotected container. WidgetKit receives a reload request after publish/clear, but the OS controls visible refresh timing. The view is marked privacy-sensitive. The old standalone development probe target remains unembedded until the production widget passes native validation.

The earlier iOS build deferral has been followed by successful shared framework linking, native Swift bridge typechecking, Foundation codec checks, an arm64 WidgetKit build and a full unsigned iOS simulator host build with embedded-widget validation. Both simulator runtimes were retained. App Group entitlement access at runtime, visual rendering, physical GPS, reminder delivery and iPhone widget taps remain open. No physical iPhone is connected.

Android registered the production provider on the connected Huawei API-28 phone. Its widget gallery did not advance through ADB, so no widget was added and existing icons/widgets were unchanged. The user explicitly chose to continue development and keep launcher pin/tap verification open. The app/cache handoff journey is distinct from that physical launcher check. Newer Android background-permission/device behavior remains a separate native gate.

## Repeating local checks

`tools/check-widget-codec.sh` compiles only the Foundation snapshot model and fixtures into a temporary macOS executable, runs EN/AR round trips, invalid-schema/status/date checks, stale-date behavior and native string-format checks, then removes its own compiler output. It does not build an iOS app or WidgetKit UI.

Shared tests validate route shape, explicit schema versions, native localization parity, lifecycle/duplicate/early/stale-tracker handling and real-database publication/failure/deletion behavior. Android's `WidgetJourneyTest` uses a temporary database and native atomic cache to resolve yesterday's route against today's date, checks one manual acquisition, changes persisted locale without changing identity, rejects invalid cache replacement, strips unknown fields, clears the cache and tests empty-cache navigation.

On the disk-constrained development machine, separate compilation from test execution and use single-use Gradle processes. Some combined runs encountered SQLite shared-memory file I/O errors; the full isolated JVM rerun passed without changing persistence logic. Keep test logs and external gates separate from success claims.

Reference APIs: [Glance activity actions](https://developer.android.com/develop/ui/compose/glance/user-interaction), [Glance state and updates](https://developer.android.com/develop/ui/compose/glance/glance-app-widget). The Android CLI's official documentation search/fetch was also used for widget integration.

## Compact widget presentation amendment — 10 November 2026

The user approved a minimal Android 3×1 strip and iOS Small square, retaining existing larger families. These replace the place/deadline/branding rows with status, last-update time and one action. The frozen design remains unchanged; this amendment supersedes only its widget presentation requirements.

Before arrival the check-in control is disabled, with no quick-check URL or queued action. Ready/Absent offers foreground check-in; Present/Late/off days offer a quiet app-open action. Missing/stale/untracked snapshots open Today/setup without checking. The snapshot stays version 1, retaining its fields. Attendance-zone dates and the inclusive deadline remain authoritative. Android remains best-effort with no new background job; it may continue showing a disabled control until refreshed. iOS uses its existing boundary timelines. Larger text may require a taller Android placement.

## Gallery and setup-state correction — 10 November 2026

The approved image is the visual reference: native outlined status icons, the location-check action, restrained border/spacing, and the iOS Today label. Android uses a 3×1 launcher target with a 250×80dp minimum content size to retain readable wrapped text; launcher grids can map that differently. Its disabled action is a noninteractive native view with no click action.

Settings now contains only setup/status guidance. WidgetSetupPort queries configured native widgets through SettingsViewModel on entry/resume and Android pin confirmation. A request is not addition evidence. Native configuration survives navigation/restarts; removal restores setup guidance. Pending, checking, unknown and unsupported states remain separate. Query callbacks are generation-fenced, and the Android confirmation receiver is released with its ViewModel. No Room field or schema migration is needed.

Android 15+ generated picker previews use the actual Glance content with a nonpersonal fixture; older devices use a native-rendered image fallback. iOS gallery snapshots use the actual WidgetKit view with canonical Gregorian sample dates and nonpersonal content. Gallery rendering never checks location, reads credentials or writes attendance. System-owned app icon/name headers can remain visible beside the preview. Preview content stays separate from authoritative snapshots.

## Android launcher check-in amendment — 14 November 2026

The user approved check-in without opening the app UI. This supersedes the earlier Android disabled-before-arrival and foreground-activity requirements only; iOS is unchanged. Scheduled Android controls remain tappable with subdued styling. A cached pre-arrival render therefore cannot prevent a valid later check. Provider periodic updates remain disabled.

The explicit, non-exported location foreground service starts directly from Glance's foreground-service action. It promotes immediately with a localized, cancellable progress notification, then uses the existing bounded 30-second location request and manual-check mutex. Widget tracker identity is validated against the authoritative loaded day inside that lock before GPS. The native adapter also rejects unstarted tracking. Early taps return start-time guidance with no GPS or queued retry. Current saved-zone boundaries, manual correction precedence, off days and transactional commit fences still apply.

Transient Android-only widget feedback exposes progress and failures without changing the version-1 snapshot or Room schema. Missing/approximate permission and disabled location offer an explicit Open Daymark recovery action; location failures offer explicit retry. Nothing automatically launches an activity or requests permission. Existing completed/off-day and setup actions remain clearly labeled app-open actions. Notification permission is not requested by the service; the OS controls whether foreground-service information appears in the notification drawer or its task UI.

Commands and service cleanup are serialized on Main. A cancelling check retains ownership until cleanup finishes; taps during that interval do not queue another check. Confirmed local deletion blocks new service checks and awaits cancellation/completion before clearing the widget. Service execution is not sticky, and neither process death nor cancellation schedules a retry. A widget publication failure cannot replace an acknowledged attendance result with a failed-check result.

Verification for this correction is recorded in `artifacts/widget-service/verification.md`. A native API-28 tap-to-service test does not qualify physical GPS, modern Android foreground-service exemptions, launcher placement, or manufacturer-specific behavior.
