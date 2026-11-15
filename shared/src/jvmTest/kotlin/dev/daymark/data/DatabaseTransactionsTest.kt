package dev.daymark.data

import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.DirtyProjectionEntity
import dev.daymark.data.local.RoomDatabaseTransactions
import dev.daymark.data.local.SettingEntity
import dev.daymark.data.local.openJvmDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest

class DatabaseTransactionsTest {
    @Test
    fun nested_write_and_read_share_the_outer_commit() = runTest {
        withDatabase { db ->
            val transactions = RoomDatabaseTransactions(db)
            val saved = transactions.withWriteTransaction {
                db.profileDao().putSetting(SettingEntity("outer", "saved"))
                transactions.withWriteTransaction {
                    db.dirtyProjectionDao()
                        .putDirtyProjection(DirtyProjectionEntity("tracker", "OVERVIEW", 1))
                }
                transactions.withReadTransaction {
                    db.dirtyProjectionDao().dirtyProjection("tracker", "OVERVIEW")?.revision
                }
            }
            assertEquals(1L, saved)
            assertEquals("saved", db.profileDao().setting("outer")?.value)
            assertEquals(
                1L,
                db.dirtyProjectionDao().dirtyProjection("tracker", "OVERVIEW")?.revision
            )
        }
    }

    @Test
    fun outer_failure_rolls_back_nested_writes() = runTest {
        withDatabase { db ->
            val transactions = RoomDatabaseTransactions(db)
            assertFailsWith<IllegalStateException> {
                transactions.withWriteTransaction {
                    db.profileDao().putSetting(SettingEntity("outer", "saved"))
                    transactions.withWriteTransaction {
                        db.dirtyProjectionDao()
                            .putDirtyProjection(DirtyProjectionEntity("tracker", "OVERVIEW", 1))
                    }
                    error("abort_outer_transaction")
                }
            }
            assertNull(db.profileDao().setting("outer"))
            assertNull(db.dirtyProjectionDao().dirtyProjection("tracker", "OVERVIEW"))
        }
    }

    @Test
    fun cancellation_rolls_back_nested_writes() = runTest {
        withDatabase { db ->
            val transactions = RoomDatabaseTransactions(db)
            val written = CompletableDeferred<Unit>()
            val pending = async {
                transactions.withWriteTransaction {
                    db.profileDao().putSetting(SettingEntity("cancelled", "saved"))
                    transactions.withWriteTransaction {
                        db.dirtyProjectionDao()
                            .putDirtyProjection(DirtyProjectionEntity("tracker", "OVERVIEW", 1))
                    }
                    written.complete(Unit)
                    awaitCancellation()
                }
            }
            written.await()
            pending.cancelAndJoin()
            assertNull(db.profileDao().setting("cancelled"))
            assertNull(db.dirtyProjectionDao().dirtyProjection("tracker", "OVERVIEW"))
        }
    }

    private suspend fun withDatabase(block: suspend (DaymarkDatabase) -> Unit) {
        val folder = Files.createTempDirectory("daymark-transactions-")
        val db = openJvmDatabase(folder.resolve("test.db").toString())
        try {
            block(db)
        } finally {
            db.close()
            folder.toFile().deleteRecursively()
        }
    }
}
