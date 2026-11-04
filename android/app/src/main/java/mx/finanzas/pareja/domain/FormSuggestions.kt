package mx.finanzas.pareja.domain

import mx.finanzas.pareja.data.Transaction

/** Cuántas categorías y tarjetas se muestran de entrada en Nuevo cobro; el resto va detrás de «Ver todas» / «+N». */
const val TOP_SUGGESTIONS = 3

/** Cuánto historial cuenta para saber qué usas más. */
const val SUGGESTION_LOOKBACK_DAYS = 90

/**
 * Los ids más repetidos de [usedIds] (los nulos no cuentan). Con el mismo número de usos gana el que
 * apareció primero, así que pasa la lista con lo más reciente al principio.
 */
fun mostUsedIds(usedIds: List<String?>, limit: Int = TOP_SUGGESTIONS): List<String> =
    usedIds.filterNotNull().groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value } // estable: respeta el orden de aparición en los empates
        .take(limit).map { it.key }

/** Emoji de cada categoría según la clave que guarda la base (`icon`); sin clave o desconocida, 🏷️. */
fun categoryEmoji(icon: String?): String = when (icon) {
    "fuel" -> "⛽"
    "utensils" -> "🍽️"
    "coffee" -> "☕"
    "cart" -> "🛒"
    "gamepad" -> "🎮"
    "shirt" -> "👕"
    "package" -> "📦"
    "repeat" -> "🔁"
    "heart" -> "❤️"
    "car" -> "🚗"
    "home" -> "🏠"
    "book" -> "📚"
    "plane" -> "✈️"
    "paw" -> "🐾"
    "gift" -> "🎁"
    else -> "🏷️"
}

private const val MIN_MERCHANT_CHARS = 3

/**
 * El gasto más reciente en el mismo comercio (mismo nombre, sin importar mayúsculas), para sugerir su
 * categoría y tarjeta. [history] va con lo más reciente primero.
 *
 * shortcut: solo coincidencia exacta del nombre; si hace falta, comparar también prefijos o ligar comercios parecidos.
 */
fun suggestForMerchant(merchant: String, history: List<Transaction>): Transaction? {
    val key = merchantKey(merchant)
    if (key.length < MIN_MERCHANT_CHARS) return null
    return history.firstOrNull { merchantKey(it.merchant) == key }
}

/** Sin historial: las que casi todos usan primero (súper, comida y gasolina). */
private val STARTER_ICONS = listOf("cart", "utensils", "fuel")

/** Ids de categoría ofrecidas de entrada: las más usadas y, si faltan, las de arranque y luego las demás. */
fun suggestedCategoryIds(usedIds: List<String?>, all: List<Pair<String, String?>>): List<String> {
    val top = mostUsedIds(usedIds).filter { id -> all.any { it.first == id } }
    val starters = STARTER_ICONS.mapNotNull { icon -> all.firstOrNull { it.second == icon }?.first }
    return (top + starters + all.map { it.first }).distinct().take(TOP_SUGGESTIONS)
}
