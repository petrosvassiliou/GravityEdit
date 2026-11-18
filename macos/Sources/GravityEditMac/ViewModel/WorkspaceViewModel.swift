import AppKit
import Combine
import Foundation
import GravityEditCore
import SwiftUI
import UniformTypeIdentifiers

public enum FindSheetTab: String, CaseIterable, Identifiable {
    case document = "Document Find & Replace"
    case directory = "Multi-File Search"
    case playground = "Regex Playground"

    public var id: String { rawValue }
}

public struct ScrollJumpRequest: Equatable {
    public let id: UUID
    public let documentId: String
    public let utf16Range: NSRange

    public init(documentId: String, utf16Range: NSRange) {
        self.id = UUID()
        self.documentId = documentId
        self.utf16Range = utf16Range
    }
}

public struct CommandPaletteItem: Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let shortcutHint: String
    public let sfSymbol: String
    public let action: () -> Void
}

@MainActor
public final class WorkspaceViewModel: ObservableObject {

    // MARK: - Persistent Preferences

    @AppStorage("gravityedit.editor.fontSize") public var fontSize: Double = 13.5
    @AppStorage("gravityedit.editor.tabWidth") public var tabWidth: Int = 4
    @AppStorage("gravityedit.editor.softWrap") public var softWrap: Bool = false
    @AppStorage("gravityedit.editor.showLineNumbers") public var showLineNumbers: Bool = true
    @AppStorage("gravityedit.editor.showInvisibles") public var showInvisibles: Bool = false
    @AppStorage("gravityedit.editor.showPageGuides") public var showPageGuides: Bool = true
    @AppStorage("gravityedit.editor.highlightCurrentLine") public var highlightCurrentLine: Bool = true
    @AppStorage("gravityedit.editor.liveAnalyzer") public var liveScriptAnalyzer: Bool = true {
        didSet { refreshActiveAnalysis() }
    }
    @AppStorage("gravityedit.appearance.theme") public var themeModeRaw: String = AppearanceThemeMode.system.rawValue
    @AppStorage("gravityedit.appearance.language") public var appLanguageRaw: String = AppLanguage.system.rawValue

    public var themeMode: AppearanceThemeMode {
        get { AppearanceThemeMode(rawValue: themeModeRaw) ?? .system }
        set { themeModeRaw = newValue.rawValue; objectWillChange.send() }
    }

    public var appLanguage: AppLanguage {
        get { AppLanguage(rawValue: appLanguageRaw) ?? .system }
        set { appLanguageRaw = newValue.rawValue; objectWillChange.send() }
    }

    public func tr(_ key: L10nKey) -> String {
        LocalizationCatalog.tr(key, language: appLanguage)
    }

    // MARK: - Workspace & Document State

    @Published public var documents: [EditorDocument] = []
    @Published public var activeDocumentId: String = ""
    @Published public var secondaryDocumentId: String = ""
    @Published public var splitMode: SplitViewMode = .none
    @Published public var isSecondaryPaneFocused: Bool = false
    @Published public var sidebarSection: SidebarSection = .openDocuments

    // Disk Browser
    @Published public var mountedFolderURL: URL? = nil
    @Published public var diskBrowserNodes: [DiskBrowserNode] = []

    // Live Parsed State (Primary & Secondary)
    @Published public var primarySymbols: [AstSymbol] = []
    @Published public var primaryFolds: [FoldRegion] = []
    @Published public var primaryDiagnostics: [PowerShellDiagnostic] = []

    @Published public var secondarySymbols: [AstSymbol] = []
    @Published public var secondaryFolds: [FoldRegion] = []
    @Published public var secondaryDiagnostics: [PowerShellDiagnostic] = []

    // Search, Text Factory, Diff & Clippings State
    @Published public var searchOptions: SearchOptions = SearchOptions()
    @Published public var documentMatches: [SearchMatch] = []
    @Published public var activeMatchIndex: Int = 0
    @Published public var multiFileMatches: [MultiFileSearchMatch] = []
    @Published public var searchErrorMessage: String? = nil

    @Published public var textFactorySteps: [TextFactoryStep] = [
        TextFactoryStep(operationType: .stripTrailingWhitespace, enabled: true),
        TextFactoryStep(operationType: .formatPrettify, enabled: true)
    ]

    @Published public var diffLeftDocumentId: String = ""
    @Published public var diffRightDocumentId: String = ""
    @Published public var currentDiffResult: DiffResult? = nil

    @Published public var customClippings: [Clipping] = []

    // Sheets, Popovers & Floating Overlays
    @Published public var showNewDocumentSheet: Bool = false
    @Published public var showFindReplaceSheet: Bool = false
    @Published public var findSheetInitialTab: FindSheetTab = .document
    @Published public var showTextFactorySheet: Bool = false
    @Published public var showDiffSheet: Bool = false
    @Published public var showDiagnosticsSheet: Bool = false
    @Published public var showClippingsSheet: Bool = false
    @Published public var showCommandPalette: Bool = false
    @Published public var showGoToLinePopover: Bool = false

    // Tab inline rename
    @Published public var renamingDocumentId: String? = nil
    @Published public var renameDraftStem: String = ""

    // Status Toast & Scroll Jump Token
    @Published public var statusBannerMessage: String? = nil
    @Published public var scrollJumpRequest: ScrollJumpRequest? = nil

    private var scratchpadSaveWorkItem: DispatchWorkItem?
    private var analysisWorkItem: DispatchWorkItem?

    // MARK: - Computed Helpers

    public var focusedDocumentId: String {
        if splitMode != .none && isSecondaryPaneFocused && documents.contains(where: { $0.id == secondaryDocumentId }) {
            return secondaryDocumentId
        }
        return activeDocumentId
    }

    public var focusedDocument: EditorDocument? {
        documents.first(where: { $0.id == focusedDocumentId }) ?? documents.first
    }

    public var primaryDocument: EditorDocument? {
        documents.first(where: { $0.id == activeDocumentId }) ?? documents.first
    }

