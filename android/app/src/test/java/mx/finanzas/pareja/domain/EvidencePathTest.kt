package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidencePathTest {
    @Test fun `jpeg usa extension jpg`() = assertEquals("h1/u1/f.jpg", evidencePath("h1", "u1", "f", "image/jpeg"))
    @Test fun `png`() = assertEquals("h1/u1/f.png", evidencePath("h1", "u1", "f", "image/png"))
    @Test fun `webp`() = assertEquals("h1/u1/f.webp", evidencePath("h1", "u1", "f", "image/webp"))
    @Test fun `pdf`() = assertEquals("h1/u1/f.pdf", evidencePath("h1", "u1", "f", "application/pdf"))
    @Test fun `ignora mayusculas`() = assertEquals("h1/u1/f.jpg", evidencePath("h1", "u1", "f", "IMAGE/JPEG"))
    @Test fun `tipo no soportado devuelve null`() = assertNull(evidencePath("h1", "u1", "f", "image/gif"))
    @Test fun `la ruta empieza con hogar y duenio como exige la politica`() {
        val path = evidencePath("hogar", "dueno", "x", "image/png")!!
        assertEquals(listOf("hogar", "dueno"), path.split("/").take(2))
    }
}
