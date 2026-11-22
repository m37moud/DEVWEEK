package dev.daymark.app

import androidx.lifecycle.ViewModelStore
import dev.daymark.attendance.AttendanceService
import dev.daymark.attendance.TestClock
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.dao.LocalDataDeletionDao
import dev.daymark.data.local.dao.PlaceAddressDao
import dev.daymark.data.local.dao.ReportDao
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.history.HistoryMetric
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.presentation.features.history.HistoryViewModel
import dev.daymark.presentation.features.historydetail.HistoryDetailViewModel
import dev.daymark.presentation.features.plan.PlanViewModel
import dev.daymark.presentation.features.settings.AttendanceSettingsViewModel
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
import kotlin.test.assertFailsWith
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
        val otherDb = openJvmDatabase(folder.resolve("other.db").toString())
        val clock = TestClock()
        val graph = AppGraph(platformModule(db, clock))
        val other = AppGraph(platformModule(otherDb, TestClock()))
        val models = ViewModelStore()
        try {
            val graphDaos = listOf(
                db.profileDao() to graph.koin.get<ProfileDao>(),
                db.attendanceDao() to graph.koin.get<AttendanceDao>(),
                db.planningDao() to graph.koin.get<PlanningDao>(),
                db.dirtyProjectionDao() to graph.koin.get<DirtyProjectionDao>(),
                db.reportDao() to graph.koin.get<ReportDao>(),
                db.placeAddressDao() to graph.koin.get<PlaceAddressDao>(),
                db.localDataDeletionDao() to graph.koin.get<LocalDataDeletionDao>()
            )
            val otherDaos = listOf(
                otherDb.profileDao() to other.koin.get<ProfileDao>(),
                otherDb.attendanceDao() to other.koin.get<AttendanceDao>(),
                otherDb.planningDao() to other.koin.get<PlanningDao>(),
                otherDb.dirtyProjectionDao() to other.koin.get<DirtyProjectionDao>(),
                otherDb.reportDao() to other.koin.get<ReportDao>(),
                otherDb.placeAddressDao() to other.koin.get<PlaceAddressDao>(),
                otherDb.localDataDeletionDao() to other.koin.get<LocalDataDeletionDao>()
            )
            graphDaos.zip(otherDaos).forEach { (first, second) ->
                assertSame(first.first, first.second)
                assertSame(second.first, second.second)
                assertNotSame(first.second, second.second)
            }
            val dao = graph.koin.get<ProfileDao>()
            val otherDao = other.koin.get<ProfileDao>()
            val transactions = graph.koin.get<DatabaseTransactions>()
            val otherTransactions = other.koin.get<DatabaseTransactions>()
            assertSame(db.profileDao(), dao)
            assertSame(otherDb.profileDao(), otherDao)
            assertNotSame(dao, otherDao)
            assertSame(transactions, graph.koin.get<DatabaseTransactions>())
            assertNotSame(transactions, otherTransactions)
            transactions.withWriteTransaction { dao.putSetting(SettingEntity("graph", "first")) }
            otherTransactions.withWriteTransaction {
                otherDao.putSetting(SettingEntity("graph", "second"))
            }
            assertEquals("first", db.profileDao().setting("graph")?.value)
            assertEquals("second", otherDb.profileDao().setting("graph")?.value)
            for ((graphDao, graphTransactions) in listOf(
                dao to transactions,
                otherDao to otherTransactions
            )) {
                assertFailsWith<IllegalStateException> {
                    graphTransactions.withWriteTransaction {
                        graphDao.putSetting(SettingEntity("rolled-back", "pending"))
                        error("abort_graph_transaction")
                    }
                }
                assertEquals(null, graphDao.setting("rolled-back"))
            }
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
                AttendanceSettingsViewModel::class,
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

            val historyDetail = graph.koin.get<HistoryDetailViewModel> {
                parametersOf(HistoryMetric.LATE, month)
            }
            models.put("history-detail", historyDetail)
            val detailState = historyDetail.uiState.value
            assertEquals(HistoryMetric.LATE, detailState.metric)
            assertEquals(month, detailState.from)
            assertEquals(LocalDate(2026, 11, 30), detailState.through)
        } finally {
            models.clear()
            graph.close()
            other.close()
            db.close()
            otherDb.close()
            folder.toFile().deleteRecursively()
            Dispatchers.resetMain()
        }
    }

    private fun platformModule(db: DaymarkDatabase, clock: Clock) = module {
        single<dev.daymark.location.PlaceGeocoder> { dev.daymark.location.PlaceGeocoder { null } }
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
