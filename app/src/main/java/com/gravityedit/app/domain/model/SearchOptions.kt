package com.gravityedit.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SearchOptions(
    val query: String = "",
    val replacement: String = "",
    val caseSensitive: Boolean = false,
    val matchEntireWords: Boolean = false,
    val grepRegex: Boolean = true,
    val wrapAround: Boolean = true,
    val searchInSelectionOnly: Boolean = false,
    val multiLine: Boolean = true,
    val dotMatchesAll: Boolean = false,
    val fileFilter: String = "*.ps1;*.psm1;*.psd1;*.json;*.txt;*.md;*.xml"
)

@Immutable
data class CaptureGroupMatch(
    val groupIndex: Int,
    val groupName: String?,
    val value: String,
    val range: IntRange
)

@Immutable
data class SearchMatch(
    val matchIndex: Int,
    val range: IntRange,
    val matchedText: String,
    val lineNumber: Int,
    val columnNumber: Int,
    val linePreview: String,
    val captureGroups: List<CaptureGroupMatch> = emptyList()
)

@Immutable
data class MultiFileSearchMatch(
    val uriString: String,
    val fileName: String,
    val relativePath: String,
    val matches: List<SearchMatch>
) {
    val totalMatches: Int get() = matches.size
}

@Immutable
data class PatternPlaygroundEvaluation(
    val isValidRegex: Boolean,
    val errorMessage: String? = null,
    val matches: List<SearchMatch> = emptyList(),
    val replacementPreview: String = "",
    val executionTimeMicros: Long = 0L
)
