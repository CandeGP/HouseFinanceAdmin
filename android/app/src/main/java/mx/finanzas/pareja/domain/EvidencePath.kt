package mx.finanzas.pareja.domain

/** Extensión de archivo para un tipo MIME de evidencia admitido; null si no se admite. */
fun evidenceExtension(mimeType: String): String? = when (mimeType.trim().lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "application/pdf" -> "pdf"
    else -> null
}

/**
 * Ruta del objeto en el bucket de evidencias: `<hogar>/<dueño>/<archivo>.<ext>`.
 * Las políticas de Storage y la restricción de `manual_payments` exigen exactamente este prefijo.
 */
fun evidencePath(householdId: String, ownerId: String, fileId: String, mimeType: String): String? =
    evidenceExtension(mimeType)?.let { "$householdId/$ownerId/$fileId.$it" }
