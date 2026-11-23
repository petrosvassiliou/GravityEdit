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
import com.gravityedit.app.BuildConfig
import com.gravityedit.app.domain.engine.AppReleaseInfo
import com.gravityedit.app.domain.engine.AppUpdateCheckStatus
import com.gravityedit.app.domain.engine.AppUpdateManagerEngine
import com.gravityedit.app.domain.engine.ClippingsManager
import com.gravityedit.app.domain.engine.DiffEngine
import com.gravityedit.app.domain.engine.FindReplaceEngine
import com.gravityedit.app.domain.engine.GitWorkbenchEngine
import com.gravityedit.app.domain.engine.MarkdownPreviewRenderer
import com.gravityedit.app.domain.engine.PowerShellDiagnostic
import com.gravityedit.app.domain.engine.PowerShellEngine
import com.gravityedit.app.domain.engine.PowerShellRunnerEngine
import com.gravityedit.app.domain.engine.PowerShellToolingEngine
import com.gravityedit.app.domain.engine.RemoteSFTPEngine
import com.gravityedit.app.domain.engine.ScriptExecutionResult
import com.gravityedit.app.domain.engine.ScriptOutputLine
import com.gravityedit.app.domain.engine.ScriptOutputStreamKind
import com.gravityedit.app.domain.engine.SearchableFileSource
import com.gravityedit.app.domain.engine.StructuredDataGridEngine
import com.gravityedit.app.domain.engine.TextTransformEngine
import com.gravityedit.app.domain.engine.UpdateDistributionChannel
import com.gravityedit.app.domain.model.AstSymbol
import com.gravityedit.app.domain.model.Clipping
import com.gravityedit.app.domain.model.ClippingCategory
import com.gravityedit.app.domain.model.CmdletHelpTopic
import com.gravityedit.app.domain.model.ColumnBlockSpec
import com.gravityedit.app.domain.model.ColumnEditAction
import com.gravityedit.app.domain.model.DiffResult
import com.gravityedit.app.domain.model.DiskBrowserNode
import com.gravityedit.app.domain.model.DocumentEncoding
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.EditorSnapshot
import com.gravityedit.app.domain.model.FoldRegion
import com.gravityedit.app.domain.model.GitRepoStatusSummary
import com.gravityedit.app.domain.model.GutterLineChangeKind
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.MultiFileReplacePreviewItem
import com.gravityedit.app.domain.model.MultiFileSearchMatch
import com.gravityedit.app.domain.model.ParameterFormSpec
import com.gravityedit.app.domain.model.PatternPlaygroundEvaluation
import com.gravityedit.app.domain.model.PesterSuiteResult
import com.gravityedit.app.domain.model.RemoteFileLocation
import com.gravityedit.app.domain.model.SavedTextFactoryPreset
import com.gravityedit.app.domain.model.SearchMatch
import com.gravityedit.app.domain.model.SearchOptions
import com.gravityedit.app.domain.model.SftpConnectionProfile
import com.gravityedit.app.domain.model.SftpRemoteEntry
import com.gravityedit.app.domain.model.SplitViewMode
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.domain.model.TextFactoryScopeMode
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
    data class ScrollToLine(
        val documentId: String,
        val lineIndex: Int,
        val requestId: Long = System.nanoTime()
    ) : GravityEditUiEvent
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

    // Find & Replace, Multi-File Search, Dry-Run Preview, and Regex Playground
    val searchOptions: SearchOptions = SearchOptions(),
    val activeDocSearchMatches: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val multiFileSearchMatches: List<MultiFileSearchMatch> = emptyList(),
    val multiFileReplacePreviews: List<MultiFileReplacePreviewItem> = emptyList(),
    val showMultiFileReplacePreview: Boolean = false,
    val isMultiFileSearching: Boolean = false,
    val playgroundSampleText: String = DEFAULT_PLAYGROUND_SAMPLE,
    val playgroundEvaluation: PatternPlaygroundEvaluation = PatternPlaygroundEvaluation(isValidRegex = true),

    // Text Factory Pipeline & Saved Presets
    val textFactorySteps: List<TextFactoryStep> = DEFAULT_TEXT_FACTORY_STEPS,
    val textFactoryScope: TextFactoryScopeMode = TextFactoryScopeMode.ACTIVE_DOCUMENT,
    val savedTextFactoryPresets: List<SavedTextFactoryPreset> = SavedTextFactoryPreset.BUILT_IN_PRESETS,

    // PowerShell Runner & BBEdit Worksheet Drawer
    val showRunnerDrawer: Boolean = false,
    val isScriptRunning: Boolean = false,
    val lastExecutionResult: ScriptExecutionResult? = null,
    val runnerStreamFilter: ScriptOutputStreamKind? = null,

    // Column / Rectangular Block Edit, External Watcher & Recent Files
    val showColumnEditModal: Boolean = false,
    val columnBlockSpec: ColumnBlockSpec = ColumnBlockSpec(),
    val externallyModifiedDocIds: Set<String> = emptySet(),
    val recentFileUris: List<Pair<String, String>> = emptyList(),

    // v1.5.0 Git Workbench, PowerShell Tooling, CSV/JSON Data Grid, Live Preview & Remote SFTP
    val gitStatusSummary: GitRepoStatusSummary? = null,
    val activeGutterChanges: Map<Int, GutterLineChangeKind> = emptyMap(),
    val showParameterFormModal: Boolean = false,
    val parameterFormSpecs: List<ParameterFormSpec> = emptyList(),
    val showQuickHelpModal: Boolean = false,
    val quickHelpQuery: String = "Get-ChildItem",
    val activeHelpTopic: CmdletHelpTopic = PowerShellToolingEngine.lookupQuickHelp("Get-ChildItem"),
    val showPesterModal: Boolean = false,
    val isRunningPester: Boolean = false,
    val lastPesterResult: PesterSuiteResult? = null,
    val activeGridDocumentIds: Set<String> = emptySet(),
    val showLiveMarkdownPreview: Boolean = false,
    val showSftpModal: Boolean = false,
    val sftpProfiles: List<SftpConnectionProfile> = emptyList(),
    val sftpCurrentPath: String = "/",
    val sftpEntries: List<SftpRemoteEntry> = emptyList(),
    val isSftpBusy: Boolean = false,
    val sftpErrorMessage: String? = null,

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

    // Google Play Protect–Compatible Auto-Update State
    val showUpdateModal: Boolean = false,
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val updateDistributionChannel: UpdateDistributionChannel = UpdateDistributionChannel.GITHUB_HTTPS_BROWSER_HANDOFF,
    val updateCheckStatus: AppUpdateCheckStatus = AppUpdateCheckStatus.IDLE,
    val latestReleaseInfo: AppReleaseInfo? = null,
    val updateDownloadProgress: Int = 0,
    val verifiedApkSha256: String? = null,
    val updateErrorMessage: String? = null,
    val autoCheckUpdatesOnStartup: Boolean = true,
    val updateIncludePreReleases: Boolean = false,

    // Modal & Dialog Visibility Flags
    val showNewDocumentDialog: Boolean = false,
    val nextUntitledNumber: Int = 2,
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
        val RECENT_FILES_RAW = stringPreferencesKey("recent_files_raw")
        val WORKSPACE_SESSION_JSON = stringPreferencesKey("workspace_session_json")
        val SFTP_PROFILES_JSON = stringPreferencesKey("sftp_profiles_json")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val UPDATE_INCLUDE_PRERELEASES = booleanPreferencesKey("update_include_prereleases")
    }

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<GravityEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GravityEditUiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GravityEditUiEvent> = _events.asSharedFlow()

    private data class CachedDocAnalysis(
        val contentLength: Int,
        val contentHash: Int,
        val language: SyntaxLanguage,
        val symbols: List<AstSymbol>,
        val folds: List<FoldRegion>,
        val diagnostics: List<PowerShellDiagnostic>
    )

    private var astParseJob: Job? = null
    private var scratchpadSaveJob: Job? = null
    private var sessionSaveJob: Job? = null
    private var externalWatchJob: Job? = null
    private val analysisCache = java.util.concurrent.ConcurrentHashMap<String, CachedDocAnalysis>()
    private val sessionSftpPasswords = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var untitledCounter = 2

    init {
        loadPersistedStateAndInitialize()
        refreshAstAndFoldsForActiveDocument()
        recomputePlayground()
        startExternalSafWatcher()
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
        val symbols = PowerShellEngine.extractAstSymbols(mainDoc.content, mainDoc.language)
        val folds = PowerShellEngine.extractFoldRegions(mainDoc.content, mainDoc.language)
        val channel = AppUpdateManagerEngine.detectDistributionChannel(getApplication())
        return GravityEditUiState(
            documents = listOf(mainDoc, scratchpadDoc),
            activeDocumentId = mainDoc.id,
            secondaryDocumentId = scratchpadDoc.id,
            astSymbols = symbols,
            foldRegions = folds,
            diffLeftDocId = mainDoc.id,
            diffRightDocId = scratchpadDoc.id,
            updateDistributionChannel = channel
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
            val savedRecentRaw = prefs[PrefKeys.RECENT_FILES_RAW] ?: ""
            val savedSessionJson = prefs[PrefKeys.WORKSPACE_SESSION_JSON]
            val savedSftpJson = prefs[PrefKeys.SFTP_PROFILES_JSON]
            val savedAutoCheckUpdates = prefs[PrefKeys.AUTO_CHECK_UPDATES] ?: true
            val savedIncludePreReleases = prefs[PrefKeys.UPDATE_INCLUDE_PRERELEASES] ?: false

            val parsedRecents: List<Pair<String, String>> = savedRecentRaw.lines()
                .mapNotNull { row ->
                    val sep = row.indexOf('\t')
                    if (sep > 0) row.substring(0, sep) to row.substring(sep + 1) else null
                }

            val restoredSession = savedSessionJson?.let { deserializeWorkspaceSession(it, savedScratchpad) }
            val restoredSftpProfiles = savedSftpJson?.let { deserializeSftpProfiles(it) } ?: emptyList()

            _uiState.update { state ->
                val updatedDocs = restoredSession?.first ?: if (!savedScratchpad.isNullOrEmpty()) {
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
                    activeDocumentId = restoredSession?.second ?: state.activeDocumentId,
                    splitViewMode = restoredSession?.third ?: state.splitViewMode,
                    recentFileUris = parsedRecents,
                    sftpProfiles = restoredSftpProfiles,
                    themeMode = savedTheme,
                    appLocaleTag = savedLocale,
                    fontSizeSp = savedFontSize,
                    tabWidth = savedTabWidth,
                    softWrap = savedSoftWrap,
                    showInvisibles = savedInvisibles,
                    showPageGuide80 = savedGuide80,
                    showPageGuide120 = savedGuide120,
                    enableCodeFolding = savedFolding,
                    autoCheckUpdatesOnStartup = savedAutoCheckUpdates,
                    updateIncludePreReleases = savedIncludePreReleases
                )
            }

            if (!savedFolderUri.isNullOrBlank()) {
                runCatching {
                    mountProjectDirectory(Uri.parse(savedFolderUri), persist = false)
                }
            }

            if (savedAutoCheckUpdates) {
                checkForAppUpdates(silentIfUpToDate = true)
            }
        }
    }

    // =========================================================================
    // DOCUMENT & MULTI-TAB MANAGEMENT
    // =========================================================================

    fun setNewDocumentDialogVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                showNewDocumentDialog = visible,
                nextUntitledNumber = untitledCounter
            )
        }
    }

    fun createNewDocument(
        initialTitle: String? = null,
        initialContent: String = "",
        language: SyntaxLanguage = SyntaxLanguage.PLAIN_TEXT,
        encoding: DocumentEncoding = DocumentEncoding.UTF_8,
        lineEnding: LineEnding = LineEnding.LF,
        useStarterTemplate: Boolean = false
    ) {
        val ext = language.extensions.firstOrNull() ?: "txt"
        val resolvedTitle = initialTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Untitled-${untitledCounter++}.$ext"
        if (initialTitle != null && initialTitle.startsWith("Untitled-")) {
            untitledCounter++
        }
        val resolvedContent = if (initialContent.isNotEmpty()) {
            initialContent
        } else if (useStarterTemplate) {
            starterTemplateForFile(resolvedTitle, language)
        } else {
            ""
        }
        val newDoc = EditorDocument(
            title = resolvedTitle,
            content = resolvedContent,
            savedContent = resolvedContent,
            language = language,
            encoding = encoding,
            lineEnding = lineEnding
        )
        _uiState.update { state ->
            state.copy(
                documents = state.documents + newDoc,
                activeDocumentId = newDoc.id,
                showNewDocumentDialog = false,
                nextUntitledNumber = untitledCounter,
                statusBannerMessage = "Created $resolvedTitle (${language.displayName}, ${encoding.displayName}, ${lineEnding.badge})"
            )
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    private fun starterTemplateForFile(fileName: String, language: SyntaxLanguage): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext == "psm1" -> """
                # PowerShell Module ($fileName)
                function Invoke-ModuleAction {
                    [CmdletBinding()]
                    param (
                        [Parameter(Mandatory = ${'$'}true)]
                        [string]${'$'}InputObject
                    )
                    Write-Output ${'$'}InputObject
                }

                Export-ModuleMember -Function 'Invoke-*'
            """.trimIndent()
            ext == "psd1" -> """
                @{
                    RootModule        = '${fileName.substringBeforeLast('.')}.psm1'
                    ModuleVersion     = '1.0.0'
                    GUID              = '00000000-0000-0000-0000-000000000000'
                    Author            = 'GravityEdit Developer'
                    Description       = 'PowerShell Module Manifest'
                    PowerShellVersion = '7.2'
                    FunctionsToExport = @('*')
                    CmdletsToExport   = @()
                    VariablesToExport = @()
                    AliasesToExport   = @()
                }
            """.trimIndent()
            language == SyntaxLanguage.POWERSHELL -> """
                [CmdletBinding()]
                param (
                    [Parameter(Mandatory = ${'$'}false)]
                    [string]${'$'}Path = "."
                )

                Get-ChildItem -Path ${'$'}Path
            """.trimIndent()
            language == SyntaxLanguage.JSON -> """
                {
                  "name": "${fileName.substringBeforeLast('.')}",
                  "version": "1.0.0",
                  "enabled": true
                }
            """.trimIndent()
            language == SyntaxLanguage.YAML -> """
                # $fileName
                name: ${fileName.substringBeforeLast('.')}
                version: "1.0.0"
                environment: production
            """.trimIndent()
            language == SyntaxLanguage.XML -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration>
                    <property name="app" value="GravityEdit" />
                </configuration>
            """.trimIndent()
            language == SyntaxLanguage.MARKDOWN -> """
                # ${fileName.substringBeforeLast('.')}

                ## Overview
                Created with **GravityEdit**.
            """.trimIndent()
            language == SyntaxLanguage.PYTHON -> """
                #!/usr/bin/env python3
                # $fileName


                def main() -> None:
                    print("Hello from $fileName")


                if __name__ == "__main__":
                    main()
            """.trimIndent()
            language == SyntaxLanguage.KOTLIN -> """
                package com.example

                fun main() {
                    println("Hello from $fileName")
                }
            """.trimIndent()
            language == SyntaxLanguage.SHELL_BASH -> """
                #!/usr/bin/env bash
                set -euo pipefail

                echo "Running $fileName..."
            """.trimIndent()
            language == SyntaxLanguage.HTML_CSS && ext == "css" -> """
                :root {
                  --bg: #13161c;
                  --fg: #e3e7f0;
                }

                body {
                  margin: 0;
                  background: var(--bg);
                  color: var(--fg);
                }
            """.trimIndent()
            language == SyntaxLanguage.HTML_CSS -> """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>${fileName.substringBeforeLast('.')}</title>
                </head>
                <body>
                  <h1>${fileName.substringBeforeLast('.')}</h1>
                </body>
                </html>
            """.trimIndent()
            else -> ""
        }
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

    fun renameDocument(documentId: String, newTitle: String) {
        val cleanInput = newTitle.trim()
        if (cleanInput.isEmpty()) return

        _uiState.update { state ->
            val targetDoc = state.documents.firstOrNull { it.id == documentId } ?: return@update state
            val cleanTitle = EditorDocument.normalizedRenamedTitle(targetDoc.title, cleanInput)
            if (targetDoc.title == cleanTitle) return@update state

            val resolvedLanguage = SyntaxLanguage.fromFileName(cleanTitle)

            val updatedDocs = state.documents.map { doc ->
                if (doc.id == documentId) {
                    doc.copy(
                        title = cleanTitle,
                        language = resolvedLanguage
                    )
                } else {
                    doc
                }
            }
            state.copy(
                documents = updatedDocs,
                statusBannerMessage = "Renamed to $cleanTitle (${resolvedLanguage.displayName})"
            )
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun selectSecondaryDocument(documentId: String) {
        _uiState.update { it.copy(secondaryDocumentId = documentId) }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun setFocusedPane(paneIndex: Int) {
        _uiState.update { it.copy(focusedPane = paneIndex.coerceIn(0, 1)) }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
    }

    fun closeDocument(documentId: String) {
        analysisCache.remove(documentId)
        _uiState.update { state ->
            val target = state.documents.firstOrNull { it.id == documentId } ?: return@update state
            if (target.isScratchpad && state.documents.size <= 1) {
                return@update state
            }
            val remaining = state.documents.filterNot { it.id == documentId }.ifEmpty {
                listOf(
                    EditorDocument(
                        title = "Untitled-${untitledCounter++}.txt",
                        language = SyntaxLanguage.PLAIN_TEXT
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
            val keptIds = kept.map { it.id }.toSet()
            analysisCache.keys.removeIf { it !in keptIds }
            state.copy(
                documents = kept,
                activeDocumentId = keepDocumentId,
                secondaryDocumentId = kept.last().id
            )
        }
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
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
        refreshActiveDocSearchMatches()
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
        scheduleSessionPersistence()
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
        val docId = doc.id
        val contentSnapshot = doc.content
        val baselineSnapshot = doc.savedContent
        val isLargeSnapshot = doc.isLargeFileMode
        val contentLen = contentSnapshot.length
        val contentHash = contentSnapshot.hashCode()
        val languageSnapshot = doc.language

        val cached = analysisCache[docId]
        if (cached != null &&
            cached.contentLength == contentLen &&
            cached.contentHash == contentHash &&
            cached.language == languageSnapshot
        ) {
            val gutterMap = if (!isLargeSnapshot) {
                DiffEngine.computeGutterLineChanges(baselineSnapshot, contentSnapshot)
            } else emptyMap()
            _uiState.update { state ->
                state.copy(
                    astSymbols = cached.symbols,
                    foldRegions = cached.folds,
                    diagnostics = cached.diagnostics,
                    activeGutterChanges = gutterMap
                )
            }
            return
        }

        if (languageSnapshot == SyntaxLanguage.PLAIN_TEXT) {
            val emptyEntry = CachedDocAnalysis(
                contentLength = contentLen,
                contentHash = contentHash,
                language = languageSnapshot,
                symbols = emptyList(),
                folds = emptyList(),
                diagnostics = emptyList()
            )
            analysisCache[docId] = emptyEntry
            val gutterMap = if (!isLargeSnapshot) {
                DiffEngine.computeGutterLineChanges(baselineSnapshot, contentSnapshot)
            } else emptyMap()
            _uiState.update { state ->
                state.copy(
                    astSymbols = emptyList(),
                    foldRegions = emptyList(),
                    diagnostics = emptyList(),
                    activeGutterChanges = gutterMap
                )
            }
            return
        }

        astParseJob = viewModelScope.launch(Dispatchers.Default) {
            val symbols = PowerShellEngine.extractAstSymbols(contentSnapshot, languageSnapshot)
            val folds = PowerShellEngine.extractFoldRegions(contentSnapshot, languageSnapshot)
            val diags = if (languageSnapshot == SyntaxLanguage.POWERSHELL) {
                PowerShellEngine.analyzeScriptDiagnostics(contentSnapshot)
            } else {
                emptyList()
            }
            val gutterMap = if (!isLargeSnapshot) {
                DiffEngine.computeGutterLineChanges(baselineSnapshot, contentSnapshot)
            } else emptyMap()
            analysisCache[docId] = CachedDocAnalysis(
                contentLength = contentLen,
                contentHash = contentHash,
                language = languageSnapshot,
                symbols = symbols,
                folds = folds,
                diagnostics = diags
            )
            _uiState.update { state ->
                if (state.focusedDocument?.id != docId) return@update state
                state.copy(
                    astSymbols = symbols,
                    foldRegions = folds,
                    diagnostics = diags,
                    activeGutterChanges = gutterMap
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
        val updatedTitle = if (doc.isScratchpad) {
            doc.title
        } else {
            EditorDocument.titleSynchronizedWithLanguage(doc.title, language)
        }
        _uiState.update { state ->
            state.copy(
                documents = state.documents.map {
                    if (it.id == doc.id) it.copy(language = language, title = updatedTitle) else it
                },
                statusBannerMessage = "File type set to ${language.displayName} (.${language.extensions.firstOrNull() ?: "txt"})"
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
        refreshAstAndFoldsForActiveDocument()
        refreshActiveDocSearchMatches()
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

    fun requestSaveDocument(documentId: String) {
        val doc = _uiState.value.documents.firstOrNull { it.id == documentId } ?: return
        if (doc.uriString != null && doc.title == doc.savedTitle) {
            saveDocumentToUri(doc.id, Uri.parse(doc.uriString))
        } else if (doc.remoteLocation != null) {
            saveRemoteSftpDocument(doc)
        } else {
            _events.tryEmit(GravityEditUiEvent.RequestSaveAsFilePicker(doc.id, doc.title))
        }
    }

    fun requestSaveActiveDocument() {
        val doc = _uiState.value.focusedDocument ?: return
        requestSaveDocument(doc.id)
    }

    fun requestSaveAsActiveDocument() {
        val doc = _uiState.value.focusedDocument ?: return
        _events.tryEmit(GravityEditUiEvent.RequestSaveAsFilePicker(doc.id, doc.title))
    }

    fun requestMountProjectFolder() {
        _events.tryEmit(GravityEditUiEvent.RequestMountFolderPicker)
    }

    fun openFileFromUri(uri: Uri, jumpToLineNumber: Int? = null) {
        // Handle in-memory open tab URIs (`memory://<docId>`) from Multi-File Search without hitting ContentResolver
        if (uri.scheme.equals("memory", ignoreCase = true)) {
            val docId = (uri.host ?: uri.schemeSpecificPart.removePrefix("//")).trim()
            if (docId.isNotEmpty() && _uiState.value.documents.any { it.id == docId }) {
                selectDocument(docId)
                if (jumpToLineNumber != null) {
                    jumpToLine(jumpToLineNumber)
                }
            }
            return
        }

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
                    if (jumpToLineNumber != null) {
                        jumpToLine(jumpToLineNumber)
                    }
                }
                return@launch
            }

            val docFile = DocumentFile.fromSingleUri(app, uri)
            val resolvedName = docFile?.name
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "Document.ps1"

            val rawBytes = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrElse { err ->
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(statusBannerMessage = "Unable to open $resolvedName: ${err.message ?: "I/O error"}") }
                }
                return@launch
            } ?: return@launch

            val (detectedEncoding, rawText) = decodeBytesWithEncodingDetection(rawBytes)
            val detectedLineEnding = LineEnding.detect(rawText)
            val normalizedText = LineEnding.normalizeToLf(rawText)
            val detectedLang = SyntaxLanguage.fromFileName(resolvedName)

            val openedDoc = EditorDocument(
                title = resolvedName,
                savedTitle = resolvedName,
                uriString = uri.toString(),
                content = normalizedText,
                savedContent = normalizedText,
                language = detectedLang,
                encoding = detectedEncoding,
                lineEnding = detectedLineEnding,
                lastKnownDiskModified = docFile?.lastModified() ?: 0L
            )

            withContext(Dispatchers.Main) {
                recordRecentFile(resolvedName, uri.toString())
                _uiState.update { state ->
                    val fastBadge = if (openedDoc.isLargeFileMode) " • Fast Viewport" else ""
                    state.copy(
                        documents = state.documents + openedDoc,
                        activeDocumentId = openedDoc.id,
                        statusBannerMessage = "Opened $resolvedName (${detectedEncoding.displayName}, ${detectedLineEnding.badge}$fastBadge)"
                    )
                }
                scheduleSessionPersistence()
                refreshAstAndFoldsForActiveDocument()
                refreshActiveDocSearchMatches()
                if (jumpToLineNumber != null) {
                    jumpToLine(jumpToLineNumber)
                }
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
                                savedTitle = updatedTitle,
                                uriString = targetUri.toString(),
                                remoteLocation = null,
                                savedContent = d.content,
                                language = updatedLang
                            )
                        },
                        statusBannerMessage = "Saved $updatedTitle (${doc.encoding.displayName}, ${doc.lineEnding.badge})"
                    )
                }
                refreshAstAndFoldsForActiveDocument()
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
            val dirtyCount = _uiState.value.documents.count { it.isDirty }
            val gitSummary = GitWorkbenchEngine.detectSafFolderGitStatus(app, treeUri, dirtyCount)
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        diskBrowserRootUri = treeUri.toString(),
                        diskBrowserRootName = rootName,
                        diskBrowserNodes = children,
                        gitStatusSummary = gitSummary,
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
            doc.safeSelectionStart until doc.safeSelectionEnd
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
        val inSelectionOnly = state.searchOptions.searchInSelectionOnly && doc.safeSelectionStart < doc.safeSelectionEnd
        if (inSelectionOnly) {
            val matches = state.activeDocSearchMatches
            if (matches.isEmpty()) return
            val nextIdx = when {
                state.currentMatchIndex + 1 < matches.size -> state.currentMatchIndex + 1
                state.searchOptions.wrapAround -> 0
                else -> return
            }
            val match = matches[nextIdx]
            _uiState.update { it.copy(currentMatchIndex = nextIdx) }
            _events.tryEmit(GravityEditUiEvent.ScrollToLine(doc.id, (match.lineNumber - 1).coerceAtLeast(0)))
            return
        }

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
        val inSelectionOnly = state.searchOptions.searchInSelectionOnly && doc.safeSelectionStart < doc.safeSelectionEnd
        if (inSelectionOnly) {
            val matches = state.activeDocSearchMatches
            if (matches.isEmpty()) return
            val prevIdx = when {
                state.currentMatchIndex - 1 >= 0 -> state.currentMatchIndex - 1
                state.searchOptions.wrapAround -> matches.lastIndex
                else -> return
            }
            val match = matches[prevIdx]
            _uiState.update { it.copy(currentMatchIndex = prevIdx) }
            _events.tryEmit(GravityEditUiEvent.ScrollToLine(doc.id, (match.lineNumber - 1).coerceAtLeast(0)))
            return
        }

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

    fun previewMultiFileReplace() {
        val state = _uiState.value
        if (state.searchOptions.query.isEmpty()) return
        _uiState.update { it.copy(isMultiFileSearching = true) }

        viewModelScope.launch {
            val sources = collectSearchableFileSources()
            val previews = FindReplaceEngine.previewReplaceAcrossFiles(sources, state.searchOptions)
            _uiState.update {
                it.copy(
                    multiFileReplacePreviews = previews,
                    showMultiFileReplacePreview = true,
                    isMultiFileSearching = false,
                    statusBannerMessage = if (previews.isEmpty()) "No matching files require replacement" else null
                )
            }
        }
    }

    fun toggleMultiFilePreviewSelection(uriString: String) {
        _uiState.update { state ->
            state.copy(
                multiFileReplacePreviews = state.multiFileReplacePreviews.map {
                    if (it.uriString == uriString) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun commitPreviewedMultiFileReplace() {
        val selected = _uiState.value.multiFileReplacePreviews.filter { it.isSelected }.map { it.uriString }.toSet()
        if (selected.isEmpty()) return
        runMultiFileReplace(selectedUris = selected)
    }

    fun runMultiFileReplace(selectedUris: Set<String>? = null) {
        val state = _uiState.value
        if (state.searchOptions.query.isEmpty()) return
        _uiState.update { it.copy(isMultiFileSearching = true) }

        viewModelScope.launch {
            val sources = collectSearchableFileSources()
            val (filesModified, totalReplacements) = FindReplaceEngine.replaceAcrossFiles(
                files = sources,
                options = state.searchOptions,
                selectedUris = selectedUris
            )
            val refreshedResults = FindReplaceEngine.searchAcrossFiles(sources, state.searchOptions)
            _uiState.update {
                it.copy(
                    multiFileSearchMatches = refreshedResults,
                    multiFileReplacePreviews = emptyList(),
                    showMultiFileReplacePreview = false,
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

    fun setTextFactoryScope(scope: TextFactoryScopeMode) {
        _uiState.update { it.copy(textFactoryScope = scope) }
    }

    fun saveCurrentPipelineAsPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state ->
            val preset = SavedTextFactoryPreset(name = trimmed, steps = state.textFactorySteps, isBuiltIn = false)
            val filtered = state.savedTextFactoryPresets.filterNot { !it.isBuiltIn && it.name.equals(trimmed, ignoreCase = true) }
            state.copy(
                savedTextFactoryPresets = filtered + preset,
                statusBannerMessage = "Saved Text Factory preset '$trimmed'"
            )
        }
    }

    fun loadTextFactoryPreset(preset: SavedTextFactoryPreset) {
        _uiState.update { state ->
            state.copy(
                textFactorySteps = preset.steps.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                statusBannerMessage = "Loaded preset '${preset.name}'"
            )
        }
    }

    fun deleteTextFactoryPreset(presetId: String) {
        _uiState.update { state ->
            state.copy(savedTextFactoryPresets = state.savedTextFactoryPresets.filterNot { !it.isBuiltIn && it.id == presetId })
        }
    }

    fun runTextFactoryPipelineOnActiveDocument() {
        val state = _uiState.value
        val steps = state.textFactorySteps
        viewModelScope.launch {
            when (state.textFactoryScope) {
                TextFactoryScopeMode.SELECTION -> {
                    val doc = state.focusedDocument ?: return@launch
                    val s = doc.safeSelectionStart
                    val e = doc.safeSelectionEnd
                    if (e <= s) {
                        _uiState.update { it.copy(statusBannerMessage = "Select text first or switch scope to Active Document") }
                        return@launch
                    }
                    val result = TextTransformEngine.runPipeline(doc.selectedText, steps)
                    val merged = doc.content.substring(0, s) + result.transformedText + doc.content.substring(e)
                    updateDocumentContent(doc.id, merged, s, s + result.transformedText.length)
                    _uiState.update { it.copy(showTextFactoryModal = false, statusBannerMessage = "${result.summary} (Selection)") }
                }
                TextFactoryScopeMode.ACTIVE_DOCUMENT -> {
                    val doc = state.focusedDocument ?: return@launch
                    val result = TextTransformEngine.runPipeline(doc.content, steps)
                    updateDocumentContent(doc.id, result.transformedText)
                    if (result.targetLineEnding != null) {
                        setDocumentLineEnding(result.targetLineEnding)
                    }
                    for ((title, extractedContent) in result.extractedDocuments) {
                        createNewDocument(initialTitle = title, initialContent = extractedContent, language = SyntaxLanguage.PLAIN_TEXT)
                    }
                    _uiState.update { it.copy(showTextFactoryModal = false, statusBannerMessage = result.summary) }
                }
                TextFactoryScopeMode.ALL_OPEN_DOCUMENTS -> {
                    var count = 0
                    for (doc in state.documents.filterNot { it.isScratchpad }) {
                        val res = TextTransformEngine.runPipeline(doc.content, steps)
                        updateDocumentContent(doc.id, res.transformedText)
                        count++
                    }
                    _uiState.update {
                        it.copy(
                            showTextFactoryModal = false,
                            statusBannerMessage = "Applied pipeline across $count open document(s)"
                        )
                    }
                }
                TextFactoryScopeMode.MOUNTED_FOLDER -> {
                    val sources = collectSearchableFileSources().filterNot { it.uriString.startsWith("memory://") }
                    val (modified, scanned) = TextTransformEngine.runPipelineAcrossFiles(sources, steps, state.searchOptions.fileFilter)
                    _uiState.update {
                        it.copy(
                            showTextFactoryModal = false,
                            statusBannerMessage = "Batch Text Factory modified $modified of $scanned file(s)"
                        )
                    }
                }
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

    fun swapDiffDocuments() {
        _uiState.update { state ->
            state.copy(
                diffLeftDocId = state.diffRightDocId,
                diffRightDocId = state.diffLeftDocId
            )
        }
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
        val activeLang = _uiState.value.focusedDocument?.language ?: SyntaxLanguage.POWERSHELL
        val custom = Clipping(
            name = name.trim(),
            triggerPrefix = triggerPrefix.trim().ifEmpty { "custom" },
            category = ClippingCategory.USER_CUSTOM,
            description = "User custom clipping",
            templateBody = templateBody,
            syntaxLanguage = activeLang,
            isBuiltIn = false
        )
        _uiState.update { state ->
            state.copy(clippings = listOf(custom) + state.clippings)
        }
    }

    fun deleteCustomClipping(clippingId: String) {
        _uiState.update { state ->
            state.copy(clippings = state.clippings.filterNot { !it.isBuiltIn && it.id == clippingId })
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
            val selStart = doc.safeSelectionStart
            val selEnd = doc.safeSelectionEnd
            val hasSelection = selEnd > selStart
            val input = if (hasSelection) doc.selectedText else doc.content
            val result = TextTransformEngine.runPipeline(input, listOf(step.copy(enabled = true)))
            if (hasSelection) {
                val merged = doc.content.substring(0, selStart) + result.transformedText + doc.content.substring(selEnd)
                updateDocumentContent(
                    documentId = doc.id,
                    newContent = merged,
                    newSelectionStart = selStart,
                    newSelectionEnd = selStart + result.transformedText.length
                )
            } else {
                updateDocumentContent(doc.id, result.transformedText)
            }
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
                    showCommandPaletteModal = false,
                    statusBannerMessage = "Executed: ${step.operationType.name.replace('_', ' ')}"
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

    // =========================================================================
    // GOOGLE PLAY PROTECT–COMPATIBLE AUTO-UPDATE MECHANISM
    // =========================================================================

    fun setUpdateModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showUpdateModal = visible) }
    }

    fun setAutoCheckUpdatesOnStartup(enabled: Boolean) {
        _uiState.update { it.copy(autoCheckUpdatesOnStartup = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.AUTO_CHECK_UPDATES] = enabled }
        }
    }

    fun setUpdateIncludePreReleases(enabled: Boolean) {
        _uiState.update { it.copy(updateIncludePreReleases = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.UPDATE_INCLUDE_PRERELEASES] = enabled }
        }
    }

    fun checkForAppUpdates(silentIfUpToDate: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.updateCheckStatus == AppUpdateCheckStatus.CHECKING ||
            currentState.updateCheckStatus == AppUpdateCheckStatus.DOWNLOADING
        ) {
            return
        }

        val channel = AppUpdateManagerEngine.detectDistributionChannel(getApplication())
        _uiState.update {
            it.copy(
                updateDistributionChannel = channel,
                updateCheckStatus = AppUpdateCheckStatus.CHECKING,
                updateErrorMessage = null
            )
        }

        viewModelScope.launch {
            val result = AppUpdateManagerEngine.fetchLatestRelease(
                includePreReleases = _uiState.value.updateIncludePreReleases
            )
            result.fold(
                onSuccess = { release ->
                    val isNewer = AppUpdateManagerEngine.isRemoteVersionNewer(
                        currentVersion = BuildConfig.VERSION_NAME,
                        remoteVersion = release.versionName
                    )
                    _uiState.update { state ->
                        state.copy(
                            latestReleaseInfo = release,
                            updateCheckStatus = if (isNewer) {
                                AppUpdateCheckStatus.UPDATE_AVAILABLE
                            } else {
                                AppUpdateCheckStatus.UP_TO_DATE
                            },
                            statusBannerMessage = when {
                                isNewer -> "Update available: GravityEdit ${release.tagName} (Play Protect Verified)"
                                !silentIfUpToDate -> "GravityEdit v${BuildConfig.VERSION_NAME} is up to date (${release.tagName})"
                                else -> state.statusBannerMessage
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            updateCheckStatus = AppUpdateCheckStatus.ERROR,
                            updateErrorMessage = err.message ?: "Unable to reach HTTPS release server",
                            statusBannerMessage = if (!silentIfUpToDate) {
                                "Update check failed: ${err.message}"
                            } else {
                                state.statusBannerMessage
                            }
                        )
                    }
                }
            )
        }
    }

    /**
     * Executes a Google Play Protect–clean update handoff:
     * - If installed via Google Play Store (`com.android.vending`), opens Google Play Store.
     * - Otherwise hands off the official HTTPS release URL to the user's trusted system browser
     *   without declaring `REQUEST_INSTALL_PACKAGES` or downloading `.apk` files inside the app.
     */
    fun downloadAndInstallUpdate() {
        val context = getApplication<Application>()
        val state = _uiState.value

        if (state.updateDistributionChannel == UpdateDistributionChannel.GOOGLE_PLAY_STORE) {
            AppUpdateManagerEngine.launchGooglePlayStorePage(context)
            return
        }

        viewModelScope.launch {
            val release = state.latestReleaseInfo ?: run {
                _uiState.update {
                    it.copy(
                        updateCheckStatus = AppUpdateCheckStatus.CHECKING,
                        updateErrorMessage = null
                    )
                }
                val fetched = AppUpdateManagerEngine.fetchLatestRelease(
                    includePreReleases = _uiState.value.updateIncludePreReleases
                )
                fetched.getOrElse { err ->
                    _uiState.update {
                        it.copy(
                            updateCheckStatus = AppUpdateCheckStatus.ERROR,
                            updateErrorMessage = err.message ?: "Unable to reach HTTPS release server"
                        )
                    }
                    return@launch
                }.also { rel ->
                    _uiState.update { it.copy(latestReleaseInfo = rel) }
                }
            }

            _uiState.update {
                it.copy(
                    verifiedApkSha256 = release.expectedSha256,
                    statusBannerMessage = "Opening ${release.tagName} in trusted system browser…"
                )
            }
            AppUpdateManagerEngine.launchSystemVerifiedUpdate(context, release)
        }
    }

    fun openLatestReleaseInBrowser() {
        val context = getApplication<Application>()
        val url = _uiState.value.latestReleaseInfo?.htmlUrl
            ?: AppUpdateManagerEngine.GITHUB_RELEASES_WEB_URL
        AppUpdateManagerEngine.openUrlInBrowser(context, url)
    }

    // =========================================================================
    // v1.4.0 POWERSHELL RUNNER, BBEDIT WORKSHEET, BOOKMARKS, COLUMN EDIT & SESSION
    // =========================================================================

    fun setRunnerDrawerVisible(visible: Boolean) {
        _uiState.update { it.copy(showRunnerDrawer = visible) }
    }

    fun setRunnerStreamFilter(filter: ScriptOutputStreamKind?) {
        _uiState.update { it.copy(runnerStreamFilter = filter) }
    }

    fun clearRunnerOutput() {
        _uiState.update { it.copy(lastExecutionResult = null) }
    }

    fun runActiveScript() {
        val doc = _uiState.value.focusedDocument ?: return
        _uiState.update { it.copy(isScriptRunning = true, showRunnerDrawer = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val res = PowerShellRunnerEngine.executeScript(doc.content, lineNumberBaseOffset = 0)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isScriptRunning = false, lastExecutionResult = res) }
            }
        }
    }

    fun runSelectionOrCurrentLine() {
        val doc = _uiState.value.focusedDocument ?: return
        val hasSel = doc.safeSelectionEnd > doc.safeSelectionStart
        val lineOffset = doc.lineIndexForOffset(doc.safeSelectionStart)
        val snippet = if (hasSel) {
            doc.selectedText
        } else {
            doc.lines.getOrElse(lineOffset) { "" }
        }
        _uiState.update { it.copy(isScriptRunning = true, showRunnerDrawer = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val res = PowerShellRunnerEngine.executeScript(snippet, lineNumberBaseOffset = lineOffset)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isScriptRunning = false, lastExecutionResult = res) }
            }
        }
    }

    fun evaluateWorksheetInline() {
        val doc = _uiState.value.focusedDocument ?: return
        val selStart = doc.safeSelectionStart
        val selEnd = doc.safeSelectionEnd
        val lineOffset = doc.lineIndexForOffset(selStart)
        val snippet = if (selEnd > selStart) doc.selectedText else doc.lines.getOrElse(lineOffset) { "" }
        _uiState.update { it.copy(isScriptRunning = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val res = PowerShellRunnerEngine.executeScript(snippet, lineNumberBaseOffset = lineOffset)
            val (updatedText, newCursor) = PowerShellRunnerEngine.formatWorksheetInsertion(
                documentText = doc.content,
                selectionStart = selStart,
                selectionEnd = selEnd,
                result = res
            )
            withContext(Dispatchers.Main) {
                updateDocumentContent(doc.id, updatedText, newCursor, newCursor)
                _uiState.update {
                    it.copy(
                        isScriptRunning = false,
                        lastExecutionResult = res,
                        statusBannerMessage = "Worksheet evaluated (${res.durationMs} ms)"
                    )
                }
            }
        }
    }

    fun jumpToExecutionOutputLine(line: ScriptOutputLine) {
        val loc = line.location ?: return
        jumpToLine(loc.lineNumber)
    }

    fun shareActiveScriptToExternalTerminal() {
        val doc = _uiState.value.focusedDocument ?: return
        val app = getApplication<Application>()
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, doc.title)
                putExtra(Intent.EXTRA_TEXT, doc.content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Send ${doc.title} to Terminal / Shell").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        }
    }

    fun openColumnEditModal() {
        val doc = _uiState.value.focusedDocument
        val spec = if (doc != null) {
            val sLn = doc.lineIndexForOffset(doc.safeSelectionStart) + 1
            val eLn = doc.lineIndexForOffset(doc.safeSelectionEnd).coerceAtLeast(sLn - 1) + 1
            val (_, col) = doc.cursorLineAndColumn()
            ColumnBlockSpec(startLine = sLn, endLine = maxOf(sLn, eLn), startColumn = col, endColumn = col + 4)
        } else {
            ColumnBlockSpec()
        }
        _uiState.update { it.copy(showColumnEditModal = true, columnBlockSpec = spec) }
    }

    fun setColumnEditModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showColumnEditModal = visible) }
    }

    fun updateColumnBlockSpec(spec: ColumnBlockSpec) {
        _uiState.update { it.copy(columnBlockSpec = spec) }
    }

    fun applyColumnBlockOperation(
        action: ColumnEditAction,
        insertText: String,
        startNumber: Int = 1,
        stepNumber: Int = 1,
        zeroPadWidth: Int = 0
    ) {
        val state = _uiState.value
        val doc = state.focusedDocument ?: return
        val updated = TextTransformEngine.applyColumnEdit(
            text = doc.content,
            spec = state.columnBlockSpec,
            action = action,
            insertText = insertText,
            startNumber = startNumber,
            stepNumber = stepNumber,
            zeroPadWidth = zeroPadWidth
        )
        updateDocumentContent(doc.id, updated)
        _uiState.update {
            it.copy(
                showColumnEditModal = false,
                statusBannerMessage = "${action.label} (Ln ${state.columnBlockSpec.normalizedStartLine}–${state.columnBlockSpec.normalizedEndLine})"
            )
        }
    }

    fun toggleBookmarkOnCurrentLine() {
        val doc = _uiState.value.focusedDocument ?: return
        val (ln, _) = doc.cursorLineAndColumn()
        toggleBookmarkAtLine(doc.id, ln)
    }

    fun toggleBookmarkAtLine(documentId: String, oneBasedLine: Int) {
        var added = false
        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc.id != documentId) return@map doc
                val next = doc.toggleBookmark(oneBasedLine)
                added = oneBasedLine in next.bookmarkedLines
                next
            }
            state.copy(
                documents = updatedDocs,
                statusBannerMessage = if (added) "Bookmarked line $oneBasedLine" else "Removed bookmark on line $oneBasedLine"
            )
        }
        scheduleSessionPersistence()
    }

    fun jumpToNextBookmark(forward: Boolean = true) {
        val doc = _uiState.value.focusedDocument ?: return
        val (curLn, _) = doc.cursorLineAndColumn()
        val target = if (forward) {
            doc.nextBookmarkedLine(curLn, wrap = true)
        } else {
            doc.previousBookmarkedLine(curLn, wrap = true)
        }
        if (target != null) {
            jumpToLine(target)
        } else {
            _uiState.update { it.copy(statusBannerMessage = "No bookmarks in active document (tap gutter line number to bookmark)") }
        }
    }

    fun clearBookmarksInActiveDocument() {
        val doc = _uiState.value.focusedDocument ?: return
        _uiState.update { state ->
            state.copy(
                documents = state.documents.map { if (it.id == doc.id) it.copy(bookmarkedLines = emptySet()) else it },
                statusBannerMessage = "Cleared bookmarks in ${doc.title}"
            )
        }
        scheduleSessionPersistence()
    }

    fun toggleLiveTailOnActiveDocument() {
        val doc = _uiState.value.focusedDocument ?: return
        if (doc.uriString == null) {
            _uiState.update { it.copy(statusBannerMessage = "Save document to storage first to enable Live Tail") }
            return
        }
        val nextTail = !doc.isLiveTailing
        _uiState.update { state ->
            state.copy(
                documents = state.documents.map { if (it.id == doc.id) it.copy(isLiveTailing = nextTail) else it },
                statusBannerMessage = if (nextTail) "Live Tail enabled for ${doc.title}" else "Live Tail paused"
            )
        }
        if (nextTail) {
            reloadActiveDocumentFromDisk(scrollToBottom = true)
        }
    }

    fun reloadActiveDocumentFromDisk(scrollToBottom: Boolean = false) {
        val doc = _uiState.value.focusedDocument ?: return
        val uriStr = doc.uriString ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromSingleUri(app, uri)
            val rawBytes = runCatching {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@launch
            val (enc, rawText) = decodeBytesWithEncodingDetection(rawBytes)
            val le = LineEnding.detect(rawText)
            val normalized = LineEnding.normalizeToLf(rawText)
            val modTime = docFile?.lastModified() ?: System.currentTimeMillis()
            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        documents = state.documents.map { d ->
                            if (d.id == doc.id) {
                                d.copy(
                                    content = normalized,
                                    savedContent = normalized,
                                    encoding = enc,
                                    lineEnding = le,
                                    lastKnownDiskModified = modTime
                                )
                            } else d
                        },
                        externallyModifiedDocIds = state.externallyModifiedDocIds - doc.id,
                        statusBannerMessage = if (scrollToBottom) state.statusBannerMessage else "Reloaded ${doc.title} from disk"
                    )
                }
                refreshAstAndFoldsForActiveDocument()
                if (scrollToBottom) {
                    val refreshed = _uiState.value.documents.firstOrNull { it.id == doc.id }
                    if (refreshed != null) jumpToLine(refreshed.lineCount)
                }
            }
        }
    }

    fun dismissExternalModificationWarning(documentId: String) {
        _uiState.update { it.copy(externallyModifiedDocIds = it.externallyModifiedDocIds - documentId) }
    }

    private fun startExternalSafWatcher() {
        externalWatchJob?.cancel()
        externalWatchJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(2_000L)
                val snapshot = _uiState.value.documents.filter { !it.isScratchpad && it.uriString != null }
                if (snapshot.isEmpty()) continue
                val app = getApplication<Application>()
                for (doc in snapshot) {
                    val uri = runCatching { Uri.parse(doc.uriString) }.getOrNull() ?: continue
                    val df = DocumentFile.fromSingleUri(app, uri) ?: continue
                    val diskMod = df.lastModified()
                    if (diskMod > 0L && doc.lastKnownDiskModified > 0L && diskMod != doc.lastKnownDiskModified) {
                        withContext(Dispatchers.Main) {
                            if (doc.isLiveTailing) {
                                reloadActiveDocumentFromDisk(scrollToBottom = true)
                            } else if (!doc.isDirty) {
                                reloadActiveDocumentFromDisk(scrollToBottom = false)
                            } else {
                                _uiState.update { st ->
                                    st.copy(
                                        documents = st.documents.map {
                                            if (it.id == doc.id) it.copy(lastKnownDiskModified = diskMod) else it
                                        },
                                        externallyModifiedDocIds = st.externallyModifiedDocIds + doc.id
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun recordRecentFile(fileName: String, uriString: String) {
        val next = (listOf(fileName to uriString) + _uiState.value.recentFileUris.filterNot { it.second == uriString }).take(12)
        _uiState.update { it.copy(recentFileUris = next) }
        val serialized = next.joinToString("\n") { "${it.first}\t${it.second}" }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[PrefKeys.RECENT_FILES_RAW] = serialized }
        }
    }

    fun openRecentFile(uriString: String) {
        runCatching { openFileFromUri(Uri.parse(uriString)) }
    }

    fun clearRecentFiles() {
        _uiState.update { it.copy(recentFileUris = emptyList(), statusBannerMessage = "Cleared Recent Files") }
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it.remove(PrefKeys.RECENT_FILES_RAW) }
        }
    }

    private fun scheduleSessionPersistence() {
        sessionSaveJob?.cancel()
        sessionSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(600L)
            val state = _uiState.value
            val root = org.json.JSONObject()
            root.put("activeDocumentId", state.activeDocumentId)
            root.put("splitViewMode", state.splitViewMode.name)
            val arr = org.json.JSONArray()
            for (doc in state.documents) {
                if (doc.isScratchpad) continue
                // Skip serializing huge clean disk-backed contents into DataStore
                val serializeBody = doc.isDirty || doc.uriString == null || doc.content.length <= 200_000
                val obj = org.json.JSONObject()
                obj.put("id", doc.id)
                obj.put("title", doc.title)
                obj.put("savedTitle", doc.savedTitle)
                obj.put("uriString", doc.uriString ?: "")
                obj.put("content", if (serializeBody) doc.content else "")
                obj.put("savedContent", if (serializeBody) doc.savedContent else "")
                obj.put("language", doc.language.name)
                obj.put("encoding", doc.encoding.name)
                obj.put("lineEnding", doc.lineEnding.name)
                obj.put("selectionStart", doc.selectionStart)
                obj.put("selectionEnd", doc.selectionEnd)
                val bmArr = org.json.JSONArray()
                doc.bookmarkedLines.sorted().forEach { bmArr.put(it) }
                obj.put("bookmarks", bmArr)
                arr.put(obj)
            }
            root.put("documents", arr)
            val jsonStr = root.toString()
            dataStore.edit { it[PrefKeys.WORKSPACE_SESSION_JSON] = jsonStr }
        }
    }

    private fun deserializeWorkspaceSession(
        rawJson: String,
        savedScratchpad: String?
    ): Triple<List<EditorDocument>, String, SplitViewMode>? {
        return runCatching {
            val root = org.json.JSONObject(rawJson)
            val activeId = root.optString("activeDocumentId", "")
            val split = runCatching {
                SplitViewMode.valueOf(root.optString("splitViewMode", SplitViewMode.NONE.name))
            }.getOrDefault(SplitViewMode.NONE)
            val arr = root.optJSONArray("documents") ?: return null
            val restored = ArrayList<EditorDocument>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uriStr = obj.optString("uriString", "").takeIf { it.isNotEmpty() }
                val bmArr = obj.optJSONArray("bookmarks")
                val bookmarks = LinkedHashSet<Int>()
                if (bmArr != null) {
                    for (b in 0 until bmArr.length()) bookmarks.add(bmArr.getInt(b))
                }
                restored.add(
                    EditorDocument(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        savedTitle = obj.optString("savedTitle", obj.getString("title")),
                        uriString = uriStr,
                        content = obj.optString("content", ""),
                        savedContent = obj.optString("savedContent", ""),
                        isScratchpad = false,
                        language = runCatching { SyntaxLanguage.valueOf(obj.getString("language")) }.getOrDefault(SyntaxLanguage.POWERSHELL),
                        encoding = runCatching { DocumentEncoding.valueOf(obj.getString("encoding")) }.getOrDefault(DocumentEncoding.UTF_8),
                        lineEnding = runCatching { LineEnding.valueOf(obj.getString("lineEnding")) }.getOrDefault(LineEnding.LF),
                        selectionStart = obj.optInt("selectionStart", 0),
                        selectionEnd = obj.optInt("selectionEnd", 0),
                        bookmarkedLines = bookmarks
                    )
                )
            }
            if (restored.isEmpty()) return null
            val scratchBody = savedScratchpad?.takeIf { it.isNotEmpty() } ?: INITIAL_SCRATCHPAD_CONTENT
            val scratchpad = EditorDocument(
                id = SCRATCHPAD_DOC_ID,
                title = "Scratchpad.ps1",
                content = scratchBody,
                savedContent = scratchBody,
                isScratchpad = true,
                language = SyntaxLanguage.POWERSHELL
            )
            val fullList = restored + scratchpad
            val validActive = if (fullList.any { it.id == activeId }) activeId else fullList.first().id
            Triple(fullList, validActive, split)
        }.getOrNull()
    }

    // =========================================================================
    // v1.5.0 GIT WORKBENCH, POWERSHELL SHOW-COMMAND / F1 / PESTER, DATA GRID, PREVIEW & SFTP
    // =========================================================================

    fun compareActiveWithSavedBaseline() {
        val doc = _uiState.value.focusedDocument ?: return
        val diff = DiffEngine.computeDiffSync(
            leftDocumentId = null,
            rightDocumentId = doc.id,
            leftTitle = "${doc.title} (Saved / HEAD Baseline)",
            rightTitle = "${doc.title} (Working Copy)",
            leftText = doc.savedContent,
            rightText = doc.content
        )
        _uiState.update {
            it.copy(
                diffLeftDocId = null,
                diffRightDocId = doc.id,
                diffResult = diff,
                showDiffModal = true
            )
        }
    }

    fun revertActiveDocumentToBaseline() {
        val doc = _uiState.value.focusedDocument ?: return
        updateDocumentContent(doc.id, doc.savedContent)
        _uiState.update { it.copy(statusBannerMessage = "Reverted ${doc.title} to baseline") }
    }

    fun openParameterFormBuilder() {
        val doc = _uiState.value.focusedDocument ?: return
        val specs = PowerShellToolingEngine.extractParameterFormSpecs(doc.content, doc.title)
        _uiState.update {
            it.copy(
                parameterFormSpecs = specs,
                showParameterFormModal = true
            )
        }
    }

    fun setParameterFormModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showParameterFormModal = visible) }
    }

    fun applyGeneratedParameterCommand(command: String, executeImmediately: Boolean) {
        if (executeImmediately) {
            val doc = _uiState.value.focusedDocument
            val codeToRun = if (doc != null && doc.language == SyntaxLanguage.POWERSHELL) {
                doc.content + "\n\n" + command
            } else {
                command
            }
            _uiState.update {
                it.copy(
                    showParameterFormModal = false,
                    isScriptRunning = true,
                    showRunnerDrawer = true
                )
            }
            viewModelScope.launch(Dispatchers.Default) {
                val res = PowerShellRunnerEngine.executeScript(codeToRun, lineNumberBaseOffset = 0)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isScriptRunning = false, lastExecutionResult = res) }
                }
            }
            return
        }

        val doc = _uiState.value.focusedDocument ?: return
        val suffix = if (doc.content.isEmpty() || doc.content.endsWith("\n")) "" else "\n"
        val updated = doc.content + suffix + command + "\n"
        updateDocumentContent(doc.id, updated, updated.length, updated.length)
        _uiState.update {
            it.copy(
                showParameterFormModal = false,
                statusBannerMessage = "Inserted PowerShell command into ${doc.title}"
            )
        }
    }

    fun openQuickHelpForCursor() {
        val doc = _uiState.value.focusedDocument
        val token = if (doc != null) {
            doc.selectedText.trim().ifEmpty {
                PowerShellToolingEngine.tokenUnderCursor(doc.content, doc.safeSelectionStart)
            }.ifEmpty { "Get-ChildItem" }
        } else {
            "Get-ChildItem"
        }
        val topic = PowerShellToolingEngine.lookupQuickHelp(token, doc?.content.orEmpty())
        _uiState.update {
            it.copy(
                quickHelpQuery = token,
                activeHelpTopic = topic,
                showQuickHelpModal = true
            )
        }
    }

    fun setQuickHelpModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showQuickHelpModal = visible) }
    }

    fun searchQuickHelp(query: String) {
        val docContent = _uiState.value.focusedDocument?.content.orEmpty()
        val topic = PowerShellToolingEngine.lookupQuickHelp(query, docContent)
        _uiState.update {
            it.copy(
                quickHelpQuery = query,
                activeHelpTopic = topic
            )
        }
    }

    fun openAndRunPesterSuite() {
        _uiState.update { it.copy(showPesterModal = true) }
        runFocusedPesterSuite()
    }

    fun setPesterModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showPesterModal = visible) }
    }

    fun runFocusedPesterSuite() {
        val doc = _uiState.value.focusedDocument ?: return
        val codeSnapshot = doc.content
        _uiState.update { it.copy(isRunningPester = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val suite = PowerShellToolingEngine.runPesterTests(codeSnapshot)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isRunningPester = false,
                        lastPesterResult = suite
                    )
                }
            }
        }
    }

    fun toggleDataGridMode() {
        val doc = _uiState.value.focusedDocument ?: return
        val activeIds = _uiState.value.activeGridDocumentIds
        if (doc.id in activeIds) {
            _uiState.update {
                it.copy(
                    activeGridDocumentIds = activeIds - doc.id,
                    statusBannerMessage = "Switched ${doc.title} to Code View"
                )
            }
        } else {
            val parsed = StructuredDataGridEngine.parseTable(doc.content, doc.language, doc.title)
            if (parsed == null) {
                _uiState.update { it.copy(statusBannerMessage = "Active document is not a CSV/TSV table or JSON object array") }
                return
            }
            _uiState.update {
                it.copy(
                    activeGridDocumentIds = activeIds + doc.id,
                    statusBannerMessage = "Opened ${doc.title} in Structured Data Grid"
                )
            }
        }
    }

    fun toggleLiveMarkdownPreview() {
        val next = !_uiState.value.showLiveMarkdownPreview
        _uiState.update {
            it.copy(
                showLiveMarkdownPreview = next,
                statusBannerMessage = if (next) "Live Markdown / HTML Preview enabled" else "Live Preview hidden"
            )
        }
    }

    fun openSftpModal() {
        _uiState.update { it.copy(showSftpModal = true, sftpErrorMessage = null) }
    }

    fun setSftpModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showSftpModal = visible) }
    }

    fun saveSftpProfile(profile: SftpConnectionProfile) {
        val updated = _uiState.value.sftpProfiles.filterNot { it.id == profile.id } + profile
        _uiState.update {
            it.copy(
                sftpProfiles = updated,
                statusBannerMessage = "Saved SFTP bookmark '${profile.name}'"
            )
        }
        persistSftpProfiles(updated)
    }

    fun deleteSftpProfile(profileId: String) {
        val updated = _uiState.value.sftpProfiles.filterNot { it.id == profileId }
        _uiState.update { it.copy(sftpProfiles = updated) }
        persistSftpProfiles(updated)
    }

    fun browseRemoteSftpDirectory(
        profile: SftpConnectionProfile,
        directoryPath: String,
        password: String?,
        privateKeyPem: String?
    ) {
        val normDir = RemoteSFTPEngine.normalizeRemotePath(directoryPath.ifBlank { profile.initialPath })
        _uiState.update { it.copy(isSftpBusy = true, sftpErrorMessage = null, sftpCurrentPath = normDir) }
        if (!password.isNullOrEmpty()) {
            sessionSftpPasswords[sftpKey(profile.host, profile.port, profile.username)] = password
        }
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching {
                RemoteSFTPEngine.listRemoteDirectory(
                    profile = profile,
                    directoryPath = normDir,
                    password = password?.takeIf { it.isNotEmpty() }
                        ?: sessionSftpPasswords[sftpKey(profile.host, profile.port, profile.username)],
                    privateKeyPem = privateKeyPem
                )
            }
            withContext(Dispatchers.Main) {
                res.fold(
                    onSuccess = { entries ->
                        _uiState.update {
                            it.copy(
                                isSftpBusy = false,
                                sftpEntries = entries,
                                sftpCurrentPath = normDir,
                                sftpErrorMessage = null
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isSftpBusy = false,
                                sftpErrorMessage = err.message ?: "SFTP connection failed"
                            )
                        }
                    }
                )
            }
        }
    }

    fun openRemoteSftpFile(
        profile: SftpConnectionProfile,
        entry: SftpRemoteEntry,
        password: String?,
        privateKeyPem: String?
    ) {
        val loc = RemoteFileLocation(
            host = profile.host.trim(),
            port = profile.port,
            username = profile.username.trim(),
            remotePath = entry.fullPath,
            privateKeyPem = privateKeyPem?.takeIf { it.isNotBlank() }
        )
        if (!password.isNullOrEmpty()) {
            sessionSftpPasswords[sftpKey(loc.host, loc.port, loc.username)] = password
        }
        val existing = _uiState.value.documents.firstOrNull { it.remoteLocation == loc }
        if (existing != null) {
            selectDocument(existing.id)
            _uiState.update { it.copy(showSftpModal = false) }
            return
        }

        _uiState.update { it.copy(isSftpBusy = true, sftpErrorMessage = null) }
        val effectivePassword = password?.takeIf { it.isNotEmpty() }
            ?: sessionSftpPasswords[sftpKey(loc.host, loc.port, loc.username)]

        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching {
                RemoteSFTPEngine.downloadRemoteFile(loc, effectivePassword)
            }
            withContext(Dispatchers.Main) {
                res.fold(
                    onSuccess = { dl ->
                        val remoteDoc = EditorDocument(
                            title = entry.name,
                            savedTitle = entry.name,
                            uriString = null,
                            remoteLocation = loc,
                            content = dl.content,
                            savedContent = dl.content,
                            language = SyntaxLanguage.fromFileName(entry.name),
                            encoding = dl.encoding,
                            lineEnding = dl.lineEnding
                        )
                        _uiState.update { st ->
                            st.copy(
                                isSftpBusy = false,
                                showSftpModal = false,
                                documents = st.documents + remoteDoc,
                                activeDocumentId = remoteDoc.id,
                                statusBannerMessage = "Opened ${loc.displayBadge}"
                            )
                        }
                        refreshAstAndFoldsForActiveDocument()
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isSftpBusy = false,
                                sftpErrorMessage = err.message ?: "Failed to download remote file"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun saveRemoteSftpDocument(doc: EditorDocument) {
        val loc = doc.remoteLocation ?: return
        val pwd = sessionSftpPasswords[sftpKey(loc.host, loc.port, loc.username)]
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching {
                RemoteSFTPEngine.uploadRemoteFile(
                    content = doc.content,
                    encoding = doc.encoding,
                    lineEnding = doc.lineEnding,
                    to = loc,
                    password = pwd
                )
            }
            withContext(Dispatchers.Main) {
                res.fold(
                    onSuccess = {
                        _uiState.update { st ->
                            st.copy(
                                documents = st.documents.map { d ->
                                    if (d.id == doc.id) d.copy(savedContent = doc.content, savedTitle = doc.title) else d
                                },
                                statusBannerMessage = "Saved to ${loc.displayBadge}"
                            )
                        }
                        refreshAstAndFoldsForActiveDocument()
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(statusBannerMessage = "SFTP save failed: ${err.message ?: "Upload error"}")
                        }
                    }
                )
            }
        }
    }

    private fun sftpKey(host: String, port: Int, username: String): String = "$username@$host:$port"

    private fun persistSftpProfiles(profiles: List<SftpConnectionProfile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val arr = org.json.JSONArray()
            for (p in profiles) {
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("host", p.host)
                obj.put("port", p.port)
                obj.put("username", p.username)
                obj.put("initialPath", p.initialPath)
                arr.put(obj)
            }
            dataStore.edit { it[PrefKeys.SFTP_PROFILES_JSON] = arr.toString() }
        }
    }

    private fun deserializeSftpProfiles(rawJson: String): List<SftpConnectionProfile> {
        return runCatching {
            val arr = org.json.JSONArray(rawJson)
            val list = ArrayList<SftpConnectionProfile>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SftpConnectionProfile(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Server"),
                        host = obj.getString("host"),
                        port = obj.optInt("port", 22),
                        username = obj.getString("username"),
                        initialPath = obj.optString("initialPath", "/")
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val SCRATCHPAD_DOC_ID = "doc_persistent_scratchpad"
        private const val MAX_UNDO_HISTORY = 150
    }
}
