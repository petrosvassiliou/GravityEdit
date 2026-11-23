package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.CmdletHelpParameter
import com.gravityedit.app.domain.model.CmdletHelpTopic
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.ParameterFieldSpec
import com.gravityedit.app.domain.model.ParameterFormSpec
import com.gravityedit.app.domain.model.PesterSuiteResult
import com.gravityedit.app.domain.model.PesterTestCaseResult
import com.gravityedit.app.domain.model.PesterTestCaseSpec
import kotlin.math.abs

object PowerShellToolingEngine {

    private val FUNCTION_HEADER_REGEX = Regex(
        pattern = """(?im)\b(function|filter)\s+([A-Za-z0-9_-]+)\s*(?:\(([^)]*)\))?\s*\{"""
    )
    private val PARAM_KEYWORD_REGEX = Regex(pattern = """(?im)\bparam\s*\(""")
    private val MANDATORY_ATTR_REGEX = Regex(pattern = """(?i)\bmandatory\s*(?:=\s*\$?(true|false|1|0))?""")
    private val HELP_MSG_ATTR_REGEX = Regex(pattern = """(?i)helpmessage\s*=\s*(['"])(.*?)\1""")
    private val SYNOPSIS_BLOCK_REGEX = Regex(pattern = """(?is)<#.*?\.SYNOPSIS\s*(.*?)(?:\.[A-Z]{3,}|#>)""")
    private val PESTER_SCOPE_REGEX = Regex(pattern = """(?im)\b(Describe|Context|It)\s+(?:-Name\s+)?(['"])(.*?)\2\s*\{""")
    private val PESTER_DESCRIBE_REGEX = Regex(pattern = """(?im)\bDescribe\s+(?:-Name\s+)?(['"])(.*?)\1\s*\{""")
    private val SHOULD_PIPE_REGEX = Regex(pattern = """\|\s*Should\b""", option = RegexOption.IGNORE_CASE)

    // MARK: - Show-Command Parameter Form Builder

    fun extractParameterFormSpecs(
        scriptContent: String,
        defaultScriptName: String = "CurrentScript.ps1"
    ): List<ParameterFormSpec> {
        val normalized = LineEnding.normalizeToLf(scriptContent)
        val specs = ArrayList<ParameterFormSpec>()
        val consumedRanges = ArrayList<IntRange>()

        for (match in FUNCTION_HEADER_REGEX.findAll(normalized)) {
            val fnName = match.groups[2]?.value ?: continue
            val openBraceIdx = match.range.last
            val closeBraceIdx = findMatchingDelimiter(normalized, openBraceIdx, '{', '}') ?: continue
            val fullSpan = match.range.first..closeBraceIdx
            consumedRanges.add(fullSpan)

            val fnSlice = normalized.substring(match.range.first, closeBraceIdx + 1)
            val synopsis = extractCommentSynopsis(fnSlice)
                ?: extractPrecedingCommentSynopsis(normalized, match.range.first)

            val inlineRaw = match.groups[3]?.value
            if (!inlineRaw.isNullOrBlank()) {
                val inlineFields = parseParamListContent(inlineRaw)
                if (inlineFields.isNotEmpty()) {
                    specs.add(
                        ParameterFormSpec(
                            commandName = fnName,
                            isFunction = true,
                            synopsis = synopsis,
                            parameters = inlineFields
                        )
                    )
                    continue
                }
            }

            val innerBody = normalized.substring(openBraceIdx + 1, closeBraceIdx)
            val paramBody = extractFirstParamBlockBody(innerBody)
            val fields = if (paramBody != null) parseParamListContent(paramBody) else emptyList()
            specs.add(
                ParameterFormSpec(
                    commandName = fnName,
                    isFunction = true,
                    synopsis = synopsis,
                    parameters = fields
                )
            )
        }

        for (match in PARAM_KEYWORD_REGEX.findAll(normalized)) {
            val insideFn = consumedRanges.any { match.range.first in it }
            if (insideFn) continue
            val openParen = match.range.last
            val closeParen = findMatchingDelimiter(normalized, openParen, '(', ')') ?: continue
            val rawParams = normalized.substring(openParen + 1, closeParen)
            val fields = parseParamListContent(rawParams)
            specs.add(
                0,
                ParameterFormSpec(
                    commandName = defaultScriptName,
                    isFunction = false,
                    synopsis = extractCommentSynopsis(normalized),
                    parameters = fields
                )
            )
            break
        }

        return specs
    }

