package mx.finanzas.pareja.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.AppTheme

/** Monto con los centavos más chicos y atenuados ($11,255.41 → $11,255 y .41). */
@Composable
fun Amount(value: Double, wholeSp: Int, centsSp: Int, modifier: Modifier = Modifier) {
    val text = formatMxn(value)
    val dot = text.lastIndexOf('.')
    val centsColor = LocalContentColor.current.copy(alpha = 0.75f)
    Text(
        buildAnnotatedString {
            if (dot < 0) append(text) else {
                append(text.substring(0, dot))
                withStyle(SpanStyle(fontSize = centsSp.sp, color = centsColor)) { append(text.substring(dot)) }
            }
        },
        fontSize = wholeSp.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        modifier = modifier,
    )
}

/** Barra de uso del límite: pista tenue y relleno con degradado turquesa a violeta. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 7.dp) {
    val chart = AppTheme.tokens.chart
    Box(
        modifier.fillMaxWidth().height(height).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(height).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(chart[3], chart[0]))),
        )
    }
}
