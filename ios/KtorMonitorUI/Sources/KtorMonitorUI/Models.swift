import Foundation

// The JSON the Kotlin bridge sends (see BridgeModels.kt). Enum values are Kotlin enum names.

struct Option: Decodable, Hashable, Identifiable {
    let id: String
    let label: String
}

struct ListState: Decodable, Equatable {
    /// Nil while the first result is loading.
    let calls: [CallRow]?
    let totalCount: Int
    let isSearchVisible: Bool
    let query: String
    let onlyErrors: Bool
    let isNarrowed: Bool
    let filters: Filters
    let filterCount: Int
    /// The values each filter can take, from the calls recorded.
    let options: Filters
    let sort: Option
    let sorts: [Option]
    let exportFormats: [Option]
    /// Nil outside selection mode.
    let selection: [String]?

    var isSelecting: Bool { selection != nil }
}

struct Filters: Decodable, Equatable {
    let hosts: [String]
    let methods: [String]
    let contentTypes: [String]
    let statuses: [Option]
    let durations: [Option]
}

struct CallRow: Decodable, Equatable, Identifiable, Hashable {
    let id: String
    let groupId: String
    let attempt: Int
    let method: String
    let url: String
    let host: String
    let path: String
    let isSecure: Bool
    let `protocol`: String?
    let requestTime: Int64
    let responseTime: Int64?
    let durationMillis: Int64?
    let requestContentType: String?
    let responseContentType: String?
    let requestSize: Int64?
    let responseSize: Int64?
    let responseCode: Int?
    let error: String?
    let isInProgress: Bool
    let isRedirect: Bool
    let isError: Bool
    let kind: Option
}

struct DetailState: Decodable, Equatable {
    /// Nil while loading.
    let call: CallDetail?
    let request: BodyState?
    let response: BodyState?
}

struct CallDetail: Decodable, Equatable {
    let summary: CallRow
    let requestHeaders: [Header]
    let responseHeaders: [Header]
}

struct Header: Decodable, Equatable, Hashable {
    let name: String
    let value: String
}

struct BodyState: Decodable, Equatable {
    let modes: [Option]
    let mode: Option
    let size: Int64
    let isTruncated: Bool
    let content: BodyContent
}

enum BodyContent: Decodable, Equatable {
    case code(language: String, lines: [[CodeSpan]], folds: [Fold])
    /// TEXT and STREAM.
    case lines([String])
    case hex([HexRow])
    case image(format: String, data: Data)
    case markdown([MarkdownBlock])

    private enum CodingKeys: String, CodingKey { case type, language, lines, folds, rows, format, base64, blocks }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "code":
            self = .code(
                language: try c.decode(String.self, forKey: .language),
                lines: try c.decode([[CodeSpan]].self, forKey: .lines),
                folds: try c.decode([Fold].self, forKey: .folds)
            )
        case "lines": self = .lines(try c.decode([String].self, forKey: .lines))
        case "hex": self = .hex(try c.decode([HexRow].self, forKey: .rows))
        case "image":
            let base64 = try c.decode(String.self, forKey: .base64)
            guard let data = Data(base64Encoded: base64) else {
                throw DecodingError.dataCorruptedError(forKey: .base64, in: c, debugDescription: "Not base64")
            }
            self = .image(format: try c.decode(String.self, forKey: .format), data: data)
        case "markdown": self = .markdown(try c.decode([MarkdownBlock].self, forKey: .blocks))
        case let type:
            throw DecodingError.dataCorruptedError(forKey: .type, in: c, debugDescription: "Unknown body content \(type)")
        }
    }
}

struct CodeSpan: Decodable, Equatable, Hashable {
    let text: String
    /// PLAIN, KEY, STRING, NUMBER, KEYWORD, PUNCTUATION, TAG, ATTRIBUTE, COMMENT, HEADING, EMPHASIS, LINK.
    let kind: String
}

/// Lines startLine...endLine (0-based) fold into startLine.
struct Fold: Decodable, Equatable, Hashable {
    let startLine: Int
    let endLine: Int
    let isCollapsedByDefault: Bool
}

struct HexRow: Decodable, Equatable, Hashable {
    let offset: String
    let hex: String
    let ascii: String
}

struct MarkdownSpan: Decodable, Equatable, Hashable {
    let text: String
    let isBold: Bool
    let isItalic: Bool
    let isCode: Bool
    let link: String?
}

enum MarkdownBlock: Decodable, Equatable, Hashable {
    case heading(level: Int, spans: [MarkdownSpan])
    case paragraph([MarkdownSpan])
    /// depth 0 at the outermost level; number nil for a bullet.
    case listItem(depth: Int, number: Int?, spans: [MarkdownSpan])
    case quote([MarkdownSpan])
    case codeBlock(language: String?, text: String)
    case rule

    private enum CodingKeys: String, CodingKey { case type, level, spans, depth, number, language, text }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "heading": self = .heading(level: try c.decode(Int.self, forKey: .level), spans: try c.decode([MarkdownSpan].self, forKey: .spans))
        case "paragraph": self = .paragraph(try c.decode([MarkdownSpan].self, forKey: .spans))
        case "listItem":
            self = .listItem(
                depth: try c.decode(Int.self, forKey: .depth),
                number: try c.decodeIfPresent(Int.self, forKey: .number),
                spans: try c.decode([MarkdownSpan].self, forKey: .spans)
            )
        case "quote": self = .quote(try c.decode([MarkdownSpan].self, forKey: .spans))
        case "codeBlock": self = .codeBlock(language: try c.decodeIfPresent(String.self, forKey: .language), text: try c.decode(String.self, forKey: .text))
        case "rule": self = .rule
        case let type:
            throw DecodingError.dataCorruptedError(forKey: .type, in: c, debugDescription: "Unknown markdown block \(type)")
        }
    }
}

enum Effect: Decodable, Equatable {
    case openCall(id: String)
    case share(name: String, mimeType: String, content: String)
    case copyText(String)
    /// The open call was deleted.
    case close

    private enum CodingKeys: String, CodingKey { case type, id, name, mimeType, content, text }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "openCall": self = .openCall(id: try c.decode(String.self, forKey: .id))
        case "share":
            self = .share(
                name: try c.decode(String.self, forKey: .name),
                mimeType: try c.decode(String.self, forKey: .mimeType),
                content: try c.decode(String.self, forKey: .content)
            )
        case "copyText": self = .copyText(try c.decode(String.self, forKey: .text))
        case "close": self = .close
        case let type:
            throw DecodingError.dataCorruptedError(forKey: .type, in: c, debugDescription: "Unknown effect \(type)")
        }
    }
}

struct NotificationUpdate: Decodable, Equatable {
    let title: String
    let lines: [String]
}
