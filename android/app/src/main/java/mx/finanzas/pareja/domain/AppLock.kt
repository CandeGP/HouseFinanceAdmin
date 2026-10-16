package mx.finanzas.pareja.domain

/** Tiempo fuera de la app a partir del cual se vuelve a pedir el desbloqueo. */
const val LOCK_GRACE_MS = 30_000L

/**
 * Si al abrir o volver a la app hay que pedir el desbloqueo.
 * [leftAtMs] es el momento en que se salió de la app; null significa que no se había salido (arranque
 * desde cero), así que siempre se bloquea. Un cambio rápido de app o girar la pantalla no bloquea.
 */
fun shouldLock(enabled: Boolean, leftAtMs: Long?, nowMs: Long, graceMs: Long = LOCK_GRACE_MS): Boolean =
    enabled && (leftAtMs == null || nowMs - leftAtMs >= graceMs)
