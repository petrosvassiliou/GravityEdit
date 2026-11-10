package com.gravityedit.app.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravityedit.app.R
import com.gravityedit.app.domain.model.AstSymbol
import com.gravityedit.app.domain.model.DiskBrowserNode
import com.gravityedit.app.domain.model.DocumentEncoding
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.SplitViewMode
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.domain.model.WorkspaceToolPanel
import com.gravityedit.app.ui.components.ClippingsModal
import com.gravityedit.app.ui.components.EditorCanvas
import com.gravityedit.app.ui.components.FindReplaceModal
import com.gravityedit.app.ui.components.GoToLineDialog
import com.gravityedit.app.ui.components.PowerShellAuxiliarySymbolRow
import com.gravityedit.app.ui.components.SettingsModal
import com.gravityedit.app.ui.components.SideBySideDiffModal
import com.gravityedit.app.ui.components.TextFactoryModal
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

    var scrollEventTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
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
                    scrollEventTarget = event.documentId to event.lineIndex
                }
                is GravityEditUiEvent.ShowStatusToast -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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

        // Shared Modals & Tool Sheets (Adaptive Dialogs on Googlebook OS / BottomSheets on Mobile)
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
                    viewModel.setFindReplaceModalVisible(false)
                    if (uriStr.startsWith("memory://")) {
                        val docId = uriStr.removePrefix("memory://")
                        viewModel.selectDocument(docId)
                        viewModel.jumpToLine(lineNum)
                    } else {
                        viewModel.openFileFromUri(Uri.parse(uriStr))
                    }
                },
                onJumpToMatchInActiveDoc = { match ->
                    val doc = uiState.focusedDocument
                    if (doc != null) {
                        viewModel.updateDocumentSelection(doc.id, match.range.first, match.range.last + 1)
                        viewModel.jumpToLine(match.lineNumber)
                    }
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
                themeMode = uiState.themeMode,
                appLocaleTag = uiState.appLocaleTag,
                fontSizeSp = uiState.fontSizeSp,
                tabWidth = uiState.tabWidth,
                softWrap = uiState.softWrap,
                showInvisibles = uiState.showInvisibles,
                showPageGuide80 = uiState.showPageGuide80,
                showPageGuide120 = uiState.showPageGuide120,
                enableCodeFolding = uiState.enableCodeFolding,
                onSelectTheme = viewModel::setThemeMode,
                onSelectLanguage = viewModel::setApplicationLanguage,
                onFontSizeChange = viewModel::setFontSizeSp,
                onTabWidthChange = viewModel::setTabWidth,
                onSoftWrapChange = viewModel::setSoftWrap,
                onShowInvisiblesChange = viewModel::setShowInvisibles,
                onPageGuide80Change = viewModel::setShowPageGuide80,
                onPageGuide120Change = viewModel::setShowPageGuide120,
                onCodeFoldingChange = viewModel::setEnableCodeFolding,
                onDismiss = { viewModel.setSettingsDialogVisible(false) }
            )
        }
    }
}

