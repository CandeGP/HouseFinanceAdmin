package mx.finanzas.pareja.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import mx.finanzas.pareja.FinanzasApp
import mx.finanzas.pareja.capture.CaptureSync
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.data.MemberRole
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.session.SessionState

/** Estado y activación del permiso de acceso a notificaciones (captura automática de cobros). */
@Composable
private fun CaptureCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(CaptureSync.isListenerEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = CaptureSync.isListenerEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Captura automática de cobros", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "Activa: se leen las notificaciones de Billetera de Google y de tus apps de bancos."
                else "Desactivada. Actívala para registrar cobros desde Billetera de Google y tus apps de bancos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!enabled) {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Activar acceso a notificaciones") }
            }
        }
    }
}

/** Avisos de bancos con monto que el parser aún no entiende; permiten afinar formatos reales. */
@Composable
private fun UnparsedCard() {
    val context = LocalContext.current
    val log = remember { (context.applicationContext as FinanzasApp).unparsedLog }
    var items by remember { mutableStateOf(log.all()) }
    val clipboard = LocalClipboardManager.current

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notificaciones no reconocidas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Avisos de tus bancos con un monto que la app aún no sabe leer. Solo se guardan en este teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isEmpty()) {
                Text("Ninguna por ahora.", style = MaterialTheme.typography.bodyMedium)
            } else {
                items.take(MAX_UNPARSED_SHOWN).forEach { n ->
                    Column {
                        Text(
                            n.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(listOfNotNull(n.title, n.text).joinToString(" — "), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val text = items.joinToString("\n") { "${it.packageName} | ${it.title} | ${it.text}" }
                        clipboard.setText(AnnotatedString(text))
                    }) { Text("Copiar") }
                    TextButton(onClick = { log.clear(); items = emptyList() }) { Text("Limpiar") }
                }
            }
        }
    }
}

private const val MAX_UNPARSED_SHOWN = 10

@Composable
fun AccountScreen(
    vm: AccountViewModel,
    session: SessionState.Ready,
    onSignOut: () -> Unit,
) {
    val inviteCode by vm.inviteCode.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val isAdmin = session.me.role == MemberRole.ADMIN

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cuenta", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Integrantes del hogar", style = MaterialTheme.typography.titleMedium)
                session.members.forEach { member ->
                    val role = if (member.role == MemberRole.ADMIN) "administrador" else "integrante"
                    val you = if (member.userId == session.userId) " (tú)" else ""
                    Text("${member.displayName}$you · $role")
                }
                if (session.members.size < 2) {
                    Text(
                        "Falta tu pareja: comparte un código de invitación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (isAdmin) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Invitar a tu pareja", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "El código sirve una sola vez y vence en 7 días.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    inviteCode?.let { code ->
                        Text(code, style = MaterialTheme.typography.headlineSmall)
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copiar código") }
                    }
                    Button(onClick = vm::createInvite) {
                        Text(if (inviteCode == null) "Generar código" else "Generar otro")
                    }
                    ErrorText(message)
                }
            }
        }

        CaptureCard()
        UnparsedCard()

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}
