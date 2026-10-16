package mx.finanzas.pareja.domain

import mx.finanzas.pareja.data.Card

/** Cobro recién registrado en el servidor, con su tarjeta si se pudo identificar. */
data class NewCharge(val txId: String, val merchant: String, val amount: Double, val card: Card?)

private const val UNKNOWN_MERCHANT = "Sin comercio"

/** Aviso "Gastaste $X en Y" de un cobro. La llave es por cobro, así no se repite. */
fun chargeAlert(charge: NewCharge): CycleAlert {
    val merchant = charge.merchant.trim().takeUnless { it.isEmpty() || it == UNKNOWN_MERCHANT }
    return CycleAlert(
        key = "charge:${charge.txId}",
        kind = AlertKind.CHARGE,
        cardId = charge.card?.id.orEmpty(),
        daysLeft = 0,
        title = "Gastaste ${formatMxn(charge.amount)}" + (merchant?.let { " en $it" } ?: ""),
        text = (charge.card?.let { "Con ${cardLabel(it)}" } ?: "No pude identificar la tarjeta") +
            " · revísalo en la bandeja",
        amount = charge.amount,
    )
}

/**
 * Avisos de los cobros capturados. Cada par trae si el servidor lo marcó como duplicado: el mismo cobro
 * suele llegar dos veces (app del banco y Billetera de Google) y solo el primero avisa.
 */
fun chargeAlertsFor(results: List<Pair<NewCharge, Boolean>>): List<CycleAlert> =
    results.filterNot { (_, duplicate) -> duplicate }.map { (charge, _) -> chargeAlert(charge) }
