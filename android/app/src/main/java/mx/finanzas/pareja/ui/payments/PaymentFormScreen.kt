package mx.finanzas.pareja.ui.payments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.PaymentField
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.timeText
import mx.finanzas.pareja.ui.components.DropdownField
import mx.finanzas.pareja.ui.components.ErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentFormScreen(
    vm: ManualPaymentFormViewModel,
    zone: TimeZone,
    onDone: (saved: Boolean) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(state.saved) { if (state.saved) onDone(true) }

    fun attach(uri: Uri) {
        scope.launch {
            val loaded = loadEvidence(context, uri)
            if (loaded == null) vm.reportMessage("No se pudo leer el archivo")
            else vm.attachEvidence(loaded.bytes, loaded.mimeType)
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::attach)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::attach)
    }

    val draft = state.draft

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Editar pago" else "Nuevo pago") },
                navigationIcon = {
                    IconButton(onClick = { onDone(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorText(state.message)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.method == PaymentMethod.CASH,
                    onClick = { vm.onDraftChange(draft.copy(method = PaymentMethod.CASH)) },
                    enabled = !state.readOnly,
                    label = { Text("Efectivo") },
                )
                FilterChip(
                    selected = draft.method == PaymentMethod.TRANSFER,
                    onClick = { vm.onDraftChange(draft.copy(method = PaymentMethod.TRANSFER)) },
                    enabled = !state.readOnly,
                    label = { Text("Transferencia") },
                )
            }

            OutlinedTextField(
                value = draft.concept,
                onValueChange = { vm.onDraftChange(draft.copy(concept = it)) },
                label = { Text("Concepto") },
                singleLine = true,
                enabled = !state.readOnly,
                isError = state.errors[PaymentField.CONCEPT] != null,
                supportingText = state.errors[PaymentField.CONCEPT]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = { vm.onDraftChange(draft.copy(amountText = it)) },
                label = { Text("Monto (MXN)") },
                singleLine = true,
                enabled = !state.readOnly,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.errors[PaymentField.AMOUNT] != null,
                supportingText = state.errors[PaymentField.AMOUNT]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    enabled = !state.readOnly,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                ) { Text(formatDateEs(draft.date)) }
                OutlinedTextField(
                    value = draft.timeText,
                    onValueChange = { vm.onDraftChange(draft.copy(timeText = it)) },
                    label = { Text("Hora (HH:mm)") },
                    singleLine = true,
                    enabled = !state.readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.errors[PaymentField.TIME] != null,
                    supportingText = state.errors[PaymentField.TIME]?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(
                onClick = {
                    val now: Instant = Clock.System.now()
                    vm.onDraftChange(
                        draft.copy(date = now.toLocalDateTime(zone).date, timeText = timeText(now, zone)),
                    )
                },
                enabled = !state.readOnly,
            ) { Text("Usar fecha y hora actuales") }

            OutlinedTextField(
                value = draft.counterparty,
                onValueChange = { vm.onDraftChange(draft.copy(counterparty = it)) },
                label = { Text("Pagado a (opcional)") },
                singleLine = true,
                enabled = !state.readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.method == PaymentMethod.TRANSFER) {
                OutlinedTextField(
                    value = draft.reference,
                    onValueChange = { vm.onDraftChange(draft.copy(reference = it)) },
                    label = { Text("Clave de rastreo / referencia (opcional)") },
                    singleLine = true,
                    enabled = !state.readOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DropdownField(
                label = "Categoría",
                selectedLabel = state.categories.firstOrNull { it.id == draft.categoryId }?.name ?: "Sin categoría",
                options = state.categories,
                optionLabel = { it.name },
                onSelect = { vm.onDraftChange(draft.copy(categoryId = it?.id)) },
                enabled = !state.readOnly,
                noneLabel = "Sin categoría",
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { vm.onDraftChange(draft.copy(notes = it)) },
                label = { Text("Notas (opcional)") },
                enabled = !state.readOnly,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Comprobante", style = MaterialTheme.typography.titleSmall)
            EvidencePreview(state)
            if (!state.readOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val uri = newCameraUri(context)
                        cameraUri = uri
                        takePhoto.launch(uri)
                    }) { Text("Tomar foto") }
                    OutlinedButton(onClick = { pickFile.launch(arrayOf("image/*", "application/pdf")) }) {
                        Text("Elegir archivo")
                    }
                }
                if (state.hasEvidence) {
                    TextButton(onClick = vm::removeEvidence) { Text("Quitar comprobante") }
                }
            }

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

@Composable
private fun EvidencePreview(state: PaymentFormUiState) {
    val pending = state.pending
    val keepsExisting = state.existingEvidencePath != null && !state.evidenceRemoved
    when {
        pending != null && pending.mimeType == "application/pdf" ->
            Text("PDF adjunto (${pending.bytes.size / 1024} KB)")
        pending != null -> AsyncImage(
            model = pending.bytes,
            contentDescription = "Comprobante",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        keepsExisting && state.existingEvidencePath?.endsWith(".pdf") == true ->
            Text("Comprobante en PDF guardado")
        keepsExisting && state.evidenceUrl != null -> AsyncImage(
            model = state.evidenceUrl,
            contentDescription = "Comprobante",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        else -> Text(
            "Sin comprobante. Adjunta una captura o foto como evidencia.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
