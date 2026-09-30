package uz.jahonov.ktormonitor.body

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import uz.jahonov.ktormonitor.model.CapturedBody

internal fun body(text: String, size: Long? = null): CapturedBody {
    val bytes = text.encodeToByteString()
    return CapturedBody(bytes, size ?: bytes.size.toLong())
}

internal fun body(bytes: ByteArray, size: Long = bytes.size.toLong()) = CapturedBody(ByteString(bytes), size)

internal fun bytesOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

internal val CodeDocument.texts: List<String> get() = lines.map { it.text }

/** The highlighted pieces, without plain text, in order. */
internal val CodeDocument.tokens: List<Pair<String, TokenKind>>
    get() = lines.flatMap { it.spans }.filter { it.kind != TokenKind.PLAIN }.map { it.text to it.kind }
