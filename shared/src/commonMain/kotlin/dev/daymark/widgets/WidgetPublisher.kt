package dev.daymark.widgets

import androidx.room3.withReadTransaction
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.attendance.evaluateDay
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.effectiveLanguage
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WidgetPublicationException : Exception("Could not update the attendance widget")

/** One application-owned publisher serializes refresh/clear. The native file contains display fields only. */
class WidgetPublisher(
    private val db: DaymarkDatabase, private val repository: AttendanceRepository,
    private val sink: WidgetSink, private val clock: Clock,
    private val systemTags: () -> List<String> = ::widgetSystemLanguageTags,
    private val statusLabel: (AttendanceStatus?, AppLanguage) -> String = ::widgetStatusLabel,
    private val settings: SettingsRepository = SettingsRepository(db)
) {
    private val mutex = Mutex()

    private data class Projection(
        val trackerId: String,
        val revision: Long,
        val settingsValue: String?,
        val snapshot: WidgetSnapshot
    )

    suspend fun refresh(): Unit = mutex.withLock {
        repeat(2) {
            val projection = project(clock.now())
            if (projection == null) {
                if (!withContext(Dispatchers.IO) { sink.clear() }) throw WidgetPublicationException()
                return@withLock
            }
            val current = db.dao().tracker()
            val preferences = db.dao().setting("ui-settings")?.value
            if (current?.id == projection.trackerId && current.changeRevision == projection.revision && preferences == projection.settingsValue) {
                if (!withContext(Dispatchers.IO) { sink.publish(encodeWidgetSnapshot(projection.snapshot)) }) throw WidgetPublicationException()
                return@withLock
            }
        }
        throw WidgetPublicationException()
    }

    suspend fun clear(): Unit =
        mutex.withLock { if (!withContext(Dispatchers.IO) { sink.clear() }) throw WidgetPublicationException() }

    private suspend fun project(now: Instant): Projection? = db.withReadTransaction {
        val tracker = db.dao().tracker() ?: return@withReadTransaction null
        val preferences = settings.snapshot()
        val settingsValue = db.dao().setting("ui-settings")?.value
        val day = repository.loadCurrentDay(now) ?: return@withReadTransaction null
        val language = effectiveLanguage(preferences.language, systemTags())
        val isTracked =
            day.revision > 0 || day.plan.arrival >= Instant.parse(tracker.trackingStartsAt)
        val outcome = evaluateDay(day.plan, day.evidence, day.off, now)
        val version =
            db.dao().versions(tracker.id).firstOrNull { it.version == day.plan.scheduleVersion }
        val label = version?.placeName.orEmpty().let {
            if (it.length <= 512) it else it.take(511)
                .trimEnd { char -> char in '\uD800'..'\uDBFF' } + "…"
        }
        Projection(
            tracker.id, tracker.changeRevision, settingsValue, WidgetSnapshot(
                trackerId = tracker.id,
                generatedAtEpochMillis = now.toEpochMilliseconds(),
                attendanceDate = day.plan.key.date.toString(),
                effectiveLocale = language.tag,
                displayStatus = statusLabel(if (isTracked) outcome.status else null, language),
                statusCode = if (isTracked) outcome.status.name else AttendanceStatus.SCHEDULED.name,
                placeLabel = label,
                deadlineDisplay = if (isTracked) DisplayFormats.time(
                    day.plan.deadline,
                    day.plan.zoneId,
                    language
                ) else null,
                deadlineEpochMillis = if (isTracked) day.plan.deadline.toEpochMilliseconds() else null,
                zoneId = day.plan.zoneId,
                quickCheckRoute = encodeQuickCheckRoute(QuickCheckRoute(trackerId = tracker.id)),
                arrivalEpochMillis = if (isTracked) day.plan.arrival.toEpochMilliseconds() else null,
                themePreference = preferences.theme.name,
                isTracked = isTracked
            )
        )
    }
}

internal expect fun widgetStatusLabel(status: AttendanceStatus?, language: AppLanguage): String
internal expect fun widgetSystemLanguageTags(): List<String>
