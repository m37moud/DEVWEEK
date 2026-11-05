package dev.daymark.models

import kotlinx.datetime.LocalDate

data class ConfigurationHealth(
    val scheduleOk: Boolean? = null, val remindersOk: Boolean? = null,
    val widgetOk: Boolean? = null, val reminderThrough: LocalDate? = null
) {
    val needsAttention get() = scheduleOk == false || remindersOk == false || widgetOk == false
}
