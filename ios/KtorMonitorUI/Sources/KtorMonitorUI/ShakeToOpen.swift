import ObjectiveC
import UIKit

/// Opens the monitor when the device is shaken: a shake travels up the responder chain to the
/// window, whose `motionEnded` is swapped for one that also opens the monitor.
enum ShakeToOpen {
    private static var isEnabled = false

    static func enable() {
        guard !isEnabled else { return }
        isEnabled = true
        let original = #selector(UIResponder.motionEnded(_:with:))
        let replacement = #selector(UIWindow.ktorMonitor_motionEnded(_:with:))
        guard
            let originalMethod = class_getInstanceMethod(UIWindow.self, original),
            let replacementMethod = class_getInstanceMethod(UIWindow.self, replacement)
        else { return }
        // UIWindow inherits motionEnded from UIResponder: add it to UIWindow first, so other responders keep theirs.
        if class_addMethod(UIWindow.self, original, method_getImplementation(replacementMethod), method_getTypeEncoding(replacementMethod)) {
            class_replaceMethod(UIWindow.self, replacement, method_getImplementation(originalMethod), method_getTypeEncoding(originalMethod))
        } else {
            method_exchangeImplementations(originalMethod, replacementMethod)
        }
    }
}

extension UIWindow {
    @objc func ktorMonitor_motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        if motion == .motionShake { KtorMonitorUI.present() }
        // After the swap this name runs the original implementation.
        ktorMonitor_motionEnded(motion, with: event)
    }
}
