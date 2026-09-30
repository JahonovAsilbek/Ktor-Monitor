import Foundation

// What this package sends to the Kotlin bridge (see BridgeEvents.kt): `{"type":"search","query":"cards"}`.

enum ListEvent: Encodable, Equatable {
    case toggleSearch
    case search(query: String)
    case toggleOnlyErrors
    case toggleHost(String)
    case toggleMethod(String)
    case toggleContentType(String)
    case toggleStatus(String)
    case toggleDuration(String)
    case clearFilters
    case sort(String)
    /// A tap: opens the call, or toggles it while selecting.
    case click(id: String)
    /// A long press: starts selecting with this call.
    case longClick(id: String)
    case startSelection
    case selectAll
    case clearSelection
    case exitSelection
    case deleteSelected
    case shareSelected(format: String)
    case clearAll

    private enum CodingKeys: String, CodingKey { case type, query, host, method, contentType, status, duration, sort, id, format }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .toggleSearch: try c.encode("toggleSearch", forKey: .type)
        case let .search(query):
            try c.encode("search", forKey: .type)
            try c.encode(query, forKey: .query)
        case .toggleOnlyErrors: try c.encode("toggleOnlyErrors", forKey: .type)
        case let .toggleHost(host):
            try c.encode("toggleHost", forKey: .type)
            try c.encode(host, forKey: .host)
        case let .toggleMethod(method):
            try c.encode("toggleMethod", forKey: .type)
            try c.encode(method, forKey: .method)
        case let .toggleContentType(contentType):
            try c.encode("toggleContentType", forKey: .type)
            try c.encode(contentType, forKey: .contentType)
        case let .toggleStatus(status):
            try c.encode("toggleStatus", forKey: .type)
            try c.encode(status, forKey: .status)
        case let .toggleDuration(duration):
            try c.encode("toggleDuration", forKey: .type)
            try c.encode(duration, forKey: .duration)
        case .clearFilters: try c.encode("clearFilters", forKey: .type)
        case let .sort(sort):
            try c.encode("sort", forKey: .type)
            try c.encode(sort, forKey: .sort)
        case let .click(id):
            try c.encode("click", forKey: .type)
            try c.encode(id, forKey: .id)
        case let .longClick(id):
            try c.encode("longClick", forKey: .type)
            try c.encode(id, forKey: .id)
        case .startSelection: try c.encode("startSelection", forKey: .type)
        case .selectAll: try c.encode("selectAll", forKey: .type)
        case .clearSelection: try c.encode("clearSelection", forKey: .type)
        case .exitSelection: try c.encode("exitSelection", forKey: .type)
        case .deleteSelected: try c.encode("deleteSelected", forKey: .type)
        case let .shareSelected(format):
            try c.encode("shareSelected", forKey: .type)
            try c.encode(format, forKey: .format)
        case .clearAll: try c.encode("clearAll", forKey: .type)
        }
    }
}

/// REQUEST or RESPONSE.
enum BodySide: String, Encodable {
    case request = "REQUEST"
    case response = "RESPONSE"
}

enum DetailEvent: Encodable, Equatable {
    case selectMode(side: BodySide, mode: String)
    /// URL, CURL, WGET or TEXT.
    case copy(format: String)
    case copyHeaders(side: BodySide)
    /// Copies the body as the current view shows it.
    case copyBody(side: BodySide)
    /// TEXT (as a .http file), MARKDOWN or HAR.
    case share(format: String)

    private enum CodingKeys: String, CodingKey { case type, side, mode, format }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .selectMode(side, mode):
            try c.encode("selectMode", forKey: .type)
            try c.encode(side, forKey: .side)
            try c.encode(mode, forKey: .mode)
        case let .copy(format):
            try c.encode("copy", forKey: .type)
            try c.encode(format, forKey: .format)
        case let .copyHeaders(side):
            try c.encode("copyHeaders", forKey: .type)
            try c.encode(side, forKey: .side)
        case let .copyBody(side):
            try c.encode("copyBody", forKey: .type)
            try c.encode(side, forKey: .side)
        case let .share(format):
            try c.encode("share", forKey: .type)
            try c.encode(format, forKey: .format)
        }
    }
}
