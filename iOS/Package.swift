// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NaviSarthiCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "NaviSarthiCore", targets: ["NaviSarthiCore"])],
    targets: [
        .target(name: "NaviSarthiCore", path: "Sources/Core"),
        .testTarget(name: "NaviSarthiCoreTests", dependencies: ["NaviSarthiCore"], path: "Tests")
    ]
)
