# Repository Guidelines

## Product Contract and Status

Daymark is the working name for a free personal attendance tracker for Android and iOS. Read these sources before feature work:

1. [Approved design v2](docs/superpowers/reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md).
2. [Frozen v1 contract](docs/superpowers/reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md), incorporated in full by v2. V2 supersedes only its stated technical amendments.
3. [Implementation plan](docs/superpowers/plans/2026-10-27-attendance-mvp.md), including its file map, compatibility gates, task interfaces, acceptance checks, and execution handoff.

V2 is approved for implementation planning; the plan records implementation/execution-method approval as pending. A scaffold now exists, but planned features and unchecked tasks are not implemented merely because they appear in these documents. Preserve the frozen reference. Changes to attendance semantics, permissions, support floors, or product scope require the design review described there.

## Project Structure and Architecture

- `shared/src/commonMain/`: shared Compose UI, lifecycle ViewModels with immutable `StateFlow`, pure attendance/calendar rules, Room persistence, and reporting. Shared resources live in `composeResources/`.
- `shared/src/androidMain/` and `shared/src/iosMain/`: platform adapters and database factories. Tests use `commonTest`, `androidHostTest`, `androidDeviceTest`, and `iosTest` as applicable.
- `androidApp/`: Android host, permissions, WorkManager scheduling, native Google authorization, and Glance widget.
- `iosApp/`: Swift host, Core Location/native authorization integration, UserNotifications, and WidgetKit. The current Xcode project is `iosApp/iosApp.xcodeproj`; the plan's `Daymark.xcodeproj` layout is future work.
- `desktopApp/` and shared JVM source sets: existing development scaffold; desktop delivery is outside the Android/iOS MVP.
- `docs/superpowers/`: design, review, and implementation records. The HTML concept under `specs/assets/` is a visual reference with simulated integrations.

Current Kotlin packages are `com.example.myworktracker`. The plan specifies `dev.daymark` for new production Kotlin, provisional application/bundle ID `dev.daymark.app`, and App Group `group.dev.daymark.app`; follow its migration/file map when implementing. Final public identifiers remain release inputs.

Keep feature-oriented packages and constructor injection from a small composition root. Room is authoritative; Sheets is an outgoing report. Keep native APIs out of shared UI/domain code. Use the planned Material 3, Navigation 3 multiplatform artifact, Room/KSP, Ktor, and native adapters after compatibility verification; avoid a module-per-layer or DI framework.

## Attendance and Persistence Rules

- One active tracker, registered place, weekly schedule, and phone per installation. Work, School, and Course are personal tracking categories; employer administration is outside scope.
- Use the saved IANA attendance zone and frozen per-date plan/place/category expectations. Ordinary configuration changes start the next attendance-zone day; travel or locale changes must not rewrite history. Never infer pre-setup absences.
- Normal checks start at scheduled arrival. A fresh captured fix at or before the inclusive deadline is Present; a later successful user-initiated GPS check is Late. Default grace is five minutes, configurable from 0–120 minutes. An overdue unconfirmed scheduled day becomes Absent when evaluated. Failed checks never downgrade a success; keep the earliest success. Explicit manual corrections take precedence over later evaluation.
- Default radius is 100 m, adjustable to 100–500 m; require accuracy of 50 m or better, sample age at most 30 seconds, and at most a 30-second request. Validate native freshness evidence; tap time is not confirmation time. Discard raw check coordinates after the verdict.
- Android gets one atomically claimed automatic attempt per eligible date, only within the arrival-to-deadline window. A consumed claim survives cancellation/process death. Delayed work skips GPS; automatic checks never create Late. Network retries never request GPS.
- iOS uses reminders and foreground checks with When In Use location. Maintain the next 30 calendar days of reminders and expose that horizon. Widgets open the foreground check and re-evaluate current state; snapshots/routes are hints, not attendance evidence.
- Preserve manual correction sources and underlying attendance when applying time off. Priority is Holiday, weekly day off, Vacation, then scheduled attendance. Past/current/future tracked time-off edits are supported; removing an overlay restores underlying attendance or derives Absent without retrospective GPS.
- Work has one whole-day vacation balance; future vacation reserves it immediately. Deduplicate eligible dates, exclude holidays/weekly days off, preview deduction differences, and reject changes that exceed available balance. Apply attendance/time-off/balance changes and dirty report revisions transactionally. Enforce one `(trackerId, attendanceLocalDate)` record. Never use destructive migration/downgrade fallback.

