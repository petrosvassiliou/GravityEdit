package com.gravityedit.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.gravityedit.app.R

enum class GravityThemeMode {
    DYNAMIC,
    LIGHT,
    DARK,
    AMOLED
}

@Immutable
data class EditorSyntaxPalette(
    val isDark: Boolean,
    val editorBackground: Color,
    val editorForeground: Color,
    val gutterBackground: Color,
    val gutterText: Color,
    val gutterActiveLineText: Color,
    val activeLineBackground: Color,
    val selectionBackground: Color,
    val pageGuideColor: Color,
    val invisibleGlyphColor: Color,
    val keyword: Color,
    val cmdlet: Color,
    val variableNormal: Color,
    val variableScoped: Color,
    val variableAutomatic: Color,
    val parameter: Color,
    val operatorToken: Color,
    val stringLiteral: Color,
    val stringInterpolation: Color,
    val number: Color,
    val typeAttribute: Color,
    val comment: Color,
    val commentDocTag: Color,
    val punctuation: Color,
    val propertyKey: Color,
    val markdownHeading: Color,
    val searchMatchBackground: Color,
    val searchActiveMatchBackground: Color,
    val diffAddedBackground: Color,
    val diffAddedHighlight: Color,
    val diffRemovedBackground: Color,
    val diffRemovedHighlight: Color,
    val diffModifiedBackground: Color
)

private val LightDeveloperColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF535E78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E2FF),
    onSecondaryContainer = Color(0xFF0F1B32),
    tertiary = Color(0xFF705574),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF29132E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E9)
)

private val DarkDeveloperColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBBC6E4),
    onSecondary = Color(0xFF253048),
    secondaryContainer = Color(0xFF3B475F),
    onSecondaryContainer = Color(0xFFD8E2FF),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF33363E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A)
)

private val AmoledPitchBlackColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF002253),
    primaryContainer = Color(0xFF003B82),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1C262B),
    secondaryContainer = Color(0xFF263238),
    onSecondaryContainer = Color(0xFFCFD8DC),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF381E40),
    tertiaryContainer = Color(0xFF4A2754),
    onTertiaryContainer = Color(0xFFF3E5F5),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF600004),
    errorContainer = Color(0xFF8C0009),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF16181D),
    onSurfaceVariant = Color(0xFFB0B5C0),
    outline = Color(0xFF5C626E),
    outlineVariant = Color(0xFF20232A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF07080B),
    surfaceContainer = Color(0xFF0D0F14),
    surfaceContainerHigh = Color(0xFF151820),
    surfaceContainerHighest = Color(0xFF1E222B)
)

val LightSyntaxPalette = EditorSyntaxPalette(
    isDark = false,
    editorBackground = Color(0xFFFFFFFF),
    editorForeground = Color(0xFF1E232A),
    gutterBackground = Color(0xFFF3F5FA),
    gutterText = Color(0xFF7B8391),
    gutterActiveLineText = Color(0xFF005AC1),
    activeLineBackground = Color(0xFFEFF4FF),
    selectionBackground = Color(0x554D90FE),
    pageGuideColor = Color(0xFFD8DEE9),
    invisibleGlyphColor = Color(0xFFB0B8C8),
    keyword = Color(0xFF8A2BE2),
    cmdlet = Color(0xFF0055B8),
    variableNormal = Color(0xFFB33E00),
    variableScoped = Color(0xFF9C1F61),
    variableAutomatic = Color(0xFFD10056),
    parameter = Color(0xFF2B5B84),
    operatorToken = Color(0xFF00796B),
    stringLiteral = Color(0xFF1C7838),
    stringInterpolation = Color(0xFFB33E00),
    number = Color(0xFF0969DA),
    typeAttribute = Color(0xFF007285),
    comment = Color(0xFF6A737D),
    commentDocTag = Color(0xFF3E5F8A),
    punctuation = Color(0xFF4B5362),
    propertyKey = Color(0xFF0550AE),
    markdownHeading = Color(0xFF005AC1),
    searchMatchBackground = Color(0x66FFE066),
    searchActiveMatchBackground = Color(0xAAFF9800),
    diffAddedBackground = Color(0xFFE6FFEC),
    diffAddedHighlight = Color(0xFFABF2BC),
    diffRemovedBackground = Color(0xFFFFEBE9),
    diffRemovedHighlight = Color(0xFFFF8182),
    diffModifiedBackground = Color(0xFFFFF8C5)
)

