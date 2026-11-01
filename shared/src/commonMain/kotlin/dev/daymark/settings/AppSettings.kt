package dev.daymark.settings

import androidx.room3.*
import dev.daymark.data.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class AppSettings(val displayName: String = "", val language: LanguagePreference = LanguagePreference.SYSTEM, val theme: ThemePreference = ThemePreference.SYSTEM, val remindersEnabled: Boolean = false) {
    override fun toString() = "AppSettings(displayName=<redacted>, language=$language, theme=$theme)"
}
class SettingsRepository(private val db: DaymarkDatabase) {
    private val dao get() = db.dao()
    fun observe(): Flow<AppSettings> = combine(dao.observeSetting("ui-settings"), dao.observeTracker()) { row, tracker ->
        decode(row?.value).let { it.copy(displayName = tracker?.displayName ?: it.displayName) }
    }.distinctUntilChanged()
    suspend fun update(language: LanguagePreference? = null, theme: ThemePreference? = null, displayName: String? = null, remindersEnabled: Boolean? = null) = db.withWriteTransaction {
        require(displayName == null || (displayName.isNotBlank() && !dev.daymark.reporting.exceedsReportTextLimit(displayName)))
        val before = decode(dao.setting("ui-settings")?.value)
        val after = before.copy(displayName = displayName ?: before.displayName, language = language ?: before.language, theme = theme ?: before.theme, remindersEnabled = remindersEnabled ?: before.remindersEnabled)
        if (after != before) dao.putSetting(SettingEntity("ui-settings", buildJsonObject {
            put("displayName", after.displayName); put("language", after.language.name); put("theme", after.theme.name); put("remindersEnabled", after.remindersEnabled)
        }.toString()))
        if (displayName != null) dao.tracker()?.takeIf { it.displayName != displayName }?.let {
            dao.putTracker(it.copy(displayName = displayName, changeRevision = it.changeRevision + 1))
            dao.markDirty(it.id, "OVERVIEW")
        }
    }
    internal suspend fun snapshot(): AppSettings = decode(dao.setting("ui-settings")?.value).let { it.copy(displayName = dao.tracker()?.displayName ?: it.displayName) }

    private fun decode(text: String?): AppSettings = text?.let { runCatching {
        val json = Json.parseToJsonElement(it).jsonObject
        AppSettings(json["displayName"]?.jsonPrimitive?.content.orEmpty(),
            LanguagePreference.valueOf(json.getValue("language").jsonPrimitive.content), ThemePreference.valueOf(json.getValue("theme").jsonPrimitive.content), json["remindersEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
    }.getOrNull() } ?: AppSettings()
}
