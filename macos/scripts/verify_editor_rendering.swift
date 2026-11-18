#!/usr/bin/env swift
import AppKit
import Foundation

// Automated AppKit + CoreAnimation pixel-rendering verification for GravityEdit's TextKit 1 code canvas.
// Asserts that a multi-page PowerShell script rendered inside the clipped NSScrollView + NSRulerView stack
// produces thousands of visible foreground glyph pixels in the code canvas (never a blank white viewport).

@MainActor
func runVerification() {
    let app = NSApplication.shared
    app.setActivationPolicy(.prohibited)

    let maxDim: CGFloat = 10_000_000
    let window = NSWindow(
        contentRect: NSRect(x: 0, y: 0, width: 1020, height: 680),
        styleMask: [.titled, .closable, .resizable],
        backing: .buffered,
        defer: false
    )

    let scrollView = NSScrollView(frame: NSRect(x: 0, y: 0, width: 1020, height: 680))
    scrollView.wantsLayer = true
    scrollView.clipsToBounds = true
    scrollView.automaticallyAdjustsContentInsets = false
    scrollView.borderType = .noBorder
    scrollView.hasVerticalScroller = true
    scrollView.hasHorizontalScroller = true
    scrollView.drawsBackground = true
    scrollView.backgroundColor = NSColor(srgbRed: 0.992, green: 0.993, blue: 0.996, alpha: 1.0)

    let cs = scrollView.contentSize
    let storage = NSTextStorage()
    let lm = NSLayoutManager()
    lm.allowsNonContiguousLayout = false
    storage.addLayoutManager(lm)

    let tc = NSTextContainer(containerSize: NSSize(width: maxDim, height: maxDim))
    tc.widthTracksTextView = false
    tc.heightTracksTextView = false
    tc.lineFragmentPadding = 8
    lm.addTextContainer(tc)

    let tv = NSTextView(frame: NSRect(origin: .zero, size: cs), textContainer: tc)
    tv.minSize = cs
    tv.maxSize = NSSize(width: maxDim, height: maxDim)
    tv.isVerticallyResizable = true
    tv.isHorizontallyResizable = true
    tv.autoresizingMask = [.width, .height]
    tv.drawsBackground = true
    tv.backgroundColor = NSColor(srgbRed: 0.992, green: 0.993, blue: 0.996, alpha: 1.0)
    tv.alignment = .left
    tv.textContainerInset = NSSize(width: 6, height: 10)

    var lines: [String] = []
    for i in 1...109 {
        lines.append("function Invoke-FileServerShareFix\(i) { [CmdletBinding()] param([string]$ShareName = 'DFS-\(i)') Get-SmbShare -Name $ShareName }")
    }
    let psScript = lines.joined(separator: "\n")
    let font = NSFont.monospacedSystemFont(ofSize: 13.5, weight: .regular)
    let para = NSMutableParagraphStyle()
    para.alignment = .left
    para.lineSpacing = 2.0

    tv.font = font
    tv.textColor = NSColor(srgbRed: 0.11, green: 0.13, blue: 0.17, alpha: 1.0)
    tv.string = psScript
    storage.setAttributes(
        [
            .font: font,
            .foregroundColor: NSColor(srgbRed: 0.03, green: 0.36, blue: 0.76, alpha: 1.0),
            .paragraphStyle: para
        ],
        range: NSRange(location: 0, length: (psScript as NSString).length)
    )

    scrollView.documentView = tv
    let ruler = NSRulerView(scrollView: scrollView, orientation: .verticalRuler)
    ruler.ruleThickness = 58
    ruler.clientView = tv
    scrollView.verticalRulerView = ruler
    scrollView.hasVerticalRuler = true
    scrollView.rulersVisible = true

    window.contentView = scrollView
    window.layoutIfNeeded()
    lm.ensureLayout(for: tc)
    tv.minSize = scrollView.contentSize
    tv.sizeToFit()
    scrollView.displayIfNeeded()

    let origin = tv.textContainerOrigin
    guard origin.x.isFinite && origin.x >= 0 && origin.x <= 20 else {
        fputs("FAIL: Unexpected textContainerOrigin: \(origin)\n", stderr)
        exit(1)
    }
    guard tv.frame.width.isFinite && tv.frame.width >= scrollView.contentSize.width && tv.frame.width <= maxDim else {
        fputs("FAIL: Invalid textView.frame: \(tv.frame)\n", stderr)
        exit(1)
    }

    guard let bitmap = scrollView.bitmapImageRepForCachingDisplay(in: scrollView.bounds) else {
        fputs("FAIL: Unable to allocate NSBitmapImageRep for scrollView\n", stderr)
        exit(1)
    }
    scrollView.cacheDisplay(in: scrollView.bounds, to: bitmap)

    // Sample code area (`x = 80..700, y = 20..500`) and count non-white glyph pixels
    var inkPixels = 0
    for y in 20..<500 {
        for x in 80..<700 {
            if let c = bitmap.colorAt(x: x, y: y)?.usingColorSpace(.sRGB) {
                if c.redComponent < 0.85 || c.greenComponent < 0.85 || c.blueComponent < 0.85 {
                    inkPixels += 1
                }
            }
        }
    }

    print("Verified editor canvas rendering: textView.frame=\(tv.frame), textContainerOrigin=\(origin), glyphInkPixels=\(inkPixels)")
    guard inkPixels > 1500 else {
        fputs("FAIL: Code canvas rendered blank/empty (inkPixels=\(inkPixels))!\n", stderr)
        exit(1)
    }
}

MainActor.assumeIsolated {
    runVerification()
}
