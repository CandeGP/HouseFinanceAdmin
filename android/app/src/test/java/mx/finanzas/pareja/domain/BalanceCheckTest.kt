package mx.finanzas.pareja.domain

import mx.finanzas.pareja.cycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceCheckTest {
    @Test
    fun `la deuda suma el corte sin pagar, el ciclo y las mensualidades que faltan`() {
        val debt = debtTotal(estimate(cycleTotal = 2278.48, msiRemaining = 22438.42), cycleStatus(CardPayStatus.PENDING, target = 2010.97))
        assertEquals(26727.87, debt, 0.001)
    }

    @Test
    fun `con el corte pagado o sin estado solo cuenta ciclo y mensualidades`() {
        assertEquals(700.0, debtTotal(estimate(cycleTotal = 500.0, msiRemaining = 200.0), cycleStatus(CardPayStatus.PAID, paid = 1000.0)), 0.0)
        assertEquals(700.0, debtTotal(estimate(cycleTotal = 500.0, msiRemaining = 200.0), null), 0.0)
        assertEquals(0.0, debtTotal(null, null), 0.0)
    }

    @Test
    fun `una diferencia de centavos cuadra`() {
        val c = compareBalance(app = 4559.11, bank = 4558.95)
        assertEquals(BalanceVerdict.MATCH, c.verdict)
        assertTrue(c.message().startsWith("Cuadra"))
    }

    @Test
    fun `la tolerancia crece con el saldo pero tiene un minimo`() {
        assertEquals(BalanceVerdict.MATCH, compareBalance(app = 100.0, bank = 109.0).verdict) // minimo de $10
        assertEquals(BalanceVerdict.BANK_HIGHER, compareBalance(app = 100.0, bank = 111.0).verdict)
        assertEquals(BalanceVerdict.MATCH, compareBalance(app = 11173.84, bank = 11200.0).verdict) // 0.5 % de $11,200 = $56
    }

    @Test
    fun `si el banco debe mas lo dice con el monto que falta`() {
        val c = compareBalance(app = 11173.84, bank = 11311.06)
        assertEquals(BalanceVerdict.BANK_HIGHER, c.verdict)
        assertTrue(c.message().contains(formatMxn(137.22)))
    }

    @Test
    fun `si la app suma de mas avisa de un posible duplicado`() {
        val c = compareBalance(app = 6804.07, bank = 5000.0)
        assertEquals(BalanceVerdict.APP_HIGHER, c.verdict)
        assertTrue(c.message().contains("duplicado"))
    }

    @Test
    fun `el saldo acepta cero y rechaza basura`() {
        assertEquals(0.0, parseBankBalance("0")!!, 0.0)
        assertEquals(0.0, parseBankBalance("$0.00")!!, 0.0)
        assertEquals(11311.06, parseBankBalance("$ 11,311.06")!!, 0.0)
        assertNull(parseBankBalance("-5"))
        assertNull(parseBankBalance("abc"))
        assertNull(parseBankBalance(""))
    }
}
