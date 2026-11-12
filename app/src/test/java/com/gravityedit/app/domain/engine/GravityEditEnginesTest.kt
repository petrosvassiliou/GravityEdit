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
            line = cmdletLine,
            cursorColumn = cmdletLine.length,
            documentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(cmdletMatches.any { it.insertText == "Get-ChildItem" && it.kind == CompletionKind.CMDLET })

        val localFnLine = "    Invoke-Clu"
        val localFnMatches = PowerShellEngine.computeCompletions(
            line = localFnLine,
            cursorColumn = localFnLine.length,
            documentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(localFnMatches.any { it.insertText == "Invoke-ClusterAudit" && it.kind == CompletionKind.FUNCTION })

        // 2. Variable completion (both built-in automatic and live document-declared variables)
        val autoVarLine = "Write-Host ${'$'}PSVer"
        val autoVarMatches = PowerShellEngine.computeCompletions(
            line = autoVarLine,
            cursorColumn = autoVarLine.length,
            documentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(autoVarMatches.any { it.insertText == "\$PSVersionTable" && it.kind == CompletionKind.VARIABLE })

        val userVarLine = "ForEach-Object -InputObject ${'$'}clusterT"
        val userVarMatches = PowerShellEngine.computeCompletions(
            line = userVarLine,
            cursorColumn = userVarLine.length,
            documentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(userVarMatches.any { it.insertText == "\$clusterTargetNodes" && it.replaceStartInLine == 28 })

        // 3. Parameter completion
        val paramLine = "Get-ChildItem -Err"
        val paramMatches = PowerShellEngine.computeCompletions(
            line = paramLine,
            cursorColumn = paramLine.length,
            documentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(paramMatches.any { it.insertText == "-ErrorAction" && it.kind == CompletionKind.PARAMETER })

        // 4. Type accelerator / attribute completion
        val typeLine = "[Cmdlet"
        val typeMatches = PowerShellEngine.computeCompletions(
            line = typeLine,
            cursorColumn = typeLine.length,
            documentContent = script,
            astSymbols = astSymbols
        )
        assertTrue(typeMatches.any { it.insertText == "[CmdletBinding()]" && it.kind == CompletionKind.TYPE_ACCELERATOR })
    }

    @Test
    fun powerShellEngine_analyzesDiagnosticsAndAppliesQuickFixes() {
        val scriptWithIssues = """
            function FooBar-Data([string]${'$'}UserPassword) {
                try {
                    gci -Path . | where { ${'$'}_.Length -gt 0 }
                } catch { }
            }
        """.trimIndent()

        val diags = PowerShellEngine.analyzeScriptDiagnostics(scriptWithIssues)
        assertTrue(diags.any { it.ruleId == "PSUseApprovedVerbs" && it.severity == DiagnosticSeverity.WARNING })
        assertTrue(diags.any { it.ruleId == "PSAvoidUsingPlainTextForPassword" && it.severity == DiagnosticSeverity.WARNING })
        assertTrue(diags.any { it.ruleId == "PSAvoidEmptyCatchBlock" && it.severity == DiagnosticSeverity.WARNING })
        val aliasDiag = diags.firstOrNull { it.ruleId == "PSAvoidUsingCmdletAliases" && it.quickFixTarget == "gci" }
        assertNotNull(aliasDiag)

        val fixedScript = PowerShellEngine.applyDiagnosticQuickFix(scriptWithIssues, aliasDiag!!)
        assertTrue(fixedScript.contains("Get-ChildItem -Path ."))

        // Unbalanced delimiter detection
        val unbalanced = "function Get-Broken {\n    Write-Host 'Missing closing brace'"
        val unbalancedDiags = PowerShellEngine.analyzeScriptDiagnostics(unbalanced)
        assertTrue(unbalancedDiags.any { it.ruleId == "PSSyntaxUnbalancedDelimiters" && it.severity == DiagnosticSeverity.ERROR })
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

    // =========================================================================
    // 6. GOOGLE PLAY PROTECT AUTO-UPDATE ENGINE & FILE TYPE RESOLUTION TESTS
    // =========================================================================

    @Test
    fun appUpdateManagerEngine_comparesSemanticVersionsAndVerifiesSha256AndApkHeaders() {
        assertEquals(listOf(1, 0, 0), AppUpdateManagerEngine.parseSemVerParts("v1.0.0"))
        assertEquals(listOf(1, 2, 10), AppUpdateManagerEngine.parseSemVerParts("v1.2.10-beta.1"))

        assertTrue(AppUpdateManagerEngine.compareSemVer(currentVersion = "1.0.0", remoteVersion = "v1.1.0") > 0)
        assertTrue(AppUpdateManagerEngine.compareSemVer(currentVersion = "v1.0.0", remoteVersion = "v1.0.0") == 0)
        assertTrue(AppUpdateManagerEngine.compareSemVer(currentVersion = "v2.0.0", remoteVersion = "v1.0.0") < 0)

        assertTrue(AppUpdateManagerEngine.isRemoteVersionNewer(currentVersion = "1.0.0", remoteVersion = "v1.0.1"))
        assertFalse(AppUpdateManagerEngine.isRemoteVersionNewer(currentVersion = "1.2.0", remoteVersion = "v1.2.0"))
        assertFalse(AppUpdateManagerEngine.isRemoteVersionNewer(currentVersion = "1.2.0", remoteVersion = "v1.1.9"))

        // Verify SHA-256 digest calculation and ZIP magic header check on a temporary file
        val tempApk = java.io.File.createTempFile("gravityedit_test_", ".apk")
        try {
            // Write valid ZIP local file header magic bytes (PK\x03\x04) + payload
            val payload = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x47, 0x72, 0x61, 0x76, 0x69, 0x74, 0x79)
            tempApk.writeBytes(payload)

            assertTrue(AppUpdateManagerEngine.hasValidApkZipHeader(tempApk))
            val sha256 = AppUpdateManagerEngine.computeSha256Hex(tempApk)
            assertEquals(64, sha256.length)
            assertEquals(sha256, AppUpdateManagerEngine.computeSha256Hex(tempApk))
        } finally {
            tempApk.delete()
        }

        // Verify file type detection across supported extensions for New Document & Rename
        assertEquals(SyntaxLanguage.PLAIN_TEXT, SyntaxLanguage.fromFileName("Untitled-1.txt"))
        assertEquals(SyntaxLanguage.POWERSHELL, SyntaxLanguage.fromFileName("Deploy-Cluster.ps1"))
        assertEquals(SyntaxLanguage.POWERSHELL, SyntaxLanguage.fromFileName("Utilities.psm1"))
        assertEquals(SyntaxLanguage.MARKDOWN, SyntaxLanguage.fromFileName("README.md"))
        assertEquals(SyntaxLanguage.JSON, SyntaxLanguage.fromFileName("config.json"))
        assertEquals(SyntaxLanguage.YAML, SyntaxLanguage.fromFileName("pipeline.yaml"))
        assertEquals(SyntaxLanguage.KOTLIN, SyntaxLanguage.fromFileName("Main.kt"))
        assertEquals(SyntaxLanguage.PYTHON, SyntaxLanguage.fromFileName("script.py"))
    }

    // =========================================================================
    // 7. QS REGRESSION TESTS (PIPELINE ALIASES, CLIPPINGS, REGEX & SELECTION)
    // =========================================================================

    @Test
    fun powerShellEngine_detectsAliasesAfterPipesAssignmentsAndSubexpressionsAndSenderVar() {
        val tokens = PowerShellEngine.tokenizePowerShellLine("Register-ObjectEvent -Action { Write-Host \$sender }")
        assertTrue(tokens.any { it.type == PowerShellTokenType.VARIABLE_AUTOMATIC && it.text == "\$sender" })

        val script = """
            ${'$'}files = ls -Path .
            ${'$'}arr = @(dir -Path .)
            ${'$'}meta = @{ ls = 'key_not_alias' }
            gci -Path . | where { ${'$'}_.Length -gt 0 } | select Name
            foreach (${'$'}f in ${'$'}files) {
                Write-Output "${'$'}(cat ${'$'}f)"
                ${'$'}raw = ${'$'}(gc ${'$'}f)
            }
        """.trimIndent()

        val diags = PowerShellEngine.analyzeScriptDiagnostics(script)
        assertTrue(diags.none { it.severity == DiagnosticSeverity.ERROR })

        val aliasTargets = diags
            .filter { it.ruleId == "PSAvoidUsingCmdletAliases" }
            .mapNotNull { it.quickFixTarget }

        assertEquals(1, aliasTargets.count { it == "ls" }) // only '$files = ls', NOT hashtable key 'ls ='
        assertTrue(aliasTargets.contains("dir"))
        assertTrue(aliasTargets.contains("gci"))
        assertTrue(aliasTargets.contains("where"))
        assertTrue(aliasTargets.contains("select"))
        assertTrue(aliasTargets.contains("cat"))
        assertTrue(aliasTargets.contains("gc"))
        // Statement keyword 'foreach ($f in $files)' must NOT be flagged as an alias
        assertFalse(aliasTargets.contains("foreach"))
    }

    @Test
    fun clippingsManager_builtInPowerShellTemplatesProduceValidSyntaxWithoutAliasOrDelimiterErrors() {
        val advFn = ClippingsManager.BUILT_IN_CLIPPINGS.first { it.id == "ps_cmdlet_binding" }
        val expanded = ClippingsManager.expandTemplate(
            templateBody = advFn.templateBody,
            context = ClippingExpansionContext(
                selectedText = "Write-Output \$item",
                fileName = "Invoke-Deploy.ps1",
                userName = "Tester"
            ),
            nowDate = "2026-11-11",
            nowTime = "15:00:00"
        )
        assertTrue(expanded.textToInsert.contains("\$(\$MyInvocation.MyCommand.Name)"))
        assertFalse(expanded.textToInsert.contains("\$({MyInvocation"))

        // Verify ALL built-in PowerShell clippings (including @{...}, @(...), $(...)) have zero syntax errors
        for (clip in ClippingsManager.BUILT_IN_CLIPPINGS.filter { it.id.startsWith("ps_") }) {
            val exp = ClippingsManager.expandTemplate(
                templateBody = clip.templateBody,
                context = ClippingExpansionContext(
                    selectedText = "Write-Verbose 'ok'",
                    fileName = "Sample.ps1",
                    userName = "Tester"
                ),
                nowDate = "2026-11-11",
                nowTime = "15:00:00"
            )
            val diagnostics = PowerShellEngine.analyzeScriptDiagnostics(exp.textToInsert)
            assertTrue(
                "Expected no syntax errors in built-in clipping ${clip.id}, got: $diagnostics",
                diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
            )
        }
    }

    @Test
    fun findReplaceEngine_respectsSelectionRangeExclusivityAndDotMatchesAllFlag() {
        val multiLineText = "BEGIN\nsecret_line_1\nsecret_line_2\nEND"

        val withoutDotAll = FindReplaceEngine.findAllMatches(
            text = multiLineText,
            options = SearchOptions(query = "BEGIN.*END", grepRegex = true, dotMatchesAll = false)
        )
        assertEquals(0, withoutDotAll.size)

        val withDotAll = FindReplaceEngine.findAllMatches(
            text = multiLineText,
            options = SearchOptions(query = "BEGIN.*END", grepRegex = true, dotMatchesAll = true)
        )
        assertEquals(1, withDotAll.size)

        // Selection-only search should not include characters outside [selStart, selEnd)
        val sample = "foo bar foo baz foo"
        val selStart = 4 // "bar foo baz "
        val selEnd = 15
        val selMatches = FindReplaceEngine.findAllMatches(
            text = sample,
            options = SearchOptions(query = "foo", grepRegex = false, searchInSelectionOnly = true),
            selectionRange = selStart until selEnd
        )
        assertEquals(1, selMatches.size)
        assertEquals(8, selMatches.first().range.first)

        val (replaced, count) = FindReplaceEngine.replaceAll(
            text = sample,
            options = SearchOptions(query = "foo", replacement = "FOO", grepRegex = false, searchInSelectionOnly = true),
            selectionRange = selStart until selEnd
        )
        assertEquals(1, count)
        assertEquals("foo bar FOO baz foo", replaced)
    }

    @Test
    fun editorDocument_computesAccurateCursorLineColAndSelectionStatistics() {
        val doc = EditorDocument(
            title = "Test.ps1",
            content = "first line\nsecond line\nthird line",
            selectionStart = 11,
            selectionEnd = 22
        )
        val (ln, col) = doc.cursorLineAndColumn()
        assertEquals(2, ln)
        assertEquals(12, col)

        val stats = doc.selectionStats()
        assertTrue(stats.hasSelection)
        assertEquals(11, stats.charCount)
        assertEquals(2, stats.wordCount)
        assertEquals(1, stats.lineCount)
    }

    @Test
    fun localizationResources_allSixLocalesHaveCompleteStringKeyParity() {
        val resRoot = java.io.File("src/main/res").takeIf { it.isDirectory }
            ?: java.io.File("app/src/main/res")
        assertTrue("Expected Android res directory to exist at ${resRoot.absolutePath}", resRoot.isDirectory)

        val stringNameRegex = Regex("""<string\s+name="([^"]+)"""")
        fun extractKeys(folderName: String): Set<String> {
            val xmlFile = java.io.File(resRoot, "$folderName/strings.xml")
            assertTrue("Missing $folderName/strings.xml", xmlFile.isFile)
            val content = xmlFile.readText(Charsets.UTF_8)
            return stringNameRegex.findAll(content).map { it.groupValues[1] }.toSet()
        }

        val baseKeys = extractKeys("values")
        assertTrue("Expected at least 115 localized string keys, got ${baseKeys.size}", baseKeys.size >= 115)

        val requiredNewKeys = setOf(
            "action_rename_document",
            "action_move_tab_left",
            "action_move_tab_right",
            "action_more_options",
            "action_diagnostics",
            "action_command_palette",
            "action_check_updates"
        )
        assertTrue("Base strings.xml missing new Expressive menu keys", baseKeys.containsAll(requiredNewKeys))

        val locales = listOf("values-de", "values-el", "values-es", "values-fr", "values-it")
        for (localeDir in locales) {
            val localeKeys = extractKeys(localeDir)
            val missingInLocale = baseKeys - localeKeys
            val extraInLocale = localeKeys - baseKeys
            assertTrue(
                "Locale $localeDir is missing keys: $missingInLocale",
                missingInLocale.isEmpty()
            )
            assertTrue(
                "Locale $localeDir has unexpected extra keys: $extraInLocale",
                extraInLocale.isEmpty()
            )
        }
    }

    @Test
    fun appUpdateEngine_handlesVersion120AndDebugSuffixCleanly() {
        assertFalse(AppUpdateManagerEngine.isRemoteVersionNewer("1.2.0-debug", "v1.2.0"))
        assertFalse(AppUpdateManagerEngine.isRemoteVersionNewer("1.2.0", "v1.1.1"))
        assertTrue(AppUpdateManagerEngine.isRemoteVersionNewer("1.1.1", "v1.2.0"))
        assertTrue(AppUpdateManagerEngine.isRemoteVersionNewer("1.2.0-debug", "v1.2.1"))
    }
}


