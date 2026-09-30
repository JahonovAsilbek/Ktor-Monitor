package uz.jahonov.ktormonitor.capture

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readByte
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uz.jahonov.ktormonitor.model.NetworkCall

// A body the engine is still writing when the app stops reading: only a real engine shows whether
// the app's call can finish. MockEngine has no writer of its own to wait for.
class StreamedResponseTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/large") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, 0)
            runCatching { exchange.responseBody.use { out -> repeat(1_000) { out.write(ByteArray(64 * 1024)) } } }
        }
        start()
    }

    private val calls = MutableStateFlow<List<NetworkCall>>(emptyList())
    private val store = object : CallStore {
        override suspend fun insert(call: NetworkCall) = update(call)

        override suspend fun update(call: NetworkCall) {
            calls.update { list -> list.filterNot { it.id == call.id } + call }
        }
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `an app that stops reading early still finishes its call`() = runBlocking {
        val client = HttpClient(OkHttp)
        KtorMonitorCapture(store, KtorMonitorConfig(), this).install(client)

        val first = withTimeout(10_000) {
            client.prepareGet("http://127.0.0.1:${server.address.port}/large").execute { it.bodyAsChannel().readByte() }
        }

        assertEquals(0, first.toInt())
        val call = withTimeout(10_000) { calls.first { list -> list.any { it.responseBody != null } } }.single()
        assertEquals(200, call.responseCode)
        // Stopping early is the app's choice, not a failure of the call.
        assertNull(call.error)
        client.close()
        coroutineContext.cancelChildren()
    }
}
