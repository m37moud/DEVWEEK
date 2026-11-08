package dev.daymark.presentation.features.history.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.HistoryMonth
import dev.daymark.resources.Res
import dev.daymark.resources.month_counts
import dev.daymark.resources.next_month
import dev.daymark.resources.previous_month
import dev.daymark.resources.status_absent
import dev.daymark.resources.status_late
import dev.daymark.resources.status_present
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.str
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

@Composable
internal fun HistoryMonthNavigator(
    year: Int,
    month: Int,
    history: HistoryMonth,
    language: AppLanguage,
    onSelectMonth: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val presentText = DisplayFormats.number(history.present, language)
    val lateText = DisplayFormats.number(history.late, language)
    val absentText = DisplayFormats.number(history.absent, language)
    val monthCountsDescription = str(Res.string.month_counts, presentText, lateText, absentText)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val date = LocalDate(year, month, 1).minus(1, DateTimeUnit.MONTH)
                        onSelectMonth(date.year, date.month.number)
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = str(Res.string.previous_month),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                AnimatedContent(
                    targetState = Pair(year, month),
                    transitionSpec = {
                        val targetDate = LocalDate(targetState.first, targetState.second, 1)
                        val initialDate = LocalDate(initialState.first, initialState.second, 1)
                        if (targetDate > initialDate) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "historyMonthTransition"
                ) { (y, m) ->
                    Text(
                        text = DisplayFormats.monthYear(LocalDate(y, m, 1), language),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        val date = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
                        onSelectMonth(date.year, date.month.number)
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = str(Res.string.next_month),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = monthCountsDescription
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonthKpiTile(
                    count = presentText,
                    label = str(Res.string.status_present),
                    dotColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MonthKpiTile(
                    count = lateText,
                    label = str(Res.string.status_late),
                    dotColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                MonthKpiTile(
                    count = absentText,
                    label = str(Res.string.status_absent),
                    dotColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthKpiTile(
    count: String,
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = dotColor, shape = CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
