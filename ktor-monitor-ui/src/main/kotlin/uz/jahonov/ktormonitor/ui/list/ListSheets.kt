package uz.jahonov.ktormonitor.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.presentation.list.CallSort
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEvent
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiState
import uz.jahonov.ktormonitor.ui.ui.BottomSheet
import uz.jahonov.ktormonitor.ui.ui.Chip
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.SheetItem
import uz.jahonov.ktormonitor.ui.ui.TextAction

@Composable
internal fun FiltersSheet(state: KtorMonitorListUiState, onEvent: (KtorMonitorListUiEvent) -> Unit, onDismiss: () -> Unit) {
    val options = state.options
    val filters = state.filters
    // The sheet scrolls its content itself; a scroll of our own inside it would get unbounded height.
    BottomSheet(title = "Filters", onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChipGroup("Host", options.hosts, filters.hosts::contains, { it }) { onEvent(KtorMonitorListUiEvent.ToggleHost(it)) }
            ChipGroup("Method", options.methods, filters.methods::contains, { it }) { onEvent(KtorMonitorListUiEvent.ToggleMethod(it)) }
            ChipGroup("Content type", options.contentTypes, filters.contentTypes::contains, { it }) {
                onEvent(KtorMonitorListUiEvent.ToggleContentType(it))
            }
            ChipGroup("Status", options.statuses, filters.statuses::contains, { it.label }) { onEvent(KtorMonitorListUiEvent.ToggleStatus(it)) }
            ChipGroup("Duration", options.durations, filters.durations::contains, { it.label }) {
                onEvent(KtorMonitorListUiEvent.ToggleDuration(it))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Clear", { onEvent(KtorMonitorListUiEvent.ClearFilters) }, enabled = !filters.isEmpty || state.onlyErrors)
            }
        }
    }
}

/** A titled group of toggle chips; nothing at all when there is nothing to choose from. */
@Composable
private fun <T> ChipGroup(
    title: String,
    values: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(title, style = MonitorTheme.typography.bodyMedium.copy(color = MonitorTheme.colors.textSecondary))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value -> Chip(label(value), isSelected(value), { onToggle(value) }) }
        }
    }
}

@Composable
internal fun SortSheet(current: CallSort, onSort: (CallSort) -> Unit, onDismiss: () -> Unit) {
    BottomSheet(title = "Sort", onDismiss = onDismiss) { dismiss ->
        CallSort.entries.forEach { sort ->
            SheetItem(
                title = sort.label,
                trailingIcon = if (sort == current) MonitorIcons.Check else null,
                onClick = {
                    onSort(sort)
                    dismiss()
                },
            )
        }
    }
}

private val CallSort.label: String
    get() = when (this) {
        CallSort.NEWEST -> "Newest first"
        CallSort.SIZE_ASCENDING -> "Size, smallest first"
        CallSort.SIZE_DESCENDING -> "Size, largest first"
        CallSort.DURATION_ASCENDING -> "Duration, fastest first"
        CallSort.DURATION_DESCENDING -> "Duration, slowest first"
    }

internal enum class MenuAction { SELECT, SORT, CLEAR_ALL }

/** The overflow menu. [onAction] runs once the sheet is gone, so a following sheet never overlaps it. */
@Composable
internal fun MenuSheet(sort: CallSort, hasCalls: Boolean, onAction: (MenuAction) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf<MenuAction?>(null) }
    BottomSheet(
        title = "More",
        onDismiss = {
            onDismiss()
            chosen?.let(onAction)
        },
    ) { dismiss ->
        fun choose(action: MenuAction) {
            chosen = action
            dismiss()
        }
        if (hasCalls) SheetItem(title = "Select", leadingIcon = MonitorIcons.CheckCircle, onClick = { choose(MenuAction.SELECT) })
        SheetItem(
            title = "Sort",
            value = sort.label,
            leadingIcon = MonitorIcons.Sort,
            trailingIcon = MonitorIcons.ChevronRight,
            onClick = { choose(MenuAction.SORT) },
        )
        if (hasCalls) SheetItem(title = "Clear all", leadingIcon = MonitorIcons.Delete, onClick = { choose(MenuAction.CLEAR_ALL) })
    }
}

@Composable
internal fun ShareSheet(onShare: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    BottomSheet(title = "Share as", onDismiss = onDismiss) { dismiss ->
        ExportFormat.entries.forEach { format ->
            SheetItem(
                title = format.label,
                description = ".${format.extension}",
                onClick = {
                    onShare(format)
                    dismiss()
                },
            )
        }
    }
}

private val ExportFormat.label: String
    get() = when (this) {
        ExportFormat.JSON -> "JSON"
        ExportFormat.TEXT -> "Text"
        ExportFormat.MARKDOWN -> "Markdown"
        ExportFormat.CURL -> "cURL"
        ExportFormat.WGET -> "wget"
        ExportFormat.URLS -> "URL list"
        ExportFormat.HAR -> "HAR"
    }
