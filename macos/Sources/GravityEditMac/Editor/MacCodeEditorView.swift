import AppKit
import GravityEditCore
import SwiftUI

// MARK: - Custom AppKit NSTextView with Page Guides, Current-Line Highlight, Invisibles & Auto-Indent

public final class GravityCodeTextView: NSTextView {
    /// Finite upper bound (`1.0e7 < 2^24`) per Apple TextKit 1 documentation so CoreGraphics & CALayer
    /// tile backing stores never overflow IEEE-754 Float32 (`DBL_MAX` causes blank layer rendering).
    public static let maxLayoutDimension: CGFloat = 10_000_000

    public var strongTextStorage: NSTextStorage?
    public var syntaxColors: EditorSyntaxColors = .tahoeMidnight
    public var drawPageGuides: Bool = true
    public var drawCurrentLine: Bool = true
    public var drawInvisibles: Bool = false
    public var configuredTabWidth: Int = 4
    public var onTriggerCompletion: (() -> Void)?
    public var onAcceptCompletionKey: (() -> Bool)?
    public var onMoveCompletionSelection: ((Int) -> Bool)?
    public var onDismissCompletion: (() -> Void)?
    public var onFocusGained: (() -> Void)?

    public override func becomeFirstResponder() -> Bool {
        let ok = super.becomeFirstResponder()
        if ok { onFocusGained?() }
        return ok
    }

    public override func mouseDown(with event: NSEvent) {
        onFocusGained?()
        onDismissCompletion?()
        super.mouseDown(with: event)
    }

    public override func keyDown(with event: NSEvent) {
        let keyCode = event.keyCode
        let flags = event.modifierFlags.intersection(.deviceIndependentFlagsMask)

        // Option+Space (49) or F5 (96): manually trigger PowerShell completion
        if (keyCode == 49 && flags == .option) || keyCode == 96 {
            onTriggerCompletion?()
            return
        }

        // Escape (53): dismiss completion if open
        if keyCode == 53 {
            onDismissCompletion?()
        }

        // Up (126) / Down (125) arrow navigation when completion list is open
        if keyCode == 125, flags.isEmpty, onMoveCompletionSelection?(1) == true {
            return
        }
        if keyCode == 126, flags.isEmpty, onMoveCompletionSelection?(-1) == true {
            return
        }

        // Tab (48) or Return (36) when completion popup has active item
        if keyCode == 48 && flags.isEmpty {
            if onAcceptCompletionKey?() == true {
                return
            }
            let spaces = String(repeating: " ", count: max(1, configuredTabWidth))
            insertText(spaces, replacementRange: selectedRange())
            return
        }

        // Smart auto-indent on Return (36)
        if keyCode == 36 && flags.isEmpty {
            if onAcceptCompletionKey?() == true {
                return
            }
            let ns = string as NSString
            let sel = selectedRange()
            let lineRange = ns.lineRange(for: NSRange(location: sel.location, length: 0))
            let linePrefixLen = max(0, sel.location - lineRange.location)
            let beforeCursor = ns.substring(with: NSRange(location: lineRange.location, length: linePrefixLen))
            let leadingWS = String(beforeCursor.prefix(while: { $0 == " " || $0 == "\t" }))
            let trimmedBefore = beforeCursor.trimmingCharacters(in: .whitespaces)
            let extraIndent = (trimmedBefore.hasSuffix("{") || trimmedBefore.hasSuffix("("))
                ? String(repeating: " ", count: max(1, configuredTabWidth))
                : ""
            insertText("\n" + leadingWS + extraIndent, replacementRange: sel)
            return
        }

        super.keyDown(with: event)
    }

    public override func drawBackground(in rect: NSRect) {
        super.drawBackground(in: rect)

        guard let lm = layoutManager, let tc = textContainer else { return }

        // 1. Current line highlight
        if drawCurrentLine {
            let ns = string as NSString
            let selLoc = min(selectedRange().location, ns.length)
            var lineRect: NSRect
            if ns.length == 0 {
                lineRect = NSRect(x: 0, y: 0, width: bounds.width, height: (font?.pointSize ?? 13.5) * 1.45)
            } else if selLoc == ns.length && ns.character(at: ns.length - 1) == 10 && !lm.extraLineFragmentRect.isEmpty {
                lineRect = lm.extraLineFragmentRect
            } else {
                let lineCharRange = ns.lineRange(for: NSRange(location: selLoc, length: 0))
                let glyphRange = lm.glyphRange(forCharacterRange: lineCharRange, actualCharacterRange: nil)
                lineRect = lm.boundingRect(forGlyphRange: glyphRange, in: tc)
            }
            if lineRect.height < 14 {
                lineRect.size.height = (font?.pointSize ?? 13.5) * 1.45
            }
            let highlightRect = NSRect(
                x: bounds.minX,
                y: lineRect.minY + textContainerOrigin.y,
                width: bounds.width,
                height: lineRect.height
            )
            if highlightRect.intersects(rect) {
                syntaxColors.currentLineHighlight.setFill()
                highlightRect.fill()
            }
        }

        // 2. 80-col & 120-col Page Guide hairlines
        if drawPageGuides, let editorFont = font {
            let charWidth = ("M" as NSString).size(withAttributes: [.font: editorFont]).width
            let originX = textContainerOrigin.x + textContainerInset.width + tc.lineFragmentPadding
            syntaxColors.pageGuideHairline.setStroke()

            for col in [80, 120] {
                let x = round(originX + CGFloat(col) * charWidth) + 0.5
                if x >= rect.minX - 2 && x <= rect.maxX + 2 {
                    let path = NSBezierPath()
                    path.lineWidth = 1.0
                    path.move(to: NSPoint(x: x, y: rect.minY))
                    path.line(to: NSPoint(x: x, y: rect.maxY))
                    path.stroke()
                }
            }
        }
    }

