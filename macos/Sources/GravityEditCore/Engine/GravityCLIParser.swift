import Foundation

public enum GravityCLIParser {

    public enum ParsedCLIAction: Equatable, Sendable {
        case showHelp
        case showVersion
        case openHandoff(GravityCLICommandPayload)
    }

    /// Parses BBEdit-style command-line arguments (`gravity script.ps1:42`, `gravity +42 script.ps1`,
    /// `gravity --diff left.ps1 right.ps1`, `gravity --title Foo -`).
    public static func parse(
        arguments: [String],
        currentDirectoryPath: String,
        stdinTempFilePath: String? = nil
    ) -> ParsedCLIAction {
        var files: [String] = []
        var jumpLine: Int? = nil
        var diffLeft: String? = nil
        var diffRight: String? = nil
        var customTitle: String? = nil

        var idx = 0
        while idx < arguments.count {
            let arg = arguments[idx]
            idx += 1

            switch arg {
            case "-h", "--help":
                return .showHelp
            case "-v", "--version":
                return .showVersion
            case "-t", "--title":
                if idx < arguments.count {
                    customTitle = arguments[idx]
                    idx += 1
                }
            case "-d", "--diff":
                if idx + 1 < arguments.count {
                    diffLeft = resolvePath(arguments[idx], cwd: currentDirectoryPath)
                    diffRight = resolvePath(arguments[idx + 1], cwd: currentDirectoryPath)
                    idx += 2
                }
            case "-":
                // Explicit stdin marker; handled via stdinTempFilePath
                continue
            default:
                if arg.hasPrefix("+"), let plusLine = Int(arg.dropFirst()), plusLine > 0 {
                    jumpLine = plusLine
                    continue
                }
                let (rawFile, colonLine) = splitPathAndLineSuffix(arg)
                if let colonLine {
                    jumpLine = colonLine
                }
                if !rawFile.isEmpty {
                    files.append(resolvePath(rawFile, cwd: currentDirectoryPath))
                }
            }
        }

        let payload = GravityCLICommandPayload(
            filesToOpen: files,
            jumpToLine: jumpLine,
            diffLeftPath: diffLeft,
            diffRightPath: diffRight,
            stdinTempFilePath: stdinTempFilePath,
            stdinTitle: customTitle
        )
        return .openHandoff(payload)
    }

    public static func splitPathAndLineSuffix(_ input: String) -> (path: String, line: Int?) {
        // Match `<path>:<line>` or `<path>:<line>:<col>` where <line> is positive digits
        let parts = input.split(separator: ":", omittingEmptySubsequences: false)
        if parts.count >= 2 {
            if parts.count >= 3,
               let ln = Int(parts[parts.count - 2]),
               Int(parts[parts.count - 1]) != nil,
               ln > 0 {
                let path = parts.dropLast(2).joined(separator: ":")
                if !path.isEmpty { return (path, ln) }
            }
            if let ln = Int(parts[parts.count - 1]), ln > 0 {
                let path = parts.dropLast().joined(separator: ":")
                if !path.isEmpty { return (path, ln) }
            }
        }
        return (input, nil)
    }

    private static func resolvePath(_ raw: String, cwd: String) -> String {
        let expanded = (raw as NSString).expandingTildeInPath
        if expanded.hasPrefix("/") {
            return URL(fileURLWithPath: expanded).standardizedFileURL.path
        }
        return URL(fileURLWithPath: cwd).appendingPathComponent(expanded).standardizedFileURL.path
    }
}
