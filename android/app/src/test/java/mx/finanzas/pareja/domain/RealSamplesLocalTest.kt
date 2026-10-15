package mx.finanzas.pareja.domain

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prueba local con estados de cuenta reales (carpeta `samples/`, ignorada por git). Si no hay
 * muestras se omite, así que no afecta a nadie más. Escribe `samples/parsed-summary.txt`.
 */
class RealSamplesLocalTest {
    private val samples = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "samples") }
        .firstOrNull { it.isDirectory }

    @Test
    fun `los estados de cuenta reales de samples se parsean`() {
        assumeTrue("no hay carpeta samples", samples != null)
        val files = samples!!.listFiles { f -> f.name.startsWith("ocr-") && f.extension == "txt" }.orEmpty()
        assumeTrue("no hay muestras ocr-*.txt", files.isNotEmpty())

        val report = StringBuilder()
        for (file in files.sortedBy { it.name }) {
            val parsed = parseStatement(file.readText())
            assertNotNull("no se pudo parsear ${file.name}", parsed)
            val s = parsed!!.summary
            report.appendLine("== ${file.name}")
            report.appendLine("banco=${s.bank} tarjeta=${s.cardLast4} corte=${s.cutoffDate} pago=${s.dueDate}")
            report.appendLine("periodo=${s.periodStart}..${s.periodEnd} limite=${s.creditLimit}")
            report.appendLine("sinIntereses=${s.payNoInterest} minimo=${s.minimumPayment}")
            report.appendLine("-- movimientos (${parsed.movements.size})")
            parsed.movements.forEach {
                report.appendLine("  ${it.operationDate} ${it.amount} msi=${it.isInstallmentCharge} | ${it.description}")
            }
            report.appendLine("-- meses (${parsed.installments.size})")
            parsed.installments.forEach {
                report.appendLine("  ${it.purchaseDate} ${it.current}/${it.total} req=${it.requiredPayment} orig=${it.originalAmount} | ${it.description}")
            }
        }
        File(samples, "parsed-summary.txt").writeText(report.toString())
    }
}
