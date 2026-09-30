import SwiftUI
import UIKit

/// The monitor's colours, the same as on Android; each follows the system's light or dark setting.
enum MonitorColor {
    static let background = dynamic(0xFFFFFF, 0x121317)
    static let surface = dynamic(0xF3F4F6, 0x1C1E24)
    static let border = dynamic(0xE0E2E7, 0x2C2F37)
    static let text = dynamic(0x16181D, 0xECEEF2)
    static let textSecondary = dynamic(0x6B7080, 0x9CA1AE)
    static let textDisabled = dynamic(0xA6AAB5, 0x5E6370)
    static let accent = dynamic(0x2F6FEB, 0x6C9CFF)
    static let onAccent = dynamic(0xFFFFFF, 0x0B1020)
    static let accentContainer = dynamic(0xE4EDFD, 0x1F2B45)
    static let error = dynamic(0xD93A3A, 0xFF6B6B)
    static let errorContainer = dynamic(0xFDECEC, 0x3A1E20)
    static let warning = dynamic(0x9A5B00, 0xF2B24C)
    static let warningContainer = dynamic(0xFFF4DE, 0x3A2E17)
    static let info = dynamic(0x11708E, 0x56C1E0)
    static let success = dynamic(0x1F7A42, 0x5CCB84)
    static let media = dynamic(0x8A4FD8, 0xB58CFF)

    private static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(rgb: dark) : UIColor(rgb: light) })
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// Text styles, the same sizes as on Android.
enum MonitorFont {
    static let caption = Font.system(size: 12)
    static let captionMedium = Font.system(size: 12, weight: .medium)
    static let captionBold = Font.system(size: 12, weight: .bold)
    static let body = Font.system(size: 14)
    static let bodyMedium = Font.system(size: 14, weight: .medium)
    static let bodyBold = Font.system(size: 14, weight: .bold)
    static let subtitle = Font.system(size: 16, weight: .medium)
    static let title = Font.system(size: 16, weight: .bold)
    static let headline = Font.system(size: 18, weight: .bold)
    static let mono = Font.system(size: 12, design: .monospaced)
}

/// Status text colour: red for an error, secondary while in flight.
func statusColor(_ call: CallRow) -> Color {
    if call.isError { return MonitorColor.error }
    if call.isInProgress { return MonitorColor.textSecondary }
    if call.isRedirect { return MonitorColor.warning }
    return MonitorColor.text
}

/// The badge colour of a content kind (its Kotlin name); kinds of a family share a hue.
func kindColor(_ kind: String) -> Color {
    switch kind {
    case "JSON", "YAML": return MonitorColor.accent
    case "XML", "HTML", "MARKDOWN", "EVENT_STREAM", "WEBSOCKET": return MonitorColor.info
    case "CSS", "JAVASCRIPT": return MonitorColor.warning
    case "FORM", "MULTIPART": return MonitorColor.success
    case "IMAGE", "FONT", "AUDIO", "VIDEO", "PDF": return MonitorColor.media
    default: return MonitorColor.textSecondary
    }
}

/// Syntax colour of a token kind (its Kotlin name).
func tokenColor(_ kind: String) -> Color {
    switch kind {
    case "KEY", "ATTRIBUTE", "LINK": return MonitorColor.info
    case "STRING": return MonitorColor.success
    case "NUMBER": return MonitorColor.warning
    case "KEYWORD", "TAG", "HEADING": return MonitorColor.accent
    case "PUNCTUATION": return MonitorColor.textSecondary
    case "COMMENT": return MonitorColor.textDisabled
    default: return MonitorColor.text
    }
}
