package mx.finanzas.pareja.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.DropdownField
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(vm: InboxViewModel, onBack: () -> Unit, onChanged: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cobros por revisar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ErrorText(state.message, Modifier.padding(horizontal = 16.dp))
            if (state.items.isEmpty() && !state.loading) {
                EmptyState(
                    title = "Todo al día",
                    subtitle = "Los cobros que detecte la app aparecerán aquí para que confirmes la tarjeta y la categoría.",
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { tx ->
                        PendingItem(
                            tx = tx,
                            state = state,
                            onConfirm = { cardId, categoryId ->
                                vm.confirm(tx.id, cardId, categoryId)
                                onChanged()
                            },
                            onDiscard = {
                                vm.discard(tx.id)
                                onChanged()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingItem(
    tx: Transaction,
    state: InboxUiState,
    onConfirm: (cardId: String?, categoryId: String?) -> Unit,
    onDiscard: () -> Unit,
) {
    var cardId by remember(tx.id) { mutableStateOf(tx.cardId) }
    var categoryId by remember(tx.id) { mutableStateOf(tx.categoryId) }

    // Si la captura trajo tarjeta (últimos 4), llega preseleccionada.
    LaunchedEffect(tx.cardId) { if (cardId == null) cardId = tx.cardId }

    var confirmDiscard by remember(tx.id) { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar este cobro?") },
            text = { Text("${tx.merchant} · ${formatMxn(tx.amount)}\nSe elimina y no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } },
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(tx.merchant, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatDateEs(tx.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatMxn(tx.amount), style = MaterialTheme.typography.titleMedium)
            }
            DropdownField(
                label = "Tarjeta",
                selectedLabel = state.cards.firstOrNull { it.id == cardId }
                    ?.let { "${it.name} •••• ${it.last4}" }.orEmpty(),
                options = state.cards,
                optionLabel = { "${it.name} •••• ${it.last4}" },
                onSelect = { cardId = it?.id },
            )
            DropdownField(
                label = "Categoría",
                selectedLabel = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Sin categoría",
                options = state.categories,
                optionLabel = { it.name },
                onSelect = { categoryId = it?.id },
                noneLabel = "Sin categoría",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConfirm(cardId, categoryId) }) { Text("Confirmar") }
                OutlinedButton(onClick = { confirmDiscard = true }) { Text("Descartar") }
            }
        }
    }
}
