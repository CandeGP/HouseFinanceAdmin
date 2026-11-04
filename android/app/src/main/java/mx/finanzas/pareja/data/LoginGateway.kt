package mx.finanzas.pareja.data

import kotlin.math.ceil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** La función `login` del servidor respondió 429: se acabaron las fichas de la cubeta (por IP o por usuario). */
class RateLimitedException(val retryAfterSeconds: Int) : RuntimeException("rate limited, retry in $retryAfterSeconds s")

/** Falla del servidor de login (caído, o el límite no se pudo consultar). */
class LoginUnavailableException : RuntimeException("login unavailable")

data class LoginTokens(val accessToken: String, val refreshToken: String)

private const val DEFAULT_RETRY_SECONDS = 5

/**
 * Interpreta la respuesta de `/functions/v1/login` (separado de la red para poder probarlo).
 * Credenciales malas lanzan "Invalid login credentials" para reutilizar el mensaje de [userMessage].
 */
fun parseLoginResponse(status: Int, body: String, retryAfterHeader: String? = null): LoginTokens {
    val json: JsonObject? = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    val access = json?.get("access_token")?.jsonPrimitive?.contentOrNull
    val refresh = json?.get("refresh_token")?.jsonPrimitive?.contentOrNull
    return when {
        status == 200 && access != null && refresh != null -> LoginTokens(access, refresh)
        status == 401 -> throw IllegalStateException("Invalid login credentials")
        status == 429 -> {
            val seconds = listOf(json?.get("retry_after")?.jsonPrimitive?.doubleOrNull, retryAfterHeader?.toDoubleOrNull())
                .firstOrNull { it != null && it > 0 } ?: DEFAULT_RETRY_SECONDS.toDouble()
            throw RateLimitedException(ceil(seconds).toInt())
        }
        else -> throw LoginUnavailableException()
    }
}
