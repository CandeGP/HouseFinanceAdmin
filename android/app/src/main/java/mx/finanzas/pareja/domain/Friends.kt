package mx.finanzas.pareja.domain

import java.text.Normalizer
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.LoanDirection

/** Filtros de la lista de Amigos. */
enum class FriendFilter { ALL, OWE_YOU, YOU_OWE, SETTLED }

/**
 * Una persona con la que llevas cuentas, armada a partir de los saldos de Préstamos (no hay tabla propia).
 * [net] positivo = te debe; negativo = le debes; cero = a mano.
 */
data class Friend(
    val name: String,
    val net: Double,
    val openLoans: Int,
    val lastActivity: LocalDate?,
    val isPartner: Boolean,
    val balance: PersonBalance,
) {
    val settled: Boolean get() = openLoans == 0
    val owesYou: Boolean get() = net > 0
    val youOwe: Boolean get() = net < 0
}

private val ACCENTS = Regex("\\p{M}+")

private fun normalize(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")

/** Convierte los saldos en amigos. [partnerNames] son los nombres de los demás integrantes del hogar. */
fun buildFriends(balances: List<PersonBalance>, partnerNames: Set<String>): List<Friend> {
    val partners = partnerNames.map(::normalize).toSet()
    return balances.map { balance ->
        val dates = balance.loans.flatMap { view -> listOf(view.loan.occurredOn) + view.repayments.map { it.paidOn } }
        Friend(
            name = balance.person,
            net = balance.net,
            openLoans = balance.loans.count { it.state == LoanState.OPEN },
            lastActivity = dates.maxOrNull(),
            isPartner = normalize(balance.person) in partners,
            balance = balance,
        )
    }
}

/** Aplica el filtro y la búsqueda por nombre (sin distinguir mayúsculas ni acentos). */
fun filterFriends(friends: List<Friend>, filter: FriendFilter, query: String): List<Friend> {
    val needle = normalize(query)
    return friends.filter { friend ->
        val byFilter = when (filter) {
            FriendFilter.ALL -> true
            FriendFilter.OWE_YOU -> friend.owesYou
            FriendFilter.YOU_OWE -> friend.youOwe
            FriendFilter.SETTLED -> friend.settled
        }
        byFilter && (needle.isEmpty() || normalize(friend.name).contains(needle))
    }
}

/**
 * Abonos que dejan en cero todo lo abierto con [friend]: lo que falta de cada préstamo abierto que registraste
 * tú ([userId]); los que registró otra persona no se tocan.
 */
fun settlementPlan(friend: Friend, userId: String): List<Pair<LoanView, Double>> =
    friend.balance.loans
        .filter { it.state == LoanState.OPEN && it.loan.ownerId == userId && it.remaining > 0 }
        .map { it to it.remaining }

/** Mensaje para recordarle a [friend] lo que te debe; null si no te debe nada. */
fun reminderOrNull(friend: Friend): String? {
    if (!friend.owesYou) return null
    val notes = friend.balance.loans
        .filter { it.state == LoanState.OPEN && it.loan.direction == LoanDirection.LENT }
        .mapNotNull { it.loan.note }
    val detail = if (notes.isEmpty()) "" else " (${notes.joinToString("; ")})"
    return "Hola ${friend.name}, te recuerdo que me debes ${formatMxn(friend.net)}$detail. ¡Gracias!"
}

fun reminderText(friend: Friend): String = reminderOrNull(friend).orEmpty()
