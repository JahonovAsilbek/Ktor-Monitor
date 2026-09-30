package uz.jahonov.ktormonitor.presentation.detail

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import uz.jahonov.ktormonitor.body.BodyAnalyzer
import uz.jahonov.ktormonitor.body.BodyMode
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.export.CallExporter
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.presentation.MviViewModel
import uz.jahonov.ktormonitor.presentation.SharedFile

/**
 * One call: summary, request, response. A call still in flight (or a live event stream) updates in
 * place. Bodies are analysed off the main thread, once per change of the call or of the chosen view.
 */
public class KtorMonitorDetailViewModel internal constructor(
    callId: String,
    repository: KtorMonitorRepository,
    private val appName: String,
    private val appVersion: String,
    private val now: () -> Long,
    onError: (Throwable) -> Unit,
    analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MviViewModel<KtorMonitorDetailUiState, KtorMonitorDetailUiEvent, KtorMonitorDetailUiEffect>(KtorMonitorDetailUiState(), onError) {

    /** The view chosen for each body; the first one it offers until the user picks another. */
    private val chosenModes = MutableStateFlow<Map<BodySide, BodyMode>>(emptyMap())

    init {
        val call = repository.call(callId)
            .onEach { if (it == null) emit(KtorMonitorDetailUiEffect.Close) }
            .takeWhile { it != null }
            .filterNotNull()

        combine(call, chosenModes) { call, modes ->
            KtorMonitorDetailUiState(
                call = Loadable.Ready(call),
                request = bodyState(call.requestBody, call.requestContentType, isEventStream = false, modes[BodySide.REQUEST]),
                response = bodyState(call.responseBody, call.responseContentType, call.isEventStream, modes[BodySide.RESPONSE]),
            )
        }
            .flowOn(analysisDispatcher)
            .onEach { analysed -> setState { analysed } }
            .collectSafely()
    }

    override fun onEvent(event: KtorMonitorDetailUiEvent) {
        val call = (state.value.call as? Loadable.Ready)?.value ?: return
        when (event) {
            is KtorMonitorDetailUiEvent.SelectMode -> chosenModes.update { it + (event.side to event.mode) }
            is KtorMonitorDetailUiEvent.Copy -> emit(
                KtorMonitorDetailUiEffect.CopyText(
                    when (event.format) {
                        CopyFormat.URL -> call.url
                        CopyFormat.CURL -> CallExporter.curl(call)
                        CopyFormat.WGET -> CallExporter.wget(call)
                        CopyFormat.TEXT -> CallExporter.text(call)
                    },
                ),
            )
            is KtorMonitorDetailUiEvent.CopyHeaders -> {
                val headers = if (event.side == BodySide.REQUEST) call.requestHeaders else call.responseHeaders
                emit(KtorMonitorDetailUiEffect.CopyText(headers.lines()))
            }
            is KtorMonitorDetailUiEvent.CopyBody -> {
                val body = if (event.side == BodySide.REQUEST) state.value.request else state.value.response
                body?.let { emit(KtorMonitorDetailUiEffect.CopyText(it.content.asText(call, event.side))) }
            }
            is KtorMonitorDetailUiEvent.Share -> {
                val exportedAt = now()
                val content = CallExporter.export(listOf(call), event.format, exportedAt, appName, appVersion)
                val name = CallExporter.fileName(event.format, exportedAt, single = true)
                emit(KtorMonitorDetailUiEffect.Share(SharedFile(name, event.format.mimeType, content)))
            }
        }
    }

    private fun bodyState(body: CapturedBody?, contentType: String?, isEventStream: Boolean, chosen: BodyMode?): BodyState? {
        if (body == null) return null
        val modes = BodyAnalyzer.modes(body, contentType, isEventStream)
        val mode = chosen?.takeIf { it in modes } ?: modes.firstOrNull() ?: return null
        val content = when (mode) {
            BodyMode.CODE -> BodyAnalyzer.code(body, contentType)?.let(BodyContent::Code)
            BodyMode.PREVIEW -> BodyAnalyzer.preview(body, contentType)?.let(BodyContent::Preview)
            BodyMode.TEXT, BodyMode.STREAM -> BodyAnalyzer.textLines(body, contentType)?.let(BodyContent::Lines)
            BodyMode.HEX -> null
        } ?: BodyContent.Hex(BodyAnalyzer.hex(body))
        return BodyState(modes, mode, content, body.size, body.isTruncated)
    }

    private fun BodyContent.asText(call: NetworkCall, side: BodySide): String = when (this) {
        is BodyContent.Code -> document.lines.joinToString("\n") { it.text }
        is BodyContent.Lines -> lines.joinToString("\n")
        is BodyContent.Hex -> rows.joinToString("\n") { "${it.offset}  ${it.hex}  ${it.ascii}" }
        is BodyContent.Preview -> {
            val body = if (side == BodySide.REQUEST) call.requestBody else call.responseBody
            val contentType = if (side == BodySide.REQUEST) call.requestContentType else call.responseContentType
            body?.text(contentType).orEmpty()
        }
    }

    private fun Map<String, List<String>>.lines() =
        entries.joinToString("\n") { (name, values) -> "$name: ${values.joinToString(", ")}" }
}

private val NetworkCall.isEventStream: Boolean
    get() = responseContentType?.substringBefore(';')?.trim().equals("text/event-stream", ignoreCase = true)
