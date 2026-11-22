import Foundation

public enum TextTransformEngine {

    // MARK: - Batch Pipeline Execution

    public static func executePipeline(
        inputText: String,
        steps: [TextFactoryStep],
        documentLanguage: SyntaxLanguage = .powershell
    ) -> TextFactoryResult {
        var current = LineEnding.normalizeToLf(inputText)
        var extracted: [ExtractedDocumentItem] = []
        var finalLineEnding: LineEnding? = nil
        var executed = 0

        for step in steps where step.enabled {
            executed += 1
            switch step.operationType {
            case .zapGremlins:
                current = zapGremlins(
                    current,
                    action: step.zapAction,
                    zapNonAscii: step.zapNonAscii,
                    zapControlChars: step.zapControlChars,
                    zapNullBytes: step.zapNullBytes,
                    replacement: step.zapReplacementChar
                )

            case .processDuplicateLines:
                let res = processDuplicateLines(
                    current,
                    mode: step.duplicateMode,
                    caseSensitive: step.duplicateCaseSensitive,
                    ignoreLeadingWhitespace: step.duplicateIgnoreLeadingWhitespace
                )
                current = res.remainingText
                if let dupDoc = res.extractedDuplicates {
                    extracted.append(ExtractedDocumentItem(title: "Extracted_Duplicates.txt", content: dupDoc))
                }

            case .sortLines:
                current = sortLines(
                    current,
                    direction: step.sortDirection,
                    caseSensitive: step.sortCaseSensitive,
                    numeric: step.sortNumeric,
                    regexPattern: step.sortRegexPattern,
                    regexGroupIndex: step.sortRegexGroupIndex
                )

            case .prefixSuffixLines:
                current = prefixSuffixLines(
                    current,
                    action: step.prefixSuffixAction,
                    prefix: step.linePrefix,
                    suffix: step.lineSuffix,
                    skipBlankLines: step.skipBlankLines
                )

            case .changeCase:
                current = changeCase(current, mode: step.caseMode)

            case .stripTrailingWhitespace:
                current = stripTrailingWhitespace(current)

            case .entabDetab:
                current = convertTabsAndSpaces(current, mode: step.tabConversionMode, tabWidth: max(1, step.tabWidth))

            case .hardWrapUnwrap:
                current = wrapOrUnwrap(
                    current,
                    mode: step.wrapMode,
                    columnWidth: max(20, step.wrapColumnWidth),
                    preserveParagraphs: step.wrapPreserveParagraphs
                )

            case .addRemoveLineNumbers:
                current = processLineNumbers(
                    current,
                    action: step.lineNumberAction,
                    start: step.lineNumberStart,
                    increment: max(1, step.lineNumberIncrement),
                    separator: step.lineNumberSeparator,
                    zeroPadWidth: max(0, step.lineNumberZeroPadWidth)
                )

            case .educateStraightenQuotes:
                current = transformQuotes(current, mode: step.quoteMode)

            case .canonizeReplaceTable:
                current = canonizeUsingTable(
                    current,
                    tableRaw: step.canonizeTableRaw,
                    caseSensitive: step.canonizeCaseSensitive,
                    wholeWords: step.canonizeWholeWords
                )

            case .processLinesContaining:
                let res = processLinesContaining(
                    current,
                    action: step.matchingLinesAction,
                    pattern: step.matchingPattern,
                    useRegex: step.matchingUseRegex,
                    caseSensitive: step.matchingCaseSensitive
                )
                current = res.updatedText
                if let extDoc = res.extractedText {
                    extracted.append(ExtractedDocumentItem(title: "Matching_Lines.txt", content: extDoc))
                }

            case .convertLineEndings:
                finalLineEnding = step.targetLineEnding

            case .formatPrettify:
                current = formatPrettify(
                    current,
                    target: step.formatTarget,
                    documentLanguage: documentLanguage,
                    indentSpaces: max(1, step.formatIndentSpaces)
                )
            }
        }

        let summary = executed == 0
            ? "No active Text Factory steps selected."
            : "Executed \(executed) Text Factory step\(executed == 1 ? "" : "s")."

        return TextFactoryResult(
            transformedText: current,
            extractedDocuments: extracted,
            targetLineEnding: finalLineEnding,
            stepsExecuted: executed,
            summary: summary
        )
    }

