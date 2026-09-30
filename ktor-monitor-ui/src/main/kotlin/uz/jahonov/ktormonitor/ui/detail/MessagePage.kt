package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.body.BodyMode
import uz.jahonov.ktormonitor.body.BodyPreview
import uz.jahonov.ktormonitor.model.NetworkCall
import uz.jahonov.ktormonitor.presentation.detail.BodyContent
import uz.jahonov.ktormonitor.presentation.detail.BodySide
import uz.jahonov.ktormonitor.presentation.detail.BodyState
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEvent
import uz.jahonov.ktormonitor.ui.ui.Chip
import uz.jahonov.ktormonitor.ui.ui.MonitorIconButton
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.Spinner
import uz.jahonov.ktormonitor.ui.ui.formatSize

/**
 * The request or the response: error, headers, body. One LazyColumn holds all of it, down to the body's
 * lines, so a body of thousands of lines composes only what is on screen.
 *
 * A failed response shows its error and whatever headers and body did arrive, leaving out the sections
 * it has nothing for.
 */
@Composable
internal fun MessagePage(
    call: NetworkCall,
    side: BodySide,
    body: BodyState?,
    onEvent: (KtorMonitorDetailUiEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var headersExpanded by rememberSaveable { mutableStateOf(true) }
    var bodyExpanded by rememberSaveable { mutableStateOf(true) }

    val isResponse = side == BodySide.RESPONSE
    val headers = if (isResponse) call.responseHeaders else call.requestHeaders
    val error = call.error.takeIf { isResponse }

    val content = body?.content
    val pan = remember(content) { HorizontalPan() }
    val folds = remember(content) { (content as? BodyContent.Code)?.let { CodeFolds(it.document) } }
    val textRows = remember(content) { (content as? BodyContent.Lines)?.let { textRows(it.lines) } }
    val colors = tokenColors()

    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (isResponse && call.isInProgress) {
            item(key = "waiting") { Waiting() }
            return@LazyColumn
        }
        if (error != null) {
            item(key = "error-title") { SectionTitle("Error") }
            item(key = "error") { ErrorBlock(error) }
        }
        if (error == null || headers.isNotEmpty()) {
            item(key = "headers-title") {
                SectionTitle(
                    title = "Headers",
                    detail = headers.size.takeIf { it > 0 }?.toString(),
                    expanded = headersExpanded,
                    onToggle = { headersExpanded = !headersExpanded },
                    onCopy = { onEvent(KtorMonitorDetailUiEvent.CopyHeaders(side)) }.takeIf { headers.isNotEmpty() },
                )
            }
            if (headersExpanded) item(key = "headers") { Headers(headers) }
        }
        if (error == null || body != null) {
            item(key = "body-title") {
                SectionTitle(
                    title = "Body",
                    detail = body?.let { formatSize(it.size) },
                    isTruncated = body?.isTruncated == true,
                    expanded = bodyExpanded,
                    onToggle = { bodyExpanded = !bodyExpanded },
                    onCopy = { onEvent(KtorMonitorDetailUiEvent.CopyBody(side)) }.takeIf { body != null },
                )
            }
            if (bodyExpanded) {
                if (body == null) {
                    item(key = "no-body") { Notice("No body") }
                } else {
                    item(key = "modes") { Modes(body, onSelect = { onEvent(KtorMonitorDetailUiEvent.SelectMode(side, it)) }) }
                    when (val shown = body.content) {
                        is BodyContent.Code -> folds?.let { codeItems(shown.document, it, pan, colors) }
                        is BodyContent.Lines -> textRows?.let { lineItems(shown.lines, it, pan) }
                        is BodyContent.Hex -> hexItems(shown.rows, pan)
                        is BodyContent.Preview -> previewItems(shown.preview)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.previewItems(preview: BodyPreview) {
    when (preview) {
        is BodyPreview.Image -> item(key = "image") { ImagePreview(preview) }
        is BodyPreview.Markdown -> markdownItems(preview.blocks)
    }
}

/** A section heading; with [onToggle] it folds its section, with [onCopy] it copies it. */
@Composable
private fun SectionTitle(
    title: String,
    detail: String? = null,
    isTruncated: Boolean = false,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
) {
    val colors = MonitorTheme.colors
    val muted = MonitorTheme.typography.caption.copy(color = colors.textSecondary)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onToggle != null) {
                    Modifier.clickable(onClickLabel = if (expanded) "Collapse" else "Expand", role = Role.Button, onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onToggle != null) {
            Image(
                painter = painterResource(if (expanded) MonitorIcons.ChevronDown else MonitorIcons.ChevronRight),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textSecondary),
                modifier = Modifier.size(16.dp),
            )
        }
        BasicText(title, style = MonitorTheme.typography.title.copy(color = colors.text))
        detail?.let { BasicText(it, style = muted) }
        if (isTruncated) BasicText("(truncated)", style = muted.copy(color = colors.warning))
        Spacer(Modifier.weight(1f))
        // Keeps every title the same height, with or without a button.
        Box(Modifier.size(40.dp)) {
            onCopy?.let { MonitorIconButton(MonitorIcons.Copy, "Copy ${title.lowercase()}", it) }
        }
    }
}

/** One line per value, so repeated headers such as Set-Cookie stay apart. */
@Composable
private fun Headers(headers: Map<String, List<String>>) {
    if (headers.isEmpty()) {
        Notice("No headers")
        return
    }
    val colors = MonitorTheme.colors
    val text = remember(headers) {
        buildAnnotatedString {
            headers.forEach { (name, values) ->
                values.forEach { value ->
                    if (length > 0) append('\n')
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                    append(": ")
                    append(value)
                }
            }
        }
    }
    SelectionContainer {
        BasicText(
            text,
            style = MonitorTheme.typography.body.copy(color = colors.text),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun Modes(body: BodyState, onSelect: (BodyMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        body.modes.forEach { mode ->
            Chip(mode.label, selected = mode == body.mode, onClick = { onSelect(mode) })
        }
    }
}

private val BodyMode.label: String
    get() = when (this) {
        BodyMode.STREAM -> "Stream"
        BodyMode.PREVIEW -> "Preview"
        BodyMode.CODE -> "Code"
        BodyMode.TEXT -> "Text"
        BodyMode.HEX -> "Hex"
    }

/** The stack trace as it was recorded: not wrapped, so its frames stay one per line. */
@Composable
private fun ErrorBlock(error: String) {
    val colors = MonitorTheme.colors
    SelectionContainer(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.errorContainer),
    ) {
        BasicText(
            error,
            style = monoStyle(),
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

@Composable
private fun Waiting() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spinner()
        BasicText(
            "Waiting for the response…",
            style = MonitorTheme.typography.body.copy(color = MonitorTheme.colors.textSecondary),
        )
    }
}

@Composable
private fun PagePreview(call: NetworkCall, side: BodySide, body: BodyState?, darkTheme: Boolean = false) {
    MonitorTheme(darkTheme = darkTheme) {
        Box(Modifier.background(MonitorTheme.colors.background)) {
            MessagePage(call, side, body, onEvent = {}, contentPadding = PaddingValues(16.dp))
        }
    }
}

@Preview(heightDp = 640)
@Composable
private fun CodePreview() {
    PagePreview(DetailSamples.call, BodySide.RESPONSE, DetailSamples.jsonBody)
}

@Preview(heightDp = 640)
@Composable
private fun CodeDarkPreview() {
    PagePreview(DetailSamples.call, BodySide.RESPONSE, DetailSamples.jsonBody, darkTheme = true)
}

@Preview(heightDp = 640)
@Composable
private fun FailedPreview() {
    PagePreview(DetailSamples.failedCall, BodySide.RESPONSE, body = null)
}

@Preview(heightDp = 640)
@Composable
private fun MarkdownPreview() {
    PagePreview(DetailSamples.call, BodySide.RESPONSE, DetailSamples.markdownBody)
}

@Preview(heightDp = 640)
@Composable
private fun HexDarkPreview() {
    PagePreview(DetailSamples.call, BodySide.REQUEST, DetailSamples.hexBody, darkTheme = true)
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun TextPreview() {
    PagePreview(DetailSamples.call, BodySide.REQUEST, DetailSamples.textBody)
}
