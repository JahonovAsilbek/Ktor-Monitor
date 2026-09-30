package uz.jahonov.ktormonitor.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig

// An upgrade only happens to a database file an older version left behind; an in-memory one never
// has a version to upgrade from.
class KtorMonitorDatabaseUpgradeTest {

    private val path = NSTemporaryDirectory() + "ktormonitor-upgrade-test.db"

    @OptIn(ExperimentalForeignApi::class)
    @AfterTest
    fun delete() {
        listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
    }

    @Test
    fun `a database from an older version is started afresh`() = runTest {
        BundledSQLiteDriver().open(path).apply {
            execSQL("CREATE TABLE call (id TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL)")
            execSQL("INSERT INTO call VALUES ('old', 'https://api.test/old')")
            execSQL("PRAGMA user_version = 1")
            close()
        }

        val database = Room.databaseBuilder<KtorMonitorDatabase>(name = path).buildKtorMonitorDatabase()
        val repository = KtorMonitorRepository(database.calls(), KtorMonitorConfig()) { 0 }
        repository.insert(testCall("new"))

        assertEquals(listOf("new"), repository.calls().first().map { it.id })
        database.close()
    }
}
