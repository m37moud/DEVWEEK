# Personal attendance tracker: promoted design v2

Date: 27 October 2026. Status: approved by the user for implementation planning on 27 October 2026; not implemented.

Approval record: the user explicitly approved v2 and requested the writing-plans skill. This includes the incorporated v1 product contract, proposed defaults, and the technical amendments below. Implementation still awaits review of the implementation plan and selection of an execution method.

## 1. Goal and non-goals

Help a person track their own work/school/course attendance on Android and iOS with local records, user-controlled corrections, optional Google Sheets reporting, and complete English/Arabic support. **The complete product contract is incorporated from immutable [01-design-v1-frozen.md](01-design-v1-frozen.md), SHA-256 `99843eeb6012ddb80a51c114daf5cd82054d99735a768bf278cc98a9d4e20e53`, all fourteen sections.** This document supersedes only the specific technical omissions identified below; it does not replace the frozen artifact or shorten its requirements.

In particular preserve one tracker/place/phone; the selected Android best-effort single automatic attempt; iOS reminder and foreground quick check; early-check restriction; inclusive configurable grace/default five minutes; absence and later manual GPS Late; correction precedence; immutable historical expectations; retrospective/current/future holiday/vacation edits and balances; no repeated GPS retries; optional outgoing Sheets; EN/AR/RTL and the visual direction. Preserve the proposed defaults in frozen section 4 for the user's design approval. Preserve all exclusions in frozen section 2: no backend, paid service, continuous tracking, geofencing, employer administration, multi-device merge, spreadsheet import, or full restoration.

The technical amendments are: an iOS sync execution contract (M1); eventual report reconciliation after ambiguous writes (M2); literal report encoding (m1); small boundary shapes (m2); and risk-first verification plus non-destructive rollback (m3). Where this amendment is silent, v1 applies in full.

## 2. Decisions, versions, rationale, evidence, and rejected alternatives

