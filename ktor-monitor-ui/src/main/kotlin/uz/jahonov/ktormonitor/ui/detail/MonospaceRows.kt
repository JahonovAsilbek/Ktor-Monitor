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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme

/**
 * One sideways offset shared by every row of a body. Rows are not wrapped, and a horizontalScroll per
 * row would scroll each on its own (and a shared ScrollState would be clamped by the shortest row).
 * So each row lays its text out at full width and shifts it by [offset] at placement: panning costs a
 * re-placement of the visible rows, never a recomposition.
 */
@Stable
internal class HorizontalPan {
    private var offset by mutableFloatStateOf(0f)

    /** How far the widest row measured so far overflows; grows as rows scroll into view. */
    private var maxOffset = 0f

    val state = ScrollableState { delta ->
        val target = (offset - delta).coerceIn(0f, maxOffset)
        val consumed = offset - target
        offset = target
        consumed
    }

    fun Modifier.panned(): Modifier = clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = constraints.maxWidth
        maxOffset = maxOf(maxOffset, (placeable.width - width).toFloat())
        layout(width, placeable.height) { placeable.placeRelative(-offset.roundToInt(), 0) }
    }
}

internal fun Modifier.panGesture(pan: HorizontalPan): Modifier = scrollable(pan.state, Orientation.Horizontal)

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
internal fun LazyListScope.lineItems(lines: List<String>, rows: List<TextRow>, pan: HorizontalPan) {
    val digits = digitsOf(lines.size)
    items(rows.size, key = { "text:${rows[it].line}:${rows[it].start}" }, contentType = { "text" }) { index ->
        val row = rows[index]
        MonoRow(
            gutter = gutterOf(row, digits),
            text = remember(row, lines) { AnnotatedString(lines[row.line].substring(row.start, row.end)) },
            pan = pan,
        )
    }
}

/** A gutter that stays put and text that pans with the rest of the body. */
@Composable
internal fun MonoRow(
    gutter: String?,
    text: AnnotatedString,
    pan: HorizontalPan,
    modifier: Modifier = Modifier,
    between: @Composable () -> Unit = {},
) {
    val style = monoStyle()
    Row(modifier.fillMaxWidth().panGesture(pan)) {
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
        BasicText(
            text,
            style = style,
            softWrap = false,
            maxLines = 1,
            modifier = with(pan) { Modifier.weight(1f).panned() },
        )
    }
}

private const val LONG_LINE = 1_000
