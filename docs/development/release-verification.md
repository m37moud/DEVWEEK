# Daymark local candidate verification — 2026-11-01

**Not cleared for distribution.** Local implementation is present. Native/live release gates below are incomplete; a debug APK, source compilation or a skipped test does not close them. No store upload, enrollment, billing change, merge or push was performed.

## Candidate and environment

- Isolated branch: `feature/attendance-mvp`, worktree `/Users/m37moud/.codex/worktrees/attendance-mvp/MyWorkTracker`.
- Android package `dev.daymark.app`, version 0.1.0 (code 1), min 26 / target 37. Final publisher identifiers and signing are release inputs.
- Physical Android: Huawei JKM-LX1, Android 9 / API 28. Other Android versions/manufacturers require additional acceptance.
- Installed Xcode 27.1; Kotlin's officially qualified Xcode/support-floor compatibility remains an open gate. Product iOS floor stays 16.0; the Native ICU availability warning must be resolved and tested without hiding it or silently raising the floor.
- Physical iPhone unavailable. User deferred iOS native host/UI linking/execution and kept both installed simulator runtimes. Shared Kotlin iOS compilation and Swift syntax checks remain permitted; they are not native acceptance evidence.

## Gate status

| Gate | Status / evidence |
| --- | --- |
| Pure attendance, grace, location freshness, fixed-zone/DST, leave and balance rules | Local automated coverage passes; see host results below. |
| Transactional attendance, once-only claims, process interruption and historical baselines | Real bundled-SQLite host tests pass; Android fixture journeys cover application wiring. |
| Report literal values, fixed annual rows, durable uncertainty, account generation fences, old-write repair | Mock HTTP plus actual SQLite close/reopen tests pass. No live Google account acceptance is claimed. |
| Foreground cancel/resume and confirmed local deletion | Host tests and the isolated Huawei journey pass. Database-clear faults roll back authoritative rows. |
| Android app journeys through Task 14 | 13 executed, zero skipped/failures, Huawei API 28. |
| EN/AR × light/dark at 200% text | Android four-configuration action-reachability journey passed at 200% text. Full visual clipping review and iOS four-configuration execution remain open. |
| Upgrade without record/leave/balance/journal loss; downgrade refuses erase | JVM synthetic prior-schema/future-version checks and Android prior-schema fixture journey passed. No older shipped application is fabricated. |
| Android lint/build and merged permissions | Local checks recorded below. No exact alarms, background location service, continuous tracking or foreground-service permission is introduced. WorkManager's wake lock and app-internal receiver permission remain. |
| Backup/privacy | Android explicitly excludes private local data from cloud and device transfer. iOS DB/widget cache are backup-excluded; native tokens use device-only Keychain. Source-observed UserDefaults reason declared. Native archive privacy aggregation and store disclosures remain open. |
| Live Google OAuth/Drive/Sheets EN/AR account/revocation/schema repair | **Open:** configure matching Android/iOS OAuth applications and complete user-driven native consent and report journeys. No credentials were requested or embedded. |
| Modern Android background permission, battery saver/Doze/reboot, signed release | **Open:** physical signed-release measurements and product acceptance of missed checks required. Existing feasibility notes include a stale physical background result; no automatic reliability guarantee. |
| Huawei launcher widget pin/tap | **Open, user deferred:** native cache/quick-check handoff instrumentation passed, launcher picker did not advance through ADB. |
| iOS native host, WidgetKit, notification delivery, real App Group, signed iPhone GPS | **Open, user deferred/unavailable.** Earlier adapter tests and current source checks do not replace these gates. |
| Final independent review | Completed: six Important findings addressed in one regression-tested author pass; two Minor findings deferred. See [final review](final-review.md). |

## Reproducible local checks

Use JDK 21, the existing verified Gradle wrapper, one worker and single-use builds:

```sh
JAVA_HOME=/Users/m37moud/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home \
./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:testDebugUnitTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process --max-workers=1
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
./gradlew :androidApp:connectedDebugAndroidTest
./gradlew :shared:compileKotlinIosSimulatorArm64
```

The approved plan's full iOS test/link command has deliberately not been rerun after the user's deferral. A future native run must record actual executed tests and not accept zero-test or skipped suites.

