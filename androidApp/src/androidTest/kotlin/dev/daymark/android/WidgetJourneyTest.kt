package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.*
import dev.daymark.attendance.*
import dev.daymark.settings.*
import dev.daymark.widgets.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class WidgetJourneyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun stale_snapshot_resolves_today_before_running_normal_check() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString(); val name = "widget-journey-$id.db"
        val folder = File(context.noBackupFilesDir,"widget-test-$id").apply { mkdirs() }
        val file = File(folder,"snapshot.json")
        val sink = AndroidWidgetPublisher(context,file,onChanged = {})
        val fixture = AttendanceTestApp(context,name,sink)
        var shown by mutableStateOf(true)
        try {
            runBlocking {
                fixture.graph.settings.update(language = LanguagePreference.AR)
                WidgetPublisher(fixture.db,fixture.graph.repository,sink,fixture.clock).refresh()
            }
            val yesterday = AndroidWidgetPublisher.readFile(file)!!
            Assert.assertEquals("ar",yesterday.effectiveLocale)
            Assert.assertEquals("حان وقت تسجيل الحضور",yesterday.displayStatus)
            Assert.assertEquals("2026-10-27",yesterday.attendanceDate)
            Assert.assertEquals(0,fixture.locationRequests)
            fixture.now = Instant.parse("2026-10-28T09:18:00Z")
            QuickCheckIntents.requestRoute(yesterday.quickCheckRoute)
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("متأخر").fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                val current = fixture.graph.repository.loadCurrentDay(fixture.clock.now())!!
                Assert.assertEquals(LocalDate(2026,10,28),current.plan.key.date)
                Assert.assertEquals(AttendanceSource.MANUAL_GPS,current.evidence?.source)
                Assert.assertNull(fixture.graph.repository.loadDay(DayKey(current.plan.key.trackerId,LocalDate(2026,10,27)))?.evidence)
            }
            Assert.assertEquals(1,fixture.locationRequests)
            compose.waitUntil(10_000) { AndroidWidgetPublisher.readFile(file)?.statusCode == "LATE" }
            compose.onNodeWithTag("nav-settings").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-language-en").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("settings-language-en").performScrollTo().performClick()
            compose.waitUntil(10_000) { AndroidWidgetPublisher.readFile(file)?.effectiveLocale == "en" }
            Assert.assertEquals("2026-10-28",AndroidWidgetPublisher.readFile(file)!!.attendanceDate)
            Assert.assertEquals("Late",AndroidWidgetPublisher.readFile(file)!!.displayStatus)
            Assert.assertEquals(1,fixture.locationRequests)
            val old = file.readText()
            Assert.assertFalse(sink.publish("{}"))
            Assert.assertEquals(old,file.readText())
            Assert.assertTrue(sink.publish(old.replaceFirst("{", "{\"accessToken\":\"do-not-store\",")))
            Assert.assertFalse(file.readText().contains("do-not-store"))
            Assert.assertTrue(sink.clear())
            Assert.assertNull(AndroidWidgetPublisher.readFile(file))
            val fallback = widgetLaunchIntent(context,null)
            Assert.assertEquals("daymark://quick-check",fallback.dataString)
            Assert.assertNull(parseQuickCheckRoute(fallback.dataString!!))
            QuickCheckIntents.requestRoute(fallback.dataString!!)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("today-root").fetchSemanticsNodes().isNotEmpty() }
            Assert.assertEquals(1,fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown = false }; compose.waitForIdle()
            fixture.db.close(); context.deleteDatabase(name); folder.deleteRecursively()
        }
    }
}
