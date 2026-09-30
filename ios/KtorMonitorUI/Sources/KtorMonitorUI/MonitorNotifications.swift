import UIKit
import UserNotifications

/**
 One notification with the latest calls, replaced in place as calls arrive. Tapping it opens the
 monitor; Clear empties the history. It is passive: it goes to the notification list without a
 banner or a sound. Without permission nothing is posted; the monitor's list asks for it.
 */
final class MonitorNotifications: NSObject, UNUserNotificationCenterDelegate {
    private let bridge: KtorMonitorUIBridge
    private let center = UNUserNotificationCenter.current()
    private var session: String?
    /// What the notification says now: posted again once the permission is granted.
    private var last: NotificationUpdate?

    init(bridge: KtorMonitorUIBridge) {
        self.bridge = bridge
        super.init()
        guard bridge.isNotificationEnabled else {
            center.removeDeliveredNotifications(withIdentifiers: [Self.id])
            return
        }
        // An app with its own delegate forwards to KtorMonitorUI.handleNotificationResponse and
        // KtorMonitorUI.presentationOptions.
        if center.delegate == nil { center.delegate = self }
        session = bridge.observeNotification { [weak self] json in
            guard let update = Wire.decode(NotificationUpdate.self, json) else { return }
            self?.post(update)
        }
    }

    deinit {
        if let session { bridge.close(sessionId: session) }
    }

    // The Kotlin side sends updates at most a few a second, the rate the system accepts.
    func repost() {
        last.map(post)
    }

    private func post(_ update: NotificationUpdate) {
        last = update
        guard !update.lines.isEmpty else {
            center.removeDeliveredNotifications(withIdentifiers: [Self.id])
            return
        }
        center.getNotificationSettings { [center] settings in
            guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
            Self.registerCategory(center) {
                let content = UNMutableNotificationContent()
                content.title = update.title
                content.body = update.lines.joined(separator: "\n")
                content.categoryIdentifier = Self.category
                content.interruptionLevel = .passive
                center.add(UNNotificationRequest(identifier: Self.id, content: content, trigger: nil))
            }
        }
    }

    /// The Clear action, added to the app's categories right before posting: registered once at
    /// launch, it would be lost to an app that sets its own categories later.
    private static func registerCategory(_ center: UNUserNotificationCenter, then post: @escaping () -> Void) {
        center.getNotificationCategories { categories in
            if !categories.contains(where: { $0.identifier == category }) {
                let clear = UNNotificationAction(identifier: clearAction, title: "Clear")
                center.setNotificationCategories(categories.union([UNNotificationCategory(identifier: category, actions: [clear], intentIdentifiers: [])]))
            }
            post()
        }
    }

    static func isMonitors(_ notification: UNNotification) -> Bool {
        notification.request.identifier == id
    }

    @MainActor
    func handle(_ response: UNNotificationResponse) -> Bool {
        guard Self.isMonitors(response.notification) else { return false }
        if response.actionIdentifier == Self.clearAction {
            bridge.clear()
        } else if response.actionIdentifier == UNNotificationDefaultActionIdentifier {
            KtorMonitorUI.present()
        }
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        DispatchQueue.main.async {
            _ = MainActor.assumeIsolated { self.handle(response) }
            completionHandler()
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Anything else is shown as it would be with no delegate at all: not while the app is open.
        completionHandler(Self.isMonitors(notification) ? [.list] : [])
    }

    static func canPost(_ completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let allowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
            DispatchQueue.main.async { completion(allowed) }
        }
    }

    private static let id = "ktormonitor"
    private static let category = "ktormonitor.calls"
    private static let clearAction = "ktormonitor.clear"
}
