package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import mx.finanzas.pareja.card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferCardLast4Test {
    private val at = Instant.parse("2026-11-04T03:32:00Z")

    private fun charge(bank: String?, last4: String? = null) =
        ParsedCharge("Domino's Pizza", 298.0, last4, "pkg", at, "raw", bank)

    // card() fija ownerId = "u1"
    private fun bankCard(id: String, bank: String, last4: String, archived: Boolean = false, owner: String = "u1") =
        card(id).copy(bank = bank, last4 = last4, archived = archived, ownerId = owner)

    @Test
    fun `aviso sin ultimos 4 usa la unica tarjeta propia de ese banco`() {
        val cards = listOf(bankCard("a", "Plata", "0931"), bankCard("b", "Nu", "1234"))
        assertEquals("0931", inferCardLast4(charge("plata"), cards, "u1"))
        assertEquals("1234", inferCardLast4(charge("nu"), cards, "u1"))
    }

    @Test
    fun `tarjeta virtual con otros digitos se liga a la tarjeta del banco`() {
        val cards = listOf(bankCard("a", "Nu", "1234"))
        assertEquals("1234", inferCardLast4(charge("nu", last4 = "4214"), cards, "u1"))
    }

    @Test
    fun `si los digitos ya son de una tarjeta o alias no se toca`() {
        val cards = listOf(bankCard("a", "Nu", "1234").copy(altLast4 = listOf("4214")), bankCard("b", "Nu", "9999"))
        assertNull(inferCardLast4(charge("nu", last4 = "1234"), cards, "u1"))
        assertNull(inferCardLast4(charge("nu", last4 = "4214"), cards, "u1"))
    }

    @Test
    fun `tarjetas con nombre corto o de emisor distinto se reconocen por banco o nombre`() {
        val cards = listOf(bankCard("a", "Mercado", "4512"), bankCard("b", "Banorte", "8476").copy(name = "Rappi"))
        assertEquals("4512", inferCardLast4(charge("mercadopago"), cards, "u1"))
        assertEquals("8476", inferCardLast4(charge("rappi"), cards, "u1"))
    }

    @Test
    fun `con dos tarjetas del mismo banco no adivina`() {
        val cards = listOf(bankCard("a", "BBVA", "1111"), bankCard("b", "BBVA", "2222"))
        assertNull(inferCardLast4(charge("bbva"), cards, "u1"))
    }

    @Test
    fun `ignora archivadas y tarjetas de otra persona del hogar`() {
        assertEquals("0931", inferCardLast4(charge("plata"), listOf(bankCard("a", "Plata", "0931"), bankCard("b", "Plata", "1", archived = true)), "u1"))
        assertNull(inferCardLast4(charge("plata"), listOf(bankCard("a", "Plata", "0931", owner = "u2")), "u1"))
        assertEquals("7777", inferCardLast4(charge("plata"), listOf(bankCard("a", "Plata", "0931", owner = "u2"), bankCard("b", "Plata", "7777")), "u1"))
    }

    @Test
    fun `sin banco conocido no adivina`() {
        assertNull(inferCardLast4(charge(null), listOf(bankCard("a", "Plata", "0931")), "u1"))
    }

    @Test
    fun `parseCharge reconoce el banco por paquete, app, Billetera y Gmail`() {
        fun parse(pkg: String, title: String?, text: String, label: String? = null) =
            parseCharge(RawNotification(pkg, title, text, at, label))?.bank

        assertEquals("plata", parse(ChargeSources.PLATA, "Domino's Pizza", "\$298"))
        assertEquals("nu", parse("com.nu.production", "Nu", "Compra aprobada por \$100.00 en OXXO"))
        assertEquals("rappi", parse(ChargeSources.GOOGLE_WALLET, "ENERGETICOS", "\$219.08 con Rappicard ••8476"))
        assertEquals("bbva", parse(ChargeSources.GMAIL, "BBVA México", "Compra aprobada por \$250.00 en OXXO terminación 1234"))
        assertEquals("liverpool", parse("mx.liverpool.app", "Liverpool", "Compra por \$1,724.25 en Liverpool Centro", label = "Liverpool"))
        assertEquals("didi", parse("com.didiglobal.passenger", "DiDi Card", "Realizaste una compra de \$159.00 en CAFFENIO", label = "DiDi"))
        assertEquals("hsbc", parse("mx.otra.app", "Aviso", "Compra por \$90.00 en OXXO", label = "HSBC México"))
    }

    @Test
    fun `bankOfRaw reconoce Plata por su formato y otros bancos por nombre`() {
        assertEquals("plata", bankOfRaw("Domino's Pizza | \$298"))
        assertEquals("nu", bankOfRaw("Nu | Compra aprobada por \$100.00"))
        assertNull(bankOfRaw("Amazon Prime | Cargo por \$129.00"))
        assertNull(bankOfRaw(null))
    }
}
