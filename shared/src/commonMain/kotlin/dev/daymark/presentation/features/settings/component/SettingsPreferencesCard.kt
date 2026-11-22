package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.presentation.features.settings.SettingsActions
import dev.daymark.presentation.features.settings.SettingsUiState
import dev.daymark.resources.Res
import dev.daymark.resources.appearance
import dev.daymark.resources.language
import dev.daymark.resources.language_arabic
import dev.daymark.resources.language_english
import dev.daymark.resources.language_system
import dev.daymark.resources.onboarding_preferences
import dev.daymark.resources.settings_done
import dev.daymark.resources.settings_summary_value
import dev.daymark.resources.theme_dark
import dev.daymark.resources.theme_light
import dev.daymark.resources.theme_system
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str

private val LanguageOptions = listOf(
    LanguagePreference.SYSTEM to Res.string.language_system,
    LanguagePreference.EN to Res.string.language_english,
    LanguagePreference.AR to Res.string.language_arabic
)

private val ThemeOptions = listOf(
    ThemePreference.SYSTEM to Res.string.theme_system,
    ThemePreference.LIGHT to Res.string.theme_light,
    ThemePreference.DARK to Res.string.theme_dark
)

@Composable
internal fun SettingsPreferencesCard(
    languagePreference: LanguagePreference,
    themePreference: ThemePreference,
    onLanguageChange: (LanguagePreference) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsPreferenceOptions(
                languagePreference,
                themePreference,
                onLanguageChange,
                onThemeChange,
                busy
            )
        }
    }
}

@Composable
private fun SettingsPreferenceOptions(
    languagePreference: LanguagePreference,
    themePreference: ThemePreference,
    onLanguageChange: (LanguagePreference) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    busy: Boolean
) {
    LanguageSection(languagePreference, onLanguageChange, busy)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    AppearanceSection(themePreference, onThemeChange, busy)
}

@Composable
internal fun SettingsPreferencesSection(state: SettingsUiState, actions: SettingsActions) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsEditorCard(
        header = SettingsEditorHeader(
            title = Res.string.onboarding_preferences,
            editTag = "settings-preferences-edit",
            closeLabel = Res.string.settings_done,
            editing = editing,
            enabled = !state.busy
        ),
        onToggle = { editing = !editing }
    ) {
        val settings = state.settings
        if (editing) {
            SettingsPreferenceOptions(
                languagePreference = settings.language,
                themePreference = settings.theme,
                onLanguageChange = actions.setLanguage,
                onThemeChange = actions.setTheme,
                busy = state.busy
            )
        } else {
            val language = str(LanguageOptions.first { it.first == settings.language }.second)
            val theme = str(ThemeOptions.first { it.first == settings.theme }.second)
            SettingsSummaryText(
                summary = str(
                    Res.string.settings_summary_value,
                    str(Res.string.language),
                    language
                )
            )
            SettingsSummaryText(
                summary = str(
                    Res.string.settings_summary_value,
                    str(Res.string.appearance),
                    theme
                )
            )
        }
    }
}

@Composable
private fun LanguageSection(
    selectedLanguage: LanguagePreference,
    onSelectLanguage: (LanguagePreference) -> Unit,
    busy: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = str(Res.string.language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageOptions.forEach { (choice, labelRes) ->
                val isSelected = selectedLanguage == choice
                FilterChip(
                    selected = isSelected,
                    enabled = !busy,
                    onClick = { onSelectLanguage(choice) },
                    label = { Text(str(labelRes)) },
                    leadingIcon = {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    },
                    modifier = Modifier.testTag("settings-language-${choice.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    selectedTheme: ThemePreference,
    onSelectTheme: (ThemePreference) -> Unit,
    busy: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = str(Res.string.appearance),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOptions.forEach { (choice, labelRes) ->
                val isSelected = selectedTheme == choice
                FilterChip(
                    selected = isSelected,
                    enabled = !busy,
                    onClick = { onSelectTheme(choice) },
                    label = { Text(str(labelRes)) },
                    leadingIcon = {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    },
                    modifier = Modifier.testTag("settings-theme-${choice.name.lowercase()}")
                )
            }
        }
    }
}
