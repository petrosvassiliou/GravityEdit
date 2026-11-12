package com.gravityedit.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravityedit.app.R
import com.gravityedit.app.domain.engine.AppUpdateCheckStatus
import com.gravityedit.app.domain.engine.PowerShellEngine
import com.gravityedit.app.domain.model.AstSymbol
import com.gravityedit.app.domain.model.DiskBrowserNode
import com.gravityedit.app.domain.model.DocumentEncoding
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.SplitViewMode
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.domain.model.WorkspaceToolPanel
import com.gravityedit.app.ui.components.AppUpdateModal
import com.gravityedit.app.ui.components.AssistChip
import com.gravityedit.app.ui.components.ClippingsModal
import com.gravityedit.app.ui.components.CommandPaletteModal
import com.gravityedit.app.ui.components.EditorCanvas
import com.gravityedit.app.ui.components.FindReplaceModal
import com.gravityedit.app.ui.components.GoToLineDialog
import com.gravityedit.app.ui.components.NewDocumentDialog
import com.gravityedit.app.ui.components.OutlinedButton
import com.gravityedit.app.ui.components.PowerShellAuxiliarySymbolRow
import com.gravityedit.app.ui.components.PowerShellDiagnosticsModal
import com.gravityedit.app.ui.components.SettingsModal
import com.gravityedit.app.ui.components.SideBySideDiffModal
import com.gravityedit.app.ui.components.TextFactoryModal
import com.gravityedit.app.ui.theme.GravityExpressiveMotion
import com.gravityedit.app.ui.theme.JetBrainsMonoFontFamily
import com.gravityedit.app.ui.viewmodel.GravityEditUiEvent
import com.gravityedit.app.ui.viewmodel.GravityEditUiState
import com.gravityedit.app.ui.viewmodel.GravityEditViewModel
import kotlinx.coroutines.launch

@Composable
fun GravityEditScaffold(
    windowSizeClass: WindowSizeClass,
    viewModel: GravityEditViewModel,
    onLaunchOpenFilePicker: () -> Unit,
    onLaunchSaveAsFilePicker: (documentId: String, suggestedName: String) -> Unit,
    onLaunchMountFolderPicker: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDesktopExpanded = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    var scrollEventTarget by remember { mutableStateOf<GravityEditUiEvent.ScrollToLine?>(null) }
    var functionMenuExpanded by remember { mutableStateOf(false) }
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GravityEditUiEvent.RequestOpenFilePicker -> onLaunchOpenFilePicker()
                is GravityEditUiEvent.RequestSaveAsFilePicker ->
                    onLaunchSaveAsFilePicker(event.documentId, event.suggestedFileName)
                GravityEditUiEvent.RequestMountFolderPicker -> onLaunchMountFolderPicker()
                is GravityEditUiEvent.ScrollToLine -> {
                    scrollEventTarget = event
                }
                is GravityEditUiEvent.ShowStatusToast -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                handleGlobalHardwareShortcuts(
                    event = keyEvent,
                    viewModel = viewModel,
                    onOpenFunctionNavigator = { functionMenuExpanded = true }
                )
            }
    ) {
        if (isDesktopExpanded) {
            GooglebookDesktopLayout(
                uiState = uiState,
                viewModel = viewModel,
                scrollEventTarget = scrollEventTarget,
                functionMenuExpanded = functionMenuExpanded,
                onFunctionMenuExpandedChange = { functionMenuExpanded = it }
            )
        } else {
            AndroidMobileCompactLayout(
                uiState = uiState,
                viewModel = viewModel,
                scrollEventTarget = scrollEventTarget,
                functionMenuExpanded = functionMenuExpanded,
                onFunctionMenuExpandedChange = { functionMenuExpanded = it }
            )
        }

        // Shared Modals & Tool Sheets (Adaptive Expressive 28.dp Dialogs on Desktop / BottomSheets on Mobile)
        if (uiState.showNewDocumentDialog) {
            NewDocumentDialog(
                isCompactMobile = !isDesktopExpanded,
                nextUntitledNumber = uiState.nextUntitledNumber,
                onCreateDocument = { fileName, language, encoding, lineEnding, useStarterTemplate ->
                    viewModel.createNewDocument(
                        initialTitle = fileName,
                        language = language,
                        encoding = encoding,
                        lineEnding = lineEnding,
                        useStarterTemplate = useStarterTemplate
                    )
                },
                onDismiss = { viewModel.setNewDocumentDialogVisible(false) }
            )
        }

        if (uiState.showFindReplaceModal) {
            FindReplaceModal(
                isCompactMobile = !isDesktopExpanded,
                initialTab = uiState.findReplaceInitialTab,
                searchOptions = uiState.searchOptions,
                activeDocMatches = uiState.activeDocSearchMatches,
                currentMatchIndex = uiState.currentMatchIndex,
                multiFileMatches = uiState.multiFileSearchMatches,
                isMultiFileSearching = uiState.isMultiFileSearching,
                playgroundSampleText = uiState.playgroundSampleText,
                playgroundEvaluation = uiState.playgroundEvaluation,
                onUpdateOptions = viewModel::updateSearchOptions,
                onFindNext = viewModel::findNextMatch,
                onFindPrev = viewModel::findPreviousMatch,
                onReplaceCurrent = viewModel::replaceCurrentMatch,
                onReplaceAll = viewModel::replaceAllInActiveDocument,
                onExtractToNewFile = viewModel::extractMatchesToNewDocument,
                onRunMultiFileSearch = viewModel::runMultiFileSearch,
                onRunMultiFileReplace = viewModel::runMultiFileReplace,
                onOpenMultiFileMatch = { uriStr, lineNum ->
                    viewModel.openFileFromUri(android.net.Uri.parse(uriStr), jumpToLineNumber = lineNum)
                    viewModel.setFindReplaceModalVisible(false)
                },
                onJumpToMatchInActiveDoc = { match ->
                    viewModel.jumpToLine(match.lineNumber)
                },
                onUpdatePlaygroundSample = viewModel::updatePlaygroundSampleText,
                onDismiss = { viewModel.setFindReplaceModalVisible(false) }
            )
        }

        if (uiState.showTextFactoryModal) {
            TextFactoryModal(
                isCompactMobile = !isDesktopExpanded,
                steps = uiState.textFactorySteps,
                onAddStep = viewModel::addTextFactoryStep,
                onUpdateStep = viewModel::updateTextFactoryStep,
                onRemoveStep = viewModel::removeTextFactoryStep,
                onMoveStepUp = viewModel::moveTextFactoryStepUp,
                onMoveStepDown = viewModel::moveTextFactoryStepDown,
                onRunPipeline = viewModel::runTextFactoryPipelineOnActiveDocument,
                onDismiss = { viewModel.setTextFactoryModalVisible(false) }
            )
        }

        if (uiState.showDiffModal) {
            SideBySideDiffModal(
                isCompactMobile = !isDesktopExpanded,
                documents = uiState.documents,
                diffResult = uiState.diffResult,
                onSelectLeftDoc = viewModel::setDiffLeftDocument,
                onSelectRightDoc = viewModel::setDiffRightDocument,
                onMergeToLeft = viewModel::mergeDiffRowToLeft,
                onMergeToRight = viewModel::mergeDiffRowToRight,
                onDismiss = { viewModel.setDiffModalVisible(false) }
            )
        }

        if (uiState.showClippingsModal) {
            ClippingsModal(
                isCompactMobile = !isDesktopExpanded,
                clippings = uiState.clippings,
                searchQuery = uiState.clippingSearchQuery,
                onSearchQueryChange = viewModel::setClippingSearchQuery,
                onInsertClipping = viewModel::insertClippingIntoActiveDocument,
                onAddCustomClipping = viewModel::addCustomClipping,
                onDismiss = { viewModel.setClippingsModalVisible(false) }
            )
        }

        if (uiState.showDiagnosticsModal) {
            PowerShellDiagnosticsModal(
                isCompactMobile = !isDesktopExpanded,
                diagnostics = uiState.diagnostics,
                onJumpToLine = viewModel::jumpToLine,
                onApplyQuickFix = viewModel::applyDiagnosticQuickFix,
                onApplyAllQuickFixes = viewModel::applyAllDiagnosticQuickFixes,
                onDismiss = { viewModel.setDiagnosticsModalVisible(false) }
            )
        }

        if (uiState.showCommandPaletteModal) {
            CommandPaletteModal(
                isCompactMobile = !isDesktopExpanded,
                documents = uiState.documents,
                astSymbols = uiState.astSymbols,
                clippings = uiState.clippings,
                onSelectDocument = viewModel::selectDocument,
                onJumpToSymbol = viewModel::jumpToAstSymbol,
                onRunQuickTransform = viewModel::runSingleQuickTransform,
                onInsertClipping = { clip -> viewModel.insertClippingIntoActiveDocument(clip) },
                onOpenToolModal = { tool ->
                    when (tool) {
                        "newdoc" -> viewModel.setNewDocumentDialogVisible(true)
                        "update" -> viewModel.setUpdateModalVisible(true)
                        "diagnostics" -> viewModel.setDiagnosticsModalVisible(true)
                        "find" -> viewModel.setFindReplaceModalVisible(true, 0)
                        "multifind" -> viewModel.setFindReplaceModalVisible(true, 1)
                        "factory" -> viewModel.setTextFactoryModalVisible(true)
                        "diff" -> viewModel.setDiffModalVisible(true)
                        "clippings" -> viewModel.setClippingsModalVisible(true)
                        "gotoline" -> viewModel.setGoToLineDialogVisible(true)
                        "settings" -> viewModel.setSettingsDialogVisible(true)
                    }
                },
                onSelectTheme = viewModel::setThemeMode,
                onSelectLocale = viewModel::setApplicationLanguage,
                onDismiss = { viewModel.setCommandPaletteModalVisible(false) }
            )
        }

        if (uiState.showGoToLineDialog) {
            val focusedDoc = uiState.focusedDocument
            if (focusedDoc != null) {
                val (currentLn, _) = focusedDoc.cursorLineAndColumn()
                GoToLineDialog(
                    maxLineCount = focusedDoc.lineCount,
                    currentLine = currentLn,
                    onGoToLine = viewModel::jumpToLine,
                    onDismiss = { viewModel.setGoToLineDialogVisible(false) }
                )
            }
        }

        if (uiState.showSettingsDialog) {
            SettingsModal(
                isCompactMobile = !isDesktopExpanded,
                themeMode = uiState.themeMode,
                appLocaleTag = uiState.appLocaleTag,
                fontSizeSp = uiState.fontSizeSp,
                tabWidth = uiState.tabWidth,
                softWrap = uiState.softWrap,
                showInvisibles = uiState.showInvisibles,
                showPageGuide80 = uiState.showPageGuide80,
                showPageGuide120 = uiState.showPageGuide120,
                enableCodeFolding = uiState.enableCodeFolding,
                installedVersionName = cleanVersionLabel(uiState.installedVersionName),
                autoCheckUpdates = uiState.autoCheckUpdatesOnStartup,
                onSelectTheme = viewModel::setThemeMode,
                onSelectLanguage = viewModel::setApplicationLanguage,
                onFontSizeChange = viewModel::setFontSizeSp,
                onTabWidthChange = viewModel::setTabWidth,
                onSoftWrapChange = viewModel::setSoftWrap,
                onShowInvisiblesChange = viewModel::setShowInvisibles,
                onPageGuide80Change = viewModel::setShowPageGuide80,
                onPageGuide120Change = viewModel::setShowPageGuide120,
                onCodeFoldingChange = viewModel::setEnableCodeFolding,
                onAutoCheckUpdatesChange = viewModel::setAutoCheckUpdatesOnStartup,
                onOpenUpdateModal = { viewModel.setUpdateModalVisible(true) },
                onDismiss = { viewModel.setSettingsDialogVisible(false) }
            )
        }

        if (uiState.showUpdateModal) {
            AppUpdateModal(
                isCompactMobile = !isDesktopExpanded,
                installedVersionName = cleanVersionLabel(uiState.installedVersionName),
                distributionChannel = uiState.updateDistributionChannel,
                updateStatus = uiState.updateCheckStatus,
                latestRelease = uiState.latestReleaseInfo,
                downloadProgress = uiState.updateDownloadProgress,
                verifiedSha256 = uiState.verifiedApkSha256,
                errorMessage = uiState.updateErrorMessage,
                autoCheckOnStartup = uiState.autoCheckUpdatesOnStartup,
                includePreReleases = uiState.updateIncludePreReleases,
                onAutoCheckChange = viewModel::setAutoCheckUpdatesOnStartup,
                onIncludePreReleasesChange = viewModel::setUpdateIncludePreReleases,
                onCheckNow = { viewModel.checkForAppUpdates(silentIfUpToDate = false) },
                onDownloadAndInstall = viewModel::downloadAndInstallUpdate,
                onOpenReleaseInBrowser = viewModel::openLatestReleaseInBrowser,
                onDismiss = { viewModel.setUpdateModalVisible(false) }
            )
        }
    }
}

