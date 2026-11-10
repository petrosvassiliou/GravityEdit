package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.Clipping
import com.gravityedit.app.domain.model.ClippingCategory
import com.gravityedit.app.domain.model.ClippingExpansionContext
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.ExpandedClippingResult
import com.gravityedit.app.domain.model.SyntaxLanguage
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ClippingsManager {

    const val PLACEHOLDER_INSERTION = "#INSERTION#"
    const val PLACEHOLDER_SELSTART = "#SELSTART#"
    const val PLACEHOLDER_SELEND = "#SELEND#"
    const val PLACEHOLDER_SELECTION = "#SELECTION#"
    const val PLACEHOLDER_CLIPBOARD = "#CLIPBOARD#"
    const val PLACEHOLDER_DATE = "#DATE#"
    const val PLACEHOLDER_TIME = "#TIME#"
    const val PLACEHOLDER_FILENAME = "#FILENAME#"
    const val PLACEHOLDER_USERNAME = "#USERNAME#"

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    val BUILT_IN_CLIPPINGS: List<Clipping> = listOf(
        Clipping(
            id = "ps_cmdlet_binding",
            name = "Advanced Function ([CmdletBinding()])",
            triggerPrefix = "func-adv",
            category = ClippingCategory.POWERSHELL_FUNCTIONS,
            description = "Full PowerShell advanced function with ShouldProcess, Parameter attributes, and Begin/Process/End blocks",
            templateBody = """
                function #SELSTART#Invoke-CustomAction#SELEND# {
                    [CmdletBinding(SupportsShouldProcess = ${'$'}true, ConfirmImpact = 'Medium')]
                    [OutputType([pscustomobject])]
                    param (
                        [Parameter(Mandatory = ${'$'}true, ValueFromPipeline = ${'$'}true, ValueFromPipelineByPropertyName = ${'$'}true, Position = 0)]
                        [ValidateNotNullOrEmpty()]
                        [string[]]${'$'}Path,

                        [Parameter(Mandatory = ${'$'}false)]
                        [switch]${'$'}Force
                    )

                    begin {
                        Write-Verbose "Starting ${'$'}({MyInvocation.MyCommand.Name}) on #DATE#"
                    }

                    process {
                        foreach (${'$'}item in ${'$'}Path) {
                            if (${'$'}PSCmdlet.ShouldProcess(${'$'}item, 'Process target item')) {
                                #SELECTION##INSERTION#
                            }
                        }
                    }

                    end {
                        Write-Verbose "Completed ${'$'}({MyInvocation.MyCommand.Name})"
                    }
                }
            """.trimIndent()
        ),
        Clipping(
            id = "ps_comment_based_help",
            name = "Comment-Based Help Block (<# .SYNOPSIS #>)",
            triggerPrefix = "help-block",
            category = ClippingCategory.POWERSHELL_HELP_DOCS,
            description = "Complete PowerShell Comment-Based Help header with .SYNOPSIS, .DESCRIPTION, .PARAMETER, .EXAMPLE, and .NOTES",
            templateBody = """
                <#
                .SYNOPSIS
                    #SELSTART#Brief synopsis of what #FILENAME# does.#SELEND#

                .DESCRIPTION
                    Detailed description of the function or script behavior, inputs, and operational notes.

                .PARAMETER Path
                    Specifies the target file or directory path to process.

                .EXAMPLE
                    PS C:\> .\Invoke-Script.ps1 -Path 'C:\Logs' -Verbose
                    Demonstrates standard execution with verbose output enabled.

                .NOTES
                    File Name  : #FILENAME#
                    Author     : #USERNAME#
                    Created On : #DATE# #TIME#
                #>
                #INSERTION#
            """.trimIndent()
        ),
        Clipping(
            id = "ps_try_catch_finally",
            name = "Try / Catch / Finally (Typed Exception)",
            triggerPrefix = "try-catch",
            category = ClippingCategory.POWERSHELL_FLOW,
            description = "Structured error handling block with ErrorAction Stop, specific exception catch, and cleanup block",
            templateBody = """
                try {
                    #SELECTION##INSERTION#
                }
                catch [System.IO.IOException] {
                    Write-Error -Message "I/O failure in #FILENAME#: ${'$'}(${'$'}_.Exception.Message)" -ErrorAction Continue
                }
                catch {
                    Write-Error -Message "Unhandled error at line ${'$'}(${'$'}_.InvocationInfo.ScriptLineNumber): ${'$'}(${'$'}_.Exception.Message)"
                    throw
                }
                finally {
                    Write-Verbose "Cleanup completed at #TIME#"
                }
            """.trimIndent()
        ),
        Clipping(
            id = "ps_foreach_parallel",
            name = "ForEach Collection Loop",
            triggerPrefix = "foreach",
            category = ClippingCategory.POWERSHELL_FLOW,
            description = "Standard PowerShell foreach loop with pipeline object construction",
            templateBody = """
                foreach (${'$'}#SELSTART#item#SELEND# in ${'$'}collection) {
                    [pscustomobject]@{
                        Name      = ${'$'}item.Name
                        Timestamp = '#DATE# #TIME#'
                    }
                    #SELECTION##INSERTION#
                }
            """.trimIndent()
        ),
        Clipping(
            id = "ps_switch_regex",
            name = "Switch -Regex Statement",
            triggerPrefix = "switch-regex",
            category = ClippingCategory.POWERSHELL_FLOW,
            description = "PowerShell switch block using -Regex pattern matching and \$Matches capture inspection",
            templateBody = """
                switch -Regex (#SELSTART#${'$'}inputValue#SELEND#) {
                    '^ERROR:\s*(?<msg>.+)${'$'}' {
                        Write-Warning "Matched error: ${'$'}(${'$'}Matches['msg'])"
                        #INSERTION#
                        break
                    }
                    '^\d{4}-\d{2}-\d{2}' {
                        Write-Verbose "Matched ISO date line"
                        break
                    }
                    default {
                        #SELECTION#
                    }
                }
            """.trimIndent()
        ),
        Clipping(
            id = "ps_invoke_restmethod",
            name = "Invoke-RestMethod JSON API Request",
            triggerPrefix = "irm-json",
            category = ClippingCategory.POWERSHELL_REMOTING_API,
            description = "REST API request using Invoke-RestMethod with Bearer token header, JSON payload, and timeout",
            templateBody = """
                ${'$'}headers = @{
                    'Accept'        = 'application/json'
                    'Authorization' = "Bearer ${'$'}env:API_TOKEN"
                }

                ${'$'}payload = [ordered]@{
                    source    = '#FILENAME#'
                    requested = '#DATE#T#TIME#'
                    data      = '#SELECTION#'
                } | ConvertTo-Json -Depth 5

                ${'$'}response = Invoke-RestMethod -Uri '#SELSTART#https://api.example.com/v1/resource#SELEND#' `
                    -Method Post `
                    -Headers ${'$'}headers `
                    -Body ${'$'}payload `
                    -ContentType 'application/json; charset=utf-8' `
                    -TimeoutSec 30 `
                    -ErrorAction Stop

                ${'$'}response#INSERTION#
            """.trimIndent()
        ),
        Clipping(
            id = "ps_pester_describe",
            name = "Pester v5 Describe / Context / It Test Block",
            triggerPrefix = "pester-test",
            category = ClippingCategory.POWERSHELL_PESTER,
            description = "Pester v5 unit test suite with BeforeAll, Describe, Context, and Should assertions",
            templateBody = """
                BeforeAll {
                    # Dot-source target script relative to test file
                    # . ${'$'}PSCommandPath.Replace('.Tests.ps1', '.ps1')
                }

                Describe '#SELSTART#Function-UnderTest#SELEND#' -Tag 'Unit' {
                    Context 'When valid parameters are provided' {
                        BeforeEach {
                            ${'$'}testPath = Join-Path -Path ${'$'}TestDrive -ChildPath 'sample.txt'
                            Set-Content -Path ${'$'}testPath -Value '#SELECTION#'
                        }

                        It 'Returns expected output without throwing' {
                            { Get-Item -Path ${'$'}testPath -ErrorAction Stop } | Should -Not -Throw
                            ${'$'}testPath | Should -Exist
                            #INSERTION#
                        }
                    }
                }
            """.trimIndent()
        ),
        Clipping(
            id = "ps_region_block",
            name = "#region / #endregion Foldable Section",
            triggerPrefix = "region",
            category = ClippingCategory.POWERSHELL_HELP_DOCS,
            description = "Foldable PowerShell #region block wrapping the current selection",
            templateBody = """
                #region #SELSTART#Section Name#SELEND#
                #SELECTION##INSERTION#
                #endregion
            """.trimIndent()
        ),
        Clipping(
            id = "ps_class_definition",
            name = "PowerShell Class with Constructor & Method",
            triggerPrefix = "class-ps",
            category = ClippingCategory.POWERSHELL_FUNCTIONS,
            description = "PowerShell 5+ class definition with typed properties, constructor, and method",
            templateBody = """
                class #SELSTART# GravityResource #SELEND# {
                    [string]${'$'}Name
                    [datetime]${'$'}CreatedAt
                    hidden [string]${'$'}InternalId

                    GravityResource([string]${'$'}name) {
                        ${'$'}this.Name = ${'$'}name
                        ${'$'}this.CreatedAt = [datetime]::UtcNow
                        ${'$'}this.InternalId = [guid]::NewGuid().ToString()
                    }

                    [string] ToSummary() {
                        return "[${'$'}(${'$'}this.Name)] initialized at ${'$'}(${'$'}this.CreatedAt.ToString('O'))"
                    }
                }
                #INSERTION#
            """.trimIndent()
        ),
        Clipping(
            id = "ps_pipeline_filter",
            name = "High-Speed Filter Function",
            triggerPrefix = "filter-ps",
            category = ClippingCategory.POWERSHELL_REMOTING_API,
            description = "Lightweight streaming PowerShell filter using \$PSItem / \$_",
            templateBody = """
                filter #SELSTART#Select-MatchingItem#SELEND# {
                    param (
                        [Parameter(Mandatory = ${'$'}false)]
                        [string]${'$'}Pattern = '.*'
                    )
                    if (${'$'}PSItem -match ${'$'}Pattern) {
                        ${'$'}PSItem#INSERTION#
                    }
                }
            """.trimIndent()
        )
    )

    /**
     * Expands all BBEdit placeholders (`#SELECTION#`, `#CLIPBOARD#`, `#DATE#`, `#TIME#`, `#FILENAME#`,
     * `#USERNAME#`, `#SELSTART#`, `#SELEND#`, `#INSERTION#`) and calculates the resulting cursor or
     * selection range relative to the start of the expanded snippet.
     */
    fun expandTemplate(
        templateBody: String,
        context: ClippingExpansionContext,
        nowDate: String = LocalDate.now().format(DATE_FORMATTER),
        nowTime: String = LocalTime.now().format(TIME_FORMATTER)
    ): ExpandedClippingResult {
        val normalizedTemplate = if (context.baseIndentation.isNotEmpty()) {
            val lines = templateBody.split('\n')
            lines.mapIndexed { idx, line ->
                if (idx == 0 || line.isBlank()) line else context.baseIndentation + line
            }.joinToString("\n")
        } else {
            templateBody
        }

        // First expand value-substituting placeholders
        val stageOne = normalizedTemplate
            .replace(PLACEHOLDER_SELECTION, context.selectedText)
            .replace(PLACEHOLDER_CLIPBOARD, context.clipboardText)
            .replace(PLACEHOLDER_DATE, nowDate)
            .replace(PLACEHOLDER_TIME, nowTime)
            .replace(PLACEHOLDER_FILENAME, context.fileName)
            .replace(PLACEHOLDER_USERNAME, context.userName)

        // Next locate cursor/selection position markers (#SELSTART#, #SELEND#, #INSERTION#)
        val sb = StringBuilder(stageOne.length)
        var selStartOffset: Int? = null
        var selEndOffset: Int? = null
        var insertionOffset: Int? = null

        var i = 0
        while (i < stageOne.length) {
            when {
                stageOne.startsWith(PLACEHOLDER_SELSTART, i) -> {
                    selStartOffset = sb.length
                    i += PLACEHOLDER_SELSTART.length
                }
                stageOne.startsWith(PLACEHOLDER_SELEND, i) -> {
                    selEndOffset = sb.length
                    i += PLACEHOLDER_SELEND.length
                }
                stageOne.startsWith(PLACEHOLDER_INSERTION, i) -> {
                    insertionOffset = sb.length
                    i += PLACEHOLDER_INSERTION.length
                }
                else -> {
                    sb.append(stageOne[i])
                    i++
                }
            }
        }

        val finalString = sb.toString()
        val resolvedSelectionRange = if (selStartOffset != null && selEndOffset != null && selEndOffset >= selStartOffset) {
            selStartOffset..selEndOffset
        } else {
            null
        }

        val resolvedCursorOffset = when {
            resolvedSelectionRange != null -> resolvedSelectionRange.last
            insertionOffset != null -> insertionOffset
            selStartOffset != null -> selStartOffset
            else -> finalString.length
        }

        return ExpandedClippingResult(
            textToInsert = finalString,
            cursorRelativeOffset = resolvedCursorOffset.coerceIn(0, finalString.length),
            selectionRelativeRange = resolvedSelectionRange
        )
    }

    /**
     * Inserts [clipping] directly into [document] at its current selection, returning an updated
     * [EditorDocument] with the expanded text, updated undo snapshot, and exact cursor/selection state.
     */
    fun applyClippingToDocument(
        document: EditorDocument,
        clipping: Clipping,
        clipboardText: String = "",
        userName: String = "Developer"
    ): EditorDocument {
        val selStart = document.safeSelectionStart
        val selEnd = document.safeSelectionEnd
        val selectedText = document.selectedText

        // Determine current line's leading whitespace for clean multi-line indentation
        val lineIdx = document.lineIndexForOffset(selStart)
        val currentLine = document.lines.getOrElse(lineIdx) { "" }
        val baseIndentation = currentLine.takeWhile { it == ' ' || it == '\t' }

        val expanded = expandTemplate(
            templateBody = clipping.templateBody,
            context = ClippingExpansionContext(
                selectedText = selectedText,
                clipboardText = clipboardText,
                fileName = document.title,
                userName = userName,
                baseIndentation = baseIndentation
            )
        )

        val newContent = buildString(document.content.length - (selEnd - selStart) + expanded.textToInsert.length) {
            append(document.content, 0, selStart)
            append(expanded.textToInsert)
            append(document.content, selEnd, document.content.length)
        }

        val newSelStart: Int
        val newSelEnd: Int
        val relRange = expanded.selectionRelativeRange
        if (relRange != null) {
            newSelStart = (selStart + relRange.first).coerceIn(0, newContent.length)
            newSelEnd = (selStart + relRange.last).coerceIn(newSelStart, newContent.length)
        } else {
            val cursor = (selStart + expanded.cursorRelativeOffset).coerceIn(0, newContent.length)
            newSelStart = cursor
            newSelEnd = cursor
        }

        return document.copy(
            content = newContent,
            selectionStart = newSelStart,
            selectionEnd = newSelEnd
        )
    }

    fun filterClippings(
        allClippings: List<Clipping>,
        searchQuery: String,
        languageFilter: SyntaxLanguage? = null
    ): List<Clipping> {
        val q = searchQuery.trim().lowercase()
        return allClippings.filter { clipping ->
            val matchesLang = languageFilter == null || clipping.syntaxLanguage == languageFilter
            val matchesQuery = q.isEmpty() ||
                clipping.name.lowercase().contains(q) ||
                clipping.triggerPrefix.lowercase().contains(q) ||
                clipping.description.lowercase().contains(q) ||
                clipping.category.displayName.lowercase().contains(q)
            matchesLang && matchesQuery
        }
    }
}
