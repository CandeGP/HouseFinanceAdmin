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

    /** Paquetes cuyas notificaciones se leen; el resto se ignora sin inspeccionarse. */
    val WATCHED: Set<String> = setOf(GOOGLE_WALLET, PLATA)
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
fun parseCharge(n: RawNotification): ParsedCharge? = when (n.packageName) {
    ChargeSources.GOOGLE_WALLET -> parseWallet(n)
    ChargeSources.PLATA -> parsePlata(n)
    else -> null
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
