// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "system-audio-capture",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "system-audio-capture", targets: ["SystemAudioCapture"])
    ],
    targets: [
        .executableTarget(
            name: "SystemAudioCapture",
            path: "Sources/SystemAudioCapture",
            linkerSettings: [
                .linkedFramework("Foundation"),
                .linkedFramework("AVFoundation"),
                .linkedFramework("CoreMedia"),
                .linkedFramework("ScreenCaptureKit")
            ]
        )
    ]
)
