package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Estado Banamex: cada mensualidad viene como un cargo más ("... 011 de 012"), sin tabla de compras a meses. */
class StatementParserBanamexTest {
    private val text = """
Banamex
Fecha de corte: 02-oct-2026
Fecha límite de pago: 26-oct-2026
05-sep-2026 05-sep-2026 MERCADO PAGO 011 de 012 ${'$'}353.94
05-sep-2026 05-sep-2026 D LOCAL*TIK TOK SHOP 003 de 003 ${'$'}113.05
09-sep-2026 09-sep-2026 D LOCAL*TIK TOK SHOP (1 de 9) ${'$'}185.44
17-sep-2026 17-sep-2026 CKT*ELECTR CHEAF TECH CTE 200714PB0MX ${'$'}205.00
07-sep-2026 07-sep-2026 SU PAGO INTERBANCARIO -${'$'}1,261.00
""".trimIndent()

    private val parsed = parseStatement(text)!!

    @Test
    fun `reconoce el banco`() {
        assertEquals("Banamex", parsed.summary.bank)
    }

    @Test
    fun `las mensualidades no cuentan como gasto nuevo y pierden el sufijo`() {
        val charges = parsed.movements.filter { it.isInstallmentCharge }

        assertEquals(listOf("MERCADO PAGO", "D LOCAL*TIK TOK SHOP", "D LOCAL*TIK TOK SHOP"), charges.map { it.description })
    }

    @Test
    fun `un cargo normal y un pago no se confunden con mensualidades`() {
        val cfe = parsed.movements.first { it.description.startsWith("CKT") }
        val pago = parsed.movements.first { it.amount < 0 }

        assertFalse(cfe.isInstallmentCharge)
        assertFalse(pago.isInstallmentCharge)
        assertEquals(1, parsed.movements.count { it.amount < 0 })
    }

    @Test
    fun `reconstruye la compra a meses con monto original igual a mensualidad por meses`() {
        val mp = parsed.installments.first { it.description == "MERCADO PAGO" }

        assertEquals(4247.28, mp.originalAmount, 0.0)
        assertEquals(11, mp.current)
        assertEquals(12, mp.total)
        assertEquals(353.94, mp.requiredPayment, 0.0)
        assertEquals(353.94, mp.pendingBalance, 0.0)
        assertEquals(LocalDate(2026, 9, 5), mp.purchaseDate)
    }

    @Test
    fun `acepta el numero de mensualidad entre parentesis`() {
        val tiktok = parsed.installments.first { it.current == 1 }

        assertEquals(1668.96, tiktok.originalAmount, 0.0)
        assertEquals(9, tiktok.total)
    }

    @Test
    fun `si el estado trae la tabla de meses no se duplica la compra`() {
        val withTable = text + "\n24-feb-2026 MERCADO PAGO ${'$'}4,247.28 ${'$'}353.94 ${'$'}353.94 11 de 12 0.00%"
        val installments = parseStatement(withTable)!!.installments

        assertEquals(1, installments.count { it.description.startsWith("MERCADO PAGO") })
    }
}
