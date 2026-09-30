package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals
import uz.jahonov.ktormonitor.body.MarkdownBlock.CodeBlock
import uz.jahonov.ktormonitor.body.MarkdownBlock.Heading
import uz.jahonov.ktormonitor.body.MarkdownBlock.ListItem
import uz.jahonov.ktormonitor.body.MarkdownBlock.Paragraph
import uz.jahonov.ktormonitor.body.MarkdownBlock.Quote
import uz.jahonov.ktormonitor.body.MarkdownBlock.Rule

class MarkdownParserTest {

    private fun text(value: String) = listOf(MarkdownSpan(value))

    @Test
    fun `blocks are parsed`() {
        val markdown = """
            # Head
            ### Third ###

            Para line one
            continues here

            - a
              - b
                1. c
            * d
            2) e

            > quote
            > more

            ```kotlin
            val x = 1

            val y = 2
            ```

            ---
            ***
        """.trimIndent()
        assertEquals(
            listOf(
                Heading(1, text("Head")),
                Heading(3, text("Third")),
                Paragraph(text("Para line one continues here")),
                ListItem(0, null, text("a")),
                ListItem(1, null, text("b")),
                ListItem(2, 1, text("c")),
                ListItem(0, null, text("d")),
                ListItem(0, 2, text("e")),
                Quote(text("quote more")),
                CodeBlock("kotlin", "val x = 1\n\nval y = 2"),
                Rule,
                Rule,
            ),
            MarkdownParser.parse(markdown),
        )
    }

    @Test
    fun `an unclosed fence runs to the end and has no language when bare`() {
        assertEquals(listOf(CodeBlock(null, "a\n# b")), MarkdownParser.parse("```\na\n# b"))
    }

    @Test
    fun `a heading keeps a hash that is part of a word`() {
        assertEquals(listOf(Heading(2, text("About C#"))), MarkdownParser.parse("## About C#"))
    }

    @Test
    fun `inline spans carry bold italic code and links`() {
        assertEquals(
            listOf(
                MarkdownSpan("Some "),
                MarkdownSpan("bold", isBold = true),
                MarkdownSpan(" and "),
                MarkdownSpan("it", isItalic = true),
                MarkdownSpan(" "),
                MarkdownSpan("too", isItalic = true),
                MarkdownSpan(" "),
                MarkdownSpan("x()", isCode = true),
                MarkdownSpan(" "),
                MarkdownSpan("site", link = "https://a.b"),
                MarkdownSpan(" and *lit* snake_case_name"),
            ),
            MarkdownParser.inline("Some **bold** and *it* _too_ `x()` [site](https://a.b \"title\") and \\*lit\\* snake_case_name"),
        )
    }

    @Test
    fun `styles nest`() {
        assertEquals(
            listOf(
                MarkdownSpan("a "),
                MarkdownSpan("b", isBold = true, isItalic = true),
                MarkdownSpan(" c", isBold = true),
                MarkdownSpan("link ", link = "https://u.test"),
                MarkdownSpan("bold", isBold = true, link = "https://u.test"),
            ),
            MarkdownParser.inline("a ***b* c**[link **bold**](https://u.test)"),
        )
    }

    @Test
    fun `unmatched markers stay text`() {
        assertEquals(text("2 * 3 and a ** b and [x] and [y]( and `z"), MarkdownParser.inline("2 * 3 and a ** b and [x] and [y]( and `z"))
    }

    @Test
    fun `an image is shown as a link with its alt text`() {
        assertEquals(listOf(MarkdownSpan("logo", link = "https://x.test/l.png")), MarkdownParser.inline("![logo](https://x.test/l.png)"))
    }

    @Test
    fun `many unmatched markers stay fast`() {
        val line = "*a _b [c ![d ".repeat(20_000)
        assertEquals(line, MarkdownParser.inline(line).joinToString("") { it.text })
    }

    @Test
    fun `only web and mail links stay links`() {
        val spans = MarkdownParser.inline("[a](https://x.test) [b](myapp://pay) [c](mailto:a@x.test) [d](#top)")

        assertEquals("a b c d", spans.joinToString("") { it.text })
        assertEquals(listOf("https://x.test", "mailto:a@x.test"), spans.mapNotNull { it.link })
    }
}
