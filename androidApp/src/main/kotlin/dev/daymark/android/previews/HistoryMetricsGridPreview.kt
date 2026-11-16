package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.history.HistoryMetrics
import dev.daymark.presentation.component.HistoryMetricsGrid
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme

@Preview
@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun HistoryMetricsGridPreview() {
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryMetricsGrid(
            HistoryMetrics(
                attendance = 16,
                late = 2,
                absent = 1,
                leaveRest = 3,
                holidays = 1
            ), {})
    }
}
