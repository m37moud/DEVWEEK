# Plan and Settings

## Approved screen placement amendment · 18 November 2026

This amends the Today, Plan, and Settings screen placement in [section 7 of the frozen design](../superpowers/reviews/2026-10-27-attendance-mvp/01-design-v1-frozen.md#7-product-experience-and-approved-visual-direction), incorporated by [design v2](../superpowers/reviews/2026-10-27-attendance-mvp/04-promoted-design-v2.md).

- **Settings** contains the weekly schedule, tracker category, zone, and registered place with its radius, next to Tracking. One review covers both cards. A foreground location reading changes the draft only. A confirmed plan starts on the next attendance-zone day. Today's and historical expectations stay intact.
- **Plan** opens **Time off**. Add, edit, and remove work across the tracked period, including past dates. Reviews show attendance outcomes and vacation balance impact. A new single-day entry follows the chosen From date until an end date is selected. A range starting before tracking began gets a specific rejection. Today and History open the editor for the selected date. When entries overlap, the editor opens the holiday first.
- **Today** shows Original balance, Used, and Remaining for Work below the attendance summary. Used includes eligible future vacation booked against the balance. Remaining includes any balance adjustment, which can be recorded from the same card. The three figures come from one local snapshot.

The controller retains the reviewed command/revision and clears its preview after apply or a stale result. It does not silently retry a stale confirmation. Invalid values, storage failures and insufficient balance remain distinct from a successful local save. Location registration uses the same freshness/accuracy validation as setup, with one bounded foreground request.

Settings persists the display name, System/EN/AR language, theme and explicit iOS reminder opt-in. Android automation has separate user-triggered consent: foreground precise permission, background permission and location services are required before enabling it. No ordinary refresh requests location or permissions. Denial retains manual attendance and local history.

Application foreground/configuration hooks rebuild Android scheduling and opted-in iOS reminders through the actual adapters. Native failures update health without changing the local commit. Successful iOS replacement exposes its 30-calendar-day planning horizon. The configuration coordinator has a typed widget-refresh hook; native widget cache publication is installed in the subsequent widget task and is not reported as successful before that exists.

Evidence: real JVM SQLite tests cover next-day plan changes, stale revision fencing, historical vacation replacement/removal, balance deltas, persisted names/language/theme, denied automation, Arabic reminder payload replacement and the widget locale hook. Android instrumented journeys use a separate temporary database and exercise zone preview/cancel, vacation add/remove through History, and persisted Arabic/dark theme without changing canonical attendance identity.

The iOS Plan and Locale journeys are registered and syntax checked but have not run. They require a dedicated configured QA profile; the Plan journey reviews/cancels rather than modifying a personal record. Host linkage and native rendering remain open under the user's explicit deferral. Physical iPhone GPS/reminder/widget verification remains open independently. Live Google connection/reset controls are implemented in their later lifecycle task.
