package com.gravityedit.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gravityedit.app.domain.engine.ClippingsManager
import com.gravityedit.app.domain.engine.DiffEngine
import com.gravityedit.app.domain.engine.FindReplaceEngine
import com.gravityedit.app.domain.engine.PowerShellDiagnostic
import com.gravityedit.app.domain.engine.PowerShellEngine
import com.gravityedit.app.domain.engine.SearchableFileSource
import com.gravityedit.app.domain.engine.TextTransformEngine
import com.gravityedit.app.domain.model.AstSymbol
import com.gravityedit.app.domain.model.Clipping
import com.gravityedit.app.domain.model.ClippingCategory
import com.gravityedit.app.domain.model.DiffResult
import com.gravityedit.app.domain.model.DiskBrowserNode
import com.gravityedit.app.domain.model.DocumentEncoding
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.EditorSnapshot
import com.gravityedit.app.domain.model.FoldRegion
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.MultiFileSearchMatch
import com.gravityedit.app.domain.model.PatternPlaygroundEvaluation
import com.gravityedit.app.domain.model.SearchMatch
import com.gravityedit.app.domain.model.SearchOptions
import com.gravityedit.app.domain.model.SplitViewMode
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.domain.model.TextFactoryStep
import com.gravityedit.app.domain.model.TextOperationType
import com.gravityedit.app.domain.model.WorkspaceToolPanel
import com.gravityedit.app.ui.theme.GravityThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val Application.gravityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gravity_edit_preferences"
)

sealed interface GravityEditUiEvent {
    data object RequestOpenFilePicker : GravityEditUiEvent
    data class RequestSaveAsFilePicker(val documentId: String, val suggestedFileName: String) : GravityEditUiEvent
    data object RequestMountFolderPicker : GravityEditUiEvent
    data class ScrollToLine(val documentId: String, val lineIndex: Int) : GravityEditUiEvent
    data class ShowStatusToast(val message: String) : GravityEditUiEvent
}

@Immutable
data class GravityEditUiState(
    val documents: List<EditorDocument> = emptyList(),
    val activeDocumentId: String = "",
    val secondaryDocumentId: String? = null,
    val focusedPane: Int = 0,
    val splitViewMode: SplitViewMode = SplitViewMode.NONE,
    val activeToolPanel: WorkspaceToolPanel = WorkspaceToolPanel.DISK_BROWSER,
    val isLeftSidebarVisible: Boolean = true,
    val leftSidebarWidthDp: Float = 272f,

    // Live AST, Code Folding & PSScriptAnalyzer Diagnostics for Active Document
    val astSymbols: List<AstSymbol> = emptyList(),
    val foldRegions: List<FoldRegion> = emptyList(),
    val diagnostics: List<PowerShellDiagnostic> = emptyList(),

    // Disk Browser (SAF Project Tree)
    val diskBrowserRootUri: String? = null,
    val diskBrowserRootName: String = "Project",
    val diskBrowserNodes: List<DiskBrowserNode> = emptyList(),

    // Find & Replace, Multi-File Search, and Regex Playground
    val searchOptions: SearchOptions = SearchOptions(),
    val activeDocSearchMatches: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val multiFileSearchMatches: List<MultiFileSearchMatch> = emptyList(),
    val isMultiFileSearching: Boolean = false,
    val playgroundSampleText: String = DEFAULT_PLAYGROUND_SAMPLE,
    val playgroundEvaluation: PatternPlaygroundEvaluation = PatternPlaygroundEvaluation(isValidRegex = true),

    // Text Factory Pipeline
    val textFactorySteps: List<TextFactoryStep> = DEFAULT_TEXT_FACTORY_STEPS,

    // Clippings & Snippets
    val clippings: List<Clipping> = ClippingsManager.BUILT_IN_CLIPPINGS,
    val clippingSearchQuery: String = "",

    // Side-by-Side Diff Tool
    val diffLeftDocId: String? = null,
    val diffRightDocId: String? = null,
    val diffResult: DiffResult? = null,

    // Theme, Localization & Editor Preferences
    val themeMode: GravityThemeMode = GravityThemeMode.DARK,
    val appLocaleTag: String = "en",
    val fontSizeSp: Float = 13.5f,
    val zoomPercent: Int = 100,
    val tabWidth: Int = 4,
    val softWrap: Boolean = false,
    val showInvisibles: Boolean = false,
    val showPageGuide80: Boolean = true,
    val showPageGuide120: Boolean = true,
    val enableCodeFolding: Boolean = true,

    // Modal & Dialog Visibility Flags
    val showFindReplaceModal: Boolean = false,
    val findReplaceInitialTab: Int = 0,
    val showTextFactoryModal: Boolean = false,
    val showDiffModal: Boolean = false,
    val showClippingsModal: Boolean = false,
    val showDiagnosticsModal: Boolean = false,
    val showCommandPaletteModal: Boolean = false,
    val showGoToLineDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val statusBannerMessage: String? = null
) {
    val activeDocument: EditorDocument?
        get() = documents.firstOrNull { it.id == activeDocumentId } ?: documents.firstOrNull()

    val secondaryDocument: EditorDocument?
        get() = documents.firstOrNull { it.id == secondaryDocumentId } ?: activeDocument

    val focusedDocument: EditorDocument?
        get() = if (focusedPane == 1 && splitViewMode != SplitViewMode.NONE) {
            secondaryDocument
        } else {
            activeDocument
        }

    val effectiveFontSizeSp: Float
        get() = (fontSizeSp * (zoomPercent / 100f)).coerceIn(8f, 36f)
}

private val DEFAULT_PLAYGROUND_SAMPLE = """
    [2026-11-09 14:22:05] INFO  Server=GKE-Node-01 Latency=18ms User=admin@gravityedit.dev
    [2026-11-09 14:22:09] ERROR Server=GKE-Node-04 Latency=412ms User=ops@gravityedit.dev
    [2026-11-09 14:22:15] WARN  Server=GKE-Node-02 Latency=95ms User=deploy@gravityedit.dev
""".trimIndent()

private val DEFAULT_TEXT_FACTORY_STEPS = listOf(
    TextFactoryStep(operationType = TextOperationType.STRIP_TRAILING_WHITESPACE, enabled = true),
    TextFactoryStep(operationType = TextOperationType.ZAP_GREMLINS, enabled = true),
    TextFactoryStep(operationType = TextOperationType.FORMAT_PRETTIFY, enabled = false)
)

