package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.CaseTransformMode
import com.gravityedit.app.domain.model.DuplicateLineMode
import com.gravityedit.app.domain.model.FormatTarget
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.LineNumberAction
import com.gravityedit.app.domain.model.MatchingLinesAction
import com.gravityedit.app.domain.model.PrefixSuffixAction
import com.gravityedit.app.domain.model.QuoteTransformMode
import com.gravityedit.app.domain.model.SortDirection
import com.gravityedit.app.domain.model.TabConversionMode
import com.gravityedit.app.domain.model.TextFactoryResult
import com.gravityedit.app.domain.model.TextFactoryStep
import com.gravityedit.app.domain.model.TextOperationType
import com.gravityedit.app.domain.model.WrapTransformMode
import com.gravityedit.app.domain.model.ZapGremlinsAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

typealias TextFactoryEngine = TextTransformEngine

object TextTransformEngine {

    private val NUMERIC_EXTRACT_REGEX = Regex("""[+-]?\d+(\.\d+)?""")
    private val STRIP_LINE_NUMBER_REGEX = Regex("""^\s*\d+\s*(?:[:.\-|)\]]\s*|\s+)""")
    private val SENTENCE_BOUNDARY_REGEX = Regex("""(^|[.!?]\s+)([a-zà-öø-ÿα-ω])""")
    private val WORD_BOUNDARY_REGEX = Regex("""\b([a-zA-ZÀ-ÖØ-öø-ÿΑ-Ωα-ω])([a-zA-ZÀ-ÖØ-öø-ÿΑ-Ωα-ω']*)\b""")

    /**
     * Executes a full sequence of [TextFactoryStep] transformations on [inputText] on [Dispatchers.Default].
     */
    suspend fun runPipeline(
        inputText: String,
        steps: List<TextFactoryStep>
    ): TextFactoryResult = withContext(Dispatchers.Default) {
        executePipelineSync(inputText, steps)
    }

