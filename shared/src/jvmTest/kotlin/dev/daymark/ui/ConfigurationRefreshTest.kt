package dev.daymark.ui

import dev.daymark.*
import dev.daymark.app.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import dev.daymark.reminders.*
import dev.daymark.settings.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class ConfigurationRefreshTest {
    @Test fun arabic_refresh_replaces_future_notification_copy_and_widget_hook_without_changing_identity() = runBlocking {
        val folder = Files.createTempDirectory("daymark-copy-"); val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db)
            val repo = RoomAttendanceRepository(db,TestClock()); repo.reconcile(at("09:02:00"))
            val before = repo.loadCurrentDay(at("09:02:00"))!!
            val preferences = SettingsRepository(db)
            preferences.update(language = LanguagePreference.AR,remindersEnabled = true)
            var payload: String? = null
            val widgets = mutableListOf<AppLanguage>()
            val reminders = object : ReminderPort {
                override fun requestPermission(completion: (Boolean) -> Unit) = error("Refresh must not prompt")
                override fun replace(remindersJson: String,completion: (Boolean) -> Unit) { payload = remindersJson; completion(true) }
                override fun cancelTracker(trackerId: String,completion: (Boolean) -> Unit) { completion(true) }
            }
            val coordinator = ConfigurationCoordinator(db,TestClock(),reminders,onScheduleRefresh = { error("No Android automation on iOS") },
                onWidgetRefresh = { widgets += it; true })
            val result = coordinator.refresh(preferences.observe().first(),AppLanguage.AR,ReminderCopy("ar","حان وقت الحضور","تحقق من موقعك"),HostPlatform.IOS)
            assertEquals(true,result.remindersOk)
            assertEquals(listOf(AppLanguage.AR),widgets)
            val entries = Json.parseToJsonElement(payload!!).jsonObject.getValue("reminders").jsonArray
            assertTrue(entries.isNotEmpty())
            assertTrue(entries.all { it.jsonObject.getValue("localeTag").jsonPrimitive.content == "ar" })
            assertEquals(before.plan.key,repo.loadCurrentDay(at("09:02:00"))!!.plan.key)
            assertEquals(before.evidence,repo.loadCurrentDay(at("09:02:00"))!!.evidence)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
}
