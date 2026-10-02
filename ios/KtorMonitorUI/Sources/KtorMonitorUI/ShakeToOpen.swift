import ObjectiveC
import UIKit

/// Opens the monitor when the device is shaken: a shake travels up the responder chain to the
/// window, whose `motionEnded` gets an implementation that also opens the monitor.
enum ShakeToOpen {
    private static var isEnabled = false

    private typealias MotionEnded = @convention(c) (UIWindow, Selector, UIEvent.EventSubtype, UIEvent?) -> Void

    static func enable() {
        guard !isEnabled else { return }
        isEnabled = true
        let selector = #selector(UIResponder.motionEnded(_:with:))
        guard let method = class_getInstanceMethod(UIWindow.self, selector) else { return }
        // UIWindow's own implementation, or the one it inherits from UIResponder. That one passes the
        // event up the chain under the selector it was called with, so it must get the real one: a
        // renamed selector (the usual swizzle) reaches the window scene, which does not know it.
        let original = unsafeBitCast(method_getImplementation(method), to: MotionEnded.self)
        let opening: @convention(block) (UIWindow, UIEvent.EventSubtype, UIEvent?) -> Void = { window, motion, event in
            if motion == .motionShake { MainActor.assumeIsolated { KtorMonitorUI.present() } }
            original(window, selector, motion, event)
        }
        let implementation = imp_implementationWithBlock(opening)
        // Added to UIWindow itself when it only inherits the method, so other responders keep theirs.
        if !class_addMethod(UIWindow.self, selector, implementation, method_getTypeEncoding(method)) {
            method_setImplementation(method, implementation)
        }
    }
}
