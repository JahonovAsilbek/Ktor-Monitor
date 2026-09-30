package uz.jahonov.ktormonitor.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uz.jahonov.ktormonitor.model.NetworkCall
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.presentation.detail.BodySide
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEffect
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiEvent
import uz.jahonov.ktormonitor.presentation.detail.KtorMonitorDetailUiState
import uz.jahonov.ktormonitor.ui.copyToClipboard
import uz.jahonov.ktormonitor.ui.monitorViewModel
import uz.jahonov.ktormonitor.ui.share
import uz.jahonov.ktormonitor.ui.ui.CollectEffects
import uz.jahonov.ktormonitor.ui.ui.MonitorIconButton
import uz.jahonov.ktormonitor.ui.ui.MonitorIcons
import uz.jahonov.ktormonitor.ui.ui.MonitorTabs
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.MonitorToolbar
import uz.jahonov.ktormonitor.ui.ui.Spinner

/**
 * [onBack] is null in the two-pane layout, where the list stays beside the detail. [onGone] runs when
 * the call is deleted while shown.
 */
@Composable
internal fun KtorMonitorDetailScreen(
    callId: String,
    onBack: (() -> Unit)?,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = monitorViewModel(key = callId) { detailViewModel(callId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is KtorMonitorDetailUiEffect.CopyText -> context.copyToClipboard(effect.text)
            is KtorMonitorDetailUiEffect.Share -> context.share(effect.file)
            // In two panes the list drops the deleted row itself; the detail keeps its last state.
            KtorMonitorDetailUiEffect.Close -> onGone()
        }
    }
    KtorMonitorDetailContent(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
internal fun KtorMonitorDetailContent(
    state: KtorMonitorDetailUiState,
    onEvent: (KtorMonitorDetailUiEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val call = (state.call as? Loadable.Ready)?.value

    Column(modifier.fillMaxSize().background(MonitorTheme.colors.background)) {
        MonitorToolbar(
            title = call?.let { "${it.method} ${it.summary.path}" }.orEmpty(),
            subtitle = call?.summary?.host,
            navigation = onBack?.let { back -> { MonitorIconButton(MonitorIcons.ChevronLeft, "Back", back) } },
            action = call?.let { { MonitorIconButton(MonitorIcons.More, "Copy or share", { menuOpen = true }) } },
        )
        when (val loaded = state.call) {
            Loadable.Loading -> Centered { Spinner() }
            is Loadable.Ready -> Pages(loaded.value, state, onEvent)
        }
    }

    if (menuOpen && call != null) ShareMenu(onEvent = onEvent, onDismiss = { menuOpen = false })
}

@Composable
private fun Pages(call: NetworkCall, state: KtorMonitorDetailUiState, onEvent: (KtorMonitorDetailUiEvent) -> Unit) {
    val pager = rememberPagerState { PAGE_TITLES.size }
    val scope = rememberCoroutineScope()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + bottomInset)

    MonitorTabs(
        tabs = PAGE_TITLES,
        selected = pager.currentPage,
        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        when (page) {
            SUMMARY -> SummaryPage(call, contentPadding)
            REQUEST -> MessagePage(call, BodySide.REQUEST, state.request, onEvent, contentPadding)
            else -> MessagePage(call, BodySide.RESPONSE, state.response, onEvent, contentPadding)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { content() }
}

private val PAGE_TITLES = listOf("Summary", "Request", "Response")
private const val SUMMARY = 0
private const val REQUEST = 1

@Preview
@Composable
private fun DetailPreview() {
    MonitorTheme {
        KtorMonitorDetailContent(DetailSamples.state, onEvent = {}, onBack = {})
    }
}

@Preview
@Composable
private fun DetailDarkPreview() {
    MonitorTheme(darkTheme = true) {
        KtorMonitorDetailContent(DetailSamples.state, onEvent = {}, onBack = {})
    }
}

@Preview
@Composable
private fun DetailInProgressPreview() {
    val waiting = DetailSamples.call.copy(responseCode = null, responseTime = null, responseHeaders = emptyMap(), responseBody = null)
    MonitorTheme {
        KtorMonitorDetailContent(KtorMonitorDetailUiState(Loadable.Ready(waiting)), onEvent = {}, onBack = null)
    }
}

@Preview
@Composable
private fun DetailLoadingPreview() {
    MonitorTheme(darkTheme = true) {
        KtorMonitorDetailContent(KtorMonitorDetailUiState(), onEvent = {}, onBack = {})
    }
}
