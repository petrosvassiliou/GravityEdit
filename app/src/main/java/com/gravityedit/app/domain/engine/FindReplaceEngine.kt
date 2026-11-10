package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.CaptureGroupMatch
import com.gravityedit.app.domain.model.MultiFileSearchMatch
import com.gravityedit.app.domain.model.PatternPlaygroundEvaluation
import com.gravityedit.app.domain.model.SearchMatch
import com.gravityedit.app.domain.model.SearchOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class SearchableFileSource(
    val uriString: String,
    val fileName: String,
    val relativePath: String,
    val contentProvider: suspend () -> String?,
    val contentWriter: (suspend (String) -> Boolean)? = null
)

object FindReplaceEngine {

    private val NAMED_GROUP_DEF_REGEX = Regex("""\(\?<([A-Za-z][A-Za-z0-9]*)>""")
    private val BACKSLASH_GROUP_REF_REGEX = Regex("""\\([1-9])""")

    /**
     * Compiles [SearchOptions] into a `java.util.regex.Pattern` supporting PCRE multi-line,
     * lookaheads/lookbehinds, named capture groups, word boundaries, and case sensitivity.
     */
    @Throws(PatternSyntaxException::class)
    fun compilePattern(options: SearchOptions): Pattern {
        val rawQuery = options.query
        if (rawQuery.isEmpty()) {
            throw PatternSyntaxException("Search pattern is empty", "", -1)
        }

        val basePattern = if (options.grepRegex) {
            rawQuery
        } else {
            Pattern.quote(rawQuery)
        }

        val wordWrappedPattern = if (options.matchEntireWords) {
            "\\b(?:$basePattern)\\b"
        } else {
            basePattern
        }

        var flags = Pattern.UNICODE_CHARACTER_CLASS
        if (!options.caseSensitive) {
            flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        }
        if (options.multiLine) {
            flags = flags or Pattern.MULTILINE
        }
        if (options.dotMatchesAll) {
            flags = flags or Pattern.DOTALL
        }

        return Pattern.compile(wordWrappedPattern, flags)
    }

    /**
     * Extracts all named capture group identifiers declared inside a regex pattern (`(?<name>...)`).
     */
    fun extractNamedGroupNames(patternSource: String): List<String> {
        return NAMED_GROUP_DEF_REGEX.findAll(patternSource)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    /**
     * Finds all matches in [text] according to [options]. If [options.searchInSelectionOnly] is true
     * and [selectionRange] is non-empty, restricts matches to [selectionRange].
     */
    fun findAllMatches(
        text: String,
        options: SearchOptions,
        selectionRange: IntRange? = null
    ): List<SearchMatch> {
        if (options.query.isEmpty() || text.isEmpty()) return emptyList()
        val pattern = runCatching { compilePattern(options) }.getOrNull() ?: return emptyList()

        val namedGroups = if (options.grepRegex) {
            extractNamedGroupNames(options.query)
        } else {
            emptyList()
        }

        val lineStartOffsets = computeLineStartOffsets(text)
        val lines = text.split('\n')

        val regionStart = if (options.searchInSelectionOnly && selectionRange != null && selectionRange.first < selectionRange.last) {
            selectionRange.first.coerceIn(0, text.length)
        } else {
            0
        }
        val regionEnd = if (options.searchInSelectionOnly && selectionRange != null && selectionRange.first < selectionRange.last) {
            (selectionRange.last + 1).coerceIn(regionStart, text.length)
        } else {
            text.length
        }

        val matcher = pattern.matcher(text)
        matcher.region(regionStart, regionEnd)

        val results = ArrayList<SearchMatch>()
        var matchIdx = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start == end) {
                if (end >= regionEnd) break
                matcher.region(end + 1, regionEnd)
                continue
            }

            val lineIndex = findLineIndexForOffset(lineStartOffsets, start)
            val lineStartOffset = lineStartOffsets.getOrElse(lineIndex) { 0 }
            val columnNumber = (start - lineStartOffset) + 1
            val linePreview = lines.getOrElse(lineIndex) { "" }.trim().take(160)

            val captureGroups = ArrayList<CaptureGroupMatch>()
            val groupCount = matcher.groupCount()
            for (g in 1..groupCount) {
                val gStart = matcher.start(g)
                val gEnd = matcher.end(g)
                if (gStart >= 0 && gEnd >= gStart) {
                    val gVal = matcher.group(g) ?: ""
                    captureGroups.add(
                        CaptureGroupMatch(
                            groupIndex = g,
                            groupName = null,
                            value = gVal,
                            range = gStart until gEnd
                        )
                    )
                }
            }

            for (groupName in namedGroups) {
                runCatching {
                    val gStart = matcher.start(groupName)
                    val gEnd = matcher.end(groupName)
                    val gVal = matcher.group(groupName)
                    if (gStart >= 0 && gEnd >= gStart && gVal != null) {
                        val existingIdx = captureGroups.indexOfFirst {
                            it.range.first == gStart && it.range.last + 1 == gEnd && it.groupName == null
                        }
                        if (existingIdx >= 0) {
                            captureGroups[existingIdx] = captureGroups[existingIdx].copy(groupName = groupName)
                        } else {
                            captureGroups.add(
                                CaptureGroupMatch(
                                    groupIndex = captureGroups.size + 1,
                                    groupName = groupName,
                                    value = gVal,
                                    range = gStart until gEnd
                                )
                            )
                        }
                    }
                }
            }

            results.add(
                SearchMatch(
                    matchIndex = matchIdx++,
                    range = start until end,
                    matchedText = text.substring(start, end),
                    lineNumber = lineIndex + 1,
                    columnNumber = columnNumber,
                    linePreview = linePreview,
                    captureGroups = captureGroups
                )
            )
        }

