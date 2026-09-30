import SwiftUI

/**
 The request or the response: error, headers, body. One LazyVStack holds all of it, down to the body's
 lines, so a body of thousands of lines draws only what is on screen.

 A failed response shows its error and whatever headers and body did arrive, leaving out the sections
 it has nothing for.
 */
struct MessagePage: View {
    let call: CallDetail
    let side: BodySide
    let message: BodyState?
    let send: (DetailEvent) -> Void

    @State private var headersExpanded = true
    @State private var bodyExpanded = true
    /// Nil until the user folds or unfolds a region: the document's defaults apply.
    @State private var collapsed: Set<Int>?
    @StateObject private var pan = HorizontalPan()

    var body: some View {
        let isResponse = side == .response
        let headers = isResponse ? call.responseHeaders : call.requestHeaders
        let error = isResponse ? call.summary.error : nil

        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if isResponse && call.summary.isInProgress {
                    Waiting()
                } else {
                    if let error {
                        SectionTitle(title: "Error")
                        ErrorBlock(error: error)
                    }
                    if error == nil || !headers.isEmpty {
                        SectionTitle(
                            title: "Headers",
                            detail: headers.isEmpty ? nil : String(headers.count),
                            expanded: headersExpanded,
                            onToggle: { headersExpanded.toggle() },
                            onCopy: headers.isEmpty ? nil : { send(.copyHeaders(side: side)) }
                        )
                        if headersExpanded { Headers(headers: headers) }
                    }
                    if error == nil || message != nil {
                        SectionTitle(
                            title: "Body",
                            detail: message.map { Format.size($0.size) },
                            isTruncated: message?.isTruncated == true,
                            expanded: bodyExpanded,
                            onToggle: { bodyExpanded.toggle() },
                            onCopy: message == nil ? nil : { send(.copyBody(side: side)) }
                        )
                        if bodyExpanded {
                            if let message {
                                Modes(message: message) { send(.selectMode(side: side, mode: $0)) }
                                content(message.content)
                            } else {
                                DetailNotice(text: "No body")
                            }
                        }
                    }
                }
            }
            .padding(EdgeInsets(top: 8, leading: 16, bottom: 16, trailing: 16))
        }
        .onChange(of: message?.content) { _ in
            pan.reset()
            collapsed = nil
        }
    }

    @ViewBuilder
    private func content(_ content: BodyContent) -> some View {
        switch content {
        case let .code(_, lines, folds):
            CodeRows(lines: lines, folds: folds, collapsed: collapsed ?? CodeRows.defaults(folds), pan: pan) { line in
                var current = collapsed ?? CodeRows.defaults(folds)
                if current.contains(line) { current.remove(line) } else { current.insert(line) }
                collapsed = current
            }
        case let .lines(lines):
            LineRows(lines: lines, pan: pan)
        case let .hex(rows):
            HexRows(rows: rows, pan: pan)
        case let .image(format, data):
            ImagePreview(format: format, data: data)
        case let .markdown(blocks):
            MarkdownBlocks(blocks: blocks)
        }
    }
}

/// A section heading; with `onToggle` it folds its section, with `onCopy` it copies it.
private struct SectionTitle: View {
    let title: String
    var detail: String?
    var isTruncated = false
    var expanded = true
    var onToggle: (() -> Void)?
    var onCopy: (() -> Void)?

    var body: some View {
        HStack(spacing: 8) {
            if onToggle != nil {
                Image(systemName: expanded ? "chevron.down" : "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(MonitorColor.textSecondary)
                    .frame(width: 16)
            }
            Text(title).font(MonitorFont.title).foregroundColor(MonitorColor.text)
            if let detail { Text(detail).font(MonitorFont.caption).foregroundColor(MonitorColor.textSecondary) }
            if isTruncated { Text("(truncated)").font(MonitorFont.caption).foregroundColor(MonitorColor.warning) }
            Spacer(minLength: 0)
            // Keeps every title the same height, with or without a button.
            ZStack {
                if let onCopy {
                    Button(action: onCopy) {
                        Image(systemName: "doc.on.doc").foregroundColor(MonitorColor.text)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Copy \(title.lowercased())")
                }
            }
            .frame(width: 40, height: 40)
        }
        .padding(.top, 8)
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { onToggle?() }
        .accessibilityAction(named: expanded ? "Collapse" : "Expand") { onToggle?() }
    }
}

/// One line per value, so repeated headers such as Set-Cookie stay apart.
private struct Headers: View {
    let headers: [Header]

    var body: some View {
        if headers.isEmpty {
            DetailNotice(text: "No headers")
        } else {
            Text(text)
                .font(MonitorFont.body)
                .foregroundColor(MonitorColor.text)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 4)
        }
    }

    private var text: AttributedString {
        var result = AttributedString()
        for (index, header) in headers.enumerated() {
            if index > 0 { result += AttributedString("\n") }
            var name = AttributedString(header.name)
            name.font = MonitorFont.bodyBold
            result += name + AttributedString(": \(header.value)")
        }
        return result
    }
}

private struct Modes: View {
    let message: BodyState
    let onSelect: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(message.modes) { mode in
                    ModeChip(text: mode.label, selected: mode == message.mode) { onSelect(mode.id) }
                }
            }
            .padding(.vertical, 8)
        }
    }
}

private struct ModeChip: View {
    let text: String
    let selected: Bool
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            Text(text)
                .font(MonitorFont.captionMedium)
                .foregroundColor(selected ? MonitorColor.onAccent : MonitorColor.text)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Capsule().fill(selected ? MonitorColor.accent : MonitorColor.surface))
                .overlay(Capsule().stroke(selected ? MonitorColor.accent : MonitorColor.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// The stack trace as it was recorded: not wrapped, so its frames stay one per line.
private struct ErrorBlock: View {
    let error: String

    var body: some View {
        ScrollView(.horizontal) {
            Text(error)
                .font(MonitorFont.mono)
                .foregroundColor(MonitorColor.text)
                .fixedSize()
                .textSelection(.enabled)
                .padding(12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(MonitorColor.errorContainer)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.vertical, 4)
    }
}

private struct Waiting: View {
    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text("Waiting for the response…")
                .font(MonitorFont.body)
                .foregroundColor(MonitorColor.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }
}

struct DetailNotice: View {
    let text: String

    var body: some View {
        Text(text)
            .font(MonitorFont.body)
            .foregroundColor(MonitorColor.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 8)
    }
}
