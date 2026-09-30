package uz.jahonov.ktormonitor.bridge

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import uz.jahonov.ktormonitor.body.BodyMode
import uz.jahonov.ktormonitor.body.BodyPreview
import uz.jahonov.ktormonitor.body.CodeDocument
import uz.jahonov.ktormonitor.body.CodeLanguage
import uz.jahonov.ktormonitor.body.CodeLine
import uz.jahonov.ktormonitor.body.CodeSpan
import uz.jahonov.ktormonitor.body.FoldRegion
import uz.jahonov.ktormonitor.body.HexRow
import uz.jahonov.ktormonitor.body.ImageFormat
import uz.jahonov.ktormonitor.body.MarkdownBlock
import uz.jahonov.ktormonitor.body.MarkdownSpan
import uz.jahonov.ktormonitor.body.TokenKind
import uz.jahonov.ktormonitor.body.body
import uz.jahonov.ktormonitor.body.bytesOf
import uz.jahonov.ktormonitor.data.testCall
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.presentation.KtorMonitorNotifier
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.presentation.SharedFile
import uz.jahonov.ktormonitor.presentation.detail.BodyContent
import uz.jahonov.ktormonitor.presentation.detail.BodySide
import uz.jahonov.ktormonitor.presentation.detail.BodyState
import uz.jahonov.ktormonitor.presentation.detail.CopyFormat
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEffect
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEvent
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiState
import uz.jahonov.ktormonitor.presentation.list.CallFilters
import uz.jahonov.ktormonitor.presentation.list.CallSort
import uz.jahonov.ktormonitor.presentation.list.DurationRange
import uz.jahonov.ktormonitor.presentation.list.FilterOptions
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEffect
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEvent
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiState
import uz.jahonov.ktormonitor.presentation.list.StatusClass

/**
 * The JSON contract with the Swift package. The fixtures live with the package's tests, which
 * decode the same files; a change here that breaks them fails on both sides.
 * `./gradlew :ktor-monitor:testAndroidHostTest -PrecordFixtures` rewrites the state and effect ones.
 */
class BridgeFixturesTest {

    private val directory = File(requireNotNull(System.getProperty("bridgeFixtures")))
    private val record = System.getProperty("recordFixtures").toBoolean()
    private val pretty = Json(BridgeJson) { prettyPrint = true }

    private val cards = testCall("cards", url = "https://api.example.com/v1/items?page=2", requestTime = 1_790_000_000_000)
    private val login = testCall(
        "login",
        url = "https://auth.example.com/login",
        method = "POST",
        requestTime = 1_790_000_001_000,
        responseCode = null,
        error = "java.net.UnknownHostException: Unable to resolve host",
    )

    @Test
    fun `list state`() = check(
        "list-state.json",
        ListStateWire.serializer(),
        KtorMonitorListUiState(
            calls = Loadable.Ready(listOf(login.summary, cards.summary)),
            totalCount = 2,
            isSearchVisible = true,
            query = "api",
            filters = CallFilters(hosts = setOf("api.example.com"), statuses = setOf(StatusClass.SUCCESS)),
            options = FilterOptions(hosts = listOf("api.example.com", "auth.example.com"), methods = listOf("GET", "POST")),
            sort = CallSort.DURATION_DESCENDING,
            selection = setOf("cards"),
        ).toWire(),
    )

    @Test
    fun `list state while loading`() = check("list-state-loading.json", ListStateWire.serializer(), KtorMonitorListUiState().toWire())

    @Test
    fun `detail with code and lines`() = check(
        "detail-code.json",
        DetailStateWire.serializer(),
        KtorMonitorDetailUiState(
            call = Loadable.Ready(cards),
            request = BodyState(listOf(BodyMode.TEXT, BodyMode.HEX), BodyMode.TEXT, BodyContent.Lines(listOf("page=2", "size=20")), 14, false),
            response = BodyState(
                modes = listOf(BodyMode.CODE, BodyMode.TEXT, BodyMode.HEX),
                mode = BodyMode.CODE,
                content = BodyContent.Code(
                    CodeDocument(
                        language = CodeLanguage.JSON,
                        lines = listOf(
                            CodeLine(listOf(CodeSpan("{", TokenKind.PUNCTUATION))),
                            CodeLine(listOf(CodeSpan("  ", TokenKind.PLAIN), CodeSpan("\"id\"", TokenKind.KEY), CodeSpan(": ", TokenKind.PUNCTUATION), CodeSpan("42", TokenKind.NUMBER))),
                            CodeLine(listOf(CodeSpan("}", TokenKind.PUNCTUATION))),
                        ),
                        folds = listOf(FoldRegion(0, 2, isCollapsedByDefault = false)),
                    ),
                ),
                size = 250_000,
                isTruncated = true,
            ),
        ).toWire(),
    )

    @Test
    fun `detail with hex and an image`() = check(
        "detail-media.json",
        DetailStateWire.serializer(),
        KtorMonitorDetailUiState(
            call = Loadable.Ready(cards.copy(responseContentType = "image/png")),
            request = BodyState(
                listOf(BodyMode.HEX),
                BodyMode.HEX,
                BodyContent.Hex(listOf(HexRow("00000000", "7b 7d".padEnd(47), "{}"))),
                2,
                false,
            ),
            response = BodyState(
                listOf(BodyMode.PREVIEW, BodyMode.HEX),
                BodyMode.PREVIEW,
                BodyContent.Preview(BodyPreview.Image(body(bytesOf(0x89, 0x50, 0x4e, 0x47)).bytes, ImageFormat.PNG)),
                4,
                false,
            ),
        ).toWire(),
    )

