package dev.daymark.widgets

import dev.daymark.TestReminders
import dev.daymark.TestRepository
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationPermissionPort
import dev.daymark.presentation.features.settings.SettingsViewModel
import kotlin.test.*
import kotlinx.coroutines.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetSetupViewModelTest {
    private val repository = object : TestRepository() {
        override val settings = object : dev.daymark.repository.SettingsDataSource {
            override fun observe() =
                kotlinx.coroutines.flow.flowOf(dev.daymark.models.settings.AppSettings())

            override fun observeTracker() =
                kotlinx.coroutines.flow.flowOf<dev.daymark.models.TrackerSummary?>(null)

            override suspend fun trackerIdentity(): String? = null
            override suspend fun update(
                language: dev.daymark.settings.LanguagePreference?,
                theme: dev.daymark.settings.ThemePreference?,
                displayName: String?,
                remindersEnabled: Boolean?
            ) = error("Unexpected settings write")

            override suspend fun setAutomation(expectedTrackerId: String, enabled: Boolean) =
                error("Unexpected automation write")
        }
    }

    private val permissions = object : LocationPermissionPort {
        override fun current() = LocationCapabilities(false, false, false, true)
        override suspend fun requestForeground() = error("Widget setup must not request location")
        override suspend fun requestBackground() = error("Widget setup must not request location")
        override fun openSystemSettings() = Unit
    }

    @Test
    fun configurationSurvivesNewModelsAndReflectsRemoval() = runTest {
        val port = FakeSetupPort()
        fun model() =
            SettingsViewModel(repository, permissions, TestReminders, backgroundScope, port)

        val first = model()
        first.refreshWidgetSetup()
        runCurrent()
        assertEquals(WidgetSetupStatus.NOT_ADDED, first.uiState.value.widgetStatus)
        first.addWidget()
        assertTrue(first.uiState.value.widgetPinPending)
        assertEquals(WidgetSetupStatus.NOT_ADDED, first.uiState.value.widgetStatus)
        port.status = WidgetSetupStatus.ADDED
        port.confirm!!()
        runCurrent()
        assertEquals(WidgetSetupStatus.ADDED, first.uiState.value.widgetStatus)
        assertFalse(first.uiState.value.widgetPinPending)
        val revisited = model()
        revisited.refreshWidgetSetup()
        runCurrent()
        assertEquals(WidgetSetupStatus.ADDED, revisited.uiState.value.widgetStatus)
        port.status = WidgetSetupStatus.NOT_ADDED
        revisited.refreshWidgetSetup()
        runCurrent()
        assertEquals(WidgetSetupStatus.NOT_ADDED, revisited.uiState.value.widgetStatus)
    }

    @Test
    fun cancelledFailedAndUnsupportedRequestsNeverBecomeAdded() = runTest {
        val port = FakeSetupPort()
        val model = SettingsViewModel(repository, permissions, TestReminders, backgroundScope, port)
        model.refreshWidgetSetup()
        runCurrent()
        model.addWidget()
        model.refreshWidgetSetup()
        runCurrent()
        assertFalse(model.uiState.value.widgetPinPending)
        assertEquals(WidgetSetupStatus.NOT_ADDED, model.uiState.value.widgetStatus)
        port.status = WidgetSetupStatus.UNKNOWN
        model.refreshWidgetSetup()
        runCurrent()
        assertEquals(WidgetSetupStatus.UNKNOWN, model.uiState.value.widgetStatus)
        port.accept = false
        model.addWidget()
        assertFalse(model.uiState.value.widgetPinPending)
        assertNotEquals(WidgetSetupStatus.ADDED, model.uiState.value.widgetStatus)
    }

    private class FakeSetupPort : WidgetSetupPort {
        var status = WidgetSetupStatus.NOT_ADDED
        var accept = true
        var confirm: (() -> Unit)? = null
        override val canRequestPin = true
        override fun query(completion: (WidgetSetupStatus) -> Unit) = completion(status)
        override fun requestPin(onConfirmed: () -> Unit): Boolean {
            confirm = onConfirmed
            return accept
        }
    }
}
