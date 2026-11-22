import AppKit
import Foundation
import GravityEditCore
import Sparkle
import SwiftUI

public enum UpdateLifecycleState: Equatable {
    case idle
    case checking
    case upToDate(checkedVersion: String, timestamp: Date)
    case updateAvailable(MacReleaseUpdateInfo)
    case downloading(progress: Double, statusText: String)
    case readyToRelaunch(stagedBundleURL: URL, version: String)
    case failed(message: String)
}

/// Manages seamless in-app automatic updates on macOS using BOTH:
/// 1. **Sparkle 2 (`SPUStandardUpdaterController`)** — industry-standard macOS appcast framework
///    supporting background scheduled checks, delta/zip updates, and EdDSA verification when
///    `SUPublicEDKey` is present in `Info.plist`.
/// 2. **Verified GitHub Releases In-App Self-Updater (`MacAutoUpdateEngine`)** — queries
///    `petrosvassiliou/GravityEdit` releases directly, displays Markdown release notes inside
///    GravityEdit's Liquid Glass Software Update sheet, verifies the `.sha256` digest via
///    Apple `CryptoKit`, unpacks `GravityEdit-macOS-*.app.zip` with `/usr/bin/ditto`, verifies
///    the bundle identifier (`com.gravityedit.mac`), atomically swaps the `.app` bundle, and
///    relaunches the updated app automatically!
@MainActor
public final class SparkleUpdateManager: NSObject, ObservableObject {

    @Published public var state: UpdateLifecycleState = .idle
    @Published public var isUpdateSheetPresented: Bool = false
    @Published public var latestReleaseInfo: MacReleaseUpdateInfo? = nil
    @Published public var verifiedSHA256Digest: String? = nil

    @AppStorage("gravityedit.updates.autoCheck") public var automaticallyChecksForUpdates: Bool = true {
        didSet { syncSparklePreferences() }
    }

    @AppStorage("gravityedit.updates.autoDownload") public var automaticallyDownloadsUpdates: Bool = false {
        didSet { syncSparklePreferences() }
    }

    private var sparkleController: SPUStandardUpdaterController?
    private var backgroundTimer: Timer?

    public var hasSparkleEdDSAPublicKey: Bool {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SUPublicEDKey") as? String else {
            return false
        }
        return !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    public var isRunningInsideAppBundle: Bool {
        Bundle.main.bundleURL.pathExtension.lowercased() == "app"
    }

    public override init() {
        super.init()

        // Initialize Sparkle 2 controller safely when running inside a packaged `.app` bundle
        if isRunningInsideAppBundle {
            let controller = SPUStandardUpdaterController(
                startingUpdater: hasSparkleEdDSAPublicKey,
                updaterDelegate: nil,
                userDriverDelegate: nil
            )
            self.sparkleController = controller
            syncSparklePreferences()
        }

        // Schedule automatic startup & periodic background update checks
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard let self, self.automaticallyChecksForUpdates else { return }
            await self.checkForUpdates(userInitiated: false)
        }