    @Test
    fun `detail with markdown, in flight`() = check(
        "detail-markdown.json",
        DetailStateWire.serializer(),
        KtorMonitorDetailUiState(
            call = Loadable.Ready(cards.copy(responseCode = null, responseTime = null, protocol = null)),
            response = BodyState(
                listOf(BodyMode.PREVIEW, BodyMode.CODE),
                BodyMode.PREVIEW,
                BodyContent.Preview(
                    BodyPreview.Markdown(
                        listOf(
                            MarkdownBlock.Heading(1, listOf(MarkdownSpan("Title"))),
                            MarkdownBlock.Paragraph(listOf(MarkdownSpan("Plain "), MarkdownSpan("bold", isBold = true), MarkdownSpan("docs", link = "https://example.com"))),
                            MarkdownBlock.ListItem(0, 1, listOf(MarkdownSpan("first"))),
                            MarkdownBlock.ListItem(1, null, listOf(MarkdownSpan("nested", isItalic = true))),
                            MarkdownBlock.Quote(listOf(MarkdownSpan("quoted"))),
                            MarkdownBlock.CodeBlock("kotlin", "val x = 1"),
                            MarkdownBlock.Rule,
                        ),
                    ),
                ),
                120,
                false,
            ),
        ).toWire(),
    )

    @Test
    fun `detail while loading`() = check("detail-loading.json", DetailStateWire.serializer(), KtorMonitorDetailUiState().toWire())

    @Test
    fun effects() = check(
        "effects.json",
        ListSerializer(EffectWire.serializer()),
        listOf(
            KtorMonitorListUiEffect.OpenCall("cards").toWire(),
            KtorMonitorListUiEffect.Share(SharedFile("calls.har", "application/json", "{}")).toWire(),
            KtorMonitorDetailUiEffect.CopyText("https://api.example.com/v1/items").toWire(),
            KtorMonitorDetailUiEffect.Close.toWire(),
        ),
    )

    @Test
    fun notification() = check(
        "notification.json",
        NotificationWire.serializer(),
        NotificationWire(KtorMonitorNotifier.TITLE, listOf("❌ POST /login", "200 GET /v1/items?page=2")),
    )

    @Test
    fun `list events`() = checkEvents(
        "list-events.jsonl",
        ListEventWire.serializer(),
        { it.toEvent() },
        listOf(
            KtorMonitorListUiEvent.ToggleSearch,
            KtorMonitorListUiEvent.Search("cards"),
            KtorMonitorListUiEvent.ToggleOnlyErrors,
            KtorMonitorListUiEvent.ToggleHost("api.example.com"),
            KtorMonitorListUiEvent.ToggleMethod("GET"),
            KtorMonitorListUiEvent.ToggleContentType("application/json"),
            KtorMonitorListUiEvent.ToggleStatus(StatusClass.CLIENT_ERROR),
            KtorMonitorListUiEvent.ToggleDuration(DurationRange.OVER_5_S),
            KtorMonitorListUiEvent.ClearFilters,
            KtorMonitorListUiEvent.Sort(CallSort.SIZE_DESCENDING),
            KtorMonitorListUiEvent.Click("cards"),
            KtorMonitorListUiEvent.LongClick("cards"),
            KtorMonitorListUiEvent.StartSelection,
            KtorMonitorListUiEvent.SelectAll,
            KtorMonitorListUiEvent.ClearSelection,
            KtorMonitorListUiEvent.ExitSelection,
            KtorMonitorListUiEvent.DeleteSelected,
            KtorMonitorListUiEvent.ShareSelected(ExportFormat.HAR),
            KtorMonitorListUiEvent.ClearAll,
        ),
    )

    @Test
    fun `detail events`() = checkEvents(
        "detail-events.jsonl",
        DetailEventWire.serializer(),
        { it.toEvent() },
        listOf(
            KtorMonitorDetailUiEvent.SelectMode(BodySide.RESPONSE, BodyMode.HEX),
            KtorMonitorDetailUiEvent.Copy(CopyFormat.CURL),
            KtorMonitorDetailUiEvent.CopyHeaders(BodySide.REQUEST),
            KtorMonitorDetailUiEvent.CopyBody(BodySide.RESPONSE),
            KtorMonitorDetailUiEvent.Share(ExportFormat.MARKDOWN),
        ),
    )

    private fun <T> check(name: String, serializer: KSerializer<T>, value: T) {
        val file = directory.resolve(name)
        val json = pretty.encodeToString(serializer, value) + "\n"
        if (record) file.writeText(json)
        assertEquals(file.readText(), json, "$name no longer matches; rerun with -PrecordFixtures if the change is meant")
    }

    /** Written by hand, one event per line as Swift sends it; never recorded. */
    private fun <W, E> checkEvents(name: String, serializer: KSerializer<W>, toEvent: (W) -> E, expected: List<E>) {
        val lines = directory.resolve(name).readLines().filter { it.isNotBlank() }
        assertEquals(expected, lines.map { toEvent(BridgeJson.decodeFromString(serializer, it)) })
    }
}
