package uz.jahonov.ktormonitor.data

import android.content.Context
import androidx.room.Room

// The cache directory: the history is disposable, and the system may clear it.
internal fun ktorMonitorDatabase(context: Context): KtorMonitorDatabase =
    Room.databaseBuilder<KtorMonitorDatabase>(
        context = context.applicationContext,
        name = context.cacheDir.resolve(KTOR_MONITOR_DB).absolutePath,
    ).buildKtorMonitorDatabase()