    fun buildInvocationCommand(
        spec: ParameterFormSpec,
        values: Map<String, String>,
        switchStates: Map<String, Boolean>
    ): String {
        val parts = ArrayList<String>()
        val targetCmd = if (spec.isFunction) {
            spec.commandName
        } else if (spec.commandName.startsWith(".") || spec.commandName.startsWith("/")) {
            spec.commandName
        } else {
            ".\\${spec.commandName}"
        }
        parts.add(targetCmd)

        for (param in spec.parameters) {
            if (param.isSwitch) {
                if (switchStates[param.name] == true) {
                    parts.add("-${param.name}")
                }
                continue
            }
            val rawVal = values[param.name]?.trim().orEmpty()
            if (rawVal.isEmpty()) continue
            parts.add("-${param.name} ${formatPowerShellArgument(rawVal, param.typeName)}")
        }
        return parts.joinToString(" ")
    }

    private fun formatPowerShellArgument(raw: String, typeName: String): String {
        val lowerType = typeName.lowercase()
        if ((raw.startsWith("'") && raw.endsWith("'")) ||
            (raw.startsWith("\"") && raw.endsWith("\"")) ||
            raw.startsWith("\$") ||
            raw.startsWith("@(") ||
            raw.startsWith("@{")
        ) {
            return raw
        }
        if (lowerType.contains("int") || lowerType.contains("double") || lowerType.contains("decimal") || lowerType.contains("long")) {
            if (raw.toDoubleOrNull() != null) return raw
        }
        if (lowerType.contains("bool")) {
            if (raw.equals("true", ignoreCase = true) || raw == "\$true") return "\$true"
            if (raw.equals("false", ignoreCase = true) || raw == "\$false") return "\$false"
        }
        val needsQuotes = raw.any { it.isWhitespace() || ";|&(){}[]\"'`".contains(it) } || lowerType == "string"
        return if (needsQuotes) "'${raw.replace("'", "''")}'" else raw
    }

    private fun extractFirstParamBlockBody(text: String): String? {
        val m = PARAM_KEYWORD_REGEX.find(text) ?: return null
        val openParen = m.range.last
        val closeParen = findMatchingDelimiter(text, openParen, '(', ')') ?: return null
        return text.substring(openParen + 1, closeParen)
    }

