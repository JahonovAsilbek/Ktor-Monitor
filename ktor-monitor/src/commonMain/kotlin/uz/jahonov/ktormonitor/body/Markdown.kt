package uz.jahonov.ktormonitor.body

/**
 * The common subset of Markdown: ATX headings, paragraphs, bullet and numbered lists nested by
 * indentation, quotes, fenced code blocks and rules; inline bold, italic, code and links.
 */
internal object MarkdownParser {

    fun parse(text: String): List<MarkdownBlock> = BlockParser(splitLines(text)).parse()

    fun inline(text: String): List<MarkdownSpan> = InlineParser(text).parse()
}

/** The number of `#` of an ATX heading, or 0. [line] starts at its first non-blank character. */
internal fun headingLevel(line: String): Int {
    val level = line.takeWhile { it == '#' }.length
    return if (level in 1..6 && (line.length == level || line[level] == ' ')) level else 0
}

/** The text of an ATX heading without its markers; a closing `#` run counts only after a space. */
internal fun headingText(line: String, level: Int): String {
    val content = line.substring(level).trim()
    val open = content.trimEnd('#')
    return if (open.isEmpty() || open.endsWith(' ')) open.trim() else content
}

/** A thematic break: three or more `-`, `*` or `_`, spaces allowed between. */
internal fun isRule(trimmed: String): Boolean {
    val c = trimmed.firstOrNull() ?: return false
    return c in "-*_" && trimmed.count { it == c } >= 3 && trimmed.all { it == c || it == ' ' }
}

/** The opening fence of a code block (``` or ~~~), or null. */
internal fun fenceOf(trimmed: String): String? = when {
    trimmed.startsWith("```") -> "```"
    trimmed.startsWith("~~~") -> "~~~"
    else -> null
}

/** A list item marker: [indent] spaces, the marker, and the content from [contentStart]. */
internal class ListMarker(val indent: Int, val number: Int?, val contentStart: Int)

internal fun listMarker(line: String): ListMarker? {
    val indent = line.indexOfFirst { it != ' ' && it != '\t' }.takeIf { it >= 0 } ?: return null
    val c = line[indent]
    if (c in "-*+") {
        return if (line.getOrNull(indent + 1) == ' ') ListMarker(indent, null, indent + 2) else null
    }
    val digits = line.substring(indent).takeWhile { it.isDigit() }
    if (digits.isEmpty() || digits.length > 9) return null
    val after = indent + digits.length
    val isMarker = line.getOrNull(after).let { it == '.' || it == ')' } && line.getOrNull(after + 1) == ' '
    return if (isMarker) ListMarker(indent, digits.toInt(), after + 2) else null
}

private sealed interface OpenBlock {
    data object Paragraph : OpenBlock
    data object Quote : OpenBlock
    class Item(val depth: Int, val number: Int?) : OpenBlock
}

private class BlockParser(private val lines: List<String>) {
    private val blocks = ArrayList<MarkdownBlock>()
    private val listIndents = ArrayList<Int>()
    private var open: OpenBlock? = null
    private val openText = StringBuilder()

    fun parse(): List<MarkdownBlock> {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val fence = fenceOf(trimmed)
            val marker = listMarker(line)
            when {
                fence != null -> {
                    finish()
                    listIndents.clear()
                    val language = trimmed.removePrefix(fence).trim().takeIf { it.isNotEmpty() }
                    val end = (i + 1 until lines.size).firstOrNull { lines[it].trim().startsWith(fence) } ?: lines.size
                    blocks += MarkdownBlock.CodeBlock(language, lines.subList(i + 1, end).joinToString("\n"))
                    i = end
                }
                trimmed.isEmpty() -> finish()
                headingLevel(trimmed) > 0 -> {
                    finish()
                    listIndents.clear()
                    val level = headingLevel(trimmed)
                    blocks += MarkdownBlock.Heading(level, MarkdownParser.inline(headingText(trimmed, level)))
                }
                isRule(trimmed) -> {
                    finish()
                    listIndents.clear()
                    blocks += MarkdownBlock.Rule
                }
                trimmed.startsWith('>') -> {
                    if (open != OpenBlock.Quote) {
                        finish()
                        open = OpenBlock.Quote
                    }
                    append(trimmed.trimStart('>', ' '))
                }
                marker != null -> {
                    finish()
                    open = OpenBlock.Item(depthOf(marker.indent), marker.number)
                    append(line.substring(marker.contentStart).trim())
                }
                else -> {
                    if (open == null) {
                        listIndents.clear()
                        open = OpenBlock.Paragraph
                    }
                    append(trimmed)
                }
            }
            i++
        }
        finish()
        return blocks
    }

    private fun depthOf(indent: Int): Int {
        while (listIndents.isNotEmpty() && listIndents.last() > indent) listIndents.removeAt(listIndents.lastIndex)
        if (listIndents.lastOrNull() != indent) listIndents += indent
        return listIndents.lastIndex
    }

    private fun append(text: String) {
        if (openText.isNotEmpty() && text.isNotEmpty()) openText.append(' ')
        openText.append(text)
    }

    private fun finish() {
        val block = open ?: return
        val spans = MarkdownParser.inline(openText.toString())
        when (block) {
            OpenBlock.Paragraph -> blocks += MarkdownBlock.Paragraph(spans)
            OpenBlock.Quote -> blocks += MarkdownBlock.Quote(spans)
            is OpenBlock.Item -> blocks += MarkdownBlock.ListItem(block.depth, block.number, spans)
        }
        open = null
        openText.clear()
    }
}

