# Daymark

A personal attendance tracker built with Kotlin and Compose Multiplatform for Android and iOS. One registered place and weekly schedule, location-verified check-in, attendance history and corrections, time off and vacation balance, English/Arabic, and light/dark themes.

Android attempts one scheduled location check subject to permissions and system restrictions. iOS uses reminders and a foreground quick-check. Records save locally in Room; optional Google Sheets publishing is outgoing only. Google reports are retained when disconnecting or deleting local data. Sheets is not a restore backup.

## Local development

Use JDK 21 and the checked-in Gradle wrapper. Dependencies and checksums are pinned.

```sh
./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
./gradlew :androidApp:connectedDebugAndroidTest
```

The iOS host is `iosApp/Daymark.xcodeproj`; native signing, App Group and OAuth setup require the final application identifiers. Do not commit credentials or signing overrides.

The current implementation is **not cleared for distribution**. See the [verification record](docs/development/release-verification.md) for actual evidence and open native/live gates, [reporting](docs/development/reporting.md) for synchronization and deletion behavior, and [native feasibility](docs/development/native-feasibility.md) for platform limitations. The approved implementation plan lives in [docs/superpowers/plans](docs/superpowers/plans/2026-10-27-attendance-mvp.md).
