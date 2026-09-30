package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.jahonov.ktormonitor.body.TokenKind.COMMENT
import uz.jahonov.ktormonitor.body.TokenKind.EMPHASIS
import uz.jahonov.ktormonitor.body.TokenKind.HEADING
import uz.jahonov.ktormonitor.body.TokenKind.KEY
import uz.jahonov.ktormonitor.body.TokenKind.KEYWORD
import uz.jahonov.ktormonitor.body.TokenKind.LINK
import uz.jahonov.ktormonitor.body.TokenKind.NUMBER
import uz.jahonov.ktormonitor.body.TokenKind.PUNCTUATION
import uz.jahonov.ktormonitor.body.TokenKind.STRING
import uz.jahonov.ktormonitor.body.TokenKind.TAG

class HighlightersTest {

    private fun assertContains(tokens: List<Pair<String, TokenKind>>, vararg expected: Pair<String, TokenKind>) {
        expected.forEach { assertTrue(it in tokens, "$it not in $tokens") }
    }

    @Test
    fun `css selectors properties and values get their kinds`() {
        val css = "a:hover, .b { color: #fff; margin: 0 10px !important; }\n/* c */\n@media (x) {\n  p { top: -1.5em }\n}"
        val document = CssHighlighter.highlight(css)
        assertEquals(css.lines(), document.texts)
        assertContains(
            document.tokens,
            "a:hover," to TAG, ".b" to TAG, "{" to PUNCTUATION, "color" to KEY, ":" to PUNCTUATION, "#fff" to NUMBER,
            "margin" to KEY, "0" to NUMBER, "10px" to NUMBER, "!important" to KEYWORD, "/* c */" to COMMENT,
            "@media" to KEYWORD, "p" to TAG, "top" to KEY, "-1.5em" to NUMBER,
        )
        assertEquals(listOf(FoldRegion(2, 4, false)), document.folds)
    }

    @Test
    fun `css words in values are strings`() {
        val tokens = CssHighlighter.highlight("a {\n  font: bold \"Inter\", sans-serif;\n}").tokens
        assertContains(tokens, "font" to KEY, "bold" to STRING, "\"Inter\"" to STRING, "," to PUNCTUATION, "sans-serif" to STRING)
    }

    @Test
    fun `javascript keywords strings numbers and comments get their kinds`() {
        val js = "// hi\nconst x = { a: \"s\", b: 42, c: 'q', d: `t\${1}` };\nfunction f() {\n  return x /* c */;\n}"
        val document = JavaScriptHighlighter.highlight(js)
        assertEquals(js.lines(), document.texts)
        assertContains(
            document.tokens,
            "// hi" to COMMENT, "const" to KEYWORD, "\"s\"" to STRING, "42" to NUMBER, "'q'" to STRING,
            "`t\${1}`" to STRING, "function" to KEYWORD, "return" to KEYWORD, "/* c */" to COMMENT, "{" to PUNCTUATION,
        )
        assertEquals(listOf(FoldRegion(2, 4, false)), document.folds)
    }

    @Test
    fun `javascript template strings and comments may span lines`() {
        val document = JavaScriptHighlighter.highlight("let a = `one\ntwo`\n/* x\ny */ 0x1F 1e-3")
        assertEquals(listOf("let a = `one", "two`", "/* x", "y */ 0x1F 1e-3"), document.texts)
        assertContains(document.tokens, "`one" to STRING, "two`" to STRING, "y */" to COMMENT, "0x1F" to NUMBER, "1e-3" to NUMBER)
    }

    @Test
    fun `yaml keys values comments and dashes get their kinds`() {
        val yaml = "# config\nname: \"app\"   # quoted\ncount: 3\nenabled: true\nitems:\n  - one\n  - 2.5\n" +
            "script: |\n  echo hi\n  : not a key\nurl: http://x\nempty: ~\n"
        val document = YamlHighlighter.highlight(yaml)
        assertEquals(yaml.trimEnd().lines(), document.texts)
        assertContains(
            document.tokens,
            "# config" to COMMENT, "name" to KEY, ":" to PUNCTUATION, "\"app\"" to STRING, "# quoted" to COMMENT,
            "count" to KEY, "3" to NUMBER, "true" to KEYWORD, "items" to KEY, "-" to PUNCTUATION, "one" to STRING,
            "2.5" to NUMBER, "|" to PUNCTUATION, "  echo hi" to STRING, "  : not a key" to STRING,
            "http://x" to STRING, "~" to KEYWORD,
        )
    }

    @Test
    fun `markdown source gets headings emphasis links and code`() {
        val markdown = "# Title\nSome **bold** and _it_ and [link](http://x) and `code`.\n- item\n> quote\n```js\nlet a\n```\n---"
        val document = MarkdownHighlighter.highlight(markdown)
        assertEquals(markdown.lines(), document.texts)
        assertContains(
            document.tokens,
            "# Title" to HEADING, "**bold**" to EMPHASIS, "_it_" to EMPHASIS, "[link](http://x)" to LINK,
            "`code`" to STRING, "- " to PUNCTUATION, "> " to PUNCTUATION, "```js" to STRING, "let a" to STRING,
            "```" to STRING, "---" to PUNCTUATION,
        )
    }

    @Test
    fun `highlighters never throw on odd input`() {
        val samples = listOf("", "{", "}", "\"", "/*", "`", "a: 'b", "**", "[x](", "- ", "@", "#", "!", "1e", "\\")
        samples.forEach {
            CssHighlighter.highlight(it)
            JavaScriptHighlighter.highlight(it)
            YamlHighlighter.highlight(it)
            MarkdownHighlighter.highlight(it)
            MarkdownParser.parse(it)
        }
    }

    @Test
    fun `yaml nested list dashes are each marked`() {
        val document = YamlHighlighter.highlight("- - - x")

        assertEquals(listOf("- - - x"), document.texts)
        assertEquals(3, document.lines.single().spans.count { it.kind == TokenKind.PUNCTUATION && it.text == "-" })
    }

    @Test
    fun `markdown quote markers keep their spacing`() {
        val document = MarkdownHighlighter.highlight(">> > a")

        assertEquals(listOf(">> > a"), document.texts)
        assertEquals(CodeSpan(">> > ", TokenKind.PUNCTUATION), document.lines.single().spans.first())
    }
}
