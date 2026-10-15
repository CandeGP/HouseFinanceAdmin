package mx.finanzas.pareja.alerts

import android.content.Context

/** Preferencias locales de las alertas: si están activas y qué avisos ya se mostraron. */
object AlertPrefs {
    private const val PREFS = "cycle_alerts"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NOTIFIED = "notified"
    private const val MAX_REMEMBERED = 200

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun wasNotified(context: Context, key: String): Boolean =
        prefs(context).getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().contains(key)

    fun markNotified(context: Context, key: String) {
        val current = prefs(context).getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        // Las claves llevan la fecha; con poco uso nunca llegan al tope, y si llegan se reinicia.
        val next = if (current.size >= MAX_REMEMBERED) setOf(key) else current + key
        prefs(context).edit().putStringSet(KEY_NOTIFIED, next).apply()
    }
}
