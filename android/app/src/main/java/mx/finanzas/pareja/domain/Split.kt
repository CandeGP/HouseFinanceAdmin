package mx.finanzas.pareja.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class SplitField { CONCEPT, TOTAL, SHARE }

/**
 * Formulario de «Dividir cobro»: lo que pagaste tú y cuánto le toca a tu pareja. Con [partnerShareText] vacío
 * se divide a la mitad. El resultado se guarda como un préstamo a la pareja, así que "Por cobrar" es Préstamos.
 */
data class SplitDraft(
    val concept: String = "",
    val totalText: String = "",
    val partnerShareText: String = "",
) {
    fun validate(): SplitResult {
        val errors = linkedMapOf<SplitField, String>()
        if (concept.isBlank()) errors[SplitField.CONCEPT] = "Escribe qué se pagó"
        val total = parseAmount(totalText)
        if (total == null) errors[SplitField.TOTAL] = "Monto inválido"

        var share: Double? = null
        if (total != null) {
            if (partnerShareText.isBlank()) {
                share = BigDecimal(total).divide(BigDecimal(2), 2, RoundingMode.HALF_UP).toDouble()
            } else {
                share = parseAmount(partnerShareText)
                if (share == null) errors[SplitField.SHARE] = "Monto inválido"
                else if (share > total) errors[SplitField.SHARE] = "No puede ser mayor al total"
            }
        }
        if (errors.isNotEmpty() || total == null || share == null) return SplitResult.Invalid(errors)
        return SplitResult.Valid(concept.trim(), total, share)
    }
}

sealed interface SplitResult {
    data class Valid(val concept: String, val total: Double, val partnerShare: Double) : SplitResult
    data class Invalid(val errors: Map<SplitField, String>) : SplitResult
}
