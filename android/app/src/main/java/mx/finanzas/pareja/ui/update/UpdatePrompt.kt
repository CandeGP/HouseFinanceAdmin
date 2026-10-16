package mx.finanzas.pareja.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.finanzas.pareja.BuildConfig
import mx.finanzas.pareja.FinanzasApp
import mx.finanzas.pareja.update.UpdateState

/** Ventanas del flujo de actualización: aviso → descarga → instalar. Va en la raíz de la app. */
@Composable
fun UpdatePrompt() {
    val controller = (LocalContext.current.applicationContext as FinanzasApp).updates
    val state by controller.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("Hay una versión nueva") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Versión ${s.manifest.versionName} (tienes la ${BuildConfig.VERSION_NAME}).")
                    s.manifest.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text("Se descarga aquí mismo y Android te pide confirmar la instalación. Tus datos se conservan.")
                }
            },
            confirmButton = { TextButton(onClick = controller::download) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("Más tarde") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {}, // no se cierra a medias: la descarga es corta
            title = { Text("Descargando ${s.manifest.versionName}…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${s.percent}%")
                }
            },
            confirmButton = {},
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = controller::acknowledge,
            title = { Text("Lista para instalar") },
            text = {
                Text(
                    if (s.needsPermission) {
                        "Android abrió los ajustes: permite a Finanzas instalar aplicaciones, regresa a esta " +
                            "pantalla y toca Instalar."
                    } else {
                        "Verifiqué que el archivo es el publicado. Toca Instalar y confirma en la pantalla de Android."
                    },
                )
            },
            confirmButton = { TextButton(onClick = controller::install) { Text("Instalar") } },
            dismissButton = { TextButton(onClick = controller::acknowledge) { Text("Cancelar") } },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = controller::acknowledge,
            title = { Text("No se pudo actualizar") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = controller::acknowledge) { Text("Entendido") } },
        )

        else -> Unit
    }
}
