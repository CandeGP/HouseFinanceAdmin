package mx.finanzas.pareja.capture

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import mx.finanzas.pareja.FinanzasApp
import mx.finanzas.pareja.data.TxSource
import mx.finanzas.pareja.domain.ChargeSources
import mx.finanzas.pareja.domain.RawNotification
import mx.finanzas.pareja.domain.parseCharge

/**
 * Lee las notificaciones de las apps vigiladas (Billetera de Google y Plata) y convierte los
 * cobros en transacciones pendientes de revisión. Solo se inspeccionan los paquetes de
 * [ChargeSources.WATCHED]; cualquier otra notificación se ignora sin leerse.
 */
class CaptureService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in ChargeSources.WATCHED) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val charge = parseCharge(
            RawNotification(sbn.packageName, title, text, Instant.fromEpochMilliseconds(sbn.postTime)),
        ) ?: return

        val app = application as FinanzasApp
        scope.launch {
            app.captureStore.enqueue(charge)
            CaptureSync.flush(app)
        }
    }
}

/** Envía al servidor las capturas pendientes (si hay sesión y hogar). Seguro de llamar varias veces. */
object CaptureSync {
    suspend fun flush(app: FinanzasApp) {
        val repo = app.repository
        val userId = runCatching { repo.currentUserId() }.getOrNull() ?: return
        val householdId = runCatching { repo.members() }.getOrNull()
            ?.firstOrNull { it.userId == userId }?.householdId ?: return

        app.captureStore.drain { charge ->
            repo.ingestCapture(
                householdId = householdId,
                merchant = charge.merchant,
                amount = charge.amount,
                capturedAt = charge.capturedAt,
                cardLast4 = charge.cardLast4,
                source = TxSource.NOTIFICATION,
                raw = charge.raw,
            )
        }
    }

    /** ¿Tiene esta app el permiso de "Acceso a notificaciones"? */
    fun isListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
