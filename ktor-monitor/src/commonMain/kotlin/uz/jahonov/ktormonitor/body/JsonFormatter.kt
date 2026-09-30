package uz.jahonov.ktormonitor.body

/**
 * Pretty-prints JSON with a 2-space indent, keeping strings and numbers exactly as written.
 * Every multi-line object and array folds, expanded. Iterative, so deep nesting cannot overflow.
 */
internal object JsonFormatter {

    /** [text] formatted, or null when it is not valid JSON. */
    fun format(text: String): CodeDocument? = try {
        JsonPrinter(text).print()
    } catch (_: MalformedJson) {
        null
    }

    /** Whether [text] looks like a JSON object or array, a cheap check before [format]. */
    fun looksLikeJson(text: String): Boolean {
        val first = text.firstOrNull { !it.isJsonWhitespace() && it != '\uFEFF' }
        return first == '{' || first == '['
    }
}

private class MalformedJson : Exception()

private class Container(val isObject: Boolean, val startLine: Int) {
    val closer: Char get() = if (isObject) '}' else ']'
}

private class JsonPrinter(private val text: String) {
    private val out = CodeBuilder(CodeLanguage.JSON)
    private val open = ArrayList<Container>()
    private var pos = if (text.startsWith('\uFEFF')) 1 else 0

    fun print(): CodeDocument {
        var expectsValue = true
        while (true) {
            if (expectsValue && value()) continue
            expectsValue = false
            val container = open.lastOrNull() ?: break
            skipWhitespace()
            when (text.getOrNull(pos++)) {
                ',' -> {
                    out.add(",", TokenKind.PUNCTUATION)
                    startEntry(container)
                    expectsValue = true
                }
                container.closer -> close(container)
                else -> fail()
            }
        }
        skipWhitespace()
        if (pos < text.length) fail()
        return out.build()
    }

    /** Reads one value. True when it opened a non-empty container, whose first value comes next. */
    private fun value(): Boolean {
        skipWhitespace()
        when (val c = text.getOrNull(pos) ?: fail()) {
            '{', '[' -> {
                pos++
                val container = Container(isObject = c == '{', startLine = out.line)
                out.add(c.toString(), TokenKind.PUNCTUATION)
                skipWhitespace()
                if (text.getOrNull(pos) == container.closer) {
                    pos++
                    out.add(container.closer.toString(), TokenKind.PUNCTUATION)
                    return false
                }
                open += container
                startEntry(container)
                return true
            }
            '"' -> out.add(string(), TokenKind.STRING)
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            else -> if (c == '-' || c in '0'..'9') number() else fail()
        }
        return false
    }

    private fun startEntry(container: Container) {
        out.newLine()
        out.indent(open.size)
        if (!container.isObject) return
        skipWhitespace()
        if (text.getOrNull(pos) != '"') fail()
        out.add(string(), TokenKind.KEY)
        skipWhitespace()
        if (text.getOrNull(pos++) != ':') fail()
        out.add(":", TokenKind.PUNCTUATION)
        out.add(" ")
    }

    private fun close(container: Container) {
        open.removeAt(open.lastIndex)
        out.newLine()
        out.indent(open.size)
        out.add(container.closer.toString(), TokenKind.PUNCTUATION)
        out.fold(container.startLine)
    }

    private fun string(): String {
        val start = pos++
        while (true) {
            val c = text.getOrNull(pos++) ?: fail()
            when {
                c == '"' -> return text.substring(start, pos)
                c == '\\' -> when (text.getOrNull(pos++)) {
                    '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                    'u' -> repeat(4) { if (text.getOrNull(pos++)?.isHexDigit() != true) fail() }
                    else -> fail()
                }
                c < ' ' -> fail()
            }
        }
    }

    private fun number() {
        val start = pos
        if (text.getOrNull(pos) == '-') pos++
        if (text.getOrNull(pos) == '0') pos++ else digits()
        if (text.getOrNull(pos) == '.') {
            pos++
            digits()
        }
        if (text.getOrNull(pos) == 'e' || text.getOrNull(pos) == 'E') {
            pos++
            if (text.getOrNull(pos) == '+' || text.getOrNull(pos) == '-') pos++
            digits()
        }
        out.add(text.substring(start, pos), TokenKind.NUMBER)
    }

    private fun digits() {
        val start = pos
        while (text.getOrNull(pos)?.let { it in '0'..'9' } == true) pos++
        if (pos == start) fail()
    }

    private fun literal(word: String) {
        if (!text.startsWith(word, pos)) fail()
        pos += word.length
        out.add(word, TokenKind.KEYWORD)
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isJsonWhitespace()) pos++
    }

    private fun fail(): Nothing = throw MalformedJson()
}

private fun Char.isJsonWhitespace() = this == ' ' || this == '\n' || this == '\r' || this == '\t'

private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
