package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Notificación del sistema reducida a lo que importa para detectar un cobro. */
data class RawNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Instant,
    /** Nombre visible de la app emisora; ayuda a saber de qué banco viene el aviso. */
    val appLabel: String? = null,
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
    /** Banco del aviso (llave de [ChargeSources.bankOf]); sirve para ligarlo a tu tarjeta de ese banco. */
    val bank: String? = null,
)

object ChargeSources {
    /** Bancos y fintech de México: llave y cómo se escribe su nombre (en el paquete, la app, el aviso o la tarjeta). */
    private val BANKS: Map<String, Regex> = listOf(
        "plata" to """\bplata\b""", "nu" to """\bnu\b|nubank""", "bbva" to """bbva|bancomer""",
        "santander" to """santander""", "banorte" to """banorte""", "hsbc" to """hsbc""",
        "banamex" to """banamex|citibanamex|\bciti\b""", "scotiabank" to """scotia""",
        "inbursa" to """inbursa""", "azteca" to """azteca""", "afirme" to """afirme""", "bajio" to """baj[ií]o""",
        "rappi" to """rappi""", "mercadopago" to """mercado\s*pago|\bmercado\b""", "liverpool" to """liverpool""", "didi" to """\bdidi\b""", "klar" to """\bklar\b""",
        "stori" to """\bstori\b""", "revolut" to """revolut""", "openbank" to """openbank""",
        "heybanco" to """hey\s*banco""", "spin" to """\bspin\b""", "uala" to """ual[aá]""",
        "fondeadora" to """fondeadora""", "amex" to """amex|american\s*express""",
    ).associate { (key, pattern) -> key to Regex(pattern, RegexOption.IGNORE_CASE) }

    /** Banco al que apunta el primer texto que lo nombra (paquete, app, aviso…); null si ninguno. */
    fun bankOf(vararg hints: String?): String? =
        hints.filterNotNull().firstNotNullOfOrNull { hint -> BANKS.entries.firstOrNull { it.value.containsMatchIn(hint) }?.key }

    /** ¿Esta tarjeta (por su banco o nombre) es de ese banco? */
    fun cardIsOfBank(bank: String, cardBank: String, cardName: String): Boolean =
        BANKS[bank]?.let { it.containsMatchIn(cardBank) || it.containsMatchIn(cardName) } ?: false

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
            """\bspin\b|ual[aá]|fondeadora|amex|american\s*express|billetera|wallet|google\s*pay|liverpool|\bdidi\b""",
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
    val charge = strict ?: parseBankText(n) ?: return null
    // Billetera nombra el banco en el texto ("con Rappicard ••8476") y Gmail en el remitente; las apps, en su paquete/nombre.
    val bank = when (n.packageName) {
        ChargeSources.GOOGLE_WALLET -> ChargeSources.bankOf(n.text)
        ChargeSources.GMAIL -> ChargeSources.bankOf(n.title)
        else -> ChargeSources.bankOf(n.packageName, n.appLabel)
    }
    return charge.copy(bank = bank)
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

/**
 * Muchos avisos no traen los últimos 4 (Plata) o traen los de una tarjeta virtual/digital (Nu, Billetera)
 * que no coinciden con la tarjeta registrada. Si se conoce el banco del aviso y tú tienes una sola tarjeta
 * activa de ese banco, se usa esa; con varias no se adivina. Si los dígitos ya son de alguna tarjeta tuya
 * (o un alias aprendido), no se toca: lo resuelve el servidor.
 */
fun inferCardLast4(charge: ParsedCharge, cards: List<mx.finanzas.pareja.data.Card>, userId: String): String? {
    val bank = charge.bank ?: return null
    val own = cards.filter { it.ownerId == userId && !it.archived }
    val digits = charge.cardLast4
    if (digits != null && own.any { it.last4 == digits || digits in it.altLast4 }) return null
    return soleOwnCardOfBank(own, bank)?.last4
}

/** Solo cuentan las tarjetas de quien recibe el aviso: en un hogar con varias personas no se mezclan. */
fun soleOwnCardOfBank(cards: List<mx.finanzas.pareja.data.Card>, userId: String, bank: String): mx.finanzas.pareja.data.Card? =
    soleOwnCardOfBank(cards.filter { it.ownerId == userId && !it.archived }, bank)

private fun soleOwnCardOfBank(own: List<mx.finanzas.pareja.data.Card>, bank: String): mx.finanzas.pareja.data.Card? =
    own.filter { ChargeSources.cardIsOfBank(bank, it.bank, it.name) }.singleOrNull()

// El aviso de la app de Plata se guarda como "Comercio | $298" (el de Billetera trae además "con …").
private val PLATA_RAW = Regex("""\|\s*\$\s*[\d,]+(?:\.\d{1,2})?$""")

/** Banco de un cobro ya guardado, por su texto original ("comercio | …"); null si no se reconoce. */
fun bankOfRaw(raw: String?): String? = when {
    raw == null -> null
    PLATA_RAW.containsMatchIn(raw.trim()) -> "plata"
    else -> ChargeSources.bankOf(raw)
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