    public override func draw(_ dirtyRect: NSRect) {
        super.draw(dirtyRect)

        guard drawInvisibles,
              let lm = layoutManager,
              let tc = textContainer,
              let editorFont = font else {
            return
        }

        let visibleGlyphs = lm.glyphRange(forBoundingRect: dirtyRect, in: tc)
        let charRange = lm.characterRange(forGlyphRange: visibleGlyphs, actualGlyphRange: nil)
        let ns = string as NSString
        let maxEnd = min(ns.length, charRange.location + charRange.length)
        guard charRange.location < maxEnd else { return }

        let attrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.monospacedSystemFont(ofSize: max(9, editorFont.pointSize - 2), weight: .regular),
            .foregroundColor: syntaxColors.invisibles
        ]

        var idx = charRange.location
        while idx < maxEnd {
            let ch = ns.character(at: idx)
            let symbol: NSString?
            switch ch {
            case 32: symbol = "·"
            case 9:  symbol = "⇥"
            case 10: symbol = "¬"
            default: symbol = nil
            }
            if let sym = symbol {
                let gIdx = lm.glyphIndexForCharacter(at: idx)
                let frag = lm.lineFragmentRect(forGlyphAt: gIdx, effectiveRange: nil)
                let loc = lm.location(forGlyphAt: gIdx)
                let pt = NSPoint(
                    x: frag.minX + loc.x + textContainerOrigin.x,
                    y: frag.minY + textContainerOrigin.y + 1
                )
                sym.draw(at: pt, withAttributes: attrs)
            }
            idx += 1
        }
    }
}

// MARK: - Synchronized Gutter (Line Numbers + PSScriptAnalyzer Dots + Code Folding Chevrons)

public final class GravityLineNumberRulerView: NSRulerView {
    public var syntaxColors: EditorSyntaxColors = .tahoeMidnight
    public var editorFontSize: CGFloat = 13.5
    public var foldRegionsByStartLine: [Int: FoldRegion] = [:]
    public var foldedStartLines: Set<Int> = []
    public var diagnosticsByLine: [Int: DiagnosticSeverity] = [:]
    public var onToggleFoldAtLine: ((Int) -> Void)?

    public init(textView: NSTextView) {
        super.init(scrollView: textView.enclosingScrollView, orientation: .verticalRuler)
        self.clientView = textView
        self.ruleThickness = 58
    }

    required init(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public override func mouseDown(with event: NSEvent) {
        guard let textView = clientView as? NSTextView,
              let lm = textView.layoutManager,
              let tc = textView.textContainer else {
            super.mouseDown(with: event)
            return
        }

        let localPt = convert(event.locationInWindow, from: nil)
        // Rightmost 18pt column is the code-folding chevron hit zone
        guard localPt.x >= ruleThickness - 20 else {
            super.mouseDown(with: event)
            return
        }

        let textPt = NSPoint(
            x: 4,
            y: localPt.y + textView.visibleRect.minY - textView.textContainerOrigin.y
        )
        let glyphIdx = lm.glyphIndex(for: textPt, in: tc)
        let charIdx = lm.characterIndexForGlyph(at: glyphIdx)
        let ns = textView.string as NSString
        var lineIdx = 0
        var i = 0
        let limit = min(charIdx, ns.length)
        while i < limit {
            if ns.character(at: i) == 10 { lineIdx += 1 }
            i += 1
        }

        if foldRegionsByStartLine[lineIdx] != nil {
            onToggleFoldAtLine?(lineIdx)
            needsDisplay = true
        }
    }

    public override func drawHashMarksAndLabels(in rect: NSRect) {
        syntaxColors.gutterBackground.setFill()
        rect.fill()

        syntaxColors.gutterSeparator.setStroke()
        let sep = NSBezierPath()
        sep.lineWidth = 1.0
        sep.move(to: NSPoint(x: bounds.maxX - 0.5, y: rect.minY))
        sep.line(to: NSPoint(x: bounds.maxX - 0.5, y: rect.maxY))
        sep.stroke()

        guard let textView = clientView as? NSTextView,
              let lm = textView.layoutManager,
              let tc = textView.textContainer else {
            return
        }

        let visibleRect = textView.visibleRect
        let ns = textView.string as NSString
        let activeCharLoc = min(textView.selectedRange().location, ns.length)

        let numberFont = NSFont.monospacedDigitSystemFont(ofSize: max(10, editorFontSize - 2), weight: .regular)
        let activeFont = NSFont.monospacedDigitSystemFont(ofSize: max(10, editorFontSize - 2), weight: .semibold)
        let chevronFont = NSFont.systemFont(ofSize: max(10, editorFontSize - 2), weight: .bold)

        if ns.length == 0 {
            let numStr: NSString = "1"
            let attrs: [NSAttributedString.Key: Any] = [
                .font: activeFont,
                .foregroundColor: syntaxColors.gutterActiveLineNumber
            ]
            let sz = numStr.size(withAttributes: attrs)
            numStr.draw(at: NSPoint(x: ruleThickness - 22 - sz.width, y: textView.textContainerInset.height), withAttributes: attrs)
            return
        }

        var activeOneBasedLine = 1
        var cursorScan = 0
        while cursorScan < activeCharLoc && cursorScan < ns.length {
            if ns.character(at: cursorScan) == 10 {
                activeOneBasedLine += 1
            }
            cursorScan += 1
        }

        let visibleGlyphRange = lm.glyphRange(forBoundingRect: visibleRect, in: tc)
        let firstVisibleChar = lm.characterIndexForGlyph(at: visibleGlyphRange.location)

        // Count lines before firstVisibleChar
        var lineNumber = 1
        var scanIdx = 0
        while scanIdx < firstVisibleChar && scanIdx < ns.length {
            if ns.character(at: scanIdx) == 10 { lineNumber += 1 }
            scanIdx += 1
        }

        var glyphIdx = visibleGlyphRange.location
        let maxGlyph = NSMaxRange(visibleGlyphRange)

        while glyphIdx < maxGlyph {
            let charIdx = lm.characterIndexForGlyph(at: glyphIdx)
            let lineCharRange = ns.lineRange(for: NSRange(location: charIdx, length: 0))
            let lineGlyphRange = lm.glyphRange(forCharacterRange: lineCharRange, actualCharacterRange: nil)
            let isFirstWrappedFragment = (glyphIdx == lineGlyphRange.location)

            var effectiveGlyphRange = NSRange(location: 0, length: 0)
            let lineRect = lm.lineFragmentRect(forGlyphAt: glyphIdx, effectiveRange: &effectiveGlyphRange)
            let yInRuler = lineRect.minY + textView.textContainerOrigin.y - visibleRect.minY

            if isFirstWrappedFragment {
                let zeroBasedLine = lineNumber - 1
                // Skip drawing hidden interior lines of folded blocks
                let isHiddenByFold = foldedStartLines.contains { foldStart in
                    guard let reg = foldRegionsByStartLine[foldStart] else { return false }
                    return zeroBasedLine > reg.startLine && zeroBasedLine <= reg.endLine
                }

                if !isHiddenByFold {
                    let isActiveLine = (lineNumber == activeOneBasedLine)
                    let numStr = "\(lineNumber)" as NSString
                    let attrs: [NSAttributedString.Key: Any] = [
                        .font: isActiveLine ? activeFont : numberFont,
                        .foregroundColor: isActiveLine ? syntaxColors.gutterActiveLineNumber : syntaxColors.gutterLineNumber
                    ]
                    let sz = numStr.size(withAttributes: attrs)
                    let drawY = yInRuler + max(0, (lineRect.height - sz.height) * 0.5)
                    numStr.draw(at: NSPoint(x: ruleThickness - 20 - sz.width, y: drawY), withAttributes: attrs)

                    // Diagnostic severity dot on left edge
                    if let sev = diagnosticsByLine[lineNumber] {
                        let dotColor: NSColor
                        switch sev {
                        case .error: dotColor = .systemRed
                        case .warning: dotColor = .systemOrange
                        case .info: dotColor = .systemCyan
                        }
                        dotColor.setFill()
                        let dotRect = NSRect(x: 5, y: yInRuler + (lineRect.height - 6) * 0.5, width: 6, height: 6)
                        NSBezierPath(ovalIn: dotRect).fill()
                    }

                    // Clickable code-folding chevron
                    if foldRegionsByStartLine[zeroBasedLine] != nil {
                        let isFolded = foldedStartLines.contains(zeroBasedLine)
                        let chev: NSString = isFolded ? "▸" : "▾"
                        let chevAttrs: [NSAttributedString.Key: Any] = [
                            .font: chevronFont,
                            .foregroundColor: isFolded ? NSColor.controlAccentColor : syntaxColors.gutterLineNumber
                        ]
                        let cSize = chev.size(withAttributes: chevAttrs)
                        chev.draw(
                            at: NSPoint(x: ruleThickness - 15, y: yInRuler + max(0, (lineRect.height - cSize.height) * 0.5)),
                            withAttributes: chevAttrs
                        )
                    }
                }
                lineNumber += 1
            }

            glyphIdx = max(glyphIdx + 1, NSMaxRange(effectiveGlyphRange))
        }

        // Draw trailing empty line number when document ends with '\n'
        if ns.length > 0, ns.character(at: ns.length - 1) == 10, !lm.extraLineFragmentRect.isEmpty {
            let extraRect = lm.extraLineFragmentRect
            let yInRuler = extraRect.minY + textView.textContainerOrigin.y - visibleRect.minY
            if yInRuler + extraRect.height >= rect.minY && yInRuler <= rect.maxY {
                let isActiveLine = (lineNumber == activeOneBasedLine)
                let numStr = "\(lineNumber)" as NSString
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: isActiveLine ? activeFont : numberFont,
                    .foregroundColor: isActiveLine ? syntaxColors.gutterActiveLineNumber : syntaxColors.gutterLineNumber
                ]
                let sz = numStr.size(withAttributes: attrs)
                let drawY = yInRuler + max(0, (extraRect.height - sz.height) * 0.5)
                numStr.draw(at: NSPoint(x: ruleThickness - 20 - sz.width, y: drawY), withAttributes: attrs)
            }
        }
    }
}

