package mx.finanzas.pareja.domain

import mx.finanzas.pareja.card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeAlertsTest {
    private val rappi = card("c1", name = "Rappi").copy(bank = "Banorte", last4 = "8476")

    @Test
    fun `avisa el monto y el comercio del cobro`() {
        val alert = chargeAlert(NewCharge("tx1", "ENERGETICOS ALTIPLANO", 219.08, rappi))

        assertEquals(AlertKind.CHARGE, alert.kind)
        assertEquals("Gastaste ${formatMxn(219.08)} en ENERGETICOS ALTIPLANO", alert.title)
        assertTrue(alert.text.contains("••8476"))
        assertTrue(alert.text.contains("bandeja"))
        assertEquals(219.08, alert.amount!!, 0.0)
        assertEquals("c1", alert.cardId)
    }

    @Test
    fun `sin comercio reconocido solo dice el monto`() {
        assertEquals("Gastaste ${formatMxn(50.0)}", chargeAlert(NewCharge("tx2", "Sin comercio", 50.0, null)).title)
        assertEquals("Gastaste ${formatMxn(50.0)}", chargeAlert(NewCharge("tx3", "   ", 50.0, null)).title)
    }

    @Test
    fun `sin tarjeta identificada lo dice y pide revisarlo`() {
        val alert = chargeAlert(NewCharge("tx4", "Oxxo", 35.0, null))
        assertEquals("", alert.cardId)
        assertTrue(alert.text.contains("tarjeta", ignoreCase = true))
        assertTrue(alert.text.contains("bandeja"))
    }

    @Test
    fun `la llave es estable por cobro para no repetir el aviso`() {
        assertEquals("charge:tx1", chargeAlert(NewCharge("tx1", "A", 1.0, null)).key)
        assertEquals(chargeAlert(NewCharge("tx1", "A", 1.0, null)).key, chargeAlert(NewCharge("tx1", "B", 2.0, rappi)).key)
    }

    @Test
    fun `solo se avisan los cobros nuevos, no los duplicados`() {
        val results = listOf(
            NewCharge("tx1", "Oxxo", 35.0, null) to false,
            NewCharge("tx2", "Oxxo", 35.0, null) to true, // el mismo cobro llegó por Wallet y por el banco
        )
        assertEquals(listOf("charge:tx1"), chargeAlertsFor(results).map { it.key })
    }
}
