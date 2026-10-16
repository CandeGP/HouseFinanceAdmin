package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val base = "https://proyecto.supabase.co/storage/v1/object/public/releases/"
    private val sha = "a".repeat(64)

    private fun json(
        code: Int = 5,
        name: String = "1.1.0",
        url: String = "${base}finanzas.apk",
        hash: String = sha,
        extra: String = "",
    ) = """{"versionCode":$code,"versionName":"$name","url":"$url","sha256":"$hash"$extra}"""

    @Test
    fun `lee un manifiesto valido`() {
        val m = parseUpdateManifest(json(extra = ""","notes":"Corrige tarjetas","sizeBytes":22707924"""), base)!!
        assertEquals(5, m.versionCode)
        assertEquals("1.1.0", m.versionName)
        assertEquals("Corrige tarjetas", m.notes)
        assertEquals(22_707_924L, m.sizeBytes)
    }

    @Test
    fun `ignora campos que no conoce`() {
        assertNotNull(parseUpdateManifest(json(extra = ""","futuro":true"""), base))
    }

    @Test
    fun `rechaza una descarga fuera del origen permitido`() {
        assertNull(parseUpdateManifest(json(url = "https://otro-sitio.com/finanzas.apk"), base))
    }

    @Test
    fun `rechaza hash invalido, version cero o JSON roto`() {
        assertNull(parseUpdateManifest(json(hash = "no-es-un-hash"), base))
        assertNull(parseUpdateManifest(json(code = 0), base))
        assertNull(parseUpdateManifest("esto no es json", base))
    }

    @Test
    fun `normaliza el hash a minusculas`() {
        val m = parseUpdateManifest(json(hash = "A".repeat(64)), base)!!
        assertEquals(sha, m.sha256)
    }

    @Test
    fun `solo hay actualizacion si la version publicada es mayor`() {
        val m = parseUpdateManifest(json(code = 5), base)!!
        assertTrue(isUpdateAvailable(installedVersionCode = 4, manifest = m))
        assertFalse(isUpdateAvailable(installedVersionCode = 5, manifest = m))
        assertFalse(isUpdateAvailable(installedVersionCode = 6, manifest = m))
    }
}
