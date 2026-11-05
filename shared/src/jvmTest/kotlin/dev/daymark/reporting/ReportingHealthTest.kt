package dev.daymark.reporting

import dev.daymark.attendance.TestClock
import dev.daymark.auth.GoogleAccess
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeGoogleAuth
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.local.reporting.ReportingRuntime
import dev.daymark.data.local.reporting.RoomReportStore
import dev.daymark.data.remote.data_source.data.ktor.GoogleReportTransport
import dev.daymark.data.seedDatabase
import dev.daymark.models.reporting.ReportBinding
import dev.daymark.models.reporting.SyncPassResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class ReportingHealthTest {
    @Test
    fun new_account_does_not_inherit_old_account_sync_status_or_timestamp(): Unit = runBlocking {
        val folder = Files.createTempDirectory("daymark-report-health-");
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        var runtime: ReportingRuntime? = null
        HttpClient(MockEngine { error("Health projection must not call Google") }).use { client ->
            try {
                seedDatabase(db);
                val clock = TestClock();
                val store = RoomReportStore(db, clock)
                val a = ReportBinding(
                    db.dao().tracker()!!.id,
                    store.beginConnection()!!,
                    "a",
                    "report-a",
                    annualTabIds = emptyMap()
                )
                store.bind(a)
                val auth = GoogleAccess(object : NativeGoogleAuth {
                    override fun authorize(
                        interactive: Boolean,
                        completion: (NativeAuthReply) -> Unit
                    ) = error("No authorization")

                    override fun forget(completion: (Boolean) -> Unit) {
                        completion(true)
                    }
                })
                runtime = ReportingRuntime(
                    db,
                    auth,
                    clock,
                    RoomAttendanceRepository(db, clock),
                    httpClient = client,
                    store = store,
                    reportTransport = GoogleReportTransport(client)
                )
                db.dao().putSetting(
                    SettingEntity(
                        "report-last-sync:${a.trackerId}:${a.connectionGeneration}",
                        clock.now().toString()
                    )
                )
                runtime.refreshHealth()
                runtime.refreshHealth(SyncPassResult.Synced)
                assertNotNull(runtime.health.value.lastSync)
                assertEquals(SyncPassResult.Synced, runtime.health.value.result)
                val b = a.copy(
                    connectionGeneration = store.beginConnection()!!,
                    accountReference = "b",
                    spreadsheetId = "report-b"
                )
                store.bind(b); runtime.refreshHealth()
                assertEquals(b, runtime.health.value.binding)
                assertNull(runtime.health.value.lastSync)
                assertEquals(SyncPassResult.Idle, runtime.health.value.result)
            } finally {
                runtime?.close(); db.close(); folder.toFile().deleteRecursively()
            }
        }
    }
}
