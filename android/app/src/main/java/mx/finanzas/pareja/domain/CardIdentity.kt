package mx.finanzas.pareja.domain

import mx.finanzas.pareja.data.Card

// Colores de marca: el banco se reconoce de un vistazo sin leer el nombre (ARGB opaco).
private val BRAND_COLORS: List<Pair<Regex, Long>> = listOf(
    Regex("bbva|bancomer", RegexOption.IGNORE_CASE) to 0xFF1464A5L,
    Regex("santander", RegexOption.IGNORE_CASE) to 0xFFEC0000L,
    Regex("\\bnu\\b|nubank", RegexOption.IGNORE_CASE) to 0xFF820AD1L,
    Regex("plata", RegexOption.IGNORE_CASE) to 0xFF0F9D8AL,
    Regex("rappi", RegexOption.IGNORE_CASE) to 0xFFFF441FL,
    Regex("banorte", RegexOption.IGNORE_CASE) to 0xFFEB0029L,
    Regex("hsbc", RegexOption.IGNORE_CASE) to 0xFFDB0011L,
    Regex("banamex|citi", RegexOption.IGNORE_CASE) to 0xFF056DAEL,
    Regex("amex|american express", RegexOption.IGNORE_CASE) to 0xFF006FCFL,
    Regex("mercado\\s*pago", RegexOption.IGNORE_CASE) to 0xFF009EE3L,
)

// Para bancos sin color de marca: estable por tarjeta, así no cambia entre sesiones.
private val FALLBACK_COLORS = longArrayOf(
    0xFF375390L, 0xFF0F9D8AL, 0xFFC2410CL, 0xFF7C3AEDL, 0xFF4D7C0FL, 0xFFBE185DL,
)

private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")

/** Color de la tarjeta: el elegido por la persona, el de su banco o uno estable derivado del id. */
fun cardAccentArgb(card: Card): Long {
    card.color?.takeIf { HEX_COLOR.matches(it) }?.let { return 0xFF000000L or it.drop(1).toLong(16) }
    BRAND_COLORS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(card.bank) }?.let { return it.second }
    val hash = card.id.fold(0L) { acc, ch -> (acc * 31 + ch.code) and 0xFFFFFFFFL }
    return FALLBACK_COLORS[(hash % FALLBACK_COLORS.size).toInt()]
}

/** "Plata ••0931", o "BBVA · Azul ••9876" cuando el nombre no incluye al banco. */
fun cardLabel(card: Card): String {
    val name = if (card.name.contains(card.bank, ignoreCase = true)) card.name else "${card.bank} · ${card.name}"
    return "$name ••${card.last4}"
}
