package mx.finanzas.pareja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import mx.finanzas.pareja.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as FinanzasApp).repository
        setContent { AppRoot(repo = repository) }
    }
}
