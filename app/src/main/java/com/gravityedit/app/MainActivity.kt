package com.gravityedit.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravityedit.app.ui.GravityEditScaffold
import com.gravityedit.app.ui.theme.GravityEditTheme
import com.gravityedit.app.ui.viewmodel.GravityEditViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: GravityEditViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingFileIntent(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(activity = this)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            var pendingSaveDocumentId by remember { mutableStateOf<String?>(null) }

            val openFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.openFileFromUri(uri)
                }
            }

            val saveAsFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain")
            ) { uri: Uri? ->
                val targetDocId = pendingSaveDocumentId ?: uiState.focusedDocument?.id
                if (uri != null && targetDocId != null) {
                    viewModel.saveDocumentToUri(targetDocId, uri)
                }
                pendingSaveDocumentId = null
            }

            val mountFolderLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { treeUri: Uri? ->
                if (treeUri != null) {
                    viewModel.mountProjectDirectory(treeUri)
                }
            }

            GravityEditTheme(themeMode = uiState.themeMode) {
                GravityEditScaffold(
                    windowSizeClass = windowSizeClass,
                    viewModel = viewModel,
                    onLaunchOpenFilePicker = {
                        openFileLauncher.launch(arrayOf("*/*"))
                    },
                    onLaunchSaveAsFilePicker = { documentId, suggestedName ->
                        pendingSaveDocumentId = documentId
                        saveAsFileLauncher.launch(suggestedName)
                    },
                    onLaunchMountFolderPicker = {
                        mountFolderLauncher.launch(null)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingFileIntent(intent)
    }

    private fun handleIncomingFileIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            val dataUri = intent.data ?: return
            viewModel.openFileFromUri(dataUri)
        }
    }
}
