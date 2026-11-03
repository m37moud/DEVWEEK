package dev.daymark.widgets

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import dev.daymark.attendance.RoomAttendanceRepository
import dev.daymark.attendance.TestClock
import dev.daymark.data.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.data.testCommit
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.SettingsRepository
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WidgetPublisherTest {
    private class Sink : WidgetSink {
        var json: String? = null
        var succeeds = true
        var clears = 0
        override fun publish(json: String): Boolean {
            if (succeeds) this.json = json; return succeeds
        }

        override fun clear(): Boolean {
            clears++; json = null; return succeeds
        }
    }

    @Test
    fun publication_uses_persisted_locale_and_public_metadata_only_then_clears_deleted_tracker() =
        runBlocking {
            val folder = Files.createTempDirectory("daymark-widget-");
            val db = openJvmDatabase(folder.resolve("test.db").toString())
            try {
                seedDatabase(db)
                val clock = TestClock();
                val repo = RoomAttendanceRepository(db, clock)
                repo.completeCheck(testCommit())
                val settings = SettingsRepository(db); settings.update(
                    language = LanguagePreference.AR,
                    theme = ThemePreference.DARK
                )
                val sink = Sink()
                val publisher = WidgetPublisher(
                    db,
                    repo,
                    sink,
                    clock,
                    systemTags = { listOf("en") },
                    statusLabel = { _, language -> if (language == AppLanguage.AR) "حاضر" else "Present" })
                val before = repo.loadCurrentDay(clock.now())!!
                publisher.refresh()
                val snapshot = decodeWidgetSnapshot(sink.json!!)!!
                assertEquals("ar", snapshot.effectiveLocale)
                assertEquals("حاضر", snapshot.displayStatus)
                assertEquals("DARK", snapshot.themePreference)
                assertEquals("2026-10-27", snapshot.attendanceDate)
                val keys = Json.parseToJsonElement(sink.json!!).jsonObject.keys
                assertFalse(keys.any {
                    it.contains("token", true) || it.contains(
                        "latitude",
                        true
                    ) || it.contains("longitude", true) || it.contains("path", true)
                })
                assertEquals(before, repo.loadCurrentDay(clock.now()))
                settings.update(language = LanguagePreference.EN)
                publisher.refresh()
                assertEquals("en", decodeWidgetSnapshot(sink.json!!)!!.effectiveLocale)
                assertEquals("Present", decodeWidgetSnapshot(sink.json!!)!!.displayStatus)
                db.useWriterConnection { it.executeSQL("DELETE FROM trackers") }
                publisher.refresh()
                assertNull(sink.json)
                assertEquals(1, sink.clears)
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

    @Test
    fun failed_native_publication_does_not_change_a_saved_attendance_record() = runBlocking {
        val folder = Files.createTempDirectory("daymark-widget-failure-");
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db)
            val clock = TestClock();
            val repo = RoomAttendanceRepository(db, clock); repo.completeCheck(testCommit())
            val before = repo.loadCurrentDay(clock.now())
            val sink = Sink().apply { succeeds = false }
            val publisher = WidgetPublisher(
                db,
                repo,
                sink,
                clock,
                systemTags = { listOf("en") },
                statusLabel = { _, _ -> "Present" })
            assertFailsWith<WidgetPublicationException> { publisher.refresh() }
            assertEquals(before, repo.loadCurrentDay(clock.now()))
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }
}
