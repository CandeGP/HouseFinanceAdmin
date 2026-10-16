package mx.finanzas.pareja.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.domain.payLabel

/** Cómo va el pago del último ciclo cerrado: verde si está pagado, rojo si venció, apagado si no hay registro. */
@Composable
fun PayStatusText(status: CardCycleStatus, modifier: Modifier = Modifier) {
    val color = when (status.status) {
        CardPayStatus.PAID -> MaterialTheme.colorScheme.tertiary
        CardPayStatus.OVERDUE -> MaterialTheme.colorScheme.error
        CardPayStatus.UNTRACKED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(status.payLabel(), style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}
