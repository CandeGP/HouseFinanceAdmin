package mx.finanzas.pareja.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import mx.finanzas.pareja.ui.components.AppLogoMark
import mx.finanzas.pareja.ui.components.GlassCard
import mx.finanzas.pareja.ui.components.StepHeader
import mx.finanzas.pareja.ui.components.GradientButton
import androidx.compose.material3.MaterialTheme
import mx.finanzas.pareja.ui.components.AuthTextField
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
        StepHeader(step = 2, total = 4)
        AppLogoMark(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
        Text("Configura tu hogar", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (joining) "Pega el código que te compartió tu pareja."
            else "Crea el espacio compartido y luego invita a tu pareja con un código.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ChoiceCard("Crear un hogar nuevo", "Después invitas a tu pareja con un código.", selected = !joining) { joining = false }
        ChoiceCard("Unirme con un código", "Mi pareja ya creó el hogar.", selected = joining) { joining = true }

        AuthTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Tu nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (joining) {
            AuthTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Código de invitación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AuthTextField(
                value = householdName,
                onValueChange = { householdName = it },
                label = { Text("Nombre del hogar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ErrorText(message)

        GradientButton(
            onClick = {
                if (joining) onJoin(code, displayName) else onCreate(householdName, displayName)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (joining) "Unirme" else "Crear hogar") }

        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}

/** Opción elegible del primer uso (crear el hogar o unirse con código); la elegida lleva borde de color. */
@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .semantics { role = Role.RadioButton; this.selected = selected },
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
