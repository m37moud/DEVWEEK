package dev.daymark.presentation

import dev.daymark.TestReminders
import dev.daymark.TestRepository
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationPermissionPort
import dev.daymark.models.TrackerSummary
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.AnnualVacationSettings
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.SettingsViewModel
import dev.daymark.repository.SettingsDataSource
import dev.daymark.repository.TimeOffDataSource
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.testRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAnnualVacationTest {
    @Test
    fun annual_policy_save_forwards_reviewed_year_and_reports_shortfall_after_acknowledgement() =
        runTest {
            val configuration =
                AnnualVacationSettings(active = null, pending = null, nextEffectiveYear = 2027)
            val observations = MutableStateFlow<AnnualVacationSettings?>(configuration)
            val requests = mutableListOf<AnnualVacationPolicy>()
            val saveReply = CompletableDeferred<PlanMutationResult>()
            val timeOff = object : TimeOffDataSource by TestRepository().timeOff {
                override fun observeAnnualVacationSettings() = observations
                override suspend fun updateAnnualVacationPolicy(
                    annualAllowanceDays: Int,
                    carryOverUnusedDays: Boolean,
                    expectedEffectiveYear: Int
                ): PlanMutationResult {
                    requests += AnnualVacationPolicy(
                        expectedEffectiveYear,
                        annualAllowanceDays,
                        carryOverUnusedDays
                    )
                    return saveReply.await()
                }
            }
            val model = SettingsViewModel(
                testRepository(timeOffSource = timeOff, settingsSource = settings),
                permissions,
                TestReminders,
                backgroundScope
            )
            runCurrent()
            assertEquals(configuration, model.uiState.value.annualVacation.configuration)

            model.saveAnnualVacationPolicy(20, true, 2027)
            model.saveAnnualVacationPolicy(30, false, 2027)
            runCurrent()
            assertEquals(listOf(AnnualVacationPolicy(2027, 20, true)), requests)
            assertTrue(model.uiState.value.busy)
            assertNull(model.uiState.value.annualVacation.saveResult)

            val shortfall = PlanMutationResult.InsufficientYear(2028)
            saveReply.complete(shortfall)
            runCurrent()
            assertEquals(shortfall, model.uiState.value.annualVacation.saveResult)
            assertEquals(configuration, model.uiState.value.annualVacation.configuration)
            assertFalse(model.uiState.value.busy)
            assertFalse(model.uiState.value.storageError)
        }

    private val settings = object : SettingsDataSource {
        override fun observe() = flowOf(AppSettings())
        override fun observeTracker() = flowOf(TrackerSummary("tracker", "", 0, false))
        override suspend fun trackerIdentity() = "tracker"
        override suspend fun update(
            language: LanguagePreference?,
            theme: ThemePreference?,
            displayName: String?,
            remindersEnabled: Boolean?,
            resultNotificationsEnabled: Boolean?
        ) = Unit

        override suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean) = Unit
    }

    private val permissions = object : LocationPermissionPort {
        override fun current() = LocationCapabilities(false, false, false, true)
        override suspend fun requestForeground() = current()
        override suspend fun requestBackground() = current()
        override fun openSystemSettings() = Unit
    }
}
