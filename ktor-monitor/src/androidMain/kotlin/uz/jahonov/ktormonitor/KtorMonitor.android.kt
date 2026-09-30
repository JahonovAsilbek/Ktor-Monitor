package uz.jahonov.ktormonitor

import android.content.Context
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.data.ktorMonitorDatabase

public fun KtorMonitor(context: Context, configure: KtorMonitorConfig.() -> Unit = {}): KtorMonitor {
    val config = KtorMonitorConfig().apply(configure)
    val app = context.applicationContext
    return KtorMonitor(
        database = ktorMonitorDatabase(app),
        config = config,
        appName = config.appName ?: app.applicationInfo.loadLabel(app.packageManager).toString(),
        appVersion = config.appVersion ?: app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty(),
    )
}
