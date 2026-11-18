package dev.daymark.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.component.DepartureCutoffEditor
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.settings.applyAppLocale
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DepartureOverridesTest {
    @get:Rule
    val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun addingAndRemovingDayRestoresDefault() {
        var schedule by mutableStateOf(
            WeeklySchedule(
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                arrival = LocalTime(9, 0),
                overrides = emptyMap(),
                graceMinutes = 5,
                zoneId = "UTC",
                departure = LocalTime(17, 0),
            )
        )
        applyAppLocale(AppLanguage.EN)
        compose.setContent {
            WithPlatformLocale(AppLanguage.EN) {
                DepartureCutoffEditor(schedule, false, { schedule = it })
            }
        }
        compose.onNodeWithText("Monday").assertDoesNotExist()
        compose.onNodeWithText("Add day to override").performClick()
        compose.onNodeWithText("Monday").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Monday").assertIsDisplayed()
        compose.onNodeWithText("Tuesday").assertDoesNotExist()
        compose.onNodeWithContentDescription("No cutoff on Monday").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(schedule.departureOverrides.containsKey(DayOfWeek.MONDAY)) }
        compose.runOnIdle { org.junit.Assert.assertNull(schedule.departureOverrides[DayOfWeek.MONDAY]) }
        compose.onNodeWithContentDescription("Remove Monday override").performClick()
        compose.onNodeWithText("Monday").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertFalse(schedule.departureOverrides.containsKey(DayOfWeek.MONDAY)) }
    }
}
