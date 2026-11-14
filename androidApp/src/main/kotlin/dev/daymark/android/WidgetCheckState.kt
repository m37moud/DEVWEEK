package dev.daymark.android

import dev.daymark.logging.AppLogger
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

internal data class WidgetCheckFeedback(
    val trackerId: String,
    val startedAtMillis: Long,
    val outcome: CheckOutcome? = null
)

/** Ephemeral feedback only; persisted attendance remains the publisher's responsibility. */
internal object WidgetCheckState {
    private val feedback = MutableStateFlow<WidgetCheckFeedback?>(null)
    val updates = feedback.asStateFlow()

    fun clearFinished() {
        feedback.update { if (it?.outcome != null) null else it }
    }

    fun cancelled(trackerId: String) {
        feedback.update {
            if (it?.trackerId == trackerId && it.outcome == null)
                it.copy(outcome = CheckOutcome.Rejected(null, CheckRejection.CANCELLED)) else it
        }
    }

    fun checking(trackerId: String) {
        feedback.value = WidgetCheckFeedback(trackerId, Clock.System.now().toEpochMilliseconds())
    }

    fun finished(trackerId: String, outcome: CheckOutcome) {
        val started = feedback.value?.takeIf { it.trackerId == trackerId }?.startedAtMillis
            ?: Clock.System.now().toEpochMilliseconds()
        feedback.value = WidgetCheckFeedback(trackerId, started, outcome)
    }
}

/** Native/storage failures become explicit rejections; publication failure cannot undo saved attendance. */
internal class WidgetCheckRunner(
    private val check: suspend (String) -> CheckOutcome,
    private val refresh: suspend () -> Unit
) {
    suspend fun run(trackerId: String): CheckOutcome {
        WidgetCheckState.checking(trackerId)
        val outcome = try {
            check(trackerId)
        } catch (cancelled: CancellationException) {
            WidgetCheckState.finished(
                trackerId,
                CheckOutcome.Rejected(null, CheckRejection.CANCELLED)
            )
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WidgetCheck", "Widget check failed before acknowledgement", failure)
            CheckOutcome.Rejected(null, CheckRejection.LOCAL_SAVE_FAILED)
        }
        WidgetCheckState.finished(trackerId, outcome)
        try {
            refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLogger.e("WidgetCheck", "Attendance result retained; widget refresh failed", failure)
        }
        return outcome
    }
}
