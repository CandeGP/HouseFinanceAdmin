package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatTest {
    @Test fun `fecha completa`() = assertEquals("15 oct 2026", formatDateEs(LocalDate(2026, 10, 15)))
    @Test fun `dia y mes`() = assertEquals("5 nov", formatDayMonthEs(LocalDate(2026, 11, 5)))
    @Test fun `mes largo`() = assertEquals("octubre 2026", formatMonthEs(2026, 10))
    @Test fun `diciembre`() = assertEquals("diciembre 2025", formatMonthEs(2025, 12))
}
