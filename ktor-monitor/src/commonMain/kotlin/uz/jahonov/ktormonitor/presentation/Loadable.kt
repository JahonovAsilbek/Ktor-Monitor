package uz.jahonov.ktormonitor.presentation

/** Data a screen is waiting for. */
public sealed interface Loadable<out T> {
    public data object Loading : Loadable<Nothing>
    public data class Ready<out T>(val value: T) : Loadable<T>
}
