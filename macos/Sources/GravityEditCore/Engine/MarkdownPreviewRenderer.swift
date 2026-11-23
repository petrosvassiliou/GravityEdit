import Foundation

public enum MarkdownPreviewRenderer {

    public static func canPreviewDocument(_ document: EditorDocument) -> Bool {
        if document.language == .markdown || document.language == .htmlCss {
            return true
        }
        let lower = document.title.lowercased()
        return lower.hasSuffix(".md")
            || lower.hasSuffix(".markdown")
            || lower.hasSuffix(".html")
            || lower.hasSuffix(".htm")
            || lower.hasSuffix(".svg")
    }

    /// Renders either GitHub-Flavored Markdown or HTML/CSS source into a self-contained, CSP-hardened HTML5 document.
    public static func renderHTML(
        from content: String,
        language: SyntaxLanguage,
        fileName: String = "",
        isDarkTheme: Bool = true,
        isOledBlack: Bool = false
    ) -> String {
        let lowerName = fileName.lowercased()
        let isRawHTML = (language == .htmlCss && !lowerName.hasSuffix(".md") && !lowerName.hasSuffix(".markdown"))
            || lowerName.hasSuffix(".html")
            || lowerName.hasSuffix(".htm")

        if isRawHTML {
            return wrapRawHTMLWithOfflineCSP(content, isDarkTheme: isDarkTheme, isOledBlack: isOledBlack)
        }

        let bodyHTML = convertMarkdownToHTMLBody(content)
        return buildStyledDocumentHTML(bodyHTML: bodyHTML, isDarkTheme: isDarkTheme, isOledBlack: isOledBlack)
    }

    // MARK: - GFM Markdown -> HTML5 Body Converter

