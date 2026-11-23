package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.SyntaxLanguage

object MarkdownPreviewRenderer {

    private val LINK_REGEX = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val BOLD_STAR_REGEX = Regex("""\*\*(.+?)\*\*""")
    private val BOLD_UNDER_REGEX = Regex("""__(.+?)__""")
    private val STRIKE_REGEX = Regex("""~~(.+?)~~""")
    private val ITALIC_REGEX = Regex("""(?<!\*)\*([^*]+)\*(?!\*)""")
    private val HEAD_TAG_REGEX = Regex("""(?i)(<head[^>]*>)""")

    private const val OFFLINE_CSP_META =
        """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:;">"""

    fun canPreviewDocument(document: EditorDocument): Boolean {
        if (document.language == SyntaxLanguage.MARKDOWN || document.language == SyntaxLanguage.HTML_CSS) {
            return true
        }
        val lower = document.title.lowercase()
        return lower.endsWith(".md") ||
            lower.endsWith(".markdown") ||
            lower.endsWith(".html") ||
            lower.endsWith(".htm") ||
            lower.endsWith(".svg")
    }

    fun renderHtml(
        content: String,
        language: SyntaxLanguage,
        fileName: String = "",
        isDarkTheme: Boolean = true,
        isOledBlack: Boolean = false
    ): String {
        val lower = fileName.lowercase()
        val isRawHtml = (language == SyntaxLanguage.HTML_CSS && !lower.endsWith(".md") && !lower.endsWith(".markdown")) ||
            lower.endsWith(".html") ||
            lower.endsWith(".htm")

        if (isRawHtml) {
            return wrapRawHtml(content, isDarkTheme, isOledBlack)
        }
        val bodyHtml = convertMarkdownToHtmlBody(content)
        return buildStyledDocumentHtml(bodyHtml, isDarkTheme, isOledBlack)
    }

