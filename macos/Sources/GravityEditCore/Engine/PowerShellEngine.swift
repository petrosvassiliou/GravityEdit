import Foundation

public enum PowerShellTokenType: Sendable {
    case whitespace
    case keyword
    case cmdlet
    case variableNormal
    case variableScoped
    case variableAutomatic
    case parameter
    case `operator`
    case stringLiteral
    case stringExpandable
    case stringInterpolation
    case number
    case typeAttribute
    case hashtablePrefix
    case comment
    case commentHelpTag
    case regionDirective
    case punctuation
    case identifier
}

public struct PowerShellToken: Equatable, Sendable {
    public let type: PowerShellTokenType
    /// UTF-16 start offset within the line
    public let start: Int
    /// UTF-16 end offset within the line
    public let end: Int
    public let text: String

    public var nsRange: NSRange {
        NSRange(location: start, length: max(0, end - start))
    }

    public init(type: PowerShellTokenType, start: Int, end: Int, text: String) {
        self.type = type
        self.start = start
        self.end = end
        self.text = text
    }
}

public enum LineLexState: Equatable, Sendable {
    case normal
    case inBlockComment
    case inExpandableHereString
    case inLiteralHereString
}

public enum PowerShellEngine {

    // MARK: - Canonical Vocabularies

    public static let approvedVerbs: Set<String> = [
        "add", "clear", "close", "copy", "enter", "exit", "find", "format", "get", "hide",
        "join", "lock", "move", "new", "open", "optimize", "pop", "push", "redo", "remove",
        "rename", "reset", "resize", "search", "select", "set", "show", "skip", "split",
        "step", "switch", "undo", "unlock", "watch",
        "connect", "disconnect", "read", "receive", "send", "write",
        "backup", "checkpoint", "compare", "compress", "convert", "convertfrom", "convertto",
        "dismount", "edit", "expand", "export", "group", "import", "initialize", "limit",
        "merge", "mount", "out", "publish", "restore", "save", "sync", "unpublish", "update",
        "debug", "measure", "ping", "repair", "resolve", "test", "trace",
        "approve", "assert", "build", "complete", "confirm", "deploy", "disable", "enable",
        "install", "invoke", "register", "request", "restart", "resume", "start", "stop",
        "submit", "suspend", "uninstall", "unregister", "wait",
        "block", "grant", "protect", "revoke", "unblock", "unprotect",
        "use", "foreach", "where", "sort", "tee"
    ]

    public static let builtinAliases: Set<String> = [
        "%", "?", "ac", "cat", "cd", "chdir", "clc", "clear", "clhy", "cli", "clp", "cls",
        "clv", "cnsn", "compare", "copy", "cp", "cpi", "cpp", "curl", "cvpa", "dbp", "del",
        "diff", "dir", "dnsn", "ebp", "echo", "epal", "epcsv", "epsn", "erase", "etsn",
        "exsn", "fc", "fhx", "fl", "ft", "fw", "gal", "gbp", "gc", "gcb", "gci", "gcm",
        "gcs", "gdr", "ghy", "gi", "gjb", "gl", "gm", "gmo", "gp", "gps", "gpv", "group",
        "gsn", "gsnp", "gsv", "gtz", "gu", "gv", "gwmi", "h", "history", "icm", "iex",
        "ihy", "ii", "ipal", "ipcsv", "ipmo", "ipsn", "irm", "ise", "iwmi", "iwr", "kill",
        "lp", "ls", "man", "md", "measure", "mi", "mount", "move", "mp", "mv", "nal",
        "ndr", "ni", "nmo", "npssc", "nsn", "nv", "ogv", "oh", "popd", "ps", "pushd",
        "pwd", "r", "rbp", "rcjb", "rcsn", "rd", "rdr", "ren", "ri", "rjb", "rm", "rmdir",
        "rmo", "rni", "rnp", "rp", "rsn", "rsnp", "rujb", "rv", "rvpa", "rwmi", "sajb",
        "sal", "saps", "sasv", "sbp", "sc", "scb", "select", "set", "shcm", "si", "sl",
        "sleep", "sls", "sort", "sp", "spjb", "spps", "spsv", "start", "stz", "sujb",
        "sv", "swmi", "tee", "trcm", "type", "wget", "where", "wjb", "write"
    ]

    public static let powershellKeywords: Set<String> = [
        "begin", "break", "catch", "class", "clean", "configuration", "continue", "data",
        "define", "do", "dynamicparam", "else", "elseif", "end", "enum", "exit", "filter",
        "finally", "for", "foreach", "from", "function", "hidden", "if", "in",
        "inlinescript", "interface", "module", "namespace", "parallel", "param", "private",
        "process", "public", "return", "sequence", "static", "switch", "throw", "trap",
        "try", "type", "until", "using", "var", "while", "workflow"
    ]

    public static let automaticVariables: Set<String> = [
        "$_", "$psitem", "$true", "$false", "$null", "$args", "$error",
        "$psscriptroot", "$pscommandpath", "$psversiontable", "$host", "$home",
        "$pid", "$profile", "$psculture", "$psuiculture", "$psedition",
        "$islinux", "$ismacos", "$iswindows", "$iscoreclr", "$lastexitcode",
        "$?", "$^", "$$", "$matches", "$input", "$myinvocation",
        "$executioncontext", "$foreach", "$switch", "$this", "$stacktrace",
        "$ofs", "$psboundparameters", "$pscmdlet", "$psdebugcontext",
        "$psdefaultparametervalues", "$psemailserver", "$pshome", "$pssessionapplicationname",
        "$pssessionconfigurationname", "$pssessionoption", "$pwd", "$sender", "$event",
        "$eventargs", "$eventsubscriber", "$nestedpromptlevel"
    ]

    public static let variableScopes: Set<String> = [
        "env", "global", "script", "private", "local", "using", "workflow", "variable",
        "function", "alias", "hkcu", "hklm", "cert"
    ]

    public static let powershellOperators: Set<String> = [
        "-eq", "-ne", "-gt", "-ge", "-lt", "-le",
        "-ieq", "-ine", "-igt", "-ige", "-ilt", "-ile",
        "-ceq", "-cne", "-cgt", "-cge", "-clt", "-cle",
        "-like", "-notlike", "-ilike", "-inotlike", "-clike", "-cnotlike",
        "-match", "-notmatch", "-imatch", "-inotmatch", "-cmatch", "-cnotmatch",
        "-replace", "-ireplace", "-creplace",
        "-contains", "-notcontains", "-icontains", "-inotcontains", "-ccontains", "-cnotcontains",
        "-in", "-notin", "-iin", "-inotin", "-cin", "-cnotin",
        "-is", "-isnot", "-as",
        "-and", "-or", "-xor", "-not",
        "-band", "-bor", "-bxor", "-bnot", "-shl", "-shr",
        "-split", "-isplit", "-csplit", "-join", "-f"
    ]

    public static let aliasToCanonicalCmdlet: [String: String] = [
        "gci": "Get-ChildItem",
        "dir": "Get-ChildItem",
        "ls": "Get-ChildItem",
        "gc": "Get-Content",
        "cat": "Get-Content",
        "sc": "Set-Content",
        "ac": "Add-Content",
        "gi": "Get-Item",
        "ni": "New-Item",
        "ri": "Remove-Item",
        "rm": "Remove-Item",
        "del": "Remove-Item",
        "cpi": "Copy-Item",
        "cp": "Copy-Item",
        "mi": "Move-Item",
        "mv": "Move-Item",
        "gps": "Get-Process",
        "ps": "Get-Process",
        "saps": "Start-Process",
        "spps": "Stop-Process",
        "kill": "Stop-Process",
        "gsv": "Get-Service",
        "irm": "Invoke-RestMethod",
        "iwr": "Invoke-WebRequest",
        "curl": "Invoke-WebRequest",
        "wget": "Invoke-WebRequest",
        "iex": "Invoke-Expression",
        "icm": "Invoke-Command",
        "gcm": "Get-Command",
        "gm": "Get-Member",
        "where": "Where-Object",
        "?": "Where-Object",
        "foreach": "ForEach-Object",
        "%": "ForEach-Object",
        "select": "Select-Object",
        "sort": "Sort-Object",
        "group": "Group-Object",
        "measure": "Measure-Object",
        "ft": "Format-Table",
        "fl": "Format-List",
        "fw": "Format-Wide",
        "ogv": "Out-GridView",
        "cls": "Clear-Host",
        "clear": "Clear-Host",
        "write": "Write-Output",
        "echo": "Write-Output",
        "sleep": "Start-Sleep"
    ]

    // MARK: - Compiled Regular Expressions

    private static let helpTagRegex = try! NSRegularExpression(
        pattern: #"\.(SYNOPSIS|DESCRIPTION|PARAMETER|EXAMPLE|INPUTS|OUTPUTS|NOTES|LINK|COMPONENT|ROLE|FUNCTIONALITY|FORWARDHELPTARGETNAME|FORWARDHELPCATEGORY|REMOTEHELPRUNSPACE|EXTERNALHELP)\b"#,
        options: [.caseInsensitive]
    )

    private static let functionDeclRegex = try! NSRegularExpression(
        pattern: #"^\s*(function|filter|workflow|configuration)\s+([\p{L}\p{N}_\-:.]+)\s*(\(([^)]*)\))?"#,
        options: [.caseInsensitive]
    )

    private static let classDeclRegex = try! NSRegularExpression(
        pattern: #"^\s*(class|enum)\s+([\p{L}\p{N}_\-]+)"#,
        options: [.caseInsensitive]
    )