// MARK: - SwiftUI Representable Wrapper + Liquid Glass Inline Completion Overlay

public struct MacCodeEditorView: View {
    public let document: EditorDocument
    public let astSymbols: [AstSymbol]
    public let foldRegions: [FoldRegion]
    public let diagnostics: [PowerShellDiagnostic]
    public let themeMode: AppearanceThemeMode
    public let fontSize: Double
    public let tabWidth: Int
    public let softWrap: Bool
    public let showLineNumbers: Bool
    public let showInvisibles: Bool
    public let showPageGuides: Bool
    public let highlightCurrentLine: Bool
    public let scrollJumpRequest: ScrollJumpRequest?
    public let onContentChange: (String, NSRange) -> Void
    public let onSelectionChange: (NSRange) -> Void
    public let onToggleFold: (Int) -> Void
    public let onFocusGained: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var completions: [PowerShellCompletionItem] = []
    @State private var selectedCompletionIndex: Int = 0
    @State private var completionAcceptNonce: UUID? = nil

    public init(
        document: EditorDocument,
        astSymbols: [AstSymbol],
        foldRegions: [FoldRegion],
        diagnostics: [PowerShellDiagnostic],
        themeMode: AppearanceThemeMode,
        fontSize: Double,
        tabWidth: Int,
        softWrap: Bool,
        showLineNumbers: Bool,
        showInvisibles: Bool,
        showPageGuides: Bool,
        highlightCurrentLine: Bool,
        scrollJumpRequest: ScrollJumpRequest?,
        onContentChange: @escaping (String, NSRange) -> Void,
        onSelectionChange: @escaping (NSRange) -> Void,
        onToggleFold: @escaping (Int) -> Void,
        onFocusGained: @escaping () -> Void
    ) {
        self.document = document
        self.astSymbols = astSymbols
        self.foldRegions = foldRegions
        self.diagnostics = diagnostics
        self.themeMode = themeMode
        self.fontSize = fontSize
        self.tabWidth = tabWidth
        self.softWrap = softWrap
        self.showLineNumbers = showLineNumbers
        self.showInvisibles = showInvisibles
        self.showPageGuides = showPageGuides
        self.highlightCurrentLine = highlightCurrentLine
        self.scrollJumpRequest = scrollJumpRequest
        self.onContentChange = onContentChange
        self.onSelectionChange = onSelectionChange
        self.onToggleFold = onToggleFold
        self.onFocusGained = onFocusGained
    }

