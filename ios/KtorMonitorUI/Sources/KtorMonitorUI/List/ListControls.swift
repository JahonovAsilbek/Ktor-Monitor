import SwiftUI
import UIKit
import UserNotifications

/**
 The search field. It keeps its own text: the view model echoes the query back only after the calls
 are found again, and a field fed that late value would drop keystrokes typed meanwhile.
 */
struct CallSearchField: View {
    let onSearch: (String) -> Void
    @State private var text: String
    @FocusState private var isFocused: Bool

    init(query: String, onSearch: @escaping (String) -> Void) {
        self.onSearch = onSearch
        _text = State(initialValue: query)
    }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(MonitorColor.textSecondary)
            TextField("Search URL, method, status, body", text: $text)
                .font(MonitorFont.body)
                .foregroundColor(MonitorColor.text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($isFocused)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundColor(MonitorColor.textSecondary)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 40)
        .background(RoundedRectangle(cornerRadius: 12).fill(MonitorColor.surface))
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
        .onChange(of: text) { onSearch($0) }
        .onAppear { isFocused = text.isEmpty }
    }
}

/// The filters in force as chips; tapping one drops it.
struct ActiveFiltersRow: View {
    let state: ListState
    let send: (ListEvent) -> Void

    var body: some View {
        let filters = state.filters
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if state.onlyErrors { FilterChip(text: "Only errors", selected: true) { send(.toggleOnlyErrors) } }
                ForEach(filters.hosts, id: \.self) { host in FilterChip(text: host, selected: true) { send(.toggleHost(host)) } }
                ForEach(filters.methods, id: \.self) { method in FilterChip(text: method, selected: true) { send(.toggleMethod(method)) } }
                ForEach(filters.contentTypes, id: \.self) { type in FilterChip(text: type, selected: true) { send(.toggleContentType(type)) } }
                ForEach(filters.statuses) { status in FilterChip(text: status.label, selected: true) { send(.toggleStatus(status.id)) } }
                ForEach(filters.durations) { duration in FilterChip(text: duration.label, selected: true) { send(.toggleDuration(duration.id)) } }
                Button("Clear") { send(.clearFilters) }
                    .font(MonitorFont.bodyMedium)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
        }
    }
}

struct FiltersSheet: View {
    let state: ListState
    let send: (ListEvent) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let options = state.options
        let filters = state.filters
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    group("Host", options.hosts.map { Option(id: $0, label: $0) }, selected: Set(filters.hosts)) { send(.toggleHost($0)) }
                    group("Method", options.methods.map { Option(id: $0, label: $0) }, selected: Set(filters.methods)) { send(.toggleMethod($0)) }
                    group("Content type", options.contentTypes.map { Option(id: $0, label: $0) }, selected: Set(filters.contentTypes)) {
                        send(.toggleContentType($0))
                    }
                    group("Status", options.statuses, selected: Set(filters.statuses.map(\.id))) { send(.toggleStatus($0)) }
                    group("Duration", options.durations, selected: Set(filters.durations.map(\.id))) { send(.toggleDuration($0)) }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(MonitorColor.background.ignoresSafeArea())
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Clear") { send(.clearFilters) }
                        .disabled(state.filterCount == 0 && !state.onlyErrors)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// A titled group of toggle chips; nothing at all when there is nothing to choose from.
    @ViewBuilder
    private func group(_ title: String, _ values: [Option], selected: Set<String>, onToggle: @escaping (String) -> Void) -> some View {
        if !values.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .font(MonitorFont.bodyMedium)
                    .foregroundColor(MonitorColor.textSecondary)
                FilterFlow(spacing: 8) {
                    ForEach(values) { value in
                        FilterChip(text: value.label, selected: selected.contains(value.id)) { onToggle(value.id) }
                    }
                }
            }
        }
    }
}

/// A toggle chip for filters.
struct FilterChip: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(MonitorFont.captionMedium)
                .foregroundColor(selected ? MonitorColor.onAccent : MonitorColor.text)
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Capsule().fill(selected ? MonitorColor.accent : MonitorColor.surface))
                .overlay(Capsule().stroke(selected ? MonitorColor.accent : MonitorColor.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Lays chips out in rows, wrapping to the next row when one is full.
struct FilterFlow: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].indices.isEmpty ? size.width : rows[rows.count - 1].width + spacing + size.width
            if needed > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width = row.indices.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}

/**
 Asks for permission to post the monitor's notification. Rechecked whenever the app becomes active,
 since it can be granted in Settings meanwhile. Once denied, the button opens the app's Settings.
 */
struct NotificationBanner: View {
    let isEnabled: Bool
    /// Nil until checked, so the banner does not flash up for an app that has the permission.
    @State private var allowed: Bool?
    @State private var deniedForGood = false

    var body: some View {
        // The hooks sit on a view that is always there: on an empty Group they would never run.
        VStack(spacing: 0) {
            Color.clear.frame(height: 0)
            if isEnabled && allowed == false {
                HStack(spacing: 12) {
                    Text("Allow notifications to see calls in Notification Center")
                        .font(MonitorFont.body)
                        .foregroundColor(MonitorColor.text)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Button("Allow", action: allow)
                        .font(MonitorFont.bodyMedium)
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 16).fill(MonitorColor.warningContainer))
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
        }
        .onAppear(perform: refresh)
        // Not scenePhase: inside a hosting controller presented from UIKit it does not always change.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in refresh() }
    }

    private func refresh() {
        guard isEnabled else { return }
        MonitorNotifications.canPost { canPost in
            // Once allowed, the notification shows the calls made so far, not only the next one.
            if canPost && allowed == false { KtorMonitorUI.repostNotification() }
            allowed = canPost
        }
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let denied = settings.authorizationStatus == .denied
            DispatchQueue.main.async { deniedForGood = denied }
        }
    }

    private func allow() {
        if deniedForGood {
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            return
        }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert]) { _, _ in
            DispatchQueue.main.async { refresh() }
        }
    }
}
