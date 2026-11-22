package dev.daymark.presentation.features.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.plan.TimeOffKind
import dev.daymark.presentation.features.plan.component.TimeOffEditorTarget
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.component.PlanPreviewDialog
import dev.daymark.presentation.features.plan.component.PlanTimeOffCard
import dev.daymark.presentation.features.plan.component.TimeOffEditorDialog
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.resources.time_off
import dev.daymark.settings.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

data class PlanActions(
    val previewTimeOff: (TimeOffCommand) -> Unit,
    val confirmTimeOff: (PlanReviewToken) -> Unit,
    val cancelPreview: () -> Unit
)

@Composable
fun PlanScreen(
    model: PlanViewModel = koinViewModel(),
    initialTimeOffDate: LocalDate? = null,
    initialWorkMission: Boolean = false,
    onCloseWorkMission: (() -> Unit)? = null
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val actions = remember(model) {
        PlanActions(model::previewTimeOff, model::confirmTimeOff, model::cancelPreview)
    }
    PlanContent(
        state = state,
        clock = model.clock,
        initialTimeOffDate = initialTimeOffDate,
        actions = actions,
        initialWorkMission = initialWorkMission,
        onCloseWorkMission = onCloseWorkMission
    )
}

@Composable
fun PlanContent(
    state: PlanUiState,
    clock: Clock,
    initialTimeOffDate: LocalDate?,
    actions: PlanActions,
    initialWorkMission: Boolean = false,
    onCloseWorkMission: (() -> Unit)? = null
) {
    val savedPlan = state.plan?.current
    var editOffId by rememberSaveable { mutableStateOf<String?>(null) }
    val editOff = state.entries.firstOrNull { it.id == editOffId }
    var timeOffStartDate by rememberSaveable { mutableStateOf<String?>(null) }
    var addingOff by rememberSaveable { mutableStateOf(false) }
    var addingMissionFromHistory by rememberSaveable { mutableStateOf(false) }
    var handledInitialDate by rememberSaveable(initialTimeOffDate) { mutableStateOf(false) }

    LaunchedEffect(state.ready, initialTimeOffDate) {
        if (state.ready && initialTimeOffDate != null && !handledInitialDate) {
            handledInitialDate = true
            addingMissionFromHistory = initialWorkMission
            editOffId = state.entries.sortedBy { if (it.kind == TimeOffKind.HOLIDAY) 0 else 1 }
                .firstOrNull {
                    initialTimeOffDate in it.from..it.through &&
                            (!initialWorkMission || it.kind == TimeOffKind.WORK_MISSION)
                }?.id
            timeOffStartDate = initialTimeOffDate.toString()
            addingOff = editOffId == null
        }
    }

    LaunchedEffect(state.result) {
        if (state.result == PlanMutationResult.Applied) {
            if (addingMissionFromHistory) onCloseWorkMission?.invoke()
            addingOff = false
            editOffId = null
            addingMissionFromHistory = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("plan-root"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = str(Res.string.time_off),
            style = MaterialTheme.typography.headlineLarge
        )

        if (!state.ready) {
            CircularProgressIndicator()
        }

        if (state.storageError) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = str(Res.string.load_failed),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        PlanMutationFeedback(state.result)
        if (savedPlan != null) {
            PlanTimeOffCard(
                category = savedPlan.category,
                entries = state.entries,
                busy = state.busy,
                onAddTimeOff = {
                    timeOffStartDate = null
                    addingMissionFromHistory = false
                    addingOff = true
                },
                onEditEntry = {
                    addingMissionFromHistory = false
                    editOffId = it.id
                }
            )
        }
    }

    if ((addingOff || editOff != null) && savedPlan != null) {
        val today = clock.now().toLocalDateTime(TimeZone.of(savedPlan.schedule.zoneId)).date
        val availableKinds = if (addingMissionFromHistory) listOf(TimeOffKind.WORK_MISSION)
        else TimeOffKind.entries.filter {
            it != TimeOffKind.WORK_MISSION || (editOff == null && state.plan.hasWorkDates)
        }
        TimeOffEditorDialog(
            target = TimeOffEditorTarget(
                editOff, timeOffStartDate?.let(LocalDate::parse) ?: today,
                savedPlan.category, availableKinds
            ),
            busy = state.busy,
            onDismiss = {
                if (addingMissionFromHistory) onCloseWorkMission?.invoke()
                addingOff = false
                editOffId = null
                addingMissionFromHistory = false
            },
            onPreview = actions.previewTimeOff
        )
    }

    state.timeOffPreview?.let { preview ->
        PlanPreviewDialog(
            preview = preview,
            category = savedPlan?.category,
            busy = state.busy,
            onConfirm = actions.confirmTimeOff,
            onDismiss = actions.cancelPreview
        )
    }
}
