package mx.finanzas.pareja.capture

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Notificación de una app vigilada que traía un monto pero no se reconoció como cobro. */
@Serializable
data class UnparsedNotification(
    val packageName: String,
    val title: String?,
    val text: String,
    val postTimeMillis: Long,
)

/**
 * Últimas notificaciones con monto que el parser no reconoció, solo en el teléfono (nunca se
 * envían). Sirven para ver en Cuenta qué formatos faltan y afinar los parsers.
 */
class UnparsedLogStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(UnparsedNotification.serializer())

    @Synchronized
    fun add(item: UnparsedNotification) {
        val current = load()
        if (current.any { it.packageName == item.packageName && it.text == item.text && it.title == item.title }) return
        save((listOf(item) + current).take(MAX_ITEMS))
    }

    @Synchronized
    fun all(): List<UnparsedNotification> = load()

    @Synchronized
    fun clear() = save(emptyList())

    private fun load(): List<UnparsedNotification> =
        runCatching { json.decodeFromString(serializer, prefs.getString(KEY, "[]").orEmpty()) }
            .getOrDefault(emptyList())

    private fun save(items: List<UnparsedNotification>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, items)).apply()
    }

    private companion object {
        const val PREFS = "unparsed_notifications"
        const val KEY = "items"
        const val MAX_ITEMS = 30
    }
}
