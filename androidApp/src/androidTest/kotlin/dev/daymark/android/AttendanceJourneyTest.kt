package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openAndroidDatabase
import dev.daymark.data.local.setup.SetupService
import dev.daymark.location.*
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.setup.SetupCommand
import dev.daymark.models.setup.SetupResult
import dev.daymark.plan.*
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.features.history.HistoryScreen
import dev.daymark.presentation.features.history.HistoryViewModel
import dev.daymark.presentation.features.today.TodayScreen
import dev.daymark.presentation.features.today.TodayViewModel
import dev.daymark.settings.*
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.settings.applyAppLocale
import dev.daymark.ui.*
import dev.daymark.ui.theme.DaymarkTheme
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttendanceJourneyTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "attendance-ui-${UUID.randomUUID()}.db"
    private lateinit var db: DaymarkDatabase
    private var graph: AppGraph? = null
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-27T09:18:00Z")
    }
    private val permissions = object : LocationPermissionPort {
        override fun current() = LocationCapabilities(true, false, true, true)
        override suspend fun requestForeground() = current()
        override suspend fun requestBackground(): LocationCapabilities =
            error("No background request")

        override fun openSystemSettings() = Unit
    }
    private val location = object : LocationGateway {
        override suspend fun requestFix(budget: Duration) =
            LocationResult.Fix(0.0, 0.0, 10.0, clock.now(), clock.now(), Duration.ZERO)
    }

    @After
    fun close() {
        if (::db.isInitialized) {
            graph?.close(); db.close(); context.deleteDatabase(name)
        }
    }

    @Test
    fun manual_late_and_mixed_direction_note_survive_reopen() {
        db = openAndroidDatabase(context, name)
        runBlocking {
            val schedule =
                WeeklySchedule(DayOfWeek.entries.toSet(), LocalTime(9, 0), emptyMap(), 5, "UTC")
            Assert.assertEquals(
                SetupResult.Completed, SetupService(db, object : Clock {
                    override fun now() = Instant.parse("2026-10-27T08:00:00Z")
                }).complete(
                    SetupCommand(
                        "أحمد / Ahmed",
                        TrackerCategory.WORK,
                        "Office",
                        RegisteredPlace(0.0, 0.0, 100, 1),
                        schedule,
                        10
                    )
                )
            )
        }
        val repo = RoomAttendanceRepository(db, clock)
        val container = AppGraph(org.koin.dsl.module {
            single<DaymarkDatabase> { db }
            single<Clock> { clock }
            single<LocationGateway> { location }
            single<dev.daymark.location.PlaceGeocoder> { dev.daymark.location.PlaceGeocoder { null } }
            single<LocationPermissionPort> { permissions }
            single<dev.daymark.auth.NativeGoogleAuth> {
                object : dev.daymark.auth.NativeGoogleAuth {
                    override fun authorize(
                        interactive: Boolean,
                        completion: (dev.daymark.auth.NativeAuthReply) -> Unit
                    ): Unit = error("No authorization expected")

                    override fun forget(completion: (Boolean) -> Unit) = completion(true)
                }
            }
            single<dev.daymark.reminders.ReminderPort> {
                object : dev.daymark.reminders.ReminderPort {
                    override fun requestPermission(completion: (Boolean) -> Unit) =
                        completion(false)

                    override fun replace(remindersJson: String, completion: (Boolean) -> Unit) =
                        completion(false)

                    override fun cancelTracker(trackerId: String, completion: (Boolean) -> Unit) =
                        completion(true)
                }
            }
            single<dev.daymark.widgets.WidgetSink> {
                object : dev.daymark.widgets.WidgetSink {
                    override fun publish(json: String) = false
                    override fun clear() = true
                }
            }
        })
        graph = container
        val today = TodayViewModel(container.repository, permissions, clock)
        var history by mutableStateOf(false)
        var shown by mutableStateOf(true)
        val historyModel = HistoryViewModel(container.repository, LocalDate(2026, 10, 27))
        applyAppLocale(AppLanguage.EN)
        compose.setContent {
            WithPlatformLocale(AppLanguage.EN) {
                DaymarkTheme(ThemePreference.LIGHT) {
                    if (shown) if (history) HistoryScreen(
                        historyModel,
                        onBack = { history = false },
                        onEditTimeOff = {})
                    else TodayScreen(today, "أحمد / Ahmed", onHistory = { history = true })
                }
            }
        }
        compose.waitUntil(10_000) { today.uiState.value.canCheck }
        compose.onNodeWithTag("today-check").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { !today.uiState.value.busy }
        compose.onNodeWithTag("today-status").assertTextEquals("Late")
        compose.onNodeWithText("Location checked manually").assertExists()
        compose.onNodeWithTag("today-history").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("history-root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("history-root")
            .performScrollToNode(hasTestTag("history-record-2026-10-27"))
        compose.onNodeWithTag("history-record-2026-10-27").performClick()
        compose.onNodeWithTag("correction-note").performTextReplacement("اجتماع / Meeting =1+2")
        compose.onNodeWithTag("correction-save").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("correction-note").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Manually corrected").assertExists()
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        db.close(); db = openAndroidDatabase(context, name)
        runBlocking {
            val outcome = RoomAttendanceRepository(db, clock).loadCurrentDay(clock.now())!!
            Assert.assertEquals(AttendanceStatus.LATE, outcome.evidence?.status)
            Assert.assertEquals("اجتماع / Meeting =1+2", outcome.evidence?.note)
            Assert.assertEquals(AttendanceSource.MANUAL_CORRECTION, outcome.evidence?.source)
        }
    }
}
