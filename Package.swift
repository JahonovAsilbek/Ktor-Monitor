// swift-tools-version:5.9
import PackageDescription

// SwiftPM reads packages from the repository root only, so the manifest sits here and points into ios/.
let package = Package(
    name: "KtorMonitorUI",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "KtorMonitorUI", targets: ["KtorMonitorUI"]),
    ],
    targets: [
        .target(
            name: "KtorMonitorUI",
            path: "ios/KtorMonitorUI/Sources/KtorMonitorUI"
        ),
        .testTarget(
            name: "KtorMonitorUITests",
            dependencies: ["KtorMonitorUI"],
            path: "ios/KtorMonitorUI/Tests/KtorMonitorUITests",
            resources: [.copy("Fixtures")]
        ),
    ]
)
