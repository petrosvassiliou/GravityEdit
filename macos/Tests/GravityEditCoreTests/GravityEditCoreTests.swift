import GravityEditCore
import XCTest

final class GravityEditCoreTests: XCTestCase {

    // MARK: - 1. PowerShell Tokenizer & Multi-Line States

    func testPowerShellLineStatesAndTokenization() {
        let script = """
        <#
        .SYNOPSIS
            Multi-line help
        #>
        $env:API_KEY = "Bearer $($token)"
        Get-ChildItem -Path './src' | Where-Object { $_.Length -gt 10 }
        """
        let lines = script.components(separatedBy: "\n")
        let states = PowerShellEngine.computeLineStates(lines, language: .powershell)
        XCTAssertEqual(states[0], .normal)
        XCTAssertEqual(states[1], .inBlockComment)
        XCTAssertEqual(states[4], .normal)

        let helpTokens = PowerShellEngine.tokenizePowerShellLine(lines[1], startState: states[1])
        XCTAssertTrue(helpTokens.contains { $0.type == .commentHelpTag && $0.text == ".SYNOPSIS" })

        let envTokens = PowerShellEngine.tokenizePowerShellLine(lines[4], startState: .normal)
        XCTAssertTrue(envTokens.contains { $0.type == .variableScoped && $0.text == "$env:API_KEY" })
        XCTAssertTrue(envTokens.contains { $0.type == .stringInterpolation && $0.text == "$($token)" })

        let pipeTokens = PowerShellEngine.tokenizePowerShellLine(lines[5], startState: .normal)
        XCTAssertTrue(pipeTokens.contains { $0.type == .cmdlet && $0.text == "Get-ChildItem" })
        XCTAssertTrue(pipeTokens.contains { $0.type == .parameter && $0.text == "-Path" })
        XCTAssertTrue(pipeTokens.contains { $0.type == .variableAutomatic && $0.text == "$_" })
        XCTAssertTrue(pipeTokens.contains { $0.type == .operator && $0.text == "-gt" })
    }

    // MARK: - 2. Advanced Function AST & Procedural DFS-Migration.ps1 Navigator

    func testAdvancedPowerShellFunctionAndMultiLineParamExtraction() {
        let script = """
        # === Deployment Helpers ===
        function Invoke-PrüfungDeployment {
            [CmdletBinding(SupportsShouldProcess = $true)]
            param (
                [Parameter(Mandatory = $true)]
                [string]$TargetHost = "srv01",
                [int]$Port = 443
            )
            Write-Verbose $TargetHost
        }

        $CleanupAction = {
            Remove-Item -Path '/tmp/stage' -Force
        }
        """

        let symbols = PowerShellEngine.extractAstSymbols(script, language: .powershell)
        XCTAssertEqual(symbols.count, 3)
        XCTAssertEqual(symbols[0].kind, .section)
        XCTAssertEqual(symbols[0].name, "Deployment Helpers")

        XCTAssertEqual(symbols[1].kind, .function)
        XCTAssertEqual(symbols[1].name, "Invoke-PrüfungDeployment")
        XCTAssertEqual(symbols[1].parameters, ["$TargetHost", "$Port"])
        XCTAssertEqual(symbols[1].signature, "Invoke-PrüfungDeployment($TargetHost, $Port)")

        XCTAssertEqual(symbols[2].kind, .scriptblock)
        XCTAssertEqual(symbols[2].name, "$CleanupAction")
    }

    func testProceduralPowerShellLandmarksFallbackForLinearAdminScript() {
        let dfsMigration = """
        $SourceRoot = "D:\\Data\\Schulen"
        $DestRoot   = "\\\\dfs.corp\\Schulen"
        $LogDir     = "C:\\Logs\\DFS"

        # ==========================================
        # Robocopy Mapping Table
        # ==========================================
        $Folders = @(
            @{ Src = "Verwaltung"; Dst = "Admin" }
            @{ Src = "Lehrer";     Dst = "Teachers" }
        )

        if (!(Test-Path -Path $LogDir)) {
            New-Item -Path $LogDir -ItemType Directory | Out-Null
        }

        foreach ($entry in $Folders) {
            Write-Output "Syncing $($entry.Src)"
        }
        """

        let symbols = PowerShellEngine.extractAstSymbols(dfsMigration, language: .powershell)
        XCTAssertTrue(symbols.contains { $0.kind == .configVars && $0.parameters.contains("$SourceRoot") })
        XCTAssertTrue(symbols.contains { $0.kind == .section && $0.name == "Robocopy Mapping Table" })
        XCTAssertTrue(symbols.contains { $0.kind == .dataTable && $0.name == "$Folders" })
        XCTAssertTrue(symbols.contains { $0.kind == .controlBlock && $0.name.hasPrefix("if") })
        XCTAssertTrue(symbols.contains { $0.kind == .controlBlock && $0.name.hasPrefix("foreach") })

        let folds = PowerShellEngine.extractFoldRegions(dfsMigration, language: .powershell)
        XCTAssertTrue(folds.contains { $0.label.contains("$Folders = @( … )") })
        XCTAssertTrue(folds.contains { $0.label.contains("foreach") })
    }