    /**
     * Synchronous pipeline execution for immediate single-step or test invocations.
     */
    fun executePipelineSync(
        inputText: String,
        steps: List<TextFactoryStep>
    ): TextFactoryResult {
        var currentText = LineEnding.normalizeToLf(inputText)
        val extractedDocs = mutableListOf<Pair<String, String>>()
        var targetEnding: LineEnding? = null
        var executedCount = 0

        for ((index, step) in steps.withIndex()) {
            if (!step.enabled) continue
            executedCount++

            when (step.operationType) {
                TextOperationType.ZAP_GREMLINS -> {
                    currentText = zapGremlins(
                        text = currentText,
                        action = step.zapAction,
                        zapNonAscii = step.zapNonAscii,
                        zapControlChars = step.zapControlChars,
                        zapNullBytes = step.zapNullBytes,
                        replacementChar = step.zapReplacementChar.ifEmpty { "?" }
                    )
                }

                TextOperationType.PROCESS_DUPLICATE_LINES -> {
                    val (updated, extracted) = processDuplicateLines(
                        text = currentText,
                        mode = step.duplicateMode,
                        caseSensitive = step.duplicateCaseSensitive,
                        ignoreLeadingWhitespace = step.duplicateIgnoreLeadingWhitespace
                    )
                    currentText = updated
                    if (!extracted.isNullOrEmpty()) {
                        extractedDocs.add("Duplicates_Step${index + 1}.txt" to extracted)
                    }
                }

                TextOperationType.SORT_LINES -> {
                    currentText = sortLines(
                        text = currentText,
                        direction = step.sortDirection,
                        caseSensitive = step.sortCaseSensitive,
                        numeric = step.sortNumeric,
                        regexPattern = step.sortRegexPattern,
                        regexGroupIndex = step.sortRegexGroupIndex
                    )
                }

                TextOperationType.PREFIX_SUFFIX_LINES -> {
                    currentText = prefixSuffixLines(
                        text = currentText,
                        action = step.prefixSuffixAction,
                        prefix = step.linePrefix,
                        suffix = step.lineSuffix,
                        skipBlankLines = step.skipBlankLines
                    )
                }

                TextOperationType.CHANGE_CASE -> {
                    currentText = changeCase(
                        text = currentText,
                        mode = step.caseMode
                    )
                }

                TextOperationType.STRIP_TRAILING_WHITESPACE -> {
                    currentText = stripTrailingWhitespace(
                        text = currentText,
                        preserveBlankLines = step.stripWhitespacePreserveBlankLines
                    )
                }

                TextOperationType.ENTAB_DETAB -> {
                    currentText = entabDetab(
                        text = currentText,
                        mode = step.tabConversionMode,
                        tabWidth = step.tabWidth.coerceIn(1, 16)
                    )
                }

                TextOperationType.HARD_WRAP_UNWRAP -> {
                    currentText = hardWrapUnwrap(
                        text = currentText,
                        mode = step.wrapMode,
                        columnWidth = step.wrapColumnWidth.coerceIn(20, 500),
                        preserveParagraphs = step.wrapPreserveParagraphs
                    )
                }

                TextOperationType.ADD_REMOVE_LINE_NUMBERS -> {
                    currentText = addRemoveLineNumbers(
                        text = currentText,
                        action = step.lineNumberAction,
                        startNumber = step.lineNumberStart,
                        increment = step.lineNumberIncrement.coerceAtLeast(1),
                        separator = step.lineNumberSeparator,
                        zeroPadWidth = step.lineNumberZeroPadWidth.coerceIn(0, 12)
                    )
                }

                TextOperationType.EDUCATE_STRAIGHTEN_QUOTES -> {
                    currentText = educateStraightenQuotes(
                        text = currentText,
                        mode = step.quoteMode
                    )
                }

                TextOperationType.CANONIZE_REPLACE_TABLE -> {
                    currentText = canonizeReplaceTable(
                        text = currentText,
                        tableRaw = step.canonizeTableRaw,
                        caseSensitive = step.canonizeCaseSensitive,
                        wholeWords = step.canonizeWholeWords
                    )
                }

                TextOperationType.PROCESS_LINES_CONTAINING -> {
                    val (updated, extracted) = processLinesContaining(
                        text = currentText,
                        action = step.matchingLinesAction,
                        pattern = step.matchingPattern,
                        useRegex = step.matchingUseRegex,
                        caseSensitive = step.matchingCaseSensitive
                    )
                    currentText = updated
                    if (!extracted.isNullOrEmpty()) {
                        extractedDocs.add("MatchedLines_Step${index + 1}.txt" to extracted)
                    }
                }

                TextOperationType.CONVERT_LINE_ENDINGS -> {
                    targetEnding = step.targetLineEnding
                    currentText = LineEnding.normalizeToLf(currentText)
                }

                TextOperationType.FORMAT_PRETTIFY -> {
                    currentText = formatPrettify(
                        text = currentText,
                        target = step.formatTarget,
                        indentSpaces = step.formatIndentSpaces.coerceIn(2, 8)
                    )
                }
            }
        }

        return TextFactoryResult(
            transformedText = currentText,
            extractedDocuments = extractedDocs,
            targetLineEnding = targetEnding,
            stepsExecuted = executedCount,
            summary = "Executed $executedCount transformation step(s)"
        )
    }

    // =========================================================================
    // 1. ZAP GREMLINS
    // =========================================================================
    fun zapGremlins(
        text: String,
        action: ZapGremlinsAction,
        zapNonAscii: Boolean,
        zapControlChars: Boolean,
        zapNullBytes: Boolean,
        replacementChar: String = "?"
    ): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            val isNull = code == 0
            val isControl = (code in 1..31 && ch != '\n' && ch != '\r' && ch != '\t') || code == 127
            val isNonAscii = code > 127

            val shouldZap = (zapNullBytes && isNull) ||
                (zapControlChars && isControl) ||
                (zapNonAscii && isNonAscii)

