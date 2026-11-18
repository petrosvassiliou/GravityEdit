// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "GravityEditMac",
    defaultLocalization: "en",
    platforms: [
        .macOS(.v15)
    ],
    products: [
        .library(
            name: "GravityEditCore",
            targets: ["GravityEditCore"]
        ),
        .executable(
            name: "GravityEdit",
            targets: ["GravityEditMac"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/sparkle-project/Sparkle", from: "2.6.4")
    ],
    targets: [
        .target(
            name: "GravityEditCore",
            path: "Sources/GravityEditCore",
            swiftSettings: [
                .swiftLanguageMode(.v5)
            ]
        ),
        .executableTarget(
            name: "GravityEditMac",
            dependencies: [
                "GravityEditCore",
                .product(name: "Sparkle", package: "Sparkle")
            ],
            path: "Sources/GravityEditMac",
            swiftSettings: [
                .swiftLanguageMode(.v5)
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-Xlinker", "-rpath",
                    "-Xlinker", "@executable_path/../Frameworks"
                ])
            ]
        ),
        .testTarget(
            name: "GravityEditCoreTests",
            dependencies: ["GravityEditCore"],
            path: "Tests/GravityEditCoreTests",
            swiftSettings: [
                .swiftLanguageMode(.v5)
            ]
        )
    ]
)
