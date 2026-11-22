package dev.daymark.presentation.features.historydetail.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.daymark.presentation.features.historydetail.HistoryDetailUiState
import dev.daymark.resources.Res
import dev.daymark.resources.apply_range
import dev.daymark.resources.cancel
import dev.daymark.resources.history_range_invalid
import dev.daymark.resources.save
import dev.daymark.resources.time_off_from
import dev.daymark.resources.time_off_through
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryRangeFilter(
    state: HistoryDetailUiState,
    onSelect: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var fromText by rememberSaveable(state.from) { mutableStateOf(state.from.toString()) }
    var throughText by rememberSaveable(state.through) { mutableStateOf(state.through.toString()) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val from = LocalDate.parse(fromText)
    val through = LocalDate.parse(throughText)
    val language = LocalAppLanguage.current
    val isDirty = from != state.from || through != state.through

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // One-line Row layout with From and Through date selectors side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { picker = "from" },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        AnimatedContent(
                            targetState = from,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "filter-from-date"
                        ) { targetFrom ->
                            Text(
                                text = str(
                                    Res.string.time_off_from,
                                    DisplayFormats.date(targetFrom, language)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { picker = "through" },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        AnimatedContent(
                            targetState = through,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "filter-through-date"
                        ) { targetThrough ->
                            Text(
                                text = str(
                                    Res.string.time_off_through,
                                    DisplayFormats.date(targetThrough, language)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }

            // Animated interactive button container
            val buttonScale by animateFloatAsState(
                targetValue = if (isDirty) 1.01f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "apply-button-scale"
            )
            val containerColor by animateColorAsState(
                targetValue = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                label = "apply-container-color"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isDirty) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                label = "apply-content-color"
            )

            Button(
                onClick = { onSelect(from, through) },
                enabled = state.ready && !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = buttonScale, scaleY = buttonScale),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            ) {
                Text(str(Res.string.apply_range))
            }

            AnimatedVisibility(
                visible = state.invalidRange,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = str(Res.string.history_range_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    picker?.let { selected ->
        val initial = if (selected == "from") from else through
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(

            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val date =
                            Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        if (selected == "from") fromText = date.toString() else throughText =
                            date.toString()
                    }
                    picker = null
                }) { Text(str(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    picker = null
                }) { Text(str(Res.string.cancel)) }
            },

            ) { DatePicker(dateState) }
    }
}
