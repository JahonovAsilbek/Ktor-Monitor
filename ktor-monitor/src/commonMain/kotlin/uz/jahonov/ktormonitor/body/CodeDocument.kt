package uz.jahonov.ktormonitor.body

/** The ways a body can be shown, in the order the UI prefers them. */
public enum class BodyMode { STREAM, PREVIEW, CODE, TEXT, HEX }

public enum class CodeLanguage { JSON, XML, HTML, CSS, JAVASCRIPT, YAML, MARKDOWN, FORM, MULTIPART }

/** What a piece of code is. The UI maps each kind to a colour. */
public enum class TokenKind { PLAIN, KEY, STRING, NUMBER, KEYWORD, PUNCTUATION, TAG, ATTRIBUTE, COMMENT, HEADING, EMPHASIS, LINK }

public data class CodeSpan(val text: String, val kind: TokenKind)

public data class CodeLine(val spans: List<CodeSpan>) {
    val text: String = spans.joinToString(separator = "") { it.text }
}

/** Lines [startLine]..[endLine] (inclusive, 0-based) can fold into [startLine]. */
public data class FoldRegion(val startLine: Int, val endLine: Int, val isCollapsedByDefault: Boolean)

/** A body laid out for the code view: highlighted lines and the regions that fold. */
public data class CodeDocument(val language: CodeLanguage, val lines: List<CodeLine>, val folds: List<FoldRegion>)

/** One row of the hex view: 16 bytes at [offset]. The last row's [hex] is padded to full width. */
public data class HexRow(val offset: String, val hex: String, val ascii: String)
