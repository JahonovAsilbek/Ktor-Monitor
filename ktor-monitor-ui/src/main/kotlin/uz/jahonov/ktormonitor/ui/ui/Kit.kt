package uz.jahonov.ktormonitor.ui.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uz.jahonov.ktormonitor.ui.R

// The monitor's few controls, built from foundation only.

internal object MonitorIcons {
    val Check = R.drawable.ktormonitor_ic_check
    val CheckCircle = R.drawable.ktormonitor_ic_check_circle
    val ChevronDown = R.drawable.ktormonitor_ic_chevron_down
    val ChevronLeft = R.drawable.ktormonitor_ic_chevron_left
    val ChevronRight = R.drawable.ktormonitor_ic_chevron_right
    val Close = R.drawable.ktormonitor_ic_close
    val Copy = R.drawable.ktormonitor_ic_copy
    val Delete = R.drawable.ktormonitor_ic_delete
    val More = R.drawable.ktormonitor_ic_more
    val OpenIn = R.drawable.ktormonitor_ic_open_in
    val Search = R.drawable.ktormonitor_ic_search
    val Share = R.drawable.ktormonitor_ic_share
    val Sort = R.drawable.ktormonitor_ic_sort
    val Tune = R.drawable.ktormonitor_ic_tune
    val Warning = R.drawable.ktormonitor_ic_warning
}

/** A drawable icon in one colour, 24dp unless [modifier] sizes it. */
@Composable
internal fun MonitorIcon(@DrawableRes icon: Int, tint: Color, description: String?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(24.dp),
    )
}

/** A 40dp icon target; [tint] defaults to the main text colour. */
@Composable
internal fun MonitorIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MonitorTheme.colors.text,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MonitorIcon(icon, tint, description)
    }
}

/** A text button in the accent colour. */
@Composable
internal fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val color = if (enabled) MonitorTheme.colors.accent else MonitorTheme.colors.textDisabled
    BasicText(
        text = text,
        style = MonitorTheme.typography.bodyMedium.copy(color = color),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** A toggle chip for filters and view modes. */
@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val colors = MonitorTheme.colors
    BasicText(
        text = text,
        style = MonitorTheme.typography.captionMedium.copy(color = if (selected) colors.onAccent else colors.text),
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accent else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A screen's top bar: optional back button, a title with a subtitle, one optional action. */
@Composable
internal fun MonitorToolbar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MonitorTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation?.invoke() ?: Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            BasicText(
                title,
                style = MonitorTheme.typography.title.copy(color = colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                BasicText(
                    it,
                    style = MonitorTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action?.invoke()
    }
}

/** Equal-width tabs with an underline under the selected one. */
@Composable
internal fun MonitorTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MonitorTheme.colors
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    title,
                    style = MonitorTheme.typography.bodyMedium.copy(color = if (isSelected) colors.accent else colors.textSecondary),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isSelected) colors.accent else colors.border),
                )
            }
        }
    }
}

/** An indeterminate spinner. */
@Composable
internal fun Spinner(modifier: Modifier = Modifier, color: Color = MonitorTheme.colors.accent, size: Dp = 24.dp) {
    val rotation by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Canvas(modifier.size(size).rotate(rotation)) {
        val stroke = size.toPx() / 8
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** A row of a sheet: optional icon, title with a description, a value on the right. */
@Composable
internal fun SheetItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    val colors = MonitorTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { MonitorIcon(it, colors.text, null) }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = MonitorTheme.typography.body.copy(color = colors.text))
            description?.let { BasicText(it, style = MonitorTheme.typography.caption.copy(color = colors.textSecondary)) }
        }
        value?.let { BasicText(it, style = MonitorTheme.typography.body.copy(color = colors.textSecondary)) }
        trailingIcon?.let { MonitorIcon(it, colors.textSecondary, null) }
    }
}

/**
 * A sheet at the bottom of the screen, in a dialog window of its own. [content] gets `dismiss`,
 * which closes it the same way as tapping outside or pressing back.
 */
@Composable
internal fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = MonitorTheme.colors
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.background)
                    // Taps inside the sheet must not reach the scrim behind it.
                    .clickable(interactionSource = null, indication = null, onClick = {})
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(title, style = MonitorTheme.typography.headline.copy(color = colors.text), modifier = Modifier.weight(1f))
                    MonitorIconButton(MonitorIcons.Close, "Close", onDismiss)
                }
                content(onDismiss)
            }
        }
    }
}
