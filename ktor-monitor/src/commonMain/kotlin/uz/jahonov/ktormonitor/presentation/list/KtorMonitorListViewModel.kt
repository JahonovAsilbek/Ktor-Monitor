package uz.jahonov.ktormonitor.presentation.list

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import uz.jahonov.ktormonitor.export.CallExporter
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.presentation.MviViewModel
import uz.jahonov.ktormonitor.presentation.SharedFile

/**
 * The call list. Search runs in the database (it covers the bodies); filters, "only errors" and sort
 * run here on the rows found, so changing them needs no query.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
public class KtorMonitorListViewModel internal constructor(
    private val repository: KtorMonitorRepository,
    private val appName: String,
    private val appVersion: String,
    private val now: () -> Long,
    onError: (Throwable) -> Unit,
) : MviViewModel<KtorMonitorListUiState, KtorMonitorListUiEvent, KtorMonitorListUiEffect>(KtorMonitorListUiState(), onError) {

    /** What the user chose; the published state adds the calls found to it. */
    private val controls = MutableStateFlow(KtorMonitorListUiState())

    init {
        val found = controls.map { it.query.trim() }
            .distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0 else SEARCH_DEBOUNCE_MILLIS }
            .flatMapLatest { repository.calls(it) }

        combine(repository.calls(), found, controls) { all, found, controls ->
            val foundIds = found.map { it.id }.toSet()
            controls.copy(
                calls = Loadable.Ready(found.shown(controls.onlyErrors, controls.filters, controls.sort)),
                totalCount = all.size,
                options = FilterOptions.of(all),
                // Calls deleted meanwhile (retention, clear from the notification) leave the selection.
                selection = controls.selection?.intersect(foundIds),
            )
        }
            .onEach { derived -> setState { derived } }
            .collectSafely()
    }

    override fun onEvent(event: KtorMonitorListUiEvent) {
        when (event) {
            KtorMonitorListUiEvent.ToggleSearch -> setControls { copy(isSearchVisible = !isSearchVisible) }
            is KtorMonitorListUiEvent.Search -> setControls { copy(query = event.query) }
            KtorMonitorListUiEvent.ToggleOnlyErrors -> setControls { copy(onlyErrors = !onlyErrors) }
            is KtorMonitorListUiEvent.ToggleHost -> setFilters { copy(hosts = hosts.toggle(event.host)) }
            is KtorMonitorListUiEvent.ToggleMethod -> setFilters { copy(methods = methods.toggle(event.method)) }
            is KtorMonitorListUiEvent.ToggleContentType -> setFilters { copy(contentTypes = contentTypes.toggle(event.contentType)) }
            is KtorMonitorListUiEvent.ToggleStatus -> setFilters { copy(statuses = statuses.toggle(event.status)) }
            is KtorMonitorListUiEvent.ToggleDuration -> setFilters { copy(durations = durations.toggle(event.duration)) }
            KtorMonitorListUiEvent.ClearFilters -> setControls { copy(filters = CallFilters(), onlyErrors = false) }
            is KtorMonitorListUiEvent.Sort -> setControls { copy(sort = event.sort) }
            is KtorMonitorListUiEvent.Click ->
                if (controls.value.isSelecting) setSelection { toggle(event.id) } else emit(KtorMonitorListUiEffect.OpenCall(event.id))
            is KtorMonitorListUiEvent.LongClick -> setControls { copy(selection = (selection ?: emptySet()) + event.id) }
            KtorMonitorListUiEvent.StartSelection -> setControls { copy(selection = selection ?: emptySet()) }
            KtorMonitorListUiEvent.SelectAll -> setSelection { this + visibleIds() }
            KtorMonitorListUiEvent.ClearSelection -> setSelection { emptySet() }
            KtorMonitorListUiEvent.ExitSelection -> setControls { copy(selection = null) }
            KtorMonitorListUiEvent.DeleteSelected -> {
                val ids = state.value.selection.orEmpty().toList()
                setControls { copy(selection = null) }
                launch { repository.delete(ids) }
            }
            is KtorMonitorListUiEvent.ShareSelected -> share(event.format)
            KtorMonitorListUiEvent.ClearAll -> {
                setControls { copy(selection = null) }
                launch { repository.clear() }
            }
        }
    }

    private fun share(format: ExportFormat) {
        val ids = state.value.selection.orEmpty().toList()
        if (ids.isEmpty()) return
        launch {
            val calls = repository.calls(ids)
            val exportedAt = now()
            val content = CallExporter.export(calls, format, exportedAt, appName, appVersion)
            emit(KtorMonitorListUiEffect.Share(SharedFile(CallExporter.fileName(format, exportedAt), format.mimeType, content)))
            setControls { copy(selection = null) }
        }
    }

    private fun visibleIds(): Set<String> =
        (state.value.calls as? Loadable.Ready)?.value?.map { it.id }?.toSet().orEmpty()

    private fun setControls(change: KtorMonitorListUiState.() -> KtorMonitorListUiState) = controls.update(change)

    private fun setFilters(change: CallFilters.() -> CallFilters) = setControls { copy(filters = filters.change()) }

    private fun setSelection(change: Set<String>.() -> Set<String>) =
        setControls { copy(selection = selection?.change()) }

    private fun <T> Set<T>.toggle(value: T) = if (value in this) this - value else this + value

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 200L
    }
}
