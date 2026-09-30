import SwiftUI

/// Highlighted code with line numbers; a line that opens a region folds it on a tap.
struct CodeRows: View {
    let lines: [[CodeSpan]]
    let folds: [Fold]
    let collapsed: Set<Int>
    let pan: HorizontalPan
    let onToggle: (Int) -> Void

    var body: some View {
        let regions = Self.regions(folds)
        let digits = Gutter.digits(lines.count)
        ForEach(rows(regions), id: \.self) { row in
            let foldable = row.isFirstOfLine && regions[row.line] != nil
            let isCollapsed = collapsed.contains(row.line)
            MonoRow(
                gutter: Gutter.label(row, digits: digits),
                text: text(of: row, ellipsis: isCollapsed && row.isLastOfLine),
                pan: pan
            ) {
                FoldMark(visible: foldable, collapsed: isCollapsed)
            }
            .onTapGesture { if foldable { onToggle(row.line) } }
            .accessibilityAction(named: isCollapsed ? "Expand" : "Collapse") { if foldable { onToggle(row.line) } }
        }
    }

    /// The regions folded before the user touches any.
    static func defaults(_ folds: [Fold]) -> Set<Int> {
        Set(regions(folds).values.filter(\.isCollapsedByDefault).map(\.startLine))
    }

    // Two regions can start on one line; the widest wins, as it hides the others anyway.
    private static func regions(_ folds: [Fold]) -> [Int: Fold] {
        Dictionary(folds.sorted { $0.endLine < $1.endLine }.map { ($0.startLine, $0) }, uniquingKeysWith: { _, widest in widest })
    }

    private func rows(_ regions: [Int: Fold]) -> [TextRow] {
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
        return rows
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
