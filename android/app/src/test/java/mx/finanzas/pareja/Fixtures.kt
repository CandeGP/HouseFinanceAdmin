package mx.finanzas.pareja

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CycleEstimate
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus

fun card(id: String, name: String = "Tarjeta $id", limit: Double = 10_000.0) = Card(
    id = id, householdId = "h1", ownerId = "u1", name = name, bank = "Banco",
    last4 = "1234", creditLimit = limit, cutoffDay = 15, dueDay = 5,
)

fun estimate(cycleTotal: Double, msiRemaining: Double = 0.0, limit: Double = 10_000.0) = CycleEstimate(
    cycleStart = LocalDate(2026, 9, 16), cycleEnd = LocalDate(2026, 10, 15), dueDate = LocalDate(2026, 11, 5),
    cycleContadoTotal = cycleTotal, cycleMsiTotal = 0.0, cycleTotal = cycleTotal,
    msiRemaining = msiRemaining, creditLimit = limit,
    usagePct = if (limit > 0) (cycleTotal + msiRemaining) / limit * 100 else 0.0,
)

fun tx(
    id: String,
    amount: Double,
    cardId: String? = "c1",
    categoryId: String? = null,
    date: LocalDate = LocalDate(2026, 10, 10),
    status: TxStatus = TxStatus.CONFIRMED,
    ownerId: String = "u1",
) = Transaction(
    id = id, householdId = "h1", ownerId = ownerId, cardId = cardId, categoryId = categoryId,
    merchant = "Comercio $id", amount = amount, occurredAt = date,
    paymentMode = PaymentMode.CONTADO, status = status,
)
