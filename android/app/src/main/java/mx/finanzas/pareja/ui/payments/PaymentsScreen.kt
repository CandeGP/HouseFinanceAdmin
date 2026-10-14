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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import mx.finanzas.pareja.ui.components.ScopeToggle

@Composable
fun PaymentsScreen(
    vm: ManualPaymentsViewModel,
    userId: String,
    memberNames: Map<String, String>,
    zone: TimeZone,
    refreshKey: Int,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }
    var pendingDelete by remember { mutableStateOf<ManualPayment?>(null) }

    val total = state.items.sumOf { it.amount }
    val withoutProof = state.items.count { it.evidencePath == null }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "Nuevo pago") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                    }
                    Text(formatMonthEs(state.year, state.month), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = vm::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                    }
                }
                ScopeToggle(scope = state.scope, onChange = vm::setScope)
            }
            Text(
                "Efectivo y transferencias: ${formatMxn(total)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (withoutProof > 0) {
                Text(
                    "$withoutProof sin comprobante",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            ErrorText(state.error, Modifier.padding(horizontal = 16.dp))

            if (state.items.isEmpty() && !state.loading) {
                EmptyState(
                    title = "Sin pagos este mes",
                    subtitle = "Registra pagos en efectivo o transferencia con su fecha, hora y comprobante.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
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

@Composable
private fun PaymentRow(
    payment: ManualPayment,
    zone: TimeZone,
    ownerName: String?,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val method = if (payment.method == PaymentMethod.CASH) "Efectivo" else "Transferencia"
    Card(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(payment.concept, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        "${formatDayMonthEs(localDate(payment.paidAt, zone))} ${timeText(payment.paidAt, zone)}",
                        method,
                        payment.counterparty,
                        ownerName?.let { "de $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (payment.evidencePath != null) {
                    Text("Con comprobante", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Sin comprobante", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                if (!payment.notes.isNullOrBlank()) Text(payment.notes, style = MaterialTheme.typography.bodySmall)
            }
            Text(formatMxn(payment.amount), style = MaterialTheme.typography.titleSmall)
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
            }
        }
    }
}
