package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {
    @Test
    fun `aviso de Rappi con usó y pre-autorizar`() {
        val g = parseReceipt("ENERGETICOS ALTIPLANO usó \$219.08 para pre-autorizar una compra con tu RappiCard digital.")
        assertEquals("ENERGETICOS ALTIPLANO", g.merchant)
        assertEquals(219.08, g.amount!!, 0.0)
        assertNull(g.cardLast4)
    }

    @Test
    fun `captura de Billetera con tarjeta terminada`() {
        val g = parseReceipt("ENERGETICOS ALTIPLANO\n\$219.08 con Rappicard ••8476")
        assertEquals("ENERGETICOS ALTIPLANO", g.merchant)
        assertEquals(219.08, g.amount!!, 0.0)
        assertEquals("8476", g.cardLast4)
    }

    @Test
    fun `ticket toma el TOTAL y no el subtotal ni el IVA`() {
        val g = parseReceipt(
            """
            TIENDA LA ESQUINA
            RFC XAXX010101000
            Fecha: 15/10/2026
            SUBTOTAL  ${'$'}200.00
            IVA 16%  ${'$'}32.00
            TOTAL  ${'$'}232.00
            """.trimIndent(),
        )
        assertEquals("TIENDA LA ESQUINA", g.merchant)
        assertEquals(232.0, g.amount!!, 0.0)
        assertEquals(LocalDate(2026, 10, 15), g.date)
    }

    @Test
    fun `compra a meses detecta comercio, monto, mensualidades y tarjeta`() {
        val g = parseReceipt("Compra en LIVERPOOL por \$5,400.00 a 6 meses sin intereses con tarjeta terminación 9876")
        assertEquals("LIVERPOOL", g.merchant)
        assertEquals(5400.0, g.amount!!, 0.0)
        assertEquals(6, g.installments)
        assertEquals("9876", g.cardLast4)
    }

    @Test
    fun `etiquetas de comercio y monto`() {
        val g = parseReceipt("Comercio: OXXO CHAPULTEPEC\nMonto: \$89.90")
        assertEquals("OXXO CHAPULTEPEC", g.merchant)
        assertEquals(89.90, g.amount!!, 0.0)
    }

    @Test
    fun `fecha con mes escrito completo`() {
        assertEquals(LocalDate(2026, 10, 4), parseReceipt("Pago \$100.00 el 4 de octubre de 2026").date)
    }

    @Test
    fun `sin datos reconocibles devuelve todo vacio`() {
        val g = parseReceipt("hola, esto no es un ticket")
        assertNull(g.amount)
        assertNull(g.merchant)
        assertNull(g.cardLast4)
        assertNull(g.installments)
        assertNull(g.date)
    }

    @Test
    fun `un solo mes no cuenta como compra a meses`() {
        assertNull(parseReceipt("Total \$300.00 a 1 mes").installments)
    }
}