    func testSecondaryLanguageSymbolOutlines() {
        let swiftCode = """
        // MARK: - Networking
        public struct APIClient {
            public func fetchReleases(owner: String, repo: String) async throws -> Data {
                Data()
            }
        }
        """
        let swiftSyms = PowerShellEngine.extractAstSymbols(swiftCode, language: .swift)
        XCTAssertEqual(swiftSyms.map(\.kind), [.section, .class, .function])
        XCTAssertEqual(swiftSyms[2].name, "fetchReleases")
    }

    // MARK: - 3. Completions & PSScriptAnalyzer Quick Fixes

    func testPowerShellCompletionsAndLinterQuickFixes() {
        let dirtyScript = """
        function CustomVerb-Report {
            param([string]$ApiPassword)
            try { gci ./logs | % { $_ } } catch { }
        }
        """

        let diags = PowerShellEngine.analyzeScriptDiagnostics(dirtyScript)
        let ruleIds = Set(diags.map(\.ruleId))
        XCTAssertTrue(ruleIds.contains("PSUseApprovedVerbs"))
        XCTAssertTrue(ruleIds.contains("PSAvoidUsingPlainTextForPassword"))
        XCTAssertTrue(ruleIds.contains("PSAvoidEmptyCatchBlock"))
        XCTAssertTrue(ruleIds.contains("PSAvoidUsingCmdletAliases"))

        let (fixed, appliedCount) = PowerShellEngine.applyAllQuickFixes(documentContent: dirtyScript)
        XCTAssertGreaterThanOrEqual(appliedCount, 4)
        XCTAssertTrue(fixed.contains("Invoke-Report"))
        XCTAssertTrue(fixed.contains("[SecureString]$ApiPassword"))
        XCTAssertTrue(fixed.contains("Get-ChildItem"))
        XCTAssertTrue(fixed.contains("ForEach-Object"))
        XCTAssertTrue(fixed.contains("catch { Write-Error $_ }"))

        let cmdCompletions = PowerShellEngine.computeCompletions(line: "Invoke-Rest", cursorUTF16Column: 11)
        XCTAssertTrue(cmdCompletions.contains { $0.label == "Invoke-RestMethod" })
    }

    // MARK: - 4. All 14 BBEdit Text Factory Operations

    func testBBEditTextFactoryPipelineOperations() {
        let raw = "item10  \nitem2\t\nitem2\nitem1\0"
        let steps: [TextFactoryStep] = [
            TextFactoryStep(operationType: .zapGremlins),
            TextFactoryStep(operationType: .stripTrailingWhitespace),
            TextFactoryStep(operationType: .processDuplicateLines, duplicateMode: .leaveOne),
            TextFactoryStep(operationType: .sortLines, sortDirection: .ascending, sortNumeric: true),
            TextFactoryStep(operationType: .changeCase, caseMode: .uppercase),
            TextFactoryStep(operationType: .prefixSuffixLines, prefixSuffixAction: .insert, linePrefix: "- ", lineSuffix: ";")
        ]
        let res = TextTransformEngine.executePipeline(inputText: raw, steps: steps, documentLanguage: .plainText)
        XCTAssertEqual(res.stepsExecuted, 6)
        XCTAssertEqual(res.transformedText, "- ITEM1;\n- ITEM2;\n- ITEM10;")

        // Case transforms (camelCase & snake_case)
        XCTAssertEqual(TextTransformEngine.changeCase("gravity edit mac", mode: .camelCase), "gravityEditMac")
        XCTAssertEqual(TextTransformEngine.changeCase("gravityEditMac", mode: .snakeCase), "gravity_edit_mac")

        // Entab / Detab, Line numbers, Quotes, JSON & PowerShell formatting
        let numbered = TextTransformEngine.processLineNumbers("alpha\nbeta", action: .addNumbers, start: 1, zeroPadWidth: 2)
        XCTAssertEqual(numbered, "01: alpha\n02: beta")
        XCTAssertEqual(TextTransformEngine.processLineNumbers(numbered, action: .removeNumbers), "alpha\nbeta")

        let curly = TextTransformEngine.transformQuotes("\"Hello\"", mode: .educateCurlyQuotes)
        XCTAssertEqual(curly, "“Hello”")
        XCTAssertEqual(TextTransformEngine.transformQuotes(curly, mode: .straightenQuotes), "\"Hello\"")

        let b64 = TextTransformEngine.base64Encode("GravityEdit")
        XCTAssertEqual(TextTransformEngine.base64Decode(b64), "GravityEdit")
    }

    // MARK: - 5. PCRE Find/Replace, Diff & Clippings

