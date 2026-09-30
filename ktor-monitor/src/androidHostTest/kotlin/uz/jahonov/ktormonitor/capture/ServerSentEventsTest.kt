package uz.jahonov.ktormonitor.capture

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.bytestring.decodeToString
import uz.jahonov.ktormonitor.model.NetworkCall

// Ktor's SSE plugin turns the body into a session inside the engine, which MockEngine cannot do.
// A real server and OkHttp show what the app would see.
class ServerSentEventsTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/denied") { exchange ->
            val body = "{\"error\":\"denied\"}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        createContext("/events") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                listOf("first", "second", "third").forEach { data ->
                    out.write("event: tick\ndata: $data\n\n".toByteArray())
                    out.flush()
                    Thread.sleep(50)
                }
            }
        }
        start()
    }

    private val calls = MutableStateFlow<List<NetworkCall>>(emptyList())
    private val bodiesSeen = mutableListOf<String>()
    private val store = object : CallStore {
        override suspend fun insert(call: NetworkCall) = update(call)

        override suspend fun update(call: NetworkCall) {
            call.responseBody?.let { bodiesSeen += it.bytes.decodeToString() }
            calls.update { list -> list.filterNot { it.id == call.id } + call }
        }
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `the app still gets every event and the monitor records the stream`() = runBlocking {
        val client = HttpClient(OkHttp) { install(SSE) }
        KtorMonitorCapture(store, KtorMonitorConfig(), this).install(client)
        val url = "http://127.0.0.1:${server.address.port}/events"

        val received = mutableListOf<String?>()
        withTimeout(10_000) {
            client.sse(url) { incoming.take(3).toList().forEach { received += it.data } }
        }

        assertEquals(listOf<String?>("first", "second", "third"), received)
        val call = withTimeout(10_000) {
            calls.first { list -> list.any { it.responseBody?.bytes?.decodeToString()?.contains("data: third") == true } }
        }.single()
        assertEquals(200, call.responseCode)
        assertTrue("data: first" in call.responseBody!!.bytes.decodeToString())
        // Recorded as it streamed, not only at the end.
        assertTrue(bodiesSeen.any { "data: first" in it && "data: third" !in it }, bodiesSeen.toString())
        client.close()
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a refused stream keeps its response body`() = runBlocking {
        val client = HttpClient(OkHttp) { install(SSE) }
        KtorMonitorCapture(store, KtorMonitorConfig(), this).install(client)

        assertFailsWith<SSEClientException> {
            withTimeout(10_000) { client.sse("http://127.0.0.1:${server.address.port}/denied") {} }
        }

        val call = withTimeout(10_000) { calls.first { list -> list.any { it.responseBody != null } } }.single()
        assertEquals(401, call.responseCode)
        assertEquals("{\"error\":\"denied\"}", call.responseBody!!.bytes.decodeToString())
        client.close()
        coroutineContext.cancelChildren()
    }
}
