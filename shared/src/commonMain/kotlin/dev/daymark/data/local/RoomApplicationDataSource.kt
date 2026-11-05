package dev.daymark.data.local

import dev.daymark.app.HostPlatform
import dev.daymark.attendance.AttendanceRepository
import dev.daymark.data.local.application.ConfigurationCoordinator
import dev.daymark.data.local.reporting.ReportingRuntime
import dev.daymark.models.BootstrapSnapshot
import dev.daymark.models.ConfigurationHealth
import dev.daymark.models.settings.AppSettings
import dev.daymark.plan.PlanReader
import dev.daymark.reminders.ReminderCopy
import dev.daymark.repository.ApplicationDataSource
import dev.daymark.repository.SettingsDataSource
import dev.daymark.settings.AppLanguage
import kotlin.time.Clock
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class RoomApplicationDataSource(
    private val settings: SettingsDataSource,
    private val attendance: AttendanceRepository,
    private val plans: PlanReader,
    private val configuration: ConfigurationCoordinator,
    private val reporting: ReportingRuntime,
    private val clock: Clock,
    private val platform: HostPlatform
) : ApplicationDataSource {
    override fun observeBootstrap() =
        combine(settings.observe(), settings.observeTracker(), ::BootstrapSnapshot)

    override fun setActive(active: Boolean) = reporting.lifecycle.onActive(active)

    override suspend fun refresh(
        settings: AppSettings,
        language: AppLanguage,
        copy: ReminderCopy
    ): ConfigurationHealth {
        attendance.reconcile(clock.now())
        return configuration.refresh(settings, language, copy, platform)
    }

    override suspend fun historyDate() = plans.context()?.let {
        clock.now().toLocalDateTime(TimeZone.of(it.zoneId)).date
    }
}
