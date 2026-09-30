package uz.jahonov.ktormonitor.body

import uz.jahonov.ktormonitor.InternalKtorMonitorApi
import kotlinx.io.bytestring.ByteString

@InternalKtorMonitorApi
public enum class ImageFormat { PNG, JPEG, WEBP, GIF, SVG }

/** A body rendered rather than shown as code or text. */
@InternalKtorMonitorApi
public sealed interface BodyPreview {
    public data class Image(val bytes: ByteString, val format: ImageFormat) : BodyPreview
    public data class Markdown(val blocks: List<MarkdownBlock>) : BodyPreview
}

/** A run of Markdown text with one style. [link] is the target when the run is a link. */
@InternalKtorMonitorApi
public data class MarkdownSpan(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isCode: Boolean = false,
    val link: String? = null,
)

@InternalKtorMonitorApi
public sealed interface MarkdownBlock {
    public data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    public data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock

    /** A list entry, [depth] 0 at the outermost level. [number] is null for a bullet. */
    public data class ListItem(val depth: Int, val number: Int?, val spans: List<MarkdownSpan>) : MarkdownBlock
    public data class Quote(val spans: List<MarkdownSpan>) : MarkdownBlock
    public data class CodeBlock(val language: String?, val text: String) : MarkdownBlock
    public data object Rule : MarkdownBlock
}
