package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRowsTest {
    private fun line(text: String, left: Int, top: Int, height: Int = 20) =
        OcrLine(text, left = left, top = top, right = left + text.length * 10, bottom = top + height)

    @Test
    fun `une en un renglon fecha, descripcion y monto de la misma altura`() {
        val rows = joinOcrRows(
            listOf(
                line("\$429.00", left = 900, top = 100),
                line("14 SEP", left = 50, top = 103),
                line("MERCADOPAGO", left = 200, top = 98),
            ),
        )
        assertEquals("14 SEP  MERCADOPAGO  \$429.00", rows)
    }

    @Test
    fun `ordena los renglones de arriba hacia abajo`() {
        val rows = joinOcrRows(
            listOf(
                line("SEGUNDO", left = 50, top = 200),
                line("PRIMERO", left = 50, top = 100),
            ),
        )
        assertEquals("PRIMERO\nSEGUNDO", rows)
    }

    @Test
    fun `renglones cercanos pero distintos no se mezclan`() {
        val rows = joinOcrRows(
            listOf(
                line("A", left = 50, top = 100),
                line("B", left = 50, top = 130),
            ),
        )
        assertEquals("A\nB", rows)
    }

    @Test
    fun `sin lineas devuelve texto vacio`() {
        assertEquals("", joinOcrRows(emptyList()))
    }

    @Test
    fun `ignora lineas en blanco`() {
        val rows = joinOcrRows(listOf(line("   ", left = 10, top = 10), line("OXXO", left = 50, top = 10)))
        assertEquals("OXXO", rows)
    }
}
