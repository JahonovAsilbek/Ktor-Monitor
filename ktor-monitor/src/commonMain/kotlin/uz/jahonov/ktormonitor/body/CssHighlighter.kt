package uz.jahonov.ktormonitor.body

/** Highlights CSS as written: selectors, properties, values and comments. Each rule block folds. */
internal object CssHighlighter {
    fun highlight(text: String): CodeDocument = CssReader(text).read()
}

private enum class CssPart { SELECTOR, PROPERTY, VALUE }

private class CssReader(private val text: String) {
    private val out = CodeBuilder(CodeLanguage.CSS)
    private val blocks = ArrayList<Int>()
    private var pos = 0
    private var part: CssPart? = null

    fun read(): CodeDocument {
        while (pos < text.length) {
            val c = text[pos]
            when {
                c.isWhitespace() -> take(TokenKind.PLAIN) { it.isWhitespace() }
                text.startsWith("/*", pos) -> comment()
                c == '"' || c == '\'' -> string(c)
                c == '{' -> {
                    blocks += out.line
                    punctuation()
                    part = null
                }
                c == '}' -> {
                    punctuation()
                    blocks.removeLastOrNull()?.let { out.fold(it) }
                    part = null
                }
                c == ';' -> {
                    punctuation()
                    part = null
                }
                else -> token(c, part ?: statementPart().also { part = it })
            }
        }
        return out.build()
    }

    private fun token(c: Char, part: CssPart) {
        when (part) {
            CssPart.SELECTOR -> if (c == '@') word(TokenKind.KEYWORD, part) else word(TokenKind.TAG, part)
            CssPart.PROPERTY -> if (c == ':') {
                punctuation()
                this.part = CssPart.VALUE
            } else {
                word(TokenKind.KEY, part)
            }
            CssPart.VALUE -> when {
                c.isDigit() || (c == '.' || c == '-' || c == '+') && text.getOrNull(pos + 1)?.isDigit() == true -> {
                    take(TokenKind.NUMBER, skip = 1) { it.isLetterOrDigit() || it == '.' || it == '%' }
                }
                c == '#' -> {
                    take(TokenKind.NUMBER, skip = 1) { it.isLetterOrDigit() }
                }
                c == '!' -> {
                    take(TokenKind.KEYWORD, skip = 1) { it.isLetter() }
                }
                c in VALUE_PUNCTUATION -> punctuation()
                else -> word(TokenKind.STRING, part)
            }
        }
    }

    /** At the start of a statement: a nested rule if a `{` comes before any `;` or `}`. */
    private fun statementPart(): CssPart {
        if (blocks.isEmpty()) return CssPart.SELECTOR
        var i = pos
        while (i < text.length) {
            when (text[i]) {
                '{' -> return CssPart.SELECTOR
                ';', '}' -> return CssPart.PROPERTY
            }
            i++
        }
        return CssPart.PROPERTY
    }

    private fun word(kind: TokenKind, part: CssPart) {
        val start = pos
        while (pos < text.length && !isWordEnd(text[pos], part)) pos++
        if (pos == start) pos++
        out.add(text.substring(start, pos), kind)
    }

    private fun isWordEnd(c: Char, part: CssPart) =
        c.isWhitespace() || c in "{};\"'" || text.startsWith("/*", pos) ||
            part == CssPart.PROPERTY && c == ':' ||
            part == CssPart.VALUE && (c in VALUE_PUNCTUATION || c == '!')

    private fun comment() {
        val end = text.indexOf("*/", pos + 2).let { if (it < 0) text.length else it + 2 }
        out.addSource(text.substring(pos, end), TokenKind.COMMENT)
        pos = end
    }

    private fun string(quote: Char) {
        val start = pos++
        while (pos < text.length && text[pos] != quote && text[pos] != '\n') {
            if (text[pos] == '\\') pos++
            pos++
        }
        pos = minOf(pos, text.length)
        if (pos < text.length && text[pos] == quote) pos++
        out.addSource(text.substring(start, pos), TokenKind.STRING)
    }

    private fun punctuation() {
        out.add(text[pos].toString(), TokenKind.PUNCTUATION)
        pos++
    }

    /** Takes [skip] characters and then all that [accepts] as one span. */
    private inline fun take(kind: TokenKind, skip: Int = 0, accepts: (Char) -> Boolean) {
        val start = pos
        pos += skip
        while (pos < text.length && accepts(text[pos])) pos++
        out.addSource(text.substring(start, pos), kind)
    }
}

private const val VALUE_PUNCTUATION = "(),/"
