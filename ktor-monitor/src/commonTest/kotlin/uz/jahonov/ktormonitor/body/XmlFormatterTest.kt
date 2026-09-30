package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals
import uz.jahonov.ktormonitor.body.TokenKind.ATTRIBUTE
import uz.jahonov.ktormonitor.body.TokenKind.COMMENT
import uz.jahonov.ktormonitor.body.TokenKind.PLAIN
import uz.jahonov.ktormonitor.body.TokenKind.PUNCTUATION
import uz.jahonov.ktormonitor.body.TokenKind.STRING
import uz.jahonov.ktormonitor.body.TokenKind.TAG

class XmlFormatterTest {

    @Test
    fun `xml is nested by two spaces with short elements on one line`() {
        val document = XmlFormatter.format(
            """<?xml version="1.0"?><root><item id="1">Hi</item><item><name>A</name></item><!-- c --><empty/></root>""",
            isHtml = false,
        )
        assertEquals(
            listOf(
                "<?xml version=\"1.0\"?>",
                "<root>",
                "  <item id=\"1\">Hi</item>",
                "  <item>",
                "    <name>A</name>",
                "  </item>",
                "  <!-- c -->",
                "  <empty />",
                "</root>",
            ),
            document.texts,
        )
        assertEquals(listOf(FoldRegion(1, 8, false), FoldRegion(3, 5, true)), document.folds)
    }

    @Test
    fun `tags attributes and values get their kinds`() {
        val document = XmlFormatter.format("<a><item id='1' ok>Hi</item><!-- note --></a>", isHtml = false)
        assertEquals(
            listOf(
                CodeSpan("  ", PLAIN),
                CodeSpan("<item", TAG),
                CodeSpan(" ", PLAIN),
                CodeSpan("id", ATTRIBUTE),
                CodeSpan("=", PUNCTUATION),
                CodeSpan("'1'", STRING),
                CodeSpan(" ", PLAIN),
                CodeSpan("ok", ATTRIBUTE),
                CodeSpan(">", TAG),
                CodeSpan("Hi", PLAIN),
                CodeSpan("</item>", TAG),
            ),
            document.lines[1].spans,
        )
        assertEquals(listOf(CodeSpan("  ", PLAIN), CodeSpan("<!-- note -->", COMMENT)), document.lines[2].spans)
    }

    @Test
    fun `text is trimmed and cdata kept`() {
        val document = XmlFormatter.format("<a>\n   <b>\n  one\n  </b>\n<![CDATA[x < y]]>\n</a>", isHtml = false)
        assertEquals(listOf("<a>", "  <b>one</b>", "  <![CDATA[x < y]]>", "</a>"), document.texts)
    }

    @Test
    fun `html void elements and raw script text are handled`() {
        val document = XmlFormatter.format(
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>T</title></head>" +
                "<body><br><p>Text</p><script>if (a < b) { x(); }</script></body></html>",
            isHtml = true,
        )
        assertEquals(
            listOf(
                "<!DOCTYPE html>",
                "<html>",
                "  <head>",
                "    <meta charset=\"utf-8\">",
                "    <title>T</title>",
                "  </head>",
                "  <body>",
                "    <br>",
                "    <p>Text</p>",
                "    <script>if (a < b) { x(); }</script>",
                "  </body>",
                "</html>",
            ),
            document.texts,
        )
        assertEquals(
            listOf(FoldRegion(1, 11, false), FoldRegion(2, 5, true), FoldRegion(6, 10, true)),
            document.folds,
        )
        assertEquals(CodeLanguage.HTML, document.language)
    }

    @Test
    fun `multi line script keeps its relative indentation`() {
        val document = XmlFormatter.format("<script>\n    if (a) {\n      b()\n    }\n</script>", isHtml = true)
        assertEquals(listOf("<script>", "  if (a) {", "    b()", "  }", "</script>"), document.texts)
    }

    @Test
    fun `unclosed and stray tags do not break the layout`() {
        val document = XmlFormatter.format("<a><b><c>x</c></a></z><d", isHtml = false)
        assertEquals(listOf("<a>", "  <b>", "    <c>x</c>", "</a>", "</z>", "<d"), document.texts)
        assertEquals(listOf(FoldRegion(0, 3, false), FoldRegion(1, 2, true)), document.folds)
    }

    @Test
    fun `malformed markup never throws`() {
        listOf("<", "<<>>", "<a b=\"unclosed>", "<!--", "<![CDATA[", "</", "<?xml", "a < b > c", "<a =x>")
            .forEach { XmlFormatter.format(it, isHtml = true) }
    }
}
