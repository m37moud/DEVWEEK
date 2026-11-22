package dev.daymark.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanRejection
import dev.daymark.resources.Res
import dev.daymark.resources.plan_insufficient
import dev.daymark.resources.plan_insufficient_year
import dev.daymark.resources.plan_invalid
import dev.daymark.resources.plan_saved
import dev.daymark.resources.plan_stale
import dev.daymark.resources.plan_storage_failed
import dev.daymark.resources.work_mission_conflict
import dev.daymark.resources.work_mission_ineligible
import dev.daymark.resources.work_mission_note_too_long
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.isolated
import dev.daymark.settings.str

internal fun PlanRejection.message() = when (this) {
    PlanRejection.INSUFFICIENT_BALANCE -> Res.string.plan_insufficient
    PlanRejection.LOCAL_SAVE_FAILED -> Res.string.plan_storage_failed
    PlanRejection.WORK_MISSION_CONFLICT -> Res.string.work_mission_conflict
    PlanRejection.WORK_MISSION_INELIGIBLE -> Res.string.work_mission_ineligible
    PlanRejection.NOTE_TOO_LONG -> Res.string.work_mission_note_too_long
    PlanRejection.INVALID_RANGE, PlanRejection.INVALID_VALUE -> Res.string.plan_invalid
}

@Composable
internal fun insufficientVacationYearMessage(year: Int): String =
    str(Res.string.plan_insufficient_year, isolated(DisplayFormats.number(year, LocalAppLanguage.current)))

@Composable
internal fun PlanMutationFeedback(mutation: PlanMutationResult?) {
    val message = when (mutation) {
        null -> return
        PlanMutationResult.Applied -> str(Res.string.plan_saved)
        PlanMutationResult.StalePreview -> str(Res.string.plan_stale)
        PlanMutationResult.InsufficientBalance -> str(Res.string.plan_insufficient)
        is PlanMutationResult.InsufficientYear -> insufficientVacationYearMessage(mutation.year)
        PlanMutationResult.LocalSaveFailed -> str(Res.string.plan_storage_failed)
        is PlanMutationResult.Invalid -> str(mutation.reason.message())
    }
    val saved = mutation == PlanMutationResult.Applied
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.small,
        color = if (saved) colors.primaryContainer else colors.errorContainer,
        contentColor = if (saved) colors.onPrimaryContainer else colors.onErrorContainer
    ) {
        Text(message, modifier = Modifier.padding(12.dp))
    }
}
