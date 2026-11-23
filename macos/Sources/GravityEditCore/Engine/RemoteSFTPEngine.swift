import Foundation

public enum RemoteSFTPError: LocalizedError, Equatable {
    case binaryNotFound
    case invalidHostOrUsername
    case connectionFailed(String)
    case remoteOperationFailed(String)

    public var errorDescription: String? {
        switch self {
        case .binaryNotFound:
            return "System /usr/bin/sftp or /usr/bin/ssh was not found."
        case .invalidHostOrUsername:
            return "Please provide both a valid hostname and username."
        case .connectionFailed(let msg):
            return msg.isEmpty ? "Unable to connect to remote SSH/SFTP server." : msg
        case .remoteOperationFailed(let msg):
            return msg.isEmpty ? "Remote SFTP file transfer failed." : msg
        }
    }
}

public enum RemoteSFTPEngine {

    public static func listRemoteDirectory(
        profile: SFTPConnectionProfile,
        directoryPath: String,
        password: String? = nil
    ) throws -> [SFTPRemoteEntry] {
        let host = profile.host.trimmingCharacters(in: .whitespacesAndNewlines)
        let user = profile.username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty, !user.isEmpty else {
            throw RemoteSFTPError.invalidHostOrUsername
        }
        guard FileManager.default.isExecutableFile(atPath: "/usr/bin/sftp") else {
            throw RemoteSFTPError.binaryNotFound
        }

        let normalizedDir = normalizeRemotePath(directoryPath.isEmpty ? profile.initialPath : directoryPath)
        let batchCommand = "ls -la \(quoteSftpPath(normalizedDir))\n"

        let result = try runSftpBatch(
            host: host,
            port: profile.port,
            username: user,
            keyFilePath: profile.keyFilePath,
            password: password,
            batchStdin: batchCommand
        )

        if result.exitCode != 0 {
            throw RemoteSFTPError.connectionFailed(cleanSshErrorMessage(result.stderr))
        }
        return parseSftpLsOutput(result.stdout, directoryPath: normalizedDir)
    }

    public static func downloadRemoteFile(
        location: RemoteFileLocation,
        password: String? = nil
    ) throws -> (content: String, encoding: DocumentEncoding, lineEnding: LineEnding) {
        let host = location.host.trimmingCharacters(in: .whitespacesAndNewlines)
        let user = location.username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty, !user.isEmpty else {
            throw RemoteSFTPError.invalidHostOrUsername
        }
        guard FileManager.default.isExecutableFile(atPath: "/usr/bin/sftp") else {
            throw RemoteSFTPError.binaryNotFound
        }

        let fm = FileManager.default
        let localStaging = fm.temporaryDirectory.appendingPathComponent("gravity_sftp_dl_\(UUID().uuidString)")
        defer { try? fm.removeItem(at: localStaging) }

        let batch = "get \(quoteSftpPath(location.remotePath)) \(quoteSftpPath(localStaging.path))\n"
        let res = try runSftpBatch(
            host: host,
            port: location.port,
            username: user,
            keyFilePath: location.keyFilePath,
            password: password,
            batchStdin: batch
        )

        guard res.exitCode == 0, fm.fileExists(atPath: localStaging.path) else {
            throw RemoteSFTPError.remoteOperationFailed(cleanSshErrorMessage(res.stderr))
        }

        let rawData = try Data(contentsOf: localStaging)
        let decoded = DocumentEncoding.decode(rawData)
        let detectedLE = LineEnding.detect(decoded.text)
        let normalizedText = LineEnding.normalizeToLf(decoded.text)
        return (normalizedText, decoded.encoding, detectedLE)
    }

    public static func uploadRemoteFile(
        content: String,
        encoding: DocumentEncoding,
        lineEnding: LineEnding,
        to location: RemoteFileLocation,
        password: String? = nil
    ) throws {
        let host = location.host.trimmingCharacters(in: .whitespacesAndNewlines)
        let user = location.username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty, !user.isEmpty else {
            throw RemoteSFTPError.invalidHostOrUsername
        }
        guard FileManager.default.isExecutableFile(atPath: "/usr/bin/sftp") else {
            throw RemoteSFTPError.binaryNotFound
        }

        let fm = FileManager.default
        let localStaging = fm.temporaryDirectory.appendingPathComponent("gravity_sftp_ul_\(UUID().uuidString)")
        defer { try? fm.removeItem(at: localStaging) }

        let withLE = LineEnding.applyLineEnding(content, target: lineEnding)
        let payloadData = encoding.encode(withLE)
        try payloadData.write(to: localStaging, options: .atomic)

        let batch = "put \(quoteSftpPath(localStaging.path)) \(quoteSftpPath(location.remotePath))\n"
        let res = try runSftpBatch(
            host: host,
            port: location.port,
            username: user,
            keyFilePath: location.keyFilePath,
            password: password,
            batchStdin: batch
        )

        if res.exitCode != 0 {
            throw RemoteSFTPError.remoteOperationFailed(cleanSshErrorMessage(res.stderr))
        }
    }

    // MARK: - SFTP `ls -la` Output Parser

    public static func parseSftpLsOutput(_ output: String, directoryPath: String) -> [SFTPRemoteEntry] {
        let normalizedDir = normalizeRemotePath(directoryPath)
        var entries: [SFTPRemoteEntry] = []
        for rawLine in output.components(separatedBy: .newlines) {
            if let entry = parseSftpLsLine(rawLine, directoryPath: normalizedDir) {
                entries.append(entry)
            }
        }
        return entries.sorted { lhs, rhs in
            if lhs.isDirectory != rhs.isDirectory {
                return lhs.isDirectory && !rhs.isDirectory
            }
            return lhs.name.localizedStandardCompare(rhs.name) == .orderedAscending
        }
    }

