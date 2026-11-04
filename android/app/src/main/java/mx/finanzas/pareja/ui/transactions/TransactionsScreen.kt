package mx.finanzas.pareja.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.data.Card as CardModel
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.domain.SpendingSlice
import mx.finanzas.pareja.domain.TxSort
import mx.finanzas.pareja.domain.cardLabel
import mx.finanzas.pareja.domain.dayTotals
import mx.finanzas.pareja.domain.formatDayMonthEs
import mx.finanzas.pareja.domain.formatMonthEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.spendingByCategory
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.Amount
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.MonthSummaryCard
import mx.finanzas.pareja.ui.components.ScopeToggle
import mx.finanzas.pareja.ui.components.UsageBar
import mx.finanzas.pareja.ui.dashboard.Scope

private val VIEWS = listOf("Movimientos", "Por categoría")

@Composable
fun TransactionsScreen(
    vm: TransactionsViewModel,
    userId: String,
    memberNames: Map<String, String>,
    refreshKey: Int,
    onEdit: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val cardsById = remember(state.cards) { state.cards.associateBy { it.id } }
    val categoryNames = remember(state.categories) { state.categories.associate { it.id to it.name } }
    val monthTotal = state.items.filter { it.status == TxStatus.CONFIRMED }.sumOf { it.amount }
    val totalsByDay = remember(state.items) { dayTotals(state.items) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterRow(
                scope = state.scope,
                onScope = vm::setScope,
                cards = state.cards.filter { !it.archived && (state.scope == Scope.BOTH || it.ownerId == userId) },
                cardFilter = state.cardFilter,
                sort = state.sort,
                showListFilters = tab == 0,
                onCard = vm::setCardFilter,
                onSort = vm::setSort,
            )
            MonthSummaryCard(
                monthLabel = formatMonthEs(state.year, state.month),
                count = state.items.size,
                total = monthTotal,
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
            )
            ErrorText(state.error, Modifier.padding(horizontal = 20.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VIEWS.forEachIndexed { index, title -> FilterPill(title, selected = tab == index, onClick = { tab = index }) }
            }

            when {
                state.items.isEmpty() && !state.loading ->
                    EmptyState(
                        title = "Sin gastos este mes",
                        subtitle = "Registra un gasto con el botón +.",
                    )
                tab == 0 -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Agrupar por día solo tiene sentido ordenado por fecha; con otro orden va una lista corrida.
                    var lastDay: LocalDate? = null
                    state.items.forEach { tx ->
                        if (state.sort == TxSort.DATE && tx.occurredAt != lastDay) {
                            val day = tx.occurredAt
                            item(key = "day-$day") { DayLabel(dayLabel(day, today), total = totalsByDay[day]) }
                        } else if (state.sort != TxSort.DATE && lastDay == null) {
                            item(key = "movements-header") { DayLabel("MOVIMIENTOS") }
                        }
                        lastDay = tx.occurredAt
                        item(key = tx.id) {
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
                }
                else -> SliceList(spendingByCategory(state.items, state.categories))
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

/** Ficha de filtro del mockup: píldora de 36 de alto; la elegida lleva borde de color y fondo tenue. */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, dropdown: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    val tokens = AppTheme.tokens
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) primary.copy(alpha = 0.25f) else tokens.glass,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) primary else tokens.glassBorder),
    ) {
        Row(
            Modifier.height(36.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
            if (dropdown) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

/** Alto de la píldora Míos/Ambos (36 + 4 + 4 de margen); los filtros se centran contra ella. */
private val SCOPE_TOGGLE_HEIGHT = 44.dp

/** Míos / Ambos y, en la lista, el filtro por tarjeta y el orden (por defecto, por fecha). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    scope: Scope,
    onScope: (Scope) -> Unit,
    cards: List<CardModel>,
    cardFilter: String?,
    sort: TxSort,
    showListFilters: Boolean,
    onCard: (String?) -> Unit,
    onSort: (TxSort) -> Unit,
) {
    var cardMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    // FlowRow: si no caben (letra grande, nombre de tarjeta largo) bajan a otra línea en vez de salirse de la pantalla.
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScopeToggle(scope, onScope)
        if (showListFilters) {
            Box(Modifier.height(SCOPE_TOGGLE_HEIGHT), contentAlignment = Alignment.CenterStart) {
                FilterPill(cards.firstOrNull { it.id == cardFilter }?.name ?: "Tarjeta", selected = cardFilter != null, onClick = { cardMenu = true }, dropdown = true)
                DropdownMenu(expanded = cardMenu, onDismissRequest = { cardMenu = false }) {
                    DropdownMenuItem(text = { Text("Todas") }, onClick = { onCard(null); cardMenu = false })
                    cards.forEach { card ->
                        DropdownMenuItem(text = { Text("${card.name} •••• ${card.last4}") }, onClick = { onCard(card.id); cardMenu = false })
                    }
                }
            }
            Box(Modifier.height(SCOPE_TOGGLE_HEIGHT), contentAlignment = Alignment.CenterStart) {
                FilterPill(if (sort == TxSort.DATE) "Orden" else sort.label, selected = sort != TxSort.DATE, onClick = { sortMenu = true }, dropdown = true)
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    TxSort.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { onSort(option); sortMenu = false })
                    }
                }
            }
        }
    }
}

/** Encabezado de un día con lo que se gastó en él (suma de todas las tarjetas) al lado contrario. */
@Composable
private fun DayLabel(text: String, total: Double? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 10.dp)
            // Un solo encabezado para el lector de pantalla: «Hoy, total $938.30».
            .semantics(mergeDescendants = true) {
                heading()
                if (total != null) contentDescription = "${text.lowercase()}, total ${formatMxn(total)}"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        total?.let { Text(formatMxn(it), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "HOY"
    today.minus(1, DateTimeUnit.DAY) -> "AYER"
    else -> formatDayMonthEs(day).uppercase()
}

/** Ícono y color de una categoría, deducidos de su nombre (el mockup lleva uno distinto por categoría). */
@Composable
private fun categoryVisual(name: String?): Pair<ImageVector, Color> {
    val chart = AppTheme.tokens.chart
    val n = name?.lowercase().orEmpty()
    return when {
        listOf("súper", "super", "compras", "mercado", "tienda").any { it in n } -> Icons.Outlined.ShoppingCart to chart[0]
        listOf("comida", "restaurante", "café", "cafe").any { it in n } -> Icons.Outlined.Restaurant to chart[1]
        listOf("transporte", "gasolina", "auto", "uber").any { it in n } -> Icons.Outlined.DirectionsCar to chart[2]
        listOf("juego", "entreten", "suscrip", "ocio", "cine").any { it in n } -> Icons.Outlined.Movie to chart[3]
        listOf("casa", "hogar", "renta", "servicio").any { it in n } -> Icons.Outlined.Home to chart[4]
        name == null -> Icons.Outlined.Receipt to chart[3]
        else -> Icons.Outlined.Category to chart[0]
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** Un gasto: ícono de su categoría, comercio, tarjeta y monto. Tocar edita; mantener presionado elimina. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    tx: Transaction,
    card: CardModel?,
    categoryName: String?,
    ownerName: String?,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val (icon, tint) = categoryVisual(categoryName)
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .semantics { if (onDelete != null) customActions = listOf(CustomAccessibilityAction("Eliminar") { onDelete(); true }) }
            .then(
                if (onClick != null || onDelete != null) {
                    Modifier.combinedClickable(
                        enabled = true,
                        onClickLabel = "Editar gasto",
                        onClick = { onClick?.invoke() },
                        onLongClickLabel = "Eliminar gasto",
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
            IconTile(icon, tint)
            Column(Modifier.weight(1f)) {
                Text(tx.merchant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    listOfNotNull(card?.let { cardLabel(it) }, categoryName, ownerName?.let { "de $it" }).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tx.paymentMode == PaymentMode.MSI) {
                    Text(
                        "MSI · mensualidad ${tx.installmentNumber ?: 1} de ${tx.installments}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!tx.notes.isNullOrBlank()) {
                    Text(tx.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            // Sin el «−» (en Gastos todo es gasto); el lector de pantalla lo dice con palabras.
            Text(
                formatMxn(tx.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "gasto de ${formatMxn(tx.amount)}" },
            )
        }
    }
}

@Composable
private fun SliceList(slices: List<SpendingSlice>) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(slices, key = { it.label }) { slice ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(slice.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${formatMxn(slice.total)} · ${"%.0f".format(slice.pct)}%", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    UsageBar(
                        fraction = (slice.pct / 100).coerceIn(0.0, 1.0).toFloat(),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