    // MARK: - 1. Zap Gremlins

    public static func zapGremlins(
        _ text: String,
        action: ZapGremlinsAction = .delete,
        zapNonAscii: Bool = false,
        zapControlChars: Bool = true,
        zapNullBytes: Bool = true,
        replacement: String = "?"
    ) -> String {
        var out = ""
        out.reserveCapacity(text.count)

        for scalar in text.unicodeScalars {
            let v = scalar.value
            let isNull = (v == 0)
            let isAllowedWS = (v == 9 || v == 10 || v == 13)
            let isControl = (!isAllowedWS && ((v > 0 && v < 32) || v == 127 || v == 0x200B || v == 0xFEFF))
            let isNonAscii = (v > 127)

            let isGremlin = (zapNullBytes && isNull) || (zapControlChars && isControl) || (zapNonAscii && isNonAscii)
            if !isGremlin {
                out.unicodeScalars.append(scalar)
            } else {
                switch action {
                case .delete:
                    break
                case .replaceWithHex:
                    out.append(String(format: "\\x%02X", v))
                case .replaceWithChar:
                    out.append(replacement.isEmpty ? "?" : replacement)
                }
            }
        }
        return out
    }

    // MARK: - 2. Process Duplicate Lines

    public static func processDuplicateLines(
        _ text: String,
        mode: DuplicateLineMode = .leaveOne,
        caseSensitive: Bool = true,
        ignoreLeadingWhitespace: Bool = false
    ) -> (remainingText: String, extractedDuplicates: String?) {
        let lines = text.components(separatedBy: "\n")
        func normalizeKey(_ line: String) -> String {
            var s = ignoreLeadingWhitespace ? String(line.drop(while: { $0 == " " || $0 == "\t" })) : line
            if !caseSensitive { s = s.lowercased() }
            return s
        }

        var counts: [String: Int] = [:]
        for line in lines {
            counts[normalizeKey(line), default: 0] += 1
        }

        switch mode {
        case .leaveOne:
            var seen: Set<String> = []
            var kept: [String] = []
            for line in lines {
                let k = normalizeKey(line)
                if !seen.contains(k) {
                    seen.insert(k)
                    kept.append(line)
                }
            }
            return (kept.joined(separator: "\n"), nil)

        case .keepUniqueOnly:
            let kept = lines.filter { (counts[normalizeKey($0)] ?? 0) == 1 }
            return (kept.joined(separator: "\n"), nil)

        case .extractDuplicates:
            var seen: Set<String> = []
            var kept: [String] = []
            var dups: [String] = []
            for line in lines {
                let k = normalizeKey(line)
                if seen.contains(k) {
                    dups.append(line)
                } else {
                    seen.insert(k)
                    kept.append(line)
                }
            }
            return (kept.joined(separator: "\n"), dups.isEmpty ? nil : dups.joined(separator: "\n"))
        }
    }

    // MARK: - 3. Sort Lines

    public static func sortLines(
        _ text: String,
        direction: SortDirection = .ascending,
        caseSensitive: Bool = false,
        numeric: Bool = false,
        regexPattern: String = "",
        regexGroupIndex: Int = 1
    ) -> String {
        var lines = text.components(separatedBy: "\n")
        guard lines.count > 1 else { return text }

        if direction == .reverseOnly {
            lines.reverse()
            return lines.joined(separator: "\n")
        }

        let compiledRegex: NSRegularExpression? = regexPattern.isEmpty
            ? nil
            : (try? NSRegularExpression(pattern: regexPattern, options: caseSensitive ? [] : [.caseInsensitive]))

        func sortKey(_ line: String) -> String {
            guard let rx = compiledRegex else { return line }
            let ns = line as NSString
            guard let m = rx.firstMatch(in: line, options: [], range: NSRange(location: 0, length: ns.length)) else {
                return line
            }
            let idx = min(max(0, regexGroupIndex), m.numberOfRanges - 1)
            let r = m.range(at: idx)
            return r.location != NSNotFound ? ns.substring(with: r) : line
        }

        var compareOptions: String.CompareOptions = []
        if !caseSensitive { compareOptions.insert(.caseInsensitive) }
        if numeric { compareOptions.insert(.numeric) }

        lines.sort { a, b in
            let ka = sortKey(a)
            let kb = sortKey(b)
            let cmp = ka.compare(kb, options: compareOptions)
            return direction == .ascending ? (cmp == .orderedAscending) : (cmp == .orderedDescending)
        }
        return lines.joined(separator: "\n")
    }

