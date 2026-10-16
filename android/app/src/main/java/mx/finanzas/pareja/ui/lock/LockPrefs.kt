package mx.finanzas.pareja.ui.lock

import android.content.Context

/** Si el bloqueo con huella o PIN está activado. Es una preferencia de este teléfono (no se sincroniza). */
object LockPrefs {
    private const val PREFS = "app_lock"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Si de verdad hay que bloquear: está activado Y el teléfono puede autenticar. Si el teléfono ya no
     * tiene huella ni PIN, no se bloquea (mejor sin candado que encerrarte fuera de tus propios datos).
     */
    fun isEffective(context: Context): Boolean = isEnabled(context) && AppAuth.isAvailable(context)
}
