package uz.jahonov.ktormonitor.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEvent
import uz.jahonov.ktormonitor.ui.ui.MonitorIconButton
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.TextAction

/** Stands in for the top bar while selecting. Two rows, so it fits the narrow list pane too. */
@Composable
internal fun SelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    allVisibleSelected: Boolean,
    onEvent: (KtorMonitorListUiEvent) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MonitorTheme.colors
    val hasSelection = selectedCount > 0
    Column(modifier.fillMaxWidth().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonitorIconButton(MonitorIcons.Close, "Stop selecting", { onEvent(KtorMonitorListUiEvent.ExitSelection) })
            BasicText(
                "$selectedCount selected of $visibleCount",
                style = MonitorTheme.typography.title.copy(color = colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            MonitorIconButton(
                MonitorIcons.Delete,
                "Delete selected",
                { if (hasSelection) onEvent(KtorMonitorListUiEvent.DeleteSelected) },
                tint = if (hasSelection) colors.error else colors.textDisabled,
            )
            MonitorIconButton(
                MonitorIcons.Share,
                "Share selected",
                { if (hasSelection) onShare() },
                tint = if (hasSelection) colors.text else colors.textDisabled,
            )
        }
        Row(
            Modifier.padding(start = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextAction("Select all", { onEvent(KtorMonitorListUiEvent.SelectAll) }, enabled = !allVisibleSelected)
            TextAction("Clear", { onEvent(KtorMonitorListUiEvent.ClearSelection) }, enabled = hasSelection)
        }
    }
}
