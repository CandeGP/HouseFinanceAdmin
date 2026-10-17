package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxSource
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCandidatesTest {
    private fun t(
        id: String, merchant: String, day: Int = 10, amount: Double = 100.0, source: TxSource = TxSource.MANUAL,
        mode: PaymentMode = PaymentMode.CONTADO, card: String = "c1", status: TxStatus = TxStatus.CONFIRMED,
    ): Transaction = tx(id, amount, cardId = card, date = LocalDate(2026, 10, day), status = status)
        .copy(merchant = merchant, source = source, paymentMode = mode)

    @Test
    fun `flags same charge from notification and statement`() {
        val pairs = findDuplicateCandidates(
            listOf(
                t("a", "Tommy Hilfiger", source = TxSource.NOTIFICATION),
                t("b", "TOMMY HILFIGER MX", day = 11, source = TxSource.STATEMENT),
            ),
        )
        assertEquals(1, pairs.size)
        assertEquals("a|b", pairs.single().key)
    }

    @Test
    fun `flags contado and msi versions of the same purchase`() {
        val pairs = findDuplicateCandidates(
            listOf(t("a", "Liverpool"), t("b", "Liverpool", mode = PaymentMode.MSI)),
        )
        assertEquals(1, pairs.size)
    }

    @Test
    fun `ignores repeated charges from the same source`() {
        val pairs = findDuplicateCandidates(
            listOf(t("a", "DLO UBER RIDE", amount = 10.0), t("b", "DLO UBER RIDE", amount = 10.0)),
        )
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `ignores other card, far dates, other amount, other merchant and pending`() {
        val base = t("a", "Liverpool", source = TxSource.NOTIFICATION)
        val others = listOf(
            t("b", "Liverpool", source = TxSource.STATEMENT, card = "c2"),
            t("c", "Liverpool", source = TxSource.STATEMENT, day = 20),
            t("d", "Liverpool", source = TxSource.STATEMENT, amount = 101.0),
            t("e", "Gasolinera", source = TxSource.STATEMENT),
            t("f", "Liverpool", source = TxSource.STATEMENT, status = TxStatus.PENDING_REVIEW),
        )
        assertTrue(findDuplicateCandidates(listOf(base) + others).isEmpty())
    }
}
