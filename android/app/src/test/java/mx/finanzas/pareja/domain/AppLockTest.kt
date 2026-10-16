package mx.finanzas.pareja.domain

import mx.finanzas.pareja.ui.lock.LockController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    @Test
    fun `un arranque desde cero bloquea si esta activado`() {
        assertTrue(shouldLock(enabled = true, leftAtMs = null, nowMs = 1_000))
    }

    @Test
    fun `desactivado nunca bloquea`() {
        assertFalse(shouldLock(enabled = false, leftAtMs = null, nowMs = 1_000))
        assertFalse(shouldLock(enabled = false, leftAtMs = 0, nowMs = 10 * LOCK_GRACE_MS))
    }

    @Test
    fun `salir un momento no bloquea y salir mucho si`() {
        assertFalse(shouldLock(true, leftAtMs = 0, nowMs = LOCK_GRACE_MS - 1))
        assertTrue(shouldLock(true, leftAtMs = 0, nowMs = LOCK_GRACE_MS))
        assertTrue(shouldLock(true, leftAtMs = 0, nowMs = LOCK_GRACE_MS + 1))
    }

    @Test
    fun `el control arranca bloqueado y se desbloquea`() {
        val lock = LockController()
        assertTrue(lock.locked)
        lock.onStart(enabled = true, nowMs = 0)
        assertTrue(lock.locked)

        lock.unlock()
        assertFalse(lock.locked)
    }

    @Test
    fun `sin la funcion activada el arranque no bloquea`() {
        val lock = LockController()
        lock.onStart(enabled = false, nowMs = 0)
        assertFalse(lock.locked)
    }

    @Test
    fun `girar la pantalla o cambiar de app un instante no vuelve a pedir el desbloqueo`() {
        val lock = LockController()
        lock.onStart(true, 0)
        lock.unlock()

        lock.onStop(10_000)
        lock.onStart(true, 10_500) // se recrea la actividad
        assertFalse(lock.locked)
    }

    @Test
    fun `volver despues de mas del tiempo de gracia bloquea de nuevo`() {
        val lock = LockController()
        lock.onStart(true, 0)
        lock.unlock()

        lock.onStop(10_000)
        lock.onStart(true, 10_000 + LOCK_GRACE_MS)
        assertTrue(lock.locked)
    }

    @Test
    fun `apagar la funcion desbloquea`() {
        val lock = LockController()
        lock.onStart(true, 0)
        assertEquals(true, lock.locked)

        lock.onStart(enabled = false, nowMs = 1_000)
        assertFalse(lock.locked)
    }
}
