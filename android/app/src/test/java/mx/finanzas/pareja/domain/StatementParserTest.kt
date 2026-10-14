package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures con la estructura real de cada banco pero datos ficticios
 * (montos, comercios y números inventados).
 */
class StatementParserTest {

    private val rappi = """
        Estado de cuenta
        Tarjeta de crédito RappiCard
        Número de tarjeta **** **** **** 1111
        TU PAGO REQUERIDO ESTE PERIODO
        Periodo 22-ago-2026 al 21-sept-2026
        Fecha de corte 21-sept-2026
        Fecha límite de pago1 lunes, 12-oct-2026
        Pago para no generar intereses2 ${'$'}3,862.32
        Pago mínimo + compras y cargos
        diferidos a meses3
        ${'$'}425.00
        Pago mínimo4 ${'$'}300.00
        El pago mínimo ${'$'}300.00 12 meses ${'$'}869.76
        Límite de crédito ${'$'}34,000.00
        DESGLOSE DE MOVIMIENTOS
        Fecha de la
        operación Fecha cargo Descripción del movimiento Monto
        2026-08-24 2026-08-25 DLO*UBER RIDE; RFC: DME180122DU4 +${'$'}70.00
        2026-09-04 2026-09-05 CAFFENIO; RFC: SAO030421M70 +${'$'}90.00
        2026-09-09 2026-09-09 PAGO POR SPEI -${'$'}6,071.12
    """.trimIndent()

    private val plata = """
        Estado de cuenta MENSUAL
        Tarjeta de Crédito Clásica Internacional
        Número de Tarjeta 531722XXXXXX0931
        Periodo 19-ago-2026 al 18-sep-2026
        Fecha de corte 18-sep-2026
        Fecha límite de pago 1 lun, 19-oct-2026
        Pago para no generar intereses 2 ${'$'}3,075.07
        Pago mínimo 4 ${'$'}200.00
        Límite de crédito ${'$'}15,000.00
        Cashback de Plata Card tiene un valor equivalente
        Desglose de movimientos
        Compras y cargos diferidos a meses sin intereses Tarjeta titular [##########]
        Fecha de la
        operación Descripción Monto original Saldo pendiente Pago requerido Núm. de pago Tasa de interés aplicable
        22-jun-2026 TIENDA EJEMPLO LEON ${'$'}2,034.00 ${'$'}678.00 ${'$'}678.00 2 de 3 0%
        Cargos, abonos y compras regulares (no a meses) Tarjeta titular [##########]
        18-ago-2026 19-ago-2026 5841 PAYPAL *EJEMPLO TWITCH + 69.60 MXN 1.00 + ${'$'}69.60
        04-sep-2026 05-sep-2026 5841 PP*EJEMPLO FACEBOOK + 79.00 MXN 1.00 + ${'$'}79.00
    """.trimIndent()

    private val bbva = """
        TU PAGO REQUERIDO ESTE PERIODO
        Periodo: 24-ago-2026 al 23-sep-2026
        Fecha de corte: 23-sep-2026
        Fecha límite de pago:1  martes, 13-oct-2026
        Pago para no generar  intereses:2 ${'$'}1,408.00
        Pago mínimo + compras y cargo diferidos a meses:3 ${'$'}2,564.25
        Pago mínimo:4 ${'$'}1,156.25
        Límite de crédito ${'$'}40,000.00
        BBVA México, S.A.
        Número de cuenta: XXXXXX5436 Página 2 de 6
        COMPRAS Y CARGOS DIFERIDOS A MESES SIN INTERESES Tarjeta titular: XXXXXXXXXXXX5436
        26-may-2026 TIENDA A MESES ; Tarjeta Digital ***4223 ${'$'}12,666.23 ${'$'}7,034.23 ${'$'}1,408.00 4 de 9 0.00%
        CARGOS,COMPRAS Y ABONOS REGULARES(NO A MESES) Tarjeta titular: XXXXXXXXXXXX5436
        09-sep-2026 09-sep-2026 BMOVIL.PAGO TDC - ${'$'}1,452.40
        23-sep-2026 24-sep-2026 04 DE 09 TIENDA A MESES ; Tarjeta Digital ***4223 + ${'$'}1,408.00
        15-sep-2026 16-sep-2026 RESTAURANTE EJEMPLO + ${'$'}350.50
    """.trimIndent()

