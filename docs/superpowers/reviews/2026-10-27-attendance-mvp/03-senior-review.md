# Senior Review

Review date: 27 October 2026. Reviewer: context-isolated Kotlin/Compose Multiplatform mobile architect, given the frozen artifact and research pack without the author's reasoning.

## Review target and evidence

The complete reviewed artifact is [01-design-v1-frozen.md](01-design-v1-frozen.md), preserved verbatim from commit `72ec628`, original path `docs/superpowers/specs/2026-10-27-attendance-mvp-design.md`. Both files were independently checked and have SHA-256 `99843eeb6012ddb80a51c114daf5cd82054d99735a768bf278cc98a9d4e20e53`. The frozen text, rather than a conversational summary, is the review target. Neither original has been edited.

Repository facts and current primary-source research are recorded in [02-research-pack.md](02-research-pack.md). I also inspected the repository file inventory and the prototype's simulated attendance/connection flows at `docs/superpowers/specs/assets/daymark-concept.html:104–109` and `:124–135`. There is no native app here to compile or device-test. That is an acknowledged project stage, not a defect.

Load-bearing domains are Android execution/location; iOS lifecycle/reminders/widgets; native Google authorization and Sheets publishing; attendance/date/leave transactions; and CMP/Arabic platform integration. The research pack states the senior standard for each and records fetched sources and dates. Additional sources fetched independently on 27 October 2026 are linked beside the findings below. Retrieval date is not publication date.

## Altitude diagnosis

**Mixed, with narrow fog at integration boundaries.** Product intent, exclusions, attendance rules, corrections, time-off transactions, historical snapshots, and platform timing limitations are unusually concrete. Sections 9–11 name the right integration responsibilities but leave the iOS publisher's execution owner and an ambiguous-write recovery contract undecided. Section 13 is a strong acceptance checklist; it is not yet a risk-first sequence or rollback plan. Exact DAO signatures and a compiled version catalog appropriately belong to the subsequent implementation plan.

## Blockers

None. The agreed Android mode is explicitly best effort, not a promise of a precise automatic check: frozen `01-design-v1-frozen.md:17`, `:53`, `:57`, `:100–106`, and `:262`. Background location throttling is a feasibility risk already accepted by the selected workflow. Missing native scaffolding and uncompiled version pins are not blockers for design approval.

## Major

### [M1] Define when iOS actually drains the durable reporting queue

**Evidence:** frozen `01-design-v1-frozen.md:27`, `:218`, `:229`, `:258`, `:266–270`, `:290`, and `:294`. The strongest version of this design is sound: local-first records plus an optional serialized publisher needs no server. However, the only named background sync owner is Android work; iOS specifies foreground location and notification delivery but no execution owner, retry opportunity, or suspension semantics for the shared publisher. An engineer must decide whether “automatic outgoing sync” means foreground opportunities, finite background completion, or a new background transfer architecture. That choice materially affects report freshness and durable recovery.

