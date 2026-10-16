package mx.finanzas.pareja

import android.app.Application
import mx.finanzas.pareja.alerts.CycleAlertScheduler
import mx.finanzas.pareja.capture.PendingCaptureStore
import mx.finanzas.pareja.capture.UnparsedLogStore
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.SupabaseFinanceRepository
import mx.finanzas.pareja.data.createSupabase
import mx.finanzas.pareja.update.UpdateController

class FinanzasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CycleAlertScheduler.sync(this)
    }

    val repository: FinanceRepository by lazy {
        SupabaseFinanceRepository(createSupabase(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY))
    }

    val captureStore: PendingCaptureStore by lazy { PendingCaptureStore(this) }

    val unparsedLog: UnparsedLogStore by lazy { UnparsedLogStore(this) }

    val updates: UpdateController by lazy { UpdateController(this) }
}
