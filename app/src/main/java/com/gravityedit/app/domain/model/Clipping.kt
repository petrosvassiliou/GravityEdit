package com.gravityedit.app.domain.model

import androidx.compose.runtime.Immutable
import java.util.UUID

enum class ClippingCategory(val displayName: String) {
    POWERSHELL_FUNCTIONS("PowerShell Functions & CmdletBinding"),
    POWERSHELL_FLOW("PowerShell Flow Control & Error Handling"),
    POWERSHELL_HELP_DOCS("Comment-Based Help & Regions"),
    POWERSHELL_REMOTING_API("REST API, Remoting & Pipeline"),
    POWERSHELL_PESTER("Pester Testing Blocks"),
    GENERAL_TEMPLATES("General & Markup Templates"),
    USER_CUSTOM("User Custom Clippings")
}

@Immutable
data class Clipping(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggerPrefix: String,
    val category: ClippingCategory,
    val description: String,
    val templateBody: String,
    val syntaxLanguage: SyntaxLanguage = SyntaxLanguage.POWERSHELL,
    val isBuiltIn: Boolean = true
)

@Immutable
data class ClippingExpansionContext(
    val selectedText: String = "",
    val clipboardText: String = "",
    val fileName: String = "Untitled.ps1",
    val userName: String = "Developer",
    val baseIndentation: String = ""
)

@Immutable
data class ExpandedClippingResult(
    val textToInsert: String,
    val cursorRelativeOffset: Int,
    val selectionRelativeRange: IntRange? = null
)
