package dev.daymark.presentation.features.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.location.LocationFailure
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.plan.parseWholeDays
import dev.daymark.presentation.component.registrationIssue
import dev.daymark.presentation.features.setup.SetupIssue
import dev.daymark.repository.Repository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PlanViewModel(
    private val repository: Repository,
    private val location: LocationGateway,
    private val permissions: LocationPermissionPort,
    val clock: Clock,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel(scope) {
    private val state = MutableStateFlow(PlanUiState())
    val uiState = state.asStateFlow()
    private var pendingPlan: PlanChange? = null
    private var pendingOff: TimeOffCommand? = null

    init {
        viewModelScope.launch {
            combine(
                repository.plans.observePlan(),
                repository.timeOff.observeEntries(),
                repository.timeOff.observeAvailableBalance()
            ) { plan, entries, balance ->
                state.update {
                    it.copy(
                        plan = plan,
                        draft = it.draft ?: plan?.let { p -> p.pending ?: p.current },
                        entries = entries,
                        availableBalance = balance,
                        ready = true,
                        storageError = false
                    )
                }
            }.catch { error ->
                if (error is CancellationException) throw error
                state.update { it.copy(ready = true, storageError = true) }
            }.collect()
        }
    }

    fun edit(change: (PlanChange) -> PlanChange) {
        if (state.value.busy) return
        val before = state.value.draft ?: return
        try {
            val after = change(before)
            pendingPlan = null
            state.update {
                it.copy(
                    draft = after,
                    preview = null,
                    invalidInput = false,
                    result = null,
                    locationIssue = null
                )
            }
        } catch (_: IllegalArgumentException) {
            state.update { it.copy(invalidInput = true) }
        }
    }

    fun preview(command: PlanChange) = launchAction {
        pendingPlan = command
        val preview = repository.plans.previewChange(command)
        state.update { it.copy(preview = preview, timeOffPreview = null, result = null) }
    }

    fun confirm(expectedRevision: Long) = launchAction {
        val command = pendingPlan
        val shown = state.value.preview
        val result =
            if (command == null || shown == null || shown.expectedRevision != expectedRevision) PlanMutationResult.StalePreview
            else repository.plans.applyChange(command, expectedRevision)
        pendingPlan = null
        state.update { it.copy(result = result, preview = null) }
        if (result == PlanMutationResult.Applied) {
            val saved = repository.plans.observePlan().first()
            state.update {
                it.copy(
                    plan = saved,
                    draft = saved?.let { p -> p.pending ?: p.current })
            }
        }
    }

    fun previewTimeOff(command: TimeOffCommand) = launchAction {
        pendingOff = command
        val preview = repository.timeOff.preview(command)
        state.update { it.copy(timeOffPreview = preview, preview = null, result = null) }
    }

    fun confirmTimeOff(expectedRevision: Long) = launchAction {
        val command = pendingOff
        val shown = state.value.timeOffPreview
        val result =
            if (command == null || shown == null || shown.expectedRevision != expectedRevision) PlanMutationResult.StalePreview
            else repository.timeOff.apply(command, expectedRevision)
        pendingOff = null
        state.update { it.copy(result = result, timeOffPreview = null) }
    }

    fun cancelPreview() {
        if (state.value.busy) return
        pendingPlan = null; pendingOff = null
        state.update { it.copy(preview = null, timeOffPreview = null) }
    }

    fun setAvailableBalance(text: String, note: String?) {
        val value = parseWholeDays(text)
        if (value == null) {
            state.update { it.copy(invalidInput = true) }; return
        }
        launchAction {
            val result = repository.timeOff.setAvailableBalance(value, note)
            state.update { it.copy(result = result, invalidInput = false) }
        }
    }

    fun registerCurrentPlace() = launchAction {
        var capability = permissions.current()
        if (!capability.foregroundGranted || !capability.preciseGranted) capability =
            permissions.requestForeground()
        val issue = when {
            !capability.foregroundGranted || !capability.preciseGranted -> SetupIssue.LOCATION_DENIED
            !capability.servicesEnabled -> SetupIssue.LOCATION_DISABLED
            else -> null
        }
        if (issue != null) {
            state.update { it.copy(locationIssue = issue) }; return@launchAction
        }
        val fix = withTimeoutOrNull(30.seconds) { location.requestFix(30.seconds) }
        if (fix !is LocationResult.Fix) {
            val reason = (fix as? LocationResult.Failure)?.reason
            state.update {
                it.copy(
                    locationIssue = when (reason) {
                        LocationFailure.PERMISSION_DENIED -> SetupIssue.LOCATION_DENIED
                        LocationFailure.SERVICES_DISABLED -> SetupIssue.LOCATION_DISABLED
                        LocationFailure.TIMEOUT, null -> SetupIssue.LOCATION_TIMEOUT
                        else -> SetupIssue.LOCATION_UNAVAILABLE
                    }
                )
            }; return@launchAction
        }
        val failure = registrationIssue(fix, clock.now())
        if (failure != null) state.update { it.copy(locationIssue = failure) }
        else {
            val draft = state.value.draft ?: return@launchAction
            pendingPlan = null
            state.update {
                it.copy(
                    draft = draft.copy(
                        place = RegisteredPlace(
                            fix.latitude,
                            fix.longitude,
                            draft.place.radiusMetres,
                            draft.place.version
                        )
                    ),
                    preview = null, locationIssue = null
                )
            }
        }
    }

    fun openLocationSettings() = permissions.openSystemSettings()
    private fun launchAction(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, storageError = false) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(storageError = true) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
