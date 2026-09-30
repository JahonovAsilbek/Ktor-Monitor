package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.jahonov.ktormonitor.body.MarkdownBlock
import uz.jahonov.ktormonitor.body.MarkdownSpan
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme

/** Rendered Markdown, one item per block. */
internal fun LazyListScope.markdownItems(blocks: List<MarkdownBlock>) {
    items(blocks.size, key = { "md:$it" }, contentType = { blocks[it]::class }) { index ->
        MarkdownBlockView(blocks[index], Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, modifier: Modifier = Modifier) {
    val colors = MonitorTheme.colors
    val type = MonitorTheme.typography
    val body = type.body.copy(color = colors.text)
    when (block) {
        is MarkdownBlock.Heading -> SpansText(block.spans, headingStyle(block.level), modifier.padding(top = 8.dp))
        is MarkdownBlock.Paragraph -> SpansText(block.spans, body, modifier)
        is MarkdownBlock.ListItem -> Row(modifier.padding(start = 16.dp * block.depth)) {
            BasicText(
                block.number?.let { "$it." } ?: "•",
                style = body,
                modifier = Modifier.width(24.dp),
            )
            SpansText(block.spans, body, Modifier.weight(1f))
        }
        is MarkdownBlock.Quote -> Row(modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(colors.border))
            SpansText(
                block.spans,
                body.copy(color = colors.textSecondary, fontStyle = FontStyle.Italic),
                Modifier.weight(1f).padding(start = 12.dp),
            )
        }
        is MarkdownBlock.CodeBlock -> Box(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            BasicText(block.text, style = monoStyle(), softWrap = false)
        }
        MarkdownBlock.Rule -> Box(modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    val fontSize = when (level) {
        1 -> 24.sp
        2 -> 20.sp
        3 -> 18.sp
        else -> 16.sp
    }
    return MonitorTheme.typography.title.copy(color = MonitorTheme.colors.text, fontSize = fontSize, lineHeight = fontSize * 1.35f)
}

@Composable
private fun SpansText(spans: List<MarkdownSpan>, style: TextStyle, modifier: Modifier = Modifier) {
    val link = MonitorTheme.colors.info
    val codeBackground = MonitorTheme.colors.border
    val text = remember(spans, link, codeBackground) { annotated(spans, link, codeBackground) }
    BasicText(text, style = style, modifier = modifier)
}

/** Links open in the browser through the platform's UriHandler. */
private fun annotated(spans: List<MarkdownSpan>, link: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        for (span in spans) {
            val style = SpanStyle(
                fontWeight = if (span.isBold) FontWeight.Bold else null,
                fontStyle = if (span.isItalic) FontStyle.Italic else null,
                fontFamily = if (span.isCode) FontFamily.Monospace else null,
                background = if (span.isCode) codeBackground else Color.Unspecified,
            )
            val target = span.link
            if (target == null) {
                withStyle(style) { append(span.text) }
            } else {
                val linkStyle = TextLinkStyles(style.merge(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))
                withLink(LinkAnnotation.Url(target, linkStyle)) { append(span.text) }
            }
        }
    }
