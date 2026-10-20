package mx.finanzas.pareja.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
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
import mx.finanzas.pareja.data.MsiInstallment
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.domain.SpendingSlice
import mx.finanzas.pareja.domain.TxSort
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
import mx.finanzas.pareja.ui.dashboard.Scope

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
            if (state.installments.isNotEmpty()) {
                Text(
                    "Mensualidades de compras a meses: ${formatMxn(state.installments.sumOf { it.amount })}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            ErrorText(state.error, Modifier.padding(horizontal = 16.dp))
            FilterBar(
                cards = state.cards.filter { !it.archived && (state.scope == Scope.BOTH || it.ownerId == userId) },
                cardFilter = state.cardFilter,
                sort = state.sort,
                onCard = vm::setCardFilter,
                onSort = vm::setSort,
            )

            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }

            when {
                state.items.isEmpty() && state.installments.isEmpty() && !state.loading ->
                    EmptyState(
                        title = "Sin gastos este mes",
                        subtitle = "Registra un gasto con el botón +.",
                    )
                tab == 0 -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.installments.isNotEmpty()) {
                        item(key = "installments-header") {
                            Text(
                                "Mensualidades programadas",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(state.installments, key = { "inst-${it.transactionId}-${it.dueDate}" }) { inst ->
                            ScheduledInstallmentRow(inst, cardsById[inst.cardId])
                        }
                        if (state.items.isNotEmpty()) {
                            item(key = "movements-header") {
                                Text(
                                    "Movimientos",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
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

/** Filtro por tarjeta y orden de los gastos (por defecto, por fecha). */
@Composable
private fun FilterBar(
    cards: List<CardModel>,
    cardFilter: String?,
    sort: TxSort,
    onCard: (String?) -> Unit,
    onSort: (TxSort) -> Unit,
) {
    var cardMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { cardMenu = true }) {
                Text("Tarjeta: " + (cards.firstOrNull { it.id == cardFilter }?.name ?: "todas"), maxLines = 1)
            }
            DropdownMenu(expanded = cardMenu, onDismissRequest = { cardMenu = false }) {
                DropdownMenuItem(text = { Text("Todas") }, onClick = { onCard(null); cardMenu = false })
                cards.forEach { card ->
                    DropdownMenuItem(text = { Text("${card.name} •••• ${card.last4}") }, onClick = { onCard(card.id); cardMenu = false })
                }
            }
        }
        Box {
            OutlinedButton(onClick = { sortMenu = true }) { Text("Orden: " + sort.label.lowercase(), maxLines = 1) }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                TxSort.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { onSort(option); sortMenu = false })
                }
            }
        }
    }
}

/** Mensualidad futura de una compra a meses: no es un gasto nuevo, es lo que toca pagar ese mes. */
@Composable
private fun ScheduledInstallmentRow(item: MsiInstallment, card: CardModel?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.merchant, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "Vence ${formatDayMonthEs(item.dueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card != null) CardChip(card)
                Text(
                    "MSI · mensualidad ${item.number} de ${item.installments}" + if (item.isLast) " · última" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(formatMxn(item.amount), style = MaterialTheme.typography.titleSmall)
        }
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
