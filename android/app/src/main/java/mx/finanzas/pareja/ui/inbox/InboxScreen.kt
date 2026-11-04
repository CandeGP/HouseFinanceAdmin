package mx.finanzas.pareja.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxSource
import mx.finanzas.pareja.domain.categoryEmoji
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.CardChip
import mx.finanzas.pareja.ui.components.DropdownField
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.GradientButton

/** Tarjeta y categoría que se aplicarán a un cobro al aprobarlo (lo detectado, o lo que la persona cambió). */
private data class Choice(val cardId: String?, val categoryId: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(vm: InboxViewModel, onBack: () -> Unit, onChanged: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Lo elegido por cobro vive aquí para que «Aprobar los N» use también lo que se editó.
    val choices = remember { mutableStateMapOf<String, Choice>() }
    fun choiceOf(tx: Transaction) = choices[tx.id] ?: Choice(tx.cardId, tx.categoryId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Por revisar") },
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
                Text(
                    "${state.items.size} ${if (state.items.size == 1) "cargo detectado" else "cargos detectados"}. " +
                        "Apruébalos para sumarlos a Gastos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { tx ->
                        val choice = choiceOf(tx)
                        PendingItem(
                            tx = tx,
                            state = state,
                            choice = choice,
                            onChoice = { choices[tx.id] = it },
                            onConfirm = {
                                vm.confirm(tx.id, choice.cardId, choice.categoryId)
                                onChanged()
                            },
                            onDiscard = {
                                vm.discard(tx.id)
                                onChanged()
                            },
                        )
                    }
                }
                if (state.items.size > 1) {
                    GradientButton(
                        onClick = {
                            vm.confirmAll(state.items.map { tx -> choiceOf(tx).let { ConfirmEntry(tx.id, it.cardId, it.categoryId) } })
                            onChanged()
                        },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) { Text("Aprobar los ${state.items.size}") }
                }
            }
        }
    }
}

private fun sourceLabel(source: TxSource) = when (source) {
    TxSource.NOTIFICATION -> "Aviso del banco"
    TxSource.EMAIL -> "Correo"
    TxSource.STATEMENT -> "Estado de cuenta"
    TxSource.MANUAL -> "Manual"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingItem(
    tx: Transaction,
    state: InboxUiState,
    choice: Choice,
    onChoice: (Choice) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    // Si la captura trajo tarjeta (últimos 4), llega preseleccionada; sin ella, se pide al editar.
    var editing by remember(tx.id) { mutableStateOf(false) }
    LaunchedEffect(tx.cardId) { if (choice.cardId == null && tx.cardId != null) onChoice(choice.copy(cardId = tx.cardId)) }

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

    val card = state.cards.firstOrNull { it.id == choice.cardId }
    val category = state.categories.firstOrNull { it.id == choice.categoryId }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(tx.merchant, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${sourceLabel(tx.source)} · ${formatDateEs(tx.occurredAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatMxn(tx.amount), style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card != null) CardChip(card) else Text(
                    "Falta la tarjeta",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // El emoji es decorativo: el lector de pantalla dice solo el nombre.
                    category?.let { Text(categoryEmoji(it.icon), fontSize = 14.sp, modifier = Modifier.clearAndSetSemantics { }) }
                    Text(category?.name ?: "Sin categoría", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (editing) {
                DropdownField(
                    label = "Tarjeta",
                    selectedLabel = card?.let { "${it.name} •••• ${it.last4}" }.orEmpty(),
                    options = state.cards,
                    optionLabel = { "${it.name} •••• ${it.last4}" },
                    onSelect = { onChoice(choice.copy(cardId = it?.id)) },
                )
                DropdownField(
                    label = "Categoría",
                    selectedLabel = category?.name ?: "Sin categoría",
                    options = state.categories,
                    optionLabel = { it.name },
                    onSelect = { onChoice(choice.copy(categoryId = it?.id)) },
                    noneLabel = "Sin categoría",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientButton(
                    onClick = { if (choice.cardId == null) editing = true else onConfirm() },
                    modifier = Modifier.weight(1f),
                ) { Text("Aprobar") }
                OutlinedButton(onClick = { editing = !editing }, modifier = Modifier.height(58.dp)) {
                    Text(if (editing) "Listo" else "Editar")
                }
                OutlinedButton(onClick = { confirmDiscard = true }, modifier = Modifier.height(58.dp)) { Text("Descartar") }
            }
        }
    }
}
