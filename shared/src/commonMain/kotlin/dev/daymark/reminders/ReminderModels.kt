package dev.daymark.reminders

import kotlinx.serialization.Serializable

@Serializable
data class ReminderSpec(
    val id: String, val trackerId: String, val attendanceDate: String, val fireAtEpochMillis: Long,
    val title: String, val body: String, val localeTag: String
)

data class ReminderCopy(val localeTag: String, val title: String, val body: String)

@Serializable
internal data class ReminderEnvelope(val trackerId: String, val reminders: List<ReminderSpec>)
