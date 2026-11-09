package dev.daymark.app

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.daymark.at
import dev.daymark.attendance.TestClock
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.setup.SetupService
import dev.daymark.data.local.settings.SettingsRepository
import dev.daymark.repository.SettingsDataSource
import dev.daymark.repository.SettingsStorageException
import dev.daymark.settings.LanguagePreference
import dev.daymark.settings.ThemePreference
import kotlinx.coroutines.CompletableDeferred
import dev.daymark.fixtureFix
import dev.daymark.fixturePlace
import dev.daymark.fixtureSchedule
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.models.attendance.TrackerCategory
import dev.daymark.models.setup.SetupDraft
import dev.daymark.presentation.features.setup.SetupIssue
import dev.daymark.presentation.features.setup.SetupViewModel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SetupViewModelTest {
    @Test
    fun preferences_save_immediately_block_navigation_and_retry_without_losing_the_step() =
        runTest {
            val folder = Files.createTempDirectory("daymark-setup-preferences-")
            val db = openJvmDatabase(folder.resolve("app.db").toString())
            val stored = SettingsRepository(db)
            val releaseWrite = CompletableDeferred<Unit>()
            var failWrite = false
            val settings = object : SettingsDataSource by stored {
                override suspend fun update(
                    language: LanguagePreference?, theme: ThemePreference?,
                    displayName: String?, remindersEnabled: Boolean?
                ) {
                    releaseWrite.await()
                    if (failWrite) throw SettingsStorageException(IllegalStateException("Test storage failure"))
                    stored.update(language, theme, displayName, remindersEnabled)
                }
            }
            val permissions = object : LocationPermissionPort {
                override fun current() = LocationCapabilities(false, false, false, true)
                override suspend fun requestForeground() = error("No GPS permission expected")
                override suspend fun requestBackground() =
                    error("No background permission expected")

                override fun openSystemSettings() = Unit
            }
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult =
                    error("No GPS expected")
            }
            val repository = dev.daymark.testRepository(
                setupSource = SetupService(db, TestClock()), settingsSource = settings
            )
            try {
                val model =
                    SetupViewModel(repository, gateway, permissions, TestClock(), backgroundScope)
                model.uiState.first { it.ready && it.preferencesReady && it.draftSaved }
                assertEquals(LanguagePreference.SYSTEM, model.uiState.value.preferences.language)
                assertEquals(ThemePreference.SYSTEM, model.uiState.value.preferences.theme)
                model.advance()
                model.uiState.first { it.draft.step == SetupStep.PREFERENCE && !it.busy }
                model.setLanguage(LanguagePreference.AR)
                assertTrue(model.uiState.value.preferencesSaving)
                model.advance()
                model.back()
                assertEquals(SetupStep.PREFERENCE, model.uiState.value.draft.step)
                assertEquals(LanguagePreference.SYSTEM, model.uiState.value.preferences.language)
                releaseWrite.complete(Unit)
                model.uiState.first { !it.preferencesSaving && it.preferences.language == LanguagePreference.AR }
                assertEquals(LanguagePreference.AR, stored.observe().first().language)
                failWrite = true
                model.setTheme(ThemePreference.DARK)
                model.uiState.first { !it.preferencesSaving && it.preferencesError }
                assertEquals(ThemePreference.SYSTEM, model.uiState.value.preferences.theme)
                failWrite = false
                model.retryPreferences()
                model.uiState.first { !it.preferencesSaving && it.preferences.theme == ThemePreference.DARK }
                assertFalse(model.uiState.value.preferencesError)
                val reopened =
                    SetupViewModel(repository, gateway, permissions, TestClock(), backgroundScope)
                val restored = reopened.uiState.first { it.ready && it.preferencesReady }
                assertEquals(SetupStep.PREFERENCE, restored.draft.step)
                assertEquals(LanguagePreference.AR, restored.preferences.language)
                assertEquals(ThemePreference.DARK, restored.preferences.theme)
                reopened.advance()
                reopened.uiState.first { it.draft.step == SetupStep.PROFILE && !it.busy }
                reopened.back()
                reopened.uiState.first { it.draft.step == SetupStep.PREFERENCE && !it.busy }
                assertNull(db.dao().tracker())
            } finally {
                db.close()
                folder.toFile().deleteRecursively()
            }
        }


    @Test
    fun invalid_midnight_window_cannot_advance_to_review() = runTest {
        val folder = Files.createTempDirectory("daymark-midnight-step-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val clock = TestClock(at("08:00:00"))
        val service = SetupService(db, clock)
        val draft = SetupDraft(
            "Person", TrackerCategory.WORK, "Office", fixturePlace(),
            fixtureSchedule(arrival = kotlinx.datetime.LocalTime(23, 59)), "0", SetupStep.SCHEDULE
        )
        service.saveDraft(draft, service.startDraftSession())
        val permissions = object : LocationPermissionPort {
            override fun current() = LocationCapabilities(true, false, true, true)
            override suspend fun requestForeground() = error("No permission request")
            override suspend fun requestBackground() = error("No background request")
            override fun openSystemSettings() = Unit
        }
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult = error("No GPS")
        }
        try {
            val model = SetupViewModel(
                repository = dev.daymark.testRepository(
                    setupSource = service,
                    settingsSource = SettingsRepository(db)
                ),
                location = gateway,
                permissions = permissions,
                clock = clock,
                scope = backgroundScope
            )
            model.uiState.first { it.ready && it.preferencesReady && it.draftSaved }
            model.advance()
            assertEquals(SetupStep.SCHEDULE, model.uiState.value.draft.step)
            assertEquals(SetupIssue.INVALID_INPUT, model.uiState.value.issue)
            assertNull(db.dao().tracker())
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun failed_step_save_stays_on_current_step_and_retries_latest_inputs() = runTest {
        val folder = Files.createTempDirectory("daymark-step-failure-")
        val path = folder.resolve("app.db").toString()
        val db = openJvmDatabase(path)
        val service = SetupService(db, TestClock())
        val permissions = object : LocationPermissionPort {
            override fun current() = LocationCapabilities(false, false, false, true)
            override suspend fun requestForeground() = current()
            override suspend fun requestBackground() = error("No background request")
            override fun openSystemSettings() = Unit
        }
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult = error("No GPS")
        }
        val connection = BundledSQLiteDriver().open(path)
        connection.execSQL("PRAGMA busy_timeout=5000")
        try {
            val model = SetupViewModel(
                repository = dev.daymark.testRepository(
                    setupSource = service,
                    settingsSource = SettingsRepository(db)
                ),
                location = gateway,
                permissions = permissions,
                clock = TestClock(),
                scope = backgroundScope
            )
            model.uiState.first { it.ready && it.preferencesReady && it.draftSaved }
            connection.execSQL("ALTER TABLE settings RENAME TO temporarily_unavailable_settings")
            model.advance()
            val failed =
                model.uiState.first { it.issue == SetupIssue.LOCAL_SAVE_FAILED && !it.busy }
            assertEquals(SetupStep.WELCOME, failed.draft.step)
            assertFalse(failed.draftSaved)
            connection.execSQL("ALTER TABLE temporarily_unavailable_settings RENAME TO settings")
            model.retrySave()
            model.uiState.first { it.draft.step == SetupStep.PREFERENCE && it.draftSaved && !it.busy }
            repeat(12) { model.updateDraft(model.uiState.value.draft.copy(displayName = "Person $it")) }
            model.uiState.first { it.draftSaved }
            assertEquals(
                "Person 11",
                (service.observeDraft().first() as SetupDraftLoad.Loaded).draft.displayName
            )
        } finally {
            connection.close()
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun invalid_restored_review_returns_to_first_incomplete_step_without_gps() = runTest {
        val folder = Files.createTempDirectory("daymark-invalid-resume-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val service = SetupService(db, TestClock())
        service.saveDraft(
            SetupDraft(
                displayName = "Saved", category = TrackerCategory.WORK,
                schedule = fixtureSchedule(), step = SetupStep.REVIEW
            ), service.startDraftSession()
        )
        val permissions = object : LocationPermissionPort {
            override fun current() = LocationCapabilities(false, false, false, true)
            override suspend fun requestForeground() = error("No automatic permission request")
            override suspend fun requestBackground() = error("No background request")
            override fun openSystemSettings() = Unit
        }
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult =
                error("No GPS on resume")
        }
        try {
            val model = SetupViewModel(
                repository = dev.daymark.testRepository(
                    setupSource = service,
                    settingsSource = SettingsRepository(db)
                ),
                location = gateway,
                permissions = permissions,
                clock = TestClock(),
                scope = backgroundScope
            )
            val restored = model.uiState.first { it.ready && it.preferencesReady && it.draftSaved }
            assertEquals(SetupStep.PLACE, restored.draft.step)
            assertEquals("Saved", restored.draft.displayName)
            assertNull(db.dao().tracker())
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun guided_steps_acknowledge_storage_and_review_edits_keep_the_balance() = runTest {
        val folder = Files.createTempDirectory("daymark-guided-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val service = SetupService(db, TestClock())
        val permissions = object : LocationPermissionPort {
            override fun current() = LocationCapabilities(true, false, true, true)
            override suspend fun requestForeground() = current()
            override suspend fun requestBackground() = error("No implicit background consent")
            override fun openSystemSettings() = Unit
        }
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult =
                error("No GPS while navigating")
        }
        try {
            val model = SetupViewModel(
                repository = dev.daymark.testRepository(
                    setupSource = service,
                    settingsSource = SettingsRepository(db)
                ),
                location = gateway,
                permissions = permissions,
                clock = TestClock(),
                scope = backgroundScope
            )
            model.uiState.first { it.ready && it.preferencesReady && it.draftSaved }
            assertEquals(SetupStep.WELCOME, model.uiState.value.draft.step)
            model.advance()
            assertEquals(SetupStep.WELCOME, model.uiState.value.draft.step)
            model.uiState.first { it.draft.step == SetupStep.PREFERENCE && !it.busy }
            model.advance()
            val profile = model.uiState.first { it.draft.step == SetupStep.PROFILE && !it.busy }
            assertTrue(profile.draftSaved)
            assertEquals(
                SetupStep.PROFILE,
                (service.observeDraft().first() as SetupDraftLoad.Loaded).draft.step
            )
            model.advance()
            assertEquals(SetupIssue.INVALID_INPUT, model.uiState.value.issue)
            model.updateDraft(
                profile.draft.copy(
                    displayName = "Person",
                    placeName = "Office",
                    place = fixturePlace(),
                    openingBalanceText = "١٢"
                )
            )
            model.advance()
            model.uiState.first { it.draft.step == SetupStep.PLACE && !it.busy }
            model.advance()
            model.uiState.first { it.draft.step == SetupStep.SCHEDULE && !it.busy }
            model.advance()
            model.uiState.first { it.draft.step == SetupStep.REVIEW && !it.busy }
            model.edit(SetupStep.PROFILE)
            model.uiState.first { it.draft.step == SetupStep.PROFILE && !it.busy }
            model.updateDraft(model.uiState.value.draft.copy(category = TrackerCategory.SCHOOL))
            model.advance()
            val reviewed = model.uiState.first { it.draft.step == SetupStep.REVIEW && !it.busy }
            assertEquals("١٢", reviewed.draft.openingBalanceText)
            model.back()
            model.uiState.first { it.draft.step == SetupStep.SCHEDULE && !it.busy }
            model.advance()
            model.uiState.first { it.draft.step == SetupStep.REVIEW && !it.busy }
            model.submit()
            model.submit()
            model.uiState.first { it.completed }
            assertEquals(0, db.dao().tracker()?.openingBalance)
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun failed_initial_load_does_not_expose_an_unusable_form() = runTest {
        val folder = Files.createTempDirectory("daymark-setup-load-")
        val db = openJvmDatabase(folder.resolve("app.db").toString())
        val service = SetupService(db, TestClock())
        service.saveDraft(
            SetupDraft(displayName = "Saved before failure"),
            service.startDraftSession()
        )
        val connection = BundledSQLiteDriver().open(folder.resolve("app.db").toString())
        connection.execSQL("PRAGMA busy_timeout=5000")
        connection.execSQL("ALTER TABLE settings RENAME TO temporarily_unavailable_settings")
        val permissions = object : LocationPermissionPort {
            override fun current() = LocationCapabilities(false, false, false, true)
            override suspend fun requestForeground() = current()
            override suspend fun requestBackground() = current()
            override fun openSystemSettings() = Unit
        }
        val gateway = object : LocationGateway {
            override suspend fun requestFix(budget: Duration): LocationResult =
                error("No GPS during load")
        }
        try {
            val model = SetupViewModel(
                repository = dev.daymark.testRepository(
                    setupSource = service,
                    settingsSource = SettingsRepository(db)
                ),
                location = gateway,
                permissions = permissions,
                clock = TestClock(),
                scope = backgroundScope
            )
            val failed = model.uiState.first { it.issue == SetupIssue.LOCAL_SAVE_FAILED }
            assertFalse(failed.ready)
            model.updateDraft(SetupDraft(displayName = "Must not overwrite unread draft"))
            assertTrue(model.uiState.value.draft.displayName.isEmpty())
            connection.execSQL("ALTER TABLE temporarily_unavailable_settings RENAME TO settings")
            model.retryLoad()
            val loaded = model.uiState.first { it.ready && it.preferencesReady }
            assertEquals("Saved before failure", loaded.draft.displayName)
            assertEquals(TrackerCategory.WORK, loaded.draft.category)
            assertEquals(5, loaded.draft.schedule?.graceMinutes)
            assertEquals(5, loaded.draft.schedule?.days?.size)
            model.retryLoad()
            assertEquals(loaded, model.uiState.value)
        } finally {
            connection.close()
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun denied_location_retains_the_draft_and_never_invents_a_place_or_creates_a_tracker() =
        runTest {
            val folder = Files.createTempDirectory("daymark-setup-vm-")
            val db = openJvmDatabase(folder.resolve("app.db").toString())
            val service = SetupService(db, TestClock())
            var requests = 0
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration): LocationResult {
                    requests++; error("Permission denied")
                }
            }
            val permissions = object : LocationPermissionPort {
                override fun current() = LocationCapabilities(false, false, false, true)
                override suspend fun requestForeground() = current()
                override suspend fun requestBackground(): LocationCapabilities =
                    error("Setup cannot request background permission implicitly")

                override fun openSystemSettings() = Unit
            }
            try {
                val vm = SetupViewModel(
                    repository = dev.daymark.testRepository(
                        setupSource = service,
                        settingsSource = SettingsRepository(db)
                    ),
                    location = gateway,
                    permissions = permissions,
                    clock = TestClock(),
                    scope = backgroundScope
                )
                vm.uiState.first { it.ready && it.preferencesReady }
                vm.updateDraft(
                    SetupDraft(
                        "أحمد / Ahmed",
                        TrackerCategory.WORK,
                        "المكتب",
                        null,
                        fixtureSchedule(),
                        "١٢"
                    )
                )
                service.observeDraft()
                    .first { it is SetupDraftLoad.Loaded && it.draft.displayName == "أحمد / Ahmed" }
                vm.uiState.first { it.draftSaved }
                vm.useCurrentLocation()
                val denied = vm.uiState.first { it.issue == SetupIssue.LOCATION_DENIED && !it.busy }
                assertNull(denied.draft.place)
                assertEquals("أحمد / Ahmed", denied.draft.displayName)
                vm.submit()
                vm.uiState.first { it.issue == SetupIssue.INVALID_INPUT && !it.busy }
                assertNull(db.dao().tracker())
                assertEquals(0, requests)
                assertEquals(
                    "أحمد / Ahmed",
                    (service.observeDraft().first() as SetupDraftLoad.Loaded).draft.displayName
                )
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }

    @Test
    fun an_inaccurate_fix_does_not_register_but_a_fresh_precise_fix_can_complete_without_google() =
        runTest {
            val folder = Files.createTempDirectory("daymark-setup-location-")
            val db = openJvmDatabase(folder.resolve("app.db").toString())
            val clock = TestClock(at("08:00:00"))
            var accuracy = 70.0
            val gateway = object : LocationGateway {
                override suspend fun requestFix(budget: Duration) =
                    fixtureFix(capturedAt = clock.now(), accuracy = accuracy)
            }
            val permissions = object : LocationPermissionPort {
                override fun current() = LocationCapabilities(true, false, true, true)
                override suspend fun requestForeground() = current()
                override suspend fun requestBackground() = current()
                override fun openSystemSettings() = Unit
            }
            try {
                val vm = SetupViewModel(
                    repository = dev.daymark.testRepository(
                        setupSource = SetupService(
                            db,
                            clock
                        ),
                        settingsSource = SettingsRepository(db)
                    ),
                    location = gateway,
                    permissions = permissions,
                    clock = clock,
                    scope = backgroundScope
                )
                vm.uiState.first { it.ready && it.preferencesReady }
                vm.updateDraft(
                    SetupDraft(
                        "Person",
                        TrackerCategory.WORK,
                        "Place",
                        null,
                        fixtureSchedule(),
                        "12"
                    )
                )
                vm.useCurrentLocation()
                assertNull(vm.uiState.first { it.issue == SetupIssue.LOCATION_INACCURATE && !it.busy }.draft.place)
                accuracy = 10.0
                vm.useCurrentLocation()
                val registered = vm.uiState.first { it.draft.place != null && !it.busy }
                assertEquals(0.0, registered.draft.place?.latitude)
                assertEquals(1L, registered.placeRegistrationRevision)
                vm.useCurrentLocation()
                vm.uiState.first { it.placeRegistrationRevision == 2L && !it.busy }
                vm.submit()
                vm.uiState.first { it.completed }
                val tracker = requireNotNull(db.dao().tracker())
                assertNull(db.dao().binding(tracker.id))
                assertFalse(tracker.automaticEnabled)
            } finally {
                db.close(); folder.toFile().deleteRecursively()
            }
        }
}
