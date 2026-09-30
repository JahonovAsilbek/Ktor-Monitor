package uz.jahonov.ktormonitor.ui.detail

import kotlinx.io.bytestring.encodeToByteString
import uz.jahonov.ktormonitor.body.BodyMode
import uz.jahonov.ktormonitor.body.BodyPreview
import uz.jahonov.ktormonitor.body.CodeDocument
import uz.jahonov.ktormonitor.body.CodeLanguage
import uz.jahonov.ktormonitor.body.CodeLine
import uz.jahonov.ktormonitor.body.CodeSpan
import uz.jahonov.ktormonitor.body.FoldRegion
import uz.jahonov.ktormonitor.body.HexRow
import uz.jahonov.ktormonitor.body.MarkdownBlock
import uz.jahonov.ktormonitor.body.MarkdownSpan
import uz.jahonov.ktormonitor.body.TokenKind
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.presentation.detail.BodyContent
import uz.jahonov.ktormonitor.presentation.detail.BodyState
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiState

/** Hand-made calls and bodies for the detail previews. */
internal object DetailSamples {
    private const val REQUEST_JSON = """{"itemId":"sku-1234","amount":15000}"""
    private const val RESPONSE_JSON = """{"id":"ord-42","status":"DONE","amount":15000,"fee":{"value":0,"currency":"USD"}}"""
    private const val START = 1_790_000_000_000L

    val call = NetworkCall(
        id = "call-1",
        groupId = "group-1",
        attempt = 2,
        method = "POST",
        url = "https://api.example.com/v1/orders/checkout?source=app&trace=5f1c2a7e9b",
        requestTime = START,
        requestHeaders = mapOf(
            "Content-Type" to listOf("application/json"),
            "Authorization" to listOf("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJraWQifQ"),
            "Accept-Language" to listOf("en"),
        ),
        requestContentType = "application/json",
        requestBody = body(REQUEST_JSON),
        protocol = "HTTP/2",
        responseCode = 200,
        responseTime = START + 184,
        responseHeaders = mapOf(
            "Content-Type" to listOf("application/json; charset=utf-8"),
            "Set-Cookie" to listOf("session=abc; Path=/", "theme=dark; Path=/"),
        ),
        responseContentType = "application/json",
        responseBody = body(RESPONSE_JSON),
    )

    val failedCall = call.copy(
        responseCode = null,
        responseTime = START + 10_000,
        responseHeaders = emptyMap(),
        responseBody = null,
        error = """
            io.ktor.client.plugins.HttpRequestTimeoutException: Request timeout has expired [url=https://api.example.com/v1/orders/checkout, request_timeout=10000 ms]
            	at io.ktor.client.plugins.HttpTimeoutKt${'$'}timeoutExceptionAndroid(HttpTimeout.kt:210)
            	at io.ktor.client.plugins.HttpTimeout${'$'}Plugin${'$'}install${'$'}1${'$'}1${'$'}killer${'$'}1.invokeSuspend(HttpTimeout.kt:165)
            	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
        """.trimIndent(),
    )

    private val jsonDocument = CodeDocument(
        language = CodeLanguage.JSON,
        lines = listOf(
            line(p("{")),
            line(p("  "), k("\"id\""), p(": "), s("\"ord-42\""), p(",")),
            line(p("  "), k("\"status\""), p(": "), s("\"DONE\""), p(",")),
            line(p("  "), k("\"amount\""), p(": "), n("15000"), p(",")),
            line(p("  "), k("\"fee\""), p(": {")),
            line(p("    "), k("\"value\""), p(": "), n("0"), p(",")),
            line(p("    "), k("\"currency\""), p(": "), s("\"USD\"")),
            line(p("  }")),
            line(p("}")),
        ),
        folds = listOf(FoldRegion(0, 8, isCollapsedByDefault = false), FoldRegion(4, 7, isCollapsedByDefault = true)),
    )

    val jsonBody = BodyState(
        modes = listOf(BodyMode.CODE, BodyMode.TEXT, BodyMode.HEX),
        mode = BodyMode.CODE,
        content = BodyContent.Code(jsonDocument),
        size = RESPONSE_JSON.length.toLong(),
        isTruncated = false,
    )

    val textBody = BodyState(
        modes = listOf(BodyMode.CODE, BodyMode.TEXT, BodyMode.HEX),
        mode = BodyMode.TEXT,
        content = BodyContent.Lines(listOf(REQUEST_JSON, "second line of a plain text body, long enough to pan sideways")),
        size = 300_000,
        isTruncated = true,
    )

    val hexBody = BodyState(
        modes = listOf(BodyMode.TEXT, BodyMode.HEX),
        mode = BodyMode.HEX,
        content = BodyContent.Hex(
            listOf(
                HexRow("00000000", "7b 22 69 74 65 6d 49 64 22 3a 22 73 6b 75 2d 31", "{\"itemId\":\"sku-1"),
                HexRow("00000010", "32 33 34 22 7d                                 ", "234\"}"),
            ),
        ),
        size = 21,
        isTruncated = false,
    )

    val markdownBody = BodyState(
        modes = listOf(BodyMode.PREVIEW, BodyMode.CODE, BodyMode.TEXT, BodyMode.HEX),
        mode = BodyMode.PREVIEW,
        content = BodyContent.Preview(
            BodyPreview.Markdown(
                listOf(
                    MarkdownBlock.Heading(1, listOf(MarkdownSpan("Release notes"))),
                    MarkdownBlock.Paragraph(
                        listOf(
                            MarkdownSpan("Transfers are "),
                            MarkdownSpan("faster", isBold = true),
                            MarkdownSpan(", see "),
                            MarkdownSpan("the docs", link = "https://example.com"),
                            MarkdownSpan(" and "),
                            MarkdownSpan("retry()", isCode = true),
                            MarkdownSpan("."),
                        ),
                    ),
                    MarkdownBlock.ListItem(0, null, listOf(MarkdownSpan("Cards"))),
                    MarkdownBlock.ListItem(1, 1, listOf(MarkdownSpan("Limits", isItalic = true))),
                    MarkdownBlock.Quote(listOf(MarkdownSpan("Only in debug builds."))),
                    MarkdownBlock.CodeBlock("kotlin", "val monitor = KtorMonitor()\nmonitor.start()"),
                    MarkdownBlock.Rule,
                    MarkdownBlock.Heading(3, listOf(MarkdownSpan("Next"))),
                ),
            ),
        ),
        size = 412,
        isTruncated = false,
    )

    val state = KtorMonitorDetailUiState(call = Loadable.Ready(call), request = textBody, response = jsonBody)

    private fun body(text: String) = CapturedBody(text.encodeToByteString(), text.length.toLong())

    private fun line(vararg spans: CodeSpan) = CodeLine(spans.toList())
    private fun p(text: String) = CodeSpan(text, TokenKind.PUNCTUATION)
    private fun k(text: String) = CodeSpan(text, TokenKind.KEY)
    private fun s(text: String) = CodeSpan(text, TokenKind.STRING)
    private fun n(text: String) = CodeSpan(text, TokenKind.NUMBER)
}
