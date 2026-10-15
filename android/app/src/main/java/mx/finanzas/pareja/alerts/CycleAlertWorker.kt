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
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.FinanzasApp
import mx.finanzas.pareja.domain.ALERT_REMINDER_DAYS
import mx.finanzas.pareja.domain.CardCycles
import mx.finanzas.pareja.domain.reminderKey
import mx.finanzas.pareja.domain.upcomingAlerts

private const val WORK_NAME = "cycle-alerts"
private const val NOTIFY_HOUR = 9

/** Una vez al día calcula los cortes y pagos próximos de tus tarjetas y avisa lo que no se avisó. */
class CycleAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FinanzasApp
        if (!AlertPrefs.isEnabled(app)) return Result.success()

        return try {
            val repo = app.repository
            val userId = repo.currentUserId() ?: return Result.success() // sin sesión no hay nada que avisar
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

            val cycles = repo.cards().filter { it.ownerId == userId && !it.archived }.map { card ->
                val current = repo.cycleEstimate(card.id, today)
                // El pago que sigue a un corte reciente vive en el ciclo anterior.
                val previous = current?.let { repo.cycleEstimate(card.id, it.cycleStart.minus(1, DateTimeUnit.DAY)) }
                CardCycles(card, current, previous)
            }

            upcomingAlerts(cycles, today)
                .filter { it.daysLeft in ALERT_REMINDER_DAYS && !AlertPrefs.wasNotified(app, it.reminderKey) }
                .forEach { alert -> if (AlertNotifier.post(app, alert)) AlertPrefs.markNotified(app, alert.reminderKey) }
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
