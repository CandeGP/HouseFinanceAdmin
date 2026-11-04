package mx.finanzas.pareja.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.domain.cardAccentArgb
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.domain.cardLabel

/** Fondo de las píldoras sobre una tarjeta: negro tenue en los temas oscuros y gris suave en el claro. */
@Composable
private fun pillColor(): Color =
    if (AppTheme.tokens.isDark) Color.Black.copy(alpha = 0.28f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

/** Tarjeta identificada por el color de su banco y "Banco ••1234". */
@Composable
fun CardChip(card: Card, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(pillColor(), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).background(Color(cardAccentArgb(card)), CircleShape))
        // Una línea con "…": un banco o nombre largo no debe partir la fila ni empujar el texto de al lado.
        Text(
            cardLabel(card),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Etiqueta en píldora con la categoría (o "Sin categoría" cuando falta). */
@Composable
fun CategoryTag(name: String?, modifier: Modifier = Modifier) {
    Text(
        text = (name ?: "Sin categoría").uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(pillColor(), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Rótulo de sección en mayúsculas (CATEGORÍA, PAGAR CON…), como en los mockups. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = modifier.padding(start = 2.dp),
    )
}

/** Ficha elegible del mockup: píldora de 36 de alto; la elegida lleva borde de color y fondo tenue. */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Emoji decorativo antes del texto; el lector de pantalla lo omite y dice solo [label]. */
    leading: String? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tokens = AppTheme.tokens
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) primary.copy(alpha = 0.25f) else tokens.glass,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) primary else tokens.glassBorder),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading?.let { Text(it, fontSize = 14.sp, modifier = Modifier.clearAndSetSemantics { }) }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
