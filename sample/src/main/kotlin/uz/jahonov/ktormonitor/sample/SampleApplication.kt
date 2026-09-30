package uz.jahonov.ktormonitor.sample

import android.app.Application
import android.util.Log
import uz.jahonov.ktormonitor.KtorMonitor
import uz.jahonov.ktormonitor.ui.KtorMonitorUi

class SampleApplication : Application() {
    lateinit var api: SampleApi
        private set

    override fun onCreate() {
        super.onCreate()
        val monitor = KtorMonitor(this) {
            onInternalError = { Log.w("KtorMonitor", "Monitor failure", it) }
        }
        api = SampleApi(monitor)
        KtorMonitorUi.install(this, monitor)
    }
}
