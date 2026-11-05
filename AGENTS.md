# Repository Guidelines

## Product Contract and Status

Daymark is the working name for a free personal attendance tracker for Android and iOS. Read these sources before feature work:

1. [Approved design v2](docs/superpowers/reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md).
2. [Frozen v1 contract](docs/superpowers/reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md), incorporated in full by v2. V2 supersedes only its stated technical amendments.
3. [Implementation plan](docs/superpowers/plans/2026-10-27-attendance-mvp.md), including its file map, compatibility gates, task interfaces, acceptance checks, and execution handoff.

V2 is approved for implementation planning; the plan records implementation/execution-method approval as pending. A scaffold now exists, but planned features and unchecked tasks are not implemented merely because they appear in these documents. Preserve the frozen reference. Changes to attendance semantics, permissions, support floors, or product scope require the design review described there.

## Project Structure

- `shared/src/commonMain/`: shared Compose UI, lifecycle ViewModels with immutable `StateFlow`, pure attendance/calendar rules, Room persistence, and reporting. Shared resources live in `composeResources/`.
- `shared/src/androidMain/` and `shared/src/iosMain/`: platform adapters and database factories. Tests use `commonTest`, `androidHostTest`, `androidDeviceTest`, and `iosTest` as applicable.
- `androidApp/`: Android host, permissions, WorkManager scheduling, native Google authorization, and Glance widget.
- `iosApp/`: Swift host, Core Location/native authorization integration, UserNotifications, and WidgetKit. The current Xcode project is `iosApp/Daymark.xcodeproj`.
- `desktopApp/`: Compose Desktop JVM host. `src/main/kotlin/` contains the entry point and desktop composition root; local Room data is stored at `~/.daymark/daymark.db`. macOS location and permissions use the Swift Core Location helper in `src/main/swift/`, bundled with metadata and localized permission text from `src/main/macos/`. Desktop host tests live in `src/test/`. Google authorization, reminders, and widgets remain unsupported desktop adapters; non-macOS hosts also lack location support. Desktop delivery remains outside the Android/iOS MVP.
- `shared/src/jvmMain/`: desktop implementations for Room, locale, logging, connectivity, host behavior, and the registered-place map fallback. Keep desktop-specific APIs here or in `desktopApp/`, outside shared UI/domain code; JVM tests live in `shared/src/jvmTest/`.
- `shared/src/mobileMain/`: Android/iOS shared implementations, including the registered-place map; desktop uses the corresponding JVM implementation.
- `docs/superpowers/`: design, review, and implementation records. The HTML concept under `specs/assets/` is a visual reference with simulated integrations.

Production Kotlin packages use `dev.daymark`. The application/bundle ID is `dev.daymark.app`, with App Group `group.dev.daymark.app`; final public identifiers remain release inputs.

## Architecture & Data Flow

The architecture is feature-oriented MVVM with a central `repository/Repository` exposing focused data-source interfaces. `AppRepository` receives those interfaces through constructor injection; it does not resolve dependencies or own a second container. There is no separate domain module or general use-case layer. See [architecture](docs/development/architecture.md) and the recorded architecture amendment in [implementation decisions](docs/development/implementation-decisions.md).

- `presentation/features/<feature>/`: separate `FeatureScreen.kt`, `FeatureViewModel.kt`, and `FeatureUiState.kt`; add `FeatureUiEffect.kt` for one-time UI actions and `FeatureMapper.kt` for actual model conversion. Feature-specific composables live in `component/` beneath the feature.
- `presentation/component/`: components shared across features.
- `navigation/`: serializable routes and Navigation 3 graphs; `ui/theme/`: shared theme and configuration composition locals. Preserve persisted route serial names when moving Kotlin types.
- `repository/`: the central repository and focused operation/read interfaces. Keep attendance/calendar rules and existing transaction/lifecycle ownership in their responsible implementations.
- `data/remote/data_source/domain/`: remote data-source interfaces; `data/remote/data_source/data/ktor/`: Ktor implementations.
- `data/local/`: Room database, DAOs, factories, local gateways, and operation implementations. Preferences remain in Room to preserve transactional behavior.
- `models/`: portable models and actual conversions; shared/platform helpers retain their existing focused packages.
- `di/ktor/`: Koin bindings for repositories, data sources, services, networking, storage, native dependencies, and lifecycle ViewModels. Each `AppGraph` owns an isolated container; platform bindings load last.

