package mx.finanzas.pareja.capture

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mx.finanzas.pareja.domain.ParsedCharge
import mx.finanzas.pareja.domain.flushCaptures

/**
 * Cola persistente (SharedPreferences privadas de la app) de cobros detectados que aún no se
 * enviaron al servidor. Se vacía al enviar; lo que falla (sin red) se conserva para reintentar.
 * Un único Mutex serializa todo, lo que además evita que dos notificaciones del mismo cobro
 * (app del banco + Google Wallet) se envíen en paralelo.
 */
class PendingCaptureStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ParsedCharge.serializer())
    private val mutex = Mutex()

    suspend fun enqueue(charge: ParsedCharge) = mutex.withLock {
        save(load() + charge)
    }

    /** Intenta enviar todo lo pendiente; deja en la cola solo lo que falló. */
    suspend fun drain(send: suspend (ParsedCharge) -> Unit) = mutex.withLock {
        val pending = load()
        if (pending.isNotEmpty()) save(flushCaptures(pending, send))
    }

    private fun load(): List<ParsedCharge> =
        runCatching { json.decodeFromString(serializer, prefs.getString(KEY, "[]").orEmpty()) }
            .getOrDefault(emptyList())

    private fun save(items: List<ParsedCharge>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, items)).apply()
    }

    private companion object {
        const val PREFS = "capture_queue"
        const val KEY = "items"
    }
}
