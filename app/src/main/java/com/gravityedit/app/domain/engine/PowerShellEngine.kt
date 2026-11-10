package com.gravityedit.app.domain.engine

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.gravityedit.app.domain.model.AstSymbol
import com.gravityedit.app.domain.model.FoldKind
import com.gravityedit.app.domain.model.FoldRegion
import com.gravityedit.app.domain.model.SymbolKind
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.ui.theme.EditorSyntaxPalette

enum class PowerShellTokenType {
    WHITESPACE,
    KEYWORD,
    CMDLET,
    VARIABLE_NORMAL,
    VARIABLE_SCOPED,
    VARIABLE_AUTOMATIC,
    PARAMETER,
    OPERATOR,
    STRING_LITERAL,
    STRING_EXPANDABLE,
    STRING_INTERPOLATION,
    NUMBER,
    TYPE_ATTRIBUTE,
    HASHTABLE_PREFIX,
    COMMENT,
    COMMENT_HELP_TAG,
    REGION_DIRECTIVE,
    PUNCTUATION,
    IDENTIFIER
}

data class PowerShellToken(
    val type: PowerShellTokenType,
    val start: Int,
    val end: Int,
    val text: String
)

enum class LineLexState {
    NORMAL,
    IN_BLOCK_COMMENT,
    IN_EXPANDABLE_HERE_STRING,
    IN_LITERAL_HERE_STRING
}

object PowerShellEngine {

    val APPROVED_VERBS: Set<String> = setOf(
        // Common
        "add", "clear", "close", "copy", "enter", "exit", "find", "format", "get", "hide",
        "join", "lock", "move", "new", "open", "optimize", "pop", "push", "redo", "remove",
        "rename", "reset", "resize", "search", "select", "set", "show", "skip", "split",
        "step", "switch", "undo", "unlock", "watch",
        // Communications
        "connect", "disconnect", "read", "receive", "send", "write",
        // Data
        "backup", "checkpoint", "compare", "compress", "convert", "convertfrom", "convertto",
        "dismount", "edit", "expand", "export", "group", "import", "initialize", "limit",
        "merge", "mount", "out", "publish", "restore", "save", "sync", "unpublish", "update",
        // Diagnostic
        "debug", "measure", "ping", "repair", "resolve", "test", "trace",
        // Lifecycle
        "approve", "assert", "build", "complete", "confirm", "deploy", "disable", "enable",
        "install", "invoke", "register", "request", "restart", "resume", "start", "stop",
        "submit", "suspend", "uninstall", "unregister", "wait",
        // Security
        "block", "grant", "protect", "revoke", "unblock", "unprotect",
        // Other & Functional
        "use", "foreach", "where", "sort", "tee"
    )

    val BUILTIN_ALIASES: Set<String> = setOf(
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
    )

    val POWERSHELL_KEYWORDS: Set<String> = setOf(
        "begin", "break", "catch", "class", "clean", "configuration", "continue", "data",
        "define", "do", "dynamicparam", "else", "elseif", "end", "enum", "exit", "filter",
        "finally", "for", "foreach", "from", "function", "hidden", "if", "in",
        "inlinescript", "interface", "module", "namespace", "parallel", "param", "private",
        "process", "public", "return", "sequence", "static", "switch", "throw", "trap",
        "try", "type", "until", "using", "var", "while", "workflow"
    )

    val AUTOMATIC_VARIABLES: Set<String> = setOf(
        "\$_", "\$psitem", "\$true", "\$false", "\$null", "\$args", "\$error",
        "\$psscriptroot", "\$pscommandpath", "\$psversiontable", "\$host", "\$home",
        "\$pid", "\$profile", "\$psculture", "\$psuiculture", "\$psedition",
        "\$islinux", "\$ismacos", "\$iswindows", "\$iscoreclr", "\$lastexitcode",
        "\$?", "\$^", "\$\$", "\$matches", "\$input", "\$myinvocation",
        "\$executioncontext", "\$foreach", "\$switch", "\$this", "\$stacktrace",
        "\$ofs", "\$psboundparameters", "\$pscmdlet", "\$psdebugcontext",
        "\$psdefaultparametervalues", "\$psemailserver", "\$pshome", "\$pssessionapplicationname",
        "\$pssessionconfigurationname", "\$pssessionoption", "\$pwd", "\$ sender", "\$event",
        "\$eventargs", "\$eventsubscriber", "\$nestedpromptlevel"
    )

    val VARIABLE_SCOPES: Set<String> = setOf(
        "env", "global", "script", "private", "local", "using", "workflow", "variable",
        "function", "alias", "hkcu", "hklm", "cert"
    )

    val POWERSHELL_OPERATORS: Set<String> = setOf(
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
    )

    val HELP_DOC_TAGS: Set<String> = setOf(
        ".SYNOPSIS", ".DESCRIPTION", ".PARAMETER", ".EXAMPLE", ".INPUTS",
        ".OUTPUTS", ".NOTES", ".LINK", ".COMPONENT", ".ROLE", ".FUNCTIONALITY",
        ".FORWARDHELPTARGETNAME", ".FORWARDHELPCATEGORY", ".REMOTEHELPRUNSPACE",
        ".EXTERNALHELP"
    )

    private val HELP_TAG_REGEX = Regex(
        """\.(SYNOPSIS|DESCRIPTION|PARAMETER|EXAMPLE|INPUTS|OUTPUTS|NOTES|LINK|COMPONENT|ROLE|FUNCTIONALITY|FORWARDHELPTARGETNAME|FORWARDHELPCATEGORY|REMOTEHELPRUNSPACE|EXTERNALHELP)\b""",
        RegexOption.IGNORE_CASE
    )

    private val FUNCTION_DECL_REGEX = Regex(
        """^\s*(function|filter|workflow|configuration)\s+([A-Za-z0-9_\-:]+)\s*(\(([^)]*)\))?""",
        RegexOption.IGNORE_CASE
    )

    private val CLASS_DECL_REGEX = Regex(
        """^\s*(class|enum)\s+([A-Za-z0-9_]+)""",
        RegexOption.IGNORE_CASE
    )

    private val REGION_START_REGEX = Regex(
        """^\s*#region\b(.*)$""",
        RegexOption.IGNORE_CASE
    )

