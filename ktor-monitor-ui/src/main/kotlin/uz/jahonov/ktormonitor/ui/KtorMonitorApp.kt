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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
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
                    KtorMonitorDetailScreen(
                        callId = id,
                        onBack = null,
                        onGone = { openCallId = null },
                        modifier = Modifier.weight(0.6f),
                    )
                } ?: Spacer(Modifier.weight(0.6f))
            }
        } else {
            val id = openCallId
            if (id == null) {
                KtorMonitorListScreen(selectedCallId = null, onOpenCall = { openCallId = it }, onClose = onClose)
            } else {
                BackHandler { openCallId = null }
                KtorMonitorDetailScreen(callId = id, onBack = { openCallId = null }, onGone = { openCallId = null })
            }
        }
    }
}

internal val LocalKtorMonitor = staticCompositionLocalOf<KtorMonitor> { error("No KtorMonitor provided") }

/** A view model kept by the screen; [key] tells apart the detail screens of different calls. */
@Composable
internal inline fun <reified VM : ViewModel> monitorViewModel(key: String? = null, crossinline create: KtorMonitor.() -> VM): VM {
    val monitor = LocalKtorMonitor.current
    return viewModel(key = key) { monitor.create() }
}

private val TWO_PANE_MIN_WIDTH = 600.dp
