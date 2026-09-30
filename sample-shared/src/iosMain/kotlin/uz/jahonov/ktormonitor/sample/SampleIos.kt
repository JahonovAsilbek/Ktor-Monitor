package uz.jahonov.ktormonitor.sample

import uz.jahonov.ktormonitor.KtorMonitor
import uz.jahonov.ktormonitor.KtorMonitorBridge

/** What the iOS sample needs: the calls, and the bridge for the KtorMonitorUI package. */
object SampleIos {
    private val monitor = KtorMonitor()
    val api = SampleApi(monitor)
    val bridge = KtorMonitorBridge(monitor)
}