    public var secondaryDocument: EditorDocument? {
        documents.first(where: { $0.id == secondaryDocumentId }) ?? documents.last
    }

    public var focusedSymbols: [AstSymbol] {
        (splitMode != .none && isSecondaryPaneFocused) ? secondarySymbols : primarySymbols
    }

    public var focusedDiagnostics: [PowerShellDiagnostic] {
        (splitMode != .none && isSecondaryPaneFocused) ? secondaryDiagnostics : primaryDiagnostics
    }

    public var allClippings: [Clipping] {
        ClippingsManager.builtInClippings + customClippings
    }

    // MARK: - Initialization & Persistence

    public init() {
        loadCustomClippings()
        let scratchpad = loadOrCreateScratchpadDocument()
        let welcomeScript = Self.createWelcomePowerShellDocument()
        self.documents = [welcomeScript, scratchpad]
        self.activeDocumentId = welcomeScript.id
        self.secondaryDocumentId = scratchpad.id
        refreshActiveAnalysisImmediate()
    }

    private var appSupportDirectory: URL {
        let fm = FileManager.default
        let base = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fm.temporaryDirectory
        let dir = base.appendingPathComponent("GravityEdit", isDirectory: true)
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private var scratchpadFileURL: URL {
        appSupportDirectory.appendingPathComponent("Scratchpad.ps1")
    }

    private var customClippingsFileURL: URL {
        appSupportDirectory.appendingPathComponent("custom_clippings.json")
    }

    private func loadOrCreateScratchpadDocument() -> EditorDocument {
        if let data = try? Data(contentsOf: scratchpadFileURL),
           let str = String(data: data, encoding: .utf8) {
            return EditorDocument(
                id: "scratchpad-permanent",
                title: "Scratchpad.ps1",
                fileURL: scratchpadFileURL,
                content: LineEnding.normalizeToLf(str),
                savedContent: LineEnding.normalizeToLf(str),
                isScratchpad: true,
                language: .powershell
            )
        }
        let starter = """
        # GravityEdit Persistent Scratchpad (Auto-Saved on macOS)
        # Notes, ad-hoc PowerShell pipelines, and regex snippets stay preserved across launches.

        $psInfo = [ordered]@{
            HostOS  = 'macOS Liquid Glass Edition'
            Version = '\(MacAutoUpdateEngine.currentAppVersion)'
        }
        $psInfo | ConvertTo-Json
        """
        return EditorDocument(
            id: "scratchpad-permanent",
            title: "Scratchpad.ps1",
            fileURL: scratchpadFileURL,
            content: starter,
            savedContent: starter,
            isScratchpad: true,
            language: .powershell
        )
    }

    private static func createWelcomePowerShellDocument() -> EditorDocument {
        let script = """
        <#
        .SYNOPSIS
            GravityEdit for macOS — PowerShell & BBEdit Workbench (Liquid Glass Edition)

        .DESCRIPTION
            Native SwiftUI + AppKit code editor featuring PowerShell AST & Procedural Function Navigation,
            live PSScriptAnalyzer linting with 1-click Quick Fixes, 14 BBEdit Text Factory transforms,
            PCRE Regex Find/Replace, Myers Side-by-Side Diff, and Sparkle 2 automatic updates.
        #>

        #region Initialization & Configuration
        function Initialize-GravityWorkspace {
            [CmdletBinding(SupportsShouldProcess = $true)]
            param (
                [Parameter(Mandatory = $true)]
                [ValidateNotNullOrEmpty()]
                [string]$WorkspacePath,

                [Parameter(Mandatory = $false)]
                [switch]$VerifySignatures
            )

            begin {
                Write-Verbose "Mounting workspace: $WorkspacePath"
            }

            process {
                if (Test-Path -Path $WorkspacePath) {
                    Get-ChildItem -Path $WorkspacePath -Filter '*.ps1' |
                        Where-Object { $_.Length -gt 0 } |
                        Select-Object -Property Name, Length, LastWriteTime
                }
            }
        }
        #endregion

        # === Health & Diagnostics Check ===
        function Test-EndpointTelemetry {
            param (
                [uri]$Endpoint = 'https://api.github.com',
                [int]$TimeoutSec = 15
            )

            try {
                $response = Invoke-RestMethod -Uri $Endpoint -Method Get -TimeoutSec $TimeoutSec -ErrorAction Stop
                return [pscustomobject]@{
                    Endpoint = $Endpoint
                    Status   = 'Healthy'
                    Payload  = $response
                }
            }
            catch {
                Write-Error -Message "Endpoint check failed: $($_.Exception.Message)"
                throw
            }
        }
        """
        return EditorDocument(
            title: "GravityWorkbench.ps1",
            content: script,
            savedContent: script,
            language: .powershell,
            encoding: .utf8,
            lineEnding: .lf
        )
    }

    private func scheduleScratchpadSave(_ content: String) {
        scratchpadSaveWorkItem?.cancel()
        let url = scratchpadFileURL
        let item = DispatchWorkItem {
            try? Data(content.utf8).write(to: url, options: .atomic)
        }
        scratchpadSaveWorkItem = item
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 0.4, execute: item)
    }

    private func loadCustomClippings() {
        guard let data = try? Data(contentsOf: customClippingsFileURL),
              let decoded = try? JSONDecoder().decode([Clipping].self, from: data) else {
            return
        }
        customClippings = decoded
    }

    private func saveCustomClippings() {
        guard let data = try? JSONEncoder().encode(customClippings) else { return }
        try? data.write(to: customClippingsFileURL, options: .atomic)
    }

    // MARK: - Live AST, Fold & Diagnostic Refresh

