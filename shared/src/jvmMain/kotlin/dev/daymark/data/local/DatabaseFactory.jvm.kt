package dev.daymark.data.local

import androidx.room3.Room
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.DaymarkDatabaseConstructor
import dev.daymark.data.local.configureDatabase

fun openJvmDatabase(path: String): DaymarkDatabase = configureDatabase(
    Room.databaseBuilder<DaymarkDatabase>(
        name = path,
        factory = DaymarkDatabaseConstructor::initialize
    )
)
