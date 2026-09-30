package uz.jahonov.ktormonitor.export

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall

class CallExporterTest {

    // 2026-09-30T14:25:01Z
    private val start = 1_790_778_301_000L

    private fun textBody(text: String, size: Long? = null): CapturedBody {
        val bytes = text.encodeToByteString()
        return CapturedBody(bytes, size ?: bytes.size.toLong())
    }

    private val binaryBody = CapturedBody(ByteString(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x01), 1234)

    private val post = NetworkCall(
        id = "c1",
        groupId = "g1",
        attempt = 1,
        method = "POST",
        url = "https://api.example.com/v1/cards?page=2&q=a%20b",
        requestTime = start,
        requestHeaders = mapOf(
            "Authorization" to listOf("Bearer abc"),
            "Content-Length" to listOf("27"),
            "Content-Type" to listOf("application/json"),
        ),
        requestContentType = "application/json",
        requestBody = textBody("""{"name":"it's","count":1}"""),
        protocol = "HTTP/1.1",
        responseCode = 201,
        responseTime = start + 150,
        responseHeaders = mapOf("Content-Type" to listOf("application/json"), "Set-Cookie" to listOf("a=1", "b=2")),
        responseContentType = "application/json",
        responseBody = textBody("""{"id":7}"""),
    )

    private val failed = NetworkCall(
        id = "c2",
        groupId = "g2",
        attempt = 2,
        method = "GET",
        url = "https://api.example.com/v1/me",
        requestTime = start + 1_000,
        requestHeaders = mapOf("Accept" to listOf("application/json")),
        requestContentType = null,
        requestBody = null,
        error = "java.net.SocketTimeoutException: timeout",
    )

    private val inProgress = failed.copy(id = "c3", error = null)

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    private fun JsonObject.array(key: String) = getValue(key).jsonArray

    @Test
    fun curlHasMethodHeadersAndEscapedBody() {
        val expected = """
            curl -X POST 'https://api.example.com/v1/cards?page=2&q=a%20b' \
              -H 'Authorization: Bearer abc' \
              -H 'Content-Type: application/json' \
              --data-binary '{"name":"it'\''s","count":1}'
        """.trimIndent()
        assertEquals(expected, CallExporter.curl(post))
    }

    @Test
    fun curlLeavesOutBinaryBodyWithComment() {
        val call = post.copy(requestContentType = "image/png", requestBody = binaryBody)
        val curl = CallExporter.curl(call)
        assertTrue(curl.startsWith("# binary body of 1234 bytes not included\ncurl -X POST"))
        assertFalse("--data-binary" in curl)
    }

    @Test
    fun curlNotesTruncatedBody() {
        val call = post.copy(requestBody = textBody("""{"a":1}""", size = 900))
        assertTrue(CallExporter.curl(call).startsWith("# body truncated: 7 of 900 bytes kept\n"))
    }

    @Test
    fun wgetHasMethodHeadersBodyAndUrl() {
        val expected = """
            wget --method=POST \
              --header='Authorization: Bearer abc' \
              --header='Content-Type: application/json' \
              --body-data='{"name":"it'\''s","count":1}' \
              -O - 'https://api.example.com/v1/cards?page=2&q=a%20b'
        """.trimIndent()
        assertEquals(expected, CallExporter.wget(post))
    }

    @Test
    fun textDumpsRequestAndResponse() {
        val expected = """
            POST https://api.example.com/v1/cards?page=2&q=a%20b HTTP/1.1
            Authorization: Bearer abc
            Content-Length: 27
            Content-Type: application/json

            {
                "name": "it's",
                "count": 1
            }

            HTTP/1.1 201 Created
            Content-Type: application/json
            Set-Cookie: a=1
            Set-Cookie: b=2

            {
                "id": 7
            }
        """.trimIndent()
        assertEquals(expected, CallExporter.text(post))
    }

    @Test
    fun textShowsError() {
        val expected = """
            GET https://api.example.com/v1/me HTTP/1.1
            Accept: application/json

            Error:
            java.net.SocketTimeoutException: timeout
        """.trimIndent()
        assertEquals(expected, CallExporter.text(failed))
    }

    @Test
    fun textShowsInProgress() {
        assertTrue(CallExporter.text(inProgress).endsWith("Accept: application/json\n\n(in progress)"))
    }

    @Test
    fun textMarksTruncatedAndBinaryBodies() {
        val call = post.copy(
            requestBody = textBody("""{"name":""", size = 250_000),
            responseContentType = "image/png",
            responseBody = binaryBody,
        )
        val text = CallExporter.text(call)
        assertTrue("{\"name\":\n(truncated: 8 of 250000 bytes kept)" in text)
        assertTrue("<binary, 1 234 bytes>" in text)
    }

