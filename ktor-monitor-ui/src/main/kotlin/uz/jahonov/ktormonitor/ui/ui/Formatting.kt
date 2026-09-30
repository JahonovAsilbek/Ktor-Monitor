package uz.jahonov.ktormonitor.ui.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// java.time needs API 26; the app starts at 24.

/** 14:25:01.3 in the device's time zone. */
internal fun formatClock(epochMillis: Long): String = SimpleDateFormat("HH:mm:ss.S", Locale.US).format(Date(epochMillis))

/** Mon, 2026 Sep 30 14:25:01.345 */
internal fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("EEE, yyyy MMM dd HH:mm:ss.SSS", Locale.US).format(Date(epochMillis))

/** 85 ms, 1.24 s, 1 min 5 s. */
internal fun formatDuration(millis: Long): String = when {
    millis < 1_000 -> "$millis ms"
    millis < 60_000 -> String.format(Locale.US, "%.2f s", millis / 1_000.0)
    else -> "${millis / 60_000} min ${millis % 60_000 / 1_000} s"
}

/** 512 B, 1.5 KB, 2.3 MB. */
internal fun formatSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1_024.0 * 1_024))
}
