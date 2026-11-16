# Today KPI alignment

Status: approved for implementation in this chat on 16 November 2026. Extends the [History/departure/results amendment](2026-11-16-history-departure-results-design.md) with Today presentation parity; attendance evidence, reporting, and notification contracts remain unchanged.

Today uses History's shared two-column grid under “This month”: Attendance (Present + Late), Late (days and total duration), Absent, Leave (elapsed effective vacation only, including School/Course DAY_OFF with VACATION reason), and Official holidays (elapsed effective HOLIDAY only). Weekly rest and future dates are excluded from these counts and detail lists. No planned-days counter is shown. The fifth tile keeps one column width with an empty adjacent slot. Zero counts remain actionable. Each tile opens the existing detail screen using the displayed dashboard attendance-zone month. Back returns to Today; the History button opens monthly History as before. Unavailable or stale dashboard data cannot navigate.

Both readers use the same frozen-row-first calendar projection, setup clipping, time-off precedence, manual-status rules, and historyMetrics calculations. Future dates stay out of attendance/late/absence totals, even if an attendance-zone change makes a saved date temporarily future. Saved evidence, week status, and report-facing totals remain intact.

TodayDashboard holds HistoryMetrics instead of three loose counters. The reader refreshes at arrival, immediately after the inclusive grace deadline, attendance-zone midnight, and pending configuration activation; collection and timers stop when Today is inactive. Reads do not persist synthetic attendance or request GPS. No polling, schema, dependency, balance card, or new analytics.

Use the existing shared tokens, localized labels/duration text, previews, and independent accessible tile actions. Verification covers parity, calendar transitions without writes, lifecycle cancellation, EN/AR direct details and Back navigation, zero GPS, build/lint, IntelliJ formatting, and clean-code-guard. See [verification](../../../artifacts/today-kpis/verification.md).

Amendment approved in this chat on 16 November 2026: separate elapsed vacation and official-holiday KPIs on Today and History. Keep the serialized LEAVE_REST route value for vacation details. Future time off remains available in Plan; persistence, attendance rules, vacation balances, and report totals are unchanged.

Verification for this amendment: [leave and holiday KPIs](../../../artifacts/leave-holiday-kpis/verification.md).
