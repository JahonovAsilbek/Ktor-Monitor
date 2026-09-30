package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import uz.jahonov.ktormonitor.body.HexRow
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme

/** "offset  hex  ascii", 16 bytes a row; the offset and the ASCII column are muted. */
internal fun LazyListScope.hexItems(rows: List<HexRow>, pan: HorizontalPan) {
    items(rows.size, key = { "hex:$it" }, contentType = { "hex" }) { index ->
        val row = rows[index]
        val muted = MonitorTheme.colors.textSecondary
        val text = remember(row, muted) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = muted)) { append(row.offset) }
                append("  ")
                append(row.hex)
                append("  ")
                withStyle(SpanStyle(color = muted)) { append(row.ascii) }
            }
        }
        MonoRow(gutter = null, text = text, pan = pan)
    }
}
