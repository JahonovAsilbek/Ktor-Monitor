package uz.jahonov.ktormonitor.presentation

import app.cash.turbine.test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.data.fakeRepository
import uz.jahonov.ktormonitor.data.testCall
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.presentation.list.CallSort
import uz.jahonov.ktormonitor.presentation.list.DurationRange
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEffect
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEvent
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiState
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListViewModel
import uz.jahonov.ktormonitor.presentation.list.StatusClass

@OptIn(ExperimentalCoroutinesApi::class)
class KtorMonitorListViewModelTest {

    private val viewModels = TestViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private suspend fun seeded(): KtorMonitorRepository = fakeRepository().apply {
        insert(testCall("cards", url = "https://api.test/cards", requestTime = 1, responseCode = 200, responseTime = 51, responseBody = "12"))
        insert(testCall("login", url = "https://auth.test/login", method = "POST", requestTime = 2, responseCode = 401, responseTime = 1_502))
        insert(testCall("slow", url = "https://api.test/report", requestTime = 3, responseCode = 500, responseTime = 7_003, responseBody = "123456"))
        insert(testCall("offline", url = "https://api.test/profile", requestTime = 4, responseCode = null, error = "IOException"))
        insert(testCall("pending", url = "https://api.test/pending", requestTime = 5, responseCode = null, responseBody = null))
    }

    private fun viewModel(repository: KtorMonitorRepository) =
        viewModels.track(KtorMonitorListViewModel(repository, "ExampleApp", "1.2.3", now = { 1_727_700_000_000 }, onError = { throw it }, workDispatcher = Dispatchers.Main))

    private suspend fun KtorMonitorListViewModel.shownIds(until: (KtorMonitorListUiState) -> Boolean = { true }): List<String> =
        state.first { it.calls is Loadable.Ready && until(it) }.let { state ->
            (state.calls as Loadable.Ready).value.map { it.id }
        }

    @Test
    fun `all calls are shown newest first`() = runTest {
        val vm = viewModel(seeded())

        assertEquals(listOf("pending", "offline", "slow", "login", "cards"), vm.shownIds())
        assertEquals(5, vm.state.value.totalCount)
        assertEquals(listOf("api.test", "auth.test"), vm.state.value.options.hosts)
        assertEquals(listOf("GET", "POST"), vm.state.value.options.methods)
    }

    @Test
    fun `search finds calls by body text`() = runTest {
        val vm = viewModel(seeded())
        vm.shownIds()

        vm.onEvent(KtorMonitorListUiEvent.Search("123456"))

        assertEquals(listOf("slow"), vm.shownIds { it.query == "123456" && (it.calls as Loadable.Ready).value.size == 1 })
    }

    @Test
    fun `only errors keeps failures and error statuses`() = runTest {
        val vm = viewModel(seeded())

        vm.onEvent(KtorMonitorListUiEvent.ToggleOnlyErrors)

        assertEquals(listOf("offline", "slow", "login"), vm.shownIds { it.onlyErrors })
    }

    @Test
    fun `filters combine across kinds and match any value within one`() = runTest {
        val vm = viewModel(seeded())

        vm.onEvent(KtorMonitorListUiEvent.ToggleHost("api.test"))
        vm.onEvent(KtorMonitorListUiEvent.ToggleStatus(StatusClass.SUCCESS))
        vm.onEvent(KtorMonitorListUiEvent.ToggleStatus(StatusClass.SERVER_ERROR))
        assertEquals(listOf("slow", "cards"), vm.shownIds { it.filters.statuses.size == 2 })

        vm.onEvent(KtorMonitorListUiEvent.ToggleDuration(DurationRange.OVER_5_S))
        assertEquals(listOf("slow"), vm.shownIds { it.filters.durations.isNotEmpty() })

        vm.onEvent(KtorMonitorListUiEvent.ClearFilters)
        assertEquals(5, vm.shownIds { it.filters.isEmpty }.size)
    }

    @Test
    fun `hiding the search bar keeps the filters`() = runTest {
        val vm = viewModel(seeded())
        vm.onEvent(KtorMonitorListUiEvent.ToggleSearch)
        vm.onEvent(KtorMonitorListUiEvent.ToggleMethod("POST"))

        vm.onEvent(KtorMonitorListUiEvent.ToggleSearch)

        assertEquals(listOf("login"), vm.shownIds { !it.isSearchVisible && it.filters.methods.isNotEmpty() })
    }

