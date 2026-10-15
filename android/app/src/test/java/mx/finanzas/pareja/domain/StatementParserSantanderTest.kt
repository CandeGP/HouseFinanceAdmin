package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Estructura real de un estado de Santander escaneado y leído con OCR (con cifras y tarjeta
 * inventadas): meses mal leídos (0ct, Qct), basura en los bordes (|, 1VA), cargos sin signo y
 * descripciones partidas en dos líneas.
 */
class StatementParserSantanderTest {
    private val text = """
Santander
TU PAGO REQUERIDO ESTE PERIODO
37260 LEON DE LOS ALDAMA, GUANAJUATO 37001  Periodo:  05-Sep-2026 al 04-0ct-2026
Fecha de corte:  04-0ct-2026
Fecha Iímite de pago:  Lunes, 26-0ct-2026
SANTANDER LIKEU VISA ORO
Numero de tarjeta: 4941 3430 02349999  Pago para no generar intereses?  $ 1,500.00
Número de cliente: 53333354  Pago mínimo + compras y cargos
diferidos a meses
Pago mínimo:+  $ 500.00
Límite de crédito:  $ 50,000.00
31-Mar-2026  AMAZON A MESES 12M S/INT  $7,000.00  $ 3,000.00  $600.00  07 DE 12  0%
29-May-2026  MERCADO PAGO 18M S/INT  $ 30,000.00  $ 21,000.00  $ 1,600.00  05 DE 18  0%
CARGOS, ABONOs Y COMPRAS REGULARES (NO A MESES)
Tarjeta titular 49413430023499 99
02-Qct-2026  02-Oct-2026 MERCADO PAGO  ${'$'} 1,600.00
02-Oct-2026 02-Oct-2026 AMAZON A MESES  ${'$'}600.00
ADMINSTRACION TARJETA DEL
|02-Oct-2026 02-Oct-2026  TITULAR  ${'$'} 150.00
02-Oct-2026 02-0ct-2026 |1VA. 16%  +  ${'$'} 24.00|
Total de cargos  +  ${'$'} 2,374.00
""".trimIndent()

    @Test
    fun `lee el mes que el OCR confunde con cero o con Q`() {
        assertEquals(LocalDate(2026, 10, 4), parseStatementDate("04-0ct-2026"))
        assertEquals(LocalDate(2026, 10, 2), parseStatementDate("02-Qct-2026"))
        assertEquals(LocalDate(2026, 10, 26), parseStatementDate("26-Oct-2026"))
    }

    @Test
    fun `encuentra corte, periodo y limite de pago`() {
        val s = parseStatement(text)!!.summary
        assertEquals(LocalDate(2026, 10, 4), s.cutoffDate)
        assertEquals(LocalDate(2026, 9, 5), s.periodStart)
        assertEquals(LocalDate(2026, 10, 4), s.periodEnd)
        assertEquals(LocalDate(2026, 10, 26), s.dueDate)
    }

    @Test
    fun `identifica banco, tarjeta y montos de cabecera`() {
        val s = parseStatement(text)!!.summary
        assertEquals("Santander", s.bank)
        assertEquals("9999", s.cardLast4)
        assertEquals(50_000.0, s.creditLimit!!, 0.0)
        assertEquals(1_500.0, s.payNoInterest!!, 0.0)
        assertEquals(500.0, s.minimumPayment!!, 0.0)
    }

    @Test
    fun `lee la tabla de compras a meses`() {
        val installments = parseStatement(text)!!.installments
        assertEquals(2, installments.size)
        val amazon = installments.first { it.description.startsWith("AMAZON") }
        assertEquals(7, amazon.current)
        assertEquals(12, amazon.total)
        assertEquals(600.0, amazon.requiredPayment, 0.0)
        assertEquals(LocalDate(2026, 3, 31), amazon.purchaseDate)
    }

    @Test
    fun `cargos sin signo son cargos y se unen las descripciones partidas`() {
        val movements = parseStatement(text)!!.movements
        assertEquals(4, movements.size)
        val fee = movements.first { it.description.contains("ADMINSTRACION") }
        assertEquals("ADMINSTRACION TARJETA DEL TITULAR", fee.description)
        assertEquals(150.0, fee.amount, 0.0)
        val iva = movements.first { it.description.contains("IVA") }
        assertEquals("IVA. 16%", iva.description)
        assertEquals(24.0, iva.amount, 0.0)
    }

    @Test
    fun `las mensualidades ya listadas en la tabla de MSI no se cuentan como gasto nuevo`() {
        val movements = parseStatement(text)!!.movements
        val mercado = movements.first { it.description.startsWith("MERCADO PAGO") }
        val amazon = movements.first { it.description.startsWith("AMAZON") }
        assertTrue(mercado.isInstallmentCharge)
        assertTrue(amazon.isInstallmentCharge)
        assertFalse(movements.first { it.description.contains("ADMINSTRACION") }.isInstallmentCharge)
    }

    @Test
    fun `un cargo con el mismo monto pero otro comercio no se confunde con una mensualidad`() {
        val extra = text + "\n02-Oct-2026 02-Oct-2026 OXXO CENTRO  \$600.00"
        val oxxo = parseStatement(extra)!!.movements.first { it.description.startsWith("OXXO") }
        assertFalse(oxxo.isInstallmentCharge)
    }

    @Test
    fun `texto sin fecha de corte no es un estado compatible`() {
        assertNull(parseStatement("Hola mundo \$ 100.00"))
        assertNotNull(parseStatement(text))
    }
}
