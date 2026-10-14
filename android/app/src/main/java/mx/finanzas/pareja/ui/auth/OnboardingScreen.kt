package mx.finanzas.pareja.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import mx.finanzas.pareja.ui.components.ErrorText

/** Primer uso: crear el hogar compartido o unirse con el código de la pareja. */
@Composable
fun OnboardingScreen(
    busy: Boolean,
    message: String?,
    onCreate: (householdName: String, displayName: String) -> Unit,
    onJoin: (code: String, displayName: String) -> Unit,
    onSignOut: () -> Unit,
) {
    var joining by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var householdName by rememberSaveable { mutableStateOf("Nuestro hogar") }
    var code by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Configura tu hogar", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (joining) "Pega el código que te compartió tu pareja."
            else "Crea el espacio compartido y luego invita a tu pareja con un código.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Tu nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (joining) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Código de invitación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = householdName,
                onValueChange = { householdName = it },
                label = { Text("Nombre del hogar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ErrorText(message)

        Button(
            onClick = {
                if (joining) onJoin(code, displayName) else onCreate(householdName, displayName)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (joining) "Unirme" else "Crear hogar") }

        TextButton(onClick = { joining = !joining }, modifier = Modifier.fillMaxWidth()) {
            Text(if (joining) "Mejor crear un hogar nuevo" else "Tengo un código de invitación")
        }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}
