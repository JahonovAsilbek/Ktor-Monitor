import SwiftUI

/// Every field is selectable on its own; a long URL wraps.
struct SummaryPage: View {
    let call: CallRow

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Field(label: "Status") { Status(call: call) }
                ForEach(fields, id: \.0) { label, value in
                    Field(label: label) {
                        Text(value)
                            .font(MonitorFont.body)
                            .foregroundColor(MonitorColor.text)
                            .textSelection(.enabled)
                    }
                }
            }
            .padding(EdgeInsets(top: 8, leading: 16, bottom: 16, trailing: 16))
        }
    }

    private var fields: [(String, String)] {
        let requestSize = call.requestSize ?? 0
        let responseSize = call.responseSize ?? 0
        return [
            ("URL", call.url),
            ("Method", call.method),
            ("Protocol", call.protocol ?? none),
            ("Attempt", call.attempt > 1 ? "\(call.attempt), retry of an earlier attempt" : "\(call.attempt)"),
            ("Request time", Format.dateTime(call.requestTime)),
            ("Response time", call.responseTime.map(Format.dateTime) ?? none),
            ("Duration", call.durationMillis.map(Format.duration) ?? none),
            ("Request size", Format.size(requestSize)),
            ("Response size", call.isInProgress ? none : Format.size(responseSize)),
            ("Total size", Format.size(requestSize + responseSize)),
        ]
    }

    private let none = "—"
}

private struct Field<Value: View>: View {
    let label: String
    @ViewBuilder let value: () -> Value

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(MonitorFont.caption)
                .foregroundColor(MonitorColor.textSecondary)
            value()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct Status: View {
    let call: CallRow

    var body: some View {
        if let code = call.responseCode {
            Text("\(code) \(reasonPhrase(code))")
                .font(MonitorFont.bodyMedium)
                .foregroundColor(statusColor(call))
                .textSelection(.enabled)
        } else if let error = call.error {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Image(systemName: "exclamationmark.circle.fill").foregroundColor(MonitorColor.error)
                Text("Failed: \(error.split(separator: "\n", omittingEmptySubsequences: false).first.map(String.init) ?? error)")
                    .font(MonitorFont.bodyMedium)
                    .foregroundColor(MonitorColor.error)
                    .textSelection(.enabled)
            }
        } else {
            HStack(spacing: 8) {
                ProgressView()
                Text("In progress")
                    .font(MonitorFont.bodyMedium)
                    .foregroundColor(statusColor(call))
            }
        }
    }
}

/// The standard reason phrase, in English whatever the device language.
private func reasonPhrase(_ code: Int) -> String {
    // "Unknown Status Code" is what Android shows (Ktor's description) for a code it does not know.
    reasonPhrases[code] ?? "Unknown Status Code"
}

private let reasonPhrases: [Int: String] = [
    100: "Continue", 101: "Switching Protocols", 102: "Processing",
    200: "OK", 201: "Created", 202: "Accepted", 203: "Non-Authoritative Information", 204: "No Content",
    205: "Reset Content", 206: "Partial Content", 207: "Multi-Status",
    300: "Multiple Choices", 301: "Moved Permanently", 302: "Found", 303: "See Other", 304: "Not Modified",
    305: "Use Proxy", 307: "Temporary Redirect", 308: "Permanent Redirect",
    400: "Bad Request", 401: "Unauthorized", 402: "Payment Required", 403: "Forbidden", 404: "Not Found",
    405: "Method Not Allowed", 406: "Not Acceptable", 407: "Proxy Authentication Required", 408: "Request Timeout",
    409: "Conflict", 410: "Gone", 411: "Length Required", 412: "Precondition Failed", 413: "Payload Too Large",
    414: "Request-URI Too Long", 415: "Unsupported Media Type", 416: "Requested Range Not Satisfiable",
    417: "Expectation Failed", 422: "Unprocessable Entity", 423: "Locked", 424: "Failed Dependency",
    425: "Too Early", 426: "Upgrade Required", 429: "Too Many Requests", 431: "Request Header Fields Too Large",
    500: "Internal Server Error", 501: "Not Implemented", 502: "Bad Gateway", 503: "Service Unavailable",
    504: "Gateway Timeout", 505: "HTTP Version Not Supported", 506: "Variant Also Negotiates",
    507: "Insufficient Storage",
]
