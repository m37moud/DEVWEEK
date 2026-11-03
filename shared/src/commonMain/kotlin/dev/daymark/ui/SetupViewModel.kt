package dev.daymark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.app.SetupCommand
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
    val completed: Boolean = false, val issue: SetupIssue? = null, val draftSaved: Boolean = false
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
    private val writes = Channel<SetupDraft>(Channel.CONFLATED)

    init {
        retryLoad()
        viewModelScope.launch {
            for (draft in writes) {
                try {
                    service.saveDraft(draft)
                    state.update { if (it.draft == draft) it.copy(draftSaved = true) else it }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    state.update { it.copy(issue = SetupIssue.LOCAL_SAVE_FAILED) }
                }
            }
        }
    }

    fun retryLoad() {
        if (state.value.ready || state.value.busy) return
        state.update { it.copy(busy = true, issue = null) }
        viewModelScope.launch {
            try {
                val saved = service.observeDraft().first()
                val draft = saved.copy(
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
                if (saved != draft) writes.trySend(draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = SetupUiState(issue = SetupIssue.LOCAL_SAVE_FAILED)
            }
        }
    }

    fun updateDraft(draft: SetupDraft) {
        if (!state.value.ready || state.value.busy || state.value.completed) return
        state.update { it.copy(draft = draft, issue = null, draftSaved = false) }
        writes.trySend(draft)
    }

    fun useCurrentLocation() {
        if (!state.value.ready || state.value.busy || state.value.completed) return
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
                            writes.trySend(draft)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(issue = SetupIssue.LOCATION_UNAVAILABLE) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    fun submit() {
        if (!state.value.ready || state.value.busy || state.value.completed) return
        val draft = state.value.draft
        val balance =
            if (draft.category == TrackerCategory.WORK) parseWholeDays(draft.openingBalanceText) else 0
        if (draft.category == null || draft.place == null || draft.schedule == null || balance == null) {
            state.update { it.copy(issue = SetupIssue.INVALID_INPUT) }; return
        }
        state.update { it.copy(busy = true, issue = null) }
        viewModelScope.launch {
            try {
                when (service.complete(
                    SetupCommand(
                        draft.displayName,
                        draft.category,
                        draft.placeName,
                        draft.place,
                        draft.schedule,
                        balance
                    )
                )) {
                    SetupResult.Completed -> state.update { it.copy(completed = true) }
                    SetupResult.Invalid -> state.update { it.copy(issue = SetupIssue.INVALID_INPUT) }
                    SetupResult.LocalSaveFailed -> state.update { it.copy(issue = SetupIssue.LOCAL_SAVE_FAILED) }
                }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