    func testFindReplaceDiffAndClippingsEngines() throws {
        let sample = "Error 404 on node-a\nOK 200 on node-b\nError 502 on node-c"
        let opts = SearchOptions(query: #"Error (\d+) on ([a-z\-]+)"#, replacement: "[$2 => HTTP $1]", grepRegex: true)
        let matches = try FindReplaceEngine.findAllMatches(in: sample, options: opts).get()
        XCTAssertEqual(matches.count, 2)
        XCTAssertEqual(matches[0].captureGroups.map(\.value), ["404", "node-a"])

        let replaced = try FindReplaceEngine.replaceAllMatches(in: sample, options: opts).get()
        XCTAssertEqual(replaced.replacedCount, 2)
        XCTAssertTrue(replaced.updatedText.contains("[node-a => HTTP 404]"))

        // Myers Diff & directional hunk merge
        let diff = DiffEngine.computeDiff(leftText: "a\nb\nc", rightText: "a\nB_MOD\nc\nd")
        XCTAssertFalse(diff.isIdentical)
        XCTAssertEqual(diff.modifiedCount, 1)
        XCTAssertEqual(diff.addedCount, 1)
        let mergedLeft = DiffEngine.mergeRowRightToLeft(diff: diff, rowIndex: 1)
        XCTAssertEqual(mergedLeft, "a\nB_MOD\nc")

        // Clippings placeholder expansion
        let expanded = ClippingsManager.expandTemplate(
            "fn #SELSTART#Get-Item#SELEND# { #SELECTION# #FILENAME# #DATE# }",
            context: ClippingExpansionContext(selectedText: "$x", fileName: "Test.ps1", userName: "Tester"),
            nowDate: "2026-11-18",
            nowTime: "12:00:00"
        )
        XCTAssertEqual(expanded.textToInsert, "fn Get-Item { $x Test.ps1 2026-11-18 }")
        XCTAssertEqual(expanded.selectionRelativeUTF16Range, NSRange(location: 3, length: 8))
    }

    // MARK: - 6. MacAutoUpdateEngine & Localization Parity

    func testMacAutoUpdateSemVerSHA256AndReleaseParser() throws {
        XCTAssertTrue(MacAutoUpdateEngine.isNewerVersion(remoteVersion: "v1.3.1", than: "1.3.0"))
        XCTAssertTrue(MacAutoUpdateEngine.isNewerVersion(remoteVersion: "2.0.0", than: "1.9.9"))
        XCTAssertFalse(MacAutoUpdateEngine.isNewerVersion(remoteVersion: "v1.3.0", than: "1.3.0"))
        XCTAssertFalse(MacAutoUpdateEngine.isNewerVersion(remoteVersion: "1.2.4", than: "1.3.0"))

        let payload = """
        {
          "tag_name": "v1.3.0",
          "name": "GravityEdit v1.3.0",
          "body": "macOS Liquid Glass release",
          "html_url": "https://github.com/petrosvassiliou/GravityEdit/releases/tag/v1.3.0",
          "published_at": "2026-11-18T08:00:00Z",
          "assets": [
            {
              "name": "GravityEdit-macOS-v1.3.0.app.zip",
              "browser_download_url": "https://github.com/petrosvassiliou/GravityEdit/releases/download/v1.3.0/GravityEdit-macOS-v1.3.0.app.zip",
              "size": 8400000
            },
            {
              "name": "GravityEdit-macOS-v1.3.0.dmg",
              "browser_download_url": "https://github.com/petrosvassiliou/GravityEdit/releases/download/v1.3.0/GravityEdit-macOS-v1.3.0.dmg",
              "size": 9100000
            },
            {
              "name": "appcast.xml",
              "browser_download_url": "https://github.com/petrosvassiliou/GravityEdit/releases/download/v1.3.0/appcast.xml",
              "size": 1200
            }
          ]
        }
        """.data(using: .utf8)!

        let parsed = try MacAutoUpdateEngine.parseGitHubReleaseJSON(payload)
        XCTAssertEqual(parsed.versionString, "1.3.0")
        XCTAssertEqual(parsed.appZipAsset?.name, "GravityEdit-macOS-v1.3.0.app.zip")
        XCTAssertEqual(parsed.dmgAsset?.name, "GravityEdit-macOS-v1.3.0.dmg")
        XCTAssertEqual(parsed.appcastAsset?.name, "appcast.xml")

        let sampleBytes = Data("GravityEdit-LiquidGlass".utf8)
        let digest = MacAutoUpdateEngine.sha256Hex(of: sampleBytes)
        XCTAssertEqual(digest.count, 64)
        let manifest = "\(digest)  GravityEdit-macOS-v1.3.0.app.zip\n"
        XCTAssertEqual(
            MacAutoUpdateEngine.parseExpectedSHA256(from: manifest, assetFileName: "GravityEdit-macOS-v1.3.0.app.zip"),
            digest
        )

        // Ensure all 6 locales return non-empty translations for every key
        for lang in [AppLanguage.english, .german, .italian, .spanish, .french, .greek] {
            for key in L10nKey.allCases {
                XCTAssertFalse(LocalizationCatalog.tr(key, language: lang).isEmpty)
            }
        }
    }

    func testDocumentTitleExtensionSyncWithLanguage() {
        XCTAssertEqual(
            EditorDocument.titleSynchronizedWithLanguage(currentTitle: "GravityWorkbench.ps1", language: .plainText),
            "GravityWorkbench.txt"
        )
        XCTAssertEqual(
            EditorDocument.titleSynchronizedWithLanguage(currentTitle: "GravityWorkbench.txt", language: .powershell),
            "GravityWorkbench.ps1"
        )
        // Secondary valid extensions for the same language are preserved
        XCTAssertEqual(
            EditorDocument.titleSynchronizedWithLanguage(currentTitle: "Inventory.psm1", language: .powershell),
            "Inventory.psm1"
        )
        XCTAssertEqual(
            EditorDocument.titleSynchronizedWithLanguage(currentTitle: "Server.log", language: .plainText),
            "Server.log"
        )
        XCTAssertEqual(
            EditorDocument.titleSynchronizedWithLanguage(currentTitle: "Server.log", language: .swift),
            "Server.swift"
        )

        var diskDoc = EditorDocument(
            title: "Fix-1_FileServer-Shares.ps1",
            fileURL: URL(fileURLWithPath: "/tmp/Fix-1_FileServer-Shares.ps1"),
            content: "Get-SmbShare",
            savedContent: "Get-SmbShare",
            language: .powershell
        )
        XCTAssertFalse(diskDoc.isDirty)
        diskDoc.language = .plainText
        diskDoc.title = EditorDocument.titleSynchronizedWithLanguage(currentTitle: diskDoc.title, language: .plainText)
        XCTAssertEqual(diskDoc.title, "Fix-1_FileServer-Shares.txt")
        XCTAssertTrue(diskDoc.isDirty)
    }

    func testCanonizePreservesPercentagesAndURLsAndFormatPrettifyRespectsPowerShell() {
        let aliasTable = "%=ForEach-Object\n?=Where-Object\ngci=Get-ChildItem"
        let psSample = "Write-Host \"100% complete https://example.com?debug=1 status=$?\"\ngci | % { $_ } | ? { $_.Length -gt 0 }"
        let canonized = TextTransformEngine.canonizeUsingTable(
            psSample,
            tableRaw: aliasTable,
            caseSensitive: false,
            wholeWords: true
        )
        XCTAssertTrue(canonized.contains("100% complete https://example.com?debug=1 status=$?"))
        XCTAssertTrue(canonized.contains("Get-ChildItem | ForEach-Object { $_ } | Where-Object { $_.Length -gt 0 }"))

        let bracketedPS = "[CmdletBinding()]\nparam(\n[string]$Node\n)\nif ($Node) {\n[int[]]$Ports = @(80, 443)\n}"
        let formattedPS = TextTransformEngine.formatPrettify(
            bracketedPS,
            target: .auto,
            documentLanguage: .powershell,
            indentSpaces: 4
        )
        XCTAssertTrue(formattedPS.contains("    [string]$Node"))
        XCTAssertTrue(formattedPS.contains("    [int[]]$Ports = @(80, 443)"))

        XCTAssertEqual(TextTransformEngine.changeCase("getServiceStatus", mode: .camelCase), "getServiceStatus")
        XCTAssertEqual(TextTransformEngine.changeCase("Get-ServiceStatus", mode: .camelCase), "getServiceStatus")

        var doc = EditorDocument(
            title: "Server.ps1",
            fileURL: URL(fileURLWithPath: "/tmp/Server.ps1"),
            content: "Get-Date",
            savedContent: "Get-Date",
            language: .powershell,
            encoding: .utf8,
            lineEnding: .lf
        )
        XCTAssertFalse(doc.isDirty)
        doc.lineEnding = .crlf
        XCTAssertTrue(doc.isDirty)
        doc.lineEnding = .lf
        doc.encoding = .utf8Bom
        XCTAssertTrue(doc.isDirty)

        let swapScript = MacAutoUpdateEngine.buildAtomicBundleSwapScript(
            processIdentifier: 4242,
            stagedAppBundleURL: URL(fileURLWithPath: "/tmp/Staged/GravityEdit.app"),
            destinationAppBundleURL: URL(fileURLWithPath: "/Applications/GravityEdit.app")
        )
        XCTAssertTrue(swapScript.contains("if kill -0 \"$TARGET_PID\" 2>/dev/null; then"))
        XCTAssertTrue(swapScript.contains("exit 0"))
    }

    func testInstantTabSwitchCachedMetricsAndPlainTextFastPath() {
        let csvRows = (1...5000).map { "Row-\($0);Informationsverbund;Verwaltung;\($0 * 42)" }
        let csvBody = csvRows.joined(separator: "\n")
        var csvDoc = EditorDocument(
            title: "Informationsverbund_Verwaltung.csv",
            content: csvBody,
            savedContent: csvBody,
            language: .plainText
        )
        XCTAssertEqual(csvDoc.lineCount, 5000)
        XCTAssertEqual(csvDoc.wordCount, 5000) // no whitespace inside semicolon-delimited rows

        let t0 = CFAbsoluteTimeGetCurrent()
        for _ in 0..<200 {
            _ = csvDoc.lineCount
            _ = csvDoc.wordCount
            _ = PowerShellEngine.extractAstSymbols(csvDoc.content, language: .plainText)
            _ = PowerShellEngine.extractFoldRegions(csvDoc.content, language: .plainText)
        }
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - t0) * 1000.0
        XCTAssertLessThan(elapsedMs, 25.0, "200 tab-switch metric/outline queries on a 5,000-line CSV must complete in < 25ms (actual: \(elapsedMs)ms)")

        csvDoc.content = "Alpha Beta\nGamma"
        XCTAssertEqual(csvDoc.lineCount, 2)
        XCTAssertEqual(csvDoc.wordCount, 3)
    }

