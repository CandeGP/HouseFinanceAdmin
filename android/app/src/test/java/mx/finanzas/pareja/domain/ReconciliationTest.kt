package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconciliationTest {
    private fun d(day: Int, month: Int = 9) = LocalDate(2026, month, day)

    private fun summary() = StatementSummary(
        bank = "Plata", cardLast4 = "0931", periodStart = d(1), periodEnd = d(30),
        cutoffDate = d(30), dueDate = LocalDate(2026, 10, 19),
        creditLimit = 15_000.0, payNoInterest = null, minimumPayment = null,
    )

    private fun mv(
        day: Int, description: String, amount: Double,
        installment: Boolean = false,
    ) = StatementMovement(
        operationDate = d(day), chargeDate = d(day), description = description,
        amount = amount, cardLast4 = null, isInstallmentCharge = installment,
    )

    private fun statement(
        movements: List<StatementMovement>,
        installments: List<StatementInstallment> = emptyList(),
    ) = ParsedStatement(summary(), movements, installments)

    private fun appTx(id: String, merchant: String, amount: Double, day: Int, cardId: String? = "c1") =
        tx(id, amount, cardId = cardId, date = d(day)).copy(merchant = merchant)

    private fun reconcile(s: ParsedStatement, txs: List<Transaction>, tolerance: Int = 5) =
        reconcile(s, txs, cardId = "c1", toleranceDays = tolerance)

    @Test
    fun `un cargo ya registrado se concilia por monto fecha y comercio`() {
        val r = reconcile(
            statement(listOf(mv(4, "CAFFENIO", 90.0))),
            listOf(appTx("t1", "Caffenio", 90.0, 4)),
        )
        assertEquals(1, r.matched.size)
        assertEquals("t1", r.matched.single().transaction.id)
        assertTrue(r.missingInApp.isEmpty())
        assertTrue(r.notInStatement.isEmpty())
    }

    @Test
    fun `un cargo sin registro queda como faltante`() {
        val r = reconcile(statement(listOf(mv(4, "CAFFENIO", 90.0))), emptyList())
        assertEquals(listOf("CAFFENIO"), r.missingInApp.map { it.description })
    }

    @Test
    fun `un gasto de la app que el estado no muestra queda como no aparece`() {
        val r = reconcile(statement(emptyList()), listOf(appTx("t1", "Oxxo", 30.0, 10)))
        assertEquals(listOf("t1"), r.notInStatement.map { it.id })
    }

    @Test
    fun `montos distintos no se concilian`() {
        val r = reconcile(
            statement(listOf(mv(4, "CAFFENIO", 90.0))),
            listOf(appTx("t1", "Caffenio", 95.0, 4)),
        )
        assertTrue(r.matched.isEmpty())
        assertEquals(1, r.missingInApp.size)
        assertEquals(1, r.notInStatement.size)
    }

    @Test
    fun `tolera unos dias de diferencia pero no muchos`() {
        val near = reconcile(statement(listOf(mv(6, "CAFFENIO", 90.0))), listOf(appTx("t1", "Caffenio", 90.0, 4)))
        assertEquals(1, near.matched.size)

        val far = reconcile(statement(listOf(mv(20, "CAFFENIO", 90.0))), listOf(appTx("t1", "Caffenio", 90.0, 4)))
        assertTrue(far.matched.isEmpty())
    }

    @Test
    fun `dos cargos iguales se concilian uno a uno`() {
        val twoMovements = listOf(mv(9, "MERPAGO*TELCEL", 100.0), mv(14, "MERPAGO*TELCEL", 100.0))
        val onlyOne = reconcile(statement(twoMovements), listOf(appTx("t1", "Mercadopago *Telcel", 100.0, 9)))
        assertEquals(1, onlyOne.matched.size)
        assertEquals(1, onlyOne.missingInApp.size)

        val both = reconcile(
            statement(twoMovements),
            listOf(appTx("t1", "Mercadopago *Telcel", 100.0, 9), appTx("t2", "Mercadopago *Telcel", 100.0, 14)),
        )
        assertEquals(2, both.matched.size)
        assertTrue(both.missingInApp.isEmpty())
    }

    @Test
    fun `con mismo monto y fecha elige el comercio mas parecido`() {
        val r = reconcile(
            statement(listOf(mv(8, "HEB TORRES LANDA", 200.0))),
            listOf(
                appTx("otro", "Gasolinera Shell", 200.0, 8),
                appTx("heb", "HEB Torres Landa", 200.0, 8),
            ),
        )
        assertEquals("heb", r.matched.single().transaction.id)
        assertEquals(listOf("otro"), r.notInStatement.map { it.id })
    }

    @Test
    fun `los pagos y las mensualidades se separan`() {
        val r = reconcile(
            statement(
                listOf(
                    mv(9, "PAGO POR SPEI", -6071.12),
                    mv(23, "04 DE 09 TIENDA A MESES", 1408.0, installment = true),
                    mv(15, "RESTAURANTE", 350.5),
                ),
            ),
            emptyList(),
        )
        assertEquals(1, r.payments.size)
        assertEquals(1, r.installmentCharges.size)
        assertEquals(listOf("RESTAURANTE"), r.missingInApp.map { it.description })
    }

    @Test
    fun `ignora gastos de otras tarjetas y fuera del periodo`() {
        val r = reconcile(
            statement(emptyList()),
            listOf(
                appTx("otra-tarjeta", "Oxxo", 30.0, 10, cardId = "c2"),
                appTx("fuera", "Oxxo", 30.0, 10).copy(occurredAt = LocalDate(2026, 7, 1)),
            ),
        )
        assertTrue(r.notInStatement.isEmpty())
    }

    @Test
    fun `las compras a meses de la app no cuentan como faltantes en el estado`() {
        val msi = appTx("laptop", "Laptop", 12_000.0, 3).copy(
            paymentMode = PaymentMode.MSI, installments = 12, installmentNumber = 1,
        )
        val r = reconcile(statement(emptyList()), listOf(msi))
        assertTrue(r.notInStatement.isEmpty())
    }

    // ------------------------------------------------------------ MSI
    private fun statementInstallment(description: String, original: Double) = StatementInstallment(
        purchaseDate = LocalDate(2026, 6, 22), description = description,
        originalAmount = original, pendingBalance = original / 3, requiredPayment = original / 3,
        current = 2, total = 3,
    )

    @Test
    fun `una compra a meses del estado se concilia con la de la app por monto original`() {
        val msi = appTx("tv", "Tienda Ejemplo", 2034.0, 22).copy(
            occurredAt = LocalDate(2026, 6, 22), paymentMode = PaymentMode.MSI, installments = 3, installmentNumber = 1,
        )
        val r = reconcile(
            statement(emptyList(), listOf(statementInstallment("TIENDA EJEMPLO LEON", 2034.0))),
            listOf(msi),
        )
        assertEquals("tv", r.matchedInstallments.single().transaction.id)
        assertTrue(r.missingInstallments.isEmpty())
    }

    @Test
    fun `una compra a meses sin registro queda como faltante`() {
        val r = reconcile(
            statement(emptyList(), listOf(statementInstallment("TIENDA EJEMPLO LEON", 2034.0))),
            emptyList(),
        )
        assertEquals(1, r.missingInstallments.size)
    }

    // ------------------------------------------------------------ similitud
    @Test
    fun `la similitud ignora mayusculas, acentos y simbolos`() {
        assertEquals(1.0, merchantSimilarity("Misc La Alemana Sión 🛒", "MISC LA ALEMANA SION"), 0.0)
    }

    @Test
    fun `mercadopago abreviado como merpago es parecido`() {
        assertTrue(merchantSimilarity("MERPAGO*ADRI", "Mercadopago *Adri") > 0.8)
    }

    @Test
    fun `comercios distintos tienen baja similitud`() {
        assertTrue(merchantSimilarity("HEB TORRES LANDA", "Gasolinera Shell") < 0.3)
    }
}
