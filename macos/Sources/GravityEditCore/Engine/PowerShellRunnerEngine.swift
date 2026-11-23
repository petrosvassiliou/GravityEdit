import Foundation

public final class ScriptRunCancellationToken: @unchecked Sendable {
    private let lock = NSLock()
    private var _isCancelled = false
    private var _process: Process?

    public init() {}

    public var isCancelled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _isCancelled
    }

    public func attach(process: Process) {
        lock.lock()
        defer { lock.unlock() }
        if _isCancelled {
            process.terminate()
        } else {
            _process = process
        }
    }

    public func cancel() {
        lock.lock()
        _isCancelled = true
        let proc = _process
        lock.unlock()
        if let proc, proc.isRunning {
            proc.terminate()
        }
    }
}

public enum PowerShellRunnerEngine {

    // MARK: - Executable Discovery

    public static func detectPwshExecutablePath() -> String? {
        let fm = FileManager.default
        let candidates: [String] = [
            "/opt/homebrew/bin/pwsh",
            "/usr/local/bin/pwsh",
            "/usr/bin/pwsh",
            "/Library/Frameworks/PowerShell.framework/Versions/Current/bin/pwsh"
        ]
        for path in candidates where fm.isExecutableFile(atPath: path) {
            return path
        }
        if let pathEnv = ProcessInfo.processInfo.environment["PATH"] {
            for dir in pathEnv.split(separator: ":") {
                let candidate = "\(dir)/pwsh"
                if fm.isExecutableFile(atPath: candidate) {
                    return candidate
                }
            }
        }
        return nil
    }

    // MARK: - Script & Selection Execution

    public static func executeScript(
        code: String,
        language: SyntaxLanguage = .powershell,
        workingDirectory: URL? = nil,
        lineNumberBaseOffset: Int = 0,
        forceEmbeddedPowerShell: Bool = false,
        cancellationToken: ScriptRunCancellationToken? = nil
    ) -> ScriptExecutionResult {
        let startNs = DispatchTime.now().uptimeNanoseconds
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return ScriptExecutionResult(
                engineDescription: "Idle",
                usedNativePwsh: false,
                exitCode: 0,
                durationMs: 0,
                lines: [ScriptOutputLine(stream: .info, text: "(No script statements to execute)")],
                rawCombinedOutput: ""
            )
        }

