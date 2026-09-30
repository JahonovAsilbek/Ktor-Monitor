package uz.jahonov.ktormonitor.body

/** Collects spans line by line. Adjacent spans of one kind are merged. */
internal class CodeBuilder(private val language: CodeLanguage) {
    private val lines = ArrayList<CodeLine>()
    private val folds = ArrayList<FoldRegion>()
    private var spans = ArrayList<CodeSpan>()
    private val pending = StringBuilder()
    private var pendingKind = TokenKind.PLAIN

    /** Index of the line being written. */
    val line: Int get() = lines.size

    /** Appends [text] to the current line; it must not contain a line break. */
    fun add(text: CharSequence, kind: TokenKind = TokenKind.PLAIN) {
        if (text.isEmpty()) return
        if (kind != pendingKind) {
            flushSpan()
            pendingKind = kind
        }
        pending.append(text)
    }

    /** Appends source text as it is: each `\n` starts a new line, a `\r` before it is dropped. */
    fun addSource(text: CharSequence, kind: TokenKind = TokenKind.PLAIN) {
        var start = 0
        for (i in text.indices) {
            if (text[i] != '\n') continue
            val end = if (i > start && text[i - 1] == '\r') i - 1 else i
            add(text.subSequence(start, end), kind)
            newLine()
            start = i + 1
        }
        add(text.subSequence(start, text.length), kind)
    }

    fun indent(depth: Int) = repeat(depth) { add("  ") }

    fun newLine() {
        flushSpan()
        lines += CodeLine(spans)
        spans = ArrayList()
    }

    fun fold(startLine: Int, endLine: Int = line, isCollapsed: Boolean = false) {
        if (endLine > startLine) folds += FoldRegion(startLine, endLine, isCollapsed)
    }

    /** The document so far. An empty last line is dropped, like a trailing newline in a file. */
    fun build(): CodeDocument {
        flushSpan()
        if (spans.isNotEmpty()) newLine()
        val lastLine = lines.lastIndex
        val regions = folds
            .map { if (it.endLine > lastLine) it.copy(endLine = lastLine) else it }
            .filter { it.endLine > it.startLine }
            .sortedBy { it.startLine }
        return CodeDocument(language, lines, regions)
    }

    private fun flushSpan() {
        if (pending.isEmpty()) return
        spans += CodeSpan(pending.toString(), pendingKind)
        pending.clear()
    }
}

/** [text] split on `\n` with `\r` dropped; a trailing newline adds no empty line. */
internal fun splitLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val lines = text.split('\n').map { it.removeSuffix("\r") }
    return if (text.endsWith('\n')) lines.dropLast(1) else lines
}
