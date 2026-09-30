import SwiftUI

/// Rendered Markdown, one row per block.
struct MarkdownBlocks: View {
    let blocks: [MarkdownBlock]

    var body: some View {
        ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
            MarkdownBlockView(block: block).padding(.vertical, 4)
        }
    }
}

private struct MarkdownBlockView: View {
    let block: MarkdownBlock

    var body: some View {
        switch block {
        case let .heading(level, spans):
            Text(spansText(spans, size: headingSize(level), weight: .bold))
                .padding(.top, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
        case let .paragraph(spans):
            Text(spansText(spans)).frame(maxWidth: .infinity, alignment: .leading)
        case let .listItem(depth, number, spans):
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(number.map { "\($0)." } ?? "•")
                    .font(MonitorFont.body)
                    .foregroundColor(MonitorColor.text)
                    .frame(width: 24, alignment: .leading)
                Text(spansText(spans)).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.leading, 16 * CGFloat(depth))
        case let .quote(spans):
            HStack(spacing: 0) {
                Rectangle().fill(MonitorColor.border).frame(width: 3)
                Text(spansText(spans, italic: true, color: MonitorColor.textSecondary))
                    .padding(.leading, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .fixedSize(horizontal: false, vertical: true)
        case let .codeBlock(_, text):
            ScrollView(.horizontal) {
                Text(text)
                    .font(MonitorFont.mono)
                    .foregroundColor(MonitorColor.text)
                    .fixedSize()
                    .padding(12)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(MonitorColor.surface)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        case .rule:
            Rectangle().fill(MonitorColor.border).frame(height: 1)
        }
    }

    private func headingSize(_ level: Int) -> CGFloat {
        switch level {
        case 1: return 24
        case 2: return 20
        case 3: return 18
        default: return 16
        }
    }

    private func spansText(
        _ spans: [MarkdownSpan],
        size: CGFloat = 14,
        weight: Font.Weight = .regular,
        italic: Bool = false,
        color: Color = MonitorColor.text
    ) -> AttributedString {
        var result = AttributedString()
        for span in spans {
            var part = AttributedString(span.text)
            var font = Font.system(size: size, weight: span.isBold ? .bold : weight, design: span.isCode ? .monospaced : .default)
            if italic || span.isItalic { font = font.italic() }
            part.font = font
            if span.isCode { part.backgroundColor = MonitorColor.surface }
            if let link = span.link, let url = URL(string: link) {
                part.link = url
                part.foregroundColor = MonitorColor.info
            } else {
                part.foregroundColor = color
            }
            result += part
        }
        return result
    }
}
