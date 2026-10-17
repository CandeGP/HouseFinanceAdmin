package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tabla de compras a meses de un estado RappiCard real (texto de PDFBox, solo comercios y montos). */
class StatementParserRappiTest {
    private val text = """
Fecha de corte 15-oct-2026
Periodo 16-sept-2026 al 15-oct-2026
Número de tarjeta **** **** **** 5705
COMPRAS Y CARGOS DIFERIDOS A MESES SIN INTERESES
2026-09- AMAZON A MESES; RFC: ANE140618P37 ${'$'}3,667.85 ${'$'}2,852.77 ${'$'}407.54 2 de 9 0.00%
03
COMPRAS Y CARGOS DIFERIDOS A MESES CON INTERESES
2026-10-07 GUESS; RFC: ${'$'}2,890.00 ${'$'}2,488.83 ${'$'}210.39 ${'$'}31.71 ${'$'}611.56 1 de 6 87.36%
GGU060301LX4
2026-07-14 VET CHRISTIAN GTZ ${'$'}7,455.00 ${'$'}2,841.26 ${'$'}299.86 ${'$'}45.19 ${'$'}1,577.57 4 de 6 87.36%
LOPE; RFC:
GULC830422L60
2026-04-13 ALMUERZO LA ${'$'}1,248.50 ${'$'}336.51 ${'$'}29.44 ${'$'}4.38 ${'$'}183.56 7 de 9 72%
LOCOMOTORA;
""".trimIndent()

    @Test
    fun `lee las compras con y sin intereses con sus montos originales`() {
        val installments = parseStatement(text)!!.installments

        assertEquals(listOf(3667.85, 2890.0, 7455.0, 1248.5), installments.map { it.originalAmount })
        assertEquals(listOf(2, 1, 4, 7), installments.map { it.current })
        assertEquals(listOf(9, 6, 6, 9), installments.map { it.total })
    }

    @Test
    fun `la fecha partida en dos renglones se une`() {
        val amazon = parseStatement(text)!!.installments.first { it.description.startsWith("AMAZON") }
        assertEquals(LocalDate(2026, 9, 3), amazon.purchaseDate)
    }

    @Test
    fun `la descripcion no arrastra montos`() {
        val names = parseStatement(text)!!.installments.map { it.description }
        assertEquals(listOf("AMAZON A MESES", "GUESS", "VET CHRISTIAN GTZ", "ALMUERZO LA"), names)
    }
}
