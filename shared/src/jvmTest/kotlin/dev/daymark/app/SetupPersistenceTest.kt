package dev.daymark.app

import dev.daymark.at
import dev.daymark.attendance.RoomAttendanceRepository
import dev.daymark.attendance.TestClock
import dev.daymark.attendance.TrackerCategory
import dev.daymark.data.openJvmDatabase
import dev.daymark.fixturePlace
import dev.daymark.fixtureSchedule
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.SettingsRepository
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupPersistenceTest {
    @Test
    fun draft_and_preferences_survive_reopen_without_creating_a_tracker() = runTest {
        val folder = Files.createTempDirectory("daymark-draft-");
        val path = folder.resolve("app.db").toString()
        var db = openJvmDatabase(path)
        val clock = TestClock()
        try {
            val draft = SetupDraft(
                "أحمد / Ahmed",
                TrackerCategory.WORK,
                "  المكتب  ",
                null,
                fixtureSchedule(),
                "١٢"
            )
            SetupService(db, clock).saveDraft(draft)
            SettingsRepository(db).update(
                language = LanguagePreference.AR,
                theme = ThemePreference.DARK
            )
            db.close(); db = openJvmDatabase(path)
            assertEquals(draft, SetupService(db, clock).observeDraft().first())
            val settings = SettingsRepository(db).observe().first()
            assertEquals(LanguagePreference.AR, settings.language)
            assertEquals(ThemePreference.DARK, settings.theme)
            assertNull(db.dao().tracker())
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun late_installation_starts_at_next_eligible_arrival_and_does_not_backfill() = runTest {
        val folder = Files.createTempDirectory("daymark-setup-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val clock = TestClock(at("09:02:00"))
        try {
            val service = SetupService(db, clock)
            val command = SetupCommand(
                "  أحمد / Ahmed  ",
                TrackerCategory.WORK,
                "Work",
                fixturePlace(),
                fixtureSchedule(),
                12
            )
            assertIs<SetupResult.Completed>(service.complete(command))
            val tracker = requireNotNull(db.dao().tracker())
            assertEquals("  أحمد / Ahmed  ", tracker.displayName)
            assertEquals("2026-10-28T09:00:00Z", tracker.trackingStartsAt)
            assertEquals("UTC", db.dao().versions(tracker.id).single().schedule().zoneId)
            val repository = RoomAttendanceRepository(db, clock)
            repository.reconcile(clock.now())
            assertTrue(db.dao().days(tracker.id).isEmpty())
            assertIs<SetupResult.Invalid>(service.complete(command))
            assertEquals(tracker.id, db.dao().tracker()?.id)
            assertEquals(1, db.dao().versions(tracker.id).size)
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun invalid_setup_does_not_create_partial_state_and_google_is_not_required() = runTest {
        val folder = Files.createTempDirectory("daymark-invalid-setup-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val clock = TestClock(at("08:00:00"))
        try {
            val service = SetupService(db, clock)
            val command = SetupCommand(
                "Person",
                TrackerCategory.WORK,
                "Place",
                fixturePlace(),
                fixtureSchedule(),
                12
            )
            assertIs<SetupResult.Invalid>(service.complete(command.copy(displayName = "  ")))
            assertIs<SetupResult.Invalid>(service.complete(command.copy(openingBalance = -1)))
            assertIs<SetupResult.Invalid>(
                service.complete(
                    command.copy(
                        schedule = fixtureSchedule(
                            arrival = LocalTime(23, 59)
                        )
                    )
                )
            )
            assertNull(db.dao().tracker())
            assertIs<SetupResult.Completed>(service.complete(command))
            val tracker = requireNotNull(db.dao().tracker())
            assertEquals("2026-10-27T09:00:00Z", tracker.trackingStartsAt)
            assertNull(db.dao().binding(tracker.id))
        } finally {
            db.close(); folder.toFile().deleteRecursively()
        }
    }
}
