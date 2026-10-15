package mx.finanzas.pareja.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.R

/** Paleta Fold: fondo niebla, texto azul medianoche y un solo azul eléctrico para lo clicable. */
object Palette {
    val MidnightNavy = Color(0xFF20294C)
    val CornflowerSteel = Color(0xFF375390)
    val ElectricBlue = Color(0xFF459AF8)
    val FogWhite = Color(0xFFF0F1F5)
    val PureWhite = Color(0xFFFFFFFF)
    val SilverLining = Color(0xFFC7CBDB)
    val MistGray = Color(0xFFDDDFE9)
    val Smoke = Color(0xFF979DB5)
    val Dusk = Color(0xFF676B89)
    val Abyss = Color(0xFF042939)
    val VoltageYellow = Color(0xFFFFFF00)
    val Hyacinth = Color(0xFF788DBA)

    // Derivados para modo oscuro (la paleta Fold es solo clara): mismos matices, más profundos.
    val NightCanvas = Color(0xFF0D1226)
    val NightCard = Color(0xFF161D38)
    val NightCardHigh = Color(0xFF1F2849)
    val NightLine = Color(0xFF2C3860)

    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFFF8A80)
}

private val LightColors = lightColorScheme(
    primary = Palette.MidnightNavy,
    onPrimary = Palette.PureWhite,
    primaryContainer = Palette.MistGray,
    onPrimaryContainer = Palette.MidnightNavy,
    secondary = Palette.CornflowerSteel,
    onSecondary = Palette.PureWhite,
    secondaryContainer = Palette.FogWhite,
    onSecondaryContainer = Palette.CornflowerSteel,
    tertiary = Palette.ElectricBlue,
    onTertiary = Palette.PureWhite,
    tertiaryContainer = Palette.VoltageYellow,
    onTertiaryContainer = Palette.Abyss,
    background = Palette.FogWhite,
    onBackground = Palette.MidnightNavy,
    surface = Palette.FogWhite,
    onSurface = Palette.MidnightNavy,
    surfaceVariant = Palette.MistGray,
    onSurfaceVariant = Palette.Dusk,
    surfaceTint = Palette.MidnightNavy,
    outline = Palette.SilverLining,
    outlineVariant = Palette.MistGray,
    error = Palette.ErrorLight,
    onError = Palette.PureWhite,
    surfaceContainerLowest = Palette.PureWhite,
    surfaceContainerLow = Palette.PureWhite,
    surfaceContainer = Palette.PureWhite,
    surfaceContainerHigh = Palette.PureWhite,
    surfaceContainerHighest = Palette.PureWhite,
)

private val DarkColors = darkColorScheme(
    primary = Palette.FogWhite,
    onPrimary = Palette.MidnightNavy,
    primaryContainer = Palette.NightCardHigh,
    onPrimaryContainer = Palette.FogWhite,
    secondary = Palette.Hyacinth,
    onSecondary = Palette.NightCanvas,
    secondaryContainer = Palette.NightCardHigh,
    onSecondaryContainer = Palette.SilverLining,
    tertiary = Palette.ElectricBlue,
    onTertiary = Palette.NightCanvas,
    tertiaryContainer = Palette.VoltageYellow,
    onTertiaryContainer = Palette.Abyss,
    background = Palette.NightCanvas,
    onBackground = Palette.FogWhite,
    surface = Palette.NightCanvas,
    onSurface = Palette.FogWhite,
    surfaceVariant = Palette.NightCardHigh,
    onSurfaceVariant = Palette.Smoke,
    surfaceTint = Palette.FogWhite,
    outline = Palette.NightLine,
    outlineVariant = Palette.NightLine,
    error = Palette.ErrorDark,
    onError = Palette.NightCanvas,
    surfaceContainerLowest = Palette.NightCard,
    surfaceContainerLow = Palette.NightCard,
    surfaceContainer = Palette.NightCard,
    surfaceContainerHigh = Palette.NightCardHigh,
    surfaceContainerHighest = Palette.NightCard,
)

// Inter variable en cada peso que usa el sistema (sustituto libre de GT Walsheim Pro).
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700))

private fun style(size: Int, lineHeight: Int, weight: Int, tracking: Double) = TextStyle(
    fontFamily = InterFamily,
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
    headlineLarge = style(32, 38, 700, -0.96),
    headlineMedium = style(28, 34, 600, -0.84),
    headlineSmall = style(24, 32, 600, -0.72),
    titleLarge = style(20, 26, 600, -0.4),
    titleMedium = style(16, 22, 600, -0.2),
    titleSmall = style(14, 20, 600, 0.0),
    bodyLarge = style(17, 24, 400, -0.3),
    bodyMedium = style(15, 22, 400, 0.0),
    bodySmall = style(13, 18, 400, 0.2),
    labelLarge = style(15, 20, 600, 0.0),
    labelMedium = style(12, 16, 600, 0.35),
    labelSmall = style(10, 12, 600, 0.4),
)

// Dos radios: 12dp en tarjetas y 24dp en paneles; los botones y tags ya son píldora por defecto.
private val FoldShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FinanzasTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = FoldTypography, shapes = FoldShapes, content = content)
}
