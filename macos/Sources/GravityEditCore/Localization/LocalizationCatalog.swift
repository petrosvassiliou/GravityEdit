import Foundation

public enum AppLanguage: String, CaseIterable, Codable, Identifiable, Sendable {
    case system = "system"
    case english = "en"
    case german = "de"
    case italian = "it"
    case spanish = "es"
    case french = "fr"
    case greek = "el"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .system: return "System Default (macOS)"
        case .english: return "English"
        case .german: return "Deutsch"
        case .italian: return "Italiano"
        case .spanish: return "Español"
        case .french: return "Français"
        case .greek: return "Ελληνικά"
        }
    }

    public var resolvedCode: String {
        if self != .system { return rawValue }
        let preferred = Locale.preferredLanguages.first?.lowercased() ?? "en"
        for code in ["de", "it", "es", "fr", "el", "en"] {
            if preferred.hasPrefix(code) { return code }
        }
        return "en"
    }
}

public enum L10nKey: String, CaseIterable, Sendable {
    case appTitle
    case openDocuments
    case diskBrowser
    case functionNavigator
    case noFunctionsFound
    case clippings
    case diagnostics
    case scratchpad
    case newDocument
    case openFile
    case mountFolder
    case save
    case saveAs
    case findReplace
    case textFactory
    case compareDiff
    case commandPalette
    case settings
    case checkForUpdates
    case updateAvailable
    case upToDate
    case installAndRelaunch
    case splitSingle
    case splitVertical
    case splitHorizontal
    case softWrap
    case showInvisibles
    case lineNumbers
    case pageGuide
    case cleanScript
    case quickFixAll
    case goToLine
}

public enum LocalizationCatalog {

    public static func tr(_ key: L10nKey, language: AppLanguage = .system) -> String {
        let code = language.resolvedCode
        return table[code]?[key] ?? table["en"]![key]!
    }

