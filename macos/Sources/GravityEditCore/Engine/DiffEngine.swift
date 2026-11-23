import Foundation

public enum DiffEngine {

    public static func computeDiff(
        leftText: String,
        rightText: String,
        leftDocumentId: String? = nil,
        rightDocumentId: String? = nil,
        leftTitle: String = "Left Document",
        rightTitle: String = "Right Document"
    ) -> DiffResult {
        let leftLines = LineEnding.normalizeToLf(leftText).components(separatedBy: "\n")
        let rightLines = LineEnding.normalizeToLf(rightText).components(separatedBy: "\n")

        let rawEdits = computeLineEdits(left: leftLines, right: rightLines)

        // Coalesce adjacent DELETE + INSERT blocks into MODIFY pairs with intra-line char highlights
        var rows: [DiffRow] = []
        var addedCount = 0
        var removedCount = 0
        var modifiedCount = 0

        var idx = 0
        while idx < rawEdits.count {
            let edit = rawEdits[idx]
            switch edit.kind {
            case .equal:
                rows.append(DiffRow(
                    rowIndex: rows.count,
                    leftLineNumber: edit.leftIdx.map { $0 + 1 },
                    rightLineNumber: edit.rightIdx.map { $0 + 1 },
                    leftText: edit.leftIdx.map { leftLines[$0] },
                    rightText: edit.rightIdx.map { rightLines[$0] },
                    changeType: .equal
                ))
                idx += 1

            case .delete:
                var delBlock: [Int] = []
                while idx < rawEdits.count, rawEdits[idx].kind == .delete, let li = rawEdits[idx].leftIdx {
                    delBlock.append(li)
                    idx += 1
                }
                var insBlock: [Int] = []
                while idx < rawEdits.count, rawEdits[idx].kind == .insert, let ri = rawEdits[idx].rightIdx {
                    insBlock.append(ri)
                    idx += 1
                }

                let pairCount = min(delBlock.count, insBlock.count)
                for p in 0..<pairCount {
                    let lText = leftLines[delBlock[p]]
                    let rText = rightLines[insBlock[p]]
                    let (lSpans, rSpans) = computeCharacterHighlights(left: lText, right: rText)
                    rows.append(DiffRow(
                        rowIndex: rows.count,
                        leftLineNumber: delBlock[p] + 1,
                        rightLineNumber: insBlock[p] + 1,
                        leftText: lText,
                        rightText: rText,
                        changeType: .modify,
                        leftCharHighlights: lSpans,
                        rightCharHighlights: rSpans
                    ))
                    modifiedCount += 1
                }
                if delBlock.count > pairCount {
                    for d in pairCount..<delBlock.count {
                        rows.append(DiffRow(
                            rowIndex: rows.count,
                            leftLineNumber: delBlock[d] + 1,
                            rightLineNumber: nil,
                            leftText: leftLines[delBlock[d]],
                            rightText: nil,
                            changeType: .delete
                        ))
                        removedCount += 1
                    }
                }
                if insBlock.count > pairCount {
                    for ins in pairCount..<insBlock.count {
                        rows.append(DiffRow(
                            rowIndex: rows.count,
                            leftLineNumber: nil,
                            rightLineNumber: insBlock[ins] + 1,
                            leftText: nil,
                            rightText: rightLines[insBlock[ins]],
                            changeType: .insert
                        ))
                        addedCount += 1
                    }
                }

            case .insert:
                if let ri = edit.rightIdx {
                    rows.append(DiffRow(
                        rowIndex: rows.count,
                        leftLineNumber: nil,
                        rightLineNumber: ri + 1,
                        leftText: nil,
                        rightText: rightLines[ri],
                        changeType: .insert
                    ))
                    addedCount += 1
                }
                idx += 1
            }
        }

        return DiffResult(
            leftDocumentId: leftDocumentId,
            rightDocumentId: rightDocumentId,
            leftTitle: leftTitle,
            rightTitle: rightTitle,
            rows: rows,
            addedCount: addedCount,
            removedCount: removedCount,
            modifiedCount: modifiedCount
        )
    }

    // MARK: - Live Gutter Change Bars

    /// Computes 1-based line change markers (`added`, `modified`, `deletedBelow`) for `currentText`
    /// relative to `baselineText` (either Git `HEAD` or `savedContent`).
    public static func computeGutterLineChanges(
        baselineText: String,
        currentText: String
    ) -> [Int: GutterLineChangeKind] {
        let normBase = LineEnding.normalizeToLf(baselineText)
        let normCurr = LineEnding.normalizeToLf(currentText)
        guard normBase != normCurr else { return [:] }

        let diff = computeDiff(leftText: normBase, rightText: normCurr)
        var changes: [Int: GutterLineChangeKind] = [:]
        var lastRightLine = 1

        for row in diff.rows {
            if let rLine = row.rightLineNumber {
                lastRightLine = rLine
            }
            switch row.changeType {
            case .equal:
                break
            case .insert:
                if let rLine = row.rightLineNumber {
                    changes[rLine] = .added
                }
            case .modify:
                if let rLine = row.rightLineNumber {
                    changes[rLine] = .modified
                }
            case .delete:
                let anchor = max(1, lastRightLine)
                if changes[anchor] == nil {
                    changes[anchor] = .deletedBelow
                }
            }
        }
        return changes
    }