val DarkSyntaxPalette = EditorSyntaxPalette(
    isDark = true,
    editorBackground = Color(0xFF13161C),
    editorForeground = Color(0xFFE3E7F0),
    gutterBackground = Color(0xFF181C24),
    gutterText = Color(0xFF697284),
    gutterActiveLineText = Color(0xFFADC6FF),
    activeLineBackground = Color(0xFF1C222E),
    selectionBackground = Color(0x553B78E7),
    pageGuideColor = Color(0xFF282E3A),
    invisibleGlyphColor = Color(0xFF454E5E),
    keyword = Color(0xFFC792EA),
    cmdlet = Color(0xFF82AAFF),
    variableNormal = Color(0xFFFFCB6B),
    variableScoped = Color(0xFFF78C6C),
    variableAutomatic = Color(0xFFFF5370),
    parameter = Color(0xFF89DDFF),
    operatorToken = Color(0xFF80CBC4),
    stringLiteral = Color(0xFFC3E88D),
    stringInterpolation = Color(0xFFFFCB6B),
    number = Color(0xFFF78C6C),
    typeAttribute = Color(0xFF4DD0E1),
    comment = Color(0xFF6E7A8A),
    commentDocTag = Color(0xFF90CAF9),
    punctuation = Color(0xFFA6B2C5),
    propertyKey = Color(0xFF82AAFF),
    markdownHeading = Color(0xFF82B1FF),
    searchMatchBackground = Color(0x55FFD54F),
    searchActiveMatchBackground = Color(0x99FF9800),
    diffAddedBackground = Color(0xFF122D1F),
    diffAddedHighlight = Color(0xFF235D3A),
    diffRemovedBackground = Color(0xFF34161B),
    diffRemovedHighlight = Color(0xFF6D2630),
    diffModifiedBackground = Color(0xFF2E2914)
)

val AmoledSyntaxPalette = DarkSyntaxPalette.copy(
    editorBackground = Color(0xFF000000),
    editorForeground = Color(0xFFF1F5F9),
    gutterBackground = Color(0xFF050608),
    gutterText = Color(0xFF5A6373),
    activeLineBackground = Color(0xFF0E121A),
    pageGuideColor = Color(0xFF1A1E26),
    invisibleGlyphColor = Color(0xFF333A48),
    diffAddedBackground = Color(0xFF0A1F14),
    diffRemovedBackground = Color(0xFF240B0F),
    diffModifiedBackground = Color(0xFF1F1B0A)
)

val LocalEditorSyntaxPalette = staticCompositionLocalOf { DarkSyntaxPalette }

private val GoogleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val JetBrainsMonoGoogleFont = GoogleFont("JetBrains Mono")
private val FiraCodeGoogleFont = GoogleFont("Fira Code")

val JetBrainsMonoFontFamily: FontFamily = FontFamily(
    Font(
        googleFont = JetBrainsMonoGoogleFont,
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
        style = FontStyle.Normal
    ),
    Font(
        googleFont = JetBrainsMonoGoogleFont,
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Medium,
        style = FontStyle.Normal
    ),
    Font(
        googleFont = JetBrainsMonoGoogleFont,
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Bold,
        style = FontStyle.Normal
    ),
    Font(
        googleFont = JetBrainsMonoGoogleFont,
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
        style = FontStyle.Italic
    ),
    Font(
        googleFont = FiraCodeGoogleFont,
        fontProvider = GoogleFontProvider,
        weight = FontWeight.Normal,
        style = FontStyle.Normal
    )
)

const val EDITOR_FONT_LIGATURES = "\"liga\" 1, \"calt\" 1"

fun editorCodeTextStyle(fontSizeSp: Float = 13.5f, lineHeightMultiplier: Float = 1.42f): TextStyle {
    return TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        fontFeatureSettings = EDITOR_FONT_LIGATURES
    )
}

val GravityTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = EDITOR_FONT_LIGATURES
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = EDITOR_FONT_LIGATURES
    )
)

object GravityEditThemeDefaults {
    val syntaxPalette: EditorSyntaxPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalEditorSyntaxPalette.current
}

@Composable
fun GravityEditTheme(
    themeMode: GravityThemeMode = GravityThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val (colorScheme, syntaxPalette, isDark) = when (themeMode) {
        GravityThemeMode.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) {
                    val dynamicDark = dynamicDarkColorScheme(context)
                    Triple(
                        dynamicDark,
                        DarkSyntaxPalette.copy(
                            editorBackground = dynamicDark.surfaceContainerLowest,
                            gutterBackground = dynamicDark.surfaceContainerLow,
                            activeLineBackground = dynamicDark.surfaceContainer,
                            gutterActiveLineText = dynamicDark.primary
                        ),
                        true
                    )
                } else {
                    val dynamicLight = dynamicLightColorScheme(context)
                    Triple(
                        dynamicLight,
                        LightSyntaxPalette.copy(
                            editorBackground = dynamicLight.surfaceContainerLowest,
                            gutterBackground = dynamicLight.surfaceContainerLow,
                            activeLineBackground = dynamicLight.surfaceContainer,
                            gutterActiveLineText = dynamicLight.primary
                        ),
                        false
                    )
                }
            } else {
                if (systemDark) {
                    Triple(DarkDeveloperColorScheme, DarkSyntaxPalette, true)
                } else {
                    Triple(LightDeveloperColorScheme, LightSyntaxPalette, false)
                }
            }
        }
        GravityThemeMode.LIGHT -> Triple(LightDeveloperColorScheme, LightSyntaxPalette, false)
        GravityThemeMode.DARK -> Triple(DarkDeveloperColorScheme, DarkSyntaxPalette, true)
        GravityThemeMode.AMOLED -> Triple(AmoledPitchBlackColorScheme, AmoledSyntaxPalette, true)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surfaceContainerLow.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalEditorSyntaxPalette provides syntaxPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GravityTypography,
            content = content
        )
    }
}
