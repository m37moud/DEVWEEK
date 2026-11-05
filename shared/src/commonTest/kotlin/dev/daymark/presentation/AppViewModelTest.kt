package dev.daymark.presentation

import dev.daymark.TestRepository
import dev.daymark.models.BootstrapSnapshot
import dev.daymark.models.ConfigurationHealth
import dev.daymark.models.DeletionResult
import dev.daymark.models.TrackerSummary
import dev.daymark.models.settings.AppSettings
import dev.daymark.presentation.features.app.AppUiEffect
import dev.daymark.presentation.features.app.AppViewModel
import dev.daymark.reminders.ReminderCopy
import dev.daymark.repository.ApplicationDataSource
import dev.daymark.repository.DataLifecycleDataSource
import dev.daymark.settings.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @Test
    fun bootstrap_revisions_lifecycle_deletion_and_history_use_repository() = runTest {
        val bootstrap = MutableStateFlow(
            BootstrapSnapshot(
                AppSettings(),
                TrackerSummary("tracker", "", 4, false)
            )
        )
        val deleting = MutableStateFlow(false)
        val activations = mutableListOf<Boolean>()
        val application = object : ApplicationDataSource {
            override fun observeBootstrap() = bootstrap
            override fun setActive(active: Boolean) {
                activations += active
            }

            override suspend fun refresh(
                settings: AppSettings,
                language: AppLanguage,
                copy: ReminderCopy
            ) = ConfigurationHealth(widgetOk = true)

            override suspend fun historyDate() = LocalDate(2026, 11, 4)
        }
        val repository = object : TestRepository() {
            override val application = application
            override val dataLifecycle = object : DataLifecycleDataSource {
                override val deleting = deleting
                override suspend fun deleteLocalData(): DeletionResult =
                    error("Unexpected deletion")
            }
        }
        val model = AppViewModel(repository, backgroundScope)
        model.setActive(true)
        runCurrent()
        assertEquals(4L, model.uiState.value.bootstrap?.tracker?.changeRevision)
        bootstrap.value =
            bootstrap.value.copy(tracker = bootstrap.value.tracker!!.copy(changeRevision = 5))
        runCurrent()
        assertEquals(5L, model.uiState.value.bootstrap?.tracker?.changeRevision)
        model.setActive(true)
        assertTrue(activations.last())
        deleting.value = true
        runCurrent()
        assertFalse(activations.last())
        deleting.value = false
        runCurrent()
        assertTrue(activations.last())
        model.showHistory()
        assertEquals(AppUiEffect.History(LocalDate(2026, 11, 4)), model.effects.first())
        model.setActive(false)
        assertFalse(activations.last())
        runCurrent()
        bootstrap.value =
            bootstrap.value.copy(tracker = bootstrap.value.tracker!!.copy(changeRevision = 6))
        runCurrent()
        assertEquals(5L, model.uiState.value.bootstrap?.tracker?.changeRevision)
        model.setActive(true)
        runCurrent()
        assertEquals(6L, model.uiState.value.bootstrap?.tracker?.changeRevision)
    }

}
