package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate

private val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val MONTHS_LONG = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** `15 oct 2026` */
fun formatDateEs(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS_SHORT[date.monthNumber - 1]} ${date.year}"

/** `15 oct` */
fun formatDayMonthEs(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS_SHORT[date.monthNumber - 1]}"

/** `octubre 2026` */
fun formatMonthEs(year: Int, month: Int): String = "${MONTHS_LONG[month - 1]} $year"
