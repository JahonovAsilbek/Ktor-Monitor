package uz.jahonov.ktormonitor.body

/**
 * Highlights YAML line by line: keys, scalar values by type, comments and list dashes. Lines of a
 * `|` or `>` block scalar are strings.
 */
internal object YamlHighlighter {

    fun highlight(text: String): CodeDocument {
        val out = CodeBuilder(CodeLanguage.YAML)
        var blockScalarIndent = -1
        for (line in splitLines(text)) {
            val indent = line.indexOfFirst { it != ' ' }.let { if (it < 0) line.length else it }
            if (blockScalarIndent >= 0 && (indent > blockScalarIndent || line.isBlank())) {
                out.add(line, TokenKind.STRING)
                out.newLine()
                continue
            }
            blockScalarIndent = if (line(out, line, indent)) indent else -1
            out.newLine()
        }
        return out.build()
    }

    /** Writes one line; true when it opens a block scalar. */
    private fun line(out: CodeBuilder, line: String, indent: Int): Boolean {
        out.add(line.substring(0, indent))
        var rest = line.substring(indent)
        when {
            rest.startsWith('#') -> {
                out.add(rest, TokenKind.COMMENT)
                return false
            }
            rest == "---" || rest == "..." || rest.startsWith("--- ") -> {
                out.add(rest, TokenKind.PUNCTUATION)
                return false
            }
        }
        while (rest == "-" || rest.startsWith("- ")) {
            out.add("-", TokenKind.PUNCTUATION)
            val spaces = rest.drop(1).takeWhile { it == ' ' }
            out.add(spaces)
            rest = rest.substring(1 + spaces.length)
        }
        val colon = keyColon(rest)
        if (colon >= 0) {
            out.add(rest.substring(0, colon), TokenKind.KEY)
            out.add(":", TokenKind.PUNCTUATION)
            rest = rest.substring(colon + 1)
        }
        return value(out, rest)
    }

    /** The colon ending a mapping key in [text], or -1. */
    private fun keyColon(text: String): Int {
        if (text.isEmpty() || text[0] in "[{#") return -1
        var i = 0
        val quote = text[0]
        if (quote == '"' || quote == '\'') {
            i = text.indexOf(quote, 1)
            if (i < 0) return -1
        }
        while (i < text.length) {
            if (text[i] == ':' && (i + 1 == text.length || text[i + 1] == ' ')) return i
            if (text[i] == '#' && i > 0 && text[i - 1] == ' ') return -1
            i++
        }
        return -1
    }

    /** Writes a scalar with its trailing comment; true when it is a block scalar indicator. */
    private fun value(out: CodeBuilder, text: String): Boolean {
        val comment = commentStart(text)
        val body = text.substring(0, comment)
        val core = body.trim()
        val leading = body.substring(0, body.indexOf(core).coerceAtLeast(0))
        out.add(leading)
        val isBlockScalar = BLOCK_SCALAR.matches(core)
        out.add(core, kindOf(core, isBlockScalar))
        out.add(body.substring(leading.length + core.length))
        out.add(text.substring(comment), TokenKind.COMMENT)
        return isBlockScalar
    }

    private fun kindOf(core: String, isBlockScalar: Boolean) = when {
        isBlockScalar -> TokenKind.PUNCTUATION
        core.startsWith('"') || core.startsWith('\'') -> TokenKind.STRING
        core.lowercase() in KEYWORDS || core.startsWith('&') || core.startsWith('*') -> TokenKind.KEYWORD
        NUMBER.matches(core) -> TokenKind.NUMBER
        else -> TokenKind.STRING
    }

    /** Where a ` #` comment starts outside quotes, or the end of [text]. */
    private fun commentStart(text: String): Int {
        var quote: Char? = null
        for (i in text.indices) {
            val c = text[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> if (i == 0 || text[i - 1] == ' ') quote = c
                c == '#' && (i == 0 || text[i - 1] == ' ') -> return i
            }
        }
        return text.length
    }
}

private val KEYWORDS = setOf("true", "false", "yes", "no", "on", "off", "null", "~")

private val NUMBER = Regex("""[-+]?(\d[\d_]*(\.\d*)?([eE][-+]?\d+)?|\.\d+([eE][-+]?\d+)?|0x[0-9a-fA-F]+|0o[0-7]+|\.inf|\.nan)""", RegexOption.IGNORE_CASE)

private val BLOCK_SCALAR = Regex("""[|>][-+0-9]*""")
