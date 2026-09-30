import SwiftUI

/// One call in the list. `selected` is nil outside selection mode.
struct CallRowView: View {
    let call: CallRow
    let highlighted: Bool
    let selected: Bool?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let selected {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundColor(selected ? MonitorColor.accent : MonitorColor.border)
                    .padding(.top, 2)
                    .accessibilityLabel(selected ? "Selected" : "Not selected")
            }
            VStack(spacing: 4) {
                status
                kindBadge
            }
            .frame(width: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(call.method) \(call.path)")
                    .font(MonitorFont.bodyBold)
                    .foregroundColor(call.isError ? MonitorColor.error : MonitorColor.text)
                    .lineLimit(2)
                HStack(spacing: 4) {
                    Image(systemName: call.isSecure ? "lock.fill" : "lock.open.fill")
                        .font(.system(size: 10))
                        .foregroundColor(call.isSecure ? MonitorColor.textSecondary : MonitorColor.error)
                        .accessibilityLabel(call.isSecure ? "Secure" : "Not secure")
                    Text(call.host).lineLimit(1)
                }
                .font(MonitorFont.caption)
                .foregroundColor(MonitorColor.textSecondary)
                Text(timing)
                    .font(MonitorFont.caption)
                    .foregroundColor(MonitorColor.textSecondary)
                    .lineLimit(1)
                if call.isInProgress {
                    Text("In progress…")
                        .font(MonitorFont.caption.italic())
                        .foregroundColor(MonitorColor.textSecondary)
                } else if let error = call.error {
                    Text(error.components(separatedBy: .newlines).first ?? error)
                        .font(MonitorFont.caption.italic())
                        .foregroundColor(MonitorColor.error)
                        .lineLimit(3)
                }
                if call.attempt > 1 {
                    Text("retry · attempt \(call.attempt)")
                        .font(MonitorFont.caption)
                        .foregroundColor(MonitorColor.warning)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    /// The status code; a spinner while waiting; a warning when the call failed with no response.
    @ViewBuilder
    private var status: some View {
        if call.isInProgress {
            ProgressView()
                .tint(MonitorColor.textSecondary)
                .frame(height: 24)
        } else if let code = call.responseCode {
            HStack(spacing: 2) {
                Text("\(code)")
                    .font(MonitorFont.title)
                    .foregroundColor(statusColor(call))
                if call.isRedirect {
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(MonitorColor.warning)
                        .accessibilityLabel("Redirect")
                }
            }
            .frame(height: 24)
        } else {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.system(size: 20))
                .foregroundColor(MonitorColor.error)
                .frame(height: 24)
                .accessibilityLabel("Failed")
        }
    }

    private var kindBadge: some View {
        Text(call.kind.label)
            .font(MonitorFont.captionMedium)
            .foregroundColor(kindColor(call.kind.id))
            .lineLimit(1)
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 4).fill(kindColor(call.kind.id).opacity(0.16)))
    }

    /// Start time · duration · response size, leaving out what has not arrived yet.
    private var timing: String {
        [Format.clock(call.requestTime), call.durationMillis.map(Format.duration), call.responseSize.map(Format.size)]
            .compactMap { $0 }
            .joined(separator: " · ")
    }
}
