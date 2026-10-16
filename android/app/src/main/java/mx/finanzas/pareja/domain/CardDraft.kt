package mx.finanzas.pareja.domain

enum class CardField { NAME, BANK, LAST4, LIMIT, CUTOFF, DUE, BUDGET, COLOR }

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

/** Edición completa de una tarjeta existente: lo de la alta más presupuesto mensual y color. */
data class ValidCardEdit(val card: ValidCard, val monthlyBudget: Double?, val color: String?)

sealed interface CardEditResult {
    data class Valid(val value: ValidCardEdit) : CardEditResult
    data class Invalid(val errors: Map<CardField, String>) : CardEditResult
}

private val HEX_COLOR = Regex("""^#[0-9A-Fa-f]{6}$""")

/**
 * Formulario de edición. `budgetText` vacío quita el presupuesto; `color` nulo o vacío deja el color
 * automático (el de su banco).
 */
data class CardEditDraft(
    val card: CardDraft,
    val budgetText: String = "",
    val color: String? = null,
) {
    fun validate(): CardEditResult {
        val errors = linkedMapOf<CardField, String>()
        val base = card.validate()
        if (base is CardDraftResult.Invalid) errors.putAll(base.errors)

        val budgetInput = budgetText.trim()
        var budget: Double? = null
        if (budgetInput.isNotEmpty()) {
            budget = parseAmount(budgetInput)
            if (budget == null) errors[CardField.BUDGET] = "Presupuesto inválido (déjalo vacío para quitarlo)"
        }

        val colorInput = color?.trim()?.takeIf { it.isNotEmpty() }
        if (colorInput != null && !HEX_COLOR.matches(colorInput)) errors[CardField.COLOR] = "Color inválido"

        if (errors.isNotEmpty() || base !is CardDraftResult.Valid) return CardEditResult.Invalid(errors)
        return CardEditResult.Valid(ValidCardEdit(base.value, budget, colorInput?.uppercase()))
    }
}

/** Colores sugeridos para personalizar una tarjeta (la tarjeta sin color usa el de su banco). */
val CARD_COLOR_CHOICES: List<String> = listOf(
    "#375390", "#459AF8", "#0F9D8A", "#2E7D32", "#F59E0B",
    "#EA580C", "#DC2626", "#DB2777", "#7C3AED", "#475569",
)

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
