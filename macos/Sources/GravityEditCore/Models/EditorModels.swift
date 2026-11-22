import Foundation

// MARK: - Syntax Languages, Encodings & Line Endings

public enum SyntaxLanguage: String, CaseIterable, Codable, Identifiable, Sendable {
    case powershell = "POWERSHELL"
    case swift = "SWIFT"
    case json = "JSON"
    case xml = "XML"
    case yaml = "YAML"
    case markdown = "MARKDOWN"
    case python = "PYTHON"
    case kotlin = "KOTLIN"
    case htmlCss = "HTML_CSS"
    case shellBash = "SHELL_BASH"
    case plainText = "PLAIN_TEXT"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .powershell: return "PowerShell"
        case .swift: return "Swift"
        case .json: return "JSON"
        case .xml: return "XML / Plist"
        case .yaml: return "YAML"
        case .markdown: return "Markdown"
        case .python: return "Python"
        case .kotlin: return "Kotlin"
        case .htmlCss: return "HTML / CSS"
        case .shellBash: return "Shell / Zsh"
        case .plainText: return "Plain Text"
        }
    }

    public var extensions: [String] {
        switch self {
        case .powershell: return ["ps1", "psm1", "psd1"]
        case .swift: return ["swift"]
        case .json: return ["json", "jsonc"]
        case .xml: return ["xml", "xsd", "svg", "plist", "csproj", "props"]
        case .yaml: return ["yaml", "yml"]
        case .markdown: return ["md", "markdown"]
        case .python: return ["py", "pyw"]
        case .kotlin: return ["kt", "kts"]
        case .htmlCss: return ["html", "htm", "css"]
        case .shellBash: return ["sh", "bash", "zsh", "env"]
        case .plainText: return ["txt", "log", "csv", "ini", "conf"]
        }
    }

    public var primaryExtension: String {
        extensions.first ?? "txt"
    }

    public var singleLineCommentPrefix: String? {
        switch self {
        case .powershell, .yaml, .python, .shellBash, .plainText:
            return "#"
        case .swift, .json, .kotlin:
            return "//"
        case .xml, .markdown, .htmlCss:
            return nil
        }
    }

    public var sfSymbolName: String {
        switch self {
        case .powershell: return "terminal.fill"
        case .swift: return "swift"
        case .json: return "curlybraces"
        case .xml: return "chevron.left.forwardslash.chevron.right"
        case .yaml: return "list.bullet.indent"
        case .markdown: return "text.document"
        case .python: return "sparkles.rectangle.stack"
        case .kotlin: return "k.square.fill"
        case .htmlCss: return "globe"
        case .shellBash: return "apple.terminal"
        case .plainText: return "doc.plaintext"
        }
    }

    public static func fromFileName(_ fileName: String) -> SyntaxLanguage {
        let ext = (fileName as NSString).pathExtension.lowercased()
        guard !ext.isEmpty else { return .plainText }
        return allCases.first(where: { $0.extensions.contains(ext) }) ?? .plainText
    }
}

public enum DocumentEncoding: String, CaseIterable, Codable, Identifiable, Sendable {
    case utf8 = "UTF-8"
    case utf8Bom = "UTF-8 BOM"
    case utf16LE = "UTF-16LE"
    case utf16BE = "UTF-16BE"
    case iso88591 = "ISO-8859-1"
    case windows1252 = "Windows-1252"

    public var id: String { rawValue }
    public var displayName: String { rawValue }

    public func encode(_ string: String) -> Data {
        switch self {
        case .utf8:
            return Data(string.utf8)
        case .utf8Bom:
            var data = Data([0xEF, 0xBB, 0xBF])
            data.append(contentsOf: string.utf8)
            return data
        case .utf16LE:
            var data = Data([0xFF, 0xFE])
            if let body = string.data(using: .utf16LittleEndian) {
                data.append(body)
            }
            return data
        case .utf16BE:
            var data = Data([0xFE, 0xFF])
            if let body = string.data(using: .utf16BigEndian) {
                data.append(body)
            }
            return data
        case .iso88591:
            return string.data(using: .isoLatin1, allowLossyConversion: true) ?? Data(string.utf8)
        case .windows1252:
            return string.data(using: .windowsCP1252, allowLossyConversion: true) ?? Data(string.utf8)
        }
    }

    public static func decode(_ data: Data) -> (text: String, encoding: DocumentEncoding) {
        if data.starts(with: [0xEF, 0xBB, 0xBF]) {
            let body = data.dropFirst(3)
            let str = String(decoding: body, as: UTF8.self)
            return (str, .utf8Bom)
        }
        if data.starts(with: [0xFF, 0xFE]) {
            let body = Data(data.dropFirst(2))
            if let str = String(data: body, encoding: .utf16LittleEndian) {
                return (str, .utf16LE)
            }
        }
        if data.starts(with: [0xFE, 0xFF]) {
            let body = Data(data.dropFirst(2))
            if let str = String(data: body, encoding: .utf16BigEndian) {
                return (str, .utf16BE)
            }
        }
        if let utf8 = String(data: data, encoding: .utf8) {
            return (utf8, .utf8)
        }
        if let cp1252 = String(data: data, encoding: .windowsCP1252) {
            return (cp1252, .windows1252)
        }
        if let latin1 = String(data: data, encoding: .isoLatin1) {
            return (latin1, .iso88591)
        }
        return (String(decoding: data, as: UTF8.self), .utf8)
    }
}

public enum LineEnding: String, CaseIterable, Codable, Identifiable, Sendable {
    case lf = "LF"
    case crlf = "CRLF"
    case cr = "CR"

    public var id: String { rawValue }
    public var badge: String { rawValue }

    public var displayName: String {
        switch self {
        case .lf: return "macOS / Unix (LF)"
        case .crlf: return "Windows (CRLF)"
        case .cr: return "Classic Mac (CR)"
        }
    }

    public var sequence: String {
        switch self {
        case .lf: return "\n"
        case .crlf: return "\r\n"
        case .cr: return "\r"
        }
    }

