package mx.finanzas.pareja.domain

import kotlinx.coroutines.CancellationException

/**
 * Intenta enviar cada captura pendiente. Devuelve, en el mismo orden, las que fallaron
 * (sin red, sesión vencida…) para reintentarlas después sin perder cobros.
 */
suspend fun flushCaptures(
    pending: List<ParsedCharge>,
    send: suspend (ParsedCharge) -> Unit,
): List<ParsedCharge> {
    val failed = mutableListOf<ParsedCharge>()
    for (charge in pending) {
        try {
            send(charge)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed += charge
        }
    }
    return failed
}
