package uz.jahonov.ktormonitor.body

import kotlinx.io.bytestring.ByteString

/** Rows of 16 bytes: 8-digit offset, hex split 8 + 8, and printable ASCII with `.` for the rest. */
internal object HexDump {

    fun rows(bytes: ByteString): List<HexRow> = (0 until bytes.size step BYTES_PER_ROW).map { offset ->
        val hex = StringBuilder(ROW_WIDTH)
        val ascii = StringBuilder(BYTES_PER_ROW)
        for (column in 0 until BYTES_PER_ROW) {
            if (column > 0) hex.append(if (column == BYTES_PER_ROW / 2) "  " else " ")
            val index = offset + column
            if (index >= bytes.size) {
                hex.append("  ")
                continue
            }
            val byte = bytes[index].toInt() and 0xff
            hex.append(DIGITS[byte shr 4]).append(DIGITS[byte and 0xf])
            ascii.append(if (byte in 0x20..0x7e) byte.toChar() else '.')
        }
        HexRow(offset.toString(16).padStart(8, '0'), hex.toString(), ascii.toString())
    }
}

private const val BYTES_PER_ROW = 16
private const val ROW_WIDTH = BYTES_PER_ROW * 3
private const val DIGITS = "0123456789abcdef"
