package dev.daymark.data.local.settings

import androidx.sqlite.SQLiteException
import dev.daymark.attendance.DEFAULT_AUTOMATIC_ATTEMPT_LIMIT
import dev.daymark.attendance.automaticAttemptLimits
import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.markDirty
import dev.daymark.models.TrackerSummary
import dev.daymark.models.settings.AppSettings
import dev.daymark.repository.SettingsDataSource
import dev.daymark.repository.SettingsStorageException
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SettingsRepository(
    private val profileDao: ProfileDao,
    private val dirtyProjectionDao: DirtyProjectionDao,
    private val transactions: DatabaseTransactions
) : SettingsDataSource {
    override fun observe(): Flow<AppSettings> =
        combine(
            profileDao.observeSetting(UI_SETTINGS_KEY),
            profileDao.observeTracker(),
            profileDao.observeSetting(ATTENDANCE_PERIOD_KEY)
        ) { row, tracker, period ->
            val settings = decodeAppSettings(row?.value)
            settings.copy(
                displayName = tracker?.displayName ?: settings.displayName,
                attendancePeriod = attendancePeriodSettings(period?.value)
            )
        }.distinctUntilChanged().catch { failure ->
            if (failure is SQLiteException) throw SettingsStorageException(failure)
            throw failure
        }

    override suspend fun update(
        language: LanguagePreference?,
        theme: ThemePreference?,
        displayName: String?,
        remindersEnabled: Boolean?,
        resultNotificationsEnabled: Boolean?,
        resultFailureSoundEnabled: Boolean?,
        automaticAttemptLimit: Int?
    ): Unit = try {
        transactions.withWriteTransaction {
            require(
                displayName == null || (displayName.isNotBlank() && !dev.daymark.reporting.exceedsReportTextLimit(
                    displayName
                ))
            )
            require(automaticAttemptLimit == null || automaticAttemptLimit in automaticAttemptLimits)
            val before = decodeAppSettings(profileDao.setting(UI_SETTINGS_KEY)?.value)
            val after = before.copy(
                displayName = displayName ?: before.displayName,
                language = language ?: before.language,
                theme = theme ?: before.theme,
                remindersEnabled = remindersEnabled ?: before.remindersEnabled,
                resultNotificationsEnabled = resultNotificationsEnabled
                    ?: before.resultNotificationsEnabled,
                resultFailureSoundEnabled = resultFailureSoundEnabled ?: before.resultFailureSoundEnabled,
                automaticAttemptLimit = automaticAttemptLimit ?: before.automaticAttemptLimit
            )
            if (after != before) profileDao.putSetting(
                SettingEntity(
                    UI_SETTINGS_KEY,
                    buildJsonObject {
                        put("displayName", after.displayName); put(
                        "language",
                        after.language.name
                    ); put("theme", after.theme.name); put(
                        "remindersEnabled",
                        after.remindersEnabled
                    ); put("resultNotificationsEnabled", after.resultNotificationsEnabled)
                        put("resultFailureSoundEnabled", after.resultFailureSoundEnabled)
                        put("automaticAttemptLimit", after.automaticAttemptLimit)
                    }.toString()
                )
            )
            if (displayName != null) profileDao.tracker()?.takeIf { it.displayName != displayName }
                ?.let {
                    profileDao.putTracker(
                        it.copy(
                            displayName = displayName,
                            changeRevision = it.changeRevision + 1
                        )
                    )
                    dirtyProjectionDao.markDirty(it.id, "OVERVIEW")
                }
        }
    } catch (failure: SQLiteException) {
        throw SettingsStorageException(failure)
    }

    override fun observeTracker(): Flow<TrackerSummary?> =
        profileDao.observeTracker().map { tracker ->
            tracker?.let {
                TrackerSummary(
                    it.id,
                    it.displayName,
                    it.changeRevision,
                    it.automaticEnabled
                )
            }
        }

    override suspend fun trackerIdentity(): String? = profileDao.tracker()?.id

    override suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean) {
        transactions.withWriteTransaction {
            val tracker = profileDao.tracker()?.takeIf { it.id == expectedTrackerId }
                ?: return@withWriteTransaction
            if (tracker.automaticEnabled != enabled) {
                profileDao.putTracker(
                    tracker.copy(
                        automaticEnabled = enabled,
                        changeRevision = tracker.changeRevision + 1
                    )
                )
                if (!enabled) {
                    val generation = profileDao.automaticGeneration() + 1
                    profileDao.putSetting(SettingEntity(key = AUTOMATION_GENERATION_KEY, value = generation.toString()))
                }
                dirtyProjectionDao.markDirty(tracker.id, "OVERVIEW")
            }
        }
    }

    internal suspend fun snapshot(): AppSettings =
        decodeAppSettings(profileDao.setting(UI_SETTINGS_KEY)?.value).let {
            it.copy(
                displayName = profileDao.tracker()?.displayName ?: it.displayName,
                attendancePeriod = attendancePeriodSettings(profileDao.setting(ATTENDANCE_PERIOD_KEY)?.value)
            )
        }
}

internal fun decodeAppSettings(text: String?): AppSettings = text?.let {
    runCatching {
        val json = Json.parseToJsonElement(it).jsonObject
        AppSettings(
            json["displayName"]?.jsonPrimitive?.content.orEmpty(),
            LanguagePreference.valueOf(json.getValue("language").jsonPrimitive.content),
            ThemePreference.valueOf(json.getValue("theme").jsonPrimitive.content),
            json["remindersEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            json["resultNotificationsEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            resultFailureSoundEnabled = json["resultFailureSoundEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            automaticAttemptLimit = json["automaticAttemptLimit"]?.jsonPrimitive?.intOrNull
                ?.takeIf { it in automaticAttemptLimits } ?: DEFAULT_AUTOMATIC_ATTEMPT_LIMIT
        )
    }.getOrNull()
} ?: AppSettings()

internal const val UI_SETTINGS_KEY = "ui-settings"
private const val AUTOMATION_GENERATION_KEY = "automation-generation"

internal suspend fun ProfileDao.automaticGeneration(): Long =
    setting(AUTOMATION_GENERATION_KEY)?.value?.toLongOrNull() ?: 0L