    public static func detect(_ rawText: String) -> LineEnding {
        if rawText.contains("\r\n") { return .crlf }
        if rawText.contains("\r") { return .cr }
        return .lf
    }

    public static func normalizeToLf(_ rawText: String) -> String {
        rawText
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
    }

    public static func applyLineEnding(_ normalizedText: String, target: LineEnding) -> String {
        let lfText = normalizeToLf(normalizedText)
        switch target {
        case .lf: return lfText
        case .crlf: return lfText.replacingOccurrences(of: "\n", with: "\r\n")
        case .cr: return lfText.replacingOccurrences(of: "\n", with: "\r")
        }
    }
}

public enum SplitViewMode: String, CaseIterable, Codable, Identifiable, Sendable {
    case none = "NONE"
    case vertical = "VERTICAL"
    case horizontal = "HORIZONTAL"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .none: return "Single Editor"
        case .vertical: return "Side-by-Side Split"
        case .horizontal: return "Top-and-Bottom Split"
        }
    }

    public var sfSymbol: String {
        switch self {
        case .none: return "rectangle"
        case .vertical: return "rectangle.split.2x1"
        case .horizontal: return "rectangle.split.1x2"
        }
    }
}

public enum SidebarSection: String, CaseIterable, Codable, Identifiable, Sendable {
    case openDocuments = "OPEN_DOCUMENTS"
    case diskBrowser = "DISK_BROWSER"
    case functionOutline = "FUNCTION_OUTLINE"
    case clippings = "CLIPPINGS"
    case diagnostics = "DIAGNOSTICS"

    public var id: String { rawValue }

    public var sfSymbol: String {
        switch self {
        case .openDocuments: return "doc.on.doc"
        case .diskBrowser: return "folder.badge.gearshape"
        case .functionOutline: return "function"
        case .clippings: return "text.badge.plus"
        case .diagnostics: return "checkmark.shield"
        }
    }
}

public enum AppearanceThemeMode: String, CaseIterable, Codable, Identifiable, Sendable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"
    case oledPitchBlack = "OLED"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .system: return "System Automatic"
        case .light: return "Daylight Crisp"
        case .dark: return "Tahoe Midnight"
        case .oledPitchBlack: return "Pitch-Black Pro"
        }
    }
}

// MARK: - AST Symbols, Code Folding & Diagnostics

public enum FoldKind: String, Codable, Sendable {
    case function
    case filter
    case workflow
    case `class`
    case region
    case commentBlock
    case block
}

public struct FoldRegion: Identifiable, Equatable, Sendable {
    public let id: String
    public let startLine: Int
    public let endLine: Int
    public let label: String
    public let kind: FoldKind

    public var lineSpan: Int {
        max(1, endLine - startLine)
    }

    public init(id: String, startLine: Int, endLine: Int, label: String, kind: FoldKind) {
        self.id = id
        self.startLine = startLine
        self.endLine = endLine
        self.label = label
        self.kind = kind
    }
}

public enum SymbolKind: String, Codable, CaseIterable, Sendable {
    case function
    case filter
    case workflow
    case `class`
    case configuration
    case scriptblock
    case paramBlock
    case pipelineBlock
    case section
    case region
    case configVars
    case dataTable
    case controlBlock

    public var badge: String {
        switch self {
        case .function: return "fn"
        case .filter: return "flt"
        case .workflow: return "wf"
        case .class: return "cls"
        case .configuration: return "cfg"
        case .scriptblock: return "$sb"
        case .paramBlock: return "prm"
        case .pipelineBlock: return "pipe"
        case .section: return "#sec"
        case .region: return "#reg"
        case .configVars: return "var"
        case .dataTable: return "tbl"
        case .controlBlock: return "blk"
        }
    }

    public var isCallableCommand: Bool {
        switch self {
        case .function, .filter, .workflow, .configuration:
            return true
        default:
            return false
        }
    }
}

public struct AstSymbol: Identifiable, Equatable, Sendable {
    public var id: String { "\(kind.rawValue)_\(lineNumber)_\(name)" }
    public let name: String
    public let kind: SymbolKind
    public let lineIndex: Int
    public let lineNumber: Int
    public let signature: String
    public let parameters: [String]

    public init(
        name: String,
        kind: SymbolKind,
        lineIndex: Int,
        lineNumber: Int,
        signature: String,
        parameters: [String] = []
    ) {
        self.name = name
        self.kind = kind
        self.lineIndex = lineIndex
        self.lineNumber = lineNumber
        self.signature = signature
        self.parameters = parameters
    }
}

public enum CompletionKind: String, Sendable {
    case cmdlet = "cmd"
    case variable = "var"
    case parameter = "prm"
    case keyword = "kw"
    case typeAccelerator = "type"
    case function = "fn"

    public var badge: String { rawValue }
}

public struct PowerShellCompletionItem: Identifiable, Equatable, Sendable {
    public var id: String { "\(kind.rawValue)_\(label)" }
    public let label: String
    public let insertText: String
    public let kind: CompletionKind
    public let detail: String
    public let replaceStartInLine: Int
    public let replaceEndInLine: Int

    public init(
        label: String,
        insertText: String,
        kind: CompletionKind,
        detail: String,
        replaceStartInLine: Int,
        replaceEndInLine: Int
    ) {
        self.label = label
        self.insertText = insertText
        self.kind = kind
        self.detail = detail
        self.replaceStartInLine = replaceStartInLine
        self.replaceEndInLine = replaceEndInLine
    }
}

public enum DiagnosticSeverity: Int, Comparable, Codable, Sendable {
    case error = 0
    case warning = 1
    case info = 2

    public static func < (lhs: DiagnosticSeverity, rhs: DiagnosticSeverity) -> Bool {
        lhs.rawValue < rhs.rawValue
    }

    public var badge: String {
        switch self {
        case .error: return "ERR"
        case .warning: return "WARN"
        case .info: return "INFO"
        }
    }
}

