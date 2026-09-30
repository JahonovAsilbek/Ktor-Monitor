package uz.jahonov.ktormonitor.model

import io.ktor.http.ContentType

/** The badge a list row shows for a call's response; each UI gives every kind its own colour. */
public enum class ContentKind(public val label: String) {
    JSON("JSON"),
    XML("XML"),
    HTML("HTML"),
    TEXT("TEXT"),
    CSS("CSS"),
    JAVASCRIPT("JS"),
    YAML("YAML"),
    MARKDOWN("MD"),
    FORM("FORM"),
    MULTIPART("MULTI"),
    IMAGE("IMG"),
    FONT("FONT"),
    AUDIO("AUDIO"),
    VIDEO("VIDEO"),
    PDF("PDF"),
    EVENT_STREAM("SSE"),
    WEBSOCKET("WS"),
    BINARY("BIN"),
    NONE("—"),
    ;

    public companion object {
        public fun of(contentType: String?, url: String): ContentKind {
            if (url.startsWith("ws://") || url.startsWith("wss://")) return WEBSOCKET
            val type = contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() } ?: return NONE
            val main = type.contentType.lowercase()
            val sub = type.contentSubtype.lowercase()
            return when {
                sub == "json" || sub.endsWith("+json") || sub == "x-ndjson" -> JSON
                sub == "event-stream" -> EVENT_STREAM
                sub == "html" || sub == "xhtml+xml" -> HTML
                sub == "xml" || sub.endsWith("+xml") && main != "image" -> XML
                sub == "css" -> CSS
                sub in setOf("javascript", "x-javascript", "ecmascript") -> JAVASCRIPT
                sub in setOf("yaml", "x-yaml") -> YAML
                sub in setOf("markdown", "x-markdown") -> MARKDOWN
                sub == "x-www-form-urlencoded" -> FORM
                main == "multipart" -> MULTIPART
                main == "image" -> IMAGE
                main == "font" -> FONT
                main == "audio" -> AUDIO
                main == "video" -> VIDEO
                sub == "pdf" -> PDF
                main == "text" -> TEXT
                else -> BINARY
            }
        }
    }
}
