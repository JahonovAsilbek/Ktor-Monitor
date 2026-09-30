import SwiftUI

/// The call list. `selectedCallId` marks the call the detail pane shows next to the list.
struct CallListView: View {
    let bridge: KtorMonitorUIBridge
    let selectedCallId: String?
    let onOpenCall: (String) -> Void
    let onClose: () -> Void

    @StateObject private var session: ListSession
    @State private var isFiltersOpen = false

    init(bridge: KtorMonitorUIBridge, selectedCallId: String?, onOpenCall: @escaping (String) -> Void, onClose: @escaping () -> Void) {
        self.bridge = bridge
        self.selectedCallId = selectedCallId
        self.onOpenCall = onOpenCall
        self.onClose = onClose
        _session = StateObject(wrappedValue: ListSession(listOf: bridge))
    }

    var body: some View {
        VStack(spacing: 0) {
            if let state = session.state {
                if let selection = state.selection {
                    SelectionControls(state: state, selection: selection, send: session.send)
                }
                if state.isSearchVisible {
                    CallSearchField(query: state.query) { session.send(.search(query: $0)) }
                }
                if state.filterCount > 0 {
                    ActiveFiltersRow(state: state, send: session.send)
                }
                NotificationBanner(isEnabled: bridge.isNotificationEnabled)
                content(state)
            } else {
                loading
            }
        }
        .background(MonitorColor.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbar }
        .sheet(isPresented: $isFiltersOpen) {
            if let state = session.state {
                FiltersSheet(state: state, send: session.send)
            }
        }
        .onReceive(session.effects) { effect in
            switch effect {
            case let .openCall(id): onOpenCall(id)
            case let .share(name, _, content): Platform.share(name: name, content: content)
            case .copyText, .close: break
            }
        }
    }

