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
import dev.daymark.resources.plan_storage_failed
import dev.daymark.resources.plan_insufficient
import dev.daymark.resources.plan_invalid
import dev.daymark.resources.plan_saved
import dev.daymark.resources.plan_stale
import dev.daymark.resources.time_off_before_tracking
import dev.daymark.settings.str

internal fun PlanRejection.message() = when (this) {
    PlanRejection.OUTSIDE_TRACKED_PERIOD -> Res.string.time_off_before_tracking
    PlanRejection.INSUFFICIENT_BALANCE -> Res.string.plan_insufficient
    PlanRejection.LOCAL_SAVE_FAILED -> Res.string.plan_storage_failed
    PlanRejection.INVALID_RANGE, PlanRejection.INVALID_VALUE -> Res.string.plan_invalid
}

@Composable
internal fun PlanMutationFeedback(mutation: PlanMutationResult?) {
    val message = when (mutation) {
        null -> return
        PlanMutationResult.Applied -> Res.string.plan_saved
        PlanMutationResult.StalePreview -> Res.string.plan_stale
        PlanMutationResult.InsufficientBalance -> Res.string.plan_insufficient
        PlanMutationResult.LocalSaveFailed -> Res.string.plan_storage_failed
        is PlanMutationResult.Invalid -> mutation.reason.message()
    }
    val saved = mutation == PlanMutationResult.Applied
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.small,
        color = if (saved) colors.primaryContainer else colors.errorContainer,
        contentColor = if (saved) colors.onPrimaryContainer else colors.onErrorContainer
    ) {
        Text(str(message), modifier = Modifier.padding(12.dp))
    }
}
