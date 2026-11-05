package dev.daymark.data.local

import android.content.Context
import androidx.room3.Room
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.DaymarkDatabaseConstructor
import dev.daymark.data.local.configureDatabase

fun openAndroidDatabase(context: Context, name: String = "daymark.db"): DaymarkDatabase {
    val app = context.applicationContext
    return configureDatabase(
        Room.databaseBuilder<DaymarkDatabase>(
            context = app,
            name = app.getDatabasePath(name).absolutePath,
            factory = DaymarkDatabaseConstructor::initialize
        )
    )
}
