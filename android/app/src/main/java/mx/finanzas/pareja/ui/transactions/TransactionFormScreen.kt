package mx.finanzas.pareja.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.domain.DraftField
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.parseAmount
import mx.finanzas.pareja.ui.components.DropdownField
import mx.finanzas.pareja.ui.components.ErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(vm: TransactionFormViewModel, onDone: (saved: Boolean) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone(true) }

    val draft = state.draft
    val amount = parseAmount(draft.amountText)
    val installments = draft.installmentsText.trim().toIntOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Editar gasto" else "Nuevo gasto") },
                navigationIcon = {
                    IconButton(onClick = { onDone(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorText(state.message)

            OutlinedTextField(
                value = draft.merchant,
                onValueChange = { vm.onDraftChange(draft.copy(merchant = it)) },
                label = { Text("Comercio") },
                singleLine = true,
                enabled = !state.readOnly,
                isError = state.errors[DraftField.MERCHANT] != null,
                supportingText = state.errors[DraftField.MERCHANT]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = { vm.onDraftChange(draft.copy(amountText = it)) },
                label = { Text("Monto total (MXN)") },
                singleLine = true,
                enabled = !state.readOnly,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.errors[DraftField.AMOUNT] != null,
                supportingText = state.errors[DraftField.AMOUNT]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownField(
                label = "Tarjeta",
                selectedLabel = state.cards.firstOrNull { it.id == draft.cardId }
                    ?.let { "${it.name} •••• ${it.last4}" }.orEmpty(),
                options = state.cards,
                optionLabel = { "${it.name} •••• ${it.last4}" },
                onSelect = { vm.onDraftChange(draft.copy(cardId = it?.id)) },
                error = state.errors[DraftField.CARD],
                enabled = !state.readOnly,
            )
            DropdownField(
                label = "Categoría",
                selectedLabel = state.categories.firstOrNull { it.id == draft.categoryId }?.name ?: "Sin categoría",
                options = state.categories,
                optionLabel = { it.name },
                onSelect = { vm.onDraftChange(draft.copy(categoryId = it?.id)) },
                enabled = !state.readOnly,
                noneLabel = "Sin categoría",
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                enabled = !state.readOnly,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Fecha: ${formatDateEs(draft.date)}") }

            Text("Forma de pago", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = draft.mode == PaymentMode.CONTADO,
                    onClick = { vm.onDraftChange(draft.copy(mode = PaymentMode.CONTADO)) },
                    enabled = !state.readOnly,
                    label = { Text("De contado") },
                )
                FilterChip(
                    selected = draft.mode == PaymentMode.MSI,
                    onClick = { vm.onDraftChange(draft.copy(mode = PaymentMode.MSI)) },
                    enabled = !state.readOnly,
                    label = { Text("Meses sin intereses") },
                )
            }

            if (draft.mode == PaymentMode.MSI) {
                OutlinedTextField(
                    value = draft.installmentsText,
                    onValueChange = { vm.onDraftChange(draft.copy(installmentsText = it)) },
                    label = { Text("Número de mensualidades (ej. 12)") },
                    singleLine = true,
                    enabled = !state.readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.errors[DraftField.INSTALLMENTS] != null,
                    supportingText = state.errors[DraftField.INSTALLMENTS]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.installmentNumberText,
                    onValueChange = { vm.onDraftChange(draft.copy(installmentNumberText = it)) },
                    label = { Text("¿En qué mensualidad va? (vacío = la 1)") },
                    singleLine = true,
                    enabled = !state.readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.errors[DraftField.INSTALLMENT_NUMBER] != null,
                    supportingText = state.errors[DraftField.INSTALLMENT_NUMBER]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (amount != null && installments != null && installments >= 2) {
                    Text(
                        "Mensualidad aproximada: ${formatMxn(amount / installments)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { vm.onDraftChange(draft.copy(notes = it)) },
                label = { Text("Notas (opcional)") },
                enabled = !state.readOnly,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = vm::save,
                enabled = !state.saving && !state.readOnly && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.saving) "Guardando…" else "Guardar") }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        vm.onDraftChange(draft.copy(date = picked))
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState) }
    }
}
