package dev.daymark.presentation.component

import androidx.compose.runtime.Composable
import dev.daymark.history.AttendancePeriod
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period_dates
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
internal fun attendancePeriodDates(period: AttendancePeriod): String {
    val language = LocalAppLanguage.current
    return str(
        Res.string.attendance_period_dates,
        isolated(DisplayFormats.date(period.from, language)),
        isolated(DisplayFormats.date(period.through, language))
    )
}
