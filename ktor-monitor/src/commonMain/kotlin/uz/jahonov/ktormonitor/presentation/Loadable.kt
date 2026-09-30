package uz.jahonov.ktormonitor.presentation

import uz.jahonov.ktormonitor.InternalKtorMonitorApi

/** Data a screen is waiting for. */
@InternalKtorMonitorApi
public sealed interface Loadable<out T> {
    public data object Loading : Loadable<Nothing>
    public data class Ready<out T>(val value: T) : Loadable<T>
}
