# Implementation decisions

Recorded in execution order, with reasons and costs. Native/live gates remain open in [release verification](release-verification.md).

1. Task 1: Ruling: use the installed Xcode 27.1 only to collect provisional compile evidence, without suppressing compatibility warnings; the supported-Xcode gate remains incomplete until 26.4 is supplied or official support changes — no supported side-by-side Xcode is installed — cost if wrong: iOS integration cannot be accepted on this machine yet.

2. Task 1: Ruling: compile/run checks cover the explicitly authorized bootstrap/configuration slice, as specified in Task 1; behavior tasks remain test-first — bootstrap is temporary, not an attendance feature — cost if wrong: a native hosting defect may surface in the run gate.

3. Task 1: Ruling: the observed successful Xcode 27.1 framework build, Swift host build, and visible simulator launch qualify the local development gate, superseding the earlier requirement to obtain 26.4 before feature work — no supported older Xcode is installed and this is observed compatibility, not a support-floor/product change — cost if wrong: older-OS/device behavior remains unproven and must pass release acceptance; official matrix qualification stays explicit.

4. Task 2: Ruling: keep the disposable widget as a separate development-only scheme, manually embedded only in the simulator test bundle — the shipping host must not acquire a temporary widget dependency — cost if wrong: development packaging needs a manual copy until Task 12 replaces it.

5. Task 3: Ruling: replace iOS Google Sign-In 10.1.0 with exact-pinned free AppAuth OAuth authorization-code/PKCE for the drive.file-only flow — inspected pinned GIDSignInInternalOptions.m unconditionally calls scopesWithBasicProfile, whose GIDScopes.m adds email/profile, contradicting the approved scope boundary — cost if wrong: iOS authorization needs additional native integration and live-provider validation; no extra scope or mobile client secret is authorized. Evidence: official google/GoogleSignIn-iOS tag 10.1.0, source files retained in the task workspace. Final AppAuth pin/compile/live gates must be recorded before claiming support.

6. Task 4: Ruling: reject an arrival/grace window whose resolved deadline crosses into a different attendance-zone date — the approved MVP has one attendance period per calendar date, same-date manual GPS checks, and explicitly excludes overnight shifts — cost if wrong: near-midnight schedules with a grace extending past midnight need a smaller grace or a later feature design; no cross-date automatic GPS is introduced.

7. Task 5: Ruling: run real temporary-file Room transaction tests in a JVM test target plus iOS cold-reopen tests, replacing the plan's Android-host persistence test location — Android builders require Context and Android native binaries, while official Room KMP guidance supports context-free JVM host tests; a JVM test target avoids stubbing Android database behavior or adding a large emulation test dependency — cost if wrong: this proves shared Room/SQLite transaction behavior, not Android-specific builder wiring; physical Android app/database acceptance remains required. The JVM target reuses the existing pure Java zone resolver and does not add a desktop app.

8. Task 5: Ruling: add a per-day userEditRevision to StoredDay and its expected value to CheckCommit, separate from the ordinary row revision — a holiday added and removed during GPS must invalidate that request, while concurrent valid GPS confirmations must still preserve the earlier success — cost if wrong: internal consumers must carry one extra revision field; conflating it with all writes would incorrectly reject earlier concurrent evidence.

9. Task 5: Ruling: add a LOCAL_SAVE_FAILED rejection reason — the plan requires a visible local-save error but its enumerated outcomes/reasons cannot represent one distinctly — cost if wrong: later localized UI mappings must handle this extra reason and must not display a sensor failure as a successful save.

10. Task 6: Ruling: extend PlanMutationResult/PlanRejection with a local-storage failure case — the required distinction between invalid whole-day input and failed storage cannot be expressed by the plan's four mutation outcomes/rejections — cost if wrong: later UI must localize the extra case instead of treating it as a successful save or bad user input.

