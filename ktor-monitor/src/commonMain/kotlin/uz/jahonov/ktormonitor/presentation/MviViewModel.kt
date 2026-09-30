package uz.jahonov.ktormonitor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base for the monitor's screens: one immutable [state], one entry point for input ([onEvent]), and
 * one-off [effects]. Effects are buffered, so one emitted before the UI starts collecting is still
 * delivered.
 */
public abstract class MviViewModel<S : Any, E : Any, F : Any> internal constructor(initial: S) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    public val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)
    public val effects: Flow<F> = _effects.receiveAsFlow()

    public abstract fun onEvent(event: E)

    protected fun setState(reduce: S.() -> S): Unit = _state.update(reduce)

    protected fun emit(effect: F) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
