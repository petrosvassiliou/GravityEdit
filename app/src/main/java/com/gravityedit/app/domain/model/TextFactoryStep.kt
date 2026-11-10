package com.gravityedit.app.domain.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.gravityedit.app.R
import java.util.UUID

enum class TextOperationType(
    @StringRes val titleRes: Int,
    val defaultSummary: String
) {
    ZAP_GREMLINS(
        R.string.tf_zap_gremlins,
        "Remove or replace non-ASCII, control characters, and null bytes"
    ),
    PROCESS_DUPLICATE_LINES(
        R.string.tf_process_duplicates,
        "Deduplicate lines, keep only unique lines, or extract duplicates"
    ),
    SORT_LINES(
        R.string.tf_sort_lines,
        "Sort lines ascending/descending, numerically, or by regex capture group"
    ),
    PREFIX_SUFFIX_LINES(
        R.string.tf_prefix_suffix,
        "Insert or strip custom prefixes and suffixes on every line"
    ),
    CHANGE_CASE(
        R.string.tf_change_case,
        "Convert text to UPPERCASE, lowercase, Title Case, Sentence case, or iNVERT cASE"
    ),
    STRIP_TRAILING_WHITESPACE(
        R.string.tf_strip_trailing_ws,
        "Remove trailing spaces and tabs from the end of each line"
    ),
    ENTAB_DETAB(
        R.string.tf_entab_detab,
        "Convert tabs to spaces (Detab) or spaces to tabs (Entab)"
    ),
    HARD_WRAP_UNWRAP(
        R.string.tf_hard_wrap_unwrap,
        "Hard wrap lines at a target column width or unwrap paragraph line breaks"
    ),
    ADD_REMOVE_LINE_NUMBERS(
        R.string.tf_line_numbers,
        "Prepend formatted line numbers or strip existing line numbers"
    ),
    EDUCATE_STRAIGHTEN_QUOTES(
        R.string.tf_educate_quotes,
        "Convert between typographic curly quotes (“ ” ‘ ’) and straight quotes (\" ')"
    ),
    CANONIZE_REPLACE_TABLE(
        R.string.tf_canonize_table,
        "Batch replace key=value pairs across the document"
    ),
    PROCESS_LINES_CONTAINING(
        R.string.tf_process_containing,
        "Delete matching lines, delete non-matching lines, or extract matches"
    ),
    CONVERT_LINE_ENDINGS(
        R.string.tf_convert_line_endings,
        "Normalize document line breaks to LF, CRLF, or CR"
    ),
    FORMAT_PRETTIFY(
        R.string.tf_format_prettify,
        "Format and indent PowerShell scripts or JSON documents"
    )
}

enum class ZapGremlinsAction(val label: String) {
    DELETE("Delete Gremlins"),
    REPLACE_WITH_HEX("Replace with \\xNN Hex Code"),
    REPLACE_WITH_CHAR("Replace with Placeholder Character")
}

enum class DuplicateLineMode(val label: String) {
    LEAVE_ONE("Delete Duplicates (Leave First)"),
    KEEP_UNIQUE_ONLY("Keep Strictly Unique Lines Only"),
    EXTRACT_DUPLICATES("Extract Duplicate Lines")
}

enum class SortDirection(val label: String) {
    ASCENDING("Ascending (A → Z)"),
    DESCENDING("Descending (Z → A)"),
    REVERSE_ONLY("Reverse Line Order")
}

enum class PrefixSuffixAction(val label: String) {
    INSERT("Insert Prefix / Suffix"),
    REMOVE("Remove Matching Prefix / Suffix")
}

enum class CaseTransformMode(val label: String) {
    UPPERCASE("UPPERCASE"),
    LOWERCASE("lowercase"),
    TITLE_CASE("Title Case"),
    CAPITALIZE_SENTENCES("Capitalize sentences"),
    INVERT_CASE("iNVERT cASE")
}

enum class TabConversionMode(val label: String) {
    DETAB_TABS_TO_SPACES("Detab (Tabs → Spaces)"),
    ENTAB_SPACES_TO_TABS("Entab (Spaces → Tabs)")
}

enum class WrapTransformMode(val label: String) {
    HARD_WRAP("Hard Wrap at Column"),
    UNWRAP_REMOVE_LINE_BREAKS("Unwrap (Remove Line Breaks)")
}

enum class LineNumberAction(val label: String) {
    ADD_NUMBERS("Add Line Numbers"),
    REMOVE_NUMBERS("Remove Existing Line Numbers")
}

enum class QuoteTransformMode(val label: String) {
    STRAIGHTEN_QUOTES("Straighten Quotes (“ ” ‘ ’ → \" ')"),
    EDUCATE_CURLY_QUOTES("Educate Quotes (\" ' → “ ” ‘ ’)")
}

enum class MatchingLinesAction(val label: String) {
    DELETE_MATCHING("Delete Lines Containing Pattern"),
    DELETE_NON_MATCHING("Delete Lines NOT Containing Pattern"),
    EXTRACT_MATCHING("Extract Matching Lines to New Document")
}

enum class FormatTarget(val label: String) {
    AUTO("Auto-Detect (PowerShell / JSON)"),
    POWERSHELL("PowerShell Script (.ps1 / .psm1)"),
    JSON("JSON Document (.json)")
}

