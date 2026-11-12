package com.gravityedit.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.gravityedit.app.R

enum class GravityThemeMode {
    DYNAMIC,
    LIGHT,
    DARK,
    AMOLED
}

/**
 * Material 3 Expressive Motion Physics (`MotionScheme.expressive()` alignment).
 *
 * - **Spatial** springs use underdamped curves (`dampingRatio = 0.62f..0.65f`) for tactile,
 *   responsive shape morphing, pill expansion, and interactive bounce.
 * - **Effects** springs use critically damped curves (`dampingRatio = 1.0f`) so color and alpha
 *   interpolations remain crisp and never overshoot gamut bounds.
 */
object GravityExpressiveMotion {
    fun <T> spatialFast(): SpringSpec<T> = spring(
        dampingRatio = 0.62f,
        stiffness = 700f
    )

    fun <T> spatialDefault(): SpringSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = 380f
    )

    fun <T> spatialSlow(): SpringSpec<T> = spring(
        dampingRatio = 0.68f,
        stiffness = 220f
    )

    fun <T> effectsFast(): SpringSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 1600f
    )

    fun <T> effectsDefault(): SpringSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 800f
    )
}

/**
 * Material 3 Expressive Shape Scale: generous squircle radii paired with full-pill (`CircleShape`)
 * interactive controls and morphing connected button groups.
 */
val GravityExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

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
    primary = Color(0xFF0057CE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF3E5AA9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE1FF),
    onSecondaryContainer = Color(0xFF00174B),
    tertiary = Color(0xFF7B418F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBD7FF),
    onTertiaryContainer = Color(0xFF310044),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8FE),
    onBackground = Color(0xFF171C23),
    surface = Color(0xFFF6F8FE),
    onSurface = Color(0xFF171C23),
    surfaceVariant = Color(0xFFDDE2EE),
    onSurfaceVariant = Color(0xFF414753),
    outline = Color(0xFF717785),
    outlineVariant = Color(0xFFC1C6D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF1FA),
    surfaceContainer = Color(0xFFE7ECF6),
    surfaceContainerHigh = Color(0xFFE0E6F1),
    surfaceContainerHighest = Color(0xFFD8DFEC)
)

private val DarkDeveloperColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF002F67),
    primaryContainer = Color(0xFF084696),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFB4C5FF),
    onSecondary = Color(0xFF092978),
    secondaryContainer = Color(0xFF254190),
    onSecondaryContainer = Color(0xFFDBE1FF),
    tertiary = Color(0xFFEEB2FF),
    onTertiary = Color(0xFF490E5D),
    tertiaryContainer = Color(0xFF622875),
    onTertiaryContainer = Color(0xFFFBD7FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE2E6F0),
    surface = Color(0xFF0E1117),
    onSurface = Color(0xFFE2E6F0),
    surfaceVariant = Color(0xFF3E4553),
    onSurfaceVariant = Color(0xFFC1C6D4),
    outline = Color(0xFF8B91A0),
    outlineVariant = Color(0xFF2C323F),
    surfaceContainerLowest = Color(0xFF090B10),
    surfaceContainerLow = Color(0xFF151922),
    surfaceContainer = Color(0xFF1B202B),
    surfaceContainerHigh = Color(0xFF242A37),
    surfaceContainerHighest = Color(0xFF2F3646)
)

private val AmoledPitchBlackColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002456),
    primaryContainer = Color(0xFF003E8C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFA7C8FF),
    onSecondary = Color(0xFF06275B),
    secondaryContainer = Color(0xFF1A3768),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFFE5A4FF),
    onTertiary = Color(0xFF420A56),
    tertiaryContainer = Color(0xFF5A216F),
    onTertiaryContainer = Color(0xFFFAD7FF),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF600004),
    errorContainer = Color(0xFF8C0009),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDF1F8),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDF1F8),
    surfaceVariant = Color(0xFF161A23),
    onSurfaceVariant = Color(0xFFB3BAC8),
    outline = Color(0xFF5D6474),
    outlineVariant = Color(0xFF1E232E),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080A0F),
    surfaceContainer = Color(0xFF10141D),
    surfaceContainerHigh = Color(0xFF181D29),
    surfaceContainerHighest = Color(0xFF222938)
)

val LightSyntaxPalette = EditorSyntaxPalette(
    isDark = false,
    editorBackground = Color(0xFFFFFFFF),
    editorForeground = Color(0xFF1A1F28),
    gutterBackground = Color(0xFFF2F5FC),
    gutterText = Color(0xFF757E8F),
    gutterActiveLineText = Color(0xFF0057CE),
    activeLineBackground = Color(0xFFEBF2FF),
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
    markdownHeading = Color(0xFF0057CE),
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
    editorBackground = Color(0xFF10141C),
    editorForeground = Color(0xFFE5E9F2),
    gutterBackground = Color(0xFF151A24),
    gutterText = Color(0xFF6B7588),
    gutterActiveLineText = Color(0xFFA8C7FA),
    activeLineBackground = Color(0xFF1A2130),
    selectionBackground = Color(0x553B78E7),
    pageGuideColor = Color(0xFF282F3D),
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
    markdownHeading = Color(0xFF8AB4F8),
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
    gutterBackground = Color(0xFF05070B),
    gutterText = Color(0xFF5A6373),
    activeLineBackground = Color(0xFF0D121C),
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

/**
 * Material 3 Expressive Typography with emphasized display/title weights and crisp monospace telemetry.
 */
val GravityTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
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
        fontWeight = FontWeight.Medium,
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

    val pillShape: Shape = CircleShape
    val dialogShape: Shape = RoundedCornerShape(28.dp)
    val sheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalEditorSyntaxPalette provides syntaxPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = GravityExpressiveShapes,
            typography = GravityTypography,
            content = content
        )
    }
}
