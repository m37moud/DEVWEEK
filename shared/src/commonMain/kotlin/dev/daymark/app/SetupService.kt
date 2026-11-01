package dev.daymark.app

import androidx.room3.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import dev.daymark.plan.*
import dev.daymark.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class SetupDraft(val displayName: String = "", val category: TrackerCategory? = null, val placeName: String = "",
    val place: RegisteredPlace? = null, val schedule: WeeklySchedule? = null, val openingBalanceText: String = "0") {
    override fun toString() = "SetupDraft(metadata=<redacted>)"
}
data class SetupCommand(val displayName: String, val category: TrackerCategory, val placeName: String,
    val place: RegisteredPlace, val schedule: WeeklySchedule, val openingBalance: Int) {
    override fun toString() = "SetupCommand(metadata=<redacted>)"
}
sealed interface SetupResult {
    data object Completed : SetupResult
    data object Invalid : SetupResult
    data object LocalSaveFailed : SetupResult
}
class SetupService(private val db: DaymarkDatabase, private val clock: Clock) {
    private val dao get() = db.dao()
    fun observeDraft(): Flow<SetupDraft> = dao.observeSetting("setup-draft").map { row ->
        row?.let { runCatching { decodeDraft(it.value) }.getOrNull() } ?: SetupDraft()
    }
    suspend fun saveDraft(draft: SetupDraft) = db.withWriteTransaction {
        // A delayed draft write cannot recreate setup data after the profile has completed.
        if (dao.tracker() == null) dao.putSetting(SettingEntity("setup-draft", encodeDraft(draft)))
    }
    suspend fun complete(command: SetupCommand): SetupResult {
        if (command.displayName.isBlank() || command.placeName.isBlank() || command.openingBalance < 0) return SetupResult.Invalid
        val now = clock.now()
        val date = now.toLocalDateTime(TimeZone.of(command.schedule.zoneId)).date
        val id = Uuid.random().toString()
        val first = try {
            (0..7).map { date.plus(it, DateTimeUnit.DAY) }.map {
                resolveDay(DayKey(id, it), command.schedule, command.category, command.place, 1)
            }.first { it.isScheduled && it.arrival >= now }
        } catch (_: IllegalArgumentException) { return SetupResult.Invalid }
        return try {
            db.withWriteTransaction {
                if (dao.tracker() != null) return@withWriteTransaction SetupResult.Invalid
                dao.insertTracker(TrackerEntity(id, command.displayName, now.toString(), first.arrival.toString(),
                    if (command.category == TrackerCategory.WORK) command.openingBalance else 0, automaticEnabled = false))
                dao.putVersion(PlanVersionEntity.fromSnapshot(id, 1, date, now, command.category, command.schedule,
                    command.place.copy(version = 1), command.placeName))
                SettingsRepository(db).update(displayName = command.displayName)
                dao.deleteSetting("setup-draft")
                dao.markDirty(id, "OVERVIEW")
                dao.markDirty(id, "TIME_OFF")
                SetupResult.Completed
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SetupResult.LocalSaveFailed }
    }
}
private fun encodeDraft(draft: SetupDraft): String = buildJsonObject {
    put("displayName", draft.displayName); put("placeName", draft.placeName); put("openingBalanceText", draft.openingBalanceText)
    draft.category?.let { put("category", it.name) }
    draft.schedule?.let { put("schedule", encodeSchedule(it)) }
    draft.place?.let { place -> putJsonObject("place") {
        put("latitude", place.latitude); put("longitude", place.longitude); put("radius", place.radiusMetres); put("version", place.version)
    } }
}.toString()
private fun decodeDraft(text: String): SetupDraft {
    val json = Json.parseToJsonElement(text).jsonObject
    val place = json["place"]?.jsonObject?.let { RegisteredPlace(it.getValue("latitude").jsonPrimitive.double,
        it.getValue("longitude").jsonPrimitive.double, it.getValue("radius").jsonPrimitive.int, it.getValue("version").jsonPrimitive.long) }
    return SetupDraft(json["displayName"]?.jsonPrimitive?.content.orEmpty(), json["category"]?.jsonPrimitive?.content?.let(TrackerCategory::valueOf),
        json["placeName"]?.jsonPrimitive?.content.orEmpty(), place, json["schedule"]?.jsonPrimitive?.content?.let(::decodeSchedule),
        json["openingBalanceText"]?.jsonPrimitive?.content.orEmpty())
}
