import SwiftUI
import UIKit
import UserNotifications

/// The monitor's screens on iOS, and the ways in: a notification and a shake of the device.
public enum KtorMonitorUI {
    private(set) static var bridge: KtorMonitorUIBridge?
    private static var notifications: MonitorNotifications?

    /// Call once at launch, with the app's bridge.
    public static func install(bridge: KtorMonitorUIBridge, shakeToOpen: Bool = true) {
        guard self.bridge == nil else { return }
        self.bridge = bridge
        notifications = MonitorNotifications(bridge: bridge)
        if shakeToOpen { ShakeToOpen.enable() }
    }

    /// Opens the monitor over the app.
    public static func present() {
        guard let bridge, let top = Platform.topViewController(), !(top is MonitorHostingController) else { return }
        let host = MonitorHostingController(bridge: bridge)
        host.modalPresentationStyle = .fullScreen
        top.present(host, animated: true)
    }

    /// For an app with its own `UNUserNotificationCenterDelegate`: pass every response here first.
    /// Returns true when it was the monitor's notification, which is then handled.
    public static func handleNotificationResponse(_ response: UNNotificationResponse) -> Bool {
        notifications?.handle(response) ?? false
    }
}

/// The whole monitor as a view, for an app that shows it itself instead of calling `present()`.
public struct KtorMonitorView: View {
    private let bridge: KtorMonitorUIBridge
    private let onClose: () -> Void
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var path: [String] = []
    @State private var selected: String?

    public init(bridge: KtorMonitorUIBridge, onClose: @escaping () -> Void) {
        self.bridge = bridge
        self.onClose = onClose
    }

    public var body: some View {
        Group {
            if sizeClass == .regular {
                // List and detail side by side, as on a wide Android screen.
                HStack(spacing: 0) {
                    NavigationStack {
                        CallListView(bridge: bridge, selectedCallId: selected, onOpenCall: { selected = $0 }, onClose: onClose)
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
                    CallListView(bridge: bridge, selectedCallId: nil, onOpenCall: { path = [$0] }, onClose: onClose)
                        .navigationDestination(for: String.self) { id in
                            CallDetailView(callId: id, bridge: bridge, onGone: { path.removeAll() })
                        }
                }
            }
        }
        .tint(MonitorColor.accent)
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

    static func copy(_ text: String) {
        UIPasteboard.general.string = text
    }

    /// Writes the file to a temporary directory and opens the share sheet for it.
    static func share(name: String, content: String) {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("ktormonitor", isDirectory: true)
        let file = directory.appendingPathComponent(name)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try content.write(to: file, atomically: true, encoding: .utf8)
        } catch {
            return
        }
        guard let top = topViewController() else { return }
        let sheet = UIActivityViewController(activityItems: [file], applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = top.view
        sheet.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
        top.present(sheet, animated: true)
    }
}
