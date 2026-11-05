package dev.daymark.app

import androidx.lifecycle.ViewModelStore
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.TestClock
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.presentation.features.history.HistoryViewModel
import dev.daymark.presentation.features.plan.PlanViewModel
import dev.daymark.presentation.features.settings.SettingsViewModel
import dev.daymark.presentation.features.setup.SetupViewModel
import dev.daymark.presentation.features.today.TodayViewModel
import dev.daymark.reminders.ReminderPort
import dev.daymark.repository.Repository
import dev.daymark.widgets.WidgetSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphTest {
    @Test
    fun graphs_isolate_singletons_and_resolve_view_models_with_route_parameters() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val folder = Files.createTempDirectory("daymark-di-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val clock = TestClock()
        val graph = AppGraph(platformModule(db, clock))
        val other = AppGraph(platformModule(db, TestClock()))
        val models = ViewModelStore()
        try {
            assertSame(db, graph.db)
            assertSame(graph.repository, graph.koin.get<Repository>())
            assertSame(graph.attendance, graph.repository.checks)
            assertSame(clock, graph.clock)
            assertSame(graph.attendance, graph.koin.get<AttendanceService>())
            assertNotSame(graph.clock, other.clock)
            assertNotSame(graph.repository, other.repository)
            other.close()
            assertSame(graph.attendance, graph.koin.get<AttendanceService>())

            // Android workers must resolve their services before an Activity supplies permissions.
            graph.koin.declare<LocationPermissionPort>(Permissions)
            assertSame(Permissions, graph.permissions)
            val today = graph.koin.get<TodayViewModel>()
            models.put("today", today)
            assertFalse(today.uiState.value.active)
            val nextToday = graph.koin.get<TodayViewModel>()
            models.put("next-today", nextToday)
            assertNotSame(today, nextToday)
            for (modelClass in listOf(
                AppViewModel::class,
                SetupViewModel::class,
                PlanViewModel::class,
                SettingsViewModel::class
            )) {
                models.put(modelClass.simpleName!!, graph.koin.get(modelClass))
            }
            val month = LocalDate(2026, 11, 1)
            val history = graph.koin.get<HistoryViewModel> {
                parametersOf(month)
            }
            models.put("history", history)
            assertEquals(month.year, history.uiState.value.year)
            assertEquals(11, history.uiState.value.month)
        } finally {
            models.clear()
            graph.close()
            other.close()
            db.close()
            folder.toFile().deleteRecursively()
            Dispatchers.resetMain()
        }
    }

    private fun platformModule(db: DaymarkDatabase, clock: Clock) = module {
        single<HttpClient> { HttpClient(MockEngine { error("No network during DI resolution") }) }
        single<DaymarkDatabase> { db }
        single<Clock> { clock }
        single<LocationGateway> {
            object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult =
                    error("No GPS during DI resolution")
            }
        }
        single<NativeGoogleAuth> {
            object : NativeGoogleAuth {
                override fun authorize(
                    interactive: Boolean,
                    completion: (NativeAuthReply) -> Unit
                ) =
                    error("No Google authorization during DI resolution")

                override fun forget(completion: (Boolean) -> Unit) = completion(true)
            }
        }
        single<ReminderPort> {
            object : ReminderPort {
                override fun requestPermission(completion: (Boolean) -> Unit) = completion(false)
                override fun replace(remindersJson: String, completion: (Boolean) -> Unit) =
                    completion(false)

                override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) =
                    completion(true)
            }
        }
        single<WidgetSink> {
            object : WidgetSink {
                override fun publish(json: String) = false
                override fun clear() = true
            }
        }
    }

    private object Permissions : LocationPermissionPort {
        override fun current() = LocationCapabilities(false, false, false, false)
        override suspend fun requestForeground() = current()
        override suspend fun requestBackground() = current()
        override fun openSystemSettings() = Unit
    }
}
