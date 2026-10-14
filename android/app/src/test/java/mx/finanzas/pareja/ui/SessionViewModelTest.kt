package mx.finanzas.pareja.ui

import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.data.Member
import mx.finanzas.pareja.data.MemberRole
import mx.finanzas.pareja.ui.session.SessionState
import mx.finanzas.pareja.ui.session.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository()

    @Test
    fun `sin usuario la sesion esta cerrada`() {
        val vm = SessionViewModel(repo)
        assertEquals(SessionState.SignedOut, vm.state.value)
    }

    @Test
    fun `usuario sin hogar necesita crear o unirse a uno`() {
        repo.userId = "u1"
        val vm = SessionViewModel(repo)
        assertEquals(SessionState.NeedsHousehold("u1"), vm.state.value)
    }

    @Test
    fun `usuario con hogar queda listo con sus miembros`() {
        repo.userId = "u1"
        repo.members += Member("h1", "u1", MemberRole.ADMIN, "Yo")
        repo.members += Member("h1", "u2", MemberRole.MEMBER, "Pareja")
        val state = SessionViewModel(repo).state.value as SessionState.Ready
        assertEquals("Yo", state.me.displayName)
        assertEquals(2, state.members.size)
    }

    @Test
    fun `iniciar sesion exitoso avanza al siguiente estado`() {
        val vm = SessionViewModel(repo)
        vm.signIn("yo@test.mx", "secreto1")
        assertEquals(SessionState.NeedsHousehold("u1"), vm.state.value)
    }

    @Test
    fun `iniciar sesion fallido muestra mensaje en espanol y no avanza`() {
        repo.failWith = Exception("Invalid login credentials")
        val vm = SessionViewModel(repo)
        vm.signIn("yo@test.mx", "malo")
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertEquals("Correo o contraseña incorrectos", vm.message.value)
    }

    @Test
    fun `correo vacio no llega al servidor`() {
        val vm = SessionViewModel(repo)
        vm.signIn("  ", "secreto1")
        assertEquals("Escribe tu correo", vm.message.value)
        assertEquals(null, repo.userId)
    }

    @Test
    fun `registro exige contrasena de al menos 6 caracteres`() {
        val vm = SessionViewModel(repo)
        vm.signUp("yo@test.mx", "123")
        assertTrue(vm.message.value!!.contains("6"))
        assertEquals(null, repo.userId)
    }

    @Test
    fun `crear hogar deja la sesion lista como admin`() {
        repo.userId = "u1"
        val vm = SessionViewModel(repo)
        vm.createHousehold("Casa", "Yo")
        val state = vm.state.value as SessionState.Ready
        assertEquals(MemberRole.ADMIN, state.me.role)
    }

    @Test
    fun `aceptar invitacion deja la sesion lista como miembro`() {
        repo.userId = "u1"
        val vm = SessionViewModel(repo)
        vm.acceptInvite("abc123xyz0", "Pareja")
        val state = vm.state.value as SessionState.Ready
        assertEquals(MemberRole.MEMBER, state.me.role)
    }

    @Test
    fun `cerrar sesion vuelve a SignedOut`() {
        repo.userId = "u1"
        val vm = SessionViewModel(repo)
        vm.signOut()
        assertEquals(SessionState.SignedOut, vm.state.value)
    }
}