        switch language {
        case .powershell:
            if !forceEmbeddedPowerShell, let pwshPath = detectPwshExecutablePath() {
                return runExternalProcess(
                    executablePath: pwshPath,
                    argumentsBuilder: { tempScriptURL in
                        [
                            "-NoLogo",
                            "-NoProfile",
                            "-NonInteractive",
                            "-ExecutionPolicy", "Bypass",
                            "-File", tempScriptURL.path
                        ]
                    },
                    scriptExtension: "ps1",
                    scriptBody: "$VerbosePreference = 'Continue'\n" + code,
                    userLineAdjustment: -1 + lineNumberBaseOffset,
                    engineLabel: "pwsh (\(pwshPath))",
                    usedNativePwsh: true,
                    workingDirectory: workingDirectory,
                    startNs: startNs,
                    cancellationToken: cancellationToken
                )
            } else {
                return evaluateEmbeddedPowerShell(
                    code: code,
                    workingDirectory: workingDirectory,
                    lineNumberBaseOffset: lineNumberBaseOffset,
                    startNs: startNs
                )
            }

        case .shellBash:
            let shell = FileManager.default.isExecutableFile(atPath: "/bin/zsh") ? "/bin/zsh" : "/bin/sh"
            return runExternalProcess(
                executablePath: shell,
                argumentsBuilder: { [ $0.path ] },
                scriptExtension: "sh",
                scriptBody: code,
                userLineAdjustment: lineNumberBaseOffset,
                engineLabel: shell,
                usedNativePwsh: false,
                workingDirectory: workingDirectory,
                startNs: startNs,
                cancellationToken: cancellationToken
            )

        case .python:
            let py = ["/opt/homebrew/bin/python3", "/usr/local/bin/python3", "/usr/bin/python3"]
                .first { FileManager.default.isExecutableFile(atPath: $0) }
            if let py {
                return runExternalProcess(
                    executablePath: py,
                    argumentsBuilder: { [ $0.path ] },
                    scriptExtension: "py",
                    scriptBody: code,
                    userLineAdjustment: lineNumberBaseOffset,
                    engineLabel: "Python 3 (\(py))",
                    usedNativePwsh: false,
                    workingDirectory: workingDirectory,
                    startNs: startNs,
                    cancellationToken: cancellationToken
                )
            }
            return evaluateEmbeddedPowerShell(
                code: code,
                workingDirectory: workingDirectory,
                lineNumberBaseOffset: lineNumberBaseOffset,
                startNs: startNs
            )

        default:
            // Default worksheet evaluation uses PowerShell engine (`pwsh` if installed, embedded evaluator otherwise)
            if !forceEmbeddedPowerShell, let pwshPath = detectPwshExecutablePath() {
                return runExternalProcess(
                    executablePath: pwshPath,
                    argumentsBuilder: { tempScriptURL in
                        ["-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", tempScriptURL.path]
                    },
                    scriptExtension: "ps1",
                    scriptBody: code,
                    userLineAdjustment: lineNumberBaseOffset,
                    engineLabel: "pwsh (\(pwshPath))",
                    usedNativePwsh: true,
                    workingDirectory: workingDirectory,
                    startNs: startNs,
                    cancellationToken: cancellationToken
                )
            }
            return evaluateEmbeddedPowerShell(
                code: code,
                workingDirectory: workingDirectory,
                lineNumberBaseOffset: lineNumberBaseOffset,
                startNs: startNs
            )
        }
    }

    // MARK: - BBEdit Worksheet Inline Insertion

    /// Evaluates or formats an execution result directly below the selected expression or current line,
    /// mirroring BBEdit Shell Worksheets (`⌃Return`).
    public static func formatWorksheetInsertion(
        in documentText: String,
        selectionRange: NSRange,
        result: ScriptExecutionResult
    ) -> (updatedText: String, updatedSelection: NSRange) {
        let ns = documentText as NSString
        let safeLoc = min(max(0, selectionRange.location), ns.length)
        let safeLen = min(max(0, selectionRange.length), max(0, ns.length - safeLoc))

        let insertionOffset: Int
        if safeLen > 0 {
            let selEnd = safeLoc + safeLen
            let endLineRange = ns.lineRange(for: NSRange(location: max(0, selEnd - 1), length: 0))
            insertionOffset = NSMaxRange(endLineRange)
        } else {
            let lineRange = ns.lineRange(for: NSRange(location: safeLoc, length: 0))
            insertionOffset = NSMaxRange(lineRange)
        }

        let needsLeadingNewline = insertionOffset > 0 && ns.character(at: insertionOffset - 1) != 10
        let bodyLines = result.lines.map { line -> String in
            switch line.stream {
            case .stderr:  return "# [ERR] \(line.text)"
            case .warning: return "# [WARN] \(line.text)"
            case .verbose: return "# [VERBOSE] \(line.text)"
            case .info:    return "# \(line.text)"
            case .stdout:  return line.text
            }
        }
        let formattedOutput = bodyLines.joined(separator: "\n")
        let block = "\(needsLeadingNewline ? "\n" : "")# ── Worksheet Output (\(result.durationMs) ms • exit \(result.exitCode)) ──\n\(formattedOutput)\n# ──────────────────────────────────────────────\n"

        let updated = ns.replacingCharacters(in: NSRange(location: insertionOffset, length: 0), with: block)
        let blockUTF16Len = (block as NSString).length
        let newCursor = insertionOffset + blockUTF16Len
        return (updated, NSRange(location: newCursor, length: 0))
    }

    // MARK: - Stack Trace & Error Location Parser

    private static let pwshLocationRegex = try! NSRegularExpression(
        pattern: #"(?:At\s+.+?:(\d+)\s+char:(\d+)|:(\d+)\s+char:(\d+))"#,
        options: [.caseInsensitive]
    )
    private static let colonLineColRegex = try! NSRegularExpression(
        pattern: #"(?:^|\s)[^\s:]+:(\d+):(\d+):"#
    )
    private static let pythonLineRegex = try! NSRegularExpression(
        pattern: #"line\s+(\d+)"#,
        options: [.caseInsensitive]
    )

    public static func parseExecutionLocation(
        from text: String,
        tempScriptPath: String? = nil,
        userLineAdjustment: Int = 0
    ) -> ScriptExecutionLocation? {
        let ns = text as NSString
        let full = NSRange(location: 0, length: ns.length)

        if let m = pwshLocationRegex.firstMatch(in: text, options: [], range: full) {
            let lineRange = m.range(at: 1).location != NSNotFound ? m.range(at: 1) : m.range(at: 3)
            let colRange = m.range(at: 2).location != NSNotFound ? m.range(at: 2) : m.range(at: 4)
            if lineRange.location != NSNotFound,
               let rawLine = Int(ns.substring(with: lineRange)) {
                let col = (colRange.location != NSNotFound ? Int(ns.substring(with: colRange)) : nil) ?? 1
                return ScriptExecutionLocation(
                    lineNumber: max(1, rawLine + userLineAdjustment),
                    columnNumber: max(1, col)
                )
            }
        }

        if let m = colonLineColRegex.firstMatch(in: text, options: [], range: full),
           let rawLine = Int(ns.substring(with: m.range(at: 1))),
           let rawCol = Int(ns.substring(with: m.range(at: 2))) {
            return ScriptExecutionLocation(
                lineNumber: max(1, rawLine + userLineAdjustment),
                columnNumber: max(1, rawCol)
            )
        }

        if let m = pythonLineRegex.firstMatch(in: text, options: [], range: full),
           let rawLine = Int(ns.substring(with: m.range(at: 1))) {
            return ScriptExecutionLocation(
                lineNumber: max(1, rawLine + userLineAdjustment),
                columnNumber: 1
            )
        }

        return nil
    }

    // MARK: - Native Subprocess Execution

    private static func runExternalProcess(
        executablePath: String,
        argumentsBuilder: (URL) -> [String],
        scriptExtension: String,
        scriptBody: String,
        userLineAdjustment: Int,
        engineLabel: String,
        usedNativePwsh: Bool,
        workingDirectory: URL?,
        startNs: UInt64,
        cancellationToken: ScriptRunCancellationToken?
    ) -> ScriptExecutionResult {
        let fm = FileManager.default
        let tempURL = fm.temporaryDirectory.appendingPathComponent("gravity_run_\(UUID().uuidString).\(scriptExtension)")
        defer { try? fm.removeItem(at: tempURL) }

        do {
            try Data(scriptBody.utf8).write(to: tempURL, options: .atomic)
        } catch {
            return ScriptExecutionResult(
                engineDescription: engineLabel,
                usedNativePwsh: usedNativePwsh,
                exitCode: 1,
                durationMs: 0,
                lines: [ScriptOutputLine(stream: .stderr, text: "Failed to stage temporary script: \(error.localizedDescription)")],
                rawCombinedOutput: error.localizedDescription
            )
        }

        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: executablePath)
        proc.arguments = argumentsBuilder(tempURL)
        if let wd = workingDirectory, fm.fileExists(atPath: wd.path) {
            proc.currentDirectoryURL = wd
        }

        let stdoutPipe = Pipe()
        let stderrPipe = Pipe()
        proc.standardOutput = stdoutPipe
        proc.standardError = stderrPipe

        cancellationToken?.attach(process: proc)

        do {
            try proc.run()
        } catch {
            return ScriptExecutionResult(
                engineDescription: engineLabel,
                usedNativePwsh: usedNativePwsh,
                exitCode: 127,
                durationMs: 0,
                lines: [ScriptOutputLine(stream: .stderr, text: "Failed to launch \(executablePath): \(error.localizedDescription)")],
                rawCombinedOutput: error.localizedDescription
            )
        }

        let outData = stdoutPipe.fileHandleForReading.readDataToEndOfFile()
        let errData = stderrPipe.fileHandleForReading.readDataToEndOfFile()
        proc.waitUntilExit()

        let elapsedMs = Int64((DispatchTime.now().uptimeNanoseconds - startNs) / 1_000_000)
        let stdoutText = LineEnding.normalizeToLf(String(decoding: outData, as: UTF8.self))
        let stderrText = LineEnding.normalizeToLf(String(decoding: errData, as: UTF8.self))

        var structured: [ScriptOutputLine] = []
        if !stdoutText.isEmpty {
            for rawLine in stdoutText.components(separatedBy: "\n") {
                if rawLine.isEmpty && rawLine == stdoutText.components(separatedBy: "\n").last { continue }
                let trimmedUpper = rawLine.trimmingCharacters(in: .whitespaces).uppercased()
                let kind: ScriptOutputStreamKind
                if trimmedUpper.hasPrefix("WARNING:") {
                    kind = .warning
                } else if trimmedUpper.hasPrefix("VERBOSE:") {
                    kind = .verbose
                } else {
                    kind = .stdout
                }
                let loc = parseExecutionLocation(from: rawLine, tempScriptPath: tempURL.path, userLineAdjustment: userLineAdjustment)
                structured.append(ScriptOutputLine(stream: kind, text: rawLine, location: loc))
            }
        }

        if !stderrText.isEmpty {
            var activeLocation: ScriptExecutionLocation? = nil
            for rawLine in stderrText.components(separatedBy: "\n") {
                if rawLine.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { continue }
                if let detected = parseExecutionLocation(from: rawLine, tempScriptPath: tempURL.path, userLineAdjustment: userLineAdjustment) {
                    activeLocation = detected
                }
                let displayLine = rawLine.replacingOccurrences(of: tempURL.path, with: "<script>")
                structured.append(ScriptOutputLine(stream: .stderr, text: displayLine, location: activeLocation))
            }
            // Back-propagate discovered location onto preceding error summary line if needed
            if let loc = activeLocation {
                for idx in structured.indices where structured[idx].stream == .stderr && structured[idx].location == nil {
                    structured[idx] = ScriptOutputLine(
                        id: structured[idx].id,
                        stream: .stderr,
                        text: structured[idx].text,
                        location: loc
                    )
                }
            }
        }

        if cancellationToken?.isCancelled == true {
            structured.append(ScriptOutputLine(stream: .warning, text: "Execution cancelled by user."))
        } else if structured.isEmpty {
            structured.append(ScriptOutputLine(stream: .info, text: "(Completed with exit code \(proc.terminationStatus) and no console output)"))
        }

        let combined = [stdoutText, stderrText]
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .joined(separator: "\n")

        return ScriptExecutionResult(
            engineDescription: engineLabel,
            usedNativePwsh: usedNativePwsh,
            exitCode: proc.terminationStatus,
            durationMs: elapsedMs,
            lines: structured,
            rawCombinedOutput: combined
        )
    }

    // MARK: - Built-in Deterministic PowerShell Worksheet Evaluator (Zero-Dependency Fallback)

    public static func evaluateEmbeddedPowerShell(
        code: String,
        workingDirectory: URL? = nil,
        lineNumberBaseOffset: Int = 0,
        startNs: UInt64 = DispatchTime.now().uptimeNanoseconds
    ) -> ScriptExecutionResult {
        var variables: [String: Any] = [
            "PSScriptRoot": workingDirectory?.path ?? FileManager.default.currentDirectoryPath,
            "PWD": workingDirectory?.path ?? FileManager.default.currentDirectoryPath,
            "HOME": NSHomeDirectory(),
            "true": true,
            "false": false
        ]
        var outputLines: [ScriptOutputLine] = []
        var exitCode: Int32 = 0

        let normalized = LineEnding.normalizeToLf(code)
        let rawLines = normalized.components(separatedBy: "\n")
        var idx = 0
        var inBlockComment = false

        while idx < rawLines.count {
            let lineNum = idx + 1 + lineNumberBaseOffset
            let raw = rawLines[idx]
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            idx += 1

            if inBlockComment {
                if trimmed.contains("#>") { inBlockComment = false }
                continue
            }
            if trimmed.hasPrefix("<#") {
                if !trimmed.contains("#>") { inBlockComment = true }
                continue
            }
            if trimmed.isEmpty || trimmed.hasPrefix("#") {
                continue
            }

            // Skip function/class declaration blocks in lightweight worksheet fallback while recording definition
            if trimmed.lowercased().hasPrefix("function ") || trimmed.lowercased().hasPrefix("filter ") || trimmed.lowercased().hasPrefix("class ") {
                var braceDepth = raw.filter({ $0 == "{" }).count - raw.filter({ $0 == "}" }).count
                while idx < rawLines.count && braceDepth > 0 {
                    let next = rawLines[idx]
                    braceDepth += next.filter({ $0 == "{" }).count - next.filter({ $0 == "}" }).count
                    idx += 1
                }
                continue
            }

            // Multi-line hashtable or array literal accumulation (`@{ ... }` or `@( ... )`)
            var statement = trimmed
            var openBraces = statement.filter({ $0 == "{" || $0 == "(" }).count - statement.filter({ $0 == "}" || $0 == ")" }).count
            while openBraces > 0 && idx < rawLines.count {
                let nextLine = rawLines[idx].trimmingCharacters(in: .whitespaces)
                statement += "\n" + nextLine
                openBraces += nextLine.filter({ $0 == "{" || $0 == "(" }).count - nextLine.filter({ $0 == "}" || $0 == ")" }).count
                idx += 1
            }

            // Variable assignment: `$varName = <expr>`
            if let assignMatch = matchVariableAssignment(statement) {
                let val = evaluateExpression(assignMatch.rhs, variables: variables, workingDirectory: workingDirectory)
                variables[assignMatch.name] = val
                continue
            }

            // Write-Error / throw
            if statement.lowercased().hasPrefix("write-error ") || statement.lowercased().hasPrefix("throw ") {
                let msgExpr = statement.drop(while: { !$0.isWhitespace }).trimmingCharacters(in: .whitespaces)
                    .replacingOccurrences(of: "-Message ", with: "", options: [.caseInsensitive])
                let msg = stringifyValue(evaluateExpression(msgExpr, variables: variables, workingDirectory: workingDirectory), variables: variables)
                let loc = ScriptExecutionLocation(lineNumber: lineNum, columnNumber: 1)
                outputLines.append(ScriptOutputLine(stream: .stderr, text: "Write-Error: \(msg) (At <script>:\(lineNum) char:1)", location: loc))
                exitCode = 1
                continue
            }

            // Write-Warning
            if statement.lowercased().hasPrefix("write-warning ") {
                let msgExpr = String(statement.dropFirst("write-warning ".count)).trimmingCharacters(in: .whitespaces)
                let msg = stringifyValue(evaluateExpression(msgExpr, variables: variables, workingDirectory: workingDirectory), variables: variables)
                outputLines.append(ScriptOutputLine(stream: .warning, text: "WARNING: \(msg)", location: ScriptExecutionLocation(lineNumber: lineNum, columnNumber: 1)))
                continue
            }

            // Write-Verbose
            if statement.lowercased().hasPrefix("write-verbose ") {
                let msgExpr = String(statement.dropFirst("write-verbose ".count)).trimmingCharacters(in: .whitespaces)
                let msg = stringifyValue(evaluateExpression(msgExpr, variables: variables, workingDirectory: workingDirectory), variables: variables)
                outputLines.append(ScriptOutputLine(stream: .verbose, text: "VERBOSE: \(msg)"))
                continue
            }

            // Write-Host / Write-Output
            if statement.lowercased().hasPrefix("write-host ") || statement.lowercased().hasPrefix("write-output ") {
                let prefixLen = statement.lowercased().hasPrefix("write-host ") ? "write-host ".count : "write-output ".count
                let msgExpr = String(statement.dropFirst(prefixLen)).trimmingCharacters(in: .whitespaces)
                let msg = stringifyValue(evaluateExpression(msgExpr, variables: variables, workingDirectory: workingDirectory), variables: variables)
                for l in msg.components(separatedBy: "\n") {
                    outputLines.append(ScriptOutputLine(stream: .stdout, text: l))
                }
                continue
            }

            // Pipeline or standalone expression (`$psInfo | ConvertTo-Json`, `Get-Date`, `Get-ChildItem`, arithmetic, etc.)
            let evaluated = evaluatePipeline(statement, variables: variables, workingDirectory: workingDirectory)
            let rendered = stringifyValue(evaluated, variables: variables)
            if !rendered.isEmpty {
                for l in rendered.components(separatedBy: "\n") {
                    outputLines.append(ScriptOutputLine(stream: .stdout, text: l))
                }
            }
        }

        if outputLines.isEmpty {
            outputLines.append(
                ScriptOutputLine(
                    stream: .info,
                    text: "Evaluated \(rawLines.count) line(s) in built-in PowerShell Worksheet engine (install `pwsh` via Homebrew for full .NET runtime)."
                )
            )
        }

        let elapsedMs = max(1, Int64((DispatchTime.now().uptimeNanoseconds - startNs) / 1_000_000))
        let combined = outputLines.map(\.text).joined(separator: "\n")
        return ScriptExecutionResult(
            engineDescription: "Built-in PowerShell Worksheet Evaluator",
            usedNativePwsh: false,
            exitCode: exitCode,
            durationMs: elapsedMs,
            lines: outputLines,
            rawCombinedOutput: combined
        )
    }

    private static func matchVariableAssignment(_ stmt: String) -> (name: String, rhs: String)? {
        guard stmt.hasPrefix("$") else { return nil }
        guard let eqRange = stmt.range(of: "=") else { return nil }
        let lhs = stmt[..<eqRange.lowerBound].trimmingCharacters(in: .whitespaces)
        // Ensure it's not `-eq` or `==`
        let afterEq = stmt[eqRange.upperBound...]
        if afterEq.hasPrefix("=") { return nil }
        let cleanName = lhs.dropFirst().replacingOccurrences(of: "script:", with: "", options: .caseInsensitive)
            .replacingOccurrences(of: "global:", with: "", options: .caseInsensitive)
            .trimmingCharacters(in: .whitespaces)
        guard !cleanName.isEmpty, cleanName.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "_" }) else {
            return nil
        }
        return (cleanName, String(afterEq).trimmingCharacters(in: .whitespaces))
    }

    private static func evaluatePipeline(_ stmt: String, variables: [String: Any], workingDirectory: URL?) -> Any {
        let stages = splitPipelineStages(stmt)
        guard let first = stages.first else { return "" }
        var current: Any = evaluateExpression(first, variables: variables, workingDirectory: workingDirectory)

        for stage in stages.dropFirst() {
            let s = stage.trimmingCharacters(in: .whitespaces)
            let lower = s.lowercased()
            if lower.hasPrefix("convertto-json") {
                if let jsonObj = toJSONCompatible(current),
                   let data = try? JSONSerialization.data(withJSONObject: jsonObj, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]),
                   let str = String(data: data, encoding: .utf8) {
                    current = str
                }
            } else if lower.hasPrefix("measure-object") {
                let arr = (current as? [Any]) ?? [current]
                current = ["Count": arr.count]
            } else if lower.hasPrefix("select-object") {
                if lower.contains("-first "),
                   let nStr = s.components(separatedBy: .whitespaces).last,
                   let n = Int(nStr),
                   let arr = current as? [Any] {
                    current = Array(arr.prefix(max(0, n)))
                }
            }
        }
        return current
    }

    private static func splitPipelineStages(_ text: String) -> [String] {
        var stages: [String] = []
        var buf = ""
        var inSingle = false
        var inDouble = false
        var depth = 0
        for ch in text {
            if ch == "'" && !inDouble { inSingle.toggle(); buf.append(ch) }
            else if ch == "\"" && !inSingle { inDouble.toggle(); buf.append(ch) }
            else if !inSingle && !inDouble {
                if ch == "{" || ch == "(" { depth += 1; buf.append(ch) }
                else if ch == "}" || ch == ")" { depth = max(0, depth - 1); buf.append(ch) }
                else if ch == "|" && depth == 0 {
                    stages.append(buf.trimmingCharacters(in: .whitespaces))
                    buf = ""
                } else {
                    buf.append(ch)
                }
            } else {
                buf.append(ch)
            }
        }
        if !buf.trimmingCharacters(in: .whitespaces).isEmpty {
            stages.append(buf.trimmingCharacters(in: .whitespaces))
        }
        return stages
    }

    private static func evaluateExpression(_ expr: String, variables: [String: Any], workingDirectory: URL?) -> Any {
        let t = expr.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return "" }
        let lower = t.lowercased()

        // Single-quoted literal
        if t.hasPrefix("'") && t.hasSuffix("'") && t.count >= 2 {
            return String(t.dropFirst().dropLast()).replacingOccurrences(of: "''", with: "'")
        }
        // Double-quoted expandable string
        if t.hasPrefix("\"") && t.hasSuffix("\"") && t.count >= 2 {
            let inner = String(t.dropFirst().dropLast())
            return interpolateString(inner, variables: variables)
        }
        // Numbers
        if let intVal = Int(t) { return intVal }
        if let dblVal = Double(t) { return dblVal }

        // Simple arithmetic `a + b`, `a * b`
        for op in ["+", "-", "*", "/"] {
            if let r = t.range(of: " \(op) ") {
                let lhs = evaluateExpression(String(t[..<r.lowerBound]), variables: variables, workingDirectory: workingDirectory)
                let rhs = evaluateExpression(String(t[r.upperBound...]), variables: variables, workingDirectory: workingDirectory)
                if let lNum = toDouble(lhs), let rNum = toDouble(rhs) {
                    let val: Double
                    switch op {
                    case "+": val = lNum + rNum
                    case "-": val = lNum - rNum
                    case "*": val = lNum * rNum
                    default:  val = rNum != 0 ? lNum / rNum : 0
                    }
                    return val.rounded() == val ? Int(val) : val
                }
            }
        }

        // Built-in Cmdlets: Get-Date, $PSVersionTable, Test-Path, Get-ChildItem
        if lower == "get-date" || lower.hasPrefix("(get-date)") {
            return ISO8601DateFormatter().string(from: Date())
        }
        if lower == "$psversiontable" {
            return [
                "PSEdition": "GravityEdit Embedded Worksheet",
                "PSVersion": "7.4-compatible",
                "OS": ProcessInfo.processInfo.operatingSystemVersionString
            ]
        }
        if lower.hasPrefix("test-path ") {
            let rawArg = String(t.dropFirst("test-path ".count))
                .replacingOccurrences(of: "-Path ", with: "", options: .caseInsensitive)
                .trimmingCharacters(in: CharacterSet(charactersIn: " '\""))
            let expanded = interpolateString(rawArg, variables: variables)
            return FileManager.default.fileExists(atPath: expanded)
        }
        if lower == "get-childitem" || lower.hasPrefix("get-childitem ") {
            let dir = workingDirectory ?? URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
            let items = (try? FileManager.default.contentsOfDirectory(atPath: dir.path))?.sorted() ?? []
            return items
        }

        // Hashtable literal `@{ ... }`, `[ordered]@{ ... }`, `[pscustomobject]@{ ... }`
        if let braceStart = t.range(of: "@{"), t.hasSuffix("}") {
            let inner = String(t[braceStart.upperBound..<t.index(before: t.endIndex)])
            var dict: [String: Any] = [:]
            let entries = inner.components(separatedBy: CharacterSet(charactersIn: ";\n"))
            for entry in entries {
                let e = entry.trimmingCharacters(in: .whitespaces)
                guard !e.isEmpty, !e.hasPrefix("#"), let eq = e.range(of: "=") else { continue }
                let k = e[..<eq.lowerBound].trimmingCharacters(in: CharacterSet(charactersIn: " '\""))
                let vRaw = String(e[eq.upperBound...]).trimmingCharacters(in: .whitespaces)
                dict[k] = evaluateExpression(vRaw, variables: variables, workingDirectory: workingDirectory)
            }
            return dict
        }

        // Array literal `@( ... )`
        if t.hasPrefix("@(") && t.hasSuffix(")") {
            let inner = String(t.dropFirst(2).dropLast())
            let items = inner.components(separatedBy: CharacterSet(charactersIn: ",;\n"))
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
            return items.map { evaluateExpression($0, variables: variables, workingDirectory: workingDirectory) }
        }

        // Variable lookup `$foo` or `$env:USER`
        if t.hasPrefix("$") {
            let varToken = String(t.dropFirst())
            if varToken.lowercased().hasPrefix("env:") {
                let envKey = String(varToken.dropFirst(4))
                return ProcessInfo.processInfo.environment[envKey] ?? ""
            }
            if let val = variables[varToken] {
                return val
            }
        }

        return interpolateString(t, variables: variables)
    }

    private static func interpolateString(_ raw: String, variables: [String: Any]) -> String {
        var result = raw
        for (key, val) in variables.sorted(by: { $0.key.count > $1.key.count }) {
            result = result.replacingOccurrences(of: "$(\(key))", with: stringifyValue(val, variables: variables))
            result = result.replacingOccurrences(of: "$\(key)", with: stringifyValue(val, variables: variables))
        }
        return result
    }

    private static func stringifyValue(_ value: Any, variables: [String: Any]) -> String {
        if let str = value as? String { return str }
        if let b = value as? Bool { return b ? "True" : "False" }
        if let i = value as? Int { return "\(i)" }
        if let d = value as? Double { return "\(d)" }
        if let arr = value as? [Any] {
            return arr.map { stringifyValue($0, variables: variables) }.joined(separator: "\n")
        }
        if let dict = value as? [String: Any] {
            let maxKey = dict.keys.map(\.count).max() ?? 4
            return dict.keys.sorted().map { k in
                let pad = String(repeating: " ", count: max(0, maxKey - k.count))
                return "\(k)\(pad) : \(stringifyValue(dict[k] ?? "", variables: variables))"
            }.joined(separator: "\n")
        }
        return "\(value)"
    }

    private static func toJSONCompatible(_ value: Any) -> Any? {
        if value is String || value is Int || value is Double || value is Bool { return value }
        if let arr = value as? [Any] { return arr.compactMap { toJSONCompatible($0) } }
        if let dict = value as? [String: Any] {
            var clean: [String: Any] = [:]
            for (k, v) in dict {
                clean[k] = toJSONCompatible(v) ?? "\(v)"
            }
            return clean
        }
        return nil
    }

    private static func toDouble(_ value: Any) -> Double? {
        if let i = value as? Int { return Double(i) }
        if let d = value as? Double { return d }
        if let s = value as? String { return Double(s) }
        return nil
    }
}