**Current evidence:** [Apple DTS, iOS Background Execution Limits](https://developer.apple.com/forums/thread/685525), originally 22 July 2021, change history through 9 January 2026, fetched 27 October 2026, explains suspension after backgrounding and the lack of a general clock-based execution guarantee. [Apple DTS on background uploads](https://developer.apple.com/forums/thread/838523), July 2026, fetched 27 October 2026, distinguishes background transfers from ordinary sessions. A Ktor Darwin engine choice alone does not specify either lifecycle contract; this is an architectural inference, not an observed app bug.

**Fix:** explicitly select automatic foreground-opportunity publishing for the MVP: launch/resume, local commits while active, reconnect, and Retry trigger one serialized drain. On suspension/cancellation retain unfinished work durably; resume from current local revisions next time. Explain pending uploads in the UI. Android additionally uses its separate network-constrained work. Do not introduce a promised closed-app iOS upload deadline or mandatory background transfer system. This clarifies an omitted execution contract while preserving the chosen attendance workflow.

### [M2] Duplicate-free date rows do not specify recovery from an ambiguous older write

**Evidence:** frozen `01-design-v1-frozen.md:284`, `:288–290`, `:294`, and `:316`. Deterministic rows correctly prevent duplicate appends, and the conditional local sync acknowledgement correctly protects a correction made during an observed upload. The gap is narrower: a client timeout/process loss may leave an earlier request's server outcome unknown. Serializing client calls does not establish a server ordering contract after that uncertainty. A possible failure model is an uncertain revision N, a later accepted N+1, then an older outstanding write taking effect. The file never defines detecting or repairing that state once N+1 has been marked clean. This is a contract risk, not evidence that Google necessarily reorders these writes.

**Current evidence:** [Sheets API limits](https://developers.google.com/workspace/sheets/api/limits), updated 3 September 2026, fetched 27 October 2026, documents per-request atomicity and a processing timeout; it does not promise compare-and-swap on the application's revision column. The distinction between one-request atomicity and cross-request ordering is an inference from that documented model.

**Fix:** persist ambiguous batches as needing reconciliation; never replay an old serialized payload as the authoritative next state. At subsequent sync sessions read app-owned report identity/revisions and compare with current local projections; repair differences, including already acknowledged rows, from local truth. Define Sheets as an eventually convergent report conditional on future execution/connectivity and the cessation of outstanding writes. A readback is an observation, not a remote lock. Keep uncertainty visible and do not promise monotonic remote reads or immediate cancellation of a request already accepted by Google. Add a fault-injection test in which an older uncertain write is applied after a newer acknowledgement and the next session repairs it.

## Minor

### [m1] Specify literal spreadsheet values

**Evidence:** frozen `01-design-v1-frozen.md:282–284`, `:290`, and `:325` describe human-entered names/notes and machine dates but omit their interpretation at the Sheets boundary. No existing formula-execution vulnerability is demonstrated because there is no publisher yet.

**Current evidence:** [ValueInputOption](https://developers.google.com/workspace/sheets/api/reference/rest/v4/ValueInputOption), updated 21 March 2025, and [Read/write values](https://developers.google.com/workspace/sheets/api/guides/values), both fetched 27 October 2026, distinguish literal input from UI-style interpretation, including formulas.

**Fix:** write user text and canonical identifiers/dates as `RAW`, numeric counts as numbers, and any app-owned formulas through a separate trusted template operation. Verify a note beginning `=1+2` remains literal. This also protects locale-independent report fields.

### [m2] Name the small native boundary payloads and their failure outcomes

**Evidence:** frozen `01-design-v1-frozen.md:70–71`, `:218`, `:245–248`, and `:270` name the contracts but do not specify the location result/failure envelope, widget snapshot version, or connection-generation fence. The adjacent rules already answer most behavior questions, so this is a modest handoff gap rather than an entirely unspecified component.

**Fix:** add data sketches for an ephemeral fix with capture/freshness evidence, a bounded failure result, versioned route/snapshot, and report batches carrying tracker/connection identity and revisions. A stale widget route must reopen current state; a callback from an old connection cannot acknowledge the replacement connection. Preserve existing location/time-off precedence; do not introduce an interface framework.

### [m3] Give the implementation handoff a risk-first order and a non-destructive exit

**Evidence:** frozen `01-design-v1-frozen.md:237–239`, `:306`, `:308–319`, and `:327–329`. Deferring a detailed implementation plan is correct. The remaining small gap is that the acceptance list does not say which risky assumption to falsify first, or how to stop a failed integration without endangering user records.

**Fix:** make compatible Android/iOS builds and native lifecycle/location/auth probes the first implementation gate; then establish transactional rules/storage, then reports/widgets and full UI. Before real data, test migrations and reject incompatible database downgrade instead of destructive recreation. Stage distribution only after existing real-device/Arabic/privacy gates. On failure pause automation or reporting while preserving local records, and seek design review if the promised platform mode must change. Do not interpret rollback as permission to publish or as permission to silently remove Android automation.

## What the junior got right

- Personal-use scope, one tracker/place/phone, no hosted backend, optional outgoing Sheets, and explicit exclusions are clear (`:11–29`, `:41–43`). Preserve them.
- Android claims are honestly bounded, late automatic samples cannot invent punctuality, and iOS uses the selected reminder/foreground flow (`:100–108`, `:256–268`). No exact alarm, continuous tracking, or geofencing is warranted by this review.
- Transactional leave differences, historical snapshots, effective overlays with preserved underlying attendance, and consumed-attempt semantics address the difficult domain cases (`:106–136`).
- Local-first commits, deterministic report rows, narrow authorization, no mobile secret, and reinstall isolation are appropriate (`:154`, `:280–296`). M2 extends failure recovery without rejecting these choices.
- English/Arabic resources, mixed-direction treatment, native surfaces, and device-level acceptance criteria go well beyond “translate the strings” (`:172–194`, `:318`).
- Current stable dependency research is distinguished from a compiled compatible set (`:237–239`). Room 3 is not rejected on outdated training knowledge; the evidence pack records its stable release.

## Delta summary

The separate [04-promoted-design-v2.md](04-promoted-design-v2.md) keeps the complete frozen product contract by reference and amends only these technical gaps:

- Give iOS sync an explicit lifecycle owner and pending/recovery semantics.
- Separate duplicate-free writes from eventual remote-state reconciliation after ambiguity.
- Define literal value encoding and the small payload/failure contracts.
- Put platform feasibility first and record a data-preserving rollout/rollback gate.

## Open questions for the human

No new product feature or service choice is required to begin the separate implementation plan after design approval. Approval of v2 includes its explicit clarification that iOS automatically uploads at foreground execution opportunities and may show pending changes until reopening.

The genuine later product decision is whether measured Android missed-check rates and permission/store feasibility are acceptable for the already selected best-effort mode. Device evidence must precede that decision; if unacceptable, return to design review rather than quietly replacing the workflow. Final public name/identifiers and intended distribution/accounts remain the release inputs already identified in frozen `:7` and `:327`.

## Approval verdict

**Original v1: not yet ready for DESIGN approval without addressing M1 and M2.** There are zero blockers and two major integration-contract gaps; this is not a claim that the product or chosen native APIs are impossible.

**Promoted v2: ready for DESIGN approval as a proposed amendment.** It remains unapproved until the user accepts it. Design approval authorizes the agreed next planning step; it is neither approval to scaffold/ship/publish nor proof that native location, scheduling, OAuth, toolchain compatibility, or RTL work on devices. Those require the later verification gates.
