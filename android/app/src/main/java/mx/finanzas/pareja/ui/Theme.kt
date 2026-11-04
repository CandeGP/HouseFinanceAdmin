package mx.finanzas.pareja.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mx.finanzas.pareja.R
import mx.finanzas.pareja.ui.lock.findFragmentActivity

/** Apariencias disponibles. Para sumar una nueva basta otro valor aquí y sus colores abajo. */
enum class ThemeStyle(val id: String, val label: String) {
    DARK("dark", "Oscuro"),
    LIGHT("light", "Claro"),
    MINIMAL("minimal", "Minimal");

    companion object {
        fun fromId(id: String?): ThemeStyle = entries.firstOrNull { it.id == id } ?: DARK
    }
}

/** Apariencia elegida en este teléfono. Es observable: al cambiarla en Cuenta, la app se redibuja al instante. */
object ThemePrefs {
    private const val PREFS = "app_theme"
    private const val KEY_STYLE = "style"
    private var flow: MutableStateFlow<ThemeStyle>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun mutable(context: Context): MutableStateFlow<ThemeStyle> =
        flow ?: MutableStateFlow(ThemeStyle.fromId(prefs(context).getString(KEY_STYLE, null))).also { flow = it }

    fun style(context: Context): StateFlow<ThemeStyle> = mutable(context)

    fun set(context: Context, style: ThemeStyle) {
        prefs(context).edit().putString(KEY_STYLE, style.id).apply()
        mutable(context).value = style
    }
}

/** Colores que Material no cubre (degradados, vidrio, quincenas, gráficas). Cada apariencia trae los suyos. */
@Immutable
class AppTokens(
    val isDark: Boolean,
    val background: Brush,
    val statusBar: Color,
    val navBar: Color,
    /** Relleno de las tarjetas y su borde fino. */
    val glass: Color,
    val glassBorder: Color,
    val cardElevation: Dp,
    /** Degradado de los botones principales y del «+». */
    val accent: Brush,
    val onAccent: Color,
    /** Sombra de color bajo los botones con degradado. */
    val accentGlow: Color,
    val dock: Color,
    val dockBorder: Color,
    val quincenaFirst: Brush,
    val onQuincenaFirst: Color,
    val quincenaSecond: Brush,
    val onQuincenaSecond: Color,
    val success: Color,
    val warning: Color,
    /** Colores de las categorías en la dona, en orden. */
    val chart: List<Color>,
)

private val AccentGradient = Brush.horizontalGradient(listOf(Color(0xFF7C6CFF), Color(0xFFFF6FA8)))

private val DarkTokens = AppTokens(
    isDark = true,
    background = Brush.verticalGradient(listOf(Color(0xFF0A2A4A), Color(0xFF1A1442), Color(0xFF4A1D4D))),
    statusBar = Color(0xFF0A2A4A),
    navBar = Color(0xFF4A1D4D),
    glass = Color(0x17FFFFFF),
    glassBorder = Color(0x29FFFFFF),
    cardElevation = 0.dp,
    accent = AccentGradient,
    onAccent = Color.White,
    accentGlow = Color(0x807C6CFF),
    dock = Color(0xEB141032),
    dockBorder = Color(0x29FFFFFF),
    quincenaFirst = Brush.linearGradient(listOf(Color(0x807C6CFF), Color(0x4D4F8CFF))),
    onQuincenaFirst = Color.White,
    quincenaSecond = Brush.linearGradient(listOf(Color(0xFFFFE36A), Color(0xFFFFB547))),
    onQuincenaSecond = Color(0xFF2A1A00),
    success = Color(0xFF6EE7A0),
    warning = Color(0xFFFFC870),
    chart = listOf(Color(0xFF8B7CFF), Color(0xFFFF6FA8), Color(0xFFFFB547), Color(0xFF3DD6D0), Color(0xFF4ADE80)),
)

