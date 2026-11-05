package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardEditDraftTest {
    private val base = CardDraft(
        name = "Plata", bank = "Plata", last4 = "0931", limitText = "15000", cutoffText = "18", dueText = "19",
    )

    private fun valid(d: CardEditDraft) = (d.validate() as CardEditResult.Valid).value
    private fun invalid(d: CardEditDraft) = (d.validate() as CardEditResult.Invalid).errors

    @Test
    fun `debito no pide limite, corte ni pago y guarda valores neutros`() {
        val v = valid(CardEditDraft(CardDraft(name = "Nómina", bank = "BBVA", last4 = "1111", kind = "debit"))).card
        assertEquals("debit", v.kind)
        assertEquals(0.0, v.creditLimit, 0.0)
        assertEquals(1, v.cutoffDay)
        assertEquals(1, v.dueDay)
    }

    @Test
    fun `credito si exige limite, corte y pago`() {
        val errors = invalid(CardEditDraft(CardDraft(name = "BBVA Azul", bank = "BBVA", last4 = "5436")))
        assertNotNull(errors[CardField.LIMIT])
        assertNotNull(errors[CardField.CUTOFF])
        assertNotNull(errors[CardField.DUE])
    }

    @Test
    fun `solo se guardan las marcas visa, mastercard y amex y un tipo raro cae a credito`() {
        assertEquals("amex", valid(CardEditDraft(base.copy(brand = "amex"))).card.brand)
        assertNull(valid(CardEditDraft(base.copy(brand = "diners"))).card.brand)
        assertEquals("credit", valid(CardEditDraft(base.copy(kind = "efectivo"))).card.kind)
    }

    @Test
    fun `el cashback se conserva al validar y por defecto esta apagado`() {
        assertEquals(true, valid(CardEditDraft(base, hasCashback = true)).hasCashback)
        assertEquals(false, valid(CardEditDraft(base)).hasCashback)
    }

    @Test
    fun `una edicion completa es valida y normaliza el color`() {
        val v = valid(CardEditDraft(base, budgetText = "\$2,000", color = "#375390"))
        assertEquals(2000.0, v.monthlyBudget!!, 0.0)
        assertEquals("#375390", v.color)
        assertEquals(18, v.card.cutoffDay)
        assertEquals(15000.0, v.card.creditLimit, 0.0)
    }

    @Test
    fun `el color en minusculas se guarda en mayusculas`() {
        assertEquals("#0F9D8A", valid(CardEditDraft(base, color = "#0f9d8a")).color)
    }

    @Test
    fun `presupuesto vacio significa sin presupuesto`() {
        assertNull(valid(CardEditDraft(base, budgetText = "  ")).monthlyBudget)
    }

    @Test
    fun `color vacio o nulo significa automatico`() {
        assertNull(valid(CardEditDraft(base, color = null)).color)
        assertNull(valid(CardEditDraft(base, color = " ")).color)
    }

    @Test
    fun `presupuesto invalido o en cero se rechaza`() {
        assertNotNull(invalid(CardEditDraft(base, budgetText = "mucho"))[CardField.BUDGET])
        assertNotNull(invalid(CardEditDraft(base, budgetText = "0"))[CardField.BUDGET])
    }

    @Test
    fun `color invalido se rechaza`() {
        assertNotNull(invalid(CardEditDraft(base, color = "azul"))[CardField.COLOR])
        assertNotNull(invalid(CardEditDraft(base, color = "#12345"))[CardField.COLOR])
    }

    @Test
    fun `los errores de la tarjeta base se conservan junto a los de presupuesto`() {
        val errors = invalid(CardEditDraft(base.copy(cutoffText = "40", dueText = ""), budgetText = "x"))
        assertTrue(errors.keys.containsAll(listOf(CardField.CUTOFF, CardField.DUE, CardField.BUDGET)))
    }
}
