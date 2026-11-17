package dev.daymark.android.previews

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.features.setup.component.SetupSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime


@Preview(
    name = "Schedule with departure",
//    widthDp = 320,
    fontScale = 1.5f
)
@Composable
private fun SetupScheduleDeparturePreview() {
    dev.daymark.ui.theme.DaymarkTheme(dev.daymark.settings.ThemePreference.LIGHT) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SetupSchedule(
                schedule = WeeklySchedule(
                    days = setOf(DayOfWeek.MONDAY),
                    arrival = LocalTime(9, 0),
                    overrides = emptyMap(),
                    graceMinutes = 5,
                    zoneId = "UTC",
                    departure = LocalTime(17, 0)
                ),
                busy = false,
                onScheduleChange = {}
            )
        }
    }
}
