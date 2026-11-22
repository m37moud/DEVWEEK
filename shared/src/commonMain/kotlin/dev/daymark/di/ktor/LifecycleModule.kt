package dev.daymark.di.ktor

import dev.daymark.auth.GoogleAccess
import dev.daymark.data.local.application.ConfigurationCoordinator
import dev.daymark.data.local.application.DataLifecycleService
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.reporting.ReportingRuntime
import dev.daymark.notifications.AttendanceResultNotifier
import dev.daymark.repository.DataLifecycleDataSource
import dev.daymark.widgets.WidgetPublisher
import org.koin.dsl.module

internal fun lifecycleModule(
    refreshSchedule: suspend () -> Boolean,
    stopExternalWork: suspend () -> Unit
) = module {
    single {
        ConfigurationCoordinator(
            get(), get(), get(), onScheduleRefresh = refreshSchedule,
            onWidgetRefresh = { get<WidgetPublisher>().refresh(); true }, reader = get()
        )
    }
    single {
        DataLifecycleService(
            get(), get(),
            get(),
            get<ReportingRuntime>().store,
            stopExternal = {
                get<ReportingRuntime>().lifecycle.stop()
                get<ProfileDao>().tracker()
                    ?.let { get<ConfigurationCoordinator>().cancelTracker(it.id) }
                get<AttendanceResultNotifier>().setEnabled(false)
                stopExternalWork()
            },
            forgetCredentials = get<GoogleAccess>()::forget,
            clearWidget = { get<WidgetPublisher>().clear(); true },
            settings = get()
        )
    }
    single<DataLifecycleDataSource> { get<DataLifecycleService>() }
}
