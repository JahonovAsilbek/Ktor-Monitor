package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import uz.jahonov.ktormonitor.body.TokenKind.COMMENT
import uz.jahonov.ktormonitor.body.TokenKind.HEADING
import uz.jahonov.ktormonitor.body.TokenKind.KEY
import uz.jahonov.ktormonitor.body.TokenKind.PUNCTUATION
import uz.jahonov.ktormonitor.body.TokenKind.STRING

class MultipartFormatterTest {

    private val type = "multipart/form-data; boundary=XyZ"

    private fun multipart(vararg parts: ByteArray): ByteString {
        val out = ArrayList<Byte>()
        parts.forEach { part ->
            out += "--XyZ\r\n".encodeToByteArray().toList()
            out += part.toList()
            out += "\r\n".encodeToByteArray().toList()
        }
        out += "--XyZ--\r\n".encodeToByteArray().toList()
        return ByteString(out.toByteArray())
    }

    private fun format(bytes: ByteString, contentType: String? = type) =
        assertNotNull(MultipartFormatter.format(bytes, contentType))

    @Test
    fun `text and binary parts are listed with their headers`() {
        val bytes = multipart(
            "Content-Disposition: form-data; name=\"field\"\r\n\r\nhello\r\nworld".encodeToByteArray(),
            "Content-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                .encodeToByteArray() + bytesOf(0, 1, 2, 255),
        )
        val document = format(bytes)
        assertEquals(
            listOf(
                "Part 1 · field",
                "  Content-Disposition: form-data; name=\"field\"",
                "  hello",
                "  world",
                "",
                "Part 2 · file · a.bin",
                "  Content-Disposition: form-data; name=\"file\"; filename=\"a.bin\"",
                "  Content-Type: application/octet-stream",
                "  <binary, 4 bytes>",
            ),
            document.texts,
        )
        assertEquals(listOf(FoldRegion(0, 3, false), FoldRegion(5, 8, false)), document.folds)
        assertEquals(
            listOf("Part 2" to HEADING, " · " to PUNCTUATION, "file" to KEY, " · " to PUNCTUATION, "a.bin" to STRING),
            document.lines[5].spans.map { it.text to it.kind },
        )
        assertEquals(listOf("Content-Type" to KEY, ": " to PUNCTUATION, "application/octet-stream" to STRING), document.lines[7].spans.drop(1).map { it.text to it.kind })
        assertEquals(COMMENT, document.lines[8].spans.last().kind)
    }

    @Test
    fun `long text parts are capped by lines and characters`() {
        val lines = listOf("x".repeat(300)) + (2..25).map { "line $it" }
        val document = format(multipart(("Content-Type: text/plain\r\n\r\n" + lines.joinToString("\n")).encodeToByteArray()))
        val content = document.texts.drop(2)
        assertEquals(21, content.size)
        assertEquals("  " + "x".repeat(239) + "…", content[0])
        assertEquals("  line 20", content[19])
        assertEquals("  … 5 more lines", content[20])
    }

    @Test
    fun `an empty part and a quoted boundary are handled`() {
        val bytes = "--a b\r\nContent-Disposition: form-data; name=\"e\"\r\n\r\n\r\n--a b--".encodeToByteString()
        assertEquals(
            listOf("Part 1 · e", "  Content-Disposition: form-data; name=\"e\"", "  <empty>"),
            format(bytes, "multipart/form-data; boundary=\"a b\"").texts,
        )
    }

    @Test
    fun `the boundary is taken from the first line when missing`() {
        val bytes = "--abc\nX-Test: 1\n\nbody\n--abc--\n".encodeToByteString()
        assertEquals(listOf("Part 1", "  X-Test: 1", "  body"), format(bytes, "multipart/mixed").texts)
    }

    @Test
    fun `a body without parts gives null`() {
        assertNull(MultipartFormatter.format("no parts here".encodeToByteString(), type))
        assertNull(MultipartFormatter.format(ByteString(), null))
    }

    @Test
    fun `multipart offers code and hex`() {
        val bytes = multipart("Content-Disposition: form-data; name=\"a\"\r\n\r\n1".encodeToByteArray())
        val captured = body(bytes.toByteArray())
        assertEquals(listOf(BodyMode.CODE, BodyMode.HEX), BodyAnalyzer.modes(captured, type, isEventStream = false))
    }
}
