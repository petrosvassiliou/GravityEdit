package com.gravityedit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gravityedit.app.R
import com.gravityedit.app.domain.engine.AppReleaseInfo
import com.gravityedit.app.domain.engine.AppUpdateCheckStatus
import com.gravityedit.app.domain.engine.ClippingsManager
import com.gravityedit.app.domain.engine.DiagnosticSeverity
import com.gravityedit.app.domain.engine.PowerShellDiagnostic
import com.gravityedit.app.domain.engine.UpdateDistributionChannel
import com.gravityedit.app.domain.model.AstSymbol
import com.gravityedit.app.domain.model.CaseTransformMode
import com.gravityedit.app.domain.model.CharDiffSpan
import com.gravityedit.app.domain.model.Clipping
import com.gravityedit.app.domain.model.DiffChangeType
import com.gravityedit.app.domain.model.DiffResult
import com.gravityedit.app.domain.model.DiffRow
import com.gravityedit.app.domain.model.DocumentEncoding
import com.gravityedit.app.domain.model.DuplicateLineMode
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.FormatTarget
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.LineNumberAction
import com.gravityedit.app.domain.model.MatchingLinesAction
import com.gravityedit.app.domain.model.MultiFileSearchMatch
import com.gravityedit.app.domain.model.PatternPlaygroundEvaluation
import com.gravityedit.app.domain.model.PrefixSuffixAction
import com.gravityedit.app.domain.model.QuoteTransformMode
import com.gravityedit.app.domain.model.SearchMatch
import com.gravityedit.app.domain.model.SearchOptions
import com.gravityedit.app.domain.model.SortDirection
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.domain.model.TabConversionMode
import com.gravityedit.app.domain.model.TextFactoryStep
import com.gravityedit.app.domain.model.TextOperationType
import com.gravityedit.app.domain.model.WrapTransformMode
import com.gravityedit.app.domain.model.ZapGremlinsAction
import com.gravityedit.app.ui.theme.EditorSyntaxPalette
import com.gravityedit.app.ui.theme.GravityEditThemeDefaults
import com.gravityedit.app.ui.theme.GravityThemeMode
import com.gravityedit.app.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