    public var body: some View {
        ZStack(alignment: .bottomLeading) {
            MacCodeEditorRepresentable(
                document: document,
                astSymbols: astSymbols,
                foldRegions: foldRegions,
                diagnostics: diagnostics,
                syntaxColors: EditorSyntaxColors.resolve(mode: themeMode, systemDark: colorScheme == .dark),
                fontSize: CGFloat(fontSize),
                tabWidth: tabWidth,
                softWrap: softWrap,
                showLineNumbers: showLineNumbers,
                showInvisibles: showInvisibles,
                showPageGuides: showPageGuides,
                highlightCurrentLine: highlightCurrentLine,
                scrollJumpRequest: scrollJumpRequest,
                completions: $completions,
                selectedCompletionIndex: $selectedCompletionIndex,
                completionAcceptNonce: $completionAcceptNonce,
                onContentChange: onContentChange,
                onSelectionChange: onSelectionChange,
                onToggleFold: onToggleFold,
                onFocusGained: onFocusGained
            )

            // Floating Liquid Glass Inline PowerShell Auto-Completion Card (Overlay Layer per HIG)
            if !completions.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Image(systemName: "sparkles")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("PowerShell Completions (Tab / Return to insert • Esc to dismiss)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Spacer()
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)

                    ForEach(Array(completions.enumerated()), id: \.element.id) { idx, item in
                        Button {
                            selectedCompletionIndex = idx
                            completionAcceptNonce = UUID()
                        } label: {
                            HStack(spacing: 8) {
                                Text(item.kind.badge)
                                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 2)
                                    .background(Color.accentColor.opacity(0.18), in: Capsule())
                                Text(item.label)
                                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                Spacer(minLength: 12)
                                Text(item.detail)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(idx == selectedCompletionIndex ? Color.accentColor.opacity(0.22) : Color.clear)
                            )
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .frame(maxWidth: 480)
                .padding(6)
                .liquidGlassCard(cornerRadius: 12)
                .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
                .padding(.leading, 68)
                .padding(.bottom, 14)
            }
        }
    }
}

// MARK: - Clipped AppKit Host Container (Prevents NSScrollView Safe-Area Toolbar Bleed & Syncs Geometry)

public final class GravityEditorContainerView: NSView {
    public let scrollView: NSScrollView
    public let textView: GravityCodeTextView
    public let rulerView: GravityLineNumberRulerView
    public var softWrap: Bool = false

