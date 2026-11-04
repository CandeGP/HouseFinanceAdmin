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
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.domain.categoryEmoji
import mx.finanzas.pareja.domain.cardLabel
import mx.finanzas.pareja.ui.payments.newCameraUri
import mx.finanzas.pareja.data.Card as CardModel

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
    var showScanSheet by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showMoreCards by remember { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // La imagen (foto o galería) se lee en el teléfono (OCR local) y solo autorrellena el formulario; no se guarda ni se sube.
    fun readReceipt(uri: Uri) {
        scope.launch {
            scanning = true
            val text = runCatching { ocrImageText(context, uri) }.getOrDefault("")
            vm.applyReceipt(parseReceipt(text))
            scanning = false
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::readReceipt) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::readReceipt)
    }
    fun startCamera() {
        val uri = newCameraUri(context)
        cameraUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            vm.showMessage("Este teléfono no tiene una app de cámara. Elige una imagen de la galería.")
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onDone(true) }
    LaunchedEffect(autoScan) { if (autoScan && !vm.isEditing && !state.readOnly) showScanSheet = true }

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
                        if (vm.isEditing) "Editar gasto" else "Nuevo gasto",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    if (!vm.isEditing && !state.readOnly) {
                        IconButton(
                            onClick = { if (!scanning) showScanSheet = true },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Escanear ticket", tint = AppTheme.tokens.chart[3])
                        }
                    }
                }
                ErrorText(state.message)

                if (scanning) Text("Leyendo imagen…", style = MaterialTheme.typography.bodySmall)
                // Región en vivo: el lector de pantalla anuncia lo que se leyó sin que haya que buscarlo.
                state.receiptNote?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
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
                            Text(
                                if (draft.date == vm.today) "Hoy" else formatDateEs(draft.date),
                                fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
                state.suggestionNote?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                // Las 3 más usadas van de entrada; la elegida siempre se ve aunque no sea de las 3; el resto, en «Ver todas».
                val shownCategories = state.categories
                    .filter { it.id in state.topCategoryIds }
                    .sortedBy { state.topCategoryIds.indexOf(it.id) }
                    .let { top -> state.categories.firstOrNull { it.id == draft.categoryId && it !in top }?.let { top + it } ?: top }
                SectionLabel("CATEGORÍA")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shownCategories.forEach { category ->
                        SelectChip(
                            category.name, draft.categoryId == category.id,
                            { vm.onDraftChange(draft.copy(categoryId = category.id)) },
                            enabled = !state.readOnly, leading = categoryEmoji(category.icon),
                        )
                    }
                    SelectChip("Ver todas", selected = false, onClick = { showCategories = true }, enabled = !state.readOnly)
                }

                SectionLabel("PAGAR CON")
                if (state.cards.isEmpty()) {
                    Text("Aún no tienes tarjetas. Agrégalas en la pestaña Tarjetas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val shownCards = state.topCardIds.mapNotNull { id -> state.cards.firstOrNull { it.id == id } }
                    .let { top -> state.cards.firstOrNull { it.id == draft.cardId && it !in top }?.let { top + it } ?: top }
                val otherCards = state.cards.filter { it !in shownCards && !it.frozen }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    shownCards.forEach { card ->
                        MiniCard(card, selected = draft.cardId == card.id, enabled = !state.readOnly) { vm.onDraftChange(draft.copy(cardId = card.id)) }
                    }
                    if (otherCards.isNotEmpty()) MoreCardsTile(otherCards.size, enabled = !state.readOnly) { showMoreCards = true }
                }
                state.errors[DraftField.CARD]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }

                SectionLabel("FORMA DE PAGO")
                val notesShown = notesOpen || draft.notes.isNotEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("De contado", draft.mode == PaymentMode.CONTADO, { vm.onDraftChange(draft.copy(mode = PaymentMode.CONTADO)) }, enabled = !state.readOnly)
                    SelectChip("Meses sin intereses", draft.mode == PaymentMode.MSI, { vm.onDraftChange(draft.copy(mode = PaymentMode.MSI)) }, enabled = !state.readOnly)
                    if (!notesShown) SelectChip("Agregar nota", selected = false, onClick = { notesOpen = true }, enabled = !state.readOnly, leading = "＋")
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

                if (notesShown) {
                    AuthField(
                        label = "Notas (opcional)",
                        value = draft.notes,
                        onValueChange = { vm.onDraftChange(draft.copy(notes = it)) },
                        enabled = !state.readOnly,
                        singleLine = false,
                    )
                }
            }
            GradientButton(
                onClick = vm::save,
                enabled = !state.saving && !state.readOnly && !state.loading,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) { Text(if (state.saving) "Guardando…" else "Guardar gasto") }
        }
    }

    if (showScanSheet) {
        ScanSheet(
            onDismiss = { showScanSheet = false },
            onCamera = { showScanSheet = false; startCamera() },
            onGallery = { showScanSheet = false; pickImage.launch("image/*") },
        )
    }
    if (showCategories) {
        CategorySheet(
            categories = state.categories,
            selectedId = draft.categoryId,
            onDismiss = { showCategories = false },
            onPick = { vm.onDraftChange(draft.copy(categoryId = it)); showCategories = false },
        )
    }
    if (showMoreCards) {
        MoreCardsSheet(
            cards = state.cards.filter { !it.frozen },
            selectedId = draft.cardId,
            onDismiss = { showMoreCards = false },
            onPick = { vm.onDraftChange(draft.copy(cardId = it)); showMoreCards = false },
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

// Altura de línea igual al tamaño de letra y centrada: el cursor mide como los dígitos, sin sobrar por arriba.
private val AMOUNT_STYLE = TextStyle(
    fontSize = 56.sp,
    lineHeight = 56.sp,
    fontWeight = FontWeight.ExtraBold,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Monto grande y centrado con subrayado degradado, como en el mockup de nuevo cobro. */
@Composable
internal fun AmountInput(value: String, onChange: (String) -> Unit, enabled: Boolean, error: String?) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Monto", fontSize = 13.sp, color = ink.copy(alpha = 0.72f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("$", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.7f))
            Box(Modifier.width(220.dp), contentAlignment = Alignment.Center) {
                // lineHeight explícito: sin él se hereda el de Material (24sp) y el cursor queda corto y fuera del número.
                if (value.isEmpty()) Text("0.00", style = LocalTextStyle.current.merge(AMOUNT_STYLE), color = ink.copy(alpha = 0.3f))
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        AMOUNT_STYLE.copy(color = ink, textAlign = TextAlign.Center),
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

/** Hoja con las dos formas de escanear: foto al momento o imagen de la galería. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanSheet(onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Escanear ticket", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SheetOption(Icons.Outlined.CameraAlt, "Tomar foto ahora", "Abre la cámara", onCamera)
            SheetOption(Icons.Outlined.PhotoLibrary, "Elegir de la galería", "Ticket, voucher o captura", onGallery)
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.tokens.chart[3])
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Todas las categorías en cuadrícula de 3, con su emoji; «Sin categoría» al final. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(categories: List<Category>, selectedId: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Categoría", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            categories.chunked(CATEGORY_COLUMNS).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { c ->
                        CategoryCell(categoryEmoji(c.icon), c.name, selectedId == c.id, Modifier.weight(1f)) { onPick(c.id) }
                    }
                    repeat(CATEGORY_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row { SelectChip("Sin categoría", selectedId == null, { onPick(null) }) }
        }
    }
}

private const val CATEGORY_COLUMNS = 3

@Composable
private fun CategoryCell(emoji: String, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tokens = AppTheme.tokens
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.25f) else tokens.glass)
            .border(if (selected) 2.dp else 1.dp, if (selected) primary else tokens.glassBorder, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // El emoji es decorativo: el lector de pantalla dice solo el nombre.
        Text(emoji, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics { })
        Text(
            name, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «+N»: abre la lista con las tarjetas que no entraron en las 3 de entrada. */
@Composable
private fun MoreCardsTile(count: Int, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(width = 72.dp, height = 68.dp)
            .clip(shape)
            .background(AppTheme.tokens.glass)
            .border(1.dp, AppTheme.tokens.glassBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Ver las demás tarjetas", onClick = onClick)
            .semantics { contentDescription = "Más tarjetas, $count" },
        contentAlignment = Alignment.Center,
    ) { Text("+$count", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreCardsSheet(cards: List<CardModel>, selectedId: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Pagar con", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            cards.forEach { card ->
                val selected = card.id == selectedId
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else AppTheme.tokens.glass)
                        .selectable(selected = selected, role = Role.RadioButton) { onPick(card.id) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(12.dp).background(Color(cardAccentArgb(card)), CircleShape))
                    Text(cardLabel(card), fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
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
