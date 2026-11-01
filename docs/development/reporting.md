# Outgoing attendance reports

Room remains authoritative. Sheets is an optional outgoing report, with literal RAW values and no record import. Annual rows are fixed at day-of-year + 2; all fifteen cells, including empty optional cells, are sent together. Headers have stable machine names and bilingual labels. Time-off removals blank previously reserved capacity.

Each dispatch has a persisted payload and PREPARED → DISPATCHED → ACKNOWLEDGED journal. A dispatched or ambiguous row after restart is uncertain, never evidence that the server rejected it. A callback acknowledges only its connection generation and matching projection revision. Split aggregate tables become clean only after all current rows have acknowledged coverage. No access token or GPS fix enters this journal.

New foreground/reconnect sessions read even acknowledged local projections. A timed-out older request may arrive after a newer acknowledgment; a later session detects and repairs that stale remote projection. There is no remote compare-and-swap, monotonic-read promise, or immediate cancellation of a request already sent. The local record is retained throughout failures.

Only tabs with the matching tracker/schema developer metadata are managed. A same-title unrelated tab, missing previously bound tab, changed headers, or different date/tracker identity pauses publication. Other tabs remain untouched. Production reads and RAW writes use [Google's stable sheet-ID grid filters](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets.values/batchUpdateByDataFilter); the isolated developer probe retains its legacy A1 calls. Template creation adds ownership metadata and header strings in one structural batch.

One pass sends at most ten batches of 100 complete rows, each below 2 MB. Transient failures persist their next retry time; the six-attempt session budget uses 2/4/8/16/32/60-second bases and up to one second jitter. Authorization, structural damage and missing reports stop the drain. A note too large for one complete row remains local and pending rather than being truncated.

Live OAuth/Drive/Sheets verification remains open pending the native account setup documented in native-feasibility.md. Mock HTTP tests and real SQLite reopen tests do not close that gate. Lifecycle connection controls and network scheduling are the next implementation task.

Local evidence (2026-11-01): 130 JVM tests and 89 shared Android host tests passed, including stateful MockEngine writes across SQLite close/reopen, late-old-write repair, generation fencing, complete-row quota batching over a year of history, time-off trailing clears, and both Retry-After forms. These are automated local checks, not a live Google account journey.
