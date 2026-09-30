import Combine
import Foundation

/// One open screen: a bridge session whose states and effects arrive as JSON. The session closes
/// when this object goes away.
final class Session<State: Decodable, Event: Encodable>: ObservableObject {
    /// Nil until the first state arrives.
    @Published private(set) var state: State?
    let effects = PassthroughSubject<Effect, Never>()

    private let bridge: KtorMonitorUIBridge
    private var id: String?

    init(bridge: KtorMonitorUIBridge, open: (KtorMonitorUIBridge, @escaping (String) -> Void, @escaping (String) -> Void) -> String) {
        self.bridge = bridge
        id = open(
            bridge,
            { [weak self] json in self?.state = Wire.decode(State.self, json) },
            { [weak self] json in Wire.decode(Effect.self, json).map { self?.effects.send($0) } }
        )
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
    static func decode<T: Decodable>(_ type: T.Type, _ json: String) -> T? {
        do {
            return try JSONDecoder().decode(type, from: Data(json.utf8))
        } catch {
            assertionFailure("KtorMonitorUI could not read \(type): \(error)")
            return nil
        }
    }

    static func encode<T: Encodable>(_ value: T) -> String? {
        guard let data = try? JSONEncoder().encode(value) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
