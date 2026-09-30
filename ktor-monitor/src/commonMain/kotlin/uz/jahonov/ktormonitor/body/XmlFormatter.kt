package uz.jahonov.ktormonitor.body

/**
 * Pretty-prints XML or HTML by nesting, with a 2-space indent. Lenient: unclosed and stray tags,
 * HTML void elements and raw `script`/`style` text are all taken as they come. Each element that
 * spans lines folds, collapsed except the outermost ones.
 */
internal object XmlFormatter {

    fun format(text: String, isHtml: Boolean): CodeDocument {
        val language = if (isHtml) CodeLanguage.HTML else CodeLanguage.XML
        return MarkupPrinter(language, isHtml).print(MarkupReader(text, isHtml).read())
    }
}

private sealed interface Markup {
    /** A start, end or empty tag; [spans] are its highlighted source on one line. */
    class Tag(val name: String, val spans: List<CodeSpan>, val isEnd: Boolean, val isEmpty: Boolean) : Markup

    /** Text, a comment, CDATA or a doctype, printed line by line in one [kind]. */
    class Block(val text: String, val kind: TokenKind) : Markup
}

private class MarkupReader(private val text: String, private val isHtml: Boolean) {
    private val tokens = ArrayList<Markup>()
    private val pendingText = StringBuilder()
    private var pos = 0

    fun read(): List<Markup> {
        while (pos < text.length) {
            val lt = text.indexOf('<', pos)
            if (lt < 0) {
                pendingText.append(text, pos, text.length)
                break
            }
            pendingText.append(text, pos, lt)
            pos = lt
            val next = text.getOrNull(pos + 1)
            when {
                text.startsWith("<!--", pos) -> block("-->", TokenKind.COMMENT)
                text.startsWith("<![CDATA[", pos) -> block("]]>", TokenKind.STRING)
                next == '!' -> block(">", TokenKind.TAG)
                next == '?' -> startTag(prefix = "<?")
                next == '/' -> endTag()
                next != null && (next.isLetter() || next == '_' || next == ':') -> startTag(prefix = "<")
                else -> {
                    pendingText.append('<')
                    pos++
                }
            }
        }
        flushText()
        return tokens
    }

    private fun block(terminator: String, kind: TokenKind) {
        val end = text.indexOf(terminator, pos).let { if (it < 0) text.length else it + terminator.length }
        emit(Markup.Block(text.substring(pos, end), kind))
        pos = end
    }

    private fun endTag() {
        pos += 2
        val name = readName()
        val close = text.indexOf('>', pos)
        pos = if (close < 0) text.length else close + 1
        emit(Markup.Tag(name, listOf(CodeSpan("</$name>", TokenKind.TAG)), isEnd = true, isEmpty = false))
    }

    private fun startTag(prefix: String) {
        val isInstruction = prefix == "<?"
        pos += prefix.length
        val name = readName()
        val spans = arrayListOf(CodeSpan(prefix + name, TokenKind.TAG))
        var isEmpty = isInstruction
        while (true) {
            skipWhitespace()
            if (pos >= text.length) break
            if (text.startsWith("/>", pos) || isInstruction && text.startsWith("?>", pos)) {
                spans += CodeSpan(if (isInstruction) "?>" else " />", TokenKind.TAG)
                isEmpty = true
                pos += 2
                break
            }
            if (text[pos] == '>') {
                spans += CodeSpan(">", TokenKind.TAG)
                pos++
                break
            }
            attribute(spans)
        }
        emit(Markup.Tag(name, spans, isEnd = false, isEmpty = isEmpty))
        if (isHtml && !isEmpty && name.lowercase() in RAW_TEXT_ELEMENTS) rawText(name)
    }

    private fun attribute(spans: MutableList<CodeSpan>) {
        val start = pos
        while (pos < text.length && !text[pos].isWhitespace() && text[pos] !in "=>" && !text.startsWith("/>", pos)) pos++
        if (pos == start) pos++
        spans += CodeSpan(" ", TokenKind.PLAIN)
        spans += CodeSpan(text.substring(start, pos), TokenKind.ATTRIBUTE)
        skipWhitespace()
        if (text.getOrNull(pos) != '=') return
        pos++
        skipWhitespace()
        val valueStart = pos
        val quote = text.getOrNull(pos)
        if (quote == '"' || quote == '\'') {
            val close = text.indexOf(quote, pos + 1)
            pos = if (close < 0) text.length else close + 1
        } else {
            while (pos < text.length && !text[pos].isWhitespace() && text[pos] != '>') pos++
        }
        spans += CodeSpan("=", TokenKind.PUNCTUATION)
        spans += CodeSpan(text.substring(valueStart, pos).toSingleLine(), TokenKind.STRING)
    }

