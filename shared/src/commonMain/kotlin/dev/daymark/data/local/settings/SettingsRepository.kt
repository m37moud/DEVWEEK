package dev.daymark.data.local.settings

import androidx.room3.withWriteTransaction
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.markDirty
import dev.daymark.models.TrackerSummary
import dev.daymark.models.settings.AppSettings
import dev.daymark.repository.SettingsDataSource
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SettingsRepository(private val db: DaymarkDatabase) : SettingsDataSource {
    private val dao get() = db.dao()
    override fun observe(): Flow<AppSettings> =
        combine(dao.observeSetting("ui-settings"), dao.observeTracker()) { row, tracker ->
            decode(row?.value).let { it.copy(displayName = tracker?.displayName ?: it.displayName) }
        }.distinctUntilChanged()

    override suspend fun update(
        language: LanguagePreference?,
        theme: ThemePreference?,
        displayName: String?,
        remindersEnabled: Boolean?
    ) = db.withWriteTransaction {
        require(
            displayName == null || (displayName.isNotBlank() && !dev.daymark.reporting.exceedsReportTextLimit(
                displayName
            ))
        )
        val before = decode(dao.setting("ui-settings")?.value)
        val after = before.copy(
            displayName = displayName ?: before.displayName,
            language = language ?: before.language,
            theme = theme ?: before.theme,
            remindersEnabled = remindersEnabled ?: before.remindersEnabled
        )
        if (after != before) dao.putSetting(SettingEntity("ui-settings", buildJsonObject {
            put("displayName", after.displayName); put(
            "language",
            after.language.name
        ); put("theme", after.theme.name); put("remindersEnabled", after.remindersEnabled)
        }.toString()))
        if (displayName != null) dao.tracker()?.takeIf { it.displayName != displayName }?.let {
            dao.putTracker(
                it.copy(
                    displayName = displayName,
                    changeRevision = it.changeRevision + 1
                )
            )
            dao.markDirty(it.id, "OVERVIEW")
        }
    }

    override fun observeTracker(): Flow<TrackerSummary?> = dao.observeTracker().map { tracker ->
        tracker?.let {
            TrackerSummary(
                it.id,
                it.displayName,
                it.changeRevision,
                it.automaticEnabled
            )
        }
    }

    override suspend fun trackerIdentity(): String? = dao.tracker()?.id

    override suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean) {
        db.withWriteTransaction {
            val tracker = dao.tracker()?.takeIf { it.id == expectedTrackerId }
                ?: return@withWriteTransaction
            if (tracker.automaticEnabled != enabled) {
                dao.putTracker(
                    tracker.copy(
                        automaticEnabled = enabled,
                        changeRevision = tracker.changeRevision + 1
                    )
                )
                dao.markDirty(tracker.id, "OVERVIEW")
            }
        }
    }

    internal suspend fun snapshot(): AppSettings = decode(dao.setting("ui-settings")?.value).let {
        it.copy(
            displayName = dao.tracker()?.displayName ?: it.displayName
        )
    }

    private fun decode(text: String?): AppSettings = text?.let {
        runCatching {
            val json = Json.parseToJsonElement(it).jsonObject
            AppSettings(
                json["displayName"]?.jsonPrimitive?.content.orEmpty(),
                LanguagePreference.valueOf(json.getValue("language").jsonPrimitive.content),
                ThemePreference.valueOf(json.getValue("theme").jsonPrimitive.content),
                json["remindersEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }.getOrNull()
    } ?: AppSettings()
}
