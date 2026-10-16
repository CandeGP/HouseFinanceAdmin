package mx.finanzas.pareja.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mx.finanzas.pareja.domain.shouldLock

/**
 * Estado del bloqueo de la app. Vive en un solo objeto por proceso ([AppLockState]): al reiniciarse el
 * proceso arranca bloqueado, y al girar la pantalla (se recrea la actividad) no se pierde ni se reinicia.
 */
class LockController {
    var locked by mutableStateOf(true)
        private set

    private var leftAtMs: Long? = null

    /** La app pasó a primer plano. [enabled] ya incluye que el teléfono pueda autenticar. */
    fun onStart(enabled: Boolean, nowMs: Long) {
        if (!enabled) {
            locked = false
            return
        }
        if (shouldLock(enabled = true, leftAtMs = leftAtMs, nowMs = nowMs)) locked = true
    }

    /** La app salió de primer plano. */
    fun onStop(nowMs: Long) {
        leftAtMs = nowMs
    }

    fun unlock() {
        locked = false
    }
}

val AppLockState = LockController()
