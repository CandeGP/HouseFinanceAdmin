package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Notificación del sistema reducida a lo que importa para detectar un cobro. */
data class RawNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Instant,
)

/** Cobro detectado en una notificación. `cardLast4` es null si la fuente no lo informa. */
@Serializable
data class ParsedCharge(
    val merchant: String,
    val amount: Double,
    val cardLast4: String?,
    val sourcePackage: String,
    val capturedAt: Instant,
    val raw: String,
)

object ChargeSources {
    const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    const val PLATA = "dif.tech.plata"
    const val GMAIL = "com.google.android.gm"

    /** Paquetes de bancos/fintech conocidos (los demás se detectan por el nombre de la app). */
    private val KNOWN_PACKAGES: Set<String> = setOf(
        GOOGLE_WALLET, PLATA,
        "com.bancomer.mbanking", // BBVA
        "mx.bancosantander.supermovil", // Santander
        "com.nu.production", // Nu
        "com.grability.rappi", // RappiCard
    )

    private val KNOWN_PREFIXES = listOf("com.bancomer.", "mx.com.bancomer.", "mx.bancosantander.")

    // Nombre visible de la app: bancos y fintech de México más comunes.
    private val BANK_LABEL = Regex(
        """banco|bank|bbva|banorte|santander|hsbc|banamex|citi|scotia|inbursa|azteca|afirme|bajio|""" +
            """\bnu\b|nubank|rappi|\bplata\b|mercado\s*pago|\bklar\b|\bstori\b|revolut|openbank|hey\s*banco|""" +
            """\bspin\b|ual[aá]|fondeadora|amex|american\s*express|billetera|wallet|google\s*pay""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * ¿Se leen las notificaciones de este paquete? El nombre visible de la app permite cubrir
     * bancos que no están en la lista sin inspeccionar apps ajenas (WhatsApp, redes, etc.).
     * Gmail solo cuenta si el remitente (el título de la notificación) parece un banco: no se leen los demás correos.
     */
    fun isWatched(packageName: String, appLabel: String? = null, title: String? = null): Boolean =
        if (packageName == GMAIL) title != null && BANK_LABEL.containsMatchIn(title) else isWatchedApp(packageName, appLabel)

    private fun isWatchedApp(packageName: String, appLabel: String?): Boolean =
        packageName in KNOWN_PACKAGES ||
            KNOWN_PREFIXES.any { packageName.startsWith(it) } ||
            (appLabel != null && BANK_LABEL.containsMatchIn(appLabel))
}

// "$127.00 con Plata card ••5841"  ->  monto, nombre de tarjeta, últimos 4
private val WALLET_TEXT = Regex("""^\$\s*([\d.,]+)\s+con\s+.+?\s*[•·●*.]{1,4}\s*(\d{4})\s*$""", RegexOption.IGNORE_CASE)

// "$127" | "$406.85"
private val PLATA_TEXT = Regex("""^\$\s*([\d,]+(?:\.\d{1,2})?)$""")

// Símbolos/emojis y espacios al final del título: "Caffenio 🛒" -> "Caffenio"
private val TRAILING_SYMBOLS = Regex("""[\p{So}\p{Sk}\p{Cs}️‍\s]+$""")

private fun cleanMerchant(raw: String?): String? =
    raw?.replace(TRAILING_SYMBOLS, "")?.replace(Regex("""\s+"""), " ")?.trim()?.ifEmpty { null }

/** Devuelve el cobro si la notificación es de un cobro en MXN reconocible; null en cualquier otro caso. */
fun parseCharge(n: RawNotification): ParsedCharge? {
    val strict = when (n.packageName) {
        ChargeSources.GOOGLE_WALLET -> parseWallet(n)
        ChargeSources.PLATA -> parsePlata(n)
        else -> null
    }
    return strict ?: parseBankText(n)
}

// Un cobro lleva uno de estos verbos/sustantivos; un abono, depósito o promoción no.
private val SPEND_WORDS = Regex(
    """compra|cargo|pagaste|pago\s+(?:con|de|en)|consumo|retiro|transacci[oó]n|aprobad|purchase|spent""",
    RegexOption.IGNORE_CASE,
)
private val INCOME_WORDS = Regex(
    """abono|dep[oó]sito|depositaron|recibiste|te\s+pagaron|reembolso|devoluci[oó]n|cashback|regalo|gana|promoci[oó]n|deposit|refund""",
    RegexOption.IGNORE_CASE,
)
private val FOREIGN_CURRENCY = Regex("""\b(?:USD|EUR|CAD|GBP|US\$)\b""", RegexOption.IGNORE_CASE)
private val BANK_AMOUNT = Regex("""(?:\$|MXN)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
private val MERCHANT_AFTER_EN = Regex("""\ben\s+(.+?)(?=\s+con\b|\s+terminad[ao]\b|\s+terminaci[oó]n\b|\s+el\b|\s+a\s+las\b|\s+de\s+tu\b|[.,;]|$)""", RegexOption.IGNORE_CASE)
// "ENERGETICOS ALTIPLANO usó $219.08 para pre-autorizar…" (aviso de Rappi): el comercio va antes de "usó".
private val MERCHANT_BEFORE_USO = Regex("""^(.+?)\s+us[oó]\s+(?:\$|MXN)""", RegexOption.IGNORE_CASE)
private val LAST4 = Regex(
    """(?:terminaci[oó]n|terminada\s+en|termina\s+en|ending(?:\s+in)?|[*•·]{2,}|x{2,})\s*(\d{4})\b""",
    RegexOption.IGNORE_CASE,
)

// Títulos que describen el tipo de aviso, no al comercio.
private val GENERIC_TITLE = Regex(
    """compra|cargo|pago|aprobad|alerta|notificaci|movimiento|transacci|tarjeta|consumo|bbva|santander|banorte|hsbc|\bnu\b|rappicard|billetera""",
    RegexOption.IGNORE_CASE,
)

/**
 * Parser genérico para apps de bancos y Billetera de Google cuando no aplica un formato estricto.
 * Es deliberadamente conservador: exige monto en MXN y lenguaje de cobro, y descarta abonos y
 * promociones. Lo que pase llega a la bandeja para revisión, nunca se registra solo.
 */
private fun parseBankText(n: RawNotification): ParsedCharge? {
    val text = n.text?.trim()?.ifEmpty { null } ?: return null
    val whole = "${n.title.orEmpty()} $text"
    if (!SPEND_WORDS.containsMatchIn(whole)) return null
    if (INCOME_WORDS.containsMatchIn(whole)) return null
    if (FOREIGN_CURRENCY.containsMatchIn(whole)) return null

    val amount = BANK_AMOUNT.find(text)?.let { parseAmount(it.groupValues[1]) } ?: return null
    val title = cleanMerchant(n.title)
    val merchant = MERCHANT_AFTER_EN.find(text)?.groupValues?.get(1)?.let(::cleanMerchant)
        ?: MERCHANT_BEFORE_USO.find(text)?.groupValues?.get(1)?.let(::cleanMerchant)
        ?: title?.takeUnless { GENERIC_TITLE.containsMatchIn(it) }
        ?: "Sin comercio"

    return ParsedCharge(
        merchant = merchant,
        amount = amount,
        cardLast4 = LAST4.find(text)?.groupValues?.get(1),
        sourcePackage = n.packageName,
        capturedAt = n.postTime,
        raw = "${n.title.orEmpty()} | $text",
    )
}

private fun parseWallet(n: RawNotification): ParsedCharge? {
    val merchant = cleanMerchant(n.title) ?: return null
    val text = n.text?.trim() ?: return null
    val match = WALLET_TEXT.matchEntire(text) ?: return null
    val amount = parseAmount(match.groupValues[1]) ?: return null
    return ParsedCharge(
        merchant = merchant, amount = amount, cardLast4 = match.groupValues[2],
        sourcePackage = n.packageName, capturedAt = n.postTime, raw = "$merchant | $text",
    )
}

private fun parsePlata(n: RawNotification): ParsedCharge? {
    val merchant = cleanMerchant(n.title) ?: return null
    val text = n.text?.trim() ?: return null
    val match = PLATA_TEXT.matchEntire(text) ?: return null
    val amount = parseAmount(match.groupValues[1]) ?: return null
    return ParsedCharge(
        merchant = merchant, amount = amount, cardLast4 = null,
        sourcePackage = n.packageName, capturedAt = n.postTime, raw = "$merchant | $text",
    )
}
