package dev.daymark.android

import android.content.Context
import android.content.res.Configuration
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.isolated
import dev.daymark.shared.R
import dev.daymark.widgets.WidgetSnapshot
import java.util.Locale
import kotlin.time.Instant

internal fun widgetLocalizedContext(context: Context, snapshot: WidgetSnapshot?): Context =
    context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        snapshot?.effectiveLocale?.let { setLocale(Locale.forLanguageTag(it)) }
    })

internal fun CheckOutcome?.needsWidgetRecovery(): Boolean = this is CheckOutcome.StateChanged ||
        this?.reason in listOf(
    CheckRejection.LOCATION_DENIED,
    CheckRejection.LOCATION_DISABLED,
    CheckRejection.NO_TRACKER
)

internal fun widgetFeedbackText(
    context: Context,
    snapshot: WidgetSnapshot?,
    feedback: WidgetCheckFeedback
): String = when (val outcome = feedback.outcome) {
    null -> context.getString(R.string.widget_checking)
    is CheckOutcome.Recorded -> context.getString(R.string.widget_check_saved)
    is CheckOutcome.AlreadyRecorded -> context.getString(R.string.widget_already_recorded)
    is CheckOutcome.Busy -> context.getString(R.string.widget_check_busy)
    is CheckOutcome.StateChanged -> context.getString(R.string.widget_check_changed)
    is CheckOutcome.Rejected -> rejectionText(context, snapshot, outcome.reason)
}

private fun rejectionText(
    context: Context,
    snapshot: WidgetSnapshot?,
    reason: CheckRejection
): String =
    when (reason) {
        CheckRejection.BEFORE_WINDOW -> snapshot?.arrivalEpochMillis?.let {
            val language = if (snapshot.effectiveLocale == "ar") AppLanguage.AR else AppLanguage.EN
            isolated(
                context.getString(
                    R.string.widget_check_starts,
                    isolated(
                        DisplayFormats.time(
                            Instant.fromEpochMilliseconds(it),
                            snapshot.zoneId,
                            language
                        )
                    )
                )
            )
        } ?: context.getString(R.string.widget_check_disabled)

        CheckRejection.LOCATION_DENIED -> context.getString(R.string.widget_check_permission)
        CheckRejection.LOCATION_DISABLED -> context.getString(R.string.widget_check_location_off)
        CheckRejection.OUTSIDE_AREA -> context.getString(R.string.widget_check_outside)
        CheckRejection.INACCURATE_FIX, CheckRejection.INVALID_FIX, CheckRejection.STALE_FIX ->
            context.getString(R.string.widget_check_accuracy)

        CheckRejection.LOCAL_SAVE_FAILED -> context.getString(R.string.widget_check_save_failed)
        CheckRejection.NO_TRACKER, CheckRejection.STATE_CHANGED -> context.getString(R.string.widget_check_changed)
        CheckRejection.DAY_OFF -> context.getString(R.string.widget_check_off_day)
        CheckRejection.CANCELLED -> context.getString(R.string.widget_check_cancelled)
        else -> context.getString(R.string.widget_check_retry)
    }
