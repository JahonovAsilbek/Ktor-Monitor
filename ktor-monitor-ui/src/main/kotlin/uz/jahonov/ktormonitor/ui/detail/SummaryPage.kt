package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.ktor.http.HttpStatusCode
import uz.jahonov.ktormonitor.model.NetworkCall
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.Spinner
import uz.jahonov.ktormonitor.ui.ui.formatDateTime
import uz.jahonov.ktormonitor.ui.ui.formatDuration
import uz.jahonov.ktormonitor.ui.ui.formatSize
import uz.jahonov.ktormonitor.ui.ui.statusColor

/** Every field is selectable on its own; a long URL wraps. */
@Composable
internal fun SummaryPage(call: NetworkCall, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val fields = summaryFields(call)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") { Field("Status") { Status(call) } }
        items(fields.size, key = { fields[it].first }) { index ->
            val (label, value) = fields[index]
            Field(label) { Value(value) }
        }
    }
}

private fun summaryFields(call: NetworkCall): List<Pair<String, String>> {
    val requestSize = call.requestBody?.size ?: 0
    val responseSize = call.responseBody?.size ?: 0
    val waiting = call.isInProgress
    return listOf(
        "URL" to call.url,
        "Method" to call.method,
        "Protocol" to (call.protocol ?: NONE),
        "Attempt" to if (call.attempt > 1) "${call.attempt}, retry of an earlier attempt" else call.attempt.toString(),
        "Request time" to formatDateTime(call.requestTime),
        "Response time" to (call.responseTime?.let(::formatDateTime) ?: NONE),
        "Duration" to (call.durationMillis?.let(::formatDuration) ?: NONE),
        "Request size" to formatSize(requestSize),
        "Response size" to if (waiting) NONE else formatSize(responseSize),
        "Total size" to formatSize(requestSize + responseSize),
    )
}

@Composable
private fun Field(label: String, value: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(label, style = MonitorTheme.typography.caption.copy(color = MonitorTheme.colors.textSecondary))
        value()
    }
}

@Composable
private fun Value(text: String) {
    SelectionContainer {
        BasicText(text, style = MonitorTheme.typography.body.copy(color = MonitorTheme.colors.text))
    }
}

@Composable
private fun Status(call: NetworkCall) {
    val summary = call.summary
    val code = call.responseCode
    val error = call.error
    when {
        code != null -> SelectionContainer {
            BasicText(
                "$code ${HttpStatusCode.fromValue(code).description}",
                style = MonitorTheme.typography.bodyMedium.copy(color = statusColor(summary)),
            )
        }
        error != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(
                painter = painterResource(MonitorIcons.Warning),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MonitorTheme.colors.error),
                modifier = Modifier.size(20.dp),
            )
            SelectionContainer {
                BasicText(
                    "Failed: ${error.lineSequence().first()}",
                    style = MonitorTheme.typography.bodyMedium.copy(color = MonitorTheme.colors.error),
                )
            }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spinner(Modifier.width(32.dp))
            BasicText("In progress", style = MonitorTheme.typography.bodyMedium.copy(color = statusColor(summary)))
        }
    }
}

private const val NONE = "—"
