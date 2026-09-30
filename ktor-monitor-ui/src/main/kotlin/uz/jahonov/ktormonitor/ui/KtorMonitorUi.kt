package uz.jahonov.ktormonitor.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import uz.jahonov.ktormonitor.KtorMonitor

/** The monitor's screens on Android, and the ways in: a notification and a shake of the device. */
public object KtorMonitorUi {
    internal var monitor: KtorMonitor? = null
        private set
    internal var notifications: KtorMonitorNotifications? = null
        private set

    /**
     * Call once, in `Application.onCreate`, with the app's monitor. Only there: after the process is
     * killed, the monitor screen or the notification can come back before any activity of the app,
     * and without the monitor installed by then, they have nothing to show.
     */
    public fun install(application: Application, monitor: KtorMonitor, shakeToOpen: Boolean = true) {
        if (this.monitor != null) return
        this.monitor = monitor
        notifications = KtorMonitorNotifications(application, monitor).also { it.start() }
        if (shakeToOpen) ShakeToOpen(application).start()
    }

    /** Opens the monitor over the app. */
    public fun open(context: Context) {
        context.startActivity(openIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun openIntent(context: Context) = Intent(context, KtorMonitorActivity::class.java)
}
