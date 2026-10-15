package mx.finanzas.pareja.ui.cards

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardField
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText

@Composable
fun CardsScreen(
    vm: CardsViewModel,
    userId: String,
    memberNames: Map<String, String>,
    openAddOnStart: Boolean,
    onChanged: () -> Unit,
    onImportStatement: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(openAddOnStart) }
    var pendingArchive by remember { mutableStateOf<mx.finanzas.pareja.data.Card?>(null) }

    LaunchedEffect(state.added) {
        if (state.added) {
            showForm = false
            vm.consumeAdded()
            onChanged()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar tarjeta")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Tarjetas",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ErrorText(state.message, Modifier.padding(horizontal = 16.dp))

            OutlinedButton(
                onClick = onImportStatement,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Importar estado de cuenta (PDF)") }

            if (state.cards.isEmpty() && !state.loading) {
                EmptyState(
                    title = "Sin tarjetas",
                    subtitle = "Agrega tus tarjetas de crédito con el botón +.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.cards, key = { it.id }) { card ->
                        val mine = card.ownerId == userId
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(card.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${card.bank} •••• ${card.last4}" +
                                            (if (mine) "" else " · de ${memberNames[card.ownerId] ?: "tu pareja"}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (card.altLast4.isNotEmpty()) {
                                        Text(
                                            "También aparece como: " + card.altLast4.joinToString { "•••• $it" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "Corte día ${card.cutoffDay} · Pago día ${card.dueDay} · " +
                                            "Límite ${formatMxn(card.creditLimit)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (mine) {
                                    IconButton(onClick = { pendingArchive = card }) {
                                        Icon(Icons.Filled.Archive, contentDescription = "Archivar tarjeta")
                                    }
                                }
                            }
                        }
                    }
                    if (state.archived.isNotEmpty()) {
                        item {
                            Text(
                                "Archivadas (${state.archived.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                        items(state.archived, key = { "archived-${it.id}" }) { card ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${card.bank} •••• ${card.last4} · conserva sus gastos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (card.ownerId == userId) {
                                        TextButton(onClick = { vm.restore(card.id); onChanged() }) { Text("Restaurar") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingArchive?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("¿Archivar esta tarjeta?") },
            text = {
                Text(
                    "${card.name} •••• ${card.last4}\n" +
                        "Se oculta de Resumen, Gastos y Tarjetas, pero sus gastos y estados de cuenta " +
                        "se conservan. La puedes restaurar en \"Archivadas\".",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.archive(card.id); onChanged(); pendingArchive = null }) { Text("Archivar") }
            },
            dismissButton = { TextButton(onClick = { pendingArchive = null }) { Text("Cancelar") } },
        )
    }

    if (showForm) {
        CardFormDialog(
            errors = state.errors,
            onDismiss = { showForm = false },
            onSave = vm::addCard,
        )
    }
}

@Composable
private fun CardFormDialog(
    errors: Map<CardField, String>,
    onDismiss: () -> Unit,
    onSave: (CardDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(CardDraft()) }

    @Composable
    fun field(
        label: String,
        value: String,
        field: CardField,
        keyboard: KeyboardType = KeyboardType.Text,
        onChange: (String) -> Unit,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            isError = errors[field] != null,
            supportingText = errors[field]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarjeta") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field("Nombre (ej. Nu azul)", draft.name, CardField.NAME) { draft = draft.copy(name = it) }
                field("Banco", draft.bank, CardField.BANK) { draft = draft.copy(bank = it) }
                field("Últimos 4 dígitos", draft.last4, CardField.LAST4, KeyboardType.Number) { draft = draft.copy(last4 = it.take(4)) }
                field("Límite de crédito", draft.limitText, CardField.LIMIT, KeyboardType.Decimal) { draft = draft.copy(limitText = it) }
                field("Día de corte (1-31)", draft.cutoffText, CardField.CUTOFF, KeyboardType.Number) { draft = draft.copy(cutoffText = it) }
                field("Día de pago (1-31)", draft.dueText, CardField.DUE, KeyboardType.Number) { draft = draft.copy(dueText = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
