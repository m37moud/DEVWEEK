package dev.daymark.android

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daymark.data.openAndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UpgradeJourneyTest {
    @Test fun synthetic_prior_schema_fixture_upgrades_without_erasing_saved_attendance() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="upgrade-journey-${UUID.randomUUID()}.db"
        val fixture=AttendanceTestApp(context,name)
        try {
            fixture.graph.attendance.manualCheck()
            val tracker=fixture.db.dao().tracker()!!.id
            val records=fixture.db.dao().days(tracker)
            Assert.assertTrue(records.isNotEmpty())
            val pending=fixture.db.dao().dirtyProjections(tracker)
            // Test-only fixture of exported schema 1; this is not an older shipped application.
            fixture.db.useWriterConnection { connection ->
                connection.executeSQL("ALTER TABLE trackers DROP COLUMN changeRevision")
                connection.executeSQL("ALTER TABLE balance_adjustments DROP COLUMN note")
                connection.executeSQL("UPDATE room_master_table SET identity_hash='f1b6d40ea633f7fa529070fcf522c02c' WHERE id=42")
                connection.executeSQL("PRAGMA user_version=1")
            }
            fixture.db.close()
            val upgraded=openAndroidDatabase(context,name)
            try {
                Assert.assertEquals(records,upgraded.dao().days(tracker))
                Assert.assertEquals(pending,upgraded.dao().dirtyProjections(tracker))
                Assert.assertEquals(10,upgraded.dao().tracker()!!.openingBalance)
            } finally { upgraded.close() }
        } finally { fixture.db.close(); context.deleteDatabase(name) }
    }
}
