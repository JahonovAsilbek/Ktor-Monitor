import SwiftUI

/**
 One row on screen: characters `start` until `end` of line `line`. A line longer than `longLine` is
 cut into several rows, so a minified body on one line never becomes one giant text layout.
 */
struct TextRow: Hashable {
    let line: Int
    let start: Int
    let end: Int
    let isLastOfLine: Bool

    var isFirstOfLine: Bool { start == 0 }

    static let longLine = 1_000
}

extension Array where Element == TextRow {
    mutating func addLine(_ line: Int, length: Int) {
        var start = 0
        repeat {
            let end = Swift.min(start + TextRow.longLine, length)
            append(TextRow(line: line, start: start, end: end, isLastOfLine: end == length))
            start = end
        } while start < length
    }
}

enum Gutter {
    static func rows(_ lines: [String]) -> [TextRow] {
        var rows: [TextRow] = []
        for (index, text) in lines.enumerated() { rows.addLine(index, length: text.count) }
        return rows
    }

    /// The line number of `row`, padded to `digits`; blank on the rows a long line continues on.
    static func label(_ row: TextRow, digits: Int) -> String {
        guard row.isFirstOfLine else { return String(repeating: " ", count: digits) }
        let number = String(row.line + 1)
        return String(repeating: " ", count: max(digits - number.count, 0)) + number
    }

    static func digits(_ lineCount: Int) -> Int { String(max(lineCount, 1)).count }
}

/**
 The rows of a body, worked out once per body and fold state rather than on every render: for a
 body of thousands of lines, counting every span again is what made the page slow.
 */
final class RowCache {
    private var lines: (source: [String], rows: [TextRow])?
    private var code: (lines: [[CodeSpan]], folds: [Fold], collapsed: Set<Int>, layout: CodeLayout)?
    private var defaults: (folds: [Fold], collapsed: Set<Int>)?

    func rows(_ source: [String]) -> [TextRow] {
        if let lines, lines.source == source { return lines.rows }
        let rows = Gutter.rows(source)
        lines = (source, rows)
        return rows
    }

    func code(_ lines: [[CodeSpan]], folds: [Fold], collapsed: Set<Int>) -> CodeLayout {
        if let code, code.collapsed == collapsed, code.folds == folds, code.lines == lines { return code.layout }
        let layout = CodeLayout(lines: lines, folds: folds, collapsed: collapsed)
        code = (lines, folds, collapsed, layout)
        return layout
    }

    /// The regions folded before the user touches any.
    func defaultCollapsed(_ folds: [Fold]) -> Set<Int> {
        if let defaults, defaults.folds == folds { return defaults.collapsed }
        let collapsed = Set(CodeLayout.regions(folds).values.filter(\.isCollapsedByDefault).map(\.startLine))
        defaults = (folds, collapsed)
        return collapsed
    }
}

extension String {
    /// Characters `start` until `end`.
    func characters(_ start: Int, _ end: Int) -> Substring {
        let from = index(startIndex, offsetBy: start)
        return self[from..<index(from, offsetBy: end - start)]
    }
}

/// A gutter, then text that wraps onto as many lines as it needs: the body only ever scrolls vertically.
struct MonoRow<Between: View>: View {
    let gutter: String?
    let text: AttributedString
    @ViewBuilder var between: () -> Between

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            if let gutter {
                Text(gutter)
                    .font(MonitorFont.mono)
                    .foregroundColor(MonitorColor.textDisabled)
                    .fixedSize()
                    .padding(.trailing, 8)
            }
            between()
            Text(text)
                .font(MonitorFont.mono)
                .foregroundColor(MonitorColor.text)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

extension MonoRow where Between == EmptyView {
    init(gutter: String?, text: AttributedString) {
        self.init(gutter: gutter, text: text) { EmptyView() }
    }
}

/// Numbered lines of TEXT and STREAM bodies.
struct LineRows: View {
    let lines: [String]
    let rows: [TextRow]

    var body: some View {
        let digits = Gutter.digits(lines.count)
        ForEach(rows, id: \.self) { row in
            MonoRow(gutter: Gutter.label(row, digits: digits), text: AttributedString(lines[row.line].characters(row.start, row.end)))
        }
    }
}

/**
 "offset  hex  ascii", the offset and the ASCII column muted. Each 16-byte row is shown as two lines
 of 8 bytes: a full row is wider than a phone, and wrapping it would break the columns.
 */
struct HexRows: View {
    let rows: [HexRow]

    var body: some View {
        ForEach(Array(halves.enumerated()), id: \.offset) { _, half in
            MonoRow(gutter: nil, text: text(half))
        }
    }

    private var halves: [(offset: String, hex: String, ascii: String)] {
        var result: [(offset: String, hex: String, ascii: String)] = []
        for row in rows {
            result.append((row.offset, String(row.hex.prefix(Self.halfHex)), String(row.ascii.prefix(Self.half))))
            let second = String(row.hex.dropFirst(Self.halfHex + 2))
            guard !second.trimmingCharacters(in: .whitespaces).isEmpty else { continue }
            let start = (Int(row.offset, radix: 16) ?? 0) + Self.half
            let digits = String(start, radix: 16)
            let offset = String(repeating: "0", count: max(row.offset.count - digits.count, 0)) + digits
            result.append((offset, second, String(row.ascii.dropFirst(Self.half))))
        }
        return result
    }

    private func text(_ half: (offset: String, hex: String, ascii: String)) -> AttributedString {
        var offset = AttributedString(half.offset)
        offset.foregroundColor = MonitorColor.textSecondary
        var ascii = AttributedString(half.ascii)
        ascii.foregroundColor = MonitorColor.textSecondary
        return offset + AttributedString("  \(half.hex)  ") + ascii
    }

    private static let half = 8
    private static let halfHex = half * 3 - 1
}
