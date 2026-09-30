package uz.jahonov.ktormonitor.ui.list

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.jahonov.ktormonitor.model.CallSummary
import uz.jahonov.ktormonitor.presentation.Loadable
import uz.jahonov.ktormonitor.presentation.list.CallFilters
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEffect
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiEvent
import uz.jahonov.ktormonitor.presentation.list.KtorMonitorListUiState
import uz.jahonov.ktormonitor.ui.KtorMonitorNotifications
import uz.jahonov.ktormonitor.ui.LocalKtorMonitor
import uz.jahonov.ktormonitor.ui.monitorViewModel
import uz.jahonov.ktormonitor.ui.share
import uz.jahonov.ktormonitor.ui.ui.CollectEffects
import uz.jahonov.ktormonitor.ui.ui.MonitorTheme
import uz.jahonov.ktormonitor.ui.ui.Spinner
import uz.jahonov.ktormonitor.ui.ui.TextAction

@Composable
internal fun KtorMonitorListScreen(
    selectedCallId: String?,
    onOpenCall: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = monitorViewModel { listViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CollectEffects(vm.effects) { effect ->
        when (effect) {
            is KtorMonitorListUiEffect.OpenCall -> onOpenCall(effect.id)
            is KtorMonitorListUiEffect.Share -> context.share(effect.file)
        }
    }
    BackHandler(enabled = state.isSelecting) { vm.onEvent(KtorMonitorListUiEvent.ExitSelection) }

    KtorMonitorListContent(
        state = state,
        onEvent = vm::onEvent,
        selectedCallId = selectedCallId,
        onClose = onClose,
        notificationBanner = { NotificationPermissionBanner() },
        modifier = modifier,
    )
}

private enum class ListSheet { FILTERS, MENU, SORT, SHARE }

@Composable
private fun KtorMonitorListContent(
    state: KtorMonitorListUiState,
    onEvent: (KtorMonitorListUiEvent) -> Unit,
    selectedCallId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    notificationBanner: @Composable () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf<ListSheet?>(null) }
    val calls = (state.calls as? Loadable.Ready)?.value.orEmpty()
    val selection = state.selection

    Column(
        modifier
            .fillMaxSize()
            .background(MonitorTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
        if (selection != null) {
            SelectionBar(
                selectedCount = selection.size,
                visibleCount = calls.size,
                allVisibleSelected = calls.all { it.id in selection },
                onEvent = onEvent,
                onShare = { sheet = ListSheet.SHARE },
            )
        } else {
            ListToolbar(
                state = state,
                onEvent = onEvent,
                onClose = onClose,
                onOpenFilters = { sheet = ListSheet.FILTERS },
                onOpenMenu = { sheet = ListSheet.MENU },
            )
        }
        if (state.isSearchVisible) SearchField(state.query, { onEvent(KtorMonitorListUiEvent.Search(it)) })
        if (!state.filters.isEmpty) ActiveFilters(state, onEvent)
        notificationBanner()

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val loaded = state.calls) {
                Loadable.Loading -> Spinner(Modifier.align(Alignment.Center))
                is Loadable.Ready -> if (loaded.value.isEmpty()) {
                    Message(if (state.isNarrowed) "Nothing matches" else "No calls yet", Modifier.align(Alignment.Center))
                } else {
                    CallList(loaded.value, selectedCallId, selection, onEvent)
                }
            }
        }
    }

    when (sheet) {
        ListSheet.FILTERS -> FiltersSheet(state, onEvent, onDismiss = { sheet = null })
        ListSheet.MENU -> MenuSheet(
            sort = state.sort,
            hasCalls = state.totalCount > 0,
            onAction = { action ->
                when (action) {
                    MenuAction.SELECT -> onEvent(KtorMonitorListUiEvent.StartSelection)
                    MenuAction.SORT -> sheet = ListSheet.SORT
                    MenuAction.CLEAR_ALL -> onEvent(KtorMonitorListUiEvent.ClearAll)
                }
            },
            onDismiss = { sheet = null },
        )
        ListSheet.SORT -> SortSheet(state.sort, { onEvent(KtorMonitorListUiEvent.Sort(it)) }, onDismiss = { sheet = null })
        ListSheet.SHARE -> ShareSheet({ onEvent(KtorMonitorListUiEvent.ShareSelected(it)) }, onDismiss = { sheet = null })
        null -> Unit
    }
}

/** Whether search, filters or "only errors" hide some of the calls. */
private val KtorMonitorListUiState.isNarrowed: Boolean
    get() = query.isNotBlank() || onlyErrors || !filters.isEmpty

@Composable
private fun CallList(
    calls: List<CallSummary>,
    selectedCallId: String?,
    selection: Set<String>?,
    onEvent: (KtorMonitorListUiEvent) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        items(calls, key = { it.id }) { call ->
            CallRow(
                call = call,
                highlighted = call.id == selectedCallId,
                selected = selection?.let { call.id in it },
                onClick = { onEvent(KtorMonitorListUiEvent.Click(call.id)) },
                onLongClick = { onEvent(KtorMonitorListUiEvent.LongClick(call.id)) },
            )
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(MonitorTheme.colors.border))
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = MonitorTheme.typography.subtitle.copy(color = MonitorTheme.colors.textSecondary, textAlign = TextAlign.Center),
        modifier = modifier.padding(24.dp),
    )
}

/**
 * Asks for the notification permission on Android 13 and later, where the monitor's notification
 * needs it. Rechecked on every resume, since it can be granted in the system settings meanwhile.
 * Once the system stops showing the prompt, the button opens the app's notification settings.
 */
@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val enabled = LocalKtorMonitor.current.notifier.isEnabled
    if (!enabled) return

    val context = LocalContext.current
    val activity = LocalActivity.current
    var allowed by remember { mutableStateOf(KtorMonitorNotifications.canPost(context)) }
    var deniedForGood by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        deniedForGood = !granted && activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
    }
    LifecycleResumeEffect(Unit) {
        allowed = KtorMonitorNotifications.canPost(context)
        onPauseOrDispose { }
    }
    if (allowed) return

    NotificationBanner(
        onAllow = {
            if (deniedForGood) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .setData("package:${context.packageName}".toUri()),
                )
            } else {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

@Composable
private fun NotificationBanner(onAllow: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MonitorTheme.colors.warningContainer, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            "Allow notifications to see calls in the notification shade",
            style = MonitorTheme.typography.body.copy(color = MonitorTheme.colors.text),
            modifier = Modifier.weight(1f),
        )
        TextAction("Allow", onAllow)
    }
}

@Preview
@Composable
private fun KtorMonitorListPreview() {
    MonitorTheme {
        KtorMonitorListContent(
            state = KtorMonitorListUiState(
                calls = Loadable.Ready(PreviewCalls),
                totalCount = PreviewCalls.size,
                isSearchVisible = true,
                filters = CallFilters(methods = setOf("GET")),
            ),
            onEvent = {},
            selectedCallId = "1",
            onClose = {},
            notificationBanner = { NotificationBanner(onAllow = {}) },
        )
    }
}

@Preview
@Composable
private fun KtorMonitorListSelectingPreview() {
    MonitorTheme(darkTheme = true) {
        KtorMonitorListContent(
            state = KtorMonitorListUiState(
                calls = Loadable.Ready(PreviewCalls),
                totalCount = PreviewCalls.size,
                selection = setOf("2", "3"),
            ),
            onEvent = {},
            selectedCallId = null,
            onClose = {},
        )
    }
}
