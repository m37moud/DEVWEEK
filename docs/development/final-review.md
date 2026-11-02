# Independent review and verified fix pass

One fresh-context `gpt-6-astra` reviewer inspected the whole branch from `deb9b59` through candidate `c8e503f`, read-only. No critical issue was identified. The reviewer found six Important issues and two Minor issues. The author accepted the six Important issues and completed one regression-tested fix pass; no second reviewer or re-review was used.

| Important finding | Result and regression evidence |
| --- | --- |
| One verification read per historical date could exhaust quota before any upload | Flattened rows across projection boundaries into 100-range reads; at most five data reads/pass, ten-second yields, successful-prefix continuation after transient failure. A new foreground session rechecks clean prefixes. A 600-day quota/continuation regression failed before the change and passes. |
| Retry button could not recover after six transients | Explicit USER_RETRY starts a new bounded budget; automatic RETRY does not. Future server backoff remains honored. Both coordinator and lifecycle button regressions failed then passed. |
| Oversized notes blocked all reporting | Visible 50,000-unit note/name boundary, field-specific errors, and isolation of legacy unreportable rows. Full local values remain intact, other records upload, and shortening the field restores publication. Starvation and input-validation regressions failed then passed. |
| Overview omitted explicit setup-day records | Materialized attendance is included consistently with History and annual rows. Tests cover explicit Present/Late/Absent corrections after arrival and frozen records across a westward zone cutover. |
| Today/History omitted approved surfaces | Added named place, monthly totals, week strip, off-day next attendance and real report health; History calendar, planned-date distinction and visible confirmation time. Typed read-model tests, active-midnight/cutover regressions and physical EN/AR light/dark 200%-text journeys pass. |
| Time-off previews omitted prior outcomes | Typed before/after snapshots show effective states and retained manual/GPS evidence, using the original attendance zone; expectedRevision fencing remains. Domain and physical preview regressions pass. |

Additional account-ownership coverage prevents discovery from adopting a previous account's report merely shared with the new account. It failed before adding the owner filter and passes afterward. A preview-cancellation/in-flight confirmation regression already passed on the prior implementation; no speculative session refactor was made.

Final verified local gate: **158 JVM +91 shared Android host +16 Android app host +16 Huawei instrumentation tests =281 executed tests; zero failures/errors/skipped.** Android lint: **0 errors, 8 warnings**. Debug/test and unsigned release APKs build; shared iOS source compilation, updated Swift syntax and plist checks pass. This is author verification of the fixes, not a second independent review or native/live release certification.

Deferred Minor findings:

- Overview counts/balance are RAW text cells. They display the correct values but numeric primitives would improve spreadsheet arithmetic/charts.
- School/course vacation-kind entries display Vacation outside Plan rather than category-aware Day off. Effective off-day priority and work-only balance deductions are correct.

The reviewer did not certify user-deferred iOS host/UI, physical iPhone GPS/reminders/WidgetKit/App Group, Huawei launcher pin/tap, modern-Android manufacturer/Doze reliability, live Google OAuth/Drive/Sheets, signed distribution/final identifiers/icon/enrollment, compiled iOS privacy/minimum-OS compatibility, or full visual/glyph/screen-reader acceptance. Excluded multi-device/import/overnight/continuous/payroll/accrual features remain outside scope. Each boundary and its cost is recorded in [implementation decisions](implementation-decisions.md); actual release gates remain open in [release verification](release-verification.md).
