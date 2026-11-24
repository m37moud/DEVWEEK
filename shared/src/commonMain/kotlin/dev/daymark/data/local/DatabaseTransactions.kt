package dev.daymark.data.local

import kotlinx.coroutines.flow.Flow

/** Transaction boundaries shared by local operations using the same database. */
interface DatabaseTransactions {
    fun observeChanges(vararg tables: String): Flow<Set<String>>

    suspend fun <T> withReadTransaction(block: suspend () -> T): T
    suspend fun <T> withWriteTransaction(block: suspend () -> T): T
}