    private static let scriptblockDeclRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:\[\s*scriptblock\s*\]\s*)?(\$(?:(?:global|script|private|local):)?[\p{L}_][\p{L}\p{N}_]*)\s*=\s*\{"#,
        options: [.caseInsensitive]
    )

    private static let paramBlockStartRegex = try! NSRegularExpression(
        pattern: #"^\s*param\s*\("#,
        options: [.caseInsensitive]
    )

    private static let paramKeywordParenRegex = try! NSRegularExpression(
        pattern: #"\bparam\s*\("#,
        options: [.caseInsensitive]
    )

    private static let pipelineBlockRegex = try! NSRegularExpression(
        pattern: #"^\s*(begin|process|end|clean)\s*(?:\{|$)"#,
        options: [.caseInsensitive]
    )

    private static let paramVariableNameRegex = try! NSRegularExpression(
        pattern: #"\$(?:(?:global|script|private|local):)?[\p{L}_][\p{L}\p{N}_]*"#
    )

    private static let collectionParenDeclRegex = try! NSRegularExpression(
        pattern: #"^\s*(\$(?:(?:global|script|private|local):)?[\p{L}_][\p{L}\p{N}_]*)\s*=\s*@\("#,
        options: [.caseInsensitive]
    )

    private static let collectionBraceDeclRegex = try! NSRegularExpression(
        pattern: #"^\s*(\$(?:(?:global|script|private|local):)?[\p{L}_][\p{L}\p{N}_]*)\s*=\s*@\{"#,
        options: [.caseInsensitive]
    )

    private static let scalarVarAssignRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:\[[A-Za-z0-9_.\[\],\s]+\]\s*)*(\$(?:(?:global|script|private|local):)?[\p{L}_][\p{L}\p{N}_]*)\s*=(?!=)"#,
        options: [.caseInsensitive]
    )

    private static let controlBlockHeadRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:(foreach|for|while|do|switch|try)\b|(if)\s*\().*$"#,
        options: [.caseInsensitive]
    )

    private static let pipelineForEachHeadRegex = try! NSRegularExpression(
        pattern: #"^.+\|\s*(?:ForEach-Object|%)(?:\s+-Process)?\s*\{?\s*$"#,
        options: [.caseInsensitive]
    )

    private static let pureDividerCommentRegex = try! NSRegularExpression(
        pattern: #"^\s*#+\s*([=\-*~_])\1{3,}\s*#*\s*$"#
    )

    private static let bannerSectionCommentRegex = try! NSRegularExpression(
        pattern: #"^\s*#+\s*[=\-*~_]{3,}\s*([^\s#=\-*~_].*?)\s*[=\-*~_]*\s*#*\s*$"#
    )

    private static let commentBoxTitleLineRegex = try! NSRegularExpression(
        pattern: #"^\s*#+\s*([^\s#=\-*~_].*?)\s*#*\s*$"#
    )

    private static let stepSectionCommentRegex = try! NSRegularExpression(
        pattern: #"^\s*#+\s*((?:(?:Step|Phase|Stage|Part|Section|Schritt|Abschnitt|Teil|Kapitel)\s+\d+\b|\[\d+(?:/\d+)?\])\s*[:\-–—.]?\s*\S.*?)\s*$"#,
        options: [.caseInsensitive]
    )

    private static let regionStartRegex = try! NSRegularExpression(
        pattern: #"^\s*#region\b(.*)$"#,
        options: [.caseInsensitive]
    )

    private static let regionEndRegex = try! NSRegularExpression(
        pattern: #"^\s*#endregion\b"#,
        options: [.caseInsensitive]
    )

    private static let swiftFuncRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:@[A-Za-z0-9_()]+\s+)*(?:(?:public|private|internal|fileprivate|open|static|class|final|override|mutating|nonmutating)\s+)*func\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^>]+>)?\s*\(([^)]*)\)"#
    )

    private static let swiftTypeRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:@[A-Za-z0-9_()]+\s+)*(?:(?:public|private|internal|fileprivate|open|final|indirect)\s+)*(struct|class|enum|actor|protocol|extension)\s+([A-Za-z_][A-Za-z0-9_.]*)"#
    )

    private static let pythonDefRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:async\s+)?def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)"#
    )

    private static let pythonClassRegex = try! NSRegularExpression(
        pattern: #"^\s*class\s+([A-Za-z_][A-Za-z0-9_]*)"#
    )

    private static let kotlinFunRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:(?:public|private|protected|internal|override|suspend|inline|operator|infix|tailrec|open|abstract)\s+)*fun\s+(?:<[^>]+>\s+)?(?:[A-Za-z0-9_<>?.*]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)"#
    )

    private static let kotlinTypeRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:(?:public|private|protected|internal|data|sealed|abstract|open|value|enum|annotation|inner)\s+)*(class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)"#
    )

    private static let bashFunctionRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:function\s+([A-Za-z_][A-Za-z0-9_\-]*)\s*(?:\(\s*\))?|([A-Za-z_][A-Za-z0-9_\-]*)\s*\(\s*\))\s*\{?"#
    )

    private static let markdownHeadingRegex = try! NSRegularExpression(
        pattern: #"^(#{1,4})\s+(.+?)\s*#*$"#
    )

    private static let userVariableExtractRegex = try! NSRegularExpression(
        pattern: #"\$(?:(?:env|global|script|private|local):)?[\p{L}_][\p{L}\p{N}_]*"#
    )

    private static let functionVerbCheckRegex = try! NSRegularExpression(
        pattern: #"^\s*(?:function|filter|workflow)\s+(?:(?:global|script|private|local):)?([A-Za-z0-9_]+)-([A-Za-z0-9_]+)"#,
        options: [.caseInsensitive]
    )

    private static let emptyCatchSingleLineRegex = try! NSRegularExpression(
        pattern: #"\bcatch\b(?:\s*\[[^\]]+\])?\s*\{\s*(?:#.*)?\}"#,
        options: [.caseInsensitive]
    )

    private static let plainTextPasswordParamRegex = try! NSRegularExpression(
        pattern: #"\[string\]\s*\$(?:[A-Za-z0-9_]*(?:Password|Secret|ApiKey|Token|Passphrase)[A-Za-z0-9_]*)"#,
        options: [.caseInsensitive]
    )

    // MARK: - Line Lex State Machine

    public static func computeLineStates(_ lines: [String], language: SyntaxLanguage) -> [LineLexState] {
        guard !lines.isEmpty else { return [] }
        guard language == .powershell else {
            return Array(repeating: .normal, count: lines.count)
        }
        var states: [LineLexState] = []
        states.reserveCapacity(lines.count)
        var current: LineLexState = .normal
        for line in lines {
            states.append(current)
            current = advanceLineState(line, startState: current)
        }
        return states
    }

    private static func advanceLineState(_ line: String, startState: LineLexState) -> LineLexState {
        let ns = line as NSString
        let len = ns.length
        var state = startState
        var i = 0

        switch state {
        case .inBlockComment:
            let closeRange = ns.range(of: "#>")
            if closeRange.location == NSNotFound {
                return .inBlockComment
            }
            state = .normal
            i = closeRange.location + 2
        case .inExpandableHereString:
            let trimmed = line.drop(while: { $0 == " " || $0 == "\t" })
            return trimmed.hasPrefix("\"@") ? .normal : .inExpandableHereString
        case .inLiteralHereString:
            let trimmed = line.drop(while: { $0 == " " || $0 == "\t" })
            return trimmed.hasPrefix("'@") ? .normal : .inLiteralHereString
        case .normal:
            break
        }

        while i < len {
            let ch = ns.character(at: i)
            let next: unichar = (i + 1 < len) ? ns.character(at: i + 1) : 0

            // '#' single-line comment
            if ch == 35 && (i == 0 || ns.character(at: i - 1) != 60) {
                break
            }
            // '<#' block comment
            if ch == 60 && next == 35 {
                let searchRange = NSRange(location: i + 2, length: max(0, len - (i + 2)))
                let closeRange = ns.range(of: "#>", options: [], range: searchRange)
                if closeRange.location != NSNotFound {
                    i = closeRange.location + 2
                    continue
                } else {
                    return .inBlockComment
                }
            }
            // '@"' or '@\'' here-string at end of line
            if ch == 64 && (next == 34 || next == 39) {
                let trailing = ns.substring(from: i + 2).trimmingCharacters(in: .whitespaces)
                if trailing.isEmpty {
                    return next == 34 ? .inExpandableHereString : .inLiteralHereString
                }
            }
            // '\'' literal string
            if ch == 39 {
                i += 1
                while i < len {
                    if ns.character(at: i) == 39 && i + 1 < len && ns.character(at: i + 1) == 39 {
                        i += 2
                        continue
                    }
                    if ns.character(at: i) == 39 {
                        i += 1
                        break
                    }
                    i += 1
                }
                continue
            }
            // '"' expandable string
            if ch == 34 {
                i += 1
                while i < len {
                    if ns.character(at: i) == 96 && i + 1 < len { // backtick `
                        i += 2
                        continue
                    }
                    if ns.character(at: i) == 34 && i + 1 < len && ns.character(at: i + 1) == 34 {
                        i += 2
                        continue
                    }
                    if ns.character(at: i) == 34 {
                        i += 1
                        break
                    }
                    i += 1
                }
                continue
            }
            i += 1
        }
        return state
    }

    // MARK: - PowerShell Line Tokenizer

    public static func tokenizePowerShellLine(
        _ line: String,
        startState: LineLexState = .normal
    ) -> [PowerShellToken] {
        let ns = line as NSString
        let len = ns.length
        guard len > 0 else { return [] }

        var tokens: [PowerShellToken] = []
        var i = 0

        switch startState {
        case .inBlockComment:
            let closeRange = ns.range(of: "#>")
            if closeRange.location == NSNotFound {
                appendBlockCommentTokens(ns: ns, start: 0, end: len, into: &tokens)
                return tokens
            } else {
                appendBlockCommentTokens(ns: ns, start: 0, end: closeRange.location + 2, into: &tokens)
                i = closeRange.location + 2
            }
        case .inExpandableHereString:
            appendExpandableStringTokens(ns: ns, start: 0, end: len, into: &tokens)
            return tokens
        case .inLiteralHereString:
            tokens.append(PowerShellToken(type: .stringLiteral, start: 0, end: len, text: line))
            return tokens
        case .normal:
            break
        }

        while i < len {
            let ch = ns.character(at: i)
            let next: unichar = (i + 1 < len) ? ns.character(at: i + 1) : 0

            // 1. Whitespace
            if isUTF16Whitespace(ch) {
                let start = i
                while i < len && isUTF16Whitespace(ns.character(at: i)) { i += 1 }
                let sub = ns.substring(with: NSRange(location: start, length: i - start))
                tokens.append(PowerShellToken(type: .whitespace, start: start, end: i, text: sub))
                continue
            }

            // 2. Block comment <# ... #>
            if ch == 60 && next == 35 {
                let search = NSRange(location: i + 2, length: max(0, len - (i + 2)))
                let closeRange = ns.range(of: "#>", options: [], range: search)
                if closeRange.location != NSNotFound {
                    appendBlockCommentTokens(ns: ns, start: i, end: closeRange.location + 2, into: &tokens)
                    i = closeRange.location + 2
                } else {
                    appendBlockCommentTokens(ns: ns, start: i, end: len, into: &tokens)
                    i = len
                }
                continue
            }

            // 3. Single-line comment '#' or '#region'
            if ch == 35 {
                let rest = ns.substring(from: i)
                let lower = rest.lowercased()
                let type: PowerShellTokenType = (lower.hasPrefix("#region") || lower.hasPrefix("#endregion")) ? .regionDirective : .comment
                tokens.append(PowerShellToken(type: type, start: i, end: len, text: rest))
                break
            }

            // 4. Here-string / Hashtable / Array splat @
            if ch == 64 {
                if next == 34 {
                    appendExpandableStringTokens(ns: ns, start: i, end: len, into: &tokens)
                    break
                } else if next == 39 {
                    tokens.append(PowerShellToken(type: .stringLiteral, start: i, end: len, text: ns.substring(from: i)))
                    break
                } else if next == 123 || next == 40 { // '{' or '('
                    let sub = ns.substring(with: NSRange(location: i, length: 2))
                    tokens.append(PowerShellToken(type: .hashtablePrefix, start: i, end: i + 2, text: sub))
                    i += 2
                    continue
                }
            }

            // 5. Single-quoted literal string
            if ch == 39 {
                let start = i
                i += 1
                while i < len {
                    if ns.character(at: i) == 39 && i + 1 < len && ns.character(at: i + 1) == 39 {
                        i += 2
                        continue
                    }
                    if ns.character(at: i) == 39 {
                        i += 1
                        break
                    }
                    i += 1
                }
                let sub = ns.substring(with: NSRange(location: start, length: i - start))
                tokens.append(PowerShellToken(type: .stringLiteral, start: start, end: i, text: sub))
                continue
            }

            // 6. Double-quoted expandable string
            if ch == 34 {
                let start = i
                i += 1
                var subDepth = 0
                while i < len {
                    if ns.character(at: i) == 96 && i + 1 < len {
                        i += 2
                        continue
                    }
                    if ns.character(at: i) == 36 && i + 1 < len && ns.character(at: i + 1) == 40 { // $(
                        subDepth += 1
                        i += 2
                        continue
                    }
                    if subDepth > 0 && ns.character(at: i) == 41 { // )
                        subDepth -= 1
                        i += 1
                        continue
                    }
                    if subDepth == 0 && ns.character(at: i) == 34 && i + 1 < len && ns.character(at: i + 1) == 34 {
                        i += 2
                        continue
                    }
                    if subDepth == 0 && ns.character(at: i) == 34 {
                        i += 1
                        break
                    }
                    i += 1
                }
                appendExpandableStringTokens(ns: ns, start: start, end: i, into: &tokens)
                continue
            }

            // 7. Variables ($var, ${var}, $env:PATH, $_, $true, $()
            if ch == 36 { // '$'
                if next == 40 { // '('
                    tokens.append(PowerShellToken(type: .operator, start: i, end: i + 2, text: "$("))
                    i += 2
                    continue
                }
                let varEnd = scanVariableEnd(ns: ns, from: i)
                if varEnd > i + 1 {
                    let rawVar = ns.substring(with: NSRange(location: i, length: varEnd - i))
                    tokens.append(PowerShellToken(type: classifyVariable(rawVar), start: i, end: varEnd, text: rawVar))
                    i = varEnd
                    continue
                }
            }

            // 8. Type Accelerators & Attributes [CmdletBinding()], [string]
            if ch == 91 { // '['
                let bracketEnd = findMatchingBracket(ns: ns, from: i)
                if bracketEnd > i + 1 {
                    let inside = ns.substring(with: NSRange(location: i + 1, length: bracketEnd - i - 2)).trimmingCharacters(in: .whitespaces)
                    if let firstScalar = inside.unicodeScalars.first,
                       CharacterSet.letters.contains(firstScalar) || inside.hasPrefix("System.") {
                        let raw = ns.substring(with: NSRange(location: i, length: bracketEnd - i))
                        tokens.append(PowerShellToken(type: .typeAttribute, start: i, end: bracketEnd, text: raw))
                        i = bracketEnd
                        continue
                    }
                }
            }

            // 9. Dash parameters (-Path) & PowerShell operators (-eq, -match)
            if ch == 45 && isAlphaUTF16(next) { // '-'
                let prevIsBoundary = (i == 0) || isUTF16Whitespace(ns.character(at: i - 1)) || "({|;=".utf16.contains(ns.character(at: i - 1))
                if prevIsBoundary {
                    let start = i
                    i += 1
                    while i < len && (isAlnumUTF16(ns.character(at: i)) || ns.character(at: i) == 95) {
                        i += 1
                    }
                    let word = ns.substring(with: NSRange(location: start, length: i - start))
                    let type: PowerShellTokenType = powershellOperators.contains(word.lowercased()) ? .operator : .parameter
                    tokens.append(PowerShellToken(type: type, start: start, end: i, text: word))
                    continue
                }
            }

            // 10. Numbers (42, 0xFF, 10MB, 3.14)
            if isDigitUTF16(ch) {
                let start = i
                i += 1
                while i < len && (isAlnumUTF16(ns.character(at: i)) || ns.character(at: i) == 46) {
                    i += 1
                }
                let num = ns.substring(with: NSRange(location: start, length: i - start))
                tokens.append(PowerShellToken(type: .number, start: start, end: i, text: num))
                continue
            }

            // 11. Identifiers, Keywords, Verb-Noun Cmdlets, Built-in Aliases
            if isIdentifierStartUTF16(ch) || ch == 63 || ch == 37 { // '?' or '%'
                if ch == 63 || ch == 37 {
                    let nextBoundary = (i + 1 == len) || isUTF16Whitespace(next) || next == 123
                    if nextBoundary {
                        let sym = ns.substring(with: NSRange(location: i, length: 1))
                        tokens.append(PowerShellToken(type: .cmdlet, start: i, end: i + 1, text: sym))
                        i += 1
                        continue
                    }
                }
                let start = i
                i += 1
                while i < len {
                    let c = ns.character(at: i)
                    if isIdentifierPartUTF16(c) || c == 45 || c == 46 || c == 58 { // '-', '.', ':'
                        i += 1
                    } else {
                        break
                    }
                }
                let word = ns.substring(with: NSRange(location: start, length: i - start))
                let lower = word.lowercased()
                let tokenType: PowerShellTokenType
                if powershellKeywords.contains(lower) {
                    tokenType = .keyword
                } else if isCmdletWord(lower) || builtinAliases.contains(lower) {
                    tokenType = .cmdlet
                } else {
                    tokenType = .identifier
                }
                tokens.append(PowerShellToken(type: tokenType, start: start, end: i, text: word))
                continue
            }

            // 12. Punctuation & Symbol Operators
            let start = i
            if (ch == 43 || ch == 45 || ch == 42 || ch == 47 || ch == 61 || ch == 33) && next == 61 {
                let op = ns.substring(with: NSRange(location: start, length: 2))
                tokens.append(PowerShellToken(type: .operator, start: start, end: start + 2, text: op))
                i += 2
                continue
            }
            if ch == 124 || ch == 61 { // '|' or '='
                let op = ns.substring(with: NSRange(location: start, length: 1))
                tokens.append(PowerShellToken(type: .operator, start: start, end: start + 1, text: op))
                i += 1
                continue
            }
            let punc = ns.substring(with: NSRange(location: start, length: 1))
            tokens.append(PowerShellToken(type: .punctuation, start: start, end: start + 1, text: punc))
            i += 1
        }

        return tokens
    }

    private static func isCmdletWord(_ lowerWord: String) -> Bool {
        guard let dashIdx = lowerWord.firstIndex(of: "-") else { return false }
        let verbPart = String(lowerWord[..<dashIdx]).components(separatedBy: ":").last ?? ""
        let nounPart = String(lowerWord[lowerWord.index(after: dashIdx)...])
        return !verbPart.isEmpty && !nounPart.isEmpty && approvedVerbs.contains(verbPart)
    }

    private static func appendBlockCommentTokens(
        ns: NSString,
        start: Int,
        end: Int,
        into tokens: inout [PowerShellToken]
    ) {
        guard end > start else { return }
        let range = NSRange(location: start, length: end - start)
        let matches = helpTagRegex.matches(in: ns as String, options: [], range: range)
        if matches.isEmpty {
            tokens.append(PowerShellToken(type: .comment, start: start, end: end, text: ns.substring(with: range)))
            return
        }
        var cursor = start
        for match in matches {
            let mRange = match.range
            if mRange.location > cursor {
                let pre = NSRange(location: cursor, length: mRange.location - cursor)
                tokens.append(PowerShellToken(type: .comment, start: cursor, end: mRange.location, text: ns.substring(with: pre)))
            }
            let tagEnd = mRange.location + mRange.length
            tokens.append(PowerShellToken(type: .commentHelpTag, start: mRange.location, end: tagEnd, text: ns.substring(with: mRange)))
            cursor = tagEnd
        }
        if cursor < end {
            let tail = NSRange(location: cursor, length: end - cursor)
            tokens.append(PowerShellToken(type: .comment, start: cursor, end: end, text: ns.substring(with: tail)))
        }
    }

    private static func appendExpandableStringTokens(
        ns: NSString,
        start: Int,
        end: Int,
        into tokens: inout [PowerShellToken]
    ) {
        var cursor = start
        var i = start
        while i < end {
            let ch = ns.character(at: i)
            if ch == 96 && i + 1 < end { // backtick escape
                i += 2
                continue
            }
            if ch == 36 && i + 1 < end { // '$'
                let next = ns.character(at: i + 1)
                if next == 40 { // $(...)
                    var depth = 1
                    var j = i + 2
                    while j < end && depth > 0 {
                        let cj = ns.character(at: j)
                        if cj == 40 { depth += 1 }
                        else if cj == 41 { depth -= 1 }
                        j += 1
                    }
                    if i > cursor {
                        let r = NSRange(location: cursor, length: i - cursor)
                        tokens.append(PowerShellToken(type: .stringExpandable, start: cursor, end: i, text: ns.substring(with: r)))
                    }
                    let subR = NSRange(location: i, length: j - i)
                    tokens.append(PowerShellToken(type: .stringInterpolation, start: i, end: j, text: ns.substring(with: subR)))
                    cursor = j
                    i = j
                    continue
                } else {
                    let varEnd = min(end, scanVariableEnd(ns: ns, from: i))
                    if varEnd > i + 1 {
                        if i > cursor {
                            let r = NSRange(location: cursor, length: i - cursor)
                            tokens.append(PowerShellToken(type: .stringExpandable, start: cursor, end: i, text: ns.substring(with: r)))
                        }
                        let vR = NSRange(location: i, length: varEnd - i)
                        tokens.append(PowerShellToken(type: .stringInterpolation, start: i, end: varEnd, text: ns.substring(with: vR)))
                        cursor = varEnd
                        i = varEnd
                        continue
                    }
                }
            }
            i += 1
        }
        if cursor < end {
            let r = NSRange(location: cursor, length: end - cursor)
            tokens.append(PowerShellToken(type: .stringExpandable, start: cursor, end: end, text: ns.substring(with: r)))
        }
    }

    private static func scanVariableEnd(ns: NSString, from dollarIndex: Int) -> Int {
        let len = ns.length
        guard dollarIndex + 1 < len else { return dollarIndex + 1 }
        let second = ns.character(at: dollarIndex + 1)
        if second == 123 { // '{' -> ${...}
            var j = dollarIndex + 2
            while j < len && ns.character(at: j) != 125 { j += 1 }
            return j < len ? j + 1 : j
        }
        if second == 63 || second == 94 || second == 36 { // $?, $^, $$
            return dollarIndex + 2
        }
        var j = dollarIndex + 1
        while j < len {
            let c = ns.character(at: j)
            if isIdentifierPartUTF16(c) || c == 58 { // ':'
                j += 1
            } else {
                break
            }
        }
        return j
    }

    private static func classifyVariable(_ rawVar: String) -> PowerShellTokenType {
        let lower = rawVar.lowercased()
        if automaticVariables.contains(lower) {
            return .variableAutomatic
        }
        let stripped = lower.trimmingCharacters(in: CharacterSet(charactersIn: "${}"))
        if let colon = stripped.firstIndex(of: ":") {
            let prefix = String(stripped[..<colon])
            if variableScopes.contains(prefix) {
                return .variableScoped
            }
        }
        return .variableNormal
    }

    private static func findMatchingBracket(ns: NSString, from openIdx: Int) -> Int {
        let len = ns.length
        var depth = 0
        var j = openIdx
        while j < len {
            let c = ns.character(at: j)
            if c == 91 { depth += 1 }
            else if c == 93 {
                depth -= 1
                if depth == 0 { return j + 1 }
            }
            j += 1
        }
        return -1
    }

    private static func isUTF16Whitespace(_ c: unichar) -> Bool {
        c == 32 || c == 9 || c == 13 || c == 160
    }

    private static func isAlphaUTF16(_ c: unichar) -> Bool {
        if (c >= 65 && c <= 90) || (c >= 97 && c <= 122) { return true }
        guard let scalar = UnicodeScalar(c) else { return false }
        return CharacterSet.letters.contains(scalar)
    }

    private static func isDigitUTF16(_ c: unichar) -> Bool {
        c >= 48 && c <= 57
    }

    private static func isAlnumUTF16(_ c: unichar) -> Bool {
        isDigitUTF16(c) || isAlphaUTF16(c)
    }

    private static func isIdentifierStartUTF16(_ c: unichar) -> Bool {
        c == 95 || isAlphaUTF16(c)
    }

    private static func isIdentifierPartUTF16(_ c: unichar) -> Bool {
        c == 95 || isAlnumUTF16(c)
    }

    // MARK: - AST & Procedural Script Function Navigator (v1.2.4+ Parity + Swift Support)

    public static func extractAstSymbols(
        _ content: String,
        language: SyntaxLanguage = .powershell
    ) -> [AstSymbol] {
        guard !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        let lines = content.components(separatedBy: "\n").map {
            $0.hasSuffix("\r") ? String($0.dropLast()) : $0
        }
        if language != .powershell {
            return extractSecondaryLanguageAstSymbols(lines: lines, language: language)
        }
        return extractPowerShellAstSymbols(lines: lines)
    }

    private static func extractPowerShellAstSymbols(lines: [String]) -> [AstSymbol] {
        let lineStates = computeLineStates(lines, language: .powershell)
        var symbols: [AstSymbol] = []
        var hasPrimaryCallableOrTypeDecls = false
        var currentBraceDepth = 0
        var currentParenDepth = 0

        for lineIdx in lines.indices {
            guard lineStates[lineIdx] == .normal else { continue }
            let line = lines[lineIdx]
            let trimmedStart = line.drop(while: { $0 == " " || $0 == "\t" })
            guard !trimmedStart.isEmpty else { continue }

            if trimmedStart.hasPrefix("<#") {
                let afterComments = stripStringsAndCommentsFromLine(line)
                if !trimmedStart.contains("#>") || afterComments.trimmingCharacters(in: .whitespaces).isEmpty {
                    continue
                }
            }

            if let regMatch = firstMatch(regionStartRegex, in: line) {
                let rawLabel = groupString(regMatch, index: 1, in: line).trimmingCharacters(in: .whitespaces)
                let label = rawLabel.isEmpty ? "Region @ Ln \(lineIdx + 1)" : rawLabel
                symbols.append(
                    AstSymbol(
                        name: label,
                        kind: .region,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: "#region \(label)"
                    )
                )
                continue
            }
            if firstMatch(regionEndRegex, in: line) != nil { continue }

            if trimmedStart.hasPrefix("#") {
                if let sectionTitle = detectSectionHeaderTitle(lines: lines, lineStates: lineStates, lineIdx: lineIdx) {
                    symbols.append(
                        AstSymbol(
                            name: sectionTitle,
                            kind: .section,
                            lineIndex: lineIdx,
                            lineNumber: lineIdx + 1,
                            signature: sectionTitle
                        )
                    )
                }
                continue
            }

            let codeOnly = stripStringsAndCommentsFromLine(line)
            guard !codeOnly.trimmingCharacters(in: .whitespaces).isEmpty else { continue }

            let braceBefore = currentBraceDepth
            let parenBefore = currentParenDepth
            func advanceDepths() {
                for ch in codeOnly {
                    switch ch {
                    case "{": currentBraceDepth += 1
                    case "}": currentBraceDepth = max(0, currentBraceDepth - 1)
                    case "(": currentParenDepth += 1
                    case ")": currentParenDepth = max(0, currentParenDepth - 1)
                    default: break
                    }
                }
            }

            if let fnMatch = firstMatch(functionDeclRegex, in: codeOnly) {
                hasPrimaryCallableOrTypeDecls = true
                let kw = groupString(fnMatch, index: 1, in: codeOnly).lowercased()
                let name = groupString(fnMatch, index: 2, in: codeOnly)
                let hasInlineParens = fnMatch.range(at: 3).location != NSNotFound
                let rawParams = groupString(fnMatch, index: 4, in: codeOnly).trimmingCharacters(in: .whitespaces)
                let kind: SymbolKind
                switch kw {
                case "filter": kind = .filter
                case "workflow": kind = .workflow
                case "configuration": kind = .configuration
                default: kind = .function
                }
                let paramsList: [String]
                let sig: String
                if hasInlineParens {
                    paramsList = rawParams.isEmpty
                        ? []
                        : rawParams.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                    sig = rawParams.isEmpty ? "\(name)()" : "\(name)(\(rawParams))"
                } else {
                    paramsList = extractFunctionBodyParamNames(lines: lines, lineStates: lineStates, fnDeclLineIdx: lineIdx)
                    sig = formatCallSignature(name: name, params: paramsList)
                }
                symbols.append(
                    AstSymbol(
                        name: name,
                        kind: kind,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: sig,
                        parameters: paramsList
                    )
                )
                advanceDepths()
                continue
            }

            if let clsMatch = firstMatch(classDeclRegex, in: codeOnly) {
                hasPrimaryCallableOrTypeDecls = true
                let kw = groupString(clsMatch, index: 1, in: codeOnly).lowercased()
                let name = groupString(clsMatch, index: 2, in: codeOnly)
                symbols.append(
                    AstSymbol(
                        name: name,
                        kind: .class,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: "\(kw) \(name)"
                    )
                )
                advanceDepths()
                continue
            }

            if let sbMatch = firstMatch(scriptblockDeclRegex, in: codeOnly) {
                hasPrimaryCallableOrTypeDecls = true
                let varName = groupString(sbMatch, index: 1, in: codeOnly)
                symbols.append(
                    AstSymbol(
                        name: varName,
                        kind: .scriptblock,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: "\(varName) = { … }"
                    )
                )
                advanceDepths()
                continue
            }

            if braceBefore == 0 && parenBefore == 0 {
                if firstMatch(paramBlockStartRegex, in: codeOnly) != nil {
                    let openParen = findUnquotedParamOpenParen(in: line)
                    let paramVars = openParen >= 0
                        ? extractParamNamesFromParenthesizedList(lines: lines, lineStates: lineStates, startLineIdx: lineIdx, openParenUTF16Offset: openParen)
                        : []
                    symbols.append(
                        AstSymbol(
                            name: "param",
                            kind: .paramBlock,
                            lineIndex: lineIdx,
                            lineNumber: lineIdx + 1,
                            signature: formatCallSignature(name: "param", params: paramVars),
                            parameters: paramVars
                        )
                    )
                    advanceDepths()
                    continue
                }

                if let pipeMatch = firstMatch(pipelineBlockRegex, in: codeOnly),
                   codeOnly.contains("{") || opensBraceOnNextCodeLine(lines: lines, lineStates: lineStates, fromLineIdx: lineIdx) {
                    let kw = groupString(pipeMatch, index: 1, in: codeOnly).lowercased()
                    symbols.append(
                        AstSymbol(
                            name: kw,
                            kind: .pipelineBlock,
                            lineIndex: lineIdx,
                            lineNumber: lineIdx + 1,
                            signature: "\(kw) { … }"
                        )
                    )
                    advanceDepths()
                    continue
                }
            }

            advanceDepths()
        }

        if !hasPrimaryCallableOrTypeDecls {
            let procedural = extractProceduralPowerShellLandmarks(lines: lines, lineStates: lineStates)
            if !procedural.isEmpty {
                symbols.append(contentsOf: procedural)
                symbols.sort { $0.lineIndex < $1.lineIndex }
            }
        }

        return symbols
    }

    private static func extractProceduralPowerShellLandmarks(
        lines: [String],
        lineStates: [LineLexState]
    ) -> [AstSymbol] {
        var landmarks: [AstSymbol] = []
        var currentBraceDepth = 0
        var currentParenDepth = 0

        var groupStartLineIdx = -1
        var groupVarNames: [String] = []
        var emittedInitialConfigGroup = false

        func flushVarGroup() {
            if !groupVarNames.isEmpty {
                var distinctVars: [String] = []
                for v in groupVarNames where !distinctVars.contains(v) {
                    distinctVars.append(v)
                }
                if !emittedInitialConfigGroup || distinctVars.count >= 3 {
                    let preview = Array(distinctVars.prefix(3)).joined(separator: ", ")
                    let extra = distinctVars.count - 3
                    let sig = extra > 0 ? "\(preview) (+\(extra))" : preview
                    let label = extra > 0 ? "\(preview)…" : preview
                    landmarks.append(
                        AstSymbol(
                            name: label,
                            kind: .configVars,
                            lineIndex: groupStartLineIdx,
                            lineNumber: groupStartLineIdx + 1,
                            signature: sig,
                            parameters: distinctVars
                        )
                    )
                    emittedInitialConfigGroup = true
                }
            }
            groupStartLineIdx = -1
            groupVarNames.removeAll(keepingCapacity: true)
        }

        for lineIdx in lines.indices {
            guard lineStates[lineIdx] == .normal else { continue }
            let line = lines[lineIdx]
            let trimmedStart = line.drop(while: { $0 == " " || $0 == "\t" })
            guard !trimmedStart.isEmpty else { continue }

            if trimmedStart.hasPrefix("<#") {
                let afterComments = stripStringsAndCommentsFromLine(line)
                if !trimmedStart.contains("#>") || afterComments.trimmingCharacters(in: .whitespaces).isEmpty {
                    continue
                }
            }

            if firstMatch(regionStartRegex, in: line) != nil || firstMatch(regionEndRegex, in: line) != nil {
                flushVarGroup()
                continue
            }

            if trimmedStart.hasPrefix("#") {
                if detectSectionHeaderTitle(lines: lines, lineStates: lineStates, lineIdx: lineIdx) != nil {
                    flushVarGroup()
                }
                continue
            }

            let codeOnly = stripStringsAndCommentsFromLine(line)
            guard !codeOnly.trimmingCharacters(in: .whitespaces).isEmpty else { continue }

            let braceBefore = currentBraceDepth
            let parenBefore = currentParenDepth
            var braceDelta = 0
            var parenDelta = 0
            for ch in codeOnly {
                switch ch {
                case "{": braceDelta += 1
                case "}": braceDelta -= 1
                case "(": parenDelta += 1
                case ")": parenDelta -= 1
                default: break
                }
            }
            currentBraceDepth = max(0, currentBraceDepth + braceDelta)
            currentParenDepth = max(0, currentParenDepth + parenDelta)

            guard braceBefore == 0 && parenBefore == 0 else { continue }

            if firstMatch(paramBlockStartRegex, in: codeOnly) != nil || firstMatch(pipelineBlockRegex, in: codeOnly) != nil {
                flushVarGroup()
                continue
            }

            if let colParen = firstMatch(collectionParenDeclRegex, in: codeOnly), parenDelta > 0 {
                flushVarGroup()
                let varName = groupString(colParen, index: 1, in: codeOnly)
                landmarks.append(
                    AstSymbol(
                        name: varName,
                        kind: .dataTable,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: "\(varName) = @( … )"
                    )
                )
                continue
            }

            if let colBrace = firstMatch(collectionBraceDeclRegex, in: codeOnly), braceDelta > 0 {
                flushVarGroup()
                let varName = groupString(colBrace, index: 1, in: codeOnly)
                landmarks.append(
                    AstSymbol(
                        name: varName,
                        kind: .dataTable,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: "\(varName) = @{ … }"
                    )
                )
                continue
            }

            let isControlHead = firstMatch(controlBlockHeadRegex, in: codeOnly) != nil ||
                firstMatch(pipelineForEachHeadRegex, in: codeOnly) != nil
            if isControlHead && (braceDelta > 0 || opensBraceOnNextCodeLine(lines: lines, lineStates: lineStates, fromLineIdx: lineIdx)) {
                flushVarGroup()
                let cleanSig = formatControlBlockSignature(stripCommentsPreserveStrings(line))
                landmarks.append(
                    AstSymbol(
                        name: cleanSig,
                        kind: .controlBlock,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: cleanSig
                    )
                )
                continue
            }

            if let scalarMatch = firstMatch(scalarVarAssignRegex, in: codeOnly) {
                if groupStartLineIdx < 0 { groupStartLineIdx = lineIdx }
                groupVarNames.append(groupString(scalarMatch, index: 1, in: codeOnly))
                continue
            }

            flushVarGroup()
        }

        flushVarGroup()
        return landmarks
    }

    private static func extractSecondaryLanguageAstSymbols(
        lines: [String],
        language: SyntaxLanguage
    ) -> [AstSymbol] {
        var symbols: [AstSymbol] = []
        var inMarkdownFence = false

        for lineIdx in lines.indices {
            let line = lines[lineIdx]
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else { continue }

            switch language {
            case .swift:
                if trimmed.hasPrefix("//") || trimmed.hasPrefix("/*") || trimmed.hasPrefix("*") {
                    if trimmed.hasPrefix("// MARK:") {
                        let markTitle = trimmed.replacingOccurrences(of: "// MARK:", with: "")
                            .trimmingCharacters(in: CharacterSet(charactersIn: " -"))
                        if !markTitle.isEmpty {
                            symbols.append(AstSymbol(name: markTitle, kind: .section, lineIndex: lineIdx, lineNumber: lineIdx + 1, signature: "MARK: \(markTitle)"))
                        }
                    }
                    continue
                }
                if let fnMatch = firstMatch(swiftFuncRegex, in: line) {
                    let name = groupString(fnMatch, index: 1, in: line)
                    let rawParams = groupString(fnMatch, index: 2, in: line).trimmingCharacters(in: .whitespaces)
                    let params = rawParams.isEmpty ? [] : rawParams.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
                    symbols.append(AstSymbol(
                        name: name,
                        kind: .function,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: formatCallSignature(name: name, params: params.map { $0.components(separatedBy: ":").first?.trimmingCharacters(in: .whitespaces) ?? $0 }),
                        parameters: params
                    ))
                    continue
                }
                if let typeMatch = firstMatch(swiftTypeRegex, in: line) {
                    let kw = groupString(typeMatch, index: 1, in: line)
                    let name = groupString(typeMatch, index: 2, in: line)
                    symbols.append(AstSymbol(name: name, kind: .class, lineIndex: lineIdx, lineNumber: lineIdx + 1, signature: "\(kw) \(name)"))
                }

            case .python:
                if trimmed.hasPrefix("#") { continue }
                if let fnMatch = firstMatch(pythonDefRegex, in: line) {
                    let name = groupString(fnMatch, index: 1, in: line)
                    let rawParams = groupString(fnMatch, index: 2, in: line).trimmingCharacters(in: .whitespaces)
                    let params = rawParams.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty && $0 != "self" && $0 != "cls" }
                    symbols.append(AstSymbol(
                        name: name,
                        kind: .function,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: rawParams.isEmpty ? "def \(name)()" : "def \(name)(\(rawParams))",
                        parameters: params
                    ))
                    continue
                }
                if let clsMatch = firstMatch(pythonClassRegex, in: line) {
                    let name = groupString(clsMatch, index: 1, in: line)
                    symbols.append(AstSymbol(name: name, kind: .class, lineIndex: lineIdx, lineNumber: lineIdx + 1, signature: "class \(name)"))
                }

            case .kotlin:
                if trimmed.hasPrefix("//") || trimmed.hasPrefix("/*") || trimmed.hasPrefix("*") { continue }
                if let fnMatch = firstMatch(kotlinFunRegex, in: line) {
                    let name = groupString(fnMatch, index: 1, in: line)
                    let rawParams = groupString(fnMatch, index: 2, in: line).trimmingCharacters(in: .whitespaces)
                    let params = rawParams.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                    symbols.append(AstSymbol(
                        name: name,
                        kind: .function,
                        lineIndex: lineIdx,
                        lineNumber: lineIdx + 1,
                        signature: formatCallSignature(name: name, params: params.map { $0.components(separatedBy: ":").first?.trimmingCharacters(in: .whitespaces) ?? $0 }),
                        parameters: params
                    ))
                    continue
                }
                if let typeMatch = firstMatch(kotlinTypeRegex, in: line) {
                    let kw = groupString(typeMatch, index: 1, in: line)
                    let name = groupString(typeMatch, index: 2, in: line)
                    symbols.append(AstSymbol(name: name, kind: .class, lineIndex: lineIdx, lineNumber: lineIdx + 1, signature: "\(kw) \(name)"))
                }

            case .shellBash:
                if trimmed.hasPrefix("#") {
                    if let bannerMatch = firstMatch(bannerSectionCommentRegex, in: line) {
                        let banner = groupString(bannerMatch, index: 1, in: line).trimmingCharacters(in: .whitespaces)
                        if !banner.isEmpty {
                            symbols.append(AstSymbol(name: banner, kind: .section, lineIndex: lineIdx, lineNumber: lineIdx + 1, signature: banner))
                        }
                    }
                    continue
                }
                if let fnMatch = firstMatch(bashFunctionRegex, in: line) {
                    let g1 = groupString(fnMatch, index: 1, in: line)
                    let name = g1.isEmpty ? groupString(fnMatch, index: 2, in: line) : g1
                    let reserved: Set<String> = ["if", "for", "while", "until", "case", "select"]
                    if !name.isEmpty && !reserved.contains(name) {
                        symbols.append(AstSymbol(name: name, kind: .function, lineIndex: lineIdx, lineNumber: lineIdx + 1, signature: "\(name)()"))
                    }
                }

            case .markdown:
                if trimmed.hasPrefix("```") || trimmed.hasPrefix("~~~") {
                    inMarkdownFence.toggle()
                    continue
                }
                if !inMarkdownFence, let hMatch = firstMatch(markdownHeadingRegex, in: line) {
                    let hashes = groupString(hMatch, index: 1, in: line)
                    let title = groupString(hMatch, index: 2, in: line).trimmingCharacters(in: .whitespaces)
                    symbols.append(AstSymbol(name: title, kind: .section, lineIndex: lineIdx, lineNumber: lineIdx + 1, signature: "\(hashes) \(title)"))
                }

            default:
                break
            }
        }
        return symbols
    }

    private static func detectSectionHeaderTitle(
        lines: [String],
        lineStates: [LineLexState],
        lineIdx: Int
    ) -> String? {
        let line = lines[lineIdx]
        if matchesEntireLine(pureDividerCommentRegex, in: line) { return nil }

        if let bannerMatch = firstMatch(bannerSectionCommentRegex, in: line) {
            let text = groupString(bannerMatch, index: 1, in: line).trimmingCharacters(in: .whitespaces)
            if !text.isEmpty { return text }
        }

        if let stepMatch = firstMatch(stepSectionCommentRegex, in: line) {
            let text = groupString(stepMatch, index: 1, in: line).trimmingCharacters(in: .whitespaces)
            if !text.isEmpty { return text }
        }

        if lineIdx > 0,
           lineStates[lineIdx - 1] == .normal,
           matchesEntireLine(pureDividerCommentRegex, in: lines[lineIdx - 1]),
           let titleMatch = firstMatch(commentBoxTitleLineRegex, in: line) {
            let maxCloseIdx = min(lines.count - 1, lineIdx + 5)
            if lineIdx + 1 <= maxCloseIdx {
                for k in (lineIdx + 1)...maxCloseIdx {
                    if lineStates[k] != .normal { break }
                    let nextTrimmed = lines[k].drop(while: { $0 == " " || $0 == "\t" })
                    if !nextTrimmed.hasPrefix("#") { break }
                    if matchesEntireLine(pureDividerCommentRegex, in: lines[k]) {
                        let text = groupString(titleMatch, index: 1, in: line).trimmingCharacters(in: .whitespaces)
                        return text.isEmpty ? nil : text
                    }
                }
            }
        }
        return nil
    }

    private static func extractFunctionBodyParamNames(
        lines: [String],
        lineStates: [LineLexState],
        fnDeclLineIdx: Int
    ) -> [String] {
        let maxIdx = min(lines.count - 1, fnDeclLineIdx + 45)
        var sawOpeningBrace = false
        var attrBracketDepth = 0

        for idx in fnDeclLineIdx...maxIdx {
            guard lineStates[idx] == .normal else { continue }
            let rawLine = lines[idx]
            let trimmedStart = rawLine.drop(while: { $0 == " " || $0 == "\t" })
            if trimmedStart.isEmpty { continue }
            if trimmedStart.hasPrefix("<#") && !trimmedStart.contains("#>") { continue }

            let stripped = stripStringsAndCommentsFromLine(rawLine)
            if stripped.trimmingCharacters(in: .whitespaces).isEmpty { continue }

            let searchSlice: String
            if idx == fnDeclLineIdx {
                guard let braceIdx = stripped.firstIndex(of: "{") else { continue }
                sawOpeningBrace = true
                searchSlice = String(stripped[stripped.index(after: braceIdx)...])
            } else {
                searchSlice = stripped
            }

            let trimmedSlice = searchSlice.trimmingCharacters(in: .whitespaces)
            if trimmedSlice.isEmpty { continue }

            if !sawOpeningBrace {
                if trimmedSlice.hasPrefix("{") {
                    sawOpeningBrace = true
                    let afterBrace = String(trimmedSlice.dropFirst()).trimmingCharacters(in: .whitespaces)
                    if afterBrace.isEmpty { continue }
                    if firstMatch(paramBlockStartRegex, in: afterBrace) != nil {
                        let openParen = findUnquotedParamOpenParen(in: rawLine)
                        if openParen >= 0 {
                            return extractParamNamesFromParenthesizedList(lines: lines, lineStates: lineStates, startLineIdx: idx, openParenUTF16Offset: openParen)
                        }
                    }
                    continue
                }
                return []
            }

            if attrBracketDepth > 0 || trimmedSlice.hasPrefix("[") {
                for ch in trimmedSlice {
                    if ch == "[" { attrBracketDepth += 1 }
                    else if ch == "]" { attrBracketDepth = max(0, attrBracketDepth - 1) }
                }
                if attrBracketDepth == 0, let lastClose = trimmedSlice.lastIndex(of: "]") {
                    let afterAttr = String(trimmedSlice[trimmedSlice.index(after: lastClose)...]).trimmingCharacters(in: .whitespaces)
                    if !afterAttr.isEmpty && firstMatch(paramBlockStartRegex, in: afterAttr) != nil {
                        let openParen = findUnquotedParamOpenParen(in: rawLine)
                        if openParen >= 0 {
                            return extractParamNamesFromParenthesizedList(lines: lines, lineStates: lineStates, startLineIdx: idx, openParenUTF16Offset: openParen)
                        }
                    }
                }
                continue
            }

            if firstMatch(paramBlockStartRegex, in: trimmedSlice) != nil {
                let openParen = findUnquotedParamOpenParen(in: rawLine)
                if openParen >= 0 {
                    return extractParamNamesFromParenthesizedList(lines: lines, lineStates: lineStates, startLineIdx: idx, openParenUTF16Offset: openParen)
                }
                return []
            }

            return []
        }

        return []
    }

    private static func findUnquotedParamOpenParen(in line: String) -> Int {
        guard let match = firstMatch(paramKeywordParenRegex, in: line) else { return -1 }
        return match.range.location + match.range.length - 1
    }

    private static func extractParamNamesFromParenthesizedList(
        lines: [String],
        lineStates: [LineLexState],
        startLineIdx: Int,
        openParenUTF16Offset: Int
    ) -> [String] {
        var params: [String] = []
        var currentClause = ""
        var nestedParenDepth = 0
        var squareDepth = 0
        var hitDefaultEquals = false
        let maxIdx = min(lines.count - 1, startLineIdx + 45)

        func finalizeClause() {
            let clause = currentClause
            currentClause = ""
            hitDefaultEquals = false
            let nsClause = clause as NSString
            let matches = paramVariableNameRegex.matches(in: clause, options: [], range: NSRange(location: 0, length: nsClause.length))
            if let last = matches.last {
                let v = nsClause.substring(with: last.range)
                if !v.isEmpty { params.append(v) }
            }
        }

        for idx in startLineIdx...maxIdx {
            if idx > startLineIdx && lineStates[idx] != .normal { continue }
            let ns = lines[idx] as NSString
            let len = ns.length
            var i = (idx == startLineIdx) ? (openParenUTF16Offset + 1) : 0

            while i < len {
                let ch = ns.character(at: i)
                if ch == 60 && i + 1 < len && ns.character(at: i + 1) == 35 { // <#
                    let search = NSRange(location: i + 2, length: max(0, len - (i + 2)))
                    let closeR = ns.range(of: "#>", options: [], range: search)
                    if closeR.location != NSNotFound {
                        i = closeR.location + 2
                        continue
                    } else {
                        break
                    }
                }
                if ch == 35 { break } // #
                if ch == 39 || ch == 34 { // ' or "
                    let q = ch
                    i += 1
                    while i < len && ns.character(at: i) != q {
                        if q == 34 && ns.character(at: i) == 96 && i + 1 < len { i += 1 }
                        i += 1
                    }
                    i += 1
                    continue
                }

                switch ch {
                case 91: // [
                    squareDepth += 1
                case 93: // ]
                    squareDepth = max(0, squareDepth - 1)
                case 40: // (
                    nestedParenDepth += 1
                case 41: // )
                    if nestedParenDepth == 0 {
                        finalizeClause()
                        return params
                    }
                    nestedParenDepth -= 1
                case 44: // ,
                    if squareDepth == 0 && nestedParenDepth == 0 {
                        finalizeClause()
                        i += 1
                        continue
                    }
                case 61: // =
                    if squareDepth == 0 && nestedParenDepth == 0 {
                        hitDefaultEquals = true
                    }
                default:
                    if squareDepth == 0 && nestedParenDepth == 0 && !hitDefaultEquals {
                        currentClause.append(ns.substring(with: NSRange(location: i, length: 1)))
                    }
                }
                i += 1
            }
        }

        finalizeClause()
        return params
    }

    private static func opensBraceOnNextCodeLine(
        lines: [String],
        lineStates: [LineLexState],
        fromLineIdx: Int
    ) -> Bool {
        let maxLook = min(lines.count - 1, fromLineIdx + 3)
        guard fromLineIdx + 1 <= maxLook else { return false }
        for idx in (fromLineIdx + 1)...maxLook {
            guard lineStates[idx] == .normal else { continue }
            let trimmed = lines[idx].drop(while: { $0 == " " || $0 == "\t" })
            if trimmed.isEmpty || trimmed.hasPrefix("#") { continue }
            let stripped = stripStringsAndCommentsFromLine(lines[idx]).trimmingCharacters(in: .whitespaces)
            if stripped.isEmpty { continue }
            return stripped.hasPrefix("{")
        }
        return false
    }

    private static func formatCallSignature(name: String, params: [String]) -> String {
        guard !params.isEmpty else { return "\(name)()" }
        let preview = Array(params.prefix(3)).joined(separator: ", ")
        return params.count > 3 ? "\(name)(\(preview), …)" : "\(name)(\(preview))"
    }

    private static func formatControlBlockSignature(_ codeOnly: String) -> String {
        var compact = codeOnly.trimmingCharacters(in: .whitespaces)
        if compact.hasSuffix("{") {
            compact.removeLast()
        }
        compact = compact
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        let lower = compact.lowercased()
        if lower == "try" || lower == "do" {
            return "\(lower) { … }"
        }
        if compact.count > 52 {
            return String(compact.prefix(51)).trimmingCharacters(in: .whitespaces) + "…"
        }
        return compact
    }

    // MARK: - Code Folding Engine

    public static func extractFoldRegions(
        _ content: String,
        language: SyntaxLanguage = .powershell
    ) -> [FoldRegion] {
        guard !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        let lines = content.components(separatedBy: "\n").map {
            $0.hasSuffix("\r") ? String($0.dropLast()) : $0
        }
        let lineStates = computeLineStates(lines, language: language)
        var regions: [FoldRegion] = []

        var regionStack: [(startLine: Int, desc: String)] = []
        var commentBlockStart: Int? = nil

        struct BraceFrame {
            let startLine: Int
            let label: String
            let kind: FoldKind
            let baseDepth: Int
        }
        struct ParenFrame {
            let startLine: Int
            let label: String
            let baseDepth: Int
        }

        var braceFrames: [BraceFrame] = []
        var parenFrames: [ParenFrame] = []
        var currentBraceDepth = 0
        var currentParenDepth = 0
        var pendingDecl: (startLine: Int, label: String, kind: FoldKind)? = nil

        for lineIdx in lines.indices {
            let line = lines[lineIdx]
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            if language == .powershell {
                if commentBlockStart == nil,
                   lineStates[lineIdx] == .normal,
                   trimmed.hasPrefix("<#"),
                   !trimmed.contains("#>") {
                    commentBlockStart = lineIdx
                    continue
                }
                if let start = commentBlockStart {
                    if trimmed.contains("#>") {
                        if lineIdx > start {
                            regions.append(
                                FoldRegion(
                                    id: "comment_\(start)",
                                    startLine: start,
                                    endLine: lineIdx,
                                    label: "<# Comment Block (\(lineIdx - start + 1) lines) #>",
                                    kind: .commentBlock
                                )
                            )
                        }
                        commentBlockStart = nil
                    }
                    continue
                }
                if lineStates[lineIdx] != .normal { continue }
            }

            if let regStart = firstMatch(regionStartRegex, in: line) {
                let rawDesc = groupString(regStart, index: 1, in: line).trimmingCharacters(in: .whitespaces)
                regionStack.append((lineIdx, rawDesc.isEmpty ? "Region" : rawDesc))
                continue
            }
            if firstMatch(regionEndRegex, in: line) != nil, let top = regionStack.popLast() {
                if lineIdx > top.startLine {
                    regions.append(
                        FoldRegion(
                            id: "region_\(top.startLine)",
                            startLine: top.startLine,
                            endLine: lineIdx,
                            label: "#region \(top.desc)",
                            kind: .region
                        )
                    )
                }
                continue
            }

            let stripped = stripStringsAndCommentsFromLine(line)
            if stripped.trimmingCharacters(in: .whitespaces).isEmpty { continue }

            if let existing = pendingDecl, existing.kind == .block, lineIdx - existing.startLine > 3 {
                pendingDecl = nil
            }

            if language == .powershell {
                if let fnMatch = firstMatch(functionDeclRegex, in: stripped) {
                    let kw = groupString(fnMatch, index: 1, in: stripped).lowercased()
                    let name = groupString(fnMatch, index: 2, in: stripped)
                    let kind: FoldKind = (kw == "filter") ? .filter : (kw == "workflow" ? .workflow : .function)
                    pendingDecl = (lineIdx, "\(kw) \(name) { … }", kind)
                } else if let clsMatch = firstMatch(classDeclRegex, in: stripped) {
                    let kw = groupString(clsMatch, index: 1, in: stripped).lowercased()
                    let name = groupString(clsMatch, index: 2, in: stripped)
                    pendingDecl = (lineIdx, "\(kw) \(name) { … }", .class)
                } else if let sbMatch = firstMatch(scriptblockDeclRegex, in: stripped) {
                    let varName = groupString(sbMatch, index: 1, in: stripped)
                    pendingDecl = (lineIdx, "\(varName) = { … }", .block)
                } else if currentBraceDepth == 0 && currentParenDepth == 0 {
                    if let colBrace = firstMatch(collectionBraceDeclRegex, in: stripped) {
                        let varName = groupString(colBrace, index: 1, in: stripped)
                        pendingDecl = (lineIdx, "\(varName) = @{ … }", .block)
                    } else if firstMatch(controlBlockHeadRegex, in: stripped) != nil || firstMatch(pipelineForEachHeadRegex, in: stripped) != nil {
                        var hdr = formatControlBlockSignature(stripCommentsPreserveStrings(line))
                        if hdr.hasSuffix(" { … }") {
                            hdr = String(hdr.dropLast(6))
                        }
                        pendingDecl = (lineIdx, "\(hdr) { … }", .block)
                    }
                }
            } else if language == .swift || language == .kotlin {
                if let fnMatch = firstMatch(language == .swift ? swiftFuncRegex : kotlinFunRegex, in: stripped) {
                    let name = groupString(fnMatch, index: 1, in: stripped)
                    pendingDecl = (lineIdx, "func \(name) { … }", .function)
                } else if let tMatch = firstMatch(language == .swift ? swiftTypeRegex : kotlinTypeRegex, in: stripped) {
                    let kw = groupString(tMatch, index: 1, in: stripped)
                    let name = groupString(tMatch, index: 2, in: stripped)
                    pendingDecl = (lineIdx, "\(kw) \(name) { … }", .class)
                }
            }

            let colParenVar: String? = (language == .powershell && currentBraceDepth == 0 && currentParenDepth == 0)
                ? firstMatch(collectionParenDeclRegex, in: stripped).map { groupString($0, index: 1, in: stripped) }
                : nil
            var armedColParen = colParenVar != nil

            for ch in stripped {
                switch ch {
                case "{":
                    if let decl = pendingDecl {
                        braceFrames.append(BraceFrame(startLine: decl.startLine, label: decl.label, kind: decl.kind, baseDepth: currentBraceDepth))
                        pendingDecl = nil
                    }
                    currentBraceDepth += 1
                case "}":
                    currentBraceDepth = max(0, currentBraceDepth - 1)
                    if let last = braceFrames.last, last.baseDepth == currentBraceDepth {
                        _ = braceFrames.popLast()
                        if lineIdx > last.startLine {
                            regions.append(FoldRegion(id: "fold_\(last.startLine)", startLine: last.startLine, endLine: lineIdx, label: last.label, kind: last.kind))
                        }
                    }
                case "(":
                    if armedColParen, let v = colParenVar {
                        parenFrames.append(ParenFrame(startLine: lineIdx, label: "\(v) = @( … )", baseDepth: currentParenDepth))
                        armedColParen = false
                    }
                    currentParenDepth += 1
                case ")":
                    currentParenDepth = max(0, currentParenDepth - 1)
                    if let last = parenFrames.last, last.baseDepth == currentParenDepth {
                        _ = parenFrames.popLast()
                        if lineIdx > last.startLine {
                            regions.append(FoldRegion(id: "paren_\(last.startLine)", startLine: last.startLine, endLine: lineIdx, label: last.label, kind: .block))
                        }
                    }
                default:
                    break
                }
            }
        }

        return regions.sorted { $0.startLine < $1.startLine }
    }

    public static func stripStringsAndCommentsFromLine(_ line: String) -> String {
        let ns = line as NSString
        let len = ns.length
        var out = ""
        out.reserveCapacity(len)
        var i = 0
        while i < len {
            let ch = ns.character(at: i)
            if ch == 60 && i + 1 < len && ns.character(at: i + 1) == 35 {
                let search = NSRange(location: i + 2, length: max(0, len - (i + 2)))
                let closeR = ns.range(of: "#>", options: [], range: search)
                if closeR.location != NSNotFound {
                    i = closeR.location + 2
                    continue
                } else {
                    break
                }
            }
            if ch == 35 { break }
            if ch == 39 || ch == 34 {
                let q = ch
                let quoteChar = q == 39 ? "'" : "\""
                out.append(quoteChar)
                i += 1
                while i < len && ns.character(at: i) != q {
                    if q == 34 && ns.character(at: i) == 96 && i + 1 < len { i += 1 }
                    i += 1
                }
                out.append(quoteChar)
                i += 1
                continue
            }
            out.append(ns.substring(with: NSRange(location: i, length: 1)))
            i += 1
        }
        return out
    }

    private static func stripCommentsPreserveStrings(_ line: String) -> String {
        let ns = line as NSString
        let len = ns.length
        var out = ""
        out.reserveCapacity(len)
        var i = 0
        while i < len {
            let ch = ns.character(at: i)
            if ch == 60 && i + 1 < len && ns.character(at: i + 1) == 35 {
                let search = NSRange(location: i + 2, length: max(0, len - (i + 2)))
                let closeR = ns.range(of: "#>", options: [], range: search)
                if closeR.location != NSNotFound {
                    i = closeR.location + 2
                    continue
                } else {
                    break
                }
            }
            if ch == 35 { break }
            if ch == 39 || ch == 34 {
                let q = ch
                out.append(ns.substring(with: NSRange(location: i, length: 1)))
                i += 1
                while i < len {
                    let inner = ns.character(at: i)
                    out.append(ns.substring(with: NSRange(location: i, length: 1)))
                    if q == 34 && inner == 96 && i + 1 < len {
                        i += 1
                        out.append(ns.substring(with: NSRange(location: i, length: 1)))
                        i += 1
                        continue
                    }
                    if inner == q {
                        i += 1
                        break
                    }
                    i += 1
                }
                continue
            }
            out.append(ns.substring(with: NSRange(location: i, length: 1)))
            i += 1
        }
        return out
    }

    // MARK: - Inline PowerShell Auto-Completion Engine

    private static let commonCmdletCatalog: [(String, String)] = [
        ("Get-ChildItem", "Gets items and child items in one or more locations (dir / ls / gci)"),
        ("Invoke-RestMethod", "Sends an HTTP or HTTPS request to a RESTful web service (irm)"),
        ("Invoke-WebRequest", "Gets content from a web page on the internet (iwr)"),
        ("Where-Object", "Selects objects from a collection based on property values (? / where)"),
        ("ForEach-Object", "Performs an operation against each item in a collection (% / foreach)"),
        ("Select-Object", "Selects specified properties of an object or set of objects (select)"),
        ("Sort-Object", "Sorts objects by property values (sort)"),
        ("Group-Object", "Groups objects that contain the same value for specified properties"),
        ("Measure-Object", "Calculates numeric properties of objects and characters/words/lines"),
        ("Get-Content", "Gets the content of the item at the specified location (cat / gc)"),
        ("Set-Content", "Writes new content or replaces the content in a file (sc)"),
        ("Add-Content", "Appends content to the specified items (ac)"),
        ("Out-File", "Sends output to a file"),
        ("ConvertTo-Json", "Converts an object to a JSON-formatted string"),
        ("ConvertFrom-Json", "Converts a JSON-formatted string to a custom PSObject or Hashtable"),
        ("Export-Csv", "Converts objects into CSV strings and saves them to a file"),
        ("Import-Csv", "Creates table-like custom objects from CSV items"),
        ("Test-Path", "Determines whether all elements of a file or path exist"),
        ("Join-Path", "Combines a path and child path into a single path"),
        ("Resolve-Path", "Resolves wildcard characters in a path"),
        ("New-Item", "Creates a new file, directory, or item (ni)"),
        ("Remove-Item", "Deletes files, folders, or variables (rm / del / ri)"),
        ("Copy-Item", "Copies an item from one location to another (cp / copy / cpi)"),
        ("Move-Item", "Moves an item from one location to another (mv / move / mi)"),
        ("Get-Item", "Gets the item at the specified location (gi)"),
        ("Get-Process", "Gets the processes running on the local computer (ps / gps)"),
        ("Start-Process", "Starts one or more processes on the local computer (saps)"),
        ("Stop-Process", "Stops one or more running processes (kill / spps)"),
        ("Get-Service", "Gets the services on the computer (gsv)"),
        ("Restart-Service", "Stops and then starts one or more services"),
        ("Write-Host", "Writes customized output to a host console"),
        ("Write-Output", "Sends objects to the next command in the pipeline (echo)"),
        ("Write-Verbose", "Writes text to the verbose message stream"),
        ("Write-Warning", "Writes a warning message to the PowerShell host"),
        ("Write-Error", "Writes an object to the error stream"),
        ("Set-ExecutionPolicy", "Changes the PowerShell script execution policy"),
        ("Import-Module", "Adds modules to the current session (ipmo)"),
        ("Get-Command", "Gets all commands installed on the system (gcm)"),
        ("Get-Help", "Displays information about PowerShell commands and concepts (man)"),
        ("Get-Member", "Gets the properties and methods of objects (gm)"),
        ("Get-Date", "Gets the current date and time"),
        ("Start-Sleep", "Suspends activity in a script or session for the specified time")
    ]

    private static let commonParameterCatalog: [(String, String)] = [
        ("-Path", "Target file or directory path (supports wildcards)"),
        ("-LiteralPath", "Target literal path (no wildcard expansion)"),
        ("-Filter", "Provider-level filter string (e.g. '*.ps1')"),
        ("-Include", "Items to include in the operation"),
        ("-Exclude", "Items to exclude from the operation"),
        ("-Recurse", "Gets items in the specified locations and all child items"),
        ("-Depth", "Specifies the recursion or serialization depth"),
        ("-Force", "Overrides restrictions that prevent the command from succeeding"),
        ("-ErrorAction", "Action on non-terminating error (Stop, Continue, SilentlyContinue)"),
        ("-WarningAction", "Action on warning (Continue, SilentlyContinue, Inquire, Stop)"),
        ("-Verbose", "Displays detailed information about the operation"),
        ("-WhatIf", "Shows what would happen if the cmdlet runs without executing"),
        ("-Confirm", "Prompts you for confirmation before running the cmdlet"),
        ("-OutVariable", "Stores output objects in the specified variable name"),
        ("-PipelineVariable", "Stores current pipeline element in a variable"),
        ("-Property", "Specifies the object properties to select or sort"),
        ("-ExpandProperty", "Specifies a property to select and expand"),
        ("-Uri", "Uniform Resource Identifier for web/REST requests"),
        ("-Method", "HTTP method (Get, Post, Put, Delete, Patch)"),
        ("-Headers", "Hashtable of HTTP request headers"),
        ("-Body", "Request body payload for REST/Web calls"),
        ("-ContentType", "Content-Type header (e.g. 'application/json')"),
        ("-TimeoutSec", "Request timeout in seconds"),
        ("-Value", "Specifies the value of the new item or content"),
        ("-Encoding", "Specifies character encoding (utf8, utf8BOM, unicode, ascii)")
    ]

    private static let builtinVariableCatalog: [(String, String)] = [
        ("$_", "Current pipeline object (alias for $PSItem)"),
        ("$PSItem", "Current object in the pipeline object stream"),
        ("$true", "Boolean True constant"),
        ("$false", "Boolean False constant"),
        ("$null", "Null / empty reference value"),
        ("$PSScriptRoot", "Directory from which the current script is being run"),
        ("$PSCommandPath", "Full path and file name of the script being run"),
        ("$PSVersionTable", "Read-only hashtable showing details about the PowerShell version"),
        ("$PSCmdlet", "Object representing the advanced function or cmdlet being run"),
        ("$PSBoundParameters", "Dictionary of parameters passed to a script or function"),
        ("$Error", "Array of error objects representing the most recent errors"),
        ("$LastExitCode", "Exit code of the last native program or script that ran"),
        ("$Args", "Array of undeclared parameters/values passed to a function or script"),
        ("$MyInvocation", "Information about the current command, script, or invocation"),
        ("$Matches", "Hashtable of regex match values populated by -match / switch -Regex"),
        ("$IsMacOS", "True when running PowerShell Core (pwsh) on macOS"),
        ("$env:PATH", "System PATH environment variable"),
        ("$env:HOME", "User home directory environment variable"),
        ("$global:", "Global variable scope modifier"),
        ("$script:", "Script-wide variable scope modifier"),
        ("$private:", "Private variable scope modifier")
    ]

    private static let typeAttributeCatalog: [(String, String, String)] = [
        ("[CmdletBinding()]", "[CmdletBinding()]", "Turns a function into an advanced cmdlet-style function"),
        ("[Parameter(Mandatory = $true)]", "[Parameter(Mandatory = $true)]", "Declares a mandatory function parameter"),
        ("[ValidateNotNullOrEmpty()]", "[ValidateNotNullOrEmpty()]", "Validates that parameter argument is not null or empty"),
        ("[ValidateSet()]", "[ValidateSet('Val1', 'Val2')]", "Restricts parameter values to a predefined set"),
        ("[OutputType()]", "[OutputType([pscustomobject])]", "Declares the return object type of a function"),
        ("[pscustomobject]", "[pscustomobject]", "PowerShell custom object type accelerator"),
        ("[ordered]", "[ordered]", "Creates an ordered dictionary hashtable ([ordered]@{})"),
        ("[string]", "[string]", "System.String type accelerator"),
        ("[string[]]", "[string[]]", "String array type accelerator"),
        ("[int]", "[int]", "System.Int32 32-bit integer type accelerator"),
        ("[long]", "[long]", "System.Int64 64-bit integer type accelerator"),
        ("[bool]", "[bool]", "System.Boolean type accelerator"),
        ("[switch]", "[switch]", "Switch parameter type accelerator"),
        ("[hashtable]", "[hashtable]", "System.Collections.Hashtable type accelerator"),
        ("[datetime]", "[datetime]", "System.DateTime type accelerator"),
        ("[SecureString]", "[SecureString]", "Encrypted SecureString type"),
        ("[PSCredential]", "[PSCredential]", "PowerShell credential object")
    ]

    public static func computeCompletions(
        line: String,
        cursorUTF16Column: Int,
        documentContent: String = "",
        astSymbols: [AstSymbol] = [],
        maxResults: Int = 8
    ) -> [PowerShellCompletionItem] {
        let ns = line as NSString
        let safeCursor = min(max(0, cursorUTF16Column), ns.length)
        guard safeCursor > 0 else { return [] }

        var tokenStart = safeCursor - 1
        while tokenStart >= 0 {
            let ch = ns.character(at: tokenStart)
            if ch == 36 || ch == 91 { break } // '$' or '['
            if ch == 45 { // '-'
                let prevIsBound = (tokenStart == 0) || isUTF16Whitespace(ns.character(at: tokenStart - 1)) || "({|;=".utf16.contains(ns.character(at: tokenStart - 1))
                if prevIsBound { break }
            }
            if isIdentifierPartUTF16(ch) || ch == 45 || ch == 58 {
                if tokenStart == 0 { break }
                tokenStart -= 1
            } else {
                tokenStart += 1
                break
            }
        }
        tokenStart = min(max(0, tokenStart), safeCursor)
        let rawPrefix = ns.substring(with: NSRange(location: tokenStart, length: safeCursor - tokenStart))
        guard !rawPrefix.isEmpty else { return [] }

        let lowerPrefix = rawPrefix.lowercased()
        var candidates: [PowerShellCompletionItem] = []

        if rawPrefix.hasPrefix("$") {
            let nsDoc = documentContent as NSString
            let matches = userVariableExtractRegex.matches(in: documentContent, options: [], range: NSRange(location: 0, length: nsDoc.length))
            var seen: Set<String> = []
            for m in matches {
                let val = nsDoc.substring(with: m.range)
                if val.caseInsensitiveCompare(rawPrefix) != .orderedSame && !seen.contains(val) {
                    seen.insert(val)
                    if val.lowercased().hasPrefix(lowerPrefix) {
                        candidates.append(PowerShellCompletionItem(
                            label: val,
                            insertText: val,
                            kind: .variable,
                            detail: "Script Variable",
                            replaceStartInLine: tokenStart,
                            replaceEndInLine: safeCursor
                        ))
                    }
                }
            }
            for (vName, desc) in builtinVariableCatalog {
                if vName.lowercased().hasPrefix(lowerPrefix) && vName.caseInsensitiveCompare(rawPrefix) != .orderedSame {
                    candidates.append(PowerShellCompletionItem(
                        label: vName,
                        insertText: vName,
                        kind: .variable,
                        detail: desc,
                        replaceStartInLine: tokenStart,
                        replaceEndInLine: safeCursor
                    ))
                }
            }
        } else if rawPrefix.hasPrefix("-") {
            guard rawPrefix.count >= 2 else { return [] }
            for (pName, desc) in commonParameterCatalog {
                if pName.lowercased().hasPrefix(lowerPrefix) && pName.caseInsensitiveCompare(rawPrefix) != .orderedSame {
                    candidates.append(PowerShellCompletionItem(
                        label: pName,
                        insertText: pName,
                        kind: .parameter,
                        detail: desc,
                        replaceStartInLine: tokenStart,
                        replaceEndInLine: safeCursor
                    ))
                }
            }
            for op in powershellOperators.sorted() {
                if op.hasPrefix(lowerPrefix) && op.caseInsensitiveCompare(rawPrefix) != .orderedSame {
                    candidates.append(PowerShellCompletionItem(
                        label: op,
                        insertText: op,
                        kind: .parameter,
                        detail: "PowerShell Operator",
                        replaceStartInLine: tokenStart,
                        replaceEndInLine: safeCursor
                    ))
                }
            }
        } else if rawPrefix.hasPrefix("[") {
            let replaceEnd = (safeCursor < ns.length && ns.character(at: safeCursor) == 93) ? (safeCursor + 1) : safeCursor
            for (label, insert, desc) in typeAttributeCatalog {
                if label.lowercased().hasPrefix(lowerPrefix) && label.caseInsensitiveCompare(rawPrefix) != .orderedSame {
                    candidates.append(PowerShellCompletionItem(
                        label: label,
                        insertText: insert,
                        kind: .typeAccelerator,
                        detail: desc,
                        replaceStartInLine: tokenStart,
                        replaceEndInLine: replaceEnd
                    ))
                }
            }
        } else {
            guard rawPrefix.count >= 2 else { return [] }
            for sym in astSymbols where sym.kind.isCallableCommand {
                if sym.name.lowercased().hasPrefix(lowerPrefix) && sym.name.caseInsensitiveCompare(rawPrefix) != .orderedSame {
                    candidates.append(PowerShellCompletionItem(
                        label: sym.name,
                        insertText: sym.name,
                        kind: .function,
                        detail: "\(sym.signature) (Ln \(sym.lineNumber))",
                        replaceStartInLine: tokenStart,
                        replaceEndInLine: safeCursor
                    ))
                }
            }
            for (cmdlet, desc) in commonCmdletCatalog {
                let lowerCmd = cmdlet.lowercased()
                if (lowerCmd.hasPrefix(lowerPrefix) || lowerCmd.contains(lowerPrefix)) && cmdlet.caseInsensitiveCompare(rawPrefix) != .orderedSame {
                    candidates.append(PowerShellCompletionItem(
                        label: cmdlet,
                        insertText: cmdlet,
                        kind: .cmdlet,
                        detail: desc,
                        replaceStartInLine: tokenStart,
                        replaceEndInLine: safeCursor
                    ))
                }
            }
            for kw in powershellKeywords.sorted() {
                if kw.hasPrefix(lowerPrefix) && kw.caseInsensitiveCompare(rawPrefix) != .orderedSame {
                    candidates.append(PowerShellCompletionItem(
                        label: kw,
                        insertText: kw,
                        kind: .keyword,
                        detail: "PowerShell Keyword",
                        replaceStartInLine: tokenStart,
                        replaceEndInLine: safeCursor
                    ))
                }
            }
        }

        candidates.sort { a, b in
            let aPre = a.label.lowercased().hasPrefix(lowerPrefix)
            let bPre = b.label.lowercased().hasPrefix(lowerPrefix)
            if aPre != bPre { return aPre && !bPre }
            return a.label.count < b.label.count
        }
        return Array(candidates.prefix(maxResults))
    }

    // MARK: - PSScriptAnalyzer Live Linter & Quick Fixes

    public static func analyzeScriptDiagnostics(_ text: String) -> [PowerShellDiagnostic] {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        let lines = text.components(separatedBy: "\n")
        let lineStates = computeLineStates(lines, language: .powershell)
        var diagnostics: [PowerShellDiagnostic] = []

        var openCurlyCount = 0
        var lastOpenCurlyLine = 1
        var openParenCount = 0
        var lastOpenParenLine = 1
        var regionOpenStack: [Int] = []

        for i in lines.indices {
            let lineNum = i + 1
            let line = lines[i]
            let nsLine = line as NSString
            let lexState = lineStates[i]

            let trimmed = line.drop(while: { $0 == " " || $0 == "\t" })
            if trimmed.lowercased().hasPrefix("#region") {
                regionOpenStack.append(lineNum)
            } else if trimmed.lowercased().hasPrefix("#endregion") {
                if regionOpenStack.isEmpty {
                    let hashCol = max(0, nsLine.range(of: "#").location == NSNotFound ? 0 : nsLine.range(of: "#").location) + 1
                    diagnostics.append(PowerShellDiagnostic(
                        id: "region_unmatched_\(lineNum)",
                        ruleId: "PSSyntaxUnmatchedEndRegion",
                        severity: .warning,
                        message: "Unmatched '#endregion' directive without a preceding '#region'.",
                        lineNumber: lineNum,
                        column: hashCol
                    ))
                } else {
                    _ = regionOpenStack.popLast()
                }
            }

            guard lexState == .normal else { continue }

            // Rule 1: PSUseApprovedVerbs
            if let fnMatch = firstMatch(functionVerbCheckRegex, in: line) {
                let verb = groupString(fnMatch, index: 1, in: line)
                let noun = groupString(fnMatch, index: 2, in: line)
                if !approvedVerbs.contains(verb.lowercased()) {
                    diagnostics.append(PowerShellDiagnostic(
                        id: "verb_\(lineNum)_\(verb)",
                        ruleId: "PSUseApprovedVerbs",
                        severity: .warning,
                        message: "Function '\(verb)-\(noun)' uses unapproved verb '\(verb)'. Prefer standard PowerShell verbs (Get, Set, Invoke, New, Test, ConvertTo).",
                        lineNumber: lineNum,
                        column: fnMatch.range.location + 1,
                        quickFixLabel: "Rename verb to 'Invoke-\(noun)'",
                        quickFixTarget: "\(verb)-\(noun)",
                        quickFixReplacement: "Invoke-\(noun)"
                    ))
                }
            }

            // Rule 2: PSAvoidEmptyCatchBlock
            if let emptyCatch = firstMatch(emptyCatchSingleLineRegex, in: line) {
                let matchedStr = nsLine.substring(with: emptyCatch.range)
                diagnostics.append(PowerShellDiagnostic(
                    id: "catch_\(lineNum)",
                    ruleId: "PSAvoidEmptyCatchBlock",
                    severity: .warning,
                    message: "Empty 'catch' block silently swallows exceptions. Log or rethrow '$_'.",
                    lineNumber: lineNum,
                    column: emptyCatch.range.location + 1,
                    quickFixLabel: "Insert Write-Error $_",
                    quickFixTarget: matchedStr,
                    quickFixReplacement: "catch { Write-Error $_ }"
                ))
            }

            // Rule 3: PSAvoidUsingPlainTextForPassword
            if let pwdMatch = firstMatch(plainTextPasswordParamRegex, in: line) {
                let matchedStr = nsLine.substring(with: pwdMatch.range)
                let replaced = matchedStr.replacingOccurrences(of: #"\[string\]"#, with: "[SecureString]", options: [.regularExpression, .caseInsensitive])
                diagnostics.append(PowerShellDiagnostic(
                    id: "pwd_\(lineNum)",
                    ruleId: "PSAvoidUsingPlainTextForPassword",
                    severity: .warning,
                    message: "Sensitive parameter '\(matchedStr)' uses plain-text [string]. Use [SecureString] or [PSCredential].",
                    lineNumber: lineNum,
                    column: pwdMatch.range.location + 1,
                    quickFixLabel: "Change type to [SecureString]",
                    quickFixTarget: matchedStr,
                    quickFixReplacement: replaced
                ))
            }

            // Tokenize line for aliases & delimiters
            let tokens = tokenizePowerShellLine(line, startState: lexState)
            var expectCommandPosition = true

            for token in tokens {
                switch token.type {
                case .whitespace, .comment, .commentHelpTag, .regionDirective:
                    break

                case .punctuation:
                    for ch in token.text {
                        switch ch {
                        case "{":
                            openCurlyCount += 1
                            lastOpenCurlyLine = lineNum
                            expectCommandPosition = true
                        case "}":
                            openCurlyCount -= 1
                            if openCurlyCount < 0 {
                                diagnostics.append(PowerShellDiagnostic(
                                    id: "curly_extra_\(lineNum)",
                                    ruleId: "PSSyntaxUnbalancedDelimiters",
                                    severity: .error,
                                    message: "Unexpected closing brace '}' with no matching opening '{'.",
                                    lineNumber: lineNum,
                                    column: token.start + 1
                                ))
                                openCurlyCount = 0
                            }
                            expectCommandPosition = true
                        case "(":
                            openParenCount += 1
                            lastOpenParenLine = lineNum
                            expectCommandPosition = true
                        case ")":
                            openParenCount -= 1
                            if openParenCount < 0 {
                                diagnostics.append(PowerShellDiagnostic(
                                    id: "paren_extra_\(lineNum)",
                                    ruleId: "PSSyntaxUnbalancedDelimiters",
                                    severity: .error,
                                    message: "Unexpected closing parenthesis ')' with no matching opening '('.",
                                    lineNumber: lineNum,
                                    column: token.start + 1
                                ))
                                openParenCount = 0
                            }
                        case "|", ";", "=":
                            expectCommandPosition = true
                        default:
                            break
                        }
                    }

                case .cmdlet, .identifier:
                    if expectCommandPosition {
                        let lowerToken = token.text.lowercased()
                        let prefixSlice = nsLine.substring(to: token.start).trimmingCharacters(in: .whitespaces)
                        let isPipeAliasContext = prefixSlice.hasSuffix("|")
                        let afterSlice = nsLine.substring(from: token.end).trimmingCharacters(in: .whitespaces)
                        let isHashtableKeyAssignment = afterSlice.hasPrefix("=") && !afterSlice.hasPrefix("==")

                        if lowerToken == "invoke-expression" {
                            diagnostics.append(PowerShellDiagnostic(
                                id: "iex_sec_\(lineNum)_\(token.start)",
                                ruleId: "PSAvoidUsingInvokeExpression",
                                severity: .warning,
                                message: "'Invoke-Expression' evaluates arbitrary strings as code. Prefer direct invocation ('&') or splatting.",
                                lineNumber: lineNum,
                                column: token.start + 1
                            ))
                        } else if let canonical = aliasToCanonicalCmdlet[lowerToken],
                                  !isHashtableKeyAssignment,
                                  (lowerToken != "foreach" || isPipeAliasContext) {
                            diagnostics.append(PowerShellDiagnostic(
                                id: "alias_\(lineNum)_\(token.start)",
                                ruleId: "PSAvoidUsingCmdletAliases",
                                severity: .info,
                                message: "'\(token.text)' is an alias for '\(canonical)'. Expand aliases in scripts for readability and portability.",
                                lineNumber: lineNum,
                                column: token.start + 1,
                                quickFixLabel: "Expand to '\(canonical)'",
                                quickFixTarget: token.text,
                                quickFixReplacement: canonical
                            ))
                        }
                        expectCommandPosition = false
                    }

                case .hashtablePrefix:
                    if token.text == "@{" {
                        openCurlyCount += 1
                        lastOpenCurlyLine = lineNum
                        expectCommandPosition = false
                    } else if token.text == "@(" {
                        openParenCount += 1
                        lastOpenParenLine = lineNum
                        expectCommandPosition = true
                    } else {
                        expectCommandPosition = false
                    }

                case .stringInterpolation:
                    if token.text.hasPrefix("$(") && token.text.hasSuffix(")") && token.text.count > 3 {
                        let nsTok = token.text as NSString
                        let innerExpr = nsTok.substring(with: NSRange(location: 2, length: nsTok.length - 3))
                        let innerNS = innerExpr as NSString
                        let innerBaseCol = token.start + 2
                        let innerTokens = tokenizePowerShellLine(innerExpr, startState: .normal)
                        var innerExpectCmd = true
                        for innerToken in innerTokens {
                            switch innerToken.type {
                            case .whitespace, .comment, .commentHelpTag, .regionDirective:
                                break
                            case .cmdlet, .identifier:
                                if innerExpectCmd {
                                    let lowerInner = innerToken.text.lowercased()
                                    let isPipe = innerNS.substring(to: innerToken.start).trimmingCharacters(in: .whitespaces).hasSuffix("|")
                                    if let canonical = aliasToCanonicalCmdlet[lowerInner], (lowerInner != "foreach" || isPipe) {
                                        diagnostics.append(PowerShellDiagnostic(
                                            id: "alias_\(lineNum)_\(innerBaseCol + innerToken.start)",
                                            ruleId: "PSAvoidUsingCmdletAliases",
                                            severity: .info,
                                            message: "'\(innerToken.text)' is an alias for '\(canonical)'. Expand aliases in scripts for readability and portability.",
                                            lineNumber: lineNum,
                                            column: innerBaseCol + innerToken.start + 1,
                                            quickFixLabel: "Expand to '\(canonical)'",
                                            quickFixTarget: innerToken.text,
                                            quickFixReplacement: canonical
                                        ))
                                    }
                                    innerExpectCmd = false
                                }
                            case .punctuation:
                                innerExpectCmd = innerToken.text.contains(where: { "{(|;=".contains($0) })
                            case .operator:
                                innerExpectCmd = ["|", "=", "+=", "$("].contains(innerToken.text)
                            case .hashtablePrefix:
                                innerExpectCmd = (innerToken.text == "@(")
                            default:
                                innerExpectCmd = false
                            }
                        }
                    }
                    expectCommandPosition = false

                case .operator:
                    if token.text == "$(" {
                        openParenCount += 1
                        lastOpenParenLine = lineNum
                        expectCommandPosition = true
                    } else {
                        expectCommandPosition = ["|", "=", "+="].contains(token.text)
                    }

                default:
                    expectCommandPosition = false
                }
            }
        }

        if openCurlyCount > 0 {
            diagnostics.append(PowerShellDiagnostic(
                id: "unclosed_curly_\(lastOpenCurlyLine)",
                ruleId: "PSSyntaxUnbalancedDelimiters",
                severity: .error,
                message: "Unclosed script block '{' (\(openCurlyCount) missing '}' brace\(openCurlyCount > 1 ? "s" : "")).",
                lineNumber: lastOpenCurlyLine,
                column: 1
            ))
        }

        if openParenCount > 0 {
            diagnostics.append(PowerShellDiagnostic(
                id: "unclosed_paren_\(lastOpenParenLine)",
                ruleId: "PSSyntaxUnbalancedDelimiters",
                severity: .error,
                message: "Unclosed parenthesis '(' (\(openParenCount) missing ')').",
                lineNumber: lastOpenParenLine,
                column: 1
            ))
        }

        for openRegionLine in regionOpenStack {
            diagnostics.append(PowerShellDiagnostic(
                id: "unclosed_region_\(openRegionLine)",
                ruleId: "PSSyntaxUnclosedRegion",
                severity: .info,
                message: "'#region' on line \(openRegionLine) has no matching '#endregion'.",
                lineNumber: openRegionLine,
                column: 1
            ))
        }

        return diagnostics.sorted {
            if $0.severity != $1.severity { return $0.severity < $1.severity }
            if $0.lineNumber != $1.lineNumber { return $0.lineNumber < $1.lineNumber }
            return $0.column < $1.column
        }
    }

    public static func applyDiagnosticQuickFix(
        documentContent: String,
        diagnostic: PowerShellDiagnostic
    ) -> String {
        guard let target = diagnostic.quickFixTarget,
              let replacement = diagnostic.quickFixReplacement else {
            return documentContent
        }
        var lines = documentContent.components(separatedBy: "\n")
        guard !lines.isEmpty else { return documentContent }
        let lineIdx = min(max(0, diagnostic.lineNumber - 1), lines.count - 1)
        let nsLine = lines[lineIdx] as NSString
        let colIdx = min(max(0, diagnostic.column - 1), nsLine.length)

        let searchFromCol = NSRange(location: colIdx, length: max(0, nsLine.length - colIdx))
        var foundRange = nsLine.range(of: target, options: [], range: searchFromCol)
        if foundRange.location == NSNotFound {
            foundRange = nsLine.range(of: target)
        }
        guard foundRange.location != NSNotFound else { return documentContent }

        lines[lineIdx] = nsLine.replacingCharacters(in: foundRange, with: replacement)
        return lines.joined(separator: "\n")
    }

    public static func applyAllQuickFixes(documentContent: String) -> (updatedContent: String, appliedCount: Int) {
        var current = documentContent
        var applied = 0
        for _ in 0..<200 {
            let diags = analyzeScriptDiagnostics(current).filter { $0.quickFixTarget != nil && $0.quickFixReplacement != nil }
            guard let first = diags.first else { break }
            let next = applyDiagnosticQuickFix(documentContent: current, diagnostic: first)
            if next == current { break }
            current = next
            applied += 1
        }
        return (current, applied)
    }

    // MARK: - Regex Helpers

    private static func firstMatch(_ regex: NSRegularExpression, in text: String) -> NSTextCheckingResult? {
        let ns = text as NSString
        return regex.firstMatch(in: text, options: [], range: NSRange(location: 0, length: ns.length))
    }

    private static func matchesEntireLine(_ regex: NSRegularExpression, in text: String) -> Bool {
        let ns = text as NSString
        let full = NSRange(location: 0, length: ns.length)
        guard let m = regex.firstMatch(in: text, options: [], range: full) else { return false }
        return m.range.location == 0 && m.range.length == ns.length
    }

    private static func groupString(_ match: NSTextCheckingResult, index: Int, in text: String) -> String {
        guard index < match.numberOfRanges else { return "" }
        let r = match.range(at: index)
        guard r.location != NSNotFound else { return "" }
        return (text as NSString).substring(with: r)
    }
}
