# Native attendance widgets

The shared publisher creates a small, display-only JSON snapshot from local Room truth. It includes the saved attendance-local date, public place label, localized status/deadline, update time, theme and strict quick-check route. It contains no credential, database path, registered coordinates or raw fix. Native writers decode and re-encode recognized fields before atomic publication, stripping unknown input. Very long place labels are shortened for display only.

Routes use `daymark://quick-check?v=1&trackerId=<UUID>`. Unknown versions, malformed shapes and missing/deleted trackers navigate to live Today/setup without starting a check. A valid route carries no attendance date or evidence: the started foreground host reconciles the current day, verifies tracker identity and invokes the ordinary manual service. Early taps are consumed immediately rather than queued until arrival. Repeated opens coalesce while busy. Permission, off-day, grace, storage and concurrent-edit rules remain authoritative.

Android uses Glance 1.2 and an AtomicFile in credential-protected `noBackupFilesDir`. Its cache changes feed the active Glance session; application/scheduled-check recovery requests widget rendering. There is no periodic widget job or extra location request. The visible date/deadline/update cue describes the cached record; a rendered widget can remain old until the app or OS updates it. Tapping always resolves current truth. System uses Glance day/night color providers; explicit light/dark preferences are in the snapshot.

WidgetKit uses native timeline entries at arrival, the first millisecond after the inclusive deadline, and attendance-zone midnight. A snapshot from another date cannot appear as today's confirmation. An untracked setup baseline cannot become a widget-derived absence. Missing data uses a nonchecking app-open action; empty timelines do not repeatedly poll. The extension has no database, token or location access.

## iOS configuration and open gates

The production `DaymarkWidget` target is embedded by the host. Both targets need the same provisioned App Group. Copy `iosApp/Daymark/Configuration.example.xcconfig` to the ignored `Configuration.xcconfig` and supply registered group/widget identifiers and signing configuration. The checked-in defaults are development identifiers, not proof of provisioning. No developer enrollment, paid account, signing-profile creation or live registration was performed.

The native writer uses a dedicated App Group subdirectory with complete file protection and backup exclusion established before writing. It returns failure when the group is unavailable; it never falls back to an unprotected container. WidgetKit receives a reload request after publish/clear, but the OS controls visible refresh timing. The view is marked privacy-sensitive. The old standalone development probe target remains unembedded until the production widget passes native validation.

The user elected to keep both simulator runtimes and defer iOS host/UI builds. Only Kotlin source compilation, Swift syntax, Foundation codec checks on macOS and Xcode project metadata have been checked. Native Swift typechecking/linkage, App Group entitlement access, visual rendering, physical GPS, reminder delivery and iPhone widget taps remain open. No physical iPhone is connected.

Android registered the production provider on the connected Huawei API-28 phone. Its widget gallery did not advance through ADB, so no widget was added and existing icons/widgets were unchanged. The user explicitly chose to continue development and keep launcher pin/tap verification open. The app/cache handoff journey is distinct from that physical launcher check. Newer Android background-permission/device behavior remains a separate native gate.

## Repeating local checks

`tools/check-widget-codec.sh` compiles only the Foundation snapshot model and fixtures into a temporary macOS executable, runs EN/AR round trips, invalid-schema/status/date checks, stale-date behavior and native string-format checks, then removes its own compiler output. It does not build an iOS app or WidgetKit UI.

Shared tests validate route shape, explicit schema versions, native localization parity, lifecycle/duplicate/early/stale-tracker handling and real-database publication/failure/deletion behavior. Android's `WidgetJourneyTest` uses a temporary database and native atomic cache to resolve yesterday's route against today's date, checks one manual acquisition, changes persisted locale without changing identity, rejects invalid cache replacement, strips unknown fields, clears the cache and tests empty-cache navigation.

On the disk-constrained development machine, separate compilation from test execution and use single-use Gradle processes. Some combined runs encountered SQLite shared-memory file I/O errors; the full isolated JVM rerun passed without changing persistence logic. Keep test logs and external gates separate from success claims.

Reference APIs: [Glance activity actions](https://developer.android.com/develop/ui/compose/glance/user-interaction), [Glance state and updates](https://developer.android.com/develop/ui/compose/glance/glance-app-widget). The Android CLI's official documentation search/fetch was also used for widget integration.