// =========================================================================
// 1. PCRE FIND & REPLACE, MULTI-FILE SEARCH & REGEX PATTERN PLAYGROUND
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FindReplaceModal(
    isCompactMobile: Boolean,
    initialTab: Int,
    searchOptions: SearchOptions,
    activeDocMatches: List<SearchMatch>,
    currentMatchIndex: Int,
    multiFileMatches: List<MultiFileSearchMatch>,
    isMultiFileSearching: Boolean,
    playgroundSampleText: String,
    playgroundEvaluation: PatternPlaygroundEvaluation,
    onUpdateOptions: (SearchOptions) -> Unit,
    onFindNext: () -> Unit,
    onFindPrev: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onExtractToNewFile: () -> Unit,
    onRunMultiFileSearch: () -> Unit,
    onRunMultiFileReplace: () -> Unit,
    onOpenMultiFileMatch: (uriString: String, lineNumber: Int) -> Unit,
    onJumpToMatchInActiveDoc: (SearchMatch) -> Unit,
    onUpdatePlaygroundSample: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val content: @Composable () -> Unit = {
        FindReplaceModalBody(
            initialTab = initialTab,
            searchOptions = searchOptions,
            activeDocMatches = activeDocMatches,
            currentMatchIndex = currentMatchIndex,
            multiFileMatches = multiFileMatches,
            isMultiFileSearching = isMultiFileSearching,
            playgroundSampleText = playgroundSampleText,
            playgroundEvaluation = playgroundEvaluation,
            onUpdateOptions = onUpdateOptions,
            onFindNext = onFindNext,
            onFindPrev = onFindPrev,
            onReplaceCurrent = onReplaceCurrent,
            onReplaceAll = onReplaceAll,
            onExtractToNewFile = onExtractToNewFile,
            onRunMultiFileSearch = onRunMultiFileSearch,
            onRunMultiFileReplace = onRunMultiFileReplace,
            onOpenMultiFileMatch = onOpenMultiFileMatch,
            onJumpToMatchInActiveDoc = onJumpToMatchInActiveDoc,
            onUpdatePlaygroundSample = onUpdatePlaygroundSample,
            onDismiss = onDismiss
        )
    }

    if (isCompactMobile) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.90f)) {
                content()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight(0.85f)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindReplaceModalBody(
    initialTab: Int,
    searchOptions: SearchOptions,
    activeDocMatches: List<SearchMatch>,
    currentMatchIndex: Int,
    multiFileMatches: List<MultiFileSearchMatch>,
    isMultiFileSearching: Boolean,
    playgroundSampleText: String,
    playgroundEvaluation: PatternPlaygroundEvaluation,
    onUpdateOptions: (SearchOptions) -> Unit,
    onFindNext: () -> Unit,
    onFindPrev: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onExtractToNewFile: () -> Unit,
    onRunMultiFileSearch: () -> Unit,
    onRunMultiFileReplace: () -> Unit,
    onOpenMultiFileMatch: (uriString: String, lineNumber: Int) -> Unit,
    onJumpToMatchInActiveDoc: (SearchMatch) -> Unit,
    onUpdatePlaygroundSample: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.find_title),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.tab_single_file)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.tab_multi_file)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.tab_pattern_playground)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Shared Find & Replace Pattern Inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchOptions.query,
                onValueChange = { onUpdateOptions(searchOptions.copy(query = it)) },
                label = { Text(stringResource(R.string.find_label)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = searchOptions.replacement,
                onValueChange = { onUpdateOptions(searchOptions.copy(replacement = it)) },
                label = { Text(stringResource(R.string.replace_label)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // PCRE Option Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = searchOptions.grepRegex,
                onClick = { onUpdateOptions(searchOptions.copy(grepRegex = !searchOptions.grepRegex)) },
                label = { Text(stringResource(R.string.opt_grep_regex)) }
            )
            FilterChip(
                selected = searchOptions.caseSensitive,
                onClick = { onUpdateOptions(searchOptions.copy(caseSensitive = !searchOptions.caseSensitive)) },
                label = { Text(stringResource(R.string.opt_case_sensitive)) }
            )
            FilterChip(
                selected = searchOptions.matchEntireWords,
                onClick = { onUpdateOptions(searchOptions.copy(matchEntireWords = !searchOptions.matchEntireWords)) },
                label = { Text(stringResource(R.string.opt_match_words)) }
            )
            FilterChip(
                selected = searchOptions.wrapAround,
                onClick = { onUpdateOptions(searchOptions.copy(wrapAround = !searchOptions.wrapAround)) },
                label = { Text(stringResource(R.string.opt_wrap_around)) }
            )
            FilterChip(
                selected = searchOptions.searchInSelectionOnly,
                onClick = { onUpdateOptions(searchOptions.copy(searchInSelectionOnly = !searchOptions.searchInSelectionOnly)) },
                label = { Text(stringResource(R.string.opt_selection_only)) }
            )
            if (searchOptions.grepRegex) {
                FilterChip(
                    selected = searchOptions.multiLine,
                    onClick = { onUpdateOptions(searchOptions.copy(multiLine = !searchOptions.multiLine)) },
                    label = { Text("Multi-Line (^\$)") }
                )
                FilterChip(
                    selected = searchOptions.dotMatchesAll,
                    onClick = { onUpdateOptions(searchOptions.copy(dotMatchesAll = !searchOptions.dotMatchesAll)) },
                    label = { Text("Dot Matches All (?s)") }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                // Single-file actions & live match list
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = onFindPrev) {
                        Text(stringResource(R.string.btn_find_prev))
                    }
                    Button(onClick = onFindNext) {
                        Text(stringResource(R.string.btn_find_next))
                    }
                    OutlinedButton(onClick = onReplaceCurrent) {
                        Text(stringResource(R.string.btn_replace))
                    }
                    Button(onClick = onReplaceAll) {
                        Text(stringResource(R.string.btn_replace_all))
                    }
                    OutlinedButton(onClick = onExtractToNewFile) {
                        Text(stringResource(R.string.btn_extract_matches))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (activeDocMatches.isEmpty()) {
                        stringResource(R.string.no_matches_found)
                    } else {
                        stringResource(R.string.matches_count, activeDocMatches.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(activeDocMatches) { idx, match ->
                        val isSelected = idx == currentMatchIndex
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToMatchInActiveDoc(match) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.status_cursor_pos, match.lineNumber, match.columnNumber),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "\"${match.matchedText}\"",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = match.linePreview,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (match.captureGroups.isNotEmpty()) {
                                    Text(
                                        text = match.captureGroups.joinToString(" | ") { g ->
                                            val label = g.groupName?.let { "\${$it}" } ?: "\$${g.groupIndex}"
                                            "$label = '${g.value}'"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Multi-File Search & Replace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchOptions.fileFilter,
                        onValueChange = { onUpdateOptions(searchOptions.copy(fileFilter = it)) },
                        label = { Text(stringResource(R.string.file_filter_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onRunMultiFileSearch) {
                        Text(stringResource(R.string.btn_find_next))
                    }
                    OutlinedButton(onClick = onRunMultiFileReplace) {
                        Text(stringResource(R.string.btn_replace_all))
                    }
                }

                if (isMultiFileSearching) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(8.dp))
                val totalMultiCount = multiFileMatches.sumOf { it.totalMatches }
                Text(
                    text = stringResource(R.string.matches_count, totalMultiCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(multiFileMatches, key = { it.uriString }) { fileMatch ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${fileMatch.relativePath} (${fileMatch.totalMatches})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                fileMatch.matches.take(15).forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenMultiFileMatch(fileMatch.uriString, m.lineNumber) }
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Ln ${m.lineNumber}:",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = m.linePreview,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Live Pattern Playground
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!playgroundEvaluation.isValidRegex) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = playgroundEvaluation.errorMessage ?: "Invalid Regex",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "${stringResource(R.string.matches_count, playgroundEvaluation.matches.size)} • ${playgroundEvaluation.executionTimeMicros} µs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = playgroundSampleText,
                        onValueChange = onUpdatePlaygroundSample,
                        label = { Text(stringResource(R.string.playground_sample_label)) },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "${stringResource(R.string.replace_label)} Preview:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = playgroundEvaluation.replacementPreview,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    playgroundEvaluation.matches.forEachIndexed { idx, match ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Match #${idx + 1} [${match.range.first}..${match.range.last}]: \"${match.matchedText}\"",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (match.captureGroups.isNotEmpty()) {
                                    match.captureGroups.forEach { cg ->
                                        val gName = cg.groupName?.let { " (?<$it>)" } ?: ""
                                        Text(
                                            text = "  Group \$${cg.groupIndex}$gName -> \"${cg.value}\"",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.5.sp,
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
    }
}

// =========================================================================
// 2. TEXT FACTORY VISUAL PIPELINE BUILDER (ALL 14 BBEDIT OPERATIONS)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFactoryModal(
    isCompactMobile: Boolean,
    steps: List<TextFactoryStep>,
    onAddStep: (TextOperationType) -> Unit,
    onUpdateStep: (TextFactoryStep) -> Unit,
    onRemoveStep: (String) -> Unit,
    onMoveStepUp: (String) -> Unit,
    onMoveStepDown: (String) -> Unit,
    onRunPipeline: () -> Unit,
    onDismiss: () -> Unit
) {
    val content: @Composable () -> Unit = {
        TextFactoryModalBody(
            steps = steps,
            onAddStep = onAddStep,
            onUpdateStep = onUpdateStep,
            onRemoveStep = onRemoveStep,
            onMoveStepUp = onMoveStepUp,
            onMoveStepDown = onMoveStepDown,
            onRunPipeline = onRunPipeline,
            onDismiss = onDismiss
        )
    }

    if (isCompactMobile) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.90f)) {
                content()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight(0.86f)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun TextFactoryModalBody(
    steps: List<TextFactoryStep>,
    onAddStep: (TextOperationType) -> Unit,
    onUpdateStep: (TextFactoryStep) -> Unit,
    onRemoveStep: (String) -> Unit,
    onMoveStepUp: (String) -> Unit,
    onMoveStepDown: (String) -> Unit,
    onRunPipeline: () -> Unit,
    onDismiss: () -> Unit
) {
    var addMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.text_factory_title),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                OutlinedButton(onClick = { addMenuExpanded = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.text_factory_add_step))
                }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false }
                ) {
                    TextOperationType.entries.forEach { opType ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = stringResource(opType.titleRes),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = opType.defaultSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                addMenuExpanded = false
                                onAddStep(opType)
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onRunPipeline,
                enabled = steps.any { it.enabled }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.text_factory_run_pipeline))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (steps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.text_factory_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                    TextFactoryStepCard(
                        index = index,
                        totalSteps = steps.size,
                        step = step,
                        onUpdateStep = onUpdateStep,
                        onRemoveStep = { onRemoveStep(step.id) },
                        onMoveUp = { onMoveStepUp(step.id) },
                        onMoveDown = { onMoveStepDown(step.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextFactoryStepCard(
    index: Int,
    totalSteps: Int,
    step: TextFactoryStep,
    onUpdateStep: (TextFactoryStep) -> Unit,
    onRemoveStep: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (step.enabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = step.enabled,
                    onCheckedChange = { onUpdateStep(step.copy(enabled = it)) }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${stringResource(step.operationType.titleRes)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = step.configurationSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_step_up))
                }
                IconButton(onClick = onMoveDown, enabled = index < totalSteps - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_step_down))
                }
                IconButton(onClick = onRemoveStep) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Per-operation configuration controls
            when (step.operationType) {
                TextOperationType.ZAP_GREMLINS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZapGremlinsAction.entries.forEach { act ->
                                FilterChip(
                                    selected = step.zapAction == act,
                                    onClick = { onUpdateStep(step.copy(zapAction = act)) },
                                    label = { Text(act.label) }
                                )
                            }
                            FilterChip(
                                selected = step.zapControlChars,
                                onClick = { onUpdateStep(step.copy(zapControlChars = !step.zapControlChars)) },
                                label = { Text("Control Chars") }
                            )
                            FilterChip(
                                selected = step.zapNullBytes,
                                onClick = { onUpdateStep(step.copy(zapNullBytes = !step.zapNullBytes)) },
                                label = { Text("Null Bytes") }
                            )
                            FilterChip(
                                selected = step.zapNonAscii,
                                onClick = { onUpdateStep(step.copy(zapNonAscii = !step.zapNonAscii)) },
                                label = { Text("Non-ASCII") }
                            )
                        }
                        if (step.zapAction == ZapGremlinsAction.REPLACE_WITH_CHAR) {
                            OutlinedTextField(
                                value = step.zapReplacementChar,
                                onValueChange = { onUpdateStep(step.copy(zapReplacementChar = it.take(4))) },
                                label = { Text("Replacement Character") },
                                singleLine = true,
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    }
                }

                TextOperationType.PROCESS_DUPLICATE_LINES -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DuplicateLineMode.entries.forEach { mode ->
                            FilterChip(
                                selected = step.duplicateMode == mode,
                                onClick = { onUpdateStep(step.copy(duplicateMode = mode)) },
                                label = { Text(mode.label) }
                            )
                        }
                        FilterChip(
                            selected = step.duplicateCaseSensitive,
                            onClick = { onUpdateStep(step.copy(duplicateCaseSensitive = !step.duplicateCaseSensitive)) },
                            label = { Text(stringResource(R.string.opt_case_sensitive)) }
                        )
                        FilterChip(
                            selected = step.duplicateIgnoreLeadingWhitespace,
                            onClick = {
                                onUpdateStep(
                                    step.copy(duplicateIgnoreLeadingWhitespace = !step.duplicateIgnoreLeadingWhitespace)
                                )
                            },
                            label = { Text("Ignore Leading Space") }
                        )
                    }
                }

                TextOperationType.SORT_LINES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SortDirection.entries.forEach { dir ->
                                FilterChip(
                                    selected = step.sortDirection == dir,
                                    onClick = { onUpdateStep(step.copy(sortDirection = dir)) },
                                    label = { Text(dir.label) }
                                )
                            }
                            FilterChip(
                                selected = step.sortCaseSensitive,
                                onClick = { onUpdateStep(step.copy(sortCaseSensitive = !step.sortCaseSensitive)) },
                                label = { Text(stringResource(R.string.opt_case_sensitive)) }
                            )
                            FilterChip(
                                selected = step.sortNumeric,
                                onClick = { onUpdateStep(step.copy(sortNumeric = !step.sortNumeric)) },
                                label = { Text("Numeric Sort") }
                            )
                        }
                        OutlinedTextField(
                            value = step.sortRegexPattern,
                            onValueChange = { onUpdateStep(step.copy(sortRegexPattern = it)) },
                            label = { Text("Optional Sort Key Regex (Group 1)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                TextOperationType.PREFIX_SUFFIX_LINES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrefixSuffixAction.entries.forEach { act ->
                                FilterChip(
                                    selected = step.prefixSuffixAction == act,
                                    onClick = { onUpdateStep(step.copy(prefixSuffixAction = act)) },
                                    label = { Text(act.label) }
                                )
                            }
                            FilterChip(
                                selected = step.skipBlankLines,
                                onClick = { onUpdateStep(step.copy(skipBlankLines = !step.skipBlankLines)) },
                                label = { Text("Skip Blank Lines") }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = step.linePrefix,
                                onValueChange = { onUpdateStep(step.copy(linePrefix = it)) },
                                label = { Text("Prefix") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = step.lineSuffix,
                                onValueChange = { onUpdateStep(step.copy(lineSuffix = it)) },
                                label = { Text("Suffix") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                TextOperationType.CHANGE_CASE -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CaseTransformMode.entries.forEach { mode ->
                            FilterChip(
                                selected = step.caseMode == mode,
                                onClick = { onUpdateStep(step.copy(caseMode = mode)) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }

                TextOperationType.STRIP_TRAILING_WHITESPACE -> {
                    FilterChip(
                        selected = step.stripWhitespacePreserveBlankLines,
                        onClick = {
                            onUpdateStep(step.copy(stripWhitespacePreserveBlankLines = !step.stripWhitespacePreserveBlankLines))
                        },
                        label = { Text("Preserve Blank Lines") }
                    )
                }

                TextOperationType.ENTAB_DETAB -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabConversionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = step.tabConversionMode == mode,
                                onClick = { onUpdateStep(step.copy(tabConversionMode = mode)) },
                                label = { Text(mode.label) }
                            )
                        }
                        listOf(2, 4, 8).forEach { w ->
                            FilterChip(
                                selected = step.tabWidth == w,
                                onClick = { onUpdateStep(step.copy(tabWidth = w)) },
                                label = { Text("$w spaces") }
                            )
                        }
                    }
                }

                TextOperationType.HARD_WRAP_UNWRAP -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WrapTransformMode.entries.forEach { mode ->
                            FilterChip(
                                selected = step.wrapMode == mode,
                                onClick = { onUpdateStep(step.copy(wrapMode = mode)) },
                                label = { Text(mode.label) }
                            )
                        }
                        if (step.wrapMode == WrapTransformMode.HARD_WRAP) {
                            listOf(72, 80, 100, 120).forEach { col ->
                                FilterChip(
                                    selected = step.wrapColumnWidth == col,
                                    onClick = { onUpdateStep(step.copy(wrapColumnWidth = col)) },
                                    label = { Text("Col $col") }
                                )
                            }
                        } else {
                            FilterChip(
                                selected = step.wrapPreserveParagraphs,
                                onClick = {
                                    onUpdateStep(step.copy(wrapPreserveParagraphs = !step.wrapPreserveParagraphs))
                                },
                                label = { Text("Preserve Paragraphs") }
                            )
                        }
                    }
                }

                TextOperationType.ADD_REMOVE_LINE_NUMBERS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LineNumberAction.entries.forEach { act ->
                                FilterChip(
                                    selected = step.lineNumberAction == act,
                                    onClick = { onUpdateStep(step.copy(lineNumberAction = act)) },
                                    label = { Text(act.label) }
                                )
                            }
                            if (step.lineNumberAction == LineNumberAction.ADD_NUMBERS) {
                                listOf(0 to "No Pad", 2 to "Pad 2", 3 to "Pad 3", 4 to "Pad 4").forEach { (pad, lbl) ->
                                    FilterChip(
                                        selected = step.lineNumberZeroPadWidth == pad,
                                        onClick = { onUpdateStep(step.copy(lineNumberZeroPadWidth = pad)) },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                        }
                        if (step.lineNumberAction == LineNumberAction.ADD_NUMBERS) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = step.lineNumberStart.toString(),
                                    onValueChange = { v ->
                                        val parsed = v.filter { it.isDigit() }.toIntOrNull() ?: 1
                                        onUpdateStep(step.copy(lineNumberStart = parsed.coerceAtLeast(0)))
                                    },
                                    label = { Text("Start #") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.lineNumberIncrement.toString(),
                                    onValueChange = { v ->
                                        val parsed = v.filter { it.isDigit() }.toIntOrNull() ?: 1
                                        onUpdateStep(step.copy(lineNumberIncrement = parsed.coerceAtLeast(1)))
                                    },
                                    label = { Text("Step") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = step.lineNumberSeparator,
                                    onValueChange = { onUpdateStep(step.copy(lineNumberSeparator = it)) },
                                    label = { Text("Separator") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                TextOperationType.EDUCATE_STRAIGHTEN_QUOTES -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuoteTransformMode.entries.forEach { mode ->
                            FilterChip(
                                selected = step.quoteMode == mode,
                                onClick = { onUpdateStep(step.copy(quoteMode = mode)) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }

                TextOperationType.CANONIZE_REPLACE_TABLE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = step.canonizeCaseSensitive,
                                onClick = {
                                    onUpdateStep(step.copy(canonizeCaseSensitive = !step.canonizeCaseSensitive))
                                },
                                label = { Text(stringResource(R.string.opt_case_sensitive)) }
                            )
                            FilterChip(
                                selected = step.canonizeWholeWords,
                                onClick = {
                                    onUpdateStep(step.copy(canonizeWholeWords = !step.canonizeWholeWords))
                                },
                                label = { Text(stringResource(R.string.opt_match_words)) }
                            )
                        }
                        OutlinedTextField(
                            value = step.canonizeTableRaw,
                            onValueChange = { onUpdateStep(step.copy(canonizeTableRaw = it)) },
                            label = { Text("Key=Value Replacement Table") },
                            textStyle = MaterialTheme.typography.labelMedium,
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                TextOperationType.PROCESS_LINES_CONTAINING -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MatchingLinesAction.entries.forEach { act ->
                                FilterChip(
                                    selected = step.matchingLinesAction == act,
                                    onClick = { onUpdateStep(step.copy(matchingLinesAction = act)) },
                                    label = { Text(act.label) }
                                )
                            }
                            FilterChip(
                                selected = step.matchingUseRegex,
                                onClick = { onUpdateStep(step.copy(matchingUseRegex = !step.matchingUseRegex)) },
                                label = { Text(stringResource(R.string.opt_grep_regex)) }
                            )
                            FilterChip(
                                selected = step.matchingCaseSensitive,
                                onClick = {
                                    onUpdateStep(step.copy(matchingCaseSensitive = !step.matchingCaseSensitive))
                                },
                                label = { Text(stringResource(R.string.opt_case_sensitive)) }
                            )
                        }
                        OutlinedTextField(
                            value = step.matchingPattern,
                            onValueChange = { onUpdateStep(step.copy(matchingPattern = it)) },
                            label = { Text(stringResource(R.string.find_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                TextOperationType.CONVERT_LINE_ENDINGS -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineEnding.entries.forEach { le ->
                            FilterChip(
                                selected = step.targetLineEnding == le,
                                onClick = { onUpdateStep(step.copy(targetLineEnding = le)) },
                                label = { Text(le.displayName) }
                            )
                        }
                    }
                }

                TextOperationType.FORMAT_PRETTIFY -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormatTarget.entries.forEach { target ->
                            FilterChip(
                                selected = step.formatTarget == target,
                                onClick = { onUpdateStep(step.copy(formatTarget = target)) },
                                label = { Text(target.label) }
                            )
                        }
                        listOf(2, 4, 8).forEach { sp ->
                            FilterChip(
                                selected = step.formatIndentSpaces == sp,
                                onClick = { onUpdateStep(step.copy(formatIndentSpaces = sp)) },
                                label = { Text("$sp spaces") }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. SIDE-BY-SIDE MYERS DIFF VIEW & DIRECTIONAL MERGE UI
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideBySideDiffModal(
    isCompactMobile: Boolean,
    documents: List<EditorDocument>,
    diffResult: DiffResult?,
    onSelectLeftDoc: (String) -> Unit,
    onSelectRightDoc: (String) -> Unit,
    onMergeToLeft: (Int) -> Unit,
    onMergeToRight: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val content: @Composable () -> Unit = {
        SideBySideDiffBody(
            documents = documents,
            diffResult = diffResult,
            onSelectLeftDoc = onSelectLeftDoc,
            onSelectRightDoc = onSelectRightDoc,
            onMergeToLeft = onMergeToLeft,
            onMergeToRight = onMergeToRight,
            onDismiss = onDismiss
        )
    }

    if (isCompactMobile) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.92f)) {
                content()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SideBySideDiffBody(
    documents: List<EditorDocument>,
    diffResult: DiffResult?,
    onSelectLeftDoc: (String) -> Unit,
    onSelectRightDoc: (String) -> Unit,
    onMergeToLeft: (Int) -> Unit,
    onMergeToRight: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = GravityEditThemeDefaults.syntaxPalette
    var leftDropdownExpanded by remember { mutableStateOf(false) }
    var rightDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.diff_title),
                    style = MaterialTheme.typography.titleLarge
                )
                if (diffResult != null) {
                    Text(
                        text = if (diffResult.isIdentical) {
                            stringResource(R.string.diff_identical)
                        } else {
                            stringResource(
                                R.string.diff_summary,
                                diffResult.addedCount,
                                diffResult.removedCount,
                                diffResult.modifiedCount
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Left & Right Document Pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { leftDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${stringResource(R.string.diff_left_doc)}: ${diffResult?.leftTitle ?: ""}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = leftDropdownExpanded,
                    onDismissRequest = { leftDropdownExpanded = false }
                ) {
                    documents.forEach { doc ->
                        DropdownMenuItem(
                            text = { Text(doc.title) },
                            onClick = {
                                leftDropdownExpanded = false
                                onSelectLeftDoc(doc.id)
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { rightDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${stringResource(R.string.diff_right_doc)}: ${diffResult?.rightTitle ?: ""}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = rightDropdownExpanded,
                    onDismissRequest = { rightDropdownExpanded = false }
                ) {
                    documents.forEach { doc ->
                        DropdownMenuItem(
                            text = { Text(doc.title) },
                            onClick = {
                                rightDropdownExpanded = false
                                onSelectRightDoc(doc.id)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (diffResult != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            ) {
                items(diffResult.rows, key = { it.rowIndex }) { row ->
                    DiffComparisonRowItem(
                        row = row,
                        palette = palette,
                        onMergeToLeft = { onMergeToLeft(row.rowIndex) },
                        onMergeToRight = { onMergeToRight(row.rowIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffComparisonRowItem(
    row: DiffRow,
    palette: EditorSyntaxPalette,
    onMergeToLeft: () -> Unit,
    onMergeToRight: () -> Unit
) {
    val leftBg = when (row.changeType) {
        DiffChangeType.EQUAL -> palette.editorBackground
        DiffChangeType.DELETE -> palette.diffRemovedBackground
        DiffChangeType.INSERT -> palette.gutterBackground.copy(alpha = 0.5f)
        DiffChangeType.MODIFY -> palette.diffModifiedBackground
    }
    val rightBg = when (row.changeType) {
        DiffChangeType.EQUAL -> palette.editorBackground
        DiffChangeType.DELETE -> palette.gutterBackground.copy(alpha = 0.5f)
        DiffChangeType.INSERT -> palette.diffAddedBackground
        DiffChangeType.MODIFY -> palette.diffModifiedBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.25.dp, palette.pageGuideColor.copy(alpha = 0.4f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side
        Row(
            modifier = Modifier
                .weight(1f)
                .background(leftBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.leftLineNumber?.toString()?.padStart(4, ' ') ?: "    ",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = palette.gutterText,
                modifier = Modifier.width(34.dp)
            )
            Text(
                text = buildCharHighlightedAnnotatedString(
                    text = row.leftText ?: "",
                    spans = row.leftCharHighlights,
                    highlightColor = palette.diffRemovedHighlight,
                    baseColor = palette.editorForeground
                ),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Center Directional Merge Actions (← / →)
        Row(
            modifier = Modifier
                .width(56.dp)
                .background(palette.gutterBackground),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (row.changeType != DiffChangeType.EQUAL) {
                IconButton(
                    onClick = onMergeToLeft,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.diff_copy_to_left),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = onMergeToRight,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.diff_copy_to_right),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Right Side
        Row(
            modifier = Modifier
                .weight(1f)
                .background(rightBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.rightLineNumber?.toString()?.padStart(4, ' ') ?: "    ",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = palette.gutterText,
                modifier = Modifier.width(34.dp)
            )
            Text(
                text = buildCharHighlightedAnnotatedString(
                    text = row.rightText ?: "",
                    spans = row.rightCharHighlights,
                    highlightColor = palette.diffAddedHighlight,
                    baseColor = palette.editorForeground
                ),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildCharHighlightedAnnotatedString(
    text: String,
    spans: List<CharDiffSpan>,
    highlightColor: androidx.compose.ui.graphics.Color,
    baseColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = baseColor), 0, text.length)
        for (span in spans) {
            if (span.isChanged) {
                val s = span.start.coerceIn(0, text.length)
                val e = span.end.coerceIn(s, text.length)
                if (s < e) {
                    addStyle(
                        SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold),
                        s,
                        e
                    )
                }
            }
        }
    }
}

// =========================================================================
// 4. CLIPPINGS & POWERSHELL SNIPPETS PICKER
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClippingsModal(
    isCompactMobile: Boolean,
    clippings: List<Clipping>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onInsertClipping: (Clipping, String) -> Unit,
    onAddCustomClipping: (name: String, trigger: String, body: String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showCreateForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newTrigger by remember { mutableStateOf("") }
    var newBody by remember { mutableStateOf("#SELECTION##INSERTION#") }

    val filtered = remember(clippings, searchQuery) {
        ClippingsManager.filterClippings(clippings, searchQuery)
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.clippings_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showCreateForm = !showCreateForm }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.clippings_new))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showCreateForm) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text(stringResource(R.string.clippings_name_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newTrigger,
                                onValueChange = { newTrigger = it },
                                label = { Text("Trigger") },
                                singleLine = true,
                                modifier = Modifier.width(130.dp)
                            )
                        }
                        OutlinedTextField(
                            value = newBody,
                            onValueChange = { newBody = it },
                            label = { Text(stringResource(R.string.clippings_body_label)) },
                            textStyle = MaterialTheme.typography.labelMedium,
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCreateForm = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = {
                                    onAddCustomClipping(newName, newTrigger, newBody)
                                    newName = ""
                                    newTrigger = ""
                                    showCreateForm = false
                                }
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.clippings_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { clipping ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = clipping.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${clipping.category.displayName} • [${clipping.triggerPrefix}]",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text ?: ""
                                        onInsertClipping(clipping, clipText)
                                    }
                                ) {
                                    Text(stringResource(R.string.action_insert))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = clipping.templateBody,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCompactMobile) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.88f)) {
                content()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .fillMaxHeight(0.84f)
            ) {
                content()
            }
        }
    }
}

// =========================================================================
// 5. GO TO LINE DIALOG (Ctrl+G)
// =========================================================================

@Composable
fun GoToLineDialog(
    maxLineCount: Int,
    currentLine: Int,
    onGoToLine: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember(currentLine) { mutableStateOf(currentLine.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GravityEditThemeDefaults.dialogShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.go_to_line_title)) },
        text = {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.go_to_line_hint, maxLineCount)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = inputText.toIntOrNull()
                        if (parsed != null) onGoToLine(parsed)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = inputText.toIntOrNull() ?: currentLine
                    onGoToLine(parsed)
                },
                shape = GravityEditThemeDefaults.pillShape
            ) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// =========================================================================
// 6. SETTINGS, MATERIAL 3 EXPRESSIVE THEMES & 6-LANGUAGE LOCALIZATION MODAL
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsModal(
    themeMode: GravityThemeMode,
    appLocaleTag: String,
    fontSizeSp: Float,
    tabWidth: Int,
    softWrap: Boolean,
    showInvisibles: Boolean,
    showPageGuide80: Boolean,
    showPageGuide120: Boolean,
    enableCodeFolding: Boolean,
    installedVersionName: String,
    autoCheckUpdates: Boolean,
    onSelectTheme: (GravityThemeMode) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onTabWidthChange: (Int) -> Unit,
    onSoftWrapChange: (Boolean) -> Unit,
    onShowInvisiblesChange: (Boolean) -> Unit,
    onPageGuide80Change: (Boolean) -> Unit,
    onPageGuide120Change: (Boolean) -> Unit,
    onCodeFoldingChange: (Boolean) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onOpenUpdateModal: () -> Unit,
    onDismiss: () -> Unit,
    isCompactMobile: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            // 1. Material 3 Expressive Color Schemes (Dynamic, Light, Dark, AMOLED Pitch Black)
            Text(
                text = stringResource(R.string.settings_theme_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val themeItems = listOf(
                    GravityThemeMode.DYNAMIC to R.string.theme_dynamic,
                    GravityThemeMode.LIGHT to R.string.theme_light,
                    GravityThemeMode.DARK to R.string.theme_dark,
                    GravityThemeMode.AMOLED to R.string.theme_amoled
                )
                themeItems.forEach { (mode, labelRes) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onSelectTheme(mode) },
                        shape = GravityEditThemeDefaults.pillShape,
                        leadingIcon = if (themeMode == mode) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            HorizontalDivider()

            // 2. Runtime 6-Language Localization Switcher
            Text(
                text = stringResource(R.string.settings_language_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val locales = listOf(
                    "en" to R.string.lang_en,
                    "de" to R.string.lang_de,
                    "it" to R.string.lang_it,
                    "es" to R.string.lang_es,
                    "fr" to R.string.lang_fr,
                    "el" to R.string.lang_el
                )
                locales.forEach { (tag, labelRes) ->
                    val isSelected = appLocaleTag.startsWith(tag, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectLanguage(tag) },
                        shape = GravityEditThemeDefaults.pillShape,
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            HorizontalDivider()

            // 3. Editor Typography & Canvas Behavior
            Text(
                text = stringResource(R.string.settings_editor_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.settings_font_size, fontSizeSp.roundToInt()),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = fontSizeSp,
                onValueChange = onFontSizeChange,
                valueRange = 10f..24f,
                steps = 13
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_tab_width, tabWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                listOf(2, 4, 8).forEach { w ->
                    FilterChip(
                        selected = tabWidth == w,
                        onClick = { onTabWidthChange(w) },
                        shape = GravityEditThemeDefaults.pillShape,
                        label = { Text("$w") }
                    )
                }
            }

            SettingSwitchRow(
                label = stringResource(R.string.soft_wrap),
                checked = softWrap,
                onCheckedChange = onSoftWrapChange
            )
            SettingSwitchRow(
                label = stringResource(R.string.show_invisibles),
                checked = showInvisibles,
                onCheckedChange = onShowInvisiblesChange
            )
            SettingSwitchRow(
                label = stringResource(R.string.page_guide_80),
                checked = showPageGuide80,
                onCheckedChange = onPageGuide80Change
            )
            SettingSwitchRow(
                label = stringResource(R.string.page_guide_120),
                checked = showPageGuide120,
                onCheckedChange = onPageGuide120Change
            )
            SettingSwitchRow(
                label = stringResource(R.string.code_folding),
                checked = enableCodeFolding,
                onCheckedChange = onCodeFoldingChange
            )

            HorizontalDivider()

            // 4. App Updates (Google Play Protect & Play Store Policy Compliant)
            Text(
                text = "App Updates • Google Play Protect Verified (v$installedVersionName)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SettingSwitchRow(
                label = "Check for Updates Automatically on Startup",
                checked = autoCheckUpdates,
                onCheckedChange = onAutoCheckUpdatesChange
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenUpdateModal()
                    },
                    shape = GravityEditThemeDefaults.pillShape
                ) {
                    Text("Check for Updates / Open Update Center…")
                }
            }
        }
    }

    if (isCompactMobile) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.90f)) {
                content()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .heightIn(max = 680.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// =========================================================================
// 7. PSSCRIPTANALYZER-STYLE LIVE POWERSHELL LINTER & DIAGNOSTICS MODAL
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerShellDiagnosticsModal(
    isCompactMobile: Boolean,
    diagnostics: List<PowerShellDiagnostic>,
    onJumpToLine: (Int) -> Unit,
    onApplyQuickFix: (PowerShellDiagnostic) -> Unit,
    onApplyAllQuickFixes: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = GravityEditThemeDefaults.syntaxPalette
    var selectedSeverityFilter by remember { mutableStateOf<DiagnosticSeverity?>(null) }

    val filteredDiagnostics = remember(diagnostics, selectedSeverityFilter) {
        if (selectedSeverityFilter == null) {
            diagnostics
        } else {
            diagnostics.filter { it.severity == selectedSeverityFilter }
        }
    }

    val errorCount = remember(diagnostics) { diagnostics.count { it.severity == DiagnosticSeverity.ERROR } }
    val warnCount = remember(diagnostics) { diagnostics.count { it.severity == DiagnosticSeverity.WARNING } }
    val infoCount = remember(diagnostics) { diagnostics.count { it.severity == DiagnosticSeverity.INFO } }
    val fixableCount = remember(diagnostics) {
        diagnostics.count { it.quickFixTarget != null && it.quickFixReplacement != null }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PSScriptAnalyzer • Live PowerShell Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$errorCount Errors • $warnCount Warnings • $infoCount Hints",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fixableCount > 0) {
                        Button(onClick = onApplyAllQuickFixes) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fix All ($fixableCount)")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
            }

            // Severity Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSeverityFilter == null,
                    onClick = { selectedSeverityFilter = null },
                    label = { Text("All (${diagnostics.size})") }
                )
                FilterChip(
                    selected = selectedSeverityFilter == DiagnosticSeverity.ERROR,
                    onClick = {
                        selectedSeverityFilter = if (selectedSeverityFilter == DiagnosticSeverity.ERROR) null else DiagnosticSeverity.ERROR
                    },
                    label = { Text("Errors ($errorCount)") }
                )
                FilterChip(
                    selected = selectedSeverityFilter == DiagnosticSeverity.WARNING,
                    onClick = {
                        selectedSeverityFilter = if (selectedSeverityFilter == DiagnosticSeverity.WARNING) null else DiagnosticSeverity.WARNING
                    },
                    label = { Text("Warnings ($warnCount)") }
                )
                FilterChip(
                    selected = selectedSeverityFilter == DiagnosticSeverity.INFO,
                    onClick = {
                        selectedSeverityFilter = if (selectedSeverityFilter == DiagnosticSeverity.INFO) null else DiagnosticSeverity.INFO
                    },
                    label = { Text("Alias / Info ($infoCount)") }
                )
            }

            HorizontalDivider()

            if (filteredDiagnostics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓ Zero PowerShell issues detected by PSScriptAnalyzer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDiagnostics, key = { it.id }) { diag ->
                        val badgeColor = when (diag.severity) {
                            DiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
                            DiagnosticSeverity.WARNING -> palette.operatorToken
                            DiagnosticSeverity.INFO -> palette.cmdlet
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onJumpToLine(diag.lineNumber)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = badgeColor.copy(alpha = 0.18f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = diag.severity.badge,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = diag.ruleId,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Ln ${diag.lineNumber}, Col ${diag.column}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = diag.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (diag.quickFixLabel != null) {
                                    OutlinedButton(
                                        onClick = { onApplyQuickFix(diag) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(diag.quickFixLabel, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCompactMobile) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(760.dp)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

// =========================================================================
// 8. KEYBOARD-DRIVEN COMMAND PALETTE (CTRL+SHIFT+P / CTRL+P)
// =========================================================================

private data class CommandPaletteEntry(
    val id: String,
    val categoryBadge: String,
    val title: String,
    val subtitle: String,
    val shortcutHint: String? = null,
    val action: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteModal(
    isCompactMobile: Boolean,
    documents: List<EditorDocument>,
    astSymbols: List<AstSymbol>,
    clippings: List<Clipping>,
    onSelectDocument: (String) -> Unit,
    onJumpToSymbol: (AstSymbol) -> Unit,
    onRunQuickTransform: (TextFactoryStep) -> Unit,
    onInsertClipping: (Clipping) -> Unit,
    onOpenToolModal: (String) -> Unit,
    onSelectTheme: (GravityThemeMode) -> Unit,
    onSelectLocale: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val allEntries = remember(documents, astSymbols, clippings) {
        val list = ArrayList<CommandPaletteEntry>()

        // 1. Open Documents
        documents.forEach { doc ->
            list.add(
                CommandPaletteEntry(
                    id = "doc_${doc.id}",
                    categoryBadge = "FILE",
                    title = "Switch to Tab: ${doc.title}",
                    subtitle = "${doc.language.displayName} • ${doc.lineCount} lines • ${doc.encoding.displayName}",
                    shortcutHint = "Ctrl+P",
                    action = {
                        onSelectDocument(doc.id)
                        onDismiss()
                    }
                )
            )
        }

        // 2. AST Symbols in Active Document
        astSymbols.forEach { sym ->
            list.add(
                CommandPaletteEntry(
                    id = "ast_${sym.name}_${sym.lineNumber}",
                    categoryBadge = sym.kind.badge.uppercase(),
                    title = "Jump to Symbol: ${sym.signature}",
                    subtitle = "Line ${sym.lineNumber}",
                    shortcutHint = "Ctrl+J",
                    action = {
                        onJumpToSymbol(sym)
                        onDismiss()
                    }
                )
            )
        }

        // 3. All 14 BBEdit Text Factory Quick Transforms
        TextOperationType.entries.forEach { opType ->
            list.add(
                CommandPaletteEntry(
                    id = "tx_${opType.name}",
                    categoryBadge = "BBEDIT",
                    title = "Text Transform: ${opType.name.replace('_', ' ')}",
                    subtitle = opType.defaultSummary,
                    action = {
                        onRunQuickTransform(TextFactoryStep(operationType = opType, enabled = true))
                    }
                )
            )
        }

        // 4. Workspace Modals & Tools (Complete Coverage)
        list.add(
            CommandPaletteEntry(
                id = "tool_newdoc",
                categoryBadge = "NEW",
                title = "File: New Document (Choose File Type & Extension…)",
                subtitle = "Create .txt, .ps1, .psm1, .psd1, .md, .json, .yaml, .xml, .kt, .py, .sh, .html, or .css",
                shortcutHint = "Ctrl+N",
                action = {
                    onDismiss()
                    onOpenToolModal("newdoc")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_gotoline",
                categoryBadge = "GOTO",
                title = "Navigate: Go to Line…",
                subtitle = "Jump cursor directly to a 1-based line number in the active document",
                shortcutHint = "Ctrl+G",
                action = {
                    onDismiss()
                    onOpenToolModal("gotoline")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_find",
                categoryBadge = "SEARCH",
                title = "Open PCRE Find, Replace & Pattern Playground",
                subtitle = "Regex search, multi-file search, and capture group inspector",
                shortcutHint = "Ctrl+F",
                action = {
                    onDismiss()
                    onOpenToolModal("find")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_multifind",
                categoryBadge = "SEARCH",
                title = "Open Multi-File Project Search & Batch Replace",
                subtitle = "Search across mounted SAF project directory files with glob filters",
                shortcutHint = "Ctrl+Shift+F",
                action = {
                    onDismiss()
                    onOpenToolModal("multifind")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_factory",
                categoryBadge = "PIPELINE",
                title = "Open BBEdit Text Factory Pipeline Builder",
                subtitle = "Chain and reorder multi-step text transformations",
                action = {
                    onDismiss()
                    onOpenToolModal("factory")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_diff",
                categoryBadge = "DIFF",
                title = "Open Side-by-Side Myers Diff & Merge Tool",
                subtitle = "Compare two open documents with character-level highlights",
                action = {
                    onDismiss()
                    onOpenToolModal("diff")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_clippings",
                categoryBadge = "SNIPPETS",
                title = "Open Clippings & PowerShell Snippets Library",
                subtitle = "Browse, search, and create custom #INSERTION# / #SELECTION# templates",
                action = {
                    onDismiss()
                    onOpenToolModal("clippings")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_diagnostics",
                categoryBadge = "LINTER",
                title = "Open PSScriptAnalyzer Live Diagnostics",
                subtitle = "Inspect warnings, errors, and apply one-click Quick Fixes",
                action = {
                    onDismiss()
                    onOpenToolModal("diagnostics")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_settings",
                categoryBadge = "PREFS",
                title = "Open Preferences & Material 3 Expressive Appearance",
                subtitle = "Configure themes, 6 UI languages, font size, tab width, soft wrap, and guides",
                action = {
                    onDismiss()
                    onOpenToolModal("settings")
                }
            )
        )
        list.add(
            CommandPaletteEntry(
                id = "tool_update",
                categoryBadge = "UPDATE",
                title = "App: Check for Updates (Google Play Protect Verified)",
                subtitle = "Check GitHub Releases / Google Play channel with SHA-256 verification & System PackageInstaller",
                action = {
                    onDismiss()
                    onOpenToolModal("update")
                }
            )
        )

        // 5. PowerShell Clippings
        clippings.forEach { clip ->
            list.add(
                CommandPaletteEntry(
                    id = "clip_${clip.id}",
                    categoryBadge = "SNIPPET",
                    title = "Insert Clipping: ${clip.name}",
                    subtitle = "${clip.triggerPrefix} — ${clip.description}",
                    action = {
                        onInsertClipping(clip)
                        onDismiss()
                    }
                )
            )
        }

        // 6. Theme & Runtime 6-Language Localization
        GravityThemeMode.entries.forEach { mode ->
            list.add(
                CommandPaletteEntry(
                    id = "theme_${mode.name}",
                    categoryBadge = "THEME",
                    title = "Switch Editor Theme: ${mode.name}",
                    subtitle = "Material 3 Expressive Color Scheme",
                    action = {
                        onSelectTheme(mode)
                        onDismiss()
                    }
                )
            )
        }

        listOf(
            "en" to "English (EN)",
            "de" to "Deutsch (DE)",
            "it" to "Italiano (IT)",
            "es" to "Español (ES)",
            "fr" to "Français (FR)",
            "el" to "Ελληνικά (EL)"
        ).forEach { (tag, label) ->
            list.add(
                CommandPaletteEntry(
                    id = "lang_$tag",
                    categoryBadge = "I18N",
                    title = "Switch UI Language: $label",
                    subtitle = "Instant runtime locale switch via AppCompatDelegate",
                    action = {
                        onSelectLocale(tag)
                        onDismiss()
                    }
                )
            )
        }

        list
    }

    val filteredEntries = remember(allEntries, query) {
        if (query.isBlank()) {
            allEntries
        } else {
            val terms = query.trim().lowercase().split(Regex("""\s+"""))
            allEntries.filter { entry ->
                val haystack = "${entry.categoryBadge} ${entry.title} ${entry.subtitle}".lowercase()
                terms.all { haystack.contains(it) }
            }
        }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Command Palette & Quick Switcher (Ctrl+P)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text("Type a command, file name, AST function, BBEdit transform, clipping, or language...")
                },
                singleLine = true,
                shape = GravityEditThemeDefaults.pillShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        filteredEntries.firstOrNull()?.action?.invoke()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredEntries, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.action() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = GravityEditThemeDefaults.pillShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = item.categoryBadge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (item.shortcutHint != null) {
                                Text(
                                    text = item.shortcutHint,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCompactMobile) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(720.dp)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

// =========================================================================
// 9. NEW DOCUMENT & FILE TYPE / EXTENSION SELECTOR DIALOG (+ / CTRL+N)
// =========================================================================

private data class FileTypePreset(
    val id: String,
    val label: String,
    val extension: String,
    val language: SyntaxLanguage,
    val badge: String
)

private val NEW_DOCUMENT_FILE_TYPE_PRESETS: List<FileTypePreset> = listOf(
    FileTypePreset("txt", "Plain Text (.txt)", "txt", SyntaxLanguage.PLAIN_TEXT, "TXT"),
    FileTypePreset("ps1", "PowerShell Script (.ps1)", "ps1", SyntaxLanguage.POWERSHELL, "PS1"),
    FileTypePreset("psm1", "PowerShell Module (.psm1)", "psm1", SyntaxLanguage.POWERSHELL, "PSM1"),
    FileTypePreset("psd1", "PowerShell Manifest (.psd1)", "psd1", SyntaxLanguage.POWERSHELL, "PSD1"),
    FileTypePreset("md", "Markdown (.md)", "md", SyntaxLanguage.MARKDOWN, "MD"),
    FileTypePreset("json", "JSON (.json)", "json", SyntaxLanguage.JSON, "JSON"),
    FileTypePreset("yaml", "YAML (.yaml)", "yaml", SyntaxLanguage.YAML, "YAML"),
    FileTypePreset("xml", "XML (.xml)", "xml", SyntaxLanguage.XML, "XML"),
    FileTypePreset("kt", "Kotlin (.kt)", "kt", SyntaxLanguage.KOTLIN, "KT"),
    FileTypePreset("py", "Python (.py)", "py", SyntaxLanguage.PYTHON, "PY"),
    FileTypePreset("sh", "Shell / Bash (.sh)", "sh", SyntaxLanguage.SHELL_BASH, "SH"),
    FileTypePreset("html", "HTML (.html)", "html", SyntaxLanguage.HTML_CSS, "HTML"),
    FileTypePreset("css", "CSS (.css)", "css", SyntaxLanguage.HTML_CSS, "CSS")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewDocumentDialog(
    isCompactMobile: Boolean,
    nextUntitledNumber: Int,
    onCreateDocument: (
        fileName: String,
        language: SyntaxLanguage,
        encoding: DocumentEncoding,
        lineEnding: LineEnding,
        useStarterTemplate: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf(NEW_DOCUMENT_FILE_TYPE_PRESETS[0]) }
    val initialStem = "Untitled-$nextUntitledNumber"
    val initialFullName = "$initialStem.${NEW_DOCUMENT_FILE_TYPE_PRESETS[0].extension}"
    var fileNameField by remember(nextUntitledNumber) {
        mutableStateOf(
            TextFieldValue(
                text = initialFullName,
                selection = TextRange(0, initialStem.length)
            )
        )
    }
    var selectedEncoding by remember { mutableStateOf(DocumentEncoding.UTF_8) }
    var selectedLineEnding by remember { mutableStateOf(LineEnding.LF) }
    var useStarterTemplate by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { nameFocusRequester.requestFocus() }
    }

    fun selectPreset(preset: FileTypePreset) {
        selectedPreset = preset
        val currentText = fileNameField.text
        val base = currentText.substringBeforeLast('.', currentText).ifBlank { "Untitled-$nextUntitledNumber" }
        val updatedText = "$base.${preset.extension}"
        fileNameField = TextFieldValue(
            text = updatedText,
            selection = TextRange(0, base.length)
        )
        runCatching { nameFocusRequester.requestFocus() }
    }

    fun submitCreate() {
        val finalName = fileNameField.text.trim().ifEmpty {
            "Untitled-$nextUntitledNumber.${selectedPreset.extension}"
        }
        val resolvedLanguage = if (finalName.contains('.')) {
            SyntaxLanguage.fromFileName(finalName)
        } else {
            selectedPreset.language
        }
        onCreateDocument(
            finalName,
            resolvedLanguage,
            selectedEncoding,
            selectedLineEnding,
            useStarterTemplate
        )
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.action_new_file),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose file type, extension, encoding, and line endings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            // 1. File Name Input (auto-selects name on focus/click and syncs extension & syntax language)
            OutlinedTextField(
                value = fileNameField,
                onValueChange = { updated ->
                    fileNameField = updated
                    val typedExt = updated.text.substringAfterLast('.', "").lowercase()
                    val matchedPreset = NEW_DOCUMENT_FILE_TYPE_PRESETS.firstOrNull { it.extension == typedExt }
                    if (matchedPreset != null) {
                        selectedPreset = matchedPreset
                    }
                },
                label = { Text("File Name & Extension") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && fileNameField.selection.collapsed) {
                            val stemLen = fileNameField.text
                                .substringBeforeLast('.', fileNameField.text)
                                .length
                                .let { if (it > 0) it else fileNameField.text.length }
                            fileNameField = fileNameField.copy(selection = TextRange(0, stemLen))
                        }
                    }
            )

            // 2. File Type & Extension Selector Grid
            Text(
                text = stringResource(R.string.status_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NEW_DOCUMENT_FILE_TYPE_PRESETS.forEach { preset ->
                    val isSelected = selectedPreset.id == preset.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectPreset(preset) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        } else null,
                        label = { Text(preset.label, fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider()

            // 3. Encoding & Line Ending Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.status_encoding),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DocumentEncoding.entries.forEach { enc ->
                            FilterChip(
                                selected = selectedEncoding == enc,
                                onClick = { selectedEncoding = enc },
                                label = { Text(enc.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.status_line_ending),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LineEnding.entries.forEach { le ->
                            FilterChip(
                                selected = selectedLineEnding == le,
                                onClick = { selectedLineEnding = le },
                                label = { Text(le.badge, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // 4. Optional Starter Boilerplate Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { useStarterTemplate = !useStarterTemplate }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Insert Starter Template for .${selectedPreset.extension}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Pre-populate new file with standard ${selectedPreset.language.displayName} boilerplate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useStarterTemplate,
                    onCheckedChange = { useStarterTemplate = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { submitCreate() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${stringResource(R.string.action_new_file)} (${selectedPreset.badge})")
                }
            }
        }
    }

    if (isCompactMobile) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(680.dp)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

// =========================================================================
// 10. GOOGLE PLAY PROTECT–COMPATIBLE AUTO-UPDATE CENTER MODAL
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateModal(
    isCompactMobile: Boolean,
    installedVersionName: String,
    distributionChannel: UpdateDistributionChannel,
    updateStatus: AppUpdateCheckStatus,
    latestRelease: AppReleaseInfo?,
    downloadProgress: Int,
    verifiedSha256: String?,
    errorMessage: String?,
    autoCheckOnStartup: Boolean,
    includePreReleases: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onIncludePreReleasesChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenReleaseInBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GravityEdit Auto-Update Center",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100% Google Play Protect & Play Store Policy Compliant",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            // 1. Distribution Channel & Play Protect Security Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Update Channel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = GravityEditThemeDefaults.pillShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "PLAY PROTECT VERIFIED",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = distributionChannel.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = distributionChannel.policyDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Installed vs. Latest Version Status Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Installed Version",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v$installedVersionName",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Latest Release",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = latestRelease?.tagName ?: "v$installedVersionName",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (updateStatus == AppUpdateCheckStatus.UPDATE_AVAILABLE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }

                    val statusLabel = when (updateStatus) {
                        AppUpdateCheckStatus.IDLE ->
                            "Ready to check for signed releases over HTTPS."
                        AppUpdateCheckStatus.CHECKING ->
                            "Querying GitHub Releases HTTPS API…"
                        AppUpdateCheckStatus.UP_TO_DATE ->
                            "✓ GravityEdit v$installedVersionName is up to date."
                        AppUpdateCheckStatus.UPDATE_AVAILABLE ->
                            "⬆ New release ${latestRelease?.tagName ?: ""} is available for verification & installation."
                        AppUpdateCheckStatus.DOWNLOADING ->
                            "Downloading APK over TLS 1.3 HTTPS ($downloadProgress%) & verifying SHA-256…"
                        AppUpdateCheckStatus.READY_TO_INSTALL ->
                            "✓ SHA-256 & APK structure verified. Handed off to Android System PackageInstaller."
                        AppUpdateCheckStatus.ERROR ->
                            errorMessage ?: "Unable to complete update check."
                    }

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateStatus == AppUpdateCheckStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )

                    if (updateStatus == AppUpdateCheckStatus.CHECKING) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (updateStatus == AppUpdateCheckStatus.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val shaToDisplay = verifiedSha256 ?: latestRelease?.expectedSha256
                    if (!shaToDisplay.isNullOrBlank()) {
                        Text(
                            text = "SHA-256: $shaToDisplay",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. Release Notes Preview (if available)
            if (latestRelease != null && latestRelease.releaseNotes.isNotBlank()) {
                Text(
                    text = "Release Notes (${latestRelease.releaseTitle})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 140.dp)
                ) {
                    Text(
                        text = latestRelease.releaseNotes,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            HorizontalDivider()

            // 4. Auto-Update Preferences
            SettingSwitchRow(
                label = "Automatically check for updates on app startup",
                checked = autoCheckOnStartup,
                onCheckedChange = onAutoCheckChange
            )
            SettingSwitchRow(
                label = "Include Pre-Release / Beta builds",
                checked = includePreReleases,
                onCheckedChange = onIncludePreReleasesChange
            )

            // 5. Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenReleaseInBrowser) {
                    Text("View on GitHub / Play Store")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCheckNow,
                        shape = GravityEditThemeDefaults.pillShape,
                        enabled = updateStatus != AppUpdateCheckStatus.CHECKING &&
                            updateStatus != AppUpdateCheckStatus.DOWNLOADING
                    ) {
                        Text("Check Now")
                    }

                    Button(
                        onClick = onDownloadAndInstall,
                        shape = GravityEditThemeDefaults.pillShape,
                        enabled = updateStatus != AppUpdateCheckStatus.DOWNLOADING
                    ) {
                        Text(
                            when (distributionChannel) {
                                UpdateDistributionChannel.GOOGLE_PLAY_STORE ->
                                    "Update via Google Play"
                                UpdateDistributionChannel.GITHUB_HTTPS_BROWSER_HANDOFF ->
                                    if (updateStatus == AppUpdateCheckStatus.UPDATE_AVAILABLE) {
                                        "Download Update (${latestRelease?.tagName ?: "Latest"})"
                                    } else {
                                        "Open Latest Release"
                                    }
                            }
                        )
                    }
                }
            }
        }
    }

    if (isCompactMobile) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = GravityEditThemeDefaults.sheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = GravityEditThemeDefaults.dialogShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(680.dp)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

