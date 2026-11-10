package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.CaseTransformMode
import com.gravityedit.app.domain.model.ClippingExpansionContext
import com.gravityedit.app.domain.model.DiffChangeType
import com.gravityedit.app.domain.model.DuplicateLineMode
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.FormatTarget
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.LineNumberAction
import com.gravityedit.app.domain.model.MatchingLinesAction
import com.gravityedit.app.domain.model.PrefixSuffixAction
import com.gravityedit.app.domain.model.QuoteTransformMode
import com.gravityedit.app.domain.model.SearchOptions
import com.gravityedit.app.domain.model.SortDirection
import com.gravityedit.app.domain.model.SymbolKind
import com.gravityedit.app.domain.model.SyntaxLanguage
import com.gravityedit.app.domain.model.TabConversionMode
import com.gravityedit.app.domain.model.TextFactoryStep
import com.gravityedit.app.domain.model.TextOperationType
import com.gravityedit.app.domain.model.WrapTransformMode
import com.gravityedit.app.domain.model.ZapGremlinsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GravityEditEnginesTest {

    // =========================================================================
    // 1. POWERSHELL ENGINE TESTS (LEXER, SCOPES, INTERPOLATION, FOLDING, AST)
    // =========================================================================

    @Test
    fun powerShellLexer_classifiesCmdletsVariablesParametersAndOperators() {
        val line = """Get-ChildItem -Path ${'$'}env:TEMP -ErrorAction Stop | Where-Object { ${'$'}_.Length -gt 10mb -and ${'$'}myVar -eq ${'$'}true }"""
        val tokens = PowerShellEngine.tokenizePowerShellLine(line)

        assertTrue(tokens.any { it.type == PowerShellTokenType.CMDLET && it.text == "Get-ChildItem" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.CMDLET && it.text == "Where-Object" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.PARAMETER && it.text == "-Path" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.PARAMETER && it.text == "-ErrorAction" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.VARIABLE_SCOPED && it.text == "\$env:TEMP" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.VARIABLE_AUTOMATIC && it.text == "\$_" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.VARIABLE_AUTOMATIC && it.text == "\$true" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.VARIABLE_NORMAL && it.text == "\$myVar" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.OPERATOR && it.text == "-gt" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.OPERATOR && it.text == "-and" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.OPERATOR && it.text == "-eq" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.NUMBER && it.text == "10mb" })
    }

    @Test
    fun powerShellLexer_interpolatesVariablesAndSubexpressionsInDoubleQuotedStrings() {
        val line = """Write-Host "Host=${'$'}env:COMPUTERNAME User=${'$'}userName Time=${'$'}(Get-Date -Format 'O')""""
        val tokens = PowerShellEngine.tokenizePowerShellLine(line)

        assertTrue(tokens.any { it.type == PowerShellTokenType.VARIABLE_SCOPED && it.text == "\$env:COMPUTERNAME" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.VARIABLE_NORMAL && it.text == "\$userName" })
        assertTrue(tokens.any { it.type == PowerShellTokenType.STRING_INTERPOLATION && it.text == "\$(Get-Date -Format 'O')" })
    }

    @Test
    fun powerShellEngine_extractsAstSymbolsAndFoldRegions() {
        val script = """
            <#
            .SYNOPSIS
                Header help block
            #>
            #region Initialization
            class NodeConfig {
                [string]${'$'}HostName
            }
            #endregion

            function Invoke-NodeProbe(${'$'}Target, ${'$'}Timeout) {
                Write-Output ${'$'}Target
            }

            filter Select-HealthyNodes {
                ${'$'}PSItem
            }
        """.trimIndent()

        val symbols = PowerShellEngine.extractAstSymbols(script)
        assertEquals(4, symbols.size)
        assertTrue(symbols.any { it.kind == SymbolKind.REGION && it.name == "Initialization" })
        assertTrue(symbols.any { it.kind == SymbolKind.CLASS && it.name == "NodeConfig" })
        assertTrue(symbols.any { it.kind == SymbolKind.FUNCTION && it.name == "Invoke-NodeProbe" && it.parameters.size == 2 })
        assertTrue(symbols.any { it.kind == SymbolKind.FILTER && it.name == "Select-HealthyNodes" })

        val folds = PowerShellEngine.extractFoldRegions(script)
        assertTrue(folds.size >= 4)
    }

    @Test
    fun powerShellEngine_computesInlineCompletionsForCmdletsVarsParamsAndTypes() {
        val script = """
            ${'$'}clusterTargetNodes = @('node-01', 'node-02')
            function Invoke-ClusterAudit(${'$'}NodeName) {
                Write-Output ${'$'}NodeName
            }
        """.trimIndent()
        val astSymbols = PowerShellEngine.extractAstSymbols(script)

        // 1. Cmdlet & AST Function completion
        val cmdletLine = "    Get-Ch"
        val cmdletMatches = PowerShellEngine.computeCompletions(
            lineText = cmdletLine,
            cursorColumn = cmdletLine.length,
            fullDocumentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(cmdletMatches.any { it.insertText == "Get-ChildItem" && it.kind == CompletionKind.CMDLET })

        val localFnLine = "    Invoke-Clu"
        val localFnMatches = PowerShellEngine.computeCompletions(
            lineText = localFnLine,
            cursorColumn = localFnLine.length,
            fullDocumentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(localFnMatches.any { it.insertText == "Invoke-ClusterAudit" })

        // 2. Variable completion (both built-in automatic and live document-declared variables)
        val autoVarLine = "Write-Host ${'$'}PSVer"
        val autoVarMatches = PowerShellEngine.computeCompletions(
            lineText = autoVarLine,
            cursorColumn = autoVarLine.length,
            fullDocumentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(autoVarMatches.any { it.insertText == "\$PSVersionTable" && it.kind == CompletionKind.VARIABLE })

        val userVarLine = "ForEach-Object -InputObject ${'$'}clusterT"
        val userVarMatches = PowerShellEngine.computeCompletions(
            lineText = userVarLine,
            cursorColumn = userVarLine.length,
            fullDocumentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(userVarMatches.any { it.insertText == "\$clusterTargetNodes" && it.replaceStartCol == 28 })

        // 3. Parameter completion
        val paramLine = "Get-ChildItem -Err"
        val paramMatches = PowerShellEngine.computeCompletions(
            lineText = paramLine,
            cursorColumn = paramLine.length,
            fullDocumentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(paramMatches.any { it.insertText == "-ErrorAction" && it.kind == CompletionKind.PARAMETER })

        // 4. Type accelerator / attribute completion
        val typeLine = "[Cmdlet"
        val typeMatches = PowerShellEngine.computeCompletions(
            lineText = typeLine,
            cursorColumn = typeLine.length,
            fullDocumentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(typeMatches.any { it.insertText == "[CmdletBinding()]" && it.kind == CompletionKind.TYPE_ACCELERATOR })
    }

    // =========================================================================
    // 2. TEXT TRANSFORM ENGINE TESTS (ALL 14 BBEDIT OPERATIONS)
    // =========================================================================

    @Test
    fun textTransformEngine_zapGremlinsAndStripTrailingWhitespace() {
        val dirty = "Hello\u0000World\u0007   \nSecondLine\t\t"
        val zapped = TextTransformEngine.zapGremlins(
            text = dirty,
            action = ZapGremlinsAction.REPLACE_WITH_HEX,
            zapNonAscii = false,
            zapControlChars = true,
            zapNullBytes = true
        )
        assertTrue(zapped.contains("\\x00"))
        assertTrue(zapped.contains("\\x07"))

        val stripped = TextTransformEngine.stripTrailingWhitespace(zapped, preserveBlankLines = true)
        assertEquals("Hello\\x00World\\x07\nSecondLine", stripped)
    }

    @Test
    fun textTransformEngine_processDuplicatesAndSortLines() {
        val raw = "banana\napple\nbanana\ncherry"
        val (leaveOne, _) = TextTransformEngine.processDuplicateLines(
            text = raw,
            mode = DuplicateLineMode.LEAVE_ONE,
            caseSensitive = true,
            ignoreLeadingWhitespace = false
        )
        assertEquals("banana\napple\ncherry", leaveOne)

        val (uniqueOnly, _) = TextTransformEngine.processDuplicateLines(
            text = raw,
            mode = DuplicateLineMode.KEEP_UNIQUE_ONLY,
            caseSensitive = true,
            ignoreLeadingWhitespace = false
        )
        assertEquals("apple\ncherry", uniqueOnly)

        val sorted = TextTransformEngine.sortLines(
            text = "item-20\nitem-3\nitem-100",
            direction = SortDirection.ASCENDING,
            caseSensitive = false,
            numeric = true
        )
        assertEquals("item-100\nitem-20\nitem-3", sorted) // signed numeric "-100", "-20", "-3"
    }

    @Test
    fun textTransformEngine_prefixSuffixChangeCaseEntabDetabAndLineNumbers() {
        val base = "alpha\nbeta"
        val prefixed = TextTransformEngine.prefixSuffixLines(
            text = base,
            action = PrefixSuffixAction.INSERT,
            prefix = "$",
            suffix = ";",
            skipBlankLines = true
        )
        assertEquals("\$alpha;\n\$beta;", prefixed)

        val inverted = TextTransformEngine.changeCase("PowerShell 7", CaseTransformMode.INVERT_CASE)
        assertEquals("pOWERsHELL 7", inverted)

        val detabbed = TextTransformEngine.entabDetab("\tCode", TabConversionMode.DETAB_TABS_TO_SPACES, tabWidth = 4)
        assertEquals("    Code", detabbed)

        val numbered = TextTransformEngine.addRemoveLineNumbers(
            text = base,
            action = LineNumberAction.ADD_NUMBERS,
            startNumber = 1,
            increment = 1,
            separator = ": ",
            zeroPadWidth = 3
        )
        assertEquals("001: alpha\n002: beta", numbered)

        val unnumbered = TextTransformEngine.addRemoveLineNumbers(
            text = numbered,
            action = LineNumberAction.REMOVE_NUMBERS,
            startNumber = 1,
            increment = 1,
            separator = ": ",
            zeroPadWidth = 3
        )
        assertEquals(base, unnumbered)
    }

    @Test
    fun textTransformEngine_quotesCanonizeContainingAndFormatPrettify() {
        val curly = "“Hello” and ‘World’"
        val straight = TextTransformEngine.educateStraightenQuotes(curly, QuoteTransformMode.STRAIGHTEN_QUOTES)
        assertEquals("\"Hello\" and 'World'", straight)

        val canonized = TextTransformEngine.canonizeReplaceTable(
            text = "gci -Path .",
            tableRaw = "gci=Get-ChildItem",
            caseSensitive = false,
            wholeWords = true
        )
        assertEquals("Get-ChildItem -Path .", canonized)

        val (filtered, _) = TextTransformEngine.processLinesContaining(
            text = "INFO ok\nERROR failed\nINFO done",
            action = MatchingLinesAction.DELETE_MATCHING,
            pattern = "^ERROR",
            useRegex = true,
            caseSensitive = true
        )
        assertEquals("INFO ok\nINFO done", filtered)

        val formattedJson = TextTransformEngine.formatPrettify(
            text = """{"name":"GravityEdit","version":1}""",
            target = FormatTarget.JSON,
            indentSpaces = 2
        )
        assertTrue(formattedJson.contains("{\n  \"name\": \"GravityEdit\",\n  \"version\": 1\n}"))

        val pipelineResult = TextTransformEngine.executePipelineSync(
            inputText = "line1   \nline2   ",
            steps = listOf(
                TextFactoryStep(operationType = TextOperationType.STRIP_TRAILING_WHITESPACE),
                TextFactoryStep(operationType = TextOperationType.CHANGE_CASE, caseMode = CaseTransformMode.UPPERCASE),
                TextFactoryStep(operationType = TextOperationType.HARD_WRAP_UNWRAP, wrapMode = WrapTransformMode.HARD_WRAP, wrapColumnWidth = 80),
                TextFactoryStep(operationType = TextOperationType.CONVERT_LINE_ENDINGS, targetLineEnding = LineEnding.CRLF)
            )
        )
        assertEquals("LINE1\nLINE2", pipelineResult.transformedText)
        assertEquals(LineEnding.CRLF, pipelineResult.targetLineEnding)
    }

    // =========================================================================
    // 3. PCRE FIND & REPLACE ENGINE TESTS
    // =========================================================================

    @Test
    fun findReplaceEngine_supportsNamedCaptureGroupsBackreferencesAndExtract() {
        val sample = "Server=Node01 Latency=14ms\nServer=Node02 Latency=89ms"
        val options = SearchOptions(
            query = """Server=(?<node>\w+)\s+Latency=(\d+)ms""",
            replacement = "${'$'}{node}: ${'$'}2 ms",
            grepRegex = true,
            caseSensitive = false
        )

        val matches = FindReplaceEngine.findAllMatches(sample, options)
        assertEquals(2, matches.size)
        assertEquals("Node01", matches[0].captureGroups.first { it.groupName == "node" }.value)

        val (replaced, count) = FindReplaceEngine.replaceAll(sample, options)
        assertEquals(2, count)
        assertEquals("Node01: 14 ms\nNode02: 89 ms", replaced)

        val (extracted, extCount) = FindReplaceEngine.extractMatchesToDocument(sample, options)
        assertEquals(2, extCount)
        assertEquals("Node01: 14 ms\nNode02: 89 ms", extracted)

        val eval = FindReplaceEngine.evaluatePatternPlayground(sample, options)
        assertTrue(eval.isValidRegex)
        assertEquals(2, eval.matches.size)
        assertTrue(FindReplaceEngine.matchesFileFilter("Deploy-App.ps1", "*.ps1;*.json"))
        assertFalse(FindReplaceEngine.matchesFileFilter("image.png", "*.ps1;*.json"))
    }

    // =========================================================================
    // 4. MYERS DIFF ENGINE & DIRECTIONAL MERGE TESTS
    // =========================================================================

    @Test
    fun diffEngine_computesMyersDiffCharHighlightsAndDirectionalMerges() {
        val left = "line1\nvalue = 10\nold_only\nline4"
        val right = "line1\nvalue = 25\nnew_only\nline4"

        val diff = DiffEngine.computeDiffSync(
            leftDocumentId = "L",
            rightDocumentId = "R",
            leftTitle = "Left.ps1",
            rightTitle = "Right.ps1",
            leftText = left,
            rightText = right
        )

        assertFalse(diff.isIdentical)
        assertEquals(2, diff.modifiedCount)
        val modifyRow = diff.rows.first { it.changeType == DiffChangeType.MODIFY }
        assertTrue(modifyRow.leftCharHighlights.isNotEmpty())
        assertTrue(modifyRow.rightCharHighlights.isNotEmpty())

        val mergedLeft = DiffEngine.mergeRowRightToLeft(diff, modifyRow.rowIndex)
        assertTrue(mergedLeft.contains("value = 25"))

        val mergedRight = DiffEngine.mergeRowLeftToRight(diff, modifyRow.rowIndex)
        assertTrue(mergedRight.contains("value = 10"))
    }

    // =========================================================================
    // 5. CLIPPINGS MANAGER PLACEHOLDER EXPANSION TESTS
    // =========================================================================

    @Test
    fun clippingsManager_expandsAllPlaceholdersAndComputesSelectionOffsets() {
        val template = "# File: #FILENAME# by #USERNAME# on #DATE#\nfunction #SELSTART#Invoke-Test#SELEND# {\n    #SELECTION##INSERTION#\n}"
        val expanded = ClippingsManager.expandTemplate(
            templateBody = template,
            context = ClippingExpansionContext(
                selectedText = "Write-Host 'Hi'",
                clipboardText = "Clip",
                fileName = "Demo.ps1",
                userName = "Alice"
            ),
            nowDate = "2026-11-10",
            nowTime = "12:00:00"
        )

        assertTrue(expanded.textToInsert.contains("# File: Demo.ps1 by Alice on 2026-11-10"))
        assertTrue(expanded.textToInsert.contains("Write-Host 'Hi'"))
        assertNotNull(expanded.selectionRelativeRange)
        val selRange = expanded.selectionRelativeRange!!
        assertEquals("Invoke-Test", expanded.textToInsert.substring(selRange.first, selRange.last))

        val doc = EditorDocument(title = "Demo.ps1", content = "Get-Date", selectionStart = 0, selectionEnd = 8)
        val updatedDoc = ClippingsManager.applyClippingToDocument(
            document = doc,
            clipping = ClippingsManager.BUILT_IN_CLIPPINGS.first { it.id == "ps_region_block" }
        )
        assertTrue(updatedDoc.content.contains("#region Section Name"))
        assertTrue(updatedDoc.content.contains("Get-Date"))
    }
}