public struct PowerShellDiagnostic: Identifiable, Equatable, Sendable {
    public let id: String
    public let ruleId: String
    public let severity: DiagnosticSeverity
    public let message: String
    public let lineNumber: Int
    public let column: Int
    public let quickFixLabel: String?
    public let quickFixTarget: String?
    public let quickFixReplacement: String?

    public init(
        id: String,
        ruleId: String,
        severity: DiagnosticSeverity,
        message: String,
        lineNumber: Int,
        column: Int,
        quickFixLabel: String? = nil,
        quickFixTarget: String? = nil,
        quickFixReplacement: String? = nil
    ) {
        self.id = id
        self.ruleId = ruleId
        self.severity = severity
        self.message = message
        self.lineNumber = lineNumber
        self.column = column
        self.quickFixLabel = quickFixLabel
        self.quickFixTarget = quickFixTarget
        self.quickFixReplacement = quickFixReplacement
    }
}

// MARK: - Document, Disk Browser, Clippings, Search, Diff & Text Factory Models

public struct SelectionStats: Equatable, Sendable {
    public let hasSelection: Bool
    public let charCount: Int
    public let wordCount: Int
    public let lineCount: Int

    public init(hasSelection: Bool, charCount: Int, wordCount: Int, lineCount: Int) {
        self.hasSelection = hasSelection
        self.charCount = charCount
        self.wordCount = wordCount
        self.lineCount = lineCount
    }
}

public struct EditorDocument: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public var title: String
    public var fileURL: URL?
    public var content: String {
        didSet {
            if content != oldValue {
                let metrics = Self.computeLineAndWordCounts(in: content)
                cachedLineCount = metrics.lines
                cachedWordCount = metrics.words
            }
        }
    }
    public var savedContent: String
    public var isScratchpad: Bool
    public var language: SyntaxLanguage
    public var encoding: DocumentEncoding
    public var savedEncoding: DocumentEncoding
    public var lineEnding: LineEnding
    public var savedLineEnding: LineEnding
    public var selectionStart: Int
    public var selectionEnd: Int
    public var foldedStartLines: Set<Int>

    private var cachedLineCount: Int
    private var cachedWordCount: Int

    private enum CodingKeys: String, CodingKey {
        case id, title, fileURL, content, savedContent, isScratchpad
        case language, encoding, savedEncoding, lineEnding, savedLineEnding
        case selectionStart, selectionEnd, foldedStartLines
    }

    public init(
        id: String = UUID().uuidString,
        title: String,
        fileURL: URL? = nil,
        content: String = "",
        savedContent: String = "",
        isScratchpad: Bool = false,
        language: SyntaxLanguage = .powershell,
        encoding: DocumentEncoding = .utf8,
        lineEnding: LineEnding = .lf,
        selectionStart: Int = 0,
        selectionEnd: Int = 0,
        foldedStartLines: Set<Int> = []
    ) {
        let metrics = Self.computeLineAndWordCounts(in: content)
        self.id = id
        self.title = title
        self.fileURL = fileURL
        self.content = content
        self.savedContent = savedContent
        self.isScratchpad = isScratchpad
        self.language = language
        self.encoding = encoding
        self.savedEncoding = encoding
        self.lineEnding = lineEnding
        self.savedLineEnding = lineEnding
        self.selectionStart = selectionStart
        self.selectionEnd = selectionEnd
        self.foldedStartLines = foldedStartLines
        self.cachedLineCount = metrics.lines
        self.cachedWordCount = metrics.words
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let decodedContent = try c.decode(String.self, forKey: .content)
        let decodedEnc = try c.decode(DocumentEncoding.self, forKey: .encoding)
        let decodedLE = try c.decode(LineEnding.self, forKey: .lineEnding)
        let metrics = Self.computeLineAndWordCounts(in: decodedContent)

        self.id = try c.decode(String.self, forKey: .id)
        self.title = try c.decode(String.self, forKey: .title)
        self.fileURL = try c.decodeIfPresent(URL.self, forKey: .fileURL)
        self.content = decodedContent
        self.savedContent = try c.decode(String.self, forKey: .savedContent)
        self.isScratchpad = try c.decode(Bool.self, forKey: .isScratchpad)
        self.language = try c.decode(SyntaxLanguage.self, forKey: .language)
        self.encoding = decodedEnc
        self.savedEncoding = try c.decodeIfPresent(DocumentEncoding.self, forKey: .savedEncoding) ?? decodedEnc
        self.lineEnding = decodedLE
        self.savedLineEnding = try c.decodeIfPresent(LineEnding.self, forKey: .savedLineEnding) ?? decodedLE
        self.selectionStart = try c.decode(Int.self, forKey: .selectionStart)
        self.selectionEnd = try c.decode(Int.self, forKey: .selectionEnd)
        self.foldedStartLines = try c.decode(Set<Int>.self, forKey: .foldedStartLines)
        self.cachedLineCount = metrics.lines
        self.cachedWordCount = metrics.words
    }

    private static func computeLineAndWordCounts(in text: String) -> (lines: Int, words: Int) {
        guard !text.isEmpty else { return (1, 0) }
        var lines = 1
        var words = 0
        var inWord = false
        for byte in text.utf8 {
            if byte == 10 { // '\n'
                lines += 1
                inWord = false
            } else if byte == 32 || byte == 9 || byte == 13 || byte == 12 || byte == 11 {
                inWord = false
            } else if !inWord {
                inWord = true
                words += 1
            }
        }
        return (lines, words)
    }

    public var isDirty: Bool {
        guard !isScratchpad else { return false }
        if content != savedContent { return true }
        if fileURL != nil && (encoding != savedEncoding || lineEnding != savedLineEnding) { return true }
        if let url = fileURL, url.lastPathComponent != title { return true }
        return false
    }

    public var lines: [String] {
        content.isEmpty ? [""] : content.components(separatedBy: "\n")
    }

    public var lineCount: Int {
        cachedLineCount
    }

    public var charCount: Int {
        content.count
    }

    public var wordCount: Int {
        cachedWordCount
    }

    public var safeSelectionStart: Int {
        let len = (content as NSString).length
        return min(max(0, min(selectionStart, selectionEnd)), len)
    }

    public var safeSelectionEnd: Int {
        let len = (content as NSString).length
        return min(max(0, max(selectionStart, selectionEnd)), len)
    }

    public var selectedText: String {
        let start = safeSelectionStart
        let end = safeSelectionEnd
        guard start < end else { return "" }
        return (content as NSString).substring(with: NSRange(location: start, length: end - start))
    }

    public func cursorLineAndColumn() -> (line: Int, column: Int) {
        if selectionEnd <= 0 || content.isEmpty { return (1, 1) }
        let ns = content as NSString
        let cursor = min(max(0, selectionEnd), ns.length)
        var line = 1
        var lastLineStart = 0
        var i = 0
        while i < cursor {
            if ns.character(at: i) == 10 { // '\n'
                line += 1
                lastLineStart = i + 1
            }
            i += 1
        }
        return (line, (cursor - lastLineStart) + 1)
    }

    public func selectionStats() -> SelectionStats {
        let start = safeSelectionStart
        let end = safeSelectionEnd
        guard start < end else {
            return SelectionStats(hasSelection: false, charCount: 0, wordCount: 0, lineCount: 0)
        }
        let sub = selectedText
        let words = sub.split(whereSeparator: { $0.isWhitespace || $0.isNewline }).count
        var lines = 1
        for ch in sub.utf8 where ch == UInt8(ascii: "\n") {
            lines += 1
        }
        return SelectionStats(hasSelection: true, charCount: sub.count, wordCount: words, lineCount: lines)
    }

    public func nsRangeForLineIndex(_ lineIndex: Int) -> NSRange {
        let ns = content as NSString
        if ns.length == 0 { return NSRange(location: 0, length: 0) }
        var currentLine = 0
        var lineStart = 0
        var i = 0
        while i < ns.length && currentLine < lineIndex {
            if ns.character(at: i) == 10 {
                currentLine += 1
                lineStart = i + 1
            }
            i += 1
        }
        var lineEnd = lineStart
        while lineEnd < ns.length && ns.character(at: lineEnd) != 10 {
            lineEnd += 1
        }
        return NSRange(location: lineStart, length: max(0, lineEnd - lineStart))
    }

    public func lineIndexForUTF16Offset(_ offset: Int) -> Int {
        let ns = content as NSString
        let clamped = min(max(0, offset), ns.length)
        var lineIdx = 0
        var i = 0
        while i < clamped {
            if ns.character(at: i) == 10 {
                lineIdx += 1
            }
            i += 1
        }
        return lineIdx
    }

    /// Stem-only rename helper: preserves extension when user types a bare stem.
    public static func normalizedRenamedTitle(currentTitle: String, userInput: String) -> String {
        let trimmed = userInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return currentTitle }
        let currentExt = (currentTitle as NSString).pathExtension
        let newExt = (trimmed as NSString).pathExtension
        if newExt.isEmpty && !currentExt.isEmpty {
            return "\(trimmed).\(currentExt)"
        }
        return trimmed
    }

    /// Updates a document filename's extension to match a newly selected `SyntaxLanguage`
    /// (for example, `GravityWorkbench.ps1` -> `GravityWorkbench.txt` when switching to `.plainText`),
    /// while preserving extensions that already belong to the target language (such as `.psm1` or `.log`).
    public static func titleSynchronizedWithLanguage(currentTitle: String, language: SyntaxLanguage) -> String {
        let trimmed = currentTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return "Untitled.\(language.primaryExtension)"
        }
        let currentExt = (trimmed as NSString).pathExtension.lowercased()
        if !currentExt.isEmpty && language.extensions.contains(currentExt) {
            return trimmed
        }
        let rawStem = (trimmed as NSString).deletingPathExtension.trimmingCharacters(in: .whitespacesAndNewlines)
        let stem = rawStem.isEmpty ? trimmed : rawStem
        return "\(stem).\(language.primaryExtension)"
    }
}

