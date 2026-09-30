package uz.jahonov.ktormonitor

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import uz.jahonov.ktormonitor.bridge.BridgeSessions

/**
 * The monitor as the `KtorMonitorUI` Swift package sees it: strings and closures only, so the package
 * needs no Kotlin types. States, effects and events are JSON. In Swift, one line makes this class
 * the package's bridge: `extension KtorMonitorBridge: @retroactive KtorMonitorUIBridge {}`.
 *
 * Call it from the main thread; the callbacks run there too.
 */
public class KtorMonitorBridge(private val monitor: KtorMonitor) {
    private val sessions = BridgeSessions(
        listViewModel = monitor::listViewModel,
        detailViewModel = monitor::detailViewModel,
        notifier = monitor.notifier,
        onInternalError = monitor.onInternalError,
    )
    private val scope = MainScope()

    public val isNotificationEnabled: Boolean get() = monitor.notifier.isEnabled

    /** Opens the call list. Returns the session id for [send] and [close]. */
    public fun openList(onState: (String) -> Unit, onEffect: (String) -> Unit): String =
        sessions.openList(onState, onEffect)

    /** Opens one call. Returns the session id for [send] and [close]. */
    public fun openCall(callId: String, onState: (String) -> Unit, onEffect: (String) -> Unit): String =
        sessions.openCall(callId, onState, onEffect)

    /** The notification's title and latest lines as they change. Returns the session id for [close]. */
    public fun observeNotification(onUpdate: (String) -> Unit): String = sessions.observeNotification(onUpdate)

    public fun send(sessionId: String, event: String) {
        sessions.send(sessionId, event)
    }

    public fun close(sessionId: String) {
        sessions.close(sessionId)
    }

    /** Deletes the whole history, as the notification's Clear action does. */
    public fun clear() {
        scope.launch { monitor.clear() }
    }
}
