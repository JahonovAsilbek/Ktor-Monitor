package uz.jahonov.ktormonitor.presentation

import uz.jahonov.ktormonitor.data.testCall
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.data.CallDao
import uz.jahonov.ktormonitor.data.CallSummaryRow
import uz.jahonov.ktormonitor.data.FakeCallDao
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.data.fakeRepository

class KtorMonitorNotifierTest {

    @Test
    fun `an inactive monitor shows no notification`() {
        val notifier = KtorMonitorNotifier(fakeRepository(), KtorMonitorConfig().apply { isActive = false })

        assertFalse(notifier.isEnabled)
    }

    @Test
    fun `a database failure goes to onInternalError and ends the lines`() = runTest {
        val errors = mutableListOf<Throwable>()
        val config = KtorMonitorConfig().apply { onInternalError = { errors += it } }
        val broken = object : CallDao by FakeCallDao() {
            override fun observeSummaries(query: String, limit: Int): Flow<List<CallSummaryRow>> =
                flow { throw IOException("file is not a database") }
        }

        val lines = KtorMonitorNotifier(KtorMonitorRepository(broken, config) { 0 }, config).lines.toList()

        assertTrue(lines.isEmpty())
        assertEquals("file is not a database", errors.single().message)
    }

    @Test
    fun `the lines are the latest five calls marked by state without queries`() = runTest {
        val repository = fakeRepository()
        (1..4).forEach { repository.insert(testCall("c$it", url = "https://api.test/c$it?token=secret", requestTime = it.toLong())) }
        repository.insert(testCall("pending", requestTime = 5, responseCode = null))
        repository.insert(testCall("failed", requestTime = 6, responseCode = null, error = "IOException"))

        val lines = KtorMonitorNotifier(repository, KtorMonitorConfig()).lines.first()

        assertEquals(listOf("❌ GET /failed", "⏳ GET /pending", "200 GET /c4", "200 GET /c3", "200 GET /c2"), lines)
    }
}
