package dev.daymark.reminders

/** A native scheduling result is separate from local attendance persistence. */
interface ReminderPort {
    fun requestPermission(completion: (Boolean) -> Unit)
    fun replace(remindersJson: String, completion: (Boolean) -> Unit)
    fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit)
}
