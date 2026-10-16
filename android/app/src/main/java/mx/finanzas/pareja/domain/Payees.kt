package mx.finanzas.pareja.domain

/**
 * Sugerencias de "Pagado a": sin vacíos ni repetidos (ignora mayúsculas y espacios sobrantes),
 * conservando la escritura más reciente de cada nombre y en orden alfabético.
 * [recentFirst] llega del más reciente al más antiguo.
 */
fun distinctPayees(recentFirst: List<String>): List<String> =
    recentFirst
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

/** Sugerencias que coinciden con lo escrito; vacío muestra todas. */
fun matchingPayees(payees: List<String>, typed: String): List<String> {
    val needle = typed.trim().lowercase()
    return if (needle.isEmpty()) payees else payees.filter { it.lowercase().contains(needle) }
}
