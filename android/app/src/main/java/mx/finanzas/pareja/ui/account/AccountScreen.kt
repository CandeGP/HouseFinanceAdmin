package mx.finanzas.pareja.ui.account

import mx.finanzas.pareja.ui.components.LocalDockInset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import mx.finanzas.pareja.ui.components.GlassCard
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
import mx.finanzas.pareja.ui.components.AppSwitch
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.sp
import mx.finanzas.pareja.ui.AppTheme
import mx.finanzas.pareja.ui.components.GlassCard
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import mx.finanzas.pareja.ui.ThemePrefs
import mx.finanzas.pareja.ui.ThemeStyle
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.lock.AppAuth
import mx.finanzas.pareja.ui.lock.AppLockState
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

    GlassCard(Modifier.fillMaxWidth()) {
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

    GlassCard(Modifier.fillMaxWidth()) {
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
            // Al activarlo no se vuelve a pedir la huella de inmediato: acaba de autenticarse.
            onSuccess = { LockPrefs.setEnabled(context, want); if (want) AppLockState.unlock(); enabled = want; message = null },
            onError = { message = it },
        )
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bloqueo de la app", style = MaterialTheme.typography.titleMedium)
                AppSwitch(checked = enabled, onCheckedChange = { change(it) })
            }
            Text(
                "Pide tu huella o el PIN del teléfono al abrir la app y al volver después de 1 minuto." +
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
    var chargeAlerts by remember { mutableStateOf(AlertPrefs.isChargeAlertsEnabled(context)) }
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

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alertas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AppSwitch(
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Avisar cada cobro", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Una notificación \"Gastaste \$X en Y\" por cada cobro nuevo que la app detecta en tus tarjetas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppSwitch(
                        checked = chargeAlerts,
                        onCheckedChange = { on ->
                            AlertPrefs.setChargeAlertsEnabled(context, on)
                            chargeAlerts = on
                        },
                    )
                }
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

    GlassCard(Modifier.fillMaxWidth()) {
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

/** Muestra de cada apariencia en miniatura (el fondo que tendrá la app). */
private fun previewBrush(style: ThemeStyle): Brush = when (style) {
    ThemeStyle.DARK -> Brush.verticalGradient(listOf(Color(0xFF0A2A4A), Color(0xFF1A1442), Color(0xFF4A1D4D)))
    ThemeStyle.LIGHT -> Brush.verticalGradient(
        0f to Color(0xFF6B4DF0), 0.4f to Color(0xFFB04FD8), 0.4f to Color(0xFFF4F0FF), 1f to Color(0xFFF4F0FF),
    )
    ThemeStyle.MINIMAL -> SolidColor(Color(0xFF0E0F13))
}

/** Apariencia de la app en este teléfono: se aplica al instante y se recuerda. */
@Composable
internal fun AppearanceCard() {
    val context = LocalContext.current
    val current by ThemePrefs.style(context).collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeStyle.entries.forEach { style ->
                    val selected = style == current
                    Column(
                        Modifier.weight(1f).selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { ThemePrefs.set(context, style) },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(84.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(previewBrush(style))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.onSurface else AppTheme.tokens.glassBorder,
                                    RoundedCornerShape(18.dp),
                                ),
                        ) {
                            if (selected) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                                        .clip(CircleShape).background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF1A1442), modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Text(
                            style.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccountScreen(
    vm: AccountViewModel,
    session: SessionState.Ready,
    onSignOut: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenTutorials: () -> Unit,
    onOpenFriends: () -> Unit,
) {
    val inviteCode by vm.inviteCode.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val isAdmin = session.me.role == MemberRole.ADMIN

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp + LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cuenta", style = MaterialTheme.typography.headlineMedium)

        ProfileCard(
            session = session,
            isAdmin = isAdmin,
            inviteCode = inviteCode,
            message = message,
            onInvite = vm::createInvite,
            onCopy = { clipboard.setText(AnnotatedString(it)) },
        )

        SectionLabel("APARIENCIA")
        AppearanceCard()

        SectionLabel("GENERAL")
        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                GroupRow("Amigos y saldos", onOpenFriends)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.tokens.glassBorder)
                GroupRow("Tutoriales", onOpenTutorials)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.tokens.glassBorder)
                GroupRow("Presupuestos por categoría", onOpenBudgets)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.tokens.glassBorder)
                GroupRow("Reportes: mes contra mes", onOpenReports)
            }
        }
        LockCard()
        AlertsCard()
        CaptureCard()
        UnparsedCard()
        VersionCard()

        val danger = MaterialTheme.colorScheme.error
        Surface(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp),
            color = danger.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, danger.copy(alpha = 0.45f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Cerrar sesión", color = danger, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 2.dp, top = 8.dp),
    )
}

/** Fila de la lista GENERAL: título y flecha; toda la fila es el botón. */
@Composable
private fun GroupRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp, colors: List<Color>, fontSize: Int) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Perfil y hogar: tu avatar, cuántos son, los demás integrantes y (si eres administrador) el acceso a invitar. */
@Composable
private fun ProfileCard(
    session: SessionState.Ready,
    isAdmin: Boolean,
    inviteCode: String?,
    message: String?,
    onInvite: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val tokens = AppTheme.tokens
    val gradients = listOf(
        listOf(Color(0xFFFF6FA8), Color(0xFFFFB547)),
        listOf(Color(0xFF3DD6D0), Color(0xFF4F8CFF)),
        listOf(Color(0xFF8B7CFF), Color(0xFFFF6FA8)),
    )
    val me = session.members.firstOrNull { it.userId == session.userId }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(me?.displayName ?: "?", 60.dp, gradients[0], 22)
                Column(Modifier.weight(1f)) {
                    Text(me?.displayName ?: "Tú", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Hogar · ${session.members.size} ${if (session.members.size == 1) "integrante" else "integrantes"}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        session.members.forEachIndexed { index, member ->
                            Box(Modifier.padding(start = if (index == 0) 0.dp else 0.dp).offset(x = (-8 * index).dp)) {
                                Avatar(member.displayName, 26.dp, gradients[index % gradients.size], 11)
                            }
                        }
                    }
                }
                if (isAdmin) {
                    Surface(
                        onClick = onInvite,
                        shape = CircleShape,
                        color = tokens.glass,
                        border = BorderStroke(1.dp, tokens.glassBorder),
                    ) {
                        Box(Modifier.height(40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(if (inviteCode == null) "Invitar" else "Otro código", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            session.members.forEach { member ->
                val role = if (member.role == MemberRole.ADMIN) "administrador" else "integrante"
                val you = if (member.userId == session.userId) " (tú)" else ""
                Text("${member.displayName}$you · $role", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
            }
            if (session.members.size < 2) {
                Text(
                    if (isAdmin) "Falta tu pareja: toca Invitar para generar un código." else "Falta tu pareja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            inviteCode?.let { code ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(code, style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { onCopy(code) }) { Text("Copiar código") }
                }
                Text("El código sirve una sola vez y vence en 7 días.", style = MaterialTheme.typography.bodySmall)
            }
            ErrorText(message)
        }
    }
}