    /** The body of a `script` or `style` element, which is not markup. */
    private fun rawText(name: String) {
        var end = pos
        while (true) {
            end = text.indexOf("</", end)
            if (end < 0 || text.regionMatches(end + 2, name, 0, name.length, ignoreCase = true)) break
            end += 2
        }
        if (end < 0) end = text.length
        pendingText.append(text, pos, end)
        pos = end
    }

    private fun readName(): String {
        val start = pos
        while (pos < text.length && !text[pos].isWhitespace() && text[pos] !in "/>?") pos++
        return text.substring(start, pos)
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun emit(token: Markup) {
        flushText()
        tokens += token
    }

    private fun flushText() {
        if (pendingText.isBlank()) {
            pendingText.clear()
            return
        }
        tokens += Markup.Block(pendingText.toString(), TokenKind.PLAIN)
        pendingText.clear()
    }
}

private class OpenElement(val name: String, val startLine: Int)

private class MarkupPrinter(language: CodeLanguage, private val isHtml: Boolean) {
    private val out = CodeBuilder(language)
    private val open = ArrayList<OpenElement>()

    fun print(tokens: List<Markup>): CodeDocument {
        var i = 0
        while (i < tokens.size) {
            i += when (val token = tokens[i]) {
                is Markup.Block -> {
                    block(token)
                    1
                }
                is Markup.Tag -> tag(token, tokens.getOrNull(i + 1), tokens.getOrNull(i + 2))
            }
        }
        while (open.isNotEmpty()) closeInnermost(endLine = out.line - 1)
        return out.build()
    }

    /** Prints [tag] and returns how many tokens it took: short elements stay on one line. */
    private fun tag(tag: Markup.Tag, next: Markup?, afterNext: Markup?): Int {
        when {
            tag.isEnd -> close(tag)
            tag.isEmpty || isHtml && tag.name.lowercase() in VOID_ELEMENTS -> line(tag.spans)
            next is Markup.Tag && closes(next, tag) -> {
                line(tag.spans + next.spans)
                return 2
            }
            next is Markup.Block && next.kind == TokenKind.PLAIN && afterNext is Markup.Tag &&
                closes(afterNext, tag) && next.text.fitsInline() -> {
                line(tag.spans + CodeSpan(next.text.trim(), TokenKind.PLAIN) + afterNext.spans)
                return 3
            }
            else -> {
                open += OpenElement(tag.name, out.line)
                out.indent(open.size - 1)
                tag.spans.forEach { out.add(it.text, it.kind) }
                out.newLine()
            }
        }
        return 1
    }

    private fun close(tag: Markup.Tag) {
        val index = open.indexOfLast { sameName(it.name, tag.name) }
        if (index < 0) return line(tag.spans)
        while (open.lastIndex > index) closeInnermost(endLine = out.line - 1)
        val element = open.removeAt(open.lastIndex)
        line(tag.spans)
        out.fold(element.startLine, out.line - 1, isCollapsed = open.isNotEmpty())
    }

    private fun closeInnermost(endLine: Int) {
        val element = open.removeAt(open.lastIndex)
        out.fold(element.startLine, endLine, isCollapsed = open.isNotEmpty())
    }

    private fun block(block: Markup.Block) {
        val lines = splitLines(block.text)
        val indent = lines.drop(1).filter { it.isNotBlank() }.minOfOrNull { line -> line.indexOfFirst { !it.isWhitespace() } } ?: 0
        lines.forEachIndexed { index, line ->
            val content = (if (index == 0) line.trimStart() else line.drop(indent)).trimEnd()
            if (content.isNotEmpty()) line(listOf(CodeSpan(content, block.kind)))
        }
    }

    private fun line(spans: List<CodeSpan>) {
        out.indent(open.size)
        spans.forEach { out.add(it.text, it.kind) }
        out.newLine()
    }

    private fun closes(end: Markup.Tag, start: Markup.Tag) = end.isEnd && sameName(end.name, start.name)

    private fun sameName(a: String, b: String) = a.equals(b, ignoreCase = isHtml)
}

private fun String.fitsInline(): Boolean {
    val trimmed = trim()
    return trimmed.length <= MAX_INLINE_TEXT && '\n' !in trimmed
}

private fun String.toSingleLine() = replace("\r", "").replace('\n', ' ')

private const val MAX_INLINE_TEXT = 80

private val RAW_TEXT_ELEMENTS = setOf("script", "style")

private val VOID_ELEMENTS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
)
