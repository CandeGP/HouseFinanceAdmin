package mx.finanzas.pareja.ui.tutorials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import mx.finanzas.pareja.ui.AppTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.finanzas.pareja.ui.components.GlassCard

/** Guía de consulta: qué hacer y en qué orden. No lleva progreso ni se marca como completada. */
data class Tutorial(val id: String, val title: String, val summary: String, val steps: List<String>)

private val TUTORIALS = listOf(
    Tutorial(
        id = "cobro",
        title = "Registrar un cobro",
        summary = "Anota un gasto en segundos.",
        steps = listOf(
            "Toca el botón + una sola vez.",
            "Escribe el comercio y el monto.",
            "Elige la tarjeta y la categoría.",
            "Toca Guardar. El gasto aparece en Gastos.",
        ),
    ),
    Tutorial(
        id = "ticket",
        title = "Escanear un ticket",
        summary = "Llena el cobro a partir de una foto o captura.",
        steps = listOf(
            "Mantén presionado el botón + hasta que aparezcan las opciones.",
            "Elige Escanear ticket y selecciona la imagen.",
            "Revisa los datos que se llenaron solos y corrige lo que haga falta.",
            "Guarda. La imagen se lee en tu teléfono y no se sube.",
        ),
    ),
    Tutorial(
        id = "tarjeta",
        title = "Registrar una tarjeta",
        summary = "Para ver cuánto debes y cuándo pagar.",
        steps = listOf(
            "Ve a Tarjetas y toca el + de arriba.",
            "Escribe el apodo y los últimos 4 dígitos. Nunca guardamos el número completo.",
            "Indica el día de corte, el día de pago y el límite.",
            "Guarda. En Resumen verás cuánto pagas cada quincena.",
        ),
    ),
    Tutorial(
        id = "pago-tarjeta",
        title = "Pagar una tarjeta",
        summary = "Registra el abono para que tu saldo se actualice.",
        steps = listOf(
            "Mantén presionado el botón + y elige Pago de tarjeta, o entra a Tarjetas.",
            "En la tarjeta que pagaste, toca Registrar pago.",
            "Pon el monto, el método y la fecha.",
            "Guarda. El corte queda marcado como pagado.",
        ),
    ),
    Tutorial(
        id = "pago-efectivo",
        title = "Registrar un pago en efectivo o transferencia",
        summary = "Con su fecha, hora y comprobante.",
        steps = listOf(
            "Mantén presionado el botón + y elige Registrar pago.",
            "Escribe el monto, la fecha y la hora.",
            "Adjunta el comprobante si lo tienes.",
            "Guarda. Lo ves en Gastos, pestaña Pagos.",
        ),
    ),
    Tutorial(
        id = "meses",
        title = "Compras a meses",
        summary = "Mira cuánto pagarás de mensualidades.",
        steps = listOf(
            "Al registrar un gasto, indica en cuántas mensualidades se paga.",
            "Ve a Gastos y abre la pestaña A meses.",
            "Ahí ves el total de cada mes y cuántas mensualidades te faltan.",
        ),
    ),
    Tutorial(
        id = "estado",
        title = "Importar un estado de cuenta",
        summary = "Trae los cargos de tu PDF y evita duplicados.",
        steps = listOf(
            "Ve a Tarjetas y toca Importar estado de cuenta (PDF).",
            "Elige la tarjeta y el archivo.",
            "Revisa los cargos encontrados. Los que ya existen aparecen marcados.",
            "Confirma para importarlos.",
        ),
    ),
    Tutorial(
        id = "captura",
        title = "Cobros detectados automáticamente",
        summary = "La app lee tus avisos de bancos y propone el cobro.",
        steps = listOf(
            "Ve a Cuenta y activa Captura automática de cobros.",
            "Los cargos detectados aparecen en Resumen como cobros por revisar.",
            "Ábrelos y aprueba o descarta cada uno.",
        ),
    ),
    Tutorial(
        id = "apariencia",
        title = "Cambiar la apariencia",
        summary = "Oscuro, claro o minimal.",
        steps = listOf(
            "Ve a Cuenta y busca Apariencia.",
            "Toca el tema que prefieras. Se aplica al instante en este teléfono.",
        ),
    ),
)

private fun tutorialIcon(id: String): ImageVector = when (id) {
    "cobro" -> Icons.Outlined.Receipt
    "ticket" -> Icons.Outlined.CameraAlt
    "tarjeta", "pago-tarjeta" -> Icons.Outlined.CreditCard
    "pago-efectivo" -> Icons.Outlined.Payments
    "meses" -> Icons.Outlined.CalendarMonth
    "estado" -> Icons.Outlined.UploadFile
    "captura" -> Icons.Outlined.Email
    else -> Icons.Outlined.Palette
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialsScreen(onBack: () -> Unit) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tutoriales") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Guías rápidas para que sepas cómo hacer cada cosa. Tócalas para ver los pasos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(TUTORIALS, key = { it.id }) { tutorial ->
                val open = openId == tutorial.id
                GlassCard(Modifier.fillMaxWidth(), onClick = { openId = if (open) null else tutorial.id }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val tint = AppTheme.tokens.chart[TUTORIALS.indexOf(tutorial) % AppTheme.tokens.chart.size]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(tutorialIcon(tutorial.id), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(tutorial.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    tutorial.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (open) "Ocultar pasos" else "Ver pasos",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (open) {
                            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tutorial.steps.forEachIndexed { index, step ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
