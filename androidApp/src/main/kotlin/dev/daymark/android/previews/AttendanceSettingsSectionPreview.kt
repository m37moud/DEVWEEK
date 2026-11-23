package dev.daymark.android.previews

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.daymark.location.PlaceAddressRequest
import dev.daymark.models.attendance.RegisteredPlace
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.plan.PlanChange
import dev.daymark.models.plan.PlanState
import dev.daymark.presentation.component.PlaceAddressUiState
import dev.daymark.presentation.features.settings.AttendanceSettingsUiState
import dev.daymark.presentation.features.settings.component.AttendanceSettingsActions
import dev.daymark.presentation.features.settings.component.AttendanceSettingsContent
import dev.daymark.settings.AppLanguage
import dev.daymark.settings.LocalAppLanguage
import dev.daymark.settings.ThemePreference
import dev.daymark.settings.WithPlatformLocale
import dev.daymark.ui.theme.DaymarkTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

private val previewAttendanceActions = AttendanceSettingsActions(
    edit = {},
    preview = {},
    confirm = {},
    cancelPreview = {},
    registerCurrentPlace = {},
    openLocationSettings = {},
    resetDraft = {}
)

private val samplePlace = RegisteredPlace(
    latitude = 30.0444,
    longitude = 31.2357,
    radiusMetres = 150,
    version = 1L
)

private val samplePlanChange = PlanChange(
    schedule = sampleSchedule,
    category = TrackerCategory.WORK,
    place = samplePlace
)

@Preview(name = "Attendance Section Summary Light")
@Composable
private fun AttendanceSettingsSummaryLightPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        AttendanceSettingsContent(
            state = AttendanceSettingsUiState(
                ready = true,
                plan = PlanState(
                    current = samplePlanChange,
                    pending = null,
                    placeName = "Downtown Headquarters",
                    effectiveFrom = null
                ),
                draft = samplePlanChange,
                placeAddress = PlaceAddressUiState(
                    request = PlaceAddressRequest(
                        latitude = samplePlace.latitude,
                        longitude = samplePlace.longitude,
                        language = AppLanguage.EN
                    ),
                    address = "123 Nile Corniche, Downtown Cairo",
                    loading = false
                )
            ),
            actions = previewAttendanceActions,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Attendance Section Summary Dark RTL", locale = "ar", widthDp = 320, fontScale = 1.5f)
@Composable
private fun AttendanceSettingsSummaryDarkRtlPreview() {
    WithPlatformLocale(language = AppLanguage.AR) {
        CompositionLocalProvider(
            LocalAppLanguage provides AppLanguage.AR,
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            DaymarkTheme(preference = ThemePreference.DARK) {
                AttendanceSettingsContent(
                    state = AttendanceSettingsUiState(
                        ready = true,
                        plan = PlanState(
                            current = samplePlanChange,
                            pending = null,
                            placeName = "المقر الرئيسي",
                            effectiveFrom = null
                        ),
                        draft = samplePlanChange,
                        placeAddress = PlaceAddressUiState(
                            request = PlaceAddressRequest(
                                latitude = samplePlace.latitude,
                                longitude = samplePlace.longitude,
                                language = AppLanguage.AR
                            ),
                            address = "١٢٣ كورنيش النيل، وسط البلد، القاهرة",
                            loading = false
                        )
                    ),
                    actions = previewAttendanceActions,
                    modifier = Modifier.padding(all = 16.dp)
                )
            }
        }
    }
}

@Preview(name = "Attendance Section Pending Effective Date Narrow", widthDp = 320, fontScale = 1.5f)
@Composable
private fun AttendanceSettingsPendingEffectiveNarrowPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        AttendanceSettingsContent(
            state = AttendanceSettingsUiState(
                ready = true,
                plan = PlanState(
                    current = samplePlanChange,
                    pending = samplePlanChange.copy(category = TrackerCategory.COURSE),
                    placeName = "Training Center",
                    effectiveFrom = LocalDate(year = 2027, month = Month.JANUARY, day = 1)
                ),
                draft = samplePlanChange,
                placeAddress = PlaceAddressUiState(
                    request = PlaceAddressRequest(
                        latitude = samplePlace.latitude,
                        longitude = samplePlace.longitude,
                        language = AppLanguage.EN
                    ),
                    address = "Building 4, Tech Park",
                    loading = false
                )
            ),
            actions = previewAttendanceActions,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Attendance Section Storage Error")
@Composable
private fun AttendanceSettingsStorageErrorPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        AttendanceSettingsContent(
            state = AttendanceSettingsUiState(
                ready = true,
                storageError = true,
                plan = PlanState(
                    current = samplePlanChange,
                    pending = null,
                    placeName = "Main Campus",
                    effectiveFrom = null
                ),
                draft = samplePlanChange
            ),
            actions = previewAttendanceActions,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}

@Preview(name = "Attendance Section Busy")
@Composable
private fun AttendanceSettingsBusyPreview() {
    DaymarkTheme(preference = ThemePreference.LIGHT) {
        AttendanceSettingsContent(
            state = AttendanceSettingsUiState(
                ready = true,
                busy = true,
                plan = PlanState(
                    current = samplePlanChange,
                    pending = null,
                    placeName = "Main Campus",
                    effectiveFrom = null
                ),
                draft = samplePlanChange
            ),
            actions = previewAttendanceActions,
            modifier = Modifier.padding(all = 16.dp)
        )
    }
}