## Reporting, Privacy, and Scope

Google connection is optional; attendance works offline without login. Use native authorization with only `drive.file`, secure native credential storage, and no embedded client secret/service account. Do not store raw credentials in Room or expose them to widgets.

Use one app-created report per tracker with bilingual headings, stable machine fields, and deterministic annual date rows (`dayOfYear + 2`). Encode user text and canonical strings with `RAW`; keep app-authored formulas separate and explicitly clear removed values. Never import spreadsheet attendance or overwrite unrelated tabs.

Journal batches before dispatch, serialize drains per binding, acknowledge only current local revisions, and fence callbacks by connection generation. Reconcile ambiguous writes and previously acknowledged rows at later sync sessions from local truth. Retry with bounded backoff; pause for authorization/schema failures. Reports are eventually convergent, not remotely transactional. iOS drains at foreground opportunities; Android also uses separate network-constrained work. Show saved-locally/pending/error states honestly.

Preserve existing Google reports on disconnect, reinstall, and local deletion. In-app local deletion requires confirmation and cancels work/reminders/sync. Never log credentials, precise positions, or personal notes. Keep credentials and signing material out of commits and logs; redact sensitive test inputs from reports and screenshots. If a dependency repository requires GitHub Packages credentials, supply them through `GPR_USER`/`GPR_API_KEY` environment variables rather than tracked files.

Use free redistributable dependencies. No backend, paid service, advertising SDK, continuous tracking, geofencing, periodic GPS retries, exact-alarm special access, persistent location service, iOS background location, multi-device merge, or full backup/restore. Account enrollment, billing, and publication need separate user instructions.

## UI, Localization, and Style

Preserve Today/History/Plan navigation and the approved soft white/forest light theme and evergreen/soft lime dark theme through Material 3 tokens. Language offers System/English/العربية; appearance offers System/Light/Dark. Use shared English `composeResources/values/strings.xml` and Arabic `values-ar/strings.xml`, plus localized native permissions, notifications, and widgets. Keep Gregorian canonical dates independent of locale. Verify RTL, mixed-direction content, Arabic numeral input, large text, screen-reader labels, and reachable touch targets; status needs text/icons alongside color.

Use four-space Kotlin indentation, `PascalCase` classes/composables, `camelCase` functions/properties, `UPPER_SNAKE_CASE` constants, and `snake_case` resources. Manage dependency versions through `gradle/libs.versions.toml`. Follow the plan's typed result/error contracts over conflicting generic skill examples.

Reformat changed code with Android Studio/IntelliJ formatting before presenting or committing it. Limit formatting to task-owned files or changed ranges, preserve behavior, and avoid unrelated formatting churn. No ktlint/Detekt setup currently exists; do not introduce a formatter dependency solely for routine formatting. Preserve bundled media filenames and locale resource directories unless the task explicitly requires changing them.

## Build and Verification

Use the Gradle wrapper and configure the Android SDK in untracked `local.properties`. Current scaffold settings are compile/target SDK 37, minimum API 24, Android JVM target 11, and Gradle daemon JDK 21. The plan targets minimum API 26, iOS 16, and JVM target/JDK 21; reconcile these in Task 1 rather than assuming that migration already happened. Gradle/catalog/wrapper files describe the actual build; planned pins are candidates until compiled together. Verify compatible stable versions against official sources before dependency changes. Select JDK/Xcode per command; do not change global Java or `xcode-select` settings.

