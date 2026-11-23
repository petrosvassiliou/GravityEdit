import AppKit
import GravityEditCore
import SwiftUI
import WebKit

// MARK: - 1. PowerShell Show-Command Parameter Form Sheet (⇧⌘I)

public struct ParameterFormSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var selectedSpecIndex: Int = 0
    @State private var fieldValues: [String: String] = [:]
    @State private var switchStates: [String: Bool] = [:]

    public init(vm: WorkspaceViewModel) {
        self.vm = vm
    }

    private var activeSpec: ParameterFormSpec? {
        guard !vm.parameterFormSpecs.isEmpty else { return nil }
        let idx = min(max(0, selectedSpecIndex), vm.parameterFormSpecs.count - 1)
        return vm.parameterFormSpecs[idx]
    }

    private var generatedCommand: String {
        guard let spec = activeSpec else { return "" }
        return PowerShellToolingEngine.buildInvocationCommand(
            spec: spec,
            values: fieldValues,
            switchStates: switchStates
        )
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 10) {
                Image(systemName: "slider.horizontal.below.rectangle")
                    .font(.title3)
                    .foregroundStyle(Color.accentColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text("PowerShell Show-Command Parameter Builder")
                        .font(.headline)
                    Text("Inspect param() attributes and build a strongly-typed command line")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("Done") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
            }
            .padding(16)
            .background(.bar)

            Divider().opacity(0.35)

            if vm.parameterFormSpecs.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "curlybraces.square")
                        .font(.system(size: 34))
                        .foregroundStyle(.secondary)
                    Text("No PowerShell param(...) blocks or functions found in the active document.")
                        .font(.callout.weight(.medium))
                    Text("Add a top-level `param(...)` block or `function Verb-Noun { param(...) }` to inspect parameters interactively.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 420)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(24)
            } else if let spec = activeSpec {
                VStack(alignment: .leading, spacing: 12) {
                    if vm.parameterFormSpecs.count > 1 {
                        HStack {
                            Text("Command / Function:")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            Picker("", selection: $selectedSpecIndex) {
                                ForEach(Array(vm.parameterFormSpecs.enumerated()), id: \.offset) { idx, item in
                                    Text(item.isFunction ? "fn \(item.commandName)" : "script \(item.commandName)")
                                        .tag(idx)
                                }
                            }
                            .labelsHidden()
                            .frame(maxWidth: 320)
                            .onChange(of: selectedSpecIndex) { _, _ in
                                seedDefaults(for: activeSpec)
                            }
                            Spacer()
                        }
                    }

                    if let synopsis = spec.synopsis, !synopsis.isEmpty {
                        Text(synopsis)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .liquidGlassCard(cornerRadius: 10)
                    }

                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            if spec.parameters.isEmpty {
                                Text("“\(spec.commandName)” takes no declared parameters.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .padding(.vertical, 12)
                            } else {
                                ForEach(spec.parameters) { param in
                                    parameterRow(param)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }

                    Divider().opacity(0.35)

                    // Live Command Preview
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Synthesized Command")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.secondary)
                        Text(generatedCommand)
                            .font(.system(size: 12, weight: .medium, design: .monospaced))
                            .textSelection(.enabled)
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .strokeBorder(Color.primary.opacity(0.12), lineWidth: 1)
                            )
                    }

                    HStack(spacing: 10) {
                        Button {
                            NSPasteboard.general.clearContents()
                            NSPasteboard.general.setString(generatedCommand, forType: .string)
                            vm.postToast("Copied PowerShell command to Clipboard")
                        } label: {
                            Label("Copy", systemImage: "doc.on.doc")
                        }
                        .liquidGlassButton()

                        Spacer()

                        Button {
                            vm.applyGeneratedParameterCommand(generatedCommand, executeImmediately: false)
                        } label: {
                            Label("Insert into Script", systemImage: "text.insert")
                        }
                        .liquidGlassButton()

                        Button {
                            vm.applyGeneratedParameterCommand(generatedCommand, executeImmediately: true)
                        } label: {
                            Label("Run Now", systemImage: "play.fill")
                        }
                        .keyboardShortcut(.defaultAction)
                        .liquidGlassButton(prominent: true)
                    }
                }
                .padding(16)
                .onAppear {
                    seedDefaults(for: spec)
                }
            }
        }
        .frame(width: 620, height: 520)
    }

    @ViewBuilder
    private func parameterRow(_ param: ParameterFieldSpec) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 6) {
                Text("-\(param.name)")
                    .font(.system(size: 12.5, weight: .semibold, design: .monospaced))
                Text("[\(param.typeName)]")
                    .font(.system(size: 10.5, design: .monospaced))
                    .foregroundStyle(.secondary)
                if param.isMandatory {
                    Text("Mandatory")
                        .font(.system(size: 9.5, weight: .bold))
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Color.orange.opacity(0.15), in: Capsule())
                }
                Spacer()
            }

            if let help = param.helpMessage, !help.isEmpty {
                Text(help)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            if param.isSwitch {
                Toggle(
                    "Include -\(param.name) switch",
                    isOn: Binding(
                        get: { switchStates[param.name] ?? false },
                        set: { switchStates[param.name] = $0 }
                    )
                )
                .toggleStyle(.switch)
                .controlSize(.small)
            } else if !param.validateSetOptions.isEmpty {
                Picker(
                    "",
                    selection: Binding(
                        get: { fieldValues[param.name] ?? (param.defaultValue ?? param.validateSetOptions.first ?? "") },
                        set: { fieldValues[param.name] = $0 }
                    )
                ) {
                    Text("(Omit)").tag("")
                    ForEach(param.validateSetOptions, id: \.self) { opt in
                        Text(opt).tag(opt)
                    }
                }
                .labelsHidden()
            } else {
                TextField(
                    param.defaultValue.map { "Default: \($0)" } ?? "Enter \(param.typeName) value…",
                    text: Binding(
                        get: { fieldValues[param.name] ?? "" },
                        set: { fieldValues[param.name] = $0 }
                    )
                )
                .textFieldStyle(.roundedBorder)
                .font(.system(size: 12, design: .monospaced))
            }
        }
        .padding(10)
        .background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }

    private func seedDefaults(for spec: ParameterFormSpec?) {
        fieldValues = [:]
        switchStates = [:]
        guard let spec else { return }
        for p in spec.parameters {
            if p.isSwitch {
                switchStates[p.name] = false
            } else if let def = p.defaultValue {
                fieldValues[p.name] = def
            } else if let firstOpt = p.validateSetOptions.first, p.isMandatory {
                fieldValues[p.name] = firstOpt
            }
        }
    }
}

