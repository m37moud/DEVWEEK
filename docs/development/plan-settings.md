# Plan and Settings

## Approved screen placement amendment · 18 November 2026

This amends the Today, Plan, and Settings screen placement in [section 7 of the frozen design](../superpowers/reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md#7-product-experience-and-approved-visual-direction), incorporated by [design v2](../superpowers/reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md).

- **Settings** contains the weekly schedule, tracker category, zone, and registered place with its radius, next to Tracking. One review covers both cards. A foreground location reading changes the draft only. A confirmed plan starts on the next attendance-zone day. Today's and historical expectations stay intact.
- **Plan** opens **Time off**. Add, edit, and remove time off for past, current, and future dates. Reviews show attendance outcomes and vacation balance impact. A new single-day entry follows the chosen From date until an end date is selected. Today and History open the editor for the selected date. When entries overlap, the editor opens the holiday first.
- **Today** shows Original balance, Used, and Remaining for Work below the attendance summary. Used includes eligible future vacation booked against the balance. Remaining includes any balance adjustment, which can be recorded from the same card. The three figures come from one local snapshot.

The controller retains the reviewed command/revision and clears its preview after apply or a stale result. It does not silently retry a stale confirmation. Invalid values, storage failures and insufficient balance remain distinct from a successful local save. Location registration uses the same freshness/accuracy validation as setup, with one bounded foreground request.

Settings persists the display name, System/EN/AR language, theme and explicit iOS reminder opt-in. Android automation has separate user-triggered consent: foreground precise permission, background permission and location services are required before enabling it. No ordinary refresh requests location or permissions. Denial retains manual attendance and local history.

Application foreground/configuration hooks rebuild Android scheduling and opted-in iOS reminders through the actual adapters. Native failures update health without changing the local commit. Successful iOS replacement exposes its 30-calendar-day planning horizon. The configuration coordinator has a typed widget-refresh hook; native widget cache publication is installed in the subsequent widget task and is not reported as successful before that exists.

Evidence: real JVM SQLite tests cover next-day plan changes, stale revision fencing, historical vacation replacement/removal, balance deltas, persisted names/language/theme, denied automation, Arabic reminder payload replacement and the widget locale hook. Android instrumented journeys use a separate temporary database and exercise zone preview/cancel, vacation add/remove through History, and persisted Arabic/dark theme without changing canonical attendance identity.

The iOS Plan and Locale journeys are registered and syntax checked but have not run. They require a dedicated configured QA profile; the Plan journey reviews/cancels rather than modifying a personal record. Host linkage and native rendering remain open under the user's explicit deferral. Physical iPhone GPS/reminder/widget verification remains open independently. Live Google connection/reset controls are implemented in their later lifecycle task.


## Annual Work vacation balance amendment · 18 November 2026

This amends the running vacation balance in [section 6 of the frozen design](../superpowers/reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md). Tracked dates, eligible vacation deductions, and attendance outcomes follow the existing rules.

Work trackers can set a separate whole-day annual allowance and opt into carryover in Settings. The first renewal and later policy edits start on January 1 in the attendance time zone. Until a policy is configured, the original running balance remains in effect. When configured, future bookings on or after the first renewal are reserved in their own calendar years. Upgraded adjustments are retained in the original balance period.

Each renewal grants the saved allowance and adds the positive remaining balance of the previous period when carryover is enabled. Confirmed historical edits can affect later carryover. Reviews and settings changes identify the affected year when they would create or worsen a shortfall. Today shows the current balance period and Sheets Overview publishes the same balance on sync.


## Time off before tracking began

This amends the time-off date boundary in [section 6 of the frozen design](../superpowers/reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md#6-schedule-days-off-and-vacation-balance). For example, a Work tracker set up on 21 November 2026 can record vacation for 19 November 2026. Earlier entries appear in Plan and the outgoing Time off tab. They do not create attendance records or extend the attendance calendar. Saved earlier vacation appears in the Leave KPI and its detail screen, where it can be opened in Plan. For a tracker initially set up as Work, each distinct earlier Vacation date uses one day of the original balance unless covered by a Holiday. Tracked dates in the same range keep their saved plan rules. Reviews show the earlier dates and balance impact before saving.

## Attendance calculation periods

The approved custom-period amendment to the frozen design groups date-based attendance into inclusive reporting periods labeled by their closing month. Calendar months are the default. A custom day closes each month on that day or the month's last day, whichever comes first. The cutoff applies to all saved history, Today metrics, and the managed Attendance periods report tab. Date keys, evidence, and attendance windows retain their original meaning.

Work vacation renewals default to calendar years. Users can opt into attendance-period years, beginning at the first day of the January period. Before applying a change, Daymark compares the affected annual balances, retains the year on each saved adjustment, and rejects changes that create or worsen a shortfall. The original running-balance rules continue to apply to dates without annual policy coverage.

Setup offers the attendance period after Profile, and the same monthly rule can be edited in Settings. Start and end each open a calendar. Selecting either sets the other to the adjacent boundary so every date belongs to one period. Calendar selections save recurring day numbers. The month and year shown in the picker are used to preview how that rule falls on actual dates.

The setup period editor also lets users choose the annual vacation year. Setup saves that choice for Work trackers. The editor compares example calendar-year and attendance-period-year start dates, while annual allowances remain configured separately.
