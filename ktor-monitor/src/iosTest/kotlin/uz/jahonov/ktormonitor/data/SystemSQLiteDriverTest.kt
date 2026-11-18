package uz.jahonov.ktormonitor.data

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemSQLiteDriverTest {

    private val connection = SystemSQLiteDriver().open(":memory:")

    @AfterTest
    fun close() = connection.close()

    @Test
    fun `every type comes back as it was bound`() {
        connection.execSQL("CREATE TABLE t (i INTEGER, f REAL, s TEXT, b BLOB, n TEXT)")
        connection.prepare("INSERT INTO t VALUES (?, ?, ?, ?, ?)").use {
            it.bindLong(1, Long.MAX_VALUE)
            it.bindDouble(2, 1.5)
            it.bindText(3, "Привет, 世界 👋")
            it.bindBlob(4, byteArrayOf(0, 1, -1))
            it.bindNull(5)
            assertFalse(it.step())
        }

        connection.prepare("SELECT * FROM t").use {
            assertTrue(it.step())
            assertEquals(listOf("i", "f", "s", "b", "n"), it.getColumnNames())
            assertEquals(
                listOf(SQLITE_DATA_INTEGER, SQLITE_DATA_FLOAT, SQLITE_DATA_TEXT, SQLITE_DATA_BLOB, SQLITE_DATA_NULL),
                List(5) { index -> it.getColumnType(index) },
            )
            assertEquals(Long.MAX_VALUE, it.getLong(0))
            assertEquals(1.5, it.getDouble(1))
            assertEquals("Привет, 世界 👋", it.getText(2))
            assertContentEquals(byteArrayOf(0, 1, -1), it.getBlob(3))
            assertTrue(it.isNull(4))
            assertFalse(it.step())
        }
    }

    @Test
    fun `an empty text and blob stay empty`() {
        connection.prepare("SELECT ?, ?").use {
            it.bindText(1, "")
            it.bindBlob(2, ByteArray(0))
            assertTrue(it.step())
            assertEquals("", it.getText(0))
            assertContentEquals(ByteArray(0), it.getBlob(1))
        }
    }

    @Test
    fun `a transaction shows while it is open`() {
        assertFalse(connection.inTransaction())
        connection.execSQL("BEGIN")
        assertTrue(connection.inTransaction())
        connection.execSQL("COMMIT")
        assertFalse(connection.inTransaction())
    }

    @Test
    fun `bad SQL throws with the database's message`() {
        val error = assertFailsWith<SQLiteException> { connection.prepare("SELECT FROM nowhere") }
        assertTrue("syntax error" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a failed step and a missing row throw`() {
        connection.execSQL("CREATE TABLE u (id INTEGER PRIMARY KEY)")
        connection.execSQL("INSERT INTO u VALUES (1)")
        assertFailsWith<SQLiteException> { connection.execSQL("INSERT INTO u VALUES (1)") }

        connection.prepare("SELECT id FROM u").use {
            assertFailsWith<SQLiteException> { it.getLong(0) }
            assertTrue(it.step())
            assertFailsWith<SQLiteException> { it.getLong(1) }
        }
    }
}
