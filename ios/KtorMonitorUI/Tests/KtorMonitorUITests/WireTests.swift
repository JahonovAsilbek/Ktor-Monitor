import XCTest
@testable import KtorMonitorUI

/// The JSON contract with the Kotlin bridge. The fixtures are written by the Kotlin tests
/// (BridgeFixturesTest), so a change on either side that breaks the other fails here.
final class WireTests: XCTestCase {

    func testListState() throws {
        let state = try fixture(ListState.self, "list-state.json")

        XCTAssertEqual(state.calls?.map(\.id), ["login", "cards"])
        let failed = try XCTUnwrap(state.calls?.first)
        XCTAssertTrue(failed.isError)
        XCTAssertNil(failed.responseCode)
        XCTAssertEqual(failed.host, "auth.example.com")
        XCTAssertEqual(state.calls?.last?.path, "/v1/items?page=2")
        XCTAssertEqual(state.calls?.last?.durationMillis, 100)
        XCTAssertEqual(state.filters.statuses, [Option(id: "SUCCESS", label: "2xx")])
        XCTAssertEqual(state.filterCount, 2)
        XCTAssertEqual(state.sort.id, "DURATION_DESCENDING")
        XCTAssertEqual(state.selection, ["cards"])
        XCTAssertTrue(state.isNarrowed)
    }

    func testListStateWhileLoading() throws {
        let state = try fixture(ListState.self, "list-state-loading.json")

        XCTAssertNil(state.calls)
        XCTAssertNil(state.selection)
        XCTAssertEqual(state.options.statuses.map(\.id), ["INFORMATIONAL", "SUCCESS", "REDIRECT", "CLIENT_ERROR", "SERVER_ERROR", "FAILED"])
        XCTAssertEqual(state.exportFormats.last, Option(id: "HAR", label: "HAR"))
    }

    func testDetailWithCodeAndLines() throws {
        let state = try fixture(DetailState.self, "detail-code.json")

        XCTAssertEqual(state.call?.responseHeaders, [Header(name: "Content-Type", value: "application/json")])
        guard case let .lines(lines)? = state.request?.content else { return XCTFail("request is not lines") }
        XCTAssertEqual(lines, ["page=2", "size=20"])
        let response = try XCTUnwrap(state.response)
        XCTAssertTrue(response.isTruncated)
        XCTAssertEqual(response.mode.id, "CODE")
        guard case let .code(language, codeLines, folds) = response.content else { return XCTFail("response is not code") }
        XCTAssertEqual(language, "JSON")
        XCTAssertEqual(codeLines[1].map(\.kind), ["PLAIN", "KEY", "PUNCTUATION", "NUMBER"])
        XCTAssertEqual(folds, [Fold(startLine: 0, endLine: 2, isCollapsedByDefault: false)])
    }

    func testDetailWithHexAndAnImage() throws {
        let state = try fixture(DetailState.self, "detail-media.json")

        guard case let .hex(rows)? = state.request?.content else { return XCTFail("request is not hex") }
        XCTAssertEqual(rows.first?.ascii, "{}")
        guard case let .image(format, data)? = state.response?.content else { return XCTFail("response is not an image") }
        XCTAssertEqual(format, "PNG")
        XCTAssertEqual([UInt8](data), [0x89, 0x50, 0x4E, 0x47])
    }

    func testDetailWithMarkdownInFlight() throws {
        let state = try fixture(DetailState.self, "detail-markdown.json")

        XCTAssertEqual(state.call?.summary.isInProgress, true)
        XCTAssertNil(state.request)
        guard case let .markdown(blocks)? = state.response?.content else { return XCTFail("response is not markdown") }
        XCTAssertEqual(blocks.count, 7)
        XCTAssertEqual(blocks.last, .rule)
        guard case let .listItem(depth, number, _) = blocks[3] else { return XCTFail("block 3 is not a list item") }
        XCTAssertEqual(depth, 1)
        XCTAssertNil(number)
        guard case let .paragraph(spans) = blocks[1] else { return XCTFail("block 1 is not a paragraph") }
        XCTAssertEqual(spans.map(\.isBold), [false, true, false])
        XCTAssertEqual(spans.last?.link, "https://example.com")
    }

    func testDetailWhileLoading() throws {
        let state = try fixture(DetailState.self, "detail-loading.json")

        XCTAssertNil(state.call)
        XCTAssertNil(state.response)
    }

    func testEffects() throws {
        let effects = try fixture([Effect].self, "effects.json")

        XCTAssertEqual(effects, [
            .openCall(id: "cards"),
            .share(name: "calls.har", mimeType: "application/json", content: "{}"),
            .copyText("https://api.example.com/v1/items"),
            .close,
        ])
    }

    func testNotification() throws {
        let update = try fixture(NotificationUpdate.self, "notification.json")

        XCTAssertEqual(update.title, "Recording network activity")
        XCTAssertEqual(update.lines.count, 2)
    }

    func testListEvents() throws {
        try assertEvents("list-events.jsonl", [
            ListEvent.toggleSearch,
            .search(query: "cards"),
            .toggleOnlyErrors,
            .toggleHost("api.example.com"),
            .toggleMethod("GET"),
            .toggleContentType("application/json"),
            .toggleStatus("CLIENT_ERROR"),
            .toggleDuration("OVER_5_S"),
            .clearFilters,
            .sort("SIZE_DESCENDING"),
            .click(id: "cards"),
            .longClick(id: "cards"),
            .startSelection,
            .selectAll,
            .clearSelection,
            .exitSelection,
            .deleteSelected,
            .shareSelected(format: "HAR"),
            .clearAll,
        ])
    }

    func testDetailEvents() throws {
        try assertEvents("detail-events.jsonl", [
            DetailEvent.selectMode(side: .response, mode: "HEX"),
            .copy(format: "CURL"),
            .copyHeaders(side: .request),
            .copyBody(side: .response),
            .share(format: "MARKDOWN"),
        ])
    }

    private func fixture<T: Decodable>(_ type: T.Type, _ name: String) throws -> T {
        try JSONDecoder().decode(type, from: data(name))
    }

    /// Each line of the fixture is one event as the Kotlin side reads it; key order does not matter.
    private func assertEvents<E: Encodable>(_ name: String, _ events: [E], file: StaticString = #filePath, line: UInt = #line) throws {
        let expected = String(decoding: try data(name), as: UTF8.self).split(separator: "\n").map { try! object(Data($0.utf8)) }
        let actual = try events.map { try object(JSONEncoder().encode($0)) }
        XCTAssertEqual(actual, expected, file: file, line: line)
    }

    private func object(_ data: Data) throws -> NSDictionary {
        try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? NSDictionary)
    }

    private func data(_ name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: nil, subdirectory: "Fixtures"), "No fixture \(name)")
        return try Data(contentsOf: url)
    }
}
