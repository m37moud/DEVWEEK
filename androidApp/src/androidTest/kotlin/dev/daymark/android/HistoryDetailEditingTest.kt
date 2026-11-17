package dev.daymark.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryRange
import dev.daymark.models.attendance.AttendanceSource
import dev.daymark.models.attendance.AttendanceStatus
import dev.daymark.models.attendance.DayKey
import dev.daymark.models.attendance.DayOutcome
import dev.daymark.models.attendance.DayPlan
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.presentation.features.historydetail.HistoryDetailActions
import dev.daymark.presentation.features.historydetail.HistoryDetailContent
import dev.daymark.presentation.features.historydetail.HistoryDetailUiState
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.settings.ThemePreference
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class HistoryDetailEditingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun correction_dialog_and_draft_survive_state_restoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TestTheme {
                HistoryDetailContent(
                    sampleState(),
                    HistoryDetailActions({}, { _, _ -> }, {}, {}, {})
                )
            }
        }
        openRecord()
        compose.onNodeWithTag("correction-note").performTextInput("Draft correction")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("correction-save").assertIsDisplayed()
        compose.onNodeWithTag("correction-note").assertTextContains("Draft correction")
    }

    @Test
    fun time_off_dialog_survives_state_restoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TestTheme {
                HistoryDetailContent(
                    sampleState(scheduled = false),
                    HistoryDetailActions({}, { _, _ -> }, {}, {}, {})
                )
            }
        }
        openRecord()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("history-time-off-action").assertIsDisplayed()
    }

    @Test
    fun time_off_navigation_is_disabled_while_correction_is_saving() {
        var state by mutableStateOf(sampleState())
        compose.setContent {
            TestTheme {
                HistoryDetailContent(
                    state,
                    HistoryDetailActions(
                        {},
                        { _, _ -> },
                        { state = state.copy(busy = true) },
                        {},
                        {})
                )
            }
        }
        openRecord()
        compose.onNodeWithTag("correction-save").performClick()
        compose.onNodeWithTag("correction-time-off").assertIsNotEnabled()
    }

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        WithPlatformLocale(AppLanguage.EN) {
            DaymarkTheme(ThemePreference.LIGHT, content)
        }
    }

    private fun openRecord() {
        compose.onNodeWithTag("history-detail-root")
            .performScrollToNode(hasTestTag("history-record-2026-11-10"))
        compose.onNodeWithTag("history-record-2026-11-10").performClick()
    }

    private fun sampleState(scheduled: Boolean = true): HistoryDetailUiState {
        val date = LocalDate(2026, 11, 10)
        val arrival = Instant.parse("2026-11-10T09:00:00Z")
        val record = HistoryRecord(
            DayPlan(
                DayKey("test", date), TrackerCategory.WORK, arrival, arrival, "UTC", 1,
                RegisteredPlace(0.0, 0.0, 100, 1), scheduled
            ),
            null,
            DayOutcome(
                if (scheduled) AttendanceStatus.ABSENT else AttendanceStatus.HOLIDAY,
                AttendanceSource.NONE, null, null
            )
        )
        return HistoryDetailUiState(
            if (scheduled) HistoryMetric.ABSENT else HistoryMetric.HOLIDAY,
            date, date, HistoryRange(listOf(record), date, date), ready = true
        )
    }
}
