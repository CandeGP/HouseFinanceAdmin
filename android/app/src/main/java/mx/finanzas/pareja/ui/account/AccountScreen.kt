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
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import mx.finanzas.pareja.alerts.AlertNotifier
import mx.finanzas.pareja.alerts.AlertPrefs
import mx.finanzas.pareja.alerts.CycleAlertScheduler
import mx.finanzas.pareja.domain.AlertKind
import mx.finanzas.pareja.domain.CycleAlert
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
import mx.finanzas.pareja.BuildConfig
import mx.finanzas.pareja.FinanzasApp
import mx.finanzas.pareja.capture.CaptureSync
import mx.finanzas.pareja.update.UpdateState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.data.MemberRole
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.lock.AppAuth
import mx.finanzas.pareja.ui.lock.LockPrefs
import mx.finanzas.pareja.ui.lock.findFragmentActivity
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

/** Versión instalada y búsqueda manual de actualizaciones (el aviso automático sale al abrir la app). */
@Composable
private fun VersionCard() {
    val controller = (LocalContext.current.applicationContext as FinanzasApp).updates
    val state by controller.state.collectAsStateWithLifecycle()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text(
                when (val s = state) {
                    UpdateState.Checking -> "Buscando actualizaciones…"
                    UpdateState.UpToDate -> "Estás al día."
                    is UpdateState.Available -> "Hay una versión nueva: ${s.manifest.versionName}."
                    else -> "Las actualizaciones se descargan aquí mismo; Android te pide confirmar la instalación."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { controller.check(manual = true) },
                enabled = state != UpdateState.Checking,
            ) { Text("Buscar actualizaciones") }
        }
    }
}

/** Activa o desactiva los avisos de corte y de pago de tus tarjetas. */
/** Interruptor del bloqueo con huella o PIN: activarlo o apagarlo pide autenticarse primero. */
@Composable
private fun LockCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(LockPrefs.isEnabled(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val available = remember { AppAuth.isAvailable(context) }

    fun change(want: Boolean) {
        if (want && !available) {
            message = "Primero configura una huella o un PIN en los ajustes del teléfono."
            return
        }
        val activity = context.findFragmentActivity() ?: return
        AppAuth.authenticate(
            activity = activity,
            title = if (want) "Activar el bloqueo" else "Apagar el bloqueo",
            subtitle = "Confirma que eres tú",
            onSuccess = { LockPrefs.setEnabled(context, want); enabled = want; message = null },
            onError = { message = it },
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bloqueo de la app", style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = { change(it) })
            }
            Text(
                "Pide tu huella o el PIN del teléfono al abrir la app y al volver después de 30 segundos. " +
                    "Los avisos y la captura de cobros siguen funcionando con la app bloqueada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled && !available) {
                Text(
                    "Este teléfono ya no tiene huella ni PIN configurados: la app no se bloqueará hasta que los configures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ErrorText(message)
        }
    }
}

@Composable
private fun AlertsCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AlertPrefs.isEnabled(context)) }
    var denied by remember { mutableStateOf(false) }

    fun apply(value: Boolean) {
        AlertPrefs.setEnabled(context, value)
        enabled = value
        CycleAlertScheduler.sync(context)
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        apply(granted)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alertas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        when {
                            !on -> apply(false)
                            AlertNotifier.canNotify(context) -> apply(true)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> denied = true
                        }
                    },
                )
            }
            Text(
                "Corte y pago de tus tarjetas: 3 días antes, un día antes y el mismo día (no avisa de pagos ya " +
                    "registrados). También avisa al llegar al 80% y al pasarse del presupuesto de una tarjeta " +
                    "(se fija tocándola) o de una categoría.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (denied) {
                Text(
                    "Activa las notificaciones de la app en los ajustes del teléfono para recibir los avisos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (enabled) {
                OutlinedButton(onClick = {
                    AlertNotifier.post(
                        context,
                        CycleAlert(
                            key = "prueba", kind = AlertKind.DUE, cardId = "", daysLeft = 3,
                            title = "Prueba: pago de tu tarjeta vence en 3 días",
                            text = "Así se verá tu aviso con el monto estimado y la fecha límite.",
                            amount = null,
                        ),
                    )
                }) { Text("Enviar aviso de prueba") }
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
    onOpenBudgets: () -> Unit,
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

        OutlinedButton(onClick = onOpenBudgets, modifier = Modifier.fillMaxWidth()) {
            Text("Presupuestos por categoría")
        }
        LockCard()
        AlertsCard()
        CaptureCard()
        UnparsedCard()
        VersionCard()

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}