    private static let table: [String: [L10nKey: String]] = [
        "en": [
            .appTitle: "GravityEdit",
            .openDocuments: "Open Documents",
            .diskBrowser: "Disk Browser",
            .functionNavigator: "Function Navigator",
            .noFunctionsFound: "No functions or landmarks found",
            .clippings: "Clippings",
            .diagnostics: "Script Diagnostics",
            .scratchpad: "Scratchpad",
            .newDocument: "New Document",
            .openFile: "Open File…",
            .mountFolder: "Mount Project Folder…",
            .save: "Save",
            .saveAs: "Save As…",
            .findReplace: "Find & Replace",
            .textFactory: "Text Factory",
            .compareDiff: "Compare Documents (Diff)",
            .commandPalette: "Command Palette",
            .settings: "Settings…",
            .checkForUpdates: "Check for Updates…",
            .updateAvailable: "Update Available",
            .upToDate: "GravityEdit is up to date",
            .installAndRelaunch: "Install Update & Relaunch",
            .splitSingle: "Single Pane",
            .splitVertical: "Vertical Split",
            .splitHorizontal: "Horizontal Split",
            .softWrap: "Soft Wrap",
            .showInvisibles: "Show Invisibles",
            .lineNumbers: "Line Numbers",
            .pageGuide: "Page Guides (80 / 120)",
            .cleanScript: "0 Issues",
            .quickFixAll: "Fix All",
            .goToLine: "Go to Line"
        ],
        "de": [
            .appTitle: "GravityEdit",
            .openDocuments: "Offene Dokumente",
            .diskBrowser: "Datei-Browser",
            .functionNavigator: "Funktionsnavigator",
            .noFunctionsFound: "Keine Funktionen gefunden",
            .clippings: "Textbausteine",
            .diagnostics: "Skript-Diagnose",
            .scratchpad: "Notizblock",
            .newDocument: "Neues Dokument",
            .openFile: "Datei öffnen…",
            .mountFolder: "Projektordner einbinden…",
            .save: "Sichern",
            .saveAs: "Sichern unter…",
            .findReplace: "Suchen & Ersetzen",
            .textFactory: "Text-Fabrik",
            .compareDiff: "Dokumente vergleichen (Diff)",
            .commandPalette: "Befehlspalette",
            .settings: "Einstellungen…",
            .checkForUpdates: "Nach Updates suchen…",
            .updateAvailable: "Update verfügbar",
            .upToDate: "GravityEdit ist auf dem neuesten Stand",
            .installAndRelaunch: "Update installieren & neu starten",
            .splitSingle: "Einzelansicht",
            .splitVertical: "Vertikal teilen",
            .splitHorizontal: "Horizontal teilen",
            .softWrap: "Zeilenumbruch",
            .showInvisibles: "Steuerzeichen anzeigen",
            .lineNumbers: "Zeilennummern",
            .pageGuide: "Seitenlinien (80 / 120)",
            .cleanScript: "0 Probleme",
            .quickFixAll: "Alle beheben",
            .goToLine: "Gehe zu Zeile"
        ],
        "it": [
            .appTitle: "GravityEdit",
            .openDocuments: "Documenti aperti",
            .diskBrowser: "Esplora disco",
            .functionNavigator: "Navigatore funzioni",
            .noFunctionsFound: "Nessuna funzione trovata",
            .clippings: "Frammenti",
            .diagnostics: "Diagnostica script",
            .scratchpad: "Blocco note",
            .newDocument: "Nuovo documento",
            .openFile: "Apri file…",
            .mountFolder: "Monta cartella progetto…",
            .save: "Salva",
            .saveAs: "Salva col nome…",
            .findReplace: "Trova e sostituisci",
            .textFactory: "Text Factory",
            .compareDiff: "Confronta documenti (Diff)",
            .commandPalette: "Tavolozza comandi",
            .settings: "Impostazioni…",
            .checkForUpdates: "Controlla aggiornamenti…",
            .updateAvailable: "Aggiornamento disponibile",
            .upToDate: "GravityEdit è aggiornato",
            .installAndRelaunch: "Installa e riavvia",
            .splitSingle: "Vista singola",
            .splitVertical: "Divisione verticale",
            .splitHorizontal: "Divisione orizzontale",
            .softWrap: "A capo automatico",
            .showInvisibles: "Mostra invisibili",
            .lineNumbers: "Numeri di riga",
            .pageGuide: "Guide pagina (80 / 120)",
            .cleanScript: "0 problemi",
            .quickFixAll: "Correggi tutto",
            .goToLine: "Vai alla riga"
        ],
        "es": [
            .appTitle: "GravityEdit",
            .openDocuments: "Documentos abiertos",
            .diskBrowser: "Explorador de disco",
            .functionNavigator: "Navegador de funciones",
            .noFunctionsFound: "No se encontraron funciones",
            .clippings: "Recortes",
            .diagnostics: "Diagnóstico de script",
            .scratchpad: "Bloc de notas",
            .newDocument: "Nuevo documento",
            .openFile: "Abrir archivo…",
            .mountFolder: "Montar carpeta de proyecto…",
            .save: "Guardar",
            .saveAs: "Guardar como…",
            .findReplace: "Buscar y reemplazar",
            .textFactory: "Text Factory",
            .compareDiff: "Comparar documentos (Diff)",
            .commandPalette: "Paleta de comandos",
            .settings: "Ajustes…",
            .checkForUpdates: "Buscar actualizaciones…",
            .updateAvailable: "Actualización disponible",
            .upToDate: "GravityEdit está actualizado",
            .installAndRelaunch: "Instalar y reiniciar",
            .splitSingle: "Panel único",
            .splitVertical: "División vertical",
            .splitHorizontal: "División horizontal",
            .softWrap: "Ajuste de línea",
            .showInvisibles: "Mostrar invisibles",
            .lineNumbers: "Números de línea",
            .pageGuide: "Guías de página (80 / 120)",
            .cleanScript: "0 problemas",
            .quickFixAll: "Corregir todo",
            .goToLine: "Ir a la línea"
        ],
        "fr": [
            .appTitle: "GravityEdit",
            .openDocuments: "Documents ouverts",
            .diskBrowser: "Explorateur de disque",
            .functionNavigator: "Navigateur de fonctions",
            .noFunctionsFound: "Aucune fonction trouvée",
            .clippings: "Extraits",
            .diagnostics: "Diagnostic du script",
            .scratchpad: "Bloc-notes",
            .newDocument: "Nouveau document",
            .openFile: "Ouvrir un fichier…",
            .mountFolder: "Monter un dossier projet…",
            .save: "Enregistrer",
            .saveAs: "Enregistrer sous…",
            .findReplace: "Rechercher et remplacer",
            .textFactory: "Text Factory",
            .compareDiff: "Comparer les documents (Diff)",
            .commandPalette: "Palette de commandes",
            .settings: "Réglages…",
            .checkForUpdates: "Rechercher des mises à jour…",
            .updateAvailable: "Mise à jour disponible",
            .upToDate: "GravityEdit est à jour",
            .installAndRelaunch: "Installer et relancer",
            .splitSingle: "Volet unique",
            .splitVertical: "Scission verticale",
            .splitHorizontal: "Scission horizontale",
            .softWrap: "Retour à la ligne",
            .showInvisibles: "Afficher les invisibles",
            .lineNumbers: "Numéros de ligne",
            .pageGuide: "Guides de page (80 / 120)",
            .cleanScript: "0 problème",
            .quickFixAll: "Tout corriger",
            .goToLine: "Aller à la ligne"
        ],
        "el": [
            .appTitle: "GravityEdit",
            .openDocuments: "Ανοιχτά έγγραφα",
            .diskBrowser: "Περιήγηση δίσκου",
            .functionNavigator: "Πλοηγός συναρτήσεων",
            .noFunctionsFound: "Δεν βρέθηκαν συναρτήσεις",
            .clippings: "Αποσπάσματα",
            .diagnostics: "Διαγνωστικά σεναρίου",
            .scratchpad: "Σημειωματάριο",
            .newDocument: "Νέο έγγραφο",
            .openFile: "Άνοιγμα αρχείου…",
            .mountFolder: "Προσάρτηση φακέλου έργου…",
            .save: "Αποθήκευση",
            .saveAs: "Αποθήκευση ως…",
            .findReplace: "Εύρεση και αντικατάσταση",
            .textFactory: "Text Factory",
            .compareDiff: "Σύγκριση εγγράφων (Diff)",
            .commandPalette: "Παλέτα εντολών",
            .settings: "Ρυθμίσεις…",
            .checkForUpdates: "Έλεγχος για ενημερώσεις…",
            .updateAvailable: "Διαθέσιμη ενημέρωση",
            .upToDate: "Το GravityEdit είναι ενημερωμένο",
            .installAndRelaunch: "Εγκατάσταση & επανεκκίνηση",
            .splitSingle: "Μονή προβολή",
            .splitVertical: "Κάθετος διαχωρισμός",
            .splitHorizontal: "Οριζόντιος διαχωρισμός",
            .softWrap: "Αναδίπλωση γραμμών",
            .showInvisibles: "Εμφάνιση αόρατων χαρακτήρων",
            .lineNumbers: "Αριθμοί γραμμών",
            .pageGuide: "Οδηγοί σελίδας (80 / 120)",
            .cleanScript: "0 ζητήματα",
            .quickFixAll: "Διόρθωση όλων",
            .goToLine: "Μετάβαση στη γραμμή"
        ]
    ]
}
