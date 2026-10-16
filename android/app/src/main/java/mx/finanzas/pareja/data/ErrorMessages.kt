package mx.finanzas.pareja.data

import java.io.IOException

private const val MAX_MESSAGE_LENGTH = 200

/**
 * Traduce errores técnicos (Supabase/red) a mensajes para la persona usuaria.
 * Nunca devuelve cabeceras, URL ni tokens: los errores de PostgREST los traen en el texto.
 */
fun userMessage(error: Throwable): String {
    val raw = cleanMessage(error)
    return when {
        raw.contains("Invalid login credentials", ignoreCase = true) -> "Correo o contraseña incorrectos"
        raw.contains("already registered", ignoreCase = true) -> "Ese correo ya está registrado"
        raw.contains("invalid or expired invite", ignoreCase = true) -> "La invitación no es válida o ya expiró"
        raw.contains("household is full", ignoreCase = true) -> "Este hogar ya tiene el máximo de miembros"
        raw.contains("only an admin", ignoreCase = true) -> "Solo el administrador puede invitar"
        raw.contains("members_user_id_fkey", ignoreCase = true) ||
            raw.contains("JWT", ignoreCase = true) -> "Tu sesión ya no es válida. Cierra sesión y vuelve a entrar."
        isConnectivityError(error) -> "Sin conexión con el servidor"
        raw.isNotBlank() -> raw.take(MAX_MESSAGE_LENGTH)
        else -> "Ocurrió un error inesperado"
    }
}

/** True si el fallo es de red (sin internet, DNS o tiempo agotado) y no una respuesta del servidor. */
fun isConnectivityError(error: Throwable): Boolean {
    val raw = cleanMessage(error)
    return error is IOException ||
        raw.contains("Unable to resolve host", ignoreCase = true) ||
        raw.contains("timeout", ignoreCase = true)
}

// Solo la primera sección: antes de "URL:" / "Headers:" que añade el cliente HTTP.
private fun cleanMessage(error: Throwable): String = error.message.orEmpty()
    .substringBefore("\nURL:")
    .substringBefore("\nHeaders:")
    .trim()
