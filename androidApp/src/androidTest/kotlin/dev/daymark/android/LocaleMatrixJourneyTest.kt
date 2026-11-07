package dev.daymark.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.app.App
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.Correction
import dev.daymark.settings.*
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocaleMatrixJourneyTest {
    @get:Rule
    val compose = createComposeRule()
    @Test
    fun english_arabic_light_dark_core_actions_remain_reachable_at_two_hundred_percent_text() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "locale-matrix-${UUID.randomUUID()}.db"
        val fixture = AttendanceTestApp(context,name)
        var shown by mutableStateOf(true)
        val verified = mutableSetOf<String>()
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.width(320.dp)) { if (shown) App(fixture.graph) }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().isNotEmpty() }
            for (corrected in listOf(false, true)) {
                if (corrected) runBlocking {
                    val repository = fixture.graph.repository
                    val day = repository.attendance.loadCurrentDay(fixture.clock.now())!!
                    repository.attendance.correct(Correction(
                        day.plan.key, AttendanceStatus.PRESENT, LocalTime(9, 2), null
                    ))
                }
                for (language in listOf(LanguagePreference.EN, LanguagePreference.AR)) for (theme in listOf(ThemePreference.LIGHT, ThemePreference.DARK)) {
                    runBlocking { fixture.graph.settings.update(language = language, theme = theme) }
                    compose.onNodeWithTag("nav-settings").performClick()
                    compose.onNodeWithTag("settings-name").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("report-connect").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("delete-local-data").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("nav-today").performClick()
                    val status = if (corrected) {
                        if (language == LanguagePreference.AR) "حاضر" else "Present"
                    } else {
                        if (language == LanguagePreference.AR) "حان وقت تسجيل الحضور" else "Ready to check in"
                    }
                    val source = if (corrected) {
                        if (language == LanguagePreference.AR) "تم التصحيح يدويًا" else "Manually corrected"
                    } else {
                        if (language == LanguagePreference.AR) "لم يتم تأكيد الحضور بعد" else "No confirmation yet"
                    }
                    compose.waitUntil(10_000) {
                        compose.onAllNodes(hasTestTag("today-status") and hasText(status)).fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onNodeWithTag("today-status").performScrollTo().assertIsDisplayed()
                        .assertWidthIsAtLeast(1.dp).assertHeightIsAtLeast(1.dp)
                    compose.onNodeWithText(source).performScrollTo().assertIsDisplayed()
                        .assertWidthIsAtLeast(1.dp).assertHeightIsAtLeast(1.dp)
                    val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
                    File(context.getExternalFilesDir(null), "today-$corrected-${language.name}-${theme.name}.png")
                        .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    compose.onNodeWithTag("today-check").performScrollTo().assertIsDisplayed()
                    for (tag in listOf("today-place", "today-month-counts", "today-week", "today-report-state"))
                        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                    verified += "$corrected/${language.name}/${theme.name}"
                }
            }
            Assert.assertEquals(8, verified.size)
            Assert.assertEquals(0, fixture.locationRequests)
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            fixture.graph.reporting.close()
            fixture.db.close()
            context.deleteDatabase(name)
        }
    }
}
