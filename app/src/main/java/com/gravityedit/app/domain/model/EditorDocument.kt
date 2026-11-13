package com.gravityedit.app.domain.model

import androidx.compose.runtime.Immutable
import java.nio.charset.Charset
import java.util.UUID

enum class SyntaxLanguage(
    val displayName: String,
    val extensions: List<String>,
    val singleLineCommentPrefix: String?
) {
    POWERSHELL("PowerShell", listOf("ps1", "psm1", "psd1"), "#"),
    JSON("JSON", listOf("json", "jsonc"), "//"),
    XML("XML", listOf("xml", "xsd", "svg", "plist", "csproj", "props"), null),
    YAML("YAML", listOf("yaml", "yml"), "#"),
    MARKDOWN("Markdown", listOf("md", "markdown"), null),
    PYTHON("Python", listOf("py", "pyw"), "#"),
    KOTLIN("Kotlin", listOf("kt", "kts"), "//"),
    HTML_CSS("HTML / CSS", listOf("html", "htm", "css"), null),
    SHELL_BASH("Shell / Bash", listOf("sh", "bash", "zsh", "env"), "#"),
    PLAIN_TEXT("Plain Text", listOf("txt", "log", "csv", "ini", "conf"), "#");

    companion object {
        fun fromFileName(fileName: String): SyntaxLanguage {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return PLAIN_TEXT
            return entries.firstOrNull { ext in it.extensions } ?: PLAIN_TEXT
        }
    }
}

enum class DocumentEncoding(
    val displayName: String,
    val charsetName: String,
    val hasBom: Boolean = false
) {
    UTF_8("UTF-8", "UTF-8", hasBom = false),
    UTF_8_BOM("UTF-8 BOM", "UTF-8", hasBom = true),
    UTF_16LE("UTF-16LE", "UTF-16LE", hasBom = true),
    UTF_16BE("UTF-16BE", "UTF-16BE", hasBom = true),
    ISO_8859_1("ISO-8859-1", "ISO-8859-1", hasBom = false),
    WINDOWS_1252("Windows-1252", "windows-1252", hasBom = false);

    fun resolveCharset(): Charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
}

enum class LineEnding(
    val displayName: String,
    val sequence: String,
    val badge: String
) {
    LF("Unix / Googlebook OS (LF)", "\n", "LF"),
    CRLF("Windows (CRLF)", "\r\n", "CRLF"),
    CR("Classic Mac (CR)", "\r", "CR");

    companion object {
        fun detect(rawText: String): LineEnding {
            val crlfIndex = rawText.indexOf("\r\n")
            if (crlfIndex >= 0) return CRLF
            val crIndex = rawText.indexOf('\r')
            if (crIndex >= 0) return CR
            return LF
        }

        fun normalizeToLf(rawText: String): String {
            return rawText.replace("\r\n", "\n").replace('\r', '\n')
        }

        fun applyLineEnding(normalizedText: String, target: LineEnding): String {
            val lfText = normalizeToLf(normalizedText)
            return when (target) {
                LF -> lfText
                CRLF -> lfText.replace("\n", "\r\n")
                CR -> lfText.replace("\n", "\r")
            }
        }
    }
}

enum class SplitViewMode {
    NONE,
    HORIZONTAL,
    VERTICAL
}

enum class WorkspaceToolPanel {
    DISK_BROWSER,
    OPEN_DOCUMENTS,
    TEXT_FACTORY,
    FIND_REPLACE,
    DIFF_TOOL,
    CLIPPINGS,
    SCRATCHPAD,
    SETTINGS
}

@Immutable
data class EditorSnapshot(
    val content: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val timestamp: Long = System.currentTimeMillis()
)

enum class FoldKind {
    FUNCTION,
    FILTER,
    WORKFLOW,
    CLASS,
    REGION,
    COMMENT_BLOCK,
    BLOCK
}

@Immutable
data class FoldRegion(
    val id: String,
    val startLine: Int,
    val endLine: Int,
    val label: String,
    val kind: FoldKind
) {
    val lineSpan: Int get() = (endLine - startLine).coerceAtLeast(1)
}

