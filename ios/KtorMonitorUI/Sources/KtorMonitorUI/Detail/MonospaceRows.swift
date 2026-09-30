import SwiftUI

/**
 One sideways offset shared by every row of a body. Rows are not wrapped, and a horizontal ScrollView
 per row would scroll each on its own; one around the whole body would lose the lazy rows. So each
 row draws its text at full width and shifts it by `offset`, and a drag on any row moves them all.
 */
final class HorizontalPan: ObservableObject {
    @Published private(set) var offset: CGFloat = 0

    /// How far the widest row seen so far overflows; grows as rows scroll into view.
    private var maxOffset: CGFloat = 0
    private var dragStart: CGFloat?

    func measured(textWidth: CGFloat, viewport: CGFloat) {
        maxOffset = max(maxOffset, textWidth - viewport)
    }

    func drag(_ translation: CGFloat) {
        let start = dragStart ?? offset
        dragStart = start
        offset = min(max(start - translation, 0), maxOffset)
    }

    func endDrag() {
        dragStart = nil
    }

    func reset() {
        offset = 0
        maxOffset = 0
        dragStart = nil
    }
}

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

extension String {
    /// Characters `start` until `end`.
    func characters(_ start: Int, _ end: Int) -> Substring {
        let from = index(startIndex, offsetBy: start)
        return self[from..<index(from, offsetBy: end - start)]
    }
}

/// A gutter that stays put and text that pans with the rest of the body.
struct MonoRow<Between: View>: View {
    let gutter: String?
    let text: AttributedString
    @ObservedObject var pan: HorizontalPan
    @ViewBuilder var between: () -> Between

    var body: some View {
        HStack(spacing: 0) {
            if let gutter {
                Text(gutter)
                    .font(MonitorFont.mono)
                    .foregroundColor(MonitorColor.textDisabled)
                    .fixedSize()
                    .padding(.trailing, 8)
            }
            between()
            GeometryReader { viewport in
                Text(text)
                    .font(MonitorFont.mono)
                    .foregroundColor(MonitorColor.text)
                    .lineLimit(1)
                    .fixedSize()
                    .background(
                        GeometryReader { measured in
                            Color.clear.onAppear { pan.measured(textWidth: measured.size.width, viewport: viewport.size.width) }
                        }
                    )
                    .offset(x: -pan.offset)
            }
            .frame(height: monoRowHeight)
            .clipped()
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 8)
                .onChanged { pan.drag($0.translation.width) }
                .onEnded { _ in pan.endDrag() }
        )
    }
}

private let monoRowHeight: CGFloat = 16

extension MonoRow where Between == EmptyView {
    init(gutter: String?, text: AttributedString, pan: HorizontalPan) {
        self.init(gutter: gutter, text: text, pan: pan) { EmptyView() }
    }
}

/// Numbered lines of TEXT and STREAM bodies.
struct LineRows: View {
    let lines: [String]
    let pan: HorizontalPan

    var body: some View {
        let rows = Gutter.rows(lines)
        let digits = Gutter.digits(lines.count)
        ForEach(rows, id: \.self) { row in
            MonoRow(gutter: Gutter.label(row, digits: digits), text: AttributedString(lines[row.line].characters(row.start, row.end)), pan: pan)
        }
    }
}

/// "offset  hex  ascii", 16 bytes a row; the offset and the ASCII column are muted.
struct HexRows: View {
    let rows: [HexRow]
    let pan: HorizontalPan

    var body: some View {
        ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
            MonoRow(gutter: nil, text: text(row), pan: pan)
        }
    }

    private func text(_ row: HexRow) -> AttributedString {
        var offset = AttributedString(row.offset)
        offset.foregroundColor = MonitorColor.textSecondary
        var ascii = AttributedString(row.ascii)
        ascii.foregroundColor = MonitorColor.textSecondary
        return offset + AttributedString("  \(row.hex)  ") + ascii
    }
}
