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
import mx.finanzas.pareja.domain.CardRank
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.ScopeToggle

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    memberNames: Map<String, String>,
    refreshKey: Int,
    onAddCard: () -> Unit,
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
                item { TotalsCard(state.summary.totalCycle, state.summary.totalMsiRemaining, state.summary.topCard?.name) }
                items(state.summary.ranking, key = { it.view.card.id }) { rank ->
                    CardRankItem(rank, ownerName = memberNames[rank.view.card.ownerId])
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(totalCycle: Double, msiRemaining: Double, topCardName: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("A pagar este ciclo (estimado)", style = MaterialTheme.typography.labelLarge)
            Text(formatMxn(totalCycle), style = MaterialTheme.typography.headlineMedium)
            Text("MSI pendiente por pagar: ${formatMxn(msiRemaining)}", style = MaterialTheme.typography.bodyMedium)
            if (topCardName != null) {
                Text("Tarjeta con más gasto: $topCardName", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CardRankItem(rank: CardRank, ownerName: String?) {
    val card = rank.view.card
    val est = rank.view.estimate
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("#${rank.rank} ${card.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${card.bank} •••• ${card.last4}" + (ownerName?.let { " · de $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatMxn(est.cycleTotal), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Corte: ${formatDateEs(est.cycleEnd)} · Pago: ${formatDateEs(est.dueDate)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (est.cycleMsiTotal > 0) {
                Text(
                    "Incluye ${formatMxn(est.cycleMsiTotal)} de mensualidades · Saldo MSI: ${formatMxn(est.msiRemaining)}",
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
