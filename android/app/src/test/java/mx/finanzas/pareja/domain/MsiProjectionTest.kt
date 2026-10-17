package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.MsiInstallment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MsiProjectionTest {
    private fun inst(merchant: String, due: LocalDate, number: Int, total: Int, amount: Double, card: String = "c1") =
        MsiInstallment(
            cardId = card, transactionId = "t-$merchant", merchant = merchant,
            cycleEnd = due, dueDate = due, number = number, installments = total, amount = amount,
        )

    private val rows = listOf(
        inst("AOC", LocalDate(2026, 12, 9), 6, 9, 948.61),
        inst("AOC", LocalDate(2026, 11, 9), 5, 9, 948.61),
        inst("HUAWEI", LocalDate(2026, 11, 9), 9, 12, 359.92),
        inst("ML", LocalDate(2026, 11, 9), 10, 10, 189.50),
        inst("HUAWEI", LocalDate(2026, 12, 9), 10, 12, 359.92),
    )

    @Test
    fun `agrupa por mes de pago en orden cronologico`() {
        val months = projectInstallments(rows)

        assertEquals(listOf(11, 12), months.map { it.month })
        assertEquals(listOf(3, 2), months.map { it.items.size })
    }

    @Test
    fun `cada mes suma sus mensualidades`() {
        val (nov, dic) = projectInstallments(rows)

        assertEquals(948.61 + 359.92 + 189.50, nov.total, 0.001)
        assertEquals(948.61 + 359.92, dic.total, 0.001)
    }

    @Test
    fun `dentro del mes van primero las mas grandes`() {
        val nov = projectInstallments(rows).first()
        assertEquals(listOf("AOC", "HUAWEI", "ML"), nov.items.map { it.merchant })
    }

    @Test
    fun `cuenta las compras que terminan ese mes`() {
        val (nov, dic) = projectInstallments(rows)

        assertEquals(1, nov.finishing)
        assertEquals(0, dic.finishing)
        assertTrue(nov.items.first { it.merchant == "ML" }.isLast)
        assertFalse(nov.items.first { it.merchant == "AOC" }.isLast)
    }

    @Test
    fun `los meses cruzan de año sin desordenarse`() {
        val months = projectInstallments(
            listOf(inst("A", LocalDate(2027, 1, 9), 2, 3, 100.0), inst("A", LocalDate(2026, 12, 9), 1, 3, 100.0)),
        )
        assertEquals(listOf(2026 to 12, 2027 to 1), months.map { it.year to it.month })
        assertEquals("diciembre 2026", months.first().label)
    }

    @Test
    fun `el pendiente suma todo lo que falta y dice cuando termina`() {
        val pending = pendingInstallments(projectInstallments(rows))

        assertEquals(5, pending.installments)
        assertEquals(948.61 * 2 + 359.92 * 2 + 189.50, pending.amount, 0.001)
        assertEquals(12, pending.lastMonth!!.month)
    }

    @Test
    fun `sin compras a meses no hay nada pendiente`() {
        val pending = pendingInstallments(projectInstallments(emptyList()))
        assertEquals(0, pending.installments)
        assertEquals(0.0, pending.amount, 0.0)
        assertNull(pending.lastMonth)
    }
}
