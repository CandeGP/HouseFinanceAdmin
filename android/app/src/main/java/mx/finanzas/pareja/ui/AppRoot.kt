package mx.finanzas.pareja.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.ui.auth.AuthScreen
import mx.finanzas.pareja.ui.auth.OnboardingScreen
import mx.finanzas.pareja.ui.lock.LockGate
import mx.finanzas.pareja.ui.session.SessionState
import mx.finanzas.pareja.ui.session.SessionViewModel
import mx.finanzas.pareja.ui.update.UpdatePrompt

@Composable
private fun OfflineScreen(busy: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sin conexión con el servidor", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tu sesión sigue guardada. Revisa tu internet y vuelve a intentar.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onRetry, enabled = !busy) { Text(if (busy) "Conectando…" else "Reintentar") }
    }
}

@Composable
fun AppRoot(repo: FinanceRepository) {
    FinanzasTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val vm: SessionViewModel = viewModel(factory = viewModelFactory { SessionViewModel(repo) })
            val state by vm.state.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()

            // Con el bloqueo activado, nada de esto se dibuja hasta desbloquear con huella o PIN.
            LockGate {
                when (val s = state) {
                    SessionState.Loading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    SessionState.Offline -> OfflineScreen(busy = busy, onRetry = vm::refresh)
                    SessionState.SignedOut ->
                        AuthScreen(busy = busy, message = message, onSignIn = vm::signIn, onSignUp = vm::signUp)
                    is SessionState.NeedsHousehold ->
                        OnboardingScreen(
                            busy = busy,
                            message = message,
                            onCreate = vm::createHousehold,
                            onJoin = vm::acceptInvite,
                            onSignOut = vm::signOut,
                        )
                    is SessionState.Ready -> MainScaffold(repo = repo, session = s, onSignOut = vm::signOut)
                }
                UpdatePrompt()
            }
        }
    }
}
