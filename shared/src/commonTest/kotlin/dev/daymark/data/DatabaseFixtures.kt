package dev.daymark.data

import androidx.room3.withWriteTransaction
import dev.daymark.at
import dev.daymark.attendance.AttendanceStatus
import dev.daymark.attendance.CheckCommit
import dev.daymark.attendance.CheckMode
import dev.daymark.attendance.FixVerdict
import dev.daymark.attendance.TrackerCategory
import dev.daymark.fixtureDate
import dev.daymark.fixturePlace
import dev.daymark.fixturePlan
import dev.daymark.fixtureSchedule
import kotlin.time.Instant

suspend fun seedDatabase(
    db: DaymarkDatabase,
    createdAt: Instant = at("08:00:00"),
    trackingStartsAt: Instant = at("09:00:00")
) {
    val key = fixturePlan().key
    db.withWriteTransaction {
        db.dao().insertTracker(
            TrackerEntity(
                key.trackerId,
                "Test person",
                createdAt.toString(),
                trackingStartsAt.toString(),
                10,
                true
            )
        )
        db.dao().putVersion(
            PlanVersionEntity.fromSnapshot(
                key.trackerId, 1, fixtureDate, createdAt,
                TrackerCategory.WORK, fixtureSchedule(), fixturePlace(), "Registered place"
            )
        )
    }
}

fun testCommit(
    time: Instant = at("09:02:00"),
    verdict: FixVerdict = FixVerdict.Accepted(AttendanceStatus.PRESENT, time),
    userEditRevision: Long = 0
) = CheckCommit(
    fixturePlan().key, null, 1, 1, verdict, CheckMode.USER_INITIATED,
    at("09:02:00"), expectedUserEditRevision = userEditRevision
)
