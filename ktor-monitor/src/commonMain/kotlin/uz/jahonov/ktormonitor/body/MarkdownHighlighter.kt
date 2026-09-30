package uz.jahonov.ktormonitor.body

/**
 * Highlights Markdown source: headings, emphasis, links and code (inline and fenced), with list,
 * quote and rule markers as punctuation.
 */
internal object MarkdownHighlighter {

    fun highlight(text: String): CodeDocument {
        val out = CodeBuilder(CodeLanguage.MARKDOWN)
        var fence: String? = null
        for (line in splitLines(text)) {
            val trimmed = line.trim()
            val lineFence = fenceOf(trimmed)
            when {
                fence != null -> {
                    out.add(line, TokenKind.STRING)
                    if (trimmed.startsWith(fence)) fence = null
                }
                lineFence != null -> {
                    out.add(line, TokenKind.STRING)
                    fence = lineFence
                }
                headingLevel(trimmed) > 0 -> out.add(line, TokenKind.HEADING)
                isRule(trimmed) -> out.add(line, TokenKind.PUNCTUATION)
                else -> blockLine(out, line)
            }
            out.newLine()
        }
        return out.build()
    }

    private fun blockLine(out: CodeBuilder, line: String) {
        var start = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        out.add(line.substring(0, start))
        while (line.startsWith(">", start)) {
            val end = start + 1 + line.drop(start + 1).takeWhile { it == ' ' }.length
            out.add(line.substring(start, end), TokenKind.PUNCTUATION)
            start = end
        }
        listMarker(line.substring(start))?.let { marker ->
            out.add(line.substring(start, start + marker.contentStart), TokenKind.PUNCTUATION)
            start += marker.contentStart
        }
        inline(out, line, start)
    }

    private fun inline(out: CodeBuilder, line: String, from: Int) {
        val unmatched = HashSet<String>()
        var i = from
        while (i < line.length) {
            val c = line[i]
            val end = when {
                c == '\\' && i + 1 < line.length -> i + 2
                c == '`' -> closing(line, i, "`".repeat(runLength(line, i)), unmatched)
                c == '*' || c == '_' -> closing(line, i, if (line.getOrNull(i + 1) == c) "$c$c" else "$c", unmatched)
                c == '[' || c == '!' && line.getOrNull(i + 1) == '[' -> linkEnd(line, i, unmatched)
                else -> -1
            }
            val kind = when (c) {
                '`' -> TokenKind.STRING
                '*', '_' -> TokenKind.EMPHASIS
                else -> TokenKind.LINK
            }
            if (end > i && c != '\\') {
                out.add(line.substring(i, end), kind)
                i = end
            } else {
                val next = if (end > i) end else i + 1
                out.add(line.substring(i, next))
                i = next
            }
        }
    }

    private fun runLength(line: String, at: Int): Int {
        var end = at
        while (end < line.length && line[end] == line[at]) end++
        return end - at
    }

    /** The end of `marker…marker` starting at [at], or -1. */
    private fun closing(line: String, at: Int, marker: String, unmatched: MutableSet<String>): Int {
        if (marker in unmatched) return -1
        val close = line.indexOf(marker, at + marker.length + 1)
        if (close < 0) unmatched += marker
        return if (close < 0) -1 else close + marker.length
    }

    private fun linkEnd(line: String, at: Int, unmatched: MutableSet<String>): Int {
        if ("](" in unmatched) return -1
        val middle = line.indexOf("](", at)
        val close = if (middle < 0) -1 else line.indexOf(')', middle + 2)
        if (close < 0) unmatched += "]("
        return if (close < 0) -1 else close + 1
    }
}
