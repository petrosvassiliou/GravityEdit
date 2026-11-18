import AppKit
import GravityEditCore
import SwiftUI

// MARK: - 1. New Document Sheet

public struct NewDocumentSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var baseName: String = ""
    @State private var selectedLanguage: SyntaxLanguage = .powershell
    @State private var selectedEncoding: DocumentEncoding = .utf8
    @State private var selectedLineEnding: LineEnding = .lf
    @State private var includeStarterTemplate: Bool = true

    private var previewFileName: String {
        let untitledIndex = vm.documents.filter { !$0.isScratchpad }.count + 1
        let trimmed = baseName.trimmingCharacters(in: .whitespacesAndNewlines)
        let rawStem = trimmed.isEmpty ? "Untitled-\(untitledIndex)" : (trimmed as NSString).deletingPathExtension
        let stem = rawStem.isEmpty ? "Untitled-\(untitledIndex)" : rawStem
        return "\(stem).\(selectedLanguage.primaryExtension)"
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label("New Document", systemImage: "doc.badge.plus")
                    .font(.title3.weight(.semibold))
                Spacer()
                Text(previewFileName)
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .foregroundStyle(Color.accentColor)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 3)
                    .liquidGlassCapsule()
            }

            Form {
                TextField("Document Name (optional stem):", text: $baseName)
                Picker("Syntax & File Type:", selection: $selectedLanguage) {
                    ForEach(SyntaxLanguage.allCases) { lang in
                        Label("\(lang.displayName) (.\(lang.primaryExtension))", systemImage: lang.sfSymbolName)
                            .tag(lang)
                    }
                }
                .onChange(of: selectedLanguage) { _, newLang in
                    let trimmed = baseName.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty && !(trimmed as NSString).pathExtension.isEmpty {
                        baseName = EditorDocument.titleSynchronizedWithLanguage(
                            currentTitle: trimmed,
                            language: newLang
                        )
                    }
                    if newLang == .plainText {
                        includeStarterTemplate = false
                    }
                }
                Picker("Character Encoding:", selection: $selectedEncoding) {
                    ForEach(DocumentEncoding.allCases) { enc in
                        Text(enc.displayName).tag(enc)
                    }
                }
                Picker("Line Endings:", selection: $selectedLineEnding) {
                    ForEach(LineEnding.allCases) { le in
                        Text(le.displayName).tag(le)
                    }
                }
                Toggle("Populate starter template", isOn: $includeStarterTemplate)
                    .disabled(selectedLanguage == .plainText)
            }
            .formStyle(.grouped)

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
                Button("Create \(previewFileName)") {
                    vm.createNewDocument(
                        baseName: baseName,
                        language: selectedLanguage,
                        encoding: selectedEncoding,
                        lineEnding: selectedLineEnding,
                        includeStarterTemplate: includeStarterTemplate
                    )
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
                .liquidGlassButton(prominent: true)
            }
        }
        .padding(20)
        .frame(width: 500)
    }
}

// MARK: - 2. PCRE Find & Replace + Multi-File Search + Regex Playground Sheet

