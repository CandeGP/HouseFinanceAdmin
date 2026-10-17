package mx.finanzas.pareja.ui.duplicates

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxSource
import mx.finanzas.pareja.domain.cardLabel
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.ErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(vm: DuplicatesViewModel, userId: String, onBack: () -> Unit, onChanged: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Posibles duplicados") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ErrorText(state.message, Modifier.padding(horizontal = 16.dp))
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                state.pairs.isEmpty() -> Text(
                    "No hay cargos que parezcan duplicados en los últimos $DUPLICATES_LOOKBACK_DAYS días.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.pairs, key = { it.key }) { pair ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(pair.first, pair.second).forEach { tx ->
                                    TxLine(tx, state.cards[tx.cardId]?.let { cardLabel(it) })
                                    if (tx.ownerId == userId) {
                                        OutlinedButton(onClick = { vm.delete(pair, tx.id); onChanged() }) { Text("Borrar este") }
                                    }
                                }
                                TextButton(onClick = { vm.dismiss(pair) }) { Text("No es duplicado") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxLine(tx: Transaction, card: String?) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tx.merchant, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(formatMxn(tx.amount), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "${tx.occurredAt} · ${card ?: "Sin tarjeta"} · ${sourceLabel(tx.source)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sourceLabel(source: TxSource) = when (source) {
    TxSource.MANUAL -> "a mano"
    TxSource.NOTIFICATION -> "notificación"
    TxSource.EMAIL -> "correo"
    TxSource.STATEMENT -> "estado de cuenta"
}
