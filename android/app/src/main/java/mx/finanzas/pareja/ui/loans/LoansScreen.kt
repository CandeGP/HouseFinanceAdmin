package mx.finanzas.pareja.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.LoanRepayment
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.LoanDraft
import mx.finanzas.pareja.domain.LoanField
import mx.finanzas.pareja.domain.LoanRepaymentDraft
import mx.finanzas.pareja.domain.LoanState
import mx.finanzas.pareja.domain.LoanView
import mx.finanzas.pareja.domain.PersonBalance
import mx.finanzas.pareja.domain.ageLabel
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.label
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.ScopeToggle
import mx.finanzas.pareja.ui.payments.PayeeField

private fun plainNumber(value: Double): String = "%.2f".format(Locale.US, value)
private fun methodName(method: PaymentMethod) = if (method == PaymentMethod.CASH) "Efectivo" else "Transferencia"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    vm: LoansViewModel,
    userId: String,
    memberNames: Map<String, String>,
    today: LocalDate,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }
    var repayFor by remember { mutableStateOf<LoanView?>(null) }
    var deleteLoan by remember { mutableStateOf<LoanView?>(null) }
    var deleteRepayment by remember { mutableStateOf<Pair<LoanView, LoanRepayment>?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            showNew = false
            repayFor = null
            vm.consumeSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Préstamos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo préstamo")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Te deben ${formatMxn(state.totals.owedToYou)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Debes ${formatMxn(state.totals.youOwe)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScopeToggle(scope = state.scope, onChange = vm::setScope)
            }
            ErrorText(state.message, Modifier.padding(horizontal = 16.dp))

            if (state.balances.isEmpty() && !state.loading) {
                EmptyState(
                    title = "Sin préstamos",
                    subtitle = "Anota lo que prestas o te prestan y ve cuánto falta por cobrar o pagar.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.balances, key = { it.person.lowercase() }) { balance ->
                        PersonCard(
                            balance = balance,
                            userId = userId,
                            memberNames = memberNames,
                            today = today,
                            onRepay = { repayFor = it },
                            onForgive = { view -> vm.setForgiven(view.loan.id, view.state != LoanState.FORGIVEN) },
                            onDeleteLoan = { deleteLoan = it },
                            onDeleteRepayment = { view, repayment -> deleteRepayment = view to repayment },
                        )
                    }
                }
            }
        }
    }

    if (showNew) {
        NewLoanDialog(
            people = state.people,
            errors = state.loanErrors,
            onDismiss = { showNew = false; vm.consumeSaved() },
            onEdit = vm::consumeSaved,
            onSave = vm::addLoan,
        )
    }

    repayFor?.let { view ->
        RepaymentDialog(
            view = view,
            error = state.repaymentError,
            onDismiss = { repayFor = null; vm.consumeSaved() },
            onEdit = vm::consumeSaved,
            onSave = { draft -> vm.addRepayment(view, draft) },
        )
    }

    deleteLoan?.let { view ->
        AlertDialog(
            onDismissRequest = { deleteLoan = null },
            title = { Text("¿Borrar este préstamo?") },
            text = {
                Text("${view.loan.person} · ${formatMxn(view.loan.amount)}. También se borran sus abonos.")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteLoan(view.loan.id); deleteLoan = null }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleteLoan = null }) { Text("Cancelar") } },
        )
    }

    deleteRepayment?.let { (view, repayment) ->
        AlertDialog(
            onDismissRequest = { deleteRepayment = null },
            title = { Text("¿Borrar este abono?") },
            text = {
                Text(
                    "${formatMxn(repayment.amount)} del ${formatDateEs(repayment.paidOn)}. " +
                        "Vuelve a faltar ese monto del préstamo.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteRepayment(view.loan.id, repayment.id); deleteRepayment = null }) {
                    Text("Borrar")
                }
            },
            dismissButton = { TextButton(onClick = { deleteRepayment = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PersonCard(
    balance: PersonBalance,
    userId: String,
    memberNames: Map<String, String>,
    today: LocalDate,
    onRepay: (LoanView) -> Unit,
    onForgive: (LoanView) -> Unit,
    onDeleteLoan: (LoanView) -> Unit,
    onDeleteRepayment: (LoanView, LoanRepayment) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(balance.label(), style = MaterialTheme.typography.titleMedium)
            balance.loans.forEach { view ->
                val mine = view.loan.ownerId == userId
                LoanRow(
                    view = view,
                    ownerName = if (mine) null else memberNames[view.loan.ownerId],
                    today = today,
                    onRepay = if (mine) ({ onRepay(view) }) else null,
                    onForgive = if (mine) ({ onForgive(view) }) else null,
                    onDeleteLoan = if (mine) ({ onDeleteLoan(view) }) else null,
                    onDeleteRepayment = if (mine) ({ repayment -> onDeleteRepayment(view, repayment) }) else null,
                )
            }
        }
    }
}

@Composable
private fun LoanRow(
    view: LoanView,
    ownerName: String?,
    today: LocalDate,
    onRepay: (() -> Unit)?,
    onForgive: (() -> Unit)?,
    onDeleteLoan: (() -> Unit)?,
    onDeleteRepayment: ((LoanRepayment) -> Unit)?,
) {
    val loan = view.loan
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            listOfNotNull(
                formatMxn(loan.amount),
                if (loan.direction == LoanDirection.LENT) "Le presté" else "Me prestó",
                ageLabel(loan.occurredOn, today),
                methodName(loan.method),
                ownerName?.let { "de $it" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!loan.note.isNullOrBlank()) Text(loan.note, style = MaterialTheme.typography.bodySmall, color = muted)
        Text(
            when (view.state) {
                LoanState.OPEN ->
                    if (view.repaid > 0) "Abonado ${formatMxn(view.repaid)} · faltan ${formatMxn(view.remaining)}"
                    else "Falta todo: ${formatMxn(view.remaining)}"
                LoanState.SETTLED -> "Liquidado ✓"
                LoanState.FORGIVEN -> "Perdonado (ya no cuenta como deuda)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (view.state == LoanState.SETTLED) MaterialTheme.colorScheme.tertiary else muted,
        )
        view.repayments.forEach { repayment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatDateEs(repayment.paidOn)} · ${methodName(repayment.method)} · ${formatMxn(repayment.amount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
                if (onDeleteRepayment != null) {
                    TextButton(onClick = { onDeleteRepayment(repayment) }) { Text("Borrar") }
                }
            }
        }
        if (onRepay != null || onForgive != null || onDeleteLoan != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRepay != null && view.state == LoanState.OPEN) {
                    TextButton(onClick = onRepay) { Text("Abonar") }
                }
                if (onForgive != null && view.state != LoanState.SETTLED) {
                    TextButton(onClick = onForgive) {
                        Text(if (view.state == LoanState.FORGIVEN) "Reabrir" else "Perdonar")
                    }
                }
                if (onDeleteLoan != null) {
                    IconButton(onClick = onDeleteLoan) { Icon(Icons.Filled.Delete, contentDescription = "Borrar préstamo") }
                }
            }
        }
    }
}

@Composable
private fun MethodChips(method: PaymentMethod, onChange: (PaymentMethod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = method == PaymentMethod.TRANSFER,
            onClick = { onChange(PaymentMethod.TRANSFER) },
            label = { Text("Transferencia") },
        )
        FilterChip(
            selected = method == PaymentMethod.CASH,
            onClick = { onChange(PaymentMethod.CASH) },
            label = { Text("Efectivo") },
        )
    }
}

@Composable
private fun NewLoanDialog(
    people: List<String>,
    errors: Map<LoanField, String>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (LoanDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(LoanDraft()) }
    // Al volver a escribir se quita el error anterior.
    LaunchedEffect(draft) { if (errors.isNotEmpty()) onEdit() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo préstamo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.direction == LoanDirection.LENT,
                        onClick = { draft = draft.copy(direction = LoanDirection.LENT) },
                        label = { Text("Le presté") },
                    )
                    FilterChip(
                        selected = draft.direction == LoanDirection.BORROWED,
                        onClick = { draft = draft.copy(direction = LoanDirection.BORROWED) },
                        label = { Text("Me prestaron") },
                    )
                }
                PayeeField(
                    value = draft.person,
                    payees = people,
                    enabled = true,
                    onChange = { draft = draft.copy(person = it) },
                    label = "Persona",
                    error = errors[LoanField.PERSON],
                )
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = { draft = draft.copy(amountText = it) },
                    label = { Text("Monto (MXN)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errors[LoanField.AMOUNT] != null,
                    supportingText = errors[LoanField.AMOUNT]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                MethodChips(draft.method) { draft = draft.copy(method = it) }
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RepaymentDialog(
    view: LoanView,
    error: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (LoanRepaymentDraft) -> Unit,
) {
    var draft by remember(view.loan.id) { mutableStateOf(LoanRepaymentDraft(amountText = plainNumber(view.remaining))) }
    // Al volver a escribir se quita el error anterior.
    LaunchedEffect(draft) { if (error != null) onEdit() }
    val direction = if (view.loan.direction == LoanDirection.LENT) "te pagó" else "le pagaste"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abono de ${view.loan.person}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Faltan ${formatMxn(view.remaining)} de ${formatMxn(view.loan.amount)}. Registra lo que $direction.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = { draft = draft.copy(amountText = it) },
                    label = { Text("Monto (MXN)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                MethodChips(draft.method) { draft = draft.copy(method = it) }
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
