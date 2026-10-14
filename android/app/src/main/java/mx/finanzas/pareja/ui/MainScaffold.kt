package mx.finanzas.pareja.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.domain.timeText
import mx.finanzas.pareja.ui.account.AccountScreen
import mx.finanzas.pareja.ui.account.AccountViewModel
import mx.finanzas.pareja.ui.cards.CardsScreen
import mx.finanzas.pareja.ui.cards.CardsViewModel
import mx.finanzas.pareja.ui.dashboard.DashboardScreen
import mx.finanzas.pareja.ui.dashboard.DashboardViewModel
import java.util.UUID
import mx.finanzas.pareja.ui.inbox.InboxScreen
import mx.finanzas.pareja.ui.inbox.InboxViewModel
import mx.finanzas.pareja.ui.payments.ManualPaymentFormViewModel
import mx.finanzas.pareja.ui.payments.ManualPaymentsViewModel
import mx.finanzas.pareja.ui.payments.PaymentFormScreen
import mx.finanzas.pareja.ui.payments.PaymentsScreen
import mx.finanzas.pareja.ui.session.SessionState
import mx.finanzas.pareja.ui.transactions.TransactionFormScreen
import mx.finanzas.pareja.ui.transactions.TransactionFormViewModel
import mx.finanzas.pareja.ui.transactions.TransactionsScreen
import mx.finanzas.pareja.ui.transactions.TransactionsViewModel

private val MEXICO_CITY = TimeZone.of("America/Mexico_City")
private fun today(): LocalDate = Clock.System.todayIn(MEXICO_CITY)

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_TRANSACTIONS = "transactions"
private const val ROUTE_CARDS = "cards"
private const val ROUTE_ACCOUNT = "account"
private const val ROUTE_PAYMENTS = "payments"
private const val ROUTE_INBOX = "inbox"
private const val ROUTE_FORM = "tx_form?id={id}"
private const val ROUTE_PAYMENT_FORM = "payment_form?id={id}"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(ROUTE_DASHBOARD, "Resumen", Icons.Filled.Dashboard),
    Tab(ROUTE_TRANSACTIONS, "Gastos", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab(ROUTE_CARDS, "Tarjetas", Icons.Filled.CreditCard),
    Tab(ROUTE_PAYMENTS, "Pagos", Icons.Filled.Payments),
    Tab(ROUTE_ACCOUNT, "Cuenta", Icons.Filled.Person),
)

@Composable
fun MainScaffold(
    repo: FinanceRepository,
    session: SessionState.Ready,
    onSignOut: () -> Unit,
) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    // Se incrementa al guardar/borrar para que las pestañas vuelvan a cargar sus datos.
    var refreshKey by remember { mutableIntStateOf(0) }

    val userId = session.userId
    val householdId = session.me.householdId
    val names = remember(session.members) { session.members.associate { it.userId to it.displayName } }

    Scaffold(
        bottomBar = {
            if (route in TABS.map { it.route }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = ROUTE_DASHBOARD,
            modifier = Modifier.padding(padding).padding(top = 0.dp),
        ) {
            composable(ROUTE_DASHBOARD) {
                val vm: DashboardViewModel = viewModel(
                    factory = viewModelFactory { DashboardViewModel(repo, userId, ::today) },
                )
                DashboardScreen(
                    vm = vm,
                    memberNames = names,
                    refreshKey = refreshKey,
                    onAddCard = { nav.navigate(ROUTE_CARDS) },
                    onOpenInbox = { nav.navigate(ROUTE_INBOX) },
                )
            }
            composable(ROUTE_INBOX) {
                val vm: InboxViewModel = viewModel(
                    factory = viewModelFactory { InboxViewModel(repo, userId) },
                )
                InboxScreen(vm = vm, onBack = { nav.popBackStack() }, onChanged = { refreshKey++ })
            }
            composable(ROUTE_TRANSACTIONS) {
                val vm: TransactionsViewModel = viewModel(
                    factory = viewModelFactory { TransactionsViewModel(repo, userId, today()) },
                )
                TransactionsScreen(
                    vm = vm,
                    userId = userId,
                    memberNames = names,
                    refreshKey = refreshKey,
                    onNew = { nav.navigate("tx_form") },
                    onEdit = { id -> nav.navigate("tx_form?id=$id") },
                )
            }
            composable(ROUTE_CARDS) {
                val vm: CardsViewModel = viewModel(
                    factory = viewModelFactory { CardsViewModel(repo, userId, householdId) },
                )
                CardsScreen(
                    vm = vm,
                    userId = userId,
                    memberNames = names,
                    openAddOnStart = false,
                    onChanged = { refreshKey++ },
                )
            }
            composable(ROUTE_PAYMENTS) {
                val vm: ManualPaymentsViewModel = viewModel(
                    factory = viewModelFactory { ManualPaymentsViewModel(repo, userId, today(), MEXICO_CITY) },
                )
                PaymentsScreen(
                    vm = vm,
                    userId = userId,
                    memberNames = names,
                    zone = MEXICO_CITY,
                    refreshKey = refreshKey,
                    onNew = { nav.navigate("payment_form") },
                    onEdit = { id -> nav.navigate("payment_form?id=$id") },
                )
            }
            composable(
                route = ROUTE_PAYMENT_FORM,
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val editingId = entry.arguments?.getString("id")
                val vm: ManualPaymentFormViewModel = viewModel(
                    factory = viewModelFactory {
                        ManualPaymentFormViewModel(
                            repo = repo, userId = userId, householdId = householdId, editingId = editingId,
                            today = today(), zone = MEXICO_CITY, newFileId = { UUID.randomUUID().toString() },
                            initialTime = timeText(Clock.System.now(), MEXICO_CITY),
                        )
                    },
                )
                PaymentFormScreen(
                    vm = vm,
                    zone = MEXICO_CITY,
                    onDone = { saved ->
                        if (saved) refreshKey++
                        nav.popBackStack()
                    },
                )
            }
            composable(ROUTE_ACCOUNT) {
                val vm: AccountViewModel = viewModel(
                    factory = viewModelFactory { AccountViewModel(repo, householdId) },
                )
                AccountScreen(vm = vm, session = session, onSignOut = onSignOut)
            }
            composable(
                route = ROUTE_FORM,
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val editingId = entry.arguments?.getString("id")
                val vm: TransactionFormViewModel = viewModel(
                    factory = viewModelFactory {
                        TransactionFormViewModel(repo, userId, householdId, editingId, today())
                    },
                )
                TransactionFormScreen(
                    vm = vm,
                    onDone = { saved ->
                        if (saved) refreshKey++
                        nav.popBackStack()
                    },
                )
            }
        }
    }
}