private fun cleanVersionLabel(rawVersionName: String): String {
    return rawVersionName.removeSuffix("-debug").ifBlank { "1.2.0" }
}

/**
 * Hardware Keyboard Shortcut Handler for Googlebook OS & Desktop Keyboards (`Modifier.onPreviewKeyEvent`):
 * - `Ctrl+N`: New Document
 * - `Ctrl+O`: Open File
 * - `Ctrl+S`: Save
 * - `Ctrl+Shift+S`: Save As
 * - `Ctrl+W`: Close Tab
 * - `Ctrl+P` / `Ctrl+Shift+P`: Command Palette & Fuzzy Quick-Open Switcher
 * - `Ctrl+F`: Find/Replace Dialog
 * - `Ctrl+Shift+F`: Multi-File Search
 * - `Ctrl+G`: Go to Line
 * - `Ctrl+J`: Jump to Function (Function Navigator)
 * - `Ctrl+D`: Duplicate Line
 * - `Ctrl+L`: Select Line
 * - `Ctrl+/`: Toggle PowerShell Comment `#`
 * - `Ctrl++` / `Ctrl+-` / `Ctrl+0`: Zoom In / Out / Reset 100%
 */
private fun handleGlobalHardwareShortcuts(
    event: KeyEvent,
    viewModel: GravityEditViewModel,
    onOpenFunctionNavigator: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    if (!ctrl) return false
    val shift = event.isShiftPressed

    return when (event.key) {
        Key.P -> {
            viewModel.setCommandPaletteModalVisible(true)
            true
        }
        Key.N -> {
            viewModel.setNewDocumentDialogVisible(true)
            true
        }
        Key.O -> {
            viewModel.requestOpenFile()
            true
        }
        Key.S -> {
            if (shift) {
                viewModel.requestSaveAsActiveDocument()
            } else {
                viewModel.requestSaveActiveDocument()
            }
            true
        }
        Key.W -> {
            val activeId = viewModel.uiState.value.focusedDocument?.id
            if (activeId != null) {
                viewModel.closeDocument(activeId)
            }
            true
        }
        Key.F -> {
            if (shift) {
                viewModel.setFindReplaceModalVisible(true, initialTab = 1)
            } else {
                viewModel.setFindReplaceModalVisible(true, initialTab = 0)
            }
            true
        }
        Key.G -> {
            viewModel.setGoToLineDialogVisible(true)
            true
        }
        Key.J -> {
            onOpenFunctionNavigator()
            true
        }
        Key.D -> {
            viewModel.duplicateCurrentLine()
            true
        }
        Key.L -> {
            viewModel.selectCurrentLine()
            true
        }
        Key.Slash -> {
            viewModel.toggleCommentOnSelection()
            true
        }
        Key.Z -> {
            if (shift) viewModel.redo() else viewModel.undo()
            true
        }
        Key.Y -> {
            viewModel.redo()
            true
        }
        Key.Equals, Key.Plus, Key.NumPadAdd -> {
            viewModel.adjustZoom(10)
            true
        }
        Key.Minus, Key.NumPadSubtract -> {
            viewModel.adjustZoom(-10)
            true
        }
        Key.Zero, Key.NumPad0 -> {
            viewModel.setZoomPercent(100)
            true
        }
        else -> false
    }
}

