package uz.jahonov.ktormonitor.capture

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.replaceResponse
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.isSaved
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall

/** Where recorded calls go. All writes come from one coroutine, in the order they happened. */
internal interface CallStore {
    suspend fun upsert(call: NetworkCall)
}

/**
 * Records every network attempt of the client it is installed on into [store].
 *
 * Installed after any plugin that retries (auth refresh and the like), it sees each refresh and
 * retry as a record of its own. Nothing it does can fail or hold up the app's call: writes are queued
 * and run on [scope], and any failure of the monitor itself goes to `onInternalError` and is dropped.
 */
internal class KtorMonitorCapture(
    private val store: CallStore,
    private val config: KtorMonitorConfig,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val writer = scope.launch(start = CoroutineStart.LAZY) {
        for (write in writes) {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                config.onInternalError(e)
            }
        }
    }

    fun install(client: HttpClient) {
        if (!config.isActive) return
        writer.start()

        client.plugin(HttpSend).intercept { request ->
            val recording = if (config.accepts(request)) guarded { start(request) } else null
            if (recording == null) return@intercept execute(request)

            val call = try {
                execute(request)
            } catch (e: Throwable) {
                recording.update { copy(error = e.stackTraceToString()) }
                throw e
            }
            guarded { recordResponse(recording, call) } ?: call
        }
    }

    private fun KtorMonitorConfig.accepts(request: HttpRequestBuilder) =
        filters.isEmpty() || filters.any { it(request) }

    private fun start(request: HttpRequestBuilder): Recording {
        val attempt = (request.attributes.getOrNull(AttemptKey) ?: 0) + 1
        request.attributes.put(AttemptKey, attempt)
        val groupId = request.attributes.computeIfAbsent(GroupKey) { newId() }

        // A retry sends the same builder again: record its body afresh, not through our last wrapper.
        val content = (request.body as? OutgoingContent)?.let { (it as? ObservedContent)?.original ?: it }
        val contentType = content?.contentType

        val recording = Recording(
            NetworkCall(
                id = newId(),
                groupId = groupId,
                attempt = attempt,
                method = request.method.value,
                url = request.url.buildString(),
                requestTime = now(),
                requestHeaders = requestHeaders(request, content),
                requestContentType = contentType?.toString(),
                requestBody = (content?.payload() as? OutgoingContent.ByteArrayContent)?.let { body(it.bytes(), contentType) },
            ),
        )
        observeAdaptedResponse(request, recording)
        if (content is OutgoingContent.WriteChannelContent || content is OutgoingContent.ReadChannelContent) {
            request.setBody(
                ObservedContent(content, config.maxContentLength) { captured ->
                    recording.update { copy(requestBody = redacted(captured, contentType)) }
                },
            )
        }
        return recording
    }

    /** Server-sent events: the body becomes a session inside the engine, so it is copied there. */
    @OptIn(InternalAPI::class)
    private fun observeAdaptedResponse(request: HttpRequestBuilder, recording: Recording) {
        val adapter = request.attributes.getOrNull(ResponseAdapterAttributeKey) ?: return
        val original = (adapter as? ObservingResponseAdapter)?.original ?: adapter
        request.attributes.put(
            ResponseAdapterAttributeKey,
            ObservingResponseAdapter(original, config.maxContentLength) { body ->
                recording.update { copy(responseBody = body) }
            },
        )
        recording.bodyFromEngine = true
    }

    @OptIn(InternalAPI::class)
    private suspend fun recordResponse(recording: Recording, call: HttpClientCall): HttpClientCall {
        val response = call.response
        val contentType = response.contentType()
        recording.update {
            copy(
                protocol = response.version.toString(),
                responseCode = response.status.value,
                responseTime = now(),
                responseHeaders = sanitize(response.headers.entries()),
                responseContentType = contentType?.toString(),
            )
        }

        // Ktor keeps an ordinary body in memory, so it can be read here and again by the app.
        if (response.isSaved) {
            val body = body(response.readRawBytes(), contentType)
            recording.update { copy(responseBody = body) }
            return call
        }

        // Copied inside the engine already (server-sent events).
        if (recording.bodyFromEngine) return call

        // A streamed body is read once: pass it through, keeping a copy as it goes.
        val isEventStream = contentType?.match(ContentType.Text.EventStream) == true
        val collector = BodyCollector(config.maxContentLength)
        val source = response.rawContent
        val observed = call.client.writer {
            try {
                source.forEachChunk { chunk, length ->
                    channel.writeFully(chunk, 0, length)
                    channel.flush()
                    collector.add(chunk, length)
                    if (isEventStream) recording.update { copy(responseBody = collector.body()) }
                }
            } catch (e: Throwable) {
                recording.update { copy(error = e.stackTraceToString()) }
                throw e
            } finally {
                val captured = collector.body()
                recording.update { copy(responseBody = redacted(captured, contentType)) }
            }
        }.channel
        return call.replaceResponse { observed }
    }

    private fun requestHeaders(request: HttpRequestBuilder, content: OutgoingContent?): Map<String, List<String>> {
        val entries = request.headers.build().entries() + content?.headers?.entries().orEmpty()
        val contentHeaders = buildList {
            content?.contentType?.let { add(HttpHeaders.ContentType to listOf(it.toString())) }
            content?.contentLength?.let { add(HttpHeaders.ContentLength to listOf(it.toString())) }
        }
        return sanitize(entries.map { it.key to it.value } + contentHeaders)
    }

    private fun sanitize(headers: Set<Map.Entry<String, List<String>>>) = sanitize(headers.map { it.key to it.value })

    private fun sanitize(headers: List<Pair<String, List<String>>>): Map<String, List<String>> =
        headers.sortedBy { it.first.lowercase() }.associate { (name, values) ->
            val rule = config.headerRules.firstOrNull { it.matches(name) }
            name to (rule?.let { listOf(it.placeholder) } ?: values)
        }

    private fun body(bytes: ByteArray, contentType: ContentType?): CapturedBody? {
        val kept = minOf(bytes.size, config.maxContentLength)
        return redacted(CapturedBody(ByteString(bytes, 0, kept), bytes.size.toLong()), contentType)
    }

    private fun redacted(body: CapturedBody, contentType: ContentType?): CapturedBody? {
        if (config.redactedFields.isEmpty() || contentType?.isJson() != true) return body
        if (body.isTruncated) return null
        val json = runCatching { Json.parseToJsonElement(body.bytes.decodeToString()) }.getOrNull() ?: return null
        return body.copy(bytes = ByteString(json.redacted().toString().encodeToByteArray()))
    }

    private fun JsonElement.redacted(): JsonElement = when (this) {
        is JsonObject -> JsonObject(
            mapValues { (key, value) ->
                if (key.lowercase() in config.redactedFields) JsonPrimitive(KtorMonitorConfig.PLACEHOLDER) else value.redacted()
            },
        )
        is JsonArray -> JsonArray(map { it.redacted() })
        is JsonPrimitive -> this
    }

    /** The latest state of one attempt. Only the write queue reads or changes it. */
    private inner class Recording(private var call: NetworkCall) {
        /** Set before the request is sent, read once it has its response. */
        var bodyFromEngine = false

        init {
            update { this }
        }

        fun update(change: NetworkCall.() -> NetworkCall) {
            writes.trySend {
                call = call.change()
                store.upsert(call)
            }
        }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        config.onInternalError(e)
        null
    }

    private companion object {
        val AttemptKey = AttributeKey<Int>("KtorMonitorAttempt")
        val GroupKey = AttributeKey<String>("KtorMonitorGroup")
    }
}

/** A wrapper around a body (as compression adds) is looked through, down to the bytes. */
private fun OutgoingContent.payload(): OutgoingContent =
    generateSequence(this) { (it as? OutgoingContent.ContentWrapper)?.delegate() }.last()

private fun ContentType.isJson() =
    match(ContentType.Application.Json) || contentSubtype.endsWith("+json")

@OptIn(ExperimentalUuidApi::class)
private fun newId(): String = Uuid.random().toString()
