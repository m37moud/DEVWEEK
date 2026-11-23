package dev.daymark.android.previews

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.AnnualVacationPolicy
import dev.daymark.models.plan.AnnualVacationSettings
import dev.daymark.presentation.features.settings.AnnualVacationSettingsUiState
import dev.daymark.presentation.features.settings.component.SettingsAnnualVacationCard
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme

private val sampleActivePolicy = AnnualVacationPolicy(
    effectiveYear = 2026,
    annualAllowanceDays = 25,
    carryOverUnusedDays = true
)

private val samplePendingPolicy = AnnualVacationPolicy(
    effectiveYear = 2027,
    annualAllowanceDays = 30,
    carryOverUnusedDays = false
)

private val sampleSettingsWithScheduled = AnnualVacationSettings(
    active = sampleActivePolicy,
    pending = samplePendingPolicy,
    nextEffectiveYear = 2027,
)

private val sampleSettingsActiveOnly = AnnualVacationSettings(
    active = sampleActivePolicy,
    pending = null,
    nextEffectiveYear = 2027,
)

private val sampleSettingsUnset = AnnualVacationSettings(
    active = null,
    pending = null,
    nextEffectiveYear = 2026,
)

@Preview(name = "Annual Vacation Card Light Summary")
@Composable
private fun SettingsAnnualVacationCardLightSummaryPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsAnnualVacationCard(
            state = AnnualVacationSettingsUiState(
                configuration = sampleSettingsActiveOnly
            ),
            busy = false,
            onSave = { _, _, _ -> },
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Annual Vacation Card Dark with Scheduled")
@Composable
private fun SettingsAnnualVacationCardDarkScheduledPreview() {
    DaymarkTheme(preference = ThemePreference.DARK) {
        SettingsAnnualVacationCard(
            state = AnnualVacationSettingsUiState(
                configuration = sampleSettingsWithScheduled
            ),
            busy = false,
            onSave = { _, _, _ -> },
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Annual Vacation Card Unset")
@Composable
private fun SettingsAnnualVacationCardUnsetPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsAnnualVacationCard(
            state = AnnualVacationSettingsUiState(
                configuration = sampleSettingsUnset
            ),
            busy = false,
            onSave = { _, _, _ -> },
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Annual Vacation Editor Arabic RTL Validation Error", locale = "ar")
@Composable
private fun SettingsAnnualVacationCardArabicRtlPreview() {
    WithPlatformLocale(language = AppLanguage.AR) {
        CompositionLocalProvider(
            LocalAppLanguage provides AppLanguage.AR,
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            DaymarkTheme(preference = ThemePreference.DARK) {
                SettingsAnnualVacationCard(
                    state = AnnualVacationSettingsUiState(
                        configuration = sampleSettingsWithScheduled
                    ),
                    busy = false,
                    onSave = { _, _, _ -> },
                    modifier = Modifier.padding(all = 16.dp),
                    initialEditorAllowance = "-١"
                )
            }
        }
    }
}

@Preview(name = "Annual Vacation Editor Narrow Large Text", widthDp = 320, fontScale = 2f)
@Composable
private fun SettingsAnnualVacationCardNarrowPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsAnnualVacationCard(
            state = AnnualVacationSettingsUiState(
                configuration = sampleSettingsWithScheduled
            ),
            busy = false,
            onSave = { _, _, _ -> },
            modifier = Modifier.padding(all = 16.dp),
            initialEditorAllowance = "31"
        )
    }
}

@Preview(name = "Annual Vacation Editor Busy Disabled")
@Composable
private fun SettingsAnnualVacationCardDisabledPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        SettingsAnnualVacationCard(
            state = AnnualVacationSettingsUiState(
                configuration = sampleSettingsActiveOnly
            ),
            busy = true,
            onSave = { _, _, _ -> },
            modifier = Modifier.padding(all = 16.dp),
            initialEditorAllowance = "25"
        )
    }
}
