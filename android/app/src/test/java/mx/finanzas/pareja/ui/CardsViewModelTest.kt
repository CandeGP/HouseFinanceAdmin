package mx.finanzas.pareja.ui

import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.ui.cards.CardsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository().apply {
        cardList = listOf(
            card("activa"),
            card("vieja").copy(archived = true),
        )
    }

    private fun vm() = CardsViewModel(repo, userId = "u1", householdId = "h1")

    @Test
    fun `separa las tarjetas activas de las archivadas`() {
        val s = vm().state.value
        assertEquals(listOf("activa"), s.cards.map { it.id })
        assertEquals(listOf("vieja"), s.archived.map { it.id })
    }

    @Test
    fun `archivar mueve la tarjeta a archivadas sin perderla`() {
        val vm = vm()
        vm.archive("activa")
        val s = vm.state.value
        assertEquals(emptyList<String>(), s.cards.map { it.id })
        assertEquals(setOf("activa", "vieja"), s.archived.map { it.id }.toSet())
    }

    @Test
    fun `restaurar devuelve la tarjeta archivada a las activas`() {
        val vm = vm()
        vm.restore("vieja")
        val s = vm.state.value
        assertEquals(setOf("activa", "vieja"), s.cards.map { it.id }.toSet())
        assertEquals(emptyList<String>(), s.archived.map { it.id })
        assertEquals(listOf("vieja"), repo.restoredCards)
    }
}
