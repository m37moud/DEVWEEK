package dev.daymark.android.previews

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.presentation.features.today.TodayVacationBalanceUiState
import dev.daymark.presentation.features.today.component.TodayVacationBalance
import dev.daymark.presentation.features.today.component.TodayVacationBalanceCard
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme

private val sampleSummary = VacationBalanceSummary(
    originalDays = 24,
    usedDays = 9,
    remainingDays = 15,
    year = 2027
)

private val lowBalanceSummary = VacationBalanceSummary(
    originalDays = 24,
    usedDays = 22,
    remainingDays = 2,
    year = 2027
)

private val depletedSummary = VacationBalanceSummary(
    originalDays = 20,
    usedDays = 20,
    remainingDays = 0,
    year = 2027
)

@Preview(name = "Light Theme", showBackground = true)
@Composable
fun TodayVacationBalanceCardLightPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        TodayVacationBalanceCard(
            summary = sampleSummary,
            onAdjustBalance = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Dark Theme", showBackground = true)
@Composable
fun TodayVacationBalanceCardDarkPreview() {
    DaymarkTheme(preference = ThemePreference.DARK) {
        TodayVacationBalanceCard(
            summary = sampleSummary,
            onAdjustBalance = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Low Balance Amber", showBackground = true)
@Composable
fun TodayVacationBalanceCardLowPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        TodayVacationBalanceCard(
            summary = lowBalanceSummary,
            onAdjustBalance = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Narrow 320dp Large Text 2f", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable
fun TodayVacationBalanceCardNarrowPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        TodayVacationBalanceCard(
            summary = sampleSummary,
            onAdjustBalance = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(name = "Depleted Balance", showBackground = true)
@Composable
fun TodayVacationBalanceCardDepletedPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        TodayVacationBalanceCard(
            summary = depletedSummary,
            onAdjustBalance = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "Arabic RTL", locale = "ar", showBackground = true)
@Composable
fun TodayVacationBalanceCardArabicPreview() {
    WithPlatformLocale(language = AppLanguage.AR) {
        DaymarkTheme(preference = ThemePreference.LIGHT) {
            TodayVacationBalance(
                state = TodayVacationBalanceUiState(summary = sampleSummary),
                onSave = { _, _ -> }
            )
        }
    }
}
