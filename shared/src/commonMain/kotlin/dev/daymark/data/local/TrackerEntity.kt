package dev.daymark.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "trackers", indices = [Index(value = ["singleton"], unique = true)])
data class TrackerEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val createdAt: String,
    val trackingStartsAt: String,
    val openingBalance: Int,
    val automaticEnabled: Boolean,
    val singleton: Int = 0,
    @ColumnInfo(defaultValue = "0") val changeRevision: Long = 0
) {
    override fun toString() = "TrackerEntity(id=$id, metadata=<redacted>)"
}
