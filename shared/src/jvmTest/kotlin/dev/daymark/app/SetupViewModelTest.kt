package dev.daymark.app

import dev.daymark.at
import dev.daymark.attendance.TestClock
import dev.daymark.attendance.TrackerCategory
import dev.daymark.data.openJvmDatabase
import dev.daymark.fixtureFix
import dev.daymark.fixtureSchedule
import dev.daymark.location.LocationCapabilities
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationPermissionPort
import dev.daymark.location.LocationResult
import dev.daymark.ui.SetupIssue
import dev.daymark.ui.SetupViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration

class SetupViewModelTest {
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
                val vm = SetupViewModel(service, gateway, permissions, TestClock(), backgroundScope)
                vm.uiState.first { it.ready }
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
                service.observeDraft().first { it.displayName == "أحمد / Ahmed" }
                vm.uiState.first { it.draftSaved }
                vm.useCurrentLocation()
                val denied = vm.uiState.first { it.issue == SetupIssue.LOCATION_DENIED && !it.busy }
                assertNull(denied.draft.place)
                assertEquals("أحمد / Ahmed", denied.draft.displayName)
                vm.submit()
                vm.uiState.first { it.issue == SetupIssue.INVALID_INPUT && !it.busy }
                assertNull(db.dao().tracker())
                assertEquals(0, requests)
                assertEquals("أحمد / Ahmed", service.observeDraft().first().displayName)
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
                    SetupService(db, clock),
                    gateway,
                    permissions,
                    clock,
                    backgroundScope
                )
                vm.uiState.first { it.ready }
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