// MARK: - 2. F1 PowerShell Cmdlet & Comment-Based Quick Help Sheet

public struct QuickHelpSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var searchInput: String = ""

    private let catalog = PowerShellToolingEngine.allOfflineHelpTopics()

    public init(vm: WorkspaceViewModel) {
        self.vm = vm
    }

    private var filteredCatalog: [CmdletHelpTopic] {
        let q = searchInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return catalog }
        return catalog.filter {
            $0.name.localizedCaseInsensitiveContains(q) || $0.synopsis.localizedCaseInsensitiveContains(q)
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "questionmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(Color.accentColor)
                Text("PowerShell Quick Help Reference (F1)")
                    .font(.headline)
                Spacer()
                Button("Query Live pwsh") {
                    vm.searchQuickHelp(query: searchInput.isEmpty ? vm.activeHelpTopic.name : searchInput, preferLivePwsh: true)
                }
                .controlSize(.small)
                .liquidGlassButton()
                Button("Done") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
            }
            .padding(14)
            .background(.bar)

            Divider().opacity(0.35)

            HSplitView {
                // Left Cmdlet Catalog
                VStack(spacing: 8) {
                    TextField("Search cmdlet or alias…", text: $searchInput)
                        .textFieldStyle(.roundedBorder)
                        .onSubmit {
                            vm.searchQuickHelp(query: searchInput, preferLivePwsh: false)
                        }
                        .padding(.horizontal, 10)
                        .padding(.top, 10)

                    List(filteredCatalog) { item in
                        Button {
                            searchInput = item.name
                            vm.activeHelpTopic = item
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name)
                                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                Text(item.moduleName)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                    }
                    .listStyle(.sidebar)
                }
                .frame(minWidth: 205, maxWidth: 240)

                // Right Topic Inspector
                ScrollView {
                    let topic = vm.activeHelpTopic
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(topic.name)
                                .font(.system(size: 20, weight: .bold, design: .monospaced))
                            Spacer()
                            Text(topic.sourceBadge)
                                .font(.caption2.weight(.semibold))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .liquidGlassCapsule()
                        }

                        Text("Module: \(topic.moduleName)")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        VStack(alignment: .leading, spacing: 4) {
                            Text("SYNOPSIS")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.secondary)
                            Text(topic.synopsis)
                                .font(.body)
                                .textSelection(.enabled)
                        }

                        if !topic.syntax.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("SYNTAX")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(topic.syntax, id: \.self) { syn in
                                    Text(syn)
                                        .font(.system(size: 11.5, design: .monospaced))
                                        .textSelection(.enabled)
                                        .padding(10)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                                }
                            }
                        }

                        if !topic.parameters.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("PARAMETERS")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(topic.parameters) { p in
                                    VStack(alignment: .leading, spacing: 2) {
                                        HStack {
                                            Text("-\(p.name) <\(p.typeName)>")
                                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                            if p.isMandatory {
                                                Text("Mandatory")
                                                    .font(.caption2.weight(.bold))
                                                    .foregroundStyle(.orange)
                                            }
                                        }
                                        Text(p.description)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    .padding(8)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 7))
                                }
                            }
                        }

                        if !topic.examples.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("EXAMPLES")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(topic.examples, id: \.self) { ex in
                                    HStack {
                                        Text(ex)
                                            .font(.system(size: 11.5, design: .monospaced))
                                            .textSelection(.enabled)
                                        Spacer()
                                        Button("Copy") {
                                            NSPasteboard.general.clearContents()
                                            NSPasteboard.general.setString(ex, forType: .string)
                                            vm.postToast("Copied example to Clipboard")
                                        }
                                        .controlSize(.mini)
                                        .liquidGlassButton()
                                    }
                                    .padding(9)
                                    .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                                }
                            }
                        }
                    }
                    .padding(18)
                }
            }
        }
        .frame(width: 720, height: 500)
        .onAppear {
            searchInput = vm.quickHelpQuery
        }
    }
}

