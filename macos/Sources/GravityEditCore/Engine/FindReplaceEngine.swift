import Foundation

public enum FindReplaceEngine {

    // MARK: - Pattern Compilation

    public static func compileRegex(options: SearchOptions) throws -> NSRegularExpression {
        guard !options.query.isEmpty else {
            throw NSError(domain: "FindReplaceEngine", code: 1, userInfo: [NSLocalizedDescriptionKey: "Search query is empty"])
        }

        var pattern = options.grepRegex ? options.query : NSRegularExpression.escapedPattern(for: options.query)
        if options.matchEntireWords {
            pattern = "\\b(?:\(pattern))\\b"
        }

        var regexOptions: NSRegularExpression.Options = []
        if !options.caseSensitive {
            regexOptions.insert(.caseInsensitive)
        }
        if options.multiLine {
            regexOptions.insert(.anchorsMatchLines)
        }
        if options.dotMatchesAll {
            regexOptions.insert(.dotMatchesLineSeparators)
        }

        return try NSRegularExpression(pattern: pattern, options: regexOptions)
    }

    // MARK: - Single-Document Search & Replace

    public static func findAllMatches(
        in text: String,
        options: SearchOptions,
        selectionRange: NSRange? = nil
    ) -> Result<[SearchMatch], Error> {
        guard !options.query.isEmpty else { return .success([]) }
        let regex: NSRegularExpression
        do {
            regex = try compileRegex(options: options)
        } catch {
            return .failure(error)
        }

        let ns = text as NSString
        let targetRange: NSRange
        if options.searchInSelectionOnly, let sel = selectionRange, sel.length > 0, sel.location + sel.length <= ns.length {
            targetRange = sel
        } else {
            targetRange = NSRange(location: 0, length: ns.length)
        }

        // Precompute line start UTF-16 offsets in O(N)
        let lineStarts = computeLineStartOffsets(ns)
        let rawMatches = regex.matches(in: text, options: [], range: targetRange)
        var results: [SearchMatch] = []
        results.reserveCapacity(rawMatches.count)

        for (idx, m) in rawMatches.enumerated() {
            guard m.range.length > 0 || idx == 0 || m.range.location != rawMatches[idx - 1].range.location else {
                continue
            }
            let matchedStr = ns.substring(with: m.range)
            let (lineNum, colNum, preview) = lineInfo(at: m.range.location, in: ns, lineStarts: lineStarts)

            var groups: [CaptureGroupMatch] = []
            if m.numberOfRanges > 1 {
                for g in 1..<m.numberOfRanges {
                    let gRange = m.range(at: g)
                    if gRange.location != NSNotFound {
                        groups.append(CaptureGroupMatch(
                            groupIndex: g,
                            value: ns.substring(with: gRange),
                            nsRange: gRange
                        ))
                    }
                }
            }

            results.append(SearchMatch(
                matchIndex: results.count,
                nsRange: m.range,
                matchedText: matchedStr,
                lineNumber: lineNum,
                columnNumber: colNum,
                linePreview: preview,
                captureGroups: groups
            ))
        }

        return .success(results)
    }

    public static func replaceSingleMatch(
        in text: String,
        match: SearchMatch,
        options: SearchOptions
    ) -> Result<(updatedText: String, updatedCursorOffset: Int), Error> {
        do {
            let regex = try compileRegex(options: options)
            let ns = text as NSString
            guard match.nsRange.location + match.nsRange.length <= ns.length else {
                return .success((text, ns.length))
            }
            let template = options.grepRegex
                ? unescapeReplacementEscapes(options.replacement)
                : NSRegularExpression.escapedTemplate(for: options.replacement)

            guard let result = regex.firstMatch(in: text, options: [], range: match.nsRange) else {
                return .success((text, match.nsRange.location))
            }
            let replacementText = regex.replacementString(for: result, in: text, offset: 0, template: template)
            let updated = ns.replacingCharacters(in: result.range, with: replacementText)
            let cursor = result.range.location + (replacementText as NSString).length
            return .success((updated, cursor))
        } catch {
            return .failure(error)
        }
    }

