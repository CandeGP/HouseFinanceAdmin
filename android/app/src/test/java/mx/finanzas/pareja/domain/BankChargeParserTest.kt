package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankChargeParserTest {
    private val at = Instant.parse("2026-10-15T18:00:00Z")

    private fun bank(pkg: String, title: String?, text: String?) = RawNotification(pkg, title, text, at)

    @Test
    fun `compra de BBVA con terminacion`() {
        val c = parseCharge(
            bank("com.bancomer.mbanking", "BBVA", "Compra por \$1,234.50 en AMAZON MX con tu tarjeta terminación 9876"),
        )!!
        assertEquals("AMAZON MX", c.merchant)
        assertEquals(1234.50, c.amount, 0.0)
        assertEquals("9876", c.cardLast4)
    }

    @Test
    fun `compra de Santander con asteriscos`() {
        val c = parseCharge(
            bank("mx.bancosantander.supermovil", "Santander", "Se realizó una compra por \$350.00 en OXXO CHAPULTEPEC con tu tarjeta ****1234."),
        )!!
        assertEquals("OXXO CHAPULTEPEC", c.merchant)
        assertEquals(350.0, c.amount, 0.0)
        assertEquals("1234", c.cardLast4)
    }

    @Test
    fun `compra aprobada de Nu`() {
        val c = parseCharge(
            bank("com.nu.production", "Compra aprobada", "Compra de \$127.00 en CAFFENIO con tu tarjeta de crédito terminada en 4321"),
        )!!
        assertEquals("CAFFENIO", c.merchant)
        assertEquals(127.0, c.amount, 0.0)
        assertEquals("4321", c.cardLast4)
    }

    @Test
    fun `pagaste de Rappi sin ultimos 4`() {
        val c = parseCharge(bank("com.grability.rappi", "RappiCard", "Pagaste \$89.90 en STARBUCKS con tu RappiCard"))!!
        assertEquals("STARBUCKS", c.merchant)
        assertEquals(89.90, c.amount, 0.0)
        assertNull(c.cardLast4)
    }

    @Test
    fun `usa el titulo cuando el texto no trae comercio`() {
        val c = parseCharge(bank("com.nu.production", "Uber Eats", "Cargo de \$212.00 aprobado"))!!
        assertEquals("Uber Eats", c.merchant)
    }

    @Test
    fun `sin comercio identificable usa texto neutro`() {
        val c = parseCharge(bank("com.nu.production", "Compra aprobada", "Cargo de \$212.00"))!!
        assertEquals("Sin comercio", c.merchant)
    }

    @Test
    fun `ignora abonos, depositos y promociones`() {
        assertNull(parseCharge(bank("com.bancomer.mbanking", "BBVA", "Recibiste un depósito por \$5,000.00")))
        assertNull(parseCharge(bank("com.nu.production", "Nu", "Abono de \$250.00 a tu tarjeta")))
        assertNull(parseCharge(bank("com.nu.production", "Gana cashback", "Hasta \$500 de regalo por tu primera compra")))
    }

    @Test
    fun `ignora monedas distintas de MXN`() {
        assertNull(parseCharge(bank("com.bancomer.mbanking", "BBVA", "Compra por USD 5.00 en STEAM con tarjeta terminación 1111")))
    }

    @Test
    fun `wallet cae al parser generico si el formato estricto no aplica`() {
        val c = parseCharge(
            RawNotification(ChargeSources.GOOGLE_WALLET, "Billetera de Google", "Pagaste \$75.00 en FARMACIA GUADALAJARA", at),
        )!!
        assertEquals("FARMACIA GUADALAJARA", c.merchant)
    }

    @Test
    fun `reconoce bancos por paquete conocido o por nombre de la app`() {
        assertTrue(ChargeSources.isWatched("com.bancomer.mbanking"))
        assertTrue(ChargeSources.isWatched("com.nu.production"))
        assertTrue(ChargeSources.isWatched("mx.cualquiera.app", appLabel = "Banorte Móvil"))
        assertTrue(ChargeSources.isWatched("com.x.y", appLabel = "HSBC México"))
        assertFalse(ChargeSources.isWatched("com.whatsapp", appLabel = "WhatsApp"))
        assertFalse(ChargeSources.isWatched("com.spotify.music", appLabel = "Spotify"))
    }
}