    @ViewBuilder
    private func content(_ state: ListState) -> some View {
        if let calls = state.calls {
            if calls.isEmpty {
                message(state.isNarrowed ? "Nothing matches" : "No calls yet")
            } else {
                List(calls) { call in
                    CallRowView(
                        call: call,
                        highlighted: call.id == selectedCallId,
                        selected: state.selection.map { $0.contains(call.id) }
                    )
                    .contentShape(Rectangle())
                    .onTapGesture { session.send(.click(id: call.id)) }
                    .onLongPressGesture { session.send(.longClick(id: call.id)) }
                    .listRowInsets(EdgeInsets())
                    .listRowSeparatorTint(MonitorColor.border)
                    .listRowBackground(rowBackground(call, state))
                    .accessibilityAction(named: "Select") { session.send(.longClick(id: call.id)) }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        } else {
            loading
        }
    }

    private func rowBackground(_ call: CallRow, _ state: ListState) -> Color {
        if state.selection?.contains(call.id) == true { return MonitorColor.accentContainer }
        if call.id == selectedCallId { return MonitorColor.surface }
        return MonitorColor.background
    }

    private var loading: some View {
        ProgressView()
            .tint(MonitorColor.accent)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(MonitorFont.subtitle)
            .foregroundColor(MonitorColor.textSecondary)
            .multilineTextAlignment(.center)
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if let state = session.state, let selection = state.selection {
            selectionToolbar(state, selection)
        } else {
            mainToolbar(session.state)
        }
    }

    @ToolbarContentBuilder
    private func mainToolbar(_ state: ListState?) -> some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button(action: onClose) { Image(systemName: "xmark") }
                .accessibilityLabel("Close")
        }
        ToolbarItem(placement: .principal) {
            VStack(spacing: 0) {
                Text("Network monitor")
                    .font(MonitorFont.title)
                    .foregroundColor(MonitorColor.text)
                Text(callCount(state?.totalCount ?? 0))
                    .font(MonitorFont.caption)
                    .foregroundColor(MonitorColor.textSecondary)
            }
        }
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            if let state {
                Button { session.send(.toggleSearch) } label: {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(state.isSearchVisible ? MonitorColor.accent : MonitorColor.text)
                }
                .accessibilityLabel(state.isSearchVisible ? "Hide search" : "Search")

                Button { session.send(.toggleOnlyErrors) } label: {
                    Image(systemName: state.onlyErrors ? "exclamationmark.circle.fill" : "exclamationmark.circle")
                        .foregroundColor(state.onlyErrors ? MonitorColor.error : MonitorColor.text)
                }
                .accessibilityLabel(state.onlyErrors ? "Show all calls" : "Only errors")

                Button { isFiltersOpen = true } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                        .foregroundColor(state.filterCount > 0 ? MonitorColor.accent : MonitorColor.text)
                        .overlay(alignment: .topTrailing) {
                            if state.filterCount > 0 { CountBadge(count: state.filterCount).offset(x: 6, y: -6) }
                        }
                }
                .accessibilityLabel(state.filterCount > 0 ? "Filters, \(state.filterCount) active" : "Filters")

                moreMenu(state)
            }
        }
    }

    private func callCount(_ count: Int) -> String { count == 1 ? "1 call" : "\(count) calls" }

    private func moreMenu(_ state: ListState) -> some View {
        Menu {
            if state.totalCount > 0 {
                Button { session.send(.startSelection) } label: { Label("Select", systemImage: "checkmark.circle") }
            }
            Menu {
                ForEach(state.sorts) { sort in
                    Button { session.send(.sort(sort.id)) } label: {
                        if sort.id == state.sort.id {
                            Label(sort.label, systemImage: "checkmark")
                        } else {
                            Text(sort.label)
                        }
                    }
                }
            } label: {
                Label("Sort: \(state.sort.label)", systemImage: "arrow.up.arrow.down")
            }
            if state.totalCount > 0 {
                Button(role: .destructive) { session.send(.clearAll) } label: { Label("Clear all", systemImage: "trash") }
            }
        } label: {
            Image(systemName: "ellipsis.circle").foregroundColor(MonitorColor.text)
        }
        .accessibilityLabel("More")
    }

    @ToolbarContentBuilder
    private func selectionToolbar(_ state: ListState, _ selection: [String]) -> some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button { session.send(.exitSelection) } label: { Image(systemName: "xmark") }
                .accessibilityLabel("Stop selecting")
        }
        ToolbarItem(placement: .principal) {
            Text("\(selection.count) selected of \(state.calls?.count ?? 0)")
                .font(MonitorFont.title)
                .foregroundColor(MonitorColor.text)
                .lineLimit(1)
        }
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            Button { session.send(.deleteSelected) } label: {
                Image(systemName: "trash")
                    .foregroundColor(selection.isEmpty ? MonitorColor.textDisabled : MonitorColor.error)
            }
            .disabled(selection.isEmpty)
            .accessibilityLabel("Delete selected")

            Menu {
                Section("Share as") {
                    ForEach(state.exportFormats) { format in
                        Button(format.label) { session.send(.shareSelected(format: format.id)) }
                    }
                }
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .foregroundColor(selection.isEmpty ? MonitorColor.textDisabled : MonitorColor.text)
            }
            .disabled(selection.isEmpty)
            .accessibilityLabel("Share selected")
        }
    }
}

/// Select all and Clear, under the top bar while selecting.
private struct SelectionControls: View {
    let state: ListState
    let selection: [String]
    let send: (ListEvent) -> Void

    var body: some View {
        let allVisibleSelected = (state.calls ?? []).allSatisfy { selection.contains($0.id) }
        HStack(spacing: 16) {
            Button("Select all") { send(.selectAll) }
                .disabled(allVisibleSelected)
            Button("Clear") { send(.clearSelection) }
                .disabled(selection.isEmpty)
            Spacer()
        }
        .font(MonitorFont.bodyMedium)
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }
}

private struct CountBadge: View {
    let count: Int

    var body: some View {
        Text("\(count)")
            .font(MonitorFont.captionBold)
            .foregroundColor(MonitorColor.onAccent)
            .padding(.horizontal, 4)
            .frame(minWidth: 16, minHeight: 16)
            .background(Capsule().fill(MonitorColor.accent))
    }
}
