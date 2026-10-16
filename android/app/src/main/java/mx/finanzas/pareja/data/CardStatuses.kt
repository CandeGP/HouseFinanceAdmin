package mx.finanzas.pareja.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

/** Estado de pago de varias tarjetas a la vez (en paralelo: una consulta por tarjeta). */
suspend fun FinanceRepository.cycleStatuses(cardIds: List<String>, ref: LocalDate): Map<String, CardCycleStatus> =
    coroutineScope {
        cardIds.map { id -> async { cardCycleStatus(id, ref)?.let { id to it } } }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }
