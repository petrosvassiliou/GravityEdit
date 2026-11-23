package com.gravityedit.app.domain.engine

import androidx.compose.runtime.Immutable
import com.gravityedit.app.domain.model.LineEnding
import java.time.Instant
import java.util.UUID

enum class ScriptOutputStreamKind(val badge: String) {
    STDOUT("OUT"),
    WARNING("WARN"),
    VERBOSE("VERB"),
    STDERR("ERR"),
    INFO("INFO")
}

@Immutable
data class ScriptExecutionLocation(
    val lineNumber: Int,
    val columnNumber: Int = 1
)

@Immutable
data class ScriptOutputLine(
    val id: String = UUID.randomUUID().toString(),
    val stream: ScriptOutputStreamKind,
    val text: String,
    val location: ScriptExecutionLocation? = null
)

@Immutable
data class ScriptExecutionResult(
    val engineDescription: String,
    val exitCode: Int,
    val durationMs: Long,
    val lines: List<ScriptOutputLine>,
    val rawCombinedOutput: String
) {
    val succeeded: Boolean get() = exitCode == 0
}

object PowerShellRunnerEngine {

    private val PWSH_LOCATION_REGEX = Regex(
        pattern = """(?:At\s+.+?:(\d+)\s+char:(\d+)|:(\d+)\s+char:(\d+))""",
        option = RegexOption.IGNORE_CASE
    )
    private val COLON_LINE_COL_REGEX = Regex("""(?:^|\s)[^\s:]+:(\d+):(\d+):""")

    fun parseExecutionLocation(text: String, lineNumberBaseOffset: Int = 0): ScriptExecutionLocation? {
        PWSH_LOCATION_REGEX.find(text)?.let { m ->
            val rawLine = (m.groups[1]?.value ?: m.groups[3]?.value)?.toIntOrNull()
            val rawCol = (m.groups[2]?.value ?: m.groups[4]?.value)?.toIntOrNull() ?: 1
            if (rawLine != null) {
                return ScriptExecutionLocation(
                    lineNumber = (rawLine + lineNumberBaseOffset).coerceAtLeast(1),
                    columnNumber = rawCol.coerceAtLeast(1)
                )
            }
        }
        COLON_LINE_COL_REGEX.find(text)?.let { m ->
            val rawLine = m.groups[1]?.value?.toIntOrNull()
            val rawCol = m.groups[2]?.value?.toIntOrNull() ?: 1
            if (rawLine != null) {
                return ScriptExecutionLocation(
                    lineNumber = (rawLine + lineNumberBaseOffset).coerceAtLeast(1),
                    columnNumber = rawCol.coerceAtLeast(1)
                )
            }
        }
        return null
    }

    /**
     * Formats an execution result inline directly below the selected expression or current line,
     * mirroring BBEdit Shell Worksheets (`Ctrl + Enter`).
     */
    fun formatWorksheetInsertion(
        documentText: String,
        selectionStart: Int,
        selectionEnd: Int,
        result: ScriptExecutionResult
    ): Pair<String, Int> {
        val safeStart = minOf(selectionStart, selectionEnd).coerceIn(0, documentText.length)
        val safeEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, documentText.length)
        val anchor = if (safeEnd > safeStart) (safeEnd - 1).coerceAtLeast(0) else safeStart

        var lineEndIdx = documentText.indexOf('\n', anchor)
        if (lineEndIdx < 0) {
            lineEndIdx = documentText.length
        } else {
            lineEndIdx += 1
        }

        val needsLeadingLf = lineEndIdx > 0 && documentText[lineEndIdx - 1] != '\n'
        val body = result.lines.joinToString("\n") { line ->
            when (line.stream) {
                ScriptOutputStreamKind.STDERR -> "# [ERR] ${line.text}"
                ScriptOutputStreamKind.WARNING -> "# [WARN] ${line.text}"
                ScriptOutputStreamKind.VERBOSE -> "# [VERBOSE] ${line.text}"
                ScriptOutputStreamKind.INFO -> "# ${line.text}"
                ScriptOutputStreamKind.STDOUT -> line.text
            }
        }
        val block = buildString {
            if (needsLeadingLf) append('\n')
            append("# ── Worksheet Output (${result.durationMs} ms • exit ${result.exitCode}) ──\n")
            append(body)
            append("\n# ──────────────────────────────────────────────\n")
        }