public struct FindReplaceSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var activeTab: FindSheetTab = .document
    @State private var playgroundSample: String = """
    2026-11-18 09:14:22 [ERROR] Node=SRV-AD01 Code=403 Message="Token expired"
    2026-11-18 09:14:25 [INFO]  Node=SRV-FS02 Code=200 Message="Replication healthy"
    2026-11-18 09:15:01 [ERROR] Node=SRV-SQL1 Code=504 Message="Gateway timeout"
    """

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label("Industrial PCRE Find & Replace", systemImage: "magnifyingglass.circle.fill")
                    .font(.title3.weight(.semibold))
                Spacer()
                Picker("", selection: $activeTab) {
                    ForEach(FindSheetTab.allCases) { tab in
                        Text(tab.rawValue).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .frame(width: 430)
            }

            // Shared Query & Replacement Controls
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    Text("Find:")
                        .frame(width: 68, alignment: .trailing)
                        .font(.caption.weight(.semibold))
                    TextField("Regular expression or literal string…", text: $vm.searchOptions.query)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(.body, design: .monospaced))
                        .onChange(of: vm.searchOptions.query) { _, _ in vm.evaluateDocumentSearch() }
                }
                HStack(spacing: 8) {
                    Text("Replace:")
                        .frame(width: 68, alignment: .trailing)
                        .font(.caption.weight(.semibold))
                    TextField("Replacement ($1, $2, \\n, \\t)…", text: $vm.searchOptions.replacement)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(.body, design: .monospaced))
                }

                HStack(spacing: 14) {
                    Toggle("Grep / Regex", isOn: $vm.searchOptions.grepRegex)
                    Toggle("Case Sensitive", isOn: $vm.searchOptions.caseSensitive)
                    Toggle("Entire Words", isOn: $vm.searchOptions.matchEntireWords)
                    Toggle("Dot Matches All", isOn: $vm.searchOptions.dotMatchesAll)
                    Toggle("Selection Only", isOn: $vm.searchOptions.searchInSelectionOnly)
                    Spacer()
                }
                .toggleStyle(.checkbox)
                .font(.caption)
                .onChange(of: vm.searchOptions) { _, _ in vm.evaluateDocumentSearch() }
            }
            .padding(12)
            .liquidGlassCard(cornerRadius: 12)

            if let err = vm.searchErrorMessage {
                Label(err, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            Group {
                switch activeTab {
                case .document:
                    documentSearchTab
                case .directory:
                    multiFileSearchTab
                case .playground:
                    regexPlaygroundTab
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            HStack {
                Text("\(vm.documentMatches.count) matches in active document")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Button("Close") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
            }
        }
        .padding(20)
        .frame(width: 760, height: 540)
        .onAppear {
            activeTab = vm.findSheetInitialTab
            vm.evaluateDocumentSearch()
        }
    }

    private var documentSearchTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Button("Find Previous") { vm.findNextMatch(forward: false) }
                    .liquidGlassButton()
                Button("Find Next") { vm.findNextMatch(forward: true) }
                    .liquidGlassButton(prominent: true)
                Button("Replace") { vm.replaceCurrentMatch() }
                    .liquidGlassButton()
                Button("Replace All") { vm.replaceAllInDocument() }
                    .liquidGlassButton()
                Spacer()
                Button("Extract Matches to New Document") {
                    vm.extractMatchesToNewDocument()
                }
                .liquidGlassButton()
            }

            List(vm.documentMatches) { match in
                Button {
                    if let doc = vm.focusedDocument {
                        vm.scrollJumpRequest = ScrollJumpRequest(documentId: doc.id, utf16Range: match.nsRange)
                    }
                } label: {
                    HStack(spacing: 10) {
                        Text("Ln \(match.lineNumber):\(match.columnNumber)")
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                            .foregroundStyle(Color.accentColor)
                            .frame(width: 78, alignment: .leading)
                        Text(match.linePreview)
                            .font(.system(size: 12, design: .monospaced))
                            .lineLimit(1)
                        Spacer()
                        if !match.captureGroups.isEmpty {
                            Text("\(match.captureGroups.count) grp")
                                .font(.caption2)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.secondary.opacity(0.15), in: Capsule())
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    private var multiFileSearchTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text("Folder:")
                    .font(.caption.weight(.semibold))
                Text(vm.mountedFolderURL?.path ?? "No project folder mounted")
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
                Button("Mount Folder…") { vm.mountProjectFolderWithPanel() }
                    .liquidGlassButton()
                TextField("Filter (*.ps1;*.swift)", text: $vm.searchOptions.fileFilter)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 170)
                Button("Search Files") { vm.runMultiFileSearch() }
                    .liquidGlassButton(prominent: true)
                Button("Replace in Files") { vm.runMultiFileReplaceAll() }
                    .liquidGlassButton()
                    .disabled(vm.multiFileMatches.isEmpty)
            }

            List(vm.multiFileMatches) { fileResult in
                Section("\(fileResult.relativePath) (\(fileResult.totalMatches))") {
                    ForEach(fileResult.matches) { m in
                        Button {
                            vm.openFile(at: fileResult.fileURL)
                            vm.jumpToLine(m.lineNumber)
                            dismiss()
                        } label: {
                            HStack(spacing: 8) {
                                Text("Ln \(m.lineNumber)")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(Color.accentColor)
                                    .frame(width: 56, alignment: .leading)
                                Text(m.linePreview)
                                    .font(.system(size: 11.5, design: .monospaced))
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    private var regexPlaygroundTab: some View {
        let eval = FindReplaceEngine.evaluatePlayground(sampleText: playgroundSample, options: vm.searchOptions)
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Sample Input Text")
                    .font(.caption.weight(.semibold))
                TextEditor(text: $playgroundSample)
                    .font(.system(size: 12, design: .monospaced))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Live Replacement Output (\(eval.matches.count) matches • \(eval.executionTimeMicros) µs)")
                        .font(.caption.weight(.semibold))
                    Spacer()
                }
                ScrollView {
                    Text(eval.replacementPreview)
                        .font(.system(size: 12, design: .monospaced))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(8)
                }
                .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

// MARK: - 3. BBEdit 14-Operation Text Factory Pipeline Sheet

public struct TextFactorySheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var selectedStepId: String? = nil

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label("BBEdit Text Factory Pipeline (14 Operations)", systemImage: "wand.and.stars")
                    .font(.title3.weight(.semibold))
                Spacer()
                Menu {
                    ForEach(TextOperationType.allCases) { op in
                        Button {
                            let step = TextFactoryStep(operationType: op)
                            vm.textFactorySteps.append(step)
                            selectedStepId = step.id
                        } label: {
                            Label(op.title, systemImage: op.sfSymbol)
                        }
                    }
                } label: {
                    Label("Add Operation", systemImage: "plus")
                }
                .liquidGlassButton()
            }

            HSplitView {
                // Left: Ordered Pipeline Steps
                List(selection: $selectedStepId) {
                    ForEach($vm.textFactorySteps) { $step in
                        HStack(spacing: 8) {
                            Toggle("", isOn: $step.enabled)
                                .labelsHidden()
                            Image(systemName: step.operationType.sfSymbol)
                                .foregroundStyle(Color.accentColor)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(step.operationType.title)
                                    .font(.system(size: 12.5, weight: .semibold))
                                Text(step.configurationSummary)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            Button {
                                vm.textFactorySteps.removeAll { $0.id == step.id }
                            } label: {
                                Image(systemName: "trash")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .buttonStyle(.plain)
                        }
                        .tag(step.id)
                    }
                    .onMove { indices, newOffset in
                        vm.textFactorySteps.move(fromOffsets: indices, toOffset: newOffset)
                    }
                }
                .frame(minWidth: 280, idealWidth: 320)

                // Right: Step Configuration Inspector
                VStack(alignment: .leading, spacing: 12) {
                    if let selId = selectedStepId,
                       let idx = vm.textFactorySteps.firstIndex(where: { $0.id == selId }) {
                        stepConfigurationForm(step: $vm.textFactorySteps[idx])
                    } else if !vm.textFactorySteps.isEmpty {
                        stepConfigurationForm(step: $vm.textFactorySteps[0])
                    } else {
                        Text("Add one or more BBEdit Text Factory operations to build a reusable pipeline.")
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .frame(minWidth: 340, maxWidth: .infinity, maxHeight: .infinity)
                .padding(12)
            }
            .frame(height: 390)

            HStack {
                Text("Applies sequentially to “\(vm.focusedDocument?.title ?? "Active Document")”")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Button("Cancel") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
                Button("Run Pipeline") {
                    vm.runFullTextFactoryPipeline()
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
                .liquidGlassButton(prominent: true)
            }
        }
        .padding(20)
        .frame(width: 780, height: 520)
        .onAppear {
            selectedStepId = vm.textFactorySteps.first?.id
        }
    }

    @ViewBuilder
    private func stepConfigurationForm(step: Binding<TextFactoryStep>) -> some View {
        Form {
            Section(step.wrappedValue.operationType.title) {
                Text(step.wrappedValue.operationType.defaultSummary)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                switch step.wrappedValue.operationType {
                case .zapGremlins:
                    Picker("Action:", selection: step.zapAction) {
                        ForEach(ZapGremlinsAction.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Toggle("Control Characters", isOn: step.zapControlChars)
                    Toggle("Null Bytes (\\0)", isOn: step.zapNullBytes)
                    Toggle("Non-ASCII (> 0x7F)", isOn: step.zapNonAscii)

                case .processDuplicateLines:
                    Picker("Mode:", selection: step.duplicateMode) {
                        ForEach(DuplicateLineMode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Toggle("Case Sensitive", isOn: step.duplicateCaseSensitive)
                    Toggle("Ignore Leading Whitespace", isOn: step.duplicateIgnoreLeadingWhitespace)

                case .sortLines:
                    Picker("Direction:", selection: step.sortDirection) {
                        ForEach(SortDirection.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Toggle("Case Sensitive", isOn: step.sortCaseSensitive)
                    Toggle("Numeric Value Sort", isOn: step.sortNumeric)
                    TextField("Optional Regex Key Pattern:", text: step.sortRegexPattern)

                case .prefixSuffixLines:
                    Picker("Action:", selection: step.prefixSuffixAction) {
                        ForEach(PrefixSuffixAction.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    TextField("Line Prefix:", text: step.linePrefix)
                    TextField("Line Suffix:", text: step.lineSuffix)
                    Toggle("Skip Blank Lines", isOn: step.skipBlankLines)

                case .changeCase:
                    Picker("Case Mode:", selection: step.caseMode) {
                        ForEach(CaseTransformMode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }

                case .stripTrailingWhitespace:
                    Text("Removes trailing spaces and tabs from every line while preserving indentation.")
                        .font(.caption)

                case .entabDetab:
                    Picker("Conversion:", selection: step.tabConversionMode) {
                        ForEach(TabConversionMode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Stepper("Spaces per Tab: \(step.wrappedValue.tabWidth)", value: step.tabWidth, in: 1...16)

                case .hardWrapUnwrap:
                    Picker("Mode:", selection: step.wrapMode) {
                        ForEach(WrapTransformMode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Stepper("Wrap Column: \(step.wrappedValue.wrapColumnWidth)", value: step.wrapColumnWidth, in: 30...200, step: 4)
                    Toggle("Preserve Paragraphs", isOn: step.wrapPreserveParagraphs)

                case .addRemoveLineNumbers:
                    Picker("Action:", selection: step.lineNumberAction) {
                        ForEach(LineNumberAction.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Stepper("Start Number: \(step.wrappedValue.lineNumberStart)", value: step.lineNumberStart, in: 0...10000)
                    Stepper("Zero Pad Width: \(step.wrappedValue.lineNumberZeroPadWidth)", value: step.lineNumberZeroPadWidth, in: 0...8)
                    TextField("Separator:", text: step.lineNumberSeparator)

                case .educateStraightenQuotes:
                    Picker("Quote Transform:", selection: step.quoteMode) {
                        ForEach(QuoteTransformMode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }

                case .canonizeReplaceTable:
                    Toggle("Case Sensitive", isOn: step.canonizeCaseSensitive)
                    Toggle("Match Whole Words", isOn: step.canonizeWholeWords)
                    TextEditor(text: step.canonizeTableRaw)
                        .font(.system(size: 11.5, design: .monospaced))
                        .frame(height: 110)

                case .processLinesContaining:
                    Picker("Action:", selection: step.matchingLinesAction) {
                        ForEach(MatchingLinesAction.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    TextField("Search / Regex Pattern:", text: step.matchingPattern)
                    Toggle("Use Regular Expression", isOn: step.matchingUseRegex)
                    Toggle("Case Sensitive", isOn: step.matchingCaseSensitive)

                case .convertLineEndings:
                    Picker("Target Line Ending:", selection: step.targetLineEnding) {
                        ForEach(LineEnding.allCases) { Text($0.displayName).tag($0) }
                    }

                case .formatPrettify:
                    Picker("Format Target:", selection: step.formatTarget) {
                        ForEach(FormatTarget.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Stepper("Indent Spaces: \(step.wrappedValue.formatIndentSpaces)", value: step.formatIndentSpaces, in: 2...8, step: 2)
                }
            }
        }
        .formStyle(.grouped)
    }
}

// MARK: - 4. Side-by-Side Myers Diff Sheet with Directional Hunk Merging

public struct DiffToolSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Label("Compare Documents (Myers Diff)", systemImage: "arrow.left.arrow.right.square.fill")
                    .font(.title3.weight(.semibold))
                Spacer()
                if let diff = vm.currentDiffResult {
                    HStack(spacing: 8) {
                        Text("+\(diff.addedCount)")
                            .foregroundStyle(.green)
                        Text("−\(diff.removedCount)")
                            .foregroundStyle(.red)
                        Text("~\(diff.modifiedCount)")
                            .foregroundStyle(.orange)
                    }
                    .font(.caption.monospacedDigit().weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .liquidGlassCapsule()
                }
            }

            HStack(spacing: 12) {
                Picker("Left:", selection: $vm.diffLeftDocumentId) {
                    ForEach(vm.documents) { doc in Text(doc.title).tag(doc.id) }
                }
                .onChange(of: vm.diffLeftDocumentId) { _, _ in vm.recomputeDiff() }

                Picker("Right:", selection: $vm.diffRightDocumentId) {
                    ForEach(vm.documents) { doc in Text(doc.title).tag(doc.id) }
                }
                .onChange(of: vm.diffRightDocumentId) { _, _ in vm.recomputeDiff() }
            }

            if let diff = vm.currentDiffResult {
                if diff.isIdentical {
                    VStack(spacing: 10) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 36))
                            .foregroundStyle(.green)
                        Text("Documents are byte-for-byte identical (normalized line endings).")
                            .font(.callout)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(diff.rows) { row in
                        HStack(spacing: 6) {
                            Text(row.leftLineNumber.map(String.init) ?? "")
                                .font(.caption2.monospacedDigit())
                                .foregroundStyle(.secondary)
                                .frame(width: 34, alignment: .trailing)

                            Text(row.leftText ?? "")
                                .font(.system(size: 11.5, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(3)
                                .background(diffRowTint(row.changeType, isLeft: true))

                            if row.changeType != .equal {
                                HStack(spacing: 3) {
                                    Button("←") {
                                        vm.mergeDiffRow(rowIndex: row.rowIndex, rightToLeft: true)
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.mini)
                                    .help("Copy Right line to Left document")

                                    Button("→") {
                                        vm.mergeDiffRow(rowIndex: row.rowIndex, rightToLeft: false)
                                    }
                                    .buttonStyle(.bordered)
                                    .controlSize(.mini)
                                    .help("Copy Left line to Right document")
                                }
                            } else {
                                Spacer().frame(width: 48)
                            }

                            Text(row.rightLineNumber.map(String.init) ?? "")
                                .font(.caption2.monospacedDigit())
                                .foregroundStyle(.secondary)
                                .frame(width: 34, alignment: .trailing)

                            Text(row.rightText ?? "")
                                .font(.system(size: 11.5, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(3)
                                .background(diffRowTint(row.changeType, isLeft: false))
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }

            HStack {
                Spacer()
                Button("Done") { dismiss() }
                    .keyboardShortcut(.defaultAction)
                    .liquidGlassButton(prominent: true)
            }
        }
        .padding(20)
        .frame(width: 920, height: 560)
    }

    private func diffRowTint(_ type: DiffChangeType, isLeft: Bool) -> Color {
        switch type {
        case .equal: return .clear
        case .delete: return isLeft ? Color.red.opacity(0.16) : Color.secondary.opacity(0.05)
        case .insert: return isLeft ? Color.secondary.opacity(0.05) : Color.green.opacity(0.16)
        case .modify: return Color.orange.opacity(0.15)
        }
    }
}

// MARK: - 5. Clippings & Snippets Sheet

public struct ClippingsSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var filterQuery: String = ""
    @State private var selectedClipping: Clipping? = nil
    @State private var isCreatingCustom: Bool = false
    @State private var newName: String = ""
    @State private var newPrefix: String = ""
    @State private var newDesc: String = ""
    @State private var newBody: String = "#SELECTION##INSERTION#"

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("PowerShell & BBEdit Clippings Library", systemImage: "text.badge.plus")
                    .font(.title3.weight(.semibold))
                Spacer()
                Button {
                    isCreatingCustom.toggle()
                } label: {
                    Label(isCreatingCustom ? "Browse Library" : "New Custom Clipping", systemImage: isCreatingCustom ? "list.bullet" : "plus")
                }
                .liquidGlassButton()
            }

            if isCreatingCustom {
                Form {
                    TextField("Clipping Name:", text: $newName)
                    TextField("Trigger Prefix (e.g. my-func):", text: $newPrefix)
                    TextField("Description:", text: $newDesc)
                    Section("Template Body (#SELECTION#, #INSERTION#, #SELSTART#…#SELEND#, #DATE#, #TIME#, #FILENAME#, #USERNAME#)") {
                        TextEditor(text: $newBody)
                            .font(.system(size: 12, design: .monospaced))
                            .frame(height: 180)
                    }
                    HStack {
                        Spacer()
                        Button("Save Custom Clipping") {
                            guard !newName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                            vm.addCustomClipping(
                                name: newName,
                                prefix: newPrefix.isEmpty ? "custom" : newPrefix,
                                description: newDesc,
                                body: newBody,
                                language: vm.focusedDocument?.language ?? .powershell
                            )
                            isCreatingCustom = false
                            newName = ""; newPrefix = ""; newDesc = ""
                        }
                        .liquidGlassButton(prominent: true)
                    }
                }
                .formStyle(.grouped)
            } else {
                let items = ClippingsManager.filterClippings(vm.allClippings, searchQuery: filterQuery)
                TextField("Search clippings by name, prefix, or category…", text: $filterQuery)
                    .textFieldStyle(.roundedBorder)

                HSplitView {
                    List(items, selection: Binding(
                        get: { selectedClipping?.id },
                        set: { id in selectedClipping = items.first { $0.id == id } }
                    )) { clip in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(clip.name).font(.system(size: 12.5, weight: .semibold))
                                Text(clip.category.displayName).font(.caption2).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(clip.triggerPrefix)
                                .font(.caption2.monospaced())
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.accentColor.opacity(0.15), in: Capsule())
                        }
                        .tag(clip.id)
                    }
                    .frame(minWidth: 260)

                    VStack(alignment: .leading, spacing: 8) {
                        if let clip = selectedClipping ?? items.first {
                            HStack {
                                Text(clip.name).font(.headline)
                                Spacer()
                                if !clip.isBuiltIn {
                                    Button("Delete", role: .destructive) {
                                        vm.deleteCustomClipping(id: clip.id)
                                        selectedClipping = nil
                                    }
                                    .controlSize(.small)
                                }
                            }
                            Text(clip.description).font(.caption).foregroundStyle(.secondary)
                            ScrollView {
                                Text(clip.templateBody)
                                    .font(.system(size: 11.5, design: .monospaced))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(10)
                            }
                            .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))

                            HStack {
                                Spacer()
                                Button("Insert at Cursor") {
                                    vm.insertClipping(clip)
                                    dismiss()
                                }
                                .liquidGlassButton(prominent: true)
                            }
                        }
                    }
                    .padding(10)
                    .frame(minWidth: 340, maxWidth: .infinity)
                }
                .frame(height: 360)
            }

            HStack {
                Spacer()
                Button("Close") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
            }
        }
        .padding(20)
        .frame(width: 760, height: 500)
    }
}

// MARK: - 6. PSScriptAnalyzer Diagnostics Sheet

public struct DiagnosticsSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("PSScriptAnalyzer Live Diagnostics", systemImage: "checkmark.shield.fill")
                    .font(.title3.weight(.semibold))
                Spacer()
                if vm.focusedDiagnostics.contains(where: { $0.quickFixTarget != nil }) {
                    Button("Apply All Quick Fixes") {
                        vm.applyAllQuickFixes()
                    }
                    .liquidGlassButton(prominent: true)
                }
            }

            if vm.focusedDiagnostics.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 36))
                        .foregroundStyle(.green)
                    Text("Zero warnings, aliases, or delimiter errors detected.")
                        .font(.callout)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(vm.focusedDiagnostics) { diag in
                    HStack(alignment: .center, spacing: 12) {
                        Text(diag.severity.badge)
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.18), in: Capsule())

                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(diag.ruleId)  •  Ln \(diag.lineNumber), Col \(diag.column)")
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                            Text(diag.message)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button("Go to Line") {
                            vm.jumpToDiagnostic(diag)
                            dismiss()
                        }
                        .liquidGlassButton()
                        .controlSize(.small)

                        if let fixLabel = diag.quickFixLabel {
                            Button(fixLabel) {
                                vm.applyQuickFix(diag)
                            }
                            .liquidGlassButton(prominent: true)
                            .controlSize(.small)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }

            HStack {
                Spacer()
                Button("Close") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
            }
        }
        .padding(20)
        .frame(width: 720, height: 440)
    }
}

// MARK: - 7. Floating Liquid Glass Command Palette Overlay (⌘P / ⇧⌘P)

public struct CommandPaletteOverlayView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @ObservedObject var updateManager: SparkleUpdateManager

    @State private var query: String = ""
    @FocusState private var searchFocused: Bool

    public var body: some View {
        let allItems = vm.buildCommandPaletteItems(updateManager: updateManager)
        let filtered = query.trimmingCharacters(in: .whitespaces).isEmpty
            ? allItems
            : allItems.filter {
                $0.title.localizedCaseInsensitiveContains(query) ||
                $0.subtitle.localizedCaseInsensitiveContains(query)
            }

        VStack(spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: "command")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
                TextField("Type a command or action…", text: $query)
                    .textFieldStyle(.plain)
                    .font(.system(size: 15))
                    .focused($searchFocused)
                    .onSubmit {
                        if let first = filtered.first {
                            vm.showCommandPalette = false
                            first.action()
                        }
                    }
                Button("Esc") {
                    vm.showCommandPalette = false
                }
                .font(.caption2.monospaced())
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)

            Divider().opacity(0.3)

            ScrollView {
                VStack(spacing: 3) {
                    ForEach(filtered) { item in
                        Button {
                            vm.showCommandPalette = false
                            item.action()
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: item.sfSymbol)
                                    .frame(width: 22)
                                    .foregroundStyle(Color.accentColor)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(item.title)
                                        .font(.system(size: 13, weight: .medium))
                                    Text(item.subtitle)
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if !item.shortcutHint.isEmpty {
                                    Text(item.shortcutHint)
                                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                        .foregroundStyle(.secondary)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.primary.opacity(0.07), in: RoundedRectangle(cornerRadius: 5))
                                }
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 6)
                .padding(.bottom, 8)
            }
            .frame(maxHeight: 320)
        }
        .frame(width: 560)
        .liquidGlassCard(cornerRadius: 18)
        .shadow(color: .black.opacity(0.28), radius: 24, y: 10)
        .onAppear { searchFocused = true }
    }
}

// MARK: - 8. Liquid Glass Software Update Sheet (Sparkle 2 + SHA-256 Self-Updater)

public struct SoftwareUpdateSheetView: View {
    @ObservedObject var updateManager: SparkleUpdateManager
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color.blue, Color.cyan.opacity(0.85)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 54, height: 54)
                    Image(systemName: "terminal.fill")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundStyle(.white)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("GravityEdit Software Update")
                        .font(.title3.weight(.semibold))
                    Text("Installed Version: v\(MacAutoUpdateEngine.currentAppVersion) (Build \(MacAutoUpdateEngine.currentBuildNumber)) • Sparkle 2 + CryptoKit SHA-256")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            .padding(14)
            .liquidGlassCard(cornerRadius: 16)

            // Lifecycle Status Card
            Group {
                switch updateManager.state {
                case .idle:
                    Label("Ready to check GitHub Releases & Sparkle Appcast.", systemImage: "arrow.triangle.2.circlepath")
                        .font(.callout)

                case .checking:
                    HStack(spacing: 10) {
                        ProgressView().controlSize(.small)
                        Text("Checking petrosvassiliou/GravityEdit for macOS updates…")
                            .font(.callout)
                    }

                case .upToDate(let ver, _):
                    VStack(alignment: .leading, spacing: 6) {
                        Label("GravityEdit v\(ver) is currently the newest version available.", systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                            .font(.callout.weight(.semibold))
                        if let rel = updateManager.latestReleaseInfo, !rel.releaseNotes.isEmpty {
                            releaseNotesBox(rel.releaseNotes)
                        }
                    }

                case .updateAvailable(let release):
                    VStack(alignment: .leading, spacing: 8) {
                        Label("New Version Available: GravityEdit v\(release.versionString)", systemImage: "sparkles")
                            .font(.headline)
                            .foregroundStyle(Color.accentColor)
                        releaseNotesBox(release.releaseNotes)
                    }

                case .downloading(let progress, let statusText):
                    VStack(alignment: .leading, spacing: 8) {
                        Text(statusText)
                            .font(.callout.weight(.medium))
                        ProgressView(value: progress, total: 1.0)
                            .progressViewStyle(.linear)
                        if let sha = updateManager.verifiedSHA256Digest {
                            Text("SHA-256: \(sha)")
                                .font(.system(size: 10.5, design: .monospaced))
                                .foregroundStyle(.secondary)
                        }
                    }

                case .readyToRelaunch(let stagedURL, let version):
                    VStack(alignment: .leading, spacing: 8) {
                        Label("GravityEdit v\(version) verified and ready to install!", systemImage: "checkmark.seal.fill")
                            .foregroundStyle(.green)
                            .font(.headline)
                        if let sha = updateManager.verifiedSHA256Digest {
                            Text("Verified SHA-256: \(sha)")
                                .font(.system(size: 10.5, design: .monospaced))
                                .foregroundStyle(.secondary)
                        }
                        Button("Install Now & Relaunch GravityEdit") {
                            updateManager.performAtomicSwapAndRelaunch(stagedBundleURL: stagedURL)
                        }
                        .liquidGlassButton(prominent: true)
                    }

                case .failed(let message):
                    VStack(alignment: .leading, spacing: 6) {
                        Label("Update check encountered an error", systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                            .font(.callout.weight(.semibold))
                        Text(message)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            Divider()

            VStack(alignment: .leading, spacing: 6) {
                Toggle("Automatically check for updates in the background", isOn: $updateManager.automaticallyChecksForUpdates)
                Toggle("Automatically download verified updates when available", isOn: $updateManager.automaticallyDownloadsUpdates)
            }
            .toggleStyle(.checkbox)
            .font(.caption)

            HStack {
                Button("View GitHub Releases") {
                    NSWorkspace.shared.open(MacAutoUpdateEngine.releasesPageURL)
                }
                .liquidGlassButton()

                Spacer()

                Button("Check Again") {
                    Task { await updateManager.checkForUpdates(userInitiated: true) }
                }
                .liquidGlassButton()

                if case .updateAvailable = updateManager.state {
                    Button("Install Update & Relaunch") {
                        Task { await updateManager.downloadAndInstallInApp() }
                    }
                    .keyboardShortcut(.defaultAction)
                    .liquidGlassButton(prominent: true)
                } else {
                    Button("Done") { dismiss() }
                        .keyboardShortcut(.cancelAction)
                        .liquidGlassButton(prominent: true)
                }
            }
        }
        .padding(20)
        .frame(width: 600, height: 450)
    }

    private func releaseNotesBox(_ notes: String) -> some View {
        ScrollView {
            Text(notes)
                .font(.system(size: 12))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
        }
        .frame(maxHeight: 165)
        .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 10))
    }
}
