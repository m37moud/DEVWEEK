package dev.daymark.attendance

import dev.daymark.data.openJvmDatabase
import dev.daymark.fixturePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessDeathClaimTest {
    @Test
    fun forcible_process_death_during_acquisition_cannot_release_the_committed_claim() = runTest {
        val folder = Files.createTempDirectory("daymark-crash-test-")
        val dbPath = folder.resolve("attendance.db")
        val ready = folder.resolve("claim-ready")
        val output = folder.resolve("child.log")
        val child = ProcessBuilder(
            System.getProperty("java.home") + "/bin/java", "-Xmx128m", "-cp", runtimeClasspath(),
            "dev.daymark.attendance.ClaimCrashProbeKt", dbPath.toString(), ready.toString(),
        ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            withContext(Dispatchers.IO) {
                withTimeout(20_000) {
                    while (!Files.exists(ready)) {
                        check(child.isAlive) {
                            "Crash fixture exited before claiming: ${
                                Files.readString(
                                    output
                                )
                            }"
                        }
                        delay(20)
                    }
                }
                child.destroyForcibly()
                assertTrue(child.waitFor(10, TimeUnit.SECONDS))
            }
            val db = openJvmDatabase(dbPath.toString())
            try {
                val clock = TestClock();
                val repo = RoomAttendanceRepository(db, clock)
                val key = fixturePlan().key
                assertIs<ClaimResult.AlreadyConsumed>(
                    repo.claimAutomatic(
                        key,
                        "after-crash",
                        clock.now()
                    )
                )
                repo.reconcile(clock.now())
                assertEquals(AutomaticState.INTERRUPTED, repo.loadDay(key)?.automaticState)
                assertNull(repo.loadDay(key)?.evidence)
                assertEquals(1, db.dao().countDay(key.trackerId, key.date.toString()))
            } finally {
                db.close()
            }
        } finally {
            if (child.isAlive) {
                child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS)
            }
            folder.toFile().deleteRecursively()
        }
    }

    private fun runtimeClasspath(): String =
        requireNotNull(System.getProperty("daymark.test.classpath")) {
            "Test runtime classpath unavailable"
        }
}
