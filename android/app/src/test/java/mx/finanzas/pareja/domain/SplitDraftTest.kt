package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitDraftTest {
    private fun valid(draft: SplitDraft): SplitResult.Valid = draft.validate() as SplitResult.Valid

    @Test
    fun `sin monto de la pareja se divide a la mitad`() {
        val result = valid(SplitDraft(concept = "Cena", totalText = "500"))
        assertEquals(250.0, result.partnerShare, 0.001)
        assertEquals(500.0, result.total, 0.001)
    }

    @Test
    fun `la mitad de un monto impar redondea a centavos`() {
        val result = valid(SplitDraft(concept = "Super", totalText = "100.01"))
        assertEquals(50.01, result.partnerShare, 0.001)
    }

    @Test
    fun `acepta una parte distinta a la mitad`() {
        val result = valid(SplitDraft(concept = "Renta", totalText = "9000", partnerShareText = "3000"))
        assertEquals(3000.0, result.partnerShare, 0.001)
    }

    @Test
    fun `rechaza concepto vacio y monto invalido`() {
        val result = SplitDraft(concept = " ", totalText = "abc").validate() as SplitResult.Invalid
        assertTrue(SplitField.CONCEPT in result.errors)
        assertTrue(SplitField.TOTAL in result.errors)
    }

    @Test
    fun `la parte de la pareja no puede pasar del total`() {
        val result = SplitDraft(concept = "Cena", totalText = "100", partnerShareText = "150").validate() as SplitResult.Invalid
        assertTrue(SplitField.SHARE in result.errors)
    }
}
