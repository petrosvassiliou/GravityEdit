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
            savedEncoding: .utf8,
            lineEnding: .lf,
            savedLineEnding: .lf
        )
        XCTAssertFalse(doc.isDirty)
        doc.lineEnding = .crlf
        XCTAssertTrue(doc.isDirty)
        doc.lineEnding = .lf
        doc.encoding = .utf8BOM
        XCTAssertTrue(doc.isDirty)

        let swapScript = MacAutoUpdateEngine.buildAtomicBundleSwapScript(
            processIdentifier: 4242,
            stagedAppBundleURL: URL(fileURLWithPath: "/tmp/Staged/GravityEdit.app"),
            destinationAppBundleURL: URL(fileURLWithPath: "/Applications/GravityEdit.app")
        )
        XCTAssertTrue(swapScript.contains("if kill -0 \"$TARGET_PID\" 2>/dev/null; then"))
        XCTAssertTrue(swapScript.contains("exit 0"))
    }
}