    @Test
    fun `hiding the search bar drops its query`() = runTest {
        val vm = viewModel(seeded())
        vm.onEvent(KtorMonitorListUiEvent.ToggleSearch)
        vm.onEvent(KtorMonitorListUiEvent.Search("login"))
        // The query shows at once; the calls it finds follow the debounce.
        assertEquals(listOf("login"), vm.shownIds { (it.calls as? Loadable.Ready)?.value?.size == 1 })

        vm.onEvent(KtorMonitorListUiEvent.ToggleSearch)

        assertEquals(5, vm.shownIds { !it.isSearchVisible && it.query.isEmpty() }.size)
    }

    @Test
    fun `the failed filter keeps calls that ended with no status`() = runTest {
        val vm = viewModel(seeded())

        vm.onEvent(KtorMonitorListUiEvent.ToggleStatus(StatusClass.FAILED))

        assertEquals(listOf("offline"), vm.shownIds { it.filters.statuses.isNotEmpty() })
    }

    @Test
    fun `sorting by size puts calls without a size last`() = runTest {
        val vm = viewModel(seeded())

        vm.onEvent(KtorMonitorListUiEvent.Sort(CallSort.SIZE_DESCENDING))
        assertEquals("slow", vm.shownIds { it.sort == CallSort.SIZE_DESCENDING }.first())
        assertEquals("pending", vm.shownIds { it.sort == CallSort.SIZE_DESCENDING }.last())

        vm.onEvent(KtorMonitorListUiEvent.Sort(CallSort.DURATION_ASCENDING))
        assertEquals(listOf("cards", "login", "slow"), vm.shownIds { it.sort == CallSort.DURATION_ASCENDING }.take(3))
    }

    @Test
    fun `a tap opens a call outside selection mode`() = runTest {
        val vm = viewModel(seeded())

        vm.effects.test {
            vm.onEvent(KtorMonitorListUiEvent.Click("cards"))
            assertEquals(KtorMonitorListUiEffect.OpenCall("cards"), awaitItem())
        }
    }

    @Test
    fun `selecting then deleting removes the chosen calls`() = runTest {
        val repository = seeded()
        val vm = viewModel(repository)
        vm.shownIds()

        vm.onEvent(KtorMonitorListUiEvent.LongClick("cards"))
        vm.onEvent(KtorMonitorListUiEvent.Click("login"))
        assertEquals(setOf("cards", "login"), vm.state.first { it.selection?.size == 2 }.selection)

        vm.onEvent(KtorMonitorListUiEvent.DeleteSelected)

        assertEquals(listOf("pending", "offline", "slow"), vm.shownIds { it.totalCount == 3 })
        assertNull(vm.state.value.selection)
    }

    @Test
    fun `select all takes only the visible calls`() = runTest {
        val vm = viewModel(seeded())
        vm.onEvent(KtorMonitorListUiEvent.ToggleOnlyErrors)
        vm.shownIds { it.onlyErrors }

        vm.onEvent(KtorMonitorListUiEvent.StartSelection)
        vm.onEvent(KtorMonitorListUiEvent.SelectAll)

        assertEquals(setOf("offline", "slow", "login"), vm.state.first { it.selection?.size == 3 }.selection)
    }

    @Test
    fun `sharing the selection exports it and leaves selection mode`() = runTest {
        val vm = viewModel(seeded())
        vm.shownIds()
        vm.onEvent(KtorMonitorListUiEvent.LongClick("cards"))
        vm.state.first { it.selection?.size == 1 }

        vm.effects.test {
            vm.onEvent(KtorMonitorListUiEvent.ShareSelected(ExportFormat.URLS))
            val file = (awaitItem() as KtorMonitorListUiEffect.Share).file
            assertEquals("https://api.test/cards", file.content.trim())
            assertEquals("text/plain", file.mimeType)
            assertTrue(file.name.endsWith(".txt"))
        }
        assertNull(vm.state.first { it.selection == null }.selection)
    }

    @Test
    fun `clear all empties the history`() = runTest {
        val vm = viewModel(seeded())
        vm.shownIds()

        vm.onEvent(KtorMonitorListUiEvent.ClearAll)

        assertEquals(emptyList(), vm.shownIds { it.totalCount == 0 })
    }
}