public struct DiskBrowserNode: Identifiable, Equatable, Sendable {
    public var id: String { url.path }
    public let url: URL
    public let name: String
    public let isDirectory: Bool
    public let depth: Int
    public var isExpanded: Bool
    public let sizeBytes: Int64
    public var children: [DiskBrowserNode]?

    public init(
        url: URL,
        name: String,
        isDirectory: Bool,
        depth: Int = 0,
        isExpanded: Bool = false,
        sizeBytes: Int64 = 0,
        children: [DiskBrowserNode]? = nil
    ) {
        self.url = url
        self.name = name
        self.isDirectory = isDirectory
        self.depth = depth
        self.isExpanded = isExpanded
        self.sizeBytes = sizeBytes
        self.children = children
    }
}

// MARK: - Clippings Models

public enum ClippingCategory: String, CaseIterable, Codable, Identifiable, Sendable {
    case powershellFunctions = "PowerShell Functions & CmdletBinding"
    case powershellFlow = "PowerShell Flow Control & Error Handling"
    case powershellHelpDocs = "Comment-Based Help & Regions"
    case powershellRemotingApi = "REST API, Remoting & Pipeline"
    case powershellPester = "Pester Testing Blocks"
    case macosAutomation = "macOS & Shell Automation"
    case userCustom = "User Custom Clippings"

    public var id: String { rawValue }
    public var displayName: String { rawValue }
}

public struct Clipping: Identifiable, Equatable, Codable, Sendable {
    public let id: String
    public var name: String
    public var triggerPrefix: String
    public var category: ClippingCategory
    public var description: String
    public var templateBody: String
    public var syntaxLanguage: SyntaxLanguage
    public var isBuiltIn: Bool

    public init(
        id: String = UUID().uuidString,
        name: String,
        triggerPrefix: String,
        category: ClippingCategory,
        description: String,
        templateBody: String,
        syntaxLanguage: SyntaxLanguage = .powershell,
        isBuiltIn: Bool = true
    ) {
        self.id = id
        self.name = name
        self.triggerPrefix = triggerPrefix
        self.category = category
        self.description = description
        self.templateBody = templateBody
        self.syntaxLanguage = syntaxLanguage
        self.isBuiltIn = isBuiltIn
    }
}

