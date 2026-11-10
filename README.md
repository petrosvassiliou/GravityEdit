# GravityEdit (`com.gravityedit.app`)

**GravityEdit** is a 1:1 functional equivalent of BBEdit for macOS, engineered natively in **100% Kotlin with Jetpack Compose (Material Design 3)** for **Googlebook OS** (Desktop/Laptop freeform windowing) and **Android** (Phones & Tablets).

## Highlights

- **Adaptive Multi-Form-Factor UI (`WindowSizeClass`):**
  - **Googlebook OS Desktop Mode:** `NavigationRail`, drag-resizable Disk Browser & Open Documents sidebar, AST Function Navigator bar, multi-document tabs with context menus, Single / Horizontal / Vertical split-view editing, dense bottom Status Bar (Cursor `Ln:Col`, selection counts, interactive Encoding, Line Ending, Syntax Language, and Zoom pickers), and hardware keyboard shortcuts (`Ctrl+N`, `Ctrl+O`, `Ctrl+S`, `Ctrl+Shift+S`, `Ctrl+W`, `Ctrl+F`, `Ctrl+Shift+F`, `Ctrl+G`, `Ctrl+J`, `Ctrl+D`, `Ctrl+L`, `Ctrl+/`).
  - **Android Mobile Mode:** `ModalNavigationDrawer`, `ScrollableTabRow`, `ModalBottomSheet` tool dialogs, and a **Sticky PowerShell Auxiliary Symbol Row** docked above the soft keyboard (`$`, `|`, `{`, `}`, `[`, `]`, `(`, `)`, `-`, `_`, `=`, `"`, `'`, `\`, `#`, `@`, `;` + Undo/Redo/Indent/Unindent).
- **First-Class PowerShell Engine (`PowerShellEngine.kt`):**
  - Approved-verbs dictionary & built-in alias highlighter (`Verb-Noun`), scoped/drive/automatic variable distinction (`$myVar`, `$env:PATH`, `$_`, `$PSItem`, `$true`), parameters (`-Path`, `-ErrorAction`), operators (`-eq`, `-match`, `-replace`), expandable string interpolation (`"$var"`, `"$(Get-Date)"`), Here-Strings (`@"..."@`), Comment-Based Help tags (`.SYNOPSIS`, `.PARAMETER`), code folding (`function`, `filter`, `workflow`, `class`, `#region`, `<#...#>`), and real-time AST Function Navigator.
- **1:1 BBEdit Core Feature Parity:**
  - **Industrial PCRE Find & Replace (`FindReplaceEngine.kt`):** Multi-line, named capture groups (`(?<name>...)`), backreferences (`$1`, `\1`, `\n`, `\t`), Extract to New File, live Pattern Playground, and Multi-File Search & Replace.
  - **Text Factory (`TextTransformEngine.kt`):** Modular batch pipeline chaining all 14 BBEdit text transformations.
  - **Side-by-Side Myers Diff (`DiffEngine.kt`):** Line & character-level diff spans with `Copy to Left (←)` and `Copy to Right (→)` directional merges.
  - **Clippings & Snippets (`ClippingsManager.kt`):** Full placeholder expansion engine with pre-loaded PowerShell snippet library.
- **6-Language Runtime Localization:**
  - 100% key parity across English (`en`), German (`de`), Italian (`it`), Spanish (`es`), French (`fr`), and Greek (`el`) via `AppCompatDelegate.setApplicationLocales()`.
