package uz.jahonov.ktormonitor.body

import io.ktor.http.ContentType
import kotlinx.io.bytestring.ByteString
import uz.jahonov.ktormonitor.model.CapturedBody

/**
 * Everything the body views need short of drawing, for one body: which views apply, and the body
 * laid out for each. Both apps render what this returns, so they show a body the same way.
 *
 * Each view is worked out once, when first asked for: deciding whether the code view applies
 * formats the body, and the code view then reuses that. Keep one analysis per body for as long as
 * the body does not change.
 *
 * A truncated body has no preview and no code view: it cannot be parsed reliably.
 */
internal class BodyAnalysis(
    private val body: CapturedBody,
    private val contentType: String?,
    private val isEventStream: Boolean = false,
) {
    private val type = parse(contentType)
    private val text: String? by lazy(LazyThreadSafetyMode.NONE) { body.text(contentType) }

    /** The views that apply, in the order the UI offers them; the first is the default. Empty for an empty body. */
    val modes: List<BodyMode> by lazy(LazyThreadSafetyMode.NONE) {
        if (body.bytes.size == 0) return@lazy emptyList()
        buildList {
            if (isEventStream) add(BodyMode.STREAM)
            if (preview != null) add(BodyMode.PREVIEW)
            if (code != null) add(BodyMode.CODE)
            if (text != null) add(BodyMode.TEXT)
            add(BodyMode.HEX)
        }
    }

    /** The body formatted for its language, or null when none applies or it does not parse. */
    val code: CodeDocument? by lazy(LazyThreadSafetyMode.NONE) {
        if (body.isTruncated || body.bytes.size == 0) return@lazy null
        val language = type?.let(::languageOf)
        if (language == CodeLanguage.MULTIPART) return@lazy MultipartFormatter.format(body.bytes, contentType)
        val text = text ?: return@lazy null
        when (language) {
            CodeLanguage.JSON -> JsonFormatter.format(text)
            CodeLanguage.XML -> XmlFormatter.format(text, isHtml = false)
            CodeLanguage.HTML -> XmlFormatter.format(text, isHtml = true)
            CodeLanguage.CSS -> CssHighlighter.highlight(text)
            CodeLanguage.JAVASCRIPT -> JavaScriptHighlighter.highlight(text)
            CodeLanguage.YAML -> YamlHighlighter.highlight(text)
            CodeLanguage.MARKDOWN -> MarkdownHighlighter.highlight(text)
            CodeLanguage.FORM -> FormFormatter.format(text)
            CodeLanguage.MULTIPART -> null
            null -> if (type == null && JsonFormatter.looksLikeJson(text)) JsonFormatter.format(text) else null
        }
    }

    /** An image (by its bytes; SVG by content type) or rendered Markdown, or null. */
    val preview: BodyPreview? by lazy(LazyThreadSafetyMode.NONE) {
        if (body.isTruncated || body.bytes.size == 0) return@lazy null
        imageFormat(body.bytes, type)?.let { return@lazy BodyPreview.Image(body.bytes, it) }
        if (type == null || !isMarkdown(type)) return@lazy null
        text?.let { BodyPreview.Markdown(MarkdownParser.parse(it)) }
    }

    /** The text split into lines, for the text and stream views, or null when the body is binary. */
    val textLines: List<String>? by lazy(LazyThreadSafetyMode.NONE) { text?.let(::splitLines) }

    val hex: List<HexRow> by lazy(LazyThreadSafetyMode.NONE) { HexDump.rows(body.bytes) }

    /** Whether this analysis is of [body] as it is now; one of an older state of it is stale. */
    fun isOf(body: CapturedBody, contentType: String?, isEventStream: Boolean): Boolean =
        this.body == body && this.contentType == contentType && this.isEventStream == isEventStream

    private fun imageFormat(bytes: ByteString, type: ContentType?): ImageFormat? = when {
        bytes.matchesAt(0, PNG) -> ImageFormat.PNG
        bytes.matchesAt(0, JPEG) -> ImageFormat.JPEG
        bytes.matchesAt(0, GIF87) || bytes.matchesAt(0, GIF89) -> ImageFormat.GIF
        bytes.matchesAt(0, RIFF) && bytes.matchesAt(8, WEBP) -> ImageFormat.WEBP
        type != null && type.match(ContentType.Image.SVG) -> ImageFormat.SVG
        else -> null
    }

    private fun isMarkdown(type: ContentType) =
        type.contentType.equals("text", ignoreCase = true) && type.contentSubtype.lowercase() in MARKDOWN_SUBTYPES

    private fun languageOf(type: ContentType): CodeLanguage? {
        val subtype = type.contentSubtype.lowercase()
        return when {
            type.contentType.equals("multipart", ignoreCase = true) -> CodeLanguage.MULTIPART
            subtype == "json" || subtype.endsWith("+json") -> CodeLanguage.JSON
            subtype == "html" || subtype == "xhtml+xml" -> CodeLanguage.HTML
            subtype == "xml" || subtype.endsWith("+xml") || "soap" in subtype || subtype == "rss" || subtype == "atom" ->
                CodeLanguage.XML
            subtype == "css" -> CodeLanguage.CSS
            "javascript" in subtype || "ecmascript" in subtype -> CodeLanguage.JAVASCRIPT
            subtype == "yaml" || subtype == "x-yaml" -> CodeLanguage.YAML
            subtype in MARKDOWN_SUBTYPES -> CodeLanguage.MARKDOWN
            subtype == "x-www-form-urlencoded" -> CodeLanguage.FORM
            else -> null
        }
    }

    private fun parse(contentType: String?): ContentType? =
        contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }

    private fun ByteString.matchesAt(offset: Int, bytes: ByteArray): Boolean {
        if (size < offset + bytes.size) return false
        return bytes.indices.all { this[offset + it] == bytes[it] }
    }
}

private val MARKDOWN_SUBTYPES = setOf("markdown", "x-markdown")

private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val GIF87 = "GIF87a".encodeToByteArray()
private val GIF89 = "GIF89a".encodeToByteArray()
private val RIFF = "RIFF".encodeToByteArray()
private val WEBP = "WEBP".encodeToByteArray()
