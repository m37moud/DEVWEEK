package dev.daymark.attendance

import dev.daymark.data.local.attendance.RoomAttendanceRepository
import dev.daymark.data.local.openJvmDatabase
import dev.daymark.data.seedDatabase
import dev.daymark.location.LocationGateway
import dev.daymark.location.LocationResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

/** Separate test process parks after the real claim commits; the parent kills it without closing Room. */
fun main(args: Array<String>): Unit = runBlocking {
    val db = openJvmDatabase(args[0])
    seedDatabase(db)
    val clock = TestClock()
    val gateway = object : LocationGateway {
        override suspend fun requestFix(budget: Duration): LocationResult {
            Files.writeString(Path.of(args[1]), "claim committed")
            awaitCancellation()
        }
    }
    AttendanceService(
        RoomAttendanceRepository(db, clock),
        gateway,
        clock
    ).runAutomatic(dev.daymark.fixturePlan().key)
    error("The crash probe must remain suspended during acquisition")
}
