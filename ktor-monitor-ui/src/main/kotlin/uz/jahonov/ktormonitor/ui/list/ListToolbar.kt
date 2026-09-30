package uz.jahonov.ktormonitor.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.presentation.list.CallFilters
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEvent
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiState
import uz.jahonov.ktormonitor.ui.ui.Chip
import uz.jahonov.ktormonitor.ui.ui.MonitorIcon
import uz.jahonov.ktormonitor.ui.ui.MonitorIconButton
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.TextAction

/** The list's top bar: the title with four actions next to it, more than [MonitorToolbar] holds. */
@Composable
internal fun ListToolbar(
    state: KtorMonitorListUiState,
    onEvent: (KtorMonitorListUiEvent) -> Unit,
    onClose: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MonitorTheme.colors
    val activeIcon = colors.accent
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonitorIconButton(MonitorIcons.Close, "Close", onClose)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            BasicText(
                "Network monitor",
                style = MonitorTheme.typography.headline.copy(color = colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                if (state.totalCount == 1) "1 call" else "${state.totalCount} calls",
                style = MonitorTheme.typography.caption.copy(color = colors.textSecondary),
                maxLines = 1,
            )
        }
        MonitorIconButton(
            MonitorIcons.Search,
            if (state.isSearchVisible) "Hide search" else "Search",
            { onEvent(KtorMonitorListUiEvent.ToggleSearch) },
            tint = if (state.isSearchVisible) activeIcon else colors.text,
        )
        MonitorIconButton(
            MonitorIcons.Warning,
            if (state.onlyErrors) "Show all calls" else "Only errors",
            { onEvent(KtorMonitorListUiEvent.ToggleOnlyErrors) },
            tint = if (state.onlyErrors) colors.error else colors.text,
        )
        Box {
            val count = state.filters.count
            MonitorIconButton(
                MonitorIcons.Tune,
                if (count > 0) "Filters, $count active" else "Filters",
                onOpenFilters,
                tint = if (count > 0) activeIcon else colors.text,
            )
            if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd))
        }
        MonitorIconButton(MonitorIcons.More, "More", onOpenMenu)
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    BasicText(
        count.toString(),
        style = MonitorTheme.typography.captionBold.copy(color = MonitorTheme.colors.background, textAlign = TextAlign.Center),
        modifier = modifier
            .offset(x = 2.dp, y = -2.dp)
            .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
            .clip(CircleShape)
            .background(MonitorTheme.colors.accent)
            .padding(horizontal = 4.dp),
    )
}

/**
 * The search field. It keeps its own text: the view model echoes the query back only after the
 * calls are found again, and a field fed that late value would drop keystrokes typed meanwhile.
 */
@Composable
internal fun SearchField(query: String, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(query) }
    val colors = MonitorTheme.colors
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onSearch(it)
        },
        singleLine = true,
        textStyle = MonitorTheme.typography.body.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        decorationBox = { field ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(start = 12.dp, end = 4.dp)
                    .heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonitorIcon(MonitorIcons.Search, colors.textSecondary, null, Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        BasicText("Search URL, method, status, body", style = MonitorTheme.typography.body.copy(color = colors.textDisabled))
                    }
                    field()
                }
                if (text.isNotEmpty()) {
                    MonitorIconButton(MonitorIcons.Close, "Clear search", {
                        text = ""
                        onSearch("")
                    }, tint = colors.textSecondary)
                }
            }
        },
    )
}

/** The filters in force as chips; tapping one drops it. */
@Composable
internal fun ActiveFilters(state: KtorMonitorListUiState, onEvent: (KtorMonitorListUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val filters = state.filters
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.onlyErrors) Chip("Only errors", true, { onEvent(KtorMonitorListUiEvent.ToggleOnlyErrors) })
        filters.hosts.forEach { Chip(it, true, { onEvent(KtorMonitorListUiEvent.ToggleHost(it)) }) }
        filters.methods.forEach { Chip(it, true, { onEvent(KtorMonitorListUiEvent.ToggleMethod(it)) }) }
        filters.contentTypes.forEach { Chip(it, true, { onEvent(KtorMonitorListUiEvent.ToggleContentType(it)) }) }
        filters.statuses.forEach { Chip(it.label, true, { onEvent(KtorMonitorListUiEvent.ToggleStatus(it)) }) }
        filters.durations.forEach { Chip(it.label, true, { onEvent(KtorMonitorListUiEvent.ToggleDuration(it)) }) }
        TextAction("Clear", { onEvent(KtorMonitorListUiEvent.ClearFilters) })
    }
}

internal val CallFilters.count: Int
    get() = hosts.size + methods.size + contentTypes.size + statuses.size + durations.size
