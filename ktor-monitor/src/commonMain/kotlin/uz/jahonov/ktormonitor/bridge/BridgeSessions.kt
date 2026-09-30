package uz.jahonov.ktormonitor.bridge

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import uz.jahonov.ktormonitor.presentation.KtorMonitorNotifier
import uz.jahonov.ktormonitor.presentation.MviViewModel
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailViewModel
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListViewModel

/**
 * The screens a Swift UI has open, each behind a session id. A session owns its view model: it
 * sends every state and effect as JSON to the callbacks, takes events as JSON, and clears the view
 * model when closed. Callbacks run on the main thread; every call is expected there too.
 */
internal class BridgeSessions(
    private val listViewModel: () -> KtorMonitorListViewModel,
    private val detailViewModel: (callId: String) -> KtorMonitorDetailViewModel,
    private val notifier: KtorMonitorNotifier,
    private val onInternalError: (Throwable) -> Unit,
) {
    // A failure would otherwise end the process: Kotlin/Native has no handler to fall back on.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e -> onInternalError(e) })
    private val sessions = mutableMapOf<String, Session>()
    private var lastId = 0

    fun openList(onState: (String) -> Unit, onEffect: (String) -> Unit): String {
        val viewModel = listViewModel()
        return open(
            viewModel = viewModel,
            onState = { onState(BridgeJson.encodeToString(ListStateWire.serializer(), it.toWire())) },
            onEffect = { onEffect(BridgeJson.encodeToString(EffectWire.serializer(), it.toWire())) },
            onEvent = { viewModel.onEvent(BridgeJson.decodeFromString(ListEventWire.serializer(), it).toEvent()) },
        )
    }

    fun openCall(callId: String, onState: (String) -> Unit, onEffect: (String) -> Unit): String {
        val viewModel = detailViewModel(callId)
        return open(
            viewModel = viewModel,
            onState = { onState(BridgeJson.encodeToString(DetailStateWire.serializer(), it.toWire())) },
            onEffect = { onEffect(BridgeJson.encodeToString(EffectWire.serializer(), it.toWire())) },
            onEvent = { viewModel.onEvent(BridgeJson.decodeFromString(DetailEventWire.serializer(), it).toEvent()) },
        )
    }

    /** The notification's lines as they change. Nothing arrives when the notification is off. */
    fun observeNotification(onUpdate: (String) -> Unit): String {
        val job = if (!notifier.isEnabled) Job() else scope.launch {
            notifier.lines.collect { lines ->
                onUpdate(BridgeJson.encodeToString(NotificationWire.serializer(), NotificationWire(KtorMonitorNotifier.TITLE, lines)))
            }
        }
        return add(Session(onEvent = {}, close = { job.cancel() }))
    }

    /** An event for the session's view model. One that cannot be read goes to `onInternalError`. */
    fun send(sessionId: String, event: String) {
        val session = sessions[sessionId] ?: return
        try {
            session.onEvent(event)
        } catch (e: IllegalArgumentException) {
            onInternalError(e)
        }
    }

    fun close(sessionId: String) {
        sessions.remove(sessionId)?.close?.invoke()
    }

    private fun <S : Any, E : Any, F : Any> open(
        viewModel: MviViewModel<S, E, F>,
        onState: (S) -> Unit,
        onEffect: (F) -> Unit,
        onEvent: (String) -> Unit,
    ): String {
        val store = ViewModelStore().apply { put(VIEW_MODEL_KEY, viewModel) }
        val job = scope.launch {
            launch { viewModel.state.collect(onState) }
            launch { viewModel.effects.collect(onEffect) }
        }
        return add(
            Session(onEvent = onEvent) {
                job.cancel()
                store.clear()
            },
        )
    }

    private fun add(session: Session): String {
        val id = "session-${++lastId}"
        sessions[id] = session
        return id
    }

    private class Session(val onEvent: (String) -> Unit, val close: () -> Unit)

    private companion object {
        const val VIEW_MODEL_KEY = "viewModel"
    }
}

internal val BridgeJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
}