    public static func replaceAllMatches(
        in text: String,
        options: SearchOptions,
        selectionRange: NSRange? = nil
    ) -> Result<(updatedText: String, replacedCount: Int), Error> {
        do {
            let regex = try compileRegex(options: options)
            let ns = text as NSString
            let targetRange: NSRange
            if options.searchInSelectionOnly, let sel = selectionRange, sel.length > 0, sel.location + sel.length <= ns.length {
                targetRange = sel
            } else {
                targetRange = NSRange(location: 0, length: ns.length)
            }

            let count = regex.numberOfMatches(in: text, options: [], range: targetRange)
            guard count > 0 else { return .success((text, 0)) }

            let template = options.grepRegex
                ? unescapeReplacementEscapes(options.replacement)
                : NSRegularExpression.escapedTemplate(for: options.replacement)

            let mutable = NSMutableString(string: text)
            let replaced = regex.replaceMatches(in: mutable, options: [], range: targetRange, withTemplate: template)
            return .success((mutable as String, replaced))
        } catch {
            return .failure(error)
        }
    }

    public static func extractMatchesToText(
        in text: String,
        options: SearchOptions,
        selectionRange: NSRange? = nil
    ) -> Result<(extractedContent: String, matchCount: Int), Error> {
        switch findAllMatches(in: text, options: options, selectionRange: selectionRange) {
        case .failure(let err):
            return .failure(err)
        case .success(let matches):
            guard !matches.isEmpty else { return .success(("", 0)) }
            if options.grepRegex && !options.replacement.isEmpty {
                do {
                    let regex = try compileRegex(options: options)
                    let template = unescapeReplacementEscapes(options.replacement)
                    var lines: [String] = []
                    lines.reserveCapacity(matches.count)
                    for m in matches {
                        if let raw = regex.firstMatch(in: text, options: [], range: m.nsRange) {
                            lines.append(regex.replacementString(for: raw, in: text, offset: 0, template: template))
                        } else {
                            lines.append(m.matchedText)
                        }
                    }
                    return .success((lines.joined(separator: "\n"), matches.count))
                } catch {
                    return .failure(error)
                }
            }
            let joined = matches.map(\.matchedText).joined(separator: "\n")
            return .success((joined, matches.count))
        }
    }

    // MARK: - Live Pattern Playground

    public static func evaluatePlayground(
        sampleText: String,
        options: SearchOptions
    ) -> PatternPlaygroundEvaluation {
        guard !options.query.isEmpty else {
            return PatternPlaygroundEvaluation(isValidRegex: true, matches: [], replacementPreview: sampleText, executionTimeMicros: 0)
        }
        let start = DispatchTime.now().uptimeNanoseconds
        switch findAllMatches(in: sampleText, options: options) {
        case .failure(let error):
            let elapsed = Int64((DispatchTime.now().uptimeNanoseconds - start) / 1_000)
            return PatternPlaygroundEvaluation(
                isValidRegex: false,
                errorMessage: error.localizedDescription,
                matches: [],
                replacementPreview: sampleText,
                executionTimeMicros: elapsed
            )
        case .success(let matches):
            let preview: String
            if case .success(let rep) = replaceAllMatches(in: sampleText, options: options) {
                preview = rep.updatedText
            } else {
                preview = sampleText
            }
            let elapsed = Int64((DispatchTime.now().uptimeNanoseconds - start) / 1_000)
            return PatternPlaygroundEvaluation(
                isValidRegex: true,
                errorMessage: nil,
                matches: matches,
                replacementPreview: preview,
                executionTimeMicros: elapsed
            )
        }
    }

    // MARK: - Multi-File Directory Search & Batch Replace

