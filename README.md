<div align="center">

# GravityEdit

**The 1:1 Functional Equivalent of BBEdit — Engineered Natively for Googlebook OS & Android with First-Class PowerShell Tooling**

[![Latest Release](https://img.shields.io/github/v/release/petrosvassiliou/GravityEdit?style=for-the-badge&color=004494&label=Latest%20Release)](https://github.com/petrosvassiliou/GravityEdit/releases/latest)
[![Android CI](https://img.shields.io/github/actions/workflow/status/petrosvassiliou/GravityEdit/android-ci.yml?branch=main&style=for-the-badge&label=Android%20CI)](https://github.com/petrosvassiliou/GravityEdit/actions/workflows/android-ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Play Protect](https://img.shields.io/badge/Play%20Protect-Policy%20Compliant-00C853?style=for-the-badge&logo=googleplay&logoColor=white)](#google-play-protect-compatible-auto-updates)

<p align="center">
  <a href="https://github.com/petrosvassiliou/GravityEdit/releases/latest"><strong>⬇ Download Latest APK (v1.1.1)</strong></a>
  &nbsp;•&nbsp;
  <a href="#screenshots">Screenshots</a>
  &nbsp;•&nbsp;
  <a href="#key-features">Key Features</a>
  &nbsp;•&nbsp;
  <a href="#keyboard-shortcuts">Shortcuts</a>
  &nbsp;•&nbsp;
  <a href="#building-from-source">Build from Source</a>
</p>

</div>

---

## Overview

**GravityEdit** (`com.gravityedit.app`) brings the rock-solid text engineering philosophy of **BBEdit for macOS** ("It doesn't suck" — zero bloat, instant startup, industrial regex, deterministic text pipelines) to **Googlebook OS** (Desktop/Laptop freeform windowing), **Android Tablets**, and **Android Phones**, paired with a **native PowerShell AST, syntax, completion, and `PSScriptAnalyzer`-grade diagnostic engine**.

Built from the ground up in **100% Kotlin** and **Jetpack Compose (Material Design 3)** with zero WebView wrappers, zero telemetry, and full offline capability.

---

## Screenshots

### Googlebook OS / Tablet Desktop Workbench
Full desktop layout featuring the `NavigationRail`, drag-resizable **Open Documents & Disk Browser** sidebar, real-time **AST Function Navigator**, multi-document tab bar with file-type chooser (`+`), **80-column page guide**, code folding gutter, **PowerShell Inline Completion**, and dense **Status Bar**.

![GravityEdit Desktop PowerShell Workbench](docs/screenshots/01-desktop-powershell-workbench.png)

---

### Adaptive Mobile Mode (AMOLED `#000000`) & File Type Selector

<table>
  <tr>
    <td width="34%" valign="top">
      <h4 align="center">Android Mobile AMOLED Mode</h4>
      <p align="center">
        <img src="docs/screenshots/02-android-mobile-amoled.png" alt="GravityEdit Android Mobile AMOLED Mode" width="100%" />
      </p>
      <p>
        True <code>#000000</code> Pitch-Black OLED theme, scrollable tabs with trailing <code>+</code> button, compact AST jump menu, and a <strong>Sticky PowerShell Auxiliary Symbol Bar</strong> (<code>$</code>, <code>|</code>, <code>{</code>, <code>}</code>, <code>[</code>, <code>]</code>, <code>-eq</code>, <code>$_</code>, <code>@{}</code>) docked above the soft keyboard.
      </p>
    </td>
    <td width="66%" valign="top">
      <h4 align="center">New File Dialog &amp; Click-to-Select Rename</h4>
      <p align="center">
        <img src="docs/screenshots/03-new-document-filetype-selector.png" alt="GravityEdit New Document File Type Selector" width="100%" />
      </p>
      <p>
        Clicking any <code>+</code> button (Top Toolbar, Desktop/Mobile Tab Bar, Sidebar, or <code>Ctrl+N</code>) opens the <strong>New File</strong> selector so you can pick Plain Text (<code>.txt</code>), PowerShell (<code>.ps1</code> / <code>.psm1</code> / <code>.psd1</code>), Markdown, JSON, YAML, XML, Kotlin, Python, Shell, HTML, or CSS, plus encoding, line endings, and optional starter templates. Clicking an active tab title or renaming auto-selects the filename stem while keeping the extension intact.
      </p>
    </td>
  </tr>
</table>

---

### Side-by-Side Myers Diff & 14-Step BBEdit Text Factory
Line- and character-level **Myers Diff** with directional `Copy to Left (←)` / `Copy to Right (→)` merges alongside chainable **BBEdit Text Factory** pipelines.

![Side-by-Side Myers Diff and BBEdit Text Factory](docs/screenshots/04-bbedit-text-factory-and-diff.png)

---

### Live `PSScriptAnalyzer` Diagnostics & Play Protect Auto-Update Center

<table>
  <tr>
    <td width="50%" valign="top">
      <h4 align="center">Live PSScriptAnalyzer Linter &amp; Quick Fixes</h4>
      <p align="center">
        <img src="docs/screenshots/05-psscriptanalyzer-diagnostics.png" alt="PSScriptAnalyzer Live Diagnostics" width="100%" />
      </p>
      <p>
        Real-time PowerShell static analysis catching unapproved verbs, interactive shell aliases (<code>gci</code>, <code>where</code>, <code>%</code>), plaintext credentials, and empty <code>catch {}</code> blocks — with one-click <strong>Quick Fixes</strong> and <strong>Fix All</strong>.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4 align="center">Google Play Protect–Verified Auto-Update</h4>
      <p align="center">
        <img src="docs/screenshots/06-play-protect-auto-update.png" alt="GravityEdit Play Protect Auto-Update Center" width="100%" />
      </p>
      <p>
        Dual-channel updater that routes Play Store installs to Google Play In-App Updates and verifies GitHub Release APKs via TLS 1.3 + SHA-256 digest before handing off to Android's official <code>PackageInstaller</code>.
      </p>
    </td>
  </tr>
</table>

---

## Key Features

### 1. Adaptive Multi-Form-Factor UI (`WindowSizeClass`)
- **Googlebook OS & Tablet Desktop Mode (`Expanded` / `Medium`):**
  - Persistent `NavigationRail` + drag-resizable **Open Documents & Disk Browser** tree sidebar.
  - Multi-document tab bar with modified-state indicator dots, click-to-rename active tab titles, context menus, and `+` New File selector.
  - **Single**, **Vertical Split (`◫`)**, and **Horizontal Split (`⊟`)** editing panes.
  - Dense **Status Bar** displaying cursor `Ln:Col`, live `PSScriptAnalyzer` health pill, Command Palette trigger, update badge, character/line counts, and interactive **Encoding**, **Line Ending**, **Syntax Language**, and **Zoom** pickers.
- **Android Phone Mode (`Compact`):**
  - `ModalNavigationDrawer`, `ScrollableTabRow` with `+` button, `ModalBottomSheet` tool surfaces, and the **Sticky PowerShell Auxiliary Symbol Row** above the keyboard.
- **4 Built-in Color Themes:** Dark Developer (Default), **AMOLED Pitch Black (`#000000`)**, Solarized Light, and High-Contrast Developer.

### 2. First-Class PowerShell Engine (`PowerShellEngine.kt` & `PowerShellDiagnosticsEngine.kt`)
- **Rich PowerShell Tokenization:** Approved-verb dictionary & alias highlighting (`Verb-Noun`), scoped/drive/automatic variable distinction (`$myVar`, `$script:x`, `$env:PATH`, `$_`, `$PSItem`, `$true`), cmdlet parameters (`-Path`, `-ErrorAction`), operators (`-eq`, `-match`, `-replace`), expandable string interpolation (`"$var"`, `"$(Get-Date)"`), Here-Strings (`@"..."@`), and Comment-Based Help tags (`.SYNOPSIS`, `.PARAMETER`, `.EXAMPLE`).
- **Code Folding & AST Function Navigator:** Fold `function`, `filter`, `workflow`, `class`, `#region ... #endregion`, and `<# ... #>` blocks; jump directly to any symbol from the top bar.
- **Inline Auto-Completion (`PowerShellCompletionEngine.kt`):** Context-aware completions for built-in PowerShell cmdlets, approved verbs, automatic variables, and functions/variables declared in the active script (`Tab` / `Enter` to accept).
- **Live `PSScriptAnalyzer`-Style Linter:** Detects `PSAvoidUsingCmdletAliases`, `PSUseApprovedVerbs`, `PSAvoidEmptyCatchBlock`, `PSAvoidUsingPlainTextForPassword`, and `PSAvoidUsingInvokeExpression` with instant one-click Quick Fixes.

### 3. 1:1 BBEdit Feature Parity
- **Industrial PCRE Find & Replace (`FindReplaceEngine.kt`):** Multi-line regex, case sensitivity, whole-word matching, named capture groups (`(?<name>...)`), backreferences (`$1`, `\1`, `\n`, `\t`), **Extract Matches to New Document**, interactive **Pattern Playground**, and **Multi-File Directory Search & Replace**.
- **BBEdit Text Factory (`TextTransformEngine.kt`):** Chain any combination of all **14 classic BBEdit text transformations** into reusable batch pipelines:
  1. Change Case (`UPPER`, `lower`, `Title Case`, `Sentence case`, `camelCase`, `snake_case`)
  2. Sort Lines (Ascending / Descending, Case-Sensitive, Numeric-Aware)
  3. Process Duplicate Lines (Deduplicate, Keep Only Duplicates, Keep Unique Only)
  4. Prefix / Suffix Lines
  5. Strip Trailing Whitespace
  6. Zap Gremlins (Remove non-printable ASCII, zero-width spaces, or null bytes)
  7. Entab / Detab (Convert spaces ↔ tabs at configurable tab stops)
  8. Hard Wrap Text (Wrap at configurable column width, e.g., 80 / 100 / 120)
  9. Remove Line Breaks (Unwrap paragraphs)
  10. Add / Remove Line Numbers
  11. Process Lines Containing… (Extract, Delete, or Copy matching lines via Regex/Literal)
  12. Straighten / Educate Quotes (Smart typography ↔ straight ASCII quotes)
  13. Base64 & URL Encode / Decode
  14. Format / Prettify JSON, XML & PowerShell
- **Side-by-Side Myers Diff (`DiffEngine.kt`):** Compare any two open documents with character-level change highlights and one-click directional merges (`←` / `→`).
- **Clippings & Snippets (`ClippingsManager.kt`):** Expand dynamic placeholders (`#DATE#`, `#TIME#`, `#FILENAME#`, `#SELECTION#`, `#INSERTION#`) with a pre-loaded PowerShell snippet library.
- **Persistent Scratchpad & Crash-Safe Auto-Save:** Instant scratch buffer and automatic session recovery across app restarts.

### 4. Google Play Protect–Compatible Auto-Updates
Implemented in [`AppUpdateManagerEngine.kt`](app/src/main/java/com/gravityedit/app/domain/engine/AppUpdateManagerEngine.kt) in strict compliance with Google Play Developer Policy & Google Play Protect:
- **Source-Aware Routing:** Automatically inspects `PackageManager.getInstallSourceInfo()` — installs originating from Google Play (`com.android.vending`) never download external APKs and route strictly through Google Play.
- **Cryptographic Verification:** Direct GitHub installs check `releases/latest` over **HTTPS (TLS 1.3)**, stream the APK solely into a private `cacheDir/app_updates/` subdirectory, and validate both the APK ZIP magic header and the **SHA-256 cryptographic digest** against the published `.sha256` release asset.
- **User-Consented System Handoff:** Hands off installation exclusively to Android's native `PackageInstaller` (`ACTION_VIEW` + scoped `FileProvider` URI) so Google Play Protect scans every update normally — zero silent installs, zero dynamic Dex/code loading.

### 5. 6-Language Runtime Localization
100% string parity with instant runtime switching via `AppCompatDelegate.setApplicationLocales()`:
- English (`en`) • Deutsch (`de`) • Italiano (`it`) • Español (`es`) • Français (`fr`) • Ελληνικά (`el`)

---

## Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| `Ctrl + P` / `Ctrl + Shift + P` | Open Command Palette |
| `Ctrl + N` | New File (Open File Type, Encoding & Line Ending Selector) |
| `Ctrl + O` | Open File from Disk / SAF |
| `Ctrl + S` / `Ctrl + Shift + S` | Save / Save As |
| `Ctrl + W` | Close Active Document |
| `Ctrl + F` | Find & Replace (PCRE Regex + Pattern Playground) |
| `Ctrl + Shift + F` | Multi-File Directory Search & Replace |
| `Ctrl + G` | Go to Line (`Ln:Col`) |
| `Ctrl + J` | Jump to PowerShell Function / Filter / Class |
| `Ctrl + D` | Duplicate Current Line |
| `Ctrl + L` | Select Entire Line |
| `Ctrl + /` | Toggle Line Comment (`#` / `//`) |
| `Ctrl + +` / `Ctrl + -` / `Ctrl + 0` | Zoom In / Zoom Out / Reset Editor Zoom (`100%`) |

---

## Architecture & Project Structure

```text
app/src/main/java/com/gravityedit/app/
├── data/
│   ├── preferences/     # DataStore persistence for editor, theme, locale & auto-update settings
│   └── repository/      # Crash-safe session, scratchpad & SAF file I/O repositories
├── domain/
│   ├── engine/
│   │   ├── AppUpdateManagerEngine.kt      # Play Protect-compliant HTTPS + SHA-256 updater
│   │   ├── ClippingsManager.kt            # BBEdit Clippings & dynamic token expansion
│   │   ├── DiffEngine.kt                  # Myers line + character diff & directional merge
│   │   ├── FindReplaceEngine.kt           # PCRE Regex Find/Replace, Extract & Playground
│   │   ├── PowerShellCompletionEngine.kt  # AST + built-in cmdlet inline completion
│   │   ├── PowerShellDiagnosticsEngine.kt # Live PSScriptAnalyzer-grade linter & Quick Fixes
│   │   ├── PowerShellEngine.kt            # PowerShell tokenizer, folder & AST navigator
│   │   └── TextTransformEngine.kt         # All 14 BBEdit Text Factory transformations
│   └── model/           # Immutable state models (EditorDocument, SyntaxLanguage, etc.)
└── ui/
    ├── components/      # CodeEditorCanvas, MultiDocumentTabBar, CommandPalette, Modals
    ├── theme/           # Material 3 color schemes & syntax palettes (incl. AMOLED #000000)
    ├── GravityEditScaffold.kt
    └── GravityEditViewModel.kt
```

---

## Installation

1. Download **`GravityEdit-v1.1.1.apk`** from [**GitHub Releases**](https://github.com/petrosvassiliou/GravityEdit/releases/latest).
2. *(Optional)* Verify integrity against `GravityEdit-v1.1.1.apk.sha256`:
   ```bash
   sha256sum -c GravityEdit-v1.1.1.apk.sha256
   ```
3. Open the APK on your **Android 8.0+ (API 26+)** phone, tablet, or **Googlebook OS** device. Future releases can be checked and verified directly inside **Settings → GravityEdit Auto-Update Center**.

---

## Building from Source

### Prerequisites
- **JDK 17+**
- **Android SDK 35** (`compileSdk = 35`, `minSdk = 26`)

### Run Unit Tests & Assemble APK
```bash
# Run the full JUnit domain engine test suite
./gradlew testDebugUnitTest

# Build Debug & Release APKs
./gradlew assembleDebug assembleRelease
```

---

## License

Released under the MIT License. Built with ❤️ in Kotlin & Jetpack Compose.
