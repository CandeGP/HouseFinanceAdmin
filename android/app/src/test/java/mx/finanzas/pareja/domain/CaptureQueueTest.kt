package mx.finanzas.pareja.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureQueueTest {
    private fun charge(merchant: String) = ParsedCharge(
        merchant = merchant, amount = 10.0, cardLast4 = null,
        sourcePackage = "p", capturedAt = Instant.parse("2026-10-14T17:39:00Z"), raw = merchant,
    )

    @Test
    fun `si todo se envia no queda nada pendiente`() = runTest {
        val sent = mutableListOf<String>()
        val remaining = flushCaptures(listOf(charge("a"), charge("b"))) { sent += it.merchant }
        assertEquals(listOf("a", "b"), sent)
        assertEquals(emptyList<ParsedCharge>(), remaining)
    }

    @Test
    fun `los que fallan se devuelven en orden y los demas se siguen enviando`() = runTest {
        val sent = mutableListOf<String>()
        val remaining = flushCaptures(listOf(charge("a"), charge("b"), charge("c"), charge("d"))) {
            if (it.merchant == "b" || it.merchant == "d") error("sin red")
            sent += it.merchant
        }
        assertEquals(listOf("a", "c"), sent)
        assertEquals(listOf("b", "d"), remaining.map { it.merchant })
    }

    @Test
    fun `la cancelacion se propaga y no se traga`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                flushCaptures(listOf(charge("a"))) { throw CancellationException("cancelado") }
            }
        }
    }
}