Flow: Compose screen → ViewModel → Repository → focused data-source interface → Ktor API or local storage. ViewModels expose immutable observable UI state and one-time effects. This also applies to bootstrap, dashboard, Google connection, report retry, and confirmed local deletion. Presentation must not access Room, persistence entities, concrete transports, or `AppGraph`; the top-level `App` function only provides the isolated composition context. Native UI interactions use the existing platform ports.

Do not add paging, DataStore, token abstractions, or empty feature files without a current need. Retain the existing serialized, generation-fenced Google token access. `expect`/`actual` bridges platform-specific functionality; keep portable logic in `commonMain`.

Room is authoritative; Sheets is an outgoing report. Keep native APIs out of shared UI/domain code. Use the planned Material 3, Navigation 3 multiplatform artifact, Room/KSP, Ktor, and native adapters after compatibility verification; avoid a module-per-layer.

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

Use the shared `dev.daymark.logging.AppLogger` for useful diagnostics in new or changed flows: `d` for execution steps/cancellation, `i` for expected outcomes, and `e` for unexpected failures. Log at meaningful operation boundaries, outside composable bodies and `StateFlow.update` lambdas; avoid noisy per-frame or polling logs. Use developer-authored tags/messages and enum reason codes only. Never log credentials, coordinates, personal text, whole drafts/results, or raw exception details; pass exceptions to `AppLogger.e`, which includes only their type. Logs must preserve cancellation and existing error handling, and must not claim persistence or sync before acknowledgement.

## UI, Localization, and Style

Preserve Today/History/Plan navigation and the approved soft white/forest light theme and evergreen/soft lime dark theme through Material 3 tokens. Language offers System/English/العربية; appearance offers System/Light/Dark. Use shared English `composeResources/values/strings.xml` and Arabic `values-ar/strings.xml`, plus localized native permissions, notifications, and widgets. Keep Gregorian canonical dates independent of locale. Verify RTL, mixed-direction content, Arabic numeral input, large text, screen-reader labels, and reachable touch targets; status needs text/icons alongside color.

- Keep screen composables small and focused. Let the screen coordinate UI state and events; extract meaningful sections into focused composables and keep business rules in the existing domain/ViewModel layers. Keep layouts uncluttered and adaptable to small displays and large text without shrinking touch targets.
- Create a preview composable for every new screen, and add or update its preview whenever modifying an existing screen. Use representative sample state and the shared app theme so previews reflect the current UI.
- Reuse shared composable components for repeated UI patterns. Check existing components before adding new ones; place components used across screens in a feature-appropriate shared package under `shared/src/commonMain/`. Prefer stateless components with explicit state, event callbacks, and a caller-supplied `Modifier`; use shared Material 3 tokens and localized resources. Extract reusable behavior when there is a real use, and avoid speculative component frameworks.
- Add purposeful animation to screen transitions, visibility changes, and interactive state changes where it improves feedback and continuity. Use Compose Multiplatform-compatible animation APIs and consistent Material 3 motion guidance. Respect the platform's reduced-motion or animation-duration settings, keep essential actions immediately usable, and never use animation completion as attendance or persistence evidence. Consult the Compose skill's `references/animation.md` and `references/material3-motion.md` when implementing motion.

Use four-space Kotlin indentation, `PascalCase` classes/composables, `camelCase` functions/properties, `UPPER_SNAKE_CASE` constants, and `snake_case` resources. Manage dependency versions through `gradle/libs.versions.toml`. Follow the plan's typed result/error contracts over conflicting generic skill examples.

Reformat changed code with Android Studio/IntelliJ formatting before presenting or committing it. Limit formatting to task-owned files or changed ranges, preserve behavior, and avoid unrelated formatting churn. No ktlint/Detekt setup currently exists; do not introduce a formatter dependency solely for routine formatting. Preserve bundled media filenames and locale resource directories unless the task explicitly requires changing them.

## Build and Verification