    public init(
        scrollView: NSScrollView,
        textView: GravityCodeTextView,
        rulerView: GravityLineNumberRulerView,
        softWrap: Bool
    ) {
        self.scrollView = scrollView
        self.textView = textView
        self.rulerView = rulerView
        self.softWrap = softWrap
        super.init(frame: NSRect(x: 0, y: 0, width: 800, height: 600))
        self.wantsLayer = true
        self.clipsToBounds = true
        scrollView.frame = self.bounds
        scrollView.autoresizingMask = [.width, .height]
        addSubview(scrollView)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public override func layout() {
        super.layout()
        if scrollView.frame != bounds {
            scrollView.frame = bounds
        }
        syncTextViewGeometry()
    }

    public func syncTextViewGeometry() {
        let cs = scrollView.contentSize
        guard cs.width > 10, cs.height > 10 else { return }

        if textView.minSize != cs {
            textView.minSize = cs
        }

        let maxDim = GravityCodeTextView.maxLayoutDimension
        if let tc = textView.textContainer {
            let targetWidth = softWrap
                ? max(120, cs.width - textView.textContainerInset.width * 2)
                : maxDim
            if abs(tc.containerSize.width - targetWidth) > 0.5 ||
               abs(tc.containerSize.height - maxDim) > 0.5 {
                tc.containerSize = NSSize(width: targetWidth, height: maxDim)
            }
        }

        var targetFrame = textView.frame
        var changed = false
        if softWrap {
            if abs(targetFrame.size.width - cs.width) > 0.5 {
                targetFrame.size.width = cs.width
                changed = true
            }
        } else if targetFrame.size.width < cs.width || targetFrame.size.width > maxDim {
            targetFrame.size.width = cs.width
            changed = true
        }
        if targetFrame.size.height < cs.height || targetFrame.size.height > maxDim {
            targetFrame.size.height = cs.height
            changed = true
        }
        if changed {
            textView.frame = targetFrame
        }
    }
}

private struct MacCodeEditorRepresentable: NSViewRepresentable {
    let document: EditorDocument
    let astSymbols: [AstSymbol]
    let foldRegions: [FoldRegion]
    let diagnostics: [PowerShellDiagnostic]
    let syntaxColors: EditorSyntaxColors
    let fontSize: CGFloat
    let tabWidth: Int
    let softWrap: Bool
    let showLineNumbers: Bool
    let showInvisibles: Bool
    let showPageGuides: Bool
    let highlightCurrentLine: Bool
    let scrollJumpRequest: ScrollJumpRequest?
    @Binding var completions: [PowerShellCompletionItem]
    @Binding var selectedCompletionIndex: Int
    @Binding var completionAcceptNonce: UUID?
    let onContentChange: (String, NSRange) -> Void
    let onSelectionChange: (NSRange) -> Void
    let onToggleFold: (Int) -> Void
    let onFocusGained: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeNSView(context: Context) -> GravityEditorContainerView {
        let maxDim = GravityCodeTextView.maxLayoutDimension

        let scrollView = NSScrollView(frame: NSRect(x: 0, y: 0, width: 800, height: 600))
        scrollView.wantsLayer = true
        scrollView.clipsToBounds = true
        scrollView.automaticallyAdjustsContentInsets = false
        scrollView.borderType = .noBorder
        scrollView.hasVerticalScroller = true
        scrollView.hasHorizontalScroller = !softWrap
        scrollView.autohidesScrollers = true
        scrollView.drawsBackground = true
        scrollView.contentView.clipsToBounds = true

        let initialContentSize = NSSize(
            width: max(320, scrollView.contentSize.width),
            height: max(240, scrollView.contentSize.height)
        )

        // Explicitly construct TextKit 1 stack (NSTextStorage -> NSLayoutManager -> NSTextContainer)
        // using finite IEEE-754 Float32-safe bounds (`1.0e7`) per Apple AppKit documentation.
        let textStorage = NSTextStorage()
        let layoutManager = FoldingAwareLayoutManager()
        layoutManager.allowsNonContiguousLayout = false
        textStorage.addLayoutManager(layoutManager)

        let containerSize = NSSize(
            width: softWrap ? max(120, initialContentSize.width - 12) : maxDim,
            height: maxDim
        )
        let textContainer = NSTextContainer(containerSize: containerSize)
        textContainer.widthTracksTextView = softWrap
        textContainer.heightTracksTextView = false
        textContainer.lineFragmentPadding = 8
        layoutManager.addTextContainer(textContainer)

        let textView = GravityCodeTextView(
            frame: NSRect(origin: .zero, size: initialContentSize),
            textContainer: textContainer
        )
        textView.delegate = context.coordinator
        layoutManager.delegate = context.coordinator

        textView.isEditable = true
        textView.isSelectable = true
        textView.isRichText = false
        textView.importsGraphics = false
        textView.allowsUndo = true
        textView.usesFindBar = false
        textView.drawsBackground = true
        textView.alignment = .left
        textView.font = NSFont.monospacedSystemFont(ofSize: fontSize, weight: .regular)
        textView.textColor = syntaxColors.plainText
        textView.isAutomaticQuoteSubstitutionEnabled = false
        textView.isAutomaticDashSubstitutionEnabled = false
        textView.isAutomaticTextReplacementEnabled = false
        textView.isAutomaticSpellingCorrectionEnabled = false
        textView.isContinuousSpellCheckingEnabled = false
        textView.isGrammarCheckingEnabled = false
        textView.smartInsertDeleteEnabled = false

        textView.minSize = initialContentSize
        textView.maxSize = NSSize(width: maxDim, height: maxDim)
        textView.isVerticallyResizable = true
        textView.isHorizontallyResizable = !softWrap
        textView.autoresizingMask = softWrap ? [.width] : [.width, .height]
        textView.textContainerInset = NSSize(width: 6, height: 10)

        scrollView.documentView = textView

        let ruler = GravityLineNumberRulerView(textView: textView)
        ruler.wantsLayer = true
        ruler.clipsToBounds = true
        ruler.onToggleFoldAtLine = { [weak coordinator = context.coordinator] startLine in
            coordinator?.parent.onToggleFold(startLine)
        }
        scrollView.verticalRulerView = ruler
        scrollView.hasVerticalRuler = true
        scrollView.rulersVisible = showLineNumbers

        // Observe clipView bounds & frame so the line-number gutter and minSize stay synchronized
        scrollView.contentView.postsBoundsChangedNotifications = true
        scrollView.contentView.postsFrameChangedNotifications = true
        NotificationCenter.default.addObserver(
            context.coordinator,
            selector: #selector(Coordinator.clipViewBoundsOrFrameDidChange(_:)),
            name: NSView.boundsDidChangeNotification,
            object: scrollView.contentView
        )
        NotificationCenter.default.addObserver(
            context.coordinator,
            selector: #selector(Coordinator.clipViewBoundsOrFrameDidChange(_:)),
            name: NSView.frameDidChangeNotification,
            object: scrollView.contentView
        )

        let container = GravityEditorContainerView(
            scrollView: scrollView,
            textView: textView,
            rulerView: ruler,
            softWrap: softWrap
        )

        textView.strongTextStorage = textStorage
        context.coordinator.textStorage = textStorage
        context.coordinator.containerView = container
        context.coordinator.textView = textView
        context.coordinator.rulerView = ruler

        textView.onFocusGained = { [weak coordinator = context.coordinator] in
            coordinator?.parent.onFocusGained()
        }
        textView.onTriggerCompletion = { [weak coordinator = context.coordinator] in
            coordinator?.recomputeCompletions(forceShow: true)
        }
        textView.onDismissCompletion = { [weak coordinator = context.coordinator] in
            coordinator?.dismissCompletions()
        }
        textView.onMoveCompletionSelection = { [weak coordinator = context.coordinator] delta in
            coordinator?.moveCompletionSelection(delta: delta) ?? false
        }
        textView.onAcceptCompletionKey = { [weak coordinator = context.coordinator] in
            coordinator?.acceptSelectedCompletionIfOpen() ?? false
        }

        // Initial content & styling
        context.coordinator.applyFullUpdate(from: self, forceRestyle: true)
        return container
    }

    func updateNSView(_ containerView: GravityEditorContainerView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.applyFullUpdate(from: self, forceRestyle: false)
    }

    // MARK: - Coordinator

    @MainActor
    final class Coordinator: NSObject, NSTextViewDelegate, NSLayoutManagerDelegate {
        var parent: MacCodeEditorRepresentable
        var textStorage: NSTextStorage?
        weak var containerView: GravityEditorContainerView?
        weak var textView: GravityCodeTextView?
        weak var rulerView: GravityLineNumberRulerView?

        var isProgrammaticUpdate = false
        private var lastDocumentId: String = ""
        private var lastStyledLanguage: SyntaxLanguage = .powershell
        private var lastFontSize: CGFloat = 0
        private var lastThemeCanvasColor: NSColor? = nil
        private var lastFoldedLines: Set<Int> = []
        private var lastJumpId: UUID? = nil
        private var lastCompletionAcceptNonce: UUID? = nil
        private var foldedCharRanges: [NSRange] = []

        init(parent: MacCodeEditorRepresentable) {
            self.parent = parent
        }

        deinit {
            NotificationCenter.default.removeObserver(self)
        }

        @objc func clipViewBoundsOrFrameDidChange(_ notification: Notification) {
            containerView?.syncTextViewGeometry()
            rulerView?.needsDisplay = true
            textView?.needsDisplay = true
        }

