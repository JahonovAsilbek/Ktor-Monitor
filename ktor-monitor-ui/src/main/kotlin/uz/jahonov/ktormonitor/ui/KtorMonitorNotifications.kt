package uz.jahonov.ktormonitor.ui

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uz.jahonov.ktormonitor.KtorMonitor
import uz.jahonov.ktormonitor.presentation.KtorMonitorNotifier

/**
 * One notification with the latest calls, replaced in place as calls arrive. Tapping it opens the
 * monitor; Clear empties the history. Without the notification permission nothing is posted: the
 * monitor's list asks for it.
 */
internal class KtorMonitorNotifications(private val application: Application, monitor: KtorMonitor) {
    private val notifier = monitor.notifier
    private val manager = NotificationManagerCompat.from(application)

    /** What the notification says now: posted again once the permission is granted. */
    private var lines: List<String> = emptyList()

    fun start() {
        if (!notifier.isEnabled) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        createChannel()
        notifier.lines
            .onEach(::post)
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    fun repost() = post(lines)

    private fun post(lines: List<String>) {
        this.lines = lines
        if (lines.isEmpty()) return manager.cancel(NOTIFICATION_ID)
        if (!canPost(application)) return

        val style = NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) }
        val notification = NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(R.drawable.ktormonitor_ic_notification)
            .setContentTitle(KtorMonitorNotifier.TITLE)
            .setContentText(lines.first())
            .setStyle(style)
            .setOnlyAlertOnce(true)
            // The lines carry paths and hosts: on the lock screen only the title shows.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(application, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ktormonitor_ic_notification)
                    .setContentTitle(KtorMonitorNotifier.TITLE)
                    .build(),
            )
            .setContentIntent(openIntent())
            .addAction(0, "Clear", clearIntent())
            .build()
        try {
            @Suppress("MissingPermission") // checked in canPost
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was taken back between the check and the post; the next call retries.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Network monitor", NotificationManager.IMPORTANCE_LOW)
        application.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openIntent() = PendingIntent.getActivity(
        application, 0, KtorMonitorUi.openIntent(application), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun clearIntent() = PendingIntent.getBroadcast(
        application, 0, Intent(application, ClearCallsReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "ktormonitor"
        private const val NOTIFICATION_ID = 0x4e4d // "NM"

        fun canPost(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

/** The notification's Clear action. The notification goes away with the last call. */
internal class ClearCallsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                KtorMonitorUi.monitor?.clear()
            } finally {
                pending.finish()
            }
        }
    }
}