public struct ClippingExpansionContext: Equatable, Sendable {
    public let selectedText: String
    public let clipboardText: String
    public let fileName: String
    public let userName: String
    public let baseIndentation: String

    public init(
        selectedText: String = "",
        clipboardText: String = "",
        fileName: String = "Untitled.ps1",
        userName: String = NSFullUserName().isEmpty ? NSUserName() : NSFullUserName(),
        baseIndentation: String = ""
    ) {
        self.selectedText = selectedText
        self.clipboardText = clipboardText
        self.fileName = fileName
        self.userName = userName
        self.baseIndentation = baseIndentation
    }
}

public struct ExpandedClippingResult: Equatable, Sendable {
    public let textToInsert: String
    public let cursorRelativeUTF16Offset: Int
    public let selectionRelativeUTF16Range: NSRange?

    public init(
        textToInsert: String,
        cursorRelativeUTF16Offset: Int,
        selectionRelativeUTF16Range: NSRange? = nil
    ) {
        self.textToInsert = textToInsert
        self.cursorRelativeUTF16Offset = cursorRelativeUTF16Offset
        self.selectionRelativeUTF16Range = selectionRelativeUTF16Range
    }
}

// MARK: - Search & Regex Models

public struct SearchOptions: Equatable, Codable, Sendable {
    public var query: String
    public var replacement: String
    public var caseSensitive: Bool
    public var matchEntireWords: Bool
    public var grepRegex: Bool
    public var wrapAround: Bool
    public var searchInSelectionOnly: Bool
    public var multiLine: Bool
    public var dotMatchesAll: Bool
    public var fileFilter: String

    public init(
        query: String = "",
        replacement: String = "",
        caseSensitive: Bool = false,
        matchEntireWords: Bool = false,
        grepRegex: Bool = true,
        wrapAround: Bool = true,
        searchInSelectionOnly: Bool = false,
        multiLine: Bool = true,
        dotMatchesAll: Bool = false,
        fileFilter: String = "*.ps1;*.psm1;*.psd1;*.swift;*.json;*.txt;*.md;*.xml;*.sh"
    ) {
        self.query = query
        self.replacement = replacement
        self.caseSensitive = caseSensitive
        self.matchEntireWords = matchEntireWords
        self.grepRegex = grepRegex
        self.wrapAround = wrapAround
        self.searchInSelectionOnly = searchInSelectionOnly
        self.multiLine = multiLine
        self.dotMatchesAll = dotMatchesAll
        self.fileFilter = fileFilter
    }
}

public struct CaptureGroupMatch: Equatable, Sendable {
    public let groupIndex: Int
    public let value: String
    public let nsRange: NSRange

    public init(groupIndex: Int, value: String, nsRange: NSRange) {
        self.groupIndex = groupIndex
        self.value = value
        self.nsRange = nsRange
    }
}

public struct SearchMatch: Identifiable, Equatable, Sendable {
    public var id: String { "\(matchIndex)_\(nsRange.location)_\(nsRange.length)" }
    public let matchIndex: Int
    public let nsRange: NSRange
    public let matchedText: String
    public let lineNumber: Int
    public let columnNumber: Int
    public let linePreview: String
    public let captureGroups: [CaptureGroupMatch]

    public init(
        matchIndex: Int,
        nsRange: NSRange,
        matchedText: String,
        lineNumber: Int,
        columnNumber: Int,
        linePreview: String,
        captureGroups: [CaptureGroupMatch] = []
    ) {
        self.matchIndex = matchIndex
        self.nsRange = nsRange
        self.matchedText = matchedText
        self.lineNumber = lineNumber
        self.columnNumber = columnNumber
        self.linePreview = linePreview
        self.captureGroups = captureGroups
    }
}

public struct MultiFileSearchMatch: Identifiable, Equatable, Sendable {
    public var id: String { fileURL.path }
    public let fileURL: URL
    public let fileName: String
    public let relativePath: String
    public let matches: [SearchMatch]

    public var totalMatches: Int { matches.count }

    public init(fileURL: URL, fileName: String, relativePath: String, matches: [SearchMatch]) {
        self.fileURL = fileURL
        self.fileName = fileName
        self.relativePath = relativePath
        self.matches = matches
    }
}

public struct PatternPlaygroundEvaluation: Equatable, Sendable {
    public let isValidRegex: Bool
    public let errorMessage: String?
    public let matches: [SearchMatch]
    public let replacementPreview: String
    public let executionTimeMicros: Int64

    public init(
        isValidRegex: Bool,
        errorMessage: String? = nil,
        matches: [SearchMatch] = [],
        replacementPreview: String = "",
        executionTimeMicros: Int64 = 0
    ) {
        self.isValidRegex = isValidRegex
        self.errorMessage = errorMessage
        self.matches = matches
        self.replacementPreview = replacementPreview
        self.executionTimeMicros = executionTimeMicros
    }
}

// MARK: - Diff Models

public enum DiffChangeType: String, Codable, Sendable {
    case equal
    case insert
    case delete
    case modify
}

public struct CharDiffSpan: Equatable, Sendable {
    public let start: Int
    public let end: Int
    public let isChanged: Bool

    public init(start: Int, end: Int, isChanged: Bool) {
        self.start = start
        self.end = end
        self.isChanged = isChanged
    }
}

public struct DiffRow: Identifiable, Equatable, Sendable {
    public var id: Int { rowIndex }
    public let rowIndex: Int
    public let leftLineNumber: Int?
    public let rightLineNumber: Int?
    public let leftText: String?
    public let rightText: String?
    public let changeType: DiffChangeType
    public let leftCharHighlights: [CharDiffSpan]
    public let rightCharHighlights: [CharDiffSpan]

    public var canMergeRightToLeft: Bool { changeType != .equal }
    public var canMergeLeftToRight: Bool { changeType != .equal }

