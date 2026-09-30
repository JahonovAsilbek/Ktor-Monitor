package uz.jahonov.ktormonitor

import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.jahonov.ktormonitor.capture.KtorMonitorCapture
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.data.KtorMonitorDatabase
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.presentation.KtorMonitorNotifier
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailViewModel
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListViewModel

/**
 * The monitor: records the calls of the clients it is attached to and serves the screens that show
 * them. Create one per app (`KtorMonitor(context)` on Android, `KtorMonitor()` on iOS) and keep it
 * for the app's lifetime; should there be more, they share one history.
 */
public class KtorMonitor internal constructor(
    database: KtorMonitorDatabase,
    private val config: KtorMonitorConfig,
    private val appName: String,
    private val appVersion: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> config.onInternalError(e) })
    private val repository = KtorMonitorRepository(database.calls(), config, ::currentTimeMillis)
    private val capture = KtorMonitorCapture(repository, config, scope, ::currentTimeMillis)

    init {
        // Calls that expired while the app was not running go before anyone looks.
        scope.launch { repository.trim() }
    }

    /** What the monitor's notification says. */
    public val notifier: KtorMonitorNotifier = KtorMonitorNotifier(repository, config)

    /**
     * Records every network attempt of [client]. Attach it after any plugin that retries requests
     * (auth refresh and the like): the monitor then sees each attempt with its final headers.
     */
    public fun attach(client: HttpClient) {
        capture.install(client)
    }

    public fun listViewModel(): KtorMonitorListViewModel =
        KtorMonitorListViewModel(repository, appName, appVersion, ::currentTimeMillis, config.onInternalError)

    public fun detailViewModel(callId: String): KtorMonitorDetailViewModel =
        KtorMonitorDetailViewModel(callId, repository, appName, appVersion, ::currentTimeMillis, config.onInternalError)

    internal val onInternalError: (Throwable) -> Unit get() = config.onInternalError

    /** Deletes the whole history. A failure goes to `onInternalError`. */
    public suspend fun clear() {
        try {
            repository.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            config.onInternalError(e)
        }
    }
}

private fun currentTimeMillis() = Clock.System.now().toEpochMilliseconds()
