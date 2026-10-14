package mx.finanzas.pareja.domain

import java.text.NumberFormat
import java.util.Locale

private val MX = Locale("es", "MX")

/** Formatea un monto como pesos mexicanos: `$1,234.50`. */
fun formatMxn(amount: Double): String =
    NumberFormat.getCurrencyInstance(MX).format(amount)
