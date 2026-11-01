# Outgoing attendance reports

Room remains authoritative. Sheets is an optional outgoing report, with literal RAW values and no record import. Annual rows are fixed at day-of-year + 2; all fifteen cells, including empty optional cells, are sent together. Headers have stable machine names and bilingual labels. Time-off removals blank previously reserved capacity.

Each dispatch has a persisted payload and PREPARED → DISPATCHED → ACKNOWLEDGED journal. A dispatched or ambiguous row after restart is uncertain, never evidence that the server rejected it. A callback acknowledges only its connection generation and matching projection revision. Split aggregate tables become clean only after all current rows have acknowledged coverage. No access token or GPS fix enters this journal.

New foreground/reconnect sessions read even acknowledged local projections. A timed-out older request may arrive after a newer acknowledgment; a later session detects and repairs that stale remote projection. There is no remote compare-and-swap, monotonic-read promise, or immediate cancellation of a request already sent. The local record is retained throughout failures.

Only tabs with the matching tracker/schema developer metadata are managed. A same-title unrelated tab, missing previously bound tab, changed headers, or different date/tracker identity pauses publication. Other tabs remain untouched. Production reads and RAW writes use [Google's stable sheet-ID grid filters](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets.values/batchUpdateByDataFilter); the isolated developer probe retains its legacy A1 calls. Template creation adds ownership metadata and header strings in one structural batch.

One pass sends at most ten batches of 100 complete rows, each below 2 MB. Transient failures persist their next retry time; the six-attempt session budget uses 2/4/8/16/32/60-second bases and up to one second jitter. Authorization, structural damage and missing reports stop the drain. A note too large for one complete row remains local and pending rather than being truncated.

Live OAuth/Drive/Sheets verification remains open pending the native account setup documented in native-feasibility.md. Mock HTTP tests and real SQLite reopen tests do not close that gate. Lifecycle controls and network scheduling are implemented in Task 14; their evidence and remaining live gates are recorded below.

Local evidence (2026-11-01): 130 JVM tests and 89 shared Android host tests passed, including stateful MockEngine writes across SQLite close/reopen, late-old-write repair, generation fencing, complete-row quota batching over a year of history, time-off trailing clears, and both Retry-After forms. These are automated local checks, not a live Google account journey.

## Connection and lifecycle controls

Settings offers explicit connect/reconnect, report selection, disconnect, managed-section rebuild, and replacement-report actions. Reconnect forgets the local native session before authorizing again, allowing account selection; silent refresh never opens account UI and verifies the opaque Google account reference before using a report. Canceling reconnect leaves uploads disconnected and preserves phone records and old reports.

Uncertain report creation is recorded before the Drive POST. After interruption or an ambiguous response, Connect searches for the existing report rather than creating another automatically. Creating a replacement is a separate confirmed user action. Rebuild can repair headers and date rows only in metadata-verified owned tabs; a missing/unowned bound tab requires a replacement. The app exposes no Google file-deletion operation.

Foreground launch/resume, local edits, reconnect, connectivity recovery and Retry feed a serialized drain. Backgrounding cancels only foreground-owned work; an interrupted dispatched batch remains uncertain in the journal. iOS network observation exists only while the app is active. Android also has a separate network-constrained WorkManager chain, with no location dependency. OS scheduling and closed-app iOS upload completion are not guaranteed.

Sync timestamps are scoped to the connection generation so another account cannot inherit an old success label. “Connected” identifies a report; “upload acknowledged” reflects an observed publishing pass. Pending data remains saved on the phone.

## Confirmed local deletion

Only the confirmation action invokes deletion. It fences the binding first, disables automatic checks/reminders, stops foreground and native work, forgets native credentials and clears the widget before clearing all nine local tables in one write transaction. No Google report is deleted. A native cleanup or database failure returns failure, retains any uncommitted authoritative records, and leaves uploads/automation disabled. Sheets is an outgoing report, not a restore backup.

Live EN/AR account/consent/revocation journeys remain incomplete without configured OAuth applications and explicit native consent. iOS host/UI/WidgetKit execution and physical iPhone checks remain user-deferred. The Huawei launcher pin/tap check remains user-deferred. Automated tests use isolated fixture databases and fake authorization; they do not create spreadsheets in a real account.

Task 14 local host evidence (2026-11-01): 141 JVM, 89 shared Android host, and 16 Android app host tests passed. Android instrumentation sources and shared iOS simulator sources compiled. The iOS journey source passes Swift syntax parsing and is registered in the Xcode UI-test target; native execution is deferred and is not counted as a pass. Native Google account-switch/revocation, live report creation and iPhone behavior remain required gates.

All 13 Android instrumentation journeys passed on Huawei JKM-LX1 / Android 9 (API 28), with no skipped tests. The new isolated journey canceled fake Google consent, canceled deletion without changing the profile, then confirmed deletion and returned to setup with zero GPS requests. This does not verify live Google consent or the Huawei launcher pin/tap gate.
