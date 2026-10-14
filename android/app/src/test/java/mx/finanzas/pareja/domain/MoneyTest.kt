package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun `formatea pesos mexicanos con separador de miles y dos decimales`() {
        assertEquals("\$1,234.50", formatMxn(1234.5))
    }

    @Test
    fun `formatea cero`() {
        assertEquals("\$0.00", formatMxn(0.0))
    }

    @Test
    fun `redondea a centavos`() {
        assertEquals("\$10.00", formatMxn(9.999))
    }
}
