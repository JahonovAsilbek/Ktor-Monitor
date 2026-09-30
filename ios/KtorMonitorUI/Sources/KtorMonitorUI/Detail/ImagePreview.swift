import ImageIO
import SwiftUI
import UIKit

/**
 The image a body holds, decoded off the main thread. SVG has no decoder here, so an SVG body is read
 as code instead; a GIF shows its first frame.
 */
struct ImagePreview: View {
    let format: String
    let data: Data

    @State private var decoded: Decoded = .loading
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        if format == "SVG" {
            DetailNotice(text: "SVG preview is not available; see Code")
        } else {
            Group {
                switch decoded {
                case .loading:
                    ProgressView().padding(.vertical, 16)
                case .failed:
                    DetailNotice(text: "The image could not be decoded")
                case let .ready(image, width, height):
                    VStack(alignment: .leading, spacing: 8) {
                        // At its own size (a favicon stays small), scaled down only to fit.
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .frame(
                                maxWidth: image.size.width / displayScale,
                                maxHeight: min(480, image.size.height / displayScale),
                                alignment: .topLeading
                            )
                            .background(MonitorColor.surface)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .accessibilityLabel("Body image")
                        Text("\(width) × \(height) px, \(format)")
                            .font(MonitorFont.caption)
                            .foregroundColor(MonitorColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .task(id: data) {
                let bytes = data
                decoded = await Task.detached(priority: .userInitiated) { decode(bytes) }.value
            }
        }
    }
}

/// Large images are sampled down: a 250 KB JPEG can still be 6000 px wide.
private func decode(_ data: Data) -> Decoded {
    guard
        let source = CGImageSourceCreateWithData(data as CFData, nil),
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
        let width = properties[kCGImagePropertyPixelWidth] as? Int,
        let height = properties[kCGImagePropertyPixelHeight] as? Int,
        width > 0, height > 0
    else { return .failed }
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: min(max(width, height), maxSidePixels),
    ]
    guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return .failed }
    return .ready(UIImage(cgImage: image), width: width, height: height)
}

private let maxSidePixels = 2_048

private enum Decoded {
    case loading
    case failed
    case ready(UIImage, width: Int, height: Int)
}
