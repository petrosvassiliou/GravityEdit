import AppKit
import Darwin
import Foundation
import GravityEditCore

private func printUsage() {
    let usage = """
    gravity — GravityEdit Command-Line Workbench Helper (v\(MacAutoUpdateEngine.currentAppVersion))

    USAGE:
      gravity <file>[:line]           Open file (or mount folder) and jump to optional 1-based line
      gravity +<line> <file>          Open file and jump to 1-based line
      gravity --diff <left> <right>   Compare two files side-by-side in Myers Diff
      <command> | gravity [-t Title]  Pipe standard input directly into a new GravityEdit buffer

    OPTIONS:
      -d, --diff <left> <right>       Open both files in Side-by-Side Diff
      -t, --title <name>              Custom buffer name when piping from stdin
      -v, --version                   Show GravityEdit CLI version
      -h, --help                      Show this help message
    """
    print(usage)
}

let rawArgs = Array(CommandLine.arguments.dropFirst())
let stdinIsPiped = (isatty(STDIN_FILENO) == 0)

var stagedStdinFilePath: String? = nil
if stdinIsPiped && !rawArgs.contains("-h") && !rawArgs.contains("--help") && !rawArgs.contains("-v") && !rawArgs.contains("--version") {
    let pipedData = FileHandle.standardInput.readDataToEndOfFile()
    if !pipedData.isEmpty {
        let fm = FileManager.default
        let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first ?? fm.temporaryDirectory
        let dir = appSupport
            .appendingPathComponent("GravityEdit", isDirectory: true)
            .appendingPathComponent(GravityCLICommandPayload.payloadQueueDirectoryName, isDirectory: true)
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        let tempStdinURL = dir.appendingPathComponent("stdin_\(UUID().uuidString).pipe")
        try? pipedData.write(to: tempStdinURL, options: .atomic)
        stagedStdinFilePath = tempStdinURL.path
    }
}

let action = GravityCLIParser.parse(
    arguments: rawArgs,
    currentDirectoryPath: FileManager.default.currentDirectoryPath,
    stdinTempFilePath: stagedStdinFilePath
)

switch action {
case .showHelp:
    printUsage()
    exit(0)

case .showVersion:
    print("gravity (GravityEdit for macOS) v\(MacAutoUpdateEngine.currentAppVersion)")
    exit(0)

case .openHandoff(let payload):
    let fm = FileManager.default
    let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first ?? fm.temporaryDirectory
    let queueDir = appSupport
        .appendingPathComponent("GravityEdit", isDirectory: true)
        .appendingPathComponent(GravityCLICommandPayload.payloadQueueDirectoryName, isDirectory: true)
    try? fm.createDirectory(at: queueDir, withIntermediateDirectories: true)

    let hasWork = !payload.filesToOpen.isEmpty
        || payload.diffLeftPath != nil
        || payload.stdinTempFilePath != nil

    if hasWork {
        let payloadURL = queueDir.appendingPathComponent("cmd_\(Int(Date().timeIntervalSince1970 * 1000))_\(UUID().uuidString.prefix(8)).json")
        if let encoded = try? JSONEncoder().encode(payload) {
            try? encoded.write(to: payloadURL, options: .atomic)
        }
    }

    // Check if GravityEdit is already running; if so, post instant IPC wakeup, otherwise launch sibling .app bundle!
    let runningApps = NSRunningApplication.runningApplications(withBundleIdentifier: "com.gravityedit.mac")
    if let running = runningApps.first {
        DistributedNotificationCenter.default().postNotificationName(
            Notification.Name(GravityCLICommandPayload.distributedNotificationName),
            object: nil,
            userInfo: nil,
            deliverImmediately: true
        )
        running.activate(options: [.activateAllWindows])
    } else {
        // Resolve sibling GravityEdit.app (when invoked via symlink into Contents/MacOS/gravity) or /Applications/GravityEdit.app
        let resolvedExec = URL(fileURLWithPath: CommandLine.arguments[0]).resolvingSymlinksInPath()
        let candidateBundle = resolvedExec
            .deletingLastPathComponent() // MacOS
            .deletingLastPathComponent() // Contents
            .deletingLastPathComponent() // GravityEdit.app

        let appURL: URL?
        if candidateBundle.pathExtension == "app" && fm.fileExists(atPath: candidateBundle.path) {
            appURL = candidateBundle
        } else if fm.fileExists(atPath: "/Applications/GravityEdit.app") {
            appURL = URL(fileURLWithPath: "/Applications/GravityEdit.app")
        } else {
            appURL = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.gravityedit.mac")
        }

        if let appURL {
            let config = NSWorkspace.OpenConfiguration()
            config.activates = true
            let sem = DispatchSemaphore(value: 0)
            NSWorkspace.shared.openApplication(at: appURL, configuration: config) { _, _ in
                sem.signal()
            }
            _ = sem.wait(timeout: .now() + 4.0)
        } else {
            fputs("gravity: Could not locate GravityEdit.app in /Applications.\n", stderr)
            exit(1)
        }
    }
}
