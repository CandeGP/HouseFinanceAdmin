package mx.finanzas.pareja.ui.payments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import mx.finanzas.pareja.ui.components.GlassCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.TimeZone
import mx.finanzas.pareja.data.ManualPayment
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.formatDayMonthEs
import mx.finanzas.pareja.domain.formatMonthEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.localDate
import mx.finanzas.pareja.domain.timeText
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.MonthSummaryCard
import mx.finanzas.pareja.ui.components.SelectChip
import mx.finanzas.pareja.ui.dashboard.Scope
import mx.finanzas.pareja.ui.components.ScopeToggle

@Composable
fun PaymentsScreen(
    vm: ManualPaymentsViewModel,
    userId: String,
    memberNames: Map<String, String>,
    zone: TimeZone,
    refreshKey: Int,
    onEdit: (String) -> Unit,
    onOpenLoans: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }
    var pendingDelete by remember { mutableStateOf<ManualPayment?>(null) }

    val total = state.items.sumOf { it.amount }
    val withoutProof = state.items.count { it.evidencePath == null }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectChip("Míos", state.scope == Scope.MINE, { vm.setScope(Scope.MINE) })
                SelectChip("Ambos", state.scope == Scope.BOTH, { vm.setScope(Scope.BOTH) })
            }
            MonthSummaryCard(
                monthLabel = formatMonthEs(state.year, state.month),
                count = state.items.size,
                total = total,
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
                noun = "pagos",
            )
            GlassCard(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                onClick = onOpenLoans,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).heightIn(min = 54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = AppTheme.tokens.chart[0])
                    Text("Préstamos entre personas", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            if (withoutProof > 0) {
                Text(
                    "$withoutProof sin comprobante",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 22.dp, top = 8.dp),
                )
            }
            ErrorText(state.error, Modifier.padding(horizontal = 20.dp))

            if (state.items.isEmpty() && !state.loading) {
                EmptyState(
                    title = "Sin pagos este mes",
                    subtitle = "Registra pagos en efectivo o transferencia con su fecha, hora y comprobante.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { payment ->
                        val mine = payment.ownerId == userId
                        PaymentRow(
                            payment = payment,
                            zone = zone,
                            ownerName = if (mine) null else memberNames[payment.ownerId],
                            onClick = if (mine) ({ onEdit(payment.id) }) else null,
                            onDelete = if (mine) ({ pendingDelete = payment }) else null,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Eliminar pago?") },
            text = { Text("${payment.concept} · ${formatMxn(payment.amount)}. También se borra su comprobante.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(payment.id); pendingDelete = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Un pago: palomita verde en recuadro, concepto, fecha y método, comprobante y monto. Mantener presionado elimina. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaymentRow(
    payment: ManualPayment,
    zone: TimeZone,
    ownerName: String?,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val method = if (payment.method == PaymentMethod.CASH) "Efectivo" else "Transferencia"
    val success = AppTheme.tokens.success
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .semantics { if (onDelete != null) customActions = listOf(CustomAccessibilityAction("Eliminar") { onDelete(); true }) }
            .then(
                if (onClick != null || onDelete != null) {
                    Modifier.combinedClickable(
                        onClickLabel = "Editar pago",
                        onClick = { onClick?.invoke() },
                        onLongClickLabel = "Eliminar pago",
                        onLongClick = onDelete,
                    )
                } else Modifier,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(success.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = success, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text(payment.concept, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    listOfNotNull(
                        "${formatDayMonthEs(localDate(payment.paidAt, zone))} ${timeText(payment.paidAt, zone)}",
                        method,
                        payment.counterparty,
                        ownerName?.let { "de $it" },
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (payment.evidencePath != null) {
                    Text("Con comprobante", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Sin comprobante", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                if (!payment.notes.isNullOrBlank()) Text(payment.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Text(formatMxn(payment.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