    public init(
        rowIndex: Int,
        leftLineNumber: Int?,
        rightLineNumber: Int?,
        leftText: String?,
        rightText: String?,
        changeType: DiffChangeType,
        leftCharHighlights: [CharDiffSpan] = [],
        rightCharHighlights: [CharDiffSpan] = []
    ) {
        self.rowIndex = rowIndex
        self.leftLineNumber = leftLineNumber
        self.rightLineNumber = rightLineNumber
        self.leftText = leftText
        self.rightText = rightText
        self.changeType = changeType
        self.leftCharHighlights = leftCharHighlights
        self.rightCharHighlights = rightCharHighlights
    }
}

public struct DiffResult: Equatable, Sendable {
    public let leftDocumentId: String?
    public let rightDocumentId: String?
    public let leftTitle: String
    public let rightTitle: String
    public let rows: [DiffRow]
    public let addedCount: Int
    public let removedCount: Int
    public let modifiedCount: Int

    public var isIdentical: Bool {
        addedCount == 0 && removedCount == 0 && modifiedCount == 0
    }

    public init(
        leftDocumentId: String?,
        rightDocumentId: String?,
        leftTitle: String,
        rightTitle: String,
        rows: [DiffRow],
        addedCount: Int,
        removedCount: Int,
        modifiedCount: Int
    ) {
        self.leftDocumentId = leftDocumentId
        self.rightDocumentId = rightDocumentId
        self.leftTitle = leftTitle
        self.rightTitle = rightTitle
        self.rows = rows
        self.addedCount = addedCount
        self.removedCount = removedCount
        self.modifiedCount = modifiedCount
    }
}

// MARK: - BBEdit 14-Operation Text Factory Models

public enum TextOperationType: String, CaseIterable, Codable, Identifiable, Sendable {
    case zapGremlins = "ZAP_GREMLINS"
    case processDuplicateLines = "PROCESS_DUPLICATE_LINES"
    case sortLines = "SORT_LINES"
    case prefixSuffixLines = "PREFIX_SUFFIX_LINES"
    case changeCase = "CHANGE_CASE"
    case stripTrailingWhitespace = "STRIP_TRAILING_WHITESPACE"
    case entabDetab = "ENTAB_DETAB"
    case hardWrapUnwrap = "HARD_WRAP_UNWRAP"
    case addRemoveLineNumbers = "ADD_REMOVE_LINE_NUMBERS"
    case educateStraightenQuotes = "EDUCATE_STRAIGHTEN_QUOTES"
    case canonizeReplaceTable = "CANONIZE_REPLACE_TABLE"
    case processLinesContaining = "PROCESS_LINES_CONTAINING"
    case convertLineEndings = "CONVERT_LINE_ENDINGS"
    case formatPrettify = "FORMAT_PRETTIFY"

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .zapGremlins: return "Zap Gremlins"
        case .processDuplicateLines: return "Process Duplicate Lines"
        case .sortLines: return "Sort Lines"
        case .prefixSuffixLines: return "Prefix / Suffix Lines"
        case .changeCase: return "Change Case"
        case .stripTrailingWhitespace: return "Strip Trailing Whitespace"
        case .entabDetab: return "Entab / Detab"
        case .hardWrapUnwrap: return "Hard Wrap / Unwrap Lines"
        case .addRemoveLineNumbers: return "Add / Remove Line Numbers"
        case .educateStraightenQuotes: return "Educate / Straighten Quotes"
        case .canonizeReplaceTable: return "Canonize (Batch Replace Table)"
        case .processLinesContaining: return "Process Lines Containing"
        case .convertLineEndings: return "Convert Line Endings"
        case .formatPrettify: return "Format / Prettify Code"
        }
    }

    public var defaultSummary: String {
        switch self {
        case .zapGremlins: return "Remove or replace non-ASCII, control characters, and null bytes"
        case .processDuplicateLines: return "Deduplicate lines, keep strictly unique lines, or extract duplicates"
        case .sortLines: return "Sort lines ascending/descending, numerically, or by regex capture group"
        case .prefixSuffixLines: return "Insert or strip custom prefixes and suffixes on every line"
        case .changeCase: return "Convert text to UPPERCASE, lowercase, Title Case, Sentence case, camelCase, snake_case, or iNVERT cASE"
        case .stripTrailingWhitespace: return "Remove trailing spaces and tabs from the end of each line"
        case .entabDetab: return "Convert tabs to spaces (Detab) or spaces to tabs (Entab)"
        case .hardWrapUnwrap: return "Hard wrap lines at a target column width or unwrap paragraph line breaks"
        case .addRemoveLineNumbers: return "Prepend formatted line numbers or strip existing line numbers"
        case .educateStraightenQuotes: return "Convert between typographic curly quotes (“ ” ‘ ’) and straight quotes (\" ')"
        case .canonizeReplaceTable: return "Batch replace key=value pairs or expand aliases across the document"
        case .processLinesContaining: return "Delete matching lines, delete non-matching lines, or extract matches"
        case .convertLineEndings: return "Normalize document line breaks to LF, CRLF, or CR"
        case .formatPrettify: return "Format and indent PowerShell scripts, JSON, or XML documents"
        }
    }

    public var sfSymbol: String {
        switch self {
        case .zapGremlins: return "sparkles"
        case .processDuplicateLines: return "rectangle.stack.badge.minus"
        case .sortLines: return "arrow.up.arrow.down"
        case .prefixSuffixLines: return "arrow.left.and.right.text.vertical"
        case .changeCase: return "textformat"
        case .stripTrailingWhitespace: return "scissors"
        case .entabDetab: return "arrow.right.to.line"
        case .hardWrapUnwrap: return "text.justify.left"
        case .addRemoveLineNumbers: return "list.number"
        case .educateStraightenQuotes: return "quote.opening"
        case .canonizeReplaceTable: return "tablecells"
        case .processLinesContaining: return "line.3.horizontal.decrease.circle"
        case .convertLineEndings: return "return"
        case .formatPrettify: return "wand.and.stars"
        }
    }
}

