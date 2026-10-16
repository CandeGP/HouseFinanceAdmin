package mx.finanzas.pareja.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.Member
import mx.finanzas.pareja.data.isConnectivityError
import mx.finanzas.pareja.data.userMessage

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState

    /** Hay sesión guardada pero no se pudo llegar al servidor al abrir: se ofrece reintentar, no volver a entrar. */
    data object Offline : SessionState
    data class NeedsHousehold(val userId: String) : SessionState
    data class Ready(val userId: String, val me: Member, val members: List<Member>) : SessionState
}

private const val MIN_PASSWORD_LENGTH = 6

class SessionViewModel(private val repo: FinanceRepository) : ViewModel() {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = launchAction { /* solo resuelve el estado */ }

    fun signIn(email: String, password: String) {
        when {
            email.isBlank() -> _message.value = "Escribe tu correo"
            password.isEmpty() -> _message.value = "Escribe tu contraseña"
            else -> launchAction { repo.signIn(email.trim(), password) }
        }
    }

    fun signUp(email: String, password: String) {
        when {
            email.isBlank() -> _message.value = "Escribe tu correo"
            password.length < MIN_PASSWORD_LENGTH ->
                _message.value = "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
            else -> launchAction { repo.signUp(email.trim(), password) }
        }
    }

    fun signOut() = launchAction { repo.signOut() }

    fun createHousehold(name: String, displayName: String) {
        when {
            name.isBlank() -> _message.value = "Ponle un nombre al hogar"
            displayName.isBlank() -> _message.value = "Escribe tu nombre"
            else -> launchAction { repo.createHousehold(name.trim(), displayName.trim()) }
        }
    }

    fun acceptInvite(code: String, displayName: String) {
        when {
            code.isBlank() -> _message.value = "Escribe el código de invitación"
            displayName.isBlank() -> _message.value = "Escribe tu nombre"
            else -> launchAction { repo.acceptInvite(code.trim().uppercase(), displayName.trim()) }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    private suspend fun resolve(): SessionState {
        val uid = repo.currentUserId() ?: return SessionState.SignedOut
        val members = repo.members()
        val me = members.firstOrNull { it.userId == uid } ?: return SessionState.NeedsHousehold(uid)
        return SessionState.Ready(uid, me, members.filter { it.householdId == me.householdId })
    }

    // La sesión se lee del almacenamiento local: responde aunque no haya red.
    private suspend fun hasSavedSession(): Boolean = try {
        repo.currentUserId() != null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                action()
                _state.value = resolve()
                _message.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = userMessage(e)
                val starting = _state.value == SessionState.Loading || _state.value == SessionState.Offline
                if (starting) _state.value = if (isConnectivityError(e) && hasSavedSession()) SessionState.Offline else SessionState.SignedOut
            } finally {
                _busy.value = false
            }
        }
    }
}
