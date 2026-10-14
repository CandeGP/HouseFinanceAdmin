package mx.finanzas.pareja.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createSupabase(url: String, key: String): SupabaseClient =
    createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }

class SupabaseFinanceRepository(private val client: SupabaseClient) : FinanceRepository {

    // ---- Sesión
    override suspend fun currentUserId(): String? {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.id
    }

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() = client.auth.signOut()

    // ---- Hogar
    override suspend fun members(): List<Member> =
        client.postgrest.from("members").select().decodeList<Member>()

    override suspend fun createHousehold(name: String, displayName: String): String =
        client.postgrest.rpc(
            "create_household",
            buildJsonObject {
                put("p_name", name)
                put("p_display_name", displayName)
            },
        ).decodeAs<String>()

    override suspend fun createInvite(householdId: String): String =
        client.postgrest.rpc(
            "create_invite",
            buildJsonObject { put("p_household", householdId) },
        ).decodeAs<String>()

    override suspend fun acceptInvite(code: String, displayName: String): String =
        client.postgrest.rpc(
            "accept_invite",
            buildJsonObject {
                put("p_code", code)
                put("p_display_name", displayName)
            },
        ).decodeAs<String>()

    // ---- Catálogos
    override suspend fun categories(): List<Category> =
        client.postgrest.from("categories").select {
            order("name", Order.ASCENDING)
        }.decodeList<Category>()

    override suspend fun cards(): List<Card> =
        client.postgrest.from("cards").select {
            order("name", Order.ASCENDING)
        }.decodeList<Card>()

    override suspend fun addCard(card: NewCard) {
        client.postgrest.from("cards").insert(card)
    }

    override suspend fun updateCard(id: String, patch: CardPatch) {
        client.postgrest.from("cards").update(patch) {
            filter { eq("id", id) }
        }
    }

    override suspend fun archiveCard(id: String) {
        client.postgrest.from("cards").update(mapOf("archived" to true)) {
            filter { eq("id", id) }
        }
    }

    override suspend fun cycleEstimate(cardId: String, ref: LocalDate): CycleEstimate? =
        client.postgrest.rpc(
            "card_cycle_estimate",
            buildJsonObject {
                put("p_card_id", cardId)
                put("p_ref", ref.toString())
            },
        ).decodeList<CycleEstimate>().firstOrNull()

    // ---- Gastos
    override suspend fun transactions(from: LocalDate, to: LocalDate): List<Transaction> =
        client.postgrest.from("transactions").select {
            filter {
                gte("occurred_at", from.toString())
                lte("occurred_at", to.toString())
            }
            order("occurred_at", Order.DESCENDING)
        }.decodeList<Transaction>()

    override suspend fun transaction(id: String): Transaction? =
        client.postgrest.from("transactions").select {
            filter { eq("id", id) }
        }.decodeList<Transaction>().firstOrNull()

    override suspend fun addTransaction(tx: NewTransaction) {
        client.postgrest.from("transactions").insert(tx)
    }

    override suspend fun updateTransaction(id: String, patch: TransactionPatch) {
        client.postgrest.from("transactions").update(patch) {
            filter { eq("id", id) }
        }
    }

    override suspend fun deleteTransaction(id: String) {
        client.postgrest.from("transactions").delete {
            filter { eq("id", id) }
        }
    }

    // ---- Captura automática
    override suspend fun ingestCapture(
        householdId: String,
        merchant: String,
        amount: Double,
        capturedAt: Instant,
        cardLast4: String?,
        source: TxSource,
        raw: String?,
    ): IngestResult =
        client.postgrest.rpc(
            "ingest_capture",
            buildJsonObject {
                put("p_household", householdId)
                put("p_merchant", merchant)
                put("p_amount", amount)
                put("p_captured_at", capturedAt.toString())
                put("p_card_last4", cardLast4)
                put("p_source", source.wireName)
                put("p_raw", raw)
            },
        ).decodeList<IngestResult>().first()

    override suspend fun pendingTransactions(): List<Transaction> =
        client.postgrest.from("transactions").select {
            filter { eq("status", "pending_review") }
            order("captured_at", Order.DESCENDING)
        }.decodeList<Transaction>()

    /** Confirma el cobro; el servidor además aprende el alias de últimos 4 de la tarjeta. */
    override suspend fun confirmTransaction(id: String, cardId: String, categoryId: String?) {
        client.postgrest.rpc(
            "confirm_capture",
            buildJsonObject {
                put("p_tx", id)
                put("p_card", cardId)
                put("p_category", categoryId)
            },
        )
    }

    // ---- Pagos en efectivo / transferencia
    override suspend fun manualPayments(from: Instant, toExclusive: Instant): List<ManualPayment> =
        client.postgrest.from("manual_payments").select {
            filter {
                gte("paid_at", from.toString())
                lt("paid_at", toExclusive.toString())
            }
            order("paid_at", Order.DESCENDING)
        }.decodeList<ManualPayment>()

    override suspend fun manualPayment(id: String): ManualPayment? =
        client.postgrest.from("manual_payments").select {
            filter { eq("id", id) }
        }.decodeList<ManualPayment>().firstOrNull()

    override suspend fun addManualPayment(payment: NewManualPayment) {
        client.postgrest.from("manual_payments").insert(payment)
    }

    override suspend fun updateManualPayment(id: String, patch: ManualPaymentPatch) {
        client.postgrest.from("manual_payments").update(patch) {
            filter { eq("id", id) }
        }
    }

    override suspend fun deleteManualPayment(id: String) {
        client.postgrest.from("manual_payments").delete {
            filter { eq("id", id) }
        }
    }

    // ---- Evidencias
    override suspend fun uploadEvidence(path: String, bytes: ByteArray, mimeType: String) {
        client.storage.from(EVIDENCE_BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
    }

    override suspend fun deleteEvidence(path: String) {
        client.storage.from(EVIDENCE_BUCKET).delete(path)
    }

    override suspend fun evidenceUrl(path: String): String =
        client.storage.from(EVIDENCE_BUCKET).createSignedUrl(path, SIGNED_URL_TTL)

    private companion object {
        const val EVIDENCE_BUCKET = "evidence"
        val SIGNED_URL_TTL = 1.hours
    }
}
