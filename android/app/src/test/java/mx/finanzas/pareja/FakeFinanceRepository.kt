package mx.finanzas.pareja

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CardPatch
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.CycleEstimate
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.Member
import mx.finanzas.pareja.data.MemberRole
import mx.finanzas.pareja.data.NewCard
import mx.finanzas.pareja.data.NewTransaction
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TransactionPatch

/** Doble en memoria de FinanceRepository, con registro de las escrituras. */
class FakeFinanceRepository : FinanceRepository {
    var userId: String? = null
    var members: MutableList<Member> = mutableListOf()
    var failWith: Exception? = null

    var cardList: List<Card> = emptyList()
    var categoryList: List<Category> = emptyList()
    val estimates: MutableMap<String, CycleEstimate> = mutableMapOf()
    var txList: MutableList<Transaction> = mutableListOf()

    val addedTransactions = mutableListOf<NewTransaction>()
    val updatedTransactions = mutableListOf<Pair<String, TransactionPatch>>()
    val deletedTransactions = mutableListOf<String>()
    val addedCards = mutableListOf<NewCard>()
    val archivedCards = mutableListOf<String>()
    var lastRange: Pair<LocalDate, LocalDate>? = null

    private fun failIfNeeded() {
        failWith?.let { throw it }
    }

    override suspend fun currentUserId(): String? = userId

    override suspend fun signIn(email: String, password: String) {
        failIfNeeded()
        userId = "u1"
    }

    override suspend fun signUp(email: String, password: String) {
        failIfNeeded()
        userId = "u1"
    }

    override suspend fun signOut() {
        userId = null
    }

    override suspend fun members(): List<Member> = members.toList()

    override suspend fun createHousehold(name: String, displayName: String): String {
        failIfNeeded()
        members += Member("h1", requireNotNull(userId), MemberRole.ADMIN, displayName)
        return "h1"
    }

    override suspend fun createInvite(householdId: String): String = "ABC123XYZ0"

    override suspend fun acceptInvite(code: String, displayName: String): String {
        failIfNeeded()
        members += Member("h1", requireNotNull(userId), MemberRole.MEMBER, displayName)
        return "h1"
    }

    override suspend fun categories(): List<Category> = categoryList
    override suspend fun cards(): List<Card> = cardList

    override suspend fun addCard(card: NewCard) {
        failIfNeeded()
        addedCards += card
    }

    override suspend fun updateCard(id: String, patch: CardPatch) = Unit

    override suspend fun archiveCard(id: String) {
        archivedCards += id
    }

    override suspend fun cycleEstimate(cardId: String, ref: LocalDate): CycleEstimate? = estimates[cardId]

    override suspend fun transactions(from: LocalDate, to: LocalDate): List<Transaction> {
        lastRange = from to to
        return txList.filter { it.occurredAt in from..to }
    }

    override suspend fun transaction(id: String): Transaction? = txList.firstOrNull { it.id == id }

    override suspend fun addTransaction(tx: NewTransaction) {
        failIfNeeded()
        addedTransactions += tx
    }

    override suspend fun updateTransaction(id: String, patch: TransactionPatch) {
        failIfNeeded()
        updatedTransactions += id to patch
    }

    override suspend fun deleteTransaction(id: String) {
        deletedTransactions += id
        txList.removeAll { it.id == id }
    }
}