    private fun parseParamListContent(rawContent: String): List<ParameterFieldSpec> {
        val cleaned = rawContent.split('\n').joinToString("\n") { line ->
            var inSingle = false
            var inDouble = false
            val sb = StringBuilder()
            for (ch in line) {
                if (ch == '\'' && !inDouble) inSingle = !inSingle
                else if (ch == '"' && !inSingle) inDouble = !inDouble
                else if (ch == '#' && !inSingle && !inDouble) break
                sb.append(ch)
            }
            sb.toString()
        }
        return splitTopLevelCommas(cleaned)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseSingleParameterSegment(it) }
    }

    private fun splitTopLevelCommas(text: String): List<String> {
        val result = ArrayList<String>()
        val cur = StringBuilder()
        var paren = 0
        var bracket = 0
        var brace = 0
        var inSingle = false
        var inDouble = false

        for (ch in text) {
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                cur.append(ch)
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                cur.append(ch)
            } else if (!inSingle && !inDouble) {
                when {
                    ch == '(' -> { paren++; cur.append(ch) }
                    ch == ')' -> { paren = (paren - 1).coerceAtLeast(0); cur.append(ch) }
                    ch == '[' -> { bracket++; cur.append(ch) }
                    ch == ']' -> { bracket = (bracket - 1).coerceAtLeast(0); cur.append(ch) }
                    ch == '{' -> { brace++; cur.append(ch) }
                    ch == '}' -> { brace = (brace - 1).coerceAtLeast(0); cur.append(ch) }
                    ch == ',' && paren == 0 && bracket == 0 && brace == 0 -> {
                        result.add(cur.toString())
                        cur.clear()
                    }
                    else -> cur.append(ch)
                }
            } else {
                cur.append(ch)
            }
        }
        if (cur.toString().isNotBlank()) {
            result.add(cur.toString())
        }
        return result
    }

    private fun parseSingleParameterSegment(segment: String): ParameterFieldSpec? {
        var bracket = 0
        var paren = 0
        var inSingle = false
        var inDouble = false
        var dollarIdx = -1

        for (i in segment.indices) {
            val ch = segment[i]
            if (ch == '\'' && !inDouble) inSingle = !inSingle
            else if (ch == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                when {
                    ch == '[' -> bracket++
                    ch == ']' -> bracket = (bracket - 1).coerceAtLeast(0)
                    ch == '(' -> paren++
                    ch == ')' -> paren = (paren - 1).coerceAtLeast(0)
                    ch == '$' && bracket == 0 && paren == 0 -> {
                        dollarIdx = i
                        break
                    }
                }
            }
        }
        if (dollarIdx < 0) return null

        var varEnd = dollarIdx + 1
        while (varEnd < segment.length && (segment[varEnd].isLetterOrDigit() || segment[varEnd] == '_')) {
            varEnd++
        }
        val paramName = segment.substring(dollarIdx + 1, varEnd)
        if (paramName.isEmpty()) return null

        val attrPrefix = segment.substring(0, dollarIdx)
        val afterVar = segment.substring(varEnd).trim()
        val defaultVal = if (afterVar.startsWith("=")) {
            unquoteSimpleLiteral(afterVar.drop(1).trim()).takeIf { it.isNotEmpty() }
        } else {
            null
        }

        var isMandatory = false
        var helpMessage: String? = null
        var validateSet: List<String> = emptyList()
        var detectedType = "String"
        var isSwitch = false

        for (rawBlock in extractTopLevelBracketBlocks(attrPrefix)) {
            val block = rawBlock.trim()
            val lower = block.lowercase()
            when {
                lower.startsWith("parameter") -> {
                    MANDATORY_ATTR_REGEX.find(block)?.let { m ->
                        val explicit = m.groups[1]?.value?.lowercase()
                        isMandatory = explicit == null || explicit == "true" || explicit == "1"
                    }
                    HELP_MSG_ATTR_REGEX.find(block)?.let { hm ->
                        helpMessage = hm.groups[2]?.value
                    }
                }
                lower.startsWith("validateset") -> {
                    val openP = block.indexOf('(')
                    val closeP = block.lastIndexOf(')')
                    if (openP in 0 until closeP) {
                        val inner = block.substring(openP + 1, closeP)
                        validateSet = splitTopLevelCommas(inner)
                            .map { unquoteSimpleLiteral(it.trim()) }
                            .filter { it.isNotEmpty() }
                    }
                }
                lower.startsWith("validate") ||
                    lower.startsWith("alias") ||
                    lower.startsWith("allow") ||
                    lower.startsWith("outputtype") ||
                    lower.startsWith("cmdletbinding") -> Unit
                else -> {
                    detectedType = block
                    if (lower == "switch" || lower == "switchparameter" || lower.endsWith(".switchparameter")) {
                        isSwitch = true
                        detectedType = "Switch"
                    }
                }
            }
        }

        return ParameterFieldSpec(
            name = paramName,
            typeName = detectedType,
            isMandatory = isMandatory,
            isSwitch = isSwitch,
            validateSetOptions = validateSet,
            defaultValue = defaultVal,
            helpMessage = helpMessage
        )
    }

    private fun extractTopLevelBracketBlocks(text: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = -1
        var inSingle = false
        var inDouble = false
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '\'' && !inDouble) inSingle = !inSingle
            else if (ch == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                if (ch == '[') {
                    if (depth == 0) start = i + 1
                    depth++
                } else if (ch == ']') {
                    depth = (depth - 1).coerceAtLeast(0)
                    if (depth == 0 && start >= 0) {
                        out.add(text.substring(start, i))
                        start = -1
                    }
                }
            }
        }
        return out
    }

    // MARK: - F1 Cmdlet & Comment-Based Quick Help

    fun tokenUnderCursor(text: String, cursorOffset: Int): String {
        if (text.isEmpty()) return ""
        val clamped = cursorOffset.coerceIn(0, text.length)
        fun isCmdChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_'

        var probe = clamped
        if (probe == text.length || !isCmdChar(text[probe])) {
            if (probe > 0 && isCmdChar(text[probe - 1])) {
                probe -= 1
            } else {
                return ""
            }
        }
        var start = probe
        while (start > 0 && isCmdChar(text[start - 1])) start--
        var end = probe
        while (end < text.length && isCmdChar(text[end])) end++
        return text.substring(start, end).trim('-')
    }

    fun lookupQuickHelp(
        queryOrCursorToken: String,
        documentContent: String = ""
    ): CmdletHelpTopic {
        val effective = queryOrCursorToken.trim().ifEmpty { "Get-Help" }

        if (documentContent.isNotEmpty()) {
            val specs = extractParameterFormSpecs(documentContent)
            val matched = specs.firstOrNull { it.commandName.equals(effective, ignoreCase = true) }
            if (matched != null) {
                val synParts = matched.parameters.map { p ->
                    val body = if (p.isSwitch) "[-${p.name}]" else "-${p.name} <${p.typeName}>"
                    if (p.isMandatory) body else "[$body]"
                }
                val synLine = (listOf(matched.commandName) + synParts).joinToString(" ")
                val params = matched.parameters.map { p ->
                    CmdletHelpParameter(
                        name = p.name,
                        typeName = p.typeName,
                        isMandatory = p.isMandatory,
                        description = p.helpMessage
                            ?: if (p.validateSetOptions.isEmpty()) "Script parameter." else "Allowed: ${p.validateSetOptions.joinToString(", ")}"
                    )
                }
                return CmdletHelpTopic(
                    name = matched.commandName,
                    moduleName = "Active Buffer",
                    synopsis = matched.synopsis ?: "Function declared in the active PowerShell document.",
                    syntax = listOf(synLine),
                    parameters = params,
                    examples = listOf(buildInvocationCommand(matched, emptyMap(), emptyMap())),
                    sourceBadge = "Document AST"
                )
            }
        }

        builtInHelpTopics.firstOrNull { it.name.equals(effective, ignoreCase = true) }?.let { return it }
        resolveAliasTopic(effective)?.let { return it }
        builtInHelpTopics.firstOrNull { it.name.contains(effective, ignoreCase = true) }?.let { return it }

        return CmdletHelpTopic(
            name = effective,
            moduleName = "PowerShell",
            synopsis = "No offline reference entry matched '$effective'. Browse or search another Verb-Noun cmdlet above.",
            syntax = listOf("$effective [[-Parameter] <Object>]"),
            examples = listOf("Get-Help $effective -Full"),
            sourceBadge = "Fallback"
        )
    }

    fun allOfflineHelpTopics(): List<CmdletHelpTopic> = builtInHelpTopics

    private fun resolveAliasTopic(alias: String): CmdletHelpTopic? {
        val target = when (alias.lowercase()) {
            "gci", "ls", "dir" -> "Get-ChildItem"
            "?", "where" -> "Where-Object"
            "%", "foreach" -> "ForEach-Object"
            "select" -> "Select-Object"
            "sort" -> "Sort-Object"
            "irm" -> "Invoke-RestMethod"
            "iwr", "curl" -> "Invoke-WebRequest"
            "gc", "cat" -> "Get-Content"
            "sc" -> "Set-Content"
            "gps", "ps" -> "Get-Process"
            "sls" -> "Select-String"
            else -> return null
        }
        return builtInHelpTopics.firstOrNull { it.name.equals(target, ignoreCase = true) }
    }

    // MARK: - Pester Unit Test Discovery & Runner

    fun discoverPesterTests(scriptContent: String): List<PesterTestCaseSpec> {
        val normalized = LineEnding.normalizeToLf(scriptContent)
        val out = ArrayList<PesterTestCaseSpec>()
        scanPesterScope(
            text = normalized,
            baseLineOffset = 0,
            currentDescribe = "Pester Suite",
            currentContext = null,
            specs = out
        )
        return out
    }

    fun runPesterTests(code: String): PesterSuiteResult {
        val startNs = System.nanoTime()
        val discovered = discoverPesterTests(code)
        if (discovered.isEmpty()) {
            return PesterSuiteResult(
                engineDescription = "Pester Discovery",
                passedCount = 0,
                failedCount = 0,
                totalDurationMs = 0L,
                results = emptyList(),
                rawOutput = "No Pester Describe / It blocks were found in the current script."
            )
        }

        val preamble = extractNonPesterPreamble(code)
        val results = ArrayList<PesterTestCaseResult>()
        val rawLines = ArrayList<String>()

        for (spec in discovered) {
            val caseStart = System.nanoTime()
            val (passed, failMsg) = evaluateItBlock(preamble, spec.bodyCode)
            val durationMs = ((System.nanoTime() - caseStart) / 1_000_000L).coerceAtLeast(1L)
            results.add(
                PesterTestCaseResult(
                    id = spec.id,
                    describeBlock = spec.describeBlock,
                    contextBlock = spec.contextBlock,
                    itName = spec.itName,
                    lineNumber = spec.lineNumber,
                    passed = passed,
                    durationMs = durationMs,
                    failureMessage = failMsg
                )
            )
            val tag = if (passed) "[PASS]" else "[FAIL]"
            val scope = listOfNotNull(spec.describeBlock, spec.contextBlock).joinToString(" > ")
            rawLines.add("$tag $scope :: ${spec.itName}")
            if (!failMsg.isNullOrBlank()) {
                rawLines.add("       $failMsg")
            }
        }

        val passedCount = results.count { it.passed }
        val failedCount = results.size - passedCount
        val totalMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

        return PesterSuiteResult(
            engineDescription = "GravityEdit Pester Harness",
            passedCount = passedCount,
            failedCount = failedCount,
            totalDurationMs = totalMs,
            results = results,
            rawOutput = rawLines.joinToString("\n")
        )
    }

    private fun scanPesterScope(
        text: String,
        baseLineOffset: Int,
        currentDescribe: String,
        currentContext: String?,
        specs: MutableList<PesterTestCaseSpec>
    ) {
        var searchFrom = 0
        while (searchFrom < text.length) {
            val match = PESTER_SCOPE_REGEX.find(text, searchFrom) ?: break
            val keyword = match.groups[1]?.value?.lowercase() ?: break
            val title = match.groups[3]?.value ?: ""
            val openBrace = match.range.last
            val closeBrace = findMatchingDelimiter(text, openBrace, '{', '}')
            if (closeBrace == null) {
                searchFrom = match.range.last + 1
                continue
            }
            val prefixLines = text.substring(0, match.range.first).count { it == '\n' }
            val oneBasedLine = baseLineOffset + prefixLines + 1
            val innerBody = text.substring(openBrace + 1, closeBrace)

            when (keyword) {
                "describe" -> scanPesterScope(innerBody, oneBasedLine - 1, title, null, specs)
                "context" -> scanPesterScope(innerBody, oneBasedLine - 1, currentDescribe, title, specs)
                "it" -> specs.add(
                    PesterTestCaseSpec(
                        id = "it_${oneBasedLine}_${specs.size}",
                        describeBlock = currentDescribe,
                        contextBlock = currentContext,
                        itName = title,
                        lineNumber = oneBasedLine,
                        bodyCode = innerBody
                    )
                )
            }
            searchFrom = closeBrace + 1
        }
    }

    private fun extractNonPesterPreamble(code: String): String {
        val normalized = LineEnding.normalizeToLf(code)
        val out = StringBuilder()
        var cursor = 0
        while (cursor < normalized.length) {
            val match = PESTER_DESCRIBE_REGEX.find(normalized, cursor)
            if (match == null) {
                out.append(normalized.substring(cursor))
                break
            }
            out.append(normalized.substring(cursor, match.range.first))
            val openBrace = match.range.last
            val closeBrace = findMatchingDelimiter(normalized, openBrace, '{', '}')
            cursor = if (closeBrace != null) closeBrace + 1 else match.range.last + 1
        }
        return out.toString()
    }

    private fun evaluateItBlock(preamble: String, itBody: String): Pair<Boolean, String?> {
        val setupLines = ArrayList<String>()
        for (rawLine in itBody.split('\n')) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val shouldMatch = SHOULD_PIPE_REGEX.find(trimmed)
            if (shouldMatch != null) {
                val rawExpr = trimmed.substring(0, shouldMatch.range.first).trim()
                val actualExpr = unwrapOuterParentheses(rawExpr)
                val clause = trimmed.substring(shouldMatch.range.last + 1).trim()
                val script = buildString {
                    append(preamble)
                    append('\n')
                    append(setupLines.joinToString("\n"))
                    append("\n\$__pesterActual = ")
                    append(actualExpr)
                    append("\nWrite-Output \$__pesterActual")
                }
                val exec = PowerShellRunnerEngine.executeScript(script)
                val actualValue = exec.lines
                    .filter { it.stream == ScriptOutputStreamKind.STDOUT }
                    .joinToString("\n") { it.text }
                    .trim()
                val check = evaluateShouldClause(actualValue, exec.exitCode, clause)
                if (!check.first) return check
            } else {
                setupLines.add(trimmed)
            }
        }
        if (setupLines.isNotEmpty()) {
            val runRes = PowerShellRunnerEngine.executeScript(preamble + "\n" + setupLines.joinToString("\n"))
            if (runRes.exitCode != 0) {
                val err = runRes.lines.firstOrNull { it.stream == ScriptOutputStreamKind.STDERR }?.text ?: "Statement failed."
                return false to err
            }
        }
        return true to null
    }

    private fun evaluateShouldClause(actual: String, exitCode: Int, clause: String): Pair<Boolean, String?> {
        val tokens = tokenizeShouldClause(clause).toMutableList()
        val notIdx = tokens.indexOfFirst { it.equals("-Not", ignoreCase = true) }
        val isNegated = notIdx >= 0
        if (notIdx >= 0) tokens.removeAt(notIdx)

        val op = tokens.firstOrNull()?.lowercase() ?: return true to null
        val expected = if (tokens.size >= 2) unquoteSimpleLiteral(tokens[1]) else ""

        val (rawMatch, failMsg) = when (op) {
            "-be" -> {
                val aNum = actual.toDoubleOrNull()
                val eNum = expected.toDoubleOrNull()
                val m = if (aNum != null && eNum != null) abs(aNum - eNum) < 1e-9 else actual.equals(expected, ignoreCase = true)
                m to "Expected '$expected'${if (isNegated) " (negated)" else ""}, but got '$actual'."
            }
            "-beexactly" -> (actual == expected) to "Expected exact '$expected', but got '$actual'."
            "-betrue" -> (actual.equals("true", ignoreCase = true) || actual == "1" || actual == "\$true") to "Expected True, got '$actual'."
            "-befalse" -> (actual.equals("false", ignoreCase = true) || actual == "0" || actual == "\$false") to "Expected False, got '$actual'."
            "-benullorempty" -> (actual.isEmpty() || actual.equals("\$null", ignoreCase = true)) to "Expected null or empty, got '$actual'."
            "-begreaterthan" -> ((actual.toDoubleOrNull() ?: 0.0) > (expected.toDoubleOrNull() ?: 0.0)) to "Expected > $expected, got '$actual'."
            "-belessthan" -> ((actual.toDoubleOrNull() ?: 0.0) < (expected.toDoubleOrNull() ?: 0.0)) to "Expected < $expected, got '$actual'."
            "-match" -> runCatching { Regex(expected, RegexOption.IGNORE_CASE).containsMatchIn(actual) }.getOrDefault(false) to "Expected '$actual' to match '$expected'."
            "-contain" -> actual.lines().any { it.trim().equals(expected, ignoreCase = true) } to "Expected collection to contain '$expected'."
            "-throw" -> (exitCode != 0) to "Expected expression to throw."
            else -> true to ""
        }
        val finalPass = if (isNegated) !rawMatch else rawMatch
        return finalPass to (if (finalPass) null else failMsg)
    }

    private fun tokenizeShouldClause(text: String): List<String> {
        val tokens = ArrayList<String>()
        val cur = StringBuilder()
        var inSingle = false
        var inDouble = false
        for (ch in text) {
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                cur.append(ch)
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                cur.append(ch)
            } else if (ch.isWhitespace() && !inSingle && !inDouble) {
                if (cur.isNotEmpty()) {
                    tokens.add(cur.toString())
                    cur.clear()
                }
            } else {
                cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) tokens.add(cur.toString())
        return tokens
    }

    // MARK: - Lexical Helpers

    private fun findMatchingDelimiter(text: String, openIndex: Int, openChar: Char, closeChar: Char): Int? {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var inLineComment = false
        for (i in openIndex until text.length) {
            val ch = text[i]
            if (inLineComment) {
                if (ch == '\n') inLineComment = false
            } else if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble
            } else if (ch == '#' && !inSingle && !inDouble) {
                inLineComment = true
            } else if (!inSingle && !inDouble) {
                if (ch == openChar) {
                    depth++
                } else if (ch == closeChar) {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun extractCommentSynopsis(text: String): String? {
        val raw = SYNOPSIS_BLOCK_REGEX.find(text)?.groups?.get(1)?.value?.trim()
        return raw?.takeIf { it.isNotEmpty() }
    }

    private fun extractPrecedingCommentSynopsis(text: String, beforeIndex: Int): String? {
        val prefix = text.substring(0, beforeIndex)
        val start = prefix.lastIndexOf("<#")
        val end = prefix.lastIndexOf("#>")
        if (start < 0 || end <= start) return null
        if (prefix.substring(end + 2).isNotBlank()) return null
        return extractCommentSynopsis(prefix.substring(start, end + 2))
    }

    private fun unquoteSimpleLiteral(raw: String): String {
        if (raw.length >= 2 && ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\"")))) {
            return raw.substring(1, raw.length - 1)
        }
        return raw
    }

    private fun unwrapOuterParentheses(raw: String): String {
        val t = raw.trim()
        if (t.length >= 2 && t.first() == '(' && findMatchingDelimiter(t, 0, '(', ')') == t.lastIndex) {
            return t.substring(1, t.lastIndex).trim()
        }
        return t
    }

    private val builtInHelpTopics: List<CmdletHelpTopic> = listOf(
        CmdletHelpTopic(
            name = "Get-ChildItem",
            moduleName = "Microsoft.PowerShell.Management",
            synopsis = "Gets the items and child items in one or more specified filesystem or provider locations.",
            syntax = listOf("Get-ChildItem [[-Path] <String[]>] [[-Filter] <String>] [-Recurse] [-Force]"),
            parameters = listOf(
                CmdletHelpParameter("Path", "String[]", false, "Specifies a path to one or more locations."),
                CmdletHelpParameter("Recurse", "Switch", false, "Recursively enumerates child directories.")
            ),
            examples = listOf("Get-ChildItem -Path . -Filter '*.ps1' -Recurse")
        ),
        CmdletHelpTopic(
            name = "Where-Object",
            moduleName = "Microsoft.PowerShell.Core",
            synopsis = "Selects objects from a collection based on property values or a filter script block.",
            syntax = listOf("Where-Object [-FilterScript] <ScriptBlock>"),
            examples = listOf("Get-Process | Where-Object { \$_.CPU -gt 10 }")
        ),
        CmdletHelpTopic(
            name = "ForEach-Object",
            moduleName = "Microsoft.PowerShell.Core",
            synopsis = "Performs an operation against each item in a collection of input objects.",
            syntax = listOf("ForEach-Object [-Process] <ScriptBlock[]> [-Parallel <ScriptBlock>]"),
            examples = listOf("1..5 | ForEach-Object { \$_ * 10 }")
        ),
        CmdletHelpTopic(
            name = "Select-Object",
            moduleName = "Microsoft.PowerShell.Utility",
            synopsis = "Selects specified properties of an object or takes First/Last/Unique subsets.",
            syntax = listOf("Select-Object [[-Property] <Object[]>] [-First <Int32>] [-Unique]"),
            examples = listOf("Get-Process | Select-Object Name, Id, CPU -First 10")
        ),
        CmdletHelpTopic(
            name = "Invoke-RestMethod",
            moduleName = "Microsoft.PowerShell.Utility",
            synopsis = "Sends an HTTP/HTTPS request to a REST endpoint and deserializes JSON/XML payloads.",
            syntax = listOf("Invoke-RestMethod [-Uri] <Uri> [-Method <String>] [-Headers <IDictionary>]"),
            examples = listOf("Invoke-RestMethod -Uri 'https://api.github.com/repos/PowerShell/PowerShell/releases/latest'")
        ),
        CmdletHelpTopic(
            name = "ConvertTo-Json",
            moduleName = "Microsoft.PowerShell.Utility",
            synopsis = "Converts any PowerShell object or hashtable into a JSON-formatted string.",
            syntax = listOf("ConvertTo-Json [-InputObject] <Object> [-Depth <Int32>] [-Compress]"),
            examples = listOf("@{ Env = 'Prod'; Nodes = 4 } | ConvertTo-Json -Depth 5")
        ),
        CmdletHelpTopic(
            name = "ConvertFrom-Json",
            moduleName = "Microsoft.PowerShell.Utility",
            synopsis = "Converts a JSON-formatted string to a custom object or hashtable.",
            syntax = listOf("ConvertFrom-Json [-InputObject] <String> [-AsHashtable]"),
            examples = listOf("Get-Content './config.json' -Raw | ConvertFrom-Json -AsHashtable")
        ),
        CmdletHelpTopic(
            name = "Import-Csv",
            moduleName = "Microsoft.PowerShell.Utility",
            synopsis = "Creates table-like custom objects from the rows of a CSV file.",
            syntax = listOf("Import-Csv [[-Path] <String[]>] [[-Delimiter] <Char>]"),
            examples = listOf("Import-Csv './users.csv' | Where-Object Role -EQ 'Admin'")
        ),
        CmdletHelpTopic(
            name = "Get-Content",
            moduleName = "Microsoft.PowerShell.Management",
            synopsis = "Reads file content line-by-line or as a single raw string.",
            syntax = listOf("Get-Content [-Path] <String[]> [-Raw] [-Tail <Int32>]"),
            examples = listOf("Get-Content -Path './app.log' -Tail 50")
        ),
        CmdletHelpTopic(
            name = "Invoke-Pester",
            moduleName = "Pester",
            synopsis = "Runs Pester unit and integration test suites (*.Tests.ps1).",
            syntax = listOf("Invoke-Pester [[-Path] <String[]>] [-Output {Detailed | Normal}]"),
            examples = listOf("Invoke-Pester -Path './Deploy.Tests.ps1' -Output Detailed")
        ),
        CmdletHelpTopic(
            name = "Should",
            moduleName = "Pester",
            synopsis = "Pester assertion command used inside `It` blocks to verify expected values.",
            syntax = listOf("<Actual> | Should [-Not] [-Be <Object>] [-BeTrue] [-BeFalse] [-Match <String>] [-Throw]"),
            examples = listOf("(2 + 2) | Should -Be 4")
        )
    )
}