// MARK: - 3. Pester Unit Test Runner Sheet (⌃⇧T)

public struct PesterRunnerSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    public init(vm: WorkspaceViewModel) {
        self.vm = vm
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.title3)
                    .foregroundStyle(.green)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Pester Unit Test Runner")
                        .font(.headline)
                    if let suite = vm.lastPesterSuiteResult {
                        Text("\(suite.engineDescription) • \(suite.passedCount) passed, \(suite.failedCount) failed • \(suite.totalDurationMs) ms")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(suite.failedCount == 0 ? Color.secondary : Color.red)
                    } else {
                        Text("Discover and execute Describe / Context / It assertions")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Button {
                    vm.runFocusedPesterSuite()
                } label: {
                    Label(vm.isRunningPester ? "Running…" : "Re-Run Tests", systemImage: "arrow.clockwise")
                }
                .disabled(vm.isRunningPester)
                .liquidGlassButton(prominent: true)

                Button("Close") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
            }
            .padding(14)
            .background(.bar)

            Divider().opacity(0.35)

            if vm.isRunningPester {
                VStack(spacing: 10) {
                    ProgressView()
                    Text("Running Pester suite…")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let suite = vm.lastPesterSuiteResult {
                if suite.results.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: 32))
                            .foregroundStyle(.secondary)
                        Text(suite.rawOutput)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                        Text("Example: Describe 'Math' { It 'adds numbers' { (2 + 2) | Should -Be 4 } }")
                            .font(.system(size: 11.5, design: .monospaced))
                            .foregroundStyle(.tertiary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(20)
                } else {
                    List(suite.results) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 8) {
                                Image(systemName: item.passed ? "checkmark.circle.fill" : "xmark.octagon.fill")
                                    .foregroundStyle(item.passed ? Color.green : Color.red)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(item.itName)
                                        .font(.system(size: 12.5, weight: .semibold))
                                    Text([item.describeBlock, item.contextBlock].compactMap { $0 }.joined(separator: " › "))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("\(item.durationMs) ms")
                                    .font(.caption2.monospacedDigit())
                                    .foregroundStyle(.secondary)
                                Button("Ln \(item.lineNumber) ↗") {
                                    dismiss()
                                    vm.jumpToLine(item.lineNumber)
                                }
                                .controlSize(.mini)
                                .buttonStyle(.bordered)
                            }
                            if let fail = item.failureMessage, !fail.isEmpty {
                                Text(fail)
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(.red)
                                    .padding(.leading, 24)
                            }
                        }
                        .padding(.vertical, 3)
                    }
                }
            } else {
                Spacer()
            }
        }
        .frame(width: 640, height: 460)
    }
}

