package mx.finanzas.pareja.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.ui.auth.AuthScreen
import mx.finanzas.pareja.ui.auth.FirstRunFlow
import mx.finanzas.pareja.ui.auth.OnboardingScreen
import mx.finanzas.pareja.ui.auth.WelcomePrefs
import mx.finanzas.pareja.ui.auth.WelcomeScreen
import mx.finanzas.pareja.ui.lock.LockGate
import mx.finanzas.pareja.ui.session.SessionState
import mx.finanzas.pareja.ui.session.SessionViewModel
import mx.finanzas.pareja.ui.update.UpdatePrompt

private val MAX_CONTENT_WIDTH = 600.dp

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
    val style by ThemePrefs.style(LocalContext.current).collectAsStateWithLifecycle()
    FinanzasTheme(style) {
        // Transparente a propósito: el degradado de la apariencia elegida se pinta aquí y las pantallas lo dejan ver.
        Surface(
            modifier = Modifier.fillMaxSize().background(AppTheme.tokens.background),
            color = Color.Transparent,
        ) {
            val context = LocalContext.current
            var welcomeSeen by remember { mutableStateOf(WelcomePrefs.seen(context)) }
            var firstRun by rememberSaveable { mutableStateOf(false) }
            var openTutorials by remember { mutableStateOf(false) }
            val vm: SessionViewModel = viewModel(factory = viewModelFactory { SessionViewModel(repo) })
            val state by vm.state.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()

            // Con el bloqueo activado, nada de esto se dibuja hasta desbloquear con huella o PIN.
            LockGate {
                // En pantallas anchas (tableta, plegable abierto) el contenido se queda en una columna centrada.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize()) {
                when (val s = state) {
                    SessionState.Loading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    SessionState.Offline -> OfflineScreen(busy = busy, onRetry = vm::refresh)
                    SessionState.SignedOut -> {
                        val invite by vm.invite.collectAsStateWithLifecycle()
                        if (welcomeSeen) {
                            AuthScreen(
                                busy = busy,
                                message = message,
                                invite = invite,
                                onSignIn = vm::signIn,
                                onSignUp = vm::signUp,
                                onCheckInvite = vm::checkInvite,
                                onClearInvite = vm::clearInvite,
                                onSignUpWithInvite = vm::signUpWithInvite,
                            )
                        } else {
                            val dismiss = { WelcomePrefs.markSeen(context); welcomeSeen = true }
                            WelcomeScreen(onStart = dismiss, onSignIn = dismiss)
                        }
                    }
                    is SessionState.NeedsHousehold -> {
                        // Quien crea su hogar en esta sesión pasa luego por la configuración inicial.
                        LaunchedEffect(Unit) { firstRun = true }
                        OnboardingScreen(
                            busy = busy,
                            message = message,
                            onCreate = vm::createHousehold,
                            onJoin = vm::acceptInvite,
                            onSignOut = vm::signOut,
                        )
                    }
                    is SessionState.Ready ->
                        if (firstRun) {
                            FirstRunFlow(repo, s.userId, s.me.householdId, s.me.displayName.substringBefore(' ')) { tutorials ->
                                firstRun = false
                                openTutorials = tutorials
                            }
                        } else {
                            MainScaffold(repo = repo, session = s, onSignOut = vm::signOut, openTutorials = openTutorials)
                        }
                }
                }
                }
                UpdatePrompt()
            }
        }
    }
}
