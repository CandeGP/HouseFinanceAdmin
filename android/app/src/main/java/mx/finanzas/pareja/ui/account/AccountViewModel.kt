package mx.finanzas.pareja.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.userMessage

class AccountViewModel(
    private val repo: FinanceRepository,
    private val householdId: String,
) : ViewModel() {
    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun createInvite() {
        viewModelScope.launch {
            try {
                _inviteCode.value = repo.createInvite(householdId)
                _message.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = userMessage(e)
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
