@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.daymark.data.local

import androidx.room3.Room
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.DaymarkDatabaseConstructor
import dev.daymark.data.local.configureDatabase
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

fun openIosDatabase(path: String = iosDatabasePath()): DaymarkDatabase = configureDatabase(
    Room.databaseBuilder<DaymarkDatabase>(
        name = path,
        factory = DaymarkDatabaseConstructor::initialize
    )
)

private fun iosDatabasePath(): String {
    val manager = NSFileManager.defaultManager
    val root = requireNotNull(
        manager.URLForDirectory(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            null,
            true,
            null
        )
    )
    val directory = requireNotNull(root.URLByAppendingPathComponent("Daymark", true))
    require(
        manager.createDirectoryAtURL(
            directory, true,
            mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication), null
        )
    )
    require(directory.setResourceValue(true, NSURLIsExcludedFromBackupKey, null))
    return requireNotNull(directory.URLByAppendingPathComponent("daymark.db")?.path)
}
