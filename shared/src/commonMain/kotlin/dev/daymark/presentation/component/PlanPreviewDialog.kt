package dev.daymark.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanRejection
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.presentation.component.label
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.no_confirmed_time
import dev.daymark.resources.plan_effective
import dev.daymark.resources.preview_after
import dev.daymark.resources.preview_balance
import dev.daymark.resources.preview_before
import dev.daymark.resources.preview_evidence
import dev.daymark.resources.preview_no_dates
import dev.daymark.resources.preview_title
import dev.daymark.resources.time_off_before_tracking
import dev.daymark.resources.save
import dev.daymark.resources.vacation_balance_before_renewal
import dev.daymark.resources.vacation_balance_year
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
internal fun PlanPreviewDialog(
    preview: TimeOffPreview,
    category: TrackerCategory?,
    busy: Boolean,
    onConfirm: (PlanReviewToken) -> Unit,
    onDismiss: () -> Unit
) {
    val language = LocalAppLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = str(Res.string.preview_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                preview.effectiveFrom?.let { effectiveDate ->
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = str(
                                    Res.string.plan_effective,
                                    DisplayFormats.date(effectiveDate, language)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (preview.balancePeriods.isNotEmpty()) {
                    items(preview.balancePeriods) { period ->
                        val title = period.year?.let { year ->
                            str(Res.string.vacation_balance_year, isolated(DisplayFormats.number(year, language)))
                        } ?: str(Res.string.vacation_balance_before_renewal)
                        BalancePreview(period.delta, period.remainingDays, title)
                    }
                } else if (showsLegacyBalance(preview, category)) {
                    item {
                        BalancePreview(preview.balanceDelta, preview.availableAfter)
                    }
                }

                preview.rejection?.let { rejection ->
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = preview.shortfallYear?.let { insufficientVacationYearMessage(it) }
                                    ?: str(rejection.message()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (preview.preTrackingDates.isNotEmpty()) {
                    item {
                        Text(str(Res.string.time_off_before_tracking), style = MaterialTheme.typography.bodyMedium)
                    }
                    items(preview.preTrackingDates) { date ->
                        Text(DisplayFormats.date(date, language), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                items(preview.affectedDays.indices.toList()) { index ->
                    val key = preview.affectedDays[index]
                    val change = preview.changes.getOrNull(index)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = DisplayFormats.date(key.date, language),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (change == null) {
                                Text(
                                    text = str(preview.resultingOutcomes[index].status.label()),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = str(
                                        Res.string.preview_before,
                                        str(change.before.status.label()),
                                        str(change.before.source.label())
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("preview-before-${key.date}")
                                )
                                Text(
                                    text = str(
                                        Res.string.preview_after,
                                        str(change.after.status.label()),
                                        str(change.after.source.label())
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("preview-after-${key.date}")
                                )
                                change.evidence?.let { evidence ->
                                    val time = evidence.confirmedAt?.let {
                                        DisplayFormats.time(it, change.zoneId, language)
                                    } ?: str(Res.string.no_confirmed_time)
                                    Text(
                                        text = str(
                                            Res.string.preview_evidence,
                                            str(evidence.status.label()),
                                            str(evidence.source.label()),
                                            time
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("preview-evidence-${key.date}")
                                    )
                                }
                            }
                        }
                    }
                }

                if (preview.affectedDays.isEmpty() && preview.preTrackingDates.isEmpty() && preview.rejection == null) {
                    item {
                        Text(
                            text = str(Res.string.preview_no_dates),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && preview.rejection == null,
                modifier = Modifier.testTag("preview-confirm"),
                onClick = { onConfirm(preview.reviewToken) }
            ) {
                Text(str(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                modifier = Modifier.testTag("preview-cancel"),
                onClick = onDismiss
            ) {
                Text(str(Res.string.cancel))
            }
        }
    )
}

private fun showsLegacyBalance(preview: TimeOffPreview, category: TrackerCategory?): Boolean {
    val balanceKnown = preview.rejection == null || preview.rejection == PlanRejection.INSUFFICIENT_BALANCE
    return balanceKnown && (category == TrackerCategory.WORK || preview.balanceDelta != 0 ||
            preview.changes.any { it.category == TrackerCategory.WORK })
}

@Composable
private fun BalancePreview(delta: Int, remainingDays: Int, title: String? = null) {
    val language = LocalAppLanguage.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(
                text = str(
                    Res.string.preview_balance,
                    DisplayFormats.number(delta, language),
                    DisplayFormats.number(remainingDays, language)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
