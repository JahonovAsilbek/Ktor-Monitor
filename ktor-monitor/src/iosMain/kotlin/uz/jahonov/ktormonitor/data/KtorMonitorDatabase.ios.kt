package uz.jahonov.ktormonitor.data

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

// Library/Caches: the history is disposable, and the system may clear it.
internal fun ktorMonitorDatabase(): KtorMonitorDatabase =
    Room.databaseBuilder<KtorMonitorDatabase>(name = "${cachesDirectory()}/$KTOR_MONITOR_DB").buildKtorMonitorDatabase()

@OptIn(ExperimentalForeignApi::class)
private fun cachesDirectory(): String =
    requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSCachesDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path,
    )