    private val nu = """
        Producto: Tarjeta de Crédito Nu, Oro
        Número de tarjeta: XXXX-XXXX-XXXX-2222
        Periodo: 12 SEP 2026 al 11 OCT 2026
        Fecha de corte: 11 OCT 2026
        Fecha límite de pago1: Jueves, 22 OCT 2026
        Pago para no generar intereses2: ${'$'}4,568.48
        Pago mínimo + compras y
        cargos diferidos a meses3: ${'$'}1,453.84
        Pago mínimo4: ${'$'}287.50
        Límite de crédito ${'$'}22,000.00
        COMPRAS Y CARGOS DIFERIDOS A MESES SIN INTERESES
        26 SEP 2026 D Local*Tda Ejemplo Mx - 1/6 ${'$'}6,998.00 ${'$'}5,831.66 ${'$'}1,166.34 1/6 0.00%
        CARGOS, ABONOS Y COMPRAS REGULARES (NO A MESES)
        05 SEP 2026 12 SEP 2026 Abono de "Tienda A Meses" | RFC: S.I. -${'$'}400.58
        Tarjeta virtual **** 3974
        16 SEP 2026 18 SEP 2026 Google G1sk005m | RFC: S.I. +${'$'}59.00
    """.trimIndent()

    // ------------------------------------------------------------ fechas
    @Test fun `fecha ISO`() = assertEquals(LocalDate(2026, 8, 24), parseStatementDate("2026-08-24"))
    @Test fun `fecha dd-mmm-yyyy`() = assertEquals(LocalDate(2026, 9, 18), parseStatementDate("18-sep-2026"))
    @Test fun `fecha con sept`() = assertEquals(LocalDate(2026, 9, 21), parseStatementDate("21-sept-2026"))
    @Test fun `fecha con espacios y mayusculas`() = assertEquals(LocalDate(2026, 10, 11), parseStatementDate("11 OCT 2026"))
    @Test fun `fecha invalida`() = assertNull(parseStatementDate("31-feb-2026"))
    @Test fun `mes desconocido`() = assertNull(parseStatementDate("10-xyz-2026"))

    // ------------------------------------------------------------ resumen
    @Test
    fun `rappi resumen`() {
        val s = parseStatement(rappi)!!.summary
        assertEquals("Rappi", s.bank)
        assertEquals("1111", s.cardLast4)
        assertEquals(LocalDate(2026, 8, 22), s.periodStart)
        assertEquals(LocalDate(2026, 9, 21), s.periodEnd)
        assertEquals(LocalDate(2026, 9, 21), s.cutoffDate)
        assertEquals(LocalDate(2026, 10, 12), s.dueDate)
        assertEquals(34_000.0, s.creditLimit!!, 0.0)
        assertEquals(3862.32, s.payNoInterest!!, 0.0)
        assertEquals(300.0, s.minimumPayment!!, 0.0)
    }

    @Test
    fun `plata resumen con tarjeta enmascarada`() {
        val s = parseStatement(plata)!!.summary
        assertEquals("Plata", s.bank)
        assertEquals("0931", s.cardLast4)
        assertEquals(LocalDate(2026, 9, 18), s.cutoffDate)
        assertEquals(LocalDate(2026, 10, 19), s.dueDate)
        assertEquals(15_000.0, s.creditLimit!!, 0.0)
        assertEquals(200.0, s.minimumPayment!!, 0.0)
    }

    @Test
    fun `bbva resumen con numero de cuenta`() {
        val s = parseStatement(bbva)!!.summary
        assertEquals("BBVA", s.bank)
        assertEquals("5436", s.cardLast4)
        assertEquals(LocalDate(2026, 9, 23), s.cutoffDate)
        assertEquals(LocalDate(2026, 10, 13), s.dueDate)
        assertEquals(1156.25, s.minimumPayment!!, 0.0)
        assertEquals(1408.0, s.payNoInterest!!, 0.0)
    }

