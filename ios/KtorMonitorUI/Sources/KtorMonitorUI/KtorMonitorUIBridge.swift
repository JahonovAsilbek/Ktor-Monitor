import Foundation

/// The monitor as this package sees it: strings and closures only. The Kotlin `KtorMonitorBridge`
/// has exactly these members, so one line in the app makes it the bridge:
///
///     extension KtorMonitorBridge: KtorMonitorUIBridge {}
///
/// States, effects and events are JSON. Every call happens on the main thread, callbacks too.
public protocol KtorMonitorUIBridge: AnyObject {
    var isNotificationEnabled: Bool { get }
    func openList(onState: @escaping (String) -> Void, onEffect: @escaping (String) -> Void) -> String
    func openCall(callId: String, onState: @escaping (String) -> Void, onEffect: @escaping (String) -> Void) -> String
    func observeNotification(onUpdate: @escaping (String) -> Void) -> String
    func send(sessionId: String, event: String)
    func close(sessionId: String)
    func clear()
}
