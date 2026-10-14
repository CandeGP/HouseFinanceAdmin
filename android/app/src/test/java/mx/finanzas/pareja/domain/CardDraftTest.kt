package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardDraftTest {
    private fun draft(
        name: String = "Nu",
        bank: String = "Nu México",
        last4: String = "4321",
        limit: String = "\$25,000",
        cutoff: String = "15",
        due: String = "5",
    ) = CardDraft(name, bank, last4, limit, cutoff, due)

    private fun invalid(d: CardDraft) = (d.validate() as CardDraftResult.Invalid).errors
    private fun valid(d: CardDraft) = (d.validate() as CardDraftResult.Valid).value

    @Test
    fun `tarjeta valida normaliza los datos`() {
        val v = valid(draft(name = " Nu ", limit = "\$25,000.50"))
        assertEquals("Nu", v.name)
        assertEquals(25_000.5, v.creditLimit, 0.0)
        assertEquals(15, v.cutoffDay)
        assertEquals(5, v.dueDay)
    }

    @Test fun `nombre vacio es invalido`() = assertNotNull(invalid(draft(name = " "))[CardField.NAME])
    @Test fun `banco vacio es invalido`() = assertNotNull(invalid(draft(bank = ""))[CardField.BANK])
    @Test fun `ultimos 4 deben ser digitos`() = assertNotNull(invalid(draft(last4 = "12a4"))[CardField.LAST4])
    @Test fun `ultimos 4 deben ser exactamente 4`() = assertNotNull(invalid(draft(last4 = "123"))[CardField.LAST4])
    @Test fun `limite debe ser positivo`() = assertNotNull(invalid(draft(limit = "0"))[CardField.LIMIT])
    @Test fun `corte 0 es invalido`() = assertNotNull(invalid(draft(cutoff = "0"))[CardField.CUTOFF])
    @Test fun `corte 32 es invalido`() = assertNotNull(invalid(draft(cutoff = "32"))[CardField.CUTOFF])
    @Test fun `pago 31 es valido`() = assertEquals(31, valid(draft(due = "31")).dueDay)
    @Test fun `pago vacio es invalido`() = assertNotNull(invalid(draft(due = ""))[CardField.DUE])
}
