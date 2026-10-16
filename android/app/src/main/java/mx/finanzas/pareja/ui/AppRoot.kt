package mx.finanzas.pareja.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.ui.auth.AuthScreen
import mx.finanzas.pareja.ui.auth.OnboardingScreen
import mx.finanzas.pareja.ui.session.SessionState
import mx.finanzas.pareja.ui.session.SessionViewModel
import mx.finanzas.pareja.ui.update.UpdatePrompt

@Composable
fun AppRoot(repo: FinanceRepository) {
    FinanzasTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val vm: SessionViewModel = viewModel(factory = viewModelFactory { SessionViewModel(repo) })
            val state by vm.state.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()

            when (val s = state) {
                SessionState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
