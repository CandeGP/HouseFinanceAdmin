package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {
    private val today = LocalDate(2026, 10, 16)

    private fun charge(
        id: String, merchant: String, amount: Double, on: LocalDate, cardId: String? = "c1",
    ): Transaction = tx(id, amount, cardId = cardId, date = on).copy(merchant = merchant)

    /** Un cargo mensual el mismo día, en los [months] indicados de 2026. */
    private fun monthly(merchant: String, amount: Double, day: Int = 12, months: List<Int> = listOf(7, 8, 9, 10)) =
        months.mapIndexed { i, month -> charge("$merchant-$i", merchant, amount, LocalDate(2026, month, day)) }

    @Test
    fun `un cargo mensual de monto fijo se detecta con su siguiente fecha`() {
        val found = detectRecurring(monthly("Netflix", 219.0), today).single()

        assertEquals("Netflix", found.merchant)
        assertEquals(219.0, found.amount, 0.0)
        assertEquals(LocalDate(2026, 10, 12), found.lastDate)
        assertEquals(LocalDate(2026, 11, 12), found.nextExpected)
        assertEquals(4, found.months)
        assertEquals("c1", found.cardId)
    }

    @Test
    fun `con menos de tres meses no se considera recurrente`() {
        val two = listOf(
            charge("a", "Spotify", 129.0, LocalDate(2026, 9, 5)),
            charge("b", "Spotify", 129.0, LocalDate(2026, 10, 5)),
        )
        assertTrue(detectRecurring(two, today).isEmpty())
    }

    @Test
    fun `montos que cambian mucho no son recurrentes`() {
        val varied = listOf(100.0, 320.0, 55.0, 410.0).mapIndexed { i, amount ->
            charge("v$i", "Uber", amount, LocalDate(2026, 7 + i, 10))
        }
        assertTrue(detectRecurring(varied, today).isEmpty())
    }

    @Test
    fun `una variacion pequena del monto si se tolera`() {
        val drift = listOf(300.0, 310.0, 295.0, 305.0).mapIndexed { i, amount ->
            charge("d$i", "Internet Casa", amount, LocalDate(2026, 7 + i, 8))
        }
        assertEquals(1, detectRecurring(drift, today).size)
    }

    @Test
    fun `un cafe diario no es una suscripcion aunque el monto sea igual`() {
        // 100 días seguidos (julio a octubre): aparece en 4 meses, pero con ~25 cargos por mes.
        val first = LocalDate(2026, 6, 30).toEpochDays()
        val daily = (1..100).map { i -> charge("c$i", "Caffenio", 45.0, LocalDate.fromEpochDays(first + i)) }
        assertTrue(detectRecurring(daily, today).isEmpty())
    }

    @Test
    fun `lo que ya dejo de cobrarse no aparece`() {
        assertTrue(detectRecurring(monthly("Gimnasio", 500.0, day = 3, months = listOf(3, 4, 5, 6)), today).isEmpty())
    }

    @Test
    fun `las compras a meses y los cobros pendientes no cuentan`() {
        val msi = monthly("Celular", 1000.0).map { it.copy(paymentMode = PaymentMode.MSI, installments = 12) }
        val pending = monthly("Pendiente", 80.0).map { it.copy(status = TxStatus.PENDING_REVIEW) }
        assertTrue(detectRecurring(msi + pending, today).isEmpty())
    }

    @Test
    fun `se agrupa el mismo comercio aunque cambie la escritura o el numero`() {
        val mixed = listOf(
            charge("m1", "NETFLIX.COM", 219.0, LocalDate(2026, 7, 12)),
            charge("m2", "Netflix.com 8841", 219.0, LocalDate(2026, 8, 12)),
            charge("m3", "netflix com", 219.0, LocalDate(2026, 9, 12)),
        )
        val found = detectRecurring(mixed, today).single()
        assertEquals(3, found.months)
        assertEquals("netflix com", found.merchant) // la escritura más reciente
    }

    @Test
    fun `un cargo que ya debio llegar hace pocos dias sigue esperandose`() {
        // Último cargo el 11 sep: el siguiente era el 11 oct (hace 5 días), dentro de la gracia de 10.
        val found = detectRecurring(monthly("Renta bodega", 1500.0, day = 11, months = listOf(6, 7, 8, 9)), today).single()
        assertEquals(LocalDate(2026, 10, 11), found.nextExpected)
    }

    @Test
    fun `un cargo atrasado mas de la gracia ya no se espera`() {
        // Último cargo el 1 sep: el siguiente era el 1 oct (hace 15 días): fuera de la gracia.
        assertTrue(detectRecurring(monthly("Seguro", 900.0, day = 1, months = listOf(6, 7, 8, 9)), today).isEmpty())
    }

    @Test
    fun `un cargo de hoy se espera dentro de un mes`() {
        val found = detectRecurring(monthly("Iphone cloud", 49.0, day = 16), today).single()
        assertEquals(LocalDate(2026, 11, 16), found.nextExpected)
    }

    @Test
    fun `se ordenan por fecha esperada`() {
        // Telcel: último cargo 20 sep → próximo 20 oct. Netflix: último 12 oct → próximo 12 nov.
        val txs = monthly("Netflix", 219.0, day = 12) + monthly("Telcel", 399.0, day = 20, months = listOf(6, 7, 8, 9))
        assertEquals(listOf("Telcel", "Netflix"), detectRecurring(txs, today).map { it.merchant })
    }
}
