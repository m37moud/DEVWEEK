# Local persistence checkpoint

Room 3.0.3 with bundled SQLite 2.7.1 stores authoritative records on the phone. Version 1 is exported under `shared/schemas/dev.daymark.data.DaymarkDatabase/1.json`. No destructive migration/downgrade fallback is configured. This checkpoint provides storage primitives for later time-off, settings and reporting work; their full workflows are not claimed complete here.

Tables cover settings/setup drafts, one tracker, versioned expectations, dated attendance and consumed claims, time-off entries, signed balance adjustments, report bindings, dirty projection revisions, and in-flight report batches. Dates/instants use canonical strings and are parsed as date/time values for comparisons. Registered-place centers are local snapshots; sensor fixes never enter the database commit contract. Notes and coordinate-bearing entities redact their string output. Tokens and refresh tokens have no database columns.

Each authoritative attendance mutation and its date/Overview dirty revisions share one immediate writer transaction. Automatic claims are committed before acquisition, remain consumed after cancellation, and are marked interrupted on recovery without releasing them. A separate per-day user-edit revision rejects an old GPS result even when a holiday was added and removed during acquisition. Ordinary GPS writes do not advance that fence, allowing an earlier valid concurrent confirmation to win. Explicit corrections and active time off retain precedence.

Automatic reconciliation starts at `trackingStartsAt`, preserves historical snapshots, and creates no pre-installation absence. Current-day loading can project a baseline without materializing a record. Explicit current-day corrections can create today's record after late setup. Future planning creates no attendance rows to freeze against later approved changes. Local-save failures produce `LOCAL_SAVE_FAILED`, not a recorded-success result.

Android uses its private database directory and has backup disabled in the manifest. The iOS default factory requests an application-support directory excluded from backup with protection until first user authentication; the directory flags compile, but device-level protection behavior remains part of native acceptance. Native tests use unique temporary files and remove only their own files.

## Verification

All 161 Kotlin tests pass: 48 Android shared tests, 51 JVM tests, 51 iOS tests, and 11 Android host tests. Android debug packaging and release Kotlin compilation pass. The JVM target exists for supported Room host tests and reuses the pure Java zone resolver; no desktop application is added.

Persistence tests use real file-backed bundled SQLite. They verify concurrent unique claims/inserts, a separate JVM process forcibly killed while acquisition is suspended, consumed claims after recovery, native iOS cold reopen, corrections during acquisition, user-edit revision fencing, earlier successful evidence, rollback of attendance and outbox together using a SQLite abort trigger, late setup without retroactive absence, explicit today correction, and frozen current-day expectations after a future change. Sensor acquisition in database/service tests is controlled; it is not physical GPS evidence.

Room's generated constructor uses Kotlin expect/actual classes, for which the compiler emits its beta-language warning. The warning is retained. Android-specific device wiring remains an acceptance check; JVM transaction tests do not substitute for it. Physical iPhone and live Google gates remain open as documented separately.

Gate command (JDK 21, one worker on this constrained machine):

```sh
./gradlew :shared:testAndroidHostTest :shared:jvmTest \
  :shared:iosSimulatorArm64Test --device=779285A4-F1C5-4274-9899-B38357C3B824 \
  :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:compileReleaseKotlin \
  --continue --max-workers=1 --console=plain
```

References: [Room KMP builders and bundled driver](https://developer.android.com/kotlin/multiplatform/room), [Room host/device testing](https://developer.android.com/training/data-storage/room/testing-db), and exact 3.0.3 runtime/plugin sources from Google Maven. The Room 3 plugin uses `room3 { schemaDirectory(...) }`.
