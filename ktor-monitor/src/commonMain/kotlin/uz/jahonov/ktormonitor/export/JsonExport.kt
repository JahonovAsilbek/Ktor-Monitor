package uz.jahonov.ktormonitor.export

import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall

internal val exportJson = Json { prettyPrint = true }

/** The monitor's own JSON: every call with its full kept bodies. */
internal fun callsJson(calls: List<NetworkCall>, exportedAt: Long, appName: String, appVersion: String): String =
    encode(
        buildJsonObject {
            put("exportedAt", isoTime(exportedAt))
            putJsonObject("app") {
                put("name", appName)
                put("version", appVersion)
            }
            putJsonArray("calls") { calls.forEach { add(it.toJson()) } }
        },
    )

/** A HAR 1.2 log (http://www.softwareishard.com/blog/har-12-spec/), as Chrome DevTools, Charles and Proxyman import it. */
internal fun harLog(calls: List<NetworkCall>, appName: String, appVersion: String): String =
    encode(
        buildJsonObject {
            putJsonObject("log") {
                put("version", "1.2")
                putJsonObject("creator") {
                    put("name", appName)
                    put("version", appVersion)
                }
                putJsonArray("entries") { calls.sortedBy { it.requestTime }.forEach { add(it.harEntry()) } }
            }
        },
    )

private fun encode(json: JsonObject) = exportJson.encodeToString(JsonElement.serializer(), json)

private fun NetworkCall.toJson() = buildJsonObject {
    put("id", id)
    put("groupId", groupId)
    put("attempt", attempt)
    put("startedAt", isoTime(requestTime))
    put("durationMs", durationMillis)
    putJsonObject("request") {
        put("method", method)
        put("url", url)
        put("headers", headersJson(requestHeaders))
        putBody(requestBody, requestContentType)
    }
    val code = responseCode
    if (code == null) {
        put("response", JsonNull)
    } else {
        putJsonObject("response") {
            put("statusCode", code)
            put("protocol", protocol)
            put("headers", headersJson(responseHeaders))
            putBody(responseBody, responseContentType)
        }
    }
    put("error", error)
}

private fun headersJson(headers: Map<String, List<String>>) = buildJsonObject {
    headers.forEach { (name, values) -> putJsonArray(name) { values.forEach { add(it) } } }
}

/** The body as kept, text as it was sent and anything else in Base64, so the export loses nothing. */
private fun JsonObjectBuilder.putBody(body: CapturedBody?, contentType: String?) {
    val text = body?.text(contentType)
    put("body", text ?: body?.let { Base64.encode(it.bytes.toByteArray()) })
    put("bodyEncoding", if (body != null && text == null) "base64" else null)
    put("bodySize", body?.size ?: 0)
    put("bodyTruncated", body?.isTruncated == true)
}

private fun NetworkCall.harEntry() = buildJsonObject {
    val time = durationMillis ?: 0
    put("startedDateTime", isoTime(requestTime))
    put("time", time)
    putJsonObject("request") {
        put("method", method)
        put("url", url)
        put("httpVersion", protocol ?: "HTTP/1.1")
        put("headers", harHeaders(requestHeaders))
        put("queryString", harQuery(url))
        putJsonArray("cookies") {}
        put("headersSize", -1)
        put("bodySize", requestBody?.size ?: 0)
        requestBody?.let { put("postData", harPostData(it, requestContentType)) }
    }
    val code = responseCode
    putJsonObject("response") {
        put("status", code ?: 0)
        put("statusText", code?.let(::reasonPhrase).orEmpty())
        put("httpVersion", if (code == null) "" else protocol ?: "HTTP/1.1")
        put("headers", harHeaders(responseHeaders))
        putJsonArray("cookies") {}
        put("content", harContent(responseBody, responseContentType))
        put("redirectURL", responseHeaders.firstValue(HttpHeaders.Location).orEmpty())
        put("headersSize", -1)
        put("bodySize", responseBody?.size ?: if (code == null) -1 else 0)
    }
    putJsonObject("cache") {}
    putJsonObject("timings") {
        put("send", 0)
        put("wait", time)
        put("receive", 0)
    }
    error?.let { put("_error", it) }
    if (isInProgress) put("comment", "In progress")
}

private fun harHeaders(headers: Map<String, List<String>>) = buildJsonArray {
    headers.forEach { (name, values) ->
        values.forEach { value ->
            addJsonObject {
                put("name", name)
                put("value", value)
            }
        }
    }
}

private fun harQuery(url: String): JsonArray {
    val parameters = runCatching { Url(url).parameters }.getOrNull()
    return buildJsonArray {
        parameters?.entries()?.forEach { (name, values) ->
            values.forEach { value ->
                addJsonObject {
                    put("name", name)
                    put("value", value)
                }
            }
        }
    }
}

private fun harPostData(body: CapturedBody, contentType: String?) = buildJsonObject {
    val text = body.text(contentType)
    put("mimeType", contentType.orEmpty())
    put("text", text.orEmpty())
    when {
        text == null -> put("comment", "Binary body of ${body.size} bytes not included")
        body.isTruncated -> put("comment", truncatedComment(body))
    }
}

/** HAR content: text as is, or base64 for a binary body. [CapturedBody.size] is the full size even when cut. */
private fun harContent(body: CapturedBody?, contentType: String?) = buildJsonObject {
    put("size", body?.size ?: 0)
    put("mimeType", contentType ?: "x-unknown")
    if (body == null) return@buildJsonObject
    val text = body.text(contentType)
    if (text != null) {
        put("text", text)
    } else {
        put("text", Base64.encode(body.bytes.toByteArray()))
        put("encoding", "base64")
    }
    if (body.isTruncated) put("comment", truncatedComment(body))
}

private fun truncatedComment(body: CapturedBody) = "Truncated: ${body.bytes.size} of ${body.size} bytes kept"

private fun Map<String, List<String>>.firstValue(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
