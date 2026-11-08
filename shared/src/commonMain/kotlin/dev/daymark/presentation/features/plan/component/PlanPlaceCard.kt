package dev.daymark.presentation.features.plan.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.presentation.component.RegisteredPlacePreview
import dev.daymark.presentation.component.label
import dev.daymark.presentation.features.setup.SetupIssue
import dev.daymark.resources.Res
import dev.daymark.resources.checking_location
import dev.daymark.resources.location_rationale
import dev.daymark.resources.onboarding_place
import dev.daymark.resources.open_settings
import dev.daymark.resources.radius
import dev.daymark.resources.radius_metres
import dev.daymark.resources.register_place
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlin.math.roundToInt

@Composable
internal fun PlanPlaceCard(
    place: RegisteredPlace,
    busy: Boolean,
    locationIssue: SetupIssue?,
    onRadiusChange: (Int) -> Unit,
    onRegisterCurrentPlace: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val metresText = isolated(DisplayFormats.number(place.radiusMetres, language))
    val radiusLabel = str(Res.string.radius)
    val radiusDescription = str(Res.string.radius_metres, metresText)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = str(Res.string.onboarding_place),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            RegisteredPlacePreview(place = place, modifier = Modifier.fillMaxWidth())

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = radiusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = radiusDescription,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Slider(
                    value = place.radiusMetres.toFloat(),
                    onValueChange = { onRadiusChange(it.roundToInt()) },
                    valueRange = 100f..500f,
                    steps = 7,
                    enabled = !busy,
                    modifier = Modifier.semantics {
                        contentDescription = radiusLabel
                        stateDescription = radiusDescription
                    }
                )
            }

            Button(
                onClick = onRegisterCurrentPlace,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = str(
                            if (busy) Res.string.checking_location
                            else Res.string.register_place
                        )
                    )
                }
            }

            Text(
                text = str(Res.string.location_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            locationIssue?.let { issue ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = str(issue.label()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                if (issue == SetupIssue.LOCATION_DENIED || issue == SetupIssue.LOCATION_DISABLED) {
                    TextButton(
                        onClick = onOpenLocationSettings,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(str(Res.string.open_settings))
                    }
                }
            }
        }
    }
}
