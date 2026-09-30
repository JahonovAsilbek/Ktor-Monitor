package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.body.CodeDocument
import uz.jahonov.ktormonitor.body.CodeLine
import uz.jahonov.ktormonitor.body.TokenKind
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.color

/** Which regions of [document] are folded, and the rows that leaves on screen. */
@Stable
internal class CodeFolds(private val document: CodeDocument) {
    // Two regions can start on one line; the widest wins, as it hides the others anyway.
    private val regions = document.folds.sortedBy { it.endLine }.associateBy { it.startLine }

    private var collapsed by mutableStateOf(regions.values.filter { it.isCollapsedByDefault }.map { it.startLine }.toSet())

    val rows: List<TextRow> by derivedStateOf {
        buildList {
            var line = 0
            while (line < document.lines.size) {
                addLine(line, document.lines[line].text.length)
                val region = regions[line]
                line = if (region != null && line in collapsed) maxOf(region.endLine, line) + 1 else line + 1
            }
        }
    }

    fun startsRegion(line: Int): Boolean = line in regions

    fun isCollapsed(line: Int): Boolean = line in collapsed

    fun toggle(line: Int) {
        collapsed = if (line in collapsed) collapsed - line else collapsed + line
    }
}

/** Syntax colours by [TokenKind.ordinal], read once per page rather than once per line. */
@Composable
internal fun tokenColors(): List<Color> = TokenKind.entries.map { it.color() }

/** Highlighted code with line numbers; a line that opens a region folds it on a tap. */
internal fun LazyListScope.codeItems(document: CodeDocument, folds: CodeFolds, pan: HorizontalPan, colors: List<Color>) {
    val rows = folds.rows
    val digits = digitsOf(document.lines.size)
    items(rows.size, key = { "code:${rows[it].line}:${rows[it].start}" }, contentType = { "code" }) { index ->
        val row = rows[index]
        val foldable = row.isFirstOfLine && folds.startsRegion(row.line)
        val collapsed = folds.isCollapsed(row.line)
        val ellipsis = MonitorTheme.colors.textSecondary
        val text = remember(row, document, collapsed, colors) {
            document.lines[row.line].annotated(row.start, row.end, colors, ellipsis.takeIf { collapsed && row.isLastOfLine })
        }
        MonoRow(
            gutter = gutterOf(row, digits),
            text = text,
            pan = pan,
            modifier = if (foldable) {
                Modifier.clickable(
                    onClickLabel = if (collapsed) "Expand" else "Collapse",
                    role = Role.Button,
                    onClick = { folds.toggle(row.line) },
                )
            } else {
                Modifier
            },
        ) {
            FoldMark(visible = foldable, collapsed = collapsed)
        }
    }
}

@Composable
private fun FoldMark(visible: Boolean, collapsed: Boolean) {
    Box(Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
        if (visible) {
            Image(
                painter = painterResource(if (collapsed) MonitorIcons.ChevronRight else MonitorIcons.ChevronDown),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MonitorTheme.colors.textSecondary),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Characters [start] until [end] of the line, coloured; [ellipsis] marks a folded region. */
private fun CodeLine.annotated(start: Int, end: Int, colors: List<Color>, ellipsis: Color?): AnnotatedString =
    buildAnnotatedString {
        var at = 0
        for (span in spans) {
            val spanEnd = at + span.text.length
            if (spanEnd > start && at < end) {
                withStyle(span.kind.style(colors)) { append(span.text, maxOf(start, at) - at, minOf(end, spanEnd) - at) }
            }
            if (spanEnd >= end) break
            at = spanEnd
        }
        if (ellipsis != null) withStyle(SpanStyle(color = ellipsis)) { append(" …") }
    }

private fun TokenKind.style(colors: List<Color>) = SpanStyle(
    color = colors[ordinal],
    fontWeight = if (this == TokenKind.HEADING) FontWeight.Bold else null,
    fontStyle = if (this == TokenKind.EMPHASIS) FontStyle.Italic else null,
)
