package dev.daymark.android.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.daymark.presentation.component.ValueSliderControl
import dev.daymark.presentation.component.ValueSliderState
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme

@Preview
@Composable
fun ValueSliderControlGracePreview() {
    var grace by remember { mutableIntStateOf(15) }
    DaymarkTheme(ThemePreference.LIGHT) {
        Surface(modifier = Modifier.padding(16.dp)) {
            ValueSliderControl(
                state = ValueSliderState(
                    label = "Grace period",
                    value = grace,
                    formattedValue = "$grace minutes",
                    min = 0,
                    max = 120,
                    step = 5,
                    sliderSteps = 119
                ),
                onValueChange = { grace = it }
            )
        }
    }
}

@Preview
@Composable
fun ValueSliderControlRadiusDarkPreview() {
    var radius by remember { mutableIntStateOf(200) }
    DaymarkTheme(ThemePreference.DARK) {
        Surface(modifier = Modifier.padding(16.dp)) {
            ValueSliderControl(
                state = ValueSliderState(
                    label = "Allowed radius",
                    value = radius,
                    formattedValue = "$radius m",
                    min = 100,
                    max = 500,
                    step = 50,
                    sliderSteps = 7
                ),
                onValueChange = { radius = it }
            )
        }
    }
}
