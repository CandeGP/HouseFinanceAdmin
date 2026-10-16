package mx.finanzas.pareja.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Descripción de la versión publicada (`latest.json` en el bucket público de releases). */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val notes: String? = null,
    val sizeBytes: Long? = null,
)

private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Lee y valida el manifiesto. Devuelve null si el JSON está roto, el hash no es SHA-256 o la URL de
 * descarga no cuelga de [allowedBase] (así un manifiesto alterado no puede mandar a otro sitio).
 */
fun parseUpdateManifest(text: String, allowedBase: String): UpdateManifest? {
    val manifest = runCatching { json.decodeFromString(UpdateManifest.serializer(), text) }.getOrNull()
        ?: return null
    val valid = manifest.versionCode > 0 &&
        manifest.versionName.isNotBlank() &&
        manifest.url.startsWith(allowedBase) &&
        SHA256_HEX.matches(manifest.sha256.lowercase())
    return if (valid) manifest.copy(sha256 = manifest.sha256.lowercase()) else null
}

fun isUpdateAvailable(installedVersionCode: Int, manifest: UpdateManifest): Boolean =
    manifest.versionCode > installedVersionCode
