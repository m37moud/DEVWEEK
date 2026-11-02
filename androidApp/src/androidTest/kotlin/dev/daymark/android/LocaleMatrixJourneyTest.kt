package dev.daymark.android

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.settings.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocaleMatrixJourneyTest {
    @get:Rule val compose=createComposeRule()
    @Test fun english_arabic_light_dark_core_actions_remain_reachable_at_two_hundred_percent_text() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="locale-matrix-${UUID.randomUUID()}.db"
        val fixture=AttendanceTestApp(context,name)
        var shown by mutableStateOf(true)
        val verified=mutableSetOf<String>()
        try {
            compose.setContent {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) { if (shown) App(fixture.graph) }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().isNotEmpty() }
            for (language in listOf(LanguagePreference.EN,LanguagePreference.AR)) for (theme in listOf(ThemePreference.LIGHT,ThemePreference.DARK)) {
                runBlocking { fixture.graph.settings.update(language=language,theme=theme) }
                compose.onNodeWithTag("nav-settings").performClick()
                compose.onNodeWithTag("settings-name").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("report-connect").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("delete-local-data").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("nav-today").performClick()
                val status=if (language==LanguagePreference.AR) "حان وقت تسجيل الحضور" else "Ready to check in"
                compose.waitUntil(10_000) { compose.onAllNodesWithText(status).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("today-check").performScrollTo().assertIsDisplayed()
                for (tag in listOf("today-place","today-month-counts","today-week","today-report-state"))
                    compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                verified+="${language.name}/${theme.name}"
            }
            Assert.assertEquals(4,verified.size)
            Assert.assertEquals(0,fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown=false }; compose.waitForIdle()
            fixture.graph.reporting.close(); fixture.db.close(); context.deleteDatabase(name)
        }
    }
}