| Decision | Version/status | Rationale and evidence | Strongest rejected alternative |
| --- | --- | --- | --- |
| Keep the small shared CMP app, native adapters, constructor injection, Room local truth | Research baseline Kotlin 2.4.21, CMP 1.12.2, Room 3.0.3; not yet compiled together. Exact Gradle/AGP/KSP/JDK/SDK/Xcode and other stable dependencies are frozen during implementation planning | Fits the intentionally unscaffolded repository and frozen `:196–250`; current official research links and dates are in [research pack R7–R9](02-research-pack.md). A compiled compatibility gate precedes feature work | A custom server or enterprise module/DI framework adds cost and boundaries without serving this scope |
| Keep Android WorkManager's single bounded attempt and foreground fallback; keep iOS UserNotifications plus foreground Core Location and WidgetKit | Android API 26+, iOS 16+ proposed minimums; implementation toolchain must confirm library support | Frozen `:65`, `:100–108`, `:254–274`; Android background limits and Fused Location behavior were fetched 27 October 2026 in [R1–R3](02-research-pack.md) | Exact alarms, persistent services, periodic polling, and iOS background scheduling would change the chosen behavior and still do not establish a successful timely fix |
| Automatically drain iOS reports at foreground execution opportunities; retain Android's separate network-constrained work | Ktor Client Android/Darwin, stable compatible versions selected at implementation gate; Sheets REST v4 | Local durability and lifecycle recovery are sufficient for the optional report. Apple DTS [execution limits](https://developer.apple.com/forums/thread/685525) (published July 2021, revised January 2026) and [upload distinction](https://developer.apple.com/forums/thread/838523) (July 2026), fetched 27 October 2026, support explicitly defining this lifecycle | A dedicated background URLSession transfer design could extend execution, but adds credential/file-transfer lifecycle work without a product requirement for closed-app report freshness; no guaranteed refresh schedule is substituted |
| Treat Sheets as an eventually convergent app-managed projection, repaired from local truth | Sheets REST v4, existing `drive.file` native authorization | Keep deterministic date rows and conditional local acknowledgements from frozen `:288–296`; add uncertainty and read/repair. [Sheets limits](https://developers.google.com/workspace/sheets/api/limits), updated 3 September 2026, fetched 27 October 2026, does not establish conditional revision writes; the consistency limitation is an inference | A transactional backend could enforce remote conditional writes, but violates the no-backend scope. Claiming a revision cell itself enforces ordering is unsupported |
| Encode all user text and canonical strings literally; keep template formulas separate | Sheets v4 `RAW` / explicitly typed cells | [ValueInputOption](https://developers.google.com/workspace/sheets/api/reference/rest/v4/ValueInputOption), updated 21 March 2025, and [values guide](https://developers.google.com/workspace/sheets/api/guides/values), fetched 27 October 2026 | Applying `USER_ENTERED` indiscriminately permits locale-dependent parsing and formula interpretation of notes/names |

These are design choices, not claims that an application has been compiled, store-approved, or device-tested. Stable patch selection remains the implementation planner's task; a version incompatibility discovered there is resolved before committing the catalog, and a required support-floor/product change returns for review.

## 3. Hard boundary shapes and failure semantics

These are logical payloads, not prescribed Kotlin interfaces or a new framework. Use the smallest implementation that preserves their invariants.

### Attendance/location boundary

```text
DayKey = { trackerId: UUID, attendanceDate: ISO LocalDate }
CheckRequest = {
  dayKey, mode: AUTOMATIC | USER_INITIATED,
  expectedPlanVersion, expectedPlaceVersion,
  attemptId, expiresAt: UTC Instant
}
LocationResult =
  Fix { latitude, longitude, horizontalAccuracyMetres,
        capturedAt: UTC Instant, receivedAt: UTC Instant,
        nativeFreshnessEvidence }
  | Failure { reason: PERMISSION_DENIED | SERVICES_DISABLED |
                      TIMEOUT | UNAVAILABLE | CANCELLED }
```

Native freshness evidence is adapter-owned, using the platform's timestamp/elapsed-time facilities where available; shared rules must reject a sample that cannot establish the approved freshness window. A fresh location request has at most the approved 30-second budget, and a success must meet frozen accuracy/radius/window rules. Outside-area, stale, and inaccurate fixes become unsuccessful domain verdicts. A capture before arrival cannot establish attendance even when delivered after arrival. Do not use tap time to manufacture a confirmation time.

Coordinates are ephemeral inputs to the verdict and are discarded afterward; stored evidence contains confirmation time/source and relevant verdict metadata, not a movement trace. Registration retains only the approved registered place data.

Before committing a success, transact against the authoritative day, current overlay, correction precedence, and referenced plan/place. Claim automatic attempts atomically before location acquisition. A process death after claim consumes the one attempt; record an interrupted diagnostic when recoverable and offer manual check. Native cancellation never resets it. Day rollover, changed state, time off, or incompatible/stale request identity cannot commit an obsolete verdict. A failed check never downgrades existing success. All frozen historical/time-off/balance rules continue unchanged.

### Widget and navigation boundary

```text
QuickCheckRoute = { schemaVersion, action: QUICK_CHECK, trackerId }
WidgetSnapshot = {
  schemaVersion, trackerId, generatedAt,
  attendanceDate, effectiveLocale, displayStatus,
  placeLabel, deadlineDisplay?, quickCheckRoute
}
```

Publish the small snapshot atomically through the existing native mechanism; no token or database is shared with the widget. Route/snapshot fields are hints, not attendance evidence. On opening, resolve the current tracker/date and re-evaluate authoritative state before showing or running the normal foreground flow. A stale/unknown version or deleted tracker falls back to current Today/setup. Location permissions, early-check rules, and the fresh-fix requirement still apply. Duplicate opens share the ordinary idempotent check path.

### Durable reporting boundary

```text
ReportBinding = {
  trackerId, connectionGeneration: UUID, accountReference,
  spreadsheetId, schemaVersion, annualTabIds
}
ReportRow = {
  dayKey, revision, effectiveStatus, source, planTimes,
  confirmedAt?, note?, updatedAt, offReason?
}
ReportBatch = {
  batchIdentity: UUID, connectionGeneration, spreadsheetId, schemaVersion,
  rows: [ReportRow], affectedTimeOffProjection,
  affectedOverviewProjection
}
PublishOutcome =
  Acknowledged { batchIdentity, rowRevisions }
  | Retryable { retryAfter? }
  | Ambiguous { batchIdentity }
  | AuthorizationRequired
  | SchemaConflict
  | ReportUnavailable
```

These shapes supplement the complete annual row fields in frozen `:284`; they do not remove fields. Native adapters own credential storage/refresh and supply authorized requests without persisting raw credentials in Room. `accountReference` is an opaque account identity, not a token.

Every local attendance/time-off/configuration change commits its authoritative state plus dirty projection revisions in one transaction. Dirty scope covers affected dates and the Time off/Overview projections; a removed time-off entry must clear/rebuild its old report cells rather than leave stale leave visible. A complete projection write clears obsolete optional values. User strings, ISO dates, status codes, UUIDs, and notes use literal values; numeric counts remain numeric. Template formulas, if needed, are app-authored and separate.

One drain owns a binding at a time. Before dispatch, durably journal the batch identity, connection generation, row revisions, and in-flight state; a process restart treats any unacknowledged in-flight journal entry as ambiguous. Android additionally runs separate network-constrained sync work. On iOS, launch/resume, local changes while active, successful reconnect, and Retry automatically request a drain. An active-app network recovery may resume it. Ordinary iOS publishing may stop on backgrounding; persist unfinished/uncertain batches and resume on the next active opportunity. Show “saved on this phone / pending upload” until confirmed, with last successful sync/error. This is automatic outgoing sync without an extra upload tap; it promises no closed-app iOS freshness deadline. No background location or new background mode is added.

On a known successful write, acknowledge only revisions still current in the database and belonging to the active connection generation. A newer edit remains dirty. A timeout, connection loss after dispatch, or process death with an in-flight journal entry has an ambiguous server outcome: retain durable reconciliation-needed state. Build retries from the latest local projection, never from an obsolete queued snapshot.

At each new sync session after launch/resume/reconnect, perform quota-batched verification of app-managed tracked rows/projection identity and revisions, including previously acknowledged rows, then repair differences from current local truth. This catches an older uncertain request that finishes after an earlier observation. A readback is not a lock or a proof that another outstanding request cannot finish later. Preserve ambiguity diagnostics and describe the report as eventually convergent, conditional on later execution/connectivity and outstanding writes settling. The app never imports remote attendance or claims strict monotonic remote revision visibility. The UI's synced time describes the last observed acknowledgement/check, not a perpetual guarantee.

Transient/quota errors use the existing bounded jittered backoff and pending state; authorization problems pause for reconnect; schema conflicts pause for the existing rebuild/new-report choice. Do not overwrite unrelated tabs. Batched reconciliation is subject to project quota and must yield rather than perform unbounded retry loops. A supported foreground session may need several batches for long history; exact batch sizes belong in implementation planning.

Disconnect first invalidates the connection generation and prevents new dispatches, then cancels local jobs and removes native authorization state. It preserves local records and the report. An already accepted remote request may still complete; stale callbacks cannot acknowledge or mutate a later binding. New installation identity remains isolated from the old report. Local deletion retains its explicit confirmation and cancels the existing schedules/reminders/sync as required by v1.

## 4. Milestones and verification sequence

This is the ordering contract for the subsequent implementation plan, not authorization to implement now. Keep all frozen section 13 acceptance checks; the gates below add sequencing and specific missing cases.

1. **Compatibility and highest-risk native probes.** After design approval and the separate implementation plan, freeze stable toolchains, build minimal Android and iOS hosts, and exercise one foreground location call on each; Android delayed/background work with the chosen permissions; one iOS reminder/widget launch; and narrow-scope native Google creation/write. Verify on physical devices without relying on debugger execution. Record what can fail and demonstrate manual recovery. Toolchain compilation, OAuth provisioning, and Play policy feasibility are observed gates, not assumed facts. If acceptable behavior requires a changed workflow/permission/support floor, stop that integration and return to design review.
2. **Authoritative rules and transactional persistence.** Implement frozen date/leave/status rules and durable schema before adding native scheduling/report side effects. Verify inclusive/zero grace, capture timing, DST, historical snapshots, retrospective/current/future overlays, deduction differences, simultaneous edits, consumed attempt after process death, and current-revision outbox state. Kill/restart at transaction boundaries and show no duplicate deduction or lost local correction. Verify migrations without destructive fallback before any real-user data.
3. **One complete daily flow on each platform.** Connect setup, registration, Today, correction, schedule/time off, native check/reminder/widget, and permissions. Verify an Android delayed attempt skips GPS, iOS opening performs a real foreground check, stale routes re-evaluate today, and off/corrected states win concurrent commits. Demonstrate the finite iOS reminder horizon and ordinary background suspension. These flows use real storage, not prototype success messages.
4. **Optional reporting under failure.** Verify create/connect, literals such as `=1+2`, offline edits, correction during upload, process death after dispatch, quota responses, stale older write after newer acknowledgement, next-session repair, deleted time-off clearing, schema damage, reconnect/account change, and disconnect during upload. Reopen iOS with queued data and show automatic drain/pending semantics. There must be one date row and convergence to current local revisions after injected outstanding writes settle.
5. **Release candidate and distribution decision.** Complete v1's physical Android manufacturer/battery tests, iOS release-build journeys, EN/AR and RTL/large text/native widget/notification verification, privacy/backup review, secret/license checks, and distribution/account inputs. Record observed limitations in user-facing language. Only then seek the separate release/distribution instruction; design approval does not publish anything.

## 5. Risks, rollout, and rollback

The hardest external risks remain Android background permission/store acceptance and missed timely fixes; physical-device evidence may show the accepted best-effort mode is insufficient. The escape hatch is design review with that evidence, not silently adding exact alarms, a persistent service, geofencing, or extra automatic attempts. Keep manual checking usable when permission is denied, as already specified.

iOS foreground sync can leave a correct local record pending until reopening. This is explicit in the health view and design approval; it must not be obscured by “synced” language. Remote ambiguity can temporarily stale the report, which remains subordinate to local truth. Schema damage pauses publishing and offers the already agreed rebuild/new-report workflow. No paid quota upgrade or hosted recovery service is introduced.

Roll out through development/internal device validation first; a broader test/release cohort and store publication require the user's later distribution instruction. No production migration exists today. Before retaining real data, verify schema upgrades and an incompatible-downgrade refusal: do not use destructive migration or install an older binary over a newer schema as a rollback strategy. Prefer a forward-compatible fix.

On an integration failure, use the existing automation/reporting settings and local cancellation to stop further side effects while retaining authoritative records and dirty revisions. A reporting pause does not erase credentials/data unless the user chooses Disconnect/Delete. Cancelled/accepted remote requests follow the uncertainty semantics above. Preserve existing Google reports on local reinstall/deletion. A narrower shipped platform mode or changed attendance rules require renewed design review, not an operational toggle presented as the original release.

## 6. Genuine human product questions

No additional product direction is invented here. The user has approved the incorporated section-4 defaults, including radius/accuracy, before-arrival behavior, zone/defaults, and device floors. Approval also accepts the explicit iOS report freshness contract at foreground opportunities.

After native probes, does the measured Android best-effort miss rate and manual recovery experience satisfy the intended use? This is a genuine product decision to make from device evidence; if the answer is no, revisit the workflow before completing the implementation. The review cannot decide that preference for the user.

What final public name/application identifiers and distribution/account route should be used? These remain release inputs from frozen `:7` and `:327`, not prerequisites for the separate implementation plan. No accounts are enrolled and nothing is published by accepting the design.

**Readiness:** v2 is approved for DESIGN with the complete incorporated v1 contract and these explicit amendments. The next deliverable is the implementation plan. It is not approved to ship, and no native API/toolchain behavior has been proven by writing this document.
