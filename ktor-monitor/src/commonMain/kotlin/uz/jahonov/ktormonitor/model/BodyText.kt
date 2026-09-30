package uz.jahonov.ktormonitor.model

import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.readText
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.write

/** Text-like media types, whatever their charset. */
internal fun isTextual(contentType: String?): Boolean {
    val type = contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() } ?: return false
    val subtype = type.contentSubtype.lowercase()
    return type.contentType.equals("text", ignoreCase = true) ||
        subtype in TEXT_SUBTYPES ||
        subtype.endsWith("+json") ||
        subtype.endsWith("+xml")
}

/**
 * [bytes] as text: in the charset [contentType] names (UTF-8 when it names none or one this platform
 * lacks), or null when the body is binary. A body without a content type is text when it decodes
 * as UTF-8 without control characters.
 */
internal fun decodeText(bytes: ByteString, contentType: String?): String? {
    val type = contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }
    if (type == null) return bytes.decodeToString().takeIf { it.looksLikeText() }
    if (!isTextual(contentType)) return null

    val charset = runCatching { type.charset() }.getOrNull()?.takeIf { it != Charsets.UTF_8 }
        ?: return bytes.decodeToString()
    return runCatching { Buffer().apply { write(bytes) }.readText(charset) }.getOrElse { bytes.decodeToString() }
}

private fun String.looksLikeText() =
    '�' !in this && none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }

private val TEXT_SUBTYPES = setOf(
    "json", "xml", "javascript", "x-javascript", "ecmascript", "x-www-form-urlencoded", "graphql",
    "yaml", "x-yaml", "problem+json", "xhtml+xml", "x-ndjson", "ld+json",
)
