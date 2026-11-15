package dev.daymark.data.local

/** Transaction boundaries shared by local operations using the same database. */
interface DatabaseTransactions {
    suspend fun <T> withReadTransaction(block: suspend () -> T): T
    suspend fun <T> withWriteTransaction(block: suspend () -> T): T
}
