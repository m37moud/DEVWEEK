# Today KPI alignment implementation record

Spec: [approved design](../specs/2026-11-16-today-kpi-alignment-design.md).

1. Share snapshot calendar projection and boundary calculation between History and Today; replace TodayDashboard counters with HistoryMetrics; preserve week/next attendance and report interfaces.
2. Share the four-tile grid and labels, update Today previews and callbacks, and push existing month detail routes while retaining Today beneath them.
3. Verify parity, no-write deadline/midnight refresh, lifecycle behavior, saved-future-date exclusion, and EN/AR zero-GPS navigation. Run relevant checks, task-owned IntelliJ formatting and guard review; record [actual results](../../../artifacts/today-kpis/verification.md).

Ruling: pending plan activation also participates in the shared boundary calculation so both readers re-evaluate the saved attendance zone at the activation instant. Report-facing observeMonth/projection behavior is unchanged.
