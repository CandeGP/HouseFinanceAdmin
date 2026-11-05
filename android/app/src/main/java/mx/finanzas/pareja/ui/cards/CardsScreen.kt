package mx.finanzas.pareja.ui.cards

import mx.finanzas.pareja.ui.components.LocalDockInset
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
import mx.finanzas.pareja.ui.components.GlassCard
import androidx.compose.material3.FilterChip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.AppTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    // Tarjeta cuyo detalle está abierto (pantalla completa); null = se ve la lista.
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    // La tarjeta al frente de la pila; por defecto, la primera.
    var selectedMine by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArchive by remember { mutableStateOf<mx.finanzas.pareja.data.Card?>(null) }
    var budgetFor by remember { mutableStateOf<mx.finanzas.pareja.data.Card?>(null) }
    var payFor by remember { mutableStateOf<Pair<mx.finanzas.pareja.data.Card, CardCycleStatus>?>(null) }
    var editPayment by remember {
        mutableStateOf<Triple<mx.finanzas.pareja.data.Card, CardCycleStatus, CardPayment>?>(null)
    }
    var pendingDeletePayment by remember { mutableStateOf<CardPayment?>(null) }
    var balanceFor by remember { mutableStateOf<mx.finanzas.pareja.data.Card?>(null) }
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

    LaunchedEffect(state.balanceSaved) {
        if (state.balanceSaved) {
            balanceFor = null
            vm.consumeBalanceSaved()
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

    LaunchedEffect(detailId) { detailId?.let(vm::loadMovements) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // Pantalla de detalle de una tarjeta; si se archiva o desaparece, se vuelve a la lista sola.
        val detailCard = detailId?.let { id -> state.cards.firstOrNull { it.id == id } }
        if (detailCard != null) {
            BackHandler { detailId = null }
            Box(Modifier.padding(padding)) {
                CardDetailScreen(
                    card = detailCard,
                    mine = detailCard.ownerId == userId,
                    ownerName = memberNames[detailCard.ownerId],
                    debt = state.appDebts[detailCard.id],
                    status = state.statuses[detailCard.id],
                    payments = state.payments[detailCard.id].orEmpty(),
                    movements = state.movements,
                    spend = state.spend[detailCard.id],
                    balanceCheck = state.balanceChecks[detailCard.id],
                    zone = zone,
                    onBack = { detailId = null },
                    onEdit = { budgetFor = detailCard },
                    onPay = { state.statuses[detailCard.id]?.let { payFor = detailCard to it } },
                    onEditPayment = { payment -> state.statuses[detailCard.id]?.let { editPayment = Triple(detailCard, it, payment) } },
                    onDeletePayment = { pendingDeletePayment = it },
                    onCaptureBalance = { balanceFor = detailCard },
                    onArchive = { pendingArchive = detailCard },
                    onToggleFrozen = { vm.setFrozen(detailCard.id, !detailCard.frozen) },
                )
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tarjetas", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // El estado de cuenta se concilia contra una tarjeta tuya: sin ninguna, el acceso no tiene sentido.
                    if (hasOwnCard) {
                        Surface(
                            onClick = onImportStatement,
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = AppTheme.tokens.glass,
                            border = BorderStroke(1.dp, AppTheme.tokens.glassBorder),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.UploadFile, contentDescription = "Importar estado de cuenta (PDF)",
                                    tint = AppTheme.tokens.chart[3], modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = { showForm = true },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = AppTheme.tokens.glass,
                        border = BorderStroke(1.dp, AppTheme.tokens.glassBorder),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = "Agregar tarjeta", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            ErrorText(state.message, Modifier.padding(horizontal = 20.dp))

            // Siempre se muestra la lista: así "Archivadas" es visible aunque no quede ninguna activa.
            run {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + LocalDockInset.current),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.loading && state.cards.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    if (state.cards.isEmpty() && !state.loading) {
                        item {
                            Text(
                                "Sin tarjetas. Agrega tus tarjetas de crédito con el botón +.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Solo tus tarjetas: las de tu pareja no se muestran aquí.
                    for (isMine in listOf(true)) {
                    val group = state.cards.filter { it.ownerId == userId }
                    if (group.isEmpty()) continue
                    val current = group.firstOrNull { it.id == selectedMine } ?: group.first()
                    item(key = "header-$isMine") {
                        Text(
                            "Mis tarjetas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item(key = "stack-$isMine") {
                        WalletStack(
                            cards = group,
                            selectedId = current.id,
                            onSelect = { selectedMine = it },
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    // Resumen de la tarjeta al frente; el detalle completo (pagos, presupuesto, conciliación) va en su pantalla.
                    item(key = "panel-$isMine") {
                        val card = current
                        CardSummaryPanel(
                            card = card,
                            debt = state.appDebts[card.id],
                            today = today,
                            mine = card.ownerId == userId,
                            canPay = state.statuses[card.id] != null,
                            onPay = { state.statuses[card.id]?.let { payFor = card to it } },
                            onDetail = { detailId = card.id },
                            onEdit = { budgetFor = card },
                        )
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
                            GlassCard(Modifier.fillMaxWidth()) {
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
        CardForm(
            initial = card,
            errors = state.editErrors,
            onDismiss = { budgetFor = null; vm.consumeEdited() },
            onSave = { draft -> vm.saveEdit(card.id, draft) },
            onArchive = { budgetFor = null; vm.consumeEdited(); pendingArchive = card },
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

    balanceFor?.let { card ->
        BalanceCheckDialog(
            card = card,
            appDebt = state.appDebts[card.id],
            error = state.balanceError,
            onDismiss = { balanceFor = null; vm.consumeBalanceSaved() },
            onSave = { text -> vm.saveBalanceCheck(card, text) },
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
        CardForm(
            initial = null,
            errors = state.errors,
            onDismiss = { showForm = false },
            onSave = vm::addCard,
        )
    }
}

/** Progreso del gasto del mes frente al presupuesto, o la invitación a fijar uno (solo tu tarjeta). */
@Composable
internal fun BudgetLine(card: mx.finanzas.pareja.data.Card, spend: MonthSpend?, mine: Boolean) {
    val budget = card.monthlyBudget
    when {
        budget == null && mine -> Text(
            "Toca Editar para fijar un presupuesto mensual",
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
                val typed = mx.finanzas.pareja.domain.parseAmount(draft.amountText)
                val excess = if (editing == null && typed != null) typed - status.remaining.coerceAtLeast(0.0) else 0.0
                if (excess > 0.005) {
                    val next = mx.finanzas.pareja.data.PaymentDestination.NEXT_CYCLE
                    val capital = mx.finanzas.pareja.data.PaymentDestination.CAPITAL
                    Text(
                        if (status.remaining > 0) {
                            "Sobran ${mx.finanzas.pareja.domain.formatMxn(excess)} del corte. ¿A dónde van?"
                        } else {
                            "El corte ya está pagado: ${mx.finanzas.pareja.domain.formatMxn(excess)} de más. ¿A dónde van?"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draft.excessTo == next,
                            onClick = { draft = draft.copy(excessTo = next) },
                            label = { Text("Siguiente corte") },
                        )
                        FilterChip(
                            selected = draft.excessTo == capital,
                            onClick = { draft = draft.copy(excessTo = capital) },
                            label = { Text("Abono a capital") },
                        )
                    }
                    Text(
                        if (draft.excessTo == capital) "Baja lo que falta de tus compras a meses."
                        else "Baja lo que pagarás en el siguiente corte de la tarjeta.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
