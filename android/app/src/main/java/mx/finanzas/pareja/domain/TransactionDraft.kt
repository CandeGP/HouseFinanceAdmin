package mx.finanzas.pareja.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.PaymentMode

enum class DraftField { MERCHANT, AMOUNT, CARD, INSTALLMENTS, INSTALLMENT_NUMBER }

/** Gasto ya validado, listo para guardarse. */
data class ValidDraft(
    val merchant: String,
    val amount: Double,
    val date: LocalDate,
    val cardId: String,
    val categoryId: String?,
    val mode: PaymentMode,
    val installments: Int?,
    val installmentNumber: Int?,
    val notes: String?,
)

sealed interface DraftResult {
    data class Valid(val value: ValidDraft) : DraftResult
    data class Invalid(val errors: Map<DraftField, String>) : DraftResult
}

private val AMOUNT_PATTERN = Regex("""^\d+(\.\d+)?$""")
private val MAX_AMOUNT = BigDecimal("9999999999.99") // numeric(12,2)

/** Acepta "1234.5", "$1,234.50", " 99 ". Devuelve null si no es un monto positivo válido. */
fun parseAmount(text: String): Double? {
    val cleaned = text.replace("$", "").replace(",", "").filterNot { it.isWhitespace() }
    if (!AMOUNT_PATTERN.matches(cleaned)) return null
    val value = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
    if (value.signum() <= 0 || value > MAX_AMOUNT) return null
    return value.toDouble()
}

/** Estado crudo del formulario de gasto (todo texto), tal como lo teclea la persona. */
data class TransactionDraft(
    val merchant: String,
    val amountText: String,
    val date: LocalDate,
    val cardId: String?,
    val categoryId: String?,
    val mode: PaymentMode,
    val installmentsText: String,
    val installmentNumberText: String,
    val notes: String,
) {
    fun validate(): DraftResult {
        val errors = linkedMapOf<DraftField, String>()

        val cleanMerchant = merchant.trim()
        if (cleanMerchant.isEmpty()) errors[DraftField.MERCHANT] = "Escribe el comercio"

        val amount = parseAmount(amountText)
        if (amount == null) errors[DraftField.AMOUNT] = "Monto inválido"

        if (cardId == null) errors[DraftField.CARD] = "Selecciona una tarjeta"

        var installments: Int? = null
        var installmentNumber: Int? = null
        if (mode == PaymentMode.MSI) {
            installments = installmentsText.trim().toIntOrNull()
            if (installments == null || installments < 2) {
                errors[DraftField.INSTALLMENTS] = "Indica 2 o más mensualidades"
            } else {
                val rawNumber = installmentNumberText.trim()
                installmentNumber = if (rawNumber.isEmpty()) 1 else rawNumber.toIntOrNull()
                if (installmentNumber == null || installmentNumber !in 1..installments) {
                    errors[DraftField.INSTALLMENT_NUMBER] = "Debe estar entre 1 y $installments"
                }
            }
        }

        if (errors.isNotEmpty()) return DraftResult.Invalid(errors)

        return DraftResult.Valid(
            ValidDraft(
                merchant = cleanMerchant,
                amount = requireNotNull(amount),
                date = date,
                cardId = requireNotNull(cardId),
                categoryId = categoryId,
                mode = mode,
                installments = installments,
                installmentNumber = installmentNumber,
                notes = notes.trim().ifEmpty { null },
            ),
        )
    }
}
