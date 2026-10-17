package mx.finanzas.pareja.data

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.cycleStatus
import mx.finanzas.pareja.estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CardSnapshotsTest {
    private val ref = LocalDate(2026, 10, 16)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `la fila de la RPC se convierte en estimacion y estado`() {
        val row = json.decodeFromString<CardSnapshotRow>(
            """{"card_id":"c1","cycle_start":"2026-10-05","cycle_end":"2026-11-04","due_date":"2026-11-26",
               "cycle_contado_total":100,"cycle_msi_total":2892.75,"cycle_total":2992.75,"msi_remaining":6066.09,
               "credit_limit":90400,"usage_pct":10.02,
               "st_cutoff_date":"2026-10-04","st_due_date":"2026-10-26","st_target_amount":3912.02,
               "st_target_source":"statement","st_minimum_payment":null,"st_paid_amount":0,"st_remaining":3912.02,
               "st_status":"pending"}""",
        )

        val snapshot = row.toSnapshot()

        assertEquals(2992.75, snapshot.estimate!!.cycleTotal, 0.0)
        assertEquals(LocalDate(2026, 11, 26), snapshot.estimate!!.dueDate)
        assertEquals(CardPayStatus.PENDING, snapshot.status!!.status)
        assertEquals(3912.02, snapshot.status!!.remaining, 0.0)
        assertNull(snapshot.status!!.minimumPayment)
    }

    @Test
    fun `una tarjeta sin datos queda sin estimacion ni estado`() {
        val snapshot = json.decodeFromString<CardSnapshotRow>("""{"card_id":"c2"}""").toSnapshot()

        assertEquals("c2", snapshot.cardId)
        assertNull(snapshot.estimate)
        assertNull(snapshot.status)
    }

    @Test
    fun `sin RPC unica el repositorio pide cada tarjeta por separado`() = runTest {
        val repo = FakeFinanceRepository()
        repo.estimates["a"] = estimate(cycleTotal = 500.0)
        repo.cycleStatuses["a"] = cycleStatus(CardPayStatus.PENDING, target = 300.0)

        val snapshots = repo.cardSnapshots(listOf("a", "b"), ref).associateBy { it.cardId }

        assertEquals(500.0, snapshots.getValue("a").estimate!!.cycleTotal, 0.0)
        assertNotNull(snapshots.getValue("a").status)
        assertNull(snapshots.getValue("b").estimate)
    }
}
