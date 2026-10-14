package mx.finanzas.pareja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import mx.finanzas.pareja.capture.CaptureSync
import mx.finanzas.pareja.ui.AppRoot

class MainActivity : ComponentActivity() {
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
    }
}
