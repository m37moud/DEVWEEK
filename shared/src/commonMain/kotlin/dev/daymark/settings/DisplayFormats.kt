package dev.daymark.settings

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

expect object DisplayFormats {
    fun date(date: LocalDate, language: AppLanguage): String
    fun time(at: Instant, zoneId: String, language: AppLanguage): String
    fun number(value: Int, language: AppLanguage): String
}

fun isolated(value: String): String = "\u2068$value\u2069"
