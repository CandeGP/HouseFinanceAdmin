package mx.finanzas.pareja.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import mx.finanzas.pareja.FinanzasApp

private const val WORK_NAME = "cycle-alerts"
private const val NOTIFY_HOUR = 9

/** Una vez al día calcula los cortes y pagos próximos de tus tarjetas y avisa lo que no se avisó. */
class CycleAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            AlertRunner.run(applicationContext as FinanzasApp)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry() // sin red u otro fallo pasajero: WorkManager reintenta con espera
        }
    }
}

object CycleAlertScheduler {
    /** Programa la revisión diaria si las alertas están activas; la cancela si no. */
    fun sync(context: Context) {
        val work = WorkManager.getInstance(context.applicationContext)
        if (!AlertPrefs.isEnabled(context)) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<CycleAlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextRun())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun delayUntilNextRun(): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(NOTIFY_HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
