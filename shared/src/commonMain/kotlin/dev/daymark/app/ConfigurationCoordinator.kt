package dev.daymark.app

import androidx.compose.runtime.staticCompositionLocalOf
import dev.daymark.data.DaymarkDatabase
import dev.daymark.plan.RoomPlanReader
import dev.daymark.reminders.*
import dev.daymark.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

data class ConfigurationHealth(val scheduleOk: Boolean? = null,val remindersOk: Boolean? = null,
    val widgetOk: Boolean? = null,val reminderThrough: LocalDate? = null) {
    val needsAttention get() = scheduleOk == false || remindersOk == false || widgetOk == false
}
val LocalConfigurationHealth = staticCompositionLocalOf { ConfigurationHealth() }

/** Rebuild derived native configuration after local commits. Never requests permissions or GPS. */
class ConfigurationCoordinator(private val db: DaymarkDatabase,private val clock: Clock,private val reminders: ReminderPort,
    private val onScheduleRefresh: suspend () -> Boolean = { false },
    private val onWidgetRefresh: suspend (AppLanguage) -> Boolean? = { null }) {
    private val mutex = Mutex()
    suspend fun refresh(settings: AppSettings,language: AppLanguage,copy: ReminderCopy,platform: HostPlatform): ConfigurationHealth = mutex.withLock {
        val tracker = db.dao().tracker() ?: return@withLock ConfigurationHealth()
        val scheduleOk = if (platform == HostPlatform.ANDROID) onScheduleRefresh() else null
        var reminderThrough: LocalDate? = null
        val remindersOk = if (platform != HostPlatform.IOS) null else if (!settings.remindersEnabled) {
            awaitNative { reminders.cancelTracker(tracker.id,it) }
        } else {
            val reader = RoomPlanReader(db,clock)
            val context = reader.context() ?: return@withLock ConfigurationHealth(remindersOk = false)
            val payload = planReminders(clock.now(),reader,copy)
            val latest = db.dao().tracker()
            if (latest?.id != tracker.id || latest.changeRevision != tracker.changeRevision) return@withLock ConfigurationHealth(remindersOk = false)
            val replaced = awaitNative { reminders.replace(encodeReminders(tracker.id,payload),it) }
            if (replaced) reminderThrough = clock.now().toLocalDateTime(TimeZone.of(context.zoneId)).date.plus(29,DateTimeUnit.DAY)
            replaced
        }
        ConfigurationHealth(scheduleOk,remindersOk,onWidgetRefresh(language),reminderThrough)
    }
    private suspend fun awaitNative(action: ((Boolean) -> Unit) -> Unit): Boolean = withTimeoutOrNull(10.seconds) {
        suspendCancellableCoroutine { continuation -> action { result -> if (continuation.isActive) continuation.resume(result) } }
    } ?: false
}