11. Task 6: Ruling: skip an attendance window introduced by a zone change when its arrival precedes that version's activation instant — ordinary edits must not impose a retrospective obligation; activate next midnight in the current attendance zone and use the next valid future window — cost if wrong: a date-line change can skip a cutover date, which the preview must show as Day off. Future reservations use the same projection, and existing historical snapshots remain frozen.

12. Task 7: Ruling: bound next-occurrence projection to 3660 calendar dates — a generic PlanReader may expose no eligible date, so an unbounded search cannot implement the promised null result — cost if wrong: a tracker with more than ten years of uninterrupted time off requires opening/recovering the app when its attendance comes within this horizon. This does not add GPS checks or persist future snapshots.

13. Task 8: Ruling: encode replace JSON as an owner envelope {trackerId, reminders}, including an empty reminders list — the opaque JSON port otherwise cannot know which tracker's pending requests to remove when all 30 dates are excluded — cost if wrong: native and shared consumers must retain this explicit envelope; it prevents unrelated-feature or other-tracker notification deletion.

14. Task 9: Ruling: add SetupResult.LocalSaveFailed — an invalid form and a failed Room transaction require distinct user feedback, as in the earlier attendance/plan services — cost if wrong: onboarding UI must localize a third result and preserve the draft on storage failure.

15. Task 9: Ruling: use en_US_POSIX and ar_EG@numbers=arab for native date/number formatters — Foundation's plain ar defaults to Western digits here, while the Unicode numbering-tag variant fails in the actual simulator SDK — cost if wrong: Arabic date conventions follow Egyptian Arabic; the MVP offers EN/AR, not per-region number/date customization. App copy language remains ar and no device locale changes.

16. Task 9: Ruling: complete iOS host source wiring and retain written XCTest UI journeys while deferring their build/run — the user explicitly chose preserving runtimes over further disk cleanup — cost if wrong: Swift host integration, native layout/glyph rendering and full release linkage remain unqualified until disk capacity is available. Android UI evidence does not substitute for these checks.

17. Task 10: Ruling: User explicitly chose to keep both iOS runtimes and defer iOS UI build checks — continue shared/Android verification and iOS Kotlin compilation/Swift syntax only; preserve native UI journeys as an open gate — costs unverified iOS host linkage, rendering and device behavior until space/hardware is available.

18. Task 10: Ruling: History starts in the attendance-zone month passed from Today, rather than device-zone month — travel must not choose a different initial report month — costs requiring Today to resolve its date before navigation.

19. Task 10: Ruling: Keep formatted arrival/deadline/source labels derived in Compose from typed plan/outcome plus LocalAppLanguage rather than storing localized strings in ViewModel state — locale changes update visible copy without mutating canonical records or restarting GPS — costs callers outside Compose needing the same format/label mapping.

20. Task 11: Ruling: Put real-database Plan controller tests in jvmTest, using the existing bundled SQLite factory — Android host test target has no real Android Context/database factory, and adding Robolectric duplicates the existing working JVM boundary — costs Android-specific database behavior remaining covered by device journeys rather than this host test.

21. Task 11: Ruling: Add a typed ConfigurationCoordinator and application-start/configuration hooks now; Android recovery and opted-in iOS reminder replacement use real native adapters, widget refresh remains an explicit nullable hook until Task 12 installs a cache publisher — prevents falsely claiming a working widget before its dependency exists — costs RF3 end-to-end widget publication verification remaining open until Task 12.

22. Task 11: Ruling: History time-off navigation carries the selected attendance-local date into Plan and opens its matching entry (or a new entry on that date), including an action from attendance corrections — the frozen spec requires a selected-date action, and a generic Plan tab loses that context — costs one optional serialized date on the Plan route.

23. Task 12: Ruling: Reminder taps now forward the notification's validated tracker UUID through the same strict quick-check route instead of an unscoped current-tracker Unit intent — a notification from an erased tracker must not check in a new profile — costs an internal Swift delegate callback signature change requiring deferred native typecheck/link validation.

