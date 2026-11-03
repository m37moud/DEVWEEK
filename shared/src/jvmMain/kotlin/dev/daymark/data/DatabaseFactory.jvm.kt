package dev.daymark.data

import androidx.room3.Room

fun openJvmDatabase(path: String): DaymarkDatabase = configureDatabase(
    Room.databaseBuilder<DaymarkDatabase>(
        name = path,
        factory = DaymarkDatabaseConstructor::initialize
    )
)
