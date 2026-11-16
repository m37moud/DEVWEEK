package dev.daymark.notifications

import dev.daymark.models.attendance.CheckMode

interface AttendanceResultPort {
    val mode: CheckMode?
    fun requestPermission(completion: (Boolean) -> Unit)
    fun post(identifier: String, title: String, body: String)
    fun clearResults()
}

object UnsupportedAttendanceResultPort : AttendanceResultPort {
    override val mode: CheckMode? = null
    override fun requestPermission(completion: (Boolean) -> Unit) = completion(false)
    override fun post(identifier: String, title: String, body: String) = Unit
    override fun clearResults() = Unit
}