    @Test
    fun markdownHasSummaryAndFencedBlocks() {
        val markdown = CallExporter.markdown(post.copy(responseContentType = "text/html", responseBody = textBody("<p>hi</p>")))
        assertTrue(markdown.startsWith("## POST /v1/cards?page=2&q=a%20b\n"))
        assertTrue("| URL | https://api.example.com/v1/cards?page=2&q=a%20b |" in markdown)
        assertTrue("| Status | 201 Created |" in markdown)
        assertTrue("| Request time | 2026-09-30T14:25:01Z |" in markdown)
        assertTrue("| Duration | 150 ms |" in markdown)
        assertTrue("### Request headers\n\n```\nAuthorization: Bearer abc\n" in markdown)
        assertTrue("### Request body\n\n```json\n{\n    \"name\"" in markdown)
        assertTrue("### Response body\n\n```html\n<p>hi</p>\n```" in markdown)
    }

    @Test
    fun markdownFencesErrorAndBinary() {
        val markdown = CallExporter.markdown(failed.copy(requestContentType = "image/png", requestBody = binaryBody))
        assertTrue("| Status | Failed |" in markdown)
        assertTrue("### Request body\n\n```\n<binary, 1 234 bytes>\n```" in markdown)
        assertTrue(markdown.endsWith("### Error\n\n```\njava.net.SocketTimeoutException: timeout\n```"))
        assertFalse("### Response" in markdown)
    }

    @Test
    fun markdownFenceOutgrowsBackticksInBody() {
        val call = post.copy(requestContentType = "text/plain", requestBody = textBody("a ``` b"))
        assertTrue("````\na ``` b\n````" in CallExporter.markdown(call))
    }

    @Test
    fun jsonExportHasAppAndCalls() {
        val json = parse(CallExporter.export(listOf(post, failed), ExportFormat.JSON, start, "ExampleApp", "1.2.0"))
        assertEquals("2026-09-30T14:25:01Z", json.string("exportedAt"))
        assertEquals("ExampleApp", json.obj("app").string("name"))
        assertEquals("1.2.0", json.obj("app").string("version"))

        val (first, second) = json.array("calls").map { it.jsonObject }
        assertEquals("c1", first.string("id"))
        assertEquals("g1", first.string("groupId"))
        assertEquals(150L, first.getValue("durationMs").jsonPrimitive.long)
        val request = first.obj("request")
        assertEquals("POST", request.string("method"))
        assertEquals(listOf("Bearer abc"), request.obj("headers").array("Authorization").map { it.jsonPrimitive.content })
        assertEquals("{\n    \"name\": \"it's\",\n    \"count\": 1\n}", request.string("body"))
        assertEquals(25, request.getValue("bodySize").jsonPrimitive.int)
        assertFalse(request.getValue("bodyTruncated").jsonPrimitive.boolean)
        val response = first.obj("response")
        assertEquals(201, response.getValue("statusCode").jsonPrimitive.int)
        assertEquals("HTTP/1.1", response.string("protocol"))
        assertEquals(2, response.obj("headers").array("Set-Cookie").size)
        assertEquals(JsonNull, first.getValue("error"))

        assertEquals(2, second.getValue("attempt").jsonPrimitive.int)
        assertEquals(JsonNull, second.getValue("response"))
        assertEquals(JsonNull, second.getValue("durationMs"))
        assertEquals(JsonNull, second.obj("request").getValue("body"))
        assertEquals("java.net.SocketTimeoutException: timeout", second.string("error"))
    }

    @Test
    fun jsonExportMarksTruncatedBody() {
        val call = post.copy(responseBody = textBody("""{"id":""", size = 812_345))
        val response = parse(CallExporter.export(listOf(call), ExportFormat.JSON, start, "App", "1")).array("calls")
            .first().jsonObject.obj("response")
        assertTrue(response.getValue("bodyTruncated").jsonPrimitive.boolean)
        assertEquals(812_345L, response.getValue("bodySize").jsonPrimitive.long)
        assertEquals("""{"id":""", response.string("body"))
    }

