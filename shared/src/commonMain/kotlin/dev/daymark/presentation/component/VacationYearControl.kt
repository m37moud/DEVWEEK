package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.history.VacationYearBasis
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period_vacation_year
import dev.daymark.resources.vacation_year_calendar
import dev.daymark.resources.vacation_year_calendar_date
import dev.daymark.resources.vacation_year_example
import dev.daymark.resources.vacation_year_period
import dev.daymark.resources.vacation_year_period_date
import dev.daymark.resources.vacation_year_same_date
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
internal fun VacationYearControl(
    rule: AttendancePeriodSettings,
    exampleYear: Int,
    enabled: Boolean,
    onChange: (AttendancePeriodSettings) -> Unit
) {
    val usesAttendanceYear = rule.vacationYearBasis == VacationYearBasis.ATTENDANCE_PERIOD
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("attendance-period-vacation-year")
            .toggleable(usesAttendanceYear, enabled = enabled, role = Role.Switch) { checked ->
                val basis = if (checked) VacationYearBasis.ATTENDANCE_PERIOD else VacationYearBasis.CALENDAR
                onChange(rule.copy(vacationYearBasis = basis))
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(str(Res.string.attendance_period_vacation_year))
            Text(vacationYearBasisLabel(rule.vacationYearBasis), style = MaterialTheme.typography.bodySmall)
        }
        Switch(usesAttendanceYear, onCheckedChange = null, enabled = enabled)
    }
    VacationYearExample(rule, exampleYear)
}

@Composable
internal fun vacationYearBasisLabel(basis: VacationYearBasis): String = str(
    when (basis) {
        VacationYearBasis.CALENDAR -> Res.string.vacation_year_calendar
        VacationYearBasis.ATTENDANCE_PERIOD -> Res.string.vacation_year_period
    }
)

@Composable
private fun VacationYearExample(rule: AttendancePeriodSettings, year: Int) {
    val language = LocalAppLanguage.current
    val calendar = rule.copy(vacationYearBasis = VacationYearBasis.CALENDAR).vacationYearStart(year)
    val attendance = rule.copy(vacationYearBasis = VacationYearBasis.ATTENDANCE_PERIOD).vacationYearStart(year)
    val calendarDate = isolated(DisplayFormats.date(calendar, language))
    val style = MaterialTheme.typography.bodySmall
    Column(Modifier.testTag("attendance-period-vacation-example")) {
        Text(str(Res.string.vacation_year_example, isolated(DisplayFormats.number(year, language))), style = style)
        if (calendar == attendance) {
            Text(str(Res.string.vacation_year_same_date, calendarDate), style = style)
        } else {
            Text(str(Res.string.vacation_year_calendar_date, calendarDate), style = style)
            Text(
                str(Res.string.vacation_year_period_date, isolated(DisplayFormats.date(attendance, language))),
                style = style
            )
        }
    }
}
