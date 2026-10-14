package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeParsersTest {
    private val at = Instant.parse("2026-10-14T17:39:00Z")

    private fun wallet(title: String?, text: String?) =
        RawNotification(ChargeSources.GOOGLE_WALLET, title, text, at)

    private fun plata(title: String?, text: String?) =
        RawNotification(ChargeSources.PLATA, title, text, at)

    // --- Google Wallet (formato observado en notificaciones reales)
    @Test
    fun `wallet extrae comercio, monto y ultimos 4`() {
        val c = parseCharge(wallet("CAFFENIO", "\$127.00 con Plata card ••4321"))!!
        assertEquals("CAFFENIO", c.merchant)
        assertEquals(127.0, c.amount, 0.0)
        assertEquals("4321", c.cardLast4)
        assertEquals(at, c.capturedAt)
        assertEquals(ChargeSources.GOOGLE_WALLET, c.sourcePackage)
    }

    @Test
    fun `wallet con centavos y varias palabras`() {
        val c = parseCharge(wallet("MISC LA ALEMANA SION", "\$406.85 con Plata card ••4321"))!!
        assertEquals(406.85, c.amount, 0.0)
        assertEquals("MISC LA ALEMANA SION", c.merchant)
    }

    @Test
    fun `wallet con asterisco en el comercio`() {
        val c = parseCharge(wallet("MERCADOPAGO *ADRI", "\$85.00 con Plata card ••4321"))!!
        assertEquals("MERCADOPAGO *ADRI", c.merchant)
        assertEquals(85.0, c.amount, 0.0)
    }

    @Test
    fun `wallet con miles y otro banco`() {
        val c = parseCharge(wallet("AMAZON MX", "\$1,234.50 con BBVA Azul ••9876"))!!
        assertEquals(1234.5, c.amount, 0.0)
        assertEquals("9876", c.cardLast4)
    }

    @Test
    fun `wallet ignora textos que no son cobros`() {
        assertNull(parseCharge(wallet("Billetera de Google", "Agregaste una tarjeta nueva")))
        assertNull(parseCharge(wallet("CAFFENIO", null)))
        assertNull(parseCharge(wallet(null, "\$127.00 con Plata card ••4321")))
    }

    @Test
    fun `wallet ignora monedas distintas de pesos`() {
        assertNull(parseCharge(wallet("STEAM", "USD 5.00 con Plata card ••4321")))
    }

    // --- App de Plata
    @Test
    fun `plata quita el emoji del titulo y lee el monto`() {
        val c = parseCharge(plata("Caffenio 🛒", "\$127"))!!
        assertEquals("Caffenio", c.merchant)
        assertEquals(127.0, c.amount, 0.0)
        assertNull(c.cardLast4)
    }

    @Test
    fun `plata con centavos`() {
        val c = parseCharge(plata("Misc La Alemana Sion 🛒", "\$406.85"))!!
        assertEquals("Misc La Alemana Sion", c.merchant)
        assertEquals(406.85, c.amount, 0.0)
    }

    @Test
    fun `plata ignora promociones`() {
        assertNull(parseCharge(plata("Gana cashback", "Usa tu tarjeta este fin de semana")))
    }

    // --- Otros
    @Test
    fun `paquetes no vigilados se ignoran`() {
        assertNull(parseCharge(RawNotification("com.otra.app", "CAFFENIO", "\$127.00 con Plata card ••4321", at)))
    }

    @Test
    fun `los mismos cobros por wallet y plata coinciden al normalizar el comercio`() {
        val w = parseCharge(wallet("MISC LA ALEMANA SION", "\$406.85 con Plata card ••4321"))!!
        val p = parseCharge(plata("Misc La Alemana Sion 🛒", "\$406.85"))!!
        assertEquals(w.merchant.lowercase(), p.merchant.lowercase())
        assertEquals(w.amount, p.amount, 0.0)
    }
}
