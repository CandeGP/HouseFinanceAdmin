package mx.finanzas.pareja.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CardBalanceCheck
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayment
import mx.finanzas.pareja.data.MonthSpend
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.localDate
import mx.finanzas.pareja.domain.monthBounds
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.Amount
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.components.PayStatusText
import mx.finanzas.pareja.ui.components.PillTabs
import mx.finanzas.pareja.ui.components.UsageBar

/** Días que faltan para el próximo corte (0 si es hoy). */
internal fun daysUntilCut(today: LocalDate, cutoffDay: Int): Int {
    fun cutIn(year: Int, month: Int): LocalDate =
        LocalDate(year, month, minOf(cutoffDay, monthBounds(year, month).second.dayOfMonth))
    val thisMonth = cutIn(today.year, today.monthNumber)
    val next = if (thisMonth >= today) thisMonth else {
        val following = LocalDate(today.year, today.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
        cutIn(following.year, following.monthNumber)
    }
    return today.daysUntil(next)
}

/** Resumen de la tarjeta al frente: saldo, corte, uso del límite y accesos (Pagar, Detalle, Editar). */
@Composable
internal fun CardSummaryPanel(
    card: Card,
    debt: Double?,
    today: LocalDate,
    mine: Boolean,
    canPay: Boolean,
    onPay: () -> Unit,
    onDetail: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owed = debt ?: 0.0
    val fraction = if (card.creditLimit > 0) (owed / card.creditLimit).toFloat() else 0f
    val days = daysUntilCut(today, card.cutoffDay)
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Saldo actual", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                Text(
                    when (days) {
                        0 -> "Corte hoy"
                        1 -> "Corte mañana"
                        else -> "Corte en $days días"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.tokens.warning,
                    modifier = Modifier.clip(CircleShape)
                        .background(AppTheme.tokens.warning.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Amount(owed, wholeSp = 28, centsSp = 17, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            UsageBar(fraction, height = 8.dp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${"%.0f".format(fraction * 100)}% de tu límite", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("Límite ${formatMxn(card.creditLimit)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mine && canPay) PanelButton("Pagar", primary = true, onClick = onPay, modifier = Modifier.weight(1f))
                PanelButton("Detalle", primary = false, onClick = onDetail, modifier = Modifier.weight(1f))
                if (mine) PanelButton("Editar", primary = false, onClick = onEdit, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PanelButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(44.dp).clip(shape)
            .then(if (primary) Modifier.background(tokens.accent) else Modifier.background(tokens.glass).border(1.dp, tokens.glassBorder, shape))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            color = if (primary) tokens.onAccent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Pantalla de detalle de una tarjeta: la tarjeta, lo que debes y lo que te queda, y tres pestañas con sus
 * movimientos, sus pagos y sus ajustes (presupuesto, conciliación con el banco y archivar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardDetailScreen(
    card: Card,
    mine: Boolean,
    ownerName: String?,
    debt: Double?,
    status: CardCycleStatus?,
    payments: List<CardPayment>,
    movements: List<Transaction>,
    spend: MonthSpend?,
    balanceCheck: CardBalanceCheck?,
    zone: TimeZone,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPay: () -> Unit,
    onEditPayment: (CardPayment) -> Unit,
    onDeletePayment: (CardPayment) -> Unit,
    onCaptureBalance: () -> Unit,
    onArchive: () -> Unit,
    onToggleFrozen: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tokens = AppTheme.tokens
    val owed = debt ?: 0.0
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = tokens.glass,
                border = BorderStroke(1.dp, tokens.glassBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                card.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            if (mine) {
                Surface(
                    onClick = onEdit,
                    shape = CircleShape,
                    color = tokens.glass,
                    border = BorderStroke(1.dp, tokens.glassBorder),
                ) {
                    Row(Modifier.height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Text("Editar", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CardFace(card, front = true, onClick = null, modifier = Modifier.fillMaxWidth().height(168.dp)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text("Debes en total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                            Amount(owed, wholeSp = 22, centsSp = 14, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    GlassCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text("Disponible", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.material3.LocalContentColor provides tokens.success,
                            ) { Amount((card.creditLimit - owed).coerceAtLeast(0.0), wholeSp = 22, centsSp = 14, modifier = Modifier.padding(top = 2.dp)) }
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("Corte día ${card.cutoffDay}", null)
                    InfoChip("Pago día ${card.dueDay}", tokens.warning)
                    if (card.hasCashback) InfoChip("Cashback", tokens.success)
                    if (!mine) InfoChip("de ${ownerName ?: "tu pareja"}", null)
                }
            }
            item { PillTabs(listOf("Movimientos", "Pagos", "Ajustes"), tab, { tab = it }) }

            when (tab) {
                0 -> {
                    if (movements.isEmpty()) item { EmptyLine("Sin movimientos en los últimos meses.") }
                    items(movements, key = { it.id }) { tx ->
                        DetailRow(tx.merchant, formatDateEs(tx.occurredAt), "-${formatMxn(tx.amount)}")
                    }
                }
                1 -> {
                    status?.let { item { PayStatusText(it) } }
                    if (payments.isEmpty()) item { EmptyLine("Aún no hay pagos registrados de este corte.") }
                    items(payments, key = { it.id }) { payment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DetailRow(
                                when (payment.destination) {
                                    mx.finanzas.pareja.data.PaymentDestination.NEXT_CYCLE -> "Adelanto al siguiente corte"
                                    mx.finanzas.pareja.data.PaymentDestination.CAPITAL -> "Abono a capital"
                                    else -> if (payment.method == PaymentMethod.CASH) "Efectivo" else "Transferencia"
                                },
                                formatDateEs(localDate(payment.paidAt, zone)),
                                formatMxn(payment.amount),
                                modifier = Modifier.weight(1f).then(if (mine) Modifier.clickable { onEditPayment(payment) } else Modifier),
                            )
                            if (mine) TextButton(onClick = { onDeletePayment(payment) }) { Text("Borrar") }
                        }
                    }
                }
                else -> item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (card.altLast4.isNotEmpty()) {
                                Text(
                                    "También aparece como: " + card.altLast4.joinToString { "•••• $it" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            BudgetLine(card = card, spend = spend, mine = mine)
                            BalanceCheckLine(appDebt = debt, check = balanceCheck, mine = mine, onCapture = onCaptureBalance)
                            if (mine) {
                                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onToggleFrozen) { Text(if (card.frozen) "Descongelar" else "Congelar") }
                                    OutlinedButton(onClick = onArchive) { Text("Archivar tarjeta") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (mine && status != null) {
            GradientButton(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 4.dp),
            ) { Text("Pagar tarjeta") }
        }
    }
}

@Composable
private fun InfoChip(text: String, tint: androidx.compose.ui.graphics.Color?) {
    val color = tint ?: MaterialTheme.colorScheme.onSurface
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (tint != null) FontWeight.Bold else FontWeight.SemiBold,
        color = color,
        modifier = Modifier.clip(CircleShape)
            .background(if (tint != null) tint.copy(alpha = 0.18f) else AppTheme.tokens.glass)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DetailRow(title: String, subtitle: String, amount: String, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f))
            }
            Text(amount, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
