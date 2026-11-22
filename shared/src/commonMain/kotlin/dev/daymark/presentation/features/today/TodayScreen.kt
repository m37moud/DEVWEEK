package dev.daymark.presentation.features.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.history.HistoryMetric
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.presentation.features.today.component.TodayErrorCallout
import dev.daymark.presentation.features.today.component.TodayHeroCard
import dev.daymark.presentation.features.today.component.TodayMonthMetrics
import dev.daymark.presentation.features.today.component.TodayNextAttendanceBanner
import dev.daymark.presentation.features.today.component.TodayReportStatus
import dev.daymark.presentation.features.today.component.TodayVacationBalance
import dev.daymark.presentation.features.today.component.TodayWeekStrip
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.resources.time_off
import dev.daymark.resources.welcome_name
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

data class TodayActions(
    val checkAttendance: () -> Unit,
    val openLocationSettings: () -> Unit,
    val openMetric: (HistoryMetric, LocalDate) -> Unit,
    val adjustVacationBalance: (String, String?) -> Unit,
    val editTimeOff: ((LocalDate) -> Unit)? = null
)

@Composable
fun TodayScreen(
    model: TodayViewModel = koinViewModel(),
    displayName: String,
    onHistory: () -> Unit,
    onMetric: (HistoryMetric, LocalDate) -> Unit,
    onEditTimeOff: ((LocalDate) -> Unit)? = null
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.setActive(true)
        onStopOrDispose { model.setActive(false) }
    }
    val actions = remember(model, onMetric, onEditTimeOff) {
        TodayActions(
            checkAttendance = model::checkAttendance,
            openLocationSettings = model::openLocationSettings,
            openMetric = onMetric,
            adjustVacationBalance = model::setAvailableBalance,
            editTimeOff = onEditTimeOff
        )
    }
    TodayContent(state, displayName, onHistory, actions)
}

@Composable
fun TodayContent(
    state: TodayUiState,
    displayName: String,
    onHistory: () -> Unit,
    actions: TodayActions
) {
    val dashboard = state.dashboard
    val report = state.report
    val language = LocalAppLanguage.current
    val summaryReady = dashboard.ready && (dashboard.date == null || dashboard.date == state.date)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("today-root"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.date?.let {
                Text(
                    text = DisplayFormats.date(it, language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = str(Res.string.welcome_name, isolated(displayName)),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        TodayHeroCard(
            state = state,
            summaryReady = summaryReady,
            language = language,
            onCheckAttendance = actions.checkAttendance,
            onHistory = onHistory
        )

        state.error?.let { rejection ->
            TodayErrorCallout(
                error = rejection,
                onOpenSettings = actions.openLocationSettings
            )
        }

        val displayedDate = state.date
        val editTimeOff = actions.editTimeOff
        if (displayedDate != null && state.ready && editTimeOff != null) {
            TextButton(
                onClick = { editTimeOff(displayedDate) },
                modifier = Modifier.testTag("today-time-off-action")
            ) {
                Text(str(Res.string.time_off))
            }
        }

        dashboard.takeIf { summaryReady }?.let { summary ->
            if (summary.failed) {
                Text(
                    text = str(Res.string.load_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        summary.date?.let { displayedDate ->
                            TodayMonthMetrics(
                                metrics = summary.metrics,
                                showWorkMissions = state.plan?.category == TrackerCategory.WORK ||
                                        summary.metrics.workMissions > 0,
                                onHistory = onHistory,
                                onMetric = { actions.openMetric(it, summary.period?.month ?: displayedDate) },
                                period = summary.period
                            )
                        }

                        TodayWeekStrip(
                            week = summary.week,
                            currentDate = summary.date,
                            language = language
                        )
                    }
                }

                summary.nextAttendance?.let { next ->
                    TodayNextAttendanceBanner(
                        next = next,
                        language = language
                    )
                }
            }
        }

        if (state.ready && state.activeCategory == TrackerCategory.WORK) {
            TodayVacationBalance(state.vacationBalance, actions.adjustVacationBalance)
        }

        TodayReportStatus(
            report = report,
            language = language
        )
    }
}
