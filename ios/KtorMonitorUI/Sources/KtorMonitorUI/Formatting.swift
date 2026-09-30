import Foundation

enum Format {
    /// 14:25:01.005 in the device's time zone.
    static func clock(_ epochMillis: Int64) -> String { clockFormatter.string(from: date(epochMillis)) }

    /// Mon, 2026 Sep 30 14:25:01.345
    static func dateTime(_ epochMillis: Int64) -> String { dateTimeFormatter.string(from: date(epochMillis)) }

    /// 85 ms, 1.24 s, 1 min 5 s.
    static func duration(_ millis: Int64) -> String {
        if millis < 1_000 { return "\(millis) ms" }
        if millis < 60_000 { return String(format: "%.2f s", Double(millis) / 1_000) }
        return "\(millis / 60_000) min \(millis % 60_000 / 1_000) s"
    }

    /// 512 B, 1.5 KB, 2.3 MB.
    static func size(_ bytes: Int64) -> String {
        if bytes < 1_024 { return "\(bytes) B" }
        if bytes < 1_024 * 1_024 { return String(format: "%.1f KB", Double(bytes) / 1_024) }
        return String(format: "%.1f MB", Double(bytes) / (1_024 * 1_024))
    }

    private static func date(_ epochMillis: Int64) -> Date { Date(timeIntervalSince1970: Double(epochMillis) / 1_000) }

    private static let clockFormatter = formatter("HH:mm:ss.SSS")
    private static let dateTimeFormatter = formatter("EEE, yyyy MMM dd HH:mm:ss.SSS")

    private static func formatter(_ pattern: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = pattern
        return formatter
    }
}