public enum ZapGremlinsAction: String, CaseIterable, Codable, Sendable {
    case delete = "Delete Gremlins"
    case replaceWithHex = "Replace with \\xNN Hex Code"
    case replaceWithChar = "Replace with Placeholder Character"
}

public enum DuplicateLineMode: String, CaseIterable, Codable, Sendable {
    case leaveOne = "Delete Duplicates (Leave First)"
    case keepUniqueOnly = "Keep Strictly Unique Lines Only"
    case extractDuplicates = "Extract Duplicate Lines"
}

public enum SortDirection: String, CaseIterable, Codable, Sendable {
    case ascending = "Ascending (A → Z)"
    case descending = "Descending (Z → A)"
    case reverseOnly = "Reverse Line Order"
}

public enum PrefixSuffixAction: String, CaseIterable, Codable, Sendable {
    case insert = "Insert Prefix / Suffix"
    case remove = "Remove Matching Prefix / Suffix"
}

public enum CaseTransformMode: String, CaseIterable, Codable, Sendable {
    case uppercase = "UPPERCASE"
    case lowercase = "lowercase"
    case titleCase = "Title Case"
    case capitalizeSentences = "Capitalize sentences"
    case camelCase = "camelCase"
    case snakeCase = "snake_case"
    case invertCase = "iNVERT cASE"
}

public enum TabConversionMode: String, CaseIterable, Codable, Sendable {
    case detabTabsToSpaces = "Detab (Tabs → Spaces)"
    case entabSpacesToTabs = "Entab (Spaces → Tabs)"
}

public enum WrapTransformMode: String, CaseIterable, Codable, Sendable {
    case hardWrap = "Hard Wrap at Column"
    case unwrapRemoveLineBreaks = "Unwrap (Remove Line Breaks)"
}

public enum LineNumberAction: String, CaseIterable, Codable, Sendable {
    case addNumbers = "Add Line Numbers"
    case removeNumbers = "Remove Existing Line Numbers"
}

public enum QuoteTransformMode: String, CaseIterable, Codable, Sendable {
    case straightenQuotes = "Straighten Quotes (“ ” ‘ ’ → \" ')"
    case educateCurlyQuotes = "Educate Quotes (\" ' → “ ” ‘ ’)"
}

public enum MatchingLinesAction: String, CaseIterable, Codable, Sendable {
    case deleteMatching = "Delete Lines Containing Pattern"
    case deleteNonMatching = "Delete Lines NOT Containing Pattern"
    case extractMatching = "Extract Matching Lines to New Document"
}

public enum FormatTarget: String, CaseIterable, Codable, Sendable {
    case auto = "Auto-Detect (PowerShell / JSON / XML)"
    case powershell = "PowerShell Script (.ps1 / .psm1)"
    case json = "JSON Document (.json)"
    case xml = "XML / Plist (.xml / .plist)"
}

public struct TextFactoryStep: Identifiable, Equatable, Codable, Sendable {
    public var id: String
    public var operationType: TextOperationType
    public var enabled: Bool

    public var zapAction: ZapGremlinsAction
    public var zapNonAscii: Bool
    public var zapControlChars: Bool
    public var zapNullBytes: Bool
    public var zapReplacementChar: String

    public var duplicateMode: DuplicateLineMode
    public var duplicateCaseSensitive: Bool
    public var duplicateIgnoreLeadingWhitespace: Bool

    public var sortDirection: SortDirection
    public var sortCaseSensitive: Bool
    public var sortNumeric: Bool
    public var sortRegexPattern: String
    public var sortRegexGroupIndex: Int

    public var prefixSuffixAction: PrefixSuffixAction
    public var linePrefix: String
    public var lineSuffix: String
    public var skipBlankLines: Bool

    public var caseMode: CaseTransformMode
    public var stripWhitespacePreserveBlankLines: Bool

    public var tabConversionMode: TabConversionMode
    public var tabWidth: Int

    public var wrapMode: WrapTransformMode
    public var wrapColumnWidth: Int
    public var wrapPreserveParagraphs: Bool

    public var lineNumberAction: LineNumberAction
    public var lineNumberStart: Int
    public var lineNumberIncrement: Int
    public var lineNumberSeparator: String
    public var lineNumberZeroPadWidth: Int

    public var quoteMode: QuoteTransformMode

    public var canonizeTableRaw: String
    public var canonizeCaseSensitive: Bool
    public var canonizeWholeWords: Bool

    public var matchingLinesAction: MatchingLinesAction
    public var matchingPattern: String
    public var matchingUseRegex: Bool
    public var matchingCaseSensitive: Bool

    public var targetLineEnding: LineEnding

    public var formatTarget: FormatTarget
    public var formatIndentSpaces: Int

