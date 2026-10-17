package mx.finanzas.pareja.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.domain.CardRank
import mx.finanzas.pareja.domain.NextPayment
import mx.finanzas.pareja.domain.Quincena
import mx.finanzas.pareja.domain.QuincenaTotals
import mx.finanzas.pareja.domain.RecurringCharge
import mx.finanzas.pareja.domain.dueLine
import mx.finanzas.pareja.domain.sortByDue
import mx.finanzas.pareja.domain.formatDayMonthEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.debtTotal
import mx.finanzas.pareja.ui.components.CardChip
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.ScopeToggle

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    memberNames: Map<String, String>,
    refreshKey: Int,
    onAddCard: () -> Unit,
    onOpenInbox: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Resumen", style = MaterialTheme.typography.headlineSmall)
            ScopeToggle(scope = state.scope, onChange = vm::setScope)
        }
        ErrorText(state.error, Modifier.padding(horizontal = 16.dp))

        if (state.pendingCount > 0) {
            Card(
                onClick = onOpenInbox,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    "${state.pendingCount} cobro(s) detectado(s) por revisar",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        when {
            state.loading && state.summary.ranking.isEmpty() ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            state.summary.ranking.isEmpty() ->
                EmptyState(
                    title = "Aún no hay tarjetas",
                    subtitle = "Agrega tus tarjetas con su día de corte y de pago para ver cuánto pagarás.",
                    actionLabel = "Agregar tarjeta",
                    onAction = onAddCard,
                )
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { QuincenaCards(state.quincenas) }
                if (state.recurring.isNotEmpty()) item { RecurringCard(state.recurring) }
                items(sortByDue(state.summary.ranking, state.nextPayments), key = { it.view.card.id }) { rank ->
                    CardRankItem(
                        rank,
                        ownerName = memberNames[rank.view.card.ownerId],
                        payment = state.nextPayments[rank.view.card.id],
                        status = state.statuses[rank.view.card.id],
                    )
                }
            }
        }
    }
}

/** Lo que vence este mes, en dos bloques grandes: pagos del 1 al 15 y del 16 a fin de mes. */
@Composable
private fun QuincenaCards(totals: QuincenaTotals) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val nextFirstEnd = totals.nextFirstEnd
        QuincenaCard(
            title = if (nextFirstEnd != null) "Próximo mes a pagar 1ª quincena" else "A pagar 1ª quincena",
            range = if (nextFirstEnd != null) "Vence del 1 al ${formatDayMonthEs(nextFirstEnd)}" else "Vence del 1 al 15",
            quincena = totals.first,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        QuincenaCard(
            "A pagar 2ª quincena", "Vence del 16 a fin de mes", totals.second,
            MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuincenaCard(
    title: String,
    range: String,
    quincena: Quincena,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                formatMxn(quincena.total),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Text(range, style = MaterialTheme.typography.bodySmall)
            Text(
                when (quincena.cards) {
                    0 -> "Nada pendiente"
                    1 -> "1 tarjeta"
                    else -> "${quincena.cards} tarjetas"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (quincena.overdue > 0) {
                Text(
                    "Incluye ${formatMxn(quincena.overdue)} vencido",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val MAX_RECURRING_SHOWN = 5

/** Cargos que se repiten cada mes y se esperan pronto, con lo que suman. */
@Composable
private fun RecurringCard(charges: List<RecurringCharge>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Próximos cargos (estimado)", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatMxn(charges.sumOf { it.amount })} comprometidos",
                style = MaterialTheme.typography.titleLarge,
            )
            charges.take(MAX_RECURRING_SHOWN).forEach { charge ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        charge.merchant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "~${formatDayMonthEs(charge.nextExpected)} · ${formatMxn(charge.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (charges.size > MAX_RECURRING_SHOWN) {
                Text(
                    "y ${charges.size - MAX_RECURRING_SHOWN} más",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Cargos que se repiten cada mes en tus últimos gastos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardRankItem(rank: CardRank, ownerName: String?, payment: NextPayment?, status: CardCycleStatus?) {
    val card = rank.view.card
    val est = rank.view.estimate
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        CardChip(card, Modifier.weight(1f, fill = false))
                        if (ownerName != null) {
                            Text(
                                "de $ownerName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                // Lo que toca pagar (con el corte anterior sin pagar si lo hay), no el gasto del ciclo abierto.
                Text(
                    formatMxn(payment?.amount ?: est.cycleTotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            payment?.let { Text(it.dueLine(), style = MaterialTheme.typography.bodyMedium) }
            // Deuda total: corte anterior sin pagar + ciclo abierto + meses sin intereses que faltan.
            Text(
                "Debes en total: ${formatMxn(debtTotal(est, status))}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (status?.status == CardPayStatus.UNTRACKED) {
                Text(
                    "Corte anterior sin registro de pago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { (est.usagePct / 100).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Uso: ${"%.1f".format(est.usagePct)}% de ${formatMxn(est.creditLimit)} · " +
                    "${"%.0f".format(rank.sharePct)}% del gasto total",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
