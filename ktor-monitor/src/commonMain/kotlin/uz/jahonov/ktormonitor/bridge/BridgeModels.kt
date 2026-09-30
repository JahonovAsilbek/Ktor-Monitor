package uz.jahonov.ktormonitor.bridge

import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.jahonov.ktormonitor.body.BodyPreview
import uz.jahonov.ktormonitor.body.CodeDocument
import uz.jahonov.ktormonitor.body.HexRow
import uz.jahonov.ktormonitor.body.MarkdownBlock
import uz.jahonov.ktormonitor.body.MarkdownSpan
import uz.jahonov.ktormonitor.body.TokenKind
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.model.CallSummary
import uz.jahonov.ktormonitor.model.NetworkCall
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.presentation.SharedFile
import uz.jahonov.ktormonitor.presentation.detail.BodyContent
import uz.jahonov.ktormonitor.presentation.detail.BodyState
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEffect
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiState
import uz.jahonov.ktormonitor.presentation.list.CallSort
import uz.jahonov.ktormonitor.presentation.list.DurationRange
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEffect
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiState
import uz.jahonov.ktormonitor.presentation.list.StatusClass

// What the SwiftUI package receives, as JSON. Derived values (host, path, labels, flags) are
// computed here once, so Swift only draws. Enum values travel as their Kotlin names.

@Serializable
internal data class Option(val id: String, val label: String)

@Serializable
internal data class FormatWire(val id: String, val label: String, val extension: String)

@Serializable
internal data class ListStateWire(
    /** Null while the first result is loading. */
    val calls: List<CallRowWire>?,
    val totalCount: Int,
    val isSearchVisible: Boolean,
    val query: String,
    val onlyErrors: Boolean,
    val isNarrowed: Boolean,
    val filters: FiltersWire,
    val filterCount: Int,
    /** The values each filter can take, from the calls recorded. */
    val options: FiltersWire,
    val sort: Option,
    val sorts: List<Option>,
    val exportFormats: List<FormatWire>,
    /** Null outside selection mode. */
    val selection: List<String>?,
)

@Serializable
internal data class FiltersWire(
    val hosts: List<String>,
    val methods: List<String>,
    val contentTypes: List<String>,
    val statuses: List<Option>,
    val durations: List<Option>,
)

@Serializable
internal data class CallRowWire(
    val id: String,
    val groupId: String,
    val attempt: Int,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val isSecure: Boolean,
    val protocol: String?,
    val requestTime: Long,
    val responseTime: Long?,
    val durationMillis: Long?,
    val requestContentType: String?,
    val responseContentType: String?,
    val requestSize: Long?,
    val responseSize: Long?,
    val responseCode: Int?,
    val error: String?,
    val isInProgress: Boolean,
    val isRedirect: Boolean,
    val isError: Boolean,
    val kind: Option,
)

@Serializable
internal data class DetailStateWire(
    /** Null while loading. */
    val call: CallDetailWire?,
    val request: BodyWire?,
    val response: BodyWire?,
)

@Serializable
internal data class CallDetailWire(
    val summary: CallRowWire,
    val requestHeaders: List<HeaderWire>,
    val responseHeaders: List<HeaderWire>,
)

@Serializable
internal data class HeaderWire(val name: String, val value: String)

@Serializable
internal data class BodyWire(
    val modes: List<Option>,
    val mode: Option,
    val size: Long,
    val isTruncated: Boolean,
    val content: ContentWire,
)

@Serializable
internal sealed interface ContentWire {
    @Serializable
    @SerialName("code")
    data class Code(val language: String, val lines: List<List<SpanWire>>, val folds: List<FoldWire>) : ContentWire

    /** TEXT and STREAM. */
    @Serializable
    @SerialName("lines")
    data class Lines(val lines: List<String>) : ContentWire

    @Serializable
    @SerialName("hex")
    data class Hex(val rows: List<HexRowWire>) : ContentWire

    @Serializable
    @SerialName("image")
    data class Image(val format: String, val base64: String) : ContentWire

    @Serializable
    @SerialName("markdown")
    data class Markdown(val blocks: List<MarkdownBlockWire>) : ContentWire
}

@Serializable
internal data class SpanWire(val text: String, val kind: TokenKind)

@Serializable
internal data class FoldWire(val startLine: Int, val endLine: Int, val isCollapsedByDefault: Boolean)

@Serializable
internal data class HexRowWire(val offset: String, val hex: String, val ascii: String)

@Serializable
internal data class MarkdownSpanWire(
    val text: String,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isCode: Boolean,
    val link: String?,
)

@Serializable
internal sealed interface MarkdownBlockWire {
    @Serializable
    @SerialName("heading")
    data class Heading(val level: Int, val spans: List<MarkdownSpanWire>) : MarkdownBlockWire

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val spans: List<MarkdownSpanWire>) : MarkdownBlockWire

    @Serializable
    @SerialName("listItem")
    data class ListItem(val depth: Int, val number: Int?, val spans: List<MarkdownSpanWire>) : MarkdownBlockWire

    @Serializable
    @SerialName("quote")
    data class Quote(val spans: List<MarkdownSpanWire>) : MarkdownBlockWire

    @Serializable
    @SerialName("codeBlock")
    data class CodeBlock(val language: String?, val text: String) : MarkdownBlockWire

    @Serializable
    @SerialName("rule")
    data object Rule : MarkdownBlockWire
}

@Serializable
internal sealed interface EffectWire {
    @Serializable
    @SerialName("openCall")
    data class OpenCall(val id: String) : EffectWire

