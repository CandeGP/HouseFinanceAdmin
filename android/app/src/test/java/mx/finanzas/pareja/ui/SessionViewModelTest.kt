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
    fun `un codigo inexistente no abre el registro`() {
        val vm = SessionViewModel(repo)
        vm.checkInvite("NOEXISTE")
        assertEquals(null, vm.invite.value)
        assertTrue(vm.message.value!!.contains("código"))
    }

    @Test
    fun `con codigo valido se crea la cuenta y se entra al hogar`() {
        repo.invites["ABC123"] = "Casa"
        val vm = SessionViewModel(repo)
        vm.checkInvite(" abc123 ")
        assertEquals("Casa", vm.invite.value?.householdName)

        vm.signUpWithInvite("a@b.mx", "secreto", "Czesto")

        assertEquals("Czesto", (vm.state.value as SessionState.Ready).me.displayName)
        assertEquals(null, vm.invite.value)
    }

    @Test
    fun `sin validar el codigo no se crea cuenta con invitacion`() {
        val vm = SessionViewModel(repo)
        vm.signUpWithInvite("a@b.mx", "secreto", "Czesto")
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertEquals(null, repo.userId)
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
    fun `sin red al abrir con sesion guardada ofrece reintentar y no pide login`() {
        repo.userId = "u1"
        repo.membersFailWith = java.net.UnknownHostException("Unable to resolve host")
        val vm = SessionViewModel(repo)
        assertEquals(SessionState.Offline, vm.state.value)
        assertEquals("Sin conexión con el servidor", vm.message.value)
        assertEquals("u1", repo.userId)
    }

    @Test
    fun `reintentar con red recupera la sesion`() {
        repo.userId = "u1"
        repo.members += Member("h1", "u1", MemberRole.ADMIN, "Yo")
        repo.membersFailWith = java.net.UnknownHostException("Unable to resolve host")
        val vm = SessionViewModel(repo)

        repo.membersFailWith = null
        vm.refresh()

        assertTrue(vm.state.value is SessionState.Ready)
        assertEquals(null, vm.message.value)
    }

    @Test
    fun `un error que no es de red al abrir si cierra la sesion`() {
        repo.userId = "u1"
        repo.membersFailWith = Exception("JWT expired")
        val vm = SessionViewModel(repo)
        assertEquals(SessionState.SignedOut, vm.state.value)
    }

    @Test
    fun `cerrar sesion vuelve a SignedOut`() {
        repo.userId = "u1"
        val vm = SessionViewModel(repo)
        vm.signOut()
        assertEquals(SessionState.SignedOut, vm.state.value)
    }
}
