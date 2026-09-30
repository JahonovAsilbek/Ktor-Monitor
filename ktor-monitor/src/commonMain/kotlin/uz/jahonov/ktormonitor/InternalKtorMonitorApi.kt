package uz.jahonov.ktormonitor

/**
 * Public only so the monitor's own UI module can use it: the screens' states, events and view
 * models, and the models they show. Not for apps, and free to change in any release.
 */
@RequiresOptIn(message = "Ktor Monitor's internal API, for its UI module only; it may change in any release.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class InternalKtorMonitorApi
