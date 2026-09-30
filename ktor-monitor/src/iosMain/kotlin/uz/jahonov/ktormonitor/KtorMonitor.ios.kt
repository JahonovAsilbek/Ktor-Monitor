package uz.jahonov.ktormonitor

import platform.Foundation.NSBundle
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.data.ktorMonitorDatabase

public fun KtorMonitor(configure: KtorMonitorConfig.() -> Unit = {}): KtorMonitor {
    val config = KtorMonitorConfig().apply(configure)
    return KtorMonitor(
        database = ktorMonitorDatabase(),
        config = config,
        appName = config.appName ?: bundleString("CFBundleName"),
        appVersion = config.appVersion ?: bundleString("CFBundleShortVersionString"),
    )
}

private fun bundleString(key: String): String = NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String ?: ""
