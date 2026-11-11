package com.gravityedit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravityedit.app.R
import com.gravityedit.app.domain.engine.CompletionKind
import com.gravityedit.app.domain.engine.DiagnosticSeverity
import com.gravityedit.app.domain.engine.LineLexState
import com.gravityedit.app.domain.engine.PowerShellCompletionItem
import com.gravityedit.app.domain.engine.PowerShellDiagnostic
import com.gravityedit.app.domain.engine.PowerShellEngine
import com.gravityedit.app.domain.model.AstSymbol
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.FoldRegion
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.SearchMatch
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.ui.theme.EditorSyntaxPalette
import com.gravityedit.app.ui.theme.GravityEditThemeDefaults
import com.gravityedit.app.ui.theme.JetBrainsMonoFontFamily
import com.gravityedit.app.ui.theme.editorCodeTextStyle

private data class VisibleLineEntry(
    val lineIndex: Int,
    val lineNumber: Int,
    val text: String,
    val lineStartOffset: Int,
    val lexState: LineLexState,
    val foldRegionStartingHere: FoldRegion?,
    val isFolded: Boolean
)

/**
 * High-speed virtualized `LazyColumn` code editor with synchronized line-number gutter,
 * code-folding toggles (`function`, `filter`, `workflow`, `#region`, `<# ... #>`),
 * Page Guide overlays at columns 80 and 120, Show Invisibles glyph overlay,
 * inline PowerShell auto-completion popup (Cmdlets, `$Variables`, `-Parameters`, `[Types]`),
 * PSScriptAnalyzer gutter diagnostic badges, and real-time PowerShell syntax highlighting.
 */
