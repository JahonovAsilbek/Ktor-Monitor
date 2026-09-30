package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals
import uz.jahonov.ktormonitor.body.TokenKind.KEY
import uz.jahonov.ktormonitor.body.TokenKind.PUNCTUATION
import uz.jahonov.ktormonitor.body.TokenKind.STRING

class FormFormatterTest {

    @Test
    fun `fields are url decoded`() {
        val document = FormFormatter.format("name=John+Doe&city=T%C3%A9st&flag&note=a%26b%3Dc")
        assertEquals(listOf("name = John Doe", "city = Tést", "flag = ", "note = a&b=c"), document.texts)
        assertEquals(
            listOf(CodeSpan("name", KEY), CodeSpan(" = ", PUNCTUATION), CodeSpan("John Doe", STRING)),
            document.lines[0].spans,
        )
        assertEquals(emptyList(), document.folds)
    }

    @Test
    fun `nested keys are grouped under their root`() {
        val document = FormFormatter.format("user%5Bname%5D=Ali&token=t&user.age=5&user[address][city]=X&items[]=1&items[]=2")
        assertEquals(
            listOf(
                "user",
                "  name = Ali",
                "  age = 5",
                "  address[city] = X",
                "token = t",
                "items",
                "  [] = 1",
                "  [] = 2",
            ),
            document.texts,
        )
        assertEquals(listOf(FoldRegion(0, 3, false), FoldRegion(5, 7, false)), document.folds)
        assertEquals(KEY, document.lines[0].spans.single().kind)
    }

    @Test
    fun `malformed escapes and line breaks stay visible`() {
        val document = FormFormatter.format("a=%E0%A4%A&b=%zz&c=x%0Ay&&")
        assertEquals(3, document.lines.size)
        assertEquals("b = %zz", document.texts[1])
        assertEquals("c = x\\ny", document.texts[2])
    }
}
