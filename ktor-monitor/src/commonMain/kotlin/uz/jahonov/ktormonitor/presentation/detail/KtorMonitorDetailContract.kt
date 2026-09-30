package uz.jahonov.ktormonitor.presentation.detail

import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.body.BodyMode
import uz.jahonov.ktormonitor.body.BodyPreview
import uz.jahonov.ktormonitor.body.CodeDocument
import uz.jahonov.ktormonitor.body.HexRow
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.model.NetworkCall
import uz.jahonov.ktormonitor.presentation.SharedFile

public data class KtorMonitorDetailUiState(
    val call: Loadable<NetworkCall> = Loadable.Loading,
    val request: BodyState? = null,
    val response: BodyState? = null,
)

public enum class BodySide { REQUEST, RESPONSE }

/** One body with the views it offers and the one shown. */
public data class BodyState(
    val modes: List<BodyMode>,
    val mode: BodyMode,
    val content: BodyContent,
    val size: Long,
    val isTruncated: Boolean,
)

public sealed interface BodyContent {
    public data class Code(val document: CodeDocument) : BodyContent
    /** TEXT and STREAM. */
    public data class Lines(val lines: List<String>) : BodyContent
    public data class Hex(val rows: List<HexRow>) : BodyContent
    public data class Preview(val preview: BodyPreview) : BodyContent
}

public enum class CopyFormat { URL, CURL, WGET, TEXT }

public sealed interface KtorMonitorDetailUiEvent {
    public data class SelectMode(val side: BodySide, val mode: BodyMode) : KtorMonitorDetailUiEvent
    public data class Copy(val format: CopyFormat) : KtorMonitorDetailUiEvent
    public data class CopyHeaders(val side: BodySide) : KtorMonitorDetailUiEvent

    /** Copies the body as the current view shows it. */
    public data class CopyBody(val side: BodySide) : KtorMonitorDetailUiEvent

    /** TEXT (as a `.http` file), MARKDOWN or HAR. */
    public data class Share(val format: ExportFormat) : KtorMonitorDetailUiEvent
}

public sealed interface KtorMonitorDetailUiEffect {
    public data class CopyText(val text: String) : KtorMonitorDetailUiEffect
    public data class Share(val file: SharedFile) : KtorMonitorDetailUiEffect

    /** The call was deleted while open. */
    public data object Close : KtorMonitorDetailUiEffect
}
