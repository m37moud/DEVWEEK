package dev.daymark.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanState
import dev.daymark.models.plan.PlanMutationResult
import dev.daymark.models.plan.TimeOffCommand
import dev.daymark.models.plan.TimeOffPreview
import dev.daymark.models.plan.PlanRejection
import dev.daymark.models.plan.VacationBalanceSummary
import dev.daymark.plan.TimeOffKind
import dev.daymark.plan.WeeklySchedule
import dev.daymark.presentation.features.plan.PlanActions
import dev.daymark.presentation.features.plan.PlanContent
import dev.daymark.presentation.features.plan.PlanUiState
import dev.daymark.presentation.features.today.TodayActions
import dev.daymark.presentation.features.today.TodayContent
import dev.daymark.presentation.features.today.TodayUiState
import dev.daymark.presentation.features.today.TodayVacationBalanceUiState
import dev.daymark.presentation.features.today.component.TodayVacationBalance
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.settings.applyAppLocale
import dev.daymark.ui.theme.DaymarkTheme
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanEditingTest {
    @get:Rule
    val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()
    private val today = LocalDate(2026, 11, 18)
    private val historyDate = LocalDate(2026, 11, 10)
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-18T12:00:00Z")
    }
    private val plan = PlanChange(
        WeeklySchedule(setOf(DayOfWeek.MONDAY), LocalTime(9, 0), emptyMap(), 5, "UTC"),
        TrackerCategory.WORK,
        // Polar fixture keeps editor checks independent of map networking.
        RegisteredPlace(90.0, 0.0, 100, 1)
    )

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun historyDateIsUsedOnlyForInitialEditor() {
        var previewed: TimeOffCommand? = null
        showPlan(sampleState(), actions { previewed = it }, historyDate)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("plan-add-time-off").performScrollTo().performClick()
        compose.onNodeWithTag("time-off-preview").performClick()
        compose.runOnIdle {
            assertEquals(today, (previewed as TimeOffCommand.Add).from)
        }
    }

    @Test
    fun dismissedHistoryEditorStaysDismissedAfterRestoration() {
        val restoration = StateRestorationTester(compose)
        applyAppLocale(AppLanguage.EN)
        restoration.setContent {
            WithPlatformLocale(AppLanguage.EN) {
                DaymarkTheme(ThemePreference.LIGHT) {
                    PlanContent(sampleState(), clock, historyDate, actions())
                }
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("time-off-preview").assertDoesNotExist()
    }

    @Test
    fun balanceEditorStaysOpenWhileSaving() {
        var state by mutableStateOf(balanceState())
        showBalance({ state }) { _, _ -> state = state.copy(saving = true) }
        compose.onNodeWithText("Adjust available balance").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Adjustment note (optional)").assertIsDisplayed()
    }

    @Test
    fun timeOffKindAndEditorSurviveRestoration() {
        var previewed: TimeOffCommand? = null
        val restoration = StateRestorationTester(compose)
        applyAppLocale(AppLanguage.EN)
        restoration.setContent {
            WithPlatformLocale(AppLanguage.EN) {
                DaymarkTheme(ThemePreference.LIGHT) {
                    PlanContent(sampleState(), clock, historyDate, actions { previewed = it })
                }
            }
        }
        compose.onNodeWithText("Holiday").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("time-off-preview").performClick()
        compose.runOnIdle {
            val command = previewed as TimeOffCommand.Add
            assertEquals(TimeOffKind.HOLIDAY, command.kind)
            assertEquals(historyDate, command.from)
        }
    }

    @Test
    fun previewDoesNotReopenHistoryEditorAfterRestoration() {
        var state by mutableStateOf(sampleState())
        val actions = actions {
            state = state.copy(
                timeOffPreview = TimeOffPreview(
                    1,
                    emptyList(),
                    emptyList(),
                    -1,
                    9,
                    null
                )
            )
        }
        val restoration = StateRestorationTester(compose)
        applyAppLocale(AppLanguage.EN)
        restoration.setContent {
            WithPlatformLocale(AppLanguage.EN) {
                DaymarkTheme(ThemePreference.LIGHT) {
                    PlanContent(state, clock, historyDate, actions)
                }
            }
        }
        compose.onNodeWithTag("time-off-preview").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("time-off-preview").assertDoesNotExist()
        compose.onNodeWithTag("preview-confirm").assertIsDisplayed()
    }

    @Test
    fun balanceFailureRetainsInputAndAcknowledgementClosesDialog() {
        var state by mutableStateOf(balanceState())
        var attemptedBalance: String? = null
        var attemptedNote: String? = null
        var attempts = 0
        showBalance({ state }) { balance, note ->
            attemptedBalance = balance
            attemptedNote = note
            attempts++
            state = if (attempts == 1) state.copy(saveResult = PlanMutationResult.LocalSaveFailed)
            else state.copy(saveResult = PlanMutationResult.Applied, saveRevision = 1)
        }
        compose.onNodeWithText("Adjust available balance").performClick()
        compose.onNodeWithText("Adjustment note (optional)")
            .performTextReplacement("Test adjustment")
        compose.onNodeWithText("Save").performClick()
        compose.onNode(
            hasText("Could not save on this phone. Your form is still here; try again.") and
                    hasAnyAncestor(isDialog())
        ).assertIsDisplayed()
        compose.onNodeWithText("Test adjustment").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Adjustment note (optional)").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("10", attemptedBalance)
            assertEquals("Test adjustment", attemptedNote)
        }
    }

    @Test
    fun workTimeOffPreviewShowsBalance() {
        showPlan(
            sampleState().copy(
                timeOffPreview = TimeOffPreview(1, emptyList(), emptyList(), -1, 9, null)
            )
        )
        compose.onNodeWithText("Balance change · -1 days. Available after · 9 days.")
            .assertIsDisplayed()
    }

    @Test
    fun preTrackingPreviewExplainsTheDateCutoff() {
        showPlan(
            sampleState().copy(
                timeOffPreview = TimeOffPreview(
                    1, emptyList(), emptyList(), 0, 10, PlanRejection.OUTSIDE_TRACKED_PERIOD
                )
            )
        )
        compose.onNodeWithText("This date range starts before you began tracking attendance.")
            .assertIsDisplayed()
    }

    @Test
    fun todayShowsVacationBalanceOnlyForTheActiveWorkCategory() {
        var category by mutableStateOf(TrackerCategory.SCHOOL)
        compose.setContent {
            DaymarkTheme(ThemePreference.LIGHT) {
                TodayContent(
                    state = TodayUiState(
                        ready = true,
                        activeCategory = category,
                        vacationBalance = balanceState()
                    ),
                    displayName = "Alex",
                    onHistory = {},
                    actions = TodayActions({}, {}, { _, _ -> }, { _, _ -> })
                )
            }
        }
        compose.onNodeWithTag("today-vacation-balance").assertDoesNotExist()
        compose.runOnIdle { category = TrackerCategory.COURSE }
        compose.onNodeWithTag("today-vacation-balance").assertDoesNotExist()
        compose.runOnIdle { category = TrackerCategory.WORK }
        compose.onNodeWithTag("today-vacation-balance").performScrollTo().assertIsDisplayed()
    }

    private fun balanceState() =
        TodayVacationBalanceUiState(summary = VacationBalanceSummary(10, 0, 10))

    private fun showBalance(
        state: () -> TodayVacationBalanceUiState,
        onSave: (String, String?) -> Unit
    ) {
        applyAppLocale(AppLanguage.EN)
        compose.setContent {
            WithPlatformLocale(AppLanguage.EN) {
                DaymarkTheme(ThemePreference.LIGHT) {
                    TodayVacationBalance(state(), onSave)
                }
            }
        }
    }

    private fun sampleState() = PlanUiState(
        plan = PlanState(plan, null, null), ready = true
    )

    private fun actions(onPreview: (TimeOffCommand) -> Unit = {}) = PlanActions(
        onPreview, {}, {}
    )

    private fun showPlan(
        state: PlanUiState,
        actions: PlanActions = actions(),
        initialDate: LocalDate? = null
    ) {
        applyAppLocale(AppLanguage.EN)
        compose.setContent {
            WithPlatformLocale(AppLanguage.EN) {
                DaymarkTheme(ThemePreference.LIGHT) {
                    PlanContent(state, clock, initialDate, actions)
                }
            }
        }
    }
}
