package mx.finanzas.pareja.ui.statements

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mx.finanzas.pareja.domain.StatementSummary
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.DropdownField
import mx.finanzas.pareja.ui.components.ErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportScreen(
    vm: StatementImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }

    LaunchedEffect(state.step) { if (state.step == ImportStep.DONE) onImported() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            reading = true
            val content = extractPdfText(context, uri)
            reading = false
            if (content == null) vm.reportUnreadable() else vm.onPdfRead(content.text, content.bytes)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estado de cuenta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorText(state.message)

            when (state.step) {
                ImportStep.PICK -> {
                    Text(
                        "Sube el PDF de tu estado de cuenta. Se lee en tu teléfono, se compara con tus gastos " +
                            "y te muestra qué falta, sin cambiar nada hasta que confirmes.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { pick.launch(arrayOf("application/pdf")) },
                        enabled = !reading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (reading) "Leyendo PDF…" else "Elegir PDF") }
                }

                ImportStep.REVIEW -> ReviewContent(state, vm, onPickAnother = { vm.reset() })

                ImportStep.DONE -> {
                    val r = state.result
                    Text("Listo", style = MaterialTheme.typography.titleLarge)
                    Text("Se importaron ${r?.movements ?: 0} movimiento(s) y ${r?.installments ?: 0} compra(s) a meses.")
                    Text(
                        "La tarjeta quedó con el corte, pago y límite de tu estado de cuenta.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable
private fun ReviewContent(
    state: StatementImportUiState,
    vm: StatementImportViewModel,
    onPickAnother: () -> Unit,
) {
    val parsed = state.parsed ?: return
    SummaryCard(parsed.summary)

    DropdownField(
        label = "Tarjeta de la app",
        selectedLabel = state.cards.firstOrNull { it.id == state.cardId }
            ?.let { "${it.name} •••• ${it.last4}" }.orEmpty(),
        options = state.cards,
        optionLabel = { "${it.name} •••• ${it.last4}" },
        onSelect = { it?.let { card -> vm.selectCard(card.id) } },
    )

    val rec = state.reconciliation
    if (rec == null) {
        Text(
            "Elige la tarjeta para comparar el estado con tus gastos.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onPickAnother) { Text("Elegir otro PDF") }
        return
    }

    Section("Ya registrados", "${rec.matched.size} cargo(s) coinciden con lo que ya tienes.")

    if (rec.missingInApp.isNotEmpty()) {
        Text("Faltan en la app (${rec.missingInApp.size})", style = MaterialTheme.typography.titleSmall)
        rec.missingInApp.forEachIndexed { index, mv ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = index !in state.excluded, onCheckedChange = { vm.toggleMovement(index) })
                Column(Modifier.weight(1f)) {
                    Text(mv.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatDateEs(mv.operationDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatMxn(mv.amount), style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        Section("Faltan en la app", "Nada: todo lo del estado ya está registrado.")
    }

    if (rec.missingInstallments.isNotEmpty()) {
        Text("Compras a meses por registrar (${rec.missingInstallments.size})", style = MaterialTheme.typography.titleSmall)
        rec.missingInstallments.forEach { i ->
            Text(
                "${i.description} · pago ${i.current} de ${i.total} · ${formatMxn(i.originalAmount)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (rec.matchedInstallments.isNotEmpty()) {
        Section("Compras a meses ya registradas", "${rec.matchedInstallments.size} coinciden por monto original.")
    }

    if (rec.notInStatement.isNotEmpty()) {
        Text(
            "En la app pero no en este estado (${rec.notInStatement.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        rec.notInStatement.forEach { tx ->
            Text(
                "${formatDateEs(tx.occurredAt)} · ${tx.merchant} · ${formatMxn(tx.amount)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Pueden caer en el siguiente corte, o ser un duplicado o un error de captura.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (rec.payments.isNotEmpty()) {
        Section(
            "Pagos y abonos",
            "${rec.payments.size} por ${formatMxn(-rec.payments.sumOf { it.amount })} (no se importan como gasto).",
        )
    }

    Text(
        "Al importar, la tarjeta se ajusta al corte, pago y límite que indica el banco.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = vm::import, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.busy) "Importando…" else "Importar")
    }
    OutlinedButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth()) { Text("Elegir otro PDF") }
}

@Composable
private fun SummaryCard(s: StatementSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                listOfNotNull(s.bank, s.cardLast4?.let { "•••• $it" }).joinToString(" ").ifBlank { "Estado de cuenta" },
                style = MaterialTheme.typography.titleMedium,
            )
            val period = if (s.periodStart != null && s.periodEnd != null)
                "${formatDateEs(s.periodStart)} – ${formatDateEs(s.periodEnd)}" else null
            period?.let { Text("Periodo: $it") }
            Text("Corte: ${formatDateEs(s.cutoffDate)}")
            s.dueDate?.let { Text("Fecha límite de pago: ${formatDateEs(it)}") }
            s.payNoInterest?.let { Text("Para no generar intereses: ${formatMxn(it)}") }
            s.minimumPayment?.let { Text("Pago mínimo: ${formatMxn(it)}") }
            s.creditLimit?.let { Text("Límite de crédito: ${formatMxn(it)}") }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
