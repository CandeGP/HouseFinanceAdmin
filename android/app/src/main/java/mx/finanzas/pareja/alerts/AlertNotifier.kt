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
import mx.finanzas.pareja.domain.CycleAlert

/** Publica las alertas de corte y pago como notificaciones locales. */
object AlertNotifier {
    private const val CHANNEL_ID = "cycle_alerts"

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
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alertas de corte y pago", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Avisos de la fecha de corte y de pago de tus tarjetas" },
        )
    }
}
