package uz.jahonov.ktormonitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.jahonov.ktormonitor.KtorMonitor
import uz.jahonov.ktormonitor.ui.detail.KtorMonitorDetailScreen
import uz.jahonov.ktormonitor.ui.list.KtorMonitorListScreen
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme

/** List and detail: side by side on a wide screen, one after the other on a phone. */
@Composable
internal fun KtorMonitorApp(onClose: () -> Unit) {
    var openCallId by rememberSaveable { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(MonitorTheme.colors.background)) {
        if (maxWidth >= TWO_PANE_MIN_WIDTH) {
            Row(Modifier.fillMaxSize()) {
                KtorMonitorListScreen(
                    selectedCallId = openCallId,
                    onOpenCall = { openCallId = it },
                    onClose = onClose,
                    modifier = Modifier.weight(0.4f),
                )
                Spacer(Modifier.width(1.dp).fillMaxHeight().background(MonitorTheme.colors.border))
                openCallId?.let { id ->
                    CallDetail(id, onBack = null, onGone = { openCallId = null }, modifier = Modifier.weight(0.6f))
                } ?: Spacer(Modifier.weight(0.6f))
            }
        } else {
            val id = openCallId
            if (id == null) {
                KtorMonitorListScreen(selectedCallId = null, onOpenCall = { openCallId = it }, onClose = onClose)
            } else {
                BackHandler { openCallId = null }
                CallDetail(id, onBack = { openCallId = null }, onGone = { openCallId = null })
            }
        }
    }
}

/**
 * One call's detail, with view models of its own: they live through a rotation, and go when another
 * call opens, so a detail left behind stops watching the database. All state inside starts afresh
 * for each call.
 */
@Composable
private fun CallDetail(id: String, onBack: (() -> Unit)?, onGone: () -> Unit, modifier: Modifier = Modifier) {
    val stores = viewModel<DetailStores>()
    key(id) {
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = stores.storeFor(id) } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            KtorMonitorDetailScreen(callId = id, onBack = onBack, onGone = onGone, modifier = modifier)
        }
    }
}

/** The view models of the detail shown; those of the call shown before are cleared. */
internal class DetailStores : ViewModel() {
    private var current: Pair<String, ViewModelStore>? = null

    fun storeFor(id: String): ViewModelStore {
        current?.let { (currentId, store) -> if (currentId == id) return store else store.clear() }
        return ViewModelStore().also { current = id to it }
    }

    override fun onCleared() {
        current?.second?.clear()
    }
}

internal val LocalKtorMonitor = staticCompositionLocalOf<KtorMonitor> { error("No KtorMonitor provided") }

/** A view model kept by the screen's owner. */
@Composable
internal inline fun <reified VM : ViewModel> monitorViewModel(crossinline create: KtorMonitor.() -> VM): VM {
    val monitor = LocalKtorMonitor.current
    return viewModel { monitor.create() }
}

private val TWO_PANE_MIN_WIDTH = 600.dp
