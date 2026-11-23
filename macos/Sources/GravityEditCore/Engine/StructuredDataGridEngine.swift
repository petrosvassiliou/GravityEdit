import CoreFoundation
import Foundation

public enum StructuredDataGridEngine {

    public static func canInspectAsGrid(document: EditorDocument) -> Bool {
        let lowerTitle = document.title.lowercased()
        if lowerTitle.hasSuffix(".csv") || lowerTitle.hasSuffix(".tsv") || lowerTitle.hasSuffix(".psv") {
            return true
        }
        if document.language == .json || lowerTitle.hasSuffix(".json") {
            let trimmed = document.content.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.hasPrefix("[")
        }
        return false
    }

    public static func detectDelimiter(in text: String, preferredFileName: String = "") -> Character {
        let lowerName = preferredFileName.lowercased()
        if lowerName.hasSuffix(".tsv") { return "\t" }
        if lowerName.hasSuffix(".psv") { return "|" }

        let sampleLines = LineEnding.normalizeToLf(text)
            .components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .prefix(10)

        guard !sampleLines.isEmpty else { return "," }

        let candidates: [Character] = [",", "\t", ";", "|"]
        var bestChar: Character = ","
        var bestScore = -1

        for candidate in candidates {
            let counts = sampleLines.map { countUnquotedOccurrences(of: candidate, in: $0) }
            guard let first = counts.first, first > 0 else { continue }
            let consistentCount = counts.filter { $0 == first }.count
            let score = consistentCount * 100 + first
            if score > bestScore {
                bestScore = score
                bestChar = candidate
            }
        }
        return bestChar
    }

    public static func parseTable(
        from content: String,
        language: SyntaxLanguage,
        fileName: String = ""
    ) -> DataGridTable? {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        let lowerName = fileName.lowercased()

        if language == .json || lowerName.hasSuffix(".json") || (trimmed.hasPrefix("[") && trimmed.hasSuffix("]") && !lowerName.hasSuffix(".csv")) {
            if let jsonTable = parseJsonObjectArray(trimmed) {
                return jsonTable
            }
            if language == .json || lowerName.hasSuffix(".json") {
                return nil
            }
        }

        let delimiter = detectDelimiter(in: content, preferredFileName: fileName)
        return parseDelimitedTable(from: content, delimiter: delimiter)
    }

    public static func serializeTable(_ table: DataGridTable) -> String {
        if table.isJsonArray {
            return serializeJsonArray(table)
        }
        return serializeDelimited(table)
    }

    public static func sortTable(
        _ table: DataGridTable,
        columnIndex: Int,
        ascending: Bool
    ) -> DataGridTable {
        guard columnIndex >= 0, columnIndex < table.headers.count else { return table }
        let sorted = table.rows.sorted { lhs, rhs in
            let a = columnIndex < lhs.count ? lhs[columnIndex] : ""
            let b = columnIndex < rhs.count ? rhs[columnIndex] : ""
            let cmp: ComparisonResult
            if let numA = Double(a.trimmingCharacters(in: .whitespaces)),
               let numB = Double(b.trimmingCharacters(in: .whitespaces)) {
                if numA < numB { cmp = .orderedAscending }
                else if numA > numB { cmp = .orderedDescending }
                else { cmp = .orderedSame }
            } else {
                cmp = a.localizedStandardCompare(b)
            }
            return ascending ? (cmp == .orderedAscending) : (cmp == .orderedDescending)
        }
        return DataGridTable(
            headers: table.headers,
            rows: sorted,
            delimiter: table.delimiter,
            isJsonArray: table.isJsonArray
        )
    }

