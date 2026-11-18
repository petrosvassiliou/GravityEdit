import AppKit
import GravityEditCore
import SwiftUI

public struct GravityEditMainWindow: View {
    @ObservedObject var vm: WorkspaceViewModel
    @ObservedObject var updateManager: SparkleUpdateManager

    @Namespace private var tabGlassNamespace
    @Namespace private var sidebarGlassNamespace
    @State private var sidebarVisibility: NavigationSplitViewVisibility = .all
    @State private var outlineFilterQuery: String = ""
    @State private var goToLineInput: String = ""

    public init(vm: WorkspaceViewModel, updateManager: SparkleUpdateManager) {
        self.vm = vm
        self.updateManager = updateManager
    }

    private var preferredColorScheme: ColorScheme? {
        switch vm.themeMode {
        case .system: return nil
        case .light: return .light
        case .dark, .oledPitchBlack: return .dark
        }
    }

    public var body: some View {
        ZStack {
            NavigationSplitView(columnVisibility: $sidebarVisibility) {
                sidebarContent
                    .navigationSplitViewColumnWidth(min: 230, ideal: 268, max: 360)
            } detail: {
                VStack(spacing: 0) {
                    liquidGlassDocumentTabStrip
                    Divider().opacity(0.35)
                    editorSplitWorkspace
                    Divider().opacity(0.35)
                    liquidGlassBottomStatusBar
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .navigationTitle(vm.focusedDocument?.title ?? "GravityEdit")
                .navigationSubtitle(navigationSubtitleText)
                .toolbarBackgroundVisibility(.visible, for: .windowToolbar)
                .modifier(
                    GravityWindowToolbarModifier(
                        vm: vm,
                        functionNavigatorMenu: { toolbarFunctionNavigatorMenu },
                        splitModeButtons: { toolbarSplitModeButtons },
                        workbenchToolButtons: { toolbarWorkbenchButtons }
                    )
                )
            }

            // Floating Liquid Glass Command Palette Overlay (⌘P / ⇧⌘P)
            if vm.showCommandPalette {
                Color.black.opacity(0.22)
                    .ignoresSafeArea()
                    .onTapGesture { vm.showCommandPalette = false }

                CommandPaletteOverlayView(vm: vm, updateManager: updateManager)
                    .transition(.opacity.combined(with: .scale(scale: 0.97)))
                    .zIndex(20)
            }

            // Floating Liquid Glass Toast Notification
            if let toast = vm.statusBannerMessage {
                VStack {
                    Spacer()
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                        Text(toast)
                            .font(.system(size: 12, weight: .medium))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .liquidGlassCapsule()
                    .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
                    .padding(.bottom, 44)
                }
                .allowsHitTesting(false)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(25)
            }
        }
        .preferredColorScheme(preferredColorScheme)
        .sheet(isPresented: $vm.showNewDocumentSheet) {
            NewDocumentSheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showFindReplaceSheet) {
            FindReplaceSheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showTextFactorySheet) {
            TextFactorySheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showDiffSheet) {
            DiffToolSheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showClippingsSheet) {
            ClippingsSheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showDiagnosticsSheet) {
            DiagnosticsSheetView(vm: vm)
        }
        .sheet(isPresented: $updateManager.isUpdateSheetPresented) {
            SoftwareUpdateSheetView(updateManager: updateManager)
        }
    }

    private var navigationSubtitleText: String {
        guard let doc = vm.focusedDocument else { return "" }
        if let url = doc.fileURL {
            return url.deletingLastPathComponent().path.replacingOccurrences(of: NSHomeDirectory(), with: "~")
        }
        return "\(doc.language.displayName) • \(doc.encoding.displayName)"
    }

    // MARK: - Floating Liquid Glass Sidebar (Apple HIG Navigation Layer)

    private var sidebarContent: some View {
        VStack(spacing: 8) {
            // Top segmented Liquid Glass section switcher
            LiquidGlassCluster(spacing: 4) {
                HStack(spacing: 4) {
                    ForEach(SidebarSection.allCases) { section in
                        let isSelected = (vm.sidebarSection == section)
                        Button {
                            withAnimation(.snappy(duration: 0.22)) {
                                vm.sidebarSection = section
                            }
                        } label: {
                            Image(systemName: section.sfSymbol)
                                .font(.system(size: 13, weight: isSelected ? .semibold : .regular))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                        .background {
                            if isSelected {
                                Capsule(style: .continuous)
                                    .fill(Color.accentColor.opacity(0.16))
                                    .liquidGlassMorphID("sidebar_pill", in: sidebarGlassNamespace)
                            }
                        }
                        .help(sidebarSectionTitle(section))
                    }
                }
                .padding(4)
                .liquidGlassCapsule()
            }
            .padding(.horizontal, 10)
            .padding(.top, 8)

            Group {
                switch vm.sidebarSection {
                case .openDocuments:
                    sidebarOpenDocumentsList
                case .diskBrowser:
                    sidebarDiskBrowserTree
                case .functionOutline:
                    sidebarFunctionOutline
                case .clippings:
                    sidebarClippingsQuickList
                case .diagnostics:
                    sidebarDiagnosticsList
                }
            }
        }
    }

    private func sidebarSectionTitle(_ section: SidebarSection) -> String {
        switch section {
        case .openDocuments: return vm.tr(.openDocuments)
        case .diskBrowser: return vm.tr(.diskBrowser)
        case .functionOutline: return vm.tr(.functionNavigator)
        case .clippings: return vm.tr(.clippings)
        case .diagnostics: return vm.tr(.diagnostics)
        }
    }

    private var sidebarOpenDocumentsList: some View {
        List {
            Section(vm.tr(.openDocuments)) {
                ForEach(vm.documents) { doc in
                    let isActive = (doc.id == vm.focusedDocumentId)
                    Button {
                        vm.selectTab(doc.id)
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: doc.isScratchpad ? "note.text" : doc.language.sfSymbolName)
                                .foregroundStyle(isActive ? Color.accentColor : Color.secondary)
                                .frame(width: 16)
                            VStack(alignment: .leading, spacing: 1) {
                                HStack(spacing: 4) {
                                    Text(doc.title)
                                        .font(.system(size: 12.5, weight: isActive ? .semibold : .regular))
                                        .lineLimit(1)
                                    if doc.isDirty {
                                        Circle()
                                            .fill(Color.orange)
                                            .frame(width: 6, height: 6)
                                    }
                                }
                                Text("\(doc.language.displayName) • \(doc.lineCount) ln")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if !doc.isScratchpad {
                                Button {
                                    vm.closeDocument(id: doc.id)
                                } label: {
                                    Image(systemName: "xmark")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(.secondary)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(
                        isActive
                            ? RoundedRectangle(cornerRadius: 8, style: .continuous).fill(Color.accentColor.opacity(0.14))
                            : nil
                    )
                }
            }

            Section("Quick Actions") {
                Button {
                    vm.showNewDocumentSheet = true
                } label: {
                    Label(vm.tr(.newDocument), systemImage: "doc.badge.plus")
                }
                .buttonStyle(.plain)

                Button {
                    vm.openDocumentWithPanel()
                } label: {
                    Label(vm.tr(.openFile), systemImage: "folder")
                }
                .buttonStyle(.plain)

                Button {
                    vm.mountProjectFolderWithPanel()
                } label: {
                    Label(vm.tr(.mountFolder), systemImage: "folder.badge.gearshape")
                }
                .buttonStyle(.plain)
            }
        }
        .listStyle(.sidebar)
    }

    private var sidebarDiskBrowserTree: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(vm.mountedFolderURL?.lastPathComponent ?? vm.tr(.diskBrowser))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
                if vm.mountedFolderURL != nil {
                    Button {
                        vm.reloadDiskBrowserTree()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption)
                    }
                    .buttonStyle(.plain)
                    .help("Refresh Directory Tree")
                }
                Button {
                    vm.mountProjectFolderWithPanel()
                } label: {
                    Image(systemName: "folder.badge.plus")
                        .font(.caption)
                }
                .buttonStyle(.plain)
                .help(vm.tr(.mountFolder))
            }
            .padding(.horizontal, 14)

            if vm.diskBrowserNodes.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "folder.badge.gearshape")
                        .font(.system(size: 28))
                        .foregroundStyle(.secondary)
                    Text("Mount a project folder to browse scripts and run multi-file regex searches.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Button("Mount Folder…") {
                        vm.mountProjectFolderWithPanel()
                    }
                    .liquidGlassButton(prominent: true)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(16)
            } else {
                List(vm.diskBrowserNodes, children: \.children) { node in
                    Button {
                        if !node.isDirectory {
                            vm.openFile(at: node.url)
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: node.isDirectory ? "folder.fill" : SyntaxLanguage.fromFileName(node.name).sfSymbolName)
                                .foregroundStyle(node.isDirectory ? Color.accentColor : Color.secondary)
                                .font(.system(size: 12))
                            Text(node.name)
                                .font(.system(size: 12))
                                .lineLimit(1)
                            Spacer()
                        }
                    }
                    .buttonStyle(.plain)
                }
                .listStyle(.sidebar)
            }
        }
    }

    private var sidebarFunctionOutline: some View {
        let filtered = outlineFilterQuery.trimmingCharacters(in: .whitespaces).isEmpty
            ? vm.focusedSymbols
            : vm.focusedSymbols.filter {
                $0.name.localizedCaseInsensitiveContains(outlineFilterQuery) ||
                $0.signature.localizedCaseInsensitiveContains(outlineFilterQuery) ||
                $0.kind.badge.localizedCaseInsensitiveContains(outlineFilterQuery)
            }

        return VStack(spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("Filter functions & landmarks…", text: $outlineFilterQuery)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12))
                if !outlineFilterQuery.isEmpty {
                    Button {
                        outlineFilterQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .liquidGlassCapsule()
            .padding(.horizontal, 10)

            if filtered.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "function")
                        .font(.title2)
                        .foregroundStyle(.secondary)
                    Text(vm.tr(.noFunctionsFound))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(filtered) { sym in
                    Button {
                        vm.jumpToSymbol(sym)
                    } label: {
                        HStack(spacing: 8) {
                            Text(sym.kind.badge)
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundStyle(symbolBadgeColor(sym.kind))
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(symbolBadgeColor(sym.kind).opacity(0.15), in: Capsule())
                            VStack(alignment: .leading, spacing: 1) {
                                Text(sym.name)
                                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                                    .lineLimit(1)
                                Text(sym.signature)
                                    .font(.system(size: 10.5, design: .monospaced))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            Text("Ln \(sym.lineNumber)")
                                .font(.caption2.monospacedDigit())
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.vertical, 2)
                    }
                    .buttonStyle(.plain)
                }
                .listStyle(.sidebar)
            }
        }
    }

    private var sidebarClippingsQuickList: some View {
        List {
            Section {
                Button {
                    vm.showClippingsSheet = true
                } label: {
                    Label("Manage & Create Clippings…", systemImage: "slider.horizontal.3")
                        .font(.caption.weight(.semibold))
                }
                .buttonStyle(.plain)
            }
            ForEach(vm.allClippings) { clip in
                Button {
                    vm.insertClipping(clip)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(clip.name)
                                .font(.system(size: 12, weight: .medium))
                                .lineLimit(1)
                            Spacer()
                            Text(clip.triggerPrefix)
                                .font(.system(size: 10, weight: .semibold, design: .monospaced))
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(Color.accentColor.opacity(0.15), in: Capsule())
                        }
                        Text(clip.description)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                    .padding(.vertical, 2)
                }
                .buttonStyle(.plain)
            }
        }
        .listStyle(.sidebar)
    }

    private var sidebarDiagnosticsList: some View {
        VStack(spacing: 6) {
            if vm.focusedDiagnostics.contains(where: { $0.quickFixTarget != nil }) {
                HStack {
                    Spacer()
                    Button {
                        vm.applyAllQuickFixes()
                    } label: {
                        Label(vm.tr(.quickFixAll), systemImage: "wand.and.stars")
                            .font(.caption.weight(.semibold))
                    }
                    .liquidGlassButton(prominent: true)
                    .controlSize(.small)
                }
                .padding(.horizontal, 12)
            }

            if vm.focusedDiagnostics.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(.green)
                    Text(vm.tr(.cleanScript))
                        .font(.caption.weight(.semibold))
                    Text("PSScriptAnalyzer found zero warnings or unbalanced delimiters.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(14)
            } else {
                List(vm.focusedDiagnostics) { diag in
                    VStack(alignment: .leading, spacing: 4) {
                        Button {
                            vm.jumpToDiagnostic(diag)
                        } label: {
                            HStack(alignment: .top, spacing: 6) {
                                Image(systemName: diagnosticIcon(diag.severity))
                                    .foregroundStyle(diagnosticColor(diag.severity))
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack {
                                        Text(diag.ruleId)
                                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                        Spacer()
                                        Text("Ln \(diag.lineNumber):\(diag.column)")
                                            .font(.caption2.monospacedDigit())
                                            .foregroundStyle(.secondary)
                                    }
                                    Text(diag.message)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .buttonStyle(.plain)

                        if let fixLabel = diag.quickFixLabel {
                            Button(fixLabel) {
                                vm.applyQuickFix(diag)
                            }
                            .font(.caption2.weight(.semibold))
                            .liquidGlassButton()
                            .controlSize(.mini)
                        }
                    }
                    .padding(.vertical, 3)
                }
                .listStyle(.sidebar)
            }
        }
    }

    // MARK: - Unified macOS Toolbar (Liquid Glass Control Groups)

    private var toolbarFunctionNavigatorMenu: some View {
        Menu {
            if vm.focusedSymbols.isEmpty {
                Text(vm.tr(.noFunctionsFound))
            } else {
                ForEach(vm.focusedSymbols) { sym in
                    Button {
                        vm.jumpToSymbol(sym)
                    } label: {
                        Text("[\(sym.kind.badge)]  \(sym.signature)   — Ln \(sym.lineNumber)")
                    }
                }
                Divider()
                Button("Show in Sidebar Outline") {
                    vm.sidebarSection = .functionOutline
                }
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: "function")
                Text("\(vm.focusedSymbols.count)")
                    .font(.caption.monospacedDigit().weight(.semibold))
            }
        }
        .help("\(vm.tr(.functionNavigator)) (\(vm.focusedSymbols.count))")
    }

    @ViewBuilder
    private var toolbarSplitModeButtons: some View {
        ForEach(SplitViewMode.allCases) { mode in
            let isSelected = (vm.splitMode == mode)
            Button {
                vm.splitMode = mode
                vm.refreshActiveAnalysisImmediate()
            } label: {
                Image(systemName: mode.sfSymbol)
                    .fontWeight(isSelected ? .semibold : .regular)
                    .foregroundStyle(isSelected ? Color.accentColor : Color.primary)
            }
            .help(mode.label)
        }
    }

    @ViewBuilder
    private var toolbarWorkbenchButtons: some View {
        Button {
            vm.findSheetInitialTab = .document
            vm.showFindReplaceSheet = true
        } label: {
            Image(systemName: "magnifyingglass")
        }
        .help("\(vm.tr(.findReplace)) (⌘F)")

        Button {
            vm.showTextFactorySheet = true
        } label: {
            Image(systemName: "wand.and.stars")
        }
        .help("\(vm.tr(.textFactory)) (⇧⌘T)")

        Button {
            vm.openDiffTool()
        } label: {
            Image(systemName: "arrow.left.arrow.right.square")
        }
        .help("\(vm.tr(.compareDiff)) (⌃⌘D)")

        Button {
            vm.showClippingsSheet = true
        } label: {
            Image(systemName: "text.badge.plus")
        }
        .help("\(vm.tr(.clippings)) (⌃⌘C)")

        Button {
            vm.showDiagnosticsSheet = true
        } label: {
            HStack(spacing: 4) {
                Image(systemName: vm.focusedDiagnostics.isEmpty ? "checkmark.shield" : "exclamationmark.shield.fill")
                    .foregroundStyle(vm.focusedDiagnostics.isEmpty ? Color.green : Color.orange)
                if !vm.focusedDiagnostics.isEmpty {
                    Text("\(vm.focusedDiagnostics.count)")
                        .font(.caption2.monospacedDigit().weight(.bold))
                }
            }
        }
        .help(vm.tr(.diagnostics))

        Button {
            vm.showCommandPalette.toggle()
        } label: {
            Image(systemName: "command.square")
        }
        .help("\(vm.tr(.commandPalette)) (⇧⌘P)")
    }

    // MARK: - Liquid Glass Morphing Document Tab Bar

    private var liquidGlassDocumentTabStrip: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                LiquidGlassCluster(spacing: 6) {
                    HStack(spacing: 6) {
                        ForEach(vm.documents) { doc in
                            documentTabPill(for: doc)
                        }
                    }
                    .padding(.leading, 10)
                    .padding(.trailing, 4)
                    .padding(.vertical, 5)
                }
                .fixedSize(horizontal: true, vertical: true)
            }
            .frame(height: 38)

            Button {
                vm.showNewDocumentSheet = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 12, weight: .semibold))
                    .frame(width: 26, height: 26)
            }
            .buttonStyle(.plain)
            .liquidGlassCapsule()
            .padding(.trailing, 10)
            .help("\(vm.tr(.newDocument)) (⌘N)")
        }
        .frame(height: 40)
        .frame(maxWidth: .infinity)
        .background(.bar)
    }

    private func documentTabPill(for doc: EditorDocument) -> some View {
        let isFocused = (doc.id == vm.focusedDocumentId)
        let isRenaming = (vm.renamingDocumentId == doc.id)

        return HStack(spacing: 6) {
            Image(systemName: doc.isScratchpad ? "note.text" : doc.language.sfSymbolName)
                .font(.system(size: 11, weight: isFocused ? .semibold : .regular))
                .foregroundStyle(isFocused ? Color.accentColor : Color.secondary)

            if isRenaming {
                HStack(spacing: 2) {
                    TextField("Filename", text: $vm.renameDraftStem)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12, weight: .semibold))
                        .frame(minWidth: 70, maxWidth: 150)
                        .onSubmit { vm.commitRenamingDocument() }
                    let ext = (doc.title as NSString).pathExtension
                    if !ext.isEmpty {
                        Text(".\(ext)")
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundStyle(.secondary)
                    }
                }
            } else {
                Text(doc.title)
                    .font(.system(size: 12, weight: isFocused ? .semibold : .regular))
                    .lineLimit(1)
            }

            if doc.isDirty {
                Circle()
                    .fill(Color.orange)
                    .frame(width: 6, height: 6)
            }

            if !doc.isScratchpad {
                Button {
                    vm.closeDocument(id: doc.id)
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.secondary)
                        .frame(width: 14, height: 14)
                }
                .buttonStyle(.plain)
                .help("Close Tab (⌘W)")
            }
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 5)
        .liquidGlassCapsule(tint: isFocused ? .accentColor : nil)
        .liquidGlassMorphID(doc.id, in: tabGlassNamespace)
        .contentShape(Capsule())
        .onTapGesture {
            vm.selectTab(doc.id)
        }
        .onTapGesture(count: 2) {
            vm.beginRenamingDocument(id: doc.id)
        }
        .contextMenu {
            Button("Save") { _ = vm.saveDocument(id: doc.id) }
            Button("Save As…") { _ = vm.saveDocumentAs(id: doc.id) }
            if !doc.isScratchpad {
                Button("Rename Tab…") { vm.beginRenamingDocument(id: doc.id) }
                Divider()
                Button("Open in Secondary Split Pane") {
                    vm.secondaryDocumentId = doc.id
                    if vm.splitMode == .none { vm.splitMode = .vertical }
                    vm.refreshActiveAnalysisImmediate()
                }
                Divider()
                Button("Close Tab", role: .destructive) { vm.closeDocument(id: doc.id) }
            }
        }
    }

    // MARK: - Split-Capable Code Editor Canvas (Crisp Non-Glass Content Layer per HIG)

    @ViewBuilder
    private var editorSplitWorkspace: some View {
        switch vm.splitMode {
        case .none:
            if let doc = vm.primaryDocument {
                primaryEditorPane(doc: doc)
            }
        case .vertical:
            HSplitView {
                if let pDoc = vm.primaryDocument {
                    primaryEditorPane(doc: pDoc)
                        .frame(minWidth: 260)
                }
                if let sDoc = vm.secondaryDocument {
                    secondaryEditorPane(doc: sDoc)
                        .frame(minWidth: 260)
                }
            }
        case .horizontal:
            VSplitView {
                if let pDoc = vm.primaryDocument {
                    primaryEditorPane(doc: pDoc)
                        .frame(minHeight: 160)
                }
                if let sDoc = vm.secondaryDocument {
                    secondaryEditorPane(doc: sDoc)
                        .frame(minHeight: 160)
                }
            }
        }
    }

    private func primaryEditorPane(doc: EditorDocument) -> some View {
        VStack(spacing: 0) {
            if vm.splitMode != .none {
                splitPaneHeaderBar(doc: doc, isSecondary: false, isFocused: !vm.isSecondaryPaneFocused)
            }
            MacCodeEditorView(
                document: doc,
                astSymbols: vm.primarySymbols,
                foldRegions: vm.primaryFolds,
                diagnostics: vm.primaryDiagnostics,
                themeMode: vm.themeMode,
                fontSize: vm.fontSize,
                tabWidth: vm.tabWidth,
                softWrap: vm.softWrap,
                showLineNumbers: vm.showLineNumbers,
                showInvisibles: vm.showInvisibles,
                showPageGuides: vm.showPageGuides,
                highlightCurrentLine: vm.highlightCurrentLine,
                scrollJumpRequest: vm.scrollJumpRequest,
                onContentChange: { text, range in
                    vm.updateDocumentContent(id: doc.id, newContent: text, selectionRange: range)
                },
                onSelectionChange: { range in
                    vm.updateDocumentSelection(id: doc.id, selectionRange: range)
                },
                onToggleFold: { line in
                    vm.toggleFold(documentId: doc.id, startLine: line)
                },
                onFocusGained: {
                    if vm.isSecondaryPaneFocused {
                        vm.isSecondaryPaneFocused = false
                    }
                }
            )
        }
    }

    private func secondaryEditorPane(doc: EditorDocument) -> some View {
        VStack(spacing: 0) {
            splitPaneHeaderBar(doc: doc, isSecondary: true, isFocused: vm.isSecondaryPaneFocused)
            MacCodeEditorView(
                document: doc,
                astSymbols: vm.secondarySymbols,
                foldRegions: vm.secondaryFolds,
                diagnostics: vm.secondaryDiagnostics,
                themeMode: vm.themeMode,
                fontSize: vm.fontSize,
                tabWidth: vm.tabWidth,
                softWrap: vm.softWrap,
                showLineNumbers: vm.showLineNumbers,
                showInvisibles: vm.showInvisibles,
                showPageGuides: vm.showPageGuides,
                highlightCurrentLine: vm.highlightCurrentLine,
                scrollJumpRequest: vm.scrollJumpRequest,
                onContentChange: { text, range in
                    vm.updateDocumentContent(id: doc.id, newContent: text, selectionRange: range)
                },
                onSelectionChange: { range in
                    vm.updateDocumentSelection(id: doc.id, selectionRange: range)
                },
                onToggleFold: { line in
                    vm.toggleFold(documentId: doc.id, startLine: line)
                },
                onFocusGained: {
                    if !vm.isSecondaryPaneFocused {
                        vm.isSecondaryPaneFocused = true
                    }
                }
            )
        }
    }

    private func splitPaneHeaderBar(doc: EditorDocument, isSecondary: Bool, isFocused: Bool) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(isFocused ? Color.accentColor : Color.secondary.opacity(0.35))
                .frame(width: 7, height: 7)
            Picker("", selection: isSecondary ? $vm.secondaryDocumentId : $vm.activeDocumentId) {
                ForEach(vm.documents) { d in
                    Text(d.title).tag(d.id)
                }
            }
            .labelsHidden()
            .controlSize(.small)
            .frame(maxWidth: 220)
            .onChange(of: vm.secondaryDocumentId) { _, _ in vm.refreshActiveAnalysisImmediate() }
            .onChange(of: vm.activeDocumentId) { _, _ in vm.refreshActiveAnalysisImmediate() }

            Spacer()
            let (ln, col) = doc.cursorLineAndColumn()
            Text("Ln \(ln), Col \(col)")
                .font(.caption2.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(.bar)
    }

    // MARK: - Bottom Liquid Glass Status Bar (with Bottom-Left Version Pill & Auto-Update Badge)

    private var liquidGlassBottomStatusBar: some View {
        let doc = vm.focusedDocument
        let (ln, col) = doc?.cursorLineAndColumn() ?? (1, 1)
        let sel = doc?.selectionStats() ?? SelectionStats(hasSelection: false, charCount: 0, wordCount: 0, lineCount: 0)
        let hasUpdate: Bool = {
            if case .updateAvailable = updateManager.state { return true }
            if case .readyToRelaunch = updateManager.state { return true }
            return false
        }()

        return HStack(spacing: 10) {
            // Bottom-Left Version Pill (explicit user requirement + opens In-App Update Center!)
            Button {
                updateManager.openSoftwareUpdateCenter()
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: hasUpdate ? "arrow.down.circle.fill" : "sparkles")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(hasUpdate ? Color.green : Color.accentColor)
                    Text("v\(MacAutoUpdateEngine.currentAppVersion)")
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    if hasUpdate {
                        Text("Update")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.green)
                    }
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 3)
                .liquidGlassCapsule(tint: hasUpdate ? .green : nil)
            }
            .buttonStyle(.plain)
            .help("GravityEdit v\(MacAutoUpdateEngine.currentAppVersion) — Click to open Software Update")

            // Cursor Ln:Col & Go to Line Popover
            Button {
                goToLineInput = "\(ln)"
                vm.showGoToLinePopover.toggle()
            } label: {
                Text("Ln \(ln), Col \(col)")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .liquidGlassCapsule()
            }
            .buttonStyle(.plain)
            .popover(isPresented: $vm.showGoToLinePopover, arrowEdge: .top) {
                HStack(spacing: 8) {
                    Text(vm.tr(.goToLine))
                        .font(.caption.weight(.semibold))
                    TextField("1..\(doc?.lineCount ?? 1)", text: $goToLineInput)
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 80)
                        .onSubmit {
                            if let target = Int(goToLineInput.trimmingCharacters(in: .whitespaces)) {
                                vm.jumpToLine(target)
                            }
                            vm.showGoToLinePopover = false
                        }
                    Button("Go") {
                        if let target = Int(goToLineInput.trimmingCharacters(in: .whitespaces)) {
                            vm.jumpToLine(target)
                        }
                        vm.showGoToLinePopover = false
                    }
                    .liquidGlassButton(prominent: true)
                    .controlSize(.small)
                }
                .padding(10)
            }

            // Selection / Document Counts
            if sel.hasSelection {
                Text("Sel: \(sel.charCount) chars (\(sel.lineCount) ln)")
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(Color.accentColor)
            } else if let doc {
                Text("\(doc.lineCount) lines • \(doc.wordCount) words • \(doc.charCount) chars")
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(.secondary)
            }

            Spacer()

            // PSScriptAnalyzer Quick Pill
            if doc?.language == .powershell {
                Button {
                    vm.showDiagnosticsSheet = true
                } label: {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(vm.focusedDiagnostics.isEmpty ? Color.green : Color.orange)
                            .frame(width: 6, height: 6)
                        Text(vm.focusedDiagnostics.isEmpty ? vm.tr(.cleanScript) : "\(vm.focusedDiagnostics.count) Issues")
                            .font(.system(size: 11, weight: .medium))
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .liquidGlassCapsule()
                }
                .buttonStyle(.plain)
            }

            // Syntax Language Picker (updates both syntax highlighting and filename extension)
            if let doc {
                Menu {
                    ForEach(SyntaxLanguage.allCases) { lang in
                        Button {
                            vm.setFocusedLanguage(lang)
                        } label: {
                            HStack {
                                Text("\(lang.displayName) (.\(lang.primaryExtension))")
                                if lang == doc.language { Image(systemName: "checkmark") }
                            }
                        }
                    }
                } label: {
                    Text(doc.language.displayName)
                        .font(.system(size: 11, weight: .medium))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .liquidGlassCapsule()
                }
                .menuStyle(.borderlessButton)
                .fixedSize()

                // Encoding Picker
                Menu {
                    ForEach(DocumentEncoding.allCases) { enc in
                        Button(enc.displayName) { vm.setFocusedEncoding(enc) }
                    }
                } label: {
                    Text(doc.encoding.displayName)
                        .font(.system(size: 11, weight: .medium))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .liquidGlassCapsule()
                }
                .menuStyle(.borderlessButton)
                .fixedSize()

                // Line Ending Picker
                Menu {
                    ForEach(LineEnding.allCases) { le in
                        Button(le.displayName) { vm.setFocusedLineEnding(le) }
                    }
                } label: {
                    Text(doc.lineEnding.badge)
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .liquidGlassCapsule()
                }
                .menuStyle(.borderlessButton)
                .fixedSize()
            }

            // Zoom Pill
            HStack(spacing: 4) {
                Button { vm.zoomOut() } label: { Image(systemName: "minus") }
                    .buttonStyle(.plain)
                Button { vm.zoomReset() } label: {
                    Text("\(Int(round((vm.fontSize / 13.5) * 100)))%")
                        .font(.system(size: 10.5, weight: .medium, design: .monospaced))
                }
                .buttonStyle(.plain)
                Button { vm.zoomIn() } label: { Image(systemName: "plus") }
                    .buttonStyle(.plain)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .liquidGlassCapsule()
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(.bar)
    }

    private func diagnosticIcon(_ severity: DiagnosticSeverity) -> String {
        switch severity {
        case .error: return "xmark.octagon.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .info: return "info.circle.fill"
        }
    }

    private func diagnosticColor(_ severity: DiagnosticSeverity) -> Color {
        switch severity {
        case .error: return .red
        case .warning: return .orange
        case .info: return .cyan
        }
    }
}

private struct GravityWindowToolbarModifier<
    FuncMenu: View,
    SplitButtons: View,
    WorkbenchButtons: View
>: ViewModifier {
    @ObservedObject var vm: WorkspaceViewModel
    let functionNavigatorMenu: () -> FuncMenu
    let splitModeButtons: () -> SplitButtons
    let workbenchToolButtons: () -> WorkbenchButtons

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(macOS 26.0, *) {
            content.toolbar {
                ToolbarItem(placement: .primaryAction) {
                    functionNavigatorMenu()
                }
                ToolbarSpacer(.fixed, placement: .primaryAction)
                ToolbarItemGroup(placement: .primaryAction) {
                    splitModeButtons()
                }
                ToolbarSpacer(.fixed, placement: .primaryAction)
                ToolbarItemGroup(placement: .primaryAction) {
                    workbenchToolButtons()
                }
            }
        } else {
            content.toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    functionNavigatorMenu()
                    Divider()
                    splitModeButtons()
                    Divider()
                    workbenchToolButtons()
                }
            }
        }
    }
}