// =========================================================================
// A. GOOGLEBOOK OS DESKTOP / EXPANDED MULTI-PANE EXPRESSIVE LAYOUT
// =========================================================================

@Composable
private fun GooglebookDesktopLayout(
    uiState: GravityEditUiState,
    viewModel: GravityEditViewModel,
    scrollEventTarget: GravityEditUiEvent.ScrollToLine?,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val railItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Upper Multi-Pane Workspace Row
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 1. Leftmost Material 3 Expressive NavigationRail
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxHeight()
            ) {
                NavigationRailItem(
                    selected = uiState.activeToolPanel == WorkspaceToolPanel.DISK_BROWSER && uiState.isLeftSidebarVisible,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.DISK_BROWSER) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.nav_disk_browser)) },
                    label = { Text(stringResource(R.string.nav_disk_browser), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = uiState.activeToolPanel == WorkspaceToolPanel.OPEN_DOCUMENTS && uiState.isLeftSidebarVisible,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.OPEN_DOCUMENTS) },
                    icon = {
                        Box {
                            Icon(Icons.Default.Description, contentDescription = stringResource(R.string.nav_open_documents))
                            if (uiState.documents.any { it.isDirty }) {
                                Badge(modifier = Modifier.align(Alignment.TopEnd))
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.nav_open_documents), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = uiState.showTextFactoryModal,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.TEXT_FACTORY) },
                    icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = stringResource(R.string.nav_text_factory)) },
                    label = { Text(stringResource(R.string.nav_text_factory), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = uiState.showFindReplaceModal,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.FIND_REPLACE) },
                    icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_find_replace)) },
                    label = { Text(stringResource(R.string.nav_find_replace), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = uiState.showDiffModal,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.DIFF_TOOL) },
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = stringResource(R.string.nav_diff_tool)) },
                    label = { Text(stringResource(R.string.nav_diff_tool), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = uiState.showClippingsModal,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.CLIPPINGS) },
                    icon = { Icon(Icons.Default.Code, contentDescription = stringResource(R.string.nav_clippings)) },
                    label = { Text(stringResource(R.string.nav_clippings), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
                NavigationRailItem(
                    selected = uiState.focusedDocument?.isScratchpad == true,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.SCRATCHPAD) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = stringResource(R.string.nav_scratchpad)) },
                    label = { Text(stringResource(R.string.nav_scratchpad), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
                Spacer(modifier = Modifier.weight(1f))
                NavigationRailItem(
                    selected = uiState.showSettingsDialog,
                    onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.SETTINGS) },
                    icon = {
                        Box {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                            if (uiState.updateCheckStatus == AppUpdateCheckStatus.UPDATE_AVAILABLE) {
                                Badge(modifier = Modifier.align(Alignment.TopEnd))
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.nav_settings), maxLines = 1, fontSize = 10.sp) },
                    colors = railItemColors
                )
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 2. Resizable Left Sidebar (Disk Browser & Open Documents)
            if (uiState.isLeftSidebarVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .width(uiState.leftSidebarWidthDp.dp)
                        .fillMaxHeight()
                ) {
                    LeftSidebarContent(
                        uiState = uiState,
                        onSelectDocument = viewModel::selectDocument,
                        onRenameDocument = viewModel::renameDocument,
                        onSaveDocument = viewModel::requestSaveDocument,
                        onCloseDocument = viewModel::closeDocument,
                        onCloseOtherDocuments = viewModel::closeOtherDocuments,
                        onNewDocument = { viewModel.setNewDocumentDialogVisible(true) },
                        onMountFolder = viewModel::requestMountProjectFolder,
                        onRefreshFolder = viewModel::refreshDiskBrowser,
                        onToggleDirectoryNode = viewModel::toggleDirectoryNode,
                        onSwitchToScratchpad = viewModel::switchToScratchpad
                    )
                }

                // Interactive Drag-Resize Handle for Googlebook OS Sidebar
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaDp = with(density) { dragAmount.toDp().value }
                                viewModel.updateLeftSidebarWidth(deltaDp)
                            }
                        }
                )
            }

            // 3. Center Desktop Workspace (Expressive Top Action Bar + Pill Tab Bar + Single/Split Editor)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                DesktopTopActionBar(
                    uiState = uiState,
                    viewModel = viewModel,
                    functionMenuExpanded = functionMenuExpanded,
                    onFunctionMenuExpandedChange = onFunctionMenuExpandedChange
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                MultiDocumentTabBar(
                    documents = uiState.documents,
                    activeDocumentId = uiState.focusedDocument?.id ?: uiState.activeDocumentId,
                    onSelectTab = viewModel::selectDocument,
                    onRenameTab = viewModel::renameDocument,
                    onCloseTab = viewModel::closeDocument,
                    onCloseOtherTabs = viewModel::closeOtherDocuments,
                    onMoveTab = viewModel::moveTab,
                    onSaveTab = viewModel::requestSaveDocument,
                    onNewTab = { viewModel.setNewDocumentDialogVisible(true) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Center Single or Split-View Editor Workspace
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AdaptiveEditorSplitWorkspace(
                        uiState = uiState,
                        scrollEventTarget = scrollEventTarget,
                        onFocusPane = viewModel::setFocusedPane,
                        onContentChange = viewModel::updateDocumentContent,
                        onSelectionChange = viewModel::updateDocumentSelection,
                        onToggleFold = viewModel::toggleFoldAtLine,
                        onOpenDiagnostics = { viewModel.setDiagnosticsModalVisible(true) }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Full-Width Expressive Bottom Status Bar with Bottom-Left Corner Version Badge
        ExpressiveBottomStatusBar(
            uiState = uiState,
            isCompactMobile = false,
            onSelectEncoding = viewModel::setDocumentEncoding,
            onSelectLineEnding = viewModel::setDocumentLineEnding,
            onSelectLanguage = viewModel::setDocumentLanguage,
            onOpenGoToLine = { viewModel.setGoToLineDialogVisible(true) },
            onOpenDiagnostics = { viewModel.setDiagnosticsModalVisible(true) },
            onOpenCommandPalette = { viewModel.setCommandPaletteModalVisible(true) },
            onOpenUpdateModal = { viewModel.setUpdateModalVisible(true) },
            onAdjustZoom = viewModel::adjustZoom,
            onResetZoom = { viewModel.setZoomPercent(100) }
        )
    }
}

// =========================================================================
// B. ANDROID MOBILE MODE (COMPACT / PHONE & PORTRAIT TABLET)
// =========================================================================

@Composable
private fun AndroidMobileCompactLayout(
    uiState: GravityEditUiState,
    viewModel: GravityEditViewModel,
    scrollEventTarget: GravityEditUiEvent.ScrollToLine?,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.width(316.dp)
            ) {
                LeftSidebarContent(
                    uiState = uiState,
                    onSelectDocument = { docId ->
                        viewModel.selectDocument(docId)
                        scope.launch { drawerState.close() }
                    },
                    onRenameDocument = viewModel::renameDocument,
                    onSaveDocument = viewModel::requestSaveDocument,
                    onCloseDocument = viewModel::closeDocument,
                    onCloseOtherDocuments = viewModel::closeOtherDocuments,
                    onNewDocument = {
                        scope.launch { drawerState.close() }
                        viewModel.setNewDocumentDialogVisible(true)
                    },
                    onMountFolder = {
                        viewModel.requestMountProjectFolder()
                        scope.launch { drawerState.close() }
                    },
                    onRefreshFolder = viewModel::refreshDiskBrowser,
                    onToggleDirectoryNode = { node ->
                        viewModel.toggleDirectoryNode(node)
                        if (!node.isDirectory) {
                            scope.launch { drawerState.close() }
                        }
                    },
                    onSwitchToScratchpad = {
                        viewModel.switchToScratchpad()
                        scope.launch { drawerState.close() }
                    },
                    showMobileToolShortcuts = true,
                    onOpenMobileTool = { panel ->
                        scope.launch { drawerState.close() }
                        viewModel.selectToolPanel(panel)
                    },
                    onOpenDiagnostics = {
                        scope.launch { drawerState.close() }
                        viewModel.setDiagnosticsModalVisible(true)
                    },
                    onOpenCommandPalette = {
                        scope.launch { drawerState.close() }
                        viewModel.setCommandPaletteModalVisible(true)
                    },
                    onOpenUpdates = {
                        scope.launch { drawerState.close() }
                        viewModel.setUpdateModalVisible(true)
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Expressive Mobile Top Bar with Drawer Toggle, Function Navigator Pill, Quick Actions & Overflow Menu
            MobileTopActionBar(
                uiState = uiState,
                viewModel = viewModel,
                functionMenuExpanded = functionMenuExpanded,
                onFunctionMenuExpandedChange = onFunctionMenuExpandedChange,
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Unified Material 3 Expressive Multi-Document Pill Tab Bar (with Long-Press Context Menu + Rename)
            MultiDocumentTabBar(
                documents = uiState.documents,
                activeDocumentId = uiState.focusedDocument?.id ?: uiState.activeDocumentId,
                onSelectTab = viewModel::selectDocument,
                onRenameTab = viewModel::renameDocument,
                onCloseTab = viewModel::closeDocument,
                onCloseOtherTabs = viewModel::closeOtherDocuments,
                onMoveTab = viewModel::moveTab,
                onSaveTab = viewModel::requestSaveDocument,
                onNewTab = { viewModel.setNewDocumentDialogVisible(true) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Center Virtualized Editor Canvas
            val activeDoc = uiState.focusedDocument
            if (activeDoc != null) {
                val mobileScroll = scrollEventTarget?.takeIf { it.documentId == activeDoc.id }
                EditorCanvas(
                    document = activeDoc,
                    foldRegions = uiState.foldRegions,
                    astSymbols = uiState.astSymbols,
                    diagnostics = uiState.diagnostics,
                    onOpenDiagnostics = { viewModel.setDiagnosticsModalVisible(true) },
                    searchMatches = uiState.activeDocSearchMatches,
                    activeSearchMatchIndex = uiState.currentMatchIndex,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = true,
                    scrollTargetLine = mobileScroll?.lineIndex,
                    scrollRequestId = mobileScroll?.requestId ?: 0L,
                    onFocusPane = { viewModel.setFocusedPane(0) },
                    onContentChange = { text, sStart, sEnd ->
                        viewModel.updateDocumentContent(activeDoc.id, text, sStart, sEnd)
                    },
                    onSelectionChange = { sStart, sEnd ->
                        viewModel.updateDocumentSelection(activeDoc.id, sStart, sEnd)
                    },
                    onToggleFold = { line ->
                        viewModel.toggleFoldAtLine(activeDoc.id, line)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Sticky PowerShell Auxiliary Symbol Row (positioned above the Bottom Status Bar)
            PowerShellAuxiliarySymbolRow(
                canUndo = uiState.focusedDocument?.undoStack?.isNotEmpty() == true,
                canRedo = uiState.focusedDocument?.redoStack?.isNotEmpty() == true,
                onInsertToken = viewModel::insertAuxiliaryToken,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onIndent = viewModel::indentSelection,
                onUnindent = viewModel::unindentSelection,
                applyImePadding = false
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Full-Width Expressive Bottom Status Bar with Pinned Bottom-Left Version Number
            ExpressiveBottomStatusBar(
                uiState = uiState,
                isCompactMobile = true,
                onSelectEncoding = viewModel::setDocumentEncoding,
                onSelectLineEnding = viewModel::setDocumentLineEnding,
                onSelectLanguage = viewModel::setDocumentLanguage,
                onOpenGoToLine = { viewModel.setGoToLineDialogVisible(true) },
                onOpenDiagnostics = { viewModel.setDiagnosticsModalVisible(true) },
                onOpenCommandPalette = { viewModel.setCommandPaletteModalVisible(true) },
                onOpenUpdateModal = { viewModel.setUpdateModalVisible(true) },
                onAdjustZoom = viewModel::adjustZoom,
                onResetZoom = { viewModel.setZoomPercent(100) }
            )
        }
    }
}

// =========================================================================
// SHARED EXPRESSIVE SUBCOMPONENTS: SIDEBAR, TOP BARS, OVERFLOW MENU, TAB BAR & STATUS BAR
// =========================================================================

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LeftSidebarContent(
    uiState: GravityEditUiState,
    onSelectDocument: (String) -> Unit,
    onRenameDocument: (docId: String, newTitle: String) -> Unit,
    onSaveDocument: (String) -> Unit,
    onCloseDocument: (String) -> Unit,
    onCloseOtherDocuments: (String) -> Unit,
    onNewDocument: () -> Unit,
    onMountFolder: () -> Unit,
    onRefreshFolder: () -> Unit,
    onToggleDirectoryNode: (DiskBrowserNode) -> Unit,
    onSwitchToScratchpad: () -> Unit,
    showMobileToolShortcuts: Boolean = false,
    onOpenMobileTool: ((WorkspaceToolPanel) -> Unit)? = null,
    onOpenDiagnostics: (() -> Unit)? = null,
    onOpenCommandPalette: (() -> Unit)? = null,
    onOpenUpdates: (() -> Unit)? = null
) {
    var editingSidebarDocId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Section 1: Currently Open Documents & Persistent Scratchpad
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_open_documents),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSwitchToScratchpad, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = stringResource(R.string.nav_scratchpad),
                        modifier = Modifier.size(16.dp)
                    )
                }
                FilledTonalIconButton(onClick = onNewDocument, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_new_file),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            uiState.documents.forEach { doc ->
                val isSelected = doc.id == uiState.focusedDocument?.id
                var docMenuExpanded by remember { mutableStateOf(false) }

                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelected && !doc.isScratchpad) {
                                        editingSidebarDocId = doc.id
                                    } else {
                                        editingSidebarDocId = null
                                        onSelectDocument(doc.id)
                                    }
                                },
                                onLongClick = { docMenuExpanded = true }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (doc.isScratchpad) {
                                    Icons.AutoMirrored.Filled.Notes
                                } else {
                                    Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                InlineEditableDocumentTitle(
                                    title = doc.title,
                                    isEditing = editingSidebarDocId == doc.id,
                                    isSelected = isSelected,
                                    onStartEditing = {
                                        if (!doc.isScratchpad) {
                                            onSelectDocument(doc.id)
                                            editingSidebarDocId = doc.id
                                        } else {
                                            onSelectDocument(doc.id)
                                        }
                                    },
                                    onCommitRename = { newName ->
                                        editingSidebarDocId = null
                                        onRenameDocument(doc.id, newName)
                                    },
                                    onCancelRename = {
                                        editingSidebarDocId = null
                                    }
                                )
                            }
                            if (doc.isDirty) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            if (!doc.isScratchpad && uiState.documents.size > 1) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close_tab),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .clickable { onCloseDocument(doc.id) }
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = docMenuExpanded,
                        onDismissRequest = { docMenuExpanded = false }
                    ) {
                        if (!doc.isScratchpad) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename_document)) },
                                onClick = {
                                    docMenuExpanded = false
                                    onSelectDocument(doc.id)
                                    editingSidebarDocId = doc.id
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save)) },
                            onClick = {
                                docMenuExpanded = false
                                onSaveDocument(doc.id)
                            }
                        )
                        if (!doc.isScratchpad && uiState.documents.size > 1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_close_tab)) },
                                onClick = {
                                    docMenuExpanded = false
                                    onCloseDocument(doc.id)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_close_other_tabs)) },
                            onClick = {
                                docMenuExpanded = false
                                onCloseOtherDocuments(doc.id)
                            }
                        )
                    }
                }
            }
        }

        if (showMobileToolShortcuts && onOpenMobileTool != null) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { onOpenMobileTool(WorkspaceToolPanel.TEXT_FACTORY) },
                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(stringResource(R.string.nav_text_factory), fontSize = 11.5.sp) },
                    shape = CircleShape
                )
                AssistChip(
                    onClick = { onOpenMobileTool(WorkspaceToolPanel.DIFF_TOOL) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(stringResource(R.string.nav_diff_tool), fontSize = 11.5.sp) },
                    shape = CircleShape
                )
                AssistChip(
                    onClick = { onOpenMobileTool(WorkspaceToolPanel.CLIPPINGS) },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(stringResource(R.string.nav_clippings), fontSize = 11.5.sp) },
                    shape = CircleShape
                )
                AssistChip(
                    onClick = { onOpenMobileTool(WorkspaceToolPanel.FIND_REPLACE) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(stringResource(R.string.nav_find_replace), fontSize = 11.5.sp) },
                    shape = CircleShape
                )
                if (onOpenDiagnostics != null) {
                    AssistChip(
                        onClick = onOpenDiagnostics,
                        leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text(stringResource(R.string.action_diagnostics), fontSize = 11.5.sp) },
                        shape = CircleShape
                    )
                }
                if (onOpenCommandPalette != null) {
                    AssistChip(
                        onClick = onOpenCommandPalette,
                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text(stringResource(R.string.action_command_palette), fontSize = 11.5.sp) },
                        shape = CircleShape
                    )
                }
                AssistChip(
                    onClick = { onOpenMobileTool(WorkspaceToolPanel.SETTINGS) },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(stringResource(R.string.nav_settings), fontSize = 11.5.sp) },
                    shape = CircleShape
                )
                if (onOpenUpdates != null) {
                    AssistChip(
                        onClick = onOpenUpdates,
                        leadingIcon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("v${cleanVersionLabel(uiState.installedVersionName)}", fontSize = 11.5.sp) },
                        shape = CircleShape
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        // Section 2: Disk Browser (SAF Mounted Directory Tree)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(R.string.nav_disk_browser)}: ${uiState.diskBrowserRootName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row {
                IconButton(onClick = onRefreshFolder, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh_folder),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onMountFolder, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.action_open_folder),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (uiState.diskBrowserNodes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.empty_folder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onMountFolder,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_open_folder), fontSize = 12.sp)
                    }
                }
            }
        } else {
            val flattenedTree = remember(uiState.diskBrowserNodes) {
                flattenDiskNodes(uiState.diskBrowserNodes)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(flattenedTree, key = { it.uriString }) { node ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleDirectoryNode(node) }
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = (node.depth * 14 + 6).dp,
                                end = 8.dp,
                                top = 5.dp,
                                bottom = 5.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (node.isDirectory) {
                                Icon(
                                    imageVector = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Icon(
                                    imageVector = if (node.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(15.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodySmall,
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

private fun flattenDiskNodes(nodes: List<DiskBrowserNode>): List<DiskBrowserNode> {
    val result = ArrayList<DiskBrowserNode>()
    fun recurse(list: List<DiskBrowserNode>) {
        for (node in list) {
            result.add(node)
            if (node.isDirectory && node.isExpanded && node.children.isNotEmpty()) {
                recurse(node.children)
            }
        }
    }
    recurse(nodes)
    return result
}

@Composable
private fun MobileTopActionBar(
    uiState: GravityEditUiState,
    viewModel: GravityEditViewModel,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit
) {
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_disk_browser))
            }

            // Expressive Function Navigator Pill Dropdown
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(CircleShape)
                        .clickable { onFunctionMenuExpandedChange(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.astSymbols.firstOrNull()?.name
                                ?: "${stringResource(R.string.function_navigator)} (${uiState.astSymbols.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                FunctionNavigatorDropdownMenu(
                    expanded = functionMenuExpanded,
                    symbols = uiState.astSymbols,
                    onDismiss = { onFunctionMenuExpandedChange(false) },
                    onSelectSymbol = { sym ->
                        onFunctionMenuExpandedChange(false)
                        viewModel.jumpToAstSymbol(sym)
                    }
                )
            }

            // Connected Tonal Action Cluster on Mobile
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.setNewDocumentDialogVisible(true) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_new_file),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = viewModel::requestOpenFile,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = stringResource(R.string.action_open_file),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = viewModel::requestSaveActiveDocument,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.action_save),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setFindReplaceModalVisible(true, 0) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.nav_find_replace),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Full Workspace Overflow Menu (exposes 100% of File, Edit, Navigation, Tool & Update actions)
            Box {
                IconButton(
                    onClick = { overflowMenuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options)
                    )
                }
                WorkspaceOverflowDropdownMenu(
                    expanded = overflowMenuExpanded,
                    installedVersion = cleanVersionLabel(uiState.installedVersionName),
                    viewModel = viewModel,
                    onDismiss = { overflowMenuExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun DesktopTopActionBar(
    uiState: GravityEditUiState,
    viewModel: GravityEditViewModel,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit
) {
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Expressive File Tonal Pill Cluster
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.setNewDocumentDialogVisible(true) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_file), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = viewModel::requestOpenFile,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.action_open_file), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = viewModel::requestSaveActiveDocument,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = viewModel::requestSaveAsActiveDocument,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.SaveAs, contentDescription = stringResource(R.string.action_save_as), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 2. Expressive Undo / Redo Tonal Pill Cluster
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.focusedDocument?.undoStack?.isNotEmpty() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.focusedDocument?.redoStack?.isNotEmpty() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 3. Expressive AST Function Navigator Pill Dropdown (Ctrl+J)
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .clickable { onFunctionMenuExpandedChange(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.function_navigator)} (${uiState.astSymbols.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                FunctionNavigatorDropdownMenu(
                    expanded = functionMenuExpanded,
                    symbols = uiState.astSymbols,
                    onDismiss = { onFunctionMenuExpandedChange(false) },
                    onSelectSymbol = { sym ->
                        onFunctionMenuExpandedChange(false)
                        viewModel.jumpToAstSymbol(sym)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. Expressive BBEdit Tool Pill Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveToolbarPillButton(
                    icon = Icons.Default.Search,
                    label = stringResource(R.string.nav_find_replace),
                    active = uiState.showFindReplaceModal,
                    onClick = { viewModel.setFindReplaceModalVisible(true, 0) }
                )
                ExpressiveToolbarPillButton(
                    icon = Icons.Default.AutoFixHigh,
                    label = stringResource(R.string.nav_text_factory),
                    active = uiState.showTextFactoryModal,
                    onClick = { viewModel.setTextFactoryModalVisible(true) }
                )
                ExpressiveToolbarPillButton(
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    label = stringResource(R.string.nav_diff_tool),
                    active = uiState.showDiffModal,
                    onClick = { viewModel.setDiffModalVisible(true) }
                )
                ExpressiveToolbarPillButton(
                    icon = Icons.Default.Code,
                    label = stringResource(R.string.nav_clippings),
                    active = uiState.showClippingsModal,
                    onClick = { viewModel.setClippingsModalVisible(true) }
                )
            }

            // 5. Material 3 Expressive Connected Button Group for Split-View Mode
            ExpressiveSplitModeButtonGroup(
                currentMode = uiState.splitViewMode,
                onSelectMode = viewModel::setSplitViewMode
            )

            // 6. Complete Workspace Overflow Menu (Edit, Navigation, Linter, Folder & Update Actions)
            Box {
                IconButton(
                    onClick = { overflowMenuExpanded = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options),
                        modifier = Modifier.size(19.dp)
                    )
                }
                WorkspaceOverflowDropdownMenu(
                    expanded = overflowMenuExpanded,
                    installedVersion = cleanVersionLabel(uiState.installedVersionName),
                    viewModel = viewModel,
                    onDismiss = { overflowMenuExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun ExpressiveToolbarPillButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = GravityExpressiveMotion.effectsDefault(),
        label = "toolbarPillBg"
    )
    val fgColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = GravityExpressiveMotion.effectsDefault(),
        label = "toolbarPillFg"
    )

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 12.sp,
                color = fgColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Authentic Material 3 Expressive Connected Button Group:
 * Outer ends form a generous capsule (`16.dp`), inner seams sit flush (`5.dp`) when unselected,
 * and the selected segment spring-morphs (`GravityExpressiveMotion.spatialFast()`) into a rounded
 * `14.dp` active pill filled with `primaryContainer`.
 */
@Composable
private fun ExpressiveSplitModeButtonGroup(
    currentMode: SplitViewMode,
    onSelectMode: (SplitViewMode) -> Unit
) {
    val modes = listOf(
        Triple(SplitViewMode.NONE, Icons.Default.Splitscreen, R.string.split_none),
        Triple(SplitViewMode.VERTICAL, Icons.Default.ViewColumn, R.string.split_vertical),
        Triple(SplitViewMode.HORIZONTAL, Icons.Default.ViewStream, R.string.split_horizontal)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEachIndexed { index, (mode, icon, contentDescRes) ->
            val selected = currentMode == mode
            val targetStartRadius = when {
                selected -> 14.dp
                index == 0 -> 16.dp
                else -> 5.dp
            }
            val targetEndRadius = when {
                selected -> 14.dp
                index == modes.lastIndex -> 16.dp
                else -> 5.dp
            }
            val startRadius by animateDpAsState(
                targetValue = targetStartRadius,
                animationSpec = GravityExpressiveMotion.spatialFast(),
                label = "splitStartRadius_$index"
            )
            val endRadius by animateDpAsState(
                targetValue = targetEndRadius,
                animationSpec = GravityExpressiveMotion.spatialFast(),
                label = "splitEndRadius_$index"
            )
            val containerColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = GravityExpressiveMotion.effectsDefault(),
                label = "splitContainerColor_$index"
            )
            val segmentShape = RoundedCornerShape(
                topStart = startRadius,
                bottomStart = startRadius,
                topEnd = endRadius,
                bottomEnd = endRadius
            )

            Surface(
                shape = segmentShape,
                color = containerColor,
                modifier = Modifier
                    .height(32.dp)
                    .width(if (selected) 38.dp else 32.dp)
                    .clip(segmentShape)
                    .clickable { onSelectMode(mode) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(contentDescRes),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceOverflowDropdownMenu(
    expanded: Boolean,
    installedVersion: String,
    viewModel: GravityEditViewModel,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp)
    ) {
        // File Operations
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_new_file)) },
            trailingIcon = { ShortcutBadge("Ctrl+N") },
            onClick = {
                onDismiss()
                viewModel.setNewDocumentDialogVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_open_file)) },
            trailingIcon = { ShortcutBadge("Ctrl+O") },
            onClick = {
                onDismiss()
                viewModel.requestOpenFile()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_open_folder)) },
            onClick = {
                onDismiss()
                viewModel.requestMountProjectFolder()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_save)) },
            trailingIcon = { ShortcutBadge("Ctrl+S") },
            onClick = {
                onDismiss()
                viewModel.requestSaveActiveDocument()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_save_as)) },
            trailingIcon = { ShortcutBadge("Ctrl+⇧+S") },
            onClick = {
                onDismiss()
                viewModel.requestSaveAsActiveDocument()
            }
        )

        HorizontalDivider()

        // Line & Code Editing Actions
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_duplicate_line)) },
            trailingIcon = { ShortcutBadge("Ctrl+D") },
            onClick = {
                onDismiss()
                viewModel.duplicateCurrentLine()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_select_line)) },
            trailingIcon = { ShortcutBadge("Ctrl+L") },
            onClick = {
                onDismiss()
                viewModel.selectCurrentLine()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_toggle_comment)) },
            trailingIcon = { ShortcutBadge("Ctrl+/") },
            onClick = {
                onDismiss()
                viewModel.toggleCommentOnSelection()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_indent)) },
            trailingIcon = { ShortcutBadge("Tab") },
            onClick = {
                onDismiss()
                viewModel.indentSelection()
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_unindent)) },
            trailingIcon = { ShortcutBadge("⇧+Tab") },
            onClick = {
                onDismiss()
                viewModel.unindentSelection()
            }
        )

        HorizontalDivider()

        // Navigation & Developer Tools
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.go_to_line_title)) },
            trailingIcon = { ShortcutBadge("Ctrl+G") },
            onClick = {
                onDismiss()
                viewModel.setGoToLineDialogVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_command_palette)) },
            trailingIcon = { ShortcutBadge("Ctrl+P") },
            onClick = {
                onDismiss()
                viewModel.setCommandPaletteModalVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_diagnostics)) },
            onClick = {
                onDismiss()
                viewModel.setDiagnosticsModalVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.nav_text_factory)) },
            onClick = {
                onDismiss()
                viewModel.setTextFactoryModalVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.nav_diff_tool)) },
            onClick = {
                onDismiss()
                viewModel.setDiffModalVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.nav_clippings)) },
            onClick = {
                onDismiss()
                viewModel.setClippingsModalVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.nav_scratchpad)) },
            onClick = {
                onDismiss()
                viewModel.switchToScratchpad()
            }
        )

        HorizontalDivider()

        // Settings & App Version / Updates
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.nav_settings)) },
            onClick = {
                onDismiss()
                viewModel.setSettingsDialogVisible(true)
            }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(stringResource(R.string.action_check_updates)) },
            trailingIcon = { ShortcutBadge("v$installedVersion") },
            onClick = {
                onDismiss()
                viewModel.setUpdateModalVisible(true)
            }
        )
    }
}

