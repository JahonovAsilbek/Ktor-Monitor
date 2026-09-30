package uz.jahonov.ktormonitor.body

import io.ktor.http.ContentType
import kotlinx.io.bytestring.ByteString
import uz.jahonov.ktormonitor.model.CapturedBody

/**
 * Everything the body views need short of drawing: which views apply, and the body laid out for
 * each. Both apps render what this returns, so they show a body the same way.
 *
 * A truncated body has no preview and no code view: it cannot be parsed reliably.
 */
internal object BodyAnalyzer {

    /**
     * The views that apply, in the order the UI offers them; the first is the default. Empty for an
     * empty body. Deciding on the code view formats the body, so call this once per body.
     */
    fun modes(body: CapturedBody, contentType: String?, isEventStream: Boolean): List<BodyMode> {
        if (body.bytes.size == 0) return emptyList()
        return buildList {
            if (isEventStream) add(BodyMode.STREAM)
            if (canPreview(body, parse(contentType))) add(BodyMode.PREVIEW)
            if (code(body, contentType) != null) add(BodyMode.CODE)
            if (body.text(contentType) != null) add(BodyMode.TEXT)
            add(BodyMode.HEX)
        }
    }

    /** The body formatted for its language, or null when none applies or it does not parse. */
    fun code(body: CapturedBody, contentType: String?): CodeDocument? {
        if (body.isTruncated || body.bytes.size == 0) return null
        val type = parse(contentType)
        val language = type?.let(::languageOf)
        if (language == CodeLanguage.MULTIPART) return MultipartFormatter.format(body.bytes, contentType)
        val text = body.text(contentType) ?: return null
        return when (language) {
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
    fun preview(body: CapturedBody, contentType: String?): BodyPreview? {
        if (body.isTruncated || body.bytes.size == 0) return null
        val type = parse(contentType)
        imageFormat(body.bytes, type)?.let { return BodyPreview.Image(body.bytes, it) }
        if (type == null || !isMarkdown(type)) return null
        return body.text(contentType)?.let { BodyPreview.Markdown(MarkdownParser.parse(it)) }
    }

    /** The text split into lines, for the text and stream views, or null when the body is binary. */
    fun textLines(body: CapturedBody, contentType: String?): List<String>? = body.text(contentType)?.let(::splitLines)

    fun hex(body: CapturedBody): List<HexRow> = HexDump.rows(body.bytes)

    private fun canPreview(body: CapturedBody, type: ContentType?) =
        !body.isTruncated && (imageFormat(body.bytes, type) != null || type != null && isMarkdown(type))

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
