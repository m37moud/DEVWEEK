package dev.daymark.widgets

import dev.daymark.data.local.RoomDatabaseTransactions

import androidx.room3.withWriteTransaction
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.deleteAllLocalRows
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.data.seedDatabase
import dev.daymark.data.testCommit
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
                val repo = RoomAttendanceRepository(
                    db.profileDao(),
                    db.attendanceDao(),
                    db.planningDao(),
                    db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db),
                    clock
                )
                repo.completeCheck(testCommit())
                val settings =
                    SettingsRepository(
                        db.profileDao(),
                        db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db)
                    ); settings.update(
                    language = LanguagePreference.AR,
                    theme = ThemePreference.DARK
                )
                val sink = Sink()
                val publisher = WidgetPublisher(
                    db.profileDao(), db.planningDao(),
                    RoomDatabaseTransactions(db),
                    repo,
                    sink,
                    clock,
                    systemTags = { listOf("en") },
                    statusLabel = { _, language -> if (language == AppLanguage.AR) "حاضر" else "Present" },
                    settings = dev.daymark.data.local.settings.SettingsRepository(
                        db.profileDao(), db.dirtyProjectionDao(),
                        RoomDatabaseTransactions(db)
                    )
                )
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
                db.withWriteTransaction { db.localDataDeletionDao().deleteAllLocalRows() }
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
            val repo = RoomAttendanceRepository(
                db.profileDao(), db.attendanceDao(), db.planningDao(), db.dirtyProjectionDao(),
                RoomDatabaseTransactions(db),
                clock
            ); repo.completeCheck(testCommit())
            val before = repo.loadCurrentDay(clock.now())
            val sink = Sink().apply { succeeds = false }
            val publisher = WidgetPublisher(
                db.profileDao(), db.planningDao(),
                RoomDatabaseTransactions(db),
                repo,
                sink,
                clock,
                systemTags = { listOf("en") },
                statusLabel = { _, _ -> "Present" },
                settings = dev.daymark.data.local.settings.SettingsRepository(
                    db.profileDao(), db.dirtyProjectionDao(),
                    RoomDatabaseTransactions(db)
                )
            )
            assertFailsWith<WidgetPublicationException> { publisher.refresh() }
            assertEquals(before, repo.loadCurrentDay(clock.now()))
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }
}