@Composable
private fun ShortcutBadge(label: String) {
    Text(
        text = label,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 10.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FunctionNavigatorDropdownMenu(
    expanded: Boolean,
    symbols: List<AstSymbol>,
    onDismiss: () -> Unit,
    onSelectSymbol: (AstSymbol) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp)
    ) {
        if (symbols.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.function_navigator_empty)) },
                onClick = onDismiss
            )
        } else {
            symbols.forEach { sym ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = sym.kind.badge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = sym.signature,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ln ${sym.lineNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { onSelectSymbol(sym) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiDocumentTabBar(
    documents: List<EditorDocument>,
    activeDocumentId: String,
    onSelectTab: (String) -> Unit,
    onRenameTab: (docId: String, newTitle: String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseOtherTabs: (String) -> Unit,
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit,
    onSaveTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    var editingTabDocId by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(documents, key = { _, doc -> doc.id }) { index, doc ->
                val isSelected = doc.id == activeDocumentId
                var contextMenuExpanded by remember { mutableStateOf(false) }

                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 16.dp else 11.dp,
                    animationSpec = GravityExpressiveMotion.spatialFast(),
                    label = "tabCornerRadius"
                )
                val tabContainerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    animationSpec = GravityExpressiveMotion.effectsDefault(),
                    label = "tabContainerColor"
                )

                Box {
                    val tabShape = RoundedCornerShape(cornerRadius)
                    Surface(
                        shape = tabShape,
                        color = tabContainerColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            }
                        ),
                        modifier = Modifier
                            .height(33.dp)
                            .clip(tabShape)
                            .combinedClickable(
                                onClick = {
                                    if (isSelected && !doc.isScratchpad) {
                                        editingTabDocId = doc.id
                                    } else {
                                        editingTabDocId = null
                                        onSelectTab(doc.id)
                                    }
                                },
                                onDoubleClick = {
                                    if (!doc.isScratchpad) {
                                        onSelectTab(doc.id)
                                        editingTabDocId = doc.id
                                    }
                                },
                                onLongClick = { contextMenuExpanded = true }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (doc.isDirty) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                            InlineEditableDocumentTitle(
                                title = doc.title,
                                isEditing = editingTabDocId == doc.id,
                                isSelected = isSelected,
                                onStartEditing = {
                                    if (!doc.isScratchpad) {
                                        onSelectTab(doc.id)
                                        editingTabDocId = doc.id
                                    } else {
                                        onSelectTab(doc.id)
                                    }
                                },
                                onCommitRename = { newName ->
                                    editingTabDocId = null
                                    onRenameTab(doc.id, newName)
                                },
                                onCancelRename = {
                                    editingTabDocId = null
                                }
                            )
                            if (!doc.isScratchpad && documents.size > 1) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close_tab),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .clickable { onCloseTab(doc.id) }
                                )
                            }
                        }
                    }

                    // Localized Tab Context Menu (Rename, Save without focus hijack, Move Left/Right, Close, Close Others)
                    DropdownMenu(
                        expanded = contextMenuExpanded,
                        onDismissRequest = { contextMenuExpanded = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (!doc.isScratchpad) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename_document)) },
                                onClick = {
                                    contextMenuExpanded = false
                                    onSelectTab(doc.id)
                                    editingTabDocId = doc.id
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save)) },
                            onClick = {
                                contextMenuExpanded = false
                                onSaveTab(doc.id)
                            }
                        )
                        if (index > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move_tab_left)) },
                                onClick = {
                                    contextMenuExpanded = false
                                    onMoveTab(index, index - 1)
                                }
                            )
                        }
                        if (index < documents.lastIndex) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move_tab_right)) },
                                onClick = {
                                    contextMenuExpanded = false
                                    onMoveTab(index, index + 1)
                                }
                            )
                        }
                        if (!doc.isScratchpad && documents.size > 1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_close_tab)) },
                                onClick = {
                                    contextMenuExpanded = false
                                    onCloseTab(doc.id)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_close_other_tabs)) },
                            onClick = {
                                contextMenuExpanded = false
                                onCloseOtherTabs(doc.id)
                            }
                        )
                    }
                }
            }

            // Expressive Trailing '+' Pill Button (opens File Type & Extension Selector Dialog)
            item {
                FilledTonalIconButton(
                    onClick = onNewTab,
                    modifier = Modifier.size(31.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_new_file),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * Inline-editable document title component.
 * Clicking on the document's title enters inline rename mode with the filename stem
 * immediately selected (`0 until dotIdx`) so the user can type a new name without
 * accidentally overwriting the file extension, and press Enter or click away to commit.
 */
@Composable
private fun InlineEditableDocumentTitle(
    title: String,
    isEditing: Boolean,
    isSelected: Boolean,
    onStartEditing: () -> Unit,
    onCommitRename: (String) -> Unit,
    onCancelRename: () -> Unit
) {
    if (isEditing) {
        var fieldValue by remember(title) {
            val dotIdx = title.lastIndexOf('.')
            val stemEnd = if (dotIdx > 0) dotIdx else title.length
            mutableStateOf(
                TextFieldValue(
                    text = title,
                    selection = TextRange(0, stemEnd)
                )
            )
        }
        val focusRequester = remember { FocusRequester() }
        var hadFocus by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }

        BasicTextField(
            value = fieldValue,
            onValueChange = { fieldValue = it },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onCommitRename(fieldValue.text) }
            ),
            modifier = Modifier
                .width(150.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        onCommitRename(fieldValue.text)
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Enter -> {
                                onCommitRename(fieldValue.text)
                                true
                            }
                            Key.Escape -> {
                                onCancelRename()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        )
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onStartEditing() }
        )
    }
}

