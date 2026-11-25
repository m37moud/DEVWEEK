package dev.daymark.android

import dev.daymark.attendance.AttendanceService
import dev.daymark.location.LocationCapabilities
import dev.daymark.models.attendance.CheckRejection
import dev.daymark.models.attendance.DayKey

/** Returns true only for a WorkManager retry after a local storage failure. */
class AndroidAttendanceJob(
    private val attendance: AttendanceService,
    private val capabilities: () -> LocationCapabilities,
    private val disableAutomation: suspend (String) -> Unit,
    private val recoverSchedule: suspend () -> Unit
) {
    suspend fun run(key: DayKey, ordinal: Int = 1): Boolean {
        val location = capabilities()
        if (!location.permitsBackgroundAttendance) {
            disableAutomation(key.trackerId)
            recoverSchedule()
            return false
        }
        if (ordinal > 1 && !location.servicesEnabled) {
            recoverSchedule()
            return false
        }
        val outcome = attendance.runAutomatic(key = key, ordinal = ordinal)
        recoverSchedule()
        if (outcome.reason == CheckRejection.LOCATION_DENIED) disableAutomation(key.trackerId)
        return outcome.reason == CheckRejection.LOCAL_SAVE_FAILED
    }
}


internal val LocationCapabilities.permitsBackgroundAttendance: Boolean
    get() = foregroundGranted && preciseGranted && backgroundGranted
