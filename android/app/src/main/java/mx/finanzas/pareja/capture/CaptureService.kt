package mx.finanzas.pareja.capture

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.BundleCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import mx.finanzas.pareja.FinanzasApp
import mx.finanzas.pareja.alerts.AlertRunner
import mx.finanzas.pareja.data.TxSource
import mx.finanzas.pareja.domain.ChargeSources
import mx.finanzas.pareja.domain.RawNotification
import mx.finanzas.pareja.domain.parseCharge

/**
 * Lee las notificaciones de Billetera de Google y de apps de bancos/fintech (paquetes conocidos o
 * nombre de app bancario, ver [ChargeSources.isWatched]) y convierte los cobros en transacciones
 * pendientes de revisión. Cualquier otra app se ignora sin leerse.
 */
class CaptureService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        if (!ChargeSources.isWatched(sbn.packageName, appLabel(extras))) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val app = application as FinanzasApp

        val charge = parseCharge(
            RawNotification(sbn.packageName, title, text, Instant.fromEpochMilliseconds(sbn.postTime)),
        )
        if (charge == null) {
            // Con monto pero sin reconocer: se guarda solo en el teléfono para afinar parsers.
            if (text != null && text.contains('$')) {
                app.unparsedLog.add(UnparsedNotification(sbn.packageName, title, text, sbn.postTime))
            }
            return
        }

        scope.launch {
            app.captureStore.enqueue(charge)
            CaptureSync.flush(app)
        }
    }

    /** Nombre visible de la app emisora; viene en los extras, así no hace falta visibilidad de paquetes. */
    private fun appLabel(extras: Bundle): String? {
        val info = BundleCompat.getParcelable(extras, EXTRA_APP_INFO, ApplicationInfo::class.java)
        return info?.let { runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull() }
    }

    private companion object {
        const val EXTRA_APP_INFO = "android.appInfo"
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
        // Un cobro nuevo puede hacer que se supere un presupuesto: se revisa en el momento.
        runCatching { AlertRunner.run(app) }
    }

    /** ¿Tiene esta app el permiso de "Acceso a notificaciones"? */
    fun isListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
