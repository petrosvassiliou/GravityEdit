package com.gravityedit.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gravityedit.app.domain.engine.PowerShellToolingEngine
import com.gravityedit.app.domain.engine.RemoteSFTPEngine
import com.gravityedit.app.domain.engine.StructuredDataGridEngine
import com.gravityedit.app.domain.model.CmdletHelpTopic
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.ParameterFormSpec
import com.gravityedit.app.domain.model.PesterSuiteResult
import com.gravityedit.app.domain.model.SftpConnectionProfile
import com.gravityedit.app.domain.model.SftpRemoteEntry
import com.gravityedit.app.ui.theme.JetBrainsMonoFontFamily

// =========================================================================
// 1. POWERSHELL SHOW-COMMAND PARAMETER FORM MODAL (Ctrl+Shift+I)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParameterFormModal(
    specs: List<ParameterFormSpec>,
    onApplyCommand: (command: String, executeImmediately: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var selectedSpecIdx by remember { mutableIntStateOf(0) }
    var valuesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var switchesMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    val activeSpec = specs.getOrNull(selectedSpecIdx.coerceIn(0, (specs.size - 1).coerceAtLeast(0)))

    LaunchedEffect(activeSpec) {
        if (activeSpec != null) {
            val v = LinkedHashMap<String, String>()
            val s = LinkedHashMap<String, Boolean>()
            for (p in activeSpec.parameters) {
                if (p.isSwitch) {
                    s[p.name] = false
                } else if (p.defaultValue != null) {
                    v[p.name] = p.defaultValue
                } else if (p.isMandatory && p.validateSetOptions.isNotEmpty()) {
                    v[p.name] = p.validateSetOptions.first()
                }
            }
            valuesMap = v
            switchesMap = s
        }
    }

    val generatedCommand = remember(activeSpec, valuesMap, switchesMap) {
        if (activeSpec == null) "" else PowerShellToolingEngine.buildInvocationCommand(activeSpec, valuesMap, switchesMap)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PowerShell Show-Command Builder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Inspect param() attributes and build an invocation command",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                if (specs.isEmpty() || activeSpec == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No PowerShell param(...) blocks or functions were found in the active document.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    if (specs.size > 1) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            specs.forEachIndexed { idx, item ->
                                FilterChip(
                                    selected = idx == selectedSpecIdx,
                                    onClick = { selectedSpecIdx = idx },
                                    label = { Text(item.commandName, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    if (!activeSpec.synopsis.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = activeSpec.synopsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeSpec.parameters.isEmpty()) {
                            Text(
                                text = "'${activeSpec.commandName}' declares no parameters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            activeSpec.parameters.forEach { param ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "-${param.name}",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "[${param.typeName}]",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (param.isMandatory) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Mandatory",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                        if (!param.helpMessage.isNullOrBlank()) {
                                            Text(
                                                text = param.helpMessage,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        when {
                                            param.isSwitch -> {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Switch(
                                                        checked = switchesMap[param.name] == true,
                                                        onCheckedChange = { checked ->
                                                            switchesMap = switchesMap + (param.name to checked)
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Include -${param.name}", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                            param.validateSetOptions.isNotEmpty() -> {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    param.validateSetOptions.forEach { opt ->
                                                        val isSel = valuesMap[param.name] == opt
                                                        FilterChip(
                                                            selected = isSel,
                                                            onClick = {
                                                                valuesMap = valuesMap + (param.name to if (isSel) "" else opt)
                                                            },
                                                            label = { Text(opt, fontSize = 11.5.sp) }
                                                        )
                                                    }
                                                }
                                            }
                                            else -> {
                                                OutlinedTextField(
                                                    value = valuesMap[param.name].orEmpty(),
                                                    onValueChange = { v -> valuesMap = valuesMap + (param.name to v) },
                                                    placeholder = {
                                                        Text(param.defaultValue?.let { "Default: $it" } ?: "Enter ${param.typeName}…")
                                                    },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = generatedCommand,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(generatedCommand)) }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Copy")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { onApplyCommand(generatedCommand, false) }
                        ) {
                            Text("Insert")
                        }
                        Button(
                            onClick = { onApplyCommand(generatedCommand, true) }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Now")
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. F1 POWERSHELL CMDLET QUICK HELP MODAL
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickHelpModal(
    initialQuery: String,
    activeTopic: CmdletHelpTopic,
    onSearchQuery: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var queryInput by remember(initialQuery) { mutableStateOf(initialQuery) }
    val catalog = remember { PowerShellToolingEngine.allOfflineHelpTopics() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "PowerShell Quick Help Reference (F1)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = queryInput,
                    onValueChange = {
                        queryInput = it
                        onSearchQuery(it)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search cmdlet or alias (e.g. Invoke-RestMethod, gci)…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchQuery(queryInput) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    catalog.take(7).forEach { topic ->
                        AssistChip(
                            onClick = {
                                queryInput = topic.name
                                onSearchQuery(topic.name)
                            },
                            label = { Text(topic.name, fontSize = 11.sp) }
                        )
                    }
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeTopic.name,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = activeTopic.sourceBadge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "Module: ${activeTopic.moduleName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = activeTopic.synopsis,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (activeTopic.syntax.isNotEmpty()) {
                        Text("SYNTAX", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        activeTopic.syntax.forEach { syn ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = syn,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    if (activeTopic.examples.isNotEmpty()) {
                        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        activeTopic.examples.forEach { ex ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ex,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. PESTER UNIT TEST RUNNER MODAL (Ctrl+Alt+T)
// =========================================================================

@Composable
fun PesterRunnerModal(
    isRunning: Boolean,
    suiteResult: PesterSuiteResult?,
    onReRun: () -> Unit,
    onJumpToLine: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pester Unit Test Runner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (suiteResult != null) {
                            Text(
                                text = "${suiteResult.engineDescription} • ${suiteResult.passedCount} passed, ${suiteResult.failedCount} failed • ${suiteResult.totalDurationMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (suiteResult.failedCount == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                    Button(onClick = onReRun, enabled = !isRunning) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isRunning -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    suiteResult == null || suiteResult.results.isEmpty() -> {
                        Text(
                            text = suiteResult?.rawOutput ?: "No Pester Describe / It blocks were found in the active script.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(suiteResult.results, key = { it.id }) { item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                                                contentDescription = null,
                                                tint = if (item.passed) Color(0xFF2EA043) else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.itName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = listOfNotNull(item.describeBlock, item.contextBlock).joinToString(" › "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    onDismiss()
                                                    onJumpToLine(item.lineNumber)
                                                }
                                            ) {
                                                Text("Ln ${item.lineNumber}", fontSize = 11.sp)
                                            }
                                        }
                                        if (!item.failureMessage.isNullOrBlank()) {
                                            Text(
                                                text = item.failureMessage,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 6.dp, start = 26.dp)
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
}

// =========================================================================
// 4. REMOTE SFTP / SSH BROWSER MODAL (Ctrl+Shift+K)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SftpBrowserModal(
    profiles: List<SftpConnectionProfile>,
    currentPath: String,
    entries: List<SftpRemoteEntry>,
    isBusy: Boolean,
    errorMessage: String?,
    onSaveProfile: (SftpConnectionProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBrowseDirectory: (profile: SftpConnectionProfile, path: String, password: String?, privateKeyPem: String?) -> Unit,
    onOpenRemoteFile: (profile: SftpConnectionProfile, entry: SftpRemoteEntry, password: String?, privateKeyPem: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("Remote Server") }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("deploy") }
    var pathInput by remember(currentPath) { mutableStateOf(currentPath) }
    var sessionPassword by remember { mutableStateOf("") }

    LaunchedEffect(profiles) {
        if (host.isEmpty() && profiles.isNotEmpty()) {
            val first = profiles.first()
            label = first.name
            host = first.host
            portText = first.port.toString()
            username = first.username
            pathInput = first.initialPath
        }
    }

    fun currentDraft(): SftpConnectionProfile = SftpConnectionProfile(
        name = label.ifBlank { host },
        host = host.trim(),
        port = portText.toIntOrNull() ?: 22,
        username = username.trim(),
        initialPath = pathInput.ifBlank { "/" }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open Remote via SFTP / SSH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pure-JVM SSH2 transport — open remote scripts and save directly with Ctrl+S",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (profiles.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        profiles.forEach { prof ->
                            AssistChip(
                                onClick = {
                                    label = prof.name
                                    host = prof.host
                                    portText = prof.port.toString()
                                    username = prof.username
                                    pathInput = prof.initialPath
                                },
                                label = { Text("${prof.name} (${prof.username}@${prof.host})", fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onDeleteProfile(prof.id) }
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.width(84.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("User") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sessionPassword,
                        onValueChange = { sessionPassword = it },
                        label = { Text("Session Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val parent = RemoteSFTPEngine.parentRemotePath(pathInput)
                            pathInput = parent
                            onBrowseDirectory(currentDraft(), parent, sessionPassword.ifEmpty { null }, null)
                        },
                        enabled = !isBusy
                    ) {
                        Text("↑ Up")
                    }
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            if (host.isNotBlank() && username.isNotBlank()) {
                                onSaveProfile(currentDraft())
                            }
                        }
                    ) {
                        Text("Bookmark")
                    }
                    Button(
                        onClick = {
                            onBrowseDirectory(currentDraft(), pathInput, sessionPassword.ifEmpty { null }, null)
                        },
                        enabled = host.isNotBlank() && username.isNotBlank() && !isBusy
                    ) {
                        Text(if (isBusy) "…" else "Browse")
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (isBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (entries.isEmpty()) {
                    Text(
                        text = "Enter host & credentials above and tap Browse to list remote files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(entries, key = { it.fullPath }) { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (entry.isDirectory) {
                                            pathInput = entry.fullPath
                                            onBrowseDirectory(currentDraft(), entry.fullPath, sessionPassword.ifEmpty { null }, null)
                                        } else {
                                            onOpenRemoteFile(currentDraft(), entry, sessionPassword.ifEmpty { null }, null)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Code,
                                        contentDescription = null,
                                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.name,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (entry.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (entry.isDirectory) "DIR" else "${entry.sizeBytes} B",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

// =========================================================================
// 5. STRUCTURED CSV / TSV / JSON DATA GRID CANVAS (Ctrl+Shift+G)
// =========================================================================

@Composable
fun StructuredDataGridCanvas(
    document: EditorDocument,
    onUpdateContent: (String) -> Unit,
    onExitGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterQuery by remember { mutableStateOf("") }
    var sortColIndex by remember { mutableStateOf<Int?>(null) }
    var sortAscending by remember { mutableStateOf(true) }

    val table = remember(document.content, document.language, document.title) {
        StructuredDataGridEngine.parseTable(document.content, document.language, document.title)
            ?: com.gravityedit.app.domain.model.DataGridTable(headers = listOf("Column1"), rows = emptyList())
    }
    val visibleRows = remember(table, filterQuery) {
        StructuredDataGridEngine.filterRows(table, filterQuery)
    }
    val horizScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (table.isJsonArray) "JSON Grid (${table.rows.size} rows)" else "CSV Grid (${table.rows.size} rows)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filter rows…", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .width(180.dp)
                        .height(46.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        val next = StructuredDataGridEngine.appendEmptyRow(table)
                        onUpdateContent(StructuredDataGridEngine.serializeTable(next))
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Row", fontSize = 11.5.sp)
                }
                Button(onClick = onExitGrid) {
                    Text("Code View", fontSize = 11.5.sp)
                }
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizScroll)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .width(44.dp)
                        .padding(horizontal = 8.dp)
                )
                table.headers.forEachIndexed { colIdx, header ->
                    Row(
                        modifier = Modifier
                            .width(165.dp)
                            .clickable {
                                if (sortColIndex == colIdx) {
                                    sortAscending = !sortAscending
                                } else {
                                    sortColIndex = colIdx
                                    sortAscending = true
                                }
                                val sorted = StructuredDataGridEngine.sortTable(table, colIdx, sortAscending)
                                onUpdateContent(StructuredDataGridEngine.serializeTable(sorted))
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = header,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (sortColIndex == colIdx) {
                            Icon(
                                imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(36.dp))
            }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleRows, key = { it.first }) { (origIdx, cells) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${origIdx + 1}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(44.dp)
                                .padding(horizontal = 8.dp)
                        )
                        for (colIdx in table.headers.indices) {
                            val cellVal = cells.getOrElse(colIdx) { "" }
                            BasicTextField(
                                value = cellVal,
                                onValueChange = { updatedVal ->
                                    val next = StructuredDataGridEngine.updateCell(table, origIdx, colIdx, updatedVal)
                                    onUpdateContent(StructuredDataGridEngine.serializeTable(next))
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .width(165.dp)
                                    .padding(horizontal = 8.dp, vertical = 7.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val next = StructuredDataGridEngine.deleteRow(table, origIdx)
                                onUpdateContent(StructuredDataGridEngine.serializeTable(next))
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Row", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. LIVE OFFLINE MARKDOWN / HTML PREVIEW PANE (Ctrl+Alt+P)
// =========================================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveMarkdownHtmlPreviewPane(
    html: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                tag = html
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    )
}