// MARK: - 4. BBEdit-Style Remote SFTP / SSH Browser Sheet (⌥⌘K)

public struct SFTPBrowserSheetView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var profileName: String = "Prod Server"
    @State private var host: String = ""
    @State private var portText: String = "22"
    @State private var username: String = NSUserName()
    @State private var currentRemotePath: String = "/"
    @State private var keyFilePath: String = "~/.ssh/id_ed25519"
    @State private var sessionPassword: String = ""
    @State private var entries: [SFTPRemoteEntry] = []
    @State private var isLoadingDir: Bool = false

    public init(vm: WorkspaceViewModel) {
        self.vm = vm
    }

    private var activeProfileDraft: SFTPConnectionProfile {
        SFTPConnectionProfile(
            name: profileName.trimmingCharacters(in: .whitespaces).isEmpty ? host : profileName,
            host: host.trimmingCharacters(in: .whitespacesAndNewlines),
            port: Int(portText) ?? 22,
            username: username.trimmingCharacters(in: .whitespacesAndNewlines),
            initialPath: currentRemotePath,
            keyFilePath: keyFilePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : keyFilePath
        )
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "network")
                    .font(.title3)
                    .foregroundStyle(Color.accentColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Open from SFTP / SSH Server")
                        .font(.headline)
                    Text("Uses system SSH Agent, ~/.ssh/config & identity keys — remote tabs save atomically via ⌘S")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("Close") { dismiss() }
                    .keyboardShortcut(.cancelAction)
                    .liquidGlassButton()
            }
            .padding(14)
            .background(.bar)

            Divider().opacity(0.35)

            HSplitView {
                // Saved SFTP Bookmarks Sidebar
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Saved Bookmarks")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button {
                            guard !host.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                            vm.saveSftpProfile(activeProfileDraft)
                        } label: {
                            Image(systemName: "plus.circle")
                        }
                        .buttonStyle(.plain)
                        .help("Save current connection as bookmark (passwords are never stored)")
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 10)

                    if vm.sftpProfiles.isEmpty {
                        Text("No saved servers yet. Fill in host & username and click + to bookmark.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .padding(12)
                        Spacer()
                    } else {
                        List {
                            ForEach(vm.sftpProfiles) { prof in
                                HStack {
                                    Button {
                                        loadProfile(prof)
                                    } label: {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(prof.name)
                                                .font(.system(size: 12, weight: .semibold))
                                            Text("\(prof.username)@\(prof.host):\(prof.port)")
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                    Spacer()
                                    Button {
                                        vm.deleteSftpProfile(id: prof.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .listStyle(.sidebar)
                    }
                }
                .frame(minWidth: 195, maxWidth: 230)

                // Connection Form + Remote Directory Listing
                VStack(alignment: .leading, spacing: 10) {
                    Grid(alignment: .leading, horizontalSpacing: 10, verticalSpacing: 8) {
                        GridRow {
                            Text("Host:")
                                .font(.caption.weight(.semibold))
                            TextField("server.example.com", text: $host)
                                .textFieldStyle(.roundedBorder)
                            Text("Port:")
                                .font(.caption.weight(.semibold))
                            TextField("22", text: $portText)
                                .textFieldStyle(.roundedBorder)
                                .frame(width: 64)
                        }
                        GridRow {
                            Text("User:")
                                .font(.caption.weight(.semibold))
                            TextField("deploy", text: $username)
                                .textFieldStyle(.roundedBorder)
                            Text("Bookmark:")
                                .font(.caption.weight(.semibold))
                            TextField("Label", text: $profileName)
                                .textFieldStyle(.roundedBorder)
                        }
                        GridRow {
                            Text("SSH Key:")
                                .font(.caption.weight(.semibold))
                            TextField("~/.ssh/id_ed25519 (optional)", text: $keyFilePath)
                                .textFieldStyle(.roundedBorder)
                            Text("Password:")
                                .font(.caption.weight(.semibold))
                            SecureField("Session only", text: $sessionPassword)
                                .textFieldStyle(.roundedBorder)
                        }
                    }

                    HStack(spacing: 8) {
                        Button {
                            let parent = RemoteSFTPEngine.parentRemotePath(of: currentRemotePath)
                            currentRemotePath = parent
                            fetchDirectory(path: parent)
                        } label: {
                            Image(systemName: "arrow.up")
                        }
                        .disabled(currentRemotePath == "/" || isLoadingDir)
                        .liquidGlassButton()

                        TextField("/var/www or /home/user", text: $currentRemotePath)
                            .textFieldStyle(.roundedBorder)
                            .font(.system(size: 12, design: .monospaced))
                            .onSubmit {
                                fetchDirectory(path: currentRemotePath)
                            }

                        Button {
                            fetchDirectory(path: currentRemotePath)
                        } label: {
                            Label(isLoadingDir ? "Connecting…" : "Browse", systemImage: "arrow.clockwise")
                        }
                        .disabled(host.trimmingCharacters(in: .whitespaces).isEmpty || isLoadingDir)
                        .liquidGlassButton(prominent: true)
                    }

                    if let err = vm.sftpErrorMessage {
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .lineLimit(2)
                    }

                    if isLoadingDir || vm.isSftpBusy {
                        HStack(spacing: 8) {
                            ProgressView().controlSize(.small)
                            Text("Communicating over SFTP…")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if entries.isEmpty {
                        VStack(spacing: 8) {
                            Image(systemName: "externaldrive.connected.to.line.below")
                                .font(.system(size: 28))
                                .foregroundStyle(.secondary)
                            Text("Enter host & username and click Browse to inspect remote files.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List(entries) { entry in
                            HStack(spacing: 8) {
                                Image(systemName: entry.isDirectory ? "folder.fill" : SyntaxLanguage.fromFileName(entry.name).sfSymbolName)
                                    .foregroundStyle(entry.isDirectory ? Color.accentColor : Color.secondary)
                                Text(entry.name)
                                    .font(.system(size: 12, weight: entry.isDirectory ? .semibold : .regular, design: .monospaced))
                                Spacer()
                                if !entry.isDirectory {
                                    Text(ByteCountFormatter.string(fromByteCount: entry.sizeBytes, countStyle: .file))
                                        .font(.caption2.monospacedDigit())
                                        .foregroundStyle(.secondary)
                                }
                                Text(entry.permissions)
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundStyle(.tertiary)
                                Button(entry.isDirectory ? "Open Dir" : "Edit") {
                                    if entry.isDirectory {
                                        currentRemotePath = entry.fullPath
                                        fetchDirectory(path: entry.fullPath)
                                    } else {
                                        vm.openRemoteSftpFile(
                                            profile: activeProfileDraft,
                                            entry: entry,
                                            sessionPassword: sessionPassword.isEmpty ? nil : sessionPassword
                                        )
                                    }
                                }
                                .controlSize(.mini)
                                .buttonStyle(.bordered)
                            }
                        }
                    }
                }
                .padding(14)
            }
        }
        .frame(width: 760, height: 520)
        .onAppear {
            if let first = vm.sftpProfiles.first, host.isEmpty {
                loadProfile(first)
            }
        }
    }

    private func loadProfile(_ prof: SFTPConnectionProfile) {
        profileName = prof.name
        host = prof.host
        portText = "\(prof.port)"
        username = prof.username
        currentRemotePath = prof.initialPath
        keyFilePath = prof.keyFilePath ?? ""
    }

    private func fetchDirectory(path: String) {
        let draft = activeProfileDraft
        let pwd = sessionPassword.isEmpty ? nil : sessionPassword
        isLoadingDir = true
        vm.sftpErrorMessage = nil

        Task {
            let result = await Task.detached(priority: .userInitiated) { () -> Result<[SFTPRemoteEntry], Error> in
                do {
                    return .success(try RemoteSFTPEngine.listRemoteDirectory(profile: draft, directoryPath: path, password: pwd))
                } catch {
                    return .failure(error)
                }
            }.value
            await MainActor.run {
                isLoadingDir = false
                switch result {
                case .success(let list):
                    entries = list
                case .failure(let error):
                    vm.sftpErrorMessage = error.localizedDescription
                }
            }
        }
    }
}

// MARK: - 5. Structured CSV / TSV / JSON Data Grid Inspector (⌃⇧G)

public struct StructuredDataGridView: View {
    public let document: EditorDocument
    public let onUpdateContent: (String) -> Void
    public let onExitGrid: () -> Void

    @State private var filterQuery: String = ""
    @State private var sortColumnIndex: Int? = nil
    @State private var sortAscending: Bool = true

    public init(
        document: EditorDocument,
        onUpdateContent: @escaping (String) -> Void,
        onExitGrid: @escaping () -> Void
    ) {
        self.document = document
        self.onUpdateContent = onUpdateContent
        self.onExitGrid = onExitGrid
    }

    private var parsedTable: DataGridTable {
        StructuredDataGridEngine.parseTable(
            from: document.content,
            language: document.language,
            fileName: document.title
        ) ?? DataGridTable(headers: ["Column1"], rows: [])
    }

    private var formatBadge: String {
        let tbl = parsedTable
        if tbl.isJsonArray { return "JSON Object Array" }
        switch tbl.delimiter {
        case "\t": return "TSV (Tab)"
        case ";": return "CSV (Semicolon)"
        case "|": return "PSV (Pipe)"
        default: return "CSV (Comma)"
        }
    }

    public var body: some View {
        let table = parsedTable
        let visibleRows = StructuredDataGridEngine.filterRows(table, query: filterQuery)

        VStack(spacing: 0) {
            // Grid Toolbar
            HStack(spacing: 10) {
                Image(systemName: "tablecells.fill")
                    .foregroundStyle(Color.accentColor)
                Text(formatBadge)
                    .font(.caption.weight(.semibold))
                Text("• \(table.rows.count) rows × \(table.headers.count) cols")
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(.secondary)

                HStack(spacing: 5) {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    TextField("Filter rows…", text: $filterQuery)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .frame(width: 180)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 4)
                .liquidGlassCapsule()

                Spacer()

                Button {
                    let next = StructuredDataGridEngine.appendEmptyRow(table)
                    onUpdateContent(StructuredDataGridEngine.serializeTable(next))
                } label: {
                    Label("Add Row", systemImage: "plus")
                        .font(.caption.weight(.semibold))
                }
                .controlSize(.small)
                .liquidGlassButton()

                Button {
                    onExitGrid()
                } label: {
                    Label("Code View", systemImage: "chevron.left.forwardslash.chevron.right")
                        .font(.caption.weight(.semibold))
                }
                .controlSize(.small)
                .liquidGlassButton(prominent: true)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(.bar)

            Divider().opacity(0.35)

            // Crisp Tabular Content Area
            ScrollView([.horizontal, .vertical]) {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    Section {
                        ForEach(visibleRows, id: \.originalRowIndex) { item in
                            HStack(spacing: 0) {
                                Text("\(item.originalRowIndex + 1)")
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(.secondary)
                                    .frame(width: 46, alignment: .trailing)
                                    .padding(.trailing, 8)

                                ForEach(0..<table.headers.count, id: \.self) { colIdx in
                                    let cellText = colIdx < item.cells.count ? item.cells[colIdx] : ""
                                    TextField(
                                        "",
                                        text: Binding(
                                            get: { cellText },
                                            set: { newVal in
                                                let updated = StructuredDataGridEngine.updateCell(
                                                    table,
                                                    rowIndex: item.originalRowIndex,
                                                    columnIndex: colIdx,
                                                    newValue: newVal
                                                )
                                                onUpdateContent(StructuredDataGridEngine.serializeTable(updated))
                                            }
                                        )
                                    )
                                    .textFieldStyle(.plain)
                                    .font(.system(size: 12, design: .monospaced))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 5)
                                    .frame(width: 175, alignment: .leading)
                                    .overlay(alignment: .trailing) {
                                        Rectangle()
                                            .fill(Color.primary.opacity(0.08))
                                            .frame(width: 1)
                                    }
                                }

                                Button {
                                    let updated = StructuredDataGridEngine.deleteRow(table, rowIndex: item.originalRowIndex)
                                    onUpdateContent(StructuredDataGridEngine.serializeTable(updated))
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                .buttonStyle(.plain)
                                .padding(.horizontal, 10)
                            }
                            .overlay(alignment: .bottom) {
                                Rectangle()
                                    .fill(Color.primary.opacity(0.07))
                                    .frame(height: 1)
                            }
                        }
                    } header: {
                        HStack(spacing: 0) {
                            Text("#")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.secondary)
                                .frame(width: 46, alignment: .trailing)
                                .padding(.trailing, 8)

                            ForEach(Array(table.headers.enumerated()), id: \.offset) { colIdx, header in
                                Button {
                                    if sortColumnIndex == colIdx {
                                        sortAscending.toggle()
                                    } else {
                                        sortColumnIndex = colIdx
                                        sortAscending = true
                                    }
                                    let sorted = StructuredDataGridEngine.sortTable(table, columnIndex: colIdx, ascending: sortAscending)
                                    onUpdateContent(StructuredDataGridEngine.serializeTable(sorted))
                                } label: {
                                    HStack(spacing: 4) {
                                        Text(header)
                                            .font(.system(size: 11.5, weight: .bold, design: .monospaced))
                                            .lineLimit(1)
                                        if sortColumnIndex == colIdx {
                                            Image(systemName: sortAscending ? "chevron.up" : "chevron.down")
                                                .font(.system(size: 9, weight: .bold))
                                                .foregroundStyle(Color.accentColor)
                                        }
                                        Spacer()
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 6)
                                    .frame(width: 175, alignment: .leading)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .overlay(alignment: .trailing) {
                                    Rectangle()
                                        .fill(Color.primary.opacity(0.12))
                                        .frame(width: 1)
                                }
                            }
                            Spacer(minLength: 32)
                        }
                        .background(.bar)
                        .overlay(alignment: .bottom) {
                            Rectangle()
                                .fill(Color.primary.opacity(0.15))
                                .frame(height: 1)
                        }
                    }
                }
            }
            .background(Color(nsColor: .textBackgroundColor))
        }
    }
}

// MARK: - 6. Live Offline Markdown / HTML Split Preview Pane (⌃⌘P)

public struct LiveWebPreviewPane: NSViewRepresentable {
    public let html: String

    public init(html: String) {
        self.html = html
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    public func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let pagePrefs = WKWebpagePreferences()
        pagePrefs.allowsContentJavaScript = false
        config.defaultWebpagePreferences = pagePrefs

        let webView = WKWebView(frame: .zero, configuration: config)
        context.coordinator.lastHTML = html
        webView.loadHTMLString(html, baseURL: nil)
        return webView
    }

    public func updateNSView(_ webView: WKWebView, context: Context) {
        guard context.coordinator.lastHTML != html else { return }
        context.coordinator.lastHTML = html
        webView.loadHTMLString(html, baseURL: nil)
    }

    public final class Coordinator {
        var lastHTML: String = ""
    }
}
