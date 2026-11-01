# Attendance screens

Today observes Room outcomes and uses the repository's arrival/deadline transitions. It schedules one attendance-zone midnight refresh while started, cancels its observer and in-flight foreground check when stopped, and never polls GPS. A check requests foreground precise permission only after a tap. Repeated taps coalesce. New installations do not display an untracked baseline as an absence; an explicit manual check can still create today's record.

History starts with Today's attendance-zone month. Month navigation reads persisted records, including prior years. Present/Late corrections require a time and retain the explicitly chosen status. Notes are stored verbatim, with directional isolation only for display. Holiday/vacation/weekly-off rows direct users to Plan instead of applying an attendance correction over an active overlay.

Formatting and translated source/status labels are derived in Compose from typed state and the current app language. Dates and evidence codes remain canonical in storage; changing locale does not run GPS or recreate a correction form. Existing repository transactions fence concurrent edits, reconcile overdue rows, and write dirty projections atomically.

Local verification includes fake-clock controller tests for grace deadlines, active midnight rollover, stopping and resuming, duplicate taps, late and rejected fixes, and untracked baselines. Real SQLite tests cover deadline persistence, cancellation, explicit status corrections, literal mixed-direction notes, monthly counts and reopen. The Android instrumented journey exercises the screens against a temporary on-device database.

The iOS journey is registered with the existing UI-test target but has **not run**. It requires an eligible preconfigured tracker and a location within the registered place. The user elected to retain both installed simulator runtimes and defer iOS UI/host build checks due to disk constraints. Kotlin source compilation and Swift syntax checks do not establish host linkage, visual correctness, notification delivery, physical GPS or widget behavior.
