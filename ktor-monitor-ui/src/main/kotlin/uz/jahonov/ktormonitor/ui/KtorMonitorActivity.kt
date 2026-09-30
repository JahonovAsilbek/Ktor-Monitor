package uz.jahonov.ktormonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme

/** The monitor, light or dark with the system. English only: it is a developer tool. */
internal class KtorMonitorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val monitor = KtorMonitorUi.monitor ?: return finish()
        setContent {
            CompositionLocalProvider(LocalKtorMonitor provides monitor) {
                MonitorTheme {
                    KtorMonitorApp(onClose = ::finish)
                }
            }
        }
    }
}
