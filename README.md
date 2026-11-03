# MyWorkTracker — Daymark

A personal attendance tracker for work, school, and courses, planned for Android and iOS using Kotlin and Compose Multiplatform. **Daymark** is the working product name; the repository is named `MyWorkTracker`.

## Project status

The current source is a Compose Multiplatform starter with a demo screen and sample tests. Attendance tracking, persistence, location checks, reminders, widgets, Google authorization, and reporting are **not implemented yet**.

The [reviewed v2 design](docs/superpowers/reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md) was approved for implementation planning on 27 October 2026. The [implementation plan](docs/superpowers/plans/2026-10-27-attendance-mvp.md) is written for review; execution and native feasibility verification remain pending. Desktop/JVM is available in the starter, but the attendance MVP targets Android and iOS.

## Planned MVP

Each installation supports one personal tracker, one registered place, one weekly schedule, and one phone at a time.

- **Setup:** display name, Work/School/Course category, current-location place registration, attendance time zone, selected weekdays, default arrival time, weekday overrides, and grace period.
- **Daily attendance:** Today status, monthly Present/Late/Absent totals, history calendar/list, and manual corrections with an explicit source label.
- **Time off:** weekly days off and editable holidays/vacations for past, current, and future tracked dates. Work trackers have a whole-day vacation balance; future vacation reserves balance, while holidays and weekly days off consume none.
- **Local records:** attendance works offline without an account. Optional Google connection creates an app-managed spreadsheet and queues outgoing updates.
- **Appearance and language:** modern light/dark themes with System overrides, English and Arabic, full RTL layouts, and localized native notifications/widgets.

### Platform behavior

| Platform | Planned attendance flow |
| --- | --- |
| Android | One best-effort automatic location attempt per scheduled day during the arrival-to-deadline window, subject to permissions and system restrictions. Foreground quick check remains available. |
| iOS | An arrival-time reminder and a foreground location check opened from the app, notification, or home-screen widget. No automatic background location check. |
| Both | Widgets open the app's normal quick-check flow, which requires a fresh location confirmation inside the registered area. A widget tap alone does not record attendance. |

Background execution and a timely GPS fix are not guaranteed. Android automation uses WorkManager; a delayed attempt after the deadline skips GPS. iOS reminders cover the next 30 calendar days and refresh when the app opens or relevant settings change.

### Attendance rules

- The default grace period is five minutes. For a 09:00 arrival, a valid confirmation from 09:00 through **09:05:00 inclusive** is Present; a later user-initiated confirmation that day is Late.
- Normal location checks are disabled before arrival. With no successful confirmation by the deadline, a scheduled day becomes Absent when next evaluated. A failed location request does not establish attendance.
- The default allowed radius is 100 m, adjustable from 100–500 m. A location request has a 30-second budget; successful fixes must be no more than 30 seconds old and report accuracy of 50 m or better.
- Holidays, weekly days off, and vacations skip attendance attempts. Explicit time-off changes preserve the underlying attendance so removing time off can restore it.
- Manual corrections are personal declarations, distinct from GPS checks. Schedule/place/category changes take effect the next attendance-zone day and preserve historical expectations.

The MVP excludes multiple trackers/places, check-out and worked hours, payroll, employer/teacher administration, continuous tracking/geofencing, overnight shifts, spreadsheet import, simultaneous multi-device sync, and full backup/restore.

## Data and Google Sheets

The planned local Room database is authoritative. Google Sheets is an optional outgoing report; direct spreadsheet edits are not imported and may be overwritten.

- Native Google authorization uses the narrow `drive.file` scope to create an Overview tab, annual attendance tabs, and a Time off tab with bilingual labels.
- Deterministic date rows, durable pending revisions, and later reconciliation keep retries from appending duplicate attendance rows and repair uncertain writes from local records.
- Android uses separate network-constrained sync work. iOS uploads at foreground opportunities; uploads can remain pending until the app is reopened.
- Disconnecting or deleting local data preserves the existing Google report. Sheets is not a restore mechanism; reinstalling can lose local records.
- Registered-place coordinates stay local. Raw check coordinates are discarded after evaluation and are not uploaded. Credentials, precise positions, and personal notes must not appear in logs.

The design uses free redistributable libraries and native frameworks, with no required account, subscription, custom backend, or advertising SDK. API quotas and store-distribution prerequisites still need validation before release.

