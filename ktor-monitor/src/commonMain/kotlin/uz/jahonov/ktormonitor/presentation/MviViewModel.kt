package uz.jahonov.ktormonitor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base for the monitor's screens: one immutable [state], one entry point for input ([onEvent]), and
 * one-off [effects]. Effects are buffered, so one emitted before the UI starts collecting is still
 * delivered.
 *
 * Work runs through [launch] and [collectSafely]: a failure (a database error, a bug in a parser)
 * goes to [onError] and never reaches the app, which would otherwise crash.
 */
public abstract class MviViewModel<S : Any, E : Any, F : Any> internal constructor(
    initial: S,
    private val onError: (Throwable) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    public val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)
    public val effects: Flow<F> = _effects.receiveAsFlow()

    private val errors = CoroutineExceptionHandler { _, e -> onError(e) }

    public abstract fun onEvent(event: E)

    protected fun setState(reduce: S.() -> S): Unit = _state.update(reduce)

    protected fun emit(effect: F) {
        launch { _effects.send(effect) }
    }

    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch(errors, block = block)

    /** Collects this flow for as long as the view model lives; a failure ends it. */
    protected fun <T> Flow<T>.collectSafely(): Job = catch { onError(it) }.launchIn(viewModelScope)
}
