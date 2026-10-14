package mx.finanzas.pareja.domain

import java.text.Normalizer
import kotlinx.datetime.LocalDate

/** Datos de cabecera de un estado de cuenta (formato estándar CONDUSEF de los bancos en México). */
data class StatementSummary(
    val bank: String?,
    val cardLast4: String?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val cutoffDate: LocalDate,
    val dueDate: LocalDate?,
    val creditLimit: Double?,
    val payNoInterest: Double?,
    val minimumPayment: Double?,
)

/** Movimiento del periodo. `amount` > 0 es cargo (compra) y < 0 es abono (pago, devolución). */
data class StatementMovement(
    val operationDate: LocalDate,
    val chargeDate: LocalDate?,
    val description: String,
    val amount: Double,
    val cardLast4: String?,
    /** Mensualidad de una compra a meses: ya está representada en la tabla de MSI. */
    val isInstallmentCharge: Boolean,
)

/** Compra a meses sin intereses vigente. `current` es el número de pago que corresponde a este corte. */
data class StatementInstallment(
    val purchaseDate: LocalDate,
    val description: String,
    val originalAmount: Double,
    val pendingBalance: Double,
    val requiredPayment: Double,
    val current: Int,
    val total: Int,
)

data class ParsedStatement(
    val summary: StatementSummary,
    val movements: List<StatementMovement>,
    val installments: List<StatementInstallment>,
)

private val MONTHS = mapOf(
    "ene" to 1, "feb" to 2, "mar" to 3, "abr" to 4, "may" to 5, "jun" to 6,
    "jul" to 7, "ago" to 8, "sep" to 9, "sept" to 9, "set" to 9, "oct" to 10, "nov" to 11, "dic" to 12,
)

private const val DATE_SRC = """(?:\d{4}-\d{2}-\d{2}|\d{1,2}[-\s][A-Za-zÁÉÍÓÚáéíóú]{3,5}\.?[-\s]\d{4})"""
private const val MONEY_SRC = """\$\s*[\d,]+\.\d{2}"""
private const val SIGN_SRC = """[+\-−–—]"""

private val DATE_PARTS = Regex("""(\d{1,2})[-\s]([A-Za-zÁÉÍÓÚáéíóú]+)\.?[-\s](\d{4})""")

