# Senior review: research and repository facts

Research date: 27 October 2026. This is an evidence pack, not the review verdict.

## Fixed review target

The complete original artifact is preserved verbatim in [01-design-v1-frozen.md](01-design-v1-frozen.md).

- Original commit: `72ec628`.
- Original path: `docs/superpowers/specs/2026-10-27-attendance-mvp-design.md`.
- SHA-256 of frozen bytes: `99843eeb6012ddb80a51c114daf5cd82054d99735a768bf278cc98a9d4e20e53`.
- The frozen file contains the full artifact, rather than a summary reconstructed from conversation.
- Asset links inside the verbatim snapshot retain the original specification's relative base; the preserved visual files remain in `docs/superpowers/specs/assets/`.

## Review domains

1. **Android execution and location.** A senior must distinguish scheduler eligibility, location permissions, and the availability of a usable fresh fix; none independently guarantees the others.
2. **iOS lifecycle, reminders, and widgets.** A senior must define which process can execute each action, what survives suspension, and how deferred work recovers.
3. **Google authorization and report synchronization.** A senior must define access scope, transaction boundaries, retry ambiguity, and account/disconnect behavior without treating a spreadsheet as a transactional application database.
4. **Attendance, dates, and leave persistence.** A senior must preserve the user's approved rules under historical edits, concurrent operations, time-zone changes, and process death.
5. **CMP toolchain and EN/AR experience.** A senior must distinguish documented library support from a compiled version set, and string translation from native/RTL behavior.

## Repository reconnaissance

A separate context-isolated repository agent verified:

- `72ec628` is the sole commit and contains five tracked files: `.gitignore`, the design, one HTML concept, and two PNG references.
- No repository/ancestor `AGENTS.md` was found.
- No Kotlin/Swift application, Gradle wrapper/build/catalog/lockfile, Xcode project, database, automated test suite, or CI exists. This is an intentionally unscaffolded design project, not a failed application build.
- Frozen design line 33 acknowledges the prototype limitation. Lines 237–239 defer exact toolchain pinning; line 325 leaves compatible patch versions to implementation planning. Lack of implementation alone is not a blocker for approving a design.
- Prototype `assets/daymark-concept.html:2` is English-only; lines 17–19 select simulated conditions, line 25 labels integrations simulated, lines 104–109 simulate a check using a timeout, and line 124 toggles Google connection locally.
- Prototype lines 128–135 implement a limited in-memory leave demonstration; they do not validate retrospective leave, transactional deductions, or real sync. Native behavior and Arabic rendering remain untested.

## Primary sources fetched for this review

Every source below was fetched on 27 October 2026. Publication/update dates are recorded where exposed; retrieval dates are not represented as publication dates.

### R1 — Current-location API in background

Source: [Google FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient). Page last updated 26 June 2026.

The `getCurrentLocation` API follows background location limits. The reference explicitly notes a higher frequency of unsuccessful location retrieval when called in the background. A foreground location service is one documented approach for clients requiring reliable background access. This source describes API behavior, not permission approval for this particular app.

### R2 — Android background location limits

