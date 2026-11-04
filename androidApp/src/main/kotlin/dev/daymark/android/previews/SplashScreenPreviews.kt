package dev.daymark.android.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.daymark.app.StartupSplash
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.DaymarkTheme

@Preview
@Composable
private fun SplashScreenPreview() {
    DaymarkTheme(preference = ThemePreference.SYSTEM) {
        StartupSplash(active = true)
    }
}
