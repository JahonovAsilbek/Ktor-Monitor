package uz.jahonov.ktormonitor.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.jahonov.ktormonitor.body.BodyMode
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.presentation.detail.BodySide
import uz.jahonov.ktormonitor.presentation.detail.CopyFormat
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEvent
import uz.jahonov.ktormonitor.presentation.list.CallSort
import uz.jahonov.ktormonitor.presentation.list.DurationRange
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEvent
import uz.jahonov.ktormonitor.presentation.list.StatusClass

// What the SwiftUI package sends, as JSON: `{"type":"search","query":"cards"}`.

@Serializable
internal sealed interface ListEventWire {
    @Serializable @SerialName("toggleSearch") data object ToggleSearch : ListEventWire
    @Serializable @SerialName("search") data class Search(val query: String) : ListEventWire
    @Serializable @SerialName("toggleOnlyErrors") data object ToggleOnlyErrors : ListEventWire
    @Serializable @SerialName("toggleHost") data class ToggleHost(val host: String) : ListEventWire
    @Serializable @SerialName("toggleMethod") data class ToggleMethod(val method: String) : ListEventWire
    @Serializable @SerialName("toggleContentType") data class ToggleContentType(val contentType: String) : ListEventWire
    @Serializable @SerialName("toggleStatus") data class ToggleStatus(val status: StatusClass) : ListEventWire
    @Serializable @SerialName("toggleDuration") data class ToggleDuration(val duration: DurationRange) : ListEventWire
    @Serializable @SerialName("clearFilters") data object ClearFilters : ListEventWire
    @Serializable @SerialName("sort") data class Sort(val sort: CallSort) : ListEventWire
    @Serializable @SerialName("click") data class Click(val id: String) : ListEventWire
    @Serializable @SerialName("longClick") data class LongClick(val id: String) : ListEventWire
    @Serializable @SerialName("startSelection") data object StartSelection : ListEventWire
    @Serializable @SerialName("selectAll") data object SelectAll : ListEventWire
    @Serializable @SerialName("clearSelection") data object ClearSelection : ListEventWire
    @Serializable @SerialName("exitSelection") data object ExitSelection : ListEventWire
    @Serializable @SerialName("deleteSelected") data object DeleteSelected : ListEventWire
    @Serializable @SerialName("shareSelected") data class ShareSelected(val format: ExportFormat) : ListEventWire
    @Serializable @SerialName("clearAll") data object ClearAll : ListEventWire
}

@Serializable
internal sealed interface DetailEventWire {
    @Serializable @SerialName("selectMode") data class SelectMode(val side: BodySide, val mode: BodyMode) : DetailEventWire
    @Serializable @SerialName("copy") data class Copy(val format: CopyFormat) : DetailEventWire
    @Serializable @SerialName("copyHeaders") data class CopyHeaders(val side: BodySide) : DetailEventWire
    @Serializable @SerialName("copyBody") data class CopyBody(val side: BodySide) : DetailEventWire
    @Serializable @SerialName("share") data class Share(val format: ExportFormat) : DetailEventWire
}

internal fun ListEventWire.toEvent(): KtorMonitorListUiEvent = when (this) {
    ListEventWire.ToggleSearch -> KtorMonitorListUiEvent.ToggleSearch
    is ListEventWire.Search -> KtorMonitorListUiEvent.Search(query)
    ListEventWire.ToggleOnlyErrors -> KtorMonitorListUiEvent.ToggleOnlyErrors
    is ListEventWire.ToggleHost -> KtorMonitorListUiEvent.ToggleHost(host)
    is ListEventWire.ToggleMethod -> KtorMonitorListUiEvent.ToggleMethod(method)
    is ListEventWire.ToggleContentType -> KtorMonitorListUiEvent.ToggleContentType(contentType)
    is ListEventWire.ToggleStatus -> KtorMonitorListUiEvent.ToggleStatus(status)
    is ListEventWire.ToggleDuration -> KtorMonitorListUiEvent.ToggleDuration(duration)
    ListEventWire.ClearFilters -> KtorMonitorListUiEvent.ClearFilters
    is ListEventWire.Sort -> KtorMonitorListUiEvent.Sort(sort)
    is ListEventWire.Click -> KtorMonitorListUiEvent.Click(id)
    is ListEventWire.LongClick -> KtorMonitorListUiEvent.LongClick(id)
    ListEventWire.StartSelection -> KtorMonitorListUiEvent.StartSelection
    ListEventWire.SelectAll -> KtorMonitorListUiEvent.SelectAll
    ListEventWire.ClearSelection -> KtorMonitorListUiEvent.ClearSelection
    ListEventWire.ExitSelection -> KtorMonitorListUiEvent.ExitSelection
    ListEventWire.DeleteSelected -> KtorMonitorListUiEvent.DeleteSelected
    is ListEventWire.ShareSelected -> KtorMonitorListUiEvent.ShareSelected(format)
    ListEventWire.ClearAll -> KtorMonitorListUiEvent.ClearAll
}

internal fun DetailEventWire.toEvent(): KtorMonitorDetailUiEvent = when (this) {
    is DetailEventWire.SelectMode -> KtorMonitorDetailUiEvent.SelectMode(side, mode)
    is DetailEventWire.Copy -> KtorMonitorDetailUiEvent.Copy(format)
    is DetailEventWire.CopyHeaders -> KtorMonitorDetailUiEvent.CopyHeaders(side)
    is DetailEventWire.CopyBody -> KtorMonitorDetailUiEvent.CopyBody(side)
    is DetailEventWire.Share -> KtorMonitorDetailUiEvent.Share(format)
}
