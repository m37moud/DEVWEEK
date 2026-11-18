package dev.daymark.presentation

import dev.daymark.TestRepository
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.presentation.features.plan.PlanViewModel
import dev.daymark.repository.PlanDataSource
import dev.daymark.repository.TimeOffDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlanBalanceSaveTest {
    @Test
    fun revisionAdvancesOnlyAfterAppliedAcknowledgement() = runTest {
        var reply = CompletableDeferred<PlanMutationResult>()
        val repository = object : TestRepository() {
            override val plans = object : PlanDataSource {
                override fun observePlan() = flowOf<PlanState?>(null)
                override suspend fun previewChange(command: PlanChange): TimeOffPreview =
                    error("Unused")

                override suspend fun applyChange(
                    command: PlanChange,
                    expectedRevision: Long
                ): PlanMutationResult = error("Unused")
            }
            override val timeOff = object : TimeOffDataSource {
                override fun observeEntries() = flowOf(emptyList<TimeOffEntry>())
                override fun observeAvailableBalance() = flowOf<Int?>(10)
                override suspend fun preview(command: TimeOffCommand): TimeOffPreview =
                    error("Unused")

                override suspend fun apply(
                    command: TimeOffCommand,
                    expectedRevision: Long
                ): PlanMutationResult = error("Unused")

                override suspend fun setAvailableBalance(targetDays: Int, note: String?) =
                    reply.await()
            }
        }
        val location = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult =
                error("Unexpected GPS")
        }
        val permissions = object : LocationPermissionPort {
            override fun current(): LocationCapabilities = error("Unexpected permission access")
            override suspend fun requestForeground(): LocationCapabilities =
                error("Unexpected permission request")

            override suspend fun requestBackground(): LocationCapabilities =
                error("Unexpected permission request")

            override fun openSystemSettings() = error("Unexpected settings access")
        }
        val model = PlanViewModel(repository, location, permissions, Clock.System, backgroundScope)
        runCurrent()
        model.setAvailableBalance("12", null)
        runCurrent()
        assertTrue(model.uiState.value.busy)
        assertEquals(0L, model.uiState.value.balanceSaveRevision)
        reply.complete(PlanMutationResult.LocalSaveFailed)
        runCurrent()
        assertEquals(PlanMutationResult.LocalSaveFailed, model.uiState.value.result)
        assertEquals(0L, model.uiState.value.balanceSaveRevision)
        reply = CompletableDeferred()
        model.setAvailableBalance("12", null)
        runCurrent()
        assertEquals(null, model.uiState.value.result)
        reply.complete(PlanMutationResult.Applied)
        runCurrent()
        assertEquals(1L, model.uiState.value.balanceSaveRevision)
    }
}
