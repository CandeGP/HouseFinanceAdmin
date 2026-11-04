package mx.finanzas.pareja.ui.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.domain.DraftField
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.parseAmount
import mx.finanzas.pareja.domain.parseReceipt
import mx.finanzas.pareja.ui.components.DropdownField
import mx.finanzas.pareja.ui.components.ErrorText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.domain.cardAccentArgb
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.components.SectionLabel
import mx.finanzas.pareja.ui.components.SelectChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
    vm: TransactionFormViewModel,
    onDone: (saved: Boolean) -> Unit,
    /** Llegar desde «Escanear ticket» abre directo el selector de imagen. */
    autoScan: Boolean = false,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // La imagen se lee en el teléfono (OCR local) y solo autorrellena el formulario; no se guarda ni se sube.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                scanning = true
                val text = runCatching { ocrImageText(context, uri) }.getOrDefault("")
                vm.applyReceipt(parseReceipt(text))
                scanning = false
            }
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onDone(true) }
    LaunchedEffect(autoScan) { if (autoScan && !vm.isEditing && !state.readOnly) pickImage.launch("image/*") }

    val draft = state.draft
    val amount = parseAmount(draft.amountText)
    val installments = draft.installmentsText.trim().toIntOrNull()

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
                        if (vm.isEditing) "Editar gasto" else "Nuevo cobro",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                ErrorText(state.message)

                if (!vm.isEditing && !state.readOnly) {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { if (!scanning) pickImage.launch("image/*") }) {
                        Row(
                            Modifier.padding(horizontal = 16.dp).height(52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = AppTheme.tokens.chart[3])
                            Text(
                                if (scanning) "Leyendo imagen…" else "Cargar imagen (ticket, voucher o captura)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    state.receiptNote?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                AmountInput(
                    value = draft.amountText,
                    onChange = { vm.onDraftChange(draft.copy(amountText = it)) },
                    enabled = !state.readOnly,
                    error = state.errors[DraftField.AMOUNT],
                )

                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Concepto", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                                if (draft.merchant.isEmpty()) {
                                    Text("Comercio", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                }
                                BasicTextField(
                                    value = draft.merchant,
                                    onValueChange = { vm.onDraftChange(draft.copy(merchant = it)) },
                                    enabled = !state.readOnly,
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.merge(
                                        TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, textAlign = TextAlign.End),
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        state.errors[DraftField.MERCHANT]?.let {
                            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.tokens.glassBorder)
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
                }

                SectionLabel("CATEGORÍA")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Sin categoría", draft.categoryId == null, { vm.onDraftChange(draft.copy(categoryId = null)) }, enabled = !state.readOnly)
                    state.categories.forEach { category ->
                        SelectChip(category.name, draft.categoryId == category.id, { vm.onDraftChange(draft.copy(categoryId = category.id)) }, enabled = !state.readOnly)
                    }
                }

                SectionLabel("PAGAR CON")
                if (state.cards.isEmpty()) {
                    Text("Aún no tienes tarjetas. Agrégalas en la pestaña Tarjetas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.cards.filter { !it.frozen || it.id == draft.cardId }.forEach { card ->
                        MiniCard(card, selected = draft.cardId == card.id, enabled = !state.readOnly) { vm.onDraftChange(draft.copy(cardId = card.id)) }
                    }
                }
                state.errors[DraftField.CARD]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }

                SectionLabel("FORMA DE PAGO")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectChip("De contado", draft.mode == PaymentMode.CONTADO, { vm.onDraftChange(draft.copy(mode = PaymentMode.CONTADO)) }, enabled = !state.readOnly)
                    SelectChip("Meses sin intereses", draft.mode == PaymentMode.MSI, { vm.onDraftChange(draft.copy(mode = PaymentMode.MSI)) }, enabled = !state.readOnly)
                }

                if (draft.mode == PaymentMode.MSI) {
                    AuthField(
                        label = "Número de mensualidades",
                        value = draft.installmentsText,
                        onValueChange = { vm.onDraftChange(draft.copy(installmentsText = it)) },
                        placeholder = "Ej. 12",
                        enabled = !state.readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        error = state.errors[DraftField.INSTALLMENTS],
                    )
                    AuthField(
                        label = "¿En qué mensualidad va?",
                        value = draft.installmentNumberText,
                        onValueChange = { vm.onDraftChange(draft.copy(installmentNumberText = it)) },
                        placeholder = "Vacío = 1; 0 = empieza el corte siguiente",
                        enabled = !state.readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        error = state.errors[DraftField.INSTALLMENT_NUMBER],
                    )
                    if (amount != null && installments != null && installments >= 2) {
                        Text(
                            "Mensualidad aproximada: ${formatMxn(amount / installments)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                AuthField(
                    label = "Notas (opcional)",
                    value = draft.notes,
                    onValueChange = { vm.onDraftChange(draft.copy(notes = it)) },
                    enabled = !state.readOnly,
                    singleLine = false,
                )
            }
            GradientButton(
                onClick = vm::save,
                enabled = !state.saving && !state.readOnly && !state.loading,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) { Text(if (state.saving) "Guardando…" else "Guardar cobro") }
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

/** Monto grande y centrado con subrayado degradado, como en el mockup de nuevo cobro. */
@Composable
internal fun AmountInput(value: String, onChange: (String) -> Unit, enabled: Boolean, error: String?) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Monto", fontSize = 13.sp, color = ink.copy(alpha = 0.72f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("$", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.7f))
            Box(Modifier.width(220.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text("0.00", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = ink.copy(alpha = 0.3f))
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(color = ink, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Monto total en pesos" },
                )
            }
        }
        Box(Modifier.width(160.dp).height(3.dp).clip(CircleShape).background(AppTheme.tokens.accent))
        error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** Tarjeta pequeña para elegir con cuál se pagó; la elegida lleva un aro blanco. */
@Composable
private fun MiniCard(card: mx.finanzas.pareja.data.Card, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val base = Color(cardAccentArgb(card))
    val shape = RoundedCornerShape(16.dp)
    val ink = if (base.luminance() > 0.5f) Color(0xFF1A1442) else Color.White
    Column(
        Modifier.size(width = 112.dp, height = 68.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.18f), lerp(base, Color.Black, 0.18f))))
            .then(if (selected) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(card.name, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("•• ${card.last4}", color = ink.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
