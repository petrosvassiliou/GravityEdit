package com.gravityedit.app.domain.model

import androidx.compose.runtime.Immutable

enum class DiffChangeType {
    EQUAL,
    INSERT,
    DELETE,
    MODIFY
}

@Immutable
data class CharDiffSpan(
    val start: Int,
    val end: Int,
    val isChanged: Boolean
)

@Immutable
data class DiffRow(
    val rowIndex: Int,
    val leftLineNumber: Int?,
    val rightLineNumber: Int?,
    val leftText: String?,
    val rightText: String?,
    val changeType: DiffChangeType,
    val leftCharHighlights: List<CharDiffSpan> = emptyList(),
    val rightCharHighlights: List<CharDiffSpan> = emptyList()
) {
    val canMergeRightToLeft: Boolean
        get() = changeType != DiffChangeType.EQUAL

    val canMergeLeftToRight: Boolean
        get() = changeType != DiffChangeType.EQUAL
}

@Immutable
data class DiffResult(
    val leftDocumentId: String?,
    val rightDocumentId: String?,
    val leftTitle: String,
    val rightTitle: String,
    val rows: List<DiffRow>,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int
) {
    val isIdentical: Boolean
        get() = addedCount == 0 && removedCount == 0 && modifiedCount == 0
}
