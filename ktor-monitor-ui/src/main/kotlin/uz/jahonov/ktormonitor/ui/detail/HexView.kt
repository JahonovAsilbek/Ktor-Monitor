package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import uz.jahonov.ktormonitor.body.HexRow
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme

/**
 * "offset  hex  ascii", the offset and the ASCII column muted. Each 16-byte row is shown as two lines
 * of 8 bytes: a full row is wider than a phone, and wrapping it would break the columns.
 */
internal fun LazyListScope.hexItems(rows: List<HexRow>) {
    val count = if (rows.lastOrNull()?.let { it.secondHalfHex().isBlank() } == true) rows.size * 2 - 1 else rows.size * 2
    items(count, key = { "hex:$it" }, contentType = { "hex" }) { index ->
        val row = rows[index / 2]
        val second = index % 2 == 1
        val muted = MonitorTheme.colors.textSecondary
        val text = remember(row, second, muted) { row.half(second, muted) }
        MonoRow(gutter = null, text = text)
    }
}

private fun HexRow.half(second: Boolean, muted: Color): AnnotatedString = buildAnnotatedString {
    val offset = if (second) (offset.toLong(16) + HALF).toString(16).padStart(offset.length, '0') else offset
    withStyle(SpanStyle(color = muted)) { append(offset) }
    append("  ")
    append(if (second) secondHalfHex() else hex.take(HALF_HEX))
    append("  ")
    withStyle(SpanStyle(color = muted)) { append(if (second) ascii.drop(HALF) else ascii.take(HALF)) }
}

/** The hex is the two halves of 8 bytes ("xx" each, one space apart) joined by two spaces. */
private fun HexRow.secondHalfHex(): String = hex.drop(HALF_HEX + 2)

private const val HALF = 8
private const val HALF_HEX = HALF * 3 - 1
