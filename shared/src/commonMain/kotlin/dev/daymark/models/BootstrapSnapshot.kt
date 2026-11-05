package dev.daymark.models

import dev.daymark.models.settings.AppSettings

data class TrackerSummary(
    val id: String, val displayName: String, val changeRevision: Long,
    val automaticEnabled: Boolean
)

data class BootstrapSnapshot(val settings: AppSettings, val tracker: TrackerSummary?)
