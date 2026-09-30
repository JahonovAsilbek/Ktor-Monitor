package uz.jahonov.ktormonitor.export

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import kotlinx.serialization.json.JsonElement
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall

/** Renders recorded calls for copying and sharing: shell commands, readable dumps, JSON and HAR. */
internal object CallExporter {

    /** A `curl` command that repeats the request, text body included as it was sent. */
    fun curl(call: NetworkCall): String {
        val args = listOf("curl -X ${call.method} ${shellQuoted(call.url)}") +
            call.sentHeaders().map { "-H ${shellQuoted(it)}" } +
            listOfNotNull(call.sentBody()?.let { "--data-binary ${shellQuoted(it)}" })
        return (call.bodyNotes() + args.joinToString(" \\\n  ")).joinToString("\n")
    }

    /** A `wget` command that repeats the request and prints the response. */
    fun wget(call: NetworkCall): String {
        val args = listOf("wget --method=${call.method}") +
            call.sentHeaders().map { "--header=${shellQuoted(it)}" } +
            listOfNotNull(call.sentBody()?.let { "--body-data=${shellQuoted(it)}" }) +
            "-O - ${shellQuoted(call.url)}"
        return (call.bodyNotes() + args.joinToString(" \\\n  ")).joinToString("\n")
    }

    /** The exchange as HTTP messages: the request, then the response, the error or "(in progress)". */
    fun text(call: NetworkCall): String = buildString {
        val protocol = call.protocol ?: DEFAULT_PROTOCOL
        append("${call.method} ${call.url} $protocol\n")
        appendMessage(call.requestHeaders, call.requestBody, call.requestContentType)
        call.responseCode?.let { code ->
            append("\n$protocol $code ${reasonPhrase(code)}\n")
            appendMessage(call.responseHeaders, call.responseBody, call.responseContentType)
        }
        call.error?.let { append("\nError:\n$it\n") }
        if (call.isInProgress) append("\n(in progress)\n")
    }.trimEnd()

    fun markdown(call: NetworkCall): String = buildString {
        append("## ${call.method} ${call.summary.path}\n\n")
        append("| Field | Value |\n|---|---|\n")
        call.summaryFields().forEach { (name, value) -> append("| $name | ${value.replace("|", "\\|")} |\n") }
        appendMarkdownSection("Request", call.requestHeaders, call.requestBody, call.requestContentType)
        if (call.responseCode != null) {
            appendMarkdownSection("Response", call.responseHeaders, call.responseBody, call.responseContentType)
        }
        call.error?.let { append("\n### Error\n\n${fenced(it, "")}\n") }
    }.trimEnd()

    /** Several calls (or one) in [format], for "share selected" and single-call share as a file. */
    fun export(
        calls: List<NetworkCall>,
        format: ExportFormat,
        exportedAt: Long,
        appName: String,
        appVersion: String,
    ): String = when (format) {
        ExportFormat.JSON -> callsJson(calls, exportedAt, appName, appVersion)
        ExportFormat.TEXT -> calls.joinToString("\n\n---\n\n", transform = ::text)
        ExportFormat.MARKDOWN ->
            "# Network calls\n\nExported ${isoTime(exportedAt)} from $appName $appVersion.\n\n" +
                calls.joinToString("\n\n", transform = ::markdown)
        ExportFormat.CURL -> calls.joinToString("\n\n", transform = ::curl)
        ExportFormat.WGET -> calls.joinToString("\n\n", transform = ::wget)
        ExportFormat.URLS -> calls.joinToString("\n") { it.url }
        ExportFormat.HAR -> harLog(calls, appName, appVersion)
    }

    /** e.g. "netmonitor-2026-09-30-142501.har"; a single call shared as TEXT uses the ".http" extension instead. */
    fun fileName(format: ExportFormat, exportedAt: Long, single: Boolean = false): String {
        // "2026-09-30T14:25:01.123Z"
        val iso = isoTime(exportedAt)
        val stamp = iso.substring(0, 10) + "-" + iso.substring(11, 19).replace(":", "")
        val extension = if (single && format == ExportFormat.TEXT) "http" else format.extension
        return "netmonitor-$stamp.$extension"
    }

    private fun NetworkCall.sentHeaders(): List<String> =
        requestHeaders
            .filterKeys { !it.equals(HttpHeaders.ContentLength, ignoreCase = true) }
            .flatMap { (name, values) -> values.map { "$name: $it" } }

    private fun NetworkCall.sentBody(): String? = requestBody?.text(requestContentType)

