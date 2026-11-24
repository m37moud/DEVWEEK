package dev.daymark.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String) {
    override fun toString() = "SettingEntity(key=$key, value=<redacted>)"
}
