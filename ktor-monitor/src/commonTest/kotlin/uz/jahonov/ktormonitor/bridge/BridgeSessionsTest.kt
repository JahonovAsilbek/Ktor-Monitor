package uz.jahonov.ktormonitor.bridge

import uz.jahonov.ktormonitor.data.FakeCallDao
import uz.jahonov.ktormonitor.data.CallSummaryRow
import uz.jahonov.ktormonitor.data.CallDao
import kotlinx.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.jahonov.ktormonitor.capture.KtorMonitorConfig
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.data.fakeRepository
import uz.jahonov.ktormonitor.data.testCall
import uz.jahonov.ktormonitor.presentation.KtorMonitorNotifier
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailViewModel
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeSessionsTest {

    private val states = mutableListOf<String>()
    private val effects = mutableListOf<String>()
    private val errors = mutableListOf<Throwable>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun seeded(): KtorMonitorRepository = fakeRepository().apply {
        insert(testCall("cards", url = "https://api.test/cards?page=2", requestTime = 1))
        insert(testCall("login", url = "https://auth.test/login", method = "POST", requestTime = 2, responseCode = 401))
    }

    private fun sessions(repository: KtorMonitorRepository, showNotification: Boolean = true) = BridgeSessions(
        listViewModel = { KtorMonitorListViewModel(repository, "ExampleApp", "1.0", now = { 0 }, onError = { errors += it }) },
        detailViewModel = { KtorMonitorDetailViewModel(it, repository, "ExampleApp", "1.0", now = { 0 }, onError = { errors += it }, analysisDispatcher = Dispatchers.Main) },
        notifier = KtorMonitorNotifier(repository, KtorMonitorConfig().apply { this.showNotification = showNotification }),
        onInternalError = { errors += it },
    )

    private fun lastList() = BridgeJson.decodeFromString(ListStateWire.serializer(), states.last())

    private fun lastDetail() = BridgeJson.decodeFromString(DetailStateWire.serializer(), states.last())

    @Test
    fun `a list session sends each state as json`() = runTest {
        val bridge = sessions(seeded())

        bridge.openList(states::add, effects::add)
        advanceUntilIdle()

        val calls = lastList().calls.orEmpty()
        assertEquals(listOf("login", "cards"), calls.map { it.id })
        assertEquals("api.test", calls[1].host)
        assertEquals("/cards?page=2", calls[1].path)
        assertTrue(calls[0].isError)
    }

    @Test
    fun `events arrive as json`() = runTest {
        val bridge = sessions(seeded())
        val session = bridge.openList(states::add, effects::add)

        bridge.send(session, """{"type":"search","query":"login"}""")
        advanceUntilIdle()

        assertEquals(listOf("login"), lastList().calls?.map { it.id })
        assertTrue(lastList().isNarrowed)
    }

    @Test
    fun `a click comes back as an effect`() = runTest {
        val bridge = sessions(seeded())
        val session = bridge.openList(states::add, effects::add)
        advanceUntilIdle()

        bridge.send(session, """{"type":"click","id":"cards"}""")
        advanceUntilIdle()

        assertEquals(listOf("""{"type":"openCall","id":"cards"}"""), effects)
    }

    @Test
    fun `a call session sends the call with its headers`() = runTest {
        val bridge = sessions(seeded())

        bridge.openCall("cards", states::add, effects::add)
        advanceUntilIdle()

        val call = lastDetail().call
        assertEquals("cards", call?.summary?.id)
        assertEquals(listOf(HeaderWire("Content-Type", "application/json")), call?.responseHeaders)
        assertIs<ContentWire.Code>(lastDetail().response?.content)
    }

    @Test
    fun `a closed session sends nothing more`() = runTest {
        val repository = seeded()
        val bridge = sessions(repository)
        val session = bridge.openList(states::add, effects::add)
        advanceUntilIdle()
        val sent = states.size

        bridge.close(session)
        repository.insert(testCall("profile", requestTime = 3))
        advanceUntilIdle()

        assertEquals(sent, states.size)
    }

    @Test
    fun `an unreadable event goes to onInternalError`() = runTest {
        val bridge = sessions(seeded())
        val session = bridge.openList(states::add, effects::add)

        bridge.send(session, """{"type":"unknown"}""")
        bridge.send(session, "not json")

        assertEquals(2, errors.size)
    }

    @Test
    fun `the notification sends its title and lines`() = runTest {
        val bridge = sessions(seeded())
        val updates = mutableListOf<String>()

        bridge.observeNotification(updates::add)
        advanceUntilIdle()

        val last = BridgeJson.decodeFromString(NotificationWire.serializer(), updates.last())
        assertEquals(KtorMonitorNotifier.TITLE, last.title)
        assertEquals(listOf("401 POST /login", "200 GET /cards?page=2"), last.lines)
    }

    @Test
    fun `a notification that is off sends nothing`() = runTest {
        val bridge = sessions(seeded(), showNotification = false)
        val updates = mutableListOf<String>()

        bridge.observeNotification(updates::add)
        advanceUntilIdle()

        assertTrue(updates.isEmpty())
    }

    @Test
    fun `a database failure goes to onInternalError instead of crashing`() = runTest {
        val broken = object : CallDao by FakeCallDao() {
            override fun observeSummaries(query: String, limit: Int): Flow<List<CallSummaryRow>> =
                flow { throw IOException("file is not a database") }
        }
        val bridge = sessions(KtorMonitorRepository(broken, KtorMonitorConfig()) { 0 })

        bridge.openList(states::add, effects::add)
        bridge.observeNotification {}
        advanceUntilIdle()

        assertTrue(errors.isNotEmpty())
        assertTrue(errors.all { it.message == "file is not a database" })
    }
}