    private val REGION_END_REGEX = Regex(
        """^\s*#endregion\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Computes the starting [LineLexState] for every line in a document in O(N) time so
     * multi-line `<# ... #>` block comments and `@" ... "@` / `@' ... '@` here-strings
     * highlight accurately inside a virtualized `LazyColumn`.
     */
    fun computeLineStates(lines: List<String>, language: SyntaxLanguage): List<LineLexState> {
        if (lines.isEmpty()) return emptyList()
        if (language != SyntaxLanguage.POWERSHELL) {
            return List(lines.size) { LineLexState.NORMAL }
        }
        val states = ArrayList<LineLexState>(lines.size)
        var currentState = LineLexState.NORMAL

        for (line in lines) {
            states.add(currentState)
            currentState = advanceLineState(line, currentState)
        }
        return states
    }

    private fun advanceLineState(line: String, startState: LineLexState): LineLexState {
        var state = startState
        var i = 0
        val len = line.length

        when (state) {
            LineLexState.IN_BLOCK_COMMENT -> {
                val closeIdx = line.indexOf("#>")
                if (closeIdx < 0) return LineLexState.IN_BLOCK_COMMENT
                state = LineLexState.NORMAL
                i = closeIdx + 2
            }
            LineLexState.IN_EXPANDABLE_HERE_STRING -> {
                if (line.trimStart().startsWith("\"@")) {
                    return LineLexState.NORMAL
                }
                return LineLexState.IN_EXPANDABLE_HERE_STRING
            }
            LineLexState.IN_LITERAL_HERE_STRING -> {
                if (line.trimStart().startsWith("'@")) {
                    return LineLexState.NORMAL
                }
                return LineLexState.IN_LITERAL_HERE_STRING
            }
            LineLexState.NORMAL -> Unit
        }

        while (i < len) {
            val ch = line[i]
            val next = if (i + 1 < len) line[i + 1] else '\u0000'

            // Single-line comment
            if (ch == '#' && (i == 0 || line[i - 1] != '<')) {
                break
            }
            // Multi-line comment start
            if (ch == '<' && next == '#') {
                val closeIdx = line.indexOf("#>", i + 2)
                if (closeIdx >= 0) {
                    i = closeIdx + 2
                    continue
                } else {
                    return LineLexState.IN_BLOCK_COMMENT
                }
            }
            // Here-string opener at end of line: @" or @'
            if (ch == '@' && (next == '"' || next == '\'')) {
                val trailing = line.substring(i + 2).trim()
                if (trailing.isEmpty()) {
                    return if (next == '"') {
                        LineLexState.IN_EXPANDABLE_HERE_STRING
                    } else {
                        LineLexState.IN_LITERAL_HERE_STRING
                    }
                }
            }
            // Single-quoted string on same line
            if (ch == '\'') {
                i++
                while (i < len) {
                    if (line[i] == '\'' && i + 1 < len && line[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    if (line[i] == '\'') {
                        i++
                        break
                    }
                    i++
                }
                continue
            }
            // Double-quoted string on same line
            if (ch == '"') {
                i++
                while (i < len) {
                    if (line[i] == '`' && i + 1 < len) {
                        i += 2
                        continue
                    }
                    if (line[i] == '"' && i + 1 < len && line[i + 1] == '"') {
                        i += 2
                        continue
                    }
                    if (line[i] == '"') {
                        i++
                        break
                    }
                    i++
                }
                continue
            }
            i++
        }

        return state
    }

    /**
     * Tokenizes a single PowerShell line given its entry [LineLexState].
     */
    fun tokenizePowerShellLine(
        line: String,
        startState: LineLexState = LineLexState.NORMAL
    ): List<PowerShellToken> {
        if (line.isEmpty()) return emptyList()
        val tokens = ArrayList<PowerShellToken>()
        var i = 0
        val len = line.length

        when (startState) {
            LineLexState.IN_BLOCK_COMMENT -> {
                val closeIdx = line.indexOf("#>")
                if (closeIdx < 0) {
                    appendBlockCommentTokens(line, 0, len, tokens)
                    return tokens
                } else {
                    appendBlockCommentTokens(line, 0, closeIdx + 2, tokens)
                    i = closeIdx + 2
                }
            }
            LineLexState.IN_EXPANDABLE_HERE_STRING -> {
                appendExpandableStringTokens(line, 0, len, tokens)
                return tokens
            }
            LineLexState.IN_LITERAL_HERE_STRING -> {
                tokens.add(PowerShellToken(PowerShellTokenType.STRING_LITERAL, 0, len, line))
                return tokens
            }
            LineLexState.NORMAL -> Unit
        }

        while (i < len) {
            val ch = line[i]
            val next = if (i + 1 < len) line[i + 1] else '\u0000'

            // 1. Whitespace
            if (ch.isWhitespace()) {
                val start = i
                while (i < len && line[i].isWhitespace()) i++
                tokens.add(PowerShellToken(PowerShellTokenType.WHITESPACE, start, i, line.substring(start, i)))
                continue
            }

            // 2. Multi-line comment `<# ... #>`
            if (ch == '<' && next == '#') {
                val closeIdx = line.indexOf("#>", i + 2)
                if (closeIdx >= 0) {
                    appendBlockCommentTokens(line, i, closeIdx + 2, tokens)
                    i = closeIdx + 2
                } else {
                    appendBlockCommentTokens(line, i, len, tokens)
                    i = len
                }
                continue
            }

            // 3. Single-line comment `#...` or `#region` / `#endregion`
            if (ch == '#') {
                val rest = line.substring(i)
                val trimmedLower = rest.lowercase()
                val type = if (trimmedLower.startsWith("#region") || trimmedLower.startsWith("#endregion")) {
                    PowerShellTokenType.REGION_DIRECTIVE
                } else {
                    PowerShellTokenType.COMMENT
                }
                tokens.add(PowerShellToken(type, i, len, rest))
                break
            }

            // 4. Here-String opener `@"` or `@'` or Hashtable `@{` or Array `@(`
            if (ch == '@') {
                if (next == '"') {
                    appendExpandableStringTokens(line, i, len, tokens)
                    break
                } else if (next == '\'') {
                    tokens.add(PowerShellToken(PowerShellTokenType.STRING_LITERAL, i, len, line.substring(i)))
                    break
                } else if (next == '{' || next == '(') {
                    tokens.add(PowerShellToken(PowerShellTokenType.HASHTABLE_PREFIX, i, i + 2, line.substring(i, i + 2)))
                    i += 2
                    continue
                }
            }

            // 5. Single-quoted literal string `'...'`
            if (ch == '\'') {
                val start = i
                i++
                while (i < len) {
                    if (line[i] == '\'' && i + 1 < len && line[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    if (line[i] == '\'') {
                        i++
                        break
                    }
                    i++
                }
                tokens.add(PowerShellToken(PowerShellTokenType.STRING_LITERAL, start, i, line.substring(start, i)))
                continue
            }

            // 6. Double-quoted expandable string `"..."` with variable & `$(...)` interpolation
            if (ch == '"') {
                val start = i
                i++
                var subDepth = 0
                while (i < len) {
                    if (line[i] == '`' && i + 1 < len) {
                        i += 2
                        continue
                    }
                    if (line[i] == '$' && i + 1 < len && line[i + 1] == '(') {
                        subDepth++
                        i += 2
                        continue
                    }
                    if (subDepth > 0 && line[i] == ')') {
                        subDepth--
                        i++
                        continue
                    }
                    if (subDepth == 0 && line[i] == '"' && i + 1 < len && line[i + 1] == '"') {
                        i += 2
                        continue
                    }
                    if (subDepth == 0 && line[i] == '"') {
                        i++
                        break
                    }
                    i++
                }
                appendExpandableStringTokens(line, start, i, tokens)
                continue
            }

            // 7. Variables (`$var`, `${var}`, `$env:PATH`, `$_`, `$true`, `$(...)`)
            if (ch == '$') {
                if (next == '(') {
                    tokens.add(PowerShellToken(PowerShellTokenType.OPERATOR, i, i + 2, "$("))
                    i += 2
                    continue
                }
                val varEnd = scanVariableEnd(line, i)
                if (varEnd > i + 1) {
                    val rawVar = line.substring(i, varEnd)
                    val varType = classifyVariable(rawVar)
                    tokens.add(PowerShellToken(varType, i, varEnd, rawVar))
                    i = varEnd
                    continue
                }
            }

            // 8. Type Accelerators & Attributes `[CmdletBinding()]`, `[Parameter(...)]`, `[string]`
            if (ch == '[') {
                val bracketEnd = findMatchingBracket(line, i)
                if (bracketEnd > i + 1) {
                    val inside = line.substring(i + 1, bracketEnd - 1).trim()
                    if (inside.isNotEmpty() && (inside[0].isLetter() || inside.startsWith("System."))) {
                        tokens.add(
                            PowerShellToken(
                                PowerShellTokenType.TYPE_ATTRIBUTE,
                                i,
                                bracketEnd,
                                line.substring(i, bracketEnd)
                            )
                        )
                        i = bracketEnd
                        continue
                    }
                }
            }

            // 9. Operators & Command Parameters starting with `-`
            if (ch == '-' && (i == 0 || line[i - 1].isWhitespace() || line[i - 1] in "({[|;=")) {
                if (next.isLetter()) {
                    val start = i
                    i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) {
                        i++
                    }
                    val tokenStr = line.substring(start, i)
                    val lower = tokenStr.lowercase()
                    val tokenType = if (lower in POWERSHELL_OPERATORS) {
                        PowerShellTokenType.OPERATOR
                    } else {
                        PowerShellTokenType.PARAMETER
                    }
                    // Include trailing colon on parameter e.g. `-Confirm:$false`
                    if (tokenType == PowerShellTokenType.PARAMETER && i < len && line[i] == ':') {
                        i++
                    }
                    tokens.add(PowerShellToken(tokenType, start, i, line.substring(start, i)))
                    continue
                }
            }

            // 10. Numbers (Hex `0x`, Binary `0b`, Decimal, Float, Byte Multipliers `kb`, `mb`, `gb`, `tb`, `pb`)
            if (ch.isDigit() || (ch == '-' && next.isDigit())) {
                val start = i
                if (ch == '-') i++
                if (i + 1 < len && line[i] == '0' && (line[i + 1] == 'x' || line[i + 1] == 'X' || line[i + 1] == 'b' || line[i + 1] == 'B')) {
                    i += 2
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                } else {
                    while (i < len && (line[i].isDigit() || line[i] == '_')) i++
                    if (i < len && line[i] == '.' && i + 1 < len && line[i + 1].isDigit()) {
                        i++
                        while (i < len && line[i].isDigit()) i++
                    }
                    if (i < len && (line[i] == 'e' || line[i] == 'E')) {
                        i++
                        if (i < len && (line[i] == '+' || line[i] == '-')) i++
                        while (i < len && line[i].isDigit()) i++
                    }
                    // PowerShell suffix multipliers: kb, mb, gb, tb, pb, d, l, u, s, y
                    val rem = line.substring(i).lowercase()
                    val suffixLen = when {
                        rem.startsWith("kb") || rem.startsWith("mb") || rem.startsWith("gb") ||
                            rem.startsWith("tb") || rem.startsWith("pb") || rem.startsWith("ul") ||
                            rem.startsWith("us") || rem.startsWith("uy") -> 2
                        rem.startsWith("d") || rem.startsWith("l") || rem.startsWith("u") ||
                            rem.startsWith("s") || rem.startsWith("y") -> 1
                        else -> 0
                    }
                    i += suffixLen
                }
                tokens.add(PowerShellToken(PowerShellTokenType.NUMBER, start, i, line.substring(start, i)))
                continue
            }

            // 11. Identifiers, Keywords, Cmdlets (`Verb-Noun`), and Built-in Aliases
            if (ch.isLetter() || ch == '_') {
                val start = i
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '-' || line[i] == '.')) {
                    // Stop if `-` is followed by whitespace or non-identifier char
                    if (line[i] == '-' && (i + 1 >= len || (!line[i + 1].isLetterOrDigit() && line[i + 1] != '_'))) {
                        break
                    }
                    i++
                }
                val word = line.substring(start, i)
                val lower = word.lowercase()
                val type = when {
                    lower in POWERSHELL_KEYWORDS -> PowerShellTokenType.KEYWORD
                    isCmdletOrAlias(word, lower) -> PowerShellTokenType.CMDLET
                    else -> PowerShellTokenType.IDENTIFIER
                }
                tokens.add(PowerShellToken(type, start, i, word))
                continue
            }

            // 12. Symbolic Operators (`|`, `=`, `+=`, `-=`, `::`, `..`, `?`, `%`, `!`)
            if (ch in "|=+*/%?:!<>") {
                val start = i
                i++
                while (i < len && line[i] in "=:<>+-") i++
                val opText = line.substring(start, i)
                val opType = if ((opText == "%" || opText == "?") &&
                    (start == 0 || line[start - 1].isWhitespace() || line[start - 1] == '|')
                ) {
                    PowerShellTokenType.CMDLET
                } else {
                    PowerShellTokenType.OPERATOR
                }
                tokens.add(PowerShellToken(opType, start, i, opText))
                continue
            }

            // 13. Punctuation (`{`, `}`, `(`, `)`, `[`, `]`, `;`, `,`, `.`)
            tokens.add(PowerShellToken(PowerShellTokenType.PUNCTUATION, i, i + 1, ch.toString()))
            i++
        }

        return tokens
    }

    private fun isCmdletOrAlias(word: String, lower: String): Boolean {
        if (lower in BUILTIN_ALIASES) return true
        val dashIdx = word.indexOf('-')
        if (dashIdx > 0 && dashIdx < word.length - 1) {
            val verb = word.substring(0, dashIdx).lowercase()
            val noun = word.substring(dashIdx + 1)
            if (verb in APPROVED_VERBS && noun[0].isLetter()) {
                return true
            }
            // Custom module Verb-Noun where Verb starts with uppercase and Noun starts with uppercase
            if (word[0].isUpperCase() && noun[0].isUpperCase() && noun.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                return true
            }
        }
        return false
    }

    private fun classifyVariable(rawVar: String): PowerShellTokenType {
        val unwrapped = if (rawVar.startsWith("\${") && rawVar.endsWith("}")) {
            "\$" + rawVar.substring(2, rawVar.length - 1)
        } else {
            rawVar
        }
        val lower = unwrapped.lowercase()
        if (lower in AUTOMATIC_VARIABLES) {
            return PowerShellTokenType.VARIABLE_AUTOMATIC
        }
        val colonIdx = lower.indexOf(':')
        if (colonIdx > 1) {
            val scopePrefix = lower.substring(1, colonIdx)
            if (scopePrefix in VARIABLE_SCOPES) {
                return PowerShellTokenType.VARIABLE_SCOPED
            }
        }
        return PowerShellTokenType.VARIABLE_NORMAL
    }

    private fun scanVariableEnd(text: String, dollarIndex: Int): Int {
        val len = text.length
        if (dollarIndex + 1 >= len) return dollarIndex + 1
        val next = text[dollarIndex + 1]

        // Braced variable: ${complex-var:name}
        if (next == '{') {
            val closeBrace = text.indexOf('}', dollarIndex + 2)
            return if (closeBrace >= 0) closeBrace + 1 else len
        }
        // Single-char automatic variables: $_, $?, $^, $$
        if (next == '?' || next == '^' || next == '$') {
            return dollarIndex + 2
        }
        if (next == '_' && (dollarIndex + 2 >= len || !text[dollarIndex + 2].isLetterOrDigit())) {
            return dollarIndex + 2
        }
        var i = dollarIndex + 1
        while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == ':')) {
            // Stop before trailing colon if not followed by identifier character
            if (text[i] == ':' && (i + 1 >= len || (!text[i + 1].isLetterOrDigit() && text[i + 1] != '_'))) {
                break
            }
            i++
        }
        return i
    }

    private fun findMatchingBracket(line: String, openBracketIndex: Int): Int {
        var depth = 0
        var i = openBracketIndex
        val len = line.length
        while (i < len) {
            when (line[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
                '\'', '"' -> {
                    val quote = line[i]
                    i++
                    while (i < len && line[i] != quote) {
                        if (line[i] == '`' && i + 1 < len) i++
                        i++
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun appendBlockCommentTokens(
        line: String,
        start: Int,
        end: Int,
        outTokens: MutableList<PowerShellToken>
    ) {
        val segment = line.substring(start, end)
        var cursor = 0
        for (match in HELP_TAG_REGEX.findAll(segment)) {
            if (match.range.first > cursor) {
                outTokens.add(
                    PowerShellToken(
                        PowerShellTokenType.COMMENT,
                        start + cursor,
                        start + match.range.first,
                        segment.substring(cursor, match.range.first)
                    )
                )
            }
            val tagEnd = match.range.last + 1
            outTokens.add(
                PowerShellToken(
                    PowerShellTokenType.COMMENT_HELP_TAG,
                    start + match.range.first,
                    start + tagEnd,
                    match.value
                )
            )
            cursor = tagEnd
        }
        if (cursor < segment.length) {
            outTokens.add(
                PowerShellToken(
                    PowerShellTokenType.COMMENT,
                    start + cursor,
                    end,
                    segment.substring(cursor)
                )
            )
        }
    }

    /**
     * Splits an expandable string `" ... $var ... $(Get-Date) ... "` into string segments and
     * embedded variable/subexpression tokens for rich interpolation highlighting.
     */
    private fun appendExpandableStringTokens(
        line: String,
        start: Int,
        end: Int,
        outTokens: MutableList<PowerShellToken>
    ) {
        var cursor = start
        var i = start
        while (i < end) {
            if (line[i] == '`' && i + 1 < end) {
                i += 2
                continue
            }
            if (line[i] == '$' && i + 1 < end) {
                val next = line[i + 1]
                if (next == '(') {
                    if (i > cursor) {
                        outTokens.add(
                            PowerShellToken(
                                PowerShellTokenType.STRING_EXPANDABLE,
                                cursor,
                                i,
                                line.substring(cursor, i)
                            )
                        )
                    }
                    var depth = 1
                    var j = i + 2
                    while (j < end && depth > 0) {
                        if (line[j] == '(') depth++
                        else if (line[j] == ')') depth--
                        j++
                    }
                    outTokens.add(
                        PowerShellToken(
                            PowerShellTokenType.STRING_INTERPOLATION,
                            i,
                            j,
                            line.substring(i, j)
                        )
                    )
                    i = j
                    cursor = i
                    continue
                } else if (next.isLetter() || next == '_' || next == '{' || next == '?') {
                    val varEnd = scanVariableEnd(line, i).coerceAtMost(end)
                    if (varEnd > i + 1) {
                        if (i > cursor) {
                            outTokens.add(
                                PowerShellToken(
                                    PowerShellTokenType.STRING_EXPANDABLE,
                                    cursor,
                                    i,
                                    line.substring(cursor, i)
                                )
                            )
                        }
                        val rawVar = line.substring(i, varEnd)
                        outTokens.add(
                            PowerShellToken(
                                classifyVariable(rawVar),
                                i,
                                varEnd,
                                rawVar
                            )
                        )
                        i = varEnd
                        cursor = i
                        continue
                    }
                }
            }
            i++
        }
        if (cursor < end) {
            outTokens.add(
                PowerShellToken(
                    PowerShellTokenType.STRING_EXPANDABLE,
                    cursor,
                    end,
                    line.substring(cursor, end)
                )
            )
        }
    }

    /**
     * Highlights a single line of code into an [AnnotatedString] with support for:
     * - First-class PowerShell lexical highlighting
     * - Secondary languages (JSON, XML, YAML, Markdown, Python, Kotlin, HTML/CSS, Shell/Bash)
     * - Search match highlights (`searchRanges` and `activeMatchRange`)
     * - Optional invisible character glyph rendering (`showInvisibles`)
     */
    fun highlightLine(
        line: String,
        language: SyntaxLanguage,
        palette: EditorSyntaxPalette,
        lineLexState: LineLexState = LineLexState.NORMAL,
        searchRanges: List<IntRange> = emptyList(),
        activeMatchRange: IntRange? = null,
        showInvisibles: Boolean = false
    ): AnnotatedString {
        val displayLine = if (showInvisibles) {
            buildString(line.length) {
                for (ch in line) {
                    when (ch) {
                        ' ' -> append('·')
                        '\t' -> append('→')
                        else -> append(ch)
                    }
                }
            }
        } else {
            line
        }

        return buildAnnotatedString {
            append(displayLine)

            // Default foreground
            addStyle(SpanStyle(color = palette.editorForeground), 0, displayLine.length)

            if (showInvisibles) {
                for (idx in line.indices) {
                    if (line[idx] == ' ' || line[idx] == '\t') {
                        addStyle(SpanStyle(color = palette.invisibleGlyphColor), idx, idx + 1)
                    }
                }
            }

            when (language) {
                SyntaxLanguage.POWERSHELL -> applyPowerShellSpans(line, lineLexState, palette)
                SyntaxLanguage.JSON -> applyJsonSpans(line, palette)
                SyntaxLanguage.XML, SyntaxLanguage.HTML_CSS -> applyXmlHtmlSpans(line, palette)
                SyntaxLanguage.YAML -> applyYamlSpans(line, palette)
                SyntaxLanguage.MARKDOWN -> applyMarkdownSpans(line, palette)
                SyntaxLanguage.PYTHON -> applyGenericCodeSpans(
                    line = line,
                    palette = palette,
                    keywords = PYTHON_KEYWORDS,
                    commentPrefix = "#"
                )
                SyntaxLanguage.KOTLIN -> applyGenericCodeSpans(
                    line = line,
                    palette = palette,
                    keywords = KOTLIN_KEYWORDS,
                    commentPrefix = "//"
                )
                SyntaxLanguage.SHELL_BASH -> applyGenericCodeSpans(
                    line = line,
                    palette = palette,
                    keywords = BASH_KEYWORDS,
                    commentPrefix = "#",
                    highlightDollarVars = true
                )
                SyntaxLanguage.PLAIN_TEXT -> Unit
            }

            // Overlay search matches
            for (range in searchRanges) {
                val start = range.first.coerceIn(0, displayLine.length)
                val end = (range.last + 1).coerceIn(start, displayLine.length)
                if (start < end) {
                    val isActive = activeMatchRange != null &&
                        activeMatchRange.first == range.first &&
                        activeMatchRange.last == range.last
                    addStyle(
                        SpanStyle(
                            background = if (isActive) {
                                palette.searchActiveMatchBackground
                            } else {
                                palette.searchMatchBackground
                            },
                            fontWeight = if (isActive) FontWeight.Bold else null
                        ),
                        start,
                        end
                    )
                }
            }
        }
    }

    fun highlightDocument(
        text: String,
        language: SyntaxLanguage,
        palette: EditorSyntaxPalette
    ): AnnotatedString {
        val docLines = text.split('\n')
        val states = computeLineStates(docLines, language)
        return buildAnnotatedString {
            for (idx in docLines.indices) {
                append(
                    highlightLine(
                        line = docLines[idx],
                        language = language,
                        palette = palette,
                        lineLexState = states.getOrElse(idx) { LineLexState.NORMAL }
                    )
                )
                if (idx < docLines.lastIndex) {
                    append('\n')
                }
            }
        }
    }

    private fun AnnotatedString.Builder.applyPowerShellSpans(
        line: String,
        lineLexState: LineLexState,
        palette: EditorSyntaxPalette
    ) {
        val tokens = tokenizePowerShellLine(line, lineLexState)
        for (token in tokens) {
            val style = when (token.type) {
                PowerShellTokenType.WHITESPACE, PowerShellTokenType.IDENTIFIER -> null
                PowerShellTokenType.KEYWORD -> SpanStyle(
                    color = palette.keyword,
                    fontWeight = FontWeight.SemiBold
                )
                PowerShellTokenType.CMDLET -> SpanStyle(
                    color = palette.cmdlet,
                    fontWeight = FontWeight.Medium
                )
                PowerShellTokenType.VARIABLE_NORMAL -> SpanStyle(color = palette.variableNormal)
                PowerShellTokenType.VARIABLE_SCOPED -> SpanStyle(
                    color = palette.variableScoped,
                    fontWeight = FontWeight.Medium
                )
                PowerShellTokenType.VARIABLE_AUTOMATIC -> SpanStyle(
                    color = palette.variableAutomatic,
                    fontWeight = FontWeight.SemiBold
                )
                PowerShellTokenType.PARAMETER -> SpanStyle(color = palette.parameter)
                PowerShellTokenType.OPERATOR -> SpanStyle(
                    color = palette.operatorToken,
                    fontWeight = FontWeight.Medium
                )
                PowerShellTokenType.STRING_LITERAL, PowerShellTokenType.STRING_EXPANDABLE ->
                    SpanStyle(color = palette.stringLiteral)
                PowerShellTokenType.STRING_INTERPOLATION -> SpanStyle(
                    color = palette.stringInterpolation,
                    fontWeight = FontWeight.Medium
                )
                PowerShellTokenType.NUMBER -> SpanStyle(color = palette.number)
                PowerShellTokenType.TYPE_ATTRIBUTE, PowerShellTokenType.HASHTABLE_PREFIX ->
                    SpanStyle(color = palette.typeAttribute)
                PowerShellTokenType.COMMENT -> SpanStyle(
                    color = palette.comment,
                    fontStyle = FontStyle.Italic
                )
                PowerShellTokenType.COMMENT_HELP_TAG -> SpanStyle(
                    color = palette.commentDocTag,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                )
                PowerShellTokenType.REGION_DIRECTIVE -> SpanStyle(
                    color = palette.commentDocTag,
                    fontWeight = FontWeight.SemiBold
                )
                PowerShellTokenType.PUNCTUATION -> SpanStyle(color = palette.punctuation)
            }
            if (style != null && token.start < token.end) {
                addStyle(style, token.start, token.end)
            }
        }
    }

    // =========================================================================
    // SECONDARY SYNTAX HIGHLIGHTERS (JSON, XML/HTML, YAML, Markdown, Kotlin, Python, Bash)
    // =========================================================================

    private val JSON_KEY_REGEX = Regex(""""([^"\\]|\\.)*"\s*(?=:)""")
    private val JSON_STRING_REGEX = Regex(""":\s*("([^"\\]|\\.)*")""")
    private val JSON_NUMBER_BOOL_REGEX = Regex("""\b(true|false|null|-?\d+(\.\d+)?([eE][+-]?\d+)?)\b""")

    private fun AnnotatedString.Builder.applyJsonSpans(line: String, palette: EditorSyntaxPalette) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//")) {
            addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), 0, line.length)
            return
        }
        for (m in JSON_NUMBER_BOOL_REGEX.findAll(line)) {
            val isBoolOrNull = m.value == "true" || m.value == "false" || m.value == "null"
            addStyle(
                SpanStyle(
                    color = if (isBoolOrNull) palette.keyword else palette.number,
                    fontWeight = if (isBoolOrNull) FontWeight.SemiBold else FontWeight.Normal
                ),
                m.range.first,
                m.range.last + 1
            )
        }
        for (m in JSON_STRING_REGEX.findAll(line)) {
            val group = m.groups[1] ?: continue
            addStyle(SpanStyle(color = palette.stringLiteral), group.range.first, group.range.last + 1)
        }
        for (m in JSON_KEY_REGEX.findAll(line)) {
            addStyle(
                SpanStyle(color = palette.propertyKey, fontWeight = FontWeight.Medium),
                m.range.first,
                m.range.last + 1
            )
        }
    }

    private val XML_TAG_REGEX = Regex("""</?[A-Za-z0-9_:\-.]+|\s*/?>""")
    private val XML_ATTR_REGEX = Regex("""\b([A-Za-z0-9_:\-.]+)\s*=\s*("[^"]*"|'[^']*')""")
    private val XML_COMMENT_REGEX = Regex("""<!--.*?-->""")

    private fun AnnotatedString.Builder.applyXmlHtmlSpans(line: String, palette: EditorSyntaxPalette) {
        for (m in XML_TAG_REGEX.findAll(line)) {
            addStyle(
                SpanStyle(color = palette.keyword, fontWeight = FontWeight.Medium),
                m.range.first,
                m.range.last + 1
            )
        }
        for (m in XML_ATTR_REGEX.findAll(line)) {
            val attrName = m.groups[1]
            val attrVal = m.groups[2]
            if (attrName != null) {
                addStyle(SpanStyle(color = palette.parameter), attrName.range.first, attrName.range.last + 1)
            }
            if (attrVal != null) {
                addStyle(SpanStyle(color = palette.stringLiteral), attrVal.range.first, attrVal.range.last + 1)
            }
        }
        for (m in XML_COMMENT_REGEX.findAll(line)) {
            addStyle(
                SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                m.range.first,
                m.range.last + 1
            )
        }
    }

    private val YAML_KEY_REGEX = Regex("""^\s*-?\s*([A-Za-z0-9_.\-]+)\s*:""")

    private fun AnnotatedString.Builder.applyYamlSpans(line: String, palette: EditorSyntaxPalette) {
        val commentIdx = line.indexOf('#')
        val codePart = if (commentIdx >= 0) line.substring(0, commentIdx) else line
        for (m in YAML_KEY_REGEX.findAll(codePart)) {
            val keyGroup = m.groups[1] ?: continue
            addStyle(
                SpanStyle(color = palette.propertyKey, fontWeight = FontWeight.Medium),
                keyGroup.range.first,
                keyGroup.range.last + 1
            )
        }
        for (m in JSON_NUMBER_BOOL_REGEX.findAll(codePart)) {
            addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
        }
        if (commentIdx >= 0) {
            addStyle(
                SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                commentIdx,
                line.length
            )
        }
    }

    private val MD_INLINE_CODE_REGEX = Regex("""`[^`]+`""")
    private val MD_BOLD_REGEX = Regex("""(\*\*|__)[^*_]+(\*\*|__)""")
    private val MD_LINK_REGEX = Regex("""\[([^\]]+)]\(([^)]+)\)""")

    private fun AnnotatedString.Builder.applyMarkdownSpans(line: String, palette: EditorSyntaxPalette) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("#")) {
            addStyle(
                SpanStyle(color = palette.markdownHeading, fontWeight = FontWeight.Bold),
                0,
                line.length
            )
            return
        }
        if (trimmed.startsWith(">")) {
            addStyle(
                SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                0,
                line.length
            )
            return
        }
        for (m in MD_INLINE_CODE_REGEX.findAll(line)) {
            addStyle(SpanStyle(color = palette.stringLiteral), m.range.first, m.range.last + 1)
        }
        for (m in MD_BOLD_REGEX.findAll(line)) {
            addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
        }
        for (m in MD_LINK_REGEX.findAll(line)) {
            addStyle(
                SpanStyle(color = palette.cmdlet, textDecoration = TextDecoration.Underline),
                m.range.first,
                m.range.last + 1
            )
        }
    }

    private val GENERIC_WORD_REGEX = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""")
    private val GENERIC_STRING_REGEX = Regex(""""([^"\\]|\\.)*"|'([^'\\]|\\.)*'""")
    private val GENERIC_NUMBER_REGEX = Regex("""\b(0x[0-9a-fA-F]+|\d+(\.\d+)?)\b""")
    private val GENERIC_VAR_REGEX = Regex("""\$[A-Za-z_][A-Za-z0-9_]*|\$\{[^}]+\}""")

    private val KOTLIN_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", "if", "else",
        "when", "for", "while", "do", "return", "break", "continue", "true", "false", "null",
        "this", "super", "is", "in", "as", "try", "catch", "finally", "throw", "private",
        "protected", "public", "internal", "override", "open", "abstract", "sealed", "data",
        "enum", "companion", "suspend", "inline", "reified", "by", "init", "where"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "if", "elif", "else", "for", "while", "try", "except", "finally",
        "with", "as", "import", "from", "return", "yield", "pass", "break", "continue",
        "lambda", "True", "False", "None", "and", "or", "not", "in", "is", "async", "await",
        "global", "nonlocal", "raise", "assert", "del", "self"
    )

    private val BASH_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "in", "while", "until",
        "do", "done", "function", "select", "time", "export", "local", "readonly", "declare",
        "typeset", "unset", "shift", "return", "exit", "source", "echo", "printf", "read",
        "cd", "pwd", "test", "true", "false"
    )

    private fun AnnotatedString.Builder.applyGenericCodeSpans(
        line: String,
        palette: EditorSyntaxPalette,
        keywords: Set<String>,
        commentPrefix: String,
        highlightDollarVars: Boolean = false
    ) {
        val commentIdx = line.indexOf(commentPrefix)
        val codeEnd = if (commentIdx >= 0) commentIdx else line.length
        val codeSegment = line.substring(0, codeEnd)

        for (m in GENERIC_NUMBER_REGEX.findAll(codeSegment)) {
            addStyle(SpanStyle(color = palette.number), m.range.first, m.range.last + 1)
        }
        for (m in GENERIC_WORD_REGEX.findAll(codeSegment)) {
            if (m.value in keywords) {
                addStyle(
                    SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold),
                    m.range.first,
                    m.range.last + 1
                )
            } else if (m.value.firstOrNull()?.isUpperCase() == true) {
                addStyle(SpanStyle(color = palette.typeAttribute), m.range.first, m.range.last + 1)
            }
        }
        if (highlightDollarVars) {
            for (m in GENERIC_VAR_REGEX.findAll(codeSegment)) {
                addStyle(SpanStyle(color = palette.variableNormal), m.range.first, m.range.last + 1)
            }
        }
        for (m in GENERIC_STRING_REGEX.findAll(codeSegment)) {
            addStyle(SpanStyle(color = palette.stringLiteral), m.range.first, m.range.last + 1)
        }
        if (commentIdx >= 0) {
            addStyle(
                SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic),
                commentIdx,
                line.length
            )
        }
    }

    // =========================================================================
    // CODE FOLDING & AST FUNCTION NAVIGATOR PARSER
    // =========================================================================

    /**
     * Extracts all declared PowerShell functions, filters, workflows, classes, configurations,
     * and `#region` markers for the top-bar Function Navigator dropdown.
     */
    fun extractAstSymbols(content: String): List<AstSymbol> {
        if (content.isBlank()) return emptyList()
        val lines = content.split('\n')
        val symbols = ArrayList<AstSymbol>()

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            val fnMatch = FUNCTION_DECL_REGEX.find(line)
            if (fnMatch != null) {
                val keyword = fnMatch.groupValues[1].lowercase()
                val name = fnMatch.groupValues[2]
                val rawParams = fnMatch.groupValues[4].trim()
                val paramsList = if (rawParams.isEmpty()) {
                    emptyList()
                } else {
                    rawParams.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                }
                val kind = when (keyword) {
                    "filter" -> SymbolKind.FILTER
                    "workflow" -> SymbolKind.WORKFLOW
                    "configuration" -> SymbolKind.CONFIGURATION
                    else -> SymbolKind.FUNCTION
                }
                val sig = if (rawParams.isNotEmpty()) "$name($rawParams)" else "$name()"
                symbols.add(
                    AstSymbol(
                        name = name,
                        kind = kind,
                        lineIndex = lineIdx,
                        lineNumber = lineIdx + 1,
                        signature = sig,
                        parameters = paramsList
                    )
                )
                continue
            }

            val clsMatch = CLASS_DECL_REGEX.find(line)
            if (clsMatch != null) {
                val name = clsMatch.groupValues[2]
                symbols.add(
                    AstSymbol(
                        name = name,
                        kind = SymbolKind.CLASS,
                        lineIndex = lineIdx,
                        lineNumber = lineIdx + 1,
                        signature = "class $name"
                    )
                )
                continue
            }

            val regionMatch = REGION_START_REGEX.find(line)
            if (regionMatch != null) {
                val label = regionMatch.groupValues[1].trim().ifEmpty { "Region @ Ln ${lineIdx + 1}" }
                symbols.add(
                    AstSymbol(
                        name = label,
                        kind = SymbolKind.REGION,
                        lineIndex = lineIdx,
                        lineNumber = lineIdx + 1,
                        signature = "#region $label"
                    )
                )
            }
        }
        return symbols
    }

    /**
     * Parses foldable code blocks for `function ... { }`, `filter ... { }`, `workflow ... { }`,
     * `class ... { }`, `#region ... #endregion`, and `<# ... #>` multi-line comments.
     */
    fun extractFoldRegions(content: String): List<FoldRegion> {
        if (content.isBlank()) return emptyList()
        val lines = content.split('\n')
        val regions = ArrayList<FoldRegion>()

        val regionStack = ArrayDeque<Pair<Int, String>>()
        var commentBlockStart: Int? = null

        data class BraceFrame(val startLine: Int, val label: String, val kind: FoldKind, val baseDepth: Int)
        val braceFrames = ArrayDeque<BraceFrame>()
        var currentBraceDepth = 0
        var pendingDecl: Triple<Int, String, FoldKind>? = null

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            val trimmed = line.trim()

            // 1. Multi-line comment blocks <# ... #>
            if (commentBlockStart == null && trimmed.startsWith("<#") && !trimmed.contains("#>")) {
                commentBlockStart = lineIdx
                continue
            }
            if (commentBlockStart != null) {
                if (trimmed.contains("#>")) {
                    val start = commentBlockStart
                    if (lineIdx > start) {
                        regions.add(
                            FoldRegion(
                                id = "comment_$start",
                                startLine = start,
                                endLine = lineIdx,
                                label = "<# Comment Block (${lineIdx - start + 1} lines) #>",
                                kind = FoldKind.COMMENT_BLOCK
                            )
                        )
                    }
                    commentBlockStart = null
                }
                continue
            }

            // 2. #region ... #endregion
            val regStart = REGION_START_REGEX.find(line)
            if (regStart != null) {
                val desc = regStart.groupValues[1].trim().ifEmpty { "Region" }
                regionStack.addLast(lineIdx to desc)
                continue
            }
            if (REGION_END_REGEX.containsMatchIn(line) && regionStack.isNotEmpty()) {
                val (startLine, desc) = regionStack.removeLast()
                if (lineIdx > startLine) {
                    regions.add(
                        FoldRegion(
                            id = "region_$startLine",
                            startLine = startLine,
                            endLine = lineIdx,
                            label = "#region $desc",
                            kind = FoldKind.REGION
                        )
                    )
                }
                continue
            }

            // 3. Function / Filter / Workflow / Class declarations
            val fnMatch = FUNCTION_DECL_REGEX.find(line)
            if (fnMatch != null) {
                val kw = fnMatch.groupValues[1].lowercase()
                val name = fnMatch.groupValues[2]
                val kind = when (kw) {
                    "filter" -> FoldKind.FILTER
                    "workflow" -> FoldKind.WORKFLOW
                    else -> FoldKind.FUNCTION
                }
                pendingDecl = Triple(lineIdx, "$kw $name { … }", kind)
            } else {
                val clsMatch = CLASS_DECL_REGEX.find(line)
                if (clsMatch != null) {
                    val kw = clsMatch.groupValues[1].lowercase()
                    val name = clsMatch.groupValues[2]
                    pendingDecl = Triple(lineIdx, "$kw $name { … }", FoldKind.CLASS)
                }
            }

            // Count braces outside comments and strings
            val stripped = stripStringsAndCommentsFromLine(line)
            for (ch in stripped) {
                if (ch == '{') {
                    val decl = pendingDecl
                    if (decl != null) {
                        braceFrames.addLast(
                            BraceFrame(
                                startLine = decl.first,
                                label = decl.second,
                                kind = decl.third,
                                baseDepth = currentBraceDepth
                            )
                        )
                        pendingDecl = null
                    }
                    currentBraceDepth++
                } else if (ch == '}') {
                    currentBraceDepth = (currentBraceDepth - 1).coerceAtLeast(0)
                    if (braceFrames.isNotEmpty() && braceFrames.last().baseDepth == currentBraceDepth) {
                        val frame = braceFrames.removeLast()
                        if (lineIdx > frame.startLine) {
                            regions.add(
                                FoldRegion(
                                    id = "fold_${frame.startLine}",
                                    startLine = frame.startLine,
                                    endLine = lineIdx,
                                    label = frame.label,
                                    kind = frame.kind
                                )
                            )
                        }
                    }
                }
            }
        }

        return regions.sortedBy { it.startLine }
    }

    private fun stripStringsAndCommentsFromLine(line: String): String {
        val sb = StringBuilder(line.length)
        var i = 0
        val len = line.length
        while (i < len) {
            val ch = line[i]
            if (ch == '#') break
            if (ch == '\'' || ch == '"') {
                val q = ch
                i++
                while (i < len && line[i] != q) {
                    if (line[i] == '`' && i + 1 < len) i++
                    i++
                }
                i++
                continue
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    // =========================================================================
    // INLINE POWERSHELL AUTO-COMPLETION ENGINE (CMDLETS, $VARS, -PARAMS, [TYPES])
    // =========================================================================

    private val USER_VARIABLE_EXTRACT_REGEX = Regex("""\$(?:(?:env|global|script|private|local):)?[A-Za-z_][A-Za-z0-9_]*""")

    private val COMMON_CMDLET_CATALOG: List<Pair<String, String>> = listOf(
        "Get-ChildItem" to "Gets items and child items in one or more locations (dir / ls / gci)",
        "Invoke-RestMethod" to "Sends an HTTP or HTTPS request to a RESTful web service (irm)",
        "Invoke-WebRequest" to "Gets content from a web page on the internet (iwr)",
        "Where-Object" to "Selects objects from a collection based on property values (? / where)",
        "ForEach-Object" to "Performs an operation against each item in a collection (% / foreach)",
        "Select-Object" to "Selects specified properties of an object or set of objects (select)",
        "Sort-Object" to "Sorts objects by property values (sort)",
        "Group-Object" to "Groups objects that contain the same value for specified properties",
        "Measure-Object" to "Calculates numeric properties of objects and characters/words/lines",
        "Get-Content" to "Gets the content of the item at the specified location (cat / gc)",
        "Set-Content" to "Writes new content or replaces the content in a file (sc)",
        "Add-Content" to "Appends content to the specified items (ac)",
        "Out-File" to "Sends output to a file",
        "ConvertTo-Json" to "Converts an object to a JSON-formatted string",
        "ConvertFrom-Json" to "Converts a JSON-formatted string to a custom PSObject or Hashtable",
        "Export-Csv" to "Converts objects into a series of CSV strings and saves them to a file",
        "Import-Csv" to "Creates table-like custom objects from the items in a CSV file",
        "Test-Path" to "Determines whether all elements of a file or registry path exist",
        "Join-Path" to "Combines a path and a child path into a single path",
        "Resolve-Path" to "Resolves the wildcard characters in a path",
        "New-Item" to "Creates a new file, directory, or registry key (ni)",
        "Remove-Item" to "Deletes files, folders, registry keys, or variables (rm / del / ri)",
        "Copy-Item" to "Copies an item from one location to another (cp / copy / cpi)",
        "Move-Item" to "Moves an item from one location to another (mv / move / mi)",
        "Get-Item" to "Gets the item at the specified location (gi)",
        "Get-Process" to "Gets the processes that are running on the local computer (ps / gps)",
        "Start-Process" to "Starts one or more processes on the local computer (saps)",
        "Stop-Process" to "Stops one or more running processes (kill / spps)",
        "Get-Service" to "Gets the services on the computer (gsv)",
        "Restart-Service" to "Stops and then starts one or more services",
        "Write-Host" to "Writes customized output to a host console",
        "Write-Output" to "Sends the specified objects to the next command in the pipeline (echo)",
        "Write-Verbose" to "Writes text to the verbose message stream",
        "Write-Warning" to "Writes a warning message to the PowerShell host",
        "Write-Error" to "Writes an object to the error stream",
        "Set-ExecutionPolicy" to "Changes the PowerShell script execution policy",
        "Import-Module" to "Adds modules to the current session (ipmo)",
        "Get-Command" to "Gets all commands installed on the system (gcm)",
        "Get-Help" to "Displays information about PowerShell commands and concepts (man)",
        "Get-Member" to "Gets the properties and methods of objects (gm)",
        "Get-Date" to "Gets the current date and time",
        "Start-Sleep" to "Suspends the activity in a script or session for the specified time"
    )

    private val COMMON_PARAMETER_CATALOG: List<Pair<String, String>> = listOf(
        "-Path" to "Target file or directory path (supports wildcards)",
        "-LiteralPath" to "Target literal path (no wildcard expansion)",
        "-Filter" to "Provider-level filter string (e.g. '*.ps1')",
        "-Include" to "Items to include in the operation",
        "-Exclude" to "Items to exclude from the operation",
        "-Recurse" to "Gets items in the specified locations and all child items",
        "-Depth" to "Specifies the recursion or serialization depth",
        "-Force" to "Overrides restrictions that prevent the command from succeeding",
        "-ErrorAction" to "Action on non-terminating error (Stop, Continue, SilentlyContinue)",
        "-WarningAction" to "Action on warning (Continue, SilentlyContinue, Inquire, Stop)",
        "-Verbose" to "Displays detailed information about the operation",
        "-WhatIf" to "Shows what would happen if the cmdlet runs without executing",
        "-Confirm" to "Prompts you for confirmation before running the cmdlet",
        "-OutVariable" to "Stores output objects in the specified variable name",
        "-PipelineVariable" to "Stores the value of the current pipeline element in a variable",
        "-Property" to "Specifies the object properties to select or sort",
        "-ExpandProperty" to "Specifies a property to select and expand",
        "-Uri" to "Uniform Resource Identifier for web/REST requests",
        "-Method" to "HTTP method (Get, Post, Put, Delete, Patch)",
        "-Headers" to "Hashtable of HTTP request headers",
        "-Body" to "Request body payload for REST/Web calls",
        "-ContentType" to "Content-Type header (e.g. 'application/json')",
        "-TimeoutSec" to "Request timeout in seconds",
        "-Value" to "Specifies the value of the new item or content",
        "-Encoding" to "Specifies the character encoding (utf8, utf8BOM, unicode, ascii)"
    )

    private val BUILTIN_VARIABLE_CATALOG: List<Pair<String, String>> = listOf(
        "\$_" to "Current pipeline object (alias for \$PSItem)",
        "\$PSItem" to "Current object in the pipeline object stream",
        "\$true" to "Boolean True constant",
        "\$false" to "Boolean False constant",
        "\$null" to "Null / empty reference value",
        "\$PSScriptRoot" to "Directory from which the current script is being run",
        "\$PSCommandPath" to "Full path and file name of the script being run",
        "\$PSVersionTable" to "Read-only hashtable showing details about the PowerShell version",
        "\$PSCmdlet" to "Object representing the advanced function or cmdlet being run",
        "\$PSBoundParameters" to "Dictionary of parameters passed to a script or function",
        "\$Error" to "Array of error objects representing the most recent errors",
        "\$LastExitCode" to "Exit code of the last native program or script that ran",
        "\$Args" to "Array of undeclared parameters/values passed to a function or script",
        "\$MyInvocation" to "Information about the current command, script, or invocation",
        "\$Matches" to "Hashtable of regex match values populated by -match / switch -Regex",
        "\$env:PATH" to "System PATH environment variable",
        "\$env:HOME" to "User home directory environment variable",
        "\$env:TEMP" to "Temporary directory environment variable",
        "\$env:COMPUTERNAME" to "Local host / computer name environment variable",
        "\$global:" to "Global variable scope modifier",
        "\$script:" to "Script-wide variable scope modifier",
        "\$private:" to "Private variable scope modifier"
    )

    private val TYPE_ATTRIBUTE_CATALOG: List<Triple<String, String, String>> = listOf(
        Triple("[CmdletBinding()]", "[CmdletBinding()]", "Turns a function into an advanced cmdlet-style function"),
        Triple("[Parameter(Mandatory = \$true)]", "[Parameter(Mandatory = \$true)]", "Declares a mandatory function parameter"),
        Triple("[ValidateNotNullOrEmpty()]", "[ValidateNotNullOrEmpty()]", "Validates that parameter argument is not null or empty"),
        Triple("[ValidateSet()]", "[ValidateSet('Val1', 'Val2')]", "Restricts parameter values to a predefined set"),
        Triple("[OutputType()]", "[OutputType([pscustomobject])]", "Declares the return object type of a function"),
        Triple("[pscustomobject]", "[pscustomobject]", "PowerShell custom object type accelerator"),
        Triple("[ordered]", "[ordered]", "Creates an ordered dictionary hashtable ([ordered]@{})"),
        Triple("[string]", "[string]", "System.String type accelerator"),
        Triple("[string[]]", "[string[]]", "String array type accelerator"),
        Triple("[int]", "[int]", "System.Int32 32-bit integer type accelerator"),
        Triple("[long]", "[long]", "System.Int64 64-bit integer type accelerator"),
        Triple("[bool]", "[bool]", "System.Boolean type accelerator"),
        Triple("[switch]", "[switch]", "Switch parameter type accelerator"),
        Triple("[hashtable]", "[hashtable]", "System.Collections.Hashtable type accelerator"),
        Triple("[datetime]", "[datetime]", "System.DateTime type accelerator"),
        Triple("[regex]", "[regex]", "System.Text.RegularExpressions.Regex type accelerator"),
        Triple("[uri]", "[uri]", "System.Uri type accelerator"),
        Triple("[xml]", "[xml]", "System.Xml.XmlDocument type accelerator"),
        Triple("[guid]", "[guid]", "System.Guid type accelerator")
    )

    /**
     * Computes context-aware inline PowerShell completions at [cursorColumn] in [line].
     * Suggests:
     * - `$Variables` (built-in automatic/scoped variables + user variables declared in [documentContent])
     * - `-Parameters` and `-Operators` when typing a token starting with `-`
     * - `[TypeAccelerators]` and `[Attributes]` when typing a token starting with `[`
     * - `Verb-Noun` Cmdlets, Keywords, and AST Functions declared in the current script
     */
    fun computeCompletions(
        line: String,
        cursorColumn: Int,
        documentContent: String = "",
        astSymbols: List<AstSymbol> = emptyList(),
        maxResults: Int = 8
    ): List<PowerShellCompletionItem> {
        val safeCursor = cursorColumn.coerceIn(0, line.length)
        if (safeCursor == 0) return emptyList()

        // Find start of token before safeCursor
        var tokenStart = safeCursor - 1
        while (tokenStart >= 0) {
            val ch = line[tokenStart]
            if (ch == '$' || ch == '[') {
                break
            }
            if (ch == '-' && (tokenStart == 0 || line[tokenStart - 1].isWhitespace() || line[tokenStart - 1] in "({|;=")) {
                break
            }
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == ':') {
                if (tokenStart == 0) break
                tokenStart--
            } else {
                tokenStart++
                break
            }
        }
        tokenStart = tokenStart.coerceIn(0, safeCursor)
        val rawPrefix = line.substring(tokenStart, safeCursor)
        if (rawPrefix.isEmpty()) return emptyList()

        val lowerPrefix = rawPrefix.lowercase()
        val candidates = ArrayList<PowerShellCompletionItem>()

        when {
            // 1. Variable completions ($...)
            rawPrefix.startsWith("$") -> {
                val userVars = LinkedHashSet<String>()
                for (m in USER_VARIABLE_EXTRACT_REGEX.findAll(documentContent)) {
                    if (!m.value.equals(rawPrefix, ignoreCase = true)) {
                        userVars.add(m.value)
                    }
                }
                for (uVar in userVars) {
                    if (uVar.lowercase().startsWith(lowerPrefix)) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = uVar,
                                insertText = uVar,
                                kind = CompletionKind.VARIABLE,
                                detail = "Script Variable",
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = safeCursor
                            )
                        )
                    }
                }
                for ((varName, desc) in BUILTIN_VARIABLE_CATALOG) {
                    if (varName.lowercase().startsWith(lowerPrefix) && !varName.equals(rawPrefix, ignoreCase = true)) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = varName,
                                insertText = varName,
                                kind = CompletionKind.VARIABLE,
                                detail = desc,
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = safeCursor
                            )
                        )
                    }
                }
            }

            // 2. Parameter & Operator completions (-...)
            rawPrefix.startsWith("-") -> {
                if (rawPrefix.length < 2) return emptyList()
                for ((paramName, desc) in COMMON_PARAMETER_CATALOG) {
                    if (paramName.lowercase().startsWith(lowerPrefix) && !paramName.equals(rawPrefix, ignoreCase = true)) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = paramName,
                                insertText = paramName,
                                kind = CompletionKind.PARAMETER,
                                detail = desc,
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = safeCursor
                            )
                        )
                    }
                }
                for (op in POWERSHELL_OPERATORS) {
                    if (op.startsWith(lowerPrefix) && !op.equals(rawPrefix, ignoreCase = true)) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = op,
                                insertText = op,
                                kind = CompletionKind.PARAMETER,
                                detail = "PowerShell Operator",
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = safeCursor
                            )
                        )
                    }
                }
            }

            // 3. Type & Attribute completions ([...)
            rawPrefix.startsWith("[") -> {
                val replaceEnd = if (safeCursor < line.length && line[safeCursor] == ']') {
                    safeCursor + 1
                } else {
                    safeCursor
                }
                for ((label, insert, desc) in TYPE_ATTRIBUTE_CATALOG) {
                    if (label.lowercase().startsWith(lowerPrefix) && !label.equals(rawPrefix, ignoreCase = true)) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = label,
                                insertText = insert,
                                kind = CompletionKind.TYPE_ACCELERATOR,
                                detail = desc,
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = replaceEnd
                            )
                        )
                    }
                }
            }

            // 4. Cmdlets, Local AST Functions & Keywords
            else -> {
                if (rawPrefix.length < 2) return emptyList()
                for (sym in astSymbols) {
                    if (sym.kind == SymbolKind.REGION) continue
                    if (sym.name.lowercase().startsWith(lowerPrefix) && !sym.name.equals(rawPrefix, ignoreCase = true)) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = sym.name,
                                insertText = sym.name,
                                kind = CompletionKind.FUNCTION,
                                detail = "${sym.signature} (Ln ${sym.lineNumber})",
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = safeCursor
                            )
                        )
                    }
                }
                for ((cmdlet, desc) in COMMON_CMDLET_CATALOG) {
                    val lowerCmd = cmdlet.lowercase()
                    if ((lowerCmd.startsWith(lowerPrefix) || lowerCmd.contains(lowerPrefix)) &&
                        !cmdlet.equals(rawPrefix, ignoreCase = true)
                    ) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = cmdlet,
                                insertText = cmdlet,
                                kind = CompletionKind.CMDLET,
                                detail = desc,
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = safeCursor
                            )
                        )
                    }
                }
                for (kw in POWERSHELL_KEYWORDS) {
                    if (kw.startsWith(lowerPrefix) && !kw.equals(rawPrefix, ignoreCase = true)) {
                        candidates.add(
                            PowerShellCompletionItem(
                                label = kw,
                                insertText = kw,
                                kind = CompletionKind.KEYWORD,
                                detail = "PowerShell Keyword",
                                replaceStartInLine = tokenStart,
                                replaceEndInLine = safeCursor
                            )
                        )
                    }
                }
            }
        }

        return candidates
            .sortedWith(
                compareByDescending<PowerShellCompletionItem> {
                    it.label.lowercase().startsWith(lowerPrefix)
                }.thenBy { it.label.length }
            )
            .take(maxResults)
    }
}

enum class CompletionKind(val badge: String) {
    CMDLET("cmd"),
    VARIABLE("var"),
    PARAMETER("prm"),
    KEYWORD("kw"),
    TYPE_ACCELERATOR("type"),
    FUNCTION("fn")
}

data class PowerShellCompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val detail: String,
    val replaceStartInLine: Int,
    val replaceEndInLine: Int
)

