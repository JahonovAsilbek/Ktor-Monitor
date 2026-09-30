package uz.jahonov.ktormonitor.sample

import android.app.Application
import uz.jahonov.ktormonitor.KtorMonitor
import uz.jahonov.ktormonitor.ui.KtorMonitorUi

class SampleApplication : Application() {
    lateinit var api: SampleApi
        private set

    override fun onCreate() {
        super.onCreate()
        val monitor = KtorMonitor(this)
        api = SampleApi(monitor)
        KtorMonitorUi.install(this, monitor)
    }
}