        func applyFullUpdate(from state: MacCodeEditorRepresentable, forceRestyle: Bool) {
            guard let tv = textView, let scroll = tv.enclosingScrollView, let ruler = rulerView else { return }
            let maxDim = GravityCodeTextView.maxLayoutDimension

            // Handle mouse-click acceptance from SwiftUI completion overlay
            if let nonce = state.completionAcceptNonce, nonce != lastCompletionAcceptNonce {
                lastCompletionAcceptNonce = nonce
                _ = acceptSelectedCompletionIfOpen()
            }

            let docChanged = (state.document.id != lastDocumentId)
            let textDiffers = (tv.string != state.document.content)
            let styleConfigChanged = forceRestyle
                || docChanged
                || state.document.language != lastStyledLanguage
                || state.fontSize != lastFontSize
                || state.syntaxColors.canvasBackground != lastThemeCanvasColor
                || state.document.foldedStartLines != lastFoldedLines

            // Update appearance & flags
            let editorFont = NSFont.monospacedSystemFont(ofSize: state.fontSize, weight: .regular)
            tv.syntaxColors = state.syntaxColors
            tv.backgroundColor = state.syntaxColors.canvasBackground
            tv.textColor = state.syntaxColors.plainText
            tv.insertionPointColor = state.syntaxColors.plainText
            if tv.font != editorFont {
                tv.font = editorFont
            }
            tv.drawPageGuides = state.showPageGuides
            tv.drawCurrentLine = state.highlightCurrentLine
            tv.drawInvisibles = state.showInvisibles
            tv.configuredTabWidth = state.tabWidth
            scroll.backgroundColor = state.syntaxColors.canvasBackground
            containerView?.softWrap = state.softWrap

            // Soft wrap toggle
            if let tc = tv.textContainer, tc.widthTracksTextView != state.softWrap {
                tc.widthTracksTextView = state.softWrap
                tc.containerSize = NSSize(
                    width: state.softWrap ? max(120, scroll.contentSize.width - tv.textContainerInset.width * 2) : maxDim,
                    height: maxDim
                )
                tv.isHorizontallyResizable = !state.softWrap
                tv.autoresizingMask = state.softWrap ? [.width] : [.width, .height]
                scroll.hasHorizontalScroller = !state.softWrap
                containerView?.syncTextViewGeometry()
            }

            // Ruler metadata
            if scroll.rulersVisible != state.showLineNumbers {
                scroll.rulersVisible = state.showLineNumbers
                containerView?.syncTextViewGeometry()
            }
            ruler.syntaxColors = state.syntaxColors
            ruler.editorFontSize = state.fontSize
            var foldMap: [Int: FoldRegion] = [:]
            for r in state.foldRegions { foldMap[r.startLine] = r }
            ruler.foldRegionsByStartLine = foldMap
            ruler.foldedStartLines = state.document.foldedStartLines

            var diagMap: [Int: DiagnosticSeverity] = [:]
            for d in state.diagnostics {
                if let existing = diagMap[d.lineNumber] {
                    if d.severity < existing { diagMap[d.lineNumber] = d.severity }
                } else {
                    diagMap[d.lineNumber] = d.severity
                }
            }
            ruler.diagnosticsByLine = diagMap

            if textDiffers {
                isProgrammaticUpdate = true
                let prevSel = tv.selectedRange()
                tv.string = state.document.content
                let newLen = (tv.string as NSString).length
                let targetSel = docChanged
                    ? NSRange(
                        location: state.document.safeSelectionStart,
                        length: max(0, state.document.safeSelectionEnd - state.document.safeSelectionStart)
                    )
                    : NSRange(location: min(prevSel.location, newLen), length: 0)
                tv.setSelectedRange(targetSel)
                applySyntaxHighlighting(to: tv)
                recomputeFoldedCharacterRanges(in: tv.string, foldRegions: state.foldRegions, foldedStarts: state.document.foldedStartLines)
                tv.layoutManager?.invalidateGlyphs(forCharacterRange: NSRange(location: 0, length: newLen), changeInLength: 0, actualCharacterRange: nil)
                tv.layoutManager?.invalidateLayout(forCharacterRange: NSRange(location: 0, length: newLen), actualCharacterRange: nil)
                if let tc = tv.textContainer {
                    tv.layoutManager?.ensureLayout(for: tc)
                }
                containerView?.syncTextViewGeometry()
                tv.sizeToFit()
                containerView?.syncTextViewGeometry()
                if docChanged {
                    if targetSel.location == 0 {
                        tv.scroll(.zero)
                    } else {
                        tv.scrollRangeToVisible(targetSel)
                    }
                }
                isProgrammaticUpdate = false
            } else if styleConfigChanged {
                isProgrammaticUpdate = true
                applySyntaxHighlighting(to: tv)
                let newLen = (tv.string as NSString).length
                recomputeFoldedCharacterRanges(in: tv.string, foldRegions: state.foldRegions, foldedStarts: state.document.foldedStartLines)
                tv.layoutManager?.invalidateGlyphs(forCharacterRange: NSRange(location: 0, length: newLen), changeInLength: 0, actualCharacterRange: nil)
                tv.layoutManager?.invalidateLayout(forCharacterRange: NSRange(location: 0, length: newLen), actualCharacterRange: nil)
                if let tc = tv.textContainer {
                    tv.layoutManager?.ensureLayout(for: tc)
                }
                containerView?.syncTextViewGeometry()
                tv.sizeToFit()
                containerView?.syncTextViewGeometry()
                isProgrammaticUpdate = false
            } else {
                containerView?.syncTextViewGeometry()
            }

            // Explicit scroll jump request (Function Navigator, Go to Line, Find Match, Diagnostic)
            if let jump = state.scrollJumpRequest, jump.id != lastJumpId, jump.documentId == state.document.id {
                lastJumpId = jump.id
                let nsLen = (tv.string as NSString).length
                let safeLoc = min(max(0, jump.utf16Range.location), nsLen)
                let safeLen = min(max(0, jump.utf16Range.length), max(0, nsLen - safeLoc))
                let clampedRange = NSRange(location: safeLoc, length: safeLen)
                isProgrammaticUpdate = true
                tv.setSelectedRange(clampedRange)
                tv.scrollRangeToVisible(clampedRange)
                tv.showFindIndicator(for: clampedRange)
                tv.window?.makeFirstResponder(tv)
                isProgrammaticUpdate = false
            }

            lastDocumentId = state.document.id
            lastStyledLanguage = state.document.language
            lastFontSize = state.fontSize
            lastThemeCanvasColor = state.syntaxColors.canvasBackground
            lastFoldedLines = state.document.foldedStartLines

            tv.needsDisplay = true
            ruler.needsDisplay = true
        }

