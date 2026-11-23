import CryptoKit
import Foundation

public struct MacReleaseAssetInfo: Equatable, Sendable {
    public let name: String
    public let downloadURL: URL
    public let sizeBytes: Int64

    public init(name: String, downloadURL: URL, sizeBytes: Int64) {
        self.name = name
        self.downloadURL = downloadURL
        self.sizeBytes = sizeBytes
    }
}

public struct MacReleaseUpdateInfo: Equatable, Sendable {
    public let tagName: String
    public let versionString: String
    public let title: String
    public let releaseNotes: String
    public let htmlURL: URL
    public let publishedAt: String
    public let appZipAsset: MacReleaseAssetInfo?
    public let dmgAsset: MacReleaseAssetInfo?
    public let sha256Asset: MacReleaseAssetInfo?
    public let appcastAsset: MacReleaseAssetInfo?

    public init(
        tagName: String,
        versionString: String,
        title: String,
        releaseNotes: String,
        htmlURL: URL,
        publishedAt: String,
        appZipAsset: MacReleaseAssetInfo?,
        dmgAsset: MacReleaseAssetInfo?,
        sha256Asset: MacReleaseAssetInfo?,
        appcastAsset: MacReleaseAssetInfo?
    ) {
        self.tagName = tagName
        self.versionString = versionString
        self.title = title
        self.releaseNotes = releaseNotes
        self.htmlURL = htmlURL
        self.publishedAt = publishedAt
        self.appZipAsset = appZipAsset
        self.dmgAsset = dmgAsset
        self.sha256Asset = sha256Asset
        self.appcastAsset = appcastAsset
    }
}

public enum MacAutoUpdateEngine {

    public static var currentAppVersion: String {
        if let ver = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
           !ver.isEmpty {
            return ver
        }
        return "1.4.0"
    }

    public static var currentBuildNumber: String {
        if let bld = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String,
           !bld.isEmpty {
            return bld
        }
        return "13"
    }
    public static let bundleIdentifier = "com.gravityedit.mac"
    public static let githubRepoSlug = "petrosvassiliou/GravityEdit"
    public static let latestReleaseAPIURL = URL(string: "https://api.github.com/repos/petrosvassiliou/GravityEdit/releases/latest")!
    public static let sparkleAppcastFeedURL = URL(string: "https://github.com/petrosvassiliou/GravityEdit/releases/latest/download/appcast.xml")!
    public static let releasesPageURL = URL(string: "https://github.com/petrosvassiliou/GravityEdit/releases")!

    // MARK: - SemVer Comparison

    public static func normalizeVersion(_ raw: String) -> [Int] {
        var cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.hasPrefix("v") || cleaned.hasPrefix("V") {
            cleaned.removeFirst()
        }
        if let dash = cleaned.firstIndex(of: "-") {
            cleaned = String(cleaned[..<dash])
        }
        return cleaned.split(separator: ".").map { Int($0) ?? 0 }
    }

    /// Returns `true` strictly when `remoteVersion` is newer than `localVersion`.
    public static func isNewerVersion(remoteVersion: String, than localVersion: String) -> Bool {
        let remote = normalizeVersion(remoteVersion)
        let local = normalizeVersion(localVersion)
        let maxLen = max(remote.count, local.count)
        guard maxLen > 0 else { return false }
        for idx in 0..<maxLen {
            let r = idx < remote.count ? remote[idx] : 0
            let l = idx < local.count ? local[idx] : 0
            if r > l { return true }
            if r < l { return false }
        }
        return false
    }

    // MARK: - GitHub Release JSON Parsing