/**
 * Hardware Keyboard Shortcut Handler for Googlebook OS & Desktop Keyboards (`Modifier.onPreviewKeyEvent`):
 * - `Ctrl+N`: New Document
 * - `Ctrl+O`: Open File
 * - `Ctrl+S`: Save
 * - `Ctrl+Shift+S`: Save As
 * - `Ctrl+W`: Close Tab
 * - `Ctrl+F`: Find/Replace Dialog
 * - `Ctrl+Shift+F`: Multi-File Search
 * - `Ctrl+G`: Go to Line
 * - `Ctrl+J`: Jump to Function (Function Navigator)
 * - `Ctrl+D`: Duplicate Line
 * - `Ctrl+L`: Select Line
 * - `Ctrl+/`: Toggle PowerShell Comment `#`
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
        Key.N -> {
            viewModel.createNewDocument()
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
        else -> false
    }
}

// =========================================================================
// A. GOOGLEBOOK OS DESKTOP / EXPANDED MULTI-PANE LAYOUT
// =========================================================================

@Composable
private fun GooglebookDesktopLayout(
    uiState: GravityEditUiState,
    viewModel: GravityEditViewModel,
    scrollEventTarget: Pair<String, Int>?,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current

    Row(modifier = Modifier.fillMaxSize()) {
        // 1. Leftmost Material 3 NavigationRail
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxHeight()
        ) {
            NavigationRailItem(
                selected = uiState.activeToolPanel == WorkspaceToolPanel.DISK_BROWSER && uiState.isLeftSidebarVisible,
                onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.DISK_BROWSER) },
                icon = { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.nav_disk_browser)) },
                label = { Text(stringResource(R.string.nav_disk_browser), maxLines = 1, fontSize = 10.sp) }
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
                label = { Text(stringResource(R.string.nav_open_documents), maxLines = 1, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = uiState.showTextFactoryModal,
                onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.TEXT_FACTORY) },
                icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = stringResource(R.string.nav_text_factory)) },
                label = { Text(stringResource(R.string.nav_text_factory), maxLines = 1, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = uiState.showFindReplaceModal,
                onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.FIND_REPLACE) },
                icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_find_replace)) },
                label = { Text(stringResource(R.string.nav_find_replace), maxLines = 1, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = uiState.showDiffModal,
                onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.DIFF_TOOL) },
                icon = { Icon(Icons.Default.CompareArrows, contentDescription = stringResource(R.string.nav_diff_tool)) },
                label = { Text(stringResource(R.string.nav_diff_tool), maxLines = 1, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = uiState.showClippingsModal,
                onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.CLIPPINGS) },
                icon = { Icon(Icons.Default.Code, contentDescription = stringResource(R.string.nav_clippings)) },
                label = { Text(stringResource(R.string.nav_clippings), maxLines = 1, fontSize = 10.sp) }
            )
            NavigationRailItem(
                selected = uiState.focusedDocument?.isScratchpad == true,
                onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.SCRATCHPAD) },
                icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = stringResource(R.string.nav_scratchpad)) },
                label = { Text(stringResource(R.string.nav_scratchpad), maxLines = 1, fontSize = 10.sp) }
            )
            Spacer(modifier = Modifier.weight(1f))
            NavigationRailItem(
                selected = uiState.showSettingsDialog,
                onClick = { viewModel.selectToolPanel(WorkspaceToolPanel.SETTINGS) },
                icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                label = { Text(stringResource(R.string.nav_settings), maxLines = 1, fontSize = 10.sp) }
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
                    onCloseDocument = viewModel::closeDocument,
                    onNewDocument = { viewModel.createNewDocument() },
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

        // 3. Center Desktop Workspace (Top Action Bar + Multi-Tab Bar + Single/Split Editor + Bottom Status Bar)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            DesktopTopActionBar(
                uiState = uiState,
                functionMenuExpanded = functionMenuExpanded,
                onFunctionMenuExpandedChange = onFunctionMenuExpandedChange,
                onNewFile = { viewModel.createNewDocument() },
                onOpenFile = viewModel::requestOpenFile,
                onSaveFile = viewModel::requestSaveActiveDocument,
                onSaveAsFile = viewModel::requestSaveAsActiveDocument,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onOpenFind = { viewModel.setFindReplaceModalVisible(true, 0) },
                onOpenTextFactory = { viewModel.setTextFactoryModalVisible(true) },
                onOpenDiff = { viewModel.setDiffModalVisible(true) },
                onOpenClippings = { viewModel.setClippingsModalVisible(true) },
                onJumpToSymbol = viewModel::jumpToAstSymbol,
                onSetSplitMode = viewModel::setSplitViewMode
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            MultiDocumentTabBar(
                documents = uiState.documents,
                activeDocumentId = uiState.focusedDocument?.id ?: uiState.activeDocumentId,
                onSelectTab = viewModel::selectDocument,
                onCloseTab = viewModel::closeDocument,
                onCloseOtherTabs = viewModel::closeOtherDocuments,
                onMoveTab = viewModel::moveTab,
                onSaveTab = { docId ->
                    viewModel.selectDocument(docId)
                    viewModel.requestSaveActiveDocument()
                },
                onNewTab = { viewModel.createNewDocument() }
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
                    onToggleFold = viewModel::toggleFoldAtLine
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Dense Bottom Status Bar
            DesktopBottomStatusBar(
                uiState = uiState,
                onSelectEncoding = viewModel::setDocumentEncoding,
                onSelectLineEnding = viewModel::setDocumentLineEnding,
                onSelectLanguage = viewModel::setDocumentLanguage,
                onOpenGoToLine = { viewModel.setGoToLineDialogVisible(true) },
                onAdjustZoom = viewModel::adjustZoom,
                onResetZoom = { viewModel.setZoomPercent(100) }
            )
        }
    }
}

// =========================================================================
// B. ANDROID MOBILE MODE (COMPACT / PHONE & PORTRAIT TABLET)
// =========================================================================

@Composable
private fun AndroidMobileCompactLayout(
    uiState: GravityEditUiState,
    viewModel: GravityEditViewModel,
    scrollEventTarget: Pair<String, Int>?,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                LeftSidebarContent(
                    uiState = uiState,
                    onSelectDocument = { docId ->
                        viewModel.selectDocument(docId)
                        scope.launch { drawerState.close() }
                    },
                    onCloseDocument = viewModel::closeDocument,
                    onNewDocument = {
                        viewModel.createNewDocument()
                        scope.launch { drawerState.close() }
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
                    }
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mobile Top Bar with Drawer Toggle, Function Navigator, and Quick Actions
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_disk_browser))
                    }

                    // Function Navigator Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { onFunctionMenuExpandedChange(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiState.astSymbols.firstOrNull()?.name
                                    ?: stringResource(R.string.function_navigator),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
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

                    IconButton(onClick = viewModel::requestOpenFile) {
                        Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.action_open_file))
                    }
                    IconButton(onClick = viewModel::requestSaveActiveDocument) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save))
                    }
                    IconButton(onClick = { viewModel.setFindReplaceModalVisible(true, 0) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_find_replace))
                    }
                    IconButton(onClick = { viewModel.setTextFactoryModalVisible(true) }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = stringResource(R.string.nav_text_factory))
                    }
                    IconButton(onClick = { viewModel.setSettingsDialogVisible(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            }

            // Scrollable Tab Row for Open Files
            val selectedTabIndex = uiState.documents.indexOfFirst {
                it.id == (uiState.focusedDocument?.id ?: uiState.activeDocumentId)
            }.coerceAtLeast(0)

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 4.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                uiState.documents.forEach { doc ->
                    Tab(
                        selected = doc.id == uiState.focusedDocument?.id,
                        onClick = { viewModel.selectDocument(doc.id) },
                        text = {
                            Row(
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
                                Text(
                                    text = doc.title,
                                    maxLines = 1,
                                    fontSize = 12.5.sp
                                )
                                if (!doc.isScratchpad && uiState.documents.size > 1) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.action_close_tab),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.closeDocument(doc.id) }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Center Virtualized Editor Canvas
            val activeDoc = uiState.focusedDocument
            if (activeDoc != null) {
                EditorCanvas(
                    document = activeDoc,
                    foldRegions = uiState.foldRegions,
                    astSymbols = uiState.astSymbols,
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
                    scrollTargetLine = if (scrollEventTarget?.first == activeDoc.id) scrollEventTarget.second else null,
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

            // Compact Mobile Status Strip
            DesktopBottomStatusBar(
                uiState = uiState,
                onSelectEncoding = viewModel::setDocumentEncoding,
                onSelectLineEnding = viewModel::setDocumentLineEnding,
                onSelectLanguage = viewModel::setDocumentLanguage,
                onOpenGoToLine = { viewModel.setGoToLineDialogVisible(true) },
                onAdjustZoom = viewModel::adjustZoom,
                onResetZoom = { viewModel.setZoomPercent(100) }
            )

            // Sticky PowerShell Auxiliary Symbol Row Docked Above Soft Keyboard
            PowerShellAuxiliarySymbolRow(
                canUndo = uiState.focusedDocument?.undoStack?.isNotEmpty() == true,
                canRedo = uiState.focusedDocument?.redoStack?.isNotEmpty() == true,
                onInsertToken = viewModel::insertAuxiliaryToken,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onIndent = viewModel::indentSelection,
                onUnindent = viewModel::unindentSelection
            )
        }
    }
}

// =========================================================================
// SHARED SUBCOMPONENTS: LEFT SIDEBAR, TOP BAR, TAB BAR, SPLIT VIEW & STATUS BAR
// =========================================================================

@Composable
private fun LeftSidebarContent(
    uiState: GravityEditUiState,
    onSelectDocument: (String) -> Unit,
    onCloseDocument: (String) -> Unit,
    onNewDocument: () -> Unit,
    onMountFolder: () -> Unit,
    onRefreshFolder: () -> Unit,
    onToggleDirectoryNode: (DiskBrowserNode) -> Unit,
    onSwitchToScratchpad: () -> Unit,
    showMobileToolShortcuts: Boolean = false,
    onOpenMobileTool: ((WorkspaceToolPanel) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
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
            Row {
                IconButton(onClick = onSwitchToScratchpad, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = stringResource(R.string.nav_scratchpad),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onNewDocument, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_new_file),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            uiState.documents.forEach { doc ->
                val isSelected = doc.id == uiState.focusedDocument?.id
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDocument(doc.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
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
                                    .size(14.dp)
                                    .clickable { onCloseDocument(doc.id) }
                            )
                        }
                    }
                }
            }
        }

        if (showMobileToolShortcuts && onOpenMobileTool != null) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { onOpenMobileTool(WorkspaceToolPanel.TEXT_FACTORY) }) {
                    Text(stringResource(R.string.nav_text_factory), fontSize = 12.sp)
                }
                TextButton(onClick = { onOpenMobileTool(WorkspaceToolPanel.DIFF_TOOL) }) {
                    Text(stringResource(R.string.nav_diff_tool), fontSize = 12.sp)
                }
                TextButton(onClick = { onOpenMobileTool(WorkspaceToolPanel.CLIPPINGS) }) {
                    Text(stringResource(R.string.nav_clippings), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.empty_folder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onMountFolder) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open_folder), fontSize = 12.sp)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDirectoryNode(node) }
                            .padding(
                                start = (node.depth * 14 + 4).dp,
                                end = 6.dp,
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
private fun DesktopTopActionBar(
    uiState: GravityEditUiState,
    functionMenuExpanded: Boolean,
    onFunctionMenuExpandedChange: (Boolean) -> Unit,
    onNewFile: () -> Unit,
    onOpenFile: () -> Unit,
    onSaveFile: () -> Unit,
    onSaveAsFile: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenFind: () -> Unit,
    onOpenTextFactory: () -> Unit,
    onOpenDiff: () -> Unit,
    onOpenClippings: () -> Unit,
    onJumpToSymbol: (AstSymbol) -> Unit,
    onSetSplitMode: (SplitViewMode) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onNewFile, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_file), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onOpenFile, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.action_open_file), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onSaveFile, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onSaveAsFile, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SaveAs, contentDescription = stringResource(R.string.action_save_as), modifier = Modifier.size(18.dp))
            }

            VerticalDivider(modifier = Modifier.height(22.dp))

            IconButton(
                onClick = onUndo,
                enabled = uiState.focusedDocument?.undoStack?.isNotEmpty() == true,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo), modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onRedo,
                enabled = uiState.focusedDocument?.redoStack?.isNotEmpty() == true,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo), modifier = Modifier.size(18.dp))
            }

            VerticalDivider(modifier = Modifier.height(22.dp))

            // AST Function Navigator Dropdown (Ctrl+J)
            Box {
                OutlinedButton(
                    onClick = { onFunctionMenuExpandedChange(true) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.function_navigator)} (${uiState.astSymbols.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FunctionNavigatorDropdownMenu(
                    expanded = functionMenuExpanded,
                    symbols = uiState.astSymbols,
                    onDismiss = { onFunctionMenuExpandedChange(false) },
                    onSelectSymbol = { sym ->
                        onFunctionMenuExpandedChange(false)
                        onJumpToSymbol(sym)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Quick BBEdit Tool Buttons
            TextButton(onClick = onOpenFind) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.nav_find_replace), fontSize = 12.sp)
            }
            TextButton(onClick = onOpenTextFactory) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.nav_text_factory), fontSize = 12.sp)
            }
            TextButton(onClick = onOpenDiff) {
                Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.nav_diff_tool), fontSize = 12.sp)
            }
            TextButton(onClick = onOpenClippings) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.nav_clippings), fontSize = 12.sp)
            }

            VerticalDivider(modifier = Modifier.height(22.dp))

            // Single / Horizontal Split / Vertical Split Switcher
            IconButton(
                onClick = { onSetSplitMode(SplitViewMode.NONE) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Splitscreen,
                    contentDescription = stringResource(R.string.split_none),
                    tint = if (uiState.splitViewMode == SplitViewMode.NONE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = { onSetSplitMode(SplitViewMode.VERTICAL) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewColumn,
                    contentDescription = stringResource(R.string.split_vertical),
                    tint = if (uiState.splitViewMode == SplitViewMode.VERTICAL) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = { onSetSplitMode(SplitViewMode.HORIZONTAL) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewStream,
                    contentDescription = stringResource(R.string.split_horizontal),
                    tint = if (uiState.splitViewMode == SplitViewMode.HORIZONTAL) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
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
        onDismissRequest = onDismiss
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
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = sym.kind.badge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
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
    onCloseTab: (String) -> Unit,
    onCloseOtherTabs: (String) -> Unit,
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit,
    onSaveTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(documents, key = { _, doc -> doc.id }) { index, doc ->
                val isSelected = doc.id == activeDocumentId
                var contextMenuExpanded by remember { mutableStateOf(false) }

                Box {
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .combinedClickable(
                                onClick = { onSelectTab(doc.id) },
                                onLongClick = { contextMenuExpanded = true }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
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
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (!doc.isScratchpad && documents.size > 1) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close_tab),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onCloseTab(doc.id) }
                                )
                            }
                        }
                    }

                    // Tab Context Menu (Save, Close, Close Others, Move Tab Left/Right)
                    DropdownMenu(
                        expanded = contextMenuExpanded,
                        onDismissRequest = { contextMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save)) },
                            onClick = {
                                contextMenuExpanded = false
                                onSaveTab(doc.id)
                            }
                        )
                        if (index > 0) {
                            DropdownMenuItem(
                                text = { Text("← Move Tab Left") },
                                onClick = {
                                    contextMenuExpanded = false
                                    onMoveTab(index, index - 1)
                                }
                            )
                        }
                        if (index < documents.lastIndex) {
                            DropdownMenuItem(
                                text = { Text("Move Tab Right →") },
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

            item {
                IconButton(onClick = onNewTab, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_new_file),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveEditorSplitWorkspace(
    uiState: GravityEditUiState,
    scrollEventTarget: Pair<String, Int>?,
    onFocusPane: (Int) -> Unit,
    onContentChange: (docId: String, newContent: String, selStart: Int, selEnd: Int) -> Unit,
    onSelectionChange: (docId: String, selStart: Int, selEnd: Int) -> Unit,
    onToggleFold: (docId: String, startLine: Int) -> Unit
) {
    val primaryDoc = uiState.activeDocument ?: return
    val secondaryDoc = uiState.secondaryDocument ?: primaryDoc

    when (uiState.splitViewMode) {
        SplitViewMode.NONE -> {
            EditorCanvas(
                document = primaryDoc,
                foldRegions = uiState.foldRegions,
                astSymbols = uiState.astSymbols,
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
                scrollTargetLine = if (scrollEventTarget?.first == primaryDoc.id) scrollEventTarget.second else null,
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
                    foldRegions = uiState.foldRegions,
                    astSymbols = uiState.astSymbols,
                    searchMatches = uiState.activeDocSearchMatches,
                    activeSearchMatchIndex = uiState.currentMatchIndex,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 0,
                    scrollTargetLine = if (scrollEventTarget?.first == primaryDoc.id) scrollEventTarget.second else null,
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
                    foldRegions = uiState.foldRegions,
                    astSymbols = uiState.astSymbols,
                    searchMatches = uiState.activeDocSearchMatches,
                    activeSearchMatchIndex = uiState.currentMatchIndex,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 1,
                    scrollTargetLine = if (scrollEventTarget?.first == secondaryDoc.id) scrollEventTarget.second else null,
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
                    foldRegions = uiState.foldRegions,
                    astSymbols = uiState.astSymbols,
                    searchMatches = uiState.activeDocSearchMatches,
                    activeSearchMatchIndex = uiState.currentMatchIndex,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 0,
                    scrollTargetLine = if (scrollEventTarget?.first == primaryDoc.id) scrollEventTarget.second else null,
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
                    foldRegions = uiState.foldRegions,
                    astSymbols = uiState.astSymbols,
                    searchMatches = uiState.activeDocSearchMatches,
                    activeSearchMatchIndex = uiState.currentMatchIndex,
                    fontSizeSp = uiState.effectiveFontSizeSp,
                    tabWidth = uiState.tabWidth,
                    softWrap = uiState.softWrap,
                    showInvisibles = uiState.showInvisibles,
                    showPageGuide80 = uiState.showPageGuide80,
                    showPageGuide120 = uiState.showPageGuide120,
                    enableCodeFolding = uiState.enableCodeFolding,
                    isFocusedPane = uiState.focusedPane == 1,
                    scrollTargetLine = if (scrollEventTarget?.first == secondaryDoc.id) scrollEventTarget.second else null,
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

@Composable
private fun DesktopBottomStatusBar(
    uiState: GravityEditUiState,
    onSelectEncoding: (DocumentEncoding) -> Unit,
    onSelectLineEnding: (LineEnding) -> Unit,
    onSelectLanguage: (SyntaxLanguage) -> Unit,
    onOpenGoToLine: () -> Unit,
    onAdjustZoom: (Int) -> Unit,
    onResetZoom: () -> Unit
) {
    val doc = uiState.focusedDocument ?: return
    val (lineNum, colNum) = doc.cursorLineAndColumn()
    val selStats = doc.selectionStats()

    var encodingMenuExpanded by remember { mutableStateOf(false) }
    var lineEndingMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cursor Line:Col (Clickable -> Go to Line Dialog)
            Text(
                text = stringResource(R.string.status_cursor_pos, lineNum, colNum),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenGoToLine)
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

            // Interactive Encoding Picker
            Box {
                Text(
                    text = doc.encoding.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { encodingMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = encodingMenuExpanded,
                    onDismissRequest = { encodingMenuExpanded = false }
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

            // Interactive Line Ending Picker (LF, CRLF, CR)
            Box {
                Text(
                    text = doc.lineEnding.badge,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { lineEndingMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = lineEndingMenuExpanded,
                    onDismissRequest = { lineEndingMenuExpanded = false }
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

            // Interactive Syntax Language Selector
            Box {
                Text(
                    text = doc.language.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { languageMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
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

            // Magnification / Zoom Percentage Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onAdjustZoom(-10) }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.status_zoom, uiState.zoomPercent),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable(onClick = onResetZoom)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onAdjustZoom(10) }
                )
            }
        }
    }
}
