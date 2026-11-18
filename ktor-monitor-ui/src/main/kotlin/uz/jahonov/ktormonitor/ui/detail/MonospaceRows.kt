package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme

/**
 * One row on screen: characters [start] until [end] of line [line]. A line longer than [LONG_LINE] is
 * cut into several rows, so a minified body on one line never becomes one giant text layout.
 */
internal data class TextRow(val line: Int, val start: Int, val end: Int, val isLastOfLine: Boolean) {
    val isFirstOfLine: Boolean get() = start == 0
}

internal fun MutableList<TextRow>.addLine(line: Int, length: Int) {
    var start = 0
    do {
        val end = minOf(start + LONG_LINE, length)
        add(TextRow(line, start, end, isLastOfLine = end == length))
        start = end
    } while (start < length)
}

internal fun textRows(lines: List<String>): List<TextRow> =
    buildList { lines.forEachIndexed { index, text -> addLine(index, text.length) } }

@Composable
@ReadOnlyComposable
internal fun monoStyle(): TextStyle =
    MonitorTheme.typography.caption.copy(fontFamily = FontFamily.Monospace, color = MonitorTheme.colors.text)

/** The line number of [row], padded to [digits]; blank on the rows a long line continues on. */
internal fun gutterOf(row: TextRow, digits: Int): String =
    if (row.isFirstOfLine) (row.line + 1).toString().padStart(digits) else " ".repeat(digits)

internal fun digitsOf(lineCount: Int): Int = lineCount.coerceAtLeast(1).toString().length

/** Numbered lines of TEXT and STREAM bodies. */
internal fun LazyListScope.lineItems(lines: List<String>, rows: List<TextRow>) {
    val digits = digitsOf(lines.size)
    items(rows.size, key = { "text:${rows[it].line}:${rows[it].start}" }, contentType = { "text" }) { index ->
        val row = rows[index]
        MonoRow(
            gutter = gutterOf(row, digits),
            text = remember(row, lines) { AnnotatedString(lines[row.line].substring(row.start, row.end)) },
        )
    }
}

/** A gutter, then text that wraps onto as many lines as it needs: the body only ever scrolls vertically. */
@Composable
internal fun MonoRow(
    gutter: String?,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    between: @Composable () -> Unit = {},
) {
    val style = monoStyle()
    Row(modifier.fillMaxWidth()) {
        if (gutter != null) {
            BasicText(
                gutter,
                style = style.copy(color = MonitorTheme.colors.textDisabled),
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        between()
        BasicText(text, style = style, modifier = Modifier.weight(1f))
    }
}

private const val LONG_LINE = 1_000
