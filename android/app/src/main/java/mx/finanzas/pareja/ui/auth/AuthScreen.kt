package mx.finanzas.pareja.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mx.finanzas.pareja.ui.components.ErrorText
import mx.finanzas.pareja.ui.session.InviteInfo

private enum class AuthMode { SIGN_IN, CODE, SIGN_UP }

/**
 * Entrada a la app: correo y contraseña con "Entrar" como acción principal. Quien trae un código de invitación
 * lo valida primero y solo entonces se le piden los datos de su cuenta.
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

    fun goTo(next: AuthMode) {
        if (next != AuthMode.CODE) onClearInvite()
        mode = next
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Finanzas en pareja", style = MaterialTheme.typography.headlineMedium)
        Text(
            when {
                mode == AuthMode.SIGN_UP -> "Crea tu cuenta"
                mode == AuthMode.CODE && invite != null -> "Te unirás a «${invite.householdName}». Crea tu cuenta."
                mode == AuthMode.CODE -> "Escribe el código que te compartieron."
                else -> "Inicia sesión para continuar"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Paso 1 del código: se valida antes de pedir cualquier dato.
        if (mode == AuthMode.CODE && invite == null) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Código de invitación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(message)
            Button(onClick = { onCheckInvite(code) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                BusyOr(busy, "Validar código")
            }
            TextButton(onClick = { goTo(AuthMode.SIGN_IN) }, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
            return@Column
        }

        if (mode == AuthMode.CODE) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Tu nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(message)

        Button(
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
                    AuthMode.SIGN_IN -> "Entrar"
                    AuthMode.SIGN_UP -> "Crear cuenta"
                    AuthMode.CODE -> "Crear cuenta y unirme"
                },
            )
        }

        if (mode == AuthMode.SIGN_IN) {
            TextButton(onClick = { goTo(AuthMode.CODE) }, modifier = Modifier.fillMaxWidth()) {
                Text("Tengo un código de invitación", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { goTo(AuthMode.SIGN_UP) }, modifier = Modifier.fillMaxWidth()) {
                Text("Crear una cuenta nueva", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            TextButton(onClick = { goTo(AuthMode.SIGN_IN) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ya tengo cuenta", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BusyOr(busy: Boolean, label: String) {
    if (busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp) else Text(label)
}
