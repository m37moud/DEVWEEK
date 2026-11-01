package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.settings.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocaleJourneyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun arabic_switch_preserves_day_identity_and_persisted_theme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "locale-journey-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context,name)
        var shown by mutableStateOf(true)
        try {
            runBlocking { fixture.graph.settings.update(language = LanguagePreference.EN); fixture.graph.repository.reconcile(fixture.clock.now()) }
            val before = runBlocking { fixture.graph.repository.loadCurrentDay(fixture.clock.now())!! }
            compose.setContent { if (shown) App(fixture.graph) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-settings").performClick()
            compose.onNodeWithTag("settings-language-ar").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("الإعدادات").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("settings-theme-dark").performScrollTo().performClick()
            runBlocking {
                fixture.graph.settings.observe().first { it.theme == ThemePreference.DARK && it.language == LanguagePreference.AR }
                val after = fixture.graph.repository.loadCurrentDay(fixture.clock.now())!!
                Assert.assertEquals(before.plan.key,after.plan.key)
                Assert.assertEquals(before.evidence,after.evidence)
            }
            compose.onNodeWithTag("nav-today").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("today-status").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("today-status").assertTextEquals("حان وقت تسجيل الحضور")
        } finally {
            compose.runOnIdle { shown = false }; compose.waitForIdle()
            fixture.db.close(); context.deleteDatabase(name)
        }
    }
}
