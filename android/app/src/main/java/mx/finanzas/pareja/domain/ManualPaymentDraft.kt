package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import mx.finanzas.pareja.data.PaymentMethod

enum class PaymentField { CONCEPT, AMOUNT, TIME }

/** Pago ya validado; `paidAt` es el instante real (fecha y hora en la zona de la persona). */
data class ValidManualPayment(
    val method: PaymentMethod,
    val amount: Double,
    val paidAt: Instant,
    val concept: String,
    val counterparty: String?,
    val reference: String?,
    val categoryId: String?,
    val notes: String?,
)

sealed interface PaymentDraftResult {
    data class Valid(val value: ValidManualPayment) : PaymentDraftResult
    data class Invalid(val errors: Map<PaymentField, String>) : PaymentDraftResult
}

private val TIME_PATTERN = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")

/** Estado crudo del formulario de pago en efectivo / transferencia. */
data class ManualPaymentDraft(
    val method: PaymentMethod = PaymentMethod.CASH,
    val amountText: String = "",
    val date: LocalDate,
    val timeText: String = "",
    val concept: String = "",
    val counterparty: String = "",
    val reference: String = "",
    val categoryId: String? = null,
    val notes: String = "",
) {
    fun validate(zone: TimeZone): PaymentDraftResult {
        val errors = linkedMapOf<PaymentField, String>()

        val cleanConcept = concept.trim()
        if (cleanConcept.isEmpty()) errors[PaymentField.CONCEPT] = "Escribe el concepto del pago"

        val amount = parseAmount(amountText)
        if (amount == null) errors[PaymentField.AMOUNT] = "Monto inválido"

        val match = TIME_PATTERN.matchEntire(timeText.trim())
        if (match == null) errors[PaymentField.TIME] = "Hora inválida (usa HH:mm, ej. 14:30)"

        if (errors.isNotEmpty() || match == null) return PaymentDraftResult.Invalid(errors)

        val time = LocalTime(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        return PaymentDraftResult.Valid(
            ValidManualPayment(
                method = method,
                amount = requireNotNull(amount),
                paidAt = LocalDateTime(date, time).toInstant(zone),
                concept = cleanConcept,
                counterparty = counterparty.trim().ifEmpty { null },
                reference = reference.trim().ifEmpty { null },
                categoryId = categoryId,
                notes = notes.trim().ifEmpty { null },
            ),
        )
    }
}

/** `HH:mm` del instante en la zona dada (para precargar el formulario al editar). */
fun timeText(instant: Instant, zone: TimeZone): String {
    val t = instant.toLocalDateTime(zone)
    return "%02d:%02d".format(t.hour, t.minute)
}

fun localDate(instant: Instant, zone: TimeZone): LocalDate = instant.toLocalDateTime(zone).date
