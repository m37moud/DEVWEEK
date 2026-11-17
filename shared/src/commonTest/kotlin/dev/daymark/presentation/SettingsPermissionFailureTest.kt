package dev.daymark.presentation

import dev.daymark.TestReminders
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationPermissionPort
import dev.daymark.models.TrackerSummary
import dev.daymark.models.attendance.CheckMode
import dev.daymark.models.settings.AppSettings
import dev.daymark.notifications.AttendanceResultNotifier
import dev.daymark.notifications.AttendanceResultPort
import dev.daymark.presentation.features.settings.SettingsViewModel
import dev.daymark.reminders.ReminderPort
import dev.daymark.repository.SettingsDataSource
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.testRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPermissionFailureTest {
    @Test
    fun foreground_and_background_request_failures_do_not_report_storage_failure() = runTest {
        for (foregroundGranted in listOf(false, true)) {
            val settings = SettingsWrites()
            val permissions = FailingPermissions(foregroundGranted)
            val model = SettingsViewModel(
                testRepository(settingsSource = settings),
                permissions,
                TestReminders,
                backgroundScope
            )
            runCurrent()
            model.setAutomation(true)
            runCurrent()
            assertFalse(model.uiState.value.busy)
            assertFalse(model.uiState.value.storageError)
            assertTrue(model.uiState.value.permissionRequestFailed)
            assertFalse(model.uiState.value.permissionDenied)
            assertEquals(0, settings.writes)

            model.setAutomation(false)
            runCurrent()
            assertEquals(1, settings.writes)
            assertFalse(model.uiState.value.permissionRequestFailed)

            permissions.failure = null
            model.setAutomation(true)
            runCurrent()
            assertEquals(2, settings.writes)
            assertFalse(model.uiState.value.permissionRequestFailed)
        }
    }

    @Test
    fun permission_cancellation_does_not_report_an_error_or_write() = runTest {
        val settings = SettingsWrites()
        val permissions = FailingPermissions(false).apply { failure = CancellationException() }
        val model = SettingsViewModel(
            testRepository(settingsSource = settings),
            permissions,
            TestReminders,
            backgroundScope
        )
        runCurrent()
        model.setAutomation(true)
        runCurrent()
        assertFalse(model.uiState.value.busy)
        assertFalse(model.uiState.value.storageError)
        assertFalse(model.uiState.value.permissionDenied)
        assertFalse(model.uiState.value.permissionRequestFailed)
        assertEquals(0, settings.writes)
    }

    @Test
    fun reminder_and_result_notification_request_failures_do_not_report_storage_failure() =
        runTest {
            val settings = SettingsWrites()
            val reminders = object : ReminderPort by TestReminders {
                override fun requestPermission(completion: (Boolean) -> Unit) =
                    error("Permission unavailable")
            }
            val notificationPort = object : AttendanceResultPort {
                override val mode = CheckMode.AUTOMATIC
                override fun requestPermission(completion: (Boolean) -> Unit) =
                    error("Permission unavailable")

                override fun post(identifier: String, title: String, body: String) =
                    error("Unexpected notification")

                override fun clearResults() = Unit
            }
            val model = SettingsViewModel(
                testRepository(settingsSource = settings),
                FailingPermissions(false),
                reminders,
                backgroundScope,
                resultNotifications = AttendanceResultNotifier(
                    settings,
                    notificationPort,
                    currentDay = { null })
            )
            runCurrent()
            model.requestReminders()
            runCurrent()
            assertFalse(model.uiState.value.storageError)
            assertFalse(model.uiState.value.requestingReminderPermission)
            assertTrue(model.uiState.value.permissionRequestFailed)
            model.setResultNotificationsEnabled(true)
            runCurrent()
            assertFalse(model.uiState.value.storageError)
            assertFalse(model.uiState.value.busy)
            assertTrue(model.uiState.value.permissionRequestFailed)
            assertEquals(0, settings.writes)
        }

    @Test
    fun granted_permissions_followed_by_a_save_failure_report_storage_failure() = runTest {
        val settings =
            SettingsWrites().apply { saveFailure = IllegalStateException("Storage unavailable") }
        val permissions = FailingPermissions(false).apply { failure = null }
        val model = SettingsViewModel(
            testRepository(settingsSource = settings),
            permissions,
            TestReminders,
            backgroundScope
        )
        runCurrent()
        model.setAutomation(true)
        runCurrent()
        assertFalse(model.uiState.value.busy)
        assertTrue(model.uiState.value.storageError)
        assertFalse(model.uiState.value.permissionRequestFailed)
        assertEquals(0, settings.writes)
    }

    private class FailingPermissions(private val foregroundGranted: Boolean) :
        LocationPermissionPort {
        var failure: Exception? = IllegalStateException("Permission unavailable")
        override fun current() = LocationCapabilities(
            foregroundGranted = foregroundGranted,
            backgroundGranted = false,
            preciseGranted = foregroundGranted,
            servicesEnabled = true
        )

        override suspend fun requestForeground(): LocationCapabilities {
            failure?.let { throw it }
            return LocationCapabilities(true, true, true, true)
        }

        override suspend fun requestBackground() = requestForeground()
        override fun openSystemSettings() = Unit
    }

    private class SettingsWrites : SettingsDataSource {
        var writes = 0
        var saveFailure: Exception? = null
        override fun observe() = flowOf(AppSettings())
        override fun observeTracker() = flowOf(TrackerSummary("tracker", "", 0, false))
        override suspend fun trackerIdentity() = "tracker"
        override suspend fun update(
            language: LanguagePreference?, theme: ThemePreference?, displayName: String?,
            remindersEnabled: Boolean?, resultNotificationsEnabled: Boolean?
        ) {
            writes++
        }

        override suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean) {
            saveFailure?.let { throw it }
            writes++
        }
    }
}
