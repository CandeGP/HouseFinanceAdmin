package mx.finanzas.pareja.domain

import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CycleEstimate

data class CardCycleView(val card: Card, val estimate: CycleEstimate)

data class CardRank(val view: CardCycleView, val rank: Int, val sharePct: Double)

data class DashboardSummary(
    val ranking: List<CardRank>,
    val totalCycle: Double,
    val totalMsiRemaining: Double,
) {
    /** Tarjeta con más gasto en su ciclo actual; null si nadie ha gastado. */
    val topCard: Card?
        get() = ranking.firstOrNull()?.takeIf { it.view.estimate.cycleTotal > 0 }?.view?.card
}

fun buildDashboard(views: List<CardCycleView>): DashboardSummary {
    val total = views.sumOf { it.estimate.cycleTotal }
    val ranking = views
        .sortedByDescending { it.estimate.cycleTotal }
        .mapIndexed { index, view ->
            CardRank(
                view = view,
                rank = index + 1,
                sharePct = if (total > 0) view.estimate.cycleTotal / total * 100 else 0.0,
            )
        }
    return DashboardSummary(
        ranking = ranking,
        totalCycle = total,
        totalMsiRemaining = views.sumOf { it.estimate.msiRemaining },
    )
}