        return results
    }

    /**
     * Finds the next match from [cursorOffset], honoring [SearchOptions.wrapAround].
     */
    fun findNext(
        text: String,
        options: SearchOptions,
        cursorOffset: Int,
        selectionRange: IntRange? = null
    ): SearchMatch? {
        val matches = findAllMatches(text, options, selectionRange)
        if (matches.isEmpty()) return null
        val nextMatch = matches.firstOrNull { it.range.first >= cursorOffset }
        return nextMatch ?: if (options.wrapAround) matches.first() else null
    }

    /**
     * Finds the previous match before [cursorOffset], honoring [SearchOptions.wrapAround].
     */
    fun findPrevious(
        text: String,
        options: SearchOptions,
        cursorOffset: Int,
        selectionRange: IntRange? = null
    ): SearchMatch? {
        val matches = findAllMatches(text, options, selectionRange)
        if (matches.isEmpty()) return null
        val prevMatch = matches.lastOrNull { it.range.first < cursorOffset }
        return prevMatch ?: if (options.wrapAround) matches.last() else null
    }

    /**
     * Translates BBEdit/PCRE replacement escape sequences (`\n`, `\t`, `\r`, `\1`..`\9`, `$1`..`$9`, `${name}`)
     * into Java `Matcher.appendReplacement` syntax when `grepRegex` is enabled.
     */
    fun prepareReplacementTemplate(rawReplacement: String, grepRegex: Boolean): String {
        if (!grepRegex) {
            return Matcher.quoteReplacement(rawReplacement)
        }
        val sb = StringBuilder(rawReplacement.length + 8)
        var i = 0
        val len = rawReplacement.length
        while (i < len) {
            val ch = rawReplacement[i]
            if (ch == '\\' && i + 1 < len) {
                when (val next = rawReplacement[i + 1]) {
                    'n' -> {
                        sb.append('\n')
                        i += 2
                        continue
                    }
                    't' -> {
                        sb.append('\t')
                        i += 2
                        continue
                    }
                    'r' -> {
                        sb.append('\r')
                        i += 2
                        continue
                    }
                    in '1'..'9' -> {
                        sb.append('$').append(next)
                        i += 2
                        continue
                    }
                    '\\' -> {
                        sb.append("\\\\")
                        i += 2
                        continue
                    }
                    '$' -> {
                        sb.append("\\$")
                        i += 2
                        continue
                    }
                    else -> {
                        sb.append(ch).append(next)
                        i += 2
                        continue
                    }
                }
            }
            sb.append(ch)
            i++
        }
        return BACKSLASH_GROUP_REF_REGEX.replace(sb.toString(), "\\$$1")
    }

    /**
     * Replaces a single [targetMatch] inside [text] and returns the updated document string and new cursor offset.
     */
    fun replaceSingleMatch(
        text: String,
        targetMatch: SearchMatch,
        options: SearchOptions
    ): Pair<String, Int> {
        val pattern = runCatching { compilePattern(options) }.getOrNull() ?: return text to targetMatch.range.first
        val slice = text.substring(targetMatch.range.first, targetMatch.range.last + 1)
        val matcher = pattern.matcher(slice)
        val template = prepareReplacementTemplate(options.replacement, options.grepRegex)

        val replacedSlice = if (matcher.find()) {
            runCatching {
                val sb = StringBuffer()
                matcher.appendReplacement(sb, template)
                matcher.appendTail(sb)
                sb.toString()
            }.getOrDefault(options.replacement)
        } else {
            options.replacement
        }

        val updatedText = buildString(text.length - slice.length + replacedSlice.length) {
            append(text, 0, targetMatch.range.first)
            append(replacedSlice)
            append(text, targetMatch.range.last + 1, text.length)
        }
        val newCursorOffset = targetMatch.range.first + replacedSlice.length
        return updatedText to newCursorOffset
    }

    /**
     * Replaces all matches in [text] (or within [selectionRange] if `searchInSelectionOnly` is enabled).
     * Returns the updated text and the number of replacements performed.
     */
    fun replaceAll(
        text: String,
        options: SearchOptions,
        selectionRange: IntRange? = null
    ): Pair<String, Int> {
        if (options.query.isEmpty() || text.isEmpty()) return text to 0
        val pattern = runCatching { compilePattern(options) }.getOrNull() ?: return text to 0
        val template = prepareReplacementTemplate(options.replacement, options.grepRegex)

        val regionStart = if (options.searchInSelectionOnly && selectionRange != null && selectionRange.first < selectionRange.last) {
            selectionRange.first.coerceIn(0, text.length)
        } else {
            0
        }
        val regionEnd = if (options.searchInSelectionOnly && selectionRange != null && selectionRange.first < selectionRange.last) {
            (selectionRange.last + 1).coerceIn(regionStart, text.length)
        } else {
            text.length
        }

        val targetSub = text.substring(regionStart, regionEnd)
        val matcher = pattern.matcher(targetSub)
        val sb = StringBuffer(targetSub.length)
        var count = 0

        while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue
            count++
            runCatching {
                matcher.appendReplacement(sb, template)
            }.onFailure {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(options.replacement))
            }
        }
        matcher.appendTail(sb)

        if (count == 0) return text to 0

        val finalString = buildString(text.length) {
            append(text, 0, regionStart)
            append(sb)
            append(text, regionEnd, text.length)
        }
        return finalString to count
    }

    /**
     * BBEdit "Extract" action: extracts all regex matches across [text] into a brand-new document.
     * If [options.replacement] is non-empty in Grep mode, formats each extracted match using the
     * replacement template (allowing capture group extraction like `$1:\t$2`); otherwise extracts
     * the full matched text of each match on its own line.
     */
    fun extractMatchesToDocument(
        text: String,
        options: SearchOptions,
        selectionRange: IntRange? = null
    ): Pair<String, Int> {
        val matches = findAllMatches(text, options, selectionRange)
        if (matches.isEmpty()) return "" to 0

        if (options.replacement.isBlank() || !options.grepRegex) {
            return matches.joinToString("\n") { it.matchedText } to matches.size
        }

        val pattern = runCatching { compilePattern(options) }.getOrNull()
            ?: return matches.joinToString("\n") { it.matchedText } to matches.size
        val template = prepareReplacementTemplate(options.replacement, grepRegex = true)

        val extractedLines = matches.map { match ->
            val matcher = pattern.matcher(match.matchedText)
            if (matcher.find()) {
                runCatching {
                    val sb = StringBuffer()
                    matcher.appendReplacement(sb, template)
                    sb.toString()
                }.getOrDefault(match.matchedText)
            } else {
                match.matchedText
            }
        }
        return extractedLines.joinToString("\n") to matches.size
    }

    /**
     * Evaluates a regex pattern and replacement live for the interactive Pattern Playground modal.
     */
    fun evaluatePatternPlayground(
        sampleText: String,
        options: SearchOptions
    ): PatternPlaygroundEvaluation {
        if (options.query.isEmpty()) {
            return PatternPlaygroundEvaluation(
                isValidRegex = true,
                errorMessage = null,
                matches = emptyList(),
                replacementPreview = sampleText,
                executionTimeMicros = 0L
            )
        }

        val startNanos = System.nanoTime()
        try {
            compilePattern(options)
        } catch (e: PatternSyntaxException) {
            return PatternPlaygroundEvaluation(
                isValidRegex = false,
                errorMessage = e.message ?: "Invalid regular expression syntax",
                matches = emptyList(),
                replacementPreview = sampleText,
                executionTimeMicros = (System.nanoTime() - startNanos) / 1_000L
            )
        }

        val playgroundOptions = options.copy(searchInSelectionOnly = false)
        val matches = findAllMatches(sampleText, playgroundOptions)
        val (replacedPreview, _) = replaceAll(sampleText, playgroundOptions)
        val elapsedMicros = (System.nanoTime() - startNanos) / 1_000L

        return PatternPlaygroundEvaluation(
            isValidRegex = true,
            errorMessage = null,
            matches = matches,
            replacementPreview = replacedPreview,
            executionTimeMicros = elapsedMicros
        )
    }

    /**
     * Checks whether [fileName] matches a semicolon/comma-separated wildcard filter such as `*.ps1;*.json`.
     */
    fun matchesFileFilter(fileName: String, filterSpec: String): Boolean {
        val trimmed = filterSpec.trim()
        if (trimmed.isEmpty() || trimmed == "*" || trimmed == "*.*") return true

        val globs = trimmed.split(';', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (globs.isEmpty()) return true

        for (glob in globs) {
            val regexStr = buildString {
                append('^')
                for (ch in glob) {
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        '.' -> append("\\.")
                        else -> if (ch.isLetterOrDigit() || ch == '_' || ch == '-') {
                            append(ch)
                        } else {
                            append('\\').append(ch)
                        }
                    }
                }
                append('$')
            }
            if (Regex(regexStr, RegexOption.IGNORE_CASE).matches(fileName)) {
                return true
            }
        }
        return false
    }

    /**
     * Multi-File Search across a list of [SearchableFileSource] items matching [options.fileFilter].
     */
    suspend fun searchAcrossFiles(
        files: List<SearchableFileSource>,
        options: SearchOptions
    ): List<MultiFileSearchMatch> = withContext(Dispatchers.Default) {
        if (options.query.isEmpty()) return@withContext emptyList()
        val scopedOptions = options.copy(searchInSelectionOnly = false)
        val results = ArrayList<MultiFileSearchMatch>()

        for (file in files) {
            if (!matchesFileFilter(file.fileName, options.fileFilter)) continue
            val content = file.contentProvider() ?: continue
            val fileMatches = findAllMatches(content, scopedOptions)
            if (fileMatches.isNotEmpty()) {
                results.add(
                    MultiFileSearchMatch(
                        uriString = file.uriString,
                        fileName = file.fileName,
                        relativePath = file.relativePath,
                        matches = fileMatches
                    )
                )
            }
        }
        results
    }

    /**
     * Multi-File Replace across [files] matching [options.fileFilter]. Returns the pair of
     * `(filesModifiedCount, totalReplacementsCount)`.
     */
    suspend fun replaceAcrossFiles(
        files: List<SearchableFileSource>,
        options: SearchOptions
    ): Pair<Int, Int> = withContext(Dispatchers.Default) {
        if (options.query.isEmpty()) return@withContext 0 to 0
        val scopedOptions = options.copy(searchInSelectionOnly = false)
        var modifiedFiles = 0
        var totalReplacements = 0

        for (file in files) {
            val writer = file.contentWriter ?: continue
            if (!matchesFileFilter(file.fileName, options.fileFilter)) continue
            val content = file.contentProvider() ?: continue
            val (updated, count) = replaceAll(content, scopedOptions)
            if (count > 0 && updated != content) {
                val saved = writer(updated)
                if (saved) {
                    modifiedFiles++
                    totalReplacements += count
                }
            }
        }
        modifiedFiles to totalReplacements
    }

    private fun computeLineStartOffsets(text: String): IntArray {
        val starts = ArrayList<Int>()
        starts.add(0)
        for (i in text.indices) {
            if (text[i] == '\n') {
                starts.add(i + 1)
            }
        }
        return starts.toIntArray()
    }

    private fun findLineIndexForOffset(lineStarts: IntArray, offset: Int): Int {
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = lineStarts[mid]
            if (midVal <= offset) {
                if (mid == lineStarts.lastIndex || lineStarts[mid + 1] > offset) {
                    return mid
                }
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return 0
    }
}
