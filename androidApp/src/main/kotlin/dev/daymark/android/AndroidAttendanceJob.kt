package dev.daymark.android

import dev.daymark.attendance.*
import dev.daymark.attendance.AttendanceService
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.DayKey

/** Returns true only when local state could not be saved; sensor outcomes never request a retry. */
class AndroidAttendanceJob(private val attendance: AttendanceService, private val canRunInBackground: () -> Boolean,
    private val disableAutomation: suspend (String) -> Unit, private val recoverSchedule: suspend () -> Unit) {
    suspend fun run(key: DayKey): Boolean {
        if (!canRunInBackground()) {
            disableAutomation(key.trackerId)
            recoverSchedule()
            return false
        }
        val outcome = attendance.runAutomatic(key)
        if (outcome.reason == CheckRejection.LOCATION_DENIED) disableAutomation(key.trackerId)
        recoverSchedule()
        return outcome.reason == CheckRejection.LOCAL_SAVE_FAILED
    }
}