        // MARK: - Code Folding Glyph Suppression

        private func recomputeFoldedCharacterRanges(in text: String, foldRegions: [FoldRegion], foldedStarts: Set<Int>) {
            guard !foldedStarts.isEmpty else {
                foldedCharRanges = []
                return
            }
            let ns = text as NSString
            guard ns.length > 0 else {
                foldedCharRanges = []
                return
            }
            var lineStarts: [Int] = [0]
            for i in 0..<ns.length where ns.character(at: i) == 10 {
                lineStarts.append(i + 1)
            }

            var ranges: [NSRange] = []
            for reg in foldRegions where foldedStarts.contains(reg.startLine) {
                let firstHiddenLine = reg.startLine + 1
                let lastHiddenLine = reg.endLine
                guard firstHiddenLine <= lastHiddenLine, firstHiddenLine < lineStarts.count else { continue }
                let hideStart = max(0, lineStarts[firstHiddenLine] - 1) // include newline after header line
                let hideEnd: Int
                if lastHiddenLine + 1 < lineStarts.count {
                    hideEnd = max(hideStart, lineStarts[lastHiddenLine + 1] - 1)
                } else {
                    hideEnd = ns.length
                }
                if hideEnd > hideStart {
                    ranges.append(NSRange(location: hideStart, length: hideEnd - hideStart))
                }
            }
            foldedCharRanges = ranges
        }

        nonisolated func layoutManager(
            _ layoutManager: NSLayoutManager,
            shouldGenerateGlyphs glyphs: UnsafePointer<CGGlyph>,
            properties props: UnsafePointer<NSLayoutManager.GlyphProperty>,
            characterIndexes charIndexes: UnsafePointer<Int>,
            font aFont: NSFont,
            forGlyphRange glyphRange: NSRange
        ) -> Int {
            guard let foldingLM = layoutManager as? FoldingAwareLayoutManager,
                  !foldingLM.hiddenCharacterRanges.isEmpty else {
                return 0
            }
            let count = glyphRange.length
            let mutableProps = UnsafeMutablePointer<NSLayoutManager.GlyphProperty>.allocate(capacity: count)
            defer { mutableProps.deallocate() }

            var modified = false
            for i in 0..<count {
                let charIdx = charIndexes[i]
                let isHidden = foldingLM.hiddenCharacterRanges.contains { NSLocationInRange(charIdx, $0) }
                if isHidden {
                    mutableProps[i] = .null
                    modified = true
                } else {
                    mutableProps[i] = props[i]
                }
            }
            guard modified else { return 0 }
            layoutManager.setGlyphs(
                glyphs,
                properties: mutableProps,
                characterIndexes: charIndexes,
                font: aFont,
                forGlyphRange: glyphRange
            )
            return count
        }

        // MARK: - NSTextViewDelegate

        func textDidChange(_ notification: Notification) {
            guard !isProgrammaticUpdate, let tv = textView else { return }
            isProgrammaticUpdate = true
            applySyntaxHighlighting(to: tv)
            recomputeFoldedCharacterRanges(
                in: tv.string,
                foldRegions: parent.foldRegions,
                foldedStarts: parent.document.foldedStartLines
            )
            tv.sizeToFit()
            containerView?.syncTextViewGeometry()
            isProgrammaticUpdate = false

            parent.onContentChange(tv.string, tv.selectedRange())
            recomputeCompletions(forceShow: false)
            rulerView?.needsDisplay = true
        }

        func textViewDidChangeSelection(_ notification: Notification) {
            guard !isProgrammaticUpdate, let tv = textView else { return }
            parent.onSelectionChange(tv.selectedRange())
            tv.needsDisplay = true
            rulerView?.needsDisplay = true
        }

        // MARK: - Inline PowerShell Completions

        func recomputeCompletions(forceShow: Bool) {
            guard let tv = textView, parent.document.language == .powershell else {
                dismissCompletions()
                return
            }
            let ns = tv.string as NSString
            let sel = tv.selectedRange()
            guard sel.length == 0, sel.location <= ns.length else {
                dismissCompletions()
                return
            }
            let lineRange = ns.lineRange(for: NSRange(location: sel.location, length: 0))
            let lineStr = ns.substring(with: lineRange).trimmingCharacters(in: CharacterSet.newlines)
            let colInLine = max(0, sel.location - lineRange.location)

            let items = PowerShellEngine.computeCompletions(
                line: lineStr,
                cursorUTF16Column: colInLine,
                documentContent: tv.string,
                astSymbols: parent.astSymbols,
                maxResults: 7
            )
            if items.isEmpty || (!forceShow && colInLine < 2) {
                dismissCompletions()
            } else {
                parent.completions = items
                parent.selectedCompletionIndex = 0
            }
        }

        func dismissCompletions() {
            if !parent.completions.isEmpty {
                parent.completions = []
                parent.selectedCompletionIndex = 0
            }
        }

        func moveCompletionSelection(delta: Int) -> Bool {
            guard !parent.completions.isEmpty else { return false }
            let count = parent.completions.count
            parent.selectedCompletionIndex = (parent.selectedCompletionIndex + delta + count) % count
            return true
        }

        func acceptSelectedCompletionIfOpen() -> Bool {
            guard !parent.completions.isEmpty, let tv = textView else { return false }
            let idx = min(max(0, parent.selectedCompletionIndex), parent.completions.count - 1)
            let item = parent.completions[idx]
            let ns = tv.string as NSString
            let sel = tv.selectedRange()
            let lineRange = ns.lineRange(for: NSRange(location: min(sel.location, ns.length), length: 0))
            let repLoc = lineRange.location + item.replaceStartInLine
            let repLen = max(0, item.replaceEndInLine - item.replaceStartInLine)
            if repLoc + repLen <= ns.length {
                tv.insertText(item.insertText, replacementRange: NSRange(location: repLoc, length: repLen))
            }
            dismissCompletions()
            return true
        }

        // MARK: - Syntax Highlighting (NSTextStorage)