private fun stripAccents(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** `2026-08-24`, `18-ago-2026`, `21-sept-2026`, `05 SEP 2026`. */
fun parseStatementDate(text: String): LocalDate? {
    val t = text.trim()
    Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(t)?.let {
        return runCatching { LocalDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
    }
    val m = DATE_PARTS.matchEntire(t) ?: return null
    val month = MONTHS[stripAccents(m.groupValues[2]).lowercase()] ?: return null
    return runCatching { LocalDate(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull()
}

private fun money(s: String): Double? = s.replace("$", "").replace(",", "").trim().toDoubleOrNull()

private fun detectBank(text: String): String? = when {
    text.contains("RappiCard", ignoreCase = true) -> "Rappi"
    text.contains("Plata Card", ignoreCase = true) || text.contains("bancoplata", ignoreCase = true) -> "Plata"
    text.contains("BBVA", ignoreCase = true) -> "BBVA"
    Regex("""Tarjeta de Cr[eé]dito Nu\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Nu"
    else -> null
}

// Nota al pie opcional pegada a la etiqueta ("pago1", "pago:1"). El lookahead evita que un solo
// dígito se coma el inicio de un número mayor ("corte 21-sept": el "2" no es una nota al pie).
private const val FOOTNOTE = """(?:\d(?![\d-]))?"""

private fun findDate(text: String, labelPattern: String): LocalDate? =
    Regex("(?:$labelPattern)\\W*$FOOTNOTE\\W*(?:[A-Za-zÁÉÍÓÚáéíóú]+,\\s*)?($DATE_SRC)", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.let(::parseStatementDate)

private fun findMoney(text: String, labelPattern: String): Double? =
    Regex("(?:$labelPattern)\\W*$FOOTNOTE\\W*($MONEY_SRC)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        .find(text)?.groupValues?.get(1)?.let(::money)

private fun findCardLast4(text: String): String? {
    Regex("""N[uú]mero de tarjeta:?\s*([*Xx\d\s-]*?)(\d{4})\b""", RegexOption.IGNORE_CASE)
        .find(text)?.let { return it.groupValues[2] }
    Regex("""Tarjeta titular:?\s*\[?[Xx*#\s-]{4,}(\d{4})""", RegexOption.IGNORE_CASE)
        .find(text)?.let { return it.groupValues[1] }
    return null
}

private fun parseSummary(text: String): StatementSummary? {
    val cutoff = findDate(text, """Fecha de corte""") ?: return null
    val period = Regex("""Periodo:?\s*($DATE_SRC)\s+al\s+($DATE_SRC)""", RegexOption.IGNORE_CASE).find(text)
    return StatementSummary(
        bank = detectBank(text),
        cardLast4 = findCardLast4(text),
        periodStart = period?.groupValues?.get(1)?.let(::parseStatementDate),
        periodEnd = period?.groupValues?.get(2)?.let(::parseStatementDate),
        cutoffDate = cutoff,
        dueDate = findDate(text, """Fecha l[ií]mite de pago"""),
        creditLimit = findMoney(text, """L[ií]mite de cr[eé]dito"""),
        payNoInterest = findMoney(text, """^Pago para no generar\s+intereses"""),
        // "Pago mínimo + compras y cargos diferidos..." es otra línea: se excluye con el lookahead.
        minimumPayment = findMoney(text, """^Pago m[ií]nimo(?!\s*\+)"""),
    )
}

// <fecha operación> <fecha cargo> <descripción> <+|-> $monto
private val MOVEMENT_LINE = Regex("""^($DATE_SRC)\s+($DATE_SRC)\s+(.+?)\s+($SIGN_SRC)\s*\$\s*([\d,]+\.\d{2})\s*$""")

// <fecha> <descripción> $original $pendiente $requerido <n> de <m> | <n>/<m> <tasa>%
private val INSTALLMENT_LINE = Regex(
    """^($DATE_SRC)\s+(.+?)\s+(\$\s*[\d,]+\.\d{2})\s+(\$\s*[\d,]+\.\d{2})\s+(\$\s*[\d,]+\.\d{2})\s+(\d{1,2})\s*(?:de|/)\s*(\d{1,2})\s+[\d.]+\s*%""",
    RegexOption.IGNORE_CASE,
)

private val CARD_CONTINUATION = Regex("""Tarjeta\s+(?:virtual|digital)[^\n]*?\*{2,4}\s*(\d{4})""", RegexOption.IGNORE_CASE)
private val EMBEDDED_CARD = Regex("""\*{3}\s*(\d{4})""")
private val PLATA_LEADING_CARD = Regex("""^(\d{4})\s+(.*)$""")
private val PLATA_TRAILING_FX = Regex("""\s+$SIGN_SRC\s*[\d.,]+\s+MXN\s+[\d.]+$""")
private val RFC_SUFFIX = Regex("""\s*[;|]?\s*RFC:.*$""", RegexOption.IGNORE_CASE)
private val INSTALLMENT_HINT = Regex("""(?i)(^\d{1,2}\s+DE\s+\d{1,2}\b|\bA\s+MESES\b|\s-\s\d{1,2}/\d{1,2}$|\bMSI\b)""")

private val DIGITAL_CARD_SUFFIX = Regex("""\s*;\s*Tarjeta\s+(?:digital|virtual).*$""", RegexOption.IGNORE_CASE)
private val INSTALLMENT_SUFFIX = Regex("""\s+-\s*\d{1,2}/\d{1,2}$""")

private fun cleanDescription(raw: String): String =
    raw.replace(PLATA_TRAILING_FX, "")
        .replace(RFC_SUFFIX, "")
        .replace(DIGITAL_CARD_SUFFIX, "")
        .replace(INSTALLMENT_SUFFIX, "")
        .replace(Regex("""\s+"""), " ")
        .trim()

/** Parsea el texto completo de un estado de cuenta. Devuelve null si no encuentra fecha de corte. */
fun parseStatement(text: String): ParsedStatement? {
    val summary = parseSummary(text) ?: return null

    val movements = mutableListOf<StatementMovement>()
    val installments = mutableListOf<StatementInstallment>()

    for (rawLine in text.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        INSTALLMENT_LINE.matchEntire(line)?.let { m ->
            val date = parseStatementDate(m.groupValues[1]) ?: return@let
            val current = m.groupValues[6].toInt()
            val total = m.groupValues[7].toInt()
            if (total >= 2 && current in 0..total) {
                installments += StatementInstallment(
                    purchaseDate = date,
                    description = cleanDescription(m.groupValues[2]),
                    originalAmount = money(m.groupValues[3]) ?: return@let,
                    pendingBalance = money(m.groupValues[4]) ?: return@let,
                    requiredPayment = money(m.groupValues[5]) ?: return@let,
                    current = current,
                    total = total,
                )
            }
            return@let
        }
        if (INSTALLMENT_LINE.matches(line)) continue

        val mv = MOVEMENT_LINE.matchEntire(line)
        if (mv != null) {
            val operation = parseStatementDate(mv.groupValues[1])
            val amountAbs = money(mv.groupValues[5])
            if (operation != null && amountAbs != null) {
                var description = mv.groupValues[3]
                var card: String? = null
                PLATA_LEADING_CARD.matchEntire(description)?.let {
                    card = it.groupValues[1]
                    description = it.groupValues[2]
                }
                EMBEDDED_CARD.find(description)?.let { card = card ?: it.groupValues[1] }
                val cleaned = cleanDescription(description)
                val negative = mv.groupValues[4] != "+"
                movements += StatementMovement(
                    operationDate = operation,
                    chargeDate = parseStatementDate(mv.groupValues[2]),
                    description = cleaned,
                    amount = if (negative) -amountAbs else amountAbs,
                    cardLast4 = card,
                    // La pista se busca antes de limpiar: la limpieza quita "- 2/6" y "; Tarjeta digital".
                    isInstallmentCharge = !negative &&
                        INSTALLMENT_HINT.containsMatchIn(description.replace(RFC_SUFFIX, "").trim()),
                )
            }
            continue
        }

        // Línea de continuación con la tarjeta virtual/digital del movimiento anterior.
        CARD_CONTINUATION.find(line)?.let { c ->
            val last = movements.lastOrNull()
            if (last != null && last.cardLast4 == null) {
                movements[movements.lastIndex] = last.copy(cardLast4 = c.groupValues[1])
            }
        }
    }

    return ParsedStatement(summary, movements, installments)
}
