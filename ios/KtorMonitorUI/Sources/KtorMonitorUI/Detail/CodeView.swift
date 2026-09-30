import SwiftUI

/// Where each line of a code body goes: the rows shown, given the regions folded.
struct CodeLayout {
    let regions: [Int: Fold]
    let rows: [TextRow]
    let digits: Int

    init(lines: [[CodeSpan]], folds: [Fold], collapsed: Set<Int>) {
        regions = Self.regions(folds)
        digits = Gutter.digits(lines.count)
        var rows: [TextRow] = []
        var line = 0
        while line < lines.count {
            rows.addLine(line, length: lines[line].reduce(0) { $0 + $1.text.count })
            if let region = regions[line], collapsed.contains(line) {
                line = max(region.endLine, line) + 1
            } else {
                line += 1
            }
        }
        self.rows = rows
    }

    // Two regions can start on one line; the widest wins, as it hides the others anyway.
    static func regions(_ folds: [Fold]) -> [Int: Fold] {
        Dictionary(folds.sorted { $0.endLine < $1.endLine }.map { ($0.startLine, $0) }, uniquingKeysWith: { _, widest in widest })
    }
}

/// Highlighted code with line numbers; a line that opens a region folds it on a tap.
struct CodeRows: View {
    let lines: [[CodeSpan]]
    let layout: CodeLayout
    let collapsed: Set<Int>
    let pan: HorizontalPan
    let onToggle: (Int) -> Void

    var body: some View {
        ForEach(layout.rows, id: \.self) { row in
            let foldable = row.isFirstOfLine && layout.regions[row.line] != nil
            let isCollapsed = collapsed.contains(row.line)
            MonoRow(
                gutter: Gutter.label(row, digits: layout.digits),
                text: text(of: row, ellipsis: isCollapsed && row.isLastOfLine),
                pan: pan
            ) {
                FoldMark(visible: foldable, collapsed: isCollapsed)
            }
            .onTapGesture { if foldable { onToggle(row.line) } }
            .accessibilityActions {
                if foldable {
                    Button(isCollapsed ? "Expand" : "Collapse") { onToggle(row.line) }
                }
            }
        }
    }

    /// Characters `row.start` until `row.end` of the line, coloured; `ellipsis` marks a folded region.
    private func text(of row: TextRow, ellipsis: Bool) -> AttributedString {
        var result = AttributedString()
        var at = 0
        for span in lines[row.line] {
            let count = span.text.count
            let spanEnd = at + count
            if spanEnd > row.start && at < row.end {
                var part = AttributedString(span.text.characters(max(row.start, at) - at, min(row.end, spanEnd) - at))
                part.foregroundColor = tokenColor(span.kind)
                if span.kind == "HEADING" { part.font = MonitorFont.mono.bold() }
                if span.kind == "EMPHASIS" { part.font = MonitorFont.mono.italic() }
                result += part
            }
            if spanEnd >= row.end { break }
            at = spanEnd
        }
        if ellipsis {
            var mark = AttributedString(" …")
            mark.foregroundColor = MonitorColor.textSecondary
            result += mark
        }
        return result
    }
}

private struct FoldMark: View {
    let visible: Bool
    let collapsed: Bool

    var body: some View {
        ZStack(alignment: .leading) {
            if visible {
                Image(systemName: collapsed ? "chevron.right" : "chevron.down")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundColor(MonitorColor.textSecondary)
            }
        }
        .frame(width: 16, alignment: .leading)
    }
}
