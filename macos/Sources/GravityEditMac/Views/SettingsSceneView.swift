import GravityEditCore
import SwiftUI

public struct SettingsSceneView: View {
    @ObservedObject var vm: WorkspaceViewModel
    @ObservedObject var updateManager: SparkleUpdateManager

    public init(vm: WorkspaceViewModel, updateManager: SparkleUpdateManager) {
        self.vm = vm
        self.updateManager = updateManager
    }

    public var body: some View {
        TabView {
            generalTab
                .tabItem {
                    Label("General", systemImage: "gearshape")
                }

            editorTab
                .tabItem {
                    Label("Editor", systemImage: "chevron.left.forwardslash.chevron.right")
                }

            appearanceTab
                .tabItem {
                    Label("Appearance", systemImage: "paintbrush")
                }

            updatesTab
                .tabItem {
                    Label("Updates", systemImage: "arrow.triangle.2.circlepath")
                }
        }
        .frame(width: 500, height: 340)
        .padding(10)
    }

    private var generalTab: some View {
        Form {
            Picker("Interface Language:", selection: Binding(
                get: { vm.appLanguage },
                set: { vm.appLanguage = $0 }
            )) {
                ForEach(AppLanguage.allCases) { lang in
                    Text(lang.displayName).tag(lang)
                }
            }

            Toggle("Enable live PSScriptAnalyzer PowerShell linting", isOn: $vm.liveScriptAnalyzer)

            Section("About GravityEdit for macOS") {
                LabeledContent("Version", value: "v\(MacAutoUpdateEngine.currentAppVersion) (Build \(MacAutoUpdateEngine.currentBuildNumber))")
                LabeledContent("Bundle Identifier", value: MacAutoUpdateEngine.bundleIdentifier)
                LabeledContent("Design Architecture", value: "Apple Liquid Glass (macOS 26 Tahoe + Sequoia Vibrancy)")
            }
        }
        .formStyle(.grouped)
    }

    private var editorTab: some View {
        Form {
            HStack {
                Text("Font Size (\(String(format: "%.1f", vm.fontSize)) pt)")
                Spacer()
                Slider(value: $vm.fontSize, in: 9...32, step: 0.5)
                    .frame(width: 200)
            }

            Picker("Tab Width (Spaces):", selection: $vm.tabWidth) {
                Text("2 Spaces").tag(2)
                Text("4 Spaces (PowerShell Standard)").tag(4)
                Text("8 Spaces").tag(8)
            }

            Toggle("Soft Wrap Lines to Window Width", isOn: $vm.softWrap)
            Toggle("Show Line Numbers & Code-Folding Gutter", isOn: $vm.showLineNumbers)
            Toggle("Show Invisible Characters (·, ⇥, ¬)", isOn: $vm.showInvisibles)
            Toggle("Show 80 & 120 Column Page Guides", isOn: $vm.showPageGuides)
            Toggle("Highlight Current Cursor Line", isOn: $vm.highlightCurrentLine)
        }
        .formStyle(.grouped)
    }

    private var appearanceTab: some View {
        Form {
            Picker("Workspace Theme:", selection: Binding(
                get: { vm.themeMode },
                set: { vm.themeMode = $0 }
            )) {
                ForEach(AppearanceThemeMode.allCases) { mode in
                    Text(mode.displayName).tag(mode)
                }
            }

            Text("Following Apple Human Interface Guidelines, Liquid Glass translucency is applied strictly to the floating sidebar, window toolbar, morphing tab strip, status pills, and transient overlays — keeping the AppKit code canvas crisp and high-contrast.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .formStyle(.grouped)
    }

    private var updatesTab: some View {
        Form {
            Toggle("Automatically check for updates", isOn: $updateManager.automaticallyChecksForUpdates)
            Toggle("Automatically download updates in the background", isOn: $updateManager.automaticallyDownloadsUpdates)

            Section("Feed & Verification") {
                LabeledContent("Appcast Feed", value: MacAutoUpdateEngine.sparkleAppcastFeedURL.absoluteString)
                LabeledContent("Integrity", value: "Sparkle 2 EdDSA + CryptoKit SHA-256")
                HStack {
                    Spacer()
                    Button("Check for Updates Now…") {
                        NSApplication.shared.windows.first(where: { $0.canBecomeMain })?.makeKeyAndOrderFront(nil)
                        updateManager.openSoftwareUpdateCenter()
                    }
                    .liquidGlassButton(prominent: true)
                }
            }
        }
        .formStyle(.grouped)
    }
}
