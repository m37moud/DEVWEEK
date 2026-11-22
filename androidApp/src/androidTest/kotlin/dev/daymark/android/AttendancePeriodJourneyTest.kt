package dev.daymark.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.history.AttendancePeriodSettings
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttendancePeriodJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun arabic_boundary_pickers_update_the_range_and_history_at_large_text() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "attendance-period-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context, name)
        var shown by mutableStateOf(true)
        try {
            runBlocking {
                fixture.graph.settings.update(language = LanguagePreference.AR, theme = ThemePreference.DARK)
            }
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    if (shown) App(fixture.graph)
                }
            }

            waitFor("nav-settings")
            compose.onNodeWithTag("nav-settings").performClick()
            waitFor("settings-attendance-period-edit")
            compose.onNodeWithTag("attendance-period-review").assertDoesNotExist()
            compose.onNodeWithTag("settings-attendance-period-edit").performScrollTo().performClick()
            waitFor("attendance-period-review")
            val year = compose.onNodeWithTag("attendance-period-vacation-year")
            year.performScrollTo().assertIsOff()
            compose.onNodeWithText("تبدأ السنتان في", substring = true).performScrollTo().assertIsDisplayed()
            year.performScrollTo().performClick().assertIsOn()
            compose.onNodeWithTag("attendance-period-custom").performScrollTo().performClick()
            pickDay("start", 6)
            assertBoundary("end", 5)
            pickDay("end", 20)
            assertBoundary("start", 21)
            compose.onNodeWithText("تبدأ سنة فترات الحضور في", substring = true)
                .performScrollTo().assertIsDisplayed()
            year.performScrollTo().performClick().assertIsOff()
            compose.onNodeWithTag("attendance-period-review").performScrollTo().performClick()
            waitFor("attendance-period-save")
            val label = DisplayFormats.monthYear(LocalDate(2026, 11, 1), AppLanguage.AR)
            compose.onNodeWithText(label).assertIsDisplayed()
            runBlocking {
                val timeOff = fixture.graph.repository.timeOff
                val competingReview = requireNotNull(timeOff.previewAttendancePeriod(AttendancePeriodSettings(19)))
                assertEquals(PlanMutationResult.Applied, timeOff.saveAttendancePeriod(competingReview))
            }
            compose.onNodeWithTag("attendance-period-save").performClick()
            compose.onNodeWithText("تغيّرت سجلاتك.", substring = true).performScrollTo().assertIsDisplayed()
            assertBoundary("end", 20)
            compose.onNodeWithTag("attendance-period-review").performScrollTo().performClick()
            waitFor("attendance-period-save")
            compose.onNodeWithTag("attendance-period-save").performClick()

            compose.waitUntil(10_000) {
                runBlocking { fixture.db.profileDao().setting("attendance-period")?.value == "20:CALENDAR" }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("attendance-period-review").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("تنتهي في اليوم", substring = true).performScrollTo()
                .assertIsDisplayed().assertTextContains("٢٠", substring = true)
            compose.onNodeWithTag("settings-annual-vacation-edit").performScrollTo().performClick()
            compose.onNodeWithTag("settings-annual-vacation-allowance").performScrollTo().performTextReplacement("٢٢")
            compose.onNodeWithTag("settings-annual-vacation-save").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("settings-annual-vacation-save").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("تم حفظ إعدادات الإجازات السنوية", substring = true)
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("nav-history").performClick()
            waitFor("history-root")
            for (tag in listOf("calendar-date-2026-10-21", "calendar-date-2026-11-20")) {
                compose.onNodeWithTag("history-root").performScrollToNode(hasTestTag("history-calendar"))
                compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            }
            assertEquals(0, fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            fixture.graph.reporting.close()
            fixture.db.close()
            context.deleteDatabase(name)
        }
    }

    private fun pickDay(boundary: String, day: Int) {
        compose.onNodeWithTag("attendance-period-$boundary").performScrollTo().performClick()
        waitFor("attendance-period-picker")
        val arabic = DisplayFormats.number(day, AppLanguage.AR)
        val dayNumber = Regex("""(?<!\p{Nd})(?:$day|$arabic)(?!\p{Nd})""")
        val hasDay = SemanticsMatcher("calendar day $day") { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty().any { dayNumber.containsMatchIn(it.text) }
        }
        compose.onNode(hasAnyAncestor(hasTestTag("attendance-period-picker")) and hasClickAction() and hasDay)
            .performScrollTo().performClick()
        compose.onNodeWithTag("attendance-period-picker-confirm").performClick()
    }

    private fun assertBoundary(boundary: String, day: Int) {
        compose.onNodeWithTag("attendance-period-$boundary").assertTextContains(
            DisplayFormats.number(day, AppLanguage.AR), substring = true
        )
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
