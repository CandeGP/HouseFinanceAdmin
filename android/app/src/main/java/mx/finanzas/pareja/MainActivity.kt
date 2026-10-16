package mx.finanzas.pareja

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import mx.finanzas.pareja.alerts.AlertRunner
import mx.finanzas.pareja.capture.CaptureSync
import mx.finanzas.pareja.ui.AppRoot

// FragmentActivity (y no solo ComponentActivity): el aviso de huella/PIN del sistema lo exige.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FinanzasApp
        setContent { AppRoot(repo = app.repository) }
    }

    override fun onStart() {
        super.onStart()
        // Reintenta enviar cobros capturados que no pudieron subirse (p. ej. sin red).
        val app = application as FinanzasApp
        lifecycleScope.launch { CaptureSync.flush(app) }
        // Avisa si hay una versión nueva (en silencio si no hay red o ya la descartaste).
        app.updates.check(manual = false)
        // Cortes, pagos y presupuestos superados (también corre cada mañana en segundo plano).
        lifecycleScope.launch { runCatching { AlertRunner.run(app) } }
    }
}