            if (!shouldZap) {
                sb.append(ch)
            } else {
                when (action) {
                    ZapGremlinsAction.DELETE -> Unit
                    ZapGremlinsAction.REPLACE_WITH_HEX -> {
                        if (code <= 0xFF) {
                            sb.append(String.format(Locale.US, "\\x%02X", code))
                        } else {
                            sb.append(String.format(Locale.US, "\\u%04X", code))
                        }
                    }
                    ZapGremlinsAction.REPLACE_WITH_CHAR -> {
                        sb.append(replacementChar)
                    }
                }
            }
        }
        return sb.toString()
    }

    // =========================================================================
    // 2. PROCESS DUPLICATE LINES
    // =========================================================================
    fun processDuplicateLines(
        text: String,
        mode: DuplicateLineMode,
        caseSensitive: Boolean,
        ignoreLeadingWhitespace: Boolean
    ): Pair<String, String?> {
        val lines = text.split('\n')
        fun normalizeKey(line: String): String {
            val wsNormalized = if (ignoreLeadingWhitespace) line.trimStart() else line
            return if (caseSensitive) wsNormalized else wsNormalized.lowercase(Locale.ROOT)
        }

        return when (mode) {
            DuplicateLineMode.LEAVE_ONE -> {
                val seen = HashSet<String>(lines.size)
                val kept = ArrayList<String>(lines.size)
                for (line in lines) {
                    val key = normalizeKey(line)
                    if (seen.add(key)) {
                        kept.add(line)
                    }
                }
                kept.joinToString("\n") to null
            }

            DuplicateLineMode.KEEP_UNIQUE_ONLY -> {
                val counts = LinkedHashMap<String, Int>(lines.size)
                for (line in lines) {
                    val key = normalizeKey(line)
                    counts[key] = (counts[key] ?: 0) + 1
                }
                val uniqueOnly = lines.filter { counts[normalizeKey(it)] == 1 }
                uniqueOnly.joinToString("\n") to null
            }

            DuplicateLineMode.EXTRACT_DUPLICATES -> {
                val seen = HashSet<String>(lines.size)
                val deduplicated = ArrayList<String>(lines.size)
                val duplicates = ArrayList<String>()
                for (line in lines) {
                    val key = normalizeKey(line)
                    if (seen.add(key)) {
                        deduplicated.add(line)
                    } else {
                        duplicates.add(line)
                    }
                }
                deduplicated.joinToString("\n") to duplicates.joinToString("\n")
            }
        }
    }

    // =========================================================================
    // 3. SORT LINES
    // =========================================================================
    fun sortLines(
        text: String,
        direction: SortDirection,
        caseSensitive: Boolean,
        numeric: Boolean,
        regexPattern: String = "",
        regexGroupIndex: Int = 1
    ): String {
        val lines = text.split('\n')
        if (lines.size <= 1) return text

        if (direction == SortDirection.REVERSE_ONLY) {
            return lines.asReversed().joinToString("\n")
        }

        val compiledRegex = if (regexPattern.isNotBlank()) {
            runCatching {
                val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(regexPattern, opts)
            }.getOrNull()
        } else {
            null
        }

        fun extractSortKey(line: String): String {
            if (compiledRegex != null) {
                val match = compiledRegex.find(line)
                if (match != null) {
                    val groupIdx = regexGroupIndex.coerceIn(0, match.groupValues.lastIndex)
                    return match.groupValues[groupIdx]
                }
            }
            return line
        }

        val comparator = Comparator<String> { a, b ->
            val keyA = extractSortKey(a)
            val keyB = extractSortKey(b)

            val cmp = if (numeric) {
                val numA = NUMERIC_EXTRACT_REGEX.find(keyA)?.value?.toDoubleOrNull()
                val numB = NUMERIC_EXTRACT_REGEX.find(keyB)?.value?.toDoubleOrNull()
                when {
                    numA != null && numB != null -> {
                        val dCmp = numA.compareTo(numB)
                        if (dCmp != 0) dCmp else compareStrings(keyA, keyB, caseSensitive)
                    }
                    numA != null -> -1
                    numB != null -> 1
                    else -> compareStrings(keyA, keyB, caseSensitive)
                }
            } else {
                compareStrings(keyA, keyB, caseSensitive)
            }

            if (direction == SortDirection.DESCENDING) -cmp else cmp
        }

        return lines.sortedWith(comparator).joinToString("\n")
    }

    private fun compareStrings(a: String, b: String, caseSensitive: Boolean): Int {
        return if (caseSensitive) {
            a.compareTo(b)
        } else {
            val ins = a.compareTo(b, ignoreCase = true)
            if (ins != 0) ins else a.compareTo(b)
        }
    }

    // =========================================================================
    // 4. PREFIX / SUFFIX LINES
    // =========================================================================
    fun prefixSuffixLines(
        text: String,
        action: PrefixSuffixAction,
        prefix: String,
        suffix: String,
        skipBlankLines: Boolean
    ): String {
        val lines = text.split('\n')
        return lines.joinToString("\n") { line ->
            if (skipBlankLines && line.isBlank()) {
                line
            } else {
                when (action) {
                    PrefixSuffixAction.INSERT -> "$prefix$line$suffix"
                    PrefixSuffixAction.REMOVE -> {
                        var current = line
                        if (prefix.isNotEmpty() && current.startsWith(prefix)) {
                            current = current.removePrefix(prefix)
                        }
                        if (suffix.isNotEmpty() && current.endsWith(suffix)) {
                            current = current.removeSuffix(suffix)
                        }
                        current
                    }
                }
            }
        }
    }

    // =========================================================================
    // 5. CHANGE CASE
    // =========================================================================
    fun changeCase(text: String, mode: CaseTransformMode): String {
        return when (mode) {
            CaseTransformMode.UPPERCASE -> text.uppercase(Locale.getDefault())
            CaseTransformMode.LOWERCASE -> text.lowercase(Locale.getDefault())
            CaseTransformMode.TITLE_CASE -> {
                WORD_BOUNDARY_REGEX.replace(text) { match ->
                    val first = match.groupValues[1].uppercase(Locale.getDefault())
                    val rest = match.groupValues[2].lowercase(Locale.getDefault())
                    first + rest
                }
            }
            CaseTransformMode.CAPITALIZE_SENTENCES -> {
                val lowered = text.lowercase(Locale.getDefault())
                SENTENCE_BOUNDARY_REGEX.replace(lowered) { match ->
                    match.groupValues[1] + match.groupValues[2].uppercase(Locale.getDefault())
                }
            }
            CaseTransformMode.INVERT_CASE -> {
                buildString(text.length) {
                    for (ch in text) {
                        when {
                            ch.isUpperCase() -> append(ch.lowercaseChar())
                            ch.isLowerCase() -> append(ch.uppercaseChar())
                            else -> append(ch)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 6. STRIP TRAILING WHITESPACE
    // =========================================================================
    fun stripTrailingWhitespace(text: String, preserveBlankLines: Boolean = true): String {
        val lines = text.split('\n')
        val stripped = lines.map { line ->
            line.trimEnd(' ', '\t')
        }
        return if (preserveBlankLines) {
            stripped.joinToString("\n")
        } else {
            stripped.filter { it.isNotEmpty() }.joinToString("\n")
        }
    }

    // =========================================================================
    // 7. ENTAB & DETAB
    // =========================================================================
    fun entabDetab(
        text: String,
        mode: TabConversionMode,
        tabWidth: Int
    ): String {
        val safeWidth = tabWidth.coerceIn(1, 16)
        val lines = text.split('\n')

        return lines.joinToString("\n") { line ->
            when (mode) {
                TabConversionMode.DETAB_TABS_TO_SPACES -> {
                    val sb = StringBuilder(line.length + 16)
                    var col = 0
                    for (ch in line) {
                        if (ch == '\t') {
                            val spacesToAdd = safeWidth - (col % safeWidth)
                            repeat(spacesToAdd) { sb.append(' ') }
                            col += spacesToAdd
                        } else {
                            sb.append(ch)
                            col++
                        }
                    }
                    sb.toString()
                }

                TabConversionMode.ENTAB_SPACES_TO_TABS -> {
                    val detabbed = buildString {
                        var col = 0
                        for (ch in line) {
                            if (ch == '\t') {
                                val spacesToAdd = safeWidth - (col % safeWidth)
                                repeat(spacesToAdd) { append(' ') }
                                col += spacesToAdd
                            } else {
                                append(ch)
                                col++
                            }
                        }
                    }
                    val sb = StringBuilder(detabbed.length)
                    var spaceCount = 0
                    var col = 0
                    for (ch in detabbed) {
                        if (ch == ' ') {
                            spaceCount++
                            col++
                            if (col % safeWidth == 0) {
                                if (spaceCount >= 2) {
                                    sb.append('\t')
                                } else {
                                    sb.append(' ')
                                }
                                spaceCount = 0
                            }
                        } else {
                            repeat(spaceCount) { sb.append(' ') }
                            spaceCount = 0
                            sb.append(ch)
                            col++
                        }
                    }
                    repeat(spaceCount) { sb.append(' ') }
                    sb.toString()
                }
            }
        }
    }

    // =========================================================================
    // 8. HARD WRAP & UNWRAP LINES
    // =========================================================================
    fun hardWrapUnwrap(
        text: String,
        mode: WrapTransformMode,
        columnWidth: Int,
        preserveParagraphs: Boolean
    ): String {
        val safeCol = columnWidth.coerceIn(20, 500)
        return when (mode) {
            WrapTransformMode.UNWRAP_REMOVE_LINE_BREAKS -> {
                if (!preserveParagraphs) {
                    text.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                } else {
                    val paragraphs = mutableListOf<String>()
                    val currentPara = mutableListOf<String>()
                    for (line in text.split('\n')) {
                        if (line.isBlank()) {
                            if (currentPara.isNotEmpty()) {
                                paragraphs.add(currentPara.joinToString(" "))
                                currentPara.clear()
                            }
                            paragraphs.add("")
                        } else {
                            currentPara.add(line.trim())
                        }
                    }
                    if (currentPara.isNotEmpty()) {
                        paragraphs.add(currentPara.joinToString(" "))
                    }
                    paragraphs.joinToString("\n")
                }
            }

            WrapTransformMode.HARD_WRAP -> {
                val outLines = mutableListOf<String>()
                for (rawLine in text.split('\n')) {
                    if (rawLine.length <= safeCol) {
                        outLines.add(rawLine)
                        continue
                    }
                    val leadingIndent = rawLine.takeWhile { it == ' ' || it == '\t' }
                    val words = rawLine.trim().split(Regex("""\s+"""))
                    var currentLine = StringBuilder(leadingIndent)
                    for (word in words) {
                        if (currentLine.length == leadingIndent.length) {
                            currentLine.append(word)
                        } else if (currentLine.length + 1 + word.length <= safeCol) {
                            currentLine.append(' ').append(word)
                        } else {
                            outLines.add(currentLine.toString())
                            currentLine = StringBuilder(leadingIndent).append(word)
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        outLines.add(currentLine.toString())
                    }
                }
                outLines.joinToString("\n")
            }
        }
    }

    // =========================================================================
    // 9. ADD / REMOVE LINE NUMBERS
    // =========================================================================
    fun addRemoveLineNumbers(
        text: String,
        action: LineNumberAction,
        startNumber: Int,
        increment: Int,
        separator: String,
        zeroPadWidth: Int
    ): String {
        val lines = text.split('\n')
        return when (action) {
            LineNumberAction.ADD_NUMBERS -> {
                var currentNum = startNumber
                lines.joinToString("\n") { line ->
                    val numStr = if (zeroPadWidth > 0) {
                        currentNum.toString().padStart(zeroPadWidth, '0')
                    } else {
                        currentNum.toString()
                    }
                    currentNum += increment
                    "$numStr$separator$line"
                }
            }
            LineNumberAction.REMOVE_NUMBERS -> {
                lines.joinToString("\n") { line ->
                    STRIP_LINE_NUMBER_REGEX.replaceFirst(line, "")
                }
            }
        }
    }

    // =========================================================================
    // 10. EDUCATE / STRAIGHTEN QUOTES
    // =========================================================================
    fun educateStraightenQuotes(text: String, mode: QuoteTransformMode): String {
        return when (mode) {
            QuoteTransformMode.STRAIGHTEN_QUOTES -> {
                buildString(text.length) {
                    for (ch in text) {
                        when (ch) {
                            '“', '”', '„', '«', '»' -> append('"')
                            '‘', '’', '‚' -> append('\'')
                            else -> append(ch)
                        }
                    }
                }
            }
            QuoteTransformMode.EDUCATE_CURLY_QUOTES -> {
                buildString(text.length) {
                    for (i in text.indices) {
                        val ch = text[i]
                        val prev = if (i > 0) text[i - 1] else ' '
                        val isOpening = prev.isWhitespace() || prev in "([{<=-"
                        when (ch) {
                            '"' -> append(if (isOpening) '“' else '”')
                            '\'' -> append(if (isOpening) '‘' else '’')
                            else -> append(ch)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 11. CANONIZE / REPLACE USING TABLE
    // =========================================================================
    fun canonizeReplaceTable(
        text: String,
        tableRaw: String,
        caseSensitive: Boolean,
        wholeWords: Boolean
    ): String {
        val pairs = tableRaw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .map { line ->
                val eqIndex = line.indexOf('=')
                val key = line.substring(0, eqIndex).trim()
                val value = line.substring(eqIndex + 1).trim()
                key to value
            }
            .filter { it.first.isNotEmpty() }
            .toList()

        if (pairs.isEmpty()) return text

        var result = text
        for ((searchKey, replacementVal) in pairs) {
            val escapedKey = Regex.escape(searchKey)
            val patternStr = if (wholeWords && searchKey.first().isLetterOrDigit() && searchKey.last().isLetterOrDigit()) {
                "\\b$escapedKey\\b"
            } else {
                escapedKey
            }
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val regex = Regex(patternStr, options)
            result = regex.replace(result, Regex.escapeReplacement(replacementVal))
        }
        return result
    }

    // =========================================================================
    // 12. PROCESS LINES CONTAINING...
    // =========================================================================
    fun processLinesContaining(
        text: String,
        action: MatchingLinesAction,
        pattern: String,
        useRegex: Boolean,
        caseSensitive: Boolean
    ): Pair<String, String?> {
        if (pattern.isEmpty()) return text to null
        val lines = text.split('\n')

        val predicate: (String) -> Boolean = if (useRegex) {
            val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val regex = runCatching { Regex(pattern, opts) }.getOrNull()
            if (regex != null) {
                { line -> regex.containsMatchIn(line) }
            } else {
                { line -> line.contains(pattern, ignoreCase = !caseSensitive) }
            }
        } else {
            { line -> line.contains(pattern, ignoreCase = !caseSensitive) }
        }

        return when (action) {
            MatchingLinesAction.DELETE_MATCHING -> {
                lines.filterNot(predicate).joinToString("\n") to null
            }
            MatchingLinesAction.DELETE_NON_MATCHING -> {
                lines.filter(predicate).joinToString("\n") to null
            }
            MatchingLinesAction.EXTRACT_MATCHING -> {
                val matched = lines.filter(predicate)
                text to matched.joinToString("\n")
            }
        }
    }

    // =========================================================================
    // 14. FORMAT / PRETTIFY POWERSHELL & JSON
    // =========================================================================
    fun formatPrettify(
        text: String,
        target: FormatTarget,
        indentSpaces: Int = 4
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text

        val resolvedTarget = when (target) {
            FormatTarget.AUTO -> {
                if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]"))
                ) {
                    FormatTarget.JSON
                } else {
                    FormatTarget.POWERSHELL
                }
            }
            else -> target
        }

        return when (resolvedTarget) {
            FormatTarget.JSON -> formatJson(text, indentSpaces)
            FormatTarget.POWERSHELL, FormatTarget.AUTO -> formatPowerShell(text, indentSpaces)
        }
    }

    fun formatJson(jsonText: String, indentSpaces: Int = 4): String {
        val indentUnit = " ".repeat(indentSpaces.coerceIn(1, 8))
        val sb = StringBuilder(jsonText.length + 128)
        var indentLevel = 0
        var inString = false
        var i = 0
        val len = jsonText.length

        fun appendNewLineAndIndent() {
            sb.append('\n')
            repeat(indentLevel.coerceAtLeast(0)) {
                sb.append(indentUnit)
            }
        }

        while (i < len) {
            val ch = jsonText[i]
            if (inString) {
                sb.append(ch)
                if (ch == '\\' && i + 1 < len) {
                    sb.append(jsonText[i + 1])
                    i += 2
                    continue
                }
                if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }

            when (ch) {
                '"' -> {
                    inString = true
                    sb.append(ch)
                }
                '{', '[' -> {
                    sb.append(ch)
                    // Check if empty object/array
                    var nextNonWs = i + 1
                    while (nextNonWs < len && jsonText[nextNonWs].isWhitespace()) nextNonWs++
                    val closeChar = if (ch == '{') '}' else ']'
                    if (nextNonWs < len && jsonText[nextNonWs] == closeChar) {
                        sb.append(closeChar)
                        i = nextNonWs + 1
                        continue
                    }
                    indentLevel++
                    appendNewLineAndIndent()
                }
                '}', ']' -> {
                    indentLevel = (indentLevel - 1).coerceAtLeast(0)
                    appendNewLineAndIndent()
                    sb.append(ch)
                }
                ',' -> {
                    sb.append(',')
                    appendNewLineAndIndent()
                }
                ':' -> {
                    sb.append(": ")
                }
                else -> {
                    if (!ch.isWhitespace()) {
                        sb.append(ch)
                    }
                }
            }
            i++
        }

        return sb.toString()
    }

    fun formatPowerShell(scriptText: String, indentSpaces: Int = 4): String {
        val indentUnit = " ".repeat(indentSpaces.coerceIn(1, 8))
        val rawLines = scriptText.split('\n')
        val formattedLines = ArrayList<String>(rawLines.size)
        var braceDepth = 0
        var inHereString = false
        var hereStringCloser = ""
        var inBlockComment = false

        for (rawLine in rawLines) {
            if (inHereString) {
                formattedLines.add(rawLine.trimEnd('\r'))
                if (rawLine.trimStart().startsWith(hereStringCloser)) {
                    inHereString = false
                }
                continue
            }

            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                formattedLines.add("")
                continue
            }

            if (inBlockComment) {
                val indent = indentUnit.repeat(braceDepth.coerceAtLeast(0))
                formattedLines.add("$indent$trimmed")
                if (trimmed.contains("#>")) {
                    inBlockComment = false
                }
                continue
            }

            if (trimmed.startsWith("<#") && !trimmed.contains("#>")) {
                val indent = indentUnit.repeat(braceDepth.coerceAtLeast(0))
                formattedLines.add("$indent$trimmed")
                inBlockComment = true
                continue
            }

            val leadingClosers = countLeadingClosingBraces(trimmed)
            val effectiveIndent = (braceDepth - leadingClosers).coerceAtLeast(0)
            formattedLines.add(indentUnit.repeat(effectiveIndent) + trimmed)

            val netDelta = countNetBracesOutsideStrings(trimmed)
            braceDepth = (braceDepth + netDelta).coerceAtLeast(0)

            if (trimmed.endsWith("@\"")) {
                inHereString = true
                hereStringCloser = "\"@"
            } else if (trimmed.endsWith("@'")) {
                inHereString = true
                hereStringCloser = "'@"
            }
        }

        return formattedLines.joinToString("\n")
    }

    private fun countLeadingClosingBraces(trimmedLine: String): Int {
        var count = 0
        for (ch in trimmedLine) {
            if (ch == '}') count++
            else if (!ch.isWhitespace()) break
        }
        return count
    }

    private fun countNetBracesOutsideStrings(line: String): Int {
        var delta = 0
        var i = 0
        val len = line.length
        while (i < len) {
            val ch = line[i]
            if (ch == '#') break
            if (ch == '\'' || ch == '"') {
                val quote = ch
                i++
                while (i < len && line[i] != quote) {
                    if (line[i] == '`' && i + 1 < len) i++
                    i++
                }
                i++
                continue
            }
            if (ch == '{') delta++
            else if (ch == '}') delta--
            i++
        }
        return delta
    }
}
