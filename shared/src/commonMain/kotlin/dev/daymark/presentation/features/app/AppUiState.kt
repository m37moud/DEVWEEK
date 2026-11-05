package dev.daymark.presentation.features.app

import dev.daymark.models.BootstrapSnapshot
import dev.daymark.models.ConfigurationHealth

data class AppUiState(
    val bootstrap: BootstrapSnapshot? = null,
    val failed: Boolean = false,
    val deleting: Boolean = false,
    val configuration: ConfigurationHealth = ConfigurationHealth()
)
