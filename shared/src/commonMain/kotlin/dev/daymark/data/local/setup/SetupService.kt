package dev.daymark.data.local.setup

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import dev.daymark.repository.SetupStorageException
import kotlinx.coroutines.flow.catch
import dev.daymark.app.SetupDraftLoad
import dev.daymark.app.SetupStep
import dev.daymark.app.firstSetupArrival
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.PlanVersionEntity
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.TrackerEntity
import dev.daymark.data.local.decodeSchedule
import dev.daymark.data.local.encodeSchedule
import dev.daymark.data.local.markDirty
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.setup.SetupCommand
import dev.daymark.models.setup.SetupDraft
import dev.daymark.models.setup.SetupResult
import dev.daymark.repository.SetupDataSource
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class SetupService(
    private val db: DaymarkDatabase, private val clock: Clock,
    private val settings: SettingsRepository = SettingsRepository(db)
) : SetupDataSource {
    private val dao get() = db.dao()
    override fun observeDraft(): Flow<SetupDraftLoad> =
        dao.observeSetting("setup-draft").map { row ->
            if (row == null) SetupDraftLoad.Absent else readDraft(row.value)
        }.catch { failure ->
            if (failure is SQLiteException) throw SetupStorageException(failure)
            throw failure
        }

    override suspend fun startDraftSession(): String = draftTransaction {
        val session = Uuid.random().toString()
        if (dao.tracker() == null) dao.putSetting(SettingEntity("setup-session", session))
        session
    }

    override suspend fun resetDraft(session: String) = draftTransaction {
        if (dao.tracker() == null && dao.setting("setup-session")?.value == session) {
            dao.deleteSetting("setup-draft")
        }
    }

    override suspend fun saveDraft(draft: SetupDraft, session: String): Boolean =
        draftTransaction {
            // Deletion and a newer setup session invalidate queued writes from an older screen.
            if (dao.tracker() != null || dao.setting("setup-session")?.value != session) false
            else {
                dao.putSetting(SettingEntity("setup-draft", encodeDraft(draft)))
                true
            }
        }

    private suspend fun <T> draftTransaction(write: suspend () -> T): T =
        try {
            db.withWriteTransaction { write() }
        } catch (failure: SQLiteException) {
            throw SetupStorageException(failure)
        }

    override suspend fun complete(command: SetupCommand): SetupResult {
        if (command.displayName.isBlank() || dev.daymark.reporting.exceedsReportTextLimit(command.displayName) || command.placeName.isBlank() || command.openingBalance < 0) return SetupResult.Invalid
        val now = clock.now()
        val id = Uuid.random().toString()
        val first = command.schedule.firstSetupArrival(now, command.category, command.place)
            ?: return SetupResult.Invalid
        val date = now.toLocalDateTime(TimeZone.of(command.schedule.zoneId)).date
        return try {
            db.withWriteTransaction {
                if (dao.tracker() != null) return@withWriteTransaction SetupResult.Invalid
                dao.insertTracker(
                    TrackerEntity(
                        id,
                        command.displayName,
                        now.toString(),
                        first.toString(),
                        if (command.category == TrackerCategory.WORK) command.openingBalance else 0,
                        automaticEnabled = false
                    )
                )
                dao.putVersion(
                    PlanVersionEntity.fromSnapshot(
                        id, 1, date, now, command.category, command.schedule,
                        command.place.copy(version = 1), command.placeName
                    )
                )
                settings.update(displayName = command.displayName)
                dao.deleteSetting("setup-draft")
                dao.deleteSetting("setup-session")
                dao.markDirty(id, "OVERVIEW")
                dao.markDirty(id, "TIME_OFF")
                SetupResult.Completed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: androidx.sqlite.SQLiteException) {
            SetupResult.LocalSaveFailed
        } catch (_: IllegalStateException) {
            SetupResult.LocalSaveFailed
        }
    }
}

private fun readDraft(text: String): SetupDraftLoad = try {
    SetupDraftLoad.Loaded(decodeDraft(text))
} catch (_: NoSuchElementException) {
    SetupDraftLoad.Unreadable
} catch (_: IllegalArgumentException) {
    SetupDraftLoad.Unreadable
} catch (_: IllegalStateException) {
    SetupDraftLoad.Unreadable
}

private fun encodeDraft(draft: SetupDraft): String = buildJsonObject {
    put("step", draft.step.name)
    put("displayName", draft.displayName); put(
    "placeName",
    draft.placeName
); put("openingBalanceText", draft.openingBalanceText)
    draft.category?.let { put("category", it.name) }
    draft.schedule?.let { put("schedule", encodeSchedule(it)) }
    draft.place?.let { place ->
        putJsonObject("place") {
            put("latitude", place.latitude); put("longitude", place.longitude); put(
            "radius",
            place.radiusMetres
        ); put("version", place.version)
        }
    }
}.toString()

private fun decodeDraft(text: String): SetupDraft {
    val json = Json.parseToJsonElement(text).jsonObject
    val place = json["place"]?.jsonObject?.let {
        RegisteredPlace(
            it.getValue("latitude").jsonPrimitive.double,
            it.getValue("longitude").jsonPrimitive.double,
            it.getValue("radius").jsonPrimitive.int,
            it.getValue("version").jsonPrimitive.long
        )
    }
    return SetupDraft(
        json["displayName"]?.jsonPrimitive?.content.orEmpty(),
        json["category"]?.jsonPrimitive?.content?.let(TrackerCategory::valueOf),
        json["placeName"]?.jsonPrimitive?.content.orEmpty(),
        place,
        json["schedule"]?.jsonPrimitive?.content?.let(::decodeSchedule),
        json["openingBalanceText"]?.jsonPrimitive?.content ?: "0",
        SetupStep.entries.firstOrNull { it.name == json["step"]?.jsonPrimitive?.content }
            ?: SetupStep.PROFILE
    )
}
