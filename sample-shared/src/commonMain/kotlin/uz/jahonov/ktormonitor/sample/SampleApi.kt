package uz.jahonov.ktormonitor.sample

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.parametersOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import uz.jahonov.ktormonitor.KtorMonitor

/** A client with the monitor attached, and a set of calls that show each kind of body and failure. */
class SampleApi(monitor: KtorMonitor) {
    private val client = HttpClient().also(monitor::attach)
    private val scope = MainScope()

    val requests: List<String> = SampleRequest.entries.map { it.title }

    /** Runs the request at [index] in [requests]; its result is only seen in the monitor. */
    fun send(index: Int) {
        scope.launch {
            runCatching { SampleRequest.entries[index].send(client) }
        }
    }
}

private enum class SampleRequest(val title: String, val send: suspend (HttpClient) -> Unit) {
    Json("GET JSON", { it.get("$HTTPBIN/json").bodyAsText() }),
    PostJson("POST JSON", { client ->
        client.post("$HTTPBIN/post") {
            setBody(TextContent("""{"name":"Ktor Monitor","tags":["kmp","ktor"],"count":3}""", ContentType.Application.Json))
        }.bodyAsText()
    }),
    Form("POST form", { client ->
        client.post("$HTTPBIN/post") { setBody(FormDataContent(parametersOf("user" to listOf("sample"), "remember" to listOf("true")))) }.bodyAsText()
    }),
    Xml("GET XML", { it.get("$HTTPBIN/xml").bodyAsText() }),
    Html("GET HTML", { it.get("$HTTPBIN/html").bodyAsText() }),
    Image("GET image", { it.get("$HTTPBIN/image/png").bodyAsText() }),
    Stream("GET stream", { it.get("$HTTPBIN/stream/5").bodyAsText() }),
    Redirect("Redirect twice", { it.get("$HTTPBIN/redirect/2").bodyAsText() }),
    NotFound("404", { it.get("$HTTPBIN/status/404").bodyAsText() }),
    ServerError("500", { it.get("$HTTPBIN/status/500").bodyAsText() }),
    Slow("Slow, 3 s", { it.get("$HTTPBIN/delay/3").bodyAsText() }),
    Offline("Unknown host", { it.get("https://ktormonitor.invalid/").bodyAsText() }),
}

private const val HTTPBIN = "https://httpbin.org"
