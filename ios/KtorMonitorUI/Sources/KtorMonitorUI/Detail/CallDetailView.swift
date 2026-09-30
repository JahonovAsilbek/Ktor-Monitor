import SwiftUI

/// One call: Summary, Request and Response, swipeable. `onGone` runs when the call is deleted while shown.
struct CallDetailView: View {
    private let onGone: () -> Void
    @StateObject private var session: DetailSession
    @State private var page = 0
    /// Bumped by each copy; the "Copied" note shows while it is the latest.
    @State private var copies = 0
    @State private var showsCopied = false

    init(callId: String, bridge: KtorMonitorUIBridge, onGone: @escaping () -> Void) {
        self.onGone = onGone
        _session = StateObject(wrappedValue: DetailSession(call: callId, of: bridge))
    }

    var body: some View {
        let state = session.state
        let call = state?.call
        Group {
            if let state, let call {
                VStack(spacing: 0) {
                    PageTabs(selected: $page)
                    TabView(selection: $page) {
                        SummaryPage(call: call.summary).tag(0)
                        MessagePage(call: call, side: .request, message: state.request, send: session.send).tag(1)
                        MessagePage(call: call, side: .response, message: state.response, send: session.send).tag(2)
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(MonitorColor.background.ignoresSafeArea())
        .overlay(alignment: .bottom) {
            if showsCopied { CopiedNote().transition(.opacity) }
        }
        .navigationTitle(call.map { "\($0.summary.method) \($0.summary.path)" } ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let call {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text("\(call.summary.method) \(call.summary.path)")
                            .font(MonitorFont.title)
                            .foregroundColor(MonitorColor.text)
                            .lineLimit(1)
                        Text(call.summary.host)
                            .font(MonitorFont.caption)
                            .foregroundColor(MonitorColor.textSecondary)
                            .lineLimit(1)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    ShareMenu(send: session.send)
                }
            }
        }
        .onReceive(session.effects) { effect in
            switch effect {
            case let .copyText(text):
                Platform.copy(text)
                confirmCopy()
            case let .share(name, _, content): Platform.share(name: name, content: content)
            case .close: onGone()
            case .openCall: break
            }
        }
    }
}

extension CallDetailView {
    /// iOS confirms nothing on a copy, unlike Android 13 and later.
    private func confirmCopy() {
        copies += 1
        let copy = copies
        withAnimation { showsCopied = true }
        UIAccessibility.post(notification: .announcement, argument: "Copied")
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            guard copy == copies else { return }
            withAnimation { showsCopied = false }
        }
    }
}

private struct CopiedNote: View {
    var body: some View {
        Text("Copied")
            .font(MonitorFont.bodyMedium)
            .foregroundColor(MonitorColor.background)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Capsule().fill(MonitorColor.text))
            .padding(.bottom, 24)
            .accessibilityHidden(true)
    }
}

private struct PageTabs: View {
    @Binding var selected: Int

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(Self.titles.enumerated()), id: \.offset) { index, title in
                let isSelected = index == selected
                Button {
                    withAnimation { selected = index }
                } label: {
                    VStack(spacing: 0) {
                        Text(title)
                            .font(MonitorFont.bodyMedium)
                            .foregroundColor(isSelected ? MonitorColor.accent : MonitorColor.textSecondary)
                            .padding(.vertical, 12)
                        Rectangle()
                            .fill(isSelected ? MonitorColor.accent : MonitorColor.border)
                            .frame(height: 2)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.horizontal, 16)
    }

    private static let titles = ["Summary", "Request", "Response"]
}

/// Copy and share actions for the open call.
private struct ShareMenu: View {
    let send: (DetailEvent) -> Void

    var body: some View {
        Menu {
            Section {
                entry("Copy URL", "doc.on.doc", .copy(format: "URL"))
                entry("Copy as cURL", "doc.on.doc", .copy(format: "CURL"))
                entry("Copy as wget", "doc.on.doc", .copy(format: "WGET"))
                entry("Copy as text", "doc.on.doc", .copy(format: "TEXT"))
            }
            Section {
                entry("Share as .http file", "square.and.arrow.up", .share(format: "TEXT"))
                entry("Share as Markdown", "square.and.arrow.up", .share(format: "MARKDOWN"))
                entry("Share as HAR", "square.and.arrow.up", .share(format: "HAR"))
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
        .accessibilityLabel("Copy or share")
    }

    private func entry(_ title: String, _ icon: String, _ event: DetailEvent) -> some View {
        Button { send(event) } label: { Label(title, systemImage: icon) }
    }
}