    // MARK: - 4. Prefix / Suffix Lines

    public static func prefixSuffixLines(
        _ text: String,
        action: PrefixSuffixAction = .insert,
        prefix: String = "",
        suffix: String = "",
        skipBlankLines: Bool = true
    ) -> String {
        let lines = text.components(separatedBy: "\n")
        let mapped = lines.map { line -> String in
            if skipBlankLines && line.trimmingCharacters(in: .whitespaces).isEmpty {
                return line
            }
            switch action {
            case .insert:
                return "\(prefix)\(line)\(suffix)"
            case .remove:
                var updated = line
                if !prefix.isEmpty && updated.hasPrefix(prefix) {
                    updated.removeFirst(prefix.count)
                }
                if !suffix.isEmpty && updated.hasSuffix(suffix) {
                    updated.removeLast(suffix.count)
                }
                return updated
            }
        }
        return mapped.joined(separator: "\n")
    }

    // MARK: - 5. Change Case

    public static func changeCase(_ text: String, mode: CaseTransformMode) -> String {
        switch mode {
        case .uppercase:
            return text.uppercased()
        case .lowercase:
            return text.lowercased()
        case .titleCase:
            return text.capitalized
        case .capitalizeSentences:
            var out = ""
            out.reserveCapacity(text.count)
            var capNext = true
            for ch in text {
                if capNext && ch.isLetter {
                    out.append(ch.uppercased())
                    capNext = false
                } else {
                    out.append(ch.lowercased())
                    if ch == "." || ch == "!" || ch == "?" || ch == "\n" {
                        capNext = true
                    }
                }
            }
            return out
        case .camelCase:
            return text.components(separatedBy: "\n").map { line in
                let expanded = line.replacingOccurrences(of: #"([a-z0-9])([A-Z])"#, with: "$1 $2", options: .regularExpression)
                let words = expanded.split(whereSeparator: { !$0.isLetter && !$0.isNumber })
                guard let first = words.first else { return line }
                let tail = words.dropFirst().map { $0.prefix(1).uppercased() + $0.dropFirst().lowercased() }.joined()
                return first.lowercased() + tail
            }.joined(separator: "\n")
        case .snakeCase:
            return text.components(separatedBy: "\n").map { line in
                let expanded = line.replacingOccurrences(of: #"([a-z0-9])([A-Z])"#, with: "$1_$2", options: .regularExpression)
                let words = expanded.split(whereSeparator: { !$0.isLetter && !$0.isNumber })
                return words.isEmpty ? line : words.map { $0.lowercased() }.joined(separator: "_")
            }.joined(separator: "\n")
        case .invertCase:
            return String(text.map { ch in
                if ch.isUppercase { return Character(ch.lowercased()) }
                if ch.isLowercase { return Character(ch.uppercased()) }
                return ch
            })
        }
    }

    // MARK: - 6. Strip Trailing Whitespace

    public static func stripTrailingWhitespace(_ text: String) -> String {
        text.components(separatedBy: "\n").map { line in
            var s = line
            while let last = s.last, last == " " || last == "\t" {
                s.removeLast()
            }
            return s
        }.joined(separator: "\n")
    }

    // MARK: - 7. Entab & Detab

    public static func convertTabsAndSpaces(
        _ text: String,
        mode: TabConversionMode,
        tabWidth: Int = 4
    ) -> String {
        let width = max(1, tabWidth)
        return text.components(separatedBy: "\n").map { line in
            switch mode {
            case .detabTabsToSpaces:
                var col = 0
                var out = ""
                for ch in line {
                    if ch == "\t" {
                        let spaces = width - (col % width)
                        out.append(String(repeating: " ", count: spaces))
                        col += spaces
                    } else {
                        out.append(ch)
                        col += 1
                    }
                }
                return out

            case .entabSpacesToTabs:
                var leadingSpaces = 0
                var idx = line.startIndex
                while idx < line.endIndex {
                    if line[idx] == " " {
                        leadingSpaces += 1
                    } else if line[idx] == "\t" {
                        leadingSpaces += width - (leadingSpaces % width)
                    } else {
                        break
                    }
                    idx = line.index(after: idx)
                }
                let tabs = leadingSpaces / width
                let remSpaces = leadingSpaces % width
                return String(repeating: "\t", count: tabs) + String(repeating: " ", count: remSpaces) + String(line[idx...])
            }
        }.joined(separator: "\n")
    }

    // MARK: - 8. Hard Wrap & Unwrap

    public static func wrapOrUnwrap(
        _ text: String,
        mode: WrapTransformMode,
        columnWidth: Int = 80,
        preserveParagraphs: Bool = true
    ) -> String {
        switch mode {
        case .unwrapRemoveLineBreaks:
            if preserveParagraphs {
                let paragraphs = text.components(separatedBy: "\n\n")
                return paragraphs.map {
                    $0.components(separatedBy: "\n")
                        .map { $0.trimmingCharacters(in: .whitespaces) }
                        .filter { !$0.isEmpty }
                        .joined(separator: " ")
                }.joined(separator: "\n\n")
            } else {
                return text.components(separatedBy: "\n")
                    .map { $0.trimmingCharacters(in: .whitespaces) }
                    .filter { !$0.isEmpty }
                    .joined(separator: " ")
            }

        case .hardWrap:
            let limit = max(20, columnWidth)
            var resultLines: [String] = []
            for rawLine in text.components(separatedBy: "\n") {
                if rawLine.count <= limit {
                    resultLines.append(rawLine)
                    continue
                }
                let words = rawLine.split(separator: " ", omittingEmptySubsequences: false).map(String.init)
                var current = ""
                for w in words {
                    if current.isEmpty {
                        current = w
                    } else if current.count + 1 + w.count <= limit {
                        current += " " + w
                    } else {
                        resultLines.append(current)
                        current = w
                    }
                }
                if !current.isEmpty {
                    resultLines.append(current)
                }
            }
            return resultLines.joined(separator: "\n")
        }
    }

    // MARK: - 9. Add / Remove Line Numbers

    public static func processLineNumbers(
        _ text: String,
        action: LineNumberAction,
        start: Int = 1,
        increment: Int = 1,
        separator: String = ": ",
        zeroPadWidth: Int = 4
    ) -> String {
        let lines = text.components(separatedBy: "\n")
        switch action {
        case .addNumbers:
            var num = start
            return lines.map { line in
                let formatted = zeroPadWidth > 0
                    ? String(format: "%0\(zeroPadWidth)d", num)
                    : "\(num)"
                num += increment
                return "\(formatted)\(separator)\(line)"
            }.joined(separator: "\n")

        case .removeNumbers:
            let stripRegex = try! NSRegularExpression(pattern: #"^\s*\d+\s*[:.\-|\)\]]?\s?"#)
            return lines.map { line in
                let ns = line as NSString
                let r = NSRange(location: 0, length: ns.length)
                if let m = stripRegex.firstMatch(in: line, options: [], range: r) {
                    return ns.replacingCharacters(in: m.range, with: "")
                }
                return line
            }.joined(separator: "\n")
        }
    }

    // MARK: - 10. Educate / Straighten Quotes

    public static func transformQuotes(_ text: String, mode: QuoteTransformMode) -> String {
        switch mode {
        case .straightenQuotes:
            return text
                .replacingOccurrences(of: "\u{201C}", with: "\"")
                .replacingOccurrences(of: "\u{201D}", with: "\"")
                .replacingOccurrences(of: "\u{201E}", with: "\"")
                .replacingOccurrences(of: "\u{2018}", with: "'")
                .replacingOccurrences(of: "\u{2019}", with: "'")

        case .educateCurlyQuotes:
            var out = ""
            out.reserveCapacity(text.count)
            var prevChar: Character = " "
            for ch in text {
                if ch == "\"" {
                    let isOpen = prevChar.isWhitespace || prevChar == "(" || prevChar == "[" || prevChar == "{"
                    out.append(isOpen ? "\u{201C}" : "\u{201D}")
                } else if ch == "'" {
                    let isOpen = prevChar.isWhitespace || prevChar == "(" || prevChar == "[" || prevChar == "{"
                    out.append(isOpen ? "\u{2018}" : "\u{2019}")
                } else {
                    out.append(ch)
                }
                prevChar = ch
            }
            return out
        }
    }

    // MARK: - 11. Canonize / Replace Using Table

    public static func canonizeUsingTable(
        _ text: String,
        tableRaw: String,
        caseSensitive: Bool = false,
        wholeWords: Bool = true
    ) -> String {
        var current = text
        let rules = tableRaw.components(separatedBy: "\n").compactMap { rawLine -> (String, String)? in
            let trimmed = rawLine.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty, !trimmed.hasPrefix("#"), let eq = trimmed.firstIndex(of: "=") else {
                return nil
            }
            let key = String(trimmed[..<eq]).trimmingCharacters(in: .whitespaces)
            let val = String(trimmed[trimmed.index(after: eq)...]).trimmingCharacters(in: .whitespaces)
            return key.isEmpty ? nil : (key, val)
        }

        for (key, val) in rules {
            let escapedKey = NSRegularExpression.escapedPattern(for: key)
            let isAlnumKey = key.allSatisfy { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "-" }
            let pattern: String
            if wholeWords {
                if isAlnumKey {
                    pattern = "\\b\(escapedKey)\\b"
                } else {
                    // Symbol aliases such as `%` (ForEach-Object) and `?` (Where-Object) must only
                    // expand at PowerShell command/pipeline boundaries — never inside "100%", "$?", or URLs!
                    pattern = "(?:^|(?<=[\\|\\;\\(\\{\\s]))\(escapedKey)(?=\\s|\\{|$)"
                }
            } else {
                pattern = escapedKey
            }
            var opts: NSRegularExpression.Options = []
            if !caseSensitive { opts.insert(.caseInsensitive) }
            if let rx = try? NSRegularExpression(pattern: pattern, options: opts) {
                let ns = current as NSString
                current = rx.stringByReplacingMatches(
                    in: current,
                    options: [],
                    range: NSRange(location: 0, length: ns.length),
                    withTemplate: NSRegularExpression.escapedTemplate(for: val)
                )
            }
        }
        return current
    }

    // MARK: - 12. Process Lines Containing

    public static func processLinesContaining(
        _ text: String,
        action: MatchingLinesAction,
        pattern: String,
        useRegex: Bool = true,
        caseSensitive: Bool = false
    ) -> (updatedText: String, extractedText: String?) {
        guard !pattern.isEmpty else { return (text, nil) }
        let lines = text.components(separatedBy: "\n")
        let rxPattern = useRegex ? pattern : NSRegularExpression.escapedPattern(for: pattern)
        var opts: NSRegularExpression.Options = []
        if !caseSensitive { opts.insert(.caseInsensitive) }
        guard let rx = try? NSRegularExpression(pattern: rxPattern, options: opts) else {
            return (text, nil)
        }

        func matches(_ line: String) -> Bool {
            let ns = line as NSString
            return rx.firstMatch(in: line, options: [], range: NSRange(location: 0, length: ns.length)) != nil
        }

        switch action {
        case .deleteMatching:
            return (lines.filter { !matches($0) }.joined(separator: "\n"), nil)
        case .deleteNonMatching:
            return (lines.filter { matches($0) }.joined(separator: "\n"), nil)
        case .extractMatching:
            let matching = lines.filter { matches($0) }
            return (text, matching.isEmpty ? nil : matching.joined(separator: "\n"))
        }
    }

    // MARK: - 14. Format / Prettify PowerShell, JSON & XML

    public static func formatPrettify(
        _ text: String,
        target: FormatTarget = .auto,
        documentLanguage: SyntaxLanguage = .powershell,
        indentSpaces: Int = 4
    ) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return text }

        let resolvedTarget: FormatTarget
        switch target {
        case .auto:
            if documentLanguage == .json {
                resolvedTarget = .json
            } else if documentLanguage == .xml {
                resolvedTarget = .xml
            } else if documentLanguage == .powershell {
                resolvedTarget = .powershell
            } else if (trimmed.hasPrefix("{") && trimmed.hasSuffix("}") && trimmed.contains("\":")) ||
                      (trimmed.hasPrefix("[") && trimmed.hasSuffix("]")) {
                resolvedTarget = .json
            } else if trimmed.hasPrefix("<") && trimmed.hasSuffix(">") && !trimmed.hasPrefix("<#") {
                resolvedTarget = .xml
            } else {
                resolvedTarget = .powershell
            }
        default:
            resolvedTarget = target
        }

        switch resolvedTarget {
        case .json:
            return formatJSON(text)
        case .xml:
            return formatXML(text, indentSpaces: indentSpaces)
        case .powershell, .auto:
            return formatPowerShell(text, indentSpaces: indentSpaces)
        }
    }

    public static func formatJSON(_ text: String) -> String {
        guard let data = text.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]),
              let prettyData = try? JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]),
              let str = String(data: prettyData, encoding: .utf8) else {
            return text
        }
        return str
    }

    public static func formatXML(_ text: String, indentSpaces: Int = 2) -> String {
        guard let xmlDoc = try? XMLDocument(xmlString: text, options: [.nodePreserveAll]) else {
            return text
        }
        let data = xmlDoc.xmlData(options: [.nodePrettyPrint])
        guard let pretty = String(data: data, encoding: .utf8) else { return text }
        let spaces = String(repeating: " ", count: max(1, indentSpaces))
        return pretty.components(separatedBy: "\n").map { line in
            let leadSpaces = line.prefix(while: { $0 == " " }).count
            guard leadSpaces > 0, leadSpaces % 2 == 0 else { return line }
            let depth = leadSpaces / 2
            return String(repeating: spaces, count: depth) + line.dropFirst(leadSpaces)
        }.joined(separator: "\n")
    }

    public static func formatPowerShell(_ text: String, indentSpaces: Int = 4) -> String {
        let lines = LineEnding.normalizeToLf(text).components(separatedBy: "\n")
        let states = PowerShellEngine.computeLineStates(lines, language: .powershell)
        let indentUnit = String(repeating: " ", count: max(1, indentSpaces))
        var depth = 0
        var out: [String] = []
        out.reserveCapacity(lines.count)

        for idx in lines.indices {
            let raw = lines[idx]
            if states[idx] != .normal {
                out.append(raw)
                continue
            }
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty {
                out.append("")
                continue
            }

            let stripped = PowerShellEngine.stripStringsAndCommentsFromLine(trimmed)
            var leadingClosers = 0
            for ch in stripped {
                if ch == "}" || ch == ")" { leadingClosers += 1 }
                else if ch == " " || ch == "\t" { continue }
                else { break }
            }

            let lineIndent = max(0, depth - leadingClosers)
            out.append(String(repeating: indentUnit, count: lineIndent) + trimmed)

            var delta = 0
            for ch in stripped {
                if ch == "{" || ch == "(" { delta += 1 }
                else if ch == "}" || ch == ")" { delta -= 1 }
            }
            depth = max(0, depth + delta)
        }
        return out.joined(separator: "\n")
    }

    // MARK: - Bonus Encoding Utilities (Base64 & URL Encode/Decode for Menu Bar)

    public static func base64Encode(_ text: String) -> String {
        Data(text.utf8).base64EncodedString()
    }

    public static func base64Decode(_ text: String) -> String {
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = Data(base64Encoded: cleaned, options: [.ignoreUnknownCharacters]),
              let decoded = String(data: data, encoding: .utf8) else {
            return text
        }
        return decoded
    }

    public static func urlEncode(_ text: String) -> String {
        text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text
    }

    public static func urlDecode(_ text: String) -> String {
        text.removingPercentEncoding ?? text
    }
}
