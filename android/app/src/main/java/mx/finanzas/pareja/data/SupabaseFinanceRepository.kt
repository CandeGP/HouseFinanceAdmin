package mx.finanzas.pareja.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createSupabase(url: String, key: String): SupabaseClient =
    createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }

private const val PAYEES_LOOKBACK = 500L // últimos pagos que se revisan para sugerir "Pagado a"
private const val LOANS_LIMIT = 500L // préstamos y abonos que se cargan a la vez

@Serializable
private data class NewCategory(@SerialName("household_id") val householdId: String, val name: String)

@Serializable
private data class PayeeRow(val counterparty: String? = null)

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

    override suspend fun addCategory(householdId: String, name: String) {
        client.postgrest.from("categories").insert(NewCategory(householdId, name))
    }

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

    override suspend fun editCard(id: String, patch: CardEditPatch) {
        client.postgrest.from("cards").update(patch) {
            filter { eq("id", id) }
        }
    }

    override suspend fun archiveCard(id: String) {
        client.postgrest.from("cards").update(mapOf("archived" to true)) {
            filter { eq("id", id) }
        }
    }

    override suspend fun restoreCard(id: String) {
        client.postgrest.from("cards").update(mapOf("archived" to false)) {
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

    override suspend fun cardMonthSpend(cardId: String, ref: LocalDate): MonthSpend? =
        client.postgrest.rpc(
            "card_month_spend",
            buildJsonObject {
                put("p_card", cardId)
                put("p_ref", ref.toString())
            },
        ).decodeList<MonthSpend>().firstOrNull()

    override suspend fun setCardBudget(id: String, amount: Double?) {
        // JsonNull limpia el campo: mapOf con null no se serializaría como null explícito.
        client.postgrest.from("cards").update(
            buildJsonObject { put("monthly_budget", amount?.let { JsonPrimitive(it) } ?: JsonNull) },
        ) {
            filter { eq("id", id) }
        }
    }

    // ---- Pagos a tarjeta
    override suspend fun cardCycleStatus(cardId: String, ref: LocalDate): CardCycleStatus? =
        client.postgrest.rpc(
            "card_cycle_status",
            buildJsonObject {
                put("p_card", cardId)
                put("p_ref", ref.toString())
            },
        ).decodeList<CardCycleStatus>().firstOrNull()

    override suspend fun cardPayments(cardId: String, cutoff: LocalDate): List<CardPayment> =
        client.postgrest.from("card_payments").select {
            filter {
                eq("card_id", cardId)
                eq("cutoff_date", cutoff.toString())
            }
            order("paid_at", Order.DESCENDING)
        }.decodeList<CardPayment>()

    override suspend fun addCardPayment(payment: NewCardPayment) {
        client.postgrest.from("card_payments").insert(payment)
    }

    override suspend fun deleteCardPayment(id: String) {
        client.postgrest.from("card_payments").delete {
            filter { eq("id", id) }
        }
    }

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

    // ---- Estados de cuenta
    override suspend fun importStatement(
        cardId: String,
        summary: StatementImportSummary,
        movements: List<ImportMovement>,
        installments: List<ImportInstallment>,
    ): ImportResult =
        client.postgrest.rpc(
            "import_statement",
            buildJsonObject {
                put("p_card", cardId)
                put("p_statement", wireJson.encodeToJsonElement(StatementImportSummary.serializer(), summary))
                put("p_movements", wireJson.encodeToJsonElement(ListSerializer(ImportMovement.serializer()), movements))
                put(
                    "p_installments",
                    wireJson.encodeToJsonElement(ListSerializer(ImportInstallment.serializer()), installments),
                )
            },
        ).decodeAs<ImportResult>()

    // ---- Pagos en efectivo / transferencia
    override suspend fun manualPayments(from: Instant, toExclusive: Instant): List<ManualPayment> =
        client.postgrest.from("manual_payments").select {
            filter {
                gte("paid_at", from.toString())
                lt("paid_at", toExclusive.toString())
            }
            order("paid_at", Order.DESCENDING)
        }.decodeList<ManualPayment>()

    override suspend fun payees(): List<String> =
        client.postgrest.from("manual_payments").select(Columns.list("counterparty")) {
            order("paid_at", Order.DESCENDING)
            limit(PAYEES_LOOKBACK)
        }.decodeList<PayeeRow>().mapNotNull { it.counterparty }

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

    // ---- Préstamos
    override suspend fun loans(): List<Loan> =
        client.postgrest.from("loans").select {
            order("occurred_on", Order.DESCENDING)
            limit(LOANS_LIMIT)
        }.decodeList<Loan>()

    override suspend fun loanRepayments(): List<LoanRepayment> =
        client.postgrest.from("loan_repayments").select {
            order("paid_on", Order.DESCENDING)
            limit(LOANS_LIMIT)
        }.decodeList<LoanRepayment>()

    override suspend fun addLoan(loan: NewLoan) {
        client.postgrest.from("loans").insert(loan)
    }

    override suspend fun deleteLoan(id: String) {
        client.postgrest.from("loans").delete {
            filter { eq("id", id) }
        }
    }

    override suspend fun setLoanForgiven(id: String, forgiven: Boolean) {
        // JsonNull limpia el campo al reabrir el préstamo.
        client.postgrest.from("loans").update(
            buildJsonObject {
                put("forgiven_at", if (forgiven) JsonPrimitive(Clock.System.now().toString()) else JsonNull)
            },
        ) {
            filter { eq("id", id) }
        }
    }

    override suspend fun addLoanRepayment(repayment: NewLoanRepayment) {
        client.postgrest.from("loan_repayments").insert(repayment)
    }

    override suspend fun deleteLoanRepayment(id: String) {
        client.postgrest.from("loan_repayments").delete {
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
        val wireJson = Json { encodeDefaults = true; explicitNulls = true }
        const val EVIDENCE_BUCKET = "evidence"
        val SIGNED_URL_TTL = 1.hours
    }
}
