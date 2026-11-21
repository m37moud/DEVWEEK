package dev.daymark.presentation.features.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.logging.AppLogger
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.repository.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class PlanViewModel(
    private val repository: Repository,
    val clock: Clock,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel(scope) {
    private val state = MutableStateFlow(PlanUiState())
    val uiState = state.asStateFlow()
    private var pendingOff: TimeOffCommand? = null

    init {
        viewModelScope.launch {
            combine(repository.plans.observePlan(), repository.timeOff.observeEntries()) { plan, entries ->
                state.update { it.copy(plan = plan, entries = entries, ready = true) }
            }.catch { error ->
                if (error is CancellationException) throw error
                AppLogger.e(TAG, "Time off load failed", error)
                state.update { it.copy(ready = true, storageError = true) }
            }.collect()
        }
    }

    fun previewTimeOff(command: TimeOffCommand) = launchAction {
        pendingOff = command
        val preview = repository.timeOff.preview(command)
        preview.rejection?.let { AppLogger.i(TAG, "Time off preview rejected: ${it.name}") }
        state.update { it.copy(timeOffPreview = preview, result = null) }
    }

    fun confirmTimeOff(review: PlanReviewToken) = launchAction {
        val command = pendingOff
        val shown = state.value.timeOffPreview
        val result =
            if (command == null || shown == null || shown.reviewToken != review) PlanMutationResult.StalePreview
            else repository.timeOff.apply(command, review)
        pendingOff = null
        state.update { it.copy(result = result, timeOffPreview = null) }
    }

    fun cancelPreview() {
        if (state.value.busy) return
        pendingOff = null
        state.update { it.copy(timeOffPreview = null) }
    }

    private fun launchAction(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLogger.e(TAG, "Time off action failed", failure)
                state.update { it.copy(result = PlanMutationResult.LocalSaveFailed, timeOffPreview = null) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val TAG = "PlanViewModel"
    }
}
