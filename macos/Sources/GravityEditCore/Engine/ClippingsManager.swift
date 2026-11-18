import Foundation

public enum ClippingsManager {

    public static let placeholderInsertion = "#INSERTION#"
    public static let placeholderSelStart = "#SELSTART#"
    public static let placeholderSelEnd = "#SELEND#"
    public static let placeholderSelection = "#SELECTION#"
    public static let placeholderClipboard = "#CLIPBOARD#"
    public static let placeholderDate = "#DATE#"
    public static let placeholderTime = "#TIME#"
    public static let placeholderFilename = "#FILENAME#"
    public static let placeholderUsername = "#USERNAME#"
    public static let placeholderUserShort = "#USER#"

    public static let builtInClippings: [Clipping] = [
        Clipping(
            id: "ps_cmdlet_binding",
            name: "Advanced Function ([CmdletBinding()])",
            triggerPrefix: "func-adv",
            category: .powershellFunctions,
            description: "Full PowerShell advanced function with ShouldProcess, Parameter attributes, and Begin/Process/End blocks",
            templateBody: """
            function #SELSTART#Invoke-CustomAction#SELEND# {
                [CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
                [OutputType([pscustomobject])]
                param (
                    [Parameter(Mandatory = $true, ValueFromPipeline = $true, ValueFromPipelineByPropertyName = $true, Position = 0)]
                    [ValidateNotNullOrEmpty()]
                    [string[]]$Path,

                    [Parameter(Mandatory = $false)]
                    [switch]$Force
                )

                begin {
                    Write-Verbose "Starting $($MyInvocation.MyCommand.Name) on #DATE#"
                }

                process {
                    foreach ($item in $Path) {
                        if ($PSCmdlet.ShouldProcess($item, 'Process target item')) {
                            #SELECTION##INSERTION#
                        }
                    }
                }

                end {
                    Write-Verbose "Completed $($MyInvocation.MyCommand.Name)"
                }
            }
            """
        ),
        Clipping(
            id: "ps_comment_based_help",
            name: "Comment-Based Help Block (<# .SYNOPSIS #>)",
            triggerPrefix: "help-block",
            category: .powershellHelpDocs,
            description: "Complete PowerShell Comment-Based Help header with .SYNOPSIS, .DESCRIPTION, .PARAMETER, .EXAMPLE, and .NOTES",
            templateBody: """
            <#
            .SYNOPSIS
                #SELSTART#Brief synopsis of what #FILENAME# does.#SELEND#

            .DESCRIPTION
                Detailed description of the function or script behavior, inputs, and operational notes.

            .PARAMETER Path
                Specifies the target file or directory path to process.

            .EXAMPLE
                PS> ./Invoke-Script.ps1 -Path './Logs' -Verbose
                Demonstrates standard execution with verbose output enabled.

            .NOTES
                File Name  : #FILENAME#
                Author     : #USERNAME#
                Created On : #DATE# #TIME#
            #>
            #INSERTION#
            """
        ),
        Clipping(
            id: "ps_try_catch_finally",
            name: "Try / Catch / Finally (Typed Exception)",
            triggerPrefix: "try-catch",
            category: .powershellFlow,
            description: "Structured error handling block with ErrorAction Stop, typed catch, and finally cleanup",
            templateBody: """
            try {
                #SELECTION##INSERTION#
            }
            catch [System.IO.IOException] {
                Write-Error -Message "I/O failure in #FILENAME#: $($_.Exception.Message)" -ErrorAction Continue
            }
            catch {
                Write-Error -Message "Unhandled error at line $($_.InvocationInfo.ScriptLineNumber): $($_.Exception.Message)"
                throw
            }
            finally {
                Write-Verbose "Cleanup completed at #TIME#"
            }
            """
        ),
        Clipping(
            id: "ps_foreach_collection",
            name: "ForEach Collection Loop",
            triggerPrefix: "foreach",
            category: .powershellFlow,
            description: "Standard PowerShell foreach loop with pscustomobject output",
            templateBody: """
            foreach ($#SELSTART#item#SELEND# in $collection) {
                [pscustomobject]@{
                    Name      = $item.Name
                    Timestamp = '#DATE# #TIME#'
                }
                #SELECTION##INSERTION#
            }
            """
        ),
        Clipping(
            id: "ps_switch_regex",
            name: "Switch -Regex Statement",
            triggerPrefix: "switch-regex",
            category: .powershellFlow,
            description: "PowerShell switch block using -Regex pattern matching and $Matches capture inspection",
            templateBody: """
            switch -Regex (#SELSTART#$inputValue#SELEND#) {
                '^ERROR:\\s*(?<msg>.+)$' {
                    Write-Warning "Matched error: $($Matches['msg'])"
                    #INSERTION#
                    break
                }
                '^\\d{4}-\\d{2}-\\d{2}' {
                    Write-Verbose "Matched ISO date line"
                    break
                }
                default {
                    #SELECTION#
                }
            }
            """
        ),
        Clipping(
            id: "ps_invoke_restmethod",
            name: "Invoke-RestMethod JSON API Request",
            triggerPrefix: "irm-json",
            category: .powershellRemotingApi,
            description: "REST API request using Invoke-RestMethod with Bearer token header, JSON payload, and timeout",
            templateBody: """
            $headers = @{
                'Accept'        = 'application/json'
                'Authorization' = "Bearer $env:API_TOKEN"
            }

            $payload = [ordered]@{
                source    = '#FILENAME#'
                requested = '#DATE#T#TIME#'
                data      = '#SELECTION#'
            } | ConvertTo-Json -Depth 5

            $response = Invoke-RestMethod -Uri '#SELSTART#https://api.example.com/v1/resource#SELEND#' `
                -Method Post `
                -Headers $headers `
                -Body $payload `
                -ContentType 'application/json; charset=utf-8' `
                -TimeoutSec 30 `
                -ErrorAction Stop

            $response#INSERTION#
            """
        ),
        Clipping(
            id: "ps_pester_describe",
            name: "Pester v5 Describe / Context / It Test Block",
            triggerPrefix: "pester-test",
            category: .powershellPester,
            description: "Pester v5 unit test suite with BeforeAll, Describe, Context, and Should assertions",
            templateBody: """
            BeforeAll {
                # . $PSCommandPath.Replace('.Tests.ps1', '.ps1')
            }

            Describe '#SELSTART#Function-UnderTest#SELEND#' -Tag 'Unit' {
                Context 'When valid parameters are provided' {
                    BeforeEach {
                        $testPath = Join-Path -Path $TestDrive -ChildPath 'sample.txt'
                        Set-Content -Path $testPath -Value '#SELECTION#'
                    }

                    It 'Returns expected output without throwing' {
                        { Get-Item -Path $testPath -ErrorAction Stop } | Should -Not -Throw
                        $testPath | Should -Exist
                        #INSERTION#
                    }
                }
            }
            """
        ),
        Clipping(
            id: "ps_region_block",
            name: "#region / #endregion Foldable Section",
            triggerPrefix: "region",
            category: .powershellHelpDocs,
            description: "Foldable PowerShell #region block wrapping the current selection",
            templateBody: """
            #region #SELSTART#Section Name#SELEND#
            #SELECTION##INSERTION#
            #endregion
            """
        ),
        Clipping(
            id: "ps_class_definition",
            name: "PowerShell Class with Constructor & Method",
            triggerPrefix: "class-ps",
            category: .powershellFunctions,
            description: "PowerShell 5/7+ class definition with typed properties, constructor, and method",
            templateBody: """
            class #SELSTART#GravityResource#SELEND# {
                [string]$Name
                [datetime]$CreatedAt
                hidden [string]$InternalId

                GravityResource([string]$name) {
                    $this.Name = $name
                    $this.CreatedAt = [datetime]::UtcNow
                    $this.InternalId = [guid]::NewGuid().ToString()
                }

                [string] ToSummary() {
                    return "[$($this.Name)] initialized at $($this.CreatedAt.ToString('O'))"
                }
            }
            #INSERTION#
            """
        ),
        Clipping(
            id: "ps_pwsh_macos_shebang",
            name: "macOS PowerShell Core (pwsh) Executable Header",
            triggerPrefix: "pwsh-mac",
            category: .macosAutomation,
            description: "Cross-platform #!/usr/bin/env pwsh header with StrictMode and $IsMacOS check",
            templateBody: """
            #!/usr/bin/env pwsh
            #Requires -Version 7.0
            Set-StrictMode -Version Latest
            $ErrorActionPreference = 'Stop'

            if (-not $IsMacOS) {
                Write-Warning "Running #FILENAME# outside macOS ($PSVersionTable.OS)"
            }

            #SELECTION##INSERTION#
            """
        )
    ]