    public static func convertMarkdownToHTMLBody(_ markdown: String) -> String {
        let lines = LineEnding.normalizeToLf(markdown).components(separatedBy: "\n")
        var html: [String] = []
        var idx = 0

        while idx < lines.count {
            let line = lines[idx]
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            // 1. Empty line
            if trimmed.isEmpty {
                idx += 1
                continue
            }

            // 2. Fenced code block (``` or ~~~)
            if trimmed.hasPrefix("```") || trimmed.hasPrefix("~~~") {
                let fence = String(trimmed.prefix(3))
                let lang = String(trimmed.dropFirst(3)).trimmingCharacters(in: .whitespaces)
                idx += 1
                var codeLines: [String] = []
                while idx < lines.count {
                    if lines[idx].trimmingCharacters(in: .whitespaces).hasPrefix(fence) {
                        idx += 1
                        break
                    }
                    codeLines.append(escapeHTML(lines[idx]))
                    idx += 1
                }
                let langBadge = lang.isEmpty ? "" : "<div class=\"code-lang\">\(escapeHTML(lang))</div>"
                html.append("<div class=\"code-block\">\(langBadge)<pre><code>\(codeLines.joined(separator: "\n"))</code></pre></div>")
                continue
            }

            // 3. Horizontal rule
            if trimmed == "---" || trimmed == "***" || trimmed == "___" {
                html.append("<hr />")
                idx += 1
                continue
            }

            // 4. ATX Headings (# .. ######)
            if let heading = parseHeading(trimmed) {
                html.append("<h\(heading.level)>\(renderInlineMarkdown(heading.text))</h\(heading.level)>")
                idx += 1
                continue
            }

            // 5. GFM Pipe Table
            if trimmed.contains("|"),
               idx + 1 < lines.count,
               isTableAlignmentRow(lines[idx + 1]) {
                let headerCells = splitPipeRow(line)
                let alignments = parseTableAlignments(lines[idx + 1], count: headerCells.count)
                idx += 2
                var bodyRows: [[String]] = []
                while idx < lines.count {
                    let rowTrim = lines[idx].trimmingCharacters(in: .whitespaces)
                    if rowTrim.isEmpty || !rowTrim.contains("|") { break }
                    bodyRows.append(splitPipeRow(lines[idx]))
                    idx += 1
                }
                var tbl = "<table><thead><tr>"
                for (c, hCell) in headerCells.enumerated() {
                    let alignAttr = c < alignments.count && !alignments[c].isEmpty ? " style=\"text-align:\(alignments[c])\"" : ""
                    tbl += "<th\(alignAttr)>\(renderInlineMarkdown(hCell))</th>"
                }
                tbl += "</tr></thead><tbody>"
                for row in bodyRows {
                    tbl += "<tr>"
                    for c in 0..<headerCells.count {
                        let cellVal = c < row.count ? row[c] : ""
                        let alignAttr = c < alignments.count && !alignments[c].isEmpty ? " style=\"text-align:\(alignments[c])\"" : ""
                        tbl += "<td\(alignAttr)>\(renderInlineMarkdown(cellVal))</td>"
                    }
                    tbl += "</tr>"
                }
                tbl += "</tbody></table>"
                html.append(tbl)
                continue
            }

            // 6. Blockquote (`> ...`)
            if trimmed.hasPrefix(">") {
                var quoteParts: [String] = []
                while idx < lines.count {
                    let qt = lines[idx].trimmingCharacters(in: .whitespaces)
                    guard qt.hasPrefix(">") else { break }
                    let inner = String(qt.dropFirst()).trimmingCharacters(in: .whitespaces)
                    quoteParts.append(renderInlineMarkdown(inner))
                    idx += 1
                }
                html.append("<blockquote><p>\(quoteParts.joined(separator: "<br />"))</p></blockquote>")
                continue
            }

            // 7. Unordered / Task List (`- `, `* `, `+ `)
            if isUnorderedListItem(trimmed) {
                var items: [String] = []
                while idx < lines.count {
                    let lt = lines[idx].trimmingCharacters(in: .whitespaces)
                    guard isUnorderedListItem(lt) else { break }
                    let body = String(lt.dropFirst(2)).trimmingCharacters(in: .whitespaces)
                    if body.hasPrefix("[x] ") || body.hasPrefix("[X] ") {
                        let taskText = String(body.dropFirst(4))
                        items.append("<li class=\"task-item\"><input type=\"checkbox\" checked disabled /> \(renderInlineMarkdown(taskText))</li>")
                    } else if body.hasPrefix("[ ] ") {
                        let taskText = String(body.dropFirst(4))
                        items.append("<li class=\"task-item\"><input type=\"checkbox\" disabled /> \(renderInlineMarkdown(taskText))</li>")
                    } else {
                        items.append("<li>\(renderInlineMarkdown(body))</li>")
                    }
                    idx += 1
                }
                html.append("<ul>\(items.joined())</ul>")
                continue
            }

            // 8. Ordered List (`1. `, `2. `)
            if let firstOrdered = stripOrderedListPrefix(trimmed) {
                var items: [String] = ["<li>\(renderInlineMarkdown(firstOrdered))</li>"]
                idx += 1
                while idx < lines.count {
                    let lt = lines[idx].trimmingCharacters(in: .whitespaces)
                    guard let nextItem = stripOrderedListPrefix(lt) else { break }
                    items.append("<li>\(renderInlineMarkdown(nextItem))</li>")
                    idx += 1
                }
                html.append("<ol>\(items.joined())</ol>")
                continue
            }

            // 9. Standard Paragraph
            var paraLines: [String] = []
            while idx < lines.count {
                let pt = lines[idx].trimmingCharacters(in: .whitespaces)
                if pt.isEmpty
                    || pt.hasPrefix("#")
                    || pt.hasPrefix("```")
                    || pt.hasPrefix("~~~")
                    || pt.hasPrefix(">")
                    || isUnorderedListItem(pt)
                    || stripOrderedListPrefix(pt) != nil
                    || pt == "---" || pt == "***" {
                    break
                }
                paraLines.append(renderInlineMarkdown(pt))
                idx += 1
            }
            if !paraLines.isEmpty {
                html.append("<p>\(paraLines.joined(separator: " "))</p>")
            } else {
                idx += 1
            }
        }

        return html.joined(separator: "\n")
    }