    private fun NetworkCall.bodyNotes(): List<String> {
        val body = requestBody ?: return emptyList()
        return when {
            body.text(requestContentType) == null -> listOf("# binary body of ${body.size} bytes not included")
            body.isTruncated -> listOf("# body truncated: ${body.bytes.size} of ${body.size} bytes kept")
            else -> emptyList()
        }
    }

    private fun StringBuilder.appendMessage(headers: Map<String, List<String>>, body: CapturedBody?, contentType: String?) {
        headers.forEach { (name, values) -> values.forEach { append("$name: $it\n") } }
        if (body == null) return
        append("\n${body.display(contentType)}\n")
        body.truncationNote()?.let { append("$it\n") }
    }

    private fun NetworkCall.summaryFields(): List<Pair<String, String>> = buildList {
        add("URL" to url)
        add("Status" to status())
        protocol?.let { add("Protocol" to it) }
        add("Request time" to isoTime(requestTime))
        responseTime?.let { add("Response time" to isoTime(it)) }
        durationMillis?.let { add("Duration" to "$it ms") }
        add("Request size" to sizeText(requestBody))
        add("Response size" to sizeText(responseBody))
        add("Attempt" to attempt.toString())
    }

    private fun NetworkCall.status(): String {
        val code = responseCode?.let { "$it ${reasonPhrase(it)}" }
        return when {
            code != null && error != null -> "$code, failed"
            code != null -> code
            error != null -> "Failed"
            else -> "In progress"
        }
    }

    private fun sizeText(body: CapturedBody?) = body?.let { "${grouped(it.size)} bytes" } ?: "none"

    private fun StringBuilder.appendMarkdownSection(
        title: String,
        headers: Map<String, List<String>>,
        body: CapturedBody?,
        contentType: String?,
    ) {
        if (headers.isNotEmpty()) {
            val lines = headers.flatMap { (name, values) -> values.map { "$name: $it" } }.joinToString("\n")
            append("\n### $title headers\n\n${fenced(lines, "")}\n")
        }
        if (body != null) {
            val language = if (body.text(contentType) == null) "" else codeLanguage(contentType)
            append("\n### $title body\n\n${fenced(body.display(contentType), language)}\n")
            body.truncationNote()?.let { append("\n$it\n") }
        }
    }

    /** A code block whose fence is longer than any run of backticks inside [content]. */
    private fun fenced(content: String, language: String): String {
        val longestRun = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestRun + 1))
        return "$fence$language\n$content\n$fence"
    }

    private fun codeLanguage(contentType: String?): String {
        val subtype = subtype(contentType) ?: return ""
        return when {
            subtype == "json" || subtype.endsWith("+json") || subtype == "x-ndjson" -> "json"
            subtype == "html" || subtype == "xhtml+xml" -> "html"
            subtype == "xml" || subtype.endsWith("+xml") -> "xml"
            subtype == "yaml" || subtype == "x-yaml" -> "yaml"
            subtype == "css" -> "css"
            subtype in setOf("javascript", "x-javascript", "ecmascript") -> "javascript"
            else -> ""
        }
    }

    /** Single-quoted for a POSIX shell: exact for any content, since nothing inside quotes is special but `'`. */
    private fun shellQuoted(value: String) = "'" + value.replace("'", "'\\''") + "'"
}

private const val DEFAULT_PROTOCOL = "HTTP/1.1"

internal fun isoTime(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).toString()

internal fun reasonPhrase(code: Int): String = HttpStatusCode.fromValue(code).description

/** The body as a reader sees it: its text, pretty-printed when it is JSON, or a placeholder when it is binary. */
internal fun CapturedBody.display(contentType: String?): String {
    val text = text(contentType) ?: return "<binary, ${grouped(size)} bytes>"
    val looksLikeJson = subtype(contentType)?.let { it == "json" || it.endsWith("+json") }
        ?: text.trimStart().let { it.startsWith("{") || it.startsWith("[") }
    if (!looksLikeJson) return text
    return runCatching { exportJson.encodeToString(JsonElement.serializer(), exportJson.parseToJsonElement(text)) }
        .getOrDefault(text)
}

internal fun CapturedBody.truncationNote(): String? =
    if (isTruncated) "(truncated: ${bytes.size} of $size bytes kept)" else null

private fun subtype(contentType: String?): String? =
    contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }?.contentSubtype?.lowercase()

/** 1234567 → "1 234 567". */
private fun grouped(value: Long): String = value.toString().reversed().chunked(3).joinToString(" ").reversed()