        private static let genericStringRegex = try! NSRegularExpression(pattern: #""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'"#)
        private static let genericNumberRegex = try! NSRegularExpression(pattern: #"\b(?:0x[0-9A-Fa-f]+|\d+(?:\.\d+)?)\b"#)
        private static let swiftKeywordsRegex = try! NSRegularExpression(
            pattern: #"\b(import|struct|class|enum|actor|protocol|extension|func|init|deinit|var|let|if|else|guard|switch|case|default|for|while|repeat|return|throw|throws|try|catch|async|await|public|private|internal|fileprivate|open|static|final|override|mutating|some|any|where|true|false|nil|self|Self)\b"#
        )
        private static let slashCommentRegex = try! NSRegularExpression(pattern: #"//.*$"#, options: [.anchorsMatchLines])
        private static let hashCommentRegex = try! NSRegularExpression(pattern: #"#.*$"#, options: [.anchorsMatchLines])
        private static let xmlTagRegex = try! NSRegularExpression(pattern: #"</?[A-Za-z0-9_:\-.]+|\s+[A-Za-z0-9_:\-]+(?==)|/?>"#)

        func applySyntaxHighlighting(to tv: GravityCodeTextView) {
            guard let storage = tv.textStorage else { return }
            if let foldingLM = tv.layoutManager as? FoldingAwareLayoutManager {
                recomputeFoldedCharacterRanges(
                    in: tv.string,
                    foldRegions: parent.foldRegions,
                    foldedStarts: parent.document.foldedStartLines
                )
                foldingLM.hiddenCharacterRanges = foldedCharRanges
            }

            let text = storage.string
            let ns = text as NSString
            let fullRange = NSRange(location: 0, length: ns.length)

            let regularFont = NSFont.monospacedSystemFont(ofSize: parent.fontSize, weight: .regular)
            let semiboldFont = NSFont.monospacedSystemFont(ofSize: parent.fontSize, weight: .semibold)
            let colors = parent.syntaxColors

            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = .left
            paragraph.lineSpacing = 2.0

            storage.beginEditing()
            storage.setAttributes(
                [
                    .font: regularFont,
                    .foregroundColor: colors.plainText,
                    .paragraphStyle: paragraph
                ],
                range: fullRange
            )
            tv.typingAttributes = [
                .font: regularFont,
                .foregroundColor: colors.plainText,
                .paragraphStyle: paragraph
            ]

            // Cap full-file token coloring above 250 KB so giant log files stay instant
            guard ns.length > 0 && ns.length <= 260_000 else {
                storage.endEditing()
                return
            }

            let lang = parent.document.language
            if lang == .powershell {
                let lines = text.components(separatedBy: "\n")
                let states = PowerShellEngine.computeLineStates(lines, language: .powershell)
                var lineStartOffset = 0

                for (idx, line) in lines.enumerated() {
                    let nsLine = line as NSString
                    let tokens = PowerShellEngine.tokenizePowerShellLine(line, startState: states[idx])
                    for tok in tokens {
                        guard tok.type != .whitespace && tok.type != .identifier && tok.type != .punctuation else {
                            continue
                        }
                        let globalRange = NSRange(location: lineStartOffset + tok.start, length: max(0, tok.end - tok.start))
                        guard NSMaxRange(globalRange) <= ns.length else { continue }

                        let fg: NSColor
                        var useBold = false
                        switch tok.type {
                        case .keyword:
                            fg = colors.keyword
                            useBold = true
                        case .cmdlet:
                            fg = colors.cmdlet
                            useBold = true
                        case .variableNormal:
                            fg = colors.variableNormal
                        case .variableScoped:
                            fg = colors.variableScoped
                        case .variableAutomatic:
                            fg = colors.variableAutomatic
                            useBold = true
                        case .parameter:
                            fg = colors.parameter
                        case .operator, .hashtablePrefix:
                            fg = colors.operator
                        case .stringLiteral, .stringExpandable:
                            fg = colors.stringLiteral
                        case .stringInterpolation:
                            fg = colors.stringInterpolation
                            useBold = true
                        case .number:
                            fg = colors.number
                        case .typeAttribute:
                            fg = colors.typeAttribute
                        case .comment:
                            fg = colors.comment
                        case .commentHelpTag:
                            fg = colors.helpTag
                            useBold = true
                        case .regionDirective:
                            fg = colors.regionDirective
                            useBold = true
                        default:
                            fg = colors.plainText
                        }

                        storage.addAttribute(.foregroundColor, value: fg, range: globalRange)
                        if useBold {
                            storage.addAttribute(.font, value: semiboldFont, range: globalRange)
                        }
                    }
                    lineStartOffset += nsLine.length + 1
                }
            } else {
                // Fast regex highlighting for Swift, JSON, XML, YAML, Markdown, Python, Kotlin, Shell
                for m in Self.genericNumberRegex.matches(in: text, options: [], range: fullRange) {
                    storage.addAttribute(.foregroundColor, value: colors.number, range: m.range)
                }
                if lang == .swift || lang == .kotlin {
                    for m in Self.swiftKeywordsRegex.matches(in: text, options: [], range: fullRange) {
                        storage.addAttributes([.foregroundColor: colors.keyword, .font: semiboldFont], range: m.range)
                    }
                }
                if lang == .xml || lang == .htmlCss {
                    for m in Self.xmlTagRegex.matches(in: text, options: [], range: fullRange) {
                        storage.addAttribute(.foregroundColor, value: colors.cmdlet, range: m.range)
                    }
                }
                for m in Self.genericStringRegex.matches(in: text, options: [], range: fullRange) {
                    storage.addAttribute(.foregroundColor, value: colors.stringLiteral, range: m.range)
                }
                if lang == .swift || lang == .kotlin || lang == .json {
                    for m in Self.slashCommentRegex.matches(in: text, options: [], range: fullRange) {
                        storage.addAttribute(.foregroundColor, value: colors.comment, range: m.range)
                    }
                } else if lang == .python || lang == .shellBash || lang == .yaml {
                    for m in Self.hashCommentRegex.matches(in: text, options: [], range: fullRange) {
                        storage.addAttribute(.foregroundColor, value: colors.comment, range: m.range)
                    }
                }
            }

            storage.endEditing()
        }
    }
}

private final class FoldingAwareLayoutManager: NSLayoutManager {
    var hiddenCharacterRanges: [NSRange] = []
}