    public static func searchInDirectory(
        rootURL: URL,
        options: SearchOptions,
        maxFiles: Int = 500
    ) -> Result<[MultiFileSearchMatch], Error> {
        let regex: NSRegularExpression
        do {
            regex = try compileRegex(options: options)
        } catch {
            return .failure(error)
        }

        let allowedExtensions = parseAllowedExtensions(options.fileFilter)
        let fm = FileManager.default
        guard let enumerator = fm.enumerator(
            at: rootURL,
            includingPropertiesForKeys: [.isRegularFileKey, .isHiddenKey, .fileSizeKey],
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else {
            return .success([])
        }

        var fileResults: [MultiFileSearchMatch] = []
        var scannedCount = 0

        for case let fileURL as URL in enumerator {
            if scannedCount >= maxFiles { break }
            guard let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
                  values.isRegularFile == true,
                  (values.fileSize ?? 0) <= 5_000_000 else {
                continue
            }

            let ext = fileURL.pathExtension.lowercased()
            if !allowedExtensions.isEmpty && !allowedExtensions.contains(ext) {
                continue
            }
            scannedCount += 1

            guard let data = try? Data(contentsOf: fileURL) else { continue }
            let decoded = DocumentEncoding.decode(data)
            let normalized = LineEnding.normalizeToLf(decoded.text)
            let ns = normalized as NSString
            let rawMatches = regex.matches(in: normalized, options: [], range: NSRange(location: 0, length: ns.length))
            guard !rawMatches.isEmpty else { continue }

            let lineStarts = computeLineStartOffsets(ns)
            var matches: [SearchMatch] = []
            for (idx, m) in rawMatches.prefix(250).enumerated() {
                let (ln, col, prev) = lineInfo(at: m.range.location, in: ns, lineStarts: lineStarts)
                matches.append(SearchMatch(
                    matchIndex: idx,
                    nsRange: m.range,
                    matchedText: ns.substring(with: m.range),
                    lineNumber: ln,
                    columnNumber: col,
                    linePreview: prev
                ))
            }

            let relPath = fileURL.path.replacingOccurrences(of: rootURL.path + "/", with: "")
            fileResults.append(MultiFileSearchMatch(
                fileURL: fileURL,
                fileName: fileURL.lastPathComponent,
                relativePath: relPath,
                matches: matches
            ))
        }

        return .success(fileResults.sorted { $0.relativePath < $1.relativePath })
    }

    public static func previewReplaceInDirectory(
        rootURL: URL,
        options: SearchOptions,
        maxFiles: Int = 500
    ) -> Result<[MultiFileReplacePreviewItem], Error> {
        switch searchInDirectory(rootURL: rootURL, options: options, maxFiles: maxFiles) {
        case .failure(let err):
            return .failure(err)
        case .success(let fileMatches):
            var previews: [MultiFileReplacePreviewItem] = []
            previews.reserveCapacity(fileMatches.count)
            for item in fileMatches {
                guard let data = try? Data(contentsOf: item.fileURL) else { continue }
                let (rawText, _) = DocumentEncoding.decode(data)
                let normalized = LineEnding.normalizeToLf(rawText)
                guard case .success(let rep) = replaceAllMatches(in: normalized, options: options),
                      rep.replacedCount > 0 else {
                    continue
                }
                let beforeLines = normalized.components(separatedBy: "\n")
                let afterLines = rep.updatedText.components(separatedBy: "\n")
                let maxLines = min(beforeLines.count, afterLines.count)
                var changes: [MultiFileReplaceLineChange] = []
                for lineIdx in 0..<maxLines where beforeLines[lineIdx] != afterLines[lineIdx] {
                    changes.append(
                        MultiFileReplaceLineChange(
                            lineNumber: lineIdx + 1,
                            beforeLine: beforeLines[lineIdx],
                            afterLine: afterLines[lineIdx]
                        )
                    )
                    if changes.count >= 40 { break }
                }
                if changes.isEmpty && beforeLines != afterLines {
                    changes.append(
                        MultiFileReplaceLineChange(
                            lineNumber: item.matches.first?.lineNumber ?? 1,
                            beforeLine: beforeLines.first ?? "",
                            afterLine: afterLines.first ?? ""
                        )
                    )
                }
                previews.append(
                    MultiFileReplacePreviewItem(
                        fileURL: item.fileURL,
                        fileName: item.fileName,
                        relativePath: item.relativePath,
                        replacementCount: rep.replacedCount,
                        lineChanges: changes,
                        isSelected: true
                    )
                )
            }
            return .success(previews)
        }
    }

    public static func replaceInDirectory(
        rootURL: URL,
        options: SearchOptions,
        selectedFilePaths: Set<String>? = nil
    ) -> Result<(filesModified: Int, totalReplacements: Int), Error> {
        switch searchInDirectory(rootURL: rootURL, options: options) {
        case .failure(let err):
            return .failure(err)
        case .success(let fileMatches):
            let resolvedAllowed: Set<String>? = selectedFilePaths.map { paths in
                Set(paths.map { URL(fileURLWithPath: $0).resolvingSymlinksInPath().path })
            }
            var filesModified = 0
            var totalReplacements = 0
            for item in fileMatches {
                if let allowed = resolvedAllowed,
                   !allowed.contains(item.fileURL.resolvingSymlinksInPath().path) {
                    continue
                }
                guard let data = try? Data(contentsOf: item.fileURL) else { continue }
                let (rawText, encoding) = DocumentEncoding.decode(data)
                let lineEnding = LineEnding.detect(rawText)
                let normalized = LineEnding.normalizeToLf(rawText)
                if case .success(let rep) = replaceAllMatches(in: normalized, options: options), rep.replacedCount > 0 {
                    let finalText = LineEnding.applyLineEnding(rep.updatedText, target: lineEnding)
                    let outData = encoding.encode(finalText)
                    if (try? outData.write(to: item.fileURL, options: .atomic)) != nil {
                        filesModified += 1
                        totalReplacements += rep.replacedCount
                    }
                }
            }
            return .success((filesModified, totalReplacements))
        }
    }

    // MARK: - Helpers

    private static func unescapeReplacementEscapes(_ raw: String) -> String {
        raw
            .replacingOccurrences(of: "\\n", with: "\n")
            .replacingOccurrences(of: "\\t", with: "\t")
            .replacingOccurrences(of: "\\r", with: "\r")
    }

    private static func parseAllowedExtensions(_ filter: String) -> Set<String> {
        let parts = filter
            .components(separatedBy: CharacterSet(charactersIn: ";, "))
            .map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
            .filter { !$0.isEmpty && $0 != "*" && $0 != "*.*" }
        var exts: Set<String> = []
        for p in parts {
            let cleaned = p.replacingOccurrences(of: "*.", with: "").trimmingCharacters(in: CharacterSet(charactersIn: "."))
            if !cleaned.isEmpty { exts.insert(cleaned) }
        }
        return exts
    }

    private static func computeLineStartOffsets(_ ns: NSString) -> [Int] {
        var starts: [Int] = [0]
        let len = ns.length
        var i = 0
        while i < len {
            if ns.character(at: i) == 10 {
                starts.append(i + 1)
            }
            i += 1
        }
        return starts
    }

    private static func lineInfo(at utf16Offset: Int, in ns: NSString, lineStarts: [Int]) -> (line: Int, col: Int, preview: String) {
        var low = 0
        var high = lineStarts.count - 1
        while low <= high {
            let mid = (low + high) / 2
            if lineStarts[mid] <= utf16Offset {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        let lineIdx = max(0, high)
        let lineStart = lineStarts[lineIdx]
        let lineEnd = (lineIdx + 1 < lineStarts.count) ? max(lineStart, lineStarts[lineIdx + 1] - 1) : ns.length
        let previewLen = min(200, max(0, lineEnd - lineStart))
        let preview = ns.substring(with: NSRange(location: lineStart, length: previewLen)).trimmingCharacters(in: .whitespaces)
        return (lineIdx + 1, (utf16Offset - lineStart) + 1, preview)
    }
}
