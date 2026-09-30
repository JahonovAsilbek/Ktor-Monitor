package uz.jahonov.ktormonitor.ui.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.presentation.detail.CopyFormat
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEvent
import uz.jahonov.ktormonitor.ui.ui.BottomSheet
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.SheetItem

private class MenuEntry(val title: String, @DrawableRes val icon: Int, val event: KtorMonitorDetailUiEvent)

private val CopyEntries = listOf(
    MenuEntry("Copy URL", MonitorIcons.Copy, KtorMonitorDetailUiEvent.Copy(CopyFormat.URL)),
    MenuEntry("Copy as cURL", MonitorIcons.Copy, KtorMonitorDetailUiEvent.Copy(CopyFormat.CURL)),
    MenuEntry("Copy as wget", MonitorIcons.Copy, KtorMonitorDetailUiEvent.Copy(CopyFormat.WGET)),
    MenuEntry("Copy as text", MonitorIcons.Copy, KtorMonitorDetailUiEvent.Copy(CopyFormat.TEXT)),
)

private val ShareEntries = listOf(
    MenuEntry("Share as .http file", MonitorIcons.Share, KtorMonitorDetailUiEvent.Share(ExportFormat.TEXT)),
    MenuEntry("Share as Markdown", MonitorIcons.Share, KtorMonitorDetailUiEvent.Share(ExportFormat.MARKDOWN)),
    MenuEntry("Share as HAR", MonitorIcons.Share, KtorMonitorDetailUiEvent.Share(ExportFormat.HAR)),
)

/** Copy and share actions for the open call. Each one closes the sheet. */
@Composable
internal fun ShareMenu(onEvent: (KtorMonitorDetailUiEvent) -> Unit, onDismiss: () -> Unit) {
    BottomSheet(title = "Copy or share", onDismiss = onDismiss) { dismiss ->
        val select: (KtorMonitorDetailUiEvent) -> Unit = {
            onEvent(it)
            dismiss()
        }
        MenuCard(CopyEntries, select)
        Spacer(Modifier.height(8.dp))
        MenuCard(ShareEntries, select)
    }
}

@Composable
private fun MenuCard(entries: List<MenuEntry>, onSelect: (KtorMonitorDetailUiEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MonitorTheme.colors.surface),
    ) {
        entries.forEach { entry ->
            SheetItem(title = entry.title, leadingIcon = entry.icon, onClick = { onSelect(entry.event) })
        }
    }
}