Use the Gradle wrapper and configure the Android SDK in untracked `local.properties`. Current build settings are compile/target SDK 37, minimum Android API 26, iOS 16, and JVM target/JDK 21. Gradle/catalog/wrapper files describe the actual build; planned pins are candidates until compiled together. Verify compatible stable versions against official sources before dependency changes. Select JDK/Xcode per command; do not change global Java or `xcode-select` settings.

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

Read and apply the applicable installed `SKILL.md` files before writing, editing, refactoring, or fixing code; listing a skill without following its workflow is insufficient. Use `clean-code-guard` for production code, `compose-expert:compose-expert` for shared or Android Compose code, and both Compose skills for Android-specific Compose work. Paths below are machine-specific; locate equivalent installations elsewhere. Adapt recommendations to this repository's Compose Multiplatform architecture; preserve the existing UI architecture, including any XML-based screens, unless migration is requested. Repository contracts, verified dependency versions, and existing architecture take precedence over generic skill examples; do not introduce Hilt, Navigation 2, or Android-only APIs into shared code from those examples.

- [android-cli](/Users/m37moud/.agents/skills/android-cli/SKILL.md): SDK/emulators, deployment, and official documentation. Read `references/interact.md` before layout/screenshots and `references/journeys.md` before journeys.
- [android-dev](/Users/m37moud/.codex/skills/android-dev/SKILL.md): architecture, lifecycle, debugging, performance, and releases. Adapt recommendations to existing Kotlin/CMP conventions; automated test creation and coverage targets are not required for now.
- [android_ui_verification](/Users/m37moud/.codex/skills/android_ui_verification/SKILL.md): emulator UI checks/screenshots. Inspect bounds before tapping; use native logcat rather than ReactNativeJS filters. Save evidence in `artifacts/`.
- [android-ui-journey-testing](/Users/m37moud/.codex/skills/android-ui-journey-testing/SKILL.md): XML journeys; execute sequentially, fail fast, mark remaining steps skipped, and emit JSON results. Also produce Markdown when following CLI journey guidance.
- [clean-code-guard](.agents/skills/clean-code-guard/SKILL.md): apply its imperatives while writing production code, then perform the mandatory guard pass described below.
- [android-jetpack-compose-expert](/Users/m37moud/.codex/skills/android-jetpack-compose-expert/SKILL.md): required alongside `compose-expert:compose-expert` when implementing, migrating, or changing Android Compose UI, state management, side effects, navigation, or performance. Adapt Android examples to the repository's constructor injection and multiplatform contracts.
- [compose-expert:compose-expert](/Users/m37moud/.codex/skills-src/compose-skill/skills/compose-expert/SKILL.md): required when writing or changing shared Compose/CMP or Android Compose code. Read the topic references selected by its routing table before implementation, including `references/multiplatform.md` for shared code; consult state, side-effect, navigation, performance, theming, and accessibility references as relevant. Use its review workflow for Compose review requests.

## Production Code Review

Use [clean-code-guard](.agents/skills/clean-code-guard/SKILL.md) throughout production code-writing tasks. Read `references/ai-failure-modes.md` first and apply its imperatives while implementing. After non-trivial production code changes, run its guard pass and self-check before presenting, committing, or merging. Fix violations and repeat the self-check after every subsequent production code edit in the same session. Report actual fixes or remaining findings using the skill's format, ending with `clean-code-guard: clean` or `clean-code-guard: <N> fixed, <M> flagged for author`. If the skill cannot be located, apply the installed [clean-code](/Users/m37moud/.codex/skills/clean-code/SKILL.md) checklist as the fallback and explicitly report that the guard itself was unavailable.

Apply the plan's RF1–RF5 review focus: sensor validity, competing edits/claims, calendar boundaries, literal/localized text, and obsolete network callbacks. Repeat review after subsequent code edits. For review-only requests, report findings without editing unless asked. Preserve behavior during refactors. This production review excludes documentation, tooling configuration, and test-code review; automated test creation remains optional.

## Commits and Pull Requests

Use focused, imperative subjects. Stage only task-owned files and inspect the staged diff, including `git diff --cached --check`; preserve unrelated staged/unstaged work. PRs describe behavior, relevant issues, actual verification results, remaining native/release gates, and screenshots for UI changes. Never claim a concept, fake adapter, zero-test run, or unavailable device passed a native acceptance gate.
