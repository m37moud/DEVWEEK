package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.*
import dev.daymark.app.App
import dev.daymark.app.QuickCheckIntents
import dev.daymark.attendance.*
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.DayKey
import dev.daymark.settings.*
import dev.daymark.settings.LanguagePreference
import dev.daymark.widgets.*
import dev.daymark.widgets.WidgetPublisher
import dev.daymark.widgets.WidgetSink
import dev.daymark.widgets.decodeWidgetSnapshot
import dev.daymark.widgets.parseQuickCheckRoute
import java.io.File
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stale_snapshot_resolves_today_before_running_normal_check() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString();
        val name = "widget-journey-$id.db"
        val folder = File(context.noBackupFilesDir, "widget-test-$id").apply { mkdirs() }
        val file = File(folder, "snapshot.json")
        val sink = AndroidWidgetPublisher(context, file, onChanged = {})
        val publications = java.util.concurrent.CopyOnWriteArrayList<String>()
        val trackedSink = object : dev.daymark.widgets.WidgetSink {
            override fun publish(json: String): Boolean {
                val snapshot = dev.daymark.widgets.decodeWidgetSnapshot(json)
                val saved = sink.publish(json)
                publications += "${snapshot?.statusCode}:${snapshot?.attendanceDate}:${snapshot?.effectiveLocale}:$saved"
                return saved
            }

            override fun clear() = sink.clear()
        }
        val fixture = AttendanceTestApp(context, name, trackedSink)
        var shown by mutableStateOf(true)
        try {
            runBlocking {
                fixture.graph.settings.update(language = LanguagePreference.AR)
                WidgetPublisher(
                    fixture.db,
                    fixture.graph.repository.attendance,
                    sink,
                    fixture.clock
                ).refresh()
            }
            val yesterday = AndroidWidgetPublisher.readFile(file)!!
            Assert.assertEquals("ar", yesterday.effectiveLocale)
            Assert.assertEquals("حان وقت تسجيل الحضور", yesterday.displayStatus)
            Assert.assertEquals("2026-10-27", yesterday.attendanceDate)
            Assert.assertEquals(0, fixture.locationRequests)
            fixture.now = Instant.parse("2026-10-28T09:18:00Z")
            QuickCheckIntents.requestRoute(yesterday.quickCheckRoute)
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("متأخر").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(15_000) {
                runBlocking { fixture.graph.repository.attendance.loadCurrentDay(fixture.clock.now()) }
                    ?.evidence?.source == AttendanceSource.MANUAL_GPS
            }
            runBlocking {
                val current =
                    fixture.graph.repository.attendance.loadCurrentDay(fixture.clock.now())!!
                Assert.assertEquals(LocalDate(2026, 10, 28), current.plan.key.date)
                Assert.assertEquals(AttendanceSource.MANUAL_GPS, current.evidence?.source)
                Assert.assertNull(
                    fixture.graph.repository.attendance.loadDay(
                        DayKey(
                            current.plan.key.trackerId,
                            LocalDate(2026, 10, 27)
                        )
                    )?.evidence
                )
            }
            Assert.assertEquals(1, fixture.locationRequests)
            try {
                compose.waitUntil(10_000) { AndroidWidgetPublisher.readFile(file)?.statusCode == "LATE" }
            } catch (failure: Exception) {
                throw AssertionError(
                    "Widget cache status=${AndroidWidgetPublisher.readFile(file)?.statusCode}; publications=$publications; trackerRevision=${
                        runBlocking {
                            fixture.db.dao().tracker()?.changeRevision
                        }
                    }", failure
                )
            }
            compose.onNodeWithTag("nav-settings").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("settings-language-en").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("settings-language-en").performScrollTo().performClick()
            try {
                compose.waitUntil(10_000) { AndroidWidgetPublisher.readFile(file)?.effectiveLocale == "en" }
            } catch (failure: Exception) {
                throw AssertionError(
                    "Expected English widget; publications=$publications; current=${
                        AndroidWidgetPublisher.readFile(
                            file
                        )?.effectiveLocale
                    }", failure
                )
            }
            Assert.assertEquals(
                "2026-10-28",
                AndroidWidgetPublisher.readFile(file)!!.attendanceDate
            )
            Assert.assertEquals("Late", AndroidWidgetPublisher.readFile(file)!!.displayStatus)
            Assert.assertEquals(1, fixture.locationRequests)
            val old = file.readText()
            Assert.assertFalse(sink.publish("{}"))
            Assert.assertEquals(old, file.readText())
            Assert.assertTrue(
                sink.publish(
                    old.replaceFirst(
                        "{",
                        "{\"accessToken\":\"do-not-store\","
                    )
                )
            )
            Assert.assertFalse(file.readText().contains("do-not-store"))
            Assert.assertTrue(sink.clear())
            Assert.assertNull(AndroidWidgetPublisher.readFile(file))
            val fallback = widgetLaunchIntent(context, null)
            Assert.assertEquals("daymark://quick-check", fallback.dataString)
            Assert.assertNull(parseQuickCheckRoute(fallback.dataString!!))
            QuickCheckIntents.requestRoute(fallback.dataString!!)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("today-root").fetchSemanticsNodes().isNotEmpty()
            }
            Assert.assertEquals(1, fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown = false }; compose.waitForIdle()
            fixture.db.close(); context.deleteDatabase(name); folder.deleteRecursively()
        }
    }
}
