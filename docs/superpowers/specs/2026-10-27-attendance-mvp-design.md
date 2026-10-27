# Personal attendance tracker: MVP design

Date: 27 October 2026

Status: Proposed design, awaiting user approval

Working name: Daymark; the final public name and application identifiers remain open.

## 1. Intent and agreed scope

Build a free personal attendance tracker for work, school, or a course. Reduce the effort of maintaining an attendance record while giving the user control over corrections and ownership of an optional Google Sheets report.

The first release targets Android and iOS with Kotlin and Compose Multiplatform. Each installation has one active tracker, one registered place, and one weekly schedule. The MVP serves a person tracking their own attendance; employer administration, official attendance certification, and tracking other people are outside this design.

The user has selected these requirements:

- Android attempts one automatic location check per scheduled day, subject to permissions and system restrictions.
- iOS sends an arrival-time reminder; its home-screen widget opens a foreground, location-verified check.
- Both platforms have the quick-check action. It requires confirmation inside the registered area.
- A successful confirmation within the configured grace period is Present; confirmation afterward is Late. The default grace period is five minutes.
- A scheduled day with no confirmation by its deadline is Absent, including when location cannot be obtained.
- Users can correct records manually. Corrections remain distinct from location-verified checks.
- Users select weekdays, set a default arrival time, and optionally override the arrival time for individual weekdays.
- Weekly days off, recorded holidays, and recorded vacations skip attendance checks.
- Users may add, edit, or remove holidays and vacations for past, current, and future tracked dates. These explicit changes update attendance, vacation balance, and the Google Sheets report.
- Work trackers have one vacation balance with manual adjustments. Vacation deducts scheduled workdays; holidays and weekly days off do not consume the balance.
- Records save locally. Google connection is optional and creates a spreadsheet template with automatic outgoing sync.
- Attendance is managed in the app. Spreadsheet edits are not imported and can be overwritten.
- One phone at a time; simultaneous use across phones is outside the MVP.
- Keep the reviewed calm visual direction: soft white/forest in light mode and evergreen/soft lime in dark mode.
- Support English and Arabic, including Arabic right-to-left layouts.

The current workspace contains a browser concept and its local preview infrastructure, not a built Android/iOS application. The approved English concept establishes visual direction, not proof of device behavior or Arabic layout quality.

## 2. Goals, exclusions, and failure conditions

### Goals

Make daily attendance visible and easy to record; preserve records offline; make corrections understandable; provide an optional useful spreadsheet; minimize background location work; and provide equivalent core functionality in English and Arabic.

### Outside the MVP

Multiple trackers or places, overnight/rotating shifts, check-out and worked-hour calculation, payroll, employer/teacher dashboards, continuous GPS or geofencing, automatic national holiday feeds, partial-day leave, leave accrual/carryover rules, multiple leave balances, simultaneous multi-device sync, spreadsheet-to-app import, and full backup/restore are deferred. There is no paid analytics, map service, hosted application backend, advertising SDK, or mandatory account.

### Failure conditions

The design fails if it invents an on-time arrival from a late location reading, silently discards offline corrections, duplicates spreadsheet records on retry, consumes vacation twice for the same date, rewrites historical attendance after a schedule edit, breaks Arabic layout, or presents background timing or location as guaranteed proof.

## 3. Approaches considered and selected

| Approach | Benefit | Tradeoff | Decision |
| --- | --- | --- | --- |
| Shared CMP app, Android automatic attempt, iOS reminder and foreground quick check | Delivers both platforms while respecting their execution models | iOS attendance requires a user action; Android execution remains best effort | Selected by the user |
| Scheduled automatic attempts on both platforms | Closest to the original fully automatic idea | iOS scheduling is unsuitable for a promised exact-time location check; missing checks cause absences | Not selected |
| Android first, iOS later | Validates automation before an iOS release | Postpones the requested iOS app | Not selected |