/**
 * Inline spans of one block. A marker without a closer stays literal text; failed searches are
 * remembered so that a long line of unmatched markers stays linear.
 */
private class InlineParser(private val text: String) {
    private val spans = ArrayList<MarkdownSpan>()
    private val plain = StringBuilder()
    private var bracketFrom = -1
    private var bracketAt = -1

    fun parse(): List<MarkdownSpan> {
        parse(0, text.length, MarkdownSpan(""))
        return spans
    }

    private fun parse(start: Int, end: Int, style: MarkdownSpan) {
        val unmatched = HashSet<String>()
        var i = start
        while (i < end) {
            val c = text[i]
            val next = if (i + 1 < end) text[i + 1] else null
            val double = "$c$c"
            i = when {
                c == '\\' && next != null && next in ESCAPABLE -> {
                    plain.append(next)
                    i + 2
                }
                c == '`' -> code(i, end, style, unmatched)
                (c == '*' || c == '_') && next == c && !style.isBold && double !in unmatched ->
                    emphasis(i, end, double, style, style.copy(isBold = true), unmatched)
                (c == '*' || c == '_') && !style.isItalic && c.toString() !in unmatched ->
                    emphasis(i, end, c.toString(), style, style.copy(isItalic = true), unmatched)
                c == '[' && style.link == null && "]" !in unmatched -> link(i, i + 1, end, style, unmatched)
                c == '!' && next == '[' && style.link == null && "]" !in unmatched -> link(i, i + 2, end, style, unmatched)
                else -> {
                    plain.append(c)
                    i + 1
                }
            }
        }
        flush(style)
    }

    /** `marker…marker` from [at] in [inner] style, or the marker as text. Returns the next index. */
    private fun emphasis(
        at: Int,
        end: Int,
        marker: String,
        outer: MarkdownSpan,
        inner: MarkdownSpan,
        unmatched: MutableSet<String>,
    ): Int {
        val contentStart = at + marker.length
        val isUnderscore = marker[0] == '_'
        val opensWord = contentStart < end && !text[contentStart].isWhitespace()
        if (!opensWord || isUnderscore && text.getOrNull(at - 1)?.isLetterOrDigit() == true) {
            plain.append(marker)
            return contentStart
        }
        var close = contentStart + 1
        while (true) {
            close = text.indexOf(marker, close)
            if (close < 0 || close + marker.length > end) {
                unmatched += marker
                plain.append(marker)
                return contentStart
            }
            val after = text.getOrNull(close + marker.length)
            val isPartOfDouble = marker.length == 1 && after == marker[0]
            val closes = !isPartOfDouble && !text[close - 1].isWhitespace() &&
                !(isUnderscore && after?.isLetterOrDigit() == true)
            if (closes) break
            close += if (isPartOfDouble) 2 else 1
        }
        flush(outer)
        parse(contentStart, close, inner)
        return close + marker.length
    }

    private fun code(at: Int, end: Int, style: MarkdownSpan, unmatched: MutableSet<String>): Int {
        var run = 0
        while (at + run < end && text[at + run] == '`') run++
        val marker = "`".repeat(run)
        val close = if (marker in unmatched) -1 else text.indexOf(marker, at + run)
        if (close < 0 || close + run > end) {
            unmatched += marker
            plain.append(marker)
            return at + run
        }
        flush(style)
        val code = text.substring(at + run, close).trim()
        if (code.isNotEmpty()) add(style.copy(text = code, isCode = true))
        return close + run
    }

    /** `[text](url)` or `![alt](url)` from [at], its text starting at [textStart]. */
    private fun link(at: Int, textStart: Int, end: Int, style: MarkdownSpan, unmatched: MutableSet<String>): Int {
        val closeBracket = closingBracket(textStart)
        val closeParen = when {
            closeBracket !in textStart until end -> -1
            text.getOrNull(closeBracket + 1) != '(' -> null
            else -> text.indexOf(')', closeBracket + 2).takeIf { it in 0 until end } ?: -1
        }
        if (closeParen == null || closeParen < 0) {
            // With no `]` or no `)` left in range, no later link here can close either.
            if (closeParen != null) unmatched += "]"
            plain.append(text, at, textStart)
            return textStart
        }
        val url = text.substring(closeBracket + 2, closeParen).trim().substringBefore(' ')
        flush(style)
        parse(textStart, closeBracket, style.copy(link = url))
        return closeParen + 1
    }

    /** The first `]` from [from]; a repeated search inside the last answer reuses it. */
    private fun closingBracket(from: Int): Int {
        if (from in bracketFrom..bracketAt) return bracketAt
        bracketFrom = from
        bracketAt = text.indexOf(']', from)
        return bracketAt
    }

    private fun flush(style: MarkdownSpan) {
        if (plain.isEmpty()) return
        add(style.copy(text = plain.toString()))
        plain.clear()
    }

    private fun add(span: MarkdownSpan) {
        val last = spans.lastOrNull()
        if (last != null && last.copy(text = "") == span.copy(text = "")) {
            spans[spans.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            spans += span
        }
    }
}

private const val ESCAPABLE = "\\`*_{}[]()#+-.!>|~"
