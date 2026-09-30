package uz.jahonov.ktormonitor.body

import uz.jahonov.ktormonitor.InternalKtorMonitorApi

/** The ways a body can be shown, in the order the UI prefers them. */
@InternalKtorMonitorApi
public enum class BodyMode(public val label: String) {
    STREAM("Stream"),
    PREVIEW("Preview"),
    CODE("Code"),
    TEXT("Text"),
    HEX("Hex"),
}

@InternalKtorMonitorApi
public enum class CodeLanguage { JSON, XML, HTML, CSS, JAVASCRIPT, YAML, MARKDOWN, FORM, MULTIPART }

/** What a piece of code is. The UI maps each kind to a colour. */
@InternalKtorMonitorApi
public enum class TokenKind { PLAIN, KEY, STRING, NUMBER, KEYWORD, PUNCTUATION, TAG, ATTRIBUTE, COMMENT, HEADING, EMPHASIS, LINK }

@InternalKtorMonitorApi
public data class CodeSpan(val text: String, val kind: TokenKind)

@InternalKtorMonitorApi
public data class CodeLine(val spans: List<CodeSpan>) {
    val text: String = spans.joinToString(separator = "") { it.text }
}

/** Lines [startLine]..[endLine] (inclusive, 0-based) can fold into [startLine]. */
@InternalKtorMonitorApi
public data class FoldRegion(val startLine: Int, val endLine: Int, val isCollapsedByDefault: Boolean)

/** A body laid out for the code view: highlighted lines and the regions that fold. */
@InternalKtorMonitorApi
public data class CodeDocument(val language: CodeLanguage, val lines: List<CodeLine>, val folds: List<FoldRegion>)

/** One row of the hex view: 16 bytes at [offset]. The last row's [hex] is padded to full width. */
@InternalKtorMonitorApi
public data class HexRow(val offset: String, val hex: String, val ascii: String)
