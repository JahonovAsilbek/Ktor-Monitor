package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import uz.jahonov.ktormonitor.body.BodyMode.CODE
import uz.jahonov.ktormonitor.body.BodyMode.HEX
import uz.jahonov.ktormonitor.body.BodyMode.PREVIEW
import uz.jahonov.ktormonitor.body.BodyMode.STREAM
import uz.jahonov.ktormonitor.body.BodyMode.TEXT
import uz.jahonov.ktormonitor.model.CapturedBody

class BodyAnalysisTest {

    private val png = bytesOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)

    private fun modes(body: CapturedBody, type: String?, stream: Boolean = false) =
        BodyAnalysis(body, type, stream).modes

    @Test
    fun `json offers code then text then hex`() {
        assertEquals(listOf(CODE, TEXT, HEX), modes(body("""{"a":1}"""), "application/json; charset=utf-8"))
        assertEquals(listOf(CODE, TEXT, HEX), modes(body("""{"a":1}"""), "application/problem+json"))
    }

    @Test
    fun `truncated json offers no code`() {
        val truncated = body("""{"a":1}""", size = 1_000)
        assertEquals(listOf(TEXT, HEX), modes(truncated, "application/json"))
        assertNull(BodyAnalysis(truncated, "application/json").code)
    }

    @Test
    fun `invalid json offers text but no code`() {
        assertEquals(listOf(TEXT, HEX), modes(body("""{"a":}"""), "application/json"))
    }

    @Test
    fun `json without a content type is detected`() {
        assertEquals(listOf(CODE, TEXT, HEX), modes(body(""" [1, 2] """), null))
        assertEquals(CodeLanguage.JSON, BodyAnalysis(body("[1]"), null).code?.language)
        assertEquals(listOf(TEXT, HEX), modes(body("hello"), null))
    }

    @Test
    fun `an image is found by its magic bytes`() {
        assertEquals(listOf(PREVIEW, HEX), modes(body(png), "application/octet-stream"))
        val preview = assertIs<BodyPreview.Image>(BodyAnalysis(body(png), null).preview)
        assertEquals(ImageFormat.PNG, preview.format)

        val formats = mapOf(
            ImageFormat.JPEG to bytesOf(0xFF, 0xD8, 0xFF, 0xE0),
            ImageFormat.GIF to "GIF89a..".encodeToByteArray(),
            ImageFormat.WEBP to "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray(),
        )
        formats.forEach { (format, bytes) ->
            assertEquals(format, assertIs<BodyPreview.Image>(BodyAnalysis(body(bytes), "image/png").preview).format)
        }
    }

    @Test
    fun `a truncated image offers only hex`() {
        assertEquals(listOf(HEX), modes(body(png, size = 5_000), "image/png"))
        assertNull(BodyAnalysis(body(png, size = 5_000), "image/png").preview)
    }

    @Test
    fun `svg is previewed and shown as xml`() {
        val svg = body("""<svg xmlns="http://www.w3.org/2000/svg"><rect width="1"/></svg>""")
        assertEquals(listOf(PREVIEW, CODE, TEXT, HEX), modes(svg, "image/svg+xml"))
        assertEquals(ImageFormat.SVG, assertIs<BodyPreview.Image>(BodyAnalysis(svg, "image/svg+xml").preview).format)
        assertEquals(CodeLanguage.XML, BodyAnalysis(svg, "image/svg+xml").code?.language)
    }

    @Test
    fun `markdown is previewed and shown as source`() {
        val markdown = body("# Title\n\nText")
        assertEquals(listOf(PREVIEW, CODE, TEXT, HEX), modes(markdown, "text/markdown"))
        assertEquals(listOf(PREVIEW, CODE, TEXT, HEX), modes(markdown, "text/x-markdown"))
        val preview = assertIs<BodyPreview.Markdown>(BodyAnalysis(markdown, "text/markdown").preview)
        assertEquals(2, preview.blocks.size)
        assertEquals(CodeLanguage.MARKDOWN, BodyAnalysis(markdown, "text/markdown").code?.language)
    }

    @Test
    fun `a binary body offers only hex`() {
        assertEquals(listOf(HEX), modes(body(bytesOf(0, 1, 2, 3)), "application/octet-stream"))
        assertNull(BodyAnalysis(body(bytesOf(0, 1, 2, 3)), "application/octet-stream").textLines)
    }

    @Test
    fun `an empty body offers nothing`() {
        assertEquals(emptyList(), modes(body(""), "application/json", stream = true))
        assertNull(BodyAnalysis(body(""), "application/json").code)
        assertNull(BodyAnalysis(body(""), "image/svg+xml").preview)
    }

    @Test
    fun `an event stream offers the stream first`() {
        val events = body("data: one\n\ndata: two\n\n")
        assertEquals(listOf(STREAM, TEXT, HEX), modes(events, "text/event-stream", stream = true))
        assertEquals(listOf(STREAM, TEXT, HEX), modes(body("data: x\n", size = 100), "text/event-stream", stream = true))
    }

    @Test
    fun `languages follow the content type`() {
        val cases = mapOf(
            "application/vnd.api+json" to CodeLanguage.JSON,
            "application/xml" to CodeLanguage.XML,
            "text/xml" to CodeLanguage.XML,
            "application/soap+xml" to CodeLanguage.XML,
            "application/rss+xml" to CodeLanguage.XML,
            "application/problem+xml" to CodeLanguage.XML,
            "text/html; charset=utf-8" to CodeLanguage.HTML,
            "application/xhtml+xml" to CodeLanguage.HTML,
            "text/css" to CodeLanguage.CSS,
            "application/javascript" to CodeLanguage.JAVASCRIPT,
            "text/ecmascript" to CodeLanguage.JAVASCRIPT,
            "application/yaml" to CodeLanguage.YAML,
            "application/x-yaml" to CodeLanguage.YAML,
            "text/markdown" to CodeLanguage.MARKDOWN,
            "application/x-www-form-urlencoded" to CodeLanguage.FORM,
        )
        val text = """{"a":"b"}"""
        cases.forEach { (type, language) -> assertEquals(language, BodyAnalysis(body(text), type).code?.language, type) }
        assertNull(BodyAnalysis(body(text), "text/plain").code)
    }

    @Test
    fun `text lines split on newlines and drop carriage returns`() {
        assertEquals(listOf("a", "b", "", "c"), BodyAnalysis(body("a\r\nb\n\nc\n"), "text/plain").textLines)
        assertEquals(emptyList(), BodyAnalysis(body(""), "text/plain").textLines)
    }
}