private val LightTokens = AppTokens(
    isDark = false,
    background = Brush.verticalGradient(listOf(Color(0xFFEAE3FF), Color(0xFFF4F0FF))),
    statusBar = Color(0xFFEAE3FF),
    navBar = Color(0xFFF4F0FF),
    glass = Color.White,
    glassBorder = Color(0x14000000),
    cardElevation = 3.dp,
    accent = Brush.horizontalGradient(listOf(Color(0xFF6B4DF0), Color(0xFFFF6FA8))),
    onAccent = Color.White,
    accentGlow = Color(0x666B4DF0),
    dock = Color.White,
    dockBorder = Color(0x14000000),
    quincenaFirst = SolidColor(Color.White),
    onQuincenaFirst = Color(0xFF1F1747),
    quincenaSecond = Brush.linearGradient(listOf(Color(0xFFFFE36A), Color(0xFFFFC043))),
    onQuincenaSecond = Color(0xFF2A1A00),
    success = Color(0xFF1E9E5A),
    warning = Color(0xFFB36B00),
    chart = listOf(Color(0xFF7C5CFF), Color(0xFFFF5F9E), Color(0xFFFFB020), Color(0xFF19C4BE), Color(0xFF2FC46B)),
)

private val MinimalTokens = AppTokens(
    isDark = true,
    background = SolidColor(Color(0xFF0E0F13)),
    statusBar = Color(0xFF0E0F13),
    navBar = Color(0xFF15171D),
    glass = Color(0xFF15171D),
    glassBorder = Color(0xFF262932),
    cardElevation = 0.dp,
    accent = SolidColor(Color(0xFFF2F3F5)),
    onAccent = Color(0xFF0E0F13),
    accentGlow = Color.Transparent,
    dock = Color(0xFF15171D),
    dockBorder = Color(0xFF262932),
    quincenaFirst = SolidColor(Color(0xFF15171D)),
    onQuincenaFirst = Color(0xFFF2F3F5),
    quincenaSecond = SolidColor(Color(0xFF15171D)),
    onQuincenaSecond = Color(0xFFFFD96A),
    success = Color(0xFF7FD89A),
    warning = Color(0xFFE8C55A),
    chart = listOf(Color(0xFF9AA3FF), Color(0xFFF08FB4), Color(0xFFF2C26B), Color(0xFF6ED3CF), Color(0xFF7FD89A)),
)

private val LocalTokens = staticCompositionLocalOf { DarkTokens }

object AppTheme {
    val tokens: AppTokens
        @Composable @ReadOnlyComposable get() = LocalTokens.current
}

// El fondo y la superficie son transparentes a propósito: AppBackground pinta el degradado debajo de todo
// y así cada Scaffold y barra superior lo deja ver. Diálogos y menús usan los contenedores, que son opacos.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B2370),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF3DD6D0),
    onSecondary = Color(0xFF042939),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFD4F8F6),
    tertiary = Color(0xFFFF6FA8),
    onTertiary = Color(0xFF2A0F1E),
    tertiaryContainer = Color(0xFFFFE36A),
    onTertiaryContainer = Color(0xFF2A1A00),
    background = Color.Transparent,
    onBackground = Color.White,
    surface = Color.Transparent,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2458),
    onSurfaceVariant = Color(0xFFC3BFE0),
    surfaceTint = Color(0xFF8B7CFF),
    outline = Color(0xFF5C5690),
    outlineVariant = Color(0xFF3A3470),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF2A0A0A),
    surfaceContainerLowest = Color(0xFF191443),
    surfaceContainerLow = Color(0xFF1F1950),
    surfaceContainer = Color(0xFF241D58),
    surfaceContainerHigh = Color(0xFF2A2362),
    surfaceContainerHighest = Color(0xFF302969),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5A3DF0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3DCFF),
    onPrimaryContainer = Color(0xFF1F1747),
    secondary = Color(0xFF0F8F8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F4F2),
    onSecondaryContainer = Color(0xFF053B39),
    tertiary = Color(0xFFD6246E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE36A),
    onTertiaryContainer = Color(0xFF2A1A00),
    background = Color.Transparent,
    onBackground = Color(0xFF1F1747),
    surface = Color.Transparent,
    onSurface = Color(0xFF1F1747),
    surfaceVariant = Color(0xFFE8E2FA),
    onSurfaceVariant = Color(0xFF6B6490),
    surfaceTint = Color(0xFF5A3DF0),
    outline = Color(0xFFB9B0DC),
    outlineVariant = Color(0xFFD8D1F0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF1ECFF),
)