    // MARK: - Inline Markdown Formatting

    private static func renderInlineMarkdown(_ raw: String) -> String {
        // Split by inline backtick spans first so code spans aren't mangled by bold/italic regexes
        var result = ""
        let parts = raw.components(separatedBy: "`")
        for (idx, segment) in parts.enumerated() {
            if idx % 2 == 1 && idx < parts.count - 1 {
                result += "<code>\(escapeHTML(segment))</code>"
            } else {
                var escaped = escapeHTML(segment)
                escaped = applyRegex(escaped, pattern: #"\[([^\]]+)\]\(([^)]+)\)"#, template: "<a href=\"$2\">$1</a>")
                escaped = applyRegex(escaped, pattern: #"\*\*(.+?)\*\*"#, template: "<strong>$1</strong>")
                escaped = applyRegex(escaped, pattern: #"__(.+?)__"#, template: "<strong>$1</strong>")
                escaped = applyRegex(escaped, pattern: #"~~(.+?)~~"#, template: "<del>$1</del>")
                escaped = applyRegex(escaped, pattern: #"(?<!\*)\*([^*]+)\*(?!\*)"#, template: "<em>$1</em>")
                result += escaped
            }
        }
        return result
    }

    private static func parseHeading(_ trimmed: String) -> (level: Int, text: String)? {
        var hashes = 0
        for ch in trimmed {
            if ch == "#" { hashes += 1 }
            else { break }
        }
        guard (1...6).contains(hashes) else { return nil }
        let rest = String(trimmed.dropFirst(hashes))
        guard rest.first == " " || rest.isEmpty else { return nil }
        return (hashes, rest.trimmingCharacters(in: .whitespaces))
    }

    private static func isUnorderedListItem(_ trimmed: String) -> Bool {
        trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") || trimmed.hasPrefix("+ ")
    }

    private static func stripOrderedListPrefix(_ trimmed: String) -> String? {
        guard let dotIdx = trimmed.firstIndex(of: ".") else { return nil }
        let prefix = trimmed[..<dotIdx]
        guard !prefix.isEmpty, prefix.allSatisfy(\.isNumber) else { return nil }
        let afterDot = trimmed.index(after: dotIdx)
        guard afterDot < trimmed.endIndex, trimmed[afterDot] == " " else { return nil }
        return String(trimmed[trimmed.index(after: afterDot)...]).trimmingCharacters(in: .whitespaces)
    }

    private static func isTableAlignmentRow(_ line: String) -> Bool {
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        guard trimmed.contains("|") && trimmed.contains("-") else { return false }
        let cells = splitPipeRow(trimmed)
        guard !cells.isEmpty else { return false }
        return cells.allSatisfy { cell in
            let c = cell.trimmingCharacters(in: .whitespaces)
            guard !c.isEmpty else { return false }
            return c.allSatisfy { $0 == "-" || $0 == ":" } && c.contains("-")
        }
    }

    private static func splitPipeRow(_ line: String) -> [String] {
        var s = line.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("|") { s.removeFirst() }
        if s.hasSuffix("|") { s.removeLast() }
        return s.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }
    }

    private static func parseTableAlignments(_ line: String, count: Int) -> [String] {
        let raw = splitPipeRow(line)
        return (0..<count).map { idx in
            guard idx < raw.count else { return "" }
            let c = raw[idx].trimmingCharacters(in: .whitespaces)
            if c.hasPrefix(":") && c.hasSuffix(":") { return "center" }
            if c.hasSuffix(":") { return "right" }
            if c.hasPrefix(":") { return "left" }
            return ""
        }
    }

    private static func escapeHTML(_ text: String) -> String {
        text
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }

    private static func applyRegex(_ input: String, pattern: String, template: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return input }
        let range = NSRange(location: 0, length: (input as NSString).length)
        return regex.stringByReplacingMatches(in: input, range: range, withTemplate: template)
    }

    // MARK: - Stylesheet & CSP Templates

    private static let offlineCSPMeta = #"<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:;">"#

    private static func wrapRawHTMLWithOfflineCSP(_ rawHTML: String, isDarkTheme: Bool, isOledBlack: Bool) -> String {
        let bg = isOledBlack ? "#000000" : (isDarkTheme ? "#14161B" : "#FFFFFF")
        let fg = isDarkTheme ? "#E8ECF4" : "#1B1F26"
        let baseStyle = "<style>:root { color-scheme: \(isDarkTheme ? "dark" : "light"); } body { background: \(bg); color: \(fg); font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 16px; }</style>"

        if rawHTML.localizedCaseInsensitiveContains("<head") {
            return applyRegex(rawHTML, pattern: #"(?i)(<head[^>]*>)"#, template: "$1\n\(offlineCSPMeta)\n\(baseStyle)")
        }
        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8" />
        \(offlineCSPMeta)
        \(baseStyle)
        </head>
        <body>
        \(rawHTML)
        </body>
        </html>
        """
    }

    private static func buildStyledDocumentHTML(bodyHTML: String, isDarkTheme: Bool, isOledBlack: Bool) -> String {
        let bg = isOledBlack ? "#000000" : (isDarkTheme ? "#14161B" : "#FFFFFF")
        let fg = isDarkTheme ? "#E6EAF2" : "#1C2026"
        let muted = isDarkTheme ? "#94A0B8" : "#5A6577"
        let surface = isOledBlack ? "#0D1017" : (isDarkTheme ? "#1D212A" : "#F4F6FA")
        let border = isDarkTheme ? "#2D3442" : "#DCE1EB"
        let accent = isDarkTheme ? "#58A6FF" : "#0969DA"

        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8" />
        \(offlineCSPMeta)
        <style>
        :root { color-scheme: \(isDarkTheme ? "dark" : "light"); }
        body {
            background: \(bg);
            color: \(fg);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            font-size: 14px;
            line-height: 1.62;
            padding: 20px 26px;
            margin: 0;
            word-wrap: break-word;
        }
        h1, h2, h3, h4, h5, h6 {
            margin-top: 1.2em;
            margin-bottom: 0.45em;
            font-weight: 650;
            line-height: 1.25;
        }
        h1 { font-size: 1.75em; padding-bottom: 0.25em; border-bottom: 1px solid \(border); }
        h2 { font-size: 1.38em; padding-bottom: 0.2em; border-bottom: 1px solid \(border); }
        h3 { font-size: 1.15em; }
        a { color: \(accent); text-decoration: none; }
        code {
            font-family: "SF Mono", Menlo, Consolas, monospace;
            font-size: 0.88em;
            background: \(surface);
            padding: 2px 6px;
            border-radius: 5px;
            border: 1px solid \(border);
        }
        .code-block {
            background: \(surface);
            border: 1px solid \(border);
            border-radius: 8px;
            margin: 12px 0;
            overflow-x: auto;
        }
        .code-lang {
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            color: \(muted);
            padding: 5px 12px;
            border-bottom: 1px solid \(border);
        }
        .code-block pre { margin: 0; padding: 12px 14px; overflow-x: auto; }
        .code-block code { background: transparent; border: none; padding: 0; }
        blockquote {
            margin: 12px 0;
            padding: 4px 14px;
            border-left: 3.5px solid \(accent);
            color: \(muted);
            background: \(surface);
            border-radius: 0 6px 6px 0;
        }
        table {
            border-collapse: collapse;
            width: 100%;
            margin: 14px 0;
            font-size: 13px;
        }
        th, td {
            border: 1px solid \(border);
            padding: 7px 11px;
            text-align: left;
        }
        th { background: \(surface); font-weight: 600; }
        hr { border: none; border-top: 1px solid \(border); margin: 18px 0; }
        ul, ol { padding-left: 22px; }
        li.task-item { list-style: none; margin-left: -18px; }
        </style>
        </head>
        <body>
        \(bodyHTML)
        </body>
        </html>
        """
    }
}
