package uz.jahonov.ktormonitor.capture

import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.ResponseAdapter
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteString
import uz.jahonov.ktormonitor.model.CapturedBody

/** A request body sent as a stream, passed through while a copy is kept. */
internal class ObservedContent(
    val original: OutgoingContent,
    private val limit: Int,
    private val onBody: (CapturedBody) -> Unit,
) : OutgoingContent.WriteChannelContent() {
    override val contentType get() = original.contentType
    override val contentLength get() = original.contentLength
    override val status get() = original.status
    override val headers get() = original.headers

    override suspend fun writeTo(channel: ByteWriteChannel) = coroutineScope {
        val source = when (original) {
            is ReadChannelContent -> original.readFrom()
            is WriteChannelContent -> writer { original.writeTo(this.channel) }.channel
            else -> error("Not a streamed body: $original")
        }
        val collector = BodyCollector(limit)
        source.forEachChunk { chunk, length ->
            channel.writeFully(chunk, 0, length)
            collector.add(chunk, length)
        }
        onBody(collector.body())
    }
}

/**
 * The engine hook through which Ktor's SSE plugin turns a response body into an event session before
 * any interceptor sees it. This passes the plugin a copy of the stream and keeps what goes through,
 * reporting after every chunk so an event stream shows live. When the plugin turns the response
 * down (an error status, another content type), nothing is read and [onAdapted] is not called: the
 * engine uses the body as usual, and the monitor records it as any other.
 */
@OptIn(InternalAPI::class)
internal class ObservingResponseAdapter(
    val original: ResponseAdapter,
    private val limit: Int,
    private val onAdapted: () -> Unit,
    private val onBody: (CapturedBody) -> Unit,
) : ResponseAdapter {
    override fun adapt(
        data: HttpRequestData,
        status: HttpStatusCode,
        headers: Headers,
        responseBody: ByteReadChannel,
        outgoingContent: OutgoingContent,
        callContext: CoroutineContext,
    ): Any? {
        val copy = ByteChannel()
        val collector = BodyCollector(limit)
        val pump = CoroutineScope(callContext).launch(start = CoroutineStart.LAZY) {
            try {
                responseBody.forEachChunk { chunk, length ->
                    copy.writeFully(chunk, 0, length)
                    copy.flush()
                    collector.add(chunk, length)
                    onBody(collector.body())
                }
                copy.flushAndClose()
            } catch (e: Throwable) {
                copy.cancel(e)
                throw e
            }
        }
        val adapted = original.adapt(data, status, headers, copy, outgoingContent, callContext)
        if (adapted == null) {
            pump.cancel()
        } else {
            onAdapted()
            pump.start()
        }
        return adapted
    }
}

/** Keeps the first [limit] bytes of a stream and counts all of them. */
internal class BodyCollector(private val limit: Int) {
    private val kept = Buffer()
    private var size = 0L

    fun add(chunk: ByteArray, length: Int) {
        val room = (limit - kept.size).coerceIn(0, length.toLong()).toInt()
        if (room > 0) kept.write(chunk, 0, room)
        size += length
    }

    fun body() = CapturedBody(kept.copy().readByteString(), size)
}

internal suspend inline fun ByteReadChannel.forEachChunk(block: (ByteArray, Int) -> Unit) {
    val chunk = ByteArray(CHUNK_SIZE)
    while (true) {
        val length = readAvailable(chunk, 0, chunk.size)
        if (length == -1) break
        if (length > 0) block(chunk, length)
    }
}

private const val CHUNK_SIZE = 8 * 1024
