package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import mx.finanzas.pareja.data.Loan
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.LoanRepayment
import mx.finanzas.pareja.data.PaymentMethod

// Los montos se comparan en centavos para no arrastrar errores de punto flotante.
private fun cents(value: Double): Long = Math.round(value * 100)
private fun fromCents(value: Long): Double = value / 100.0

enum class LoanState { OPEN, SETTLED, FORGIVEN }

/** Un préstamo con sus abonos y lo que falta por cobrar o pagar. */
data class LoanView(val loan: Loan, val repayments: List<LoanRepayment>) {
    private val repaidCents: Long = repayments.sumOf { cents(it.amount) }
    private val remainingCents: Long = maxOf(0L, cents(loan.amount) - repaidCents)

    val repaid: Double = fromCents(repaidCents)
    val remaining: Double = fromCents(remainingCents)
    val state: LoanState = when {
        loan.forgivenAt != null -> LoanState.FORGIVEN
        remainingCents == 0L -> LoanState.SETTLED
        else -> LoanState.OPEN
    }
}

/** Une cada préstamo con sus abonos; conserva el orden recibido (más recientes primero). */
fun buildLoanViews(loans: List<Loan>, repayments: List<LoanRepayment>): List<LoanView> {
    val byLoan = repayments.groupBy { it.loanId }
    return loans.map { LoanView(it, byLoan[it.id].orEmpty()) }
}

/** Saldo con una persona: positivo = te debe, negativo = le debes. Solo cuentan los préstamos abiertos. */
data class PersonBalance(val person: String, val net: Double, val loans: List<LoanView>)

fun personBalances(views: List<LoanView>): List<PersonBalance> =
    views.groupBy { it.loan.person.trim().lowercase() }
        .map { (_, group) ->
            val netCents = group.filter { it.state == LoanState.OPEN }.sumOf {
                val remaining = cents(it.remaining)
                if (it.loan.direction == LoanDirection.LENT) remaining else -remaining
            }
            // La escritura del nombre más reciente es la que se muestra.
            PersonBalance(group.first().loan.person.trim(), fromCents(netCents), group)
        }
        .sortedWith(compareByDescending<PersonBalance> { Math.abs(cents(it.net)) }.thenBy { it.person.lowercase() })

fun PersonBalance.label(): String = when {
    cents(net) > 0 -> "$person te debe ${formatMxn(net)}"
    cents(net) < 0 -> "Le debes ${formatMxn(-net)} a $person"
    else -> "Estás a mano con $person"
}

data class LoanTotals(val owedToYou: Double, val youOwe: Double)

fun loanTotals(views: List<LoanView>): LoanTotals {
    val open = views.filter { it.state == LoanState.OPEN }
    fun sum(direction: LoanDirection) =
        fromCents(open.filter { it.loan.direction == direction }.sumOf { cents(it.remaining) })
    return LoanTotals(owedToYou = sum(LoanDirection.LENT), youOwe = sum(LoanDirection.BORROWED))
}

/** "hoy", "ayer" o "hace N días", para saber cuánto lleva abierto un préstamo. */
fun ageLabel(since: LocalDate, today: LocalDate): String = when (val days = since.daysUntil(today)) {
    0 -> "hoy"
    1 -> "ayer"
    else -> "hace $days días"
}

enum class LoanField { PERSON, AMOUNT, DATE }

/** Formulario de "Nuevo préstamo". [date] null significa hoy; no puede ser futura. */
data class LoanDraft(
    val person: String = "",
    val amountText: String = "",
    val direction: LoanDirection = LoanDirection.LENT,
    val method: PaymentMethod = PaymentMethod.TRANSFER,
    val note: String = "",
    val date: LocalDate? = null,
) {
    fun validate(today: LocalDate): LoanResult {
        val errors = linkedMapOf<LoanField, String>()
        val cleanPerson = person.trim()
        if (cleanPerson.isEmpty()) errors[LoanField.PERSON] = "Escribe con quién"
        val amount = parseAmount(amountText)
        if (amount == null) errors[LoanField.AMOUNT] = "Monto inválido"
        if (date != null && date > today) errors[LoanField.DATE] = "La fecha no puede ser futura"
        if (errors.isNotEmpty() || amount == null) return LoanResult.Invalid(errors)
        return LoanResult.Valid(cleanPerson, amount, direction, method, note.trim().ifEmpty { null }, date ?: today)
    }
}

sealed interface LoanResult {
    data class Valid(
        val person: String,
        val amount: Double,
        val direction: LoanDirection,
        val method: PaymentMethod,
        val note: String?,
        val occurredOn: LocalDate,
    ) : LoanResult

    data class Invalid(val errors: Map<LoanField, String>) : LoanResult
}

/** Formulario de "Abonar" a un préstamo. [date] null significa hoy; no puede ser futura. */
data class LoanRepaymentDraft(
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.TRANSFER,
    val note: String = "",
    val date: LocalDate? = null,
) {
    /**
     * [remaining] es lo que falta del préstamo: un abono no puede pasarse de ahí. Al editar un abono,
     * [remaining] debe incluir lo que ese mismo abono ya cubría.
     */
    fun validate(remaining: Double, today: LocalDate): LoanRepaymentResult {
        val amount = parseAmount(amountText) ?: return LoanRepaymentResult.Invalid("Monto inválido")
        if (cents(amount) > cents(remaining)) {
            return LoanRepaymentResult.Invalid("Es más de lo que falta (${formatMxn(remaining)})")
        }
        if (date != null && date > today) return LoanRepaymentResult.Invalid("La fecha no puede ser futura")
        return LoanRepaymentResult.Valid(amount, method, note.trim().ifEmpty { null }, date ?: today)
    }
}

sealed interface LoanRepaymentResult {
    data class Valid(
        val amount: Double,
        val method: PaymentMethod,
        val note: String?,
        val paidOn: LocalDate,
    ) : LoanRepaymentResult

    data class Invalid(val message: String) : LoanRepaymentResult
}
