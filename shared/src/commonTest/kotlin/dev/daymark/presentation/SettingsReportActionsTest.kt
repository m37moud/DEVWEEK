package dev.daymark.presentation

import dev.daymark.TestReminders
import dev.daymark.TestRepository
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationPermissionPort
import dev.daymark.models.DeletionResult
import dev.daymark.models.TrackerSummary
import dev.daymark.models.reporting.ConnectionResult
import dev.daymark.models.reporting.ReportHealth
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.settings.SettingsViewModel
import dev.daymark.repository.DataLifecycleDataSource
import dev.daymark.repository.ReportingDataSource
import dev.daymark.repository.SettingsDataSource
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsReportActionsTest {
    @Test
    fun connection_retry_and_confirmed_deletion_are_owned_by_the_view_model() = runTest {
        val calls = mutableListOf<String>()
        val reports = object : ReportingDataSource {
            override val health = MutableStateFlow(ReportHealth())
            override suspend fun connect(): ConnectionResult {
                calls += "connect"; return ConnectionResult.Cancelled
            }

            override suspend fun reconnect(): ConnectionResult {
                calls += "reconnect"; return ConnectionResult.Cancelled
            }

            override suspend fun selectReport(reportId: String): ConnectionResult {
                calls += "select"; return ConnectionResult.Cancelled
            }

            override suspend fun rebuildReport(): ConnectionResult {
                calls += "rebuild"; return ConnectionResult.Cancelled
            }

            override suspend fun createReplacementReport(): ConnectionResult {
                calls += "replacement"; return ConnectionResult.Cancelled
            }

            override suspend fun disconnect() {
                calls += "disconnect"
            }

            override suspend fun refreshConnection(result: ConnectionResult?) {
                health.value = health.value.copy(connectionResult = result)
            }

            override fun retryReport() {
                calls += "retry"
            }
        }
        val repository = object : TestRepository() {
            override val reporting = reports
            override val settings = object : SettingsDataSource {
                override fun observe() = flowOf(AppSettings())
                override fun observeTracker() = flowOf<TrackerSummary?>(null)
                override suspend fun trackerIdentity(): String? = null
                override suspend fun update(
                    language: LanguagePreference?,
                    theme: ThemePreference?,
                    displayName: String?,
                    remindersEnabled: Boolean?
                ) = error("Unexpected settings write")

                override suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean) =
                    error("Unexpected automation write")
            }
            override val dataLifecycle = object : DataLifecycleDataSource {
                override val deleting = MutableStateFlow(false)
                override suspend fun deleteLocalData(): DeletionResult {
                    calls += "delete"; return DeletionResult.Failed
                }
            }
        }
        val permissions = object : LocationPermissionPort {
            override fun current() = LocationCapabilities(false, false, false, false)
            override suspend fun requestForeground() = current()
            override suspend fun requestBackground() = current()
            override fun openSystemSettings() = Unit
        }
        val model = SettingsViewModel(repository, permissions, TestReminders, backgroundScope)
        runCurrent()
        assertTrue(calls.isEmpty())
        model.connectReport()
        assertTrue(model.uiState.value.reportBusy)
        model.connectReport()
        runCurrent()
        assertEquals(listOf("connect"), calls)
        assertEquals(ConnectionResult.Cancelled, model.uiState.value.report.connectionResult)
        assertFalse(model.uiState.value.reportBusy)
        model.reconnectReport(); runCurrent()
        model.selectReport("report"); runCurrent()
        model.rebuildReport(); runCurrent()
        model.createReplacementReport(); runCurrent()
        model.disconnectReport(); runCurrent()
        assertEquals(null, model.uiState.value.report.connectionResult)
        model.retryReport()
        model.deleteLocalData(); runCurrent()
        assertTrue(model.uiState.value.deletionFailed)
        assertEquals(
            listOf(
                "connect",
                "reconnect",
                "select",
                "rebuild",
                "replacement",
                "disconnect",
                "retry",
                "delete"
            ), calls
        )
    }
}
