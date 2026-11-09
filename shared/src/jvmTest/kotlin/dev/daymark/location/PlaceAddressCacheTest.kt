package dev.daymark.location

import dev.daymark.data.local.address.RoomPlaceAddressDataSource
import dev.daymark.data.local.deleteAllLocalRows
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.settings.AppLanguage
import androidx.room3.withWriteTransaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.room3.useWriterConnection
import androidx.room3.executeSQL

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceAddressCacheTest {
    private val request = PlaceAddressRequest(30.0, 31.0, AppLanguage.EN)

    @Test
    fun successful_addresses_survive_restart_and_language_has_its_own_entry() = runTest {
        val folder = Files.createTempDirectory("daymark-address-")
        val path = folder.resolve("test.db").toString()
        var db = openJvmDatabase(path)
        try {
            seedDatabase(db)
            val source = RoomPlaceAddressDataSource(db, PlaceGeocoder { "Test street" })
            assertEquals("Test street", source.resolve(request))
            db.close()
            db = openJvmDatabase(path)
            val cached = RoomPlaceAddressDataSource(db, PlaceGeocoder { null })
            assertEquals("Test street", cached.resolve(request))
            assertNull(cached.resolve(request.copy(language = AppLanguage.AR)))
            db.withWriteTransaction { db.dao().deleteAllLocalRows() }
            assertNull(cached.resolve(request))
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun lookup_completing_after_local_deletion_cannot_repopulate_the_cache() = runTest {
        val folder = Files.createTempDirectory("daymark-address-delete-")
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db)
            val started = CompletableDeferred<Unit>()
            val reply = CompletableDeferred<String?>()
            val source = RoomPlaceAddressDataSource(db, PlaceGeocoder {
                started.complete(Unit)
                reply.await()
            })
            val lookup = async { source.resolve(request) }
            started.await()
            db.withWriteTransaction { db.dao().deleteAllLocalRows() }
            reply.complete("Test street")
            assertNull(lookup.await())
            seedDatabase(db)
            assertNull(RoomPlaceAddressDataSource(db, PlaceGeocoder { null }).resolve(request))
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }

    @Test
    fun empty_results_and_timeouts_are_not_saved_and_storage_failure_returns_unavailable() = runTest {
        val folder = Files.createTempDirectory("daymark-address-failure-")
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db)
            assertNull(RoomPlaceAddressDataSource(db, PlaceGeocoder { "  " }).resolve(request))
            val started = CompletableDeferred<Unit>()
            val timeout = async {
                RoomPlaceAddressDataSource(db, PlaceGeocoder {
                    started.complete(Unit)
                    awaitCancellation()
                }).resolve(request)
            }
            started.await()
            advanceTimeBy(10_001)
            assertNull(timeout.await())
            assertNull(db.dao().placeAddress(request.latitude, request.longitude, request.language.tag))
            db.useWriterConnection {
                it.executeSQL("CREATE TRIGGER reject_address BEFORE INSERT ON place_addresses BEGIN SELECT RAISE(ABORT, 'test cache fault'); END")
            }
            assertNull(RoomPlaceAddressDataSource(db, PlaceGeocoder { "Test street" }).resolve(request))
            assertNull(db.dao().placeAddress(request.latitude, request.longitude, request.language.tag))
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }
}
