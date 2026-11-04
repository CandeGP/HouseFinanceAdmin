package mx.finanzas.pareja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.ui.AppTheme

/** Tarjeta de resumen del mes: cuántos registros, el total y las flechas para cambiar de mes. */
@Composable
fun MonthSummaryCard(
    monthLabel: String,
    count: Int,
    total: Double,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    noun: String = "movimientos",
) {
    GlassCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$monthLabel · $count $noun",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
                Amount(total, wholeSp = 24, centsSp = 15)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior", onPrevious)
                MonthButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente", onNext)
            }
        }
    }
}

@Composable
private fun MonthButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val tokens = AppTheme.tokens
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = tokens.glass,
        border = BorderStroke(1.dp, tokens.glassBorder),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp)) }
    }
}
