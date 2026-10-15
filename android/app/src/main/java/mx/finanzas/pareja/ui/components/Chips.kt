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
import androidx.compose.ui.unit.dp
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.domain.cardAccentArgb
import mx.finanzas.pareja.domain.cardLabel

/** Tarjeta identificada por el color de su banco y "Banco ••1234". */
@Composable
fun CardChip(card: Card, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).background(Color(cardAccentArgb(card)), CircleShape))
        Text(cardLabel(card), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
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
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