    // MARK: - Placeholder Expansion

    public static func expandTemplate(
        _ templateBody: String,
        context: ClippingExpansionContext,
        nowDate: String? = nil,
        nowTime: String? = nil
    ) -> ExpandedClippingResult {
        let resolvedDate = nowDate ?? formattedDate()
        let resolvedTime = nowTime ?? formattedTime()

        let normalizedTemplate: String
        if !context.baseIndentation.isEmpty {
            let lines = templateBody.components(separatedBy: "\n")
            normalizedTemplate = lines.enumerated().map { idx, line in
                (idx == 0 || line.trimmingCharacters(in: .whitespaces).isEmpty) ? line : (context.baseIndentation + line)
            }.joined(separator: "\n")
        } else {
            normalizedTemplate = templateBody
        }

        let stageOne = normalizedTemplate
            .replacingOccurrences(of: placeholderSelection, with: context.selectedText)
            .replacingOccurrences(of: placeholderClipboard, with: context.clipboardText)
            .replacingOccurrences(of: placeholderDate, with: resolvedDate)
            .replacingOccurrences(of: placeholderTime, with: resolvedTime)
            .replacingOccurrences(of: placeholderFilename, with: context.fileName)
            .replacingOccurrences(of: placeholderUsername, with: context.userName)
            .replacingOccurrences(of: placeholderUserShort, with: context.userName)

        let ns = stageOne as NSString
        let len = ns.length
        let selStartTok = placeholderSelStart as NSString
        let selEndTok = placeholderSelEnd as NSString
        let insTok = placeholderInsertion as NSString

        let out = NSMutableString(capacity: len)
        var selStartUTF16: Int? = nil
        var selEndUTF16: Int? = nil
        var insertionUTF16: Int? = nil

        var i = 0
        while i < len {
            let rem = len - i
            if rem >= selStartTok.length && ns.substring(with: NSRange(location: i, length: selStartTok.length)) == placeholderSelStart {
                selStartUTF16 = out.length
                i += selStartTok.length
            } else if rem >= selEndTok.length && ns.substring(with: NSRange(location: i, length: selEndTok.length)) == placeholderSelEnd {
                selEndUTF16 = out.length
                i += selEndTok.length
            } else if rem >= insTok.length && ns.substring(with: NSRange(location: i, length: insTok.length)) == placeholderInsertion {
                insertionUTF16 = out.length
                i += insTok.length
            } else {
                out.append(ns.substring(with: NSRange(location: i, length: 1)))
                i += 1
            }
        }

        let finalStr = out as String
        let finalLen = out.length

        let resolvedRange: NSRange?
        if let s = selStartUTF16, let e = selEndUTF16, e >= s {
            resolvedRange = NSRange(location: s, length: e - s)
        } else {
            resolvedRange = nil
        }

        let cursorOffset: Int
        if let r = resolvedRange {
            cursorOffset = r.location + r.length
        } else if let ins = insertionUTF16 {
            cursorOffset = ins
        } else if let s = selStartUTF16 {
            cursorOffset = s
        } else {
            cursorOffset = finalLen
        }

        return ExpandedClippingResult(
            textToInsert: finalStr,
            cursorRelativeUTF16Offset: min(max(0, cursorOffset), finalLen),
            selectionRelativeUTF16Range: resolvedRange
        )
    }

