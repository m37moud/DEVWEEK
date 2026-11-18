package dev.daymark.presentation.features.today.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.presentation.features.today.TodayVacationBalanceUiState
import dev.daymark.resources.Res
import dev.daymark.resources.adjust_balance
import dev.daymark.resources.vacation_balance_load_failed
import dev.daymark.resources.vacation_balance
import dev.daymark.resources.vacation_balance_original
import dev.daymark.resources.vacation_balance_remaining
import dev.daymark.resources.vacation_balance_used
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

@Composable
fun TodayVacationBalance(
    state: TodayVacationBalanceUiState,
    onSave: (String, String?) -> Unit
) {
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var submittedAtRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.saveRevision) {
        val submitted = submittedAtRevision
        if (submitted != null && state.saveRevision > submitted) {
            adjusting = false
            submittedAtRevision = null
        }
    }

    val summary = state.summary
    if (state.loadFailed) {
        Text(
            text = str(Res.string.vacation_balance_load_failed),
            color = MaterialTheme.colorScheme.error
        )
    }
    if (summary == null) return

    TodayVacationBalanceCard(
        summary = summary,
        onAdjustBalance = {
            submittedAtRevision = null
            adjusting = true
        }
    )
    if (adjusting) {
        BalanceAdjustDialog(
            state = BalanceAdjustUiState(
                initialBalance = summary.remainingDays,
                busy = state.saving,
                saveFailed = submittedAtRevision != null && !state.saving &&
                        state.saveResult != null && state.saveResult != PlanMutationResult.Applied
            ),
            onSave = { text, note ->
                submittedAtRevision = state.saveRevision
                onSave(text, note)
            },
            onDismiss = { adjusting = false }
        )
    }
}

@Composable
private fun TodayVacationBalanceCard(
    summary: VacationBalanceSummary,
    onAdjustBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().testTag("today-vacation-balance")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(str(Res.string.vacation_balance), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BalanceFigure(str(Res.string.vacation_balance_original), summary.originalDays)
                BalanceFigure(str(Res.string.vacation_balance_used), summary.usedDays)
                BalanceFigure(
                    str(Res.string.vacation_balance_remaining),
                    summary.remainingDays,
                    emphasized = true
                )
            }
            TextButton(onClick = onAdjustBalance) { Text(str(Res.string.adjust_balance)) }
        }
    }
}

@Composable
private fun BalanceFigure(label: String, days: Int, emphasized: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = isolated(DisplayFormats.number(days, LocalAppLanguage.current)),
            style = if (emphasized) MaterialTheme.typography.headlineSmall
            else MaterialTheme.typography.titleLarge,
            color = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