enum class SymbolKind(val badge: String) {
    FUNCTION("fn"),
    FILTER("flt"),
    WORKFLOW("wf"),
    CLASS("cls"),
    CONFIGURATION("cfg"),
    SCRIPTBLOCK("\$sb"),
    PARAM_BLOCK("prm"),
    PIPELINE_BLOCK("pipe"),
    SECTION("#sec"),
    REGION("#reg"),
    CONFIG_VARS("var"),
    DATA_TABLE("tbl"),
    CONTROL_BLOCK("blk");

    val isCallableCommand: Boolean
        get() = this == FUNCTION || this == FILTER || this == WORKFLOW || this == CONFIGURATION
}

@Immutable
data class AstSymbol(
    val name: String,
    val kind: SymbolKind,
    val lineIndex: Int,
    val lineNumber: Int,
    val signature: String,
    val parameters: List<String> = emptyList()
)

@Immutable
data class SelectionStats(
    val hasSelection: Boolean,
    val charCount: Int,
    val wordCount: Int,
    val lineCount: Int
)

@Immutable
data class DiskBrowserNode(
    val uriString: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int = 0,
    val isExpanded: Boolean = false,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val children: List<DiskBrowserNode> = emptyList()
)

@Immutable
data class EditorDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val uriString: String? = null,
    val content: String = "",
    val savedContent: String = "",
    val isScratchpad: Boolean = false,
    val language: SyntaxLanguage = SyntaxLanguage.POWERSHELL,
    val encoding: DocumentEncoding = DocumentEncoding.UTF_8,
    val lineEnding: LineEnding = LineEnding.LF,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val scrollLineIndex: Int = 0,
    val foldedStartLines: Set<Int> = emptySet(),
    val undoStack: List<EditorSnapshot> = emptyList(),
    val redoStack: List<EditorSnapshot> = emptyList()
) {
    val isDirty: Boolean
        get() = !isScratchpad && content != savedContent

    val lines: List<String>
        get() = if (content.isEmpty()) listOf("") else content.split('\n')

    val lineCount: Int
        get() = content.count { it == '\n' } + 1

    val charCount: Int
        get() = content.length

    val wordCount: Int
        get() = WORD_REGEX.findAll(content).count()

    val safeSelectionStart: Int
        get() = minOf(selectionStart, selectionEnd).coerceIn(0, content.length)

    val safeSelectionEnd: Int
        get() = maxOf(selectionStart, selectionEnd).coerceIn(0, content.length)

    val selectedText: String
        get() = if (safeSelectionStart < safeSelectionEnd) {
            content.substring(safeSelectionStart, safeSelectionEnd)
        } else {
            ""
        }

    fun cursorLineAndColumn(): Pair<Int, Int> {
        val cursorOffset = selectionEnd.coerceIn(0, content.length)
        var line = 1
        var lastLineStart = 0
        for (i in 0 until cursorOffset) {
            if (content[i] == '\n') {
                line++
                lastLineStart = i + 1
            }
        }
        val column = (cursorOffset - lastLineStart) + 1
        return line to column
    }

    fun selectionStats(): SelectionStats {
        val start = safeSelectionStart
        val end = safeSelectionEnd
        if (start >= end) {
            return SelectionStats(
                hasSelection = false,
                charCount = 0,
                wordCount = 0,
                lineCount = 0
            )
        }
        val sub = content.substring(start, end)
        val words = WORD_REGEX.findAll(sub).count()
        val linesCount = sub.count { it == '\n' } + 1
        return SelectionStats(
            hasSelection = true,
            charCount = sub.length,
            wordCount = words,
            lineCount = linesCount
        )
    }

    fun lineOffsetRange(lineIndex: Int): IntRange {
        val docLines = lines
        if (docLines.isEmpty()) return 0..0
        val clamped = lineIndex.coerceIn(0, docLines.lastIndex)
        var startOffset = 0
        for (i in 0 until clamped) {
            startOffset += docLines[i].length + 1
        }
        val endOffset = (startOffset + docLines[clamped].length).coerceAtMost(content.length)
        return startOffset..endOffset
    }

    fun lineIndexForOffset(offset: Int): Int {
        val clamped = offset.coerceIn(0, content.length)
        var lineIdx = 0
        for (i in 0 until clamped) {
            if (content[i] == '\n') lineIdx++
        }
        return lineIdx
    }

    companion object {
        private val WORD_REGEX = Regex("""\S+""")
    }
}
