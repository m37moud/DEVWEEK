package dev.daymark.android

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.notifications.AutomaticResultNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttendanceResultNotificationTest {
    @Test
    fun publishFailureForManualActionJourney() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("daymarkNotificationQa") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val failure = AutomaticResultNotification(
            identifier = "daymark.result:qa:manual-action",
            title = "Attendance unconfirmed",
            body = "The location request timed out.",
            manualCheckLabel = "Check in manually"
        )
        AndroidAttendanceResults(context).post(notification = failure)
        assertNotNull(manager.awaitNotification(identifier = failure.identifier).actions.single().actionIntent)
    }

    @Test
    fun legacyChannelKeepsFailuresUntilExplicitOptIn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("daymarkNotificationQa") == "true"
        )
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val results = AndroidAttendanceResults(context)
        assertTrue(results.status.value.appAllowed)
        var permission: Boolean? = null
        results.requestPermission { permission = it }
        assertEquals(true, permission)
        assertTrue(results.status.value.legacyChannelOnly)
        val failure = AutomaticResultNotification(
            identifier = "daymark.result:qa:failure",
            title = "Attendance unconfirmed",
            body = "The location request timed out.",
            manualCheckLabel = "Check in manually",
            failureSoundEnabled = true
        )
        results.post(notification = failure)
        val legacyFailure = manager.awaitNotification(identifier = failure.identifier)
        assertEquals("attendance-results", legacyFailure.channelId)
        assertEquals("Check in manually", legacyFailure.actions.single().title.toString())
        assertNotNull(legacyFailure.actions.single().actionIntent)

        results.enableFailureChannel()
        assertFalse(results.status.value.legacyChannelOnly)
        val failureChannel = manager.getNotificationChannel("attendance-result-failures")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, failureChannel.importance)
        assertNotNull(failureChannel.sound)
        val successChannel = manager.getNotificationChannel("attendance-results")
        assertEquals(NotificationManager.IMPORTANCE_LOW, successChannel.importance)

        val alerted = failure.copy(identifier = "daymark.result:qa:alerted")
        results.post(notification = alerted)
        val failureNotice = manager.awaitNotification(identifier = alerted.identifier)
        assertEquals(failureChannel.id, failureNotice.channelId)
        assertEquals("Check in manually", failureNotice.actions.single().title.toString())

        val success = AutomaticResultNotification(
            identifier = "daymark.result:qa:success",
            title = "Attendance saved",
            body = "Present"
        )
        results.post(notification = success)
        val successNotice = manager.awaitNotification(identifier = success.identifier)
        assertEquals(successChannel.id, successNotice.channelId)
        assertTrue(successNotice.actions.isNullOrEmpty())
    }
}

private fun NotificationManager.awaitNotification(identifier: String): Notification {
    repeat(100) {
        activeNotifications.firstOrNull { it.tag == identifier }?.let { return it.notification }
        SystemClock.sleep(50)
    }
    error("QA result notification did not reach Android")
}

/** Runs against the previous APK on a disposable installation before the upgrade check. */
@RunWith(AndroidJUnit4::class)
class LegacyAttendanceResultFixture {
    @Test
    fun postUsingTheOriginalAdapter() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("daymarkNotificationQa") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        AndroidAttendanceResults(context).post(
            identifier = "daymark.result:qa:legacy",
            title = "Attendance saved",
            body = "Present"
        )
        assertNotNull(manager.getNotificationChannel("attendance-results"))
        assertTrue(manager.getNotificationChannel("attendance-result-failures") == null)
    }
}

/** Gives the on-device onboarding exercise a synthetic place without collecting location. */
@RunWith(AndroidJUnit4::class)
class AttendanceOptionsSetupFixture {
    @Test
    fun seedScheduleDraft() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("daymarkNotificationQa") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = dev.daymark.data.local.openAndroidDatabase(context)
        try {
            val profile = database.profileDao()
            val projections = database.dirtyProjectionDao()
            val transactions = dev.daymark.data.local.RoomDatabaseTransactions(database)
            val settings = dev.daymark.data.local.settings.SettingsRepository(
                profile,
                projections,
                transactions
            )
            val setup = dev.daymark.data.local.setup.SetupService(
                profile,
                database.planningDao(),
                projections,
                transactions,
                kotlin.time.Clock.System,
                settings
            )
            kotlinx.coroutines.runBlocking {
                assertTrue(settings.trackerIdentity() == null)
                settings.update(language = dev.daymark.settings.LanguagePreference.EN)
                assertTrue(
                    setup.saveDraft(
                        draft = dev.daymark.models.setup.SetupDraft(
                            displayName = "Sample profile",
                            category = dev.daymark.models.attendance.TrackerCategory.WORK,
                            placeName = "Sample place",
                            place = dev.daymark.models.attendance.RegisteredPlace(0.0, 0.0, 100, 1),
                            schedule = dev.daymark.plan.WeeklySchedule(
                                days = kotlinx.datetime.DayOfWeek.entries.toSet(),
                                arrival = kotlinx.datetime.LocalTime(9, 0),
                                overrides = emptyMap(),
                                graceMinutes = 5,
                                zoneId = "UTC"
                            ),
                            step = dev.daymark.app.SetupStep.SCHEDULE
                        ),
                        session = setup.startDraftSession()
                    )
                )
            }
        } finally {
            database.close()
        }
    }
}