        backgroundTimer = Timer.scheduledTimer(withTimeInterval: 6 * 3600, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.automaticallyChecksForUpdates else { return }
                await self.checkForUpdates(userInitiated: false)
            }
        }
    }

    private func syncSparklePreferences() {
        guard let updater = sparkleController?.updater, hasSparkleEdDSAPublicKey else { return }
        updater.automaticallyChecksForUpdates = automaticallyChecksForUpdates
        updater.automaticallyDownloadsUpdates = automaticallyDownloadsUpdates
    }

    public func openSoftwareUpdateCenter() {
        isUpdateSheetPresented = true
        if case .idle = state {
            Task { await checkForUpdates(userInitiated: true) }
        }
    }

    public func triggerSparkleNativeCheck() {
        if let controller = sparkleController, hasSparkleEdDSAPublicKey {
            controller.checkForUpdates(nil)
        } else {
            openSoftwareUpdateCenter()
        }
    }

    public func checkForUpdates(userInitiated: Bool) async {
        if userInitiated {
            isUpdateSheetPresented = true
        }
        state = .checking
        do {
            let release = try await MacAutoUpdateEngine.fetchLatestRelease()
            latestReleaseInfo = release
            if MacAutoUpdateEngine.isNewerVersion(
                remoteVersion: release.versionString,
                than: MacAutoUpdateEngine.currentAppVersion
            ) {
                state = .updateAvailable(release)
                if automaticallyDownloadsUpdates && !userInitiated && isRunningInsideAppBundle {
                    await downloadAndStageUpdate(release: release, autoRelaunch: false)
                } else if !userInitiated {
                    // Present unobtrusive sheet when an update is discovered on launch
                    isUpdateSheetPresented = true
                }
            } else {
                state = .upToDate(checkedVersion: MacAutoUpdateEngine.currentAppVersion, timestamp: Date())
            }
        } catch {
            if userInitiated {
                state = .failed(message: error.localizedDescription)
            } else {
                state = .idle
            }
        }
    }

    public func downloadAndInstallInApp() async {
        guard let release = latestReleaseInfo else {
            await checkForUpdates(userInitiated: true)
            return
        }

        // If Sparkle EdDSA signing is configured and appcast is live, Sparkle can also drive installation
        if let controller = sparkleController, hasSparkleEdDSAPublicKey, release.appcastAsset != nil {
            controller.checkForUpdates(nil)
            return
        }

        await downloadAndStageUpdate(release: release, autoRelaunch: true)
    }

    private func downloadAndStageUpdate(release: MacReleaseUpdateInfo, autoRelaunch: Bool) async {
        guard let zipAsset = release.appZipAsset else {
            // Fallback to opening DMG download if .app.zip is not attached
            if let dmg = release.dmgAsset {
                NSWorkspace.shared.open(dmg.downloadURL)
            } else {
                NSWorkspace.shared.open(release.htmlURL)
            }
            return
        }

        do {
            state = .downloading(progress: 0.08, statusText: "Fetching SHA-256 cryptographic manifest…")
            var expectedSHA: String? = nil
            if let shaAsset = release.sha256Asset {
                let (shaData, _) = try await URLSession.shared.data(from: shaAsset.downloadURL)
                if let shaText = String(data: shaData, encoding: .utf8) {
                    expectedSHA = MacAutoUpdateEngine.parseExpectedSHA256(from: shaText, assetFileName: zipAsset.name)
                }
            }

            state = .downloading(progress: 0.25, statusText: "Downloading \(zipAsset.name)…")
            let (tempDownloadURL, response) = try await URLSession.shared.download(from: zipAsset.downloadURL)
            if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                throw NSError(domain: "SparkleUpdateManager", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "Download failed with HTTP \(http.statusCode)"])
            }

            state = .downloading(progress: 0.72, statusText: "Verifying CryptoKit SHA-256 signature…")
            let actualSHA = try MacAutoUpdateEngine.sha256Hex(of: tempDownloadURL)
            verifiedSHA256Digest = actualSHA

            if let expected = expectedSHA, expected != actualSHA {
                throw NSError(
                    domain: "SparkleUpdateManager",
                    code: 409,
                    userInfo: [NSLocalizedDescriptionKey: "SHA-256 mismatch! Expected \(expected.prefix(12))…, got \(actualSHA.prefix(12))…"]
                )
            }

            state = .downloading(progress: 0.86, statusText: "Unpacking verified macOS application bundle…")
            let fm = FileManager.default
            let stagingRoot = fm.temporaryDirectory.appendingPathComponent("GravityEditUpdate-\(UUID().uuidString)", isDirectory: true)
            try fm.createDirectory(at: stagingRoot, withIntermediateDirectories: true)

            let ditto = Process()
            ditto.executableURL = URL(fileURLWithPath: "/usr/bin/ditto")
            ditto.arguments = ["-x", "-k", tempDownloadURL.path, stagingRoot.path]
            try ditto.run()
            ditto.waitUntilExit()
            guard ditto.terminationStatus == 0 else {
                throw NSError(domain: "SparkleUpdateManager", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to unpack .app.zip archive"])
            }

            let contents = try fm.contentsOfDirectory(at: stagingRoot, includingPropertiesForKeys: nil)
            guard let stagedAppURL = contents.first(where: { $0.pathExtension.lowercased() == "app" }) else {
                throw NSError(domain: "SparkleUpdateManager", code: 404, userInfo: [NSLocalizedDescriptionKey: "Archive did not contain GravityEdit.app"])
            }

            // Verify bundle identifier matches com.gravityedit.mac
            guard let stagedBundle = Bundle(url: stagedAppURL),
                  stagedBundle.bundleIdentifier == MacAutoUpdateEngine.bundleIdentifier else {
                throw NSError(domain: "SparkleUpdateManager", code: 403, userInfo: [NSLocalizedDescriptionKey: "Bundle identifier verification failed"])
            }

            state = .readyToRelaunch(stagedBundleURL: stagedAppURL, version: release.versionString)
            if autoRelaunch {
                performAtomicSwapAndRelaunch(stagedBundleURL: stagedAppURL)
            }
        } catch {
            state = .failed(message: error.localizedDescription)
        }
    }

    public var confirmBeforeQuitHandler: (() -> Bool)? = nil

    public func performAtomicSwapAndRelaunch(stagedBundleURL: URL) {
        if let confirm = confirmBeforeQuitHandler, !confirm() {
            return
        }
        let currentAppURL = Bundle.main.bundleURL
        guard currentAppURL.pathExtension.lowercased() == "app" else {
            // Running via `swift run` during local development: open the staged .app directly
            NSWorkspace.shared.open(stagedBundleURL)
            return
        }

        let pid = ProcessInfo.processInfo.processIdentifier
        let scriptContent = MacAutoUpdateEngine.buildAtomicBundleSwapScript(
            processIdentifier: pid,
            stagedAppBundleURL: stagedBundleURL,
            destinationAppBundleURL: currentAppURL
        )

        let scriptURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("gravityedit-updater-\(pid).sh")
        do {
            try scriptContent.write(to: scriptURL, atomically: true, encoding: .utf8)
            try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: scriptURL.path)

            let launcher = Process()
            launcher.executableURL = URL(fileURLWithPath: "/bin/sh")
            launcher.arguments = [scriptURL.path]
            launcher.standardOutput = FileHandle.nullDevice
            launcher.standardError = FileHandle.nullDevice
            try launcher.run()

            NSApplication.shared.terminate(nil)
        } catch {
            state = .failed(message: "Unable to launch atomic updater: \(error.localizedDescription)")
        }
    }
}
