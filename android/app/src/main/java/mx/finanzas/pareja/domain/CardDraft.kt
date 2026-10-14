package mx.finanzas.pareja.domain

enum class CardField { NAME, BANK, LAST4, LIMIT, CUTOFF, DUE }

data class ValidCard(
    val name: String,
    val bank: String,
    val last4: String,
    val creditLimit: Double,
    val cutoffDay: Int,
    val dueDay: Int,
)

sealed interface CardDraftResult {
    data class Valid(val value: ValidCard) : CardDraftResult
    data class Invalid(val errors: Map<CardField, String>) : CardDraftResult
}

private val LAST4 = Regex("""^\d{4}$""")

/** Estado crudo del formulario de tarjeta. */
data class CardDraft(
    val name: String = "",
    val bank: String = "",
    val last4: String = "",
    val limitText: String = "",
    val cutoffText: String = "",
    val dueText: String = "",
) {
    fun validate(): CardDraftResult {
        val errors = linkedMapOf<CardField, String>()

        if (name.isBlank()) errors[CardField.NAME] = "Ponle un nombre a la tarjeta"
        if (bank.isBlank()) errors[CardField.BANK] = "Escribe el banco"
        if (!LAST4.matches(last4.trim())) errors[CardField.LAST4] = "Deben ser 4 dígitos"

        val limit = parseAmount(limitText)
        if (limit == null) errors[CardField.LIMIT] = "Límite de crédito inválido"

        val cutoff = cutoffText.trim().toIntOrNull()
        if (cutoff == null || cutoff !in 1..31) errors[CardField.CUTOFF] = "Día entre 1 y 31"

        val due = dueText.trim().toIntOrNull()
        if (due == null || due !in 1..31) errors[CardField.DUE] = "Día entre 1 y 31"

        if (errors.isNotEmpty()) return CardDraftResult.Invalid(errors)

        return CardDraftResult.Valid(
            ValidCard(
                name = name.trim(),
                bank = bank.trim(),
                last4 = last4.trim(),
                creditLimit = requireNotNull(limit),
                cutoffDay = requireNotNull(cutoff),
                dueDay = requireNotNull(due),
            ),
        )
    }
}