    public static func parseGitHubReleaseJSON(_ data: Data) throws -> MacReleaseUpdateInfo {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "MacAutoUpdateEngine", code: 10, userInfo: [NSLocalizedDescriptionKey: "Invalid GitHub release payload"])
        }
        let tagName = (root["tag_name"] as? String) ?? ""
        guard !tagName.isEmpty else {
            throw NSError(domain: "MacAutoUpdateEngine", code: 11, userInfo: [NSLocalizedDescriptionKey: "Missing tag_name in release payload"])
        }
        let versionString = tagName.hasPrefix("v") ? String(tagName.dropFirst()) : tagName
        let title = (root["name"] as? String) ?? "GravityEdit \(tagName)"
        let body = (root["body"] as? String) ?? ""
        let htmlURLStr = (root["html_url"] as? String) ?? releasesPageURL.absoluteString
        let htmlURL = URL(string: htmlURLStr) ?? releasesPageURL
        let publishedAt = (root["published_at"] as? String) ?? ""

        var appZip: MacReleaseAssetInfo? = nil
        var dmg: MacReleaseAssetInfo? = nil
        var sha256: MacReleaseAssetInfo? = nil
        var appcast: MacReleaseAssetInfo? = nil

        if let assets = root["assets"] as? [[String: Any]] {
            for asset in assets {
                guard let name = asset["name"] as? String,
                      let dlStr = asset["browser_download_url"] as? String,
                      let dlURL = URL(string: dlStr) else {
                    continue
                }
                let size = (asset["size"] as? NSNumber)?.int64Value ?? 0
                let info = MacReleaseAssetInfo(name: name, downloadURL: dlURL, sizeBytes: size)
                let lower = name.lowercased()
                if lower.hasSuffix(".app.zip") {
                    appZip = info
                } else if lower.hasSuffix(".dmg") {
                    dmg = info
                } else if lower.contains("macos") && lower.hasSuffix(".app.zip.sha256") {
                    sha256 = info
                } else if sha256 == nil && lower.contains("macos") && lower.hasSuffix(".sha256") {
                    sha256 = info
                } else if lower == "appcast.xml" {
                    appcast = info
                }
            }
        }

        return MacReleaseUpdateInfo(
            tagName: tagName,
            versionString: versionString,
            title: title,
            releaseNotes: body,
            htmlURL: htmlURL,
            publishedAt: publishedAt,
            appZipAsset: appZip,
            dmgAsset: dmg,
            sha256Asset: sha256,
            appcastAsset: appcast
        )
    }

    public static func fetchLatestRelease() async throws -> MacReleaseUpdateInfo {
        var req = URLRequest(url: latestReleaseAPIURL)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue("GravityEdit-macOS/\(currentAppVersion)", forHTTPHeaderField: "User-Agent")
        req.timeoutInterval = 15

        let (data, response) = try await URLSession.shared.data(for: req)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw NSError(
                domain: "MacAutoUpdateEngine",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "GitHub Releases returned HTTP \(http.statusCode)"]
            )
        }
        return try parseGitHubReleaseJSON(data)
    }

    // MARK: - Cryptographic SHA-256 Digest Verification

    public static func sha256Hex(of fileURL: URL) throws -> String {
        let data = try Data(contentsOf: fileURL, options: [.mappedIfSafe])
        return sha256Hex(of: data)
    }

    public static func sha256Hex(of data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    public static func parseExpectedSHA256(from shaFileContent: String, assetFileName: String? = nil) -> String? {
        for rawLine in shaFileContent.components(separatedBy: .newlines) {
            let trimmed = rawLine.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else { continue }
            let parts = trimmed.split(whereSeparator: { $0.isWhitespace })
            guard let hashCandidate = parts.first, hashCandidate.count == 64,
                  hashCandidate.allSatisfy({ $0.isHexDigit }) else {
                continue
            }
            if let targetName = assetFileName, parts.count >= 2 {
                let filePart = String(parts.last!).trimmingCharacters(in: CharacterSet(charactersIn: "*"))
                if filePart == targetName {
                    return hashCandidate.lowercased()
                }
            } else {
                return hashCandidate.lowercased()
            }
        }
        return nil
    }

    // MARK: - Atomic In-App .app Bundle Swap & Relaunch Script

    /// Generates the POSIX shell script that waits for the running PID to terminate,
    /// atomically replaces the target `.app` bundle with the verified staged `.app` bundle,
    /// clears transient quarantine attributes on the updated bundle, and relaunches GravityEdit.
    public static func buildAtomicBundleSwapScript(
        processIdentifier: Int32,
        stagedAppBundleURL: URL,
        destinationAppBundleURL: URL
    ) -> String {
        func shellQuote(_ path: String) -> String {
            "'" + path.replacingOccurrences(of: "'", with: "'\\''") + "'"
        }

        let staged = shellQuote(stagedAppBundleURL.path)
        let dest = shellQuote(destinationAppBundleURL.path)
        let backup = shellQuote(destinationAppBundleURL.path + ".backup.\(processIdentifier)")

        return """
        #!/bin/sh
        set -e
        TARGET_PID=\(processIdentifier)
        STAGED_APP=\(staged)
        DEST_APP=\(dest)
        BACKUP_APP=\(backup)

        # Wait up to 30 seconds for current GravityEdit instance to exit cleanly
        ATTEMPTS=0
        while kill -0 "$TARGET_PID" 2>/dev/null && [ "$ATTEMPTS" -lt 120 ]; do
            sleep 0.25
            ATTEMPTS=$((ATTEMPTS + 1))
        done

        # If user canceled the unsaved-changes quit prompt and the app is still running, abort swap safely!
        if kill -0 "$TARGET_PID" 2>/dev/null; then
            exit 0
        fi

        if [ -d "$DEST_APP" ]; then
            rm -rf "$BACKUP_APP"
            mv "$DEST_APP" "$BACKUP_APP"
        fi

        if mv "$STAGED_APP" "$DEST_APP"; then
            rm -rf "$BACKUP_APP"
            xattr -dr com.apple.quarantine "$DEST_APP" 2>/dev/null || true
            open -n "$DEST_APP"
        else
            if [ -d "$BACKUP_APP" ]; then
                mv "$BACKUP_APP" "$DEST_APP"
            fi
            exit 1
        fi
        """
    }
}
