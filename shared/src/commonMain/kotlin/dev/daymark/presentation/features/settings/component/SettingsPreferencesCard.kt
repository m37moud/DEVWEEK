package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Adjust
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Moon
import compose.icons.fontawesomeicons.solid.Palette
import compose.icons.fontawesomeicons.solid.Sun
import dev.daymark.app.reducedMotionEnabled
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
import dev.daymark.resources.theme_dark
import dev.daymark.resources.theme_light
import dev.daymark.resources.theme_system
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.str
import org.jetbrains.compose.resources.StringResource

private data class PreferenceOptionConfig<T>(
    val preference: T,
    val labelRes: StringResource,
    val icon: ImageVector,
    val testTagSuffix: String
)

private val LanguageConfigs = listOf(
    PreferenceOptionConfig(
        preference = LanguagePreference.SYSTEM,
        labelRes = Res.string.language_system,
        icon = FontAwesomeIcons.Solid.Globe,
        testTagSuffix = "system"
    ),
    PreferenceOptionConfig(
        preference = LanguagePreference.EN,
        labelRes = Res.string.language_english,
        icon = FontAwesomeIcons.Solid.Globe,
        testTagSuffix = "en"
    ),
    PreferenceOptionConfig(
        preference = LanguagePreference.AR,
        labelRes = Res.string.language_arabic,
        icon = FontAwesomeIcons.Solid.Globe,
        testTagSuffix = "ar"
    )
)

private val ThemeConfigs = listOf(
    PreferenceOptionConfig(
        preference = ThemePreference.SYSTEM,
        labelRes = Res.string.theme_system,
        icon = FontAwesomeIcons.Solid.Adjust,
        testTagSuffix = "system"
    ),
    PreferenceOptionConfig(
        preference = ThemePreference.LIGHT,
        labelRes = Res.string.theme_light,
        icon = FontAwesomeIcons.Solid.Sun,
        testTagSuffix = "light"
    ),
    PreferenceOptionConfig(
        preference = ThemePreference.DARK,
        labelRes = Res.string.theme_dark,
        icon = FontAwesomeIcons.Solid.Moon,
        testTagSuffix = "dark"
    )
)

private data class PreferenceGroup<T>(
    val titleRes: StringResource,
    val icon: ImageVector,
    val testTagPrefix: String,
    val options: List<PreferenceOptionConfig<T>>
)

private val LanguageGroup = PreferenceGroup(
    titleRes = Res.string.language,
    icon = FontAwesomeIcons.Solid.Globe,
    testTagPrefix = "settings-language",
    options = LanguageConfigs
)

private val AppearanceGroup = PreferenceGroup(
    titleRes = Res.string.appearance,
    icon = FontAwesomeIcons.Solid.Palette,
    testTagPrefix = "settings-theme",
    options = ThemeConfigs
)

private data class OptionTile(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val selected: Boolean
)

@Composable
fun SettingsPreferencesCard(
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
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(all = 20.dp)) {
            SettingsPreferenceOptions(
                languagePreference = languagePreference,
                themePreference = themePreference,
                onLanguageChange = onLanguageChange,
                onThemeChange = onThemeChange,
                busy = busy
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
    Column(verticalArrangement = Arrangement.spacedBy(space = 16.dp)) {
        PreferenceOptionGroup(
            group = LanguageGroup,
            selectedPreference = languagePreference,
            onSelect = onLanguageChange,
            enabled = !busy
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        PreferenceOptionGroup(
            group = AppearanceGroup,
            selectedPreference = themePreference,
            onSelect = onThemeChange,
            enabled = !busy
        )
    }
}

@Composable
fun SettingsPreferencesSection(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsEditorCard(
        header = SettingsEditorHeader(
            title = Res.string.onboarding_preferences,
            editTag = "settings-preferences-edit",
            closeLabel = Res.string.settings_done,
            editing = editing,
            enabled = !state.busy
        ),
        onToggle = { editing = !editing },
        modifier = modifier
    ) {
        val reducedMotion = reducedMotionEnabled()
        AnimatedContent(
            targetState = editing,
            transitionSpec = {
                if (reducedMotion) {
                    (EnterTransition.None togetherWith ExitTransition.None) using null
                } else {
                    (fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically()) togetherWith
                            (fadeOut(animationSpec = tween(durationMillis = 150)) + shrinkVertically())
                }
            },
            label = "preferences-editing-transition"
        ) { isEditing ->
            if (isEditing) {
                SettingsPreferenceOptions(
                    languagePreference = state.settings.language,
                    themePreference = state.settings.theme,
                    onLanguageChange = { if (editing) actions.setLanguage(it) },
                    onThemeChange = { if (editing) actions.setTheme(it) },
                    busy = state.busy || !editing
                )
            } else {
                SettingsPreferencesSummary(
                    languagePreference = state.settings.language,
                    themePreference = state.settings.theme
                )
            }
        }
    }
}

@Composable
private fun SettingsPreferencesSummary(
    languagePreference: LanguagePreference,
    themePreference: ThemePreference,
    modifier: Modifier = Modifier
) {
    val languageLabel = str(resource = LanguageConfigs.first { it.preference == languagePreference }.labelRes)
    val themeConfig = ThemeConfigs.first { it.preference == themePreference }
    val themeLabel = str(resource = themeConfig.labelRes)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp)
    ) {
        PreferenceSummaryRow(
            icon = FontAwesomeIcons.Solid.Globe,
            category = str(resource = Res.string.language),
            preferenceLabel = languageLabel
        )
        PreferenceSummaryRow(
            icon = themeConfig.icon,
            category = str(resource = Res.string.appearance),
            preferenceLabel = themeLabel
        )
    }
}

@Composable
private fun PreferenceSummaryRow(
    icon: ImageVector,
    category: String,
    preferenceLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(size = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = preferenceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun <T> PreferenceOptionGroup(
    group: PreferenceGroup<T>,
    selectedPreference: T,
    onSelect: (T) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        PreferenceSectionTitle(title = str(resource = group.titleRes), icon = group.icon)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            group.options.forEach { option ->
                PreferenceOptionTile(
                    tile = OptionTile(
                        label = str(resource = option.labelRes),
                        icon = option.icon,
                        testTag = "${group.testTagPrefix}-${option.testTagSuffix}",
                        selected = selectedPreference == option.preference
                    ),
                    enabled = enabled,
                    onClick = { onSelect(option.preference) }
                )
            }
        }
    }
}

@Composable
private fun PreferenceSectionTitle(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size = 14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PreferenceOptionTile(
    tile: OptionTile,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = tile.selected
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 200),
        label = "tile-container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "tile-content"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "tile-border"
    )

    Surface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(tag = tile.testTag)
            .clip(shape = MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = borderColor
        )
    ) {
        PreferenceTileContent(
            tile = tile,
            contentColor = contentColor
        )
    }
}

@Composable
private fun PreferenceTileContent(
    tile: OptionTile,
    contentColor: Color
) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            modifier = Modifier.size(size = 16.dp),
            tint = contentColor
        )
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
        PreferenceSelectionMark(selected = tile.selected)
    }
}

@Composable
private fun PreferenceSelectionMark(selected: Boolean) {
    val reducedMotion = reducedMotionEnabled()
    Box(modifier = Modifier.size(size = 12.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = selected,
            enter = if (reducedMotion) {
                fadeIn()
            } else {
                scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
            },
            exit = if (reducedMotion) fadeOut() else scaleOut() + fadeOut()
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.Check,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
