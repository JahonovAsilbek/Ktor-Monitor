package uz.jahonov.ktormonitor.presentation.list

import uz.jahonov.ktormonitor.InternalKtorMonitorApi
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.model.CallSummary
import uz.jahonov.ktormonitor.presentation.SharedFile

@InternalKtorMonitorApi
public data class KtorMonitorListUiState(
    /** The calls to show: searched, filtered and sorted. */
    val calls: Loadable<List<CallSummary>> = Loadable.Loading,
    /** Every recorded call, before search and filters. */
    val totalCount: Int = 0,
    val isSearchVisible: Boolean = false,
    val query: String = "",
    val onlyErrors: Boolean = false,
    val filters: CallFilters = CallFilters(),
    /** The values each filter can take, from the calls recorded. */
    val options: FilterOptions = FilterOptions(),
    val sort: CallSort = CallSort.NEWEST,
    /** Ids of the selected calls; null outside selection mode. */
    val selection: Set<String>? = null,
) {
    val isSelecting: Boolean get() = selection != null

    /** Whether search, filters or "only errors" hide some of the calls. */
    val isNarrowed: Boolean get() = query.isNotBlank() || onlyErrors || !filters.isEmpty
}

@InternalKtorMonitorApi
public sealed interface KtorMonitorListUiEvent {
    public data object ToggleSearch : KtorMonitorListUiEvent
    public data class Search(val query: String) : KtorMonitorListUiEvent
    public data object ToggleOnlyErrors : KtorMonitorListUiEvent
    public data class ToggleHost(val host: String) : KtorMonitorListUiEvent
    public data class ToggleMethod(val method: String) : KtorMonitorListUiEvent
    public data class ToggleContentType(val contentType: String) : KtorMonitorListUiEvent
    public data class ToggleStatus(val status: StatusClass) : KtorMonitorListUiEvent
    public data class ToggleDuration(val duration: DurationRange) : KtorMonitorListUiEvent
    public data object ClearFilters : KtorMonitorListUiEvent
    public data class Sort(val sort: CallSort) : KtorMonitorListUiEvent

    /** A tap: opens the call, or toggles it while selecting. */
    public data class Click(val id: String) : KtorMonitorListUiEvent

    /** A long press: starts selecting with this call. */
    public data class LongClick(val id: String) : KtorMonitorListUiEvent
    public data object StartSelection : KtorMonitorListUiEvent
    public data object SelectAll : KtorMonitorListUiEvent
    public data object ClearSelection : KtorMonitorListUiEvent
    public data object ExitSelection : KtorMonitorListUiEvent
    public data object DeleteSelected : KtorMonitorListUiEvent
    public data class ShareSelected(val format: ExportFormat) : KtorMonitorListUiEvent
    public data object ClearAll : KtorMonitorListUiEvent
}

@InternalKtorMonitorApi
public sealed interface KtorMonitorListUiEffect {
    public data class OpenCall(val id: String) : KtorMonitorListUiEffect
    public data class Share(val file: SharedFile) : KtorMonitorListUiEffect
}