    public init(
        id: String = UUID().uuidString,
        operationType: TextOperationType,
        enabled: Bool = true,
        zapAction: ZapGremlinsAction = .delete,
        zapNonAscii: Bool = false,
        zapControlChars: Bool = true,
        zapNullBytes: Bool = true,
        zapReplacementChar: String = "?",
        duplicateMode: DuplicateLineMode = .leaveOne,
        duplicateCaseSensitive: Bool = true,
        duplicateIgnoreLeadingWhitespace: Bool = false,
        sortDirection: SortDirection = .ascending,
        sortCaseSensitive: Bool = false,
        sortNumeric: Bool = false,
        sortRegexPattern: String = "",
        sortRegexGroupIndex: Int = 1,
        prefixSuffixAction: PrefixSuffixAction = .insert,
        linePrefix: String = "",
        lineSuffix: String = "",
        skipBlankLines: Bool = true,
        caseMode: CaseTransformMode = .uppercase,
        stripWhitespacePreserveBlankLines: Bool = true,
        tabConversionMode: TabConversionMode = .detabTabsToSpaces,
        tabWidth: Int = 4,
        wrapMode: WrapTransformMode = .hardWrap,
        wrapColumnWidth: Int = 80,
        wrapPreserveParagraphs: Bool = true,
        lineNumberAction: LineNumberAction = .addNumbers,
        lineNumberStart: Int = 1,
        lineNumberIncrement: Int = 1,
        lineNumberSeparator: String = ": ",
        lineNumberZeroPadWidth: Int = 4,
        quoteMode: QuoteTransformMode = .straightenQuotes,
        canonizeTableRaw: String = "# Example replacement pairs (one per line):\ngci=Get-ChildItem\n%=ForEach-Object\n?=Where-Object",
        canonizeCaseSensitive: Bool = false,
        canonizeWholeWords: Bool = true,
        matchingLinesAction: MatchingLinesAction = .deleteMatching,
        matchingPattern: String = "",
        matchingUseRegex: Bool = true,
        matchingCaseSensitive: Bool = false,
        targetLineEnding: LineEnding = .lf,
        formatTarget: FormatTarget = .auto,
        formatIndentSpaces: Int = 4
    ) {
        self.id = id
        self.operationType = operationType
        self.enabled = enabled
        self.zapAction = zapAction
        self.zapNonAscii = zapNonAscii
        self.zapControlChars = zapControlChars
        self.zapNullBytes = zapNullBytes
        self.zapReplacementChar = zapReplacementChar
        self.duplicateMode = duplicateMode
        self.duplicateCaseSensitive = duplicateCaseSensitive
        self.duplicateIgnoreLeadingWhitespace = duplicateIgnoreLeadingWhitespace
        self.sortDirection = sortDirection
        self.sortCaseSensitive = sortCaseSensitive
        self.sortNumeric = sortNumeric
        self.sortRegexPattern = sortRegexPattern
        self.sortRegexGroupIndex = sortRegexGroupIndex
        self.prefixSuffixAction = prefixSuffixAction
        self.linePrefix = linePrefix
        self.lineSuffix = lineSuffix
        self.skipBlankLines = skipBlankLines
        self.caseMode = caseMode
        self.stripWhitespacePreserveBlankLines = stripWhitespacePreserveBlankLines
        self.tabConversionMode = tabConversionMode
        self.tabWidth = tabWidth
        self.wrapMode = wrapMode
        self.wrapColumnWidth = wrapColumnWidth
        self.wrapPreserveParagraphs = wrapPreserveParagraphs
        self.lineNumberAction = lineNumberAction
        self.lineNumberStart = lineNumberStart
        self.lineNumberIncrement = lineNumberIncrement
        self.lineNumberSeparator = lineNumberSeparator
        self.lineNumberZeroPadWidth = lineNumberZeroPadWidth
        self.quoteMode = quoteMode
        self.canonizeTableRaw = canonizeTableRaw
        self.canonizeCaseSensitive = canonizeCaseSensitive
        self.canonizeWholeWords = canonizeWholeWords
        self.matchingLinesAction = matchingLinesAction
        self.matchingPattern = matchingPattern
        self.matchingUseRegex = matchingUseRegex
        self.matchingCaseSensitive = matchingCaseSensitive
        self.targetLineEnding = targetLineEnding
        self.formatTarget = formatTarget
        self.formatIndentSpaces = formatIndentSpaces
    }

    public var configurationSummary: String {
        switch operationType {
        case .zapGremlins:
            return "\(zapAction.rawValue) (Ctrl=\(zapControlChars), Null=\(zapNullBytes), NonASCII=\(zapNonAscii))"
        case .processDuplicateLines:
            return "\(duplicateMode.rawValue) (CaseSensitive=\(duplicateCaseSensitive))"
        case .sortLines:
            let num = sortNumeric ? ", Numeric" : ""
            let rx = sortRegexPattern.isEmpty ? "" : ", Regex='\(sortRegexPattern)'"
            return "\(sortDirection.rawValue)\(num)\(rx)"
        case .prefixSuffixLines:
            return "\(prefixSuffixAction.rawValue): prefix='\(linePrefix)', suffix='\(lineSuffix)'"
        case .changeCase:
            return caseMode.rawValue
        case .stripTrailingWhitespace:
            return "Strip trailing spaces and tabs"
        case .entabDetab:
            return "\(tabConversionMode.rawValue) (\(tabWidth) spaces)"
        case .hardWrapUnwrap:
            return wrapMode == .hardWrap ? "Hard wrap at col \(wrapColumnWidth)" : wrapMode.rawValue
        case .addRemoveLineNumbers:
            return "\(lineNumberAction.rawValue) (start=\(lineNumberStart), pad=\(lineNumberZeroPadWidth))"
        case .educateStraightenQuotes:
            return quoteMode.rawValue
        case .canonizeReplaceTable:
            let count = canonizeTableRaw
                .components(separatedBy: "\n")
                .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty && !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") && $0.contains("=") }
                .count
            return "\(count) replacement rules"
        case .processLinesContaining:
            return "\(matchingLinesAction.rawValue): '\(matchingPattern.isEmpty ? "*" : matchingPattern)'"
        case .convertLineEndings:
            return "Target: \(targetLineEnding.displayName)"
        case .formatPrettify:
            return "\(formatTarget.rawValue) (\(formatIndentSpaces) spaces)"
        }
    }
}

public struct ExtractedDocumentItem: Equatable, Sendable {
    public let title: String
    public let content: String

    public init(title: String, content: String) {
        self.title = title
        self.content = content
    }
}

public struct TextFactoryResult: Equatable, Sendable {
    public let transformedText: String
    public let extractedDocuments: [ExtractedDocumentItem]
    public let targetLineEnding: LineEnding?
    public let stepsExecuted: Int
    public let summary: String

    public init(
        transformedText: String,
        extractedDocuments: [ExtractedDocumentItem] = [],
        targetLineEnding: LineEnding? = nil,
        stepsExecuted: Int = 0,
        summary: String = ""
    ) {
        self.transformedText = transformedText
        self.extractedDocuments = extractedDocuments
        self.targetLineEnding = targetLineEnding
        self.stepsExecuted = stepsExecuted
        self.summary = summary
    }
}
