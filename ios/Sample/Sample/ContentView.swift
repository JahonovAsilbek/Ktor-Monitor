import KtorMonitorUI
import SampleShared
import SwiftUI

/// A button per sample call, and one that opens the monitor. Shaking the device opens it too.
struct ContentView: View {
    private let api = SampleIos.shared.api

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button("Open monitor") { KtorMonitorUI.present() }
                }
                Section("Calls") {
                    ForEach(Array(api.requests.enumerated()), id: \.offset) { index, title in
                        Button(title) { api.send(index: Int32(index)) }
                    }
                }
            }
            .navigationTitle("Ktor Monitor")
        }
    }
}
