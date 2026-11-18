#!/usr/bin/env swift
import AppKit
import CoreGraphics
import Foundation

// Renders a macOS Tahoe Liquid Glass squircle icon set into <output.iconset>
guard CommandLine.arguments.count >= 2 else {
    fputs("Usage: generate_icon.swift <path/to/AppIcon.iconset>\n", stderr)
    exit(1)
}

let iconsetURL = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
try FileManager.default.createDirectory(at: iconsetURL, withIntermediateDirectories: true)

func renderIcon(pixelSize: Int) -> Data? {
    let size = CGFloat(pixelSize)
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: pixelSize,
        pixelsHigh: pixelSize,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    ) else { return nil }

    NSGraphicsContext.saveGraphicsState()
    guard let ctx = NSGraphicsContext(bitmapImageRep: rep)?.cgContext else {
        NSGraphicsContext.restoreGraphicsState()
        return nil
    }

    let inset = size * 0.085
    let rect = CGRect(x: inset, y: inset, width: size - inset * 2, height: size - inset * 2)
    let radius = rect.width * 0.225
    let squircle = CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)

    // Deep Tahoe sapphire-to-cyan gradient base
    ctx.saveGState()
    ctx.addPath(squircle)
    ctx.clip()

    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let bgColors = [
        CGColor(red: 0.05, green: 0.16, blue: 0.42, alpha: 1.0),
        CGColor(red: 0.09, green: 0.44, blue: 0.88, alpha: 1.0),
        CGColor(red: 0.18, green: 0.78, blue: 0.96, alpha: 1.0)
    ] as CFArray
    if let grad = CGGradient(colorsSpace: colorSpace, colors: bgColors, locations: [0.0, 0.55, 1.0]) {
        ctx.drawLinearGradient(
            grad,
            start: CGPoint(x: rect.minX, y: rect.minY),
            end: CGPoint(x: rect.maxX, y: rect.maxY),
            options: []
        )
    }

    // Top Liquid Glass specular lens highlight
    let highlightRect = CGRect(x: rect.minX, y: rect.midY, width: rect.width, height: rect.height * 0.55)
    let glassColors = [
        CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.36),
        CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.02)
    ] as CFArray
    if let glassGrad = CGGradient(colorsSpace: colorSpace, colors: glassColors, locations: [0.0, 1.0]) {
        ctx.drawLinearGradient(
            glassGrad,
            start: CGPoint(x: highlightRect.midX, y: highlightRect.maxY),
            end: CGPoint(x: highlightRect.midX, y: highlightRect.minY),
            options: []
        )
    }
    ctx.restoreGState()

    // Subtle Liquid Glass inner rim stroke
    ctx.saveGState()
    ctx.addPath(squircle)
    ctx.setStrokeColor(CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.42))
    ctx.setLineWidth(max(1.0, size * 0.015))
    ctx.strokePath()
    ctx.restoreGState()

    // Draw crisp PowerShell ">_" glyph in center
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let label: NSString = ">_"
    let font = NSFont.monospacedSystemFont(ofSize: size * 0.38, weight: .bold)
    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(0.32)
    shadow.shadowBlurRadius = size * 0.04
    shadow.shadowOffset = NSSize(width: 0, height: -size * 0.02)

    let attrs: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: NSColor.white,
        .shadow: shadow
    ]
    let textSize = label.size(withAttributes: attrs)
    let drawPoint = NSPoint(
        x: (size - textSize.width) * 0.5,
        y: (size - textSize.height) * 0.5
    )
    label.draw(at: drawPoint, withAttributes: attrs)

    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])
}

let specs: [(String, Int)] = [
    ("icon_16x16.png", 16),
    ("icon_16x16@2x.png", 32),
    ("icon_32x32.png", 32),
    ("icon_32x32@2x.png", 64),
    ("icon_128x128.png", 128),
    ("icon_128x128@2x.png", 256),
    ("icon_256x256.png", 256),
    ("icon_256x256@2x.png", 512),
    ("icon_512x512.png", 512),
    ("icon_512x512@2x.png", 1024)
]

for (filename, px) in specs {
    if let png = renderIcon(pixelSize: px) {
        try png.write(to: iconsetURL.appendingPathComponent(filename))
    }
}
