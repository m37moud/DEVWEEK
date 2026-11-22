package dev.daymark.presentation.features.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.AttendancePeriodReview
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.component.attendancePeriodDates
import dev.daymark.resources.Res
import dev.daymark.resources.attendance_period_balance_impact
import dev.daymark.resources.cancel
import dev.daymark.resources.preview_title
import dev.daymark.resources.save
import dev.daymark.resources.vacation_balance_original
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
internal fun AttendancePeriodReviewDialog(
    review: AttendancePeriodReview,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val language = LocalAppLanguage.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(Res.string.preview_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(DisplayFormats.monthYear(review.period.month, language))
                Text(attendancePeriodDates(review.period))
                review.balances.forEach { balance ->
                    val year = balance.year
                    val label = if (year == null) str(Res.string.vacation_balance_original)
                    else isolated(DisplayFormats.number(year, language))
                    Text(
                        str(
                            Res.string.attendance_period_balance_impact,
                            label,
                            isolated(DisplayFormats.number(balance.usedDelta, language)),
                            isolated(DisplayFormats.number(balance.remainingDays, language))
                        )
                    )
                }
                PlanMutationFeedback(review.rejection)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enabled && review.rejection == null,
                modifier = Modifier.testTag("attendance-period-save")
            ) {
                Text(str(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) {
                Text(str(Res.string.cancel))
            }
        }
    )
}
