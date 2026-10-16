package mx.finanzas.pareja.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import mx.finanzas.pareja.MainActivity
import mx.finanzas.pareja.R
import mx.finanzas.pareja.domain.AlertKind
import mx.finanzas.pareja.domain.CycleAlert

/** Publica las alertas de corte y pago como notificaciones locales. */
object AlertNotifier {
    private const val CHANNEL_ID = "cycle_alerts"

    // Los avisos de cada cobro van en su propio canal para poder silenciarlos aparte desde Android.
    private const val CHARGES_CHANNEL_ID = "charge_alerts"

    /** ¿Puede la app mostrar notificaciones? En Android 13+ hace falta el permiso en tiempo de ejecución. */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Devuelve false si no se pudo mostrar (sin permiso). */
    @SuppressLint("MissingPermission") // canNotify() ya comprobó el permiso
    fun post(context: Context, alert: CycleAlert): Boolean {
        if (!canNotify(context)) return false
        val channelId = if (alert.kind == AlertKind.CHARGE) CHARGES_CHANNEL_ID else CHANNEL_ID
        ensureChannel(context, channelId)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_house)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(alert.key.hashCode(), notification)
        return true
    }

    private fun ensureChannel(context: Context, channelId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) != null) return
        val (name, text) = if (channelId == CHARGES_CHANNEL_ID) {
            "Cobros detectados" to "Un aviso por cada cobro que la app detecta en tus tarjetas"
        } else {
            "Alertas de corte y pago" to "Avisos de la fecha de corte y de pago de tus tarjetas y de tus presupuestos"
        }
        manager.createNotificationChannel(
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT).apply { description = text },
        )
    }
}
