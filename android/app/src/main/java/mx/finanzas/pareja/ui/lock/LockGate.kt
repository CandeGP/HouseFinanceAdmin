package mx.finanzas.pareja.ui.lock

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Muestra [content] solo cuando la app está desbloqueada. Si el bloqueo está activado, pide huella o
 * PIN al abrirla desde cero y al volver tras más de 30 segundos fuera. Con la app bloqueada no se dibuja
 * nada de [content], así que ningún dato queda a la vista.
 */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> AppLockState.onStart(LockPrefs.isEffective(context), SystemClock.elapsedRealtime())
                Lifecycle.Event.ON_STOP -> AppLockState.onStop(SystemClock.elapsedRealtime())
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Con el bloqueo apagado nunca se pinta la pantalla de bloqueo (el estado arranca "bloqueado" y, si no,
    // alcanzaba a pedir la huella antes de que ON_START lo desbloqueara).
    if (AppLockState.locked && LockPrefs.isEffective(context)) LockScreen() else content()
}

@Composable
private fun LockScreen() {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    fun unlock() {
        val activity = context.findFragmentActivity() ?: return
        AppAuth.authenticate(
            activity = activity,
            title = "Desbloquear Finanzas en pareja",
            subtitle = "Usa tu huella o el PIN del teléfono",
            onSuccess = { message = null; AppLockState.unlock() },
            onError = { message = it },
        )
    }

    // Pide el desbloqueo en cuanto aparece la pantalla; el botón sirve para reintentar.
    LaunchedEffect(Unit) { unlock() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Finanzas en pareja", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Bloqueada",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Button(onClick = ::unlock) { Text("Desbloquear") }
    }
}