    @Serializable
    @SerialName("share")
    data class Share(val name: String, val mimeType: String, val content: String) : EffectWire

    @Serializable
    @SerialName("copyText")
    data class CopyText(val text: String) : EffectWire

    /** The open call was deleted. */
    @Serializable
    @SerialName("close")
    data object Close : EffectWire
}

@Serializable
internal data class NotificationWire(val title: String, val lines: List<String>)

internal fun KtorMonitorListUiState.toWire() = ListStateWire(
    calls = (calls as? Loadable.Ready)?.value?.map { it.toWire() },
    totalCount = totalCount,
    isSearchVisible = isSearchVisible,
    query = query,
    onlyErrors = onlyErrors,
    isNarrowed = isNarrowed,
    filters = FiltersWire(
        hosts = filters.hosts.toList(),
        methods = filters.methods.toList(),
        contentTypes = filters.contentTypes.toList(),
        statuses = filters.statuses.map { it.toOption() },
        durations = filters.durations.map { it.toOption() },
    ),
    filterCount = filters.count,
    options = FiltersWire(
        hosts = options.hosts,
        methods = options.methods,
        contentTypes = options.contentTypes,
        statuses = options.statuses.map { it.toOption() },
        durations = options.durations.map { it.toOption() },
    ),
    sort = sort.toOption(),
    sorts = CallSort.entries.map { it.toOption() },
    exportFormats = ExportFormat.entries.map { FormatWire(it.name, it.label, it.extension) },
    selection = selection?.toList(),
)

internal fun CallSummary.toWire() = CallRowWire(
    id = id,
    groupId = groupId,
    attempt = attempt,
    method = method,
    url = url,
    host = host,
    path = path,
    isSecure = isSecure,
    protocol = protocol,
    requestTime = requestTime,
    responseTime = responseTime,
    durationMillis = durationMillis,
    requestContentType = requestContentType,
    responseContentType = responseContentType,
    requestSize = requestSize,
    responseSize = responseSize,
    responseCode = responseCode,
    error = error,
    isInProgress = isInProgress,
    isRedirect = isRedirect,
    isError = isError,
    kind = Option(kind.name, kind.label),
)

internal fun KtorMonitorDetailUiState.toWire() = DetailStateWire(
    call = (call as? Loadable.Ready)?.value?.toWire(),
    request = request?.toWire(),
    response = response?.toWire(),
)

private fun NetworkCall.toWire() = CallDetailWire(
    summary = summary.toWire(),
    requestHeaders = requestHeaders.toWire(),
    responseHeaders = responseHeaders.toWire(),
)

private fun Map<String, List<String>>.toWire() = flatMap { (name, values) -> values.map { HeaderWire(name, it) } }

private fun BodyState.toWire() = BodyWire(
    modes = modes.map { Option(it.name, it.label) },
    mode = Option(mode.name, mode.label),
    size = size,
    isTruncated = isTruncated,
    content = content.toWire(),
)

private fun BodyContent.toWire(): ContentWire = when (this) {
    is BodyContent.Code -> document.toWire()
    is BodyContent.Lines -> ContentWire.Lines(lines)
    is BodyContent.Hex -> ContentWire.Hex(rows.map { it.toWire() })
    is BodyContent.Preview -> when (val preview = preview) {
        is BodyPreview.Image -> ContentWire.Image(preview.format.name, Base64.encode(preview.bytes.toByteArray()))
        is BodyPreview.Markdown -> ContentWire.Markdown(preview.blocks.map { it.toWire() })
    }
}

private fun CodeDocument.toWire() = ContentWire.Code(
    language = language.name,
    lines = lines.map { line -> line.spans.map { SpanWire(it.text, it.kind) } },
    folds = folds.map { FoldWire(it.startLine, it.endLine, it.isCollapsedByDefault) },
)

private fun HexRow.toWire() = HexRowWire(offset, hex, ascii)

private fun MarkdownBlock.toWire(): MarkdownBlockWire = when (this) {
    is MarkdownBlock.Heading -> MarkdownBlockWire.Heading(level, spans.toWire())
    is MarkdownBlock.Paragraph -> MarkdownBlockWire.Paragraph(spans.toWire())
    is MarkdownBlock.ListItem -> MarkdownBlockWire.ListItem(depth, number, spans.toWire())
    is MarkdownBlock.Quote -> MarkdownBlockWire.Quote(spans.toWire())
    is MarkdownBlock.CodeBlock -> MarkdownBlockWire.CodeBlock(language, text)
    MarkdownBlock.Rule -> MarkdownBlockWire.Rule
}

private fun List<MarkdownSpan>.toWire() = map { MarkdownSpanWire(it.text, it.isBold, it.isItalic, it.isCode, it.link) }

internal fun KtorMonitorListUiEffect.toWire(): EffectWire = when (this) {
    is KtorMonitorListUiEffect.OpenCall -> EffectWire.OpenCall(id)
    is KtorMonitorListUiEffect.Share -> file.toWire()
}

internal fun KtorMonitorDetailUiEffect.toWire(): EffectWire = when (this) {
    is KtorMonitorDetailUiEffect.CopyText -> EffectWire.CopyText(text)
    is KtorMonitorDetailUiEffect.Share -> file.toWire()
    KtorMonitorDetailUiEffect.Close -> EffectWire.Close
}

private fun SharedFile.toWire() = EffectWire.Share(name, mimeType, content)

private fun StatusClass.toOption() = Option(name, label)

private fun DurationRange.toOption() = Option(name, label)

private fun CallSort.toOption() = Option(name, label)

