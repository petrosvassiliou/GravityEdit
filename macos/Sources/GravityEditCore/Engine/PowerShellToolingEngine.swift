import Foundation

public enum PowerShellToolingEngine {

    // MARK: - Show-Command Parameter Form Builder

    /// Extracts PowerShell `param(...)` blocks from both top-level script headers and `function`/`filter` declarations.
    public static func extractParameterFormSpecs(
        from scriptContent: String,
        defaultScriptName: String = "CurrentScript.ps1"
    ) -> [ParameterFormSpec] {
        let normalized = LineEnding.normalizeToLf(scriptContent)
        var specs: [ParameterFormSpec] = []
        var consumedRanges: [Range<String.Index>] = []

        // 1. Match `function <Name> { ... param(...) ... }` or `function <Name>(...) { ... }`
        let fnPattern = #"(?im)\b(function|filter)\s+([A-Za-z0-9_-]+)\s*(?:\(([^)]*)\))?\s*\{"#
        if let fnRegex = try? NSRegularExpression(pattern: fnPattern) {
            let ns = normalized as NSString
            let matches = fnRegex.matches(in: normalized, range: NSRange(location: 0, length: ns.length))
            for match in matches {
                guard match.numberOfRanges >= 3,
                      let nameRange = Range(match.range(at: 2), in: normalized),
                      let fullMatchRange = Range(match.range(at: 0), in: normalized) else {
                    continue
                }
                let fnName = String(normalized[nameRange])
                let openBraceIdx = normalized.index(before: fullMatchRange.upperBound)
                guard let closeBraceIdx = findMatchingDelimiter(
                    in: normalized,
                    openIndex: openBraceIdx,
                    openChar: "{",
                    closeChar: "}"
                ) else {
                    continue
                }
                let bodyRange = openBraceIdx..<normalized.index(after: closeBraceIdx)
                consumedRanges.append(fullMatchRange.lowerBound..<bodyRange.upperBound)

                let fnSlice = String(normalized[fullMatchRange.lowerBound..<bodyRange.upperBound])
                let synopsis = extractCommentSynopsis(in: fnSlice) ?? extractPrecedingCommentSynopsis(in: normalized, before: fullMatchRange.lowerBound)

                if match.numberOfRanges >= 4,
                   match.range(at: 3).location != NSNotFound,
                   let inlineRange = Range(match.range(at: 3), in: normalized) {
                    let inlineRaw = String(normalized[inlineRange])
                    let fields = parseParamListContent(inlineRaw)
                    if !fields.isEmpty {
                        specs.append(ParameterFormSpec(
                            commandName: fnName,
                            isFunction: true,
                            synopsis: synopsis,
                            parameters: fields
                        ))
                        continue
                    }
                }

                let innerBody = String(normalized[normalized.index(after: openBraceIdx)..<closeBraceIdx])
                if let paramInner = extractFirstParamBlockBody(in: innerBody) {
                    let fields = parseParamListContent(paramInner)
                    specs.append(ParameterFormSpec(
                        commandName: fnName,
                        isFunction: true,
                        synopsis: synopsis,
                        parameters: fields
                    ))
                } else {
                    specs.append(ParameterFormSpec(
                        commandName: fnName,
                        isFunction: true,
                        synopsis: synopsis,
                        parameters: []
                    ))
                }
            }
        }

        // 2. Match top-level script `param(...)` outside any function body
        let paramWordPattern = #"(?im)\bparam\s*\("#
        if let paramRegex = try? NSRegularExpression(pattern: paramWordPattern) {
            let ns = normalized as NSString
            let matches = paramRegex.matches(in: normalized, range: NSRange(location: 0, length: ns.length))
            for match in matches {
                guard let mRange = Range(match.range(at: 0), in: normalized) else { continue }
                let insideFunction = consumedRanges.contains { $0.contains(mRange.lowerBound) }
                if insideFunction { continue }

                let openParenIdx = normalized.index(before: mRange.upperBound)
                if let closeParenIdx = findMatchingDelimiter(
                    in: normalized,
                    openIndex: openParenIdx,
                    openChar: "(",
                    closeChar: ")"
                ) {
                    let rawParams = String(normalized[normalized.index(after: openParenIdx)..<closeParenIdx])
                    let fields = parseParamListContent(rawParams)
                    let topSynopsis = extractCommentSynopsis(in: normalized)
                    specs.insert(
                        ParameterFormSpec(
                            commandName: defaultScriptName,
                            isFunction: false,
                            synopsis: topSynopsis,
                            parameters: fields
                        ),
                        at: 0
                    )
                    break
                }
            }
        }

        return specs
    }

