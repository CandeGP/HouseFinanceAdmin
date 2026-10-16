package mx.finanzas.pareja.domain

import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.CategorySpend

/** A partir de este porcentaje del presupuesto se avisa que ya vas cerca. */
const val BUDGET_WARNING_PCT = 80

/** Categoría con su gasto del mes calendario. */
data class CategoryMonthSpend(val category: Category, val spend: CategorySpend)

enum class BudgetLevel { NONE, OK, NEAR, OVER }

/** Fila de la pantalla de presupuestos: gasto del mes de una categoría frente a su presupuesto. */
data class CategoryBudgetRow(val category: Category, val spent: Double, val budget: Double?) {
    val pct: Double? get() = budget?.let { spent / it * 100 }
    val level: BudgetLevel
        get() = when {
            budget == null -> BudgetLevel.NONE
            spent > budget -> BudgetLevel.OVER
            spent >= budget * BUDGET_WARNING_PCT / 100 -> BudgetLevel.NEAR
            else -> BudgetLevel.OK
        }
}

/** Primero las que tienen presupuesto (la más usada arriba), luego las demás por gasto. */
fun categoryBudgetRows(categories: List<Category>, spends: List<CategorySpend>): List<CategoryBudgetRow> {
    val spentById = spends.associate { it.categoryId to it.spent }
    return categories
        .map { CategoryBudgetRow(it, spentById[it.id] ?: 0.0, it.monthlyBudget) }
        .sortedWith(
            compareBy<CategoryBudgetRow> { it.budget == null }
                .thenByDescending { it.pct ?: 0.0 }
                .thenByDescending { it.spent }
                .thenBy { it.category.name.lowercase() },
        )
}

private fun monthKey(day: LocalDate) = "${day.year}-${day.monthNumber.toString().padStart(2, '0')}"

private fun isNear(spent: Double, budget: Double) = spent >= budget * BUDGET_WARNING_PCT / 100 && spent <= budget

/**
 * Aviso preventivo de los presupuestos por tarjeta: el gasto del mes ya está entre el 80% y el 100%
 * (inclusive) del presupuesto. Al pasarse de ahí deja de avisar esto y sale el aviso de [budgetAlerts].
 * Una vez por tarjeta y mes.
 */
fun budgetWarnings(items: List<CardMonthSpend>): List<CycleAlert> =
    items.mapNotNull { (card, spend) ->
        val budget = spend?.budget ?: return@mapNotNull null
        if (!isNear(spend.spent, budget)) return@mapNotNull null

        val percent = (spend.spent / budget * 100).roundToInt()
        spend.spent / budget to CycleAlert(
            key = "${card.id}:${AlertKind.BUDGET_NEAR}:${monthKey(spend.monthStart)}",
            kind = AlertKind.BUDGET_NEAR,
            cardId = card.id,
            daysLeft = 0,
            title = "Vas en $percent% del presupuesto de ${cardLabel(card)}",
            text = "Llevas ${formatMxn(spend.spent)} de ${formatMxn(budget)} este mes; " +
                "te quedan ${formatMxn(budget - spend.spent)}.",
            amount = spend.spent,
        )
    }.sortedByDescending { it.first }.map { it.second }

/**
 * Avisos de los presupuestos por categoría (del hogar): primero las que se pasaron (las que más se
 * pasaron arriba) y luego las que van entre el 80% y el 100%. Cada aviso sale una vez por categoría y mes.
 */
fun categoryBudgetAlerts(items: List<CategoryMonthSpend>): List<CycleAlert> {
    val over = mutableListOf<Pair<Double, CycleAlert>>()
    val near = mutableListOf<Pair<Double, CycleAlert>>()
    for ((category, spend) in items) {
        val budget = spend.budget ?: continue
        val month = monthKey(spend.monthStart)
        val percent = (spend.spent / budget * 100).roundToInt()
        when {
            spend.spent > budget -> over += (spend.spent - budget) to CycleAlert(
                key = "cat:${category.id}:${AlertKind.BUDGET}:$month",
                kind = AlertKind.BUDGET, cardId = "", daysLeft = 0,
                title = "Superaste el presupuesto de ${category.name}",
                text = "Llevas ${formatMxn(spend.spent)} de ${formatMxn(budget)} este mes ($percent%), " +
                    "${formatMxn(spend.spent - budget)} de más.",
                amount = spend.spent,
            )
            isNear(spend.spent, budget) -> near += (spend.spent / budget) to CycleAlert(
                key = "cat:${category.id}:${AlertKind.BUDGET_NEAR}:$month",
                kind = AlertKind.BUDGET_NEAR, cardId = "", daysLeft = 0,
                title = "Vas en $percent% del presupuesto de ${category.name}",
                text = "Llevas ${formatMxn(spend.spent)} de ${formatMxn(budget)} este mes; " +
                    "te quedan ${formatMxn(budget - spend.spent)}.",
                amount = spend.spent,
            )
        }
    }
    return over.sortedByDescending { it.first }.map { it.second } + near.sortedByDescending { it.first }.map { it.second }
}
