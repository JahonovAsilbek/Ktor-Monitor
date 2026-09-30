import KtorMonitorUI
import SampleShared
import SwiftUI

// The one line that makes the Kotlin bridge the package's bridge.
extension KtorMonitorBridge: @retroactive KtorMonitorUIBridge {}

@main
struct SampleApp: App {
    init() {
        KtorMonitorUI.install(bridge: SampleIos.shared.bridge)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