    public static func filterRows(
        _ table: DataGridTable,
        query: String
    ) -> [(originalRowIndex: Int, cells: [String])] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return table.rows.enumerated().map { ($0.offset, $0.element) }
        }
        return table.rows.enumerated().compactMap { idx, cells in
            let matches = cells.contains { $0.localizedCaseInsensitiveContains(trimmed) }
            return matches ? (idx, cells) : nil
        }
    }

    public static func updateCell(
        _ table: DataGridTable,
        rowIndex: Int,
        columnIndex: Int,
        newValue: String
    ) -> DataGridTable {
        guard rowIndex >= 0, rowIndex < table.rows.count,
              columnIndex >= 0, columnIndex < table.headers.count else {
            return table
        }
        var copy = table
        while copy.rows[rowIndex].count < copy.headers.count {
            copy.rows[rowIndex].append("")
        }
        copy.rows[rowIndex][columnIndex] = newValue
        return copy
    }

    public static func appendEmptyRow(_ table: DataGridTable) -> DataGridTable {
        var copy = table
        if copy.headers.isEmpty {
            copy.headers = ["Column1", "Column2"]
        }
        copy.rows.append(Array(repeating: "", count: copy.headers.count))
        return copy
    }

    public static func deleteRow(_ table: DataGridTable, rowIndex: Int) -> DataGridTable {
        guard rowIndex >= 0, rowIndex < table.rows.count else { return table }
        var copy = table
        copy.rows.remove(at: rowIndex)
        return copy
    }

    // MARK: - RFC-4180 CSV / DSV Parser & Serializer

    private static func parseDelimitedTable(from rawText: String, delimiter: Character) -> DataGridTable {
        let normalized = LineEnding.normalizeToLf(rawText)
        guard !normalized.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return DataGridTable(headers: ["Column1", "Column2"], rows: [], delimiter: delimiter, isJsonArray: false)
        }

        var parsedRecords: [[String]] = []
        var currentRow: [String] = []
        var currentCell = ""
        var inQuotes = false

        let chars = Array(normalized)
        var i = 0
        while i < chars.count {
            let ch = chars[i]
            if inQuotes {
                if ch == "\"" {
                    if i + 1 < chars.count && chars[i + 1] == "\"" {
                        currentCell.append("\"")
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentCell.append(ch)
                }
            } else {
                if ch == "\"" && currentCell.isEmpty {
                    inQuotes = true
                } else if ch == delimiter {
                    currentRow.append(currentCell)
                    currentCell = ""
                } else if ch == "\n" {
                    currentRow.append(currentCell)
                    parsedRecords.append(currentRow)
                    currentRow = []
                    currentCell = ""
                } else {
                    currentCell.append(ch)
                }
            }
            i += 1
        }

        if !currentCell.isEmpty || !currentRow.isEmpty {
            currentRow.append(currentCell)
            parsedRecords.append(currentRow)
        }

        // Remove trailing completely empty line if file ended with '\n'
        if let last = parsedRecords.last, last.count == 1, last[0].isEmpty, parsedRecords.count > 1 {
            parsedRecords.removeLast()
        }

        guard let rawHeaders = parsedRecords.first else {
            return DataGridTable(headers: ["Column1"], rows: [], delimiter: delimiter, isJsonArray: false)
        }

        let maxCols = max(rawHeaders.count, parsedRecords.map(\.count).max() ?? rawHeaders.count)
        var headers = rawHeaders
        while headers.count < maxCols {
            headers.append("Column\(headers.count + 1)")
        }
        headers = headers.enumerated().map { idx, h in
            h.trimmingCharacters(in: .whitespaces).isEmpty ? "Column\(idx + 1)" : h
        }

        let bodyRows = parsedRecords.dropFirst().map { row -> [String] in
            if row.count == maxCols { return row }
            if row.count < maxCols { return row + Array(repeating: "", count: maxCols - row.count) }
            return Array(row.prefix(maxCols))
        }

        return DataGridTable(headers: headers, rows: bodyRows, delimiter: delimiter, isJsonArray: false)
    }

    private static func serializeDelimited(_ table: DataGridTable) -> String {
        let delimStr = String(table.delimiter)
        func encodeCell(_ val: String) -> String {
            let mustQuote = val.contains(table.delimiter) || val.contains("\"") || val.contains("\n") || val.contains("\r")
            if mustQuote {
                let escaped = val.replacingOccurrences(of: "\"", with: "\"\"")
                return "\"\(escaped)\""
            }
            return val
        }

        var lines: [String] = []
        lines.append(table.headers.map(encodeCell).joined(separator: delimStr))
        for row in table.rows {
            let padded: [String]
            if row.count < table.headers.count {
                padded = row + Array(repeating: "", count: table.headers.count - row.count)
            } else {
                padded = Array(row.prefix(table.headers.count))
            }
            lines.append(padded.map(encodeCell).joined(separator: delimStr))
        }
        return lines.joined(separator: "\n")
    }

    // MARK: - JSON Object Array Parser & Serializer

    private static func parseJsonObjectArray(_ text: String) -> DataGridTable? {
        guard let data = text.data(using: .utf8),
              let rawArray = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }
        guard !rawArray.isEmpty else {
            return DataGridTable(headers: ["id", "value"], rows: [], delimiter: ",", isJsonArray: true)
        }

        // Preserve stable column ordering using key order from the raw JSON text when possible
        let orderedKeys = extractOrderedJsonKeys(from: text, objects: rawArray)
        let rows: [[String]] = rawArray.map { dict in
            orderedKeys.map { key in
                stringifyJsonValue(dict[key])
            }
        }
        return DataGridTable(headers: orderedKeys, rows: rows, delimiter: ",", isJsonArray: true)
    }

    private static func serializeJsonArray(_ table: DataGridTable) -> String {
        var outLines: [String] = ["["]
        for (rIdx, row) in table.rows.enumerated() {
            var propLines: [String] = []
            for (cIdx, header) in table.headers.enumerated() {
                let rawCell = cIdx < row.count ? row[cIdx] : ""
                let encodedKey = jsonEscapeString(header)
                let encodedVal = encodeJsonScalarLiteral(rawCell)
                let propComma = (cIdx + 1 < table.headers.count) ? "," : ""
                propLines.append("    \"\(encodedKey)\": \(encodedVal)\(propComma)")
            }
            let objComma = (rIdx + 1 < table.rows.count) ? "," : ""
            outLines.append("  {")
            outLines.append(contentsOf: propLines)
            outLines.append("  }\(objComma)")
        }
        outLines.append("]")
        return outLines.joined(separator: "\n")
    }

    private static func extractOrderedJsonKeys(from rawJson: String, objects: [[String: Any]]) -> [String] {
        var seen: Set<String> = []
        var ordered: [String] = []

        if let keyRegex = try? NSRegularExpression(pattern: #""((?:\\.|[^"\\])*)"\s*:"#) {
            let ns = rawJson as NSString
            let matches = keyRegex.matches(in: rawJson, range: NSRange(location: 0, length: min(ns.length, 32_768)))
            let validSet = Set(objects.flatMap(\.keys))
            for m in matches {
                if let r = Range(m.range(at: 1), in: rawJson) {
                    let key = String(rawJson[r])
                    if validSet.contains(key) && seen.insert(key).inserted {
                        ordered.append(key)
                    }
                }
            }
        }

        for obj in objects {
            for key in obj.keys.sorted() where seen.insert(key).inserted {
                ordered.append(key)
            }
        }
        return ordered
    }

    private static func stringifyJsonValue(_ value: Any?) -> String {
        guard let value else { return "" }
        if value is NSNull { return "" }
        if let str = value as? String { return str }
        if let num = value as? NSNumber {
            if CFGetTypeID(num) == CFBooleanGetTypeID() {
                return num.boolValue ? "true" : "false"
            }
            return num.stringValue
        }
        if JSONSerialization.isValidJSONObject(value),
           let data = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]),
           let compact = String(data: data, encoding: .utf8) {
            return compact
        }
        return "\(value)"
    }

    private static func encodeJsonScalarLiteral(_ cell: String) -> String {
        let trimmed = cell.trimmingCharacters(in: .whitespaces)
        if trimmed == "true" || trimmed == "false" {
            return trimmed
        }
        if Int64(trimmed) != nil, !(trimmed.count > 1 && trimmed.hasPrefix("0")) {
            return trimmed
        }
        if Double(trimmed) != nil,
           trimmed.contains("."),
           !(trimmed.hasPrefix("0") && !trimmed.hasPrefix("0.")) {
            return trimmed
        }
        if (trimmed.hasPrefix("{") && trimmed.hasSuffix("}")) || (trimmed.hasPrefix("[") && trimmed.hasSuffix("]")),
           let data = trimmed.data(using: .utf8),
           (try? JSONSerialization.jsonObject(with: data)) != nil {
            return trimmed
        }
        return "\"\(jsonEscapeString(cell))\""
    }

    private static func jsonEscapeString(_ str: String) -> String {
        var out = ""
        for ch in str {
            switch ch {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default: out.append(ch)
            }
        }
        return out
    }

    private static func countUnquotedOccurrences(of delimiter: Character, in line: String) -> Int {
        var inQuotes = false
        var count = 0
        for ch in line {
            if ch == "\"" {
                inQuotes.toggle()
            } else if ch == delimiter && !inQuotes {
                count += 1
            }
        }
        return count
    }
}
