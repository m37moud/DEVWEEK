package dev.daymark.notifications

import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.*
import dev.daymark.repository.SettingsDataSource
import dev.daymark.resources.*
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.applyAppLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.StringResource

class AttendanceResultNotifier(
    private val settings: SettingsDataSource,
    private val port: AttendanceResultPort,
    private val text: suspend (StringResource) -> String = { getString(it) },
    private val currentDay: suspend () -> Pair<DayKey, DayOutcome>?
) {
    private val mutex = Mutex()
    val supported get() = port.mode != null
    val androidControls get() = port as? AndroidResultNotificationControls
    fun requestPermission(completion: (Boolean) -> Unit) = port.requestPermission(completion)

    suspend fun awaitPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        requestPermission { granted ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }

    suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        settings.update(resultNotificationsEnabled = enabled)
        if (!enabled) port.clearResults()
    }

    suspend fun completed(key: DayKey, mode: CheckMode, outcome: CheckOutcome) = mutex.withLock {
        if (port.mode != mode || !shouldNotify(outcome)) return@withLock
        try {
            val preferences = settings.observe().first()
            if (!preferences.resultNotificationsEnabled || settings.trackerIdentity() != key.trackerId) return@withLock
            val current = currentDay() ?: return@withLock
            if (current.first != key) return@withLock
            if (!stillRelevant(outcome = outcome, live = current.second)) return@withLock
            when (preferences.language) {
                LanguagePreference.EN -> applyAppLocale(AppLanguage.EN)
                LanguagePreference.AR -> applyAppLocale(AppLanguage.AR)
                LanguagePreference.SYSTEM -> Unit
            }
            val saved = outcome is CheckOutcome.Recorded
            val identifier = "daymark.result:${key.trackerId}:${key.date}"
            val title = text(if (saved) Res.string.result_saved else Res.string.result_unconfirmed)
            val body = text(
                if (saved) {
                    if (outcome.currentDay.status == AttendanceStatus.LATE) Res.string.result_late else Res.string.result_present
                } else failureCopy(outcome.reason)
            )
            val android = androidControls
            if (android == null) {
                port.post(identifier = identifier, title = title, body = body)
            } else {
                android.post(
                    notification = AutomaticResultNotification(
                        identifier = identifier,
                        title = title,
                        body = body,
                        manualCheckLabel = if (saved) null else text(Res.string.result_manual_action),
                        failureSoundEnabled = preferences.resultFailureSoundEnabled
                    )
                )
            }
            AppLogger.i("AttendanceResultNotifier", "Result notification submitted")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Notification failures must never turn an acknowledged attendance write into a GPS retry.
            AppLogger.e("AttendanceResultNotifier", "Result notification failed", failure)
        }
    }

    private fun stillRelevant(outcome: CheckOutcome, live: DayOutcome): Boolean {
        if (outcome.reason == CheckRejection.LOCAL_SAVE_FAILED) {
            return shouldNotify(outcome = CheckOutcome.Rejected(live, CheckRejection.LOCAL_SAVE_FAILED))
        }
        if (live == outcome.currentDay) return true
        // A failure can be saved just before the deadline and published just after it.
        return androidControls != null && outcome is CheckOutcome.Rejected &&
            outcome.currentDay?.status == AttendanceStatus.AWAITING_CHECK &&
            live.status == AttendanceStatus.ABSENT &&
            shouldNotify(outcome = CheckOutcome.Rejected(live, outcome.reason))
    }

    internal fun shouldNotify(outcome: CheckOutcome): Boolean = when (outcome) {
        is CheckOutcome.Recorded -> outcome.currentDay.source != AttendanceSource.MANUAL_CORRECTION &&
                outcome.currentDay.status in setOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE)

        is CheckOutcome.Rejected -> outcome.currentDay?.status !in setOf(
            AttendanceStatus.PRESENT, AttendanceStatus.LATE,
            AttendanceStatus.HOLIDAY, AttendanceStatus.VACATION, AttendanceStatus.DAY_OFF, AttendanceStatus.WORK_MISSION
        ) &&
                outcome.currentDay?.source != AttendanceSource.MANUAL_CORRECTION &&
                outcome.reason in setOf(
            CheckRejection.LOCATION_DENIED, CheckRejection.LOCATION_DISABLED,
            CheckRejection.LOCATION_TIMEOUT, CheckRejection.UNAVAILABLE, CheckRejection.STALE_FIX,
            CheckRejection.INVALID_FIX, CheckRejection.INACCURATE_FIX, CheckRejection.OUTSIDE_AREA,
            CheckRejection.LOCAL_SAVE_FAILED
        )

        else -> false
    }

    private fun failureCopy(reason: CheckRejection?) = when (reason) {
        CheckRejection.LOCATION_DENIED -> Res.string.result_location_denied
        CheckRejection.LOCATION_DISABLED -> Res.string.result_location_disabled
        CheckRejection.LOCATION_TIMEOUT -> Res.string.result_location_timeout
        CheckRejection.OUTSIDE_AREA -> Res.string.result_outside_area
        CheckRejection.LOCAL_SAVE_FAILED -> Res.string.result_save_failed
        CheckRejection.STALE_FIX, CheckRejection.INVALID_FIX, CheckRejection.INACCURATE_FIX -> Res.string.result_fix_unusable
        else -> Res.string.result_location_unavailable
    }
}
