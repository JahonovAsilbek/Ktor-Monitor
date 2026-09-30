package uz.jahonov.ktormonitor.body

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import kotlinx.io.bytestring.ByteString
import uz.jahonov.ktormonitor.model.decodeText

/**
 * A multipart body as its parts: a `Part N` heading with the part's name and file name, its headers,
 * and a preview of its content. Each part folds, expanded.
 */
internal object MultipartFormatter {

    /** [bytes] split by the boundary of [contentType] (or the first line), or null when no part is found. */
    fun format(bytes: ByteString, contentType: String?): CodeDocument? {
        val data = bytes.toByteArray()
        val boundary = boundaryOf(contentType) ?: firstLineBoundary(data) ?: return null
        val parts = split(data, "--$boundary".encodeToByteArray())
        if (parts.isEmpty()) return null

        val out = CodeBuilder(CodeLanguage.MULTIPART)
        parts.forEachIndexed { index, range ->
            if (index > 0) out.newLine()
            val start = out.line
            part(out, index + 1, bytes, data, range)
            out.fold(start, out.line - 1)
        }
        return out.build()
    }

    private fun part(out: CodeBuilder, number: Int, bytes: ByteString, data: ByteArray, range: IntRange) {
        val headers = ArrayList<Pair<String, String>>()
        val end = range.last + 1
        var bodyStart = end
        var lineStart = range.first
        while (lineStart < end) {
            var newline = lineStart
            while (newline < end && data[newline] != LF) newline++
            val line = data.decodeToString(lineStart, newline).removeSuffix("\r")
            lineStart = newline + 1
            if (line.isEmpty()) {
                bodyStart = minOf(lineStart, end)
                break
            }
            headers += line.substringBefore(':').trim() to line.substringAfter(':', "").trim()
        }

        val disposition = headers.firstOrNull { it.first.equals("Content-Disposition", ignoreCase = true) }?.second
            ?.let { runCatching { ContentDisposition.parse(it) }.getOrNull() }
        val partType = headers.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second

        out.add("Part $number", TokenKind.HEADING)
        disposition?.parameter(ContentDisposition.Parameters.Name)?.let {
            out.add(" · ", TokenKind.PUNCTUATION)
            out.add(it, TokenKind.KEY)
        }
        disposition?.parameter(ContentDisposition.Parameters.FileName)?.let {
            out.add(" · ", TokenKind.PUNCTUATION)
            out.add(it, TokenKind.STRING)
        }
        out.newLine()
        for ((name, value) in headers) {
            out.indent(1)
            out.add(name, TokenKind.KEY)
            out.add(": ", TokenKind.PUNCTUATION)
            out.add(value, TokenKind.STRING)
            out.newLine()
        }
        content(out, bytes.substring(bodyStart, end), partType)
    }

    private fun content(out: CodeBuilder, content: ByteString, contentType: String?) {
        val lines = if (content.size == 0) null else decodeText(content, contentType)?.let(::splitLines)
        if (lines == null) {
            out.indent(1)
            out.add(if (content.size == 0) "<empty>" else "<binary, ${content.size} bytes>", TokenKind.COMMENT)
            out.newLine()
            return
        }
        lines.take(MAX_PREVIEW_LINES).forEach { line ->
            out.indent(1)
            out.add(if (line.length > MAX_LINE_LENGTH) line.take(MAX_LINE_LENGTH - 1) + "…" else line)
            out.newLine()
        }
        if (lines.size > MAX_PREVIEW_LINES) {
            out.indent(1)
            out.add("… ${lines.size - MAX_PREVIEW_LINES} more lines", TokenKind.COMMENT)
            out.newLine()
        }
    }

    /** Content ranges of the parts between [delimiter] lines, without the line break before each. */
    private fun split(data: ByteArray, delimiter: ByteArray): List<IntRange> {
        val parts = ArrayList<IntRange>()
        var at = indexOfDelimiter(data, delimiter, 0)
        while (at >= 0) {
            var start = at + delimiter.size
            if (start + 1 < data.size && data[start] == DASH && data[start + 1] == DASH) break
            while (start < data.size && data[start] != LF) start++
            start++
            if (start > data.size) break
            val next = indexOfDelimiter(data, delimiter, start)
            var end = if (next < 0) data.size else next
            if (next >= 0 && end > start && data[end - 1] == LF) end--
            if (next >= 0 && end > start && data[end - 1] == CR) end--
            parts += start until end
            at = next
        }
        return parts
    }

    /** The first match of [delimiter] from [from] that starts a line. */
    private fun indexOfDelimiter(data: ByteArray, delimiter: ByteArray, from: Int): Int {
        var i = from
        while (i <= data.size - delimiter.size) {
            if ((i == 0 || data[i - 1] == LF) && data.matchesAt(i, delimiter)) return i
            i++
        }
        return -1
    }

    private fun ByteArray.matchesAt(index: Int, other: ByteArray): Boolean {
        for (j in other.indices) if (this[index + j] != other[j]) return false
        return true
    }

    private fun boundaryOf(contentType: String?): String? = contentType
        ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
        ?.parameter("boundary")
        ?.removeSurrounding("\"")
        ?.takeIf { it.isNotEmpty() }

    private fun firstLineBoundary(data: ByteArray): String? {
        val end = data.indexOf(LF).let { if (it < 0) data.size else it }
        val line = data.decodeToString(0, end).removeSuffix("\r")
        return line.takeIf { it.startsWith("--") && it.length > 2 }?.substring(2)
    }
}

private const val MAX_PREVIEW_LINES = 20
private const val MAX_LINE_LENGTH = 240
private const val LF = '\n'.code.toByte()
private const val CR = '\r'.code.toByte()
private const val DASH = '-'.code.toByte()
