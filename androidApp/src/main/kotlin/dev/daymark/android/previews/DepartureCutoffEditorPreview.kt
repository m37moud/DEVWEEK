package dev.daymark.android.previews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.component.DepartureCutoffEditor
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Preview(name = "Departure cutoff large text", widthDp = 320, fontScale = 2f)
@Composable
private fun DepartureCutoffEditorPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        Surface(
            modifier = Modifier.fillMaxWidth(),

            ) {
            DepartureCutoffEditor(
                schedule = WeeklySchedule(
                    setOf(DayOfWeek.MONDAY),
                    LocalTime(9, 0),
                    emptyMap(),
                    5,
                    "UTC",
                    LocalTime(17, 0)
                ),
                busy = false, onChange = {}
            )
        }
    }
}
