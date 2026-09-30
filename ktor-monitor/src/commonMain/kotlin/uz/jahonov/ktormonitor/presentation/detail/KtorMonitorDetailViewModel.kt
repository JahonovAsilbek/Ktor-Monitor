package uz.jahonov.ktormonitor.presentation.detail

import uz.jahonov.ktormonitor.InternalKtorMonitorApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import uz.jahonov.ktormonitor.body.BodyAnalysis
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
 * place. Bodies are analysed off the main thread, and only when they change: a view picked or an
 * update of the other body reuses the analysis already made.
 */
@InternalKtorMonitorApi
public class KtorMonitorDetailViewModel internal constructor(
    callId: String,
    repository: KtorMonitorRepository,
    private val appName: String,
    private val appVersion: String,
    private val now: () -> Long,
    onError: (Throwable) -> Unit,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MviViewModel<KtorMonitorDetailUiState, KtorMonitorDetailUiEvent, KtorMonitorDetailUiEffect>(KtorMonitorDetailUiState(), onError) {

    /** The view chosen for each body; the first one it offers until the user picks another. */
    private val chosenModes = MutableStateFlow<Map<BodySide, BodyMode>>(emptyMap())

    /** The last analysis of each body. Only the analysing flow below touches it, one state at a time. */
    private val analyses = mutableMapOf<BodySide, BodyAnalysis>()

    init {
        val call = repository.call(callId)
            // The database reports every write to any call; only a change of this one matters.
            .distinctUntilChanged()
            .onEach { if (it == null) emit(KtorMonitorDetailUiEffect.Close) }
            .takeWhile { it != null }
            .filterNotNull()
            // A live stream can change faster than it is analysed: skip to its latest state.
            .conflate()

        combine(call, chosenModes) { call, modes ->
            KtorMonitorDetailUiState(
                call = Loadable.Ready(call),
                request = bodyState(BodySide.REQUEST, call.requestBody, call.requestContentType, isEventStream = false, modes[BodySide.REQUEST]),
                response = bodyState(BodySide.RESPONSE, call.responseBody, call.responseContentType, call.isEventStream, modes[BodySide.RESPONSE]),
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
                body?.content?.asText(call, event.side)?.let { emit(KtorMonitorDetailUiEffect.CopyText(it)) }
            }
            is KtorMonitorDetailUiEvent.Share -> launch {
                val exportedAt = now()
                val content = withContext(analysisDispatcher) {
                    CallExporter.export(listOf(call), event.format, exportedAt, appName, appVersion)
                }
                val name = CallExporter.fileName(event.format, exportedAt, single = true)
                emit(KtorMonitorDetailUiEffect.Share(SharedFile(name, event.format.mimeType, content)))
            }
        }
    }

    private fun bodyState(
        side: BodySide,
        body: CapturedBody?,
        contentType: String?,
        isEventStream: Boolean,
        chosen: BodyMode?,
    ): BodyState? {
        if (body == null) return null
        val analysis = analyses[side]?.takeIf { it.isOf(body, contentType, isEventStream) }
            ?: BodyAnalysis(body, contentType, isEventStream).also { analyses[side] = it }
        val modes = analysis.modes
        val mode = chosen?.takeIf { it in modes } ?: modes.firstOrNull() ?: return null
        val content = when (mode) {
            BodyMode.CODE -> analysis.code?.let(BodyContent::Code)
            BodyMode.PREVIEW -> analysis.preview?.let(BodyContent::Preview)
            BodyMode.TEXT, BodyMode.STREAM -> analysis.textLines?.let(BodyContent::Lines)
            BodyMode.HEX -> null
        } ?: BodyContent.Hex(analysis.hex)
        return BodyState(modes, mode, content, body.size, body.isTruncated)
    }

    /**
     * The body as its view shows it. The code and preview views copy the text as it was sent: the
     * formatted one leaves parts out (long multipart parts) and changes others (whitespace).
     */
    private fun BodyContent.asText(call: NetworkCall, side: BodySide): String? {
        val body = if (side == BodySide.REQUEST) call.requestBody else call.responseBody
        val contentType = if (side == BodySide.REQUEST) call.requestContentType else call.responseContentType
        return when (this) {
            is BodyContent.Code -> body?.text(contentType) ?: document.lines.joinToString("\n") { it.text }
            is BodyContent.Lines -> lines.joinToString("\n")
            is BodyContent.Hex -> rows.joinToString("\n") { "${it.offset}  ${it.hex}  ${it.ascii}" }
            // An image has no text to copy.
            is BodyContent.Preview -> body?.text(contentType)
        }
    }

    private fun Map<String, List<String>>.lines() =
        entries.joinToString("\n") { (name, values) -> "$name: ${values.joinToString(", ")}" }
}

private val NetworkCall.isEventStream: Boolean
    get() = responseContentType?.substringBefore(';')?.trim().equals("text/event-stream", ignoreCase = true)