    @Test
    fun harHasLogCreatorAndEntries() {
        val log = parse(CallExporter.export(listOf(failed, post), ExportFormat.HAR, start, "ExampleApp", "1.2.0")).obj("log")
        assertEquals("1.2", log.string("version"))
        assertEquals("ExampleApp", log.obj("creator").string("name"))
        assertEquals("1.2.0", log.obj("creator").string("version"))

        val entries = log.array("entries").map { it.jsonObject }
        assertEquals(listOf("POST", "GET"), entries.map { it.obj("request").string("method") })

        val entry = entries.first()
        assertEquals("2026-09-30T14:25:01Z", entry.string("startedDateTime"))
        assertEquals(150, entry.getValue("time").jsonPrimitive.int)
        assertEquals(150, entry.obj("timings").getValue("wait").jsonPrimitive.int)
        assertEquals(JsonObject(emptyMap()), entry.obj("cache"))

        val request = entry.obj("request")
        assertEquals("HTTP/1.1", request.string("httpVersion"))
        assertEquals(-1, request.getValue("headersSize").jsonPrimitive.int)
        assertEquals(JsonArray(emptyList()), request.array("cookies"))
        assertEquals(
            listOf("page" to "2", "q" to "a b"),
            request.array("queryString").map { it.jsonObject.string("name") to it.jsonObject.string("value") },
        )
        assertEquals("application/json", request.obj("postData").string("mimeType"))
        assertEquals("""{"name":"it's","count":1}""", request.obj("postData").string("text"))

        val response = entry.obj("response")
        assertEquals(201, response.getValue("status").jsonPrimitive.int)
        assertEquals("Created", response.string("statusText"))
        assertEquals(3, response.array("headers").size)
        assertEquals("""{"id":7}""", response.obj("content").string("text"))
        assertEquals(8, response.obj("content").getValue("size").jsonPrimitive.int)
        assertEquals("", response.string("redirectURL"))
    }

    @Test
    fun harGivesFailedCallStatusZeroAndError() {
        val entry = parse(CallExporter.export(listOf(failed), ExportFormat.HAR, start, "App", "1"))
            .obj("log").array("entries").single().jsonObject
        assertEquals(0, entry.obj("response").getValue("status").jsonPrimitive.int)
        assertEquals("", entry.obj("response").string("statusText"))
        assertEquals("java.net.SocketTimeoutException: timeout", entry.string("_error"))
        assertEquals(0, entry.getValue("time").jsonPrimitive.int)
        assertFalse("postData" in entry.obj("request"))
        assertEquals(JsonArray(emptyList()), entry.obj("request").array("queryString"))
    }

    @Test
    fun harEncodesBinaryAsBase64AndNotesTruncation() {
        val call = post.copy(
            responseCode = 302,
            responseHeaders = mapOf("location" to listOf("https://example.com/next")),
            responseContentType = "image/png",
            responseBody = binaryBody,
        )
        val response = parse(CallExporter.export(listOf(call), ExportFormat.HAR, start, "App", "1"))
            .obj("log").array("entries").single().jsonObject.obj("response")
        val content = response.obj("content")
        assertEquals("base64", content.string("encoding"))
        assertEquals(Base64.encode(binaryBody.bytes.toByteArray()), content.string("text"))
        assertEquals(1234, content.getValue("size").jsonPrimitive.int)
        assertEquals("Truncated: 6 of 1234 bytes kept", content.string("comment"))
        assertEquals("image/png", content.string("mimeType"))
        assertEquals("Found", response.string("statusText"))
        assertEquals("https://example.com/next", response.string("redirectURL"))
    }

    @Test
    fun textExportSeparatesCalls() {
        val text = CallExporter.export(listOf(failed, inProgress), ExportFormat.TEXT, start, "App", "1")
        assertEquals(CallExporter.text(failed) + "\n\n---\n\n" + CallExporter.text(inProgress), text)
    }

    @Test
    fun urlListHasOneUrlPerLine() {
        assertEquals(
            "https://api.example.com/v1/cards?page=2&q=a%20b\nhttps://api.example.com/v1/me",
            CallExporter.export(listOf(post, failed), ExportFormat.URLS, start, "App", "1"),
        )
    }

    @Test
    fun fileNameUsesUtcTimestampAndExtension() {
        assertEquals("netmonitor-2026-09-30-142501.har", CallExporter.fileName(ExportFormat.HAR, start + 250))
        assertEquals("netmonitor-2026-09-30-142501.txt", CallExporter.fileName(ExportFormat.TEXT, start))
        assertEquals("netmonitor-2026-09-30-142501.http", CallExporter.fileName(ExportFormat.TEXT, start, single = true))
        assertEquals("netmonitor-2026-09-30-142501.md", CallExporter.fileName(ExportFormat.MARKDOWN, start, single = true))
    }
}
