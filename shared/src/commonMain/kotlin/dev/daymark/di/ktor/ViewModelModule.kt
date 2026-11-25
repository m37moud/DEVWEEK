package dev.daymark.di.ktor

import dev.daymark.history.HistoryMetric
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.presentation.features.history.HistoryViewModel
import dev.daymark.presentation.features.historydetail.HistoryDetailViewModel
import dev.daymark.presentation.features.plan.PlanViewModel
import dev.daymark.presentation.features.settings.AttendanceSettingsViewModel
import dev.daymark.presentation.features.settings.SettingsViewModel
import dev.daymark.presentation.features.setup.SetupViewModel
import dev.daymark.presentation.features.today.TodayViewModel
import dev.daymark.repository.Repository
import dev.daymark.widgets.WidgetSetupPort
import dev.daymark.widgets.createWidgetSetupPort
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock

internal fun viewModelModule() = module {
    viewModel { AppViewModel(get()) }
    viewModel { SetupViewModel(
        repository = get(), location = get(), permissions = get(), clock = get(),
        resultNotifications = get()
    ) }
    viewModel { TodayViewModel(get(), get(), get(), initiallyActive = false) }
    viewModel { PlanViewModel(get(), get()) }
    viewModel { AttendanceSettingsViewModel(get(), get(), get(), get()) }
    factory<WidgetSetupPort> { createWidgetSetupPort() }
    viewModel {
        SettingsViewModel(
            get(),
            get(),
            get(),
            widgetSetup = get(),
            resultNotifications = get()
        )
    }
    viewModel { (metric: HistoryMetric, initialDate: LocalDate) ->
        val repository = get<Repository>()
        HistoryDetailViewModel(
            repository, metric, initialDate,
            periodSettings = repository.settings.observe().map { it.attendancePeriod }
                .distinctUntilChanged()
        )
    }
    viewModel { (initialDate: LocalDate) ->
        val repository = get<Repository>()
        val clock = get<Clock>()
        HistoryViewModel(
            repository, initialDate, clock = clock,
            periodSettings = repository.settings.observe().map { it.attendancePeriod }
                .distinctUntilChanged(),
            today = {
                repository.attendance.loadCurrentDay(clock.now())?.plan?.key?.date ?: initialDate
            },
            refreshBoundary = {
                repository.attendance.loadCurrentDay(clock.now())?.plan?.let { day ->
                    day.key.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.of(day.zoneId))
                }
            }
        )
    }
}