    // MARK: - v1.4.0 Tests (#1 Runner/Worksheet, #2 Dry-Run/Presets/Column/Bookmarks, #3 Huge-File/Watcher, #4 Session/CLI)

    func testPowerShellRunnerWorksheetAndStackTraceLocationParsing() {
        let script = """
        $svc = [ordered]@{
            Name = 'GravityGateway'
            Port = 8443
        }
        $svc | ConvertTo-Json
        Write-Warning 'Certificate expires soon'
        Write-Error 'Connection refused'
        """
        let res = PowerShellRunnerEngine.executeScript(
            code: script,
            language: .powershell,
            forceEmbeddedPowerShell: true
        )
        XCTAssertEqual(res.exitCode, 1)
        XCTAssertTrue(res.rawCombinedOutput.contains("GravityGateway"))
        XCTAssertTrue(res.lines.contains(where: { $0.stream == .warning && $0.text.contains("Certificate expires soon") }))
        let errLine = res.lines.first(where: { $0.stream == .stderr })
        XCTAssertNotNil(errLine?.location)
        XCTAssertEqual(errLine?.location?.lineNumber, 7)

        let parsedLoc = PowerShellRunnerEngine.parseExecutionLocation(
            from: "At /tmp/deploy.ps1:42 char:15",
            userLineAdjustment: 0
        )
        XCTAssertEqual(parsedLoc?.lineNumber, 42)
        XCTAssertEqual(parsedLoc?.columnNumber, 15)

        let docText = "$x = 20 + 22\n$x"
        let evalRes = PowerShellRunnerEngine.executeScript(code: docText, language: .powershell, forceEmbeddedPowerShell: true)
        let worksheet = PowerShellRunnerEngine.formatWorksheetInsertion(
            in: docText,
            selectionRange: NSRange(location: 0, length: (docText as NSString).length),
            result: evalRes
        )
        XCTAssertTrue(worksheet.updatedText.contains("Worksheet Output"))
        XCTAssertTrue(worksheet.updatedText.contains("42"))
    }

