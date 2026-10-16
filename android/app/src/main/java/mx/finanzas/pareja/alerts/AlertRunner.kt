package mx.finanzas.pareja.alerts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.FinanzasApp
import mx.finanzas.pareja.data.cycleStatuses
import mx.finanzas.pareja.domain.ALERT_REMINDER_DAYS
import mx.finanzas.pareja.domain.CardCycles
import mx.finanzas.pareja.domain.CardMonthSpend
import mx.finanzas.pareja.domain.CategoryMonthSpend
import mx.finanzas.pareja.domain.buildLoanViews
import mx.finanzas.pareja.domain.loanReminders
import mx.finanzas.pareja.domain.budgetAlerts
import mx.finanzas.pareja.domain.budgetWarnings
import mx.finanzas.pareja.domain.categoryBudgetAlerts
import mx.finanzas.pareja.domain.dueAlertKey
import mx.finanzas.pareja.domain.isSettled
import mx.finanzas.pareja.domain.reminderKey
import mx.finanzas.pareja.domain.upcomingAlerts

/**
 * Calcula y publica los avisos pendientes: cortes y pagos de tus tarjetas, y presupuestos superados
 * de **todas** las tarjetas del hogar (así los dos se enteran). Lo usan el trabajador diario, la
 * apertura de la app y los cambios de datos. Un mismo aviso no se repite (ver [AlertPrefs]).
 */
object AlertRunner {
    private val lock = Mutex() // evita avisos duplicados si dos disparadores coinciden

    /** Propaga las excepciones de red: el trabajador las usa para reintentar. */
    suspend fun run(app: FinanzasApp) = lock.withLock {
        if (!AlertPrefs.isEnabled(app)) return@withLock
        val repo = app.repository
        val userId = repo.currentUserId() ?: return@withLock // sin sesión no hay nada que avisar
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val cards = repo.cards().filter { !it.archived }

        val cycles = cards.filter { it.ownerId == userId }.map { card ->
            val current = repo.cycleEstimate(card.id, today)
            // El pago que sigue a un corte reciente vive en el ciclo anterior.
            val previous = current?.let { repo.cycleEstimate(card.id, it.cycleStart.minus(1, DateTimeUnit.DAY)) }
            CardCycles(card, current, previous)
        }
        val budgets = cards.filter { it.monthlyBudget != null }
            .map { CardMonthSpend(it, repo.cardMonthSpend(it.id, today)) }

        // Un pago ya cubierto no se avisa: se descartan los avisos de pago de los ciclos liquidados.
        val settled = repo.cycleStatuses(cards.filter { it.ownerId == userId }.map { it.id }, today)
            .filterValues { it.isSettled }
            .map { (cardId, status) -> status.dueAlertKey(cardId) }
            .toSet()

        // Presupuestos por categoría (individuales): el gasto y el presupuesto son de esta persona.
        val budgetedSpends = repo.categoryMonthSpend(today).filter { it.budget != null }
        val categorySpends = if (budgetedSpends.isEmpty()) emptyList() else {
            val categoryById = repo.categories().associateBy { it.id }
            budgetedSpends.mapNotNull { spend -> categoryById[spend.categoryId]?.let { CategoryMonthSpend(it, spend) } }
        }

        // Préstamos propios abiertos con 15+ días; si falla la consulta no se tumban los demás avisos.
        val loanAlerts = runCatching {
            val mine = repo.loans().filter { it.ownerId == userId }
            loanReminders(buildLoanViews(mine, repo.loanRepayments()), today)
        }.getOrDefault(emptyList())

        val pending = (upcomingAlerts(cycles, today).filter { it.daysLeft in ALERT_REMINDER_DAYS } +
            budgetAlerts(budgets) + budgetWarnings(budgets) + categoryBudgetAlerts(categorySpends) + loanAlerts)
            .filterNot { it.key in settled }

        pending
            .filterNot { AlertPrefs.wasNotified(app, it.reminderKey) }
            .forEach { alert -> if (AlertNotifier.post(app, alert)) AlertPrefs.markNotified(app, alert.reminderKey) }
    }
}
