package mx.finanzas.pareja.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt
import mx.finanzas.pareja.domain.BudgetLevel
import mx.finanzas.pareja.domain.CategoryBudgetRow
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.ErrorText

private fun plainNumber(value: Double): String = "%.2f".format(Locale.US, value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBudgetsScreen(vm: CategoryBudgetsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryBudgetRow?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            editing = null
            vm.consumeSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presupuestos por categoría") },
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
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Las categorías son de los dos, pero el presupuesto es solo tuyo: se compara con tu gasto " +
                            "de este mes (tus tarjetas, las compras a meses por su mensualidad, y tus pagos en " +
                            "efectivo con categoría). Tu pareja fija los suyos y no ve los tuyos. Toca una " +
                            "categoría para fijar el tuyo; te avisamos al llegar al 80% y al pasarte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.rows, key = { it.category.id }) { row ->
                    BudgetRow(row, onClick = { editing = row })
                }
            }
        }
    }

    editing?.let { row ->
        BudgetDialog(
            row = row,
            error = state.error,
            onDismiss = { editing = null; vm.consumeSaved() },
            onEdit = vm::consumeSaved,
            onSave = { text -> vm.setBudget(row.category.id, text) },
        )
    }
}

@Composable
private fun BudgetRow(row: CategoryBudgetRow, onClick: () -> Unit) {
    val budget = row.budget
    val color = when (row.level) {
        BudgetLevel.OVER -> MaterialTheme.colorScheme.error
        BudgetLevel.NEAR -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.category.name, style = MaterialTheme.typography.titleMedium)
            if (budget == null) {
                Text(
                    "Este mes: ${formatMxn(row.spent)} · sin presupuesto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val percent = (row.pct ?: 0.0).roundToInt()
                Text(
                    "Este mes: ${formatMxn(row.spent)} de ${formatMxn(budget)} ($percent%)" +
                        if (row.level == BudgetLevel.OVER) " · te pasaste por ${formatMxn(row.spent - budget)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.level == BudgetLevel.OVER) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { (row.spent / budget).toFloat().coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Las categorías son de los dos: si el hogar gastó más que tú, se ve cuánto entre ambos.
            if (row.householdSpent > row.spent + 0.005) {
                Text(
                    "Entre los dos: ${formatMxn(row.householdSpent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetDialog(
    row: CategoryBudgetRow,
    error: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(row.category.id) { mutableStateOf(row.budget?.let(::plainNumber).orEmpty()) }
    // Al volver a escribir se quita el error anterior.
    LaunchedEffect(text) { if (error != null) onEdit() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presupuesto de ${row.category.name}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Presupuesto mensual (MXN)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                supportingText = { Text(error ?: "Déjalo vacío para quitar el presupuesto") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
