package uz.jahonov.ktormonitor.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.throwSQLiteException
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.utf16
import kotlinx.cinterop.value
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_bind_blob
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_bind_double
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_bind_int64
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_bind_null
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_bind_text16
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_clear_bindings
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_close_v2
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_blob
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_bytes
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_count
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_double
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_int64
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_name16
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_text16
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_column_type
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_errcode
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_errmsg16
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_extended_result_codes
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_finalize
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_get_autocommit
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_open_v2
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_prepare16_v2
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_reset
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_step
import uz.jahonov.ktormonitor.data.sqlite3.sqlite3_stmt_busy

private const val SQLITE_OK = 0
private const val SQLITE_NOMEM = 7
private const val SQLITE_MISUSE = 21
private const val SQLITE_RANGE = 25
private const val SQLITE_ROW = 100
private const val SQLITE_DONE = 101
private const val SQLITE_NULL = 5
private const val SQLITE_OPEN_READWRITE = 0x02
private const val SQLITE_OPEN_CREATE = 0x04

// SQLITE_TRANSIENT, ((sqlite3_destructor_type)-1): sqlite copies the value before the call returns.
@OptIn(ExperimentalForeignApi::class)
private val SQLITE_TRANSIENT = (-1L).toCPointer<CFunction<(COpaquePointer?) -> Unit>>()

/**
 * Room's driver on iOS, over the sqlite3 the app links. It does what androidx's NativeSQLiteDriver
 * does, minus extensions, and with the same errors; that one cannot be used because it references
 * sqlite3_load_extension, which Apple's libsqlite3 lacks.
 */
@OptIn(ExperimentalForeignApi::class)
internal class SystemSQLiteDriver : SQLiteDriver {

    override fun open(fileName: String): SQLiteConnection = memScoped {
        val db = allocPointerTo<sqlite3>()
        val resultCode = sqlite3_open_v2(fileName, db.ptr, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE, null)
        val pointer = db.value
        if (resultCode != SQLITE_OK) {
            // Even a failed open usually leaves a handle, which holds the message and must be closed.
            val message = pointer?.errorMessage()
            sqlite3_close_v2(pointer)
            throwSQLiteException(resultCode, message)
        }
        sqlite3_extended_result_codes(pointer, 1)
        SystemSQLiteConnection(pointer!!)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class SystemSQLiteConnection(private val db: CPointer<sqlite3>) : SQLiteConnection {

    private var isClosed = false

    override fun inTransaction(): Boolean {
        throwIfClosed()
        return sqlite3_get_autocommit(db) == 0
    }

    override fun prepare(sql: String): SQLiteStatement = memScoped {
        throwIfClosed()
        val statement = allocPointerTo<sqlite3_stmt>()
        val sqlUtf16 = sql.utf16
        val resultCode = sqlite3_prepare16_v2(db, sqlUtf16, sqlUtf16.size, statement.ptr, null)
        if (resultCode != SQLITE_OK) throwSQLiteException(resultCode, db.errorMessage())
        // Blank SQL, or only a comment, compiles to no statement at all.
        val pointer = statement.value ?: throwSQLiteException(SQLITE_MISUSE, "no statement in: $sql")
        SystemSQLiteStatement(db, pointer)
    }

    override fun close() {
        if (!isClosed) sqlite3_close_v2(db)
        isClosed = true
    }

    private fun throwIfClosed() {
        if (isClosed) throwSQLiteException(SQLITE_MISUSE, "connection is closed")
    }
}

@OptIn(ExperimentalForeignApi::class)
private class SystemSQLiteStatement(
    private val db: CPointer<sqlite3>,
    private val statement: CPointer<sqlite3_stmt>,
) : SQLiteStatement {

    private var isClosed = false

    override fun bindBlob(index: Int, value: ByteArray) =
        ensureOk(sqlite3_bind_blob(handle(), index, value.toCValues(), value.size, SQLITE_TRANSIENT))

    override fun bindDouble(index: Int, value: Double) = ensureOk(sqlite3_bind_double(handle(), index, value))

    override fun bindLong(index: Int, value: Long) = ensureOk(sqlite3_bind_int64(handle(), index, value))

    override fun bindText(index: Int, value: String) {
        // utf16 ends with a terminating zero, which is not part of the text.
        val utf16 = value.utf16
        ensureOk(sqlite3_bind_text16(handle(), index, utf16, utf16.size - 2, SQLITE_TRANSIENT))
    }

    override fun bindNull(index: Int) = ensureOk(sqlite3_bind_null(handle(), index))

    override fun getBlob(index: Int): ByteArray {
        val blob = sqlite3_column_blob(row(index), index)
        val size = sqlite3_column_bytes(statement, index)
        if (blob == null || size == 0) {
            throwIfOutOfMemory()
            return ByteArray(0)
        }
        return blob.readBytes(size)
    }

    override fun getDouble(index: Int): Double = sqlite3_column_double(row(index), index)

    override fun getLong(index: Int): Long = sqlite3_column_int64(row(index), index)

    override fun getText(index: Int): String {
        val text = sqlite3_column_text16(row(index), index)
        if (text == null) {
            throwIfOutOfMemory()
            return ""
        }
        return text.reinterpret<UShortVar>().toKStringFromUtf16()
    }

    override fun isNull(index: Int): Boolean = getColumnType(index) == SQLITE_NULL

    override fun getColumnType(index: Int): Int = sqlite3_column_type(row(index), index)

    override fun getColumnCount(): Int = sqlite3_column_count(handle())

    override fun getColumnName(index: Int): String {
        throwIfInvalidColumn(index)
        val name = sqlite3_column_name16(statement, index) ?: throw OutOfMemoryError()
        return name.reinterpret<UShortVar>().toKStringFromUtf16()
    }

    override fun step(): Boolean =
        when (val resultCode = sqlite3_step(handle())) {
            SQLITE_ROW -> true
            SQLITE_DONE -> false
            else -> throwSQLiteException(resultCode, db.errorMessage())
        }

    override fun reset() = ensureOk(sqlite3_reset(handle()))

    override fun clearBindings() = ensureOk(sqlite3_clear_bindings(handle()))

    override fun close() {
        if (!isClosed) sqlite3_finalize(statement)
        isClosed = true
    }

    private fun handle(): CPointer<sqlite3_stmt> {
        if (isClosed) throwSQLiteException(SQLITE_MISUSE, "statement is closed")
        return statement
    }

    /** The statement, once it is known to be on a row that has this column. */
    private fun row(index: Int): CPointer<sqlite3_stmt> {
        if (sqlite3_stmt_busy(handle()) == 0) throwSQLiteException(SQLITE_MISUSE, "no row")
        throwIfInvalidColumn(index)
        return statement
    }

    private fun throwIfInvalidColumn(index: Int) {
        if (index < 0 || index >= getColumnCount()) throwSQLiteException(SQLITE_RANGE, "column index out of range")
    }

    private fun throwIfOutOfMemory() {
        if (sqlite3_errcode(db) == SQLITE_NOMEM) throw OutOfMemoryError()
    }

    private fun ensureOk(resultCode: Int) {
        if (resultCode != SQLITE_OK) throwSQLiteException(resultCode, db.errorMessage())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<sqlite3>.errorMessage(): String? =
    sqlite3_errmsg16(this)?.reinterpret<UShortVar>()?.toKStringFromUtf16()
