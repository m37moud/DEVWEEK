package dev.daymark.data.local

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction

class RoomDatabaseTransactions(private val database: DaymarkDatabase) : DatabaseTransactions {
    override suspend fun <T> withReadTransaction(block: suspend () -> T): T =
        database.withReadTransaction { block() }

    override suspend fun <T> withWriteTransaction(block: suspend () -> T): T =
        database.withWriteTransaction { block() }
}