Android WorkManager does not promise exact execution, and iOS background task requests do not promise launch at a requested date. A precise GPS fix also takes time. The selected approach accepts that uncertainty and keeps a foreground fallback. [Android work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [Apple scheduling limits](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate?changes=_3).

## 4. Proposed defaults for review

These are designer decisions, not requirements previously supplied by the user. Approving this document approves these defaults as well.

| Topic | Proposed default |
| --- | --- |
| Device support | Android 8/API 26 and iOS 16 or newer; current stable target/compile SDKs at implementation time |
| Language | Follow system language; Settings offers System, English, and العربية; English fallback for unsupported languages |
| Arabic | Modern Standard Arabic, Gregorian attendance dates, locale-aware display formatting, and full RTL behavior |
| Theme | Follow system; Settings offers System, Light, and Dark |
| Registration | User stands at the place and chooses “Use current location”; map search and paid map APIs are unnecessary |
| Allowed area | Default 100 m radius, adjustable from 100–500 m; require a fresh fix with reported accuracy of 50 m or better |
| Location attempt | At most 30 seconds for one location request; sample age at confirmation no more than 30 seconds; discard raw check coordinates after computing the verdict |
| Before arrival time | Show the upcoming check and disable the normal check action until scheduled arrival; historical manual corrections remain available |
| Time zone | Save an IANA attendance time zone at setup, initially the device zone; travel does not silently move the schedule |
| Schedule changes | Take effect on the next attendance-zone calendar day; preserve the original expectations for today and historical dates, while allowing explicit attendance/time-off corrections |
| Leave | Whole-day entries for past/current/future tracked dates; future vacations reserve balance immediately; reject a vacation change if its additional deduction exceeds the available balance |
| Reporting | Monthly Present/Late/Absent totals and a calendar/list; attendance percentages and additional analytics are deferred |
| Spreadsheet | One app-created workbook per tracker, bilingual labels, stable machine fields, and deterministic date rows |
| Restore | Sheets is a report, not an MVP restore mechanism. Reinstalling can lose local state; a new installation must not overwrite the previous tracker’s report |

The location radius and accuracy threshold are practical starting values for personal tracking, not an anti-fraud or indoor positioning guarantee. Device testing may require a change that is brought back for review if it changes the attendance behavior.

## 5. Attendance rules

### A day and its deadline

Each tracked date belongs to the tracker’s attendance time zone and has a stored baseline day plan: weekly scheduled/off classification, category, scheduled arrival, grace duration, deadline, place version, and schedule version. Explicit holiday/vacation entries are editable overlays on that baseline. Store UTC instants alongside the attendance-local date and zone identifier.

Example: arrival 09:00, grace five minutes, deadline 09:05:00. A successful location confirmation at or before the deadline is Present. A successful manual location confirmation after the deadline, on that attendance-local date, is Late. Use the location sample’s capture time, validated as fresh, rather than pretending the button tap itself confirmed location.

| Condition | Result |
| --- | --- |
| Future day or before scheduled arrival | Scheduled; not counted in monthly outcomes |
| Arrival reached, still within grace, no confirmation | Awaiting check; not yet Absent |
| Valid location confirmation within the window | Present |
| Deadline passed with no confirmation | Absent |
| Valid manual location check after deadline | Late; replaces that day’s Absent outcome |
| Location outside radius, unavailable, inaccurate, stale, or permission denied | No successful confirmation; the date becomes/remains Absent after its deadline |
| Weekly day off, holiday, or vacation | Corresponding off-day reason; no attendance attempt and no absence |

The deadline is inclusive. At 09:05:00 a valid confirmation is Present; after that instant it is Late. No app execution is guaranteed at the exact deadline. Compute an overdue day as Absent whenever the app/worker next evaluates it. A spreadsheet can consequently receive an absence later than the deadline.

### One automatic attempt and manual checks

An Android automatic attempt is only eligible during the arrival-to-deadline window. If the system starts it after the deadline or on a later date, reconcile Absent and skip GPS. A later location reading cannot establish presence at the scheduled time. Automatic checks never create Late records in this MVP; Late follows a successful user-initiated check.

Use an atomic per-date claim in local storage to prevent duplicate automatic attempts, including after worker re-delivery. A process killed during a claimed attempt consumes that attempt; preserve a diagnostic reason and offer manual check. Permission failures and location timeouts do not start automatic location retries. Manual checks can be retried by the user.

Do not downgrade an existing Present/Late record with a failed check or inferred absence. Keep the earliest successful confirmation for that date. Repeated taps and widget actions must not create additional records. An explicit manual correction has precedence over later automatic evaluation until the user edits it again. An explicit time-off overlay takes precedence while active; preserve the underlying confirmation/correction so removing time off can restore it.

### Corrections

History allows users to change a scheduled day to Present, Late, or Absent and optionally enter arrival time and a note. This is a personal declaration and does not require a GPS match. Store `source=MANUAL_CORRECTION`, correction time, and revision. A normal verified button check uses `source=MANUAL_GPS`; a successful Android automatic check uses `source=AUTOMATIC_GPS`.

Require an arrival time for a Present/Late correction; allow the user’s explicitly chosen status even when it differs from time-derived classification. The manual label explains the discrepancy. Absent corrections have no required arrival time. A holiday/vacation date offers Edit time off; users remove that overlay before setting an attendance outcome. Retain the underlying current attendance record while an overlay is active; a full audit-history interface is deferred.

### Calendar changes and interruptions

Preserve a schedule/place snapshot for each evaluated date. Ordinary schedule/place/category changes must not reclassify past records; explicit attendance or time-off corrections may change their effective outcomes. Reconcile missed dates on launch using the versions applicable to each date, without sampling current location for them. Tracking starts with the next eligible scheduled arrival at or after setup completion; never create pre-installation absences. A user may explicitly add/correct today’s record after setup. Time-off changes cover the tracked period, including today; importing pre-setup attendance history is deferred.

Recalculate future scheduling after reboot, app update where needed, clock/time-zone changes, and app configuration changes. Use the tracker’s zone. For daylight-saving gaps, resolve a nonexistent local arrival to the first valid instant after the gap; for overlaps use the earlier occurrence. Store the resolved instant to avoid evaluating a date twice.

## 6. Schedule, days off, and vacation balance

The weekly schedule contains selected weekdays, one default arrival time, optional weekday overrides, and a grace duration from 0–120 whole minutes. At least one weekday is required. There is at most one attendance period on a calendar date.

Users enter holidays and vacations themselves, including date ranges. Date priority is Holiday, then weekly day off, then Vacation, then scheduled attendance. Holidays and weekly days off consume zero vacation days. Duplicate dates are idempotent; a holiday inside a vacation range excludes that date from its deduction.

Adding, changing, or removing time off is allowed for past, current, and future tracked dates. Show affected dates, existing attendance outcomes, and the balance difference before saving. A retrospective holiday or vacation can replace a past Absent, Present, Late, or manually corrected outcome with the effective off-day reason. Label the change as user-entered time off, retain its update time, and preserve the underlying attendance evidence/correction. Removing the entry restores that underlying outcome or derives Absent when its deadline passed without confirmation; it never obtains GPS for a past date.

Apply each time-off change, its balance difference, and dirty report revisions in one transaction. Refresh Today/History/monthly totals and widget snapshots, update affected spreadsheet rows plus the Time off/Overview tabs, and cancel or replace pending work/reminders for affected current/future dates. A running location check revalidates the effective day classification before committing and must not replace an active time-off overlay. Removing today’s time off can make an unclaimed automatic attempt eligible only while the check window is still open; never reset a consumed attempt or request retrospective location.

For a work tracker, available balance is opening balance plus signed manual adjustments minus distinct eligible vacation dates, including reserved future vacation. Show the computed deduction before saving. Adding/deleting/changing leave applies only the difference, in one database transaction, so repeated saves cannot consume balance again.

Adding a holiday inside existing vacation refunds that vacation day, including for a past date. Changes to future schedules or removing a holiday can increase a deduction: preview the difference, and block that change when it would make available balance negative until the user adjusts the balance or leave. Historical schedule/category expectations stay frozen, so ordinary configuration changes do not rewrite past deductions; explicit time-off edits recalculate the affected historical deductions using those original expectations.

The balance-edit action sets a nonnegative whole-day target by recording the needed adjustment. There is no automatic annual reset, accrual, carryover, sick-leave balance, or automatic conversion of an absence into vacation. Users can explicitly record retrospective vacation as described above. School/course trackers have off-day entries without a vacation balance; use “Day off” rather than a paid-leave implication.

## 7. Product experience and approved visual direction

Preserved visual references: [interactive English concept](assets/daymark-concept.html), [light-theme screenshot](assets/daymark-light-preview.png), and [dark-theme screenshot](assets/daymark-dark-preview.png). These contain sample data and simulated integrations. The concept is reference material, not application source; Arabic/RTL layouts must be implemented and verified against section 8.

### Setup

1. Choose language or continue with system language.
2. Enter display name and tracker category: Work, School, or Course.
3. Name the place, request foreground location at the point of use, and register the current position and radius. If denied, retain setup progress and explain how to retry; tracking cannot begin without a registered place.
4. Select attendance weekdays, default/override times, grace, and confirm the attendance time zone.
5. For Work, enter an optional starting vacation balance, default zero.
6. Explain the platform’s attendance mode. On Android, offer automatic checking with a separate explanation and background-location consent. Denial leaves manual checking available.
7. Offer notifications, widget setup guidance, and optional Google connection. These can be skipped.

### Today

Show current date, day status, arrival/deadline, registered place, one clear check action, monthly totals, and a compact week strip. Off days display their reason and next attendance date. Show useful actions when location/notification permission is unavailable and distinguish saved locally from pending/synced reporting. Do not claim a check succeeded before committing it locally.

### History

Calendar plus records with status, time, method, and correction affordance. Text/icons accompany status colors. Future dates are plans, not outcomes. Previous months/years remain accessible from tracking start.

### Plan

Weekday selection, default time, overrides for any selected weekday, grace, registered place/radius, attendance zone, holidays/vacations, and the work-only vacation balance. Explain when schedule changes take effect. Let users add, edit, or remove past/current/future time-off entries in the tracked period and review their attendance/balance effect. History provides the same time-off action for a selected date.

### Settings and widget

Settings contains profile/category, language, appearance, notification/automation health, Google connection, last sync/error/retry, and local-data deletion. Changing category is an explicit future-effective tracker setting; preserve historical category/snapshots and hide the balance outside Work.

Widgets show a compact status, place, deadline when relevant, and a button that opens the app’s foreground quick-check destination. Refresh is system-controlled; a widget snapshot is not an authoritative real-time absence calculation. Include a last-updated cue where a stale status could mislead. A successful tap flows through permissions and a fresh location check; it does not bypass verification.

Keep the approved restrained colors, rounded surfaces, clear typography, and Today/History/Plan navigation. Bind colors and spacing through Material 3 tokens. Support system/light/dark selection, safe areas, text enlargement, screen-reader labels, and readable touch targets. Prioritize content fit over reproducing the browser concept’s small labels exactly. Dynamic wallpaper colors are deferred to preserve the reviewed palette.

## 8. English and Arabic localization

Use Compose Multiplatform resources for shared text, with English in `composeResources/values/strings.xml` and Arabic in `composeResources/values-ar/strings.xml`. Use parameterized/plural resources rather than assembling translated sentences. This follows the official [shared localization model](https://kotlinlang.org/docs/multiplatform/compose-localize-strings.html).

Support System, English, and العربية in Settings, persist the preference locally, and apply it to shared UI and native surfaces. System resolves Arabic when the preferred supported language is Arabic, otherwise English. Language changes never alter stored dates, time zones, status codes, or attendance outcomes.

| English | Arabic |
| --- | --- |
| Today | اليوم |
| History | السجل |
| Plan | الخطة |
| Present | حاضر |
| Late | متأخر |
| Absent | غائب |
| Check attendance | تسجيل الحضور |
| Grace period | فترة السماح |
| Vacation balance | رصيد الإجازات |

Use layout start/end, mirror directional navigation icons and spatial navigation order, and handle mixed Arabic/Latin names, Google Sheets titles, dates, and times with bidirectional isolation. Preserve the reading direction of numeric times and technical identifiers. Arabic glyph shaping, font weight, and line height need device verification. Prefer system fonts with Arabic support; any added bundled font must have a free redistribution license. [Compose RTL guidance](https://kotlinlang.org/docs/multiplatform/compose-rtl.html).

Keep attendance dates Gregorian for both languages; a locale change must not change which date a record represents. Format visible dates, times, numbers, and week presentation for the supported effective locale and platform conventions. Keep calendar column order and weekday headings consistent in RTL. Store canonical ISO dates and numeric values independently of display formatting. [Regional formatting guidance](https://kotlinlang.org/docs/multiplatform/compose-regional-format.html).

Localize native Android notification channels/content, widget text, permission rationale, and supported-app-language metadata. On iOS, localize native permission usage descriptions, widget resources, and notification content. Republish widget snapshots and replace pending future notifications after an app-language change. User-entered names and notes are preserved verbatim and are not automatically translated. Use bilingual spreadsheet headings; machine identifiers stay stable.

## 9. Architecture and technology choices

### Structure

Use a small layered application with shared UI, attendance rules, and data access. There is no need for a multi-module enterprise framework.

```text
androidApp/                 Android host, Worker scheduling, Glance widget
shared/
  commonMain/
    ui/                     Compose screens, theme, ViewModels, routes
    attendance/             Day evaluation, check-in, correction, leave rules
    data/                   Room storage, Sheets publisher, sync state
    platform/               Interfaces for native OS services
  androidMain/              Android location/auth/service adapters
  iosMain/                  iOS-compatible adapters and shared host entry
  commonMain/composeResources/values/ and values-ar/
iosApp/                     Swift app host, notification delegate, WidgetKit extension
```

UI observes immutable `StateFlow` state from shared lifecycle ViewModels. User actions call focused attendance operations, which transact against local storage and publish updated UI/widget state. Pure date/status/balance rules take an injected clock and zone for deterministic tests. Use constructor injection from a small composition root; a DI framework is unnecessary for this MVP.

Native contracts cover foreground/background location capability, scheduling/cancellation, native Google authorization, widget snapshot publication, and locale/notification integration. Keep Android `Context`, `WorkManager`, `Glance`, iOS notification APIs, SwiftUI/WidgetKit, and native credential handling out of shared UI/domain code.

### Libraries

| Concern | Choice |
| --- | --- |
| Language/build | Kotlin Multiplatform, Gradle Kotlin DSL, version catalog, Compose compiler plugin matching Kotlin |
| UI | Compose Multiplatform with Material 3 and shared resources |
| Navigation | Navigation 3, using JetBrains’ multiplatform UI artifact and serializable route keys |
| State | Kotlin coroutines/Flow and multiplatform lifecycle ViewModel |
| Local database | Room 3 KMP with bundled SQLite and KSP |
| HTTP/JSON | Ktor Client with Android/Darwin engines and kotlinx.serialization |
| Time | Kotlin time plus kotlinx-datetime where needed for local dates/IANA zones |
| Android location | Google Play services fused location provider; Android GPS permission APIs |
| Android scheduling/widget | WorkManager and Glance AppWidget |
| iOS location/reminder/widget | Core Location, UserNotifications, WidgetKit/SwiftUI |
| Google authorization | Native Google Identity authorization on Android and Google Sign-In SDK on iOS |
| Tests | kotlin.test, coroutines test, database integration tests, Compose/device journey tests |

As checked on 27 October 2026, official references list Kotlin 2.4.21, Compose Multiplatform 1.12.2, and Room 3.0.3. These are a research baseline, not a substitute for compiling a compatible version set. Freeze exact stable dependency, KSP, AGP, Gradle, SDK, JDK, and Xcode versions during implementation planning; avoid preview/RC/EAP dependencies unless a reviewed exception is necessary. Navigation 3’s Android and JetBrains UI artifacts have separate versions. [Kotlin releases](https://kotlinlang.org/docs/releases.html), [CMP compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), [Room 3 releases](https://developer.android.com/jetpack/androidx/releases/room3), [Navigation 3 for CMP](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html).

The Android CLI documentation search was used for current guidance. Its Studio version lookup could not run because no compatible Android Studio instance was connected; exact artifact versions still need a Maven/official-release check before the version catalog is committed.

### Logical persistent records

- App settings/profile: display name, effective language preference, theme, and native permission/notification state references.
- Tracker and versioned schedule/place settings: UUID, category, registered coordinates/radius, zone, selected weekdays, arrival overrides, grace, and effective dates.
- Attendance day: unique `(trackerId, attendanceLocalDate)`, frozen baseline plan, underlying attendance status/source and evidence, effective projected status, confirmation time, correction note/time, automatic-attempt claim/result, revision, and sync revision.
- Time-off dates/ranges: kind, dates, user-entered/update metadata, and recalculable balance effects evaluated against historical baseline plans where applicable; deduplicate eligible vacation dates.
- Vacation opening balance and adjustments.
- Reporting connection/outbox: spreadsheet identity, schema version, dirty date/revision, retry/authorization state; no raw credentials.

The builder may combine or split these into tables where it improves clarity. The date uniqueness constraint, atomic attempt claim, immutable historical snapshots, and revision-aware outgoing sync are required behaviors.

## 10. Native execution, permissions, and power

### Android

Use a unique one-time WorkManager request for the next scheduled occurrence, with no network requirement for local attendance. Recompute the next future occurrence after processing and on foreground/configuration recovery; there is no repeating 15-minute poll. The worker checks eligibility, claims the date atomically, requests at most one fresh location, stores the result, and stops. Off days and dates with an existing successful/manual-corrected record skip the request.

WorkManager redelivery may reconcile state and schedule the next occurrence, but must not issue a second location request for a claimed date. A delayed worker outside the window records the missed outcome and skips location. On the next launch, reconcile any dates that were never processed. Sheet retries use separate network-constrained work and never re-run GPS.

Request foreground location during place registration and manual check. Request background location separately when enabling automation, with a prominent feature-specific explanation. Denial/revocation disables automatic location work and exposes manual check. Request notification permission only for notification features. Background location requires a justified core feature and Play review; approval is not guaranteed. [Google Play background location requirements](https://support.google.com/googleplay/android-developer/answer/9799150).

Do not request exact-alarm special access or `USE_EXACT_ALARM` by default. Do not claim WorkManager meets a hard five-minute wake deadline. Exact-alarm access has different permission/policy implications and does not itself guarantee a usable GPS fix; if validation shows the selected behavior is unacceptable, revisit the design rather than quietly adding a persistent service or extra polling. [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).

### iOS

Use a foreground Core Location request for registration and quick check, with When In Use authorization. No Always location permission or background location mode is needed for the selected iOS workflow. Schedule local UserNotifications reminders at each eligible arrival time; opening a notification or widget enters the shared quick-check flow.

Maintain reminders for the next 30 calendar days and replace the future queue on launch, notification/widget opening, and schedule/leave/language changes. State the horizon in the reminder setup/health view: if the app is never opened beyond this window, reminders may stop until it is opened again. Notifications remain subject to user permission and system presentation controls. Do not schedule an extra location request at the grace deadline.

The SwiftUI widget reads a small atomically written App Group snapshot containing localized display data and a quick-check route. Keep credentials and the full Room database out of the widget snapshot. Opening the host app reconciles the authoritative status and performs location verification.

### Power and release validation

There is no continuous tracking, geofence registration, periodic GPS retry, or background location on iOS. One Android attempt is bounded; local absence evaluation does not require location. Batch spreadsheet uploads and use backoff. Validate Android automation on real devices, including a non-Google manufacturer and battery-saving conditions. Report scheduling limitations honestly in onboarding and permission health.

## 11. Google Sheets report

### Connection and template

No login is required to track attendance. On connection, use native OAuth to authorize the narrow `drive.file` scope, create one app-owned spreadsheet, store its identity locally, and queue all local records for reporting. Never embed a service account or client secret in the mobile application. Native authorization adapters manage credential refresh and secure persistence. [Sheets authorization scopes](https://developers.google.com/workspace/sheets/api/scopes).

Create an Overview tab with category, place name, attendance zone, and counts; one attendance tab per year; and a Time off tab. Use bilingual English/Arabic human-facing labels with stable machine column identifiers/status codes. Do not upload registered latitude/longitude or the user’s movement history.

An attendance row includes tracker/date key, date, category, scheduled arrival, grace deadline, status code, human-readable status, confirmed arrival, source, note, updated time, and revision. Off dates carry a specific holiday/vacation/weekly-off reason, rather than Absent. Freeze and filter headers and use status formatting. The template is app-managed and explains that direct edits may be overwritten.

### Retry-safe updates

Use deterministic rows in each annual tab: row 1 stable machine headings, row 2 bilingual display headings, and `dayOfYear + 2` for the date’s data row. Hide the machine header if appropriate. Update the same date range rather than append on every check or retry. Stable tab IDs and tracker identity are stored locally. This makes repeating a timed-out write safe without creating duplicate attendance rows.

A local database transaction commits each record and its dirty revision together. A single serialized sync worker batches dirty dates. Mark a revision synced only if it is still the current revision after the write; a concurrent local correction stays dirty. Reconcile schema before writing. If user changes to app-owned tabs break the expected structure, pause and offer to rebuild those tabs or create a new report; leave unrelated user tabs alone.

### Failure and reconnection

When offline, keep tracking locally and show pending sync. Retry transient failures and quota responses with bounded exponential backoff and jitter; persistent errors remain visible with a Retry action. On expired/revoked consent, pause uploads and offer reconnect while preserving local records. Reconnect to the same authorized report when available; if it was deleted or belongs to a different account, explain and create a new report after the user chooses that action.

Disconnect stops outgoing work and removes local authorization state, while preserving the local database and the user-owned report. There is no incoming spreadsheet merge or multi-phone conflict protocol. Reinstall creates a new tracker identity and must not silently attach to and overwrite an old report. The existing report remains readable in Google Sheets; full app restoration is deferred.

## 12. Cost, privacy, and distribution constraints

Use free, redistributable libraries and native frameworks. There is no required subscription or custom server. Standard Sheets API usage is currently available at no additional cost within quota; the published policy also announces planned charging changes for excess usage later in 2026. Respect quotas and do not automatically enable billing or paid quota upgrades. Project-wide quotas can become a growth constraint even when each user owns their report. [Sheets usage and pricing](https://developers.google.com/workspace/sheets/api/limits).

App-store distribution has separate developer-account costs, even for a free app: the Apple Developer Program is ordinarily USD 99/year, with regional pricing/tax variations. Confirm the intended Google Play distribution/account requirements before release rather than treating development as paid-library-free distribution. [Apple membership](https://developer.apple.com/help/account/membership/program-enrollment).

Store the registered location in the OS-protected app sandbox; use native secure credential facilities and platform file protection. Do not log credentials, precise positions, or personal notes. Keep location request data only long enough to evaluate presence. Explain location usage, optional Google reporting, local-data deletion, and record correction in English and Arabic. Provide store privacy disclosures and a publicly accessible privacy policy before release. This app records personal declarations and phone location; it does not certify a person’s identity or official attendance.

Local-data deletion requires an explicit in-app confirmation. It removes local tracker/records and stops scheduled work, reminders, and sync. It does not silently delete the Google spreadsheet. Backup/restore is not promised by the MVP; exclusion of credentials and widget state from inappropriate backups is a release requirement, and platform backup behavior must be documented during implementation.

## 13. Verification and release acceptance

This section defines future implementation checks; the native app has not yet been built or tested.

- Pure rule tests cover deadline boundaries, zero grace, weekday overrides, holidays/vacation overlap, retrospective/current/future leave additions/edits/removal, deductions/refunds, restoration of underlying attendance, manual corrections, earlier-success precedence, and historical schedule/zone snapshots including daylight-saving transitions.
- Database tests cover per-date uniqueness, competing automatic/manual/time-off writes, single automatic-attempt claims, transaction recovery, revision-aware outbox updates, and schema migrations.
- Android device journeys cover granted/denied/revoked foreground/background location, accurate/inaccurate/unavailable fixes, delayed/missed worker execution, reboot recovery, battery saver, and widget launch. Demonstrate that retries do not issue another automatic GPS request for the date.
- iOS device journeys cover notification authorization, reminder replacement/horizon, foreground location, notification/widget deep links, denied permissions, and stale widget snapshots. Do not infer behavior solely from a simulator.
- Sync tests cover offline records, local correction while uploading, repeat writes after a timeout, quota errors, reauthorization, disconnect, deleted/modified templates, and reconnection without duplicate rows.
- UI journeys cover setup, all day states, correction, schedule changes, time off/balance, optional Google connection, and settings.
- Localization verification covers EN/LTR and AR/RTL on both platforms in light/dark modes, large text, Arabic shaping, plural forms, mixed-direction content, calendars/time inputs, native permission descriptions, notifications, and widgets. A missing translation or clipped primary action blocks release.
- Verify dependency licenses, secret-free binaries/logs, stable compatible toolchains, store permission declarations, privacy disclosures, and the native build/signing requirements before distribution.

The browser concept’s simulated flows have been exercised for within-grace Present, post-deadline Late, outside-area rejection, vacation deduction, weekday overrides, and the sample spreadsheet. These checks do not validate native location, background scheduling, OAuth, or Arabic rendering.

## 14. What remains with the builder

Within this design, the builder chooses exact compatible stable patch versions, concrete DAO/table boundaries, route serialization details, test fixture organization, retry intervals, and native integration plumbing. Keep the implementation small and use real-device evidence for platform behavior.

The final public app name, package/bundle identifiers, developer accounts/credentials, signing assets, store listing, and distribution rollout are release inputs. They do not block implementation planning: use explicitly provisional local identifiers until supplied, and do not publish or enroll accounts without the user’s instruction.

Changes to automatic-check semantics, the grace rule, required permissions, paid services, tracking multiple people, cross-device behavior, or the agreed scope require design review. After this document is approved, the next step is a separate implementation plan; application scaffolding and dependency installation have not been authorized by approving the visual concept alone.