        val updated = buildString(documentText.length + block.length) {
            append(documentText, 0, lineEndIdx)
            append(block)
            append(documentText, lineEndIdx, documentText.length)
        }
        return updated to (lineEndIdx + block.length)
    }

    /**
     * Play Protect–clean deterministic PowerShell Worksheet & Pipeline Evaluator.
     * Evaluates variables, hashtables (`@{ ... }`, `[ordered]@{ ... }`, `[pscustomobject]@{ ... }`),
     * arrays (`@(...)`), `ConvertTo-Json`, `Measure-Object`, `Select-Object -First`,
     * `Write-Host` / `Write-Output` / `Write-Warning` / `Write-Verbose` / `Write-Error`,
     * `Get-Date`, `$PSVersionTable`, arithmetic, and expandable string interpolation.
     */
    fun executeScript(
        code: String,
        lineNumberBaseOffset: Int = 0
    ): ScriptExecutionResult {
        val startNs = System.nanoTime()
        val normalized = LineEnding.normalizeToLf(code)
        if (normalized.isBlank()) {
            return ScriptExecutionResult(
                engineDescription = "PowerShell Worksheet Engine",
                exitCode = 0,
                durationMs = 0L,
                lines = listOf(ScriptOutputLine(stream = ScriptOutputStreamKind.INFO, text = "(No script statements to execute)")),
                rawCombinedOutput = ""
            )
        }

        val variables = LinkedHashMap<String, Any?>()
        variables["PSScriptRoot"] = "/workspace"
        variables["PWD"] = "/workspace"
        variables["HOME"] = "/home/gravity"
        variables["true"] = true
        variables["false"] = false

        val outputLines = ArrayList<ScriptOutputLine>()
        var exitCode = 0
        val rawLines = normalized.split('\n')
        var idx = 0
        var inBlockComment = false

        while (idx < rawLines.size) {
            val lineNum = idx + 1 + lineNumberBaseOffset
            val raw = rawLines[idx]
            val trimmed = raw.trim()
            idx++

            if (inBlockComment) {
                if ("#>" in trimmed) inBlockComment = false
                continue
            }
            if (trimmed.startsWith("<#")) {
                if ("#>" !in trimmed) inBlockComment = true
                continue
            }
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val lower = trimmed.lowercase()
            if (lower.startsWith("function ") || lower.startsWith("filter ") || lower.startsWith("class ")) {
                var depth = raw.count { it == '{' } - raw.count { it == '}' }
                while (idx < rawLines.size && depth > 0) {
                    val next = rawLines[idx]
                    depth += next.count { it == '{' } - next.count { it == '}' }
                    idx++
                }
                continue
            }

            var statement = trimmed
            var openDelims = statement.count { it == '{' || it == '(' } - statement.count { it == '}' || it == ')' }
            while (openDelims > 0 && idx < rawLines.size) {
                val nextLine = rawLines[idx].trim()
                statement += "\n$nextLine"
                openDelims += nextLine.count { it == '{' || it == '(' } - nextLine.count { it == '}' || it == ')' }
                idx++
            }

            val assignment = matchVariableAssignment(statement)
            if (assignment != null) {
                variables[assignment.first] = evaluateExpression(assignment.second, variables)
                continue
            }

            val stmtLower = statement.lowercase()
            if (stmtLower.startsWith("write-error ") || stmtLower.startsWith("throw ")) {
                val arg = statement.substringAfter(' ').trim().replace(Regex("^-Message\\s+", RegexOption.IGNORE_CASE), "")
                val msg = stringifyValue(evaluateExpression(arg, variables), variables)
                val loc = ScriptExecutionLocation(lineNumber = lineNum, columnNumber = 1)
                outputLines.add(
                    ScriptOutputLine(
                        stream = ScriptOutputStreamKind.STDERR,
                        text = "Write-Error: $msg (At <script>:$lineNum char:1)",
                        location = loc
                    )
                )
                exitCode = 1
                continue
            }

            if (stmtLower.startsWith("write-warning ")) {
                val arg = statement.substring("write-warning ".length).trim()
                val msg = stringifyValue(evaluateExpression(arg, variables), variables)
                outputLines.add(
                    ScriptOutputLine(
                        stream = ScriptOutputStreamKind.WARNING,
                        text = "WARNING: $msg",
                        location = ScriptExecutionLocation(lineNumber = lineNum, columnNumber = 1)
                    )
                )
                continue
            }

            if (stmtLower.startsWith("write-verbose ")) {
                val arg = statement.substring("write-verbose ".length).trim()
                val msg = stringifyValue(evaluateExpression(arg, variables), variables)
                outputLines.add(ScriptOutputLine(stream = ScriptOutputStreamKind.VERBOSE, text = "VERBOSE: $msg"))
                continue
            }

            if (stmtLower.startsWith("write-host ") || stmtLower.startsWith("write-output ")) {
                val pLen = if (stmtLower.startsWith("write-host ")) "write-host ".length else "write-output ".length
                val arg = statement.substring(pLen).trim()
                val msg = stringifyValue(evaluateExpression(arg, variables), variables)
                for (l in msg.split('\n')) {
                    outputLines.add(ScriptOutputLine(stream = ScriptOutputStreamKind.STDOUT, text = l))
                }
                continue
            }

            val evaluated = evaluatePipeline(statement, variables)
            val rendered = stringifyValue(evaluated, variables)
            if (rendered.isNotEmpty()) {
                for (l in rendered.split('\n')) {
                    outputLines.add(ScriptOutputLine(stream = ScriptOutputStreamKind.STDOUT, text = l))
                }
            }
        }

        if (outputLines.isEmpty()) {
            outputLines.add(
                ScriptOutputLine(
                    stream = ScriptOutputStreamKind.INFO,
                    text = "Evaluated ${rawLines.size} line(s) with 0 errors."
                )
            )
        }

        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        val combined = outputLines.joinToString("\n") { it.text }
        return ScriptExecutionResult(
            engineDescription = "PowerShell Worksheet Engine",
            exitCode = exitCode,
            durationMs = elapsedMs,
            lines = outputLines,
            rawCombinedOutput = combined
        )
    }

    private fun matchVariableAssignment(stmt: String): Pair<String, String>? {
        if (!stmt.startsWith("$")) return null
        val eqIdx = stmt.indexOf('=')
        if (eqIdx <= 1) return null
        if (eqIdx + 1 < stmt.length && stmt[eqIdx + 1] == '=') return null
        val lhs = stmt.substring(1, eqIdx).trim()
            .replace(Regex("^(script|global):", RegexOption.IGNORE_CASE), "")
            .trim()
        if (lhs.isEmpty() || !lhs.all { it.isLetterOrDigit() || it == '_' }) return null
        val rhs = stmt.substring(eqIdx + 1).trim()
        return lhs to rhs
    }

    private fun evaluatePipeline(stmt: String, variables: Map<String, Any?>): Any? {
        val stages = splitPipelineStages(stmt)
        if (stages.isEmpty()) return ""
        var current = evaluateExpression(stages[0], variables)

        for (stage in stages.drop(1)) {
            val s = stage.trim()
            val lower = s.lowercase()
            when {
                lower.startsWith("convertto-json") -> {
                    current = renderJsonValue(current, indent = 0)
                }
                lower.startsWith("measure-object") -> {
                    val count = (current as? List<*>)?.size ?: if (current == null) 0 else 1
                    current = linkedMapOf("Count" to count)
                }
                lower.startsWith("select-object") -> {
                    if ("-first " in lower && current is List<*>) {
                        val n = s.substringAfterLast(' ').toIntOrNull() ?: current.size
                        current = current.take(n.coerceAtLeast(0))
                    }
                }
                lower.startsWith("foreach-object") -> {
                    val block = s.substringAfter('{', "").substringBeforeLast('}', "").trim()
                    if (block.isNotEmpty()) {
                        val items = (current as? List<*>) ?: listOf(current)
                        current = items.map { item ->
                            val localVars = LinkedHashMap(variables)
                            localVars["_"] = item
                            localVars["PSItem"] = item
                            evaluateExpression(block, localVars)
                        }
                    }
                }
            }
        }
        return current
    }

    private fun splitPipelineStages(text: String): List<String> {
        val stages = ArrayList<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var depth = 0
        for (ch in text) {
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                sb.append(ch)
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                sb.append(ch)
            } else if (!inSingle && !inDouble) {
                when (ch) {
                    '{', '(' -> { depth++; sb.append(ch) }
                    '}', ')' -> { depth = (depth - 1).coerceAtLeast(0); sb.append(ch) }
                    '|' -> if (depth == 0) {
                        stages.add(sb.toString().trim())
                        sb.setLength(0)
                    } else {
                        sb.append(ch)
                    }
                    else -> sb.append(ch)
                }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotBlank()) stages.add(sb.toString().trim())
        return stages
    }

    private fun evaluateExpression(expr: String, variables: Map<String, Any?>): Any? {
        val t = expr.trim()
        if (t.isEmpty()) return ""
        val lower = t.lowercase()

        if (t.startsWith("'") && t.endsWith("'") && t.length >= 2) {
            return t.substring(1, t.length - 1).replace("''", "'")
        }
        if (t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) {
            return interpolateString(t.substring(1, t.length - 1), variables)
        }
        t.toIntOrNull()?.let { return it }
        t.toDoubleOrNull()?.let { return it }

        for (op in listOf("+", "-", "*", "/")) {
            val token = " $op "
            val pos = t.indexOf(token)
            if (pos > 0) {
                val lVal = toDouble(evaluateExpression(t.substring(0, pos), variables))
                val rVal = toDouble(evaluateExpression(t.substring(pos + token.length), variables))
                if (lVal != null && rVal != null) {
                    val res = when (op) {
                        "+" -> lVal + rVal
                        "-" -> lVal - rVal
                        "*" -> lVal * rVal
                        else -> if (rVal != 0.0) lVal / rVal else 0.0
                    }
                    return if (res == res.toLong().toDouble()) res.toLong().toInt() else res
                }
            }
        }

        if (lower == "get-date" || lower.startsWith("(get-date)")) {
            return Instant.now().toString()
        }
        if (lower == "\$psversiontable") {
            return linkedMapOf(
                "PSEdition" to "GravityEdit Worksheet",
                "PSVersion" to "7.4-compatible",
                "Platform" to "Android / Googlebook OS"
            )
        }

        val braceIdx = t.indexOf("@{")
        if (braceIdx >= 0 && t.endsWith("}")) {
            val inner = t.substring(braceIdx + 2, t.length - 1)
            val map = LinkedHashMap<String, Any?>()
            for (entry in inner.split(';', '\n')) {
                val e = entry.trim()
                if (e.isEmpty() || e.startsWith("#")) continue
                val eq = e.indexOf('=')
                if (eq <= 0) continue
                val k = e.substring(0, eq).trim().trim('\'', '"')
                val vRaw = e.substring(eq + 1).trim()
                map[k] = evaluateExpression(vRaw, variables)
            }
            return map
        }

        if (t.startsWith("@(") && t.endsWith(")")) {
            val inner = t.substring(2, t.length - 1)
            return inner.split(',', ';', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { evaluateExpression(it, variables) }
        }

        if (t.startsWith("$")) {
            val varName = t.substring(1)
            if ("." in varName) {
                val root = varName.substringBefore('.')
                val prop = varName.substringAfter('.')
                val obj = variables[root]
                if (obj is Map<*, *>) {
                    return obj.entries.firstOrNull { it.key.toString().equals(prop, ignoreCase = true) }?.value ?: ""
                }
            }
            if (variables.containsKey(varName)) return variables[varName]
        }

        return interpolateString(t, variables)
    }

    private fun interpolateString(raw: String, variables: Map<String, Any?>): String {
        var result = raw
        val subExprRegex = Regex("""\$\(([^)]+)\)""")
        result = subExprRegex.replace(result) { m ->
            val inner = m.groupValues[1].trim()
            if (inner.startsWith("$") && "." in inner) {
                stringifyValue(evaluateExpression(inner, variables), variables)
            } else {
                stringifyValue(evaluateExpression(inner, variables), variables)
            }
        }
        for ((k, v) in variables.entries.sortedByDescending { it.key.length }) {
            result = result.replace("\$$k", stringifyValue(v, variables))
        }
        return result
    }

    private fun stringifyValue(value: Any?, variables: Map<String, Any?>): String {
        return when (value) {
            null -> ""
            is String -> value
            is Boolean -> if (value) "True" else "False"
            is List<*> -> value.joinToString("\n") { stringifyValue(it, variables) }
            is Map<*, *> -> {
                val maxKey = value.keys.maxOfOrNull { it.toString().length } ?: 4
                value.entries.joinToString("\n") { (k, v) ->
                    val kStr = k.toString()
                    val pad = " ".repeat((maxKey - kStr.length).coerceAtLeast(0))
                    "$kStr$pad : ${stringifyValue(v, variables)}"
                }
            }
            else -> value.toString()
        }
    }

    private fun renderJsonValue(value: Any?, indent: Int): String {
        val pad = "  ".repeat(indent)
        val innerPad = "  ".repeat(indent + 1)
        return when (value) {
            null -> "null"
            is Boolean, is Int, is Long, is Double -> value.toString()
            is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is List<*> -> {
                if (value.isEmpty()) "[]"
                else "[\n" + value.joinToString(",\n") { "$innerPad${renderJsonValue(it, indent + 1)}" } + "\n$pad]"
            }
            is Map<*, *> -> {
                if (value.isEmpty()) "{}"
                else "{\n" + value.entries.joinToString(",\n") { (k, v) ->
                    "$innerPad\"${k}\": ${renderJsonValue(v, indent + 1)}"
                } + "\n$pad}"
            }
            else -> "\"$value\""
        }
    }

    private fun toDouble(v: Any?): Double? = when (v) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Double -> v
        is String -> v.toDoubleOrNull()
        else -> null
    }
}