    public func refreshActiveAnalysis() {
        analysisWorkItem?.cancel()
        let item = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                self?.refreshActiveAnalysisImmediate()
            }
        }
        analysisWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12, execute: item)
    }

    public func refreshActiveAnalysisImmediate() {
        if let pDoc = primaryDocument {
            primarySymbols = PowerShellEngine.extractAstSymbols(pDoc.content, language: pDoc.language)
            primaryFolds = PowerShellEngine.extractFoldRegions(pDoc.content, language: pDoc.language)
            primaryDiagnostics = (liveScriptAnalyzer && pDoc.language == .powershell)
                ? PowerShellEngine.analyzeScriptDiagnostics(pDoc.content)
                : []
        } else {
            primarySymbols = []
            primaryFolds = []
            primaryDiagnostics = []
        }

        if splitMode != .none, let sDoc = secondaryDocument {
            secondarySymbols = PowerShellEngine.extractAstSymbols(sDoc.content, language: sDoc.language)
            secondaryFolds = PowerShellEngine.extractFoldRegions(sDoc.content, language: sDoc.language)
            secondaryDiagnostics = (liveScriptAnalyzer && sDoc.language == .powershell)
                ? PowerShellEngine.analyzeScriptDiagnostics(sDoc.content)
                : []
        } else {
            secondarySymbols = []
            secondaryFolds = []
            secondaryDiagnostics = []
        }
    }

    // MARK: - Document Mutations

    public func updateDocumentContent(id: String, newContent: String, selectionRange: NSRange) {
        guard let idx = documents.firstIndex(where: { $0.id == id }) else { return }
        let normalized = LineEnding.normalizeToLf(newContent)
        let contentChanged = documents[idx].content != normalized
        documents[idx].content = normalized
        documents[idx].selectionStart = selectionRange.location
        documents[idx].selectionEnd = selectionRange.location + selectionRange.length

        if documents[idx].isScratchpad && contentChanged {
            scheduleScratchpadSave(normalized)
        }
        if contentChanged {
            refreshActiveAnalysis()
        }
    }

    public func updateDocumentSelection(id: String, selectionRange: NSRange) {
        guard let idx = documents.firstIndex(where: { $0.id == id }) else { return }
        if documents[idx].selectionStart != selectionRange.location ||
           documents[idx].selectionEnd != selectionRange.location + selectionRange.length {
            documents[idx].selectionStart = selectionRange.location
            documents[idx].selectionEnd = selectionRange.location + selectionRange.length
        }
    }

    public func toggleFold(documentId: String, startLine: Int) {
        guard let idx = documents.firstIndex(where: { $0.id == documentId }) else { return }
        if documents[idx].foldedStartLines.contains(startLine) {
            documents[idx].foldedStartLines.remove(startLine)
        } else {
            documents[idx].foldedStartLines.insert(startLine)
        }
    }

    public func foldAllInFocusedDocument() {
        let targetId = focusedDocumentId
        guard let idx = documents.firstIndex(where: { $0.id == targetId }) else { return }
        let folds = (targetId == secondaryDocumentId && splitMode != .none) ? secondaryFolds : primaryFolds
        documents[idx].foldedStartLines = Set(folds.map(\.startLine))
        postToast("Folded \(folds.count) region\(folds.count == 1 ? "" : "s")")
    }

    public func unfoldAllInFocusedDocument() {
        let targetId = focusedDocumentId
        guard let idx = documents.firstIndex(where: { $0.id == targetId }) else { return }
        documents[idx].foldedStartLines.removeAll()
        postToast("Unfolded all regions")
    }

    public func setFocusedLanguage(_ lang: SyntaxLanguage) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        documents[idx].language = lang
        refreshActiveAnalysisImmediate()
    }

    public func setFocusedEncoding(_ encoding: DocumentEncoding) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        documents[idx].encoding = encoding
        postToast("Encoding set to \(encoding.displayName)")
    }

    public func setFocusedLineEnding(_ le: LineEnding) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        documents[idx].lineEnding = le
        postToast("Line endings set to \(le.displayName)")
    }

    // MARK: - Create, Open, Save & Close Documents (Native AppKit Panels & Alerts)

    public func createNewDocument(
        baseName: String = "",
        language: SyntaxLanguage = .powershell,
        encoding: DocumentEncoding = .utf8,
        lineEnding: LineEnding = .lf,
        includeStarterTemplate: Bool = true
    ) {
        let untitledIndex = documents.filter { !$0.isScratchpad }.count + 1
        let stem = baseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Untitled-\(untitledIndex)"
            : (baseName as NSString).deletingPathExtension
        let title = "\(stem).\(language.primaryExtension)"
        let body = includeStarterTemplate ? Self.starterTemplate(for: language, fileName: title) : ""

        let doc = EditorDocument(
            title: title,
            content: body,
            savedContent: body,
            language: language,
            encoding: encoding,
            lineEnding: lineEnding
        )
        documents.insert(doc, at: max(0, documents.count - 1))
        selectTab(doc.id)
        refreshActiveAnalysisImmediate()
    }

    public static func starterTemplate(for language: SyntaxLanguage, fileName: String) -> String {
        switch language {
        case .powershell:
            return """
            <#
            .SYNOPSIS
                \(fileName)
            #>
            [CmdletBinding()]
            param (
                [Parameter(Mandatory = $false)]
                [string]$Path = $PSScriptRoot
            )

            process {
                Write-Verbose "Executing \(fileName) in $Path"
            }
            """
        case .swift:
            return """
            import Foundation

            struct MainTask {
                func run() throws {
                    print("Hello from \(fileName)")
                }
            }
            """
        case .json:
            return "{\n  \"name\": \"\(fileName)\",\n  \"version\": \"1.0.0\"\n}\n"
        case .xml:
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<configuration>\n  <app name=\"GravityEdit\" />\n</configuration>\n"
        case .yaml:
            return "# \(fileName)\nname: gravity-service\nenabled: true\n"
        case .markdown:
            return "# \(fileName)\n\n## Overview\n\n- Created with GravityEdit for macOS.\n"
        case .python:
            return "#!/usr/bin/env python3\n\"\"\"\(fileName)\"\"\"\n\n\ndef main() -> None:\n    print(\"Hello from \(fileName)\")\n\n\nif __name__ == \"__main__\":\n    main()\n"
        case .kotlin:
            return "fun main() {\n    println(\"Hello from \(fileName)\")\n}\n"
        case .htmlCss:
            return "<!doctype html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"utf-8\" />\n  <title>\(fileName)</title>\n</head>\n<body>\n  <h1>\(fileName)</h1>\n</body>\n</html>\n"
        case .shellBash:
            return "#!/usr/bin/env zsh\nset -euo pipefail\n\necho \"Running \(fileName)\"\n"
        case .plainText:
            return ""
        }
    }

    public func selectTab(_ id: String) {
        if splitMode != .none && isSecondaryPaneFocused {
            secondaryDocumentId = id
        } else {
            activeDocumentId = id
        }
        refreshActiveAnalysisImmediate()
    }

    public func switchToScratchpad() {
        if let scratch = documents.first(where: \.isScratchpad) {
            selectTab(scratch.id)
        }
    }

    public func openDocumentWithPanel() {
        let panel = NSOpenPanel()
        panel.title = "Open File in GravityEdit"
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = true
        panel.treatsFilePackagesAsDirectories = true

        guard panel.runModal() == .OK else { return }
        for url in panel.urls {
            openFile(at: url)
        }
    }

    public func openFile(at url: URL) {
        if let existing = documents.first(where: { $0.fileURL?.standardizedFileURL == url.standardizedFileURL }) {
            selectTab(existing.id)
            return
        }
        do {
            let data = try Data(contentsOf: url)
            let (rawText, encoding) = DocumentEncoding.decode(data)
            let lineEnding = LineEnding.detect(rawText)
            let normalized = LineEnding.normalizeToLf(rawText)
            let fileName = url.lastPathComponent
            let doc = EditorDocument(
                title: fileName,
                fileURL: url,
                content: normalized,
                savedContent: normalized,
                isScratchpad: false,
                language: SyntaxLanguage.fromFileName(fileName),
                encoding: encoding,
                lineEnding: lineEnding
            )
            let insertAt = max(0, documents.firstIndex(where: \.isScratchpad) ?? documents.count)
            documents.insert(doc, at: insertAt)
            selectTab(doc.id)
            refreshActiveAnalysisImmediate()
            NSDocumentController.shared.noteNewRecentDocumentURL(url)
            postToast("Opened \(fileName)")
        } catch {
            postToast("Failed to open \(url.lastPathComponent): \(error.localizedDescription)")
        }
    }

    @discardableResult
    public func saveFocusedDocument() -> Bool {
        saveDocument(id: focusedDocumentId)
    }

    @discardableResult
    public func saveDocument(id: String) -> Bool {
        guard let idx = documents.firstIndex(where: { $0.id == id }) else { return false }
        let doc = documents[idx]
        if let url = doc.fileURL {
            return writeDocument(at: idx, to: url)
        } else {
            return saveDocumentAs(id: id)
        }
    }

    @discardableResult
    public func saveFocusedDocumentAs() -> Bool {
        saveDocumentAs(id: focusedDocumentId)
    }

    @discardableResult
    public func saveDocumentAs(id: String) -> Bool {
        guard let idx = documents.firstIndex(where: { $0.id == id }) else { return false }
        let doc = documents[idx]

        let panel = NSSavePanel()
        panel.title = "Save Document"
        panel.nameFieldStringValue = doc.title
        panel.canCreateDirectories = true
        if let dir = doc.fileURL?.deletingLastPathComponent() ?? mountedFolderURL {
            panel.directoryURL = dir
        }

        guard panel.runModal() == .OK, let targetURL = panel.url else { return false }
        return writeDocument(at: idx, to: targetURL)
    }

    private func writeDocument(at idx: Int, to url: URL) -> Bool {
        let doc = documents[idx]
        let serialized = LineEnding.applyLineEnding(doc.content, target: doc.lineEnding)
        let data = doc.encoding.encode(serialized)
        do {
            try data.write(to: url, options: .atomic)
            documents[idx].fileURL = url
            documents[idx].title = url.lastPathComponent
            documents[idx].savedContent = doc.content
            documents[idx].language = SyntaxLanguage.fromFileName(url.lastPathComponent)
            NSDocumentController.shared.noteNewRecentDocumentURL(url)
            refreshActiveAnalysisImmediate()
            postToast("Saved \(url.lastPathComponent)")
            return true
        } catch {
            postToast("Save failed: \(error.localizedDescription)")
            return false
        }
    }

    public func closeDocument(id: String) {
        guard let idx = documents.firstIndex(where: { $0.id == id }) else { return }
        let doc = documents[idx]
        guard !doc.isScratchpad else {
            postToast("Scratchpad is permanent and auto-saved")
            return
        }

        if doc.isDirty {
            let alert = NSAlert()
            alert.alertStyle = .warning
            alert.messageText = "Do you want to save changes made to “\(doc.title)”?"
            alert.informativeText = "Your changes will be lost if you don’t save them."
            alert.addButton(withTitle: "Save")
            alert.addButton(withTitle: "Don’t Save")
            alert.addButton(withTitle: "Cancel")

            let resp = alert.runModal()
            if resp == .alertThirdButtonReturn {
                return
            } else if resp == .alertFirstButtonReturn {
                if !saveDocument(id: id) { return }
            }
        }

        documents.removeAll { $0.id == id }
        if documents.isEmpty {
            documents.append(loadOrCreateScratchpadDocument())
        }
        if activeDocumentId == id {
            activeDocumentId = documents.first?.id ?? ""
        }
        if secondaryDocumentId == id {
            secondaryDocumentId = documents.last?.id ?? activeDocumentId
        }
        refreshActiveAnalysisImmediate()
    }

    /// Invoked by `NSApplicationDelegate.applicationShouldTerminate` per Apple HIG.
    public func confirmQuitWithUnsavedDocuments() -> Bool {
        let dirtyDocs = documents.filter(\.isDirty)
        guard !dirtyDocs.isEmpty else { return true }

        let names = dirtyDocs.map(\.title).joined(separator: ", ")
        let alert = NSAlert()
        alert.alertStyle = .warning
        alert.messageText = "You have \(dirtyDocs.count) unsaved document\(dirtyDocs.count == 1 ? "" : "s")."
        alert.informativeText = "Review unsaved changes in: \(names)"
        alert.addButton(withTitle: "Save All & Quit")
        alert.addButton(withTitle: "Quit Without Saving")
        alert.addButton(withTitle: "Cancel")

        let resp = alert.runModal()
        if resp == .alertThirdButtonReturn {
            return false
        }
        if resp == .alertFirstButtonReturn {
            for d in dirtyDocs {
                if !saveDocument(id: d.id) {
                    return false
                }
            }
        }
        return true
    }

    // MARK: - Inline Stem-Only Tab Rename

    public func beginRenamingDocument(id: String) {
        guard let doc = documents.first(where: { $0.id == id }), !doc.isScratchpad else { return }
        renamingDocumentId = id
        renameDraftStem = (doc.title as NSString).deletingPathExtension
    }

    public func commitRenamingDocument() {
        guard let id = renamingDocumentId,
              let idx = documents.firstIndex(where: { $0.id == id }) else {
            renamingDocumentId = nil
            return
        }
        let newTitle = EditorDocument.normalizedRenamedTitle(
            currentTitle: documents[idx].title,
            userInput: renameDraftStem
        )
        documents[idx].title = newTitle
        documents[idx].language = SyntaxLanguage.fromFileName(newTitle)
        renamingDocumentId = nil
        refreshActiveAnalysisImmediate()
    }

    // MARK: - Disk Browser Folder Mounting

    public func mountProjectFolderWithPanel() {
        let panel = NSOpenPanel()
        panel.title = "Mount Project Folder in Disk Browser"
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false

        guard panel.runModal() == .OK, let url = panel.url else { return }
        mountProjectFolder(url: url)
    }

    public func mountProjectFolder(url: URL) {
        mountedFolderURL = url
        sidebarSection = .diskBrowser
        reloadDiskBrowserTree()
        postToast("Mounted \(url.lastPathComponent)")
    }

    public func reloadDiskBrowserTree() {
        guard let root = mountedFolderURL else {
            diskBrowserNodes = []
            return
        }
        diskBrowserNodes = Self.loadDirectoryChildren(at: root, depth: 0)
    }

    private static func loadDirectoryChildren(at dirURL: URL, depth: Int) -> [DiskBrowserNode] {
        guard depth <= 4 else { return [] }
        let fm = FileManager.default
        guard let urls = try? fm.contentsOfDirectory(
            at: dirURL,
            includingPropertiesForKeys: [.isDirectoryKey, .isHiddenKey, .fileSizeKey],
            options: [.skipsHiddenFiles]
        ) else {
            return []
        }

        let sorted = urls.sorted { a, b in
            let aDir = (try? a.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false
            let bDir = (try? b.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false
            if aDir != bDir { return aDir && !bDir }
            return a.lastPathComponent.localizedStandardCompare(b.lastPathComponent) == .orderedAscending
        }

        return sorted.prefix(250).map { url in
            let vals = try? url.resourceValues(forKeys: [.isDirectoryKey, .fileSizeKey])
            let isDir = vals?.isDirectory ?? false
            let size = Int64(vals?.fileSize ?? 0)
            let children: [DiskBrowserNode]? = isDir ? loadDirectoryChildren(at: url, depth: depth + 1) : nil
            return DiskBrowserNode(
                url: url,
                name: url.lastPathComponent,
                isDirectory: isDir,
                depth: depth,
                sizeBytes: size,
                children: children
            )
        }
    }

    // MARK: - Navigation & Code Editing Shortcuts

    public func jumpToLine(_ oneBasedLine: Int) {
        guard let doc = focusedDocument else { return }
        let clamped = min(max(1, oneBasedLine), doc.lineCount)
        let nsRange = doc.nsRangeForLineIndex(clamped - 1)
        if let idx = documents.firstIndex(where: { $0.id == doc.id }) {
            documents[idx].selectionStart = nsRange.location
            documents[idx].selectionEnd = nsRange.location
        }
        scrollJumpRequest = ScrollJumpRequest(documentId: doc.id, utf16Range: nsRange)
    }

    public func jumpToSymbol(_ symbol: AstSymbol) {
        jumpToLine(symbol.lineNumber)
    }

    public func jumpToDiagnostic(_ diagnostic: PowerShellDiagnostic) {
        jumpToLine(diagnostic.lineNumber)
    }

    public func applyQuickFix(_ diagnostic: PowerShellDiagnostic) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let updated = PowerShellEngine.applyDiagnosticQuickFix(
            documentContent: documents[idx].content,
            diagnostic: diagnostic
        )
        documents[idx].content = updated
        refreshActiveAnalysisImmediate()
        postToast("Applied Quick Fix: \(diagnostic.ruleId)")
    }

    public func applyAllQuickFixes() {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let (updated, count) = PowerShellEngine.applyAllQuickFixes(documentContent: documents[idx].content)
        guard count > 0 else {
            postToast("No automatic Quick Fixes needed")
            return
        }
        documents[idx].content = updated
        refreshActiveAnalysisImmediate()
        postToast("Applied \(count) PSScriptAnalyzer Quick Fix\(count == 1 ? "" : "es")")
    }

    public func duplicateCurrentLine() {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        var lines = doc.lines
        let lineIdx = min(max(0, doc.lineIndexForUTF16Offset(doc.safeSelectionStart)), lines.count - 1)
        lines.insert(lines[lineIdx], at: lineIdx + 1)
        documents[idx].content = lines.joined(separator: "\n")
        refreshActiveAnalysis()
    }

    public func deleteCurrentLine() {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        var lines = doc.lines
        guard !lines.isEmpty else { return }
        let lineIdx = min(max(0, doc.lineIndexForUTF16Offset(doc.safeSelectionStart)), lines.count - 1)
        lines.remove(at: lineIdx)
        documents[idx].content = lines.isEmpty ? "" : lines.joined(separator: "\n")
        refreshActiveAnalysis()
    }

    public func selectCurrentLine() {
        guard let doc = focusedDocument else { return }
        let lineIdx = doc.lineIndexForUTF16Offset(doc.safeSelectionStart)
        let r = doc.nsRangeForLineIndex(lineIdx)
        scrollJumpRequest = ScrollJumpRequest(documentId: doc.id, utf16Range: r)
    }

    public func toggleCommentOnSelection() {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        let prefix = doc.language.singleLineCommentPrefix ?? "#"
        var lines = doc.lines
        let startLine = doc.lineIndexForUTF16Offset(doc.safeSelectionStart)
        let endLine = doc.lineIndexForUTF16Offset(doc.safeSelectionEnd)
        guard startLine <= endLine, endLine < lines.count else { return }

        let nonBlankSlice = (startLine...endLine).map { lines[$0] }.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        let allCommented = !nonBlankSlice.isEmpty && nonBlankSlice.allSatisfy {
            $0.trimmingCharacters(in: .whitespaces).hasPrefix(prefix)
        }

        for i in startLine...endLine {
            if allCommented {
                if let range = lines[i].range(of: prefix + " ") {
                    lines[i].removeSubrange(range)
                } else if let range = lines[i].range(of: prefix) {
                    lines[i].removeSubrange(range)
                }
            } else {
                lines[i] = "\(prefix) \(lines[i])"
            }
        }
        documents[idx].content = lines.joined(separator: "\n")
        refreshActiveAnalysis()
    }

    public func indentSelection(outdent: Bool) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        let spaces = String(repeating: " ", count: max(1, tabWidth))
        var lines = doc.lines
        let startLine = doc.lineIndexForUTF16Offset(doc.safeSelectionStart)
        let endLine = doc.lineIndexForUTF16Offset(doc.safeSelectionEnd)
        guard startLine <= endLine, endLine < lines.count else { return }

        for i in startLine...endLine {
            if outdent {
                if lines[i].hasPrefix(spaces) {
                    lines[i].removeFirst(spaces.count)
                } else if lines[i].hasPrefix("\t") {
                    lines[i].removeFirst()
                } else {
                    let lead = lines[i].prefix(while: { $0 == " " }).count
                    if lead > 0 { lines[i].removeFirst(min(lead, tabWidth)) }
                }
            } else {
                lines[i] = spaces + lines[i]
            }
        }
        documents[idx].content = lines.joined(separator: "\n")
        refreshActiveAnalysis()
    }

    public func zoomIn() { fontSize = min(32.0, fontSize + 1.0) }
    public func zoomOut() { fontSize = max(9.0, fontSize - 1.0) }
    public func zoomReset() { fontSize = 13.5 }

    // MARK: - Direct BBEdit Menu Bar Text Transformations

    public func applyQuickStepToSelectionOrDocument(_ step: TextFactoryStep) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        let selStart = doc.safeSelectionStart
        let selEnd = doc.safeSelectionEnd
        let hasSelection = selEnd > selStart

        let targetInput = hasSelection ? doc.selectedText : doc.content
        let result = TextTransformEngine.executePipeline(
            inputText: targetInput,
            steps: [step],
            documentLanguage: doc.language
        )

        if hasSelection {
            let ns = doc.content as NSString
            documents[idx].content = ns.replacingCharacters(
                in: NSRange(location: selStart, length: selEnd - selStart),
                with: result.transformedText
            )
        } else {
            documents[idx].content = result.transformedText
        }

        if let le = result.targetLineEnding {
            documents[idx].lineEnding = le
        }

        for extra in result.extractedDocuments {
            let newDoc = EditorDocument(title: extra.title, content: extra.content, savedContent: "", language: .plainText)
            documents.insert(newDoc, at: max(0, documents.count - 1))
        }

        refreshActiveAnalysisImmediate()
        postToast(step.operationType.title)
    }

    public func runFullTextFactoryPipeline() {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        let result = TextTransformEngine.executePipeline(
            inputText: doc.content,
            steps: textFactorySteps,
            documentLanguage: doc.language
        )
        documents[idx].content = result.transformedText
        if let le = result.targetLineEnding {
            documents[idx].lineEnding = le
        }
        for extra in result.extractedDocuments {
            let newDoc = EditorDocument(title: extra.title, content: extra.content, savedContent: "", language: .plainText)
            documents.insert(newDoc, at: max(0, documents.count - 1))
            activeDocumentId = newDoc.id
        }
        refreshActiveAnalysisImmediate()
        postToast(result.summary)
    }

    public func applyEncodingUtility(_ transform: (String) -> String, label: String) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        let s = doc.safeSelectionStart
        let e = doc.safeSelectionEnd
        if e > s {
            let ns = doc.content as NSString
            let replaced = transform(doc.selectedText)
            documents[idx].content = ns.replacingCharacters(in: NSRange(location: s, length: e - s), with: replaced)
        } else {
            documents[idx].content = transform(doc.content)
        }
        refreshActiveAnalysis()
        postToast(label)
    }

    // MARK: - Find & Replace Actions

    public func evaluateDocumentSearch() {
        guard let doc = focusedDocument else {
            documentMatches = []
            return
        }
        let selRange = (doc.safeSelectionEnd > doc.safeSelectionStart)
            ? NSRange(location: doc.safeSelectionStart, length: doc.safeSelectionEnd - doc.safeSelectionStart)
            : nil
        switch FindReplaceEngine.findAllMatches(in: doc.content, options: searchOptions, selectionRange: selRange) {
        case .success(let matches):
            documentMatches = matches
            searchErrorMessage = nil
            activeMatchIndex = min(max(0, activeMatchIndex), max(0, matches.count - 1))
        case .failure(let err):
            documentMatches = []
            searchErrorMessage = err.localizedDescription
        }
    }

    public func findNextMatch(forward: Bool = true) {
        evaluateDocumentSearch()
        guard !documentMatches.isEmpty, let doc = focusedDocument else { return }
        if forward {
            activeMatchIndex = (activeMatchIndex + 1) % documentMatches.count
        } else {
            activeMatchIndex = (activeMatchIndex - 1 + documentMatches.count) % documentMatches.count
        }
        let match = documentMatches[activeMatchIndex]
        scrollJumpRequest = ScrollJumpRequest(documentId: doc.id, utf16Range: match.nsRange)
    }

    public func replaceCurrentMatch() {
        evaluateDocumentSearch()
        guard !documentMatches.isEmpty,
              let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let match = documentMatches[min(activeMatchIndex, documentMatches.count - 1)]
        if case .success(let res) = FindReplaceEngine.replaceSingleMatch(
            in: documents[idx].content,
            match: match,
            options: searchOptions
        ) {
            documents[idx].content = res.updatedText
            documents[idx].selectionStart = res.updatedCursorOffset
            documents[idx].selectionEnd = res.updatedCursorOffset
            evaluateDocumentSearch()
            refreshActiveAnalysis()
        }
    }

    public func replaceAllInDocument() {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let doc = documents[idx]
        let selRange = (doc.safeSelectionEnd > doc.safeSelectionStart)
            ? NSRange(location: doc.safeSelectionStart, length: doc.safeSelectionEnd - doc.safeSelectionStart)
            : nil
        if case .success(let res) = FindReplaceEngine.replaceAllMatches(
            in: doc.content,
            options: searchOptions,
            selectionRange: selRange
        ) {
            documents[idx].content = res.updatedText
            evaluateDocumentSearch()
            refreshActiveAnalysis()
            postToast("Replaced \(res.replacedCount) occurrence\(res.replacedCount == 1 ? "" : "s")")
        }
    }

    public func extractMatchesToNewDocument() {
        guard let doc = focusedDocument else { return }
        if case .success(let res) = FindReplaceEngine.extractMatchesToText(in: doc.content, options: searchOptions),
           res.matchCount > 0 {
            let extracted = EditorDocument(
                title: "Extracted-\(res.matchCount)-Matches.txt",
                content: res.extractedContent,
                savedContent: "",
                language: .plainText
            )
            documents.insert(extracted, at: max(0, documents.count - 1))
            selectTab(extracted.id)
            showFindReplaceSheet = false
            postToast("Extracted \(res.matchCount) matches to \(extracted.title)")
        } else {
            postToast("No matches found to extract")
        }
    }

    public func runMultiFileSearch() {
        guard let root = mountedFolderURL else {
            searchErrorMessage = "Mount a project folder first to search across files."
            return
        }
        switch FindReplaceEngine.searchInDirectory(rootURL: root, options: searchOptions) {
        case .success(let results):
            multiFileMatches = results
            searchErrorMessage = nil
        case .failure(let err):
            multiFileMatches = []
            searchErrorMessage = err.localizedDescription
        }
    }

    public func runMultiFileReplaceAll() {
        guard let root = mountedFolderURL else { return }
        if case .success(let res) = FindReplaceEngine.replaceInDirectory(rootURL: root, options: searchOptions) {
            runMultiFileSearch()
            postToast("Modified \(res.filesModified) files (\(res.totalReplacements) replacements)")
        }
    }

    // MARK: - Side-by-Side Diff & Hunk Merge

    public func openDiffTool() {
        if diffLeftDocumentId.isEmpty || !documents.contains(where: { $0.id == diffLeftDocumentId }) {
            diffLeftDocumentId = activeDocumentId
        }
        if diffRightDocumentId.isEmpty || !documents.contains(where: { $0.id == diffRightDocumentId }) || diffRightDocumentId == diffLeftDocumentId {
            diffRightDocumentId = documents.first(where: { $0.id != diffLeftDocumentId })?.id ?? activeDocumentId
        }
        recomputeDiff()
        showDiffSheet = true
    }

    public func recomputeDiff() {
        let leftDoc = documents.first(where: { $0.id == diffLeftDocumentId }) ?? documents.first
        let rightDoc = documents.first(where: { $0.id == diffRightDocumentId }) ?? documents.last
        guard let l = leftDoc, let r = rightDoc else { return }
        currentDiffResult = DiffEngine.computeDiff(
            leftText: l.content,
            rightText: r.content,
            leftDocumentId: l.id,
            rightDocumentId: r.id,
            leftTitle: l.title,
            rightTitle: r.title
        )
    }

    public func mergeDiffRow(rowIndex: Int, rightToLeft: Bool) {
        guard let diff = currentDiffResult else { return }
        if rightToLeft {
            guard let leftId = diff.leftDocumentId,
                  let idx = documents.firstIndex(where: { $0.id == leftId }) else { return }
            documents[idx].content = DiffEngine.mergeRowRightToLeft(diff: diff, rowIndex: rowIndex)
        } else {
            guard let rightId = diff.rightDocumentId,
                  let idx = documents.firstIndex(where: { $0.id == rightId }) else { return }
            documents[idx].content = DiffEngine.mergeRowLeftToRight(diff: diff, rowIndex: rowIndex)
        }
        recomputeDiff()
        refreshActiveAnalysisImmediate()
    }

    // MARK: - Clippings Insertion & Management

    public func insertClipping(_ clipping: Clipping) {
        guard let idx = documents.firstIndex(where: { $0.id == focusedDocumentId }) else { return }
        let clipboard = NSPasteboard.general.string(forType: .string) ?? ""
        let updated = ClippingsManager.applyClippingToDocument(
            documents[idx],
            clipping: clipping,
            clipboardText: clipboard
        )
        documents[idx] = updated
        let selLen = max(0, updated.selectionEnd - updated.selectionStart)
        scrollJumpRequest = ScrollJumpRequest(
            documentId: updated.id,
            utf16Range: NSRange(location: updated.selectionStart, length: selLen)
        )
        refreshActiveAnalysisImmediate()
        postToast("Inserted clipping: \(clipping.name)")
    }

    public func addCustomClipping(name: String, prefix: String, description: String, body: String, language: SyntaxLanguage) {
        let clip = Clipping(
            name: name,
            triggerPrefix: prefix,
            category: .userCustom,
            description: description,
            templateBody: body,
            syntaxLanguage: language,
            isBuiltIn: false
        )
        customClippings.append(clip)
        saveCustomClippings()
        postToast("Saved custom clipping '\(name)'")
    }

    public func deleteCustomClipping(id: String) {
        customClippings.removeAll { $0.id == id }
        saveCustomClippings()
    }

    // MARK: - Command Palette Catalog

    public func buildCommandPaletteItems(updateManager: SparkleUpdateManager) -> [CommandPaletteItem] {
        [
            CommandPaletteItem(id: "new_doc", title: "New Document…", subtitle: "Create PowerShell, Swift, JSON, Markdown, or Shell file", shortcutHint: "⌘N", sfSymbol: "doc.badge.plus") { [weak self] in
                self?.showNewDocumentSheet = true
            },
            CommandPaletteItem(id: "open_file", title: "Open File…", subtitle: "Open local files via macOS Finder panel", shortcutHint: "⌘O", sfSymbol: "folder") { [weak self] in
                self?.openDocumentWithPanel()
            },
            CommandPaletteItem(id: "mount_dir", title: "Mount Project Folder…", subtitle: "Attach directory tree to Disk Browser & Multi-File Search", shortcutHint: "⇧⌘O", sfSymbol: "folder.badge.gearshape") { [weak self] in
                self?.mountProjectFolderWithPanel()
            },
            CommandPaletteItem(id: "save", title: "Save Document", subtitle: "Write active document preserving encoding & line endings", shortcutHint: "⌘S", sfSymbol: "square.and.arrow.down") { [weak self] in
                _ = self?.saveFocusedDocument()
            },
            CommandPaletteItem(id: "find", title: "Find & Replace (PCRE Regex)", subtitle: "Search with capture groups, playground & match extraction", shortcutHint: "⌘F", sfSymbol: "magnifyingglass") { [weak self] in
                self?.findSheetInitialTab = .document
                self?.showFindReplaceSheet = true
            },
            CommandPaletteItem(id: "find_dir", title: "Multi-File Search in Folder", subtitle: "Recursively search & batch-replace across mounted project", shortcutHint: "⇧⌘F", sfSymbol: "doc.text.magnifyingglass") { [weak self] in
                self?.findSheetInitialTab = .directory
                self?.showFindReplaceSheet = true
            },
            CommandPaletteItem(id: "text_factory", title: "Open BBEdit Text Factory…", subtitle: "Chain 14 deterministic text & code transformations", shortcutHint: "⇧⌘T", sfSymbol: "wand.and.stars") { [weak self] in
                self?.showTextFactorySheet = true
            },
            CommandPaletteItem(id: "format_code", title: "Format / Prettify Document", subtitle: "Auto-indent PowerShell, JSON, or XML", shortcutHint: "⌃⌥F", sfSymbol: "text.alignleft") { [weak self] in
                self?.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .formatPrettify))
            },
            CommandPaletteItem(id: "fix_all_ps", title: "PSScriptAnalyzer: Apply All Quick Fixes", subtitle: "Expand cmdlet aliases & fix warnings across script", shortcutHint: "⌥⇧F", sfSymbol: "checkmark.shield.fill") { [weak self] in
                self?.applyAllQuickFixes()
            },
            CommandPaletteItem(id: "diff", title: "Compare Two Documents (Side-by-Side Diff)", subtitle: "Myers line & character diff with hunk merging", shortcutHint: "⌃⌘D", sfSymbol: "arrow.left.arrow.right.square") { [weak self] in
                self?.openDiffTool()
            },
            CommandPaletteItem(id: "clippings", title: "Insert PowerShell Clipping / Snippet…", subtitle: "Expand CmdletBinding, Pester, REST, or custom templates", shortcutHint: "⌃⌘C", sfSymbol: "text.badge.plus") { [weak self] in
                self?.showClippingsSheet = true
            },
            CommandPaletteItem(id: "split_v", title: "Toggle Side-by-Side Vertical Split", subtitle: "View two documents side by side", shortcutHint: "⌥⌘2", sfSymbol: "rectangle.split.2x1") { [weak self] in
                guard let self else { return }
                self.splitMode = (self.splitMode == .vertical) ? .none : .vertical
                self.refreshActiveAnalysisImmediate()
            },
            CommandPaletteItem(id: "split_h", title: "Toggle Top-and-Bottom Horizontal Split", subtitle: "View two documents stacked vertically", shortcutHint: "⌥⌘3", sfSymbol: "rectangle.split.1x2") { [weak self] in
                guard let self else { return }
                self.splitMode = (self.splitMode == .horizontal) ? .none : .horizontal
                self.refreshActiveAnalysisImmediate()
            },
            CommandPaletteItem(id: "scratchpad", title: "Switch to Persistent Scratchpad", subtitle: "Open auto-saved Scratchpad.ps1", shortcutHint: "⇧⌘K", sfSymbol: "note.text") { [weak self] in
                self?.switchToScratchpad()
            },
            CommandPaletteItem(id: "updates", title: "Check for Updates…", subtitle: "Sparkle 2 & GitHub Releases SHA-256 Software Update", shortcutHint: "", sfSymbol: "arrow.triangle.2.circlepath.circle") {
                updateManager.openSoftwareUpdateCenter()
            }
        ]
    }

    // MARK: - Status Toast

    public func postToast(_ message: String) {
        statusBannerMessage = message
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            if self?.statusBannerMessage == message {
                self?.statusBannerMessage = nil
            }
        }
    }
}