@Composable
private fun AdaptiveEditorSplitWorkspace(
    uiState: GravityEditUiState,
    scrollEventTarget: GravityEditUiEvent.ScrollToLine?,
    onFocusPane: (Int) -> Unit,
    onContentChange: (docId: String, newContent: String, selStart: Int, selEnd: Int) -> Unit,
    onSelectionChange: (docId: String, selStart: Int, selEnd: Int) -> Unit,
    onToggleFold: (docId: String, startLine: Int) -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val primaryDoc = uiState.activeDocument ?: return
    val secondaryDoc = uiState.secondaryDocument ?: primaryDoc
    val focusedDocId = uiState.focusedDocument?.id

    val primaryIsFocusedDoc = primaryDoc.id == focusedDocId
    val primaryFolds = if (primaryIsFocusedDoc) {
        uiState.foldRegions
    } else {
        remember(primaryDoc.content) { PowerShellEngine.extractFoldRegions(primaryDoc.content) }
    }
    val primarySymbols = if (primaryIsFocusedDoc) {
        uiState.astSymbols
    } else {
        remember(primaryDoc.content) { PowerShellEngine.extractAstSymbols(primaryDoc.content) }
    }
    val primaryDiags = if (primaryIsFocusedDoc) {
        uiState.diagnostics
    } else {
        remember(primaryDoc.content, primaryDoc.language) {
            if (primaryDoc.language == SyntaxLanguage.POWERSHELL) {
                PowerShellEngine.analyzeScriptDiagnostics(primaryDoc.content)
            } else {
                emptyList()
            }
        }
    }

    val secondaryIsFocusedDoc = secondaryDoc.id == focusedDocId
    val secondaryFolds = if (secondaryIsFocusedDoc) {
        uiState.foldRegions
    } else {
        remember(secondaryDoc.content) { PowerShellEngine.extractFoldRegions(secondaryDoc.content) }
    }
    val secondarySymbols = if (secondaryIsFocusedDoc) {
        uiState.astSymbols
    } else {
        remember(secondaryDoc.content) { PowerShellEngine.extractAstSymbols(secondaryDoc.content) }
    }
    val secondaryDiags = if (secondaryIsFocusedDoc) {
        uiState.diagnostics
    } else {
        remember(secondaryDoc.content, secondaryDoc.language) {
            if (secondaryDoc.language == SyntaxLanguage.POWERSHELL) {
                PowerShellEngine.analyzeScriptDiagnostics(secondaryDoc.content)
            } else {
                emptyList()
            }
        }
    }

    val primaryScroll = scrollEventTarget?.takeIf { it.documentId == primaryDoc.id }
    val secondaryScroll = scrollEventTarget?.takeIf { it.documentId == secondaryDoc.id }

    when (uiState.splitViewMode) {
        SplitViewMode.NONE -> {
            EditorCanvas(
                document = primaryDoc,
                foldRegions = primaryFolds,
                astSymbols = primarySymbols,
                diagnostics = primaryDiags,
                onOpenDiagnostics = onOpenDiagnostics,
                searchMatches = uiState.activeDocSearchMatches,
                activeSearchMatchIndex = uiState.currentMatchIndex,
                fontSizeSp = uiState.effectiveFontSizeSp,
                tabWidth = uiState.tabWidth,
                softWrap = uiState.softWrap,
                showInvisibles = uiState.showInvisibles,
                showPageGuide80 = uiState.showPageGuide80,
                showPageGuide120 = uiState.showPageGuide120,
                enableCodeFolding = uiState.enableCodeFolding,
                isFocusedPane = true,
                scrollTargetLine = primaryScroll?.lineIndex,
                scrollRequestId = primaryScroll?.requestId ?: 0L,
                onFocusPane = { onFocusPane(0) },
                onContentChange = { text, sStart, sEnd ->
                    onContentChange(primaryDoc.id, text, sStart, sEnd)
                },
                onSelectionChange = { sStart, sEnd ->
                    onSelectionChange(primaryDoc.id, sStart, sEnd)
                },
                onToggleFold = { line -> onToggleFold(primaryDoc.id, line) },
                modifier = Modifier.fillMaxSize()
            )
        }

        SplitViewMode.VERTICAL -> {
            Row(modifier = Modifier.fillMaxSize()) {
                EditorCanvas(
                    document = primaryDoc,
                    foldRegions = primaryFolds,
                    astSymbols = primarySymbols,
                    diagnostics = primaryDiags,
                    onOpenDiagnostics = onOpenDiagnostics,
                    searchMatches = if (primaryIsFocusedDoc) uiState.activeDocSearchMatches else emptyList(),
                    activeSearchMatchIndex = if (primaryIsFocusedDoc) uiState.currentMatchIndex else -1,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 0,
                    scrollTargetLine = primaryScroll?.lineIndex,
                    scrollRequestId = primaryScroll?.requestId ?: 0L,
                    onFocusPane = { onFocusPane(0) },
                    onContentChange = { text, sStart, sEnd ->
                        onContentChange(primaryDoc.id, text, sStart, sEnd)
                    },
                    onSelectionChange = { sStart, sEnd ->
                        onSelectionChange(primaryDoc.id, sStart, sEnd)
                    },
                    onToggleFold = { line -> onToggleFold(primaryDoc.id, line) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                VerticalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                EditorCanvas(
                    document = secondaryDoc,
                    foldRegions = secondaryFolds,
                    astSymbols = secondarySymbols,
                    diagnostics = secondaryDiags,
                    onOpenDiagnostics = onOpenDiagnostics,
                    searchMatches = if (secondaryIsFocusedDoc) uiState.activeDocSearchMatches else emptyList(),
                    activeSearchMatchIndex = if (secondaryIsFocusedDoc) uiState.currentMatchIndex else -1,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 1,
                    scrollTargetLine = secondaryScroll?.lineIndex,
                    scrollRequestId = secondaryScroll?.requestId ?: 0L,
                    onFocusPane = { onFocusPane(1) },
                    onContentChange = { text, sStart, sEnd ->
                        onContentChange(secondaryDoc.id, text, sStart, sEnd)
                    },
                    onSelectionChange = { sStart, sEnd ->
                        onSelectionChange(secondaryDoc.id, sStart, sEnd)
                    },
                    onToggleFold = { line -> onToggleFold(secondaryDoc.id, line) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        SplitViewMode.HORIZONTAL -> {
            Column(modifier = Modifier.fillMaxSize()) {
                EditorCanvas(
                    document = primaryDoc,
                    foldRegions = primaryFolds,
                    astSymbols = primarySymbols,
                    diagnostics = primaryDiags,
                    onOpenDiagnostics = onOpenDiagnostics,
                    searchMatches = if (primaryIsFocusedDoc) uiState.activeDocSearchMatches else emptyList(),
                    activeSearchMatchIndex = if (primaryIsFocusedDoc) uiState.currentMatchIndex else -1,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 0,
                    scrollTargetLine = primaryScroll?.lineIndex,
                    scrollRequestId = primaryScroll?.requestId ?: 0L,
                    onFocusPane = { onFocusPane(0) },
                    onContentChange = { text, sStart, sEnd ->
                        onContentChange(primaryDoc.id, text, sStart, sEnd)
                    },
                    onSelectionChange = { sStart, sEnd ->
                        onSelectionChange(primaryDoc.id, sStart, sEnd)
                    },
                    onToggleFold = { line -> onToggleFold(primaryDoc.id, line) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                EditorCanvas(
                    document = secondaryDoc,
                    foldRegions = secondaryFolds,
                    astSymbols = secondarySymbols,
                    diagnostics = secondaryDiags,
                    onOpenDiagnostics = onOpenDiagnostics,
                    searchMatches = if (secondaryIsFocusedDoc) uiState.activeDocSearchMatches else emptyList(),
                    activeSearchMatchIndex = if (secondaryIsFocusedDoc) uiState.currentMatchIndex else -1,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 1,
                    scrollTargetLine = secondaryScroll?.lineIndex,
                    scrollRequestId = secondaryScroll?.requestId ?: 0L,
                    onFocusPane = { onFocusPane(1) },
                    onContentChange = { text, sStart, sEnd ->
                        onContentChange(secondaryDoc.id, text, sStart, sEnd)
                    },
                    onSelectionChange = { sStart, sEnd ->
                        onSelectionChange(secondaryDoc.id, sStart, sEnd)
                    },
                    onToggleFold = { line -> onToggleFold(secondaryDoc.id, line) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Full-width Material 3 Expressive Bottom Status Bar.
 *
 * **Bottom-Left Corner Guarantee:**
 * The installed version pill (`v1.2.0`) is permanently anchored at index `0` at the true
 * bottom-left corner of the screen (`x = 0, y = bottom`) in both Googlebook OS Desktop mode
 * and Android Compact Mobile mode. On compact mobile, the version pill stays pinned on the left
 * while the remaining status telemetry & pickers scroll horizontally to its right.
 */
@Composable
private fun ExpressiveBottomStatusBar(
    uiState: GravityEditUiState,
    onSelectEncoding: (DocumentEncoding) -> Unit,
    onSelectLineEnding: (LineEnding) -> Unit,
    onSelectLanguage: (SyntaxLanguage) -> Unit,
    onOpenGoToLine: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenUpdateModal: () -> Unit,
    onAdjustZoom: (Int) -> Unit,
    onResetZoom: () -> Unit,
    isCompactMobile: Boolean = false
) {
    val doc = uiState.focusedDocument
    val versionClean = cleanVersionLabel(uiState.installedVersionName)
    val updateAvailable = uiState.updateCheckStatus == AppUpdateCheckStatus.UPDATE_AVAILABLE
    val checkUpdatesLabel = stringResource(R.string.action_check_updates)

    var encodingMenuExpanded by remember { mutableStateOf(false) }
    var lineEndingMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 0. PINNED BOTTOM-LEFT CORNER VERSION PILL (always visible, never scrolled away)
            Surface(
                shape = CircleShape,
                color = if (updateAvailable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (updateAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .height(22.dp)
                    .semantics { contentDescription = "$checkUpdatesLabel v$versionClean" }
                    .clip(CircleShape)
                    .clickable(onClick = onOpenUpdateModal)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (updateAvailable) {
                            "v$versionClean • ⬆ ${uiState.latestReleaseInfo?.tagName ?: "Update"}"
                        } else {
                            "v$versionClean"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (updateAvailable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 1
                    )
                }
            }

            if (doc != null) {
                val (lineNum, colNum) = doc.cursorLineAndColumn()
                val selStats = doc.selectionStats()

                VerticalDivider(
                    modifier = Modifier
                        .height(16.dp)
                        .padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                val telemetryModifier = if (isCompactMobile) {
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                } else {
                    Modifier.weight(1f)
                }

                Row(
                    modifier = telemetryModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cursor Line:Col Pill (Clickable -> Go to Line Dialog)
                    StatusInteractivePill(
                        text = stringResource(R.string.status_cursor_pos, lineNum, colNum),
                        emphasized = true,
                        onClick = onOpenGoToLine
                    )

                    // PSScriptAnalyzer Live Diagnostics Status Pill
                    if (doc.language == SyntaxLanguage.POWERSHELL) {
                        val diagCount = uiState.diagnostics.size
                        Surface(
                            shape = CircleShape,
                            color = if (diagCount == 0) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            modifier = Modifier
                                .height(22.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenDiagnostics)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (diagCount == 0) "✓ 0 Issues" else "⚠ $diagCount Issues",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (diagCount == 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Command Palette Quick Trigger Pill
                    StatusInteractivePill(
                        text = "⌘ Ctrl+P",
                        emphasized = false,
                        onClick = onOpenCommandPalette
                    )

                    // Selection or Document Stats
                    Text(
                        text = if (selStats.hasSelection) {
                            stringResource(
                                R.string.status_selection_stats,
                                selStats.charCount,
                                selStats.wordCount,
                                selStats.lineCount
                            )
                        } else {
                            stringResource(R.string.status_doc_stats, doc.charCount, doc.lineCount)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    if (isCompactMobile) {
                        if (uiState.statusBannerMessage != null) {
                            Text(
                                text = "• ${uiState.statusBannerMessage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1
                            )
                        }
                    } else {
                        if (uiState.statusBannerMessage != null) {
                            Text(
                                text = "• ${uiState.statusBannerMessage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    // Interactive Encoding Picker Pill
                    Box {
                        StatusInteractivePill(
                            text = doc.encoding.displayName,
                            emphasized = false,
                            onClick = { encodingMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = encodingMenuExpanded,
                            onDismissRequest = { encodingMenuExpanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DocumentEncoding.entries.forEach { enc ->
                                DropdownMenuItem(
                                    text = { Text(enc.displayName) },
                                    onClick = {
                                        encodingMenuExpanded = false
                                        onSelectEncoding(enc)
                                    }
                                )
                            }
                        }
                    }

                    // Interactive Line Ending Picker Pill (LF, CRLF, CR)
                    Box {
                        StatusInteractivePill(
                            text = doc.lineEnding.badge,
                            emphasized = false,
                            onClick = { lineEndingMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = lineEndingMenuExpanded,
                            onDismissRequest = { lineEndingMenuExpanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            LineEnding.entries.forEach { le ->
                                DropdownMenuItem(
                                    text = { Text(le.displayName) },
                                    onClick = {
                                        lineEndingMenuExpanded = false
                                        onSelectLineEnding(le)
                                    }
                                )
                            }
                        }
                    }

                    // Interactive Syntax Language Selector Pill
                    Box {
                        StatusInteractivePill(
                            text = doc.language.displayName,
                            emphasized = true,
                            onClick = { languageMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            SyntaxLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.displayName) },
                                    onClick = {
                                        languageMenuExpanded = false
                                        onSelectLanguage(lang)
                                    }
                                )
                            }
                        }
                    }

                    // Expressive Connected Zoom Pill (- / 100% / +)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.height(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Zoom Out",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .clickable { onAdjustZoom(-10) }
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.status_zoom, uiState.zoomPercent),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onResetZoom)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .clickable { onAdjustZoom(10) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusInteractivePill(
    text: String,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .height(22.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
        }
    }
}
