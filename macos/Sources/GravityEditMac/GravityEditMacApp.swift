import AppKit
import GravityEditCore
import SwiftUI

@MainActor
public final class GravityEditAppDelegate: NSObject, NSApplicationDelegate {
    weak var workspaceViewModel: WorkspaceViewModel?

    public func applicationDidFinishLaunching(_ notification: Notification) {
        NSWindow.allowsAutomaticWindowTabbing = false
        NSApplication.shared.activate(ignoringOtherApps: true)
    }

    public func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls {
            var isDir: ObjCBool = false
            if FileManager.default.fileExists(atPath: url.path, isDirectory: &isDir), isDir.boolValue {
                workspaceViewModel?.mountProjectFolder(url: url)
            } else {
                workspaceViewModel?.openFile(at: url)
            }
        }
    }

    public func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard let vm = workspaceViewModel else { return .terminateNow }
        return vm.confirmQuitWithUnsavedDocuments() ? .terminateNow : .terminateCancel
    }

    public func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }
}

@main
public struct GravityEditMacApp: App {
    @NSApplicationDelegateAdaptor(GravityEditAppDelegate.self) private var appDelegate
    @StateObject private var workspaceVM = WorkspaceViewModel()
    @StateObject private var updateManager = SparkleUpdateManager()

    public init() {}

    public var body: some Scene {
        WindowGroup("GravityEdit") {
            GravityEditMainWindow(vm: workspaceVM, updateManager: updateManager)
                .frame(minWidth: 920, minHeight: 580)
                .onAppear {
                    appDelegate.workspaceViewModel = workspaceVM
                    updateManager.confirmBeforeQuitHandler = { [weak workspaceVM] in
                        workspaceVM?.confirmQuitWithUnsavedDocuments() ?? true
                    }
                }
        }
        .windowToolbarStyle(.unified(showsTitle: true))
        .defaultSize(width: 1260, height: 800)
        .commands {
            GravityEditMenuCommands(vm: workspaceVM, updateManager: updateManager)
        }

        Settings {
            SettingsSceneView(vm: workspaceVM, updateManager: updateManager)
        }
    }
}
