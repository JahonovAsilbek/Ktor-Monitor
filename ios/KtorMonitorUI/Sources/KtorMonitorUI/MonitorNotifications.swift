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

    init(bridge: KtorMonitorUIBridge) {
        self.bridge = bridge
        super.init()
        guard bridge.isNotificationEnabled else {
            center.removeDeliveredNotifications(withIdentifiers: [Self.id])
            return
        }
        let clear = UNNotificationAction(identifier: Self.clearAction, title: "Clear")
        center.getNotificationCategories { [center] categories in
            let own = UNNotificationCategory(identifier: Self.category, actions: [clear], intentIdentifiers: [])
            center.setNotificationCategories(categories.filter { $0.identifier != Self.category }.union([own]))
        }
        // An app with its own delegate forwards responses through KtorMonitorUI.handleNotificationResponse.
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
    private func post(_ update: NotificationUpdate) {
        guard !update.lines.isEmpty else {
            center.removeDeliveredNotifications(withIdentifiers: [Self.id])
            return
        }
        center.getNotificationSettings { [center] settings in
            guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
            let content = UNMutableNotificationContent()
            content.title = update.title
            content.body = update.lines.joined(separator: "\n")
            content.categoryIdentifier = Self.category
            content.interruptionLevel = .passive
            center.add(UNNotificationRequest(identifier: Self.id, content: content, trigger: nil))
        }
    }

    func handle(_ response: UNNotificationResponse) -> Bool {
        guard response.notification.request.identifier == Self.id else { return false }
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
            _ = self.handle(response)
            completionHandler()
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler(notification.request.identifier == Self.id ? [.list] : [.banner, .list, .sound])
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