Task 14 evidence: 141 JVM +89 shared Android host +16 Android app host tests pass; 13 Huawei instrumentation journeys pass. Shared iOS simulator source, Android instrumentation sources, new Swift syntax and Xcode project plist validation pass. Evidence logs are in `.superpowers/sdd/2026-10-27-attendance-mvp/` (task-14-final-compile, task-14-host-tests, task-14-device-tests).

## Dependency, privacy and cost review

The point-in-time [dependency audit](dependency-audit.json) queries 247 locked Android release-runtime Maven coordinates against the free OSV API: zero indexed advisory matches. This is not proof of absence of vulnerabilities. Cached POM declarations identify Apache 2.0/BSD and Android SDK terms; two coordinates lack direct POM license metadata. [SLF4J's primary license page](https://www.slf4j.org/license.html) identifies MIT; [Guava's primary POM](https://github.com/google/guava/blob/master/pom.xml) identifies Apache 2.0. Review inherited notices, packaged native frameworks, vendor terms and refreshed advisories before distribution.

No paid runtime SDK, custom backend, advertising or paid quota escalation was added. Optional Sheets calls remain quota-limited and use `drive.file`; quota errors stay pending. Distribution accounts, final OAuth configuration, privacy-policy hosting, final app icon and final store declarations are still owner-provided release inputs. Do not enable billing or enroll/publish automatically.

Android backup rules follow [the official backup/device-transfer guidance](https://developer.android.com/identity/data/autobackup). Bundled EN/AR resources follow [the official language-splitting guidance](https://developer.android.com/guide/app-bundle/configure-base). Apple's required-reason declaration follows [TN3183](https://developer.apple.com/documentation/technotes/tn3183-adding-required-reason-api-entries-to-your-privacy-manifest). The optional outgoing Google report contains profile/attendance/notes; final store data disclosures must account for that user-enabled transfer rather than claiming the entire product never transfers data.

## Recovery and handoff

Room is authoritative; authorization/schema/network failures pause reporting while local records and dirty revisions remain. Reconnect never imports records from Sheets. Disconnect and confirmed local deletion retain existing Google reports. Local deletion/reinstall loses the phone's records; outgoing Sheets is not an app restore backup and does not provide multi-device tracking.

Prefer a forward-compatible migration/fix. An unsupported newer database is refused, never destructively downgraded. Do not roll back by deleting the database, resetting claims, changing support floors, adding permissions or adding extra GPS retries. Changes to those constraints require a design decision. Keep this worktree and verification evidence while native/live gates remain open.

## Final local candidate evidence

The widget acceptance test exposed an Android AtomicFile concurrency defect: reads can restore a backup while another thread writes. A shared process lock now protects cache read/write/clear. The same unchanged attendance/locale expectations then passed on API 28. The large-text matrix waits for the asynchronously loaded localized status before scrolling, and all four Android language/theme combinations pass at 200% text.

A new retry-budget regression also failed before correction: successful batches were resetting the active session's transient count. ACKs now preserve that count. Fresh host results are 145 JVM +89 shared Android host +16 Android app host, zero failures. iOS shared source compilation passes after the correction. Final complete device/build results follow below.

Final complete candidate gate passed (2026-11-01): **145 JVM +89 shared Android host +16 Android app host tests; 15 Huawei instrumentation tests, zero skipped/failures**. Android `lintDebug` reports **0 errors, 8 warnings**; debug and unsigned release APK builds pass. The language-split warning is resolved. Remaining lint items are the API-guarded background-location constant, platform-version-only attributes, loading-layout overdraw, URI extension suggestions and the final application icon input. Merged permissions contain network, foreground/background location, boot, WorkManager wake lock and the app-private receiver permission; no foreground-service, exact-alarm or biometric permission remains. Evidence: `task-15-candidate-gate.log`, `task-15-budget-compile.log`, final Gradle XML reports, Android lint report, and plist/Swift syntax checks.

## Post-review gate

The single final fix pass passed all **281 tests: 158 JVM +91 shared Android host +16 Android app host +16 Huawei instrumentation**, zero failures/errors/skipped (`final-fix-suite.log`). Lint remains 0 errors/8 warnings. Debug/test APKs and the unsigned release APK rebuilt. `final-fix-compile.log` verifies the final shared iOS source and Android test sources; updated Swift syntax and plists also pass. The independent review and exact fixes/deferred minors are recorded in [final-review.md](final-review.md). No second review was dispatched. All native/live release gates above remain open; this does not authorize distribution.
