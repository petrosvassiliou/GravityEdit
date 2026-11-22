import AppKit
import GravityEditCore
import SwiftUI

public struct GravityEditMenuCommands: Commands {
    @ObservedObject var vm: WorkspaceViewModel
    @ObservedObject var updateManager: SparkleUpdateManager

    public init(vm: WorkspaceViewModel, updateManager: SparkleUpdateManager) {
        self.vm = vm
        self.updateManager = updateManager
    }

    public var body: some Commands {
        // 1. Application Menu: Check for Updates… right after About / Settings per Apple HIG
        CommandGroup(after: .appInfo) {
            Button(vm.tr(.checkForUpdates)) {
                updateManager.openSoftwareUpdateCenter()
            }
            .keyboardShortcut("u", modifiers: [.command, .option])
        }

        // 2. File Menu
        CommandGroup(replacing: .newItem) {
            Button(vm.tr(.newDocument)) {
                vm.showNewDocumentSheet = true
            }
            .keyboardShortcut("n", modifiers: [.command])

            Button("Quick New PowerShell Script") {
                vm.createNewDocument(language: .powershell)
            }
            .keyboardShortcut("n", modifiers: [.command, .shift])

            Divider()

            Button(vm.tr(.openFile)) {
                vm.openDocumentWithPanel()
            }
            .keyboardShortcut("o", modifiers: [.command])

            Button(vm.tr(.mountFolder)) {
                vm.mountProjectFolderWithPanel()
            }
            .keyboardShortcut("o", modifiers: [.command, .shift])
        }

        CommandGroup(replacing: .saveItem) {
            Button(vm.tr(.save)) {
                _ = vm.saveFocusedDocument()
            }
            .keyboardShortcut("s", modifiers: [.command])

            Button(vm.tr(.saveAs)) {
                _ = vm.saveFocusedDocumentAs()
            }
            .keyboardShortcut("s", modifiers: [.command, .shift])

            Divider()

            Button("Close Active Tab") {
                vm.closeDocument(id: vm.focusedDocumentId)
            }
            .keyboardShortcut("w", modifiers: [.command])
        }

        // 3. Edit Menu Code Actions
        CommandGroup(after: .textEditing) {
            Divider()
            Button("Duplicate Line") {
                vm.duplicateCurrentLine()
            }
            .keyboardShortcut("d", modifiers: [.command])

            Button("Delete Line") {
                vm.deleteCurrentLine()
            }
            .keyboardShortcut("k", modifiers: [.command, .control])

            Button("Select Current Line") {
                vm.selectCurrentLine()
            }
            .keyboardShortcut("l", modifiers: [.command])

            Divider()

            Button("Toggle Line Comment") {
                vm.toggleCommentOnSelection()
            }
            .keyboardShortcut("/", modifiers: [.command])

            Button("Indent Selection") {
                vm.indentSelection(outdent: false)
            }
            .keyboardShortcut("]", modifiers: [.command])

            Button("Outdent Selection") {
                vm.indentSelection(outdent: true)
            }
            .keyboardShortcut("[", modifiers: [.command])
        }

        // 4. Dedicated Search Menu
        CommandMenu("Search") {
            Button("Find & Replace…") {
                vm.findSheetInitialTab = .document
                vm.showFindReplaceSheet = true
            }
            .keyboardShortcut("f", modifiers: [.command])

            Button("Multi-File Search in Folder…") {
                vm.findSheetInitialTab = .directory
                vm.showFindReplaceSheet = true
            }
            .keyboardShortcut("f", modifiers: [.command, .shift])

            Button("Live Regex Pattern Playground…") {
                vm.findSheetInitialTab = .playground
                vm.showFindReplaceSheet = true
            }
            .keyboardShortcut("r", modifiers: [.command, .option])

            Divider()

            Button("Find Next") {
                vm.findNextMatch(forward: true)
            }
            .keyboardShortcut("g", modifiers: [.command])

            Button("Find Previous") {
                vm.findNextMatch(forward: false)
            }
            .keyboardShortcut("g", modifiers: [.command, .shift])

            Divider()

            Button("Go to Line…") {
                vm.showGoToLinePopover = true
            }
            .keyboardShortcut("l", modifiers: [.control])

            Button("Jump to Function / Outline") {
                vm.revealSidebar(section: .functionOutline)
            }
            .keyboardShortcut("j", modifiers: [.command])
        }

        // 5. Dedicated BBEdit "Text" Menu (All 14 BBEdit Operations + Base64/URL Utilities!)
        CommandMenu("Text") {
            Button("Open BBEdit Text Factory Pipeline…") {
                vm.showTextFactorySheet = true
            }
            .keyboardShortcut("t", modifiers: [.command, .shift])

            Divider()

            Menu("Change Case") {
                ForEach(CaseTransformMode.allCases, id: \.self) { mode in
                    Button(mode.rawValue) {
                        vm.applyQuickStepToSelectionOrDocument(
                            TextFactoryStep(operationType: .changeCase, caseMode: mode)
                        )
                    }
                }
            }

            Menu("Sort Lines") {
                Button("Sort Ascending (A → Z)") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .sortLines, sortDirection: .ascending))
                }
                Button("Sort Descending (Z → A)") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .sortLines, sortDirection: .descending))
                }
                Button("Sort Numerically") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .sortLines, sortDirection: .ascending, sortNumeric: true))
                }
                Button("Reverse Line Order") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .sortLines, sortDirection: .reverseOnly))
                }
            }

            Menu("Process Duplicate Lines") {
                ForEach(DuplicateLineMode.allCases, id: \.self) { mode in
                    Button(mode.rawValue) {
                        vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .processDuplicateLines, duplicateMode: mode))
                    }
                }
            }

            Menu("Prefix & Suffix Lines") {
                Button("Quote Lines with Single Quotes ('…')") {
                    vm.applyQuickStepToSelectionOrDocument(
                        TextFactoryStep(operationType: .prefixSuffixLines, prefixSuffixAction: .insert, linePrefix: "'", lineSuffix: "',")
                    )
                }
                Button("Bullet Lines (- …)") {
                    vm.applyQuickStepToSelectionOrDocument(
                        TextFactoryStep(operationType: .prefixSuffixLines, prefixSuffixAction: .insert, linePrefix: "- ", lineSuffix: "")
                    )
                }
                Divider()
                Button("Custom Prefix / Suffix in Text Factory…") {
                    vm.textFactorySteps = [TextFactoryStep(operationType: .prefixSuffixLines, enabled: true)]
                    vm.showTextFactorySheet = true
                }
            }

            Menu("Process Lines Containing…") {
                Button("Remove Blank / Whitespace-Only Lines") {
                    vm.applyQuickStepToSelectionOrDocument(
                        TextFactoryStep(operationType: .processLinesContaining, matchingLinesAction: .deleteMatching, matchingPattern: #"^\s*$"#, matchingUseRegex: true)
                    )
                }
                Button("Configure Pattern in Text Factory…") {
                    vm.textFactorySteps = [TextFactoryStep(operationType: .processLinesContaining, enabled: true)]
                    vm.showTextFactorySheet = true
                }
            }

            Button("Strip Trailing Whitespace") {
                vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .stripTrailingWhitespace))
            }

            Button("Zap Gremlins (Strip Null & Control Bytes)") {
                vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .zapGremlins))
            }

            Menu("Tabs & Indentation") {
                Button("Detab (Tabs → Spaces)") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .entabDetab, tabConversionMode: .detabTabsToSpaces, tabWidth: vm.tabWidth))
                }
                Button("Entab (Spaces → Tabs)") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .entabDetab, tabConversionMode: .entabSpacesToTabs, tabWidth: vm.tabWidth))
                }
            }

            Menu("Wrap & Line Numbers") {
                Button("Hard Wrap at Column 80") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .hardWrapUnwrap, wrapMode: .hardWrap, wrapColumnWidth: 80))
                }
                Button("Unwrap Paragraph Line Breaks") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .hardWrapUnwrap, wrapMode: .unwrapRemoveLineBreaks))
                }
                Divider()
                Button("Add Zero-Padded Line Numbers") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .addRemoveLineNumbers, lineNumberAction: .addNumbers))
                }
                Button("Remove Existing Line Numbers") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .addRemoveLineNumbers, lineNumberAction: .removeNumbers))
                }
            }

            Menu("Quotes & Canonize") {
                Button("Straighten Curly Quotes (“ ” → \")") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .educateStraightenQuotes, quoteMode: .straightenQuotes))
                }
                Button("Educate Curly Quotes (\" → “ ”)") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .educateStraightenQuotes, quoteMode: .educateCurlyQuotes))
                }
                Divider()
                Button("Canonize PowerShell Shorthand Aliases") {
                    vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .canonizeReplaceTable))
                }
            }

            Menu("Convert Line Endings") {
                ForEach(LineEnding.allCases) { le in
                    Button(le.displayName) {
                        vm.applyQuickStepToSelectionOrDocument(
                            TextFactoryStep(operationType: .convertLineEndings, targetLineEnding: le)
                        )
                    }
                }
            }

            Menu("Encode / Decode") {
                Button("Base64 Encode") {
                    vm.applyEncodingUtility(TextTransformEngine.base64Encode, label: "Base64 Encoded")
                }
                Button("Base64 Decode") {
                    vm.applyEncodingUtility(TextTransformEngine.base64Decode, label: "Base64 Decoded", validateBase64: true)
                }
                Divider()
                Button("URL Percent-Encode") {
                    vm.applyEncodingUtility(TextTransformEngine.urlEncode, label: "URL Encoded")
                }
                Button("URL Percent-Decode") {
                    vm.applyEncodingUtility(TextTransformEngine.urlDecode, label: "URL Decoded")
                }
            }

            Divider()

            Button("Format / Prettify Document") {
                vm.applyQuickStepToSelectionOrDocument(TextFactoryStep(operationType: .formatPrettify))
            }
            .keyboardShortcut("f", modifiers: [.control, .option])
        }

        // 6. View Menu Additions
        CommandGroup(after: .toolbar) {
            Toggle(vm.tr(.softWrap), isOn: $vm.softWrap)
                .keyboardShortcut("z", modifiers: [.option])
            Toggle(vm.tr(.showInvisibles), isOn: $vm.showInvisibles)
            Toggle(vm.tr(.lineNumbers), isOn: $vm.showLineNumbers)
            Toggle(vm.tr(.pageGuide), isOn: $vm.showPageGuides)
            Toggle("Highlight Current Cursor Line", isOn: $vm.highlightCurrentLine)

            Divider()

            Button("Single Editor Pane") { vm.splitMode = .none; vm.refreshActiveAnalysisImmediate() }
                .keyboardShortcut("1", modifiers: [.command, .option])
            Button("Side-by-Side Vertical Split") { vm.splitMode = .vertical; vm.refreshActiveAnalysisImmediate() }
                .keyboardShortcut("2", modifiers: [.command, .option])
            Button("Top-and-Bottom Horizontal Split") { vm.splitMode = .horizontal; vm.refreshActiveAnalysisImmediate() }
                .keyboardShortcut("3", modifiers: [.command, .option])

            Divider()

            Button("Fold All Regions") { vm.foldAllInFocusedDocument() }
                .keyboardShortcut("[", modifiers: [.command, .option])
            Button("Unfold All Regions") { vm.unfoldAllInFocusedDocument() }
                .keyboardShortcut("]", modifiers: [.command, .option])

            Divider()

            Menu("Syntax & File Type") {
                let currentLang = vm.focusedDocument?.language
                ForEach(SyntaxLanguage.allCases) { lang in
                    Button {
                        vm.setFocusedLanguage(lang)
                    } label: {
                        HStack {
                            Text("\(lang.displayName) (.\(lang.primaryExtension))")
                            if lang == currentLang { Image(systemName: "checkmark") }
                        }
                    }
                }
            }

            Divider()

            Button("Zoom In") { vm.zoomIn() }
                .keyboardShortcut("=", modifiers: [.command])
            Button("Zoom Out") { vm.zoomOut() }
                .keyboardShortcut("-", modifiers: [.command])
            Button("Actual Size (100%)") { vm.zoomReset() }
                .keyboardShortcut("0", modifiers: [.command])
        }

        // 7. Tools Menu
        CommandMenu("Tools") {
            Button("Command Palette…") {
                vm.showCommandPalette.toggle()
            }
            .keyboardShortcut("p", modifiers: [.command, .shift])

            Button("Compare Documents (Diff)…") {
                vm.openDiffTool()
            }
            .keyboardShortcut("d", modifiers: [.command, .control])

            Button("Clippings & Snippets…") {
                vm.showClippingsSheet = true
            }
            .keyboardShortcut("c", modifiers: [.command, .control])

            Divider()

            Button("PSScriptAnalyzer Diagnostics…") {
                vm.showDiagnosticsSheet = true
            }

            Button("Apply All PowerShell Quick Fixes") {
                vm.applyAllQuickFixes()
            }
            .keyboardShortcut("f", modifiers: [.option, .shift])

            Divider()

            Button("Switch to Persistent Scratchpad") {
                vm.switchToScratchpad()
            }
            .keyboardShortcut("k", modifiers: [.command, .shift])
        }

        // 8. Help Menu
        CommandGroup(replacing: .help) {
            Button("GravityEdit GitHub Repository") {
                if let url = URL(string: "https://github.com/petrosvassiliou/GravityEdit") {
                    NSWorkspace.shared.open(url)
                }
            }
            Button("Check for Software Updates…") {
                updateManager.openSoftwareUpdateCenter()
            }
        }
    }
}
