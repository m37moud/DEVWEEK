package dev.daymark.android.previews

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import dev.daymark.history.HistoryMetric
import dev.daymark.history.HistoryRange
import dev.daymark.models.attendance.HistoryRecord
import dev.daymark.presentation.features.historydetail.HistoryDetailActions
import dev.daymark.presentation.features.historydetail.HistoryDetailContent
import dev.daymark.presentation.features.historydetail.HistoryDetailUiState
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Instant


@Preview(name = "Leave before tracking")
@Composable
private fun HistoryEarlierVacationPreview() {
    val from = LocalDate(2026, 11, 1)
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                metric = HistoryMetric.LEAVE_REST,
                from = from,
                through = LocalDate(2026, 11, 30),
                range = HistoryRange(
                    records = emptyList(),
                    firstDate = LocalDate(2026, 11, 21),
                    today = LocalDate(2026, 11, 21),
                    earlierVacationDates = listOf(LocalDate(2026, 11, 19))
                ),
                ready = true
            ),
            actions = HistoryDetailActions({}, { _, _ -> }, {}, {}, {})
        )
    }
}

@Preview
@Composable
private fun HistoryHolidayDetailPreview() {
    val from = LocalDate(2026, 10, 21)
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                metric = HistoryMetric.HOLIDAY,
                from = from,
                through = LocalDate(2026, 11, 20),
                range = HistoryRange(
                    records = listOf(
                        previewLateRecord().copy(
                            outcome = dev.daymark.models.attendance.DayOutcome(
                                dev.daymark.models.attendance.AttendanceStatus.HOLIDAY,
                                dev.daymark.models.attendance.AttendanceSource.MANUAL_TIME_OFF,
                                null, null
                            )
                        )
                    ),
                    firstDate = from,
                    today = LocalDate(2026, 11, 15)
                ),
                ready = true
            ),
            actions = HistoryDetailActions({}, { _, _ -> }, {}, {}, {})
        )
    }
}

@Preview(name = "Late history dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryLateDetailPreview() {
    val from = LocalDate(year = 2026, month = 11, day = 1)
    DaymarkTheme(preference = ThemePreference.SYSTEM) {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                metric = HistoryMetric.LATE,
                from = from,
                through = LocalDate(year = 2026, month = 11, day = 30),
                range = HistoryRange(
                    records = listOf(previewLateRecord()),
                    firstDate = from,
                    today = LocalDate(year = 2026, month = 11, day = 15)
                ),
                ready = true
            ),
            actions = HistoryDetailActions(
                back = {},
                selectRange = { _, _ -> },
                correct = {},
                timeOff = {},
                beginCorrection = {}
            )
        )
    }
}

@Preview(name = "Late history Arabic", locale = "ar", widthDp = 360, heightDp = 900)
@Preview(
    name = "Late history Arabic narrow large text",
    locale = "ar",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HistoryLateDetailArabicPreview() {
    WithPlatformLocale(language = AppLanguage.AR) {
        CompositionLocalProvider(
            LocalAppLanguage provides AppLanguage.AR,
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            HistoryLateDetailPreview()
        }
    }
}

@Preview
@Composable
private fun HistoryAttendanceDetailArabicPreview() {
    val from = LocalDate(2026, 11, 1)
    CompositionLocalProvider(LocalAppLanguage provides AppLanguage.AR) {
        DaymarkTheme(ThemePreference.LIGHT) {
            HistoryDetailContent(
                state = HistoryDetailUiState(
                    metric = HistoryMetric.ATTENDANCE,
                    from = from,
                    through = LocalDate(2026, 11, 30),
                    range = HistoryRange(
                        records = listOf(previewLateRecord()),
                        firstDate = from,
                        today = LocalDate(2026, 11, 15)
                    ),
                    ready = true
                ),
                actions = HistoryDetailActions({}, { _, _ -> }, {}, {}, {})
            )
        }
    }
}

@Preview(name = "Narrow large text", widthDp = 320, fontScale = 2f)
@Composable
private fun HistoryNarrowTextPreview() {
    val from = LocalDate(2026, 11, 1)
    DaymarkTheme(ThemePreference.LIGHT) {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                metric = HistoryMetric.ATTENDANCE,
                from = from,
                through = LocalDate(2026, 11, 30),
                range = HistoryRange(
                    records = listOf(previewLateRecord()),
                    firstDate = from,
                    today = LocalDate(2026, 11, 15)
                ),
                ready = true
            ),
            actions = HistoryDetailActions({}, { _, _ -> }, {}, {}, {})
        )
    }
}

@Preview(name = "Work missions Arabic", locale = "ar", widthDp = 320, fontScale = 1.5f)
@Composable
private fun HistoryWorkMissionArabicPreview() {
    val from = LocalDate(2026, 10, 21)
    val through = LocalDate(2026, 11, 20)
    CompositionLocalProvider(LocalAppLanguage provides AppLanguage.AR) {
        DaymarkTheme(ThemePreference.DARK) {
            HistoryDetailContent(
                state = HistoryDetailUiState(
                    metric = HistoryMetric.WORK_MISSION,
                    from = from,
                    through = through,
                    range = HistoryRange(listOf(previewWorkMissionRecord()), from, through),
                    ready = true
                ),
                actions = HistoryDetailActions({}, { _, _ -> }, {}, {}, {})
            )
        }
    }
}

internal fun previewWorkMissionRecord(): HistoryRecord {
    val original = previewLateRecord()
    return original.copy(
        evidence = null,
        outcome = dev.daymark.models.attendance.DayOutcome(
            dev.daymark.models.attendance.AttendanceStatus.WORK_MISSION,
            dev.daymark.models.attendance.AttendanceSource.MANUAL_WORK_MISSION,
            null,
            dev.daymark.plan.TimeOffKind.WORK_MISSION
        ),
        workMissionNote = "زيارة عميل / Client visit"
    )
}

private fun previewLateRecord(): HistoryRecord {
    val arrival = Instant.parse("2026-11-10T09:00:00Z")
    val confirmed = Instant.parse("2026-11-10T09:10:00Z")
    val plan = dev.daymark.models.attendance.DayPlan(
        dev.daymark.models.attendance.DayKey("preview", LocalDate(2026, 11, 10)),
        dev.daymark.models.attendance.TrackerCategory.WORK,
        arrival,
        arrival,
        "UTC",
        1,
        dev.daymark.models.attendance.RegisteredPlace(0.0, 0.0, 100, 1),
        true
    )
    val evidence = dev.daymark.models.attendance.AttendanceEvidence(
        dev.daymark.models.attendance.AttendanceStatus.LATE,
        dev.daymark.models.attendance.AttendanceSource.MANUAL_CORRECTION,
        confirmed,
        null,
        confirmed
    )
    return HistoryRecord(
        plan = plan,
        evidence = evidence,
        outcome = dev.daymark.models.attendance.DayOutcome(
            evidence.status,
            evidence.source,
            confirmed,
            null
        )
    )
}