Source: [Android Background Location Limits](https://developer.android.com/about/versions/oreo/background-location-limits). Page last updated 29 April 2024; fetched current during this review.

On Android 8 and later, background location delivery is throttled to a few updates per hour. The rules apply independently of target SDK. Moving an activity to the foreground or using an eligible foreground location service changes the execution model. Background permission alone is not a timing guarantee.

### R3 — iOS background execution

Source: [Apple DTS: iOS Background Execution Limits](https://developer.apple.com/forums/thread/685525). Originally published 22 July 2021; latest listed revision 9 January 2026; current page fetched during this review.

An iOS process normally becomes suspended shortly after backgrounding. There is no general-purpose promise of execution at a particular clock time. Specific APIs provide bounded or system-scheduled background mechanisms.

Corroboration: [Apple DTS on background uploads](https://developer.apple.com/forums/thread/838523), July 2026. It distinguishes background URLSession uploads from a normal session accompanied by a finite background execution allowance; an app refresh task is not a general upload guarantee.

### R4 — Sheets limits and request ambiguity

Source: [Sheets API usage limits](https://developers.google.com/workspace/sheets/api/limits). Last updated 3 September 2026; current page fetched during this review.

The documented project quota is 300 read and 300 write requests per minute; per-user/project quotas are 60 each. Google documents atomic application of an individual request, a 180-second processing limit, and exponential backoff for time-based quota failures. These are not a cross-request compare-and-swap or monotonic revision guarantee. Deterministic cell ranges prevent append duplicates but do not themselves order different revisions of separate requests. The latter conclusion is an inference from the API's documented request model.

### R5 — Values must have an explicit interpretation

Source: [Sheets ValueInputOption](https://developers.google.com/workspace/sheets/api/reference/rest/v4/ValueInputOption). Last updated 21 March 2025.

`RAW` stores values without UI-style interpretation. `USER_ENTERED` applies spreadsheet parsing rules. [Read/write values guidance](https://developers.google.com/workspace/sheets/api/guides/values) illustrates that formula-like strings can become formulas in the latter mode. User-entered labels/notes and app-owned formulas should therefore have an explicit boundary; this is a design inference, not a claim that the current prototype already executes formulas.

### R6 — Narrow Google authorization

Source: [Sheets API scopes](https://developers.google.com/workspace/sheets/api/scopes). Current page fetched during this review.

`drive.file` is Google's recommended non-sensitive per-file scope for files used with the app. Broad spreadsheet and Drive scopes grant wider access and introduce different verification requirements. The original design's choice of an optional native authorization connection with no mobile service-account secret is consistent with this model.

Source: [Android authorization guidance](https://developer.android.com/identity/authorization). Current page fetched during this review. AuthorizationClient is the official authorization path for access to Google user data; authorization and app authentication are different concerns.

### R7 — Room 3 is a current stable option

Source: [Room 3 release notes](https://developer.android.com/jetpack/androidx/releases/room3). Stable 3.0.3 released 9 September 2026; release page also lists newer alpha activity.

Room 3.0.3 is stable, uses `androidx.room3`, KSP and coroutine-oriented APIs, and is focused on KMP. This confirms that rejecting it solely on older training knowledge that Room KMP is experimental would be incorrect. No repository manifest currently pins or compiles it.

### R8 — CMP compatibility and navigation

Source: [CMP compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html). Current documentation lists Compose Multiplatform 1.12.2 and compiler-plugin/Kotlin alignment requirements.

Source: [Navigation 3 for CMP](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html). Current page fetched during this review; it documents multiplatform support and a JetBrains UI artifact with versions separate from AndroidX artifacts.

These sources support the architectural choices but do not constitute a compiled AGP/Gradle/KSP/Xcode/SDK compatibility matrix.

### R9 — Arabic/RTL

Source: [Compose RTL guidance](https://kotlinlang.org/docs/multiplatform/compose-rtl.html). Current page fetched during this review.

Compose handles much layout mirroring based on locale and supports explicit direction overrides and mixed-direction input. This does not test this app's translations or native widgets/permission dialogs. The original design correctly requires both shared and native localization verification.

### R10 — Maintainer security advisory checks

On 27 October 2026, the [Google Sign-In iOS advisory page](https://github.com/google/GoogleSignIn-iOS/security/advisories) listed no published advisories. This is not proof that a future pinned dependency tree is vulnerability-free.

The [Ktor advisory page](https://github.com/ktorio/ktor/security/advisories) listed [GHSA-xrr9-rh8p-433v](https://github.com/ktorio/ktor/security/advisories/GHSA-xrr9-rh8p-433v), published 27 January 2020. It concerns CIO client/server packages below 1.3.0 and identifies 1.3.0 as patched. The proposed current-stable Android/Darwin clients do not justify treating that old advisory as a blocker. Recheck actual direct and transitive pinned versions during implementation planning.

## Boundaries for the independent reviewer

Review the frozen artifact, not the author's conversational reasoning. Preserve the approved product choices: personal tracker, one phone/place, Android best-effort single automatic attempt, iOS reminder/foreground check, five-minute configurable grace, optional outgoing Sheets report, EN/AR and RTL, and retrospective time-off corrections. Do not silently substitute geofencing, continuous tracking, stricter employer verification, multi-device sync, a paid service, or a custom backend.

Calibrate findings to a design awaiting a later implementation plan. Exact file-by-file implementation tasks and a completely compiled dependency catalog are not prerequisites for a greenfield design document. Required boundary contracts, failure behavior, high-risk feasibility sequencing, and rollback should still be concrete enough to plan.
