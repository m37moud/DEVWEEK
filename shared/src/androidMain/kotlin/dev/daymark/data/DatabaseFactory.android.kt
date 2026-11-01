package dev.daymark.data

import android.content.Context
import androidx.room3.Room

fun openAndroidDatabase(context: Context): DaymarkDatabase {
    val app = context.applicationContext
    return configureDatabase(Room.databaseBuilder<DaymarkDatabase>(context = app,
        name = app.getDatabasePath("daymark.db").absolutePath, factory = DaymarkDatabaseConstructor::initialize))
}
