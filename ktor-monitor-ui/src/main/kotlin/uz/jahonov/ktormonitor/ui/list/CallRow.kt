package uz.jahonov.ktormonitor.ui.list

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uz.jahonov.ktormonitor.model.CallSummary
import uz.jahonov.ktormonitor.model.ContentKind
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.Spinner
import uz.jahonov.ktormonitor.ui.ui.color
import uz.jahonov.ktormonitor.ui.ui.formatClock
import uz.jahonov.ktormonitor.ui.ui.formatDuration
import uz.jahonov.ktormonitor.ui.ui.formatSize
import uz.jahonov.ktormonitor.ui.ui.statusColor

/**
 * One call in the list. [selected] is null outside selection mode; [highlighted] marks the call the
 * detail pane shows next to the list.
 */
@Composable
internal fun CallRow(
    call: CallSummary,
    highlighted: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MonitorTheme.colors
    val type = MonitorTheme.typography
    val secondary = type.caption.copy(color = colors.textSecondary)
    val background = when {
        selected == true -> colors.accentContainer
        highlighted -> colors.surface
        else -> colors.background
    }

    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onLongClickLabel = "Select", onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (selected != null) {
            SelectionMark(selected, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(
            Modifier.width(StatusColumnWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Status(call)
            KindBadge(call.kind)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = "${call.method} ${call.path}",
                style = type.bodyBold.copy(color = if (call.isError) colors.error else colors.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LockMark(call.isSecure)
                Spacer(Modifier.width(4.dp))
                BasicText(call.host, style = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicText(call.timing(), style = secondary, maxLines = 1)
            when {
                call.isInProgress -> BasicText("In progress…", style = secondary.copy(fontStyle = FontStyle.Italic))
                call.error != null -> BasicText(
                    text = call.error.orEmpty().lineSequence().first(),
                    style = secondary.copy(color = colors.error, fontStyle = FontStyle.Italic),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (call.attempt > 1) {
                BasicText("retry · attempt ${call.attempt}", style = secondary.copy(color = colors.warning))
            }
        }
    }
}

/** Start time · duration · response size, leaving out what has not arrived yet. */
private fun CallSummary.timing(): String =
    listOfNotNull(formatClock(requestTime), durationMillis?.let(::formatDuration), responseSize?.let(::formatSize))
        .joinToString(" · ")

/** The status code; a spinner while waiting; a warning when the call failed with no response. */
@Composable
private fun Status(call: CallSummary) {
    val colors = MonitorTheme.colors
    when {
        call.isInProgress -> Box(Modifier.height(StatusHeight), contentAlignment = Alignment.Center) {
            Spinner(Modifier.width(StatusColumnWidth - 12.dp), color = colors.textSecondary)
        }
        call.responseCode == null -> TintedIcon(MonitorIcons.Warning, colors.error, "Failed", Modifier.size(StatusHeight))
        else -> Row(Modifier.height(StatusHeight), verticalAlignment = Alignment.CenterVertically) {
            BasicText(call.responseCode.toString(), style = MonitorTheme.typography.title.copy(color = statusColor(call)))
            if (call.isRedirect) {
                TintedIcon(MonitorIcons.OpenIn, colors.warning, "Redirect", Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun KindBadge(kind: ContentKind) {
    BasicText(
        text = kind.label,
        style = MonitorTheme.typography.captionMedium.copy(color = MonitorTheme.colors.text),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(kind.color())
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** A padlock, closed for https and open in red for plain http. */
@Composable
private fun LockMark(secure: Boolean) {
    val color = if (secure) MonitorTheme.colors.textSecondary else MonitorTheme.colors.error
    Canvas(
        Modifier
            .size(LockSize)
            .semantics { contentDescription = if (secure) "Secure" else "Not secure" },
    ) {
        val stroke = size.width * 0.14f
        val bodyTop = size.height * 0.45f
        val shackleWidth = size.width * 0.5f
        val shackleLeft = (size.width - shackleWidth) / 2
        // An open lock lifts its shackle out of the body.
        val lift = if (secure) 0f else size.height * 0.15f
        val arcTop = stroke / 2 - lift
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(shackleLeft, arcTop),
            size = Size(shackleWidth, shackleWidth),
            style = Stroke(stroke),
        )
        val legTop = arcTop + shackleWidth / 2
        drawLine(color, Offset(shackleLeft + shackleWidth, legTop), Offset(shackleLeft + shackleWidth, bodyTop), stroke)
        if (secure) drawLine(color, Offset(shackleLeft, legTop), Offset(shackleLeft, bodyTop), stroke)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.1f, bodyTop),
            size = Size(size.width * 0.8f, size.height - bodyTop),
            cornerRadius = CornerRadius(size.width * 0.12f),
        )
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MonitorTheme.colors
    val shape = CircleShape
    Box(
        modifier
            .size(SelectionMarkSize)
            .clip(shape)
            .background(if (selected) colors.accent else colors.background)
            .border(1.dp, if (selected) colors.accent else colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) TintedIcon(MonitorIcons.Check, colors.background, "Selected", Modifier.size(16.dp))
    }
}

@Composable
internal fun TintedIcon(@DrawableRes icon: Int, tint: Color, description: String?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

private val StatusColumnWidth = 56.dp
private val StatusHeight = 24.dp
private val LockSize = 12.dp
private val SelectionMarkSize = 24.dp

@Preview
@Composable
private fun CallRowLightPreview() {
    MonitorTheme(darkTheme = false) { CallRowSamples() }
}

@Preview
@Composable
private fun CallRowDarkPreview() {
    MonitorTheme(darkTheme = true) { CallRowSamples() }
}

@Composable
private fun CallRowSamples() {
    Column(Modifier.background(MonitorTheme.colors.background)) {
        PreviewCalls.forEachIndexed { index, call ->
            CallRow(
                call = call,
                highlighted = index == 0,
                selected = if (index == 3) true else null,
                onClick = {},
                onLongClick = {},
            )
        }
    }
}

internal val PreviewCalls = listOf(
    previewCall("1", "https://api.example.com/v1/items?page=2", code = 200, type = "application/json", size = 2_480),
    previewCall("2", "https://api.example.com/v1/orders", method = "POST"),
    previewCall("3", "http://cdn.example.com/img/avatar.png", code = 404, type = "image/png", size = 312, attempt = 2),
    previewCall("4", "https://api.example.com/v1/login", code = 302, size = 0),
    previewCall("5", "https://api.example.com/v1/profile", error = "java.net.UnknownHostException: Unable to resolve host\n\tat …"),
)

private fun previewCall(
    id: String,
    url: String,
    method: String = "GET",
    code: Int? = null,
    type: String? = null,
    size: Long? = null,
    error: String? = null,
    attempt: Int = 1,
) = CallSummary(
    id = id,
    groupId = id,
    attempt = attempt,
    method = method,
    url = url,
    requestTime = 1_790_000_000_000,
    requestContentType = null,
    requestSize = null,
    protocol = "HTTP/1.1",
    responseCode = code,
    responseTime = if (code != null || error != null) 1_790_000_000_245 else null,
    responseContentType = type,
    responseSize = size,
    error = error,
)
