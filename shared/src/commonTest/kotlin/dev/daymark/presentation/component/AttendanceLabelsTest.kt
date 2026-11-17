package dev.daymark.presentation.component

import dev.daymark.settings.AppLanguage
import dev.daymark.settings.DisplayFormats
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceLabelsTest {
    @Test
    fun formatsTwelveHourTime() {
        assertEquals("\u206812:05 AM\u2069", localTimeLabel(LocalTime(0, 5), AppLanguage.EN))
        assertEquals("\u206812:05 PM\u2069", localTimeLabel(LocalTime(12, 5), AppLanguage.EN))
        assertEquals("\u206801:05 PM\u2069", localTimeLabel(LocalTime(13, 5), AppLanguage.EN))
        val zero = DisplayFormats.number(0, AppLanguage.AR)
        val one = DisplayFormats.number(1, AppLanguage.AR)
        val five = DisplayFormats.number(5, AppLanguage.AR)
        assertEquals("\u2068$zero$one:$zero$five م\u2069", localTimeLabel(LocalTime(13, 5), AppLanguage.AR))
    }
}