## Architecture and repository layout

The planned app shares Compose UI, lifecycle ViewModels/StateFlow, attendance/calendar rules, Room persistence, and Ktor reporting. Thin native hosts own location permissions, scheduling, Google authorization, and widgets. Constructor injection from a small composition root keeps platform APIs outside shared business rules.

The current starter is organized as follows:

```text
androidApp/                  Android application host
iosApp/                      SwiftUI host and Xcode project
desktopApp/                  JVM desktop demo host
shared/
  src/commonMain/            Shared Compose demo and Kotlin logic
  src/commonMain/composeResources/
  src/commonTest/            Shared sample tests
  src/androidMain/          Android platform implementation
  src/androidHostTest/      Android host sample tests
  src/iosMain/              iOS platform implementation and UI entry point
  src/iosTest/              iOS sample tests
  src/jvmMain/              Desktop platform implementation
  src/jvmTest/              Desktop sample tests
gradle/libs.versions.toml    Dependency version catalog
docs/superpowers/           Design, reviews, implementation plan, and visual references
```

The implementation plan defines the future `dev.daymark` packages and provisional application identifiers. The starter currently uses `com.example.myworktracker`; the public name and final identifiers remain open.

## Development setup

These instructions describe the **current starter**, whose configuration differs from the planned MVP baseline.

| Setting | Current starter | Planned MVP baseline |
| --- | --- | --- |
| Android compile / target SDK | 37 / 37 | 37 / 37 |
| Android minimum SDK | 24 | 26 |
| iOS deployment target | 18.2 | 16.0 |
| Gradle daemon JDK / Android JVM target | 21 / 11 | 21 / 21 |
| Gradle wrapper / Android Gradle Plugin | 9.5.1 / 9.1.1 | 9.8.2 / 9.3.1, subject to the compatibility gate |
| Kotlin / Compose Multiplatform | 2.4.21 / 1.12.2 | Same initial pins, subject to the compatibility gate |

Use the Gradle wrapper and the checked-in [version catalog](gradle/libs.versions.toml), [shared build](shared/build.gradle.kts), [Android build](androidApp/build.gradle.kts), and [Xcode project](iosApp/iosApp.xcodeproj/project.pbxproj) as the source of truth for the current build. The plan's initial dependency/toolchain candidates are not a verified compatibility matrix.

1. Install/configure JDK 21, an Android development environment, and Android SDK Platform 37.
2. Set `sdk.dir` in your local `local.properties` file, for example:

   ```properties
   sdk.dir=/absolute/path/to/Android/sdk
   ```

3. For iOS development, use macOS with Xcode and an available simulator. Physical-device signing requires your own development team in the Xcode project.
4. Keep local paths, credentials, OAuth configuration, and signing material out of commits. Google setup will be needed for reporting implementation; the starter demo does not require it.

### Android

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

The debug APK is generated at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Installation requires an emulator or connected device; launch the app from its launcher afterward.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode, select the `iosApp` scheme and an available simulator, and run. Its build phase invokes `:shared:embedAndSignAppleFrameworkForXcode` to build the shared Kotlin framework.

### Desktop demo

```sh
./gradlew :desktopApp:run
```

### Tests

```sh
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
./gradlew :shared:iosSimulatorArm64Test
```

The iOS task requires macOS, Xcode, and a compatible simulator. These are starter sample tests; they do not verify attendance or native integrations. The implementation plan defines the future rule, persistence, sync, device, and EN/AR UI verification gates.

## Design and implementation references

- [Promoted design v2](docs/superpowers/reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md) — approved design and technical amendments; incorporates the complete [frozen v1 contract](docs/superpowers/reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md).
- [Implementation plan](docs/superpowers/plans/2026-10-27-attendance-mvp.md) — dependency candidates, compatibility/native probes, task order, and release acceptance checks.
- [Senior review](docs/superpowers/reviews/2026-10-27-attendance-mvp/03-senior-review.md) and [research pack](docs/superpowers/reviews/2026-10-27-attendance-mvp/02-research-pack.md) — rationale and supporting research.
- [Interactive visual concept](docs/superpowers/specs/assets/daymark-concept.html), [light preview](docs/superpowers/specs/assets/daymark-light-preview.png), and [dark preview](docs/superpowers/specs/assets/daymark-dark-preview.png) — visual references using sample data and simulated integrations, not native application screenshots.
