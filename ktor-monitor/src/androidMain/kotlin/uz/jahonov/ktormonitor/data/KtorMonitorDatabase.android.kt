package uz.jahonov.ktormonitor.data

import android.content.Context
import androidx.room.Room

private var database: KtorMonitorDatabase? = null
private val lock = Any()

/**
 * One database per process, however many monitors: separate instances on one file would not see
 * each other's writes. In the cache directory: the history is disposable, and the system may clear it.
 */
internal fun ktorMonitorDatabase(context: Context): KtorMonitorDatabase = synchronized(lock) {
    database ?: Room.databaseBuilder<KtorMonitorDatabase>(
        context = context.applicationContext,
        name = context.cacheDir.resolve(KTOR_MONITOR_DB).absolutePath,
    ).buildKtorMonitorDatabase().also { database = it }
}
