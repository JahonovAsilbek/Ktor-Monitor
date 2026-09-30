package uz.jahonov.ktormonitor.body

import kotlin.test.Test
import kotlin.test.assertEquals

class HexDumpTest {

    @Test
    fun `rows hold sixteen bytes with offset hex and ascii`() {
        val bytes = "Hi!".encodeToByteArray() + ByteArray(13) { (it + 0x7d).toByte() } + bytesOf(0x41)
        val rows = BodyAnalysis(body(bytes), null).hex
        assertEquals(2, rows.size)
        assertEquals(
            HexRow("00000000", "48 69 21 7d 7e 7f 80 81  82 83 84 85 86 87 88 89", "Hi!}~..........."),
            rows[0],
        )
        assertEquals("00000010", rows[1].offset)
        assertEquals("41".padEnd(48), rows[1].hex)
        assertEquals("A", rows[1].ascii)
    }

    @Test
    fun `offsets count in hex across the whole body`() {
        val rows = BodyAnalysis(body(ByteArray(4_096)), null).hex
        assertEquals(256, rows.size)
        assertEquals("00000ff0", rows.last().offset)
        assertEquals("00 00 00 00 00 00 00 00  00 00 00 00 00 00 00 00", rows.last().hex)
        assertEquals(".".repeat(16), rows.last().ascii)
    }

    @Test
    fun `an empty body has no rows`() {
        assertEquals(emptyList(), BodyAnalysis(body(ByteArray(0)), null).hex)
    }
}
