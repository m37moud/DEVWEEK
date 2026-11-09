package dev.daymark.repository

import dev.daymark.app.*
import dev.daymark.app.SetupDraftLoad
import dev.daymark.models.BootstrapSnapshot
import dev.daymark.models.ConfigurationHealth
import dev.daymark.models.DeletionResult
import dev.daymark.models.TodayDashboard
import dev.daymark.models.TrackerSummary
import dev.daymark.models.attendance.CheckOutcome
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.TimeOffEntry
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.models.reporting.ConnectionResult
import dev.daymark.models.reporting.ReportHealth
import dev.daymark.models.settings.AppSettings
import dev.daymark.models.setup.SetupCommand
import dev.daymark.models.setup.SetupDraft
import dev.daymark.models.setup.SetupResult
import dev.daymark.plan.*
import dev.daymark.reminders.ReminderCopy
import dev.daymark.settings.*
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

interface AttendanceChecks {
    suspend fun manualCheck(): CheckOutcome
}

interface PlanDataSource {
    fun observePlan(): Flow<PlanState?>
    suspend fun previewChange(command: PlanChange): TimeOffPreview
    suspend fun applyChange(command: PlanChange, expectedRevision: Long): PlanMutationResult
}

interface TimeOffDataSource {
    fun observeEntries(): Flow<List<TimeOffEntry>>
    fun observeAvailableBalance(): Flow<Int?>
    suspend fun preview(command: TimeOffCommand): TimeOffPreview
    suspend fun apply(command: TimeOffCommand, expectedRevision: Long): PlanMutationResult
    suspend fun setAvailableBalance(targetDays: Int, note: String?): PlanMutationResult
}

/** Draft storage failed before the requested operation could complete. */
class SetupStorageException(cause: Throwable) :
    RuntimeException("Setup draft storage failed", cause)

interface SetupDataSource {
    fun observeDraft(): Flow<SetupDraftLoad>
    suspend fun startDraftSession(): String
    suspend fun resetDraft(session: String)
    suspend fun saveDraft(draft: SetupDraft, session: String): Boolean
    suspend fun complete(command: SetupCommand): SetupResult
}

class SettingsStorageException(cause: Throwable) :
    RuntimeException("Settings storage failed", cause)

interface SettingsDataSource {
    fun observe(): Flow<AppSettings>
    fun observeTracker(): Flow<TrackerSummary?>
    suspend fun trackerIdentity(): String?
    suspend fun update(
        language: LanguagePreference? = null, theme: ThemePreference? = null,
        displayName: String? = null, remindersEnabled: Boolean? = null
    )

    suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean)
}

interface DashboardDataSource {
    fun observe(): Flow<TodayDashboard>
}

interface ReportingDataSource {
    val health: StateFlow<ReportHealth>
    suspend fun connect(): ConnectionResult
    suspend fun reconnect(): ConnectionResult
    suspend fun selectReport(reportId: String): ConnectionResult
    suspend fun rebuildReport(): ConnectionResult
    suspend fun createReplacementReport(): ConnectionResult
    suspend fun disconnect()
    suspend fun refreshConnection(result: ConnectionResult?)
    fun retryReport()
}

interface DataLifecycleDataSource {
    val deleting: StateFlow<Boolean>
    suspend fun deleteLocalData(): DeletionResult
}

interface ApplicationDataSource {
    fun observeBootstrap(): Flow<BootstrapSnapshot>
    fun setActive(active: Boolean)
    suspend fun refresh(
        settings: AppSettings,
        language: AppLanguage,
        copy: ReminderCopy
    ): ConfigurationHealth

    suspend fun historyDate(): LocalDate?
}