    @Test
    fun `nu resumen con fechas en mayusculas`() {
        val s = parseStatement(nu)!!.summary
        assertEquals("Nu", s.bank)
        assertEquals("2222", s.cardLast4)
        assertEquals(LocalDate(2026, 10, 11), s.cutoffDate)
        assertEquals(LocalDate(2026, 10, 22), s.dueDate)
        assertEquals(287.5, s.minimumPayment!!, 0.0)
    }

    @Test
    fun `sin fecha de corte no es un estado de cuenta`() {
        assertNull(parseStatement("Hola, este PDF no es un estado de cuenta"))
    }

    // ------------------------------------------------------------ movimientos
    @Test
    fun `rappi movimientos con signo y sin RFC`() {
        val m = parseStatement(rappi)!!.movements
        assertEquals(3, m.size)
        assertEquals("DLO*UBER RIDE", m[0].description)
        assertEquals(70.0, m[0].amount, 0.0)
        assertEquals(LocalDate(2026, 8, 24), m[0].operationDate)
        assertEquals(LocalDate(2026, 8, 25), m[0].chargeDate)
        assertEquals(-6071.12, m[2].amount, 0.0)
    }

    @Test
    fun `plata movimientos con tarjeta y sin la columna de cambio`() {
        val m = parseStatement(plata)!!.movements
        assertEquals(2, m.size)
        assertEquals("PAYPAL *EJEMPLO TWITCH", m[0].description)
        assertEquals("5841", m[0].cardLast4)
        assertEquals(69.6, m[0].amount, 0.0)
    }

    @Test
    fun `bbva distingue pagos, mensualidades y compras`() {
        val m = parseStatement(bbva)!!.movements
        assertEquals(3, m.size)
        assertEquals(-1452.40, m[0].amount, 0.0)
        assertFalse(m[0].isInstallmentCharge)
        assertTrue(m[1].isInstallmentCharge)
        assertEquals("4223", m[1].cardLast4)
        assertFalse(m[2].isInstallmentCharge)
        assertEquals(350.5, m[2].amount, 0.0)
    }

    @Test
    fun `nu usa la linea siguiente como tarjeta virtual`() {
        val m = parseStatement(nu)!!.movements
        assertEquals(2, m.size)
        assertEquals(-400.58, m[0].amount, 0.0)
        assertEquals("3974", m[0].cardLast4)
        assertNull(m[1].cardLast4)
        assertEquals(59.0, m[1].amount, 0.0)
    }

    // ------------------------------------------------------------ MSI
    @Test
    fun `plata msi con n de m`() {
        val i = parseStatement(plata)!!.installments.single()
        assertEquals(LocalDate(2026, 6, 22), i.purchaseDate)
        assertEquals("TIENDA EJEMPLO LEON", i.description)
        assertEquals(2034.0, i.originalAmount, 0.0)
        assertEquals(678.0, i.pendingBalance, 0.0)
        assertEquals(678.0, i.requiredPayment, 0.0)
        assertEquals(2, i.current)
        assertEquals(3, i.total)
    }

    @Test
    fun `bbva msi conserva la tarjeta digital en la descripcion limpia`() {
        val i = parseStatement(bbva)!!.installments.single()
        assertEquals(12666.23, i.originalAmount, 0.0)
        assertEquals(4, i.current)
        assertEquals(9, i.total)
    }

    @Test
    fun `nu msi con formato n slash m`() {
        val i = parseStatement(nu)!!.installments.single()
        assertEquals(LocalDate(2026, 9, 26), i.purchaseDate)
        assertEquals(6998.0, i.originalAmount, 0.0)
        assertEquals(1, i.current)
        assertEquals(6, i.total)
    }

    @Test
    fun `la linea de msi no se confunde con un movimiento regular`() {
        assertNotNull(parseStatement(bbva))
        assertEquals(3, parseStatement(bbva)!!.movements.size)
    }
}
