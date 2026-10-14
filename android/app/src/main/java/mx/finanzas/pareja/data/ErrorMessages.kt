package mx.finanzas.pareja.data

import java.io.IOException

/** Traduce errores técnicos (Supabase/red) a mensajes para la persona usuaria. */
fun userMessage(error: Throwable): String {
    val raw = error.message.orEmpty()
    return when {
        raw.contains("Invalid login credentials", ignoreCase = true) -> "Correo o contraseña incorrectos"
        raw.contains("already registered", ignoreCase = true) -> "Ese correo ya está registrado"
        raw.contains("invalid or expired invite", ignoreCase = true) -> "La invitación no es válida o ya expiró"
        raw.contains("household is full", ignoreCase = true) -> "Este hogar ya tiene el máximo de miembros"
        raw.contains("only an admin", ignoreCase = true) -> "Solo el administrador puede invitar"
        error is IOException ||
            raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("timeout", ignoreCase = true) -> "Sin conexión con el servidor"
        raw.isNotBlank() -> raw
        else -> "Ocurrió un error inesperado"
    }
}
