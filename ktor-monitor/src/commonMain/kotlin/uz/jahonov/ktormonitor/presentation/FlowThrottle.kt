package uz.jahonov.ktormonitor.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * Passes the first value at once, then at most one per [periodMillis]: the latest. Unlike a debounce,
 * a steady stream of values still gets through, and the last one always arrives.
 */
internal fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMillis)
}
