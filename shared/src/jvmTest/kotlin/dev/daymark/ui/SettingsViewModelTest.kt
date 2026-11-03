package dev.daymark.ui

import dev.daymark.at
import dev.daymark.attendance.RoomAttendanceRepository
import dev.daymark.attendance.TestClock
import dev.daymark.data.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationPermissionPort
import dev.daymark.reminders.ReminderPort
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.SettingsRepository
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @Test
    fun granted_reminder_permission_is_preserved_when_local_save_fails() = runTest {
        val folder = Files.createTempDirectory("daymark-reminder-save-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val permissions = object : LocationPermissionPort {
            override fun current() = LocationCapabilities(false, false, false, true)
            override suspend fun requestForeground() = current()
            override suspend fun requestBackground() = current()
            override fun openSystemSettings() = Unit
        }
        val reminders = object : ReminderPort {
            override fun requestPermission(completion: (Boolean) -> Unit) = completion(true)
            override fun replace(remindersJson: String, completion: (Boolean) -> Unit) =
                error("No scheduling during setup")

            override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) =
                error("No cancellation during setup")
        }
        try {
            val model = SettingsViewModel(db, SettingsRepository(db), permissions, backgroundScope)
            model.uiState.first { it.ready }
            model.requestReminders(reminders)
            model.uiState.first { !it.busy && it.settings.remindersEnabled }
            assertTrue(SettingsRepository(db).observe().first().remindersEnabled)
            db.close()
            model.requestReminders(reminders)
            val failed = model.uiState.first { !it.busy && it.storageError }
            assertEquals(true, failed.reminderPermissionGranted)
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun language_name_and_theme_persist_without_changing_attendance_identity_and_denied_automation_stays_off() =
        runTest {
            val folder = Files.createTempDirectory("daymark-settings-ui-");
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            try {
                seedDatabase(db)
                db.dao().putTracker(db.dao().tracker()!!.copy(automaticEnabled = false))
                val repo = RoomAttendanceRepository(db, TestClock()); repo.reconcile(at("09:02:00"))
                val before = repo.loadCurrentDay(at("09:02:00"))!!
                var requests = 0
                val permissions = object : LocationPermissionPort {
                    override fun current() = LocationCapabilities(false, false, false, true)
                    override suspend fun requestForeground(): LocationCapabilities {
                        requests++; return current()
                    }

                    override suspend fun requestBackground(): LocationCapabilities =
                        error("Foreground was denied")

                    override fun openSystemSettings() = Unit
                }
                val model =
                    SettingsViewModel(db, SettingsRepository(db), permissions, backgroundScope)
                model.uiState.first { it.ready }
                model.setLanguage(LanguagePreference.AR)
                model.uiState.first { !it.busy && it.settings.language == LanguagePreference.AR }
                model.setTheme(ThemePreference.DARK)
                model.uiState.first { !it.busy && it.settings.theme == ThemePreference.DARK }
                model.setDisplayName("x".repeat(50_001))
                assertTrue(model.uiState.value.invalidName)
                model.setDisplayName("أحمد / Ahmed =1+2")
                model.uiState.first { !it.busy && it.settings.displayName == "أحمد / Ahmed =1+2" }
                model.setAutomation(true)
                model.uiState.first { !it.busy && it.permissionDenied }
                assertEquals(1, requests)
                assertFalse(db.dao().tracker()!!.automaticEnabled)
                assertEquals(before.plan.key, repo.loadCurrentDay(at("09:02:00"))!!.plan.key)
                assertEquals(before.evidence, repo.loadCurrentDay(at("09:02:00"))!!.evidence)
                assertEquals(
                    LanguagePreference.AR,
                    SettingsRepository(db).observe().first().language
                )
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }
}
