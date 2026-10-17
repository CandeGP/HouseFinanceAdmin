package mx.finanzas.pareja.ui.installments

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import mx.finanzas.pareja.domain.MsiMonth
import mx.finanzas.pareja.domain.cardLabel
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.pendingInstallments
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.ScopeToggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsScreen(vm: InstallmentsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // El primer mes va abierto; el resto se despliega al tocarlo.
    var open by remember { mutableStateOf<Set<String>>(emptySet()) }
    val firstLabel = state.months.firstOrNull()?.label

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compras a meses") },
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                ScopeToggle(scope = state.scope, onChange = vm::setScope)
            }
            when {
                state.loading -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
                state.months.isEmpty() -> Text(
                    "No hay mensualidades pendientes. Las compras a meses que registres (a mano o desde un estado " +
                        "de cuenta) aparecen aquí, mes por mes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { SummaryCard(state.months) }
                    items(state.months, key = { it.label }) { month ->
                        val expanded = month.label in open || (open.isEmpty() && month.label == firstLabel)
                        MonthCard(
                            month = month,
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

@Composable
private fun SummaryCard(months: List<MsiMonth>) {
    val pending = pendingInstallments(months)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Te falta pagar de compras a meses", style = MaterialTheme.typography.labelLarge)
            Text(formatMxn(pending.amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "${pending.installments} mensualidades" + (pending.lastMonth?.let { " hasta ${it.label}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MonthCard(month: MsiMonth, expanded: Boolean, cards: Map<String, CardModel>, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
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