    func testMultiFileDryRunPreviewSelectiveReplaceAndBatchTextFactory() throws {
        let fm = FileManager.default
        let tempDir = fm.temporaryDirectory.appendingPathComponent("gravity_v140_\(UUID().uuidString)", isDirectory: true)
        try fm.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: tempDir) }

        let fileA = tempDir.appendingPathComponent("Alpha.ps1")
        let fileB = tempDir.appendingPathComponent("Beta.ps1")
        try Data("gci -Path /etc   \nWrite-Host 'Alpha'  ".utf8).write(to: fileA)
        try Data("gci -Path /var   \nWrite-Host 'Beta'".utf8).write(to: fileB)

        let opts = SearchOptions(query: #"\bgci\b"#, replacement: "Get-ChildItem", grepRegex: true, fileFilter: "*.ps1")
        let previewRes = FindReplaceEngine.previewReplaceInDirectory(rootURL: tempDir, options: opts)
        guard case .success(let previews) = previewRes else {
            XCTFail("Expected dry-run preview success")
            return
        }
        XCTAssertEqual(previews.count, 2)
        XCTAssertEqual(previews[0].lineChanges.first?.afterLine.contains("Get-ChildItem"), true)

        // Apply selectively ONLY to Alpha.ps1
        let selectiveRes = FindReplaceEngine.replaceInDirectory(
            rootURL: tempDir,
            options: opts,
            selectedFilePaths: [fileA.path]
        )
        if case .success(let stats) = selectiveRes {
            XCTAssertEqual(stats.filesModified, 1)
            XCTAssertEqual(stats.totalReplacements, 1)
        } else {
            XCTFail("Expected selective replace success")
        }
        XCTAssertTrue(try String(contentsOf: fileA, encoding: .utf8).contains("Get-ChildItem"))
        XCTAssertTrue(try String(contentsOf: fileB, encoding: .utf8).contains("gci -Path /var"))

        // Batch Text Factory across folder using built-in preset
        let preset = SavedTextFactoryPreset.builtInPresets.first!
        let batchRes = TextTransformEngine.executePipelineInDirectory(
            rootURL: tempDir,
            steps: preset.steps,
            fileFilter: "*.ps1"
        )
        if case .success(let batchStats) = batchRes {
            XCTAssertEqual(batchStats.filesScanned, 2)
            XCTAssertGreaterThanOrEqual(batchStats.filesModified, 1)
        } else {
            XCTFail("Expected batch folder Text Factory success")
        }
        // Beta.ps1 should now have trailing whitespace stripped & `gci` canonized!
        let updatedBeta = try String(contentsOf: fileB, encoding: .utf8)
        XCTAssertTrue(updatedBeta.contains("Get-ChildItem"))
    }

    func testColumnRectangularBlockEditingAndLineBookmarks() {
        let source = "SRV01  Online\nSRV02  Offline\nSRV03  Online"
        let spec = ColumnBlockSpec(startLine: 1, endLine: 3, startColumn: 1, endColumn: 5)
        XCTAssertEqual(TextTransformEngine.extractColumnBlock(source, spec: spec), "SRV01\nSRV02\nSRV03")

        let prefixed = TextTransformEngine.applyColumnEdit(
            source,
            spec: ColumnBlockSpec(startLine: 1, endLine: 3, startColumn: 1, endColumn: 1),
            action: .insertAtColumn,
            insertText: "[PROD] "
        )
        XCTAssertTrue(prefixed.hasPrefix("[PROD] SRV01"))
        XCTAssertTrue(prefixed.contains("\n[PROD] SRV03"))

        let numbered = TextTransformEngine.applyColumnEdit(
            source,
            spec: ColumnBlockSpec(startLine: 1, endLine: 3, startColumn: 1, endColumn: 1),
            action: .numberColumn,
            insertText: ": ",
            startNumber: 10,
            stepNumber: 10,
            zeroPadWidth: 3
        )
        XCTAssertTrue(numbered.contains("010: SRV01"))
        XCTAssertTrue(numbered.contains("030: SRV03"))

        var doc = EditorDocument(title: "Bookmarks.ps1", content: "L1\nL2\nL3\nL4\nL5", savedContent: "L1\nL2\nL3\nL4\nL5")
        XCTAssertTrue(doc.toggleBookmark(oneBasedLine: 2))
        XCTAssertTrue(doc.toggleBookmark(oneBasedLine: 4))
        XCTAssertEqual(doc.nextBookmarkedLine(after: 1), 2)
        XCTAssertEqual(doc.nextBookmarkedLine(after: 2), 4)
        XCTAssertEqual(doc.nextBookmarkedLine(after: 4), 2) // wrap-around
        XCTAssertEqual(doc.previousBookmarkedLine(before: 4), 2)
        XCTAssertFalse(doc.toggleBookmark(oneBasedLine: 2))
        XCTAssertEqual(doc.bookmarkedLines, [4])
    }

    func testHugeFileThresholdExternalWatcherSessionAndGravityCLIParser() throws {
        let hugeBody = String(repeating: "2026-11-23 INFO Telemetry OK\n", count: 10_000)
        let hugeDoc = EditorDocument(title: "Massive.log", content: hugeBody, savedContent: hugeBody, language: .plainText)
        XCTAssertTrue(hugeDoc.isLargeFileMode)

        let fm = FileManager.default
        let logURL = fm.temporaryDirectory.appendingPathComponent("live_tail_\(UUID().uuidString).log")
        defer { try? fm.removeItem(at: logURL) }
        try Data("Line 1\n".utf8).write(to: logURL)
        let meta1 = ExternalFileWatcher.inspectDiskMetadata(at: logURL)
        XCTAssertNotNil(meta1)
        XCTAssertEqual(ExternalFileWatcher.checkExternalChange(at: logURL, lastKnownModDate: meta1?.modificationTime), .unchanged)

        // Advance mtime & append new line
        try Data("Line 1\nLine 2\nLine 3\n".utf8).write(to: logURL)
        try fm.setAttributes([.modificationDate: Date().addingTimeInterval(5)], ofItemAtPath: logURL.path)
        if case .modifiedExternally = ExternalFileWatcher.checkExternalChange(at: logURL, lastKnownModDate: meta1?.modificationTime) {
            let tail = ExternalFileWatcher.readLatestDiskText(at: logURL, maxTailBytes: 14)
            XCTAssertNotNil(tail)
            XCTAssertTrue(tail?.text.contains("Line 3") == true)
        } else {
            XCTFail("Expected external modification detection")
        }

        // Session JSON persistence round-trip
        var savedDoc = EditorDocument(title: "Unsaved.ps1", content: "$a = 1", savedContent: "", bookmarkedLines: [1])
        savedDoc.selectionStart = 3
        savedDoc.selectionEnd = 3
        let session = PersistedWorkspaceSession(
            openDocuments: [savedDoc],
            activeDocumentId: savedDoc.id,
            secondaryDocumentId: savedDoc.id,
            splitMode: .vertical,
            mountedFolderPath: "/tmp",
            recentFilePaths: [logURL.path]
        )
        let sessionData = try JSONEncoder().encode(session)
        let decodedSession = try JSONDecoder().decode(PersistedWorkspaceSession.self, from: sessionData)
        XCTAssertEqual(decodedSession.splitMode, .vertical)
        XCTAssertEqual(decodedSession.openDocuments.first?.bookmarkedLines, [1])
        XCTAssertEqual(decodedSession.recentFilePaths, [logURL.path])

        // `gravity` CLI argument parser
        let cliJump = GravityCLIParser.parse(arguments: ["scripts/Deploy.ps1:42"], currentDirectoryPath: "/work")
        if case .openHandoff(let payload) = cliJump {
            XCTAssertEqual(payload.filesToOpen, ["/work/scripts/Deploy.ps1"])
            XCTAssertEqual(payload.jumpToLine, 42)
        } else {
            XCTFail("Expected openHandoff")
        }

        let cliDiff = GravityCLIParser.parse(arguments: ["--diff", "a.ps1", "b.ps1"], currentDirectoryPath: "/work")
        if case .openHandoff(let diffPayload) = cliDiff {
            XCTAssertEqual(diffPayload.diffLeftPath, "/work/a.ps1")
            XCTAssertEqual(diffPayload.diffRightPath, "/work/b.ps1")
        } else {
            XCTFail("Expected diff openHandoff")
        }
    }

    // MARK: - v1.5.0 Git Workbench, PowerShell Tooling, CSV/JSON Grid, Markdown Preview & SFTP Tests

    func testGutterLineChangesAndGitWorkbenchLifecycle() throws {
        let baseline = "Line 1\nLine 2\nLine 3\nLine 4"
        let modified = "Line 1\nLine 2 Updated\nLine 4\nLine 5 Added"
        let gutter = DiffEngine.computeGutterLineChanges(baselineText: baseline, currentText: modified)
        XCTAssertEqual(gutter[2], .modified)
        XCTAssertEqual(gutter[4], .added)

        guard let gitPath = GitWorkbenchEngine.detectGitExecutablePath() else { return }
        let fm = FileManager.default
        let repoDir = fm.temporaryDirectory.appendingPathComponent("gravity_git_test_\(UUID().uuidString)").resolvingSymlinksInPath()
        try fm.createDirectory(at: repoDir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: repoDir) }

        func runCmd(_ args: [String]) throws {
            let p = Process()
            p.executableURL = URL(fileURLWithPath: gitPath)
            p.arguments = args
            p.currentDirectoryURL = repoDir
            p.standardOutput = Pipe()
            p.standardError = Pipe()
            try p.run()
            p.waitUntilExit()
        }

        try runCmd(["init", "-b", "main"])
        try runCmd(["config", "user.email", "test@gravityedit.dev"])
        try runCmd(["config", "user.name", "GravityEdit Tester"])

        let scriptURL = repoDir.appendingPathComponent("Deploy.ps1")
        try "Write-Output 'v1'\n".write(to: scriptURL, atomically: true, encoding: .utf8)
        try GitWorkbenchEngine.stageFile(at: scriptURL)
        try runCmd(["commit", "-m", "Initial commit"])

        let cleanStatus = GitWorkbenchEngine.detectRepositoryStatus(for: scriptURL)
        XCTAssertEqual(cleanStatus?.branchName, "main")
        XCTAssertEqual(cleanStatus?.modifiedFileCount, 0)
        XCTAssertEqual(cleanStatus?.isFileTracked, true)
        XCTAssertEqual(GitWorkbenchEngine.fetchHeadBaselineContent(for: scriptURL), "Write-Output 'v1'\n")

        try "Write-Output 'v2-dirty'\n".write(to: scriptURL, atomically: true, encoding: .utf8)
        let dirtyStatus = GitWorkbenchEngine.detectRepositoryStatus(for: scriptURL)
        XCTAssertEqual(dirtyStatus?.modifiedFileCount, 1)
        XCTAssertEqual(dirtyStatus?.hasUnstagedChanges, true)

        try GitWorkbenchEngine.discardWorkingTreeChanges(at: scriptURL)
        let revertedText = try String(contentsOf: scriptURL, encoding: .utf8)
        XCTAssertEqual(revertedText, "Write-Output 'v1'\n")
    }

    func testPowerShellShowCommandQuickHelpAndPesterRunner() {
        let script = """
        <#
        .SYNOPSIS
            Deploys cluster services across environments.
        #>
        function Invoke-ClusterDeploy {
            [CmdletBinding()]
            param (
                [Parameter(Mandatory = $true, HelpMessage = "Target stage")]
                [ValidateSet('Dev', 'Staging', 'Prod')]
                [string]$Environment = 'Staging',

                [int]$ReplicaCount = 3,

                [switch]$ForceRestart
            )
            Write-Output "$Environment:$ReplicaCount"
        }

        Describe 'Cluster Math & String Suite' {
            Context 'Arithmetic' {
                It 'computes 20 + 22' {
                    $total = 20 + 22
                    $total | Should -Be 42
                }
                It 'detects mismatch' {
                    $status = 'Degraded'
                    $status | Should -Be 'Healthy'
                }
            }
        }
        """

        let specs = PowerShellToolingEngine.extractParameterFormSpecs(from: script)
        XCTAssertEqual(specs.count, 1)
        let fnSpec = specs[0]
        XCTAssertEqual(fnSpec.commandName, "Invoke-ClusterDeploy")
        XCTAssertEqual(fnSpec.synopsis, "Deploys cluster services across environments.")
        XCTAssertEqual(fnSpec.parameters.count, 3)
        XCTAssertTrue(fnSpec.parameters[0].isMandatory)
        XCTAssertEqual(fnSpec.parameters[0].validateSetOptions, ["Dev", "Staging", "Prod"])
        XCTAssertTrue(fnSpec.parameters[2].isSwitch)

        let builtCmd = PowerShellToolingEngine.buildInvocationCommand(
            spec: fnSpec,
            values: ["Environment": "Prod", "ReplicaCount": "5"],
            switchStates: ["ForceRestart": true]
        )
        XCTAssertEqual(builtCmd, "Invoke-ClusterDeploy -Environment 'Prod' -ReplicaCount 5 -ForceRestart")

        let docHelp = PowerShellToolingEngine.lookupQuickHelp(
            queryOrCursorToken: "Invoke-ClusterDeploy",
            documentContent: script,
            preferLivePwsh: false
        )
        XCTAssertEqual(docHelp.sourceBadge, "Document AST")
        XCTAssertTrue(docHelp.synopsis.contains("Deploys cluster services"))

        let aliasHelp = PowerShellToolingEngine.lookupQuickHelp(queryOrCursorToken: "irm", preferLivePwsh: false)
        XCTAssertEqual(aliasHelp.name, "Invoke-RestMethod")

        let discovered = PowerShellToolingEngine.discoverPesterTests(in: script)
        XCTAssertEqual(discovered.count, 2)
        XCTAssertEqual(discovered[0].contextBlock, "Arithmetic")

        let pesterBuiltIn = PowerShellToolingEngine.runPesterTests(code: script, forceBuiltIn: true)
        XCTAssertEqual(pesterBuiltIn.passedCount, 1)
        XCTAssertEqual(pesterBuiltIn.failedCount, 1)
        XCTAssertFalse(pesterBuiltIn.allPassed)
    }

    func testStructuredDataGridAndMarkdownPreviewRenderer() {
        let csvRaw = "Server,Role,Notes\n\"node-01\",\"Gateway\",\"Quotes \"\"OK\"\", comma\"\n\"node-02\",\"Worker\",\"Multi\nLine\""
        guard let csvTable = StructuredDataGridEngine.parseTable(from: csvRaw, language: .plainText, fileName: "nodes.csv") else {
            XCTFail("Expected CSV table")
            return
        }
        XCTAssertEqual(csvTable.headers, ["Server", "Role", "Notes"])
        XCTAssertEqual(csvTable.rows.count, 2)
        XCTAssertEqual(csvTable.rows[0][2], "Quotes \"OK\", comma")
        XCTAssertEqual(csvTable.rows[1][2], "Multi\nLine")

        let roundTripCsv = StructuredDataGridEngine.serializeTable(csvTable)
        let reparsedCsv = StructuredDataGridEngine.parseTable(from: roundTripCsv, language: .plainText, fileName: "nodes.csv")
        XCTAssertEqual(reparsedCsv?.rows, csvTable.rows)

        let jsonRaw = """
        [
          {"host": "db-02", "latency": 42, "active": true},
          {"host": "db-01", "latency": 9, "active": false}
        ]
        """
        guard let jsonTable = StructuredDataGridEngine.parseTable(from: jsonRaw, language: .json, fileName: "hosts.json") else {
            XCTFail("Expected JSON object array table")
            return
        }
        XCTAssertTrue(jsonTable.isJsonArray)
        XCTAssertEqual(jsonTable.headers, ["host", "latency", "active"])
        let sortedByLatency = StructuredDataGridEngine.sortTable(jsonTable, columnIndex: 1, ascending: true)
        XCTAssertEqual(sortedByLatency.rows.first?.first, "db-01")

        let filtered = StructuredDataGridEngine.filterRows(sortedByLatency, query: "db-02")
        XCTAssertEqual(filtered.count, 1)
        XCTAssertEqual(filtered[0].originalRowIndex, 1)

        let md = "# Release v1.5.0\n\n- [x] Ship Data Grid\n\n| Col A | Col B |\n| --- | --- |\n| 1 | 2 |\n\n```powershell\nGet-Date\n```"
        let html = MarkdownPreviewRenderer.renderHTML(from: md, language: .markdown, fileName: "CHANGELOG.md", isDarkTheme: true)
        XCTAssertTrue(html.contains("Content-Security-Policy"))
        XCTAssertTrue(html.contains("<h1>Release v1.5.0</h1>"))
        XCTAssertTrue(html.contains("<table>"))
        XCTAssertTrue(html.contains("type=\"checkbox\" checked"))
    }

    func testRemoteSFTPLsParserAndRemoteDocumentSerialization() throws {
        let lsOutput = """
        sftp> ls -la "/var/www"
        drwxr-xr-x    4 deploy   www-data      128 Nov 23 19:00 .
        drwxr-xr-x   12 root     root          384 Nov 20 09:00 ..
        drwxr-xr-x    3 deploy   www-data       96 Nov 23 19:01 scripts
        -rw-r--r--    1 deploy   www-data     2048 Nov 23 19:05 Invoke-Sync.ps1
        """
        let entries = RemoteSFTPEngine.parseSftpLsOutput(lsOutput, directoryPath: "/var/www/")
        XCTAssertEqual(entries.count, 2)
        XCTAssertTrue(entries[0].isDirectory)
        XCTAssertEqual(entries[0].fullPath, "/var/www/scripts")
        XCTAssertFalse(entries[1].isDirectory)
        XCTAssertEqual(entries[1].name, "Invoke-Sync.ps1")
        XCTAssertEqual(entries[1].sizeBytes, 2048)
        XCTAssertEqual(RemoteSFTPEngine.parentRemotePath(of: "/var/www/scripts"), "/var/www")

        let loc = RemoteFileLocation(host: "prod.example.com", port: 2222, username: "deploy", remotePath: "/var/www/Invoke-Sync.ps1")
        XCTAssertEqual(loc.displayBadge, "sftp://deploy@prod.example.com:2222/var/www/Invoke-Sync.ps1")

        var remoteDoc = EditorDocument(title: "Invoke-Sync.ps1", remoteLocation: loc, content: "Get-Date", savedContent: "Get-Date")
        XCTAssertFalse(remoteDoc.isDirty)
        remoteDoc.encoding = .utf8Bom
        XCTAssertTrue(remoteDoc.isDirty)

        let encoded = try JSONEncoder().encode(remoteDoc)
        let decoded = try JSONDecoder().decode(EditorDocument.self, from: encoded)
        XCTAssertEqual(decoded.remoteLocation, loc)
    }
}


