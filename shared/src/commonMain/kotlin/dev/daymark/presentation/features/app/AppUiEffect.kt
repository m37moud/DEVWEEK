package dev.daymark.presentation.features.app

import kotlinx.datetime.LocalDate

sealed interface AppUiEffect {
    data class History(val date: LocalDate) : AppUiEffect
}
