package mx.finanzas.pareja.ui.installments

import mx.finanzas.pareja.ui.components.LocalDockInset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import mx.finanzas.pareja.ui.components.GlassCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.data.Card as CardModel
import mx.finanzas.pareja.data.MsiInstallment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.domain.MsiMonth
import mx.finanzas.pareja.domain.activePurchases
import mx.finanzas.pareja.domain.cardLabel
import mx.finanzas.pareja.domain.compactMxn
import mx.finanzas.pareja.ui.components.SectionLabel
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.pendingInstallments
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.ScopeToggle
import mx.finanzas.pareja.ui.components.UsageBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsScreen(vm: InstallmentsViewModel, onBack: (() -> Unit)?) {
    val state by vm.state.collectAsStateWithLifecycle()
    // El primer mes va abierto; el resto se despliega al tocarlo.
    var open by remember { mutableStateOf<Set<String>>(emptySet()) }
    val firstLabel = state.months.firstOrNull()?.label

    Scaffold(
        // Sin barra superior cuando va como pestaña dentro de Gastos (no hay a dónde volver).
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Compras a meses") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ErrorText(state.message, Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                ScopeToggle(scope = state.scope, onChange = vm::setScope)
            }
            when {
                state.loading -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
                state.months.isEmpty() -> EmptyState(
                    title = "Sin mensualidades pendientes",
                    subtitle = "Las compras a meses que registres (a mano o desde un estado de cuenta) aparecen aquí, mes por mes.",
                    icon = Icons.Outlined.CalendarMonth,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalDockInset.current),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { SummaryCard(state.months) }
                    item { SectionLabel("COMPRAS ACTIVAS", Modifier.padding(top = 8.dp)) }
                    items(activePurchases(state.months), key = { "active-${it.transactionId}" }) { purchase ->
                        ActivePurchaseCard(purchase, state.cards[purchase.cardId])
                    }
                    item { SectionLabel("POR MES", Modifier.padding(top = 8.dp)) }
                    items(state.months, key = { it.label }) { month ->
                        val expanded = month.label in open || (open.isEmpty() && month.label == firstLabel)
                        MonthCard(
                            month = month,
                            maxTotal = state.months.maxOf { it.total },
                            expanded = expanded,
                            cards = state.cards,
                            onToggle = {
                                val current = if (open.isEmpty() && firstLabel != null) setOf(firstLabel) else open
                                open = if (month.label in current) current - month.label else current + month.label
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val BAR_MONTHS = 5
private val BAR_MAX_HEIGHT = 96.dp
private val BAR_MIN_HEIGHT = 14.dp

/** Barras de lo que se paga de compras a meses en los próximos meses; la del mes más cercano va resaltada. */
@Composable
private fun MonthBars(months: List<MsiMonth>) {
    if (months.size < 2) return // con un solo mes una barra no dice nada
    val maxTotal = months.maxOf { it.total }.coerceAtLeast(1.0)
    val tokens = AppTheme.tokens
    val spoken = months.joinToString(", ") { "${it.label}: ${formatMxn(it.total)}" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    // Las barras son dibujo: el lector de pantalla lee los montos de cada mes completos.
                    .clearAndSetSemantics { contentDescription = "Mensualidades por mes. $spoken" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                months.forEachIndexed { index, month ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(compactMxn(month.total), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.fillMaxWidth()
                                .height((BAR_MAX_HEIGHT * (month.total / maxTotal).toFloat()).coerceAtLeast(BAR_MIN_HEIGHT))
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (index == 0) tokens.accent
                                    else SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                                ),
                        )
                        Text(
                            month.label.take(3).replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
    }
}

/** Una compra a meses que sigue en pie: su siguiente mensualidad y cuánto lleva pagado. */
@Composable
private fun ActivePurchaseCard(item: MsiInstallment, card: CardModel?) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.merchant, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
                Text(formatMxn(item.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                (card?.let { cardLabel(it) } ?: "Tarjeta") + " · mensualidad ${item.number} de ${item.installments}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            UsageBar(fraction = item.number.toFloat() / item.installments)
        }
    }
}

@Composable
private fun SummaryCard(months: List<MsiMonth>) {
    val pending = pendingInstallments(months)
    // Una sola tarjeta (como en el mockup): lo que falta pagar arriba y, debajo, las barras de los próximos meses.
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Te falta pagar de compras a meses",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(formatMxn(pending.amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${pending.installments} mensualidades", style = MaterialTheme.typography.bodyMedium)
                }
                pending.lastMonth?.let {
                    Text(
                        "Hasta ${it.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.tokens.chart[0],
                        modifier = Modifier.padding(start = 8.dp)
                            .background(AppTheme.tokens.chart[0].copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            MonthBars(months.take(BAR_MONTHS))
        }
    }
}

@Composable
private fun MonthCard(month: MsiMonth, maxTotal: Double, expanded: Boolean, cards: Map<String, CardModel>, onToggle: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(month.label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${month.items.size} mensualidades" +
                            if (month.finishing > 0) " · ${month.finishing} termina${if (month.finishing > 1) "n" else ""}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatMxn(month.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            UsageBar(fraction = if (maxTotal > 0) (month.total / maxTotal).toFloat() else 0f)
            if (expanded) month.items.forEach { InstallmentRow(it, cards[it.cardId]) }
        }
    }
}

@Composable
private fun InstallmentRow(item: MsiInstallment, card: CardModel?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.merchant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                (card?.let { cardLabel(it) } ?: "Tarjeta") + " · ${item.number} de ${item.installments}" +
                    if (item.isLast) " · última" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(formatMxn(item.amount), style = MaterialTheme.typography.bodyMedium)
    }
}