private val INITIAL_POWERSHELL_SCRIPT = """
<#
.SYNOPSIS
    GravityEdit PowerShell Workspace Demonstration & Telemetry Inspector.

.DESCRIPTION
    Demonstrates first-class PowerShell syntax highlighting, AST Function Navigation,
    code folding regions, expandable strings, and BBEdit Text Factory readiness.

.PARAMETER Environment
    Target deployment environment (Production, Staging, Development).

.EXAMPLE
    PS> Invoke-GravityHealthCheck -Environment 'Production' -Verbose
#>

#region Configuration & Constants
${'$'}script:DefaultTimeoutSec = 30
${'$'}global:GravityTelemetryRoot = "${'$'}env:HOME/gravity-logs"
#endregion

class ServiceEndpoint {
    [string]${'$'}Name
    [uri]${'$'}EndpointUri
    [int]${'$'}TimeoutSeconds

    ServiceEndpoint([string]${'$'}name, [string]${'$'}uri, [int]${'$'}timeout) {
        ${'$'}this.Name = ${'$'}name
        ${'$'}this.EndpointUri = [uri]${'$'}uri
        ${'$'}this.TimeoutSeconds = ${'$'}timeout
    }
}

function Get-GravitySystemMetrics {
    [CmdletBinding()]
    [OutputType([pscustomobject])]
    param (
        [Parameter(Mandatory = ${'$'}false)]
        [ValidateSet('Production', 'Staging', 'Development')]
        [string]${'$'}Environment = 'Production',

        [Parameter(Mandatory = ${'$'}false)]
        [switch]${'$'}IncludeDiagnostics
    )

    ${'$'}timestamp = (Get-Date).ToString('O')
    ${'$'}metrics = [ordered]@{
        Environment = ${'$'}Environment
        Timestamp   = ${'$'}timestamp
        Runtime     = ${'$'}PSVersionTable.PSVersion.ToString()
        ScriptRoot  = ${'$'}PSScriptRoot
        Healthy     = ${'$'}true
    }

    if (${'$'}IncludeDiagnostics -and ${'$'}metrics.Healthy -eq ${'$'}true) {
        Write-Verbose "Diagnostics collected for ${'$'}Environment at ${'$'}(Get-Date -Format 'HH:mm:ss')"
    }

    return [pscustomobject]${'$'}metrics
}

filter Select-CriticalEvents {
    param (
        [string]${'$'}SeverityPattern = '^(ERROR|CRITICAL|FATAL)'
    )
    if (${'$'}_.Severity -match ${'$'}SeverityPattern) {
        ${'$'}PSItem
    }
}

function Invoke-GravityHealthCheck {
    [CmdletBinding(SupportsShouldProcess = ${'$'}true)]
    param (
        [Parameter(Mandatory = ${'$'}true, Position = 0)]
        [string[]]${'$'}TargetHosts,

        [Parameter(Mandatory = ${'$'}false)]
        [int]${'$'}RetryCount = 3
    )

    begin {
        Write-Host "Initializing GravityEdit health check across ${'$'}(${'$'}TargetHosts.Count) host(s)..."
    }

    process {
        foreach (${'$'}hostName in ${'$'}TargetHosts) {
            if (${'$'}PSCmdlet.ShouldProcess(${'$'}hostName, 'Execute HTTPS telemetry probe')) {
                try {
                    ${'$'}status = Get-GravitySystemMetrics -Environment 'Production' -IncludeDiagnostics
                    Write-Output ${'$'}status
                }
                catch {
                    Write-Error "Health check failed on ${'$'}hostName: ${'$'}(${'$'}_.Exception.Message)"
                }
            }
        }
    }
}
""".trimIndent()

private val INITIAL_SCRATCHPAD_CONTENT = """
# =====================================================================
# Scratchpad.ps1 — Persistent Auto-Saved PowerShell & Text Scratchpad
# Any notes or quick scripts typed here are automatically persisted.
# =====================================================================

${'$'}items = @(
    [pscustomobject]@{ Id = 101; Service = 'AuthGateway'; Status = 'Online'; LatencyMs = 12 }
    [pscustomobject]@{ Id = 102; Service = 'SearchIndex'; Status = 'Degraded'; LatencyMs = 248 }
    [pscustomobject]@{ Id = 103; Service = 'StorageNode'; Status = 'Online'; LatencyMs = 9 }
)

${'$'}items | Where-Object { ${'$'}_.LatencyMs -lt 50 } | ForEach-Object {
    "Service [${'$'}(${'$'}_.Service)] responded in ${'$'}(${'$'}_.LatencyMs) ms"
}
""".trimIndent()

class GravityEditViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.gravityDataStore

    private object PrefKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LOCALE_TAG = stringPreferencesKey("locale_tag")
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val TAB_WIDTH = intPreferencesKey("tab_width")
        val SOFT_WRAP = booleanPreferencesKey("soft_wrap")
        val SHOW_INVISIBLES = booleanPreferencesKey("show_invisibles")
        val PAGE_GUIDE_80 = booleanPreferencesKey("page_guide_80")
        val PAGE_GUIDE_120 = booleanPreferencesKey("page_guide_120")
        val CODE_FOLDING = booleanPreferencesKey("code_folding")
        val SCRATCHPAD_CONTENT = stringPreferencesKey("scratchpad_content")
        val MOUNTED_FOLDER_URI = stringPreferencesKey("mounted_folder_uri")
    }

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<GravityEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GravityEditUiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GravityEditUiEvent> = _events.asSharedFlow()

    private var astParseJob: Job? = null
    private var scratchpadSaveJob: Job? = null
    private var untitledCounter = 2

    init {
        loadPersistedStateAndInitialize()
        refreshAstAndFoldsForActiveDocument()
        recomputePlayground()
    }

    private fun createInitialState(): GravityEditUiState {
        val mainDoc = EditorDocument(
            id = "doc_main_ps1",
            title = "GravityTelemetry.ps1",
            content = INITIAL_POWERSHELL_SCRIPT,
            savedContent = INITIAL_POWERSHELL_SCRIPT,
            isScratchpad = false,
            language = SyntaxLanguage.POWERSHELL,
            encoding = DocumentEncoding.UTF_8,
            lineEnding = LineEnding.LF
        )
        val scratchpadDoc = EditorDocument(
            id = SCRATCHPAD_DOC_ID,
            title = "Scratchpad.ps1",
            content = INITIAL_SCRATCHPAD_CONTENT,
            savedContent = INITIAL_SCRATCHPAD_CONTENT,
            isScratchpad = true,
            language = SyntaxLanguage.POWERSHELL,
            encoding = DocumentEncoding.UTF_8,
            lineEnding = LineEnding.LF
        )
        val symbols = PowerShellEngine.extractAstSymbols(mainDoc.content)
        val folds = PowerShellEngine.extractFoldRegions(mainDoc.content)
        return GravityEditUiState(
            documents = listOf(mainDoc, scratchpadDoc),
            activeDocumentId = mainDoc.id,
            secondaryDocumentId = scratchpadDoc.id,
            astSymbols = symbols,
            foldRegions = folds,
            diffLeftDocId = mainDoc.id,
            diffRightDocId = scratchpadDoc.id
        )
    }

    private fun loadPersistedStateAndInitialize() {
        viewModelScope.launch {
            val prefs = runCatching { dataStore.data.first() }.getOrNull() ?: return@launch
            val savedTheme = prefs[PrefKeys.THEME_MODE]?.let {
                runCatching { GravityThemeMode.valueOf(it) }.getOrNull()
            } ?: GravityThemeMode.DARK
            val savedLocale = prefs[PrefKeys.LOCALE_TAG] ?: "en"
            val savedFontSize = prefs[PrefKeys.FONT_SIZE_SP] ?: 13.5f
            val savedTabWidth = prefs[PrefKeys.TAB_WIDTH] ?: 4
            val savedSoftWrap = prefs[PrefKeys.SOFT_WRAP] ?: false
            val savedInvisibles = prefs[PrefKeys.SHOW_INVISIBLES] ?: false
            val savedGuide80 = prefs[PrefKeys.PAGE_GUIDE_80] ?: true
            val savedGuide120 = prefs[PrefKeys.PAGE_GUIDE_120] ?: true
            val savedFolding = prefs[PrefKeys.CODE_FOLDING] ?: true
            val savedScratchpad = prefs[PrefKeys.SCRATCHPAD_CONTENT]
            val savedFolderUri = prefs[PrefKeys.MOUNTED_FOLDER_URI]

            _uiState.update { state ->
                val updatedDocs = if (!savedScratchpad.isNullOrEmpty()) {
                    state.documents.map { doc ->
                        if (doc.isScratchpad) {
                            doc.copy(content = savedScratchpad, savedContent = savedScratchpad)
                        } else {
                            doc
                        }
                    }
                } else {
                    state.documents
                }
                state.copy(
                    documents = updatedDocs,
                    themeMode = savedTheme,
                    appLocaleTag = savedLocale,
                    fontSizeSp = savedFontSize,
                    tabWidth = savedTabWidth,
                    softWrap = savedSoftWrap,
                    showInvisibles = savedInvisibles,
                    showPageGuide80 = savedGuide80,
                    showPageGuide120 = savedGuide120,
                    enableCodeFolding = savedFolding
                )
            }

            if (!savedFolderUri.isNullOrBlank()) {
                runCatching {
                    mountProjectDirectory(Uri.parse(savedFolderUri), persist = false)
                }
            }
        }
    }

    // =========================================================================
    // DOCUMENT & MULTI-TAB MANAGEMENT
    // =========================================================================

    fun createNewDocument(
        initialTitle: String? = null,
        initialContent: String = "",
        language: SyntaxLanguage = SyntaxLanguage.POWERSHELL
    ) {
        val ext = language.extensions.firstOrNull() ?: "ps1"
        val title = initialTitle ?: "Untitled-${untitledCounter++}.$ext"
        val newDoc = EditorDocument(
            title = title,
            content = initialContent,
            savedContent = initialContent,
            language = language
        )
        _uiState.update { state ->
            state.copy(
                documents = state.documents + newDoc,
                activeDocumentId = newDoc.id
            )
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun selectDocument(documentId: String) {
        _uiState.update { state ->
            if (state.focusedPane == 1 && state.splitViewMode != SplitViewMode.NONE) {
                state.copy(secondaryDocumentId = documentId)
            } else {
                state.copy(activeDocumentId = documentId)
            }
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun selectSecondaryDocument(documentId: String) {
        _uiState.update { it.copy(secondaryDocumentId = documentId) }
    }

    fun setFocusedPane(paneIndex: Int) {
        _uiState.update { it.copy(focusedPane = paneIndex.coerceIn(0, 1)) }
        refreshAstAndFoldsForActiveDocument()
    }

    fun closeDocument(documentId: String) {
        _uiState.update { state ->
            val target = state.documents.firstOrNull { it.id == documentId } ?: return@update state
            if (target.isScratchpad && state.documents.size <= 1) {
                return@update state
            }
            val remaining = state.documents.filterNot { it.id == documentId }.ifEmpty {
                listOf(
                    EditorDocument(
                        title = "Untitled-${untitledCounter++}.ps1",
                        language = SyntaxLanguage.POWERSHELL
                    )
                )
            }
            val nextActiveId = if (state.activeDocumentId == documentId) {
                remaining.first().id
            } else {
                state.activeDocumentId
            }
            val nextSecondaryId = if (state.secondaryDocumentId == documentId) {
                remaining.last().id
            } else {
                state.secondaryDocumentId
            }
            state.copy(
                documents = remaining,
                activeDocumentId = nextActiveId,
                secondaryDocumentId = nextSecondaryId
            )
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun closeOtherDocuments(keepDocumentId: String) {
        _uiState.update { state ->
            val kept = state.documents.filter { it.id == keepDocumentId || it.isScratchpad }
            if (kept.isEmpty()) return@update state
            state.copy(
                documents = kept,
                activeDocumentId = keepDocumentId,
                secondaryDocumentId = kept.last().id
            )
        }
        refreshAstAndFoldsForActiveDocument()
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.documents.indices || toIndex !in state.documents.indices || fromIndex == toIndex) {
                return@update state
            }
            val mutable = state.documents.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(documents = mutable)
        }
    }

    fun switchToScratchpad() {
        _uiState.update { state ->
            val existingScratchpad = state.documents.firstOrNull { it.isScratchpad }
            if (existingScratchpad != null) {
                state.copy(
                    activeDocumentId = existingScratchpad.id,
                    activeToolPanel = WorkspaceToolPanel.SCRATCHPAD
                )
            } else {
                val scratchpad = EditorDocument(
                    id = SCRATCHPAD_DOC_ID,
                    title = "Scratchpad.ps1",
                    content = INITIAL_SCRATCHPAD_CONTENT,
                    savedContent = INITIAL_SCRATCHPAD_CONTENT,
                    isScratchpad = true,
                    language = SyntaxLanguage.POWERSHELL
                )
                state.copy(
                    documents = state.documents + scratchpad,
                    activeDocumentId = scratchpad.id,
                    activeToolPanel = WorkspaceToolPanel.SCRATCHPAD
                )
            }
        }
        refreshAstAndFoldsForActiveDocument()
    }

    // =========================================================================
    // CONTENT EDITING, DEEP UNDO / REDO STACK & KEYBOARD ACTIONS
    // =========================================================================

    fun updateDocumentContent(
        documentId: String,
        newContent: String,
        newSelectionStart: Int = -1,
        newSelectionEnd: Int = -1,
        recordUndo: Boolean = true
    ) {
        var isScratchpadModified = false
        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc.id != documentId) return@map doc
                val normalized = LineEnding.normalizeToLf(newContent)
                val resolvedStart = if (newSelectionStart >= 0) {
                    newSelectionStart.coerceIn(0, normalized.length)
                } else {
                    doc.selectionStart.coerceIn(0, normalized.length)
                }
                val resolvedEnd = if (newSelectionEnd >= 0) {
                    newSelectionEnd.coerceIn(0, normalized.length)
                } else {
                    doc.selectionEnd.coerceIn(0, normalized.length)
                }

                val nextUndoStack = if (recordUndo && normalized != doc.content) {
                    val snapshot = EditorSnapshot(
                        content = doc.content,
                        selectionStart = doc.selectionStart,
                        selectionEnd = doc.selectionEnd
                    )
                    (doc.undoStack + snapshot).takeLast(MAX_UNDO_HISTORY)
                } else {
                    doc.undoStack
                }

                if (doc.isScratchpad && normalized != doc.content) {
                    isScratchpadModified = true
                }

                doc.copy(
                    content = normalized,
                    selectionStart = resolvedStart,
                    selectionEnd = resolvedEnd,
                    undoStack = nextUndoStack,
                    redoStack = if (recordUndo && normalized != doc.content) emptyList() else doc.redoStack
                )
            }
            state.copy(documents = updatedDocs)
        }

        if (isScratchpadModified) {
            scheduleScratchpadPersistence()
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun updateDocumentSelection(
        documentId: String,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc.id != documentId) return@map doc
                doc.copy(
                    selectionStart = selectionStart.coerceIn(0, doc.content.length),
                    selectionEnd = selectionEnd.coerceIn(0, doc.content.length)
                )
            }
            state.copy(documents = updatedDocs)
        }
    }

    fun undo(targetDocumentId: String? = null) {
        val docId = targetDocumentId ?: (_uiState.value.focusedDocument?.id ?: return)
        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc.id != docId || doc.undoStack.isEmpty()) return@map doc
                val previous = doc.undoStack.last()
                val currentSnapshot = EditorSnapshot(
                    content = doc.content,
                    selectionStart = doc.selectionStart,
                    selectionEnd = doc.selectionEnd
                )
                doc.copy(
                    content = previous.content,
                    selectionStart = previous.selectionStart.coerceIn(0, previous.content.length),
                    selectionEnd = previous.selectionEnd.coerceIn(0, previous.content.length),
                    undoStack = doc.undoStack.dropLast(1),
                    redoStack = (doc.redoStack + currentSnapshot).takeLast(MAX_UNDO_HISTORY)
                )
            }
            state.copy(documents = updatedDocs)
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun redo(targetDocumentId: String? = null) {
        val docId = targetDocumentId ?: (_uiState.value.focusedDocument?.id ?: return)
        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc.id != docId || doc.redoStack.isEmpty()) return@map doc
                val next = doc.redoStack.last()
                val currentSnapshot = EditorSnapshot(
                    content = doc.content,
                    selectionStart = doc.selectionStart,
                    selectionEnd = doc.selectionEnd
                )
                doc.copy(
                    content = next.content,
                    selectionStart = next.selectionStart.coerceIn(0, next.content.length),
                    selectionEnd = next.selectionEnd.coerceIn(0, next.content.length),
                    undoStack = (doc.undoStack + currentSnapshot).takeLast(MAX_UNDO_HISTORY),
                    redoStack = doc.redoStack.dropLast(1)
                )
            }
            state.copy(documents = updatedDocs)
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    /**
     * `Ctrl+D`: Duplicates the active line or currently selected lines immediately below.
     */
    fun duplicateCurrentLine() {
        val doc = _uiState.value.focusedDocument ?: return
        val lineIdx = doc.lineIndexForOffset(doc.safeSelectionEnd)
        val lines = doc.lines.toMutableList()
        if (lineIdx !in lines.indices) return
        val duplicatedLine = lines[lineIdx]
        lines.add(lineIdx + 1, duplicatedLine)
        val newContent = lines.joinToString("\n")
        val newCursor = (doc.lineOffsetRange(lineIdx).last + 1 + duplicatedLine.length).coerceIn(0, newContent.length)
        updateDocumentContent(doc.id, newContent, newCursor, newCursor)
    }

    /**
     * `Ctrl+L`: Selects the entire current line where the cursor resides.
     */
    fun selectCurrentLine() {
        val doc = _uiState.value.focusedDocument ?: return
        val lineIdx = doc.lineIndexForOffset(doc.safeSelectionEnd)
        val range = doc.lineOffsetRange(lineIdx)
        updateDocumentSelection(doc.id, range.first, range.last)
    }

    /**
     * `Ctrl+/`: Toggles single-line comment (`#` for PowerShell/YAML/Python/Bash, `//` for Kotlin/JSON)
     * across all lines spanned by the current selection.
     */
    fun toggleCommentOnSelection() {
        val doc = _uiState.value.focusedDocument ?: return
        val prefix = doc.language.singleLineCommentPrefix ?: "#"
        val startLine = doc.lineIndexForOffset(doc.safeSelectionStart)
        val endLine = doc.lineIndexForOffset(doc.safeSelectionEnd)
        val lines = doc.lines.toMutableList()

        val targetSlice = lines.subList(startLine, endLine + 1)
        val allCommented = targetSlice.filter { it.isNotBlank() }
            .all { it.trimStart().startsWith(prefix) }

        var deltaChars = 0
        for (idx in startLine..endLine) {
            val line = lines[idx]
            if (line.isBlank()) continue
            if (allCommented) {
                val pIdx = line.indexOf(prefix)
                if (pIdx >= 0) {
                    val removeLen = if (pIdx + prefix.length < line.length && line[pIdx + prefix.length] == ' ') {
                        prefix.length + 1
                    } else {
                        prefix.length
                    }
                    lines[idx] = line.removeRange(pIdx, pIdx + removeLen)
                    deltaChars -= removeLen
                }
            } else {
                val indentLen = line.takeWhile { it == ' ' || it == '\t' }.length
                lines[idx] = line.substring(0, indentLen) + "$prefix " + line.substring(indentLen)
                deltaChars += prefix.length + 1
            }
        }

        val updated = lines.joinToString("\n")
        val newCursor = (doc.safeSelectionEnd + deltaChars).coerceIn(0, updated.length)
        updateDocumentContent(doc.id, updated, newCursor, newCursor)
    }

    fun indentSelection() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val indentSpaces = " ".repeat(state.tabWidth.coerceIn(1, 8))
        val startLine = doc.lineIndexForOffset(doc.safeSelectionStart)
        val endLine = doc.lineIndexForOffset(doc.safeSelectionEnd)
        val lines = doc.lines.toMutableList()
        var addedChars = 0
        for (i in startLine..endLine) {
            lines[i] = indentSpaces + lines[i]
            addedChars += indentSpaces.length
        }
        val updated = lines.joinToString("\n")
        val newEnd = (doc.safeSelectionEnd + addedChars).coerceIn(0, updated.length)
        updateDocumentContent(doc.id, updated, doc.safeSelectionStart + indentSpaces.length, newEnd)
    }

    fun unindentSelection() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val tabW = state.tabWidth.coerceIn(1, 8)
        val startLine = doc.lineIndexForOffset(doc.safeSelectionStart)
        val endLine = doc.lineIndexForOffset(doc.safeSelectionEnd)
        val lines = doc.lines.toMutableList()
        var removedTotal = 0
        var removedFirst = 0
        for (i in startLine..endLine) {
            val line = lines[i]
            val removeCount = when {
                line.startsWith("\t") -> 1
                else -> line.takeWhile { it == ' ' }.length.coerceAtMost(tabW)
            }
            if (removeCount > 0) {
                lines[i] = line.substring(removeCount)
                removedTotal += removeCount
                if (i == startLine) removedFirst = removeCount
            }
        }
        if (removedTotal == 0) return
        val updated = lines.joinToString("\n")
        val newStart = (doc.safeSelectionStart - removedFirst).coerceAtLeast(0)
        val newEnd = (doc.safeSelectionEnd - removedTotal).coerceIn(newStart, updated.length)
        updateDocumentContent(doc.id, updated, newStart, newEnd)
    }

    /**
     * Used by the Android Sticky PowerShell Auxiliary Symbol Row for one-tap token insertion
     * with automatic pair completion for `{`, `(`, `[`, `"`, `'`.
     */
    fun insertAuxiliaryToken(token: String) {
        val doc = _uiState.value.focusedDocument ?: return
        val start = doc.safeSelectionStart
        val end = doc.safeSelectionEnd
        val selected = doc.selectedText

        val (insertionText, cursorOffsetInside) = if (selected.isNotEmpty()) {
            when (token) {
                "{" -> "{$selected}" to (selected.length + 2)
                "(" -> "($selected)" to (selected.length + 2)
                "[" -> "[$selected]" to (selected.length + 2)
                "\"" -> "\"$selected\"" to (selected.length + 2)
                "'" -> "'$selected'" to (selected.length + 2)
                else -> token to token.length
            }
        } else {
            when (token) {
                "{" -> "{}" to 1
                "(" -> "()" to 1
                "[" -> "[]" to 1
                "\"" -> "\"\"" to 1
                "'" -> "''" to 1
                else -> token to token.length
            }
        }

        val updated = buildString(doc.content.length - (end - start) + insertionText.length) {
            append(doc.content, 0, start)
            append(insertionText)
            append(doc.content, end, doc.content.length)
        }
        val newCursor = (start + cursorOffsetInside).coerceIn(0, updated.length)
        updateDocumentContent(doc.id, updated, newCursor, newCursor)
    }

    // =========================================================================
    // CODE FOLDING & AST FUNCTION NAVIGATOR
    // =========================================================================

    fun toggleFoldAtLine(documentId: String, startLine: Int) {
        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc.id != documentId) return@map doc
                val nextSet = if (startLine in doc.foldedStartLines) {
                    doc.foldedStartLines - startLine
                } else {
                    doc.foldedStartLines + startLine
                }
                doc.copy(foldedStartLines = nextSet)
            }
            state.copy(documents = updatedDocs)
        }
    }

    fun jumpToLine(lineNumberOneBased: Int) {
        val doc = _uiState.value.focusedDocument ?: return
        val targetLineIndex = (lineNumberOneBased - 1).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
        val lineRange = doc.lineOffsetRange(targetLineIndex)

        // Unfold any region enclosing targetLineIndex so the line is visible
        val enclosingFolds = _uiState.value.foldRegions
            .filter { targetLineIndex in (it.startLine + 1)..it.endLine }
            .map { it.startLine }
            .toSet()

        _uiState.update { state ->
            val updatedDocs = state.documents.map { d ->
                if (d.id != doc.id) return@map d
                d.copy(
                    selectionStart = lineRange.first,
                    selectionEnd = lineRange.first,
                    scrollLineIndex = targetLineIndex,
                    foldedStartLines = d.foldedStartLines - enclosingFolds
                )
            }
            state.copy(
                documents = updatedDocs,
                showGoToLineDialog = false
            )
        }
        _events.tryEmit(GravityEditUiEvent.ScrollToLine(doc.id, targetLineIndex))
    }

    fun jumpToAstSymbol(symbol: AstSymbol) {
        jumpToLine(symbol.lineNumber)
    }

    private fun refreshAstAndFoldsForActiveDocument() {
        astParseJob?.cancel()
        val doc = _uiState.value.focusedDocument ?: return
        val contentSnapshot = doc.content
        val languageSnapshot = doc.language
        astParseJob = viewModelScope.launch(Dispatchers.Default) {
            val symbols = PowerShellEngine.extractAstSymbols(contentSnapshot)
            val folds = PowerShellEngine.extractFoldRegions(contentSnapshot)
            val diags = if (languageSnapshot == SyntaxLanguage.POWERSHELL) {
                PowerShellEngine.analyzeScriptDiagnostics(contentSnapshot)
            } else {
                emptyList()
            }
            _uiState.update { state ->
                state.copy(
                    astSymbols = symbols,
                    foldRegions = folds,
                    diagnostics = diags
                )
            }
        }
    }

    fun applyDiagnosticQuickFix(diagnostic: PowerShellDiagnostic) {
        val doc = _uiState.value.focusedDocument ?: return
        val updatedContent = PowerShellEngine.applyDiagnosticQuickFix(doc.content, diagnostic)
        if (updatedContent != doc.content) {
            updateDocumentContent(doc.id, updatedContent)
            _uiState.update {
                it.copy(statusBannerMessage = "Applied Quick Fix: ${diagnostic.quickFixLabel ?: diagnostic.ruleId}")
            }
        }
    }

    fun applyAllDiagnosticQuickFixes() {
        val doc = _uiState.value.focusedDocument ?: return
        val fixable = _uiState.value.diagnostics
            .filter { it.quickFixTarget != null && it.quickFixReplacement != null }
            .sortedWith(compareByDescending<PowerShellDiagnostic> { it.lineNumber }.thenByDescending { it.column })
        if (fixable.isEmpty()) return

        var currentText = doc.content
        var appliedCount = 0
        for (diag in fixable) {
            val nextText = PowerShellEngine.applyDiagnosticQuickFix(currentText, diag)
            if (nextText != currentText) {
                currentText = nextText
                appliedCount++
            }
        }
        if (appliedCount > 0) {
            updateDocumentContent(doc.id, currentText)
            _uiState.update {
                it.copy(statusBannerMessage = "Applied $appliedCount PowerShell Quick Fix(es)")
            }
        }
    }

    private fun scheduleScratchpadPersistence() {
        scratchpadSaveJob?.cancel()
        scratchpadSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350L)
            val scratchpadDoc = _uiState.value.documents.firstOrNull { it.isScratchpad } ?: return@launch
            dataStore.edit { prefs ->
                prefs[PrefKeys.SCRATCHPAD_CONTENT] = scratchpadDoc.content
            }
        }
    }

    // =========================================================================
    // DOCUMENT METADATA (ENCODING, LINE ENDING, LANGUAGE, SPLIT VIEW, ZOOM)
    // =========================================================================

    fun setDocumentEncoding(encoding: DocumentEncoding) {
        val doc = _uiState.value.focusedDocument ?: return
        _uiState.update { state ->
            state.copy(
                documents = state.documents.map {
                    if (it.id == doc.id) it.copy(encoding = encoding) else it
                }
            )
        }
    }

    fun setDocumentLineEnding(lineEnding: LineEnding) {
        val doc = _uiState.value.focusedDocument ?: return
        _uiState.update { state ->
            state.copy(
                documents = state.documents.map {
                    if (it.id == doc.id) it.copy(lineEnding = lineEnding) else it
                }
            )
        }
    }

    fun setDocumentLanguage(language: SyntaxLanguage) {
        val doc = _uiState.value.focusedDocument ?: return
        _uiState.update { state ->
            state.copy(
                documents = state.documents.map {
                    if (it.id == doc.id) it.copy(language = language) else it
                }
            )
        }
        refreshAstAndFoldsForActiveDocument()
    }

    fun setSplitViewMode(mode: SplitViewMode) {
        _uiState.update { state ->
            val secId = state.secondaryDocumentId ?: state.documents.lastOrNull()?.id ?: state.activeDocumentId
            state.copy(
                splitViewMode = mode,
                secondaryDocumentId = secId,
                focusedPane = if (mode == SplitViewMode.NONE) 0 else state.focusedPane
            )
        }
    }

    fun setZoomPercent(percent: Int) {
        _uiState.update { it.copy(zoomPercent = percent.coerceIn(50, 250)) }
    }

    fun adjustZoom(deltaPercent: Int) {
        _uiState.update { it.copy(zoomPercent = (it.zoomPercent + deltaPercent).coerceIn(50, 250)) }
    }

    fun selectToolPanel(panel: WorkspaceToolPanel) {
        _uiState.update { state ->
            val toggleSidebar = if (panel == WorkspaceToolPanel.DISK_BROWSER || panel == WorkspaceToolPanel.OPEN_DOCUMENTS) {
                if (state.activeToolPanel == panel) !state.isLeftSidebarVisible else true
            } else {
                state.isLeftSidebarVisible
            }
            state.copy(
                activeToolPanel = panel,
                isLeftSidebarVisible = toggleSidebar,
                showFindReplaceModal = panel == WorkspaceToolPanel.FIND_REPLACE,
                showTextFactoryModal = panel == WorkspaceToolPanel.TEXT_FACTORY,
                showDiffModal = panel == WorkspaceToolPanel.DIFF_TOOL,
                showClippingsModal = panel == WorkspaceToolPanel.CLIPPINGS,
                showSettingsDialog = panel == WorkspaceToolPanel.SETTINGS
            )
        }
        if (panel == WorkspaceToolPanel.SCRATCHPAD) {
            switchToScratchpad()
        } else if (panel == WorkspaceToolPanel.DIFF_TOOL) {
            recomputeDiff()
        }
    }

    fun toggleLeftSidebar() {
        _uiState.update { it.copy(isLeftSidebarVisible = !it.isLeftSidebarVisible) }
    }

    fun updateLeftSidebarWidth(deltaDp: Float) {
        _uiState.update { state ->
            state.copy(leftSidebarWidthDp = (state.leftSidebarWidthDp + deltaDp).coerceIn(180f, 520f))
        }
    }

    // =========================================================================
    // ANDROID STORAGE ACCESS FRAMEWORK (SAF) FILE & PROJECT TREE I/O
    // =========================================================================

    fun requestOpenFile() {
        _events.tryEmit(GravityEditUiEvent.RequestOpenFilePicker)
    }

    fun requestSaveActiveDocument() {
        val doc = _uiState.value.focusedDocument ?: return
        if (doc.uriString != null) {
            saveDocumentToUri(doc.id, Uri.parse(doc.uriString))
        } else {
            _events.tryEmit(GravityEditUiEvent.RequestSaveAsFilePicker(doc.id, doc.title))
        }
    }

    fun requestSaveAsActiveDocument() {
        val doc = _uiState.value.focusedDocument ?: return
        _events.tryEmit(GravityEditUiEvent.RequestSaveAsFilePicker(doc.id, doc.title))
    }

    fun requestMountProjectFolder() {
        _events.tryEmit(GravityEditUiEvent.RequestMountFolderPicker)
    }

    fun openFileFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val existing = _uiState.value.documents.firstOrNull { it.uriString == uri.toString() }
            if (existing != null) {
                withContext(Dispatchers.Main) {
                    selectDocument(existing.id)
                }
                return@launch
            }

            val docFile = DocumentFile.fromSingleUri(app, uri)
            val resolvedName = docFile?.name
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "Document.ps1"

            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val (detectedEncoding, rawText) = decodeBytesWithEncodingDetection(rawBytes)
            val detectedLineEnding = LineEnding.detect(rawText)
            val normalizedText = LineEnding.normalizeToLf(rawText)
            val detectedLang = SyntaxLanguage.fromFileName(resolvedName)

            val openedDoc = EditorDocument(
                title = resolvedName,
                uriString = uri.toString(),
                content = normalizedText,
                savedContent = normalizedText,
                language = detectedLang,
                encoding = detectedEncoding,
                lineEnding = detectedLineEnding
            )

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        documents = state.documents + openedDoc,
                        activeDocumentId = openedDoc.id,
                        statusBannerMessage = "Opened $resolvedName (${detectedEncoding.displayName}, ${detectedLineEnding.badge})"
                    )
                }
                refreshAstAndFoldsForActiveDocument()
                refreshActiveDocSearchMatches()
            }
        }
    }

    fun saveDocumentToUri(documentId: String, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val doc = _uiState.value.documents.firstOrNull { it.id == documentId } ?: return@launch

            runCatching {
                resolver.takePersistableUriPermission(
                    targetUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val textWithLineEndings = LineEnding.applyLineEnding(doc.content, doc.lineEnding)
            val encodedBytes = encodeTextWithBom(textWithLineEndings, doc.encoding)

            val writeSuccess = runCatching {
                resolver.openOutputStream(targetUri, "wt")?.use { out ->
                    out.write(encodedBytes)
                    out.flush()
                }
                true
            }.getOrDefault(false)

            if (!writeSuccess) return@launch

            val docFile = DocumentFile.fromSingleUri(app, targetUri)
            val updatedTitle = docFile?.name ?: doc.title
            val updatedLang = SyntaxLanguage.fromFileName(updatedTitle)

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        documents = state.documents.map { d ->
                            if (d.id != documentId) return@map d
                            d.copy(
                                title = updatedTitle,
                                uriString = targetUri.toString(),
                                savedContent = d.content,
                                language = updatedLang
                            )
                        },
                        statusBannerMessage = "Saved $updatedTitle (${doc.encoding.displayName}, ${doc.lineEnding.badge})"
                    )
                }
                _events.tryEmit(GravityEditUiEvent.ShowStatusToast("Saved $updatedTitle"))
            }
        }
    }

    fun mountProjectDirectory(treeUri: Uri, persist: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            if (persist) {
                dataStore.edit { prefs ->
                    prefs[PrefKeys.MOUNTED_FOLDER_URI] = treeUri.toString()
                }
            }
            val rootDoc = DocumentFile.fromTreeUri(app, treeUri) ?: return@launch
            val rootName = rootDoc.name ?: "Project"
            val children = buildDiskBrowserNodes(rootDoc, depth = 0)
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        diskBrowserRootUri = treeUri.toString(),
                        diskBrowserRootName = rootName,
                        diskBrowserNodes = children,
                        isLeftSidebarVisible = true,
                        activeToolPanel = WorkspaceToolPanel.DISK_BROWSER
                    )
                }
            }
        }
    }

    fun refreshDiskBrowser() {
        val rootUriStr = _uiState.value.diskBrowserRootUri ?: return
        mountProjectDirectory(Uri.parse(rootUriStr), persist = false)
    }

    fun toggleDirectoryNode(node: DiskBrowserNode) {
        if (!node.isDirectory) {
            openFileFromUri(Uri.parse(node.uriString))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val updatedTree = toggleNodeRecursive(app, _uiState.value.diskBrowserNodes, node.uriString)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(diskBrowserNodes = updatedTree) }
            }
        }
    }

    private fun toggleNodeRecursive(
        app: Application,
        nodes: List<DiskBrowserNode>,
        targetUri: String
    ): List<DiskBrowserNode> {
        return nodes.map { current ->
            if (current.uriString == targetUri && current.isDirectory) {
                if (current.isExpanded) {
                    current.copy(isExpanded = false)
                } else {
                    val docDir = DocumentFile.fromTreeUri(app, Uri.parse(current.uriString))
                    val loadedChildren = if (docDir != null) {
                        buildDiskBrowserNodes(docDir, current.depth + 1)
                    } else {
                        current.children
                    }
                    current.copy(isExpanded = true, children = loadedChildren)
                }
            } else if (current.isDirectory && current.children.isNotEmpty()) {
                current.copy(children = toggleNodeRecursive(app, current.children, targetUri))
            } else {
                current
            }
        }
    }

    private fun buildDiskBrowserNodes(directory: DocumentFile, depth: Int): List<DiskBrowserNode> {
        val entries = directory.listFiles()
        return entries
            .filter { it.name != null && !it.name!!.startsWith(".") }
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })
            .map { file ->
                DiskBrowserNode(
                    uriString = file.uri.toString(),
                    name = file.name ?: "Unnamed",
                    isDirectory = file.isDirectory,
                    depth = depth,
                    isExpanded = false,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
    }

    private fun decodeBytesWithEncodingDetection(bytes: ByteArray): Pair<DocumentEncoding, String> {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return DocumentEncoding.UTF_8_BOM to String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return DocumentEncoding.UTF_16LE to String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return DocumentEncoding.UTF_16BE to String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return DocumentEncoding.UTF_8 to String(bytes, Charsets.UTF_8)
    }

    private fun encodeTextWithBom(text: String, encoding: DocumentEncoding): ByteArray {
        val out = ByteArrayOutputStream(text.length + 4)
        when (encoding) {
            DocumentEncoding.UTF_8_BOM -> {
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            DocumentEncoding.UTF_16LE -> {
                out.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
                out.write(text.toByteArray(Charsets.UTF_16LE))
            }
            DocumentEncoding.UTF_16BE -> {
                out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
                out.write(text.toByteArray(Charsets.UTF_16BE))
            }
            else -> {
                out.write(text.toByteArray(encoding.resolveCharset()))
            }
        }
        return out.toByteArray()
    }

    // =========================================================================
    // PCRE FIND & REPLACE, EXTRACT, PATTERN PLAYGROUND & MULTI-FILE SEARCH
    // =========================================================================

    fun updateSearchOptions(newOptions: SearchOptions) {
        _uiState.update { it.copy(searchOptions = newOptions) }
        refreshActiveDocSearchMatches()
        recomputePlayground()
    }

    fun updatePlaygroundSampleText(sampleText: String) {
        _uiState.update { it.copy(playgroundSampleText = sampleText) }
        recomputePlayground()
    }

    private fun recomputePlayground() {
        val state = _uiState.value
        val eval = FindReplaceEngine.evaluatePatternPlayground(
            sampleText = state.playgroundSampleText,
            options = state.searchOptions
        )
        _uiState.update { it.copy(playgroundEvaluation = eval) }
    }

    private fun refreshActiveDocSearchMatches() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        if (state.searchOptions.query.isEmpty()) {
            _uiState.update { it.copy(activeDocSearchMatches = emptyList(), currentMatchIndex = -1) }
            return
        }
        val selRange = if (state.searchOptions.searchInSelectionOnly && doc.safeSelectionStart < doc.safeSelectionEnd) {
            doc.safeSelectionStart..doc.safeSelectionEnd
        } else {
            null
        }
        val matches = FindReplaceEngine.findAllMatches(doc.content, state.searchOptions, selRange)
        val clampedIdx = if (matches.isEmpty()) -1 else state.currentMatchIndex.coerceIn(0, matches.lastIndex)
        _uiState.update {
            it.copy(
                activeDocSearchMatches = matches,
                currentMatchIndex = clampedIdx
            )
        }
    }

    fun findNextMatch() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val match = FindReplaceEngine.findNext(
            text = doc.content,
            options = state.searchOptions,
            cursorOffset = doc.safeSelectionEnd
        ) ?: return

        val matchIdx = state.activeDocSearchMatches.indexOfFirst { it.range == match.range }
        _uiState.update { it.copy(currentMatchIndex = matchIdx) }
        updateDocumentSelection(doc.id, match.range.first, match.range.last + 1)
        _events.tryEmit(GravityEditUiEvent.ScrollToLine(doc.id, (match.lineNumber - 1).coerceAtLeast(0)))
    }

    fun findPreviousMatch() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val match = FindReplaceEngine.findPrevious(
            text = doc.content,
            options = state.searchOptions,
            cursorOffset = doc.safeSelectionStart
        ) ?: return

        val matchIdx = state.activeDocSearchMatches.indexOfFirst { it.range == match.range }
        _uiState.update { it.copy(currentMatchIndex = matchIdx) }
        updateDocumentSelection(doc.id, match.range.first, match.range.last + 1)
        _events.tryEmit(GravityEditUiEvent.ScrollToLine(doc.id, (match.lineNumber - 1).coerceAtLeast(0)))
    }

    fun replaceCurrentMatch() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val matches = state.activeDocSearchMatches
        if (matches.isEmpty()) return
        val activeMatch = matches.getOrNull(state.currentMatchIndex.coerceAtLeast(0)) ?: matches.first()
        val (updatedText, newCursor) = FindReplaceEngine.replaceSingleMatch(
            text = doc.content,
            targetMatch = activeMatch,
            options = state.searchOptions
        )
        updateDocumentContent(doc.id, updatedText, newCursor, newCursor)
        findNextMatch()
    }

    fun replaceAllInActiveDocument() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val selRange = if (state.searchOptions.searchInSelectionOnly && doc.safeSelectionStart < doc.safeSelectionEnd) {
            doc.safeSelectionStart until doc.safeSelectionEnd
        } else {
            null
        }
        val (updatedText, count) = FindReplaceEngine.replaceAll(
            text = doc.content,
            options = state.searchOptions,
            selectionRange = selRange
        )
        if (count > 0) {
            updateDocumentContent(doc.id, updatedText)
            _uiState.update { it.copy(statusBannerMessage = "Replaced $count occurrence(s)") }
        }
    }

    fun extractMatchesToNewDocument() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val selRange = if (state.searchOptions.searchInSelectionOnly && doc.safeSelectionStart < doc.safeSelectionEnd) {
            doc.safeSelectionStart until doc.safeSelectionEnd
        } else {
            null
        }
        val (extractedContent, count) = FindReplaceEngine.extractMatchesToDocument(
            text = doc.content,
            options = state.searchOptions,
            selectionRange = selRange
        )
        if (count > 0) {
            createNewDocument(
                initialTitle = "Extracted-${doc.title}.txt",
                initialContent = extractedContent,
                language = SyntaxLanguage.PLAIN_TEXT
            )
            _uiState.update {
                it.copy(
                    showFindReplaceModal = false,
                    statusBannerMessage = "Extracted $count match(es) to new document"
                )
            }
        }
    }

    fun runMultiFileSearch() {
        val state = _uiState.value
        if (state.searchOptions.query.isEmpty()) return
        _uiState.update { it.copy(isMultiFileSearching = true) }

        viewModelScope.launch {
            val sources = collectSearchableFileSources()
            val results = FindReplaceEngine.searchAcrossFiles(sources, state.searchOptions)
            _uiState.update {
                it.copy(
                    multiFileSearchMatches = results,
                    isMultiFileSearching = false
                )
            }
        }
    }

    fun runMultiFileReplace() {
        val state = _uiState.value
        if (state.searchOptions.query.isEmpty()) return
        _uiState.update { it.copy(isMultiFileSearching = true) }

        viewModelScope.launch {
            val sources = collectSearchableFileSources()
            val (filesModified, totalReplacements) = FindReplaceEngine.replaceAcrossFiles(sources, state.searchOptions)
            val refreshedResults = FindReplaceEngine.searchAcrossFiles(sources, state.searchOptions)
            _uiState.update {
                it.copy(
                    multiFileSearchMatches = refreshedResults,
                    isMultiFileSearching = false,
                    statusBannerMessage = "Replaced $totalReplacements match(es) across $filesModified file(s)"
                )
            }
        }
    }

    private fun collectSearchableFileSources(): List<SearchableFileSource> {
        val currentDocs = _uiState.value.documents
        val sources = ArrayList<SearchableFileSource>()

        // Include all currently open documents
        for (doc in currentDocs) {
            val docId = doc.id
            sources.add(
                SearchableFileSource(
                    uriString = doc.uriString ?: "memory://$docId",
                    fileName = doc.title,
                    relativePath = "[Open Tab] ${doc.title}",
                    contentProvider = {
                        _uiState.value.documents.firstOrNull { it.id == docId }?.content
                    },
                    contentWriter = { updatedContent ->
                        withContext(Dispatchers.Main) {
                            updateDocumentContent(docId, updatedContent)
                        }
                        true
                    }
                )
            )
        }

        // Also include mounted SAF project directory files not already open
        val rootUriStr = _uiState.value.diskBrowserRootUri
        if (!rootUriStr.isNullOrBlank()) {
            val app = getApplication<Application>()
            val rootDoc = DocumentFile.fromTreeUri(app, Uri.parse(rootUriStr))
            if (rootDoc != null) {
                val openUris = currentDocs.mapNotNull { it.uriString }.toSet()
                collectSafFilesRecursive(app, rootDoc, "", openUris, sources)
            }
        }
        return sources
    }

    private fun collectSafFilesRecursive(
        app: Application,
        dir: DocumentFile,
        parentPath: String,
        skipUris: Set<String>,
        outSources: MutableList<SearchableFileSource>
    ) {
        for (file in dir.listFiles()) {
            val name = file.name ?: continue
            if (name.startsWith(".")) continue
            val relPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
            if (file.isDirectory) {
                collectSafFilesRecursive(app, file, relPath, skipUris, outSources)
            } else if (file.isFile && file.uri.toString() !in skipUris && file.length() < 2_000_000L) {
                val fileUri = file.uri
                outSources.add(
                    SearchableFileSource(
                        uriString = fileUri.toString(),
                        fileName = name,
                        relativePath = relPath,
                        contentProvider = {
                            runCatching {
                                app.contentResolver.openInputStream(fileUri)?.use {
                                    LineEnding.normalizeToLf(String(it.readBytes(), Charsets.UTF_8))
                                }
                            }.getOrNull()
                        },
                        contentWriter = { updatedText ->
                            runCatching {
                                app.contentResolver.openOutputStream(fileUri, "wt")?.use { out ->
                                    out.write(updatedText.toByteArray(Charsets.UTF_8))
                                }
                                true
                            }.getOrDefault(false)
                        }
                    )
                )
            }
        }
    }

    // =========================================================================
    // TEXT FACTORY PIPELINE ACTIONS
    // =========================================================================

    fun addTextFactoryStep(operationType: TextOperationType) {
        _uiState.update { state ->
            state.copy(textFactorySteps = state.textFactorySteps + TextFactoryStep(operationType = operationType))
        }
    }

    fun updateTextFactoryStep(updatedStep: TextFactoryStep) {
        _uiState.update { state ->
            state.copy(
                textFactorySteps = state.textFactorySteps.map {
                    if (it.id == updatedStep.id) updatedStep else it
                }
            )
        }
    }

    fun removeTextFactoryStep(stepId: String) {
        _uiState.update { state ->
            state.copy(textFactorySteps = state.textFactorySteps.filterNot { it.id == stepId })
        }
    }

    fun moveTextFactoryStepUp(stepId: String) {
        _uiState.update { state ->
            val idx = state.textFactorySteps.indexOfFirst { it.id == stepId }
            if (idx <= 0) return@update state
            val list = state.textFactorySteps.toMutableList()
            val item = list.removeAt(idx)
            list.add(idx - 1, item)
            state.copy(textFactorySteps = list)
        }
    }

    fun moveTextFactoryStepDown(stepId: String) {
        _uiState.update { state ->
            val idx = state.textFactorySteps.indexOfFirst { it.id == stepId }
            if (idx < 0 || idx >= state.textFactorySteps.lastIndex) return@update state
            val list = state.textFactorySteps.toMutableList()
            val item = list.removeAt(idx)
            list.add(idx + 1, item)
            state.copy(textFactorySteps = list)
        }
    }

    fun runTextFactoryPipelineOnActiveDocument() {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val steps = state.textFactorySteps
        viewModelScope.launch {
            val result = TextTransformEngine.runPipeline(doc.content, steps)
            updateDocumentContent(doc.id, result.transformedText)
            if (result.targetLineEnding != null) {
                setDocumentLineEnding(result.targetLineEnding)
            }
            for ((title, extractedContent) in result.extractedDocuments) {
                createNewDocument(
                    initialTitle = title,
                    initialContent = extractedContent,
                    language = SyntaxLanguage.PLAIN_TEXT
                )
            }
            _uiState.update {
                it.copy(
                    showTextFactoryModal = false,
                    statusBannerMessage = result.summary
                )
            }
        }
    }

    // =========================================================================
    // SIDE-BY-SIDE DIFF TOOL ACTIONS
    // =========================================================================

    fun setDiffLeftDocument(documentId: String) {
        _uiState.update { it.copy(diffLeftDocId = documentId) }
        recomputeDiff()
    }

    fun setDiffRightDocument(documentId: String) {
        _uiState.update { it.copy(diffRightDocId = documentId) }
        recomputeDiff()
    }

    fun recomputeDiff() {
        val state = _uiState.value
        val leftDoc = state.documents.firstOrNull { it.id == state.diffLeftDocId }
            ?: state.documents.firstOrNull()
            ?: return
        val rightDoc = state.documents.firstOrNull { it.id == state.diffRightDocId }
            ?: state.documents.lastOrNull()
            ?: return

        viewModelScope.launch {
            val diff = DiffEngine.computeDiff(
                leftDocumentId = leftDoc.id,
                rightDocumentId = rightDoc.id,
                leftTitle = leftDoc.title,
                rightTitle = rightDoc.title,
                leftText = leftDoc.content,
                rightText = rightDoc.content
            )
            _uiState.update {
                it.copy(
                    diffLeftDocId = leftDoc.id,
                    diffRightDocId = rightDoc.id,
                    diffResult = diff
                )
            }
        }
    }

    fun mergeDiffRowToLeft(rowIndex: Int) {
        val diff = _uiState.value.diffResult ?: return
        val leftDocId = diff.leftDocumentId ?: return
        val updatedLeftText = DiffEngine.mergeRowRightToLeft(diff, rowIndex)
        updateDocumentContent(leftDocId, updatedLeftText)
        recomputeDiff()
    }

    fun mergeDiffRowToRight(rowIndex: Int) {
        val diff = _uiState.value.diffResult ?: return
        val rightDocId = diff.rightDocumentId ?: return
        val updatedRightText = DiffEngine.mergeRowLeftToRight(diff, rowIndex)
        updateDocumentContent(rightDocId, updatedRightText)
        recomputeDiff()
    }

    // =========================================================================
    // CLIPPINGS & SNIPPETS ACTIONS
    // =========================================================================

    fun setClippingSearchQuery(query: String) {
        _uiState.update { it.copy(clippingSearchQuery = query) }
    }

    fun insertClippingIntoActiveDocument(clipping: Clipping, clipboardText: String = "") {
        val doc = _uiState.value.focusedDocument ?: return
        val snapshot = EditorSnapshot(
            content = doc.content,
            selectionStart = doc.selectionStart,
            selectionEnd = doc.selectionEnd
        )
        val expandedDoc = ClippingsManager.applyClippingToDocument(
            document = doc,
            clipping = clipping,
            clipboardText = clipboardText
        ).copy(
            undoStack = (doc.undoStack + snapshot).takeLast(MAX_UNDO_HISTORY),
            redoStack = emptyList()
        )

        _uiState.update { state ->
            state.copy(
                documents = state.documents.map { if (it.id == doc.id) expandedDoc else it },
                showClippingsModal = false,
                statusBannerMessage = "Inserted clipping: ${clipping.name}"
            )
        }
        refreshAstAndFoldsForActiveDocument()
    }

    fun addCustomClipping(name: String, triggerPrefix: String, templateBody: String) {
        if (name.isBlank() || templateBody.isBlank()) return
        val custom = Clipping(
            name = name.trim(),
            triggerPrefix = triggerPrefix.trim().ifEmpty { "custom" },
            category = ClippingCategory.USER_CUSTOM,
            description = "User custom clipping",
            templateBody = templateBody,
            isBuiltIn = false
        )
        _uiState.update { state ->
            state.copy(clippings = listOf(custom) + state.clippings)
        }
    }

    // =========================================================================
    // DIALOG VISIBILITY & PREFERENCES / RUNTIME LOCALIZATION
    // =========================================================================

    fun setFindReplaceModalVisible(visible: Boolean, initialTab: Int = 0) {
        _uiState.update {
            it.copy(
                showFindReplaceModal = visible,
                findReplaceInitialTab = initialTab.coerceIn(0, 2)
            )
        }
        if (visible) {
            refreshActiveDocSearchMatches()
            recomputePlayground()
        }
    }

    fun setTextFactoryModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showTextFactoryModal = visible) }
    }

    fun setDiffModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showDiffModal = visible) }
        if (visible) {
            recomputeDiff()
        }
    }

    fun setClippingsModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showClippingsModal = visible) }
    }

    fun setDiagnosticsModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showDiagnosticsModal = visible) }
    }

    fun setCommandPaletteModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showCommandPaletteModal = visible) }
    }

    fun runSingleQuickTransform(step: TextFactoryStep) {
        val doc = _uiState.value.focusedDocument ?: return
        viewModelScope.launch {
            val result = TextTransformEngine.runPipeline(doc.content, listOf(step.copy(enabled = true)))
            updateDocumentContent(doc.id, result.transformedText)
            for ((title, extractedContent) in result.extractedDocuments) {
                createNewDocument(
                    initialTitle = title,
                    initialContent = extractedContent,
                    language = SyntaxLanguage.PLAIN_TEXT
                )
            }
            _uiState.update {
                it.copy(
                    showCommandPaletteModal = false,
                    statusBannerMessage = "Executed: ${step.operationType.defaultTitle}"
                )
            }
        }
    }

    fun setGoToLineDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showGoToLineDialog = visible) }
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = visible) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun setThemeMode(mode: GravityThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.THEME_MODE] = mode.name }
        }
    }

    /**
     * Switches the application language at runtime using `AppCompatDelegate.setApplicationLocales()`
     * without losing any open tabs or unsaved editor state.
     */
    fun setApplicationLanguage(localeTag: String) {
        _uiState.update { it.copy(appLocaleTag = localeTag) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.LOCALE_TAG] = localeTag }
        }
        val localeList = LocaleListCompat.forLanguageTags(localeTag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setFontSizeSp(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(10f, 28f)
        _uiState.update { it.copy(fontSizeSp = clamped) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.FONT_SIZE_SP] = clamped }
        }
    }

    fun setTabWidth(width: Int) {
        val clamped = width.coerceIn(2, 8)
        _uiState.update { it.copy(tabWidth = clamped) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.TAB_WIDTH] = clamped }
        }
    }

    fun setSoftWrap(enabled: Boolean) {
        _uiState.update { it.copy(softWrap = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.SOFT_WRAP] = enabled }
        }
    }

    fun setShowInvisibles(enabled: Boolean) {
        _uiState.update { it.copy(showInvisibles = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.SHOW_INVISIBLES] = enabled }
        }
    }

    fun setShowPageGuide80(enabled: Boolean) {
        _uiState.update { it.copy(showPageGuide80 = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.PAGE_GUIDE_80] = enabled }
        }
    }

    fun setShowPageGuide120(enabled: Boolean) {
        _uiState.update { it.copy(showPageGuide120 = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.PAGE_GUIDE_120] = enabled }
        }
    }

    fun setEnableCodeFolding(enabled: Boolean) {
        _uiState.update { it.copy(enableCodeFolding = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.CODE_FOLDING] = enabled }
        }
    }

    companion object {
        private const val SCRATCHPAD_DOC_ID = "doc_persistent_scratchpad"
        private const val MAX_UNDO_HISTORY = 150
    }
}
