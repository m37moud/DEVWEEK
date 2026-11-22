package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import dev.daymark.app.reducedMotionEnabled
import dev.daymark.resources.Res
import dev.daymark.resources.onboarding_edit_section
import dev.daymark.settings.str
import org.jetbrains.compose.resources.StringResource

internal data class SettingsEditorHeader(
    val title: StringResource,
    val editTag: String,
    val closeLabel: StringResource,
    val editing: Boolean,
    val enabled: Boolean
)

@Composable
internal fun SettingsEditorCard(
    header: SettingsEditorHeader,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val resize = if (reducedMotionEnabled()) Modifier else Modifier.animateContentSize()
    Card(
        modifier = modifier.fillMaxWidth().then(resize),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsEditorHeading(header = header, onToggle = onToggle)
            content()
        }
    }
}

@Composable
private fun SettingsEditorHeading(header: SettingsEditorHeader, onToggle: () -> Unit) {
    val title = str(header.title)
    val actionModifier = Modifier.testTag(header.editTag)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (header.editing) {
            OutlinedButton(onClick = onToggle, enabled = header.enabled, modifier = actionModifier) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = str(header.closeLabel))
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.Check,
                        contentDescription = str(header.closeLabel),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)


                    )
                }
            }
        } else {
            FilledTonalIconButton(
                onClick = onToggle,
                enabled = header.enabled,
                modifier = actionModifier
            ) {
                Icon(Icons.Default.Edit, str(Res.string.onboarding_edit_section, title))
            }
        }
    }
}

@Composable
internal fun SettingsSummaryText(summary: String) {
    Text(
        summary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
