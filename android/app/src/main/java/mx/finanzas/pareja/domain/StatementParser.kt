package mx.finanzas.pareja.domain

import java.text.Normalizer
import kotlin.math.abs
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

// El mes admite "0" porque el OCR lee la O de "Oct" como cero ("04-0ct-2026").
private const val DATE_SRC = """(?:\d{4}-\d{2}-\d{2}|\d{1,2}[-\s][A-Za-zÁÉÍÓÚáéíóú0]{3,5}\.?[-\s]\d{4})"""
private const val MONEY_SRC = """\$\s*[\d,]+\.\d{2}"""
private const val SIGN_SRC = """[+\-−–—]"""

private val DATE_PARTS = Regex("""(\d{1,2})[-\s]([A-Za-zÁÉÍÓÚáéíóú0]+)\.?[-\s](\d{4})""")

private fun stripAccents(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

// Confusiones típicas del OCR en el mes: "0ct" y "Qct" por "Oct". Ningún mes en español lleva q.
private fun normalizeMonth(token: String) =
    stripAccents(token).lowercase().replace('0', 'o').replace('q', 'o')

/** `2026-08-24`, `18-ago-2026`, `21-sept-2026`, `05 SEP 2026`, `04-0ct-2026` (OCR). */
fun parseStatementDate(text: String): LocalDate? {
    val t = text.trim()
    Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(t)?.let {
        return runCatching { LocalDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
    }
    val m = DATE_PARTS.matchEntire(t) ?: return null
    val month = MONTHS[normalizeMonth(m.groupValues[2])] ?: return null
    return runCatching { LocalDate(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull()
}

private fun money(s: String): Double? = s.replace("$", "").replace(",", "").trim().toDoubleOrNull()

private fun detectBank(text: String): String? = when {
    text.contains("RappiCard", ignoreCase = true) -> "Rappi"
    text.contains("Plata Card", ignoreCase = true) || text.contains("bancoplata", ignoreCase = true) -> "Plata"
    text.contains("BBVA", ignoreCase = true) -> "BBVA"
    Regex("""Tarjeta de Cr[eé]dito Nu\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Nu"
    text.contains("Santander", ignoreCase = true) -> "Santander"
    else -> null
}

// Nota al pie opcional pegada a la etiqueta ("pago1", "pago:1"). El lookahead evita que un solo
// dígito se coma el inicio de un número mayor ("corte 21-sept": el "2" no es una nota al pie).
private const val FOOTNOTE = """(?:\d(?![\d-]))?"""

// "l" de "límite" que el OCR suele leer como I, | o 1.
private const val L_LIKE = """[lI|1]"""

private fun findDate(text: String, labelPattern: String): LocalDate? =
    Regex("(?:$labelPattern)\\W*$FOOTNOTE\\W*(?:[A-Za-zÁÉÍÓÚáéíóú]+,\\s*)?($DATE_SRC)", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.let(::parseStatementDate)

private fun findMoney(text: String, labelPattern: String): Double? =
    Regex("(?:$labelPattern)\\W*$FOOTNOTE\\W*($MONEY_SRC)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        .find(text)?.groupValues?.get(1)?.let(::money)

private fun findCardLast4(text: String): String? {
    // Voraz y sin cruzar renglón: con "4941 3430 0234 5716" interesan los últimos 4, no los primeros.
    Regex("""N[uú]mero de tarjeta:?[ \t]*([*Xx\d \t-]*)(\d{4})\b""", RegexOption.IGNORE_CASE)
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
        dueDate = findDate(text, """Fecha $L_LIKE[ií]mite de pago"""),
        creditLimit = findMoney(text, """$L_LIKE[ií]mite de cr[eé]dito"""),
        // Sin ancla de línea: en un escaneo la etiqueta puede venir pegada a otra columna.
        payNoInterest = findMoney(text, """Pago para no generar\s+intereses"""),
        // "Pago mínimo + compras y cargos diferidos..." es otra línea: se excluye con el lookahead.
        minimumPayment = findMoney(text, """^Pago m[ií]nimo(?!\s*\+)"""),
    )
}

// <fecha operación> <fecha cargo> <descripción> [<+|->] $monto. Algunos bancos no ponen signo en cargos.
private val MOVEMENT_LINE = Regex("""^($DATE_SRC)\s+($DATE_SRC)\s+(.+?)\s+(?:($SIGN_SRC)\s*)?\$\s*([\d,]+\.\d{2})\s*$""")

// <fecha> <descripción> $original $pendiente $requerido <n> de <m> | <n>/<m> <tasa>%
private val INSTALLMENT_LINE = Regex(
    """^($DATE_SRC)\s+(.+?)\s+(\$\s*[\d,]+\.\d{2})\s+(\$\s*[\d,]+\.\d{2})\s+(\$\s*[\d,]+\.\d{2})\s+(\d{1,2})\s*(?:de|/)\s*(\d{1,2})\s+[\d.]+\s*%""",
    RegexOption.IGNORE_CASE,
)

// Compras a meses CON intereses (BBVA): <fecha> <desc> $original $pendiente $interés $IVA $requerido <n> de <m> <tasa>%
private val INTEREST_INSTALLMENT_LINE = Regex(
    """^($DATE_SRC)\s+(.+?)\s+(\$\s*[\d,]+\.\d{2})\s+(\$\s*[\d,]+\.\d{2})\s+\$\s*[\d,]+\.\d{2}\s+\$\s*[\d,]+\.\d{2}\s+(\$\s*[\d,]+\.\d{2})\s+(\d{1,2})\s*(?:de|/)\s*(\d{1,2})\s+[\d.]+\s*%""",
    RegexOption.IGNORE_CASE,
)

// Rappi parte la fecha en dos renglones: "2026-09- AMAZON A MESES ..." y debajo "03". Se vuelve a unir.
private val SPLIT_ISO_DATE = Regex("""^(\d{4}-\d{2})-[ \t]+(.*)\r?\n(\d{2})[ \t]*$""", RegexOption.MULTILINE)

private val CARD_CONTINUATION = Regex("""Tarjeta\s+(?:virtual|digital)[^\n]*?\*{2,4}\s*(\d{4})""", RegexOption.IGNORE_CASE)
private val EMBEDDED_CARD = Regex("""\*{3}\s*(\d{4})""")
private val PLATA_LEADING_CARD = Regex("""^(\d{4})\s+(.*)$""")
private val PLATA_TRAILING_FX = Regex("""\s+$SIGN_SRC\s*[\d.,]+\s+MXN\s+[\d.]+$""")
private val RFC_SUFFIX = Regex("""\s*[;|]?\s*RFC:.*$""", RegexOption.IGNORE_CASE)
private val INSTALLMENT_HINT = Regex("""(?i)(^\d{1,2}\s+DE\s+\d{1,2}\b|\bA\s+(?:\d{1,2}\s+)?MESES\b|\s-\s\d{1,2}/\d{1,2}$|\bMSI\b)""")

private val DIGITAL_CARD_SUFFIX = Regex("""\s*;\s*Tarjeta\s+(?:digital|virtual).*$""", RegexOption.IGNORE_CASE)
private val INSTALLMENT_SUFFIX = Regex("""\s+-\s*\d{1,2}/\d{1,2}$""")

// Primera mitad de una descripción partida en dos renglones: solo mayúsculas y espacios.
private val WRAPPED_DESCRIPTION_HEAD = Regex("""^[A-ZÁÉÍÓÚÑ ]{4,40}$""")

private val NEGATIVE_SIGNS = setOf("-", "−", "–", "—")
private const val AMOUNT_EPSILON = 0.005
private const val MIN_SHARED_WORD = 4

private fun cleanDescription(raw: String): String =
    raw.replace(PLATA_TRAILING_FX, "")
        .replace(RFC_SUFFIX, "")
        .replace(DIGITAL_CARD_SUFFIX, "")
        .replace(INSTALLMENT_SUFFIX, "")
        .replace(Regex("""^[|\s]+"""), "")
        .replace(Regex("""^1VA\b"""), "IVA") // OCR: la I de IVA leída como 1
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun words(description: String): Set<String> =
    stripAccents(description).lowercase().split(Regex("""[^a-z0-9]+"""))
        .filter { it.length >= MIN_SHARED_WORD && !it.all(Char::isDigit) }.toSet()

/**
 * La mensualidad de una compra a meses aparece también como cargo del periodo. Si su monto
 * coincide con el pago requerido de una fila de la tabla de MSI y comparten una palabra del
 * comercio, ya está representada ahí y no debe contarse como gasto nuevo.
 */
private fun markInstallmentCharges(
    movements: List<StatementMovement>,
    installments: List<StatementInstallment>,
): List<StatementMovement> = movements.map { movement ->
    val matches = !movement.isInstallmentCharge && movement.amount > 0 && installments.any { i ->
        // BBVA además lista la compra completa ("TOMMY A 03 MESES S/I", monto original): es la misma compra.
        (abs(i.requiredPayment - movement.amount) < AMOUNT_EPSILON ||
            abs(i.originalAmount - movement.amount) < AMOUNT_EPSILON) &&
            words(i.description).intersect(words(movement.description)).isNotEmpty()
    }
    if (matches) movement.copy(isInstallmentCharge = true) else movement
}

/** Parsea el texto completo de un estado de cuenta. Devuelve null si no encuentra fecha de corte. */
fun parseStatement(rawText: String): ParsedStatement? {
    val text = SPLIT_ISO_DATE.replace(rawText, "$1-$3 $2")
    val summary = parseSummary(text) ?: return null

    val movements = mutableListOf<StatementMovement>()
    val installments = mutableListOf<StatementInstallment>()
    var previousLine = ""

    for (rawLine in text.lines()) {
        // El OCR deja barras verticales (bordes de tabla) pegadas al inicio o al final del renglón.
        val line = rawLine.trim().trim('|').trim()
        if (line.isEmpty()) continue
        val head = previousLine
        previousLine = line

        // Con intereses va primero: su renglón tiene 5 montos y el patrón de 3 se comería los dos primeros en la descripción.
        (INTEREST_INSTALLMENT_LINE.matchEntire(line) ?: INSTALLMENT_LINE.matchEntire(line))?.let { m ->
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
        if (INTEREST_INSTALLMENT_LINE.matches(line) || INSTALLMENT_LINE.matches(line)) continue

        val mv = MOVEMENT_LINE.matchEntire(line)
        if (mv != null) {
            val operation = parseStatementDate(mv.groupValues[1])
            val amountAbs = money(mv.groupValues[5])
            if (operation != null && amountAbs != null) {
                var description = mv.groupValues[3]
                if (WRAPPED_DESCRIPTION_HEAD.matches(head)) description = "$head $description"
                var card: String? = null
                PLATA_LEADING_CARD.matchEntire(description)?.let {
                    card = it.groupValues[1]
                    description = it.groupValues[2]
                }
                EMBEDDED_CARD.find(description)?.let { card = card ?: it.groupValues[1] }
                val cleaned = cleanDescription(description)
                val negative = mv.groupValues[4] in NEGATIVE_SIGNS
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

    return ParsedStatement(summary, markInstallmentCharges(movements, installments), installments)
}