24. Task 12: Ruling: Android re-delivers URI actions through onNewIntent and consumes a cold URI only when savedInstanceState is null; configuration/task restoration alone never replays a sensor action — avoids turning activity restoration into another check — costs a rare fresh intent combined with restored instance state potentially requiring the visible manual button.

25. Task 12: Ruling: Extend display snapshot with optional arrival epoch, app theme and isTracked flag — native widgets need boundary transitions and must not derive an absence from an untracked setup baseline — costs maintaining backward-compatible defaults in both native decoders.

26. Task 12: Ruling: Android widget has no periodic or deadline background job; the single scheduled sensor check remains unchanged. Its visible attendance date, deadline and last-updated cue describe the cached record, and every tap resolves live truth in the host — preserves the user's low-power goal — costs Android's rendered status potentially staying old until an app/OS widget update. WidgetKit can use native timeline entries without running an extra location request.

27. Task 12: Ruling: Native widget cache publication Boolean confirms atomic cache storage/reload request, not immediate OS-visible rendering — both Glance and WidgetKit schedule rendering independently — costs requiring separate launcher/WidgetKit visual gates and timestamped cached-state copy.

28. Task 13: Ruling: Journal reopen tests use the existing JVM bundled-SQLite database factory instead of Android host stubs — it executes the real persistence engine and actual file reopen with fewer native dependencies — cost if wrong: Android filesystem-specific behavior still needs its separate device gate.

29. Task 13: Ruling: Store the complete binding envelope in the existing annualTabIdsJson text field and connection tombstones in settings — no schema change is required for new report metadata, and stale generations must remain rejected even after disconnect — cost if wrong: legacy malformed binding JSON requires explicit reconnect, never destructive recreation.

30. Task 13: Ruling: Production report ranges use stable sheet-ID grid filters while the development probe keeps its original A1 transport — titles alone can be renamed to point at unrelated tabs between validation and dispatch — cost if wrong: API filter compatibility must be exercised in the still-open live OAuth probe.

31. Task 13: Ruling: Split aggregate projections into complete row fragments and persist ACK coverage in the same batch journal; the projection becomes clean only when all current-revision rows are covered — this handles time-off tables larger than a dispatch without losing trailing clears — cost if wrong: large histories retain more journal rows and need performance monitoring.

32. Task 13: Ruling: Bound each foreground drain to ten 100-row dispatches and persist a Pending continuation; repair reflags the current projection revision without inventing an attendance edit — prevents an unlimited foreground loop while retaining the local authority — cost if wrong: long histories require multiple lifecycle passes and may sync more slowly.

33. Task 14: Ruling: Connection/discovery binds a report before publication prepares its owned template — Connected identifies the selected account/report, while observed sync remains a separate health state — cost if wrong: a connection may immediately surface a schema/permission error rather than a successful publication.

34. Task 14: Ruling: Explicit rebuild carries a persisted repair request, repairing only metadata-verified owned headers and local rows; missing or unowned bound tabs still require a replacement report — avoids adopting/deleting unrelated tabs — cost if wrong: some damaged reports cannot be rebuilt in place and users must retain the old report and create another.

35. Task 14: Ruling: Disable automation/reminder preferences and invalidate the connection before external cleanup; preserve authoritative records if cleanup or the final clear fails — prevents a failed deletion from rearming side effects — cost if wrong: a failed deletion leaves the retained profile disconnected with automation/reminders disabled until explicitly re-enabled.

36. Task 14: Ruling: Reconnect first fences the binding, stops its foreground drain, and forgets the native session before interactive authorization — Google's Android guidance says clearCredentialState forces account selection, while every silent sync also verifies the opaque account reference — cost if wrong: canceling a reconnect leaves uploads disconnected until explicitly connected again. Primary source: https://developer.android.com/identity/authorization .

