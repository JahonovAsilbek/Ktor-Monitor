package uz.jahonov.ktormonitor.ui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/** Handles one-off effects while the screen is resumed, so nothing fires from the background. */
@Composable
internal fun <F> CollectEffects(effects: Flow<F>, onEffect: (F) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler = rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            effects.collect { handler.value(it) }
        }
    }
}
