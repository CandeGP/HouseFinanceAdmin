package mx.finanzas.pareja.ui.auth

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mx.finanzas.pareja.ui.components.AppLogoMark
import mx.finanzas.pareja.ui.components.AuthField
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.components.GoogleSignInPlaceholder
import mx.finanzas.pareja.ui.components.GradientButton
import mx.finanzas.pareja.ui.session.InviteInfo

private enum class AuthMode { SIGN_IN, CODE, SIGN_UP }

/**
 * Entrada a la app: correo y contraseña con "Iniciar sesión" como acción principal. Quien trae un código de
 * invitación lo valida primero y solo entonces se le piden los datos de su cuenta.
 */
@Composable
fun AuthScreen(
    busy: Boolean,
    message: String?,
    invite: InviteInfo?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onCheckInvite: (code: String) -> Unit,
    onClearInvite: () -> Unit,
    onSignUpWithInvite: (email: String, password: String, displayName: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    fun goTo(next: AuthMode) {
        if (next != AuthMode.CODE) onClearInvite()
        mode = next
    }

    // Resplandores de fondo (violeta arriba, turquesa abajo), dibujados fuera del scroll para que no se muevan.
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            val top = Offset(size.width * 0.95f, 0f)
            val bottom = Offset(0f, size.height)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x808B7CFF), Color.Transparent), center = top, radius = size.width * 0.7f),
                radius = size.width * 0.7f,
                center = top,
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0x593DD6D0), Color.Transparent), center = bottom, radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = bottom,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                // El pie se ancla abajo: la columna llena al menos toda la pantalla.
                .heightIn(min = maxHeight)
                .padding(start = 28.dp, end = 28.dp, top = 44.dp, bottom = 16.dp),
        ) {
            val accent = MaterialTheme.colorScheme.primary
            AppLogoMark(Modifier.align(Alignment.CenterHorizontally))
            Text(
                if (mode == AuthMode.SIGN_IN) "Bienvenido de vuelta" else "Finanzas en pareja",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                when {
                    mode == AuthMode.SIGN_UP -> "Crea tu cuenta"
                    mode == AuthMode.CODE && invite != null -> "Te unirás a «${invite.householdName}». Crea tu cuenta."
                    mode == AuthMode.CODE -> "Escribe el código que te compartieron."
                    else -> "Inicia sesión para ver las finanzas de tu casa."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )

            if (mode == AuthMode.CODE && invite == null) {
                // Paso 1 del código: se valida antes de pedir cualquier dato.
                AuthField("Código de invitación", code, { code = it }, placeholder = "Pega el código")
                ErrorText(message, Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(26.dp))
                GradientButton(onClick = { onCheckInvite(code) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    BusyOr(busy, "Validar código")
                }
            } else {
                if (mode == AuthMode.CODE) {
                    AuthField("Tu nombre", displayName, { displayName = it })
                    Spacer(Modifier.height(14.dp))
                }
                AuthField(
                    label = "Correo",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "tu@correo.com",
                    leadingIcon = Icons.Outlined.Email,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Spacer(Modifier.height(14.dp))
                AuthField(
                    label = "Contraseña",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "••••••••",
                    leadingIcon = Icons.Outlined.Lock,
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )
                ErrorText(message, Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(20.dp))

                GradientButton(
                    onClick = {
                        when (mode) {
                            AuthMode.SIGN_IN -> onSignIn(email, password)
                            AuthMode.SIGN_UP -> onSignUp(email, password)
                            AuthMode.CODE -> onSignUpWithInvite(email, password, displayName)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BusyOr(
                        busy,
                        when (mode) {
                            AuthMode.SIGN_IN -> "Iniciar sesión"
                            AuthMode.SIGN_UP -> "Crear cuenta"
                            AuthMode.CODE -> "Crear cuenta y unirme"
                        },
                    )
                }

                if (mode == AuthMode.SIGN_IN) GoogleSignInPlaceholder()
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))

            // Pie de una línea como en el mockup; cada enlace conserva un área táctil cómoda.
            when {
                mode == AuthMode.SIGN_IN -> {
                    FooterLink(
                        buildAnnotatedString {
                            append("¿Tienes un código de invitación? ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append("Únete") }
                        },
                        onClick = { goTo(AuthMode.CODE) },
                    )
                    FooterLink(
                        buildAnnotatedString { append("Crear una cuenta nueva") },
                        onClick = { goTo(AuthMode.SIGN_UP) },
                        small = true,
                    )
                }
                mode == AuthMode.CODE && invite == null ->
                    FooterLink(buildAnnotatedString { append("Volver") }, onClick = { goTo(AuthMode.SIGN_IN) })
                else ->
                    FooterLink(buildAnnotatedString { append("Ya tengo cuenta") }, onClick = { goTo(AuthMode.SIGN_IN) }, small = true)
            }
        }
    }
}

@Composable
private fun FooterLink(text: AnnotatedString, onClick: () -> Unit, small: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = if (small) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun BusyOr(busy: Boolean, label: String) {
    if (busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp) else Text(label)
}