    public static func applyClippingToDocument(
        _ document: EditorDocument,
        clipping: Clipping,
        clipboardText: String = "",
        userName: String = NSFullUserName().isEmpty ? NSUserName() : NSFullUserName()
    ) -> EditorDocument {
        let selStart = document.safeSelectionStart
        let selEnd = document.safeSelectionEnd
        let selectedText = document.selectedText

        let lineIdx = document.lineIndexForUTF16Offset(selStart)
        let docLines = document.lines
        let currentLine = (lineIdx >= 0 && lineIdx < docLines.count) ? docLines[lineIdx] : ""
        let baseIndent = String(currentLine.prefix(while: { $0 == " " || $0 == "\t" }))

        let expanded = expandTemplate(
            clipping.templateBody,
            context: ClippingExpansionContext(
                selectedText: selectedText,
                clipboardText: clipboardText,
                fileName: document.title,
                userName: userName,
                baseIndentation: baseIndent
            )
        )

        let nsContent = document.content as NSString
        let updatedContent = nsContent.replacingCharacters(
            in: NSRange(location: selStart, length: max(0, selEnd - selStart)),
            with: expanded.textToInsert
        )
        let updatedLen = (updatedContent as NSString).length

        var updatedDoc = document
        updatedDoc.content = updatedContent
        if let relRange = expanded.selectionRelativeUTF16Range {
            let s = min(max(0, selStart + relRange.location), updatedLen)
            let e = min(max(s, s + relRange.length), updatedLen)
            updatedDoc.selectionStart = s
            updatedDoc.selectionEnd = e
        } else {
            let c = min(max(0, selStart + expanded.cursorRelativeUTF16Offset), updatedLen)
            updatedDoc.selectionStart = c
            updatedDoc.selectionEnd = c
        }
        return updatedDoc
    }

    public static func filterClippings(
        _ allClippings: [Clipping],
        searchQuery: String,
        languageFilter: SyntaxLanguage? = nil
    ) -> [Clipping] {
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return allClippings.filter { c in
            let matchesLang = (languageFilter == nil) || (c.syntaxLanguage == languageFilter)
            let matchesQuery = q.isEmpty ||
                c.name.lowercased().contains(q) ||
                c.triggerPrefix.lowercased().contains(q) ||
                c.description.lowercased().contains(q) ||
                c.category.displayName.lowercased().contains(q)
            return matchesLang && matchesQuery
        }
    }

    private static func formattedDate() -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: Date())
    }

    private static func formattedTime() -> String {
        let tf = DateFormatter()
        tf.locale = Locale(identifier: "en_US_POSIX")
        tf.dateFormat = "HH:mm:ss"
        return tf.string(from: Date())
    }
}
