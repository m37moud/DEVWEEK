package dev.daymark.presentation

import dev.daymark.TestRepository
import dev.daymark.models.BootstrapSnapshot
import dev.daymark.models.ConfigurationHealth
import dev.daymark.models.TrackerSummary
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.reminders.ReminderCopy
import dev.daymark.repository.ApplicationDataSource
import dev.daymark.settings.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppStorageFailureTest {
    @Test
    fun failed_bootstrap_retries_on_foreground_return() = runTest {
        var fail = true
        val saved = BootstrapSnapshot(AppSettings(), TrackerSummary("tracker", "", 7, false))
        val repository = object : TestRepository() {
            override val application = object : ApplicationDataSource {
                override fun observeBootstrap() = flow {
                    emit(saved)
                    if (fail) error("Storage unavailable")
                }

                override fun setActive(active: Boolean) = Unit
                override suspend fun refresh(
                    settings: AppSettings,
                    language: AppLanguage,
                    copy: ReminderCopy
                ) = ConfigurationHealth()

                override suspend fun historyDate(): LocalDate? = null
            }
        }
        val model = AppViewModel(repository, backgroundScope)
        model.setActive(true)
        runCurrent()
        assertTrue(model.uiState.value.failed)
        assertNull(model.uiState.value.bootstrap)
        model.setActive(false)
        fail = false
        model.setActive(true)
        runCurrent()
        assertFalse(model.uiState.value.failed)
        assertEquals(saved, model.uiState.value.bootstrap)
    }
}
