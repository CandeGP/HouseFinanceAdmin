package mx.finanzas.pareja.ui.duplicates

import android.content.Context

/** Pares marcados "No es duplicado" en este dispositivo. */
object DismissedDuplicates {
    private const val PREFS = "dismissed_duplicates"
    private const val KEY = "keys"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Set<String> = prefs(context).getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun save(context: Context, keys: Set<String>) {
        prefs(context).edit().putStringSet(KEY, keys).apply()
    }
}
