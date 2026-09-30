package uz.jahonov.ktormonitor.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.model.CallSummary

/**
 * What the monitor's notification says, the same on both platforms. Each platform posts it under
 * one fixed id, so an update replaces the one shown, and a tap opens the monitor.
 */
public class KtorMonitorNotifier internal constructor(repository: KtorMonitorRepository, config: KtorMonitorConfig) {
    public val isEnabled: Boolean = config.showNotification

    /** The latest calls, newest first: `200 GET /cards`, `⏳ GET /cards`, `❌ GET /cards`. */
    public val lines: Flow<List<String>> = repository.calls(limit = LATEST)
        .map { calls -> calls.map { it.notificationLine() } }
        .distinctUntilChanged()

    private fun CallSummary.notificationLine(): String {
        val status = when {
            error != null -> "❌"
            responseCode == null -> "⏳"
            else -> responseCode.toString()
        }
        return "$status $method $path"
    }

    public companion object {
        public const val TITLE: String = "Recording network activity"
        public const val LATEST: Int = 5
    }
}
