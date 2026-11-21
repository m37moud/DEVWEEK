package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.presentation.features.history.HistoryActions
import dev.daymark.presentation.features.history.HistoryContent
import dev.daymark.presentation.features.history.HistoryUiState
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.LocalDate

@Preview(name = "Cross-month attendance period")
@Composable
private fun HistoryAttendancePeriodPreview() {
    val today = LocalDate(2026, 11, 19)
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryContent(
            state = HistoryUiState(
                year = 2026,
                month = 11,
                ready = true,
                today = today,
                period = AttendancePeriodSettings(20).periodFor(today)
            ),
            onBack = {},
            onEditTimeOff = {},
            actions = HistoryActions({ _, _ -> }, {})
        )
    }
}
