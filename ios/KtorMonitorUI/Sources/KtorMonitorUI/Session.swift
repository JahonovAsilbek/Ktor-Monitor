import Combine
import Foundation
import os

/// One open screen: a bridge session whose states and effects arrive as JSON. The session closes
/// when this object goes away.
///
/// A state can be large (the whole list, a formatted body), so it is decoded off the main thread, in
/// the order it came, and published only when it differs from the one shown.
final class Session<State: Decodable & Equatable, Event: Encodable>: ObservableObject {
    /// Nil until the first state arrives.
    @Published private(set) var state: State?
    let effects = PassthroughSubject<Effect, Never>()

    private let bridge: KtorMonitorUIBridge
    private let decoding = DispatchQueue(label: "uz.jahonov.ktormonitor.decode")
    private var id: String?

    init(bridge: KtorMonitorUIBridge, open: (KtorMonitorUIBridge, @escaping (String) -> Void, @escaping (String) -> Void) -> String) {
        self.bridge = bridge
        id = open(
            bridge,
            { [weak self] json in self?.receive(json) },
            { [weak self] json in Wire.decode(Effect.self, json).map { self?.effects.send($0) } }
        )
    }

    private func receive(_ json: String) {
        decoding.async { [weak self] in
            // One that cannot be read leaves the last state on screen.
            guard let state = Wire.decode(State.self, json) else { return }
            DispatchQueue.main.async {
                guard let self, self.state != state else { return }
                self.state = state
            }
        }
    }

    func send(_ event: Event) {
        guard let id, let json = Wire.encode(event) else { return }
        bridge.send(sessionId: id, event: json)
    }

    deinit {
        if let id { bridge.close(sessionId: id) }
    }
}

typealias ListSession = Session<ListState, ListEvent>
typealias DetailSession = Session<DetailState, DetailEvent>

extension Session where State == ListState, Event == ListEvent {
    convenience init(listOf bridge: KtorMonitorUIBridge) {
        self.init(bridge: bridge) { $0.openList(onState: $1, onEffect: $2) }
    }
}

extension Session where State == DetailState, Event == DetailEvent {
    convenience init(call callId: String, of bridge: KtorMonitorUIBridge) {
        self.init(bridge: bridge) { $0.openCall(callId: callId, onState: $1, onEffect: $2) }
    }
}

enum Wire {
    /// Nil for JSON this version cannot read, such as a newer Kotlin side's; logged, never a crash.
    static func decode<T: Decodable>(_ type: T.Type, _ json: String) -> T? {
        do {
            return try JSONDecoder().decode(type, from: Data(json.utf8))
        } catch {
            log.error("Could not read \(String(describing: type), privacy: .public): \(String(describing: error), privacy: .public)")
            return nil
        }
    }

    private static let log = Logger(subsystem: "uz.jahonov.ktormonitor", category: "bridge")

    static func encode<T: Encodable>(_ value: T) -> String? {
        guard let data = try? JSONEncoder().encode(value) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
