@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.daymark.data

import androidx.room3.Room
import platform.Foundation.*

fun openIosDatabase(path: String = iosDatabasePath()): DaymarkDatabase = configureDatabase(
    Room.databaseBuilder<DaymarkDatabase>(name = path, factory = DaymarkDatabaseConstructor::initialize))

private fun iosDatabasePath(): String {
    val manager = NSFileManager.defaultManager
    val root = requireNotNull(manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null))
    val directory = requireNotNull(root.URLByAppendingPathComponent("Daymark", true))
    require(manager.createDirectoryAtURL(directory, true,
        mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication), null))
    require(directory.setResourceValue(true, NSURLIsExcludedFromBackupKey, null))
    return requireNotNull(directory.URLByAppendingPathComponent("daymark.db")?.path)
}
