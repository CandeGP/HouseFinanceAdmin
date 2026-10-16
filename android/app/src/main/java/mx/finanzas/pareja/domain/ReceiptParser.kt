package mx.finanzas.pareja.domain

import java.text.Normalizer
import kotlinx.datetime.LocalDate

/** Lo que se pudo leer de una imagen (ticket, voucher o captura de un pago). Todo es opcional. */
data class ReceiptGuess(
    val amount: Double? = null,
    val merchant: String? = null,
    val cardLast4: String? = null,
    val installments: Int? = null,
    val date: LocalDate? = null,
)

private val MONEY = Regex("""(?:\$|MXN)\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
private val TOTAL_LINE = Regex("""\btotal\b[^\d$]{0,15}\$?\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
private val NOT_THE_AMOUNT = Regex("""subtotal|iva|propina|cambio|recibido|efectivo""", RegexOption.IGNORE_CASE)
private val LABELED_MERCHANT = Regex("""^(?:comercio|establecimiento|negocio|lugar|merchant)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
private val BEFORE_USO = Regex("""^(.+?)\s+us[oó]\s+(?:\$|MXN)""", RegexOption.IGNORE_CASE)
private val AFTER_EN = Regex(
    """\b(?:compra|pago|cargo|consumo)\s+(?:de\s+\S+\s+)?en\s+(.+?)(?=\s+(?:por|con|el|a las|a)\b|[.,;]|$)""",
    RegexOption.IGNORE_CASE,
)
private val CARD_LAST4 = Regex(
    """(?:terminaci[oó]n|terminada\s+en|termina\s+en|ending(?:\s+in)?|tarjeta\D{0,20}?|[•*·]{2,}|x{2,})\s*(\d{4})\b""",
    RegexOption.IGNORE_CASE,
)
private val INSTALLMENTS = Regex("""\b(\d{1,2})\s*(?:meses|mensualidades|msi)\b""", RegexOption.IGNORE_CASE)
private val DATE_NUMERIC = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\b""")
private val DATE_WORDS = Regex("""\b(\d{1,2})\s*(?:de\s+)?([A-Za-zÁÉÍÓÚáéíóú]{3,10})\.?\s*(?:de\s+)?(\d{4})\b""")
private val TRAILING_SYMBOLS = Regex("""[\p{So}\p{Sk}\p{Cs}️‍\s]+$""")

// Renglones de encabezado de ticket que no son el nombre del comercio.
private val GENERIC_LINE = Regex(
    """ticket|factura|rfc|fecha|hora|total|gracias|bienvenid|sucursal|folio|caja|cajero|direcci|tel[eé]fono|""" +
        """compra|pago|cargo|aprobad|comprobante|voucher|autorizaci|operaci|referencia|tarjeta""",
    RegexOption.IGNORE_CASE,
)

private const val MIN_INSTALLMENTS = 2
private const val MAX_INSTALLMENTS = 48
private const val MIN_MERCHANT_LETTERS = 3

private fun toAmount(raw: String): Double? = raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

private fun cleanName(raw: String): String? =
    raw.replace(TRAILING_SYMBOLS, "").replace(Regex("""\s+"""), " ").trim().ifEmpty { null }

private fun findAmount(lines: List<String>): Double? {
    // 1) Renglón "TOTAL ..." (sin subtotal, IVA, propina...). 2) El monto con $ más grande.
    lines.filterNot { NOT_THE_AMOUNT.containsMatchIn(it) }
        .mapNotNull { TOTAL_LINE.find(it)?.groupValues?.get(1)?.let(::toAmount) }
        .maxOrNull()?.let { return it }
    return lines.filterNot { NOT_THE_AMOUNT.containsMatchIn(it) }
        .flatMap { line -> MONEY.findAll(line).mapNotNull { toAmount(it.groupValues[1]) }.toList() }
        .maxOrNull()
}

private fun findMerchant(lines: List<String>): String? {
    lines.firstNotNullOfOrNull { LABELED_MERCHANT.find(it)?.groupValues?.get(1) }?.let(::cleanName)?.let { return it }
    lines.firstNotNullOfOrNull { BEFORE_USO.find(it)?.groupValues?.get(1) }?.let(::cleanName)?.let { return it }
    lines.firstNotNullOfOrNull { AFTER_EN.find(it)?.groupValues?.get(1) }?.let(::cleanName)?.let { return it }
    // Encabezado del ticket: primer renglón que parece un nombre (letras, sin cifras ni palabras de formato).
    return lines.firstNotNullOfOrNull { line ->
        val letters = line.count { it.isLetter() }
        line.takeIf { letters >= MIN_MERCHANT_LETTERS && letters * 2 >= line.length && !GENERIC_LINE.containsMatchIn(it) && !line.any(Char::isDigit) }
    }?.let(::cleanName)
}

private fun findDate(text: String): LocalDate? {
    DATE_NUMERIC.find(text)?.let { m ->
        // Formato mexicano: día/mes/año.
        runCatching { LocalDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
            .getOrNull()?.let { return it }
    }
    for (m in DATE_WORDS.findAll(text)) {
        val month = Normalizer.normalize(m.groupValues[2], Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .lowercase().take(3)
        parseStatementDate("${m.groupValues[1]}-$month-${m.groupValues[3]}")?.let { return it }
    }
    return null
}

/**
 * Lee el texto de una imagen de pago y propone los datos del gasto. Es una ayuda para autorrellenar:
 * lo que no se reconoce queda en null y la persona lo completa o corrige antes de guardar.
 */
fun parseReceipt(text: String): ReceiptGuess {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val installments = INSTALLMENTS.find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?.takeIf { it in MIN_INSTALLMENTS..MAX_INSTALLMENTS }
    return ReceiptGuess(
        amount = findAmount(lines),
        merchant = findMerchant(lines),
        cardLast4 = CARD_LAST4.find(text)?.groupValues?.get(1),
        installments = installments,
        date = findDate(text),
    )
}
