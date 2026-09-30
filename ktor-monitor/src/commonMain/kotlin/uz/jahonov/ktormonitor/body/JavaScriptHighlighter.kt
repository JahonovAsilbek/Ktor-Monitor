package uz.jahonov.ktormonitor.body

/** Highlights JavaScript as written: keywords, strings, numbers and comments. Each brace block folds. */
internal object JavaScriptHighlighter {

    fun highlight(text: String): CodeDocument {
        val out = CodeBuilder(CodeLanguage.JAVASCRIPT)
        val braces = ArrayList<Int>()
        var pos = 0
        fun span(end: Int, kind: TokenKind) {
            out.addSource(text.substring(pos, end), kind)
            pos = end
        }
        while (pos < text.length) {
            val c = text[pos]
            val next = text.getOrNull(pos + 1)
            when {
                c.isWhitespace() -> span(scan(text, pos) { it.isWhitespace() }, TokenKind.PLAIN)
                c == '/' && next == '/' -> span(text.indexOf('\n', pos).let { if (it < 0) text.length else it }, TokenKind.COMMENT)
                c == '/' && next == '*' -> span(text.indexOf("*/", pos + 2).let { if (it < 0) text.length else it + 2 }, TokenKind.COMMENT)
                c == '"' || c == '\'' || c == '`' -> span(stringEnd(text, pos), TokenKind.STRING)
                c.isDigit() || c == '.' && next?.isDigit() == true -> span(numberEnd(text, pos), TokenKind.NUMBER)
                c.isIdentifierStart() -> {
                    val end = scan(text, pos) { it.isIdentifierStart() || it.isDigit() }
                    span(end, if (text.substring(pos, end) in KEYWORDS) TokenKind.KEYWORD else TokenKind.PLAIN)
                }
                else -> {
                    if (c == '{') braces += out.line
                    span(pos + 1, TokenKind.PUNCTUATION)
                    if (c == '}') braces.removeLastOrNull()?.let { out.fold(it) }
                }
            }
        }
        return out.build()
    }

    private fun stringEnd(text: String, start: Int): Int {
        val quote = text[start]
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                quote -> return i + 1
                '\n' -> if (quote != '`') return i
            }
            i++
        }
        return text.length
    }

    private fun numberEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            val c = text[i]
            val isExponentSign = (c == '+' || c == '-') && text[i - 1].let { it == 'e' || it == 'E' } &&
                !text.startsWith("0x", start, ignoreCase = true)
            if (!c.isLetterOrDigit() && c != '.' && c != '_' && !isExponentSign) break
            i++
        }
        return i
    }

    private inline fun scan(text: String, start: Int, accepts: (Char) -> Boolean): Int {
        var i = start
        while (i < text.length && accepts(text[i])) i++
        return i
    }

    private fun Char.isIdentifierStart() = isLetter() || this == '_' || this == '$'
}

private val KEYWORDS = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
    "do", "else", "export", "extends", "false", "finally", "for", "from", "function", "if", "import", "in",
    "instanceof", "let", "new", "null", "return", "static", "super", "switch", "this", "throw", "true", "try",
    "typeof", "undefined", "var", "void", "while", "with", "yield",
)
