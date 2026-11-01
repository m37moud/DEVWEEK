package dev.daymark.reporting

import androidx.room3.withWriteTransaction
import dev.daymark.*
import dev.daymark.attendance.*
import dev.daymark.data.*
import kotlinx.coroutines.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class SyncCoordinatorTest {
    private class Server : ReportTransport {
        val cells = mutableMapOf<String,List<List<JsonPrimitive>>>()
        var delayed: RawPatch? = null
        var ambiguous = false
        var writes = 0
        var reads = 0
        var onWrite: suspend () -> Unit = {}
        var reject = false
        override suspend fun prepareTemplate(report: RemoteReport,trackerId: String,zoneId: String,years: Set<Int>,token: String) =
            report.copy(tabIds=report.tabIds+mapOf("Overview" to 1,"Time off" to 2)+years.associate { "Attendance $it" to (it+10) })
        override suspend fun read(report: RemoteReport,ranges: List<String>,token: String): RemoteSnapshot {
            reads++; return RemoteSnapshot(ranges.associateWith { cells[it].orEmpty() })
        }
        override suspend fun write(report: RemoteReport,patch: RawPatch,token: String): RemoteWriteOutcome {
            writes++; onWrite()
            return HttpClient(MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("values:batchUpdateByDataFilter"))
                val body=Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals("RAW",body.getValue("valueInputOption").jsonPrimitive.content)
                val received=RawPatch(body.getValue("data").jsonArray.map { item ->
                    val data=item.jsonObject
                    val grid=data.getValue("dataFilter").jsonObject.getValue("gridRange").jsonObject
                    val title=report.tabIds.entries.single { it.value==grid.getValue("sheetId").jsonPrimitive.int }.key
                    val start=grid.getValue("startRowIndex").jsonPrimitive.int+1
                    val end=grid.getValue("endRowIndex").jsonPrimitive.int
                    val column=('A'.code+grid.getValue("endColumnIndex").jsonPrimitive.int-1).toChar()
                    RawRange("'$title'!A$start:$column$end",data.getValue("values").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive } })
                })
                val status=when {
                    reject -> HttpStatusCode.TooManyRequests
                    ambiguous -> { ambiguous=false; delayed=received; HttpStatusCode.ServiceUnavailable }
                    else -> { received.ranges.forEach { cells[it.a1]=it.cells }; HttpStatusCode.OK }
                }
                respond("{}",status,headersOf(HttpHeaders.ContentType,"application/json"))
            }).use { GoogleReportTransport(it).write(report,patch,token) }
        }

        fun applyDelayed() { delayed!!.ranges.forEach { cells[it.a1]=it.cells } }
    }
    @Test fun later_session_repairs_old_write_after_new_ack(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-sync-"); var db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val clock=TestClock(); val repo=RoomAttendanceRepository(db,clock); repo.completeCheck(testCommit())
            var store=RoomReportStore(db,clock)
            val binding=ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=mapOf(2026 to 3))
            store.bind(binding)
            val server=Server(); var sync=SyncCoordinator(store,server,clock,token={ "token" },zoneId={ "UTC" },retry=SyncRetryPolicy { kotlin.time.Duration.ZERO })
            server.ambiguous=true
            assertEquals(SyncPassResult.Pending,sync.runPass(SyncTrigger.LAUNCH))
            db.close(); db=openJvmDatabase(folder.resolve("test.db").toString())
            store=RoomReportStore(db,clock)
            sync=SyncCoordinator(store,server,clock,token={ "token" },zoneId={ "UTC" },retry=SyncRetryPolicy { kotlin.time.Duration.ZERO })
            val key=ProjectionKey.Day(fixturePlan().key)
            db.withWriteTransaction { db.dao().markDirty(binding.trackerId,key.storageKey()); db.dao().markDirty(binding.trackerId,"OVERVIEW") }
            clock.value=at("09:03:00")
            assertEquals(SyncPassResult.Synced,sync.runPass(SyncTrigger.RESUME))
            val latest=store.localProjections(store.activeBinding()!!).first { it.key==key }.revision
            server.applyDelayed()
            val a1="'Attendance 2026'!A302:O302"
            assertTrue(server.cells.getValue(a1).single()[11].content.toLong() < latest)
            assertEquals(SyncPassResult.Synced,sync.runPass(SyncTrigger.RESUME))
            assertEquals(latest,server.cells.getValue(a1).single()[11].content.toLong())
            assertTrue(server.reads >= 2)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun obsolete_callback_cannot_clean_new_binding(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-sync-generation-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val clock=TestClock(); val store=RoomReportStore(db,clock)
            val a=ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=mapOf(2026 to 3))
            store.bind(a); val server=Server()
            server.onWrite = { store.bind(a.copy(connectionGeneration=store.beginConnection()!!,accountReference="b",spreadsheetId="b")) }
            val sync=SyncCoordinator(store,server,clock,token={ "token" },zoneId={ "UTC" })
            assertEquals(SyncPassResult.StaleBinding,sync.runPass(SyncTrigger.LAUNCH))
            val b=store.activeBinding()!!
            assertEquals("b",b.accountReference)
            assertNotNull(store.buildBatch(b,100))
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }
    @Test fun transient_dispatches_stop_after_six_attempts_in_one_session(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-sync-bounded-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val clock=TestClock(); val store=RoomReportStore(db,clock)
            store.bind(ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=emptyMap()))
            val server=Server().apply { reject=true }
            val sync=SyncCoordinator(store,server,clock,token={ "token" },zoneId={ "UTC" },retry=SyncRetryPolicy { kotlin.time.Duration.ZERO })
            repeat(8) { attempt ->
                assertEquals(SyncPassResult.Pending,sync.runPass(if (attempt==0) SyncTrigger.LAUNCH else SyncTrigger.RETRY))
                clock.value += 2.minutes
            }
            assertEquals(6,server.writes)
            assertTrue(db.dao().dirtyProjections(db.dao().tracker()!!.id).any { it.acknowledgedRevision < it.revision })
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

    @Test fun a_new_binding_is_verified_even_when_local_change_is_the_first_trigger(): Unit = runBlocking {
        val folder=Files.createTempDirectory("daymark-sync-first-"); val db=openJvmDatabase(folder.resolve("test.db").toString())
        try {
            seedDatabase(db); val clock=TestClock(); val store=RoomReportStore(db,clock)
            store.bind(ReportBinding(db.dao().tracker()!!.id,store.beginConnection()!!,"a","r",annualTabIds=emptyMap()))
            val server=Server()
            val sync=SyncCoordinator(store,server,clock,token={ "token" },zoneId={ "UTC" })
            assertEquals(SyncPassResult.Synced,sync.runPass(SyncTrigger.LOCAL_CHANGE))
            assertTrue(server.reads>0)
        } finally { db.close(); folder.toFile().deleteRecursively() }
    }

}
