package dev.daymark.presentation.features.settings.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanReviewToken
import dev.daymark.models.plan.PlanState
import dev.daymark.presentation.component.ArrivalTimeDialog
import dev.daymark.presentation.component.PlanMutationFeedback
import dev.daymark.presentation.component.PlanPreviewDialog
import dev.daymark.presentation.component.ZonePicker
import dev.daymark.presentation.features.settings.AttendanceSettingsUiState
import dev.daymark.presentation.features.settings.AttendanceSettingsViewModel
import dev.daymark.resources.Res
import dev.daymark.resources.load_failed
import dev.daymark.resources.plan_effective
import dev.daymark.resources.plan_invalid
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.str
import kotlinx.datetime.DayOfWeek
import org.koin.compose.viewmodel.koinViewModel

data class AttendanceSettingsActions(
    val edit: ((PlanChange) -> PlanChange) -> Unit,
    val preview: (PlanChange) -> Unit,
    val confirm: (PlanReviewToken) -> Unit,
    val cancelPreview: () -> Unit,
    val registerCurrentPlace: () -> Unit,
    val openLocationSettings: () -> Unit,
    val resetDraft: () -> Unit
)

private enum class AttendanceEditor {
    ROUTINE,
    PLACE
}

@Composable
internal fun AttendanceSettingsSection(
    attendanceSettingsViewModel: AttendanceSettingsViewModel = koinViewModel()
) {
    val state by attendanceSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val place = state.draft?.place ?: state.plan?.let { it.pending?.place ?: it.current.place }
    LaunchedEffect(
        attendanceSettingsViewModel,
        place?.latitude,
        place?.longitude,
        language,
        state.placeRegistrationRevision
    ) {
        if (place != null) {
            attendanceSettingsViewModel.showPlaceAddress(
                place = place,
                language = language
            )
        }
    }
    val actions = remember(attendanceSettingsViewModel) {
        AttendanceSettingsActions(
            edit = attendanceSettingsViewModel::edit,
            preview = attendanceSettingsViewModel::preview,
            confirm = attendanceSettingsViewModel::confirm,
            cancelPreview = attendanceSettingsViewModel::cancelPreview,
            registerCurrentPlace = attendanceSettingsViewModel::registerCurrentPlace,
            openLocationSettings = attendanceSettingsViewModel::openLocationSettings,
            resetDraft = attendanceSettingsViewModel::resetDraft
        )
    }
    AttendanceSettingsContent(
        state = state,
        actions = actions
    )
}

@Composable
fun AttendanceSettingsContent(
    state: AttendanceSettingsUiState,
    actions: AttendanceSettingsActions,
    modifier: Modifier = Modifier
) {
    var activeEditor by rememberSaveable { mutableStateOf<AttendanceEditor?>(value = null) }
    var chooseZone by rememberSaveable { mutableStateOf(false) }
    var timeDay by rememberSaveable { mutableStateOf<String?>(null) }

    fun toggleEditor(editor: AttendanceEditor) {
        val current = activeEditor
        if (state.busy || (current != null && current != editor)) return
        actions.resetDraft()
        chooseZone = false
        timeDay = null
        activeEditor = if (current == editor) null else editor
    }

    LaunchedEffect(state.result) {
        if (state.result == PlanMutationResult.Applied) {
            activeEditor = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp)
    ) {
        if (!state.ready) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        AnimatedVisibility(
            visible = state.storageError,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically(),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)) + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(all = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(size = 20.dp)
                    )
                    Text(
                        text = str(resource = Res.string.load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        val plan = state.plan ?: return@Column
        val draft = state.draft

        AttendanceRoutineCard(
            plan = plan,
            draft = draft,
            state = state,
            actions = actions,
            editing = activeEditor == AttendanceEditor.ROUTINE,
            onToggleEditing = if (activeEditor == AttendanceEditor.PLACE) null else {
                { toggleEditor(editor = AttendanceEditor.ROUTINE) }
            },
            onPickZone = { chooseZone = true },
            onPickTime = { day -> timeDay = day }
        )

        RegisteredPlaceCard(
            plan = plan,
            draft = draft,
            state = state,
            actions = actions,
            editing = activeEditor == AttendanceEditor.PLACE,
            onToggleEditing = if (activeEditor == AttendanceEditor.ROUTINE) null else {
                { toggleEditor(editor = AttendanceEditor.PLACE) }
            }
        )
    }

    if (activeEditor == AttendanceEditor.ROUTINE && chooseZone && state.draft != null) {
        ZonePicker(
            current = state.draft.schedule.zoneId,
            onSelect = { zone ->
                actions.edit { it.copy(schedule = it.schedule.copy(zoneId = zone)) }
                chooseZone = false
            },
            onDismiss = { chooseZone = false }
        )
    }

    val currentDraft = state.draft
    val selectedDay = timeDay
    if (activeEditor == AttendanceEditor.ROUTINE && selectedDay != null && currentDraft != null) {
        AttendancePlanTimePicker(
            draft = currentDraft,
            selection = selectedDay,
            edit = actions.edit,
            close = { timeDay = null }
        )
    }

    val currentPreview = state.preview
    if (currentPreview != null) {
        val currentCategory = state.draft?.category
            ?: state.plan?.pending?.category
            ?: state.plan?.current?.category
            ?: TrackerCategory.WORK
        PlanPreviewDialog(
            preview = currentPreview,
            category = currentCategory,
            busy = state.busy,
            onConfirm = actions.confirm,
            onDismiss = actions.cancelPreview
        )
    }
}

@Composable
internal fun AttendancePlanTimePicker(
    draft: PlanChange,
    selection: String,
    edit: ((PlanChange) -> PlanChange) -> Unit,
    close: () -> Unit
) {
    val day = selection.takeIf { it != "default" }?.let(DayOfWeek::valueOf)
    ArrivalTimeDialog(
        initial = day?.let { draft.schedule.overrides[it] } ?: draft.schedule.arrival,
        onConfirm = { selected ->
            edit { plan ->
                val schedule = if (day == null) {
                    plan.schedule.copy(arrival = selected)
                } else {
                    plan.schedule.copy(overrides = plan.schedule.overrides + (day to selected))
                }
                plan.copy(schedule = schedule)
            }
            close()
        },
        onDismiss = close
    )
}

@Composable
internal fun AttendanceEditorReviewFooter(
    state: AttendanceSettingsUiState,
    draft: PlanChange,
    actions: AttendanceSettingsActions,
    reviewTestTag: String,
    reviewButtonText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp)
        ) {
            state.plan?.let { AttendancePlanEffectiveDate(plan = it) }

            AnimatedVisibility(
                visible = state.invalidInput,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically(),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)) + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(all = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(size = 18.dp)
                        )
                        Text(
                            text = str(resource = Res.string.plan_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            PlanMutationFeedback(mutation = state.result)

            Button(
                onClick = { actions.preview(draft) },
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(tag = reviewTestTag)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(size = 18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(text = reviewButtonText)
                }
            }
        }
    }
}

@Composable
internal fun SummaryInfoTile(
    icon: ImageVector,
    title: String,
    badgeText: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(weight = 1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(size = 32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(size = 16.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (secondaryText != null) {
                        Text(
                            text = secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun AttendancePlanEffectiveDate(
    plan: PlanState,
    modifier: Modifier = Modifier
) {
    val date = plan.effectiveFrom ?: return
    val language = LocalAppLanguage.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(size = 18.dp)
            )
            Text(
                text = str(
                    resource = Res.string.plan_effective,
                    DisplayFormats.date(date = date, language = language)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