    // MARK: - Directional Row / Hunk Merge

    public static func mergeRowRightToLeft(diff: DiffResult, rowIndex: Int) -> String {
        var leftOut: [String] = []
        for row in diff.rows {
            if row.rowIndex == rowIndex {
                if let right = row.rightText {
                    leftOut.append(right)
                }
            } else if let left = row.leftText {
                leftOut.append(left)
            }
        }
        return leftOut.joined(separator: "\n")
    }

    public static func mergeRowLeftToRight(diff: DiffResult, rowIndex: Int) -> String {
        var rightOut: [String] = []
        for row in diff.rows {
            if row.rowIndex == rowIndex {
                if let left = row.leftText {
                    rightOut.append(left)
                }
            } else if let right = row.rightText {
                rightOut.append(right)
            }
        }
        return rightOut.joined(separator: "\n")
    }

    // MARK: - Myers / LCS Line Diff & Intra-Line Char Spans

    private enum RawEditKind {
        case equal
        case delete
        case insert
    }

    private struct RawLineEdit {
        let kind: RawEditKind
        let leftIdx: Int?
        let rightIdx: Int?
    }

    private static func computeLineEdits(left: [String], right: [String]) -> [RawLineEdit] {
        // Trim common prefix and suffix in O(N) before running LCS/Myers
        var prefix = 0
        while prefix < left.count && prefix < right.count && left[prefix] == right[prefix] {
            prefix += 1
        }
        var leftEnd = left.count
        var rightEnd = right.count
        while leftEnd > prefix && rightEnd > prefix && left[leftEnd - 1] == right[rightEnd - 1] {
            leftEnd -= 1
            rightEnd -= 1
        }

        var edits: [RawLineEdit] = []
        edits.reserveCapacity(left.count + right.count)

        for i in 0..<prefix {
            edits.append(RawLineEdit(kind: .equal, leftIdx: i, rightIdx: i))
        }

        let midLeft = Array(left[prefix..<leftEnd])
        let midRight = Array(right[prefix..<rightEnd])

        let diff = midRight.difference(from: midLeft)
        var removedSet: Set<Int> = []
        var insertedByTargetOffset: [Int: Int] = [:]
        for change in diff {
            switch change {
            case .remove(let offset, _, _):
                removedSet.insert(offset)
            case .insert(let offset, _, _):
                insertedByTargetOffset[offset] = offset
            }
        }

        var lIdx = 0
        var rIdx = 0
        while lIdx < midLeft.count || rIdx < midRight.count {
            if lIdx < midLeft.count && removedSet.contains(lIdx) {
                edits.append(RawLineEdit(kind: .delete, leftIdx: prefix + lIdx, rightIdx: nil))
                lIdx += 1
            } else if insertedByTargetOffset[rIdx] != nil {
                edits.append(RawLineEdit(kind: .insert, leftIdx: nil, rightIdx: prefix + rIdx))
                rIdx += 1
            } else if lIdx < midLeft.count && rIdx < midRight.count {
                edits.append(RawLineEdit(kind: .equal, leftIdx: prefix + lIdx, rightIdx: prefix + rIdx))
                lIdx += 1
                rIdx += 1
            } else if lIdx < midLeft.count {
                edits.append(RawLineEdit(kind: .delete, leftIdx: prefix + lIdx, rightIdx: nil))
                lIdx += 1
            } else if rIdx < midRight.count {
                edits.append(RawLineEdit(kind: .insert, leftIdx: nil, rightIdx: prefix + rIdx))
                rIdx += 1
            }
        }

        let suffixCount = left.count - leftEnd
        for s in 0..<suffixCount {
            edits.append(RawLineEdit(kind: .equal, leftIdx: leftEnd + s, rightIdx: rightEnd + s))
        }

        return edits
    }

    private static func computeCharacterHighlights(
        left: String,
        right: String
    ) -> (leftSpans: [CharDiffSpan], rightSpans: [CharDiffSpan]) {
        let lChars = Array(left)
        let rChars = Array(right)
        var commonPre = 0
        while commonPre < lChars.count && commonPre < rChars.count && lChars[commonPre] == rChars[commonPre] {
            commonPre += 1
        }
        var lEnd = lChars.count
        var rEnd = rChars.count
        while lEnd > commonPre && rEnd > commonPre && lChars[lEnd - 1] == rChars[rEnd - 1] {
            lEnd -= 1
            rEnd -= 1
        }

        var lSpans: [CharDiffSpan] = []
        if commonPre > 0 { lSpans.append(CharDiffSpan(start: 0, end: commonPre, isChanged: false)) }
        if lEnd > commonPre { lSpans.append(CharDiffSpan(start: commonPre, end: lEnd, isChanged: true)) }
        if lEnd < lChars.count { lSpans.append(CharDiffSpan(start: lEnd, end: lChars.count, isChanged: false)) }

        var rSpans: [CharDiffSpan] = []
        if commonPre > 0 { rSpans.append(CharDiffSpan(start: 0, end: commonPre, isChanged: false)) }
        if rEnd > commonPre { rSpans.append(CharDiffSpan(start: commonPre, end: rEnd, isChanged: true)) }
        if rEnd < rChars.count { rSpans.append(CharDiffSpan(start: rEnd, end: rChars.count, isChanged: false)) }

        return (lSpans, rSpans)
    }
}