37. Task 14: Ruling: Android network work uses a separate connected-network unique chain and at most six WorkManager invocations per retry chain; projection revision changes enqueue continuation, ACK-only changes do not — avoids losing a correction made while a worker exits without causing an ACK-driven work loop — cost if wrong: OS scheduling may delay report uploads; local records remain authoritative.

38. Task 15: Ruling: Use synthetic prior-schema/future-version-marker fixtures rather than inventing an older shipped APK — the app has no released predecessor and schema 1 is the actual exported initial schema — cost if wrong: a future real release still needs an APK-to-APK upgrade journey using that shipped artifact.

39. Task 15: Ruling: Apple app manifest declares the source-observed app-local UserDefaults reason and no tracking; compiled-framework required-reason aggregation and final optional Google data disclosures remain a native archive/release gate — source-only inspection cannot certify transitive native SDK behavior — cost if wrong: store validation may require additional evidenced declarations before distribution.

40. Task 15: Ruling: Share one process lock across Android widget cache readers/writers/clear — widget/native components currently run in the same app process and AtomicFile requires caller exclusion — cost if wrong: a future separate widget process needs cross-process file coordination rather than this lock.

41. Task 15: Ruling: Package EN/AR language resources together instead of adding Play Core downloads — the app/widget must switch language offline and lint identified a split-language risk — cost if wrong: a modest increase in installed resource size. PrivacyMetadataTest language-packaging assertion RED in task-15-regressions-red.log, GREEN in task-15-widget-diagnostic-2.log.

42. Final: Ruling: Actual iOS host/UI remains unqualified — user explicitly deferred native linking/execution — cost if wrong: source checks may miss native integration/rendering defects.

43. Final: Ruling: Physical iPhone GPS/reminder/WidgetKit/App Group remains unqualified — no signed physical iPhone is available and user accepted this open gate — cost if wrong: real delivery, accuracy, widget interaction or provisioning may fail.

44. Final: Ruling: Huawei launcher pin/tap remains unqualified — user explicitly deferred the picker that did not advance via ADB — cost if wrong: cache/handoff tests may miss launcher-specific behavior.

45. Final: Ruling: Modern Android/manufacturer/Doze reliability remains unqualified — requires authorized physical signed-release measurements — cost if wrong: automatic checks may be missed beyond the already-observed best-effort limitations.

46. Final: Ruling: Live OAuth/Drive/Sheets remains unqualified — native clients/consent are not provisioned or exercised in a real account — cost if wrong: provider integration may fail despite mock HTTP tests.

47. Final: Ruling: Signing, final identifiers/icon, store submission and enrollment remain owner release inputs — no distribution/enrollment is authorized — cost if wrong: final packaging/ownership/store acceptance can require further work.

48. Final: Ruling: Compiled iOS privacy aggregation and minimum-OS compatibility remain unqualified — source-only checks cannot certify the final archive or lower OS floors — cost if wrong: archive validation or older-device runtime can fail.

49. Final: Ruling: Full visual clipping, glyph shaping and screen-reader acceptance remain unqualified — action reachability is narrower evidence — cost if wrong: native layout/accessibility defects may remain.

50. Final: Ruling: Multi-device merge, restore/import, overnight shifts, continuous tracking, payroll and accrual remain excluded — approved MVP scope excludes them — cost if wrong: those needs require a separate design and implementation.

51. Final: Ruling: Keep reconciliation cursor process-local and restart it on a new foreground session — a reopened/new session must recheck even previously acknowledged prefixes for late old writes; global 100-row batches and bounded yields prevent per-date quota starvation — cost if wrong: interruption rechecks the current prefix, so very long histories need a sufficiently long supported foreground session.

52. Final: Ruling: Bound new note/profile fields to 50,000 UTF-16 units and quarantine legacy unreportable rows instead of truncating local text — Google Sheets has per-cell text limits, and one record must not block unrelated publication — cost if wrong: an unusually long field must be shortened by the user before that field's row can sync; the original local text is retained. Primary limit context: https://support.google.com/drive/answer/37603 .

