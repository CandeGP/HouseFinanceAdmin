package mx.finanzas.pareja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.finanzas.pareja.ui.AppTheme

private val CardShape = RoundedCornerShape(22.dp)

/**
 * Tarjeta del diseño: vidrio con borde fino sobre el degradado (o blanca con sombra en el tema claro).
 * Sustituye a Card de Material, que no deja ver el fondo y no cambia con la apariencia elegida.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Para tarjetas destacadas (p. ej. la quincena amarilla): degradado en lugar del vidrio. */
    container: Brush? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = AppTheme.tokens
    val color = if (container != null) Color.Transparent else tokens.glass
    val border = if (container != null) null else BorderStroke(1.dp, tokens.glassBorder)
    val body: @Composable () -> Unit = {
        Column(modifier = if (container != null) Modifier.background(container) else Modifier, content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CardShape,
            color = color,
            contentColor = contentColor,
            border = border,
            shadowElevation = tokens.cardElevation,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = CardShape,
            color = color,
            contentColor = contentColor,
            border = border,
            shadowElevation = tokens.cardElevation,
            content = body,
        )
    }
}