private val MinimalColors = darkColorScheme(
    primary = Color(0xFFF2F3F5),
    onPrimary = Color(0xFF0E0F13),
    primaryContainer = Color(0xFF20232B),
    onPrimaryContainer = Color(0xFFF2F3F5),
    secondary = Color(0xFF9AA3FF),
    onSecondary = Color(0xFF0E0F13),
    secondaryContainer = Color(0xFF20232B),
    onSecondaryContainer = Color(0xFFC9CCD6),
    tertiary = Color(0xFFF2C26B),
    onTertiary = Color(0xFF0E0F13),
    tertiaryContainer = Color(0xFF15171D),
    onTertiaryContainer = Color(0xFFFFD96A),
    background = Color.Transparent,
    onBackground = Color(0xFFF2F3F5),
    surface = Color.Transparent,
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF20232B),
    onSurfaceVariant = Color(0xFFA0A4B0),
    surfaceTint = Color(0xFFF2F3F5),
    outline = Color(0xFF3A3D48),
    outlineVariant = Color(0xFF262932),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF2A0A0A),
    surfaceContainerLowest = Color(0xFF15171D),
    surfaceContainerLow = Color(0xFF15171D),
    surfaceContainer = Color(0xFF1A1C23),
    surfaceContainerHigh = Color(0xFF20232B),
    surfaceContainerHighest = Color(0xFF262932),
)

// Plus Jakarta Sans (licencia OFL), la tipografía de los mockups. Se usan los cinco pesos estáticos: la versión
// variable dibuja bien el peso pero deja los espacios entre palabras demasiado angostos en Android.
private val JakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, lineHeight: Int, weight: Int, tracking: Double) = TextStyle(
    fontFamily = JakartaFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

// Escala Fold adaptada a teléfono: titulares densos con tracking negativo, etiquetas con +0.4.
private val FoldTypography = Typography(
    displayLarge = style(48, 50, 700, -1.9),
    displayMedium = style(40, 42, 700, -1.6),
    displaySmall = style(34, 38, 700, -1.0),
    headlineLarge = style(32, 38, 800, -0.5),
    headlineMedium = style(28, 34, 800, -0.5),
    headlineSmall = style(24, 32, 800, -0.4),
    titleLarge = style(20, 26, 800, -0.4),
    titleMedium = style(16, 22, 700, 0.0),
    titleSmall = style(14, 20, 600, 0.0),
    bodyLarge = style(17, 24, 400, 0.0),
    bodyMedium = style(15, 22, 400, 0.0),
    bodySmall = style(13, 18, 400, 0.2),
    labelLarge = style(15, 20, 600, 0.0),
    labelMedium = style(12, 16, 600, 0.35),
    labelSmall = style(10, 12, 600, 0.4),
)

// Radios generosos: 14dp en controles, 22dp en tarjetas y 28dp en paneles; los botones ya son píldora.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FinanzasTheme(style: ThemeStyle = ThemeStyle.DARK, content: @Composable () -> Unit) {
    val colors: ColorScheme
    val tokens: AppTokens
    when (style) {
        ThemeStyle.DARK -> { colors = DarkColors; tokens = DarkTokens }
        ThemeStyle.LIGHT -> { colors = LightColors; tokens = LightTokens }
        ThemeStyle.MINIMAL -> { colors = MinimalColors; tokens = MinimalTokens }
    }

    // Barras del sistema acordes al tema: iconos oscuros sobre el tema claro, claros sobre los demás.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findFragmentActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = tokens.statusBar.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = tokens.navBar.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !tokens.isDark
                isAppearanceLightNavigationBars = !tokens.isDark
            }
        }
    }

    CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(colorScheme = colors, typography = FoldTypography, shapes = AppShapes, content = content)
    }
}
