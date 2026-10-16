package mx.finanzas.pareja.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import java.util.Locale
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayment
import mx.finanzas.pareja.data.MonthSpend
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.CARD_COLOR_CHOICES
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardPaymentDraft
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.localDate
import mx.finanzas.pareja.ui.components.PastDateButton
import mx.finanzas.pareja.ui.components.PayStatusText
import mx.finanzas.pareja.domain.CardEditDraft
import mx.finanzas.pareja.domain.CardField
import mx.finanzas.pareja.domain.cardAccentArgb
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText

@Composable
fun CardsScreen(
    vm: CardsViewModel,
    userId: String,
    memberNames: Map<String, String>,
    openAddOnStart: Boolean,
    onChanged: () -> Unit,
    onImportStatement: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Cada quien ve y administra lo suyo: archivadas solo las tuyas, y el PDF solo si ya tienes una tarjeta.
    val myArchived = state.archived.filter { it.ownerId == userId }
    val hasOwnCard = state.cards.any { it.ownerId == userId }
    var showForm by remember { mutableStateOf(openAddOnStart) }
    var pendingArchive by remember { mutableStateOf<mx.finanzas.pareja.data.Card?>(null) }
    var budgetFor by remember { mutableStateOf<mx.finanzas.pareja.data.Card?>(null) }
    var payFor by remember { mutableStateOf<Pair<mx.finanzas.pareja.data.Card, CardCycleStatus>?>(null) }
    var editPayment by remember {
        mutableStateOf<Triple<mx.finanzas.pareja.data.Card, CardCycleStatus, CardPayment>?>(null)
    }
    var pendingDeletePayment by remember { mutableStateOf<CardPayment?>(null) }
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(zone) }

    LaunchedEffect(state.paymentSaved) {
        if (state.paymentSaved) {
            payFor = null
            editPayment = null
            vm.consumePaymentSaved()
            onChanged()
        }
    }

    LaunchedEffect(state.edited) {
        if (state.edited) {
            budgetFor = null
            vm.consumeEdited()
            onChanged()
        }
    }

    LaunchedEffect(state.added) {
        if (state.added) {
            showForm = false
            vm.consumeAdded()
            onChanged()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar tarjeta")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Tarjetas",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ErrorText(state.message, Modifier.padding(horizontal = 16.dp))

            // El estado de cuenta se concilia contra una tarjeta tuya: sin ninguna, el botón no tiene sentido.
            if (hasOwnCard) {
                OutlinedButton(
                    onClick = onImportStatement,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Importar estado de cuenta (PDF)") }
            }

            // Siempre se muestra la lista: así "Archivadas" es visible aunque no quede ninguna activa.
            run {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.cards.isEmpty() && !state.loading) {
                        item {
                            Text(
                                "Sin tarjetas. Agrega tus tarjetas de crédito con el botón +.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Mis tarjetas primero y las de la pareja aparte (solo lectura).
                    val grouped = state.cards.groupBy { it.ownerId == userId }
                    for (isMine in listOf(true, false)) {
                    val group = grouped[isMine].orEmpty()
                    if (group.isEmpty()) continue
                    item(key = "header-$isMine") {
                        Text(
                            if (isMine) "Mis tarjetas" else "Tarjetas de ${memberNames[group.first().ownerId] ?: "tu pareja"}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (isMine) 0.dp else 12.dp),
                        )
                    }
                    items(group, key = { it.id }) { card ->
                        val mine = card.ownerId == userId
                        val spend = state.spend[card.id]
                        Card(Modifier.fillMaxWidth().let { if (mine) it.clickable { budgetFor = card } else it }) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(Modifier.size(12.dp).background(Color(cardAccentArgb(card)), CircleShape))
                                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        "${card.bank} •••• ${card.last4}" +
                                            (if (mine) "" else " · de ${memberNames[card.ownerId] ?: "tu pareja"}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (card.altLast4.isNotEmpty()) {
                                        Text(
                                            "También aparece como: " + card.altLast4.joinToString { "•••• $it" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "Corte día ${card.cutoffDay} · Pago día ${card.dueDay} · " +
                                            "Límite ${formatMxn(card.creditLimit)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    BudgetLine(card = card, spend = spend, mine = mine)
                                    state.statuses[card.id]?.let { status ->
                                        PayStatusText(status, Modifier.padding(top = 6.dp))
                                        state.payments[card.id].orEmpty().forEach { payment ->
                                            Row(
                                                Modifier.fillMaxWidth().let {
                                                    if (mine) it.clickable { editPayment = Triple(card, status, payment) } else it
                                                },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    "${formatDateEs(localDate(payment.paidAt, zone))} · " +
                                                        "${if (payment.method == PaymentMethod.CASH) "Efectivo" else "Transferencia"}" +
                                                        " · ${formatMxn(payment.amount)}" +
                                                        if (mine) " · toca para editar" else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (mine) {
                                                    TextButton(onClick = { pendingDeletePayment = payment }) { Text("Borrar") }
                                                }
                                            }
                                        }
                                        if (mine) {
                                            TextButton(onClick = { payFor = card to status }) { Text("Registrar pago") }
                                        }
                                    }
                                }
                                if (mine) {
                                    IconButton(onClick = { pendingArchive = card }) {
                                        Icon(Icons.Filled.Archive, contentDescription = "Archivar tarjeta")
                                    }
                                }
                            }
                        }
                    }
                    }
                    run {
                        item {
                            Text(
                                "Archivadas (${myArchived.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                        if (myArchived.isEmpty()) {
                            item {
                                Text(
                                    "Ninguna por ahora. Las tarjetas que archives aparecerán aquí para restaurarlas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(myArchived, key = { "archived-${it.id}" }) { card ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${card.bank} •••• ${card.last4} · conserva sus gastos",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (card.ownerId == userId) {
                                        TextButton(onClick = { vm.restore(card.id); onChanged() }) { Text("Restaurar") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    budgetFor?.let { card ->
        CardEditDialog(
            card = card,
            errors = state.editErrors,
            onDismiss = { budgetFor = null; vm.consumeEdited() },
            onSave = { draft -> vm.saveEdit(card.id, draft) },
        )
    }

    payFor?.let { (card, status) ->
        CardPaymentDialog(
            card = card,
            status = status,
            editing = null,
            today = today,
            zone = zone,
            error = state.paymentError,
            onDismiss = { payFor = null; vm.consumePaymentSaved() },
            onEdit = vm::consumePaymentSaved,
            onSave = { draft -> vm.registerPayment(card, status, draft) },
        )
    }

    editPayment?.let { (card, status, payment) ->
        CardPaymentDialog(
            card = card,
            status = status,
            editing = payment,
            today = today,
            zone = zone,
            error = state.paymentError,
            onDismiss = { editPayment = null; vm.consumePaymentSaved() },
            onEdit = vm::consumePaymentSaved,
            onSave = { draft -> vm.updatePayment(payment, draft) },
        )
    }

    pendingDeletePayment?.let { payment ->
        AlertDialog(
            onDismissRequest = { pendingDeletePayment = null },
            title = { Text("¿Borrar este pago?") },
            text = {
                Text(
                    "${formatMxn(payment.amount)} del ${formatDateEs(localDate(payment.paidAt, zone))}. " +
                        "El ciclo vuelve a mostrar ese monto como pendiente.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deletePayment(payment.id); onChanged(); pendingDeletePayment = null }) {
                    Text("Borrar")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeletePayment = null }) { Text("Cancelar") } },
        )
    }

    pendingArchive?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("¿Archivar esta tarjeta?") },
            text = {
                Text(
                    "${card.name} •••• ${card.last4}\n" +
                        "Se oculta de Resumen, Gastos y Tarjetas, pero sus gastos y estados de cuenta " +
                        "se conservan. La puedes restaurar en \"Archivadas\".",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.archive(card.id); onChanged(); pendingArchive = null }) { Text("Archivar") }
            },
            dismissButton = { TextButton(onClick = { pendingArchive = null }) { Text("Cancelar") } },
        )
    }

    if (showForm) {
        CardFormDialog(
            errors = state.errors,
            onDismiss = { showForm = false },
            onSave = vm::addCard,
        )
    }
}

/** Progreso del gasto del mes frente al presupuesto, o la invitación a fijar uno (solo tu tarjeta). */
@Composable
private fun BudgetLine(card: mx.finanzas.pareja.data.Card, spend: MonthSpend?, mine: Boolean) {
    val budget = card.monthlyBudget
    when {
        budget == null && mine -> Text(
            "Toca la tarjeta para editarla o fijar un presupuesto mensual",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        budget != null -> {
            val spent = spend?.spent ?: 0.0
            val over = spent > budget
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Este mes: ${formatMxn(spent)} de ${formatMxn(budget)}" +
                        (if (over) " · te pasaste por ${formatMxn(spent - budget)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { (spent / budget).toFloat().coerceIn(0f, 1f) },
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun plainNumber(value: Double): String = "%.2f".format(Locale.US, value)

/**
 * Registrar un pago a la tarjeta (monto con lo que falta, fecha, forma de pago y nota) o editar uno
 * existente ([editing]).
 */
@Composable
private fun CardPaymentDialog(
    card: mx.finanzas.pareja.data.Card,
    status: CardCycleStatus,
    editing: CardPayment?,
    today: LocalDate,
    zone: TimeZone,
    error: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (CardPaymentDraft) -> Unit,
) {
    var draft by remember(card.id, status.cutoffDate, editing?.id) {
        mutableStateOf(
            if (editing == null) {
                CardPaymentDraft(amountText = if (status.remaining > 0) plainNumber(status.remaining) else "")
            } else {
                CardPaymentDraft(
                    amountText = plainNumber(editing.amount), method = editing.method,
                    notes = editing.notes.orEmpty(), date = localDate(editing.paidAt, zone),
                )
            },
        )
    }
    // Al volver a escribir se quita el error anterior.
    LaunchedEffect(draft) { if (error != null) onEdit() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Pago a ${card.name}" else "Editar pago a ${card.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Corte del ${formatDateEs(status.cutoffDate)} · límite de pago ${formatDateEs(status.dueDate)}\n" +
                        "A pagar: ${formatMxn(status.targetAmount)}" +
                        if (status.targetSource == "statement") " (según tu estado de cuenta)" else " (estimado de la app)",
                    style = MaterialTheme.typography.bodySmall,
                )
                PastDateButton(draft.date, today, onChange = { draft = draft.copy(date = it) })
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = { draft = draft.copy(amountText = it) },
                    label = { Text("Monto pagado (MXN)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.method == PaymentMethod.TRANSFER,
                        onClick = { draft = draft.copy(method = PaymentMethod.TRANSFER) },
                        label = { Text("Transferencia") },
                    )
                    FilterChip(
                        selected = draft.method == PaymentMethod.CASH,
                        onClick = { draft = draft.copy(method = PaymentMethod.CASH) },
                        label = { Text("Efectivo") },
                    )
                }
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Editar todo lo de una tarjeta propia: datos, fechas, límite, presupuesto mensual y color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardEditDialog(
    card: mx.finanzas.pareja.data.Card,
    errors: Map<CardField, String>,
    onDismiss: () -> Unit,
    onSave: (CardEditDraft) -> Unit,
) {
    var draft by remember(card.id) {
        mutableStateOf(
            CardDraft(
                name = card.name, bank = card.bank, last4 = card.last4,
                limitText = plainNumber(card.creditLimit),
                cutoffText = card.cutoffDay.toString(), dueText = card.dueDay.toString(),
            ),
        )
    }
    var budgetText by remember(card.id) { mutableStateOf(card.monthlyBudget?.let(::plainNumber).orEmpty()) }
    var color by remember(card.id) { mutableStateOf(card.color) }

    @Composable
    fun field(label: String, value: String, field: CardField, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            isError = errors[field] != null,
            supportingText = errors[field]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar tarjeta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field("Nombre", draft.name, CardField.NAME) { draft = draft.copy(name = it) }
                field("Banco", draft.bank, CardField.BANK) { draft = draft.copy(bank = it) }
                field("Últimos 4 dígitos", draft.last4, CardField.LAST4, KeyboardType.Number) { draft = draft.copy(last4 = it.take(4)) }
                field("Límite de crédito", draft.limitText, CardField.LIMIT, KeyboardType.Decimal) { draft = draft.copy(limitText = it) }
                field("Día de corte (1-31)", draft.cutoffText, CardField.CUTOFF, KeyboardType.Number) { draft = draft.copy(cutoffText = it) }
                field("Día de pago (1-31)", draft.dueText, CardField.DUE, KeyboardType.Number) { draft = draft.copy(dueText = it) }
                field("Presupuesto mensual (opcional)", budgetText, CardField.BUDGET, KeyboardType.Decimal) { budgetText = it }
                Text(
                    "Te avisamos a los dos cuando el gasto del mes lo supere. Las compras a meses cuentan por su " +
                        "mensualidad. Déjalo vacío para no tener presupuesto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Color", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // "Auto": el color de su banco.
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .border(if (color == null) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = null },
                        contentAlignment = Alignment.Center,
                    ) { Text("Auto", style = MaterialTheme.typography.labelSmall) }
                    CARD_COLOR_CHOICES.forEach { hex ->
                        val selected = color.equals(hex, ignoreCase = true)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = hex },
                        )
                    }
                }
                errors[CardField.COLOR]?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(CardEditDraft(draft, budgetText, color)) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CardFormDialog(
    errors: Map<CardField, String>,
    onDismiss: () -> Unit,
    onSave: (CardDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(CardDraft()) }

    @Composable
    fun field(
        label: String,
        value: String,
        field: CardField,
        keyboard: KeyboardType = KeyboardType.Text,
        onChange: (String) -> Unit,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            isError = errors[field] != null,
            supportingText = errors[field]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarjeta") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field("Nombre (ej. Nu azul)", draft.name, CardField.NAME) { draft = draft.copy(name = it) }
                field("Banco", draft.bank, CardField.BANK) { draft = draft.copy(bank = it) }
                field("Últimos 4 dígitos", draft.last4, CardField.LAST4, KeyboardType.Number) { draft = draft.copy(last4 = it.take(4)) }
                field("Límite de crédito", draft.limitText, CardField.LIMIT, KeyboardType.Decimal) { draft = draft.copy(limitText = it) }
                field("Día de corte (1-31)", draft.cutoffText, CardField.CUTOFF, KeyboardType.Number) { draft = draft.copy(cutoffText = it) }
                field("Día de pago (1-31)", draft.dueText, CardField.DUE, KeyboardType.Number) { draft = draft.copy(dueText = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
