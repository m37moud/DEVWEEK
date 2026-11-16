package dev.daymark.notifications

import dev.daymark.models.TrackerSummary
import dev.daymark.models.attendance.*
import dev.daymark.models.settings.AppSettings
import dev.daymark.repository.SettingsDataSource
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AttendanceResultNotifierTest {
    private val preferences = object : SettingsDataSource {
        var enabled = false
        override fun observe() = flowOf(AppSettings(resultNotificationsEnabled = enabled))
        override fun observeTracker() = flowOf<TrackerSummary?>(null)
        override suspend fun trackerIdentity() = "tracker"
        override suspend fun update(
            language: LanguagePreference?, theme: ThemePreference?, displayName: String?,
            remindersEnabled: Boolean?, resultNotificationsEnabled: Boolean?
        ) {
            resultNotificationsEnabled?.let { enabled = it }
        }

        override suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean) = Unit
    }
    private val port = object : AttendanceResultPort {
        var cleared = false
        var posts = 0
        var deliveryFails = false
        override val mode = CheckMode.AUTOMATIC
        override fun requestPermission(completion: (Boolean) -> Unit) = completion(true)
        override fun post(identifier: String, title: String, body: String) {
            posts++
            if (deliveryFails) throw IllegalStateException("Delivery unavailable")
        }

        override fun clearResults() {
            cleared = true
        }
    }
    private val notifier =
        AttendanceResultNotifier(preferences, port, text = { "Localized result" }) {
            DayKey(
                "tracker",
                LocalDate(2026, 11, 15)
            ) to present
        }
    private val present = DayOutcome(
        AttendanceStatus.PRESENT, AttendanceSource.AUTOMATIC_GPS,
        Instant.parse("2026-11-15T07:00:00Z"), null
    )

    @Test
    fun acknowledgedSuccessAndKnownSensorFailureQualify() {
        assertTrue(notifier.shouldNotify(CheckOutcome.Recorded(present)))
        assertTrue(
            notifier.shouldNotify(
                CheckOutcome.Rejected(
                    null,
                    CheckRejection.LOCATION_TIMEOUT
                )
            )
        )
        assertTrue(
            notifier.shouldNotify(
                CheckOutcome.Rejected(
                    null,
                    CheckRejection.LOCAL_SAVE_FAILED
                )
            )
        )
    }

    @Test
    fun duplicateObsoleteAndCancelledOutcomesStaySilent() {
        assertFalse(notifier.shouldNotify(CheckOutcome.Busy(null)))
        assertFalse(notifier.shouldNotify(CheckOutcome.AlreadyRecorded(present)))
        assertFalse(notifier.shouldNotify(CheckOutcome.StateChanged(null)))
        assertFalse(notifier.shouldNotify(CheckOutcome.Rejected(null, CheckRejection.CANCELLED)))
        assertFalse(
            notifier.shouldNotify(
                CheckOutcome.Rejected(
                    null,
                    CheckRejection.AFTER_AUTOMATIC_WINDOW
                )
            )
        )
    }

    @Test
    fun competingSuccessCorrectionAndTimeOffNeverClaimFailure() {
        assertFalse(
            notifier.shouldNotify(
                CheckOutcome.Rejected(
                    present,
                    CheckRejection.OUTSIDE_AREA
                )
            )
        )
        assertFalse(notifier.shouldNotify(CheckOutcome.Recorded(present.copy(source = AttendanceSource.MANUAL_CORRECTION))))
        assertFalse(
            notifier.shouldNotify(
                CheckOutcome.Rejected(
                    present.copy(status = AttendanceStatus.VACATION),
                    CheckRejection.LOCATION_TIMEOUT
                )
            )
        )
    }

    @Test
    fun optOutAndWrongPlatformModeNeverPost() = runTest {
        val key = DayKey("tracker", LocalDate(2026, 11, 15))
        notifier.completed(key, CheckMode.AUTOMATIC, CheckOutcome.Recorded(present))
        notifier.setEnabled(true)
        notifier.completed(key, CheckMode.USER_INITIATED, CheckOutcome.Recorded(present))
        notifier.setEnabled(false)
        assertFalse(preferences.enabled)
        assertTrue(port.cleared)
        assertEquals(0, port.posts)
    }

    @Test
    fun currentDayAndCompetingEditsFenceDelivery() = runTest {
        val key = DayKey("tracker", LocalDate(2026, 11, 15))
        preferences.enabled = true
        AttendanceResultNotifier(preferences, port, text = { "Localized result" }) {
            key.copy(date = LocalDate(2026, 11, 16)) to present
        }.completed(key, CheckMode.AUTOMATIC, CheckOutcome.Recorded(present))
        AttendanceResultNotifier(preferences, port, text = { "Localized result" }) {
            key to present.copy(source = AttendanceSource.MANUAL_CORRECTION)
        }.completed(key, CheckMode.AUTOMATIC, CheckOutcome.Recorded(present))
        notifier.completed(
            key, CheckMode.AUTOMATIC,
            CheckOutcome.Rejected(null, CheckRejection.LOCAL_SAVE_FAILED)
        )
        assertEquals(0, port.posts)
    }

    @Test
    fun acknowledged_result_and_real_save_failure_submit_and_delivery_failure_stays_isolated() =
        runTest {
            val key = DayKey("tracker", LocalDate(2026, 11, 15))
            preferences.enabled = true
            notifier.completed(key, CheckMode.AUTOMATIC, CheckOutcome.Recorded(present))
            assertEquals(1, port.posts)
            val absent = DayOutcome(AttendanceStatus.ABSENT, AttendanceSource.NONE, null, null)
            val failedSave = AttendanceResultNotifier(
                preferences,
                port,
                text = { "Localized result" }) { key to absent }
            failedSave.completed(
                key,
                CheckMode.AUTOMATIC,
                CheckOutcome.Rejected(null, CheckRejection.LOCAL_SAVE_FAILED)
            )
            assertEquals(2, port.posts)
            port.deliveryFails = true
            notifier.completed(key, CheckMode.AUTOMATIC, CheckOutcome.Recorded(present))
            assertEquals(3, port.posts)
            assertTrue(preferences.enabled)
        }

}
