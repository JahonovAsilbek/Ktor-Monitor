package uz.jahonov.ktormonitor.data

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * One database per process, however many monitors: separate instances on one file would not see
 * each other's writes. In Library/Caches: the history is disposable, and the system may clear it.
 */
private val database: KtorMonitorDatabase by lazy {
    Room.databaseBuilder<KtorMonitorDatabase>(name = "${cachesDirectory()}/$KTOR_MONITOR_DB").buildKtorMonitorDatabase()
}

internal fun ktorMonitorDatabase(): KtorMonitorDatabase = database

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
