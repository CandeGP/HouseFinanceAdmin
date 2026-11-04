package mx.finanzas.pareja.ui.friends

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.Friend
import mx.finanzas.pareja.domain.FriendFilter
import mx.finanzas.pareja.domain.LoanRepaymentDraft
import mx.finanzas.pareja.domain.LoanState
import mx.finanzas.pareja.domain.LoanView
import mx.finanzas.pareja.domain.ageLabel
import mx.finanzas.pareja.domain.buildFriends
import mx.finanzas.pareja.domain.filterFriends
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn
import mx.finanzas.pareja.domain.reminderOrNull
import mx.finanzas.pareja.domain.settlementPlan
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.EmptyState
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.SectionLabel
import mx.finanzas.pareja.ui.components.SelectChip
import mx.finanzas.pareja.ui.dashboard.Scope
import mx.finanzas.pareja.ui.loans.LoansViewModel
import mx.finanzas.pareja.ui.loans.TotalTile

private val FILTER_LABELS = listOf(
    FriendFilter.ALL to "Todos",
    FriendFilter.OWE_YOU to "Te deben",
    FriendFilter.YOU_OWE to "Debes",
    FriendFilter.SETTLED to "Saldados",
)

/**
 * Amigos: las personas con las que llevas cuentas (tu pareja o quien sea), con búsqueda, filtros, el saldo de cada una
 * y su detalle (historial, recordatorio, liquidar y préstamo nuevo). Sale de los saldos de Préstamos, sin tablas nuevas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    vm: LoansViewModel,
    userId: String,
    partnerNames: Set<String>,
    today: LocalDate,
    onBack: () -> Unit,
    /** Abre el formulario de préstamo; [person] llega relleno cuando se pide desde el detalle de alguien. */
    onNewLoan: (person: String?) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Al volver desde el formulario de préstamo, los saldos se recalculan.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FriendFilter.ALL) }

    val friends = remember(state.balances, partnerNames) { buildFriends(state.balances, partnerNames) }
    val current = selected?.let { name -> friends.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    // Si la persona desaparece (por ejemplo, al borrar su último préstamo), se vuelve a la lista.
    BackHandler(enabled = current != null) { selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "Amigos") },
                navigationIcon = {
                    IconButton(onClick = { if (current != null) selected = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (current == null) {
                        IconButton(onClick = { onNewLoan(null) }) { Icon(Icons.Filled.Add, contentDescription = "Nuevo préstamo") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (current != null) {
                FriendDetail(current, vm, userId, today, state.message, onNewLoan)
            } else {
                FriendList(
                    friends = friends,
                    state = state,
                    query = query,
                    onQuery = { query = it },
                    filter = filter,
                    onFilter = { filter = it },
                    onScope = vm::setScope,
                    onOpen = { selected = it.name },
                    onNewLoan = { onNewLoan(null) },
                )
            }
        }
    }
}

@Composable
private fun FriendList(
    friends: List<Friend>,
    state: mx.finanzas.pareja.ui.loans.LoansUiState,
    query: String,
    onQuery: (String) -> Unit,
    filter: FriendFilter,
    onFilter: (FriendFilter) -> Unit,
    onScope: (Scope) -> Unit,
    onOpen: (Friend) -> Unit,
    onNewLoan: () -> Unit,
) {
    val visible = filterFriends(friends, filter, query)
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Míos", state.scope == Scope.MINE, { onScope(Scope.MINE) })
                SelectChip("Ambos", state.scope == Scope.BOTH, { onScope(Scope.BOTH) })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalTile("Te deben", state.totals.owedToYou, AppTheme.tokens.success, Modifier.weight(1f))
                TotalTile("Debes", state.totals.youOwe, AppTheme.tokens.warning, Modifier.weight(1f))
            }
        }
        item { ErrorText(state.message) }
        if (friends.isNotEmpty()) {
            item {
                AuthField(
                    label = "Buscar",
                    value = query,
                    onValueChange = onQuery,
                    placeholder = "Nombre",
                    leadingIcon = Icons.Outlined.Search,
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTER_LABELS.forEach { (value, label) -> SelectChip(label, filter == value, { onFilter(value) }) }
                }
            }
        }
        when {
            friends.isEmpty() && !state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    EmptyState(
                        title = "Aún no llevas cuentas con nadie",
                        subtitle = "Divide un cobro con tu pareja o registra un préstamo y aparecerán aquí con su saldo.",
                        icon = Icons.Outlined.Groups,
                        actionLabel = "Nuevo préstamo",
                        onAction = onNewLoan,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            visible.isEmpty() && friends.isNotEmpty() -> item {
                Text(
                    "Nadie coincide con ese filtro o búsqueda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            else -> items(visible, key = { it.name.lowercase() }) { friend -> FriendRow(friend, onOpen) }
        }
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    val tokens = AppTheme.tokens
    Box(Modifier.size(size.dp).clip(CircleShape).background(tokens.accent), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = tokens.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.4f).sp)
    }
}

@Composable
private fun balanceColor(friend: Friend): Color = when {
    friend.owesYou -> AppTheme.tokens.success
    friend.youOwe -> AppTheme.tokens.warning
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
}

private fun balanceText(friend: Friend): String = when {
    friend.owesYou -> "Te debe ${formatMxn(friend.net)}"
    friend.youOwe -> "Le debes ${formatMxn(-friend.net)}"
    friend.openLoans == 0 -> "Saldado"
    else -> "A mano"
}

@Composable
private fun PartnerBadge() {
    Text(
        "Pareja",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun FriendRow(friend: Friend, onClick: (Friend) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = { onClick(friend) }) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(friend.name, 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(friend.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (friend.isPartner) PartnerBadge()
                }
                Text(balanceText(friend), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = balanceColor(friend))
                val loansText = when (friend.openLoans) {
                    0 -> "Sin préstamos abiertos"
                    1 -> "1 préstamo abierto"
                    else -> "${friend.openLoans} préstamos abiertos"
                }
                Text(loansText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FriendDetail(
    friend: Friend,
    vm: LoansViewModel,
    userId: String,
    today: LocalDate,
    message: String?,
    onNewLoan: (String?) -> Unit,
) {
    val context = LocalContext.current
    var confirmSettle by remember { mutableStateOf(false) }
    val plan = settlementPlan(friend, userId)
    val reminder = reminderOrNull(friend)

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Avatar(friend.name, 56)
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(friend.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            if (friend.isPartner) PartnerBadge()
                        }
                        Text(balanceText(friend), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = balanceColor(friend))
                        friend.lastActivity?.let {
                            Text(
                                "Última actividad ${ageLabel(it, today)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Nuevo préstamo", false, { onNewLoan(friend.name) })
                if (reminder != null) {
                    SelectChip("Recordar", false, {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reminder)
                        }
                        context.startActivity(Intent.createChooser(send, "Recordar a ${friend.name}"))
                    })
                }
                if (plan.isNotEmpty()) SelectChip("Liquidar todo", false, { confirmSettle = true })
            }
        }
        item { ErrorText(message) }
        item { SectionLabel("HISTORIAL") }
        items(friend.balance.loans, key = { it.loan.id }) { view -> LoanHistoryCard(view) }
    }

    if (confirmSettle) {
        val total = plan.sumOf { it.second }
        AlertDialog(
            onDismissRequest = { confirmSettle = false },
            title = { Text("¿Liquidar todo con ${friend.name}?") },
            text = {
                Text(
                    "Se registra un abono por lo que falta de ${plan.size} préstamo(s), ${formatMxn(total)} en total. " +
                        "Puedes editar o borrar cada abono después.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSettle = false
                    plan.forEach { (view, amount) ->
                        vm.addRepayment(
                            view,
                            LoanRepaymentDraft(
                                amountText = "%.2f".format(Locale.US, amount),
                                method = PaymentMethod.TRANSFER,
                                note = "Liquidación",
                            ),
                        )
                    }
                }) { Text("Liquidar") }
            },
            dismissButton = { TextButton(onClick = { confirmSettle = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LoanHistoryCard(view: LoanView) {
    val tokens = AppTheme.tokens
    val lent = view.loan.direction == LoanDirection.LENT
    val statusLabel = when (view.state) {
        LoanState.OPEN -> "Abierto"
        LoanState.SETTLED -> "Saldado"
        LoanState.FORGIVEN -> "Perdonado"
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (lent) "Le prestaste " else "Te prestó ") + formatMxn(view.loan.amount),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        formatDateEs(view.loan.occurredOn),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                }
                Text(
                    statusLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (view.state == LoanState.OPEN) tokens.warning else tokens.success,
                )
            }
            view.loan.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }
            if (view.state == LoanState.OPEN) {
                Text("Falta ${formatMxn(view.remaining)}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            view.repayments.sortedByDescending { it.paidOn }.forEach { repayment ->
                Text(
                    "Abono ${formatMxn(repayment.amount)} · ${formatDateEs(repayment.paidOn)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
