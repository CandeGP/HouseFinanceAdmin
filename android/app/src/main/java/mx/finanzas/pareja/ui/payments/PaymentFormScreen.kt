package mx.finanzas.pareja.ui.payments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import mx.finanzas.pareja.domain.matchingPayees
import mx.finanzas.pareja.domain.timeText
import mx.finanzas.pareja.ui.components.DropdownField
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.components.SectionLabel
import mx.finanzas.pareja.ui.components.SelectChip
import mx.finanzas.pareja.ui.transactions.AmountInput

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var newCategory by remember { mutableStateOf<String?>(null) } // null = diálogo cerrado

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

    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    TextButton(onClick = { onDone(false) }, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancelar") }
                    Text(
                        if (vm.isEditing) "Editar pago" else "Registrar pago",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                ErrorText(state.message)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Efectivo", draft.method == PaymentMethod.CASH, { vm.onDraftChange(draft.copy(method = PaymentMethod.CASH)) }, enabled = !state.readOnly)
                    SelectChip("Transferencia", draft.method == PaymentMethod.TRANSFER, { vm.onDraftChange(draft.copy(method = PaymentMethod.TRANSFER)) }, enabled = !state.readOnly)
                }

                AmountInput(
                    value = draft.amountText,
                    onChange = { vm.onDraftChange(draft.copy(amountText = it)) },
                    enabled = !state.readOnly,
                    error = state.errors[PaymentField.AMOUNT],
                )

                AuthField(
                    label = "Concepto",
                    value = draft.concept,
                    onValueChange = { vm.onDraftChange(draft.copy(concept = it)) },
                    placeholder = "Ej. Renta, mandado",
                    enabled = !state.readOnly,
                    error = state.errors[PaymentField.CONCEPT],
                )

                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp)
                            .clickable(enabled = !state.readOnly, role = Role.Button) { showDatePicker = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Fecha", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(formatDateEs(draft.date), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    AuthField(
                        label = "Hora (HH:mm)",
                        value = draft.timeText,
                        onValueChange = { vm.onDraftChange(draft.copy(timeText = it)) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        error = state.errors[PaymentField.TIME],
                    )
                    TextButton(
                        onClick = {
                            val now: Instant = Clock.System.now()
                            vm.onDraftChange(draft.copy(date = now.toLocalDateTime(zone).date, timeText = timeText(now, zone)))
                        },
                        enabled = !state.readOnly,
                    ) { Text("Ahora") }
                }

                PayeeField(
                    value = draft.counterparty,
                    payees = state.payees,
                    enabled = !state.readOnly,
                    onChange = { vm.onDraftChange(draft.copy(counterparty = it)) },
                )
                if (draft.method == PaymentMethod.TRANSFER) {
                    AuthField(
                        label = "Clave de rastreo / referencia (opcional)",
                        value = draft.reference,
                        onValueChange = { vm.onDraftChange(draft.copy(reference = it)) },
                        enabled = !state.readOnly,
                    )
                }

                SectionLabel("CATEGORÍA")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Sin categoría", draft.categoryId == null, { vm.onDraftChange(draft.copy(categoryId = null)) }, enabled = !state.readOnly)
                    state.categories.forEach { category ->
                        SelectChip(category.name, draft.categoryId == category.id, { vm.onDraftChange(draft.copy(categoryId = category.id)) }, enabled = !state.readOnly)
                    }
                    if (!state.readOnly) SelectChip("+ Nueva", false, { newCategory = "" })
                }

                AuthField(
                    label = "Notas (opcional)",
                    value = draft.notes,
                    onValueChange = { vm.onDraftChange(draft.copy(notes = it)) },
                    enabled = !state.readOnly,
                    singleLine = false,
                )

                SectionLabel("COMPROBANTE")
                EvidencePreview(state)
                if (!state.readOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("Tomar foto", false, {
                            val uri = newCameraUri(context)
                            cameraUri = uri
                            takePhoto.launch(uri)
                        })
                        SelectChip("Elegir archivo", false, { pickFile.launch(arrayOf("image/*", "application/pdf")) })
                        if (state.hasEvidence) SelectChip("Quitar", false, vm::removeEvidence)
                    }
                }
            }
            GradientButton(
                onClick = vm::save,
                enabled = !state.saving && !state.readOnly && !state.loading,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) { Text(if (state.saving) "Guardando…" else "Guardar pago") }
        }
    }

    newCategory?.let { name ->
        AlertDialog(
            onDismissRequest = { newCategory = null },
            title = { Text("Nueva categoría") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { newCategory = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.addCategory(name); newCategory = null },
                    enabled = name.isNotBlank(),
                ) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { newCategory = null }) { Text("Cancelar") } },
        )
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

/** Campo "Pagado a": se puede escribir un nombre nuevo o elegir uno de los ya usados, para no teclearlo igual cada vez. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayeeField(
    value: String,
    payees: List<String>,
    enabled: Boolean,
    onChange: (String) -> Unit,
    label: String = "Pagado a (opcional)",
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = matchingPayees(payees, value)
    val open = expanded && enabled && matches.isNotEmpty()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }) {
        AuthField(
            label = label,
            value = value,
            onValueChange = { onChange(it); expanded = true },
            error = error,
            enabled = enabled,
            trailing = { if (payees.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            matches.forEach { payee ->
                DropdownMenuItem(text = { Text(payee) }, onClick = { onChange(payee); expanded = false })
            }
        }
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