    public static func parseSftpLsLine(_ rawLine: String, directoryPath: String) -> SFTPRemoteEntry? {
        let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              !trimmed.hasPrefix("sftp>"),
              !trimmed.hasPrefix("total ") else {
            return nil
        }

        // POSIX long listing format:
        // drwxr-xr-x    5 deploy   staff         160 Nov 23 18:10 scripts
        // -rw-r--r--    1 deploy   staff        4096 Nov 23 18:12 deploy.ps1
        let firstChar = trimmed.first!
        guard "dl-bcps".contains(firstChar) else { return nil }

        let tokens = trimmed.split(maxSplits: 8, omittingEmptySubsequences: true, whereSeparator: { $0.isWhitespace })
        guard tokens.count >= 9 else { return nil }

        let perms = String(tokens[0])
        guard perms.count >= 9 else { return nil }

        let size = Int64(tokens[4]) ?? 0
        let modDisplay = "\(tokens[5]) \(tokens[6]) \(tokens[7])"
        var rawName = String(tokens[8])

        // Strip symlink target display `linkname -> target`
        if firstChar == "l", let arrowRange = rawName.range(of: " -> ") {
            rawName = String(rawName[..<arrowRange.lowerBound])
        }

        let baseName = (rawName as NSString).lastPathComponent
        guard !baseName.isEmpty, baseName != ".", baseName != ".." else { return nil }

        let isDir = (firstChar == "d")
        let normalizedDir = normalizeRemotePath(directoryPath)
        let fullPath = normalizedDir == "/" ? "/\(baseName)" : "\(normalizedDir)/\(baseName)"

        return SFTPRemoteEntry(
            name: baseName,
            fullPath: fullPath,
            isDirectory: isDir,
            sizeBytes: size,
            permissions: perms,
            modifiedDisplay: modDisplay
        )
    }

    public static func normalizeRemotePath(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "/" }
        var cleaned = trimmed.replacingOccurrences(of: "//", with: "/")
        if cleaned.count > 1 && cleaned.hasSuffix("/") {
            cleaned.removeLast()
        }
        return cleaned
    }

    public static func parentRemotePath(of path: String) -> String {
        let norm = normalizeRemotePath(path)
        guard norm != "/", norm.contains("/") else { return "/" }
        let parent = (norm as NSString).deletingLastPathComponent
        return parent.isEmpty ? "/" : parent
    }

    // MARK: - Subprocess Transport

    private static func quoteSftpPath(_ path: String) -> String {
        let escaped = path
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
        return "\"\(escaped)\""
    }

    private static func cleanSshErrorMessage(_ stderr: String) -> String {
        let filtered = stderr
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("Warning: Permanently added") }
            .joined(separator: " ")
        return filtered.isEmpty ? "SFTP connection or authentication failed." : filtered
    }

    private static func runSftpBatch(
        host: String,
        port: Int,
        username: String,
        keyFilePath: String?,
        password: String?,
        batchStdin: String
    ) throws -> (exitCode: Int32, stdout: String, stderr: String) {
        let fm = FileManager.default
        let batchFile = fm.temporaryDirectory.appendingPathComponent("gravity_sftp_batch_\(UUID().uuidString).txt")
        try batchStdin.write(to: batchFile, atomically: true, encoding: .utf8)
        defer { try? fm.removeItem(at: batchFile) }

        var askPassURL: URL? = nil
        defer {
            if let askPassURL {
                try? fm.removeItem(at: askPassURL)
            }
        }

        var args: [String] = [
            "-P", "\(max(1, port))",
            "-o", "ConnectTimeout=10",
            "-o", "ServerAliveInterval=15"
        ]

        let hasSessionPassword = !(password ?? "").isEmpty
        if hasSessionPassword {
            let helper = fm.temporaryDirectory.appendingPathComponent("gravity_askpass_\(UUID().uuidString).sh")
            let script = "#!/bin/sh\nprintf '%s\\n' \"$GRAVITY_SSH_EPHEMERAL_SECRET\"\n"
            try script.write(to: helper, atomically: true, encoding: .utf8)
            try fm.setAttributes([.posixPermissions: 0o700], ofItemAtPath: helper.path)
            askPassURL = helper
            args += ["-o", "BatchMode=no", "-o", "NumberOfPasswordPrompts=1"]
        } else {
            args += ["-o", "BatchMode=yes"]
        }

        if let keyPath = keyFilePath?.trimmingCharacters(in: .whitespacesAndNewlines), !keyPath.isEmpty {
            let expandedKey = (keyPath as NSString).expandingTildeInPath
            args += ["-i", expandedKey]
        }

        args += ["-b", batchFile.path, "\(username)@\(host)"]

        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: "/usr/bin/sftp")
        proc.arguments = args

        var env = ProcessInfo.processInfo.environment
        if hasSessionPassword, let askPassURL, let password {
            env["SSH_ASKPASS"] = askPassURL.path
            env["SSH_ASKPASS_REQUIRE"] = "force"
            env["DISPLAY"] = env["DISPLAY"] ?? "gravityedit:0"
            env["GRAVITY_SSH_EPHEMERAL_SECRET"] = password
        }
        proc.environment = env

        let outPipe = Pipe()
        let errPipe = Pipe()
        proc.standardOutput = outPipe
        proc.standardError = errPipe

        try proc.run()
        let outData = outPipe.fileHandleForReading.readDataToEndOfFile()
        let errData = errPipe.fileHandleForReading.readDataToEndOfFile()
        proc.waitUntilExit()

        return (
            proc.terminationStatus,
            String(decoding: outData, as: UTF8.self),
            String(decoding: errData, as: UTF8.self)
        )
    }
}