53. Final: Ruling: Discover only current-account-owned app-managed reports — an old account's report shared with a new account must not be silently adopted on reconnect — cost if wrong: other-owned/shared-drive reports are not offered by this personal My Drive MVP. Primary owner-query contract: https://developers.google.com/workspace/drive/api/guides/search-files .

54. Final: Ruling: Preserve the plan workspace and managed worktree while full native/live acceptance remains incomplete — user deferred required gates, so the plan's full completion/cleanup condition is not met — cost if wrong: about 24 MB of task evidence remains until those gates are closed or the user chooses cleanup.

## Follow-up — 2 November 2026

55. Tasks 6/10/12/13: Ruling: Derive `DAY_OFF` for school/course vacation-kind overlays in the shared attendance rule, using each date's historical category — all existing UI, widget and report consumers then use the bilingual Day off status without independent presentation mappings. Retain `VACATION` as the specific off reason, `MANUAL_TIME_OFF` as source, and underlying evidence. Work trackers retain `VACATION`; balance rules are unchanged. Cost if wrong: consumers that previously displayed school/course `VACATION` must accept the already-supported `DAY_OFF` code. Foreground reconciliation and report verification refresh old cached values without a schema change.

56. Task 13: Ruling: Publish Overview counts and work balance as JSON numbers, retaining literal strings and the blank non-work balance. Compare remote primitive types during reconciliation while accepting numerically equal JSON numbers (such as `1` and `1.0`) — a new session must repair totals written as text by earlier builds. Cost if wrong: formerly type-mismatched managed cells are rewritten once from local truth; unrelated cells are untouched.

57. Execution: Ruling: Continue in the current checkout, as explicitly requested by the user on 2 November; preserve the pre-existing local Gradle/IDE/Xcode/script changes. Native/live release prerequisites remain open and no earlier user deferral is revoked by local source/test work.

## Follow-up — 4 November 2026

58. DI: At the user's request, replace manual shared dependency wiring with Koin 4.2.2, the [current stable release](https://insert-koin.io/docs/support/releases/). This supersedes the frozen design's choice to avoid a DI framework; attendance and platform contracts are unchanged. `di/AppModules.kt` registers shared services and lifecycle ViewModel factories; native hosts supply database/location/authorization/reminder/widget bindings. Each `AppGraph` owns an isolated container, and shared Compose uses `KoinIsolatedContext` with the existing Navigation 3 entry stores. Android workers and UI use the same container; permissions are registered when the Activity supplies its retained port. Platform modules load last so native reporting hooks and test clocks override shared defaults. Desktop closes the container before its host-owned database. Cost if wrong: incorrect runtime bindings can fail at resolution, so the DI smoke test resolves every ViewModel, checks route parameters, and verifies separate graphs do not share singletons.

    Verification: 296 shared JVM/Android host, Android app, and desktop tests passed; the Android debug APK and journey fixtures compiled, as did both iOS architectures. The simulator framework link failed when Xcode's `libtool` could not write its output (`errno=28`, no space left on device). Native linking and device journeys remain unverified for this change.

59. DI follow-up: Audited Kotlin and native host construction across the repository. Koin now supplies the attendance repository to plan/time-off services, settings to setup/deletion/widgets, the reminder plan reader, reporting store/HTTP client/transport, Android authorization/scheduler/job factories, and the shared macOS location adapter. Android location diagnostics reuse the application gateway. Constructor defaults remain for existing standalone tests; application modules explicitly inject these dependencies. ReportingRuntime retains ownership of its callback-coupled sync/connection/lifecycle coordinators, native gateways retain their sensor helpers, and standalone diagnostic probes retain their operation-owned resources. These are implementation lifetimes rather than additional application dependency graphs. Injected reporting HTTP clients are closed during runtime teardown even when the injected transport bypasses the runtime's lazy client accessor.
