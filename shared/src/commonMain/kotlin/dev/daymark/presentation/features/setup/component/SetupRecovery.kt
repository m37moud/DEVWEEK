package dev.daymark.presentation.features.setup.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.daymark.presentation.features.setup.OnboardingActions
import dev.daymark.presentation.features.setup.SetupUiState
import dev.daymark.resources.Res
import dev.daymark.resources.cancel
import dev.daymark.resources.onboarding_reset
import dev.daymark.resources.onboarding_reset_confirmation
import dev.daymark.resources.reset
import dev.daymark.resources.retry
import dev.daymark.resources.storage_unavailable
import dev.daymark.settings.str

@Composable
internal fun SetupRecovery(state: SetupUiState, actions: OnboardingActions) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    if (state.busy) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = str(Res.string.storage_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.testTag("setup-load-error")
                )
                Button(
                    onClick = actions.retryLoad,
                    modifier = Modifier.testTag("setup-retry")
                ) {
                    Text(str(Res.string.retry))
                }
                if (state.unreadable) {
                    TextButton(onClick = { confirmReset = true }) {
                        Text(str(Res.string.onboarding_reset))
                    }
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(str(Res.string.onboarding_reset)) },
            text = { Text(str(Res.string.onboarding_reset_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    actions.reset()
                }) {
                    Text(str(Res.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(str(Res.string.cancel))
                }
            }
        )
    }
}