- `./gradlew :androidApp:assembleDebug` — build the Android debug APK.
- `./gradlew :androidApp:installDebug` — install on a connected device/emulator.
- `./gradlew :androidApp:lintDebug :androidApp:lintRelease` — Android lint.
- `./gradlew :androidApp:assembleRelease` — build release output; the scaffold has no release signing configuration and minification is disabled.
- `./gradlew :shared:tasks --all` — confirm generated KMP task names at the compatibility gate.
- `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :androidApp:testDebugUnitTest` — shared/native/unit checks, subject to required toolchains.
- `./gradlew :androidApp:connectedDebugAndroidTest` — connected Android journeys when implemented.
- `./gradlew :desktopApp:run` and `./gradlew :shared:jvmTest` — existing desktop development checks.

For iOS, inspect the actual Xcode project/scheme and available simulator before building/testing; use the plan's Daymark commands after its host migration. Simulator results do not prove physical location, background execution, signing, or App Group behavior.

Automated test creation and coverage targets are optional for now unless explicitly requested for the task. Run relevant existing checks and report verification gaps honestly; do not claim an unperformed acceptance check passed. Start with toolchain/native feasibility gates before dependent feature work. Record missing hardware/OAuth/signing prerequisites as incomplete and continue independent work. Device evidence must cover Android delay/Doze/reboot and manufacturer differences, iOS reminders/widgets/suspension, failure recovery, and EN/AR × light/dark on both platforms. Save redacted UI evidence in `artifacts/`. Documentation-only changes need link/configuration review and `git diff --check`, not application tests.

## Android and Compose Skills for Agents

Read applicable installed `SKILL.md` files before work. Paths below are machine-specific; locate equivalent installations elsewhere. Adapt recommendations to this repository's Compose Multiplatform architecture; preserve the existing UI architecture, including any XML-based screens, unless migration is requested.

- [android-cli](/Users/m37moud/.agents/skills/android-cli/SKILL.md): SDK/emulators, deployment, and official documentation. Read `references/interact.md` before layout/screenshots and `references/journeys.md` before journeys.
- [android-dev](/Users/m37moud/.codex/skills/android-dev/SKILL.md): architecture, lifecycle, debugging, performance, and releases. Adapt recommendations to existing Kotlin/CMP conventions; automated test creation and coverage targets are not required for now.
- [android_ui_verification](/Users/m37moud/.codex/skills/android_ui_verification/SKILL.md): emulator UI checks/screenshots. Inspect bounds before tapping; use native logcat rather than ReactNativeJS filters. Save evidence in `artifacts/`.
- [android-ui-journey-testing](/Users/m37moud/.codex/skills/android-ui-journey-testing/SKILL.md): XML journeys; execute sequentially, fail fast, mark remaining steps skipped, and emit JSON results. Also produce Markdown when following CLI journey guidance.
- [android-jetpack-compose-expert](/Users/m37moud/.codex/skills/android-jetpack-compose-expert/SKILL.md): Android Compose implementation/migration only. For Compose questions, also consult [compose-expert](/Users/m37moud/.codex/skills/compose-expert/SKILL.md) and its relevant references.
- [compose-expert](/Users/m37moud/.codex/skills/compose-expert/SKILL.md): shared Compose/CMP work; read the relevant references, including `references/multiplatform.md`.

## Production Code Review

After non-trivial production code changes, read and apply [clean-code-guard](.agents/skills/clean-code-guard/SKILL.md) before presenting, committing, or merging when that skill is available. Read `references/ai-failure-modes.md` first, run the skill's self-check, fix violations, and report actual fixes or remaining findings. The repository-local guard is currently absent; until installed, apply the installed [clean-code](/Users/m37moud/.codex/skills/clean-code/SKILL.md) checklist as the fallback and report that the guard itself was unavailable.

Apply the plan's RF1–RF5 review focus: sensor validity, competing edits/claims, calendar boundaries, literal/localized text, and obsolete network callbacks. Repeat review after subsequent code edits. For review-only requests, report findings without editing unless asked. Preserve behavior during refactors. This production review excludes documentation, tooling configuration, and test-code review; automated test creation remains optional.

## Commits and Pull Requests

Use focused, imperative subjects. Stage only task-owned files and inspect the staged diff, including `git diff --cached --check`; preserve unrelated staged/unstaged work. PRs describe behavior, relevant issues, actual verification results, remaining native/release gates, and screenshots for UI changes. Never claim a concept, fake adapter, zero-test run, or unavailable device passed a native acceptance gate.
