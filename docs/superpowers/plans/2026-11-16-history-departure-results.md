# History, departure, and result notices implementation record

Spec: [approved design amendment](../specs/2026-11-16-history-departure-results-design.md). User requested implementation of the complete plan in this chat. Work remains uncommitted on `codex/history-departure-notifications`.

1. Extend schedule/DayPlan and JSON codecs; add Room 3→4 migration; enforce cutoff at request, verdict, and commit boundaries; expose Setup/Plan controls and Today/widget feedback.
2. Add read-only range projection without changing the existing stored-month/report contract; implement KPI calculations, shared detail ViewModel/screen, inclusive date filters, correction actions, localized labels and previews.
3. Persist notification opt-in using existing settings; wire focused notifier and Android/iOS adapters; add permission entry, silent same-date replacement, current-state fencing, deletion cleanup, and safe Today intent.
4. Run focused and existing tests, Android build/lint, available iOS checks, IntelliJ formatting, and clean-code/RF1–RF5 review. Record actual evidence and incomplete native gates in [verification](../../../artifacts/history-departure-results/verification.md).

Rulings: preserve `observeMonth` as the stored-row/report query; the new History calendar uses `observeRange`. Explicit setup-day corrections remain visible even when normal tracking starts tomorrow. Off days have no departure cutoff. A native result notice is a check outcome, not an ongoing live attendance feed.
