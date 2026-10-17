package mx.finanzas.pareja.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CardBalanceCheck
import mx.finanzas.pareja.domain.BalanceVerdict
import mx.finanzas.pareja.domain.compareBalance
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.message
import mx.finanzas.pareja.ui.components.ErrorText

/** Última comparación contra el banco (si la hay) y el acceso para capturar el saldo de hoy (solo tu tarjeta). */
@Composable
internal fun BalanceCheckLine(
    appDebt: Double?,
    check: CardBalanceCheck?,
    mine: Boolean,
    onCapture: () -> Unit,
) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (check != null && appDebt != null) {
            val comparison = compareBalance(app = appDebt, bank = check.bankBalance)
            Text(
                "Banco ${formatMxn(check.bankBalance)} (${formatDateEs(check.checkedOn)}) · la app suma ${formatMxn(appDebt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                comparison.message(),
                style = MaterialTheme.typography.bodySmall,
                color = if (comparison.verdict == BalanceVerdict.MATCH) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (mine) {
            TextButton(onClick = onCapture) { Text(if (check == null) "Conciliar con el banco" else "Volver a conciliar") }
        }
    }
}

@Composable
internal fun BalanceCheckDialog(
    card: Card,
    appDebt: Double?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conciliar ${card.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Escribe el \"saldo utilizado\" (la deuda total) que muestra la app de tu banco hoy." +
                        (appDebt?.let { " La app suma ${formatMxn(it)}." } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Saldo del banco") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(error)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Comparar y guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
