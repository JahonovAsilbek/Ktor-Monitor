package uz.jahonov.ktormonitor.ui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import uz.jahonov.ktormonitor.body.TokenKind
import uz.jahonov.ktormonitor.model.CallSummary
import uz.jahonov.ktormonitor.model.ContentKind

/** Status text colour: red for an error, the secondary text colour while in flight. */
@Composable
@ReadOnlyComposable
internal fun statusColor(call: CallSummary): Color = when {
    call.isError -> MonitorTheme.colors.error
    call.isInProgress -> MonitorTheme.colors.textSecondary
    call.isRedirect -> MonitorTheme.colors.warning
    else -> MonitorTheme.colors.text
}

/** The badge colour of each content kind; kinds of a family share a hue. */
@Composable
@ReadOnlyComposable
internal fun ContentKind.color(): Color = with(MonitorTheme.colors) {
    when (this@color) {
        ContentKind.JSON, ContentKind.YAML -> accent
        ContentKind.XML, ContentKind.HTML, ContentKind.MARKDOWN -> info
        ContentKind.CSS, ContentKind.JAVASCRIPT -> warning
        ContentKind.FORM, ContentKind.MULTIPART -> success
        ContentKind.IMAGE, ContentKind.FONT, ContentKind.AUDIO, ContentKind.VIDEO, ContentKind.PDF -> media
        ContentKind.EVENT_STREAM, ContentKind.WEBSOCKET -> info
        ContentKind.TEXT, ContentKind.BINARY, ContentKind.NONE -> textSecondary
    }
}

/** Syntax colours for code views. */
@Composable
@ReadOnlyComposable
internal fun TokenKind.color(): Color = with(MonitorTheme.colors) {
    when (this@color) {
        TokenKind.PLAIN -> text
        TokenKind.KEY, TokenKind.ATTRIBUTE -> info
        TokenKind.STRING -> success
        TokenKind.NUMBER -> warning
        TokenKind.KEYWORD, TokenKind.TAG, TokenKind.HEADING -> accent
        TokenKind.PUNCTUATION -> textSecondary
        TokenKind.COMMENT -> textDisabled
        TokenKind.EMPHASIS -> text
        TokenKind.LINK -> info
    }
}
