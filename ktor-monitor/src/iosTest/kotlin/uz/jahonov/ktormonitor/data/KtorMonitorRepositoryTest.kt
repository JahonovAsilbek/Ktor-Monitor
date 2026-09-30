package uz.jahonov.ktormonitor.data

import androidx.room.Room
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.capture.Retention
import uz.jahonov.ktormonitor.model.CapturedBody
import uz.jahonov.ktormonitor.model.NetworkCall

// Room needs a real SQLite: these run on the simulator. The Android build uses the same Room code.
class KtorMonitorRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder<KtorMonitorDatabase>().buildKtorMonitorDatabase()
    private var clock = 10.hours.inWholeMilliseconds

    @AfterTest
    fun close() = database.close()

    private fun repository(configure: KtorMonitorConfig.() -> Unit = {}) =
        KtorMonitorRepository(database.calls(), KtorMonitorConfig().apply(configure)) { clock }

    private fun call(
        id: String,
        requestTime: Long = clock,
        url: String = "https://api.test/$id",
        responseBody: String? = null,
        responseCode: Int? = null,
    ) = NetworkCall(
        id = id,
        groupId = "g-$id",
        attempt = 1,
        method = "GET",
        url = url,
        requestTime = requestTime,
        requestHeaders = mapOf("Accept" to listOf("application/json", "text/plain")),
        requestContentType = null,
        requestBody = null,
        responseCode = responseCode,
        responseBody = responseBody?.let { CapturedBody(it.encodeToByteString(), it.length.toLong()) },
    )

    @Test
    fun `a call comes back as it was saved`() = runTest {
        val repository = repository()
        val saved = call("a", responseCode = 200).copy(
            responseHeaders = mapOf("Content-Type" to listOf("application/json")),
            responseBody = CapturedBody(ByteString(1, 2, 3), size = 10),
        )

        repository.upsert(saved)

        assertEquals(saved, repository.call("a").first())
        assertTrue(repository.call("a").first()!!.responseBody!!.isTruncated)
    }

    @Test
    fun `an update replaces the record`() = runTest {
        val repository = repository()

        repository.upsert(call("a"))
        repository.upsert(call("a", responseCode = 201))

        assertEquals(listOf(201), repository.calls().first().map { it.responseCode })
    }

    @Test
    fun `the list is newest first and carries sizes but no bodies`() = runTest {
        val repository = repository()
        repository.upsert(call("old", requestTime = clock - 2))
        repository.upsert(call("new", requestTime = clock - 1, responseBody = "12345", responseCode = 200))

        val calls = repository.calls().first()

        assertEquals(listOf("new", "old"), calls.map { it.id })
        assertEquals(5L, calls.first().responseSize)
    }

    @Test
    fun `search matches url method status and bodies in any case`() = runTest {
        val repository = repository()
        repository.upsert(call("cards", responseCode = 404))
        repository.upsert(call("user", responseBody = """{"name":"Ali"}""", responseCode = 200))

        assertEquals(listOf("cards"), repository.calls("CARDS").first().map { it.id })
        assertEquals(listOf("cards"), repository.calls("404").first().map { it.id })
        assertEquals(listOf("user"), repository.calls("ali").first().map { it.id })
        assertEquals(2, repository.calls("get").first().size)
    }

    @Test
    fun `search treats like wildcards as plain characters`() = runTest {
        val repository = repository()
        repository.upsert(call("a", url = "https://api.test/100%_done"))
        repository.upsert(call("b", url = "https://api.test/100x"))

        assertEquals(listOf("a"), repository.calls("%_").first().map { it.id })
    }

    @Test
    fun `calls past the retention period are removed as new ones arrive`() = runTest {
        val repository = repository { retention = Retention.OneHour }
        repository.upsert(call("stale", requestTime = clock - 61.minutes.inWholeMilliseconds, responseCode = 200))
        repository.upsert(call("recent", requestTime = clock - 59.minutes.inWholeMilliseconds, responseCode = 200))

        repository.upsert(call("new"))

        assertEquals(listOf("new", "recent"), repository.calls().first().map { it.id })
    }

    @Test
    fun `forever keeps old calls`() = runTest {
        val repository = repository { retention = Retention.Forever }
        repository.upsert(call("ancient", requestTime = 0, responseCode = 200))

        repository.upsert(call("new"))

        assertEquals(2, repository.calls().first().size)
    }

    @Test
    fun `only the newest max calls are kept`() = runTest {
        val repository = repository { maxCalls = 2 }

        (1..4).forEach { repository.upsert(call("c$it", requestTime = clock + it)) }

        assertEquals(listOf("c4", "c3"), repository.calls().first().map { it.id })
    }

    @Test
    fun `selected calls can be read and deleted and all can be cleared`() = runTest {
        val repository = repository()
        listOf("a", "b", "c").forEach { repository.upsert(call(it)) }

        assertEquals(setOf("a", "c"), repository.calls(listOf("a", "c")).map { it.id }.toSet())
        repository.delete(listOf("a"))
        assertEquals(setOf("b", "c"), repository.calls().first().map { it.id }.toSet())

        repository.clear()
        assertTrue(repository.calls().first().isEmpty())
        assertNull(repository.call("b").first())
    }
}