    /// Formats a clean, executable PowerShell command line from user-entered form values and switch toggles.
    public static func buildInvocationCommand(
        spec: ParameterFormSpec,
        values: [String: String],
        switchStates: [String: Bool]
    ) -> String {
        var parts: [String] = []
        if spec.isFunction {
            parts.append(spec.commandName)
        } else {
            let cleanName = spec.commandName.hasPrefix(".") || spec.commandName.hasPrefix("/")
                ? spec.commandName
                : ".\\\(spec.commandName)"
            parts.append(cleanName)
        }

        for param in spec.parameters {
            if param.isSwitch {
                if switchStates[param.name] == true {
                    parts.append("-\(param.name)")
                }
                continue
            }
            let rawVal = (values[param.name] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !rawVal.isEmpty else { continue }
            parts.append("-\(param.name) \(formatPowerShellArgument(rawVal, typeName: param.typeName))")
        }

        return parts.joined(separator: " ")
    }

    private static func formatPowerShellArgument(_ raw: String, typeName: String) -> String {
        let lowerType = typeName.lowercased()
        // Leave already-quoted strings, variables ($foo), subexpressions ($(...)), arrays (@(...)), and numbers bare
        if (raw.hasPrefix("'") && raw.hasSuffix("'"))
            || (raw.hasPrefix("\"") && raw.hasSuffix("\""))
            || raw.hasPrefix("$")
            || raw.hasPrefix("@(")
            || raw.hasPrefix("@{") {
            return raw
        }
        if lowerType.contains("int") || lowerType.contains("double") || lowerType.contains("decimal") || lowerType.contains("long") {
            if Double(raw) != nil { return raw }
        }
        if lowerType.contains("bool") {
            if raw.lowercased() == "true" || raw == "$true" { return "$true" }
            if raw.lowercased() == "false" || raw == "$false" { return "$false" }
        }
        let needsQuotes = raw.contains(where: { $0.isWhitespace || ";|&(){}[]\"'`".contains($0) }) || lowerType == "string"
        if needsQuotes {
            let escaped = raw.replacingOccurrences(of: "'", with: "''")
            return "'\(escaped)'"
        }
        return raw
    }

    private static func extractFirstParamBlockBody(in text: String) -> String? {
        let pattern = #"(?im)\bparam\s*\("#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let ns = text as NSString
        guard let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)),
              let mRange = Range(match.range(at: 0), in: text) else {
            return nil
        }
        let openParen = text.index(before: mRange.upperBound)
        guard let closeParen = findMatchingDelimiter(in: text, openIndex: openParen, openChar: "(", closeChar: ")") else {
            return nil
        }
        return String(text[text.index(after: openParen)..<closeParen])
    }

    private static func parseParamListContent(_ rawContent: String) -> [ParameterFieldSpec] {
        // Strip single-line `# ...` comments first so commas inside comments don't split parameters
        let cleanedLines = rawContent.components(separatedBy: "\n").map { line -> String in
            var inSingle = false
            var inDouble = false
            var out = ""
            for ch in line {
                if ch == "'" && !inDouble { inSingle.toggle() }
                else if ch == "\"" && !inSingle { inDouble.toggle() }
                else if ch == "#" && !inSingle && !inDouble { break }
                out.append(ch)
            }
            return out
        }
        let cleaned = cleanedLines.joined(separator: "\n")
        let segments = splitTopLevelCommas(cleaned)

        var fields: [ParameterFieldSpec] = []
        for seg in segments {
            let trimmed = seg.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }
            if let field = parseSingleParameterSegment(trimmed) {
                fields.append(field)
            }
        }
        return fields
    }

    private static func splitTopLevelCommas(_ text: String) -> [String] {
        var result: [String] = []
        var current = ""
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inSingle = false
        var inDouble = false

        for ch in text {
            if ch == "'" && !inDouble {
                inSingle.toggle()
                current.append(ch)
            } else if ch == "\"" && !inSingle {
                inDouble.toggle()
                current.append(ch)
            } else if !inSingle && !inDouble {
                switch ch {
                case "(": parenDepth += 1; current.append(ch)
                case ")": parenDepth = max(0, parenDepth - 1); current.append(ch)
                case "[": bracketDepth += 1; current.append(ch)
                case "]": bracketDepth = max(0, bracketDepth - 1); current.append(ch)
                case "{": braceDepth += 1; current.append(ch)
                case "}": braceDepth = max(0, braceDepth - 1); current.append(ch)
                case "," where parenDepth == 0 && bracketDepth == 0 && braceDepth == 0:
                    result.append(current)
                    current = ""
                default:
                    current.append(ch)
                }
            } else {
                current.append(ch)
            }
        }
        if !current.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            result.append(current)
        }
        return result
    }

    private static func parseSingleParameterSegment(_ segment: String) -> ParameterFieldSpec? {
        // Locate the parameter variable `$Name` outside `[Attribute(...)]` brackets
        var bracketDepth = 0
        var parenDepth = 0
        var inSingle = false
        var inDouble = false
        var dollarIndex: String.Index? = nil

        var idx = segment.startIndex
        while idx < segment.endIndex {
            let ch = segment[idx]
            if ch == "'" && !inDouble { inSingle.toggle() }
            else if ch == "\"" && !inSingle { inDouble.toggle() }
            else if !inSingle && !inDouble {
                if ch == "[" { bracketDepth += 1 }
                else if ch == "]" { bracketDepth = max(0, bracketDepth - 1) }
                else if ch == "(" { parenDepth += 1 }
                else if ch == ")" { parenDepth = max(0, parenDepth - 1) }
                else if ch == "$" && bracketDepth == 0 && parenDepth == 0 {
                    dollarIndex = idx
                    break
                }
            }
            idx = segment.index(after: idx)
        }

        guard let varStart = dollarIndex else { return nil }
        let afterDollar = segment.index(after: varStart)
        var varEnd = afterDollar
        while varEnd < segment.endIndex {
            let ch = segment[varEnd]
            if ch.isLetter || ch.isNumber || ch == "_" {
                varEnd = segment.index(after: varEnd)
            } else {
                break
            }
        }
        let paramName = String(segment[afterDollar..<varEnd])
        guard !paramName.isEmpty else { return nil }

        let attributesPrefix = String(segment[..<varStart])
        let afterVar = String(segment[varEnd...]).trimmingCharacters(in: .whitespacesAndNewlines)

        var defaultVal: String? = nil
        if afterVar.hasPrefix("=") {
            let rawDef = String(afterVar.dropFirst()).trimmingCharacters(in: .whitespacesAndNewlines)
            if !rawDef.isEmpty {
                defaultVal = unquoteSimpleLiteral(rawDef)
            }
        }

        // Parse Mandatory, HelpMessage, ValidateSet, and Type accelerator from attributesPrefix
        var isMandatory = false
        var helpMessage: String? = nil
        var validateSet: [String] = []
        var detectedType = "String"
        var isSwitch = false

        let attrBlocks = extractTopLevelBracketBlocks(in: attributesPrefix)
        for block in attrBlocks {
            let trimmedBlock = block.trimmingCharacters(in: .whitespacesAndNewlines)
            let lower = trimmedBlock.lowercased()
            if lower.hasPrefix("parameter") {
                if let mandRegex = try? NSRegularExpression(pattern: #"(?i)\bmandatory\s*(?:=\s*\$?(true|false|1|0))?"#),
                   let match = mandRegex.firstMatch(in: trimmedBlock, range: NSRange(location: 0, length: (trimmedBlock as NSString).length)) {
                    if match.numberOfRanges >= 2, match.range(at: 1).location != NSNotFound,
                       let valR = Range(match.range(at: 1), in: trimmedBlock) {
                        let v = trimmedBlock[valR].lowercased()
                        isMandatory = (v == "true" || v == "1")
                    } else {
                        isMandatory = true
                    }
                }
                if let helpRegex = try? NSRegularExpression(pattern: #"(?i)helpmessage\s*=\s*(['"])(.*?)\1"#),
                   let hMatch = helpRegex.firstMatch(in: trimmedBlock, range: NSRange(location: 0, length: (trimmedBlock as NSString).length)),
                   let hRange = Range(hMatch.range(at: 2), in: trimmedBlock) {
                    helpMessage = String(trimmedBlock[hRange])
                }
            } else if lower.hasPrefix("validateset") {
                if let openP = trimmedBlock.firstIndex(of: "("),
                   let closeP = trimmedBlock.lastIndex(of: ")"),
                   openP < closeP {
                    let inner = String(trimmedBlock[trimmedBlock.index(after: openP)..<closeP])
                    validateSet = splitTopLevelCommas(inner)
                        .map { unquoteSimpleLiteral($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
                        .filter { !$0.isEmpty }
                }
            } else if lower.hasPrefix("validatenotnull")
                        || lower.hasPrefix("validatepattern")
                        || lower.hasPrefix("validaterange")
                        || lower.hasPrefix("validatescript")
                        || lower.hasPrefix("validatelength")
                        || lower.hasPrefix("validatecount")
                        || lower.hasPrefix("alias")
                        || lower.hasPrefix("allowempty")
                        || lower.hasPrefix("allownull")
                        || lower.hasPrefix("pstypename")
                        || lower.hasPrefix("outputtype")
                        || lower.hasPrefix("cmdletbinding") {
                continue
            } else {
                // Type constraint e.g. [string], [int], [switch], [System.IO.FileInfo], [string[]]
                detectedType = trimmedBlock
                if lower == "switch" || lower == "switchparameter" || lower == "system.management.automation.switchparameter" {
                    isSwitch = true
                    detectedType = "Switch"
                }
            }
        }

        return ParameterFieldSpec(
            name: paramName,
            typeName: detectedType,
            isMandatory: isMandatory,
            isSwitch: isSwitch,
            validateSetOptions: validateSet,
            defaultValue: defaultVal,
            helpMessage: helpMessage
        )
    }

    private static func extractTopLevelBracketBlocks(in text: String) -> [String] {
        var blocks: [String] = []
        var depth = 0
        var startIdx: String.Index? = nil
        var inSingle = false
        var inDouble = false
        var idx = text.startIndex
        while idx < text.endIndex {
            let ch = text[idx]
            if ch == "'" && !inDouble { inSingle.toggle() }
            else if ch == "\"" && !inSingle { inDouble.toggle() }
            else if !inSingle && !inDouble {
                if ch == "[" {
                    if depth == 0 {
                        startIdx = text.index(after: idx)
                    }
                    depth += 1
                } else if ch == "]" {
                    depth = max(0, depth - 1)
                    if depth == 0, let s = startIdx {
                        blocks.append(String(text[s..<idx]))
                        startIdx = nil
                    }
                }
            }
            idx = text.index(after: idx)
        }
        return blocks
    }

    private static func unquoteSimpleLiteral(_ raw: String) -> String {
        if raw.count >= 2 {
            if (raw.hasPrefix("'") && raw.hasSuffix("'")) || (raw.hasPrefix("\"") && raw.hasSuffix("\"")) {
                return String(raw.dropFirst().dropLast())
            }
        }
        return raw
    }

    private static func unwrapOuterParentheses(_ raw: String) -> String {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.count >= 2, t.first == "(", let matchClose = findMatchingDelimiter(in: t, openIndex: t.startIndex, openChar: "(", closeChar: ")"), matchClose == t.index(before: t.endIndex) {
            return String(t.dropFirst().dropLast()).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return t
    }

    // MARK: - F1 Cmdlet & Comment-Based Quick Help

    public static func tokenUnderCursor(in text: String, cursorOffset: Int) -> String {
        let ns = text as NSString
        guard ns.length > 0 else { return "" }
        let clamped = min(max(0, cursorOffset), ns.length)

        func isCmdletChar(_ u: unichar) -> Bool {
            guard let scalar = UnicodeScalar(u) else { return false }
            return CharacterSet.alphanumerics.contains(scalar) || scalar == "-" || scalar == "_"
        }

        var probe = clamped
        if probe == ns.length || !isCmdletChar(ns.character(at: probe)) {
            if probe > 0 && isCmdletChar(ns.character(at: probe - 1)) {
                probe -= 1
            } else {
                return ""
            }
        }

        var start = probe
        while start > 0 && isCmdletChar(ns.character(at: start - 1)) {
            start -= 1
        }
        var end = probe
        while end < ns.length && isCmdletChar(ns.character(at: end)) {
            end += 1
        }
        return ns.substring(with: NSRange(location: start, length: end - start))
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    }

    public static func lookupQuickHelp(
        queryOrCursorToken: String,
        documentContent: String = "",
        preferLivePwsh: Bool = false
    ) -> CmdletHelpTopic {
        let query = queryOrCursorToken.trimmingCharacters(in: .whitespacesAndNewlines)
        let effectiveQuery = query.isEmpty ? "Get-Help" : query

        // 1. Check user-defined function + comment-based help in active document
        if !documentContent.isEmpty {
            let specs = extractParameterFormSpecs(from: documentContent)
            if let matchedSpec = specs.first(where: { $0.commandName.caseInsensitiveCompare(effectiveQuery) == .orderedSame }) {
                let syntaxParts = matchedSpec.parameters.map { p -> String in
                    let paramPart = p.isSwitch ? "[-\(p.name)]" : "-\(p.name) <\(p.typeName)>"
                    return p.isMandatory ? paramPart : "[\(paramPart)]"
                }
                let syntaxLine = ([matchedSpec.commandName] + syntaxParts).joined(separator: " ")
                let helpParams = matchedSpec.parameters.map { p in
                    CmdletHelpParameter(
                        name: p.name,
                        typeName: p.typeName,
                        isMandatory: p.isMandatory,
                        description: p.helpMessage ?? (p.validateSetOptions.isEmpty ? "Script parameter." : "Allowed values: \(p.validateSetOptions.joined(separator: ", "))")
                    )
                }
                return CmdletHelpTopic(
                    name: matchedSpec.commandName,
                    moduleName: "Active Buffer",
                    synopsis: matchedSpec.synopsis ?? "Function defined in the active PowerShell document.",
                    syntax: [syntaxLine],
                    parameters: helpParams,
                    examples: [buildInvocationCommand(spec: matchedSpec, values: [:], switchStates: [:])],
                    sourceBadge: "Document AST"
                )
            }
        }

        // 2. Check built-in curated PowerShell cmdlet dictionary (instant offline lookup)
        if let builtin = builtInHelpTopics.first(where: { $0.name.caseInsensitiveCompare(effectiveQuery) == .orderedSame })
            ?? resolveAliasTopic(effectiveQuery) {
            return builtin
        }

        // 3. Optional live `pwsh` Get-Help query when requested
        if preferLivePwsh, let live = queryLivePwshHelp(cmdletName: effectiveQuery) {
            return live
        }

        // 4. Fuzzy fallback match
        if let partial = builtInHelpTopics.first(where: { $0.name.localizedCaseInsensitiveContains(effectiveQuery) }) {
            return partial
        }

        return CmdletHelpTopic(
            name: effectiveQuery,
            moduleName: "PowerShell",
            synopsis: "No offline reference entry matched '\(effectiveQuery)'. Install PowerShell 7 (`pwsh`) or search another Verb-Noun cmdlet above.",
            syntax: ["\(effectiveQuery) [[-Parameter] <Object>]"],
            parameters: [],
            examples: ["Get-Help \(effectiveQuery) -Full"],
            sourceBadge: "Fallback"
        )
    }

    public static func allOfflineHelpTopics() -> [CmdletHelpTopic] {
        builtInHelpTopics
    }

    private static func resolveAliasTopic(_ aliasName: String) -> CmdletHelpTopic? {
        let map: [String: String] = [
            "gci": "Get-ChildItem", "ls": "Get-ChildItem", "dir": "Get-ChildItem",
            "?": "Where-Object", "where": "Where-Object",
            "%": "ForEach-Object", "foreach": "ForEach-Object",
            "select": "Select-Object", "sort": "Sort-Object", "group": "Group-Object",
            "measure": "Measure-Object", "irm": "Invoke-RestMethod", "iwr": "Invoke-WebRequest",
            "curl": "Invoke-WebRequest", "gc": "Get-Content", "cat": "Get-Content",
            "sc": "Set-Content", "ac": "Add-Content", "gps": "Get-Process", "ps": "Get-Process",
            "gsv": "Get-Service", "gcm": "Get-Command", "gm": "Get-Member", "sls": "Select-String"
        ]
        guard let target = map[aliasName.lowercased()] else { return nil }
        return builtInHelpTopics.first { $0.name.caseInsensitiveCompare(target) == .orderedSame }
    }

    private static func queryLivePwshHelp(cmdletName: String) -> CmdletHelpTopic? {
        guard let pwshPath = PowerShellRunnerEngine.detectPwshExecutablePath() else { return nil }
        // Strict validation: allow only safe cmdlet characters
        guard cmdletName.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" || $0 == "." }) else {
            return nil
        }
        let escaped = cmdletName.replacingOccurrences(of: "'", with: "''")
        let psScript = """
        $ErrorActionPreference = 'Stop'
        $h = Get-Help -Name '\(escaped)' -ErrorAction Stop
        $syn = ($h.Synopsis | Out-String).Trim()
        $mod = if ($h.ModuleName) { [string]$h.ModuleName } else { 'PowerShell' }
        [pscustomobject]@{ Name = [string]$h.Name; Module = $mod; Synopsis = $syn } | ConvertTo-Json -Compress
        """

        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: pwshPath)
        proc.arguments = ["-NoLogo", "-NoProfile", "-NonInteractive", "-Command", psScript]
        let outPipe = Pipe()
        proc.standardOutput = outPipe
        proc.standardError = Pipe()
        do {
            try proc.run()
        } catch {
            return nil
        }
        let data = outPipe.fileHandleForReading.readDataToEndOfFile()
        proc.waitUntilExit()
        guard proc.terminationStatus == 0,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let name = json["Name"] as? String,
              let synopsis = json["Synopsis"] as? String,
              !synopsis.isEmpty else {
            return nil
        }
        let mod = (json["Module"] as? String) ?? "PowerShell 7"
        return CmdletHelpTopic(
            name: name,
            moduleName: mod,
            synopsis: synopsis,
            syntax: ["\(name) [parameters]"],
            parameters: [],
            examples: ["Get-Help \(name) -Examples"],
            sourceBadge: "Live pwsh"
        )
    }

    // MARK: - Pester Unit Test Discovery & Runner

    /// Discovers Pester `Describe` / `Context` / `It` blocks with 1-based line numbers for one-click editor navigation.
    public static func discoverPesterTests(in scriptContent: String) -> [PesterTestCaseSpec] {
        let normalized = LineEnding.normalizeToLf(scriptContent)
        var specs: [PesterTestCaseSpec] = []
        scanPesterScope(
            in: normalized,
            baseLineOffset: 0,
            currentDescribe: "Pester Suite",
            currentContext: nil,
            into: &specs
        )
        return specs
    }

    /// Executes Pester tests in `code` using native `pwsh` when installed (with built-in Pester v5-compatible assertion harness if module is absent),
    /// or falls back to GravityEdit's deterministic embedded Pester `Should` evaluator so tests run anywhere.
    public static func runPesterTests(
        code: String,
        workingDirectory: URL? = nil,
        forceBuiltIn: Bool = false
    ) -> PesterSuiteResult {
        let startNs = DispatchTime.now().uptimeNanoseconds
        let discovered = discoverPesterTests(in: code)
        guard !discovered.isEmpty else {
            return PesterSuiteResult(
                engineDescription: "Pester Discovery",
                usedNativePwsh: false,
                passedCount: 0,
                failedCount: 0,
                totalDurationMs: 0,
                results: [],
                rawOutput: "No Pester Describe / It blocks were found in the current script."
            )
        }

        if !forceBuiltIn, let pwshPath = PowerShellRunnerEngine.detectPwshExecutablePath() {
            if let nativeResult = runPesterViaPwsh(pwshPath: pwshPath, code: code, discovered: discovered, workingDirectory: workingDirectory) {
                return nativeResult
            }
        }

        // Deterministic built-in Pester evaluator
        // Collect helper functions defined outside `Describe` blocks so `It` bodies can call functions under test!
        let preamble = extractNonPesterPreamble(from: code)
        var results: [PesterTestCaseResult] = []
        var rawLines: [String] = []

        for test in discovered {
            let caseStart = DispatchTime.now().uptimeNanoseconds
            let evaluation = evaluateBuiltInPesterItBlock(preamble: preamble, itBody: test.bodyCode, workingDirectory: workingDirectory)
            let caseMs = Int64((DispatchTime.now().uptimeNanoseconds - caseStart) / 1_000_000)
            results.append(PesterTestCaseResult(
                id: test.id,
                describeBlock: test.describeBlock,
                contextBlock: test.contextBlock,
                itName: test.itName,
                lineNumber: test.lineNumber,
                passed: evaluation.passed,
                durationMs: max(1, caseMs),
                failureMessage: evaluation.failureMessage
            ))
            let icon = evaluation.passed ? "[PASS]" : "[FAIL]"
            let scope = [test.describeBlock, test.contextBlock].compactMap { $0 }.joined(separator: " > ")
            rawLines.append("\(icon) \(scope) :: \(test.itName)")
            if let fail = evaluation.failureMessage {
                rawLines.append("       \(fail)")
            }
        }

        let passedCount = results.filter(\.passed).count
        let failedCount = results.count - passedCount
        let totalMs = Int64((DispatchTime.now().uptimeNanoseconds - startNs) / 1_000_000)

        return PesterSuiteResult(
            engineDescription: "Embedded Pester Harness",
            usedNativePwsh: false,
            passedCount: passedCount,
            failedCount: failedCount,
            totalDurationMs: max(1, totalMs),
            results: results,
            rawOutput: rawLines.joined(separator: "\n")
        )
    }

    private static func runPesterViaPwsh(
        pwshPath: String,
        code: String,
        discovered: [PesterTestCaseSpec],
        workingDirectory: URL?
    ) -> PesterSuiteResult? {
        let startNs = DispatchTime.now().uptimeNanoseconds
        let tempDir = FileManager.default.temporaryDirectory
        let suiteFile = tempDir.appendingPathComponent("gravity_pester_\(UUID().uuidString).Tests.ps1")
        defer { try? FileManager.default.removeItem(at: suiteFile) }

        // Lightweight self-contained Pester v5-compatible shim in real pwsh so tests execute with full .NET/PowerShell fidelity even on machines without the Pester module installed!
        let harness = #"""
        $__gravityResults = [System.Collections.Generic.List[object]]::new()
        $__gravityDescribe = 'Suite'
        $__gravityContext = $null

        function Describe([string]$Name, [scriptblock]$Fixture) {
            $prev = $__gravityDescribe
            $script:__gravityDescribe = $Name
            try { & $Fixture } finally { $script:__gravityDescribe = $prev }
        }
        function Context([string]$Name, [scriptblock]$Fixture) {
            $prev = $__gravityContext
            $script:__gravityContext = $Name
            try { & $Fixture } finally { $script:__gravityContext = $prev }
        }
        function BeforeAll([scriptblock]$Body) { & $Body }
        function AfterAll([scriptblock]$Body) { & $Body }
        function BeforeEach([scriptblock]$Body) { $script:__gravityBeforeEach = $Body }
        function AfterEach([scriptblock]$Body) { $script:__gravityAfterEach = $Body }

        function Should {
            param(
                [Parameter(ValueFromPipeline = $true)] $ActualValue,
                [switch] $Not,
                $Be,
                $BeExactly,
                [switch] $BeTrue,
                [switch] $BeFalse,
                [switch] $BeNullOrEmpty,
                $BeGreaterThan,
                $BeLessThan,
                $Match,
                $Contain,
                [switch] $Throw
            )
            process {
                $ok = $true
                $msg = ''
                if ($PSBoundParameters.ContainsKey('Throw')) {
                    $threw = $false
                    if ($ActualValue -is [scriptblock]) {
                        try { & $ActualValue | Out-Null } catch { $threw = $true }
                    }
                    $ok = if ($Not) { -not $threw } else { $threw }
                    if (-not $ok) { $msg = "Expected scriptblock $(if ($Not) {'not '} else {''})to throw." }
                } elseif ($PSBoundParameters.ContainsKey('Be')) {
                    $eq = ($ActualValue -eq $Be)
                    $ok = if ($Not) { -not $eq } else { [bool]$eq }
                    if (-not $ok) { $msg = "Expected '$Be'$(if ($Not) {' (negated)'} else {''}), but got '$ActualValue'." }
                } elseif ($PSBoundParameters.ContainsKey('BeExactly')) {
                    $eq = ($ActualValue -ceq $BeExactly)
                    $ok = if ($Not) { -not $eq } else { [bool]$eq }
                    if (-not $ok) { $msg = "Expected exact '$BeExactly', but got '$ActualValue'." }
                } elseif ($BeTrue) {
                    $eq = ([bool]$ActualValue -eq $true)
                    $ok = if ($Not) { -not $eq } else { $eq }
                    if (-not $ok) { $msg = "Expected `$true, got '$ActualValue'." }
                } elseif ($BeFalse) {
                    $eq = ([bool]$ActualValue -eq $false)
                    $ok = if ($Not) { -not $eq } else { $eq }
                    if (-not $ok) { $msg = "Expected `$false, got '$ActualValue'." }
                } elseif ($BeNullOrEmpty) {
                    $eq = [string]::IsNullOrEmpty([string]$ActualValue) -and ($null -eq $ActualValue -or "$ActualValue" -eq '')
                    $ok = if ($Not) { -not $eq } else { $eq }
                    if (-not $ok) { $msg = "Expected null or empty, got '$ActualValue'." }
                } elseif ($PSBoundParameters.ContainsKey('BeGreaterThan')) {
                    $eq = ($ActualValue -gt $BeGreaterThan)
                    $ok = if ($Not) { -not $eq } else { [bool]$eq }
                    if (-not $ok) { $msg = "Expected value > $BeGreaterThan, got '$ActualValue'." }
                } elseif ($PSBoundParameters.ContainsKey('BeLessThan')) {
                    $eq = ($ActualValue -lt $BeLessThan)
                    $ok = if ($Not) { -not $eq } else { [bool]$eq }
                    if (-not $ok) { $msg = "Expected value < $BeLessThan, got '$ActualValue'." }
                } elseif ($PSBoundParameters.ContainsKey('Match')) {
                    $eq = ([string]$ActualValue -match $Match)
                    $ok = if ($Not) { -not $eq } else { [bool]$eq }
                    if (-not $ok) { $msg = "Expected '$ActualValue' to match '$Match'." }
                } elseif ($PSBoundParameters.ContainsKey('Contain')) {
                    $eq = ($ActualValue -contains $Contain)
                    $ok = if ($Not) { -not $eq } else { [bool]$eq }
                    if (-not $ok) { $msg = "Expected collection to contain '$Contain'." }
                }
                if (-not $ok) { throw $msg }
            }
        }

        function It([string]$Name, [scriptblock]$Test) {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $passed = $true
            $err = $null
            try {
                if ($script:__gravityBeforeEach) { & $script:__gravityBeforeEach }
                & $Test | Out-Null
                if ($script:__gravityAfterEach) { & $script:__gravityAfterEach }
            } catch {
                $passed = $false
                $err = $_.Exception.Message
            }
            $sw.Stop()
            $__gravityResults.Add([pscustomobject]@{
                Describe = $__gravityDescribe
                Context  = $__gravityContext
                It       = $Name
                Passed   = $passed
                Ms       = [int64]$sw.ElapsedMilliseconds
                Error    = $err
            })
        }
        """#

        let combinedScript = harness + "\n\n" + code + "\n\nWrite-Output '__GRAVITY_PESTER_JSON__'\n$__gravityResults | ConvertTo-Json -Compress -Depth 4\n"
        do {
            try combinedScript.write(to: suiteFile, atomically: true, encoding: .utf8)
        } catch {
            return nil
        }

        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: pwshPath)
        proc.arguments = ["-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", suiteFile.path]
        if let wd = workingDirectory, FileManager.default.fileExists(atPath: wd.path) {
            proc.currentDirectoryURL = wd
        }
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

        let stdout = String(decoding: outData, as: UTF8.self)
        let stderr = String(decoding: errData, as: UTF8.self)
        guard let markerRange = stdout.range(of: "__GRAVITY_PESTER_JSON__") else {
            return nil
        }
        let jsonPart = stdout[markerRange.upperBound...].trimmingCharacters(in: .whitespacesAndNewlines)
        guard let jsonData = jsonPart.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: jsonData) else {
            return nil
        }

        let rawObjects: [[String: Any]]
        if let arr = parsed as? [[String: Any]] {
            rawObjects = arr
        } else if let single = parsed as? [String: Any] {
            rawObjects = [single]
        } else {
            return nil
        }

        var mapped: [PesterTestCaseResult] = []
        for (idx, obj) in rawObjects.enumerated() {
            let desc = (obj["Describe"] as? String) ?? (idx < discovered.count ? discovered[idx].describeBlock : "Suite")
            let ctx = obj["Context"] as? String
            let itName = (obj["It"] as? String) ?? (idx < discovered.count ? discovered[idx].itName : "Test \(idx + 1)")
            let passed = (obj["Passed"] as? Bool) ?? false
            let ms = (obj["Ms"] as? NSNumber)?.int64Value ?? 1
            let errMsg = obj["Error"] as? String
            let matchedSpec = (idx < discovered.count)
                ? discovered[idx]
                : discovered.first(where: { $0.itName == itName && $0.describeBlock == desc })
            mapped.append(PesterTestCaseResult(
                id: matchedSpec?.id ?? "pester_\(idx)",
                describeBlock: desc,
                contextBlock: ctx,
                itName: itName,
                lineNumber: matchedSpec?.lineNumber ?? 1,
                passed: passed,
                durationMs: max(1, ms),
                failureMessage: errMsg
            ))
        }

        let passedCount = mapped.filter(\.passed).count
        let failedCount = mapped.count - passedCount
        let totalMs = Int64((DispatchTime.now().uptimeNanoseconds - startNs) / 1_000_000)
        let summaryLines = mapped.map { r -> String in
            let tag = r.passed ? "[PASS]" : "[FAIL]"
            let errSuffix = r.failureMessage.map { " — \($0)" } ?? ""
            return "\(tag) \(r.describeBlock) :: \(r.itName) (\(r.durationMs) ms)\(errSuffix)"
        } + (stderr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? [] : [stderr.trimmingCharacters(in: .whitespacesAndNewlines)])

        return PesterSuiteResult(
            engineDescription: "PowerShell 7 (pwsh Pester Runner)",
            usedNativePwsh: true,
            passedCount: passedCount,
            failedCount: failedCount,
            totalDurationMs: max(1, totalMs),
            results: mapped,
            rawOutput: summaryLines.joined(separator: "\n")
        )
    }

    private static func scanPesterScope(
        in text: String,
        baseLineOffset: Int,
        currentDescribe: String,
        currentContext: String?,
        into specs: inout [PesterTestCaseSpec]
    ) {
        let pattern = #"(?im)\b(Describe|Context|It)\s+(?:-Name\s+)?(['"])(.*?)\2\s*\{"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return }

        var searchIndex = text.startIndex
        while searchIndex < text.endIndex {
            let nsRange = NSRange(searchIndex..<text.endIndex, in: text)
            guard let match = regex.firstMatch(in: text, range: nsRange),
                  let fullRange = Range(match.range(at: 0), in: text),
                  let kwRange = Range(match.range(at: 1), in: text),
                  let titleRange = Range(match.range(at: 3), in: text) else {
                break
            }

            let keyword = text[kwRange].lowercased()
            let title = String(text[titleRange])
            let openBrace = text.index(before: fullRange.upperBound)
            guard let closeBrace = findMatchingDelimiter(in: text, openIndex: openBrace, openChar: "{", closeChar: "}") else {
                searchIndex = fullRange.upperBound
                continue
            }

            let prefixLines = text[..<fullRange.lowerBound].reduce(0) { $1 == "\n" ? $0 + 1 : $0 }
            let oneBasedLine = baseLineOffset + prefixLines + 1
            let innerBody = String(text[text.index(after: openBrace)..<closeBrace])

            switch keyword {
            case "describe":
                scanPesterScope(
                    in: innerBody,
                    baseLineOffset: oneBasedLine - 1,
                    currentDescribe: title,
                    currentContext: nil,
                    into: &specs
                )
            case "context":
                scanPesterScope(
                    in: innerBody,
                    baseLineOffset: oneBasedLine - 1,
                    currentDescribe: currentDescribe,
                    currentContext: title,
                    into: &specs
                )
            case "it":
                specs.append(PesterTestCaseSpec(
                    id: "it_\(oneBasedLine)_\(specs.count)",
                    describeBlock: currentDescribe,
                    contextBlock: currentContext,
                    itName: title,
                    lineNumber: oneBasedLine,
                    bodyCode: innerBody
                ))
            default:
                break
            }

            searchIndex = text.index(after: closeBrace)
        }
    }

    private static func extractNonPesterPreamble(from code: String) -> String {
        let normalized = LineEnding.normalizeToLf(code)
        let pattern = #"(?im)\bDescribe\s+(?:-Name\s+)?(['"])(.*?)\1\s*\{"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return normalized }
        var output = ""
        var cursor = normalized.startIndex
        while cursor < normalized.endIndex {
            let nsRange = NSRange(cursor..<normalized.endIndex, in: normalized)
            guard let match = regex.firstMatch(in: normalized, range: nsRange),
                  let fullRange = Range(match.range(at: 0), in: normalized) else {
                output.append(contentsOf: normalized[cursor...])
                break
            }
            output.append(contentsOf: normalized[cursor..<fullRange.lowerBound])
            let openBrace = normalized.index(before: fullRange.upperBound)
            if let closeBrace = findMatchingDelimiter(in: normalized, openIndex: openBrace, openChar: "{", closeChar: "}") {
                cursor = normalized.index(after: closeBrace)
            } else {
                cursor = fullRange.upperBound
            }
        }
        return output
    }

    private static func evaluateBuiltInPesterItBlock(
        preamble: String,
        itBody: String,
        workingDirectory: URL?
    ) -> (passed: Bool, failureMessage: String?) {
        let lines = itBody.components(separatedBy: "\n")
        var setupLines: [String] = []

        for rawLine in lines {
            let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty || trimmed.hasPrefix("#") { continue }

            if let shouldRange = trimmed.range(of: #"\|\s*Should\b"#, options: .regularExpression) {
                let rawExpr = String(trimmed[..<shouldRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
                let actualExpr = unwrapOuterParentheses(rawExpr)
                let assertionTail = String(trimmed[shouldRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)

                let scriptToEval = (preamble + "\n" + setupLines.joined(separator: "\n") + "\n$__pesterActual = \(actualExpr)\nWrite-Output $__pesterActual")
                let execResult = PowerShellRunnerEngine.executeScript(
                    code: scriptToEval,
                    language: .powershell,
                    workingDirectory: workingDirectory,
                    forceEmbeddedPowerShell: true
                )
                let actualValue = execResult.lines
                    .filter { $0.stream == .stdout }
                    .map(\.text)
                    .joined(separator: "\n")
                    .trimmingCharacters(in: .whitespacesAndNewlines)

                let check = evaluateShouldClause(actual: actualValue, exitCode: execResult.exitCode, clause: assertionTail)
                if !check.passed {
                    return check
                }
            } else {
                setupLines.append(trimmed)
            }
        }

        if !setupLines.isEmpty {
            let checkRun = PowerShellRunnerEngine.executeScript(
                code: preamble + "\n" + setupLines.joined(separator: "\n"),
                language: .powershell,
                workingDirectory: workingDirectory,
                forceEmbeddedPowerShell: true
            )
            if checkRun.exitCode != 0 {
                let errText = checkRun.lines.first(where: { $0.stream == .stderr })?.text ?? "Statement threw an error."
                return (false, errText)
            }
        }
        return (true, nil)
    }

    private static func evaluateShouldClause(
        actual: String,
        exitCode: Int32,
        clause: String
    ) -> (passed: Bool, failureMessage: String?) {
        var tokens = tokenizeShouldClause(clause)
        var isNegated = false
        if let notIdx = tokens.firstIndex(where: { $0.caseInsensitiveCompare("-Not") == .orderedSame }) {
            isNegated = true
            tokens.remove(at: notIdx)
        }
        guard let op = tokens.first?.lowercased() else {
            return (true, nil)
        }
        let expected = tokens.count >= 2 ? unquoteSimpleLiteral(tokens[1]) : ""

        let rawMatch: Bool
        let failDesc: String

        switch op {
        case "-be":
            if let aNum = Double(actual), let eNum = Double(expected) {
                rawMatch = abs(aNum - eNum) < 1e-9
            } else {
                rawMatch = actual.caseInsensitiveCompare(expected) == .orderedSame
            }
            failDesc = "Expected '\(expected)'\(isNegated ? " (negated)" : ""), but got '\(actual)'."
        case "-beexactly":
            rawMatch = (actual == expected)
            failDesc = "Expected exact '\(expected)', but got '\(actual)'."
        case "-betrue":
            rawMatch = (actual.lowercased() == "true" || actual == "1" || actual == "$true")
            failDesc = "Expected True, but got '\(actual)'."
        case "-befalse":
            rawMatch = (actual.lowercased() == "false" || actual == "0" || actual == "$false")
            failDesc = "Expected False, but got '\(actual)'."
        case "-benullorempty":
            rawMatch = actual.isEmpty || actual.lowercased() == "$null"
            failDesc = "Expected null or empty value, but got '\(actual)'."
        case "-begreaterthan":
            rawMatch = (Double(actual) ?? 0) > (Double(expected) ?? 0)
            failDesc = "Expected value > \(expected), got '\(actual)'."
        case "-belessthan":
            rawMatch = (Double(actual) ?? 0) < (Double(expected) ?? 0)
            failDesc = "Expected value < \(expected), got '\(actual)'."
        case "-match":
            rawMatch = (actual.range(of: expected, options: [.regularExpression, .caseInsensitive]) != nil)
            failDesc = "Expected '\(actual)' to match regex '\(expected)'."
        case "-contain":
            let items = actual.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }
            rawMatch = items.contains { $0.caseInsensitiveCompare(expected) == .orderedSame } || actual.localizedCaseInsensitiveContains(expected)
            failDesc = "Expected output to contain '\(expected)', got '\(actual)'."
        case "-throw":
            rawMatch = (exitCode != 0)
            failDesc = "Expected expression to throw an error."
        default:
            rawMatch = true
            failDesc = ""
        }

        let finalPass = isNegated ? !rawMatch : rawMatch
        return (finalPass, finalPass ? nil : failDesc)
    }

    private static func tokenizeShouldClause(_ text: String) -> [String] {
        var tokens: [String] = []
        var current = ""
        var inSingle = false
        var inDouble = false
        for ch in text {
            if ch == "'" && !inDouble {
                inSingle.toggle()
                current.append(ch)
            } else if ch == "\"" && !inSingle {
                inDouble.toggle()
                current.append(ch)
            } else if ch.isWhitespace && !inSingle && !inDouble {
                if !current.isEmpty {
                    tokens.append(current)
                    current = ""
                }
            } else {
                current.append(ch)
            }
        }
        if !current.isEmpty {
            tokens.append(current)
        }
        return tokens
    }

    // MARK: - Lexical Utilities

    private static func findMatchingDelimiter(
        in text: String,
        openIndex: String.Index,
        openChar: Character,
        closeChar: Character
    ) -> String.Index? {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var inLineComment = false
        var idx = openIndex

        while idx < text.endIndex {
            let ch = text[idx]
            if inLineComment {
                if ch == "\n" { inLineComment = false }
            } else if ch == "'" && !inDouble {
                inSingle.toggle()
            } else if ch == "\"" && !inSingle {
                inDouble.toggle()
            } else if ch == "#" && !inSingle && !inDouble {
                inLineComment = true
            } else if !inSingle && !inDouble {
                if ch == openChar {
                    depth += 1
                } else if ch == closeChar {
                    depth -= 1
                    if depth == 0 {
                        return idx
                    }
                }
            }
            idx = text.index(after: idx)
        }
        return nil
    }

    private static func extractCommentSynopsis(in text: String) -> String? {
        let pattern = #"(?is)<#.*?\.SYNOPSIS\s*(.*?)(?:\.[A-Z]{3,}|#>)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: (text as NSString).length)),
              let sRange = Range(match.range(at: 1), in: text) else {
            return nil
        }
        let cleaned = String(text[sRange]).trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }

    private static func extractPrecedingCommentSynopsis(in text: String, before index: String.Index) -> String? {
        let prefix = String(text[..<index])
        guard let blockStart = prefix.range(of: "<#", options: .backwards),
              let blockEnd = prefix.range(of: "#>", options: .backwards),
              blockStart.lowerBound < blockEnd.lowerBound else {
            return nil
        }
        let between = prefix[blockEnd.upperBound...].trimmingCharacters(in: .whitespacesAndNewlines)
        guard between.isEmpty else { return nil }
        return extractCommentSynopsis(in: String(prefix[blockStart.lowerBound..<blockEnd.upperBound]))
    }

    // MARK: - Curated Offline Cmdlet Reference

    private static let builtInHelpTopics: [CmdletHelpTopic] = [
        CmdletHelpTopic(
            name: "Get-ChildItem",
            moduleName: "Microsoft.PowerShell.Management",
            synopsis: "Gets the items and child items in one or more specified filesystem or provider locations.",
            syntax: ["Get-ChildItem [[-Path] <String[]>] [[-Filter] <String>] [-Recurse] [-Force] [-File] [-Directory]"],
            parameters: [
                CmdletHelpParameter(name: "Path", typeName: "String[]", description: "Specifies a path to one or more locations."),
                CmdletHelpParameter(name: "Filter", typeName: "String", description: "Provider-level filter string such as '*.ps1'."),
                CmdletHelpParameter(name: "Recurse", typeName: "Switch", description: "Gets the items in the specified locations and in all child items.")
            ],
            examples: ["Get-ChildItem -Path . -Filter '*.ps1' -Recurse", "Get-ChildItem -File | Sort-Object Length -Descending"]
        ),
        CmdletHelpTopic(
            name: "Where-Object",
            moduleName: "Microsoft.PowerShell.Core",
            synopsis: "Selects objects from a collection based on their property values or a predicate script block.",
            syntax: ["Where-Object [-FilterScript] <ScriptBlock>", "Where-Object [-Property] <String> [[-Value] <Object>] [-EQ | -NE | -GT | -LT | -Like | -Match]"],
            parameters: [
                CmdletHelpParameter(name: "FilterScript", typeName: "ScriptBlock", isMandatory: true, description: "Script block that evaluates to $true or $false for each pipeline item ($PSItem / $_).")
            ],
            examples: ["Get-Process | Where-Object { $_.CPU -gt 10 }", "Get-Service | Where-Object Status -EQ 'Running'"]
        ),
        CmdletHelpTopic(
            name: "ForEach-Object",
            moduleName: "Microsoft.PowerShell.Core",
            synopsis: "Performs an operation against each item in a collection of input objects.",
            syntax: ["ForEach-Object [-Process] <ScriptBlock[]> [-Begin <ScriptBlock>] [-End <ScriptBlock>] [-Parallel <ScriptBlock>]"],
            parameters: [
                CmdletHelpParameter(name: "Process", typeName: "ScriptBlock[]", isMandatory: true, description: "Operation executed for every incoming pipeline object."),
                CmdletHelpParameter(name: "Parallel", typeName: "ScriptBlock", description: "Runs each script block concurrently across runspaces (PowerShell 7+).")
            ],
            examples: ["1..5 | ForEach-Object { $_ * 10 }", "$servers | ForEach-Object -Parallel { Test-Connection $_ -Count 1 }"]
        ),
        CmdletHelpTopic(
            name: "Select-Object",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Selects specified properties of an object or set of objects, or takes First/Last/Unique subsets.",
            syntax: ["Select-Object [[-Property] <Object[]>] [-ExpandProperty <String>] [-First <Int32>] [-Last <Int32>] [-Unique]"],
            parameters: [
                CmdletHelpParameter(name: "Property", typeName: "Object[]", description: "Properties or calculated property hashtables to project."),
                CmdletHelpParameter(name: "ExpandProperty", typeName: "String", description: "Unwraps the named property value directly onto the pipeline.")
            ],
            examples: ["Get-Process | Select-Object Name, Id, CPU -First 10"]
        ),
        CmdletHelpTopic(
            name: "Invoke-RestMethod",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Sends an HTTP or HTTPS request to a RESTful web service and automatically deserializes JSON/XML payloads.",
            syntax: ["Invoke-RestMethod [-Uri] <Uri> [-Method <WebRequestMethod>] [-Headers <IDictionary>] [-Body <Object>] [-ContentType <String>]"],
            parameters: [
                CmdletHelpParameter(name: "Uri", typeName: "Uri", isMandatory: true, description: "Target Uniform Resource Identifier for the REST endpoint."),
                CmdletHelpParameter(name: "Method", typeName: "String", description: "HTTP method: Get, Post, Put, Patch, Delete."),
                CmdletHelpParameter(name: "Headers", typeName: "IDictionary", description: "HTTP request headers hashtable.")
            ],
            examples: ["Invoke-RestMethod -Uri 'https://api.github.com/repos/PowerShell/PowerShell/releases/latest'"]
        ),
        CmdletHelpTopic(
            name: "Invoke-WebRequest",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Gets content from a web page on the internet and returns status codes, headers, and raw content.",
            syntax: ["Invoke-WebRequest [-Uri] <Uri> [-Method <WebRequestMethod>] [-OutFile <String>] [-Headers <IDictionary>]"],
            parameters: [
                CmdletHelpParameter(name: "Uri", typeName: "Uri", isMandatory: true, description: "URL of the web resource."),
                CmdletHelpParameter(name: "OutFile", typeName: "String", description: "Saves the response body directly to a file.")
            ],
            examples: ["Invoke-WebRequest -Uri 'https://example.com' -OutFile './page.html'"]
        ),
        CmdletHelpTopic(
            name: "ConvertTo-Json",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Converts any .NET or PowerShell object into a JSON-formatted string.",
            syntax: ["ConvertTo-Json [-InputObject] <Object> [-Depth <Int32>] [-Compress] [-AsArray]"],
            parameters: [
                CmdletHelpParameter(name: "Depth", typeName: "Int32", description: "Maximum nested object depth (defaults to 2; use -Depth 10 for deep trees)."),
                CmdletHelpParameter(name: "Compress", typeName: "Switch", description: "Omits whitespace and indentation.")
            ],
            examples: ["@{ Env = 'Prod'; Nodes = 4 } | ConvertTo-Json -Depth 5"]
        ),
        CmdletHelpTopic(
            name: "ConvertFrom-Json",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Converts a JSON-formatted string to a custom PSObject or Hashtable.",
            syntax: ["ConvertFrom-Json [-InputObject] <String> [-AsHashtable] [-Depth <Int32>]"],
            parameters: [
                CmdletHelpParameter(name: "AsHashtable", typeName: "Switch", description: "Converts the JSON into a mutable [hashtable] instead of PSCustomObject.")
            ],
            examples: ["Get-Content './config.json' -Raw | ConvertFrom-Json -AsHashtable"]
        ),
        CmdletHelpTopic(
            name: "Import-Csv",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Creates table-like custom objects from the items in a character-separated value (CSV) file.",
            syntax: ["Import-Csv [[-Path] <String[]>] [[-Delimiter] <Char>] [-Encoding <Encoding>]"],
            parameters: [
                CmdletHelpParameter(name: "Path", typeName: "String[]", isMandatory: true, description: "Path to the CSV file to parse."),
                CmdletHelpParameter(name: "Delimiter", typeName: "Char", description: "Column delimiter character (default is ',').")
            ],
            examples: ["Import-Csv './users.csv' | Where-Object Department -EQ 'Infra'"]
        ),
        CmdletHelpTopic(
            name: "Export-Csv",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Converts objects into a series of CSV strings and saves them to a file.",
            syntax: ["Export-Csv [[-Path] <String>] [-Delimiter <Char>] [-NoTypeInformation] [-Append]"],
            parameters: [
                CmdletHelpParameter(name: "Path", typeName: "String", isMandatory: true, description: "Output CSV file path."),
                CmdletHelpParameter(name: "Append", typeName: "Switch", description: "Appends rows to an existing CSV file.")
            ],
            examples: ["Get-Process | Select-Object Name, Id, CPU | Export-Csv './procs.csv' -NoTypeInformation"]
        ),
        CmdletHelpTopic(
            name: "Get-Content",
            moduleName: "Microsoft.PowerShell.Management",
            synopsis: "Gets the content of the item at the specified location line-by-line or as a single raw string.",
            syntax: ["Get-Content [-Path] <String[]> [-Raw] [-TotalCount <Int64>] [-Tail <Int32>] [-Wait] [-Encoding <Encoding>]"],
            parameters: [
                CmdletHelpParameter(name: "Raw", typeName: "Switch", description: "Reads the entire file as one multi-line string rather than an array of lines."),
                CmdletHelpParameter(name: "Tail", typeName: "Int32", description: "Reads the last N lines of the file.")
            ],
            examples: ["Get-Content -Path './app.log' -Tail 50 -Wait"]
        ),
        CmdletHelpTopic(
            name: "Set-Content",
            moduleName: "Microsoft.PowerShell.Management",
            synopsis: "Writes new content or replaces existing content in a file.",
            syntax: ["Set-Content [-Path] <String[]> [-Value] <Object[]> [-NoNewline] [-Encoding <Encoding>]"],
            parameters: [
                CmdletHelpParameter(name: "Path", typeName: "String[]", isMandatory: true, description: "Destination file path."),
                CmdletHelpParameter(name: "Value", typeName: "Object[]", isMandatory: true, description: "New content to write.")
            ],
            examples: ["Set-Content -Path './version.txt' -Value '1.5.0' -NoNewline"]
        ),
        CmdletHelpTopic(
            name: "Test-Path",
            moduleName: "Microsoft.PowerShell.Management",
            synopsis: "Determines whether all elements of a filesystem or provider path exist.",
            syntax: ["Test-Path [-Path] <String[]> [-PathType {Any | Container | Leaf}]"],
            parameters: [
                CmdletHelpParameter(name: "PathType", typeName: "TestPathType", description: "Checks specifically for Leaf (file) or Container (directory).")
            ],
            examples: ["if (Test-Path './build' -PathType Container) { Remove-Item './build' -Recurse }"]
        ),
        CmdletHelpTopic(
            name: "Sort-Object",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Sorts objects by property values in ascending or descending order.",
            syntax: ["Sort-Object [[-Property] <Object[]>] [-Descending] [-Unique]"],
            parameters: [
                CmdletHelpParameter(name: "Descending", typeName: "Switch", description: "Sorts in descending order.")
            ],
            examples: ["Get-ChildItem | Sort-Object Length -Descending"]
        ),
        CmdletHelpTopic(
            name: "Group-Object",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Groups objects that contain the same value for specified properties.",
            syntax: ["Group-Object [[-Property] <Object[]>] [-NoElement] [-AsHashTable]"],
            examples: ["Get-Service | Group-Object Status"]
        ),
        CmdletHelpTopic(
            name: "Measure-Object",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Calculates numeric statistics (Count, Average, Sum, Minimum, Maximum) or text counts.",
            syntax: ["Measure-Object [[-Property] <String[]>] [-Sum] [-Average] [-Maximum] [-Minimum] [-Line] [-Word] [-Character]"],
            examples: ["Get-ChildItem -File | Measure-Object -Property Length -Sum -Average"]
        ),
        CmdletHelpTopic(
            name: "Select-String",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Finds text and regular expression matches in strings and files (PowerShell's structured grep).",
            syntax: ["Select-String [-Pattern] <String[]> [-Path <String[]>] [-CaseSensitive] [-SimpleMatch] [-Context <Int32[]>]"],
            examples: ["Select-String -Path './*.ps1' -Pattern 'Invoke-RestMethod'"]
        ),
        CmdletHelpTopic(
            name: "Get-Process",
            moduleName: "Microsoft.PowerShell.Management",
            synopsis: "Gets the processes currently running on the local computer.",
            syntax: ["Get-Process [[-Name] <String[]>] [-Id <Int32[]>]"],
            examples: ["Get-Process -Name 'pwsh*' | Select-Object Id, ProcessName, WorkingSet64"]
        ),
        CmdletHelpTopic(
            name: "Get-Help",
            moduleName: "Microsoft.PowerShell.Core",
            synopsis: "Displays information about PowerShell commands and conceptual topics.",
            syntax: ["Get-Help [[-Name] <String>] [-Examples] [-Detailed] [-Full] [-Online]"],
            examples: ["Get-Help Invoke-RestMethod -Examples"]
        ),
        CmdletHelpTopic(
            name: "Get-Command",
            moduleName: "Microsoft.PowerShell.Core",
            synopsis: "Gets all cmdlets, functions, aliases, and executables available in the session.",
            syntax: ["Get-Command [[-Name] <String[]>] [-Module <String[]>] [-CommandType <CommandTypes>]"],
            examples: ["Get-Command -Verb Invoke -Module Microsoft.PowerShell.Utility"]
        ),
        CmdletHelpTopic(
            name: "Get-Member",
            moduleName: "Microsoft.PowerShell.Utility",
            synopsis: "Inspects the .NET type, properties, and methods of objects on the pipeline.",
            syntax: ["Get-Member [[-Name] <String[]>] [-MemberType <PSMemberTypes>]"],
            examples: ["(Get-Date) | Get-Member -MemberType Method"]
        ),
        CmdletHelpTopic(
            name: "Join-Path",
            moduleName: "Microsoft.PowerShell.Management",
            synopsis: "Combines a path and one or more child-paths into a single platform-normalized path.",
            syntax: ["Join-Path [-Path] <String[]> [-ChildPath] <String> [-AdditionalChildPath <String[]>] [-Resolve]"],
            examples: ["Join-Path -Path $HOME -ChildPath '.config/powershell'"]
        ),
        CmdletHelpTopic(
            name: "Invoke-Pester",
            moduleName: "Pester",
            synopsis: "Runs Pester unit and integration test suites (*.Tests.ps1) and reports pass/fail results.",
            syntax: ["Invoke-Pester [[-Path] <String[]>] [-Output {None | Minimal | Normal | Detailed | Diagnostic}] [-PassThru]"],
            examples: ["Invoke-Pester -Path './Deploy.Tests.ps1' -Output Detailed"]
        ),
        CmdletHelpTopic(
            name: "Should",
            moduleName: "Pester",
            synopsis: "Pester assertion command used inside `It` blocks to verify expected values or exceptions.",
            syntax: ["<Actual> | Should [-Not] [-Be <Object>] [-BeExactly <Object>] [-BeTrue] [-BeFalse] [-BeNullOrEmpty] [-Match <String>] [-Contain <Object>] [-Throw]"],
            examples: ["(2 + 2) | Should -Be 4", "{ throw 'boom' } | Should -Throw"]
        )
    ]
}
