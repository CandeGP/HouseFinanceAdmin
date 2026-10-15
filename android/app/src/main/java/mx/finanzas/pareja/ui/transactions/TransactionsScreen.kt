package mx.finanzas.pareja.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.data.Card as CardModel
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.domain.SpendingSlice
import mx.finanzas.pareja.domain.formatDayMonthEs
import mx.finanzas.pareja.domain.formatMonthEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.spendingByCard
import mx.finanzas.pareja.domain.spendingByCategory
import mx.finanzas.pareja.ui.components.CardChip
import mx.finanzas.pareja.ui.components.CategoryTag
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.ScopeToggle

private val TABS = listOf("Movimientos", "Por categoría", "Por tarjeta")

@Composable
fun TransactionsScreen(
    vm: TransactionsViewModel,
    userId: String,
    memberNames: Map<String, String>,
    refreshKey: Int,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    val cardsById = remember(state.cards) { state.cards.associateBy { it.id } }
    val categoryNames = remember(state.categories) { state.categories.associate { it.id to it.name } }
    val monthTotal = state.items.filter { it.status == TxStatus.CONFIRMED }.sumOf { it.amount }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "Nuevo gasto") }
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
                "Gastado en el mes: ${formatMxn(monthTotal)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ErrorText(state.error, Modifier.padding(horizontal = 16.dp))

            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }

            when {
                state.items.isEmpty() && !state.loading ->
                    EmptyState(
                        title = "Sin gastos este mes",
                        subtitle = "Registra un gasto con el botón +.",
                    )
                tab == 0 -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { tx ->
                        val mine = tx.ownerId == userId
                        TransactionRow(
                            tx = tx,
                            card = cardsById[tx.cardId],
                            categoryName = categoryNames[tx.categoryId],
                            ownerName = if (mine) null else memberNames[tx.ownerId],
                            onClick = if (mine) ({ onEdit(tx.id) }) else null,
                            onDelete = if (mine) ({ pendingDelete = tx }) else null,
                        )
                    }
                }
                tab == 1 -> SliceList(spendingByCategory(state.items, state.categories))
                else -> SliceList(spendingByCard(state.items, state.cards))
            }
        }
    }

    pendingDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Eliminar gasto?") },
            text = { Text("${tx.merchant} · ${formatMxn(tx.amount)}") },
            confirmButton = {
                TextButton(onClick = { vm.delete(tx.id); pendingDelete = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionRow(
    tx: Transaction,
    card: CardModel?,
    categoryName: String?,
    ownerName: String?,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tx.merchant, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(formatDayMonthEs(tx.occurredAt), ownerName?.let { "de $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (card != null) CardChip(card)
                    CategoryTag(categoryName)
                }
                if (tx.paymentMode == PaymentMode.MSI) {
                    Text(
                        "MSI · mensualidad ${tx.installmentNumber ?: 1} de ${tx.installments}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!tx.notes.isNullOrBlank()) {
                    Text(tx.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(formatMxn(tx.amount), style = MaterialTheme.typography.titleSmall)
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
            }
        }
    }
}

@Composable
private fun SliceList(slices: List<SpendingSlice>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(slices, key = { it.label }) { slice ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(slice.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${formatMxn(slice.total)} · ${"%.0f".format(slice.pct)}%",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                LinearProgressIndicator(
                    progress = { (slice.pct / 100).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
