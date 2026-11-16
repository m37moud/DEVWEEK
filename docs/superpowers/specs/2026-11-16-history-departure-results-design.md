# History details, departure cutoff, and result notices

Status: approved for implementation in this chat on 15 November 2026; implemented verification recorded separately. This amendment preserves the [promoted v2 design](../reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md) and its incorporated frozen v1 except for the explicit additions below. It does not approve publication.

## Attendance amendment

Every category may have an optional same-day departure time, at or after its inclusive grace deadline. Each scheduled weekday can inherit the default, override it, or disable it. Ordinary changes take effect the next attendance-zone day; persisted dates retain their frozen expectations. Weekly rest dates have no cutoff.

GPS requests started after departure are rejected. Capture at departure is accepted, including later delivery that still satisfies existing freshness requirements. Classification and the commit transaction independently enforce capture time. Automatic checks remain restricted to arrival through grace; absence still evaluates after grace. Manual corrections remain available after departure. No actual checkout, overnight shift, hours-worked calculation, or new GPS retry is introduced.

Schedule JSON additions default to no cutoff for old data. Room schema 4 adds nullable attendance departure snapshots with a non-destructive 3-to-4 migration. Existing reports retain their current schema and receive normal status updates.

## History amendment

History exposes four clickable monthly KPIs: Attendance (Present + Late), Late, Absent, and Leave/rest (effective weekly rest, holidays, vacations). A shared details destination starts with the chosen month and accepts an inclusive ordered date range within the tracked period. Dates appear newest first and retain existing correction/time-off actions. Calendar gaps and future leave are projected from local schedule versions and time off without inserting attendance rows. Existing stored-month queries retain their report-facing contract.

Only elapsed/current dates contribute to attendance/late/absence. Future leave/rest has a separate planned count. Leave precedence is unchanged; School/Course vacation keeps its actual leave reason even when its display status is Day off.

Effective Late duration is max(confirmed arrival minus scheduled arrival, zero). Manual statuses are authoritative. Durations retain seconds for sums, display whole minutes rounded down, and show positive durations shorter than one minute as “Less than 1 minute.”

## Notification amendment

Registration result notices are opt-in, initially off, and require native notification permission. Android notices follow an actual eligible automatic attempt; iOS notices follow an actual foreground GPS attempt. They are silent and replace the same tracker/date result. Success is reported only after local acknowledgement; failures show unconfirmed attendance and an evidenced reason. Busy, duplicate, obsolete, cancelled, and ineligible outcomes remain silent. Native delivery is best effort, with no new scheduler and no promise when Android work never executes.

Before delivery, current tracker/day/outcome are checked again. Disable and local deletion clear owned result notices while preserving arrival reminders. Tapping opens current Today without requesting GPS. Delivery errors cannot roll back attendance or retry location. Text is localized and contains no notes, coordinates, or credentials. Desktop has no notification adapter.

## Approach and exclusions

Extend the existing schedule, repository, MVVM screens, Koin bindings, and native adapters. A statistics table or separate daily notification scheduler would duplicate existing local truth without serving this scope.

Excluded: Today balance card, unrelated History navigation changes, percentage/comparison analytics, charts, actual checkout, overnight attendance, Google schema expansion, export, and publication. Verification and remaining native gates are in [the verification record](../../../artifacts/history-departure-results/verification.md).


### KPI amendment — 16 November 2026

The user approved five shared Today/History KPIs: Attendance, Late, Absent, Leave, and Official holidays. This supersedes the four-KPI and planned-count presentation above. Leave counts effective vacation, including School/Course DAY_OFF with VACATION reason; plain weekly rest is excluded. Official holidays count effective HOLIDAY separately. Every count and detail list excludes future dates; future time off remains in Plan. The fifth card occupies one column with an empty adjacent slot. Preserve the serialized LEAVE_REST route value and existing attendance, balance, persistence, and reporting rules. See [Today KPI alignment](2026-11-16-today-kpi-alignment-design.md).
