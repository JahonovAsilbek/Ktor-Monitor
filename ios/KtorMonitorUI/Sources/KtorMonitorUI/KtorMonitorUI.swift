import SwiftUI
import UIKit
import UniformTypeIdentifiers
import UserNotifications

/// The monitor's screens on iOS, and the ways in: a notification and a shake of the device.
public enum KtorMonitorUI {
    private(set) static var bridge: KtorMonitorUIBridge?
    private static var notifications: MonitorNotifications?
    /// Monitor views on screen, presented or embedded by the app.
    static var visibleMonitors = 0
    private static var pendingPresent: NSObjectProtocol?

    /// Call once at launch, with the app's bridge.
    public static func install(bridge: KtorMonitorUIBridge, shakeToOpen: Bool = true) {
        guard self.bridge == nil else { return }
        self.bridge = bridge
        notifications = MonitorNotifications(bridge: bridge)
        if shakeToOpen { ShakeToOpen.enable() }
    }

    /// Opens the monitor over the app, unless it is on screen already (even under a sheet of its own).
    @MainActor
    public static func present() {
        guard let bridge, visibleMonitors == 0 else { return }
        guard let top = Platform.topViewController() else {
            // Launched by a tap on the notification, the app has no key window yet.
            presentOnceActive()
            return
        }
        let host = MonitorHostingController(bridge: bridge)
        host.modalPresentationStyle = .fullScreen
        top.present(host, animated: true)
    }

    @MainActor
    private static func presentOnceActive() {
        guard pendingPresent == nil else { return }
        pendingPresent = NotificationCenter.default.addObserver(forName: UIScene.didActivateNotification, object: nil, queue: .main) { _ in
            if let pendingPresent { NotificationCenter.default.removeObserver(pendingPresent) }
            pendingPresent = nil
            MainActor.assumeIsolated { present() }
        }
    }

    static func repostNotification() {
        notifications?.repost()
    }

    /// For an app with its own `UNUserNotificationCenterDelegate`: pass every response here first.
    /// Returns true when it was the monitor's notification, which is then handled.
    @MainActor
    public static func handleNotificationResponse(_ response: UNNotificationResponse) -> Bool {
        notifications?.handle(response) ?? false
    }

    /// For an app with its own `UNUserNotificationCenterDelegate`, in `willPresent`: the options for
    /// the monitor's notification (the list only: no banner, no sound), or nil for any other.
    public static func presentationOptions(for notification: UNNotification) -> UNNotificationPresentationOptions? {
        MonitorNotifications.isMonitors(notification) ? [.list] : nil
    }
}

/// The whole monitor as a view, for an app that shows it itself instead of calling `present()`.
public struct KtorMonitorView: View {
    private let bridge: KtorMonitorUIBridge
    private let onClose: () -> Void
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var path: [String] = []
    @State private var selected: String?
    /// Here, not in the list: a change of size class (a rotation, an iPad split) rebuilds the list,
    /// and its search, filters and selection must live through that.
    @StateObject private var list: ListSession

    public init(bridge: KtorMonitorUIBridge, onClose: @escaping () -> Void) {
        self.bridge = bridge
        self.onClose = onClose
        _list = StateObject(wrappedValue: ListSession(listOf: bridge))
    }

    public var body: some View {
        Group {
            if sizeClass == .regular {
                // List and detail side by side, as on a wide Android screen.
                HStack(spacing: 0) {
                    NavigationStack {
                        CallListView(session: list, bridge: bridge, selectedCallId: selected, onOpenCall: { selected = $0 }, onClose: onClose)
                    }
                    .frame(maxWidth: 420)
                    Rectangle().fill(MonitorColor.border).frame(width: 1)
                    NavigationStack {
                        if let selected {
                            CallDetailView(callId: selected, bridge: bridge, onGone: { self.selected = nil }).id(selected)
                        } else {
                            MonitorColor.background
                        }
                    }
                }
            } else {
                NavigationStack(path: $path) {
                    CallListView(session: list, bridge: bridge, selectedCallId: nil, onOpenCall: { path = [$0] }, onClose: onClose)
                        .navigationDestination(for: String.self) { id in
                            CallDetailView(callId: id, bridge: bridge, onGone: { path.removeAll() })
                        }
                }
            }
        }
        .tint(MonitorColor.accent)
        .onAppear { KtorMonitorUI.visibleMonitors += 1 }
        .onDisappear { KtorMonitorUI.visibleMonitors -= 1 }
    }
}

final class MonitorHostingController: UIHostingController<KtorMonitorView> {
    init(bridge: KtorMonitorUIBridge) {
        var dismiss: () -> Void = {}
        super.init(rootView: KtorMonitorView(bridge: bridge, onClose: { dismiss() }))
        dismiss = { [weak self] in self?.dismiss(animated: true) }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
}

enum Platform {
    static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    /// Kept to this device and for ten minutes: a copy can hold a token.
    static func copy(_ text: String) {
        UIPasteboard.general.setItems(
            [[UTType.utf8PlainText.identifier: text]],
            options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(10 * 60)]
        )
    }

    /// Writes the file to a temporary directory and opens the share sheet for it. An export holds
    /// tokens and bodies: it is protected on disk, and removed once the sheet closes.
    static func share(name: String, content: String) {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("ktormonitor", isDirectory: true)
        let file = directory.appendingPathComponent(name)
        do {
            try? FileManager.default.removeItem(at: directory)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try Data(content.utf8).write(to: file, options: [.atomic, .completeFileProtection])
        } catch {
            return
        }
        guard let top = topViewController() else { return }
        let sheet = UIActivityViewController(activityItems: [file], applicationActivities: nil)
        sheet.completionWithItemsHandler = { _, _, _, _ in try? FileManager.default.removeItem(at: file) }
        sheet.popoverPresentationController?.sourceView = top.view
        sheet.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
        top.present(sheet, animated: true)
    }
}
