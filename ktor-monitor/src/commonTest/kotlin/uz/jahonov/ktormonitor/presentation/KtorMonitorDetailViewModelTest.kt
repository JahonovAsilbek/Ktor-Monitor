package uz.jahonov.ktormonitor.presentation

import app.cash.turbine.test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import uz.jahonov.ktormonitor.body.BodyMode
import uz.jahonov.ktormonitor.data.KtorMonitorRepository
import uz.jahonov.ktormonitor.data.fakeRepository
import uz.jahonov.ktormonitor.data.testCall
import uz.jahonov.ktormonitor.export.ExportFormat
import uz.jahonov.ktormonitor.presentation.detail.BodyContent
import uz.jahonov.ktormonitor.presentation.detail.BodySide
import uz.jahonov.ktormonitor.presentation.detail.CopyFormat
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEffect
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEvent
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiState
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class KtorMonitorDetailViewModelTest {

    private val viewModels = TestViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: KtorMonitorRepository, id: String = "cards") = viewModels.track(
        KtorMonitorDetailViewModel(id, repository, "ExampleApp", "1.2.3", now = { 0 }, onError = { throw it }, analysisDispatcher = Dispatchers.Main),
    )

    private suspend fun KtorMonitorDetailViewModel.ready(until: (KtorMonitorDetailUiState) -> Boolean = { true }) =
        state.first { it.call is Loadable.Ready && until(it) }

    @Test
    fun `a json response opens in the code view`() = runTest {
        val repository = fakeRepository().apply { insert(testCall("cards", responseBody = """{"id":1}""")) }

        val state = viewModel(repository).ready()

        val response = state.response!!
        assertEquals(BodyMode.CODE, response.mode)
        assertTrue(BodyMode.HEX in response.modes)
        assertIs<BodyContent.Code>(response.content)
        assertNull(state.request)
    }

    @Test
    fun `another view is kept once chosen`() = runTest {
        val repository = fakeRepository().apply { insert(testCall("cards", responseBody = """{"id":1}""")) }
        val vm = viewModel(repository)
        vm.ready()

        vm.onEvent(KtorMonitorDetailUiEvent.SelectMode(BodySide.RESPONSE, BodyMode.HEX))

        assertIs<BodyContent.Hex>(vm.ready { it.response?.mode == BodyMode.HEX }.response!!.content)
    }

    @Test
    fun `a call in flight updates in place`() = runTest {
        val repository = fakeRepository().apply { insert(testCall("cards", responseCode = null, responseBody = null)) }
        val vm = viewModel(repository)
        assertTrue((vm.ready().call as Loadable.Ready).value.isInProgress)

        repository.insert(testCall("cards", responseCode = 200))

        assertEquals(200, (vm.ready { (it.call as Loadable.Ready).value.responseCode != null }.call as Loadable.Ready).value.responseCode)
    }

    @Test
    fun `copy actions hand the text to the platform`() = runTest {
        val repository = fakeRepository().apply { insert(testCall("cards", responseBody = """{"id":1}""")) }
        val vm = viewModel(repository)
        vm.ready()

        vm.effects.test {
            vm.onEvent(KtorMonitorDetailUiEvent.Copy(CopyFormat.URL))
            assertEquals(KtorMonitorDetailUiEffect.CopyText("https://api.test/cards"), awaitItem())

            vm.onEvent(KtorMonitorDetailUiEvent.Copy(CopyFormat.CURL))
            assertTrue((awaitItem() as KtorMonitorDetailUiEffect.CopyText).text.startsWith("curl"))

            vm.onEvent(KtorMonitorDetailUiEvent.CopyHeaders(BodySide.RESPONSE))
            assertEquals(KtorMonitorDetailUiEffect.CopyText("Content-Type: application/json"), awaitItem())

            vm.onEvent(KtorMonitorDetailUiEvent.CopyBody(BodySide.RESPONSE))
            assertTrue("\"id\"" in (awaitItem() as KtorMonitorDetailUiEffect.CopyText).text)
        }
    }

    @Test
    fun `sharing as har gives a har file`() = runTest {
        val repository = fakeRepository().apply { insert(testCall("cards")) }
        val vm = viewModel(repository)
        vm.ready()

        vm.effects.test {
            vm.onEvent(KtorMonitorDetailUiEvent.Share(ExportFormat.HAR))
            val file = (awaitItem() as KtorMonitorDetailUiEffect.Share).file
            assertTrue(file.name.endsWith(".har"))
            assertTrue("\"log\"" in file.content)
        }
    }

    @Test
    fun `a deleted call closes the screen`() = runTest {
        val repository = fakeRepository().apply { insert(testCall("cards")) }
        val vm = viewModel(repository)
        vm.ready()

        vm.effects.test {
            repository.clear()
            assertEquals(KtorMonitorDetailUiEffect.Close, awaitItem())
        }
    }
}
