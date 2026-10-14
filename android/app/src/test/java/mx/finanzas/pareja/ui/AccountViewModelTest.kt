package mx.finanzas.pareja.ui

import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.ui.account.AccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository()

    @Test
    fun `sin invitacion generada no hay codigo`() {
        assertNull(AccountViewModel(repo, "h1").inviteCode.value)
    }

    @Test
    fun `generar invitacion expone el codigo`() {
        val vm = AccountViewModel(repo, "h1")
        vm.createInvite()
        assertEquals("ABC123XYZ0", vm.inviteCode.value)
    }

    @Test
    fun `un error al invitar se muestra en espanol`() {
        val failing = object : mx.finanzas.pareja.data.FinanceRepository by repo {
            override suspend fun createInvite(householdId: String): String = throw Exception("only an admin can invite")
        }
        val vm = AccountViewModel(failing, "h1")
        vm.createInvite()
        assertEquals("Solo el administrador puede invitar", vm.message.value)
    }
}