@Immutable
data class TextFactoryStep(
    val id: String = UUID.randomUUID().toString(),
    val operationType: TextOperationType,
    val enabled: Boolean = true,

    // 1. Zap Gremlins
    val zapAction: ZapGremlinsAction = ZapGremlinsAction.DELETE,
    val zapNonAscii: Boolean = false,
    val zapControlChars: Boolean = true,
    val zapNullBytes: Boolean = true,
    val zapReplacementChar: String = "?",

    // 2. Process Duplicate Lines
    val duplicateMode: DuplicateLineMode = DuplicateLineMode.LEAVE_ONE,
    val duplicateCaseSensitive: Boolean = true,
    val duplicateIgnoreLeadingWhitespace: Boolean = false,

    // 3. Sort Lines
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val sortCaseSensitive: Boolean = false,
    val sortNumeric: Boolean = false,
    val sortRegexPattern: String = "",
    val sortRegexGroupIndex: Int = 1,

    // 4. Prefix / Suffix Lines
    val prefixSuffixAction: PrefixSuffixAction = PrefixSuffixAction.INSERT,
    val linePrefix: String = "",
    val lineSuffix: String = "",
    val skipBlankLines: Boolean = true,

    // 5. Change Case
    val caseMode: CaseTransformMode = CaseTransformMode.UPPERCASE,

    // 6. Strip Trailing Whitespace
    val stripWhitespacePreserveBlankLines: Boolean = true,

    // 7. Entab & Detab
    val tabConversionMode: TabConversionMode = TabConversionMode.DETAB_TABS_TO_SPACES,
    val tabWidth: Int = 4,

    // 8. Hard Wrap & Unwrap
    val wrapMode: WrapTransformMode = WrapTransformMode.HARD_WRAP,
    val wrapColumnWidth: Int = 80,
    val wrapPreserveParagraphs: Boolean = true,

    // 9. Add / Remove Line Numbers
    val lineNumberAction: LineNumberAction = LineNumberAction.ADD_NUMBERS,
    val lineNumberStart: Int = 1,
    val lineNumberIncrement: Int = 1,
    val lineNumberSeparator: String = ": ",
    val lineNumberZeroPadWidth: Int = 4,

    // 10. Educate / Straighten Quotes
    val quoteMode: QuoteTransformMode = QuoteTransformMode.STRAIGHTEN_QUOTES,

    // 11. Canonize / Replace Using Table (key=value lines)
    val canonizeTableRaw: String = "# Example replacement pairs (one per line):\ngci=Get-ChildItem\n%=ForEach-Object\n?=Where-Object",
    val canonizeCaseSensitive: Boolean = false,
    val canonizeWholeWords: Boolean = true,

    // 12. Process Lines Containing
    val matchingLinesAction: MatchingLinesAction = MatchingLinesAction.DELETE_MATCHING,
    val matchingPattern: String = "",
    val matchingUseRegex: Boolean = true,
    val matchingCaseSensitive: Boolean = false,

    // 13. Convert Line Endings
    val targetLineEnding: LineEnding = LineEnding.LF,

    // 14. Format / Prettify PowerShell & JSON
    val formatTarget: FormatTarget = FormatTarget.AUTO,
    val formatIndentSpaces: Int = 4
) {
    fun configurationSummary(): String {
        return when (operationType) {
            TextOperationType.ZAP_GREMLINS -> "${zapAction.label} (Ctrl=${zapControlChars}, Null=${zapNullBytes}, NonASCII=${zapNonAscii})"
            TextOperationType.PROCESS_DUPLICATE_LINES -> "${duplicateMode.label} (CaseSensitive=$duplicateCaseSensitive)"
            TextOperationType.SORT_LINES -> "${sortDirection.label}${if (sortNumeric) ", Numeric" else ""}${if (sortRegexPattern.isNotBlank()) ", Regex='$sortRegexPattern'" else ""}"
            TextOperationType.PREFIX_SUFFIX_LINES -> "${prefixSuffixAction.label}: prefix='$linePrefix', suffix='$lineSuffix'"
            TextOperationType.CHANGE_CASE -> caseMode.label
            TextOperationType.STRIP_TRAILING_WHITESPACE -> "Strip trailing spaces and tabs"
            TextOperationType.ENTAB_DETAB -> "${tabConversionMode.label} ($tabWidth spaces)"
            TextOperationType.HARD_WRAP_UNWRAP -> if (wrapMode == WrapTransformMode.HARD_WRAP) "Hard wrap at col $wrapColumnWidth" else wrapMode.label
            TextOperationType.ADD_REMOVE_LINE_NUMBERS -> "${lineNumberAction.label} (start=$lineNumberStart, pad=$lineNumberZeroPadWidth)"
            TextOperationType.EDUCATE_STRAIGHTEN_QUOTES -> quoteMode.label
            TextOperationType.CANONIZE_REPLACE_TABLE -> {
                val ruleCount = canonizeTableRaw.lines().count { it.isNotBlank() && !it.trim().startsWith("#") && '=' in it }
                "$ruleCount replacement rules"
            }
            TextOperationType.PROCESS_LINES_CONTAINING -> "${matchingLinesAction.label}: '${matchingPattern.ifEmpty { "*" }}'"
            TextOperationType.CONVERT_LINE_ENDINGS -> "Target: ${targetLineEnding.displayName}"
            TextOperationType.FORMAT_PRETTIFY -> "${formatTarget.label} (${formatIndentSpaces} spaces)"
        }
    }
}

@Immutable
data class TextFactoryResult(
    val transformedText: String,
    val extractedDocuments: List<Pair<String, String>> = emptyList(),
    val targetLineEnding: LineEnding? = null,
    val stepsExecuted: Int = 0,
    val summary: String = ""
)
