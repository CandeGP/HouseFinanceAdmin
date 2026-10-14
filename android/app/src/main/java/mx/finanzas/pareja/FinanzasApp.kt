package mx.finanzas.pareja

import android.app.Application
import mx.finanzas.pareja.capture.PendingCaptureStore
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.SupabaseFinanceRepository
import mx.finanzas.pareja.data.createSupabase

class FinanzasApp : Application() {
    val repository: FinanceRepository by lazy {
        SupabaseFinanceRepository(createSupabase(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY))
    }

    val captureStore: PendingCaptureStore by lazy { PendingCaptureStore(this) }
}
