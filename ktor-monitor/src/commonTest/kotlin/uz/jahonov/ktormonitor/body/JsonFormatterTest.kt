package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import uz.jahonov.ktormonitor.body.TokenKind.KEY
import uz.jahonov.ktormonitor.body.TokenKind.KEYWORD
import uz.jahonov.ktormonitor.body.TokenKind.NUMBER
import uz.jahonov.ktormonitor.body.TokenKind.PLAIN
import uz.jahonov.ktormonitor.body.TokenKind.PUNCTUATION
import uz.jahonov.ktormonitor.body.TokenKind.STRING

class JsonFormatterTest {

    private fun format(text: String) = assertNotNull(JsonFormatter.format(text), text)

    @Test
    fun `nested objects and arrays are indented by two spaces`() {
        val document = format("""{"a":1,"b":[true,null],"c":{},"d":{"e":-1.5e3}}""")
        assertEquals(
            listOf(
                "{",
                "  \"a\": 1,",
                "  \"b\": [",
                "    true,",
                "    null",
                "  ],",
                "  \"c\": {},",
                "  \"d\": {",
                "    \"e\": -1.5e3",
                "  }",
                "}",
            ),
            document.texts,
        )
    }

    @Test
    fun `every multi line container folds expanded`() {
        val document = format("""{"a":[1,2],"b":[],"c":{"d":{"e":0}}}""")
        assertEquals(
            listOf(
                FoldRegion(0, 11, false),
                FoldRegion(1, 4, false),
                FoldRegion(6, 10, false),
                FoldRegion(7, 9, false),
            ),
            document.folds,
        )
    }

    @Test
    fun `tokens get their kinds`() {
        val line = format("""{"a" : 1, "b": "x", "c": false}""").lines[1]
        assertEquals(
            listOf(
                CodeSpan("  ", PLAIN),
                CodeSpan("\"a\"", KEY),
                CodeSpan(":", PUNCTUATION),
                CodeSpan(" ", PLAIN),
                CodeSpan("1", NUMBER),
                CodeSpan(",", PUNCTUATION),
            ),
            line.spans,
        )
        val tokens = format("""{"b": "x", "c": false}""").tokens
        assertEquals(("\"x\"" to STRING), tokens[3])
        assertEquals(("false" to KEYWORD), tokens[7])
    }

    @Test
    fun `strings keep their escapes and unicode as written`() {
        val document = format("""["q\"uote\\","\u00e9\n","привет 👋"]""")
        assertEquals(
            listOf("[", "  \"q\\\"uote\\\\\",", "  \"\\u00e9\\n\",", "  \"привет 👋\"", "]"),
            document.texts,
        )
    }

    @Test
    fun `a scalar and an empty container stay on one line`() {
        assertEquals(listOf("42"), format(" 42 ").texts)
        assertEquals(listOf("[]"), format("[ ]").texts)
        assertEquals(emptyList(), format("{}").folds)
    }

    @Test
    fun `invalid json gives null`() {
        listOf(
            "", "{", """{"a":}""", "[1,]", """{"a":1,}""", """{"a" 1}""", "tru", "\"abc", """{"a":1} x""",
            "01", "1.", "-", """["\x"]""", "[\"a\nb\"]", "{a:1}", "[1 2]",
        ).forEach { assertNull(JsonFormatter.format(it), it) }
    }

    @Test
    fun `deep nesting does not overflow`() {
        val depth = 2_000
        val document = format("[".repeat(depth) + "]".repeat(depth))
        assertEquals(depth * 2 - 1, document.lines.size)
    }
}
