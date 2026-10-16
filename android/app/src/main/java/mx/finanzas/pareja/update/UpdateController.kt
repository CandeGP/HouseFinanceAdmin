package mx.finanzas.pareja.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.finanzas.pareja.BuildConfig
import mx.finanzas.pareja.domain.UpdateManifest
import mx.finanzas.pareja.domain.isUpdateAvailable
import mx.finanzas.pareja.domain.parseUpdateManifest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val percent: Int) : UpdateState

    /** `needsPermission`: falta permitir a Finanzas instalar apps; ya se abrieron los ajustes. */
    data class Ready(val manifest: UpdateManifest, val file: File, val needsPermission: Boolean = false) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Busca versiones nuevas en el bucket público, las descarga, verifica su SHA-256 y abre el instalador.
 * Android exige que la persona confirme la instalación: no hay forma de hacerlo en silencio.
 */
class UpdateController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val releasesBase = BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/public/releases/"
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** `manual`: lo pidió la persona (muestra "estás al día" o el error); si no, falla en silencio. */
    fun check(manual: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            try {
                val text = UpdateIo.fetchText("${releasesBase}latest.json?t=${System.currentTimeMillis()}")
                val manifest = parseUpdateManifest(text, releasesBase)
                _state.value = when {
                    manifest == null -> if (manual) UpdateState.Failed(MSG_CHECK) else UpdateState.Idle
                    !isUpdateAvailable(BuildConfig.VERSION_CODE, manifest) ->
                        if (manual) UpdateState.UpToDate else UpdateState.Idle
                    !manual && prefs.getInt(KEY_DISMISSED, 0) >= manifest.versionCode -> UpdateState.Idle
                    else -> UpdateState.Available(manifest)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = if (manual) UpdateState.Failed(MSG_CHECK) else UpdateState.Idle
            }
        }
    }

    /** "Más tarde": no vuelve a molestar por esta versión; sí por una más nueva o al pedirlo a mano. */
    fun dismiss() {
        (_state.value as? UpdateState.Available)?.let {
            prefs.edit().putInt(KEY_DISMISSED, it.manifest.versionCode).apply()
        }
        _state.value = UpdateState.Idle
    }

    fun download() {
        val manifest = (_state.value as? UpdateState.Available)?.manifest ?: return
        job = scope.launch {
            val file = File(File(appContext.cacheDir, UPDATES_DIR), "finanzas-${manifest.versionName}.apk")
            try {
                _state.value = UpdateState.Downloading(manifest, 0)
                UpdateIo.download(manifest.url, file) { percent ->
                    _state.value = UpdateState.Downloading(manifest, percent)
                }
                val hash = withContext(Dispatchers.IO) { UpdateIo.sha256(file) }
                if (hash != manifest.sha256) {
                    file.delete()
                    _state.value = UpdateState.Failed("El archivo descargado no coincide con el publicado. No se instaló.")
                } else {
                    _state.value = UpdateState.Ready(manifest, file)
                }
            } catch (e: CancellationException) {
                file.delete()
                throw e
            } catch (_: Exception) {
                file.delete()
                _state.value = UpdateState.Failed("No se pudo descargar la actualización. Revisa tu conexión.")
            }
        }
    }

    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            // Primera vez: Android pide permitir "instalar apps desconocidas" para esta app.
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _state.value = ready.copy(needsPermission = true)
            return
        }
        appContext.startActivity(UpdateIo.installIntent(appContext, ready.file))
        _state.value = UpdateState.Idle
    }

    fun acknowledge() {
        _state.value = UpdateState.Idle
    }

    private companion object {
        const val PREFS = "app_update"
        const val KEY_DISMISSED = "dismissed_version_code"
        const val UPDATES_DIR = "updates"
        const val MSG_CHECK = "No se pudo consultar si hay una versión nueva. Revisa tu conexión."
    }
}
