package mx.finanzas.pareja.domain

import kotlin.math.abs

/** Línea de texto reconocida por OCR con su caja en píxeles (y crece hacia abajo). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Double get() = (top + bottom) / 2.0
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

// Dos líneas son del mismo renglón si sus centros difieren menos que esta fracción de la altura.
private const val SAME_ROW_FRACTION = 0.6

/**
 * Reconstruye los renglones de una tabla. El OCR entrega fecha, descripción y monto como líneas
 * sueltas; aquí se agrupan por altura y se ordenan de izquierda a derecha, parecido a como un
 * PDF digital da su texto, para que el mismo parser de estados de cuenta pueda leerlos.
 */
fun joinOcrRows(lines: List<OcrLine>): String {
    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
        val row = rows.lastOrNull()
        val sameRow = row != null &&
            abs(line.centerY - row.map { it.centerY }.average()) <=
            minOf(line.height, row.minOf { it.height }) * SAME_ROW_FRACTION
        if (sameRow) row!!.add(line) else rows.add(mutableListOf(line))
    }
    return rows.joinToString("\n") { row ->
        row.sortedBy { it.left }.joinToString("  ") { it.text.trim() }
    }
}