@Composable
fun EditorCanvas(
    document: EditorDocument,
    foldRegions: List<FoldRegion>,
    searchMatches: List<SearchMatch>,
    activeSearchMatchIndex: Int,
    fontSizeSp: Float,
    tabWidth: Int,
    softWrap: Boolean,
    showInvisibles: Boolean,
    showPageGuide80: Boolean,
    showPageGuide120: Boolean,
    enableCodeFolding: Boolean,
    isFocusedPane: Boolean,
    scrollTargetLine: Int?,
    onFocusPane: () -> Unit,
    onContentChange: (newContent: String, newSelStart: Int, newSelEnd: Int) -> Unit,
    onSelectionChange: (selStart: Int, selEnd: Int) -> Unit,
    onToggleFold: (startLine: Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollRequestId: Long = 0L,
    astSymbols: List<AstSymbol> = emptyList(),
    diagnostics: List<PowerShellDiagnostic> = emptyList(),
    onOpenDiagnostics: (() -> Unit)? = null
) {
    val palette = GravityEditThemeDefaults.syntaxPalette
    val codeStyle = remember(fontSizeSp) { editorCodeTextStyle(fontSizeSp = fontSizeSp) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Measure exact character cell width in pixels for Page Guide columns 80 & 120
    val charWidthPx = remember(codeStyle, density) {
        val measured = textMeasurer.measure(
            text = AnnotatedString("MMMMMMMMMM"),
            style = codeStyle
        )
        (measured.size.width / 10f).coerceAtLeast(1f)
    }

    val lines = remember(document.content) { document.lines }
    val lineStates = remember(lines, document.language) {
        PowerShellEngine.computeLineStates(lines, document.language)
    }

    val lineStartOffsets = remember(lines) {
        val arr = IntArray(lines.size)
        var offset = 0
        for (i in lines.indices) {
            arr[i] = offset
            offset += lines[i].length + 1
        }
        arr
    }

    val foldByStartLine = remember(foldRegions) {
        foldRegions.associateBy { it.startLine }
    }

    val diagnosticsByLineNumber = remember(diagnostics) {
        diagnostics.groupBy { it.lineNumber }
    }

    // Build visible line list skipping lines hidden inside currently collapsed FoldRegions
    val visibleEntries = remember(
        lines,
        lineStates,
        lineStartOffsets,
        foldByStartLine,
        document.foldedStartLines,
        enableCodeFolding
    ) {
        val list = ArrayList<VisibleLineEntry>(lines.size)
        var i = 0
        while (i < lines.size) {
            val fold = if (enableCodeFolding) foldByStartLine[i] else null
            val isCollapsed = fold != null && (i in document.foldedStartLines)
            list.add(
                VisibleLineEntry(
                    lineIndex = i,
                    lineNumber = i + 1,
                    text = lines[i],
                    lineStartOffset = lineStartOffsets[i],
                    lexState = lineStates.getOrElse(i) { LineLexState.NORMAL },
                    foldRegionStartingHere = fold,
                    isFolded = isCollapsed
                )
            )
            if (isCollapsed && fold != null && fold.endLine > i) {
                i = fold.endLine + 1
            } else {
                i++
            }
        }
        list
    }

    val activeLineIndex by remember(document.selectionEnd, document.content) {
        derivedStateOf { document.lineIndexForOffset(document.selectionEnd) }
    }

    val listState: LazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(scrollTargetLine, scrollRequestId, document.scrollLineIndex) {
        val target = scrollTargetLine ?: document.scrollLineIndex
        val visibleIdx = visibleEntries.indexOfFirst { it.lineIndex >= target }
        if (visibleIdx >= 0) {
            listState.animateScrollToItem((visibleIdx - 3).coerceAtLeast(0))
        }
    }

    val gutterDigits = remember(lines.size) {
        lines.size.toString().length.coerceAtLeast(3)
    }
    val gutterWidthDp = remember(gutterDigits, fontSizeSp, enableCodeFolding) {
        val base = (gutterDigits * (fontSizeSp * 0.65f)) + 20f
        (if (enableCodeFolding) base + 18f else base).dp
    }

    val activeSearchMatch = searchMatches.getOrNull(activeSearchMatchIndex)

    val lineEndingGlyph = remember(document.lineEnding) {
        when (document.lineEnding) {
            LineEnding.LF -> "¬"
            LineEnding.CRLF -> "¤"
            LineEnding.CR -> "¶"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.editorBackground)
            .border(
                width = if (isFocusedPane) 1.5.dp else 0.5.dp,
                color = if (isFocusedPane) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onFocusPane
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(
                items = visibleEntries,
                key = { it.lineIndex }
            ) { entry ->
                val isCurrentLine = entry.lineIndex == activeLineIndex
                val rowBg = if (isCurrentLine) palette.activeLineBackground else palette.editorBackground

                // Compute search match ranges intersecting this line
                val lineEndOffset = entry.lineStartOffset + entry.text.length
                val lineSearchRanges = remember(searchMatches, entry.lineStartOffset, lineEndOffset) {
                    searchMatches.mapNotNull { m ->
                        val startInLine = (m.range.first - entry.lineStartOffset).coerceAtLeast(0)
                        val endInLine = (m.range.last - entry.lineStartOffset).coerceAtMost(entry.text.length - 1)
                        if (m.range.first <= lineEndOffset && m.range.last >= entry.lineStartOffset && startInLine <= endInLine) {
                            startInLine..endInLine
                        } else {
                            null
                        }
                    }
                }
                val activeMatchInLine = remember(activeSearchMatch, entry.lineStartOffset, lineEndOffset) {
                    val m = activeSearchMatch ?: return@remember null
                    val startInLine = (m.range.first - entry.lineStartOffset).coerceAtLeast(0)
                    val endInLine = (m.range.last - entry.lineStartOffset).coerceAtMost(entry.text.length - 1)
                    if (m.range.first <= lineEndOffset && m.range.last >= entry.lineStartOffset && startInLine <= endInLine) {
                        startInLine..endInLine
                    } else {
                        null
                    }
                }

                // Compute inline PowerShell completions for the currently focused line
                val localCursorCol = if (isCurrentLine) {
                    (document.selectionEnd - entry.lineStartOffset).coerceIn(0, entry.text.length)
                } else {
                    0
                }
                val lineCompletions = remember(
                    isCurrentLine,
                    isFocusedPane,
                    document.language,
                    entry.text,
                    localCursorCol,
                    astSymbols
                ) {
                    if (isCurrentLine && isFocusedPane && document.language == SyntaxLanguage.POWERSHELL) {
                        PowerShellEngine.computeCompletions(
                            line = entry.text,
                            cursorColumn = localCursorCol,
                            documentContent = document.content,
                            astSymbols = astSymbols
                        )
                    } else {
                        emptyList()
                    }
                }

                fun acceptCompletion(item: PowerShellCompletionItem) {
                    val safeStart = item.replaceStartInLine.coerceIn(0, entry.text.length)
                    val safeEnd = item.replaceEndInLine.coerceIn(safeStart, entry.text.length)
                    val updatedLine = buildString {
                        append(entry.text, 0, safeStart)
                        append(item.insertText)
                        append(entry.text, safeEnd, entry.text.length)
                    }
                    val newLocalCursor = safeStart + item.insertText.length
                    val newDocContent = buildString(
                        document.content.length - entry.text.length + updatedLine.length
                    ) {
                        append(document.content, 0, entry.lineStartOffset)
                        append(updatedLine)
                        val afterLine = (entry.lineStartOffset + entry.text.length).coerceAtMost(document.content.length)
                        append(document.content, afterLine, document.content.length)
                    }
                    val globalCursor = (entry.lineStartOffset + newLocalCursor).coerceIn(0, newDocContent.length)
                    onContentChange(newDocContent, globalCursor, globalCursor)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Synchronized Line Number, Diagnostic Indicator & Code Folding Gutter
                        val lineDiagnostics = diagnosticsByLineNumber[entry.lineNumber].orEmpty()
                        EditorLineGutterCell(
                            lineNumber = entry.lineNumber,
                            isCurrentLine = isCurrentLine,
                            foldRegion = entry.foldRegionStartingHere,
                            isFolded = entry.isFolded,
                            enableCodeFolding = enableCodeFolding,
                            gutterWidth = gutterWidthDp,
                            fontSizeSp = fontSizeSp,
                            palette = palette,
                            highestDiagnosticSeverity = lineDiagnostics.minByOrNull { it.severity.ordinal }?.severity,
                            onSelectLine = {
                                onFocusPane()
                                onSelectionChange(
                                    entry.lineStartOffset,
                                    entry.lineStartOffset + entry.text.length
                                )
                                if (lineDiagnostics.isNotEmpty()) {
                                    onOpenDiagnostics?.invoke()
                                }
                            },
                            onToggleFold = {
                                entry.foldRegionStartingHere?.let { onToggleFold(it.startLine) }
                            }
                        )

                        // Vertical Gutter Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height((fontSizeSp * 1.52f).dp)
                                .background(palette.pageGuideColor.copy(alpha = 0.55f))
                        )

                        // Editable Syntax-Highlighted Code Line + Page Guides + Fold Pill
                        val lineBoxModifier = if (softWrap) {
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        } else {
                            Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                                .padding(horizontal = 8.dp)
                        }

                        Row(
                            modifier = lineBoxModifier.drawBehind {
                                if (showPageGuide80) {
                                    val x80 = charWidthPx * 80f
                                    drawLine(
                                        color = palette.pageGuideColor,
                                        start = Offset(x80, 0f),
                                        end = Offset(x80, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                if (showPageGuide120) {
                                    val x120 = charWidthPx * 120f
                                    drawLine(
                                        color = palette.pageGuideColor,
                                        start = Offset(x120, 0f),
                                        end = Offset(x120, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val visibleEntryIndex = remember(visibleEntries, entry.lineIndex) {
                                visibleEntries.indexOfFirst { it.lineIndex == entry.lineIndex }
                            }
                            VirtualizedEditableCodeLine(
                                entry = entry,
                                document = document,
                                isCurrentLine = isCurrentLine && isFocusedPane,
                                palette = palette,
                                codeStyle = codeStyle,
                                tabWidth = tabWidth,
                                softWrap = softWrap,
                                showInvisibles = showInvisibles,
                                lineSearchRanges = lineSearchRanges,
                                activeMatchInLine = activeMatchInLine,
                                topCompletionItem = lineCompletions.firstOrNull(),
                                onAcceptTopCompletion = { topItem ->
                                    acceptCompletion(topItem)
                                },
                                onSelectionInLineChanged = { localStart, localEnd ->
                                    onFocusPane()
                                    val minOff = (entry.lineStartOffset + minOf(localStart, localEnd)).coerceIn(
                                        entry.lineStartOffset,
                                        entry.lineStartOffset + entry.text.length
                                    )
                                    val maxOff = (entry.lineStartOffset + maxOf(localStart, localEnd)).coerceIn(
                                        minOff,
                                        entry.lineStartOffset + entry.text.length
                                    )
                                    onSelectionChange(minOff, maxOff)
                                },
                                onNavigateVerticalLine = { deltaVisibleRow, preferredCol ->
                                    val targetVisibleIdx = visibleEntryIndex + deltaVisibleRow
                                    if (targetVisibleIdx in visibleEntries.indices) {
                                        onFocusPane()
                                        val targetEntry = visibleEntries[targetVisibleIdx]
                                        val clampedCol = preferredCol.coerceIn(0, targetEntry.text.length)
                                        val targetOffset = targetEntry.lineStartOffset + clampedCol
                                        onSelectionChange(targetOffset, targetOffset)
                                    }
                                },
                                onLineTextChanged = { updatedLineText, newLocalCursor ->
                                    val newDocContent = buildString(
                                        document.content.length - entry.text.length + updatedLineText.length
                                    ) {
                                        append(document.content, 0, entry.lineStartOffset)
                                        append(updatedLineText)
                                        val afterLine = (entry.lineStartOffset + entry.text.length).coerceAtMost(document.content.length)
                                        append(document.content, afterLine, document.content.length)
                                    }
                                    val globalCursor = (entry.lineStartOffset + newLocalCursor).coerceIn(0, newDocContent.length)
                                    onContentChange(newDocContent, globalCursor, globalCursor)
                                },
                                onMergeWithPreviousLine = {
                                    if (entry.lineIndex > 0) {
                                        val prevLineStart = lineStartOffsets[entry.lineIndex - 1]
                                        val prevLineLen = lines[entry.lineIndex - 1].length
                                        val newlineCharIndex = entry.lineStartOffset - 1
                                        if (newlineCharIndex in document.content.indices) {
                                            val merged = document.content.removeRange(newlineCharIndex, newlineCharIndex + 1)
                                            val targetCursor = prevLineStart + prevLineLen
                                            onContentChange(merged, targetCursor, targetCursor)
                                        }
                                    }
                                }
                            )

                            if (showInvisibles) {
                                Text(
                                    text = lineEndingGlyph,
                                    style = codeStyle.copy(color = palette.invisibleGlyphColor),
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }

                            if (entry.isFolded && entry.foldRegionStartingHere != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                FoldedRegionPill(
                                    foldRegion = entry.foldRegionStartingHere,
                                    palette = palette,
                                    fontSizeSp = fontSizeSp,
                                    onUnfold = { onToggleFold(entry.foldRegionStartingHere.startLine) }
                                )
                            }
                        }
                    }

                    // Inline PowerShell Auto-Completion Popup Bar below active line
                    if (lineCompletions.isNotEmpty()) {
                        PowerShellInlineCompletionPopup(
                            completions = lineCompletions,
                            gutterWidth = gutterWidthDp,
                            palette = palette,
                            onSelectCompletion = { acceptCompletion(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerShellInlineCompletionPopup(
    completions: List<PowerShellCompletionItem>,
    gutterWidth: androidx.compose.ui.unit.Dp,
    palette: EditorSyntaxPalette,
    onSelectCompletion: (PowerShellCompletionItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.activeLineBackground)
            .padding(start = gutterWidth + 8.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(completions, key = { it.label }) { item ->
                        val badgeColor = when (item.kind) {
                            CompletionKind.CMDLET -> palette.cmdlet
                            CompletionKind.VARIABLE -> palette.variableNormal
                            CompletionKind.PARAMETER -> palette.parameter
                            CompletionKind.TYPE_ACCELERATOR -> palette.typeAttribute
                            CompletionKind.FUNCTION -> palette.keyword
                            CompletionKind.KEYWORD -> palette.keyword
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.clickable { onSelectCompletion(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = item.kind.badge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                                Text(
                                    text = item.label,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.editorForeground
                                )
                            }
                        }
                    }
                }
                val topDetail = completions.firstOrNull()?.detail
                if (!topDetail.isNullOrBlank()) {
                    Text(
                        text = "⇥ Tab: ${completions.first().label} — $topDetail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorLineGutterCell(
    lineNumber: Int,
    isCurrentLine: Boolean,
    foldRegion: FoldRegion?,
    isFolded: Boolean,
    enableCodeFolding: Boolean,
    gutterWidth: androidx.compose.ui.unit.Dp,
    fontSizeSp: Float,
    palette: EditorSyntaxPalette,
    highestDiagnosticSeverity: DiagnosticSeverity?,
    onSelectLine: () -> Unit,
    onToggleFold: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(gutterWidth)
            .background(palette.gutterBackground)
            .padding(start = 4.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (highestDiagnosticSeverity != null) {
            val dotColor = when (highestDiagnosticSeverity) {
                DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
                DiagnosticSeverity.WARNING -> palette.operatorToken
                DiagnosticSeverity.INFO -> palette.cmdlet
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, RoundedCornerShape(3.dp))
                    .clickable(onClick = onSelectLine)
            )
            Spacer(modifier = Modifier.width(3.dp))
        }

        Text(
            text = lineNumber.toString(),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = (fontSizeSp * 0.86f).sp,
            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentLine) palette.gutterActiveLineText else palette.gutterText,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelectLine)
        )

        if (enableCodeFolding) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (foldRegion != null) {
                            Modifier.clickable(onClick = onToggleFold)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (foldRegion != null) {
                    Icon(
                        imageVector = if (isFolded) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.code_folding),
                        tint = if (isFolded) MaterialTheme.colorScheme.primary else palette.gutterText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualizedEditableCodeLine(
    entry: VisibleLineEntry,
    document: EditorDocument,
    isCurrentLine: Boolean,
    palette: EditorSyntaxPalette,
    codeStyle: androidx.compose.ui.text.TextStyle,
    tabWidth: Int,
    softWrap: Boolean,
    showInvisibles: Boolean,
    lineSearchRanges: List<IntRange>,
    activeMatchInLine: IntRange?,
    topCompletionItem: PowerShellCompletionItem?,
    onAcceptTopCompletion: (PowerShellCompletionItem) -> Unit,
    onSelectionInLineChanged: (localStart: Int, localEnd: Int) -> Unit,
    onNavigateVerticalLine: (deltaVisibleRow: Int, preferredCol: Int) -> Unit,
    onLineTextChanged: (updatedText: String, newLocalCursor: Int) -> Unit,
    onMergeWithPreviousLine: () -> Unit
) {
    val highlightedLine = remember(
        entry.text,
        document.language,
        palette,
        entry.lexState,
        lineSearchRanges,
        activeMatchInLine,
        showInvisibles
    ) {
        PowerShellEngine.highlightLine(
            line = entry.text,
            language = document.language,
            palette = palette,
            lineLexState = entry.lexState,
            searchRanges = lineSearchRanges,
            activeMatchRange = activeMatchInLine,
            showInvisibles = showInvisibles
        )
    }

    val syntaxVisualTransformation = remember(highlightedLine) {
        VisualTransformation { _ ->
            TransformedText(highlightedLine, OffsetMapping.Identity)
        }
    }

    val localCursorStart = remember(document.selectionStart, entry.lineStartOffset, entry.text.length, isCurrentLine) {
        if (isCurrentLine) {
            (document.selectionStart - entry.lineStartOffset).coerceIn(0, entry.text.length)
        } else {
            0
        }
    }
    val localCursorEnd = remember(document.selectionEnd, entry.lineStartOffset, entry.text.length, isCurrentLine) {
        if (isCurrentLine) {
            (document.selectionEnd - entry.lineStartOffset).coerceIn(0, entry.text.length)
        } else {
            0
        }
    }

    var textFieldValue by remember(entry.text, localCursorStart, localCursorEnd) {
        mutableStateOf(
            TextFieldValue(
                text = entry.text,
                selection = TextRange(localCursorStart, localCursorEnd)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var lastRequestedLine by remember { mutableIntStateOf(-1) }

    LaunchedEffect(isCurrentLine) {
        if (isCurrentLine && !hasFocus && lastRequestedLine != entry.lineIndex) {
            lastRequestedLine = entry.lineIndex
            runCatching { focusRequester.requestFocus() }
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val oldText = textFieldValue.text
            val previousSelection = textFieldValue.selection
            textFieldValue = newValue
            if (newValue.text != oldText) {
                // Auto-indent when user presses Enter on soft/hardware keyboard to create a new line
                if (newValue.text.length == oldText.length + 1 && newValue.text.contains('\n')) {
                    val nlIndex = newValue.text.indexOf('\n')
                    val currentIndent = oldText.takeWhile { it == ' ' || it == '\t' }
                    val beforeTrimmed = oldText.substring(0, nlIndex).trimEnd()
                    val extraIndent = if (beforeTrimmed.endsWith("{") || beforeTrimmed.endsWith("(")) {
                        " ".repeat(tabWidth.coerceIn(2, 8))
                    } else {
                        ""
                    }
                    val indentedText = newValue.text.substring(0, nlIndex + 1) +
                        currentIndent + extraIndent +
                        newValue.text.substring(nlIndex + 1)
                    val newCursor = nlIndex + 1 + currentIndent.length + extraIndent.length
                    onLineTextChanged(indentedText, newCursor)
                } else {
                    onLineTextChanged(newValue.text, newValue.selection.end)
                }
            } else if (newValue.selection != previousSelection) {
                onSelectionInLineChanged(newValue.selection.start, newValue.selection.end)
            }
        },
        textStyle = codeStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = false,
        visualTransformation = syntaxVisualTransformation,
        modifier = Modifier
            .then(if (softWrap) Modifier.fillMaxWidth() else Modifier.width(2200.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                val newlyFocused = focusState.isFocused
                if (newlyFocused && !hasFocus) {
                    onSelectionInLineChanged(textFieldValue.selection.start, textFieldValue.selection.end)
                }
                hasFocus = newlyFocused
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val hasCmdOrAlt = event.isCtrlPressed || event.isMetaPressed || event.isAltPressed

                when {
                    !hasCmdOrAlt && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                        val selMin = textFieldValue.selection.min.coerceIn(0, textFieldValue.text.length)
                        val selMax = textFieldValue.selection.max.coerceIn(selMin, textFieldValue.text.length)
                        val before = textFieldValue.text.substring(0, selMin)
                        val after = textFieldValue.text.substring(selMax)
                        val currentIndent = before.takeWhile { it == ' ' || it == '\t' }
                        val extraIndent = if (before.trimEnd().endsWith("{") || before.trimEnd().endsWith("(")) {
                            " ".repeat(tabWidth.coerceIn(2, 8))
                        } else {
                            ""
                        }
                        val inserted = "\n$currentIndent$extraIndent"
                        onLineTextChanged(before + inserted + after, before.length + inserted.length)
                        true
                    }
                    !hasCmdOrAlt && event.key == Key.Tab -> {
                        if (event.isShiftPressed) {
                            val txt = textFieldValue.text
                            val removeCount = when {
                                txt.startsWith("\t") -> 1
                                else -> txt.takeWhile { it == ' ' }.length.coerceAtMost(tabWidth.coerceIn(1, 8))
                            }
                            if (removeCount > 0) {
                                val nextCursor = (textFieldValue.selection.end - removeCount).coerceAtLeast(0)
                                onLineTextChanged(txt.substring(removeCount), nextCursor)
                            }
                            true
                        } else if (topCompletionItem != null) {
                            onAcceptTopCompletion(topCompletionItem)
                            true
                        } else {
                            val indentStr = " ".repeat(tabWidth.coerceIn(1, 8))
                            val selMin = textFieldValue.selection.min.coerceIn(0, textFieldValue.text.length)
                            val selMax = textFieldValue.selection.max.coerceIn(selMin, textFieldValue.text.length)
                            val updated = textFieldValue.text.substring(0, selMin) +
                                indentStr +
                                textFieldValue.text.substring(selMax)
                            val nextCursor = selMin + indentStr.length
                            onLineTextChanged(updated, nextCursor)
                            true
                        }
                    }
                    !hasCmdOrAlt && !event.isShiftPressed && event.key == Key.DirectionUp -> {
                        onNavigateVerticalLine(-1, textFieldValue.selection.end)
                        true
                    }
                    !hasCmdOrAlt && !event.isShiftPressed && event.key == Key.DirectionDown -> {
                        onNavigateVerticalLine(1, textFieldValue.selection.end)
                        true
                    }
                    !hasCmdOrAlt &&
                        event.key == Key.Backspace &&
                        textFieldValue.selection.collapsed &&
                        textFieldValue.selection.start == 0 -> {
                        onMergeWithPreviousLine()
                        true
                    }
                    else -> false
                }
            }
    )
}

@Composable
private fun FoldedRegionPill(
    foldRegion: FoldRegion,
    palette: EditorSyntaxPalette,
    fontSizeSp: Float,
    onUnfold: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        ),
        modifier = Modifier.clickable(onClick = onUnfold)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "${foldRegion.label} (${foldRegion.lineSpan} lines)",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = (fontSizeSp * 0.80f).sp,
                fontWeight = FontWeight.Medium,
                color = palette.editorForeground
            )
        }
    }
}

private val POWERSHELL_AUX_SYMBOLS: List<String> = listOf(
    "$", "|", "{", "}", "[", "]", "(", ")", "-", "_", "=", "\"", "'", "\\", "#", "@", ";"
)

/**
 * Sticky PowerShell Auxiliary Symbol Row for Android Mobile Mode (and touch tablets):
 * Docked directly above the soft keyboard (`Modifier.imePadding()`) providing one-tap
 * insertion for PowerShell-critical tokens plus quick Undo, Redo, Tab/Indent, and Unindent buttons.
 */
@Composable
fun PowerShellAuxiliarySymbolRow(
    canUndo: Boolean,
    canRedo: Boolean,
    onInsertToken: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onIndent: () -> Unit,
    onUnindent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auxRowDescription = stringResource(R.string.aux_row_description)
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .semantics { contentDescription = auxRowDescription }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Action Controls: Undo, Redo, Indent, Unindent
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.action_undo),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.action_redo),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onIndent,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatIndentIncrease,
                        contentDescription = stringResource(R.string.action_indent),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onUnindent,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatIndentDecrease,
                        contentDescription = stringResource(R.string.action_unindent),
                        modifier = Modifier.size(18.dp)
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight(0.65f)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Scrollable PowerShell Symbol Keys
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(POWERSHELL_AUX_SYMBOLS) { symbol ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { onInsertToken(symbol) }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = symbol,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
