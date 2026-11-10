package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.CharDiffSpan
import com.gravityedit.app.domain.model.DiffChangeType
import com.gravityedit.app.domain.model.DiffResult
import com.gravityedit.app.domain.model.DiffRow
import com.gravityedit.app.domain.model.LineEnding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DiffEngine {

    private enum class EditOpType {
        EQUAL,
        DELETE_LEFT,
        INSERT_RIGHT
    }

    private data class RawEditOp(
        val type: EditOpType,
        val leftIndex: Int = -1,
        val rightIndex: Int = -1,
        val text: String
    )

    /**
     * Computes a side-by-side Myers Diff between [leftText] and [rightText] on [Dispatchers.Default],
     * pairing adjacent deletions and insertions into aligned `MODIFY` rows with character-level diff spans.
     */
    suspend fun computeDiff(
        leftDocumentId: String?,
        rightDocumentId: String?,
        leftTitle: String,
        rightTitle: String,
        leftText: String,
        rightText: String
    ): DiffResult = withContext(Dispatchers.Default) {
        computeDiffSync(
            leftDocumentId = leftDocumentId,
            rightDocumentId = rightDocumentId,
            leftTitle = leftTitle,
            rightTitle = rightTitle,
            leftText = leftText,
            rightText = rightText
        )
    }

    fun computeDiffSync(
        leftDocumentId: String?,
        rightDocumentId: String?,
        leftTitle: String,
        rightTitle: String,
        leftText: String,
        rightText: String
    ): DiffResult {
        val leftLines = LineEnding.normalizeToLf(leftText).split('\n')
        val rightLines = LineEnding.normalizeToLf(rightText).split('\n')

        val rawOps = computeMyersLineOps(leftLines, rightLines)
        val rows = ArrayList<DiffRow>()

        var addedCount = 0
        var removedCount = 0
        var modifiedCount = 0
        var rowIndex = 0
        var i = 0

        while (i < rawOps.size) {
            val op = rawOps[i]
            if (op.type == EditOpType.EQUAL) {
                rows.add(
                    DiffRow(
                        rowIndex = rowIndex++,
                        leftLineNumber = op.leftIndex + 1,
                        rightLineNumber = op.rightIndex + 1,
                        leftText = op.text,
                        rightText = op.text,
                        changeType = DiffChangeType.EQUAL
                    )
                )
                i++
                continue
            }

            // Collect contiguous chunk of DELETE_LEFT and INSERT_RIGHT operations to pair into MODIFY rows
            val deletes = ArrayList<RawEditOp>()
            val inserts = ArrayList<RawEditOp>()
            while (i < rawOps.size && rawOps[i].type != EditOpType.EQUAL) {
                if (rawOps[i].type == EditOpType.DELETE_LEFT) {
                    deletes.add(rawOps[i])
                } else if (rawOps[i].type == EditOpType.INSERT_RIGHT) {
                    inserts.add(rawOps[i])
                }
                i++
            }

            val pairedCount = minOf(deletes.size, inserts.size)
            for (p in 0 until pairedCount) {
                val delOp = deletes[p]
                val insOp = inserts[p]
                val (leftSpans, rightSpans) = computeCharDiffSpans(delOp.text, insOp.text)
                modifiedCount++
                rows.add(
                    DiffRow(
                        rowIndex = rowIndex++,
                        leftLineNumber = delOp.leftIndex + 1,
                        rightLineNumber = insOp.rightIndex + 1,
                        leftText = delOp.text,
                        rightText = insOp.text,
                        changeType = DiffChangeType.MODIFY,
                        leftCharHighlights = leftSpans,
                        rightCharHighlights = rightSpans
                    )
                )
            }

            for (d in pairedCount until deletes.size) {
                val delOp = deletes[d]
                removedCount++
                rows.add(
                    DiffRow(
                        rowIndex = rowIndex++,
                        leftLineNumber = delOp.leftIndex + 1,
                        rightLineNumber = null,
                        leftText = delOp.text,
                        rightText = null,
                        changeType = DiffChangeType.DELETE,
                        leftCharHighlights = if (delOp.text.isNotEmpty()) {
                            listOf(CharDiffSpan(0, delOp.text.length, isChanged = true))
                        } else {
                            emptyList()
                        }
                    )
                )
            }

            for (ins in pairedCount until inserts.size) {
                val insOp = inserts[ins]
                addedCount++
                rows.add(
                    DiffRow(
                        rowIndex = rowIndex++,
                        leftLineNumber = null,
                        rightLineNumber = insOp.rightIndex + 1,
                        leftText = null,
                        rightText = insOp.text,
                        changeType = DiffChangeType.INSERT,
                        rightCharHighlights = if (insOp.text.isNotEmpty()) {
                            listOf(CharDiffSpan(0, insOp.text.length, isChanged = true))
                        } else {
                            emptyList()
                        }
                    )
                )
            }
        }

        return DiffResult(
            leftDocumentId = leftDocumentId,
            rightDocumentId = rightDocumentId,
            leftTitle = leftTitle,
            rightTitle = rightTitle,
            rows = rows,
            addedCount = addedCount,
            removedCount = removedCount,
            modifiedCount = modifiedCount
        )
    }

    /**
     * Myers O(ND) shortest edit script algorithm over line arrays.
     */
    private fun computeMyersLineOps(
        leftLines: List<String>,
        rightLines: List<String>
    ): List<RawEditOp> {
        val n = leftLines.size
        val m = rightLines.size
        val maxD = n + m
        if (maxD == 0) return emptyList()

        val offset = maxD
        var v = IntArray(2 * maxD + 1)
        val trace = ArrayList<IntArray>()

        var finalD = 0
        outer@ for (d in 0..maxD) {
            trace.add(v.copyOf())
            var k = -d
            while (k <= d) {
                val idx = k + offset
                var x = if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) {
                    v[idx + 1]
                } else {
                    v[idx - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && leftLines[x] == rightLines[y]) {
                    x++
                    y++
                }
                v[idx] = x
                if (x >= n && y >= m) {
                    finalD = d
                    break@outer
                }
                k += 2
            }
            v = v.copyOf()
        }

        // Backtrack through Myers trace to build edit operations
        val reversedOps = ArrayList<RawEditOp>(n + m)
        var x = n
        var y = m

        for (d in finalD downTo 1) {
            val vPrev = trace[d]
            val k = x - y
            val idx = k + offset
            val prevK = if (k == -d || (k != d && vPrev[idx - 1] < vPrev[idx + 1])) {
                k + 1
            } else {
                k - 1
            }
            val prevX = vPrev[prevK + offset]
            val prevY = prevX - prevK

            while (x > prevX && y > prevY) {
                x--
                y--
                reversedOps.add(
                    RawEditOp(
                        type = EditOpType.EQUAL,
                        leftIndex = x,
                        rightIndex = y,
                        text = leftLines[x]
                    )
                )
            }

            if (x == prevX) {
                y--
                reversedOps.add(
                    RawEditOp(
                        type = EditOpType.INSERT_RIGHT,
                        rightIndex = y,
                        text = rightLines[y]
                    )
                )
            } else {
                x--
                reversedOps.add(
                    RawEditOp(
                        type = EditOpType.DELETE_LEFT,
                        leftIndex = x,
                        text = leftLines[x]
                    )
                )
            }
        }

        while (x > 0 && y > 0) {
            x--
            y--
            reversedOps.add(
                RawEditOp(
                    type = EditOpType.EQUAL,
                    leftIndex = x,
                    rightIndex = y,
                    text = leftLines[x]
                )
            )
        }

        reversedOps.reverse()
        return reversedOps
    }

    /**
     * Computes character-level diff spans between two modified lines ([leftLine] and [rightLine])
     * using common prefix/suffix trimming + dynamic programming LCS over the interior substring.
     */
    fun computeCharDiffSpans(
        leftLine: String,
        rightLine: String
    ): Pair<List<CharDiffSpan>, List<CharDiffSpan>> {
        if (leftLine == rightLine) return emptyList<CharDiffSpan>() to emptyList()
        if (leftLine.isEmpty()) {
            return emptyList<CharDiffSpan>() to listOf(CharDiffSpan(0, rightLine.length, true))
        }
        if (rightLine.isEmpty()) {
            return listOf(CharDiffSpan(0, leftLine.length, true)) to emptyList()
        }

        var prefixLen = 0
        val minLen = minOf(leftLine.length, rightLine.length)
        while (prefixLen < minLen && leftLine[prefixLen] == rightLine[prefixLen]) {
            prefixLen++
        }

        var leftSuffixStart = leftLine.length
        var rightSuffixStart = rightLine.length
        while (leftSuffixStart > prefixLen &&
            rightSuffixStart > prefixLen &&
            leftLine[leftSuffixStart - 1] == rightLine[rightSuffixStart - 1]
        ) {
            leftSuffixStart--
            rightSuffixStart--
        }

        val leftMid = leftLine.substring(prefixLen, leftSuffixStart)
        val rightMid = rightLine.substring(prefixLen, rightSuffixStart)

        // If middle segments are reasonable length, compute exact character LCS; otherwise highlight middle block
        if (leftMid.length * rightMid.length > 16_384) {
            val leftSpans = if (prefixLen < leftSuffixStart) {
                listOf(CharDiffSpan(prefixLen, leftSuffixStart, true))
            } else {
                emptyList()
            }
            val rightSpans = if (prefixLen < rightSuffixStart) {
                listOf(CharDiffSpan(prefixLen, rightSuffixStart, true))
            } else {
                emptyList()
            }
            return leftSpans to rightSpans
        }

        val n = leftMid.length
        val m = rightMid.length
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (leftMid[i] == rightMid[j]) {
                    1 + dp[i + 1][j + 1]
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val leftChangedFlags = BooleanArray(leftLine.length)
        val rightChangedFlags = BooleanArray(rightLine.length)

        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (leftMid[i] == rightMid[j]) {
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                leftChangedFlags[prefixLen + i] = true
                i++
            } else {
                rightChangedFlags[prefixLen + j] = true
                j++
            }
        }
        while (i < n) {
            leftChangedFlags[prefixLen + i] = true
            i++
        }
        while (j < m) {
            rightChangedFlags[prefixLen + j] = true
            j++
        }

        return compressBooleanFlagsToSpans(leftChangedFlags) to compressBooleanFlagsToSpans(rightChangedFlags)
    }

    private fun compressBooleanFlagsToSpans(flags: BooleanArray): List<CharDiffSpan> {
        val spans = ArrayList<CharDiffSpan>()
        var i = 0
        while (i < flags.size) {
            if (!flags[i]) {
                i++
                continue
            }
            val start = i
            while (i < flags.size && flags[i]) {
                i++
            }
            spans.add(CharDiffSpan(start = start, end = i, isChanged = true))
        }
        return spans
    }

    /**
     * Directional Merge ("Copy to Left" ←): applies the right-hand side of [targetRowIndex] into
     * the left document and returns the updated left document text.
     */
    fun mergeRowRightToLeft(diffResult: DiffResult, targetRowIndex: Int): String {
        val outLeftLines = ArrayList<String>(diffResult.rows.size)
        for (row in diffResult.rows) {
            if (row.rowIndex == targetRowIndex) {
                when (row.changeType) {
                    DiffChangeType.EQUAL -> row.leftText?.let { outLeftLines.add(it) }
                    DiffChangeType.INSERT -> row.rightText?.let { outLeftLines.add(it) }
                    DiffChangeType.DELETE -> {
                        // Right side has no line -> deleting from Left merges Right's deletion into Left
                    }
                    DiffChangeType.MODIFY -> row.rightText?.let { outLeftLines.add(it) }
                }
            } else {
                row.leftText?.let { outLeftLines.add(it) }
            }
        }
        return outLeftLines.joinToString("\n")
    }

    /**
     * Directional Merge ("Copy to Right" →): applies the left-hand side of [targetRowIndex] into
     * the right document and returns the updated right document text.
     */
    fun mergeRowLeftToRight(diffResult: DiffResult, targetRowIndex: Int): String {
        val outRightLines = ArrayList<String>(diffResult.rows.size)
        for (row in diffResult.rows) {
            if (row.rowIndex == targetRowIndex) {
                when (row.changeType) {
                    DiffChangeType.EQUAL -> row.rightText?.let { outRightLines.add(it) }
                    DiffChangeType.DELETE -> row.leftText?.let { outRightLines.add(it) }
                    DiffChangeType.INSERT -> {
                        // Left side has no line -> deleting from Right merges Left's state into Right
                    }
                    DiffChangeType.MODIFY -> row.leftText?.let { outRightLines.add(it) }
                }
            } else {
                row.rightText?.let { outRightLines.add(it) }
            }
        }
        return outRightLines.joinToString("\n")
    }
}