    fun convertMarkdownToHtmlBody(markdown: String): String {
        val lines = LineEnding.normalizeToLf(markdown).split('\n')
        val html = ArrayList<String>()
        var idx = 0

        while (idx < lines.size) {
            val line = lines[idx]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                idx++
                continue
            }

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val fence = trimmed.take(3)
                val lang = trimmed.drop(3).trim()
                idx++
                val codeLines = ArrayList<String>()
                while (idx < lines.size) {
                    if (lines[idx].trim().startsWith(fence)) {
                        idx++
                        break
                    }
                    codeLines.add(escapeHtml(lines[idx]))
                    idx++
                }
                val langBadge = if (lang.isEmpty()) "" else """<div class="code-lang">${escapeHtml(lang)}</div>"""
                html.add("""<div class="code-block">$langBadge<pre><code>${codeLines.joinToString("\n")}</code></pre></div>""")
                continue
            }

            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                html.add("<hr />")
                idx++
                continue
            }

            val heading = parseHeading(trimmed)
            if (heading != null) {
                html.add("<h${heading.first}>${renderInlineMarkdown(heading.second)}</h${heading.first}>")
                idx++
                continue
            }

            if (trimmed.contains('|') && idx + 1 < lines.size && isTableAlignmentRow(lines[idx + 1])) {
                val headers = splitPipeRow(line)
                idx += 2
                val rows = ArrayList<List<String>>()
                while (idx < lines.size) {
                    val rt = lines[idx].trim()
                    if (rt.isEmpty() || !rt.contains('|')) break
                    rows.add(splitPipeRow(lines[idx]))
                    idx++
                }
                val sb = StringBuilder("<table><thead><tr>")
                for (h in headers) sb.append("<th>").append(renderInlineMarkdown(h)).append("</th>")
                sb.append("</tr></thead><tbody>")
                for (r in rows) {
                    sb.append("<tr>")
                    for (c in headers.indices) {
                        sb.append("<td>").append(renderInlineMarkdown(r.getOrElse(c) { "" })).append("</td>")
                    }
                    sb.append("</tr>")
                }
                sb.append("</tbody></table>")
                html.add(sb.toString())
                continue
            }

            if (trimmed.startsWith(">")) {
                val parts = ArrayList<String>()
                while (idx < lines.size) {
                    val qt = lines[idx].trim()
                    if (!qt.startsWith(">")) break
                    parts.add(renderInlineMarkdown(qt.drop(1).trim()))
                    idx++
                }
                html.add("<blockquote><p>${parts.joinToString("<br />")}</p></blockquote>")
                continue
            }

            if (isUnorderedListItem(trimmed)) {
                val items = ArrayList<String>()
                while (idx < lines.size) {
                    val lt = lines[idx].trim()
                    if (!isUnorderedListItem(lt)) break
                    val body = lt.drop(2).trim()
                    when {
                        body.startsWith("[x] ", ignoreCase = true) ->
                            items.add("""<li class="task-item"><input type="checkbox" checked disabled /> ${renderInlineMarkdown(body.drop(4))}</li>""")
                        body.startsWith("[ ] ") ->
                            items.add("""<li class="task-item"><input type="checkbox" disabled /> ${renderInlineMarkdown(body.drop(4))}</li>""")
                        else ->
                            items.add("<li>${renderInlineMarkdown(body)}</li>")
                    }
                    idx++
                }
                html.add("<ul>${items.joinToString("")}</ul>")
                continue
            }

            val firstOrd = stripOrderedListPrefix(trimmed)
            if (firstOrd != null) {
                val items = ArrayList<String>()
                items.add("<li>${renderInlineMarkdown(firstOrd)}</li>")
                idx++
                while (idx < lines.size) {
                    val nextOrd = stripOrderedListPrefix(lines[idx].trim()) ?: break
                    items.add("<li>${renderInlineMarkdown(nextOrd)}</li>")
                    idx++
                }
                html.add("<ol>${items.joinToString("")}</ol>")
                continue
            }

            val para = ArrayList<String>()
            while (idx < lines.size) {
                val pt = lines[idx].trim()
                if (pt.isEmpty() || pt.startsWith("#") || pt.startsWith("```") || pt.startsWith("~~~") ||
                    pt.startsWith(">") || isUnorderedListItem(pt) || stripOrderedListPrefix(pt) != null ||
                    pt == "---" || pt == "***"
                ) {
                    break
                }
                para.add(renderInlineMarkdown(pt))
                idx++
            }
            if (para.isNotEmpty()) {
                html.add("<p>${para.joinToString(" ")}</p>")
            } else {
                idx++
            }
        }
        return html.joinToString("\n")
    }

    private fun renderInlineMarkdown(raw: String): String {
        val segments = raw.split('`')
        val out = StringBuilder()
        for ((i, seg) in segments.withIndex()) {
            if (i % 2 == 1 && i < segments.lastIndex) {
                out.append("<code>").append(escapeHtml(seg)).append("</code>")
            } else {
                var s = escapeHtml(seg)
                s = LINK_REGEX.replace(s, """<a href="$2">$1</a>""")
                s = BOLD_STAR_REGEX.replace(s, "<strong>$1</strong>")
                s = BOLD_UNDER_REGEX.replace(s, "<strong>$1</strong>")
                s = STRIKE_REGEX.replace(s, "<del>$1</del>")
                s = ITALIC_REGEX.replace(s, "<em>$1</em>")
                out.append(s)
            }
        }
        return out.toString()
    }

    private fun parseHeading(trimmed: String): Pair<Int, String>? {
        val hashes = trimmed.takeWhile { it == '#' }.length
        if (hashes !in 1..6) return null
        val rest = trimmed.drop(hashes)
        if (rest.isNotEmpty() && rest.first() != ' ') return null
        return hashes to rest.trim()
    }

    private fun isUnorderedListItem(t: String): Boolean =
        t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")

    private fun stripOrderedListPrefix(t: String): String? {
        val dot = t.indexOf('.')
        if (dot <= 0) return null
        if (!t.substring(0, dot).all { it.isDigit() }) return null
        if (dot + 1 >= t.length || t[dot + 1] != ' ') return null
        return t.substring(dot + 2).trim()
    }

    private fun isTableAlignmentRow(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('|') || !t.contains('-')) return false
        val cells = splitPipeRow(t)
        return cells.isNotEmpty() && cells.all { cell ->
            val c = cell.trim()
            c.isNotEmpty() && c.contains('-') && c.all { it == '-' || it == ':' }
        }
    }

    private fun splitPipeRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun wrapRawHtml(rawHtml: String, isDark: Boolean, isOled: Boolean): String {
        val bg = if (isOled) "#000000" else if (isDark) "#13161C" else "#FFFFFF"
        val fg = if (isDark) "#E6EAF2" else "#1B1F26"
        val style = "<style>body { background:$bg; color:$fg; font-family: sans-serif; margin:16px; }</style>"
        return if (HEAD_TAG_REGEX.containsMatchIn(rawHtml)) {
            HEAD_TAG_REGEX.replaceFirst(rawHtml, "$1\n$OFFLINE_CSP_META\n$style")
        } else {
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\" />$OFFLINE_CSP_META$style</head><body>$rawHtml</body></html>"
        }
    }

    private fun buildStyledDocumentHtml(body: String, isDark: Boolean, isOled: Boolean): String {
        val bg = if (isOled) "#000000" else if (isDark) "#13161C" else "#FFFFFF"
        val fg = if (isDark) "#E6EAF2" else "#1B1F26"
        val surface = if (isOled) "#0D1017" else if (isDark) "#1D222B" else "#F3F5FA"
        val border = if (isDark) "#2E3544" else "#DCE1EB"
        val accent = if (isDark) "#6EB5FF" else "#0969DA"
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            $OFFLINE_CSP_META
            <style>
            body { background:$bg; color:$fg; font-family: Roboto, sans-serif; font-size:14px; line-height:1.6; padding:16px 20px; margin:0; }
            h1, h2 { border-bottom:1px solid $border; padding-bottom:4px; }
            a { color:$accent; text-decoration:none; }
            code { font-family: monospace; background:$surface; padding:2px 5px; border-radius:4px; border:1px solid $border; }
            .code-block { background:$surface; border:1px solid $border; border-radius:8px; margin:10px 0; overflow-x:auto; }
            .code-lang { font-size:11px; font-weight:bold; padding:4px 10px; border-bottom:1px solid $border; opacity:0.75; }
            .code-block pre { margin:0; padding:10px 12px; overflow-x:auto; }
            .code-block code { background:transparent; border:none; padding:0; }
            blockquote { margin:10px 0; padding:4px 12px; border-left:3.5px solid $accent; background:$surface; }
            table { border-collapse:collapse; width:100%; margin:12px 0; font-size:13px; }
            th, td { border:1px solid $border; padding:6px 10px; text-align:left; }
            th { background:$surface; }
            li.task-item { list-style:none; margin-left:-18px; }
            </style>
            </head>
            <body>$body</body>
            </html>
        """.trimIndent()
    }
}
