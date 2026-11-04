package dev.daymark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.app.SetupCommand
import dev.daymark.app.SetupDraftLoad
import dev.daymark.app.SetupStep
import dev.daymark.app.invalidStep
import dev.daymark.app.restoredStep
import dev.daymark.app.SetupDraft
import dev.daymark.app.SetupResult
import dev.daymark.app.SetupService
import dev.daymark.attendance.RegisteredPlace
import dev.daymark.attendance.TrackerCategory
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.plan.WeeklySchedule
import dev.daymark.plan.parseWholeDays
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

enum class SetupIssue {
    INVALID_INPUT, LOCATION_DENIED, LOCATION_DISABLED, LOCATION_TIMEOUT, LOCATION_UNAVAILABLE,
    LOCATION_STALE, LOCATION_INACCURATE, LOCAL_SAVE_FAILED
}

data class SetupUiState(
    val draft: SetupDraft = SetupDraft(), val ready: Boolean = false, val busy: Boolean = false,
    val completed: Boolean = false, val issue: SetupIssue? = null, val draftSaved: Boolean = false,
    val unreadable: Boolean = false, val reviewEditing: Boolean = false,
    val pendingStep: SetupStep? = null
)

class SetupViewModel(
    private val service: SetupService,
    private val location: LocationGateway,
    private val permissions: LocationPermissionPort,
    private val clock: Clock,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel(scope) {
    private val state = MutableStateFlow(SetupUiState())
    val uiState = state.asStateFlow()

    private data class DraftWrite(val draft: SetupDraft, val revision: Long)

    private val writes = Channel<DraftWrite>(Channel.UNLIMITED)
    private var revision = 0L
    private var draftSession: String? = null

    init {
        retryLoad()
        viewModelScope.launch {
            for (write in writes) {
                if (write.revision == revision) persist(write)
            }
        }
    }

    private suspend fun persist(write: DraftWrite) {
        try {
            val saved = service.saveDraft(write.draft, requireNotNull(draftSession))
            if (!saved) {
                saveFailed(write.revision)
                return
            }
            if (write.revision == revision) state.update {
                it.copy(
                    draft = write.draft,
                    draftSaved = true,
                    busy = it.busy && it.pendingStep == null,
                    pendingStep = null
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: androidx.sqlite.SQLiteException) {
            saveFailed(write.revision)
        } catch (_: IllegalStateException) {
            saveFailed(write.revision)
        }
    }

    private fun saveFailed(failedRevision: Long) {
        if (revision == failedRevision) state.update {
            it.copy(busy = it.busy && it.pendingStep == null, draftSaved = false, issue = SetupIssue.LOCAL_SAVE_FAILED)
        }
    }

    fun retryLoad() {
        if (state.value.ready || state.value.busy) return
        state.update { it.copy(busy = true, issue = null) }
        viewModelScope.launch {
            try {
                draftSession = service.startDraftSession()
                val loaded = service.observeDraft().first()
                if (loaded is SetupDraftLoad.Unreadable) {
                    state.value = SetupUiState(issue = SetupIssue.LOCAL_SAVE_FAILED, unreadable = true)
                    return@launch
                }
                val saved = (loaded as? SetupDraftLoad.Loaded)?.draft ?: SetupDraft()
                val draft = saved.copy(
                    step = saved.restoredStep(clock.now()),
                    category = saved.category ?: TrackerCategory.WORK,
                    schedule = saved.schedule ?: WeeklySchedule(
                        setOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                        ),
                        LocalTime(9, 0), emptyMap(), 5, TimeZone.currentSystemDefault().id
                    )
                )
                state.value = SetupUiState(draft = draft, ready = true, draftSaved = saved == draft)
                if (saved != draft || loaded is SetupDraftLoad.Absent) enqueueDraft(draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: androidx.sqlite.SQLiteException) {
                state.value = SetupUiState(issue = SetupIssue.LOCAL_SAVE_FAILED)
            } catch (_: IllegalStateException) {
                state.value = SetupUiState(issue = SetupIssue.LOCAL_SAVE_FAILED)
            }
        }
    }

    private fun canEdit() = state.value.ready && !state.value.busy && !state.value.completed

    fun updateDraft(draft: SetupDraft) {
        if (!canEdit()) return
        val previous = state.value
        state.update {
            it.copy(
                draft = draft.copy(step = previous.draft.step), pendingStep = null, draftSaved = false,
                issue = previous.issue.takeIf { issue -> issue == SetupIssue.LOCAL_SAVE_FAILED })
        }
        revision++
        if (state.value.issue != SetupIssue.LOCAL_SAVE_FAILED) enqueueDraft(state.value.draft)
    }

    private fun enqueueDraft(draft: SetupDraft) {
        writes.trySend(DraftWrite(draft, ++revision))
    }

    fun retrySave() {
        if (!canEdit()) return
        val pending = state.value.pendingStep
        state.update { it.copy(issue = null, busy = pending != null) }
        enqueueDraft(state.value.draft.copy(step = pending ?: state.value.draft.step))
    }

    fun advance() {
        if (!canEdit()) return
        val current = state.value.draft
        val invalid = current.invalidStep(clock.now())
        if (invalid != null && invalid.ordinal <= current.step.ordinal) {
            showInvalid(invalid)
            return
        }
        val destination = if (state.value.reviewEditing) invalid ?: SetupStep.REVIEW
        else SetupStep.entries.getOrNull(current.step.ordinal + 1) ?: return
        navigate(destination)
    }

    fun back() {
        if (!canEdit() || state.value.draft.step == SetupStep.WELCOME) return
        state.update { it.copy(reviewEditing = false) }
        navigate(SetupStep.entries[state.value.draft.step.ordinal - 1])
    }

    fun edit(step: SetupStep) {
        if (!canEdit() || state.value.draft.step != SetupStep.REVIEW) return
        if (step !in listOf(SetupStep.PROFILE, SetupStep.PLACE, SetupStep.SCHEDULE)) return
        state.update { it.copy(reviewEditing = true) }
        navigate(step)
    }

    private fun navigate(step: SetupStep) {
        state.update { it.copy(busy = true, issue = null, pendingStep = step, draftSaved = false) }
        enqueueDraft(state.value.draft.copy(step = step))
    }

    private fun showInvalid(step: SetupStep) {
        if (state.value.draft.step != step) navigate(step)
        state.update { it.copy(issue = SetupIssue.INVALID_INPUT) }
    }

    fun resetDraft() {
        if (state.value.ready || state.value.busy || !state.value.unreadable) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                service.resetDraft(requireNotNull(draftSession))
                state.value = SetupUiState()
                retryLoad()
            } catch (_: androidx.sqlite.SQLiteException) {
                state.update { it.copy(busy = false, issue = SetupIssue.LOCAL_SAVE_FAILED) }
            } catch (_: IllegalStateException) {
                state.update { it.copy(busy = false, issue = SetupIssue.LOCAL_SAVE_FAILED) }
            }
        }
    }

    fun useCurrentLocation() {
        if (!canEdit()) return
        state.update { it.copy(busy = true, issue = null) }
        viewModelScope.launch {
            try {
                var capability = permissions.current()
                if (!capability.foregroundGranted || !capability.preciseGranted) capability =
                    permissions.requestForeground()
                if (!capability.foregroundGranted || !capability.preciseGranted) {
                    state.update { it.copy(issue = SetupIssue.LOCATION_DENIED) }; return@launch
                }
                if (!capability.servicesEnabled) {
                    state.update { it.copy(issue = SetupIssue.LOCATION_DISABLED) }; return@launch
                }
                val result = withTimeoutOrNull(30.seconds) { location.requestFix(30.seconds) }
                    ?: LocationResult.Failure(LocationFailure.TIMEOUT)
                when (result) {
                    is LocationResult.Failure -> state.update {
                        it.copy(
                            issue = when (result.reason) {
                                LocationFailure.PERMISSION_DENIED -> SetupIssue.LOCATION_DENIED
                                LocationFailure.SERVICES_DISABLED -> SetupIssue.LOCATION_DISABLED
                                LocationFailure.TIMEOUT -> SetupIssue.LOCATION_TIMEOUT
                                else -> SetupIssue.LOCATION_UNAVAILABLE
                            }
                        )
                    }

                    is LocationResult.Fix -> {
                        val issue = registrationIssue(result, clock.now())
                        if (issue != null) state.update { it.copy(issue = issue) }
                        else {
                            val draft = state.value.draft.copy(
                                place = RegisteredPlace(
                                    result.latitude,
                                    result.longitude,
                                    state.value.draft.place?.radiusMetres ?: 100,
                                    1
                                )
                            )
                            state.update { it.copy(draft = draft, draftSaved = false) }
                            enqueueDraft(draft)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                state.update { it.copy(issue = SetupIssue.LOCATION_UNAVAILABLE) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    fun submit() {
        if (!canEdit()) return
        val draft = state.value.draft
        val balance =
            if (draft.category == TrackerCategory.WORK) parseWholeDays(draft.openingBalanceText) else 0
        val invalid = draft.invalidStep(clock.now())
        if (invalid != null) {
            showInvalid(invalid)
            return
        }
        val category = requireNotNull(draft.category)
        val place = requireNotNull(draft.place)
        val schedule = requireNotNull(draft.schedule)
        val openingBalance = requireNotNull(balance)
        revision++
        state.update { it.copy(busy = true, issue = null) }
        viewModelScope.launch {
            try {
                when (service.complete(
                    SetupCommand(
                        draft.displayName,
                        category,
                        draft.placeName,
                        place,
                        schedule,
                        openingBalance
                    )
                )) {
                    SetupResult.Completed -> state.update { it.copy(completed = true) }
                    SetupResult.Invalid -> showInvalid(SetupStep.SCHEDULE)
                    SetupResult.LocalSaveFailed -> state.update { it.copy(issue = SetupIssue.LOCAL_SAVE_FAILED) }
                }
            } finally {
                state.update { it.copy(busy = it.pendingStep != null) }
            }
        }
    }
}
