package uz.jahonov.ktormonitor.capture

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.bytestring.decodeToString
import uz.jahonov.ktormonitor.model.NetworkCall

class KtorMonitorCaptureTest {

    private class FakeCallStore : CallStore {
        val calls = MutableStateFlow<List<NetworkCall>>(emptyList())
        var failsFor: String? = null

        override suspend fun upsert(call: NetworkCall) {
            failsFor?.let { if (it in call.url) error("disk full") }
            calls.update { list -> list.filterNot { it.id == call.id } + call }
        }

        suspend fun await(count: Int, until: (NetworkCall) -> Boolean = { !it.isInProgress }) =
            calls.first { list -> list.size == count && list.all(until) }
    }

    private fun CoroutineScope.monitoredClient(
        store: CallStore,
        configure: KtorMonitorConfig.() -> Unit = {},
        outside: HttpClient.() -> Unit = {},
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        val client = HttpClient(MockEngine(handler)) { expectSuccess = false }.apply(outside)
        KtorMonitorCapture(store, KtorMonitorConfig().apply(configure), this).install(client)
        return client
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `a call is recorded with its request and response and timing`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store) { json("""{"id":1}""") }

        client.post("https://api.test/cards?page=2") {
            header("x-app-lang", "uz")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Ali"}""")
        }

        val call = store.await(1).single()
        assertEquals("POST", call.method)
        assertEquals("https://api.test/cards?page=2", call.url)
        assertEquals(listOf("uz"), call.requestHeaders["x-app-lang"])
        assertEquals("""{"name":"Ali"}""", call.requestBody?.bytes?.decodeToString())
        assertEquals(200, call.responseCode)
        assertEquals("HTTP/1.1", call.protocol)
        assertEquals("application/json", call.responseContentType)
        assertEquals("""{"id":1}""", call.responseBody?.bytes?.decodeToString())
        assertEquals(1, call.attempt)
        assertTrue(call.durationMillis!! >= 0)
        assertNull(call.error)
    }

    @Test
    fun `the app still reads the whole body`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store, { maxContentLength = 4 }) { json("0123456789") }

        assertEquals("0123456789", client.get("https://api.test/").bodyAsText())

        val body = assertNotNull(store.await(1).single().responseBody)
        assertEquals("0123", body.bytes.decodeToString())
        assertEquals(10, body.size)
        assertTrue(body.isTruncated)
    }

    @Test
    fun `a streamed request body is recorded and sent unchanged`() = runTest {
        val store = FakeCallStore()
        var sent = ""
        val client = backgroundScope.monitoredClient(store) { request ->
            sent = request.body.toByteArray().decodeToString()
            json("{}")
        }

        client.post("https://api.test/upload") {
            setBody(MultiPartFormDataContent(formData { append("title", "avatar") }, boundary = "b"))
        }

        val recorded = store.await(1) { it.requestBody != null }.single().requestBody!!
        assertTrue("avatar" in sent)
        assertEquals(sent, recorded.bytes.decodeToString())
        assertFalse(recorded.isTruncated)
    }

    @Test
    fun `a streamed response is recorded as the app reads it`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store) {
            respond(ByteReadChannel("line 1\nline 2\n"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain"))
        }

        val text = client.prepareGet("https://api.test/stream").execute { it.bodyAsText() }

        assertEquals("line 1\nline 2\n", text)
        val call = store.await(1) { it.responseBody != null }.single()
        assertEquals("line 1\nline 2\n", call.responseBody?.bytes?.decodeToString())
    }

    @Test
    fun `a failed call is recorded with its error and still fails`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store) { throw IOException("offline") }

        assertFailsWith<IOException> { client.get("https://api.test/") }

        val call = store.await(1).single()
        assertNull(call.responseCode)
        assertTrue("offline" in call.error!!)
    }

    @Test
    fun `every attempt of a retried request is its own record in one group`() = runTest {
        val store = FakeCallStore()
        var answered = 0
        // Registered before the monitor, so it runs outside it — where an auth plugin sits in an app.
        val retryOn401: HttpClient.() -> Unit = {
            plugin(HttpSend).intercept { request ->
                val first = execute(request)
                if (first.response.status == HttpStatusCode.Unauthorized) execute(request) else first
            }
        }
        val client = backgroundScope.monitoredClient(store, outside = retryOn401) {
            json("{}", if (answered++ == 0) HttpStatusCode.Unauthorized else HttpStatusCode.OK)
        }

        client.get("https://api.test/cards")

        val calls = store.await(2).sortedBy { it.attempt }
        assertEquals(listOf(401, 200), calls.map { it.responseCode })
        assertEquals(listOf(1, 2), calls.map { it.attempt })
        assertEquals(1, calls.map { it.groupId }.distinct().size)
    }

    @Test
    fun `filtered out calls are not recorded`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store, { filter { "health" !in it.url.encodedPath } }) { json("{}") }

        client.get("https://api.test/health")
        client.get("https://api.test/cards")

        assertEquals("https://api.test/cards", store.await(1).single().url)
    }

    @Test
    fun `sanitized headers keep their name and lose their value`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store, { sanitizeHeaders("authorization") }) { json("{}") }

        client.get("https://api.test/") { header(HttpHeaders.Authorization, "Bearer secret") }

        assertEquals(listOf("***"), store.await(1).single().requestHeaders[HttpHeaders.Authorization])
    }

    @Test
    fun `redacted body fields are replaced at any depth`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store, { redactBodyFields("accessToken") }) {
            json("""{"data":{"AccessToken":"secret","user":"Ali"},"list":[{"accessToken":"x"}]}""")
        }

        client.get("https://api.test/")

        assertEquals(
            """{"data":{"AccessToken":"***","user":"Ali"},"list":[{"accessToken":"***"}]}""",
            store.await(1).single().responseBody?.bytes?.decodeToString(),
        )
    }

    @Test
    fun `nothing is redacted by default`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store) { json("""{"accessToken":"secret"}""") }

        client.get("https://api.test/") { header(HttpHeaders.Authorization, "Bearer secret") }

        val call = store.await(1).single()
        assertEquals(listOf("Bearer secret"), call.requestHeaders[HttpHeaders.Authorization])
        assertEquals("""{"accessToken":"secret"}""", call.responseBody?.bytes?.decodeToString())
    }

    @Test
    fun `a failing store never fails the app and recording resumes`() = runTest {
        val store = FakeCallStore().apply { failsFor = "first" }
        val client = backgroundScope.monitoredClient(store) { json("""{"ok":true}""") }

        assertEquals("""{"ok":true}""", client.get("https://api.test/first").bodyAsText())
        client.get("https://api.test/second")

        assertEquals("https://api.test/second", store.await(1).single().url)
    }

    @Test
    fun `an inactive monitor records nothing`() = runTest {
        val store = FakeCallStore()
        val client = backgroundScope.monitoredClient(store, { isActive = false }) { json("{}") }

        client.get("https://api.test/")

        assertTrue(store.calls.value.isEmpty())
    }
}
