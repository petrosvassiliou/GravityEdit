import Foundation

public enum GitWorkbenchError: LocalizedError, Equatable {
    case gitBinaryNotFound
    case notAGitRepository
    case commandFailed(String)

    public var errorDescription: String? {
        switch self {
        case .gitBinaryNotFound:
            return "Git executable was not found on this Mac."
        case .notAGitRepository:
            return "The active file is not inside a Git repository."
        case .commandFailed(let msg):
            return msg.isEmpty ? "Git command failed." : msg
        }
    }
}

public enum GitWorkbenchEngine {

    public static func detectGitExecutablePath() -> String? {
        let fm = FileManager.default
        let candidates = [
            "/usr/bin/git",
            "/opt/homebrew/bin/git",
            "/usr/local/bin/git"
        ]
        for path in candidates where fm.isExecutableFile(atPath: path) {
            return path
        }
        return nil
    }

    public static func detectRepositoryStatus(for fileOrDirectoryURL: URL) -> GitRepoStatusSummary? {
        guard let gitPath = detectGitExecutablePath() else { return nil }
        let resolvedURL = fileOrDirectoryURL.resolvingSymlinksInPath()
        var isDir: ObjCBool = false
        let exists = FileManager.default.fileExists(atPath: resolvedURL.path, isDirectory: &isDir)
        let workDir = (exists && isDir.boolValue) ? resolvedURL : resolvedURL.deletingLastPathComponent()
        guard FileManager.default.fileExists(atPath: workDir.path) else { return nil }

        guard let topOut = runGit(gitPath: gitPath, arguments: ["rev-parse", "--show-toplevel"], workingDirectory: workDir),
              topOut.exitCode == 0 else {
            return nil
        }
        let rawRoot = topOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rawRoot.isEmpty else { return nil }
        let repoRootURL = URL(fileURLWithPath: rawRoot).resolvingSymlinksInPath()

        var branch = "HEAD"
        if let branchOut = runGit(gitPath: gitPath, arguments: ["branch", "--show-current"], workingDirectory: repoRootURL),
           branchOut.exitCode == 0 {
            let trimmed = branchOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                branch = trimmed
            } else if let revOut = runGit(gitPath: gitPath, arguments: ["rev-parse", "--short", "HEAD"], workingDirectory: repoRootURL),
                      revOut.exitCode == 0 {
                let shortSha = revOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)
                if !shortSha.isEmpty { branch = "detached@\(shortSha)" }
            }
        }

        var modifiedCount = 0
        var isTracked = false
        var isStaged = false
        var hasUnstaged = false

        let relFilePath: String? = (!isDir.boolValue) ? relativePath(of: resolvedURL, under: repoRootURL) : nil

        if let statusOut = runGit(gitPath: gitPath, arguments: ["status", "--porcelain=v1"], workingDirectory: repoRootURL),
           statusOut.exitCode == 0 {
            for rawLine in statusOut.stdout.components(separatedBy: .newlines) where rawLine.count >= 3 {
                modifiedCount += 1
                let chars = Array(rawLine)
                let indexStatus = chars[0]
                let workTreeStatus = chars[1]
                var entryPath = String(chars.dropFirst(3))
                if let arrowRange = entryPath.range(of: " -> ") {
                    entryPath = String(entryPath[arrowRange.upperBound...])
                }
                entryPath = entryPath.trimmingCharacters(in: CharacterSet(charactersIn: "\""))
                if let rel = relFilePath, entryPath == rel {
                    if indexStatus == "?" && workTreeStatus == "?" {
                        isTracked = false
                        hasUnstaged = true
                    } else {
                        isTracked = true
                        if indexStatus != " " && indexStatus != "?" {
                            isStaged = true
                        }
                        if workTreeStatus != " " {
                            hasUnstaged = true
                        }
                    }
                }
            }
        }

        if let rel = relFilePath, !isTracked, exists {
            if let lsOut = runGit(gitPath: gitPath, arguments: ["ls-files", "--error-unmatch", "--", rel], workingDirectory: repoRootURL),
               lsOut.exitCode == 0 {
                isTracked = true
            }
        }

        return GitRepoStatusSummary(
            repoRootPath: repoRootURL.path,
            branchName: branch,
            modifiedFileCount: modifiedCount,
            isFileTracked: isTracked,
            isStaged: isStaged,
            hasUnstagedChanges: hasUnstaged
        )
    }

    public static func fetchHeadBaselineContent(for fileURL: URL) -> String? {
        guard let gitPath = detectGitExecutablePath() else { return nil }
        let resolvedFile = fileURL.resolvingSymlinksInPath()
        let workDir = resolvedFile.deletingLastPathComponent()
        guard FileManager.default.fileExists(atPath: workDir.path),
              let topOut = runGit(gitPath: gitPath, arguments: ["rev-parse", "--show-toplevel"], workingDirectory: workDir),
              topOut.exitCode == 0 else {
            return nil
        }
        let repoRootURL = URL(fileURLWithPath: topOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)).resolvingSymlinksInPath()
        guard let rel = relativePath(of: resolvedFile, under: repoRootURL) else { return nil }

        guard let showOut = runGitData(gitPath: gitPath, arguments: ["show", "HEAD:\(rel)"], workingDirectory: repoRootURL),
              showOut.exitCode == 0 else {
            return nil
        }
        let decoded = DocumentEncoding.decode(showOut.stdoutData)
        return LineEnding.normalizeToLf(decoded.text)
    }

    public static func stageFile(at fileURL: URL) throws {
        guard let gitPath = detectGitExecutablePath() else {
            throw GitWorkbenchError.gitBinaryNotFound
        }
        let resolvedFile = fileURL.resolvingSymlinksInPath()
        let workDir = resolvedFile.deletingLastPathComponent()
        guard let topOut = runGit(gitPath: gitPath, arguments: ["rev-parse", "--show-toplevel"], workingDirectory: workDir),
              topOut.exitCode == 0 else {
            throw GitWorkbenchError.notAGitRepository
        }
        let repoRootURL = URL(fileURLWithPath: topOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)).resolvingSymlinksInPath()
        guard let rel = relativePath(of: resolvedFile, under: repoRootURL) else {
            throw GitWorkbenchError.notAGitRepository
        }
        guard let addOut = runGit(gitPath: gitPath, arguments: ["add", "--", rel], workingDirectory: repoRootURL) else {
            throw GitWorkbenchError.commandFailed("Unable to invoke git add.")
        }
        if addOut.exitCode != 0 {
            throw GitWorkbenchError.commandFailed(addOut.stderr.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }

    public static func discardWorkingTreeChanges(at fileURL: URL) throws {
        guard let gitPath = detectGitExecutablePath() else {
            throw GitWorkbenchError.gitBinaryNotFound
        }
        let resolvedFile = fileURL.resolvingSymlinksInPath()
        let workDir = resolvedFile.deletingLastPathComponent()
        guard let topOut = runGit(gitPath: gitPath, arguments: ["rev-parse", "--show-toplevel"], workingDirectory: workDir),
              topOut.exitCode == 0 else {
            throw GitWorkbenchError.notAGitRepository
        }
        let repoRootURL = URL(fileURLWithPath: topOut.stdout.trimmingCharacters(in: .whitespacesAndNewlines)).resolvingSymlinksInPath()
        guard let rel = relativePath(of: resolvedFile, under: repoRootURL) else {
            throw GitWorkbenchError.notAGitRepository
        }
        guard let out = runGit(gitPath: gitPath, arguments: ["checkout", "HEAD", "--", rel], workingDirectory: repoRootURL) else {
            throw GitWorkbenchError.commandFailed("Unable to invoke git checkout.")
        }
        if out.exitCode != 0 {
            throw GitWorkbenchError.commandFailed(out.stderr.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }

    // MARK: - Helpers

    private static func relativePath(of fileURL: URL, under repoRootURL: URL) -> String? {
        let rootPrefix = repoRootURL.path.hasSuffix("/") ? repoRootURL.path : (repoRootURL.path + "/")
        guard fileURL.path.hasPrefix(rootPrefix) else { return nil }
        let rel = String(fileURL.path.dropFirst(rootPrefix.count))
        return rel.isEmpty ? nil : rel
    }

    private static func runGit(
        gitPath: String,
        arguments: [String],
        workingDirectory: URL
    ) -> (exitCode: Int32, stdout: String, stderr: String)? {
        guard let res = runGitData(gitPath: gitPath, arguments: arguments, workingDirectory: workingDirectory) else {
            return nil
        }
        return (
            res.exitCode,
            String(decoding: res.stdoutData, as: UTF8.self),
            String(decoding: res.stderrData, as: UTF8.self)
        )
    }

    private static func runGitData(
        gitPath: String,
        arguments: [String],
        workingDirectory: URL
    ) -> (exitCode: Int32, stdoutData: Data, stderrData: Data)? {
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: gitPath)
        proc.arguments = arguments
        proc.currentDirectoryURL = workingDirectory

        var env = ProcessInfo.processInfo.environment
        env["GIT_OPTIONAL_LOCKS"] = "0"
        env["GIT_TERMINAL_PROMPT"] = "0"
        proc.environment = env

        let outPipe = Pipe()
        let errPipe = Pipe()
        proc.standardOutput = outPipe
        proc.standardError = errPipe

        do {
            try proc.run()
        } catch {
            return nil
        }

        let outData = outPipe.fileHandleForReading.readDataToEndOfFile()
        let errData = errPipe.fileHandleForReading.readDataToEndOfFile()
        proc.waitUntilExit()
        return (proc.terminationStatus, outData, errData)
    }
}
