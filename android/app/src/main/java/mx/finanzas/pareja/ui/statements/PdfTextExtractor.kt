package mx.finanzas.pareja.ui.statements

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.finanzas.pareja.BuildConfig
import java.io.File

class PdfContent(val bytes: ByteArray, val text: String)

/**
 * Lee el PDF elegido y extrae su texto en el dispositivo (nada sale del teléfono en este paso).
 * Si el PDF es una imagen escaneada, sin capa de texto, lo reconoce con OCR local.
 * Devuelve null si no se pudo abrir el archivo; texto vacío si ni el OCR encontró nada.
 */
suspend fun extractPdfText(context: Context, uri: Uri): PdfContent? = withContext(Dispatchers.IO) {
    PDFBoxResourceLoader.init(context.applicationContext)
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return@withContext null
    val digitalText = try {
        PDDocument.load(bytes).use { document ->
            PDFTextStripper().apply { sortByPosition = true }.getText(document)
        }
    } catch (_: Exception) {
        return@withContext null
    }
    if (digitalText.isNotBlank()) return@withContext PdfContent(bytes, digitalText)

    val scannedText = try {
        ocrPdfText(context, bytes)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ""
    }
    if (BuildConfig.DEBUG && scannedText.isNotBlank()) {
        // Solo en compilaciones de depuración: permite afinar el parser con un estado real.
        runCatching { File(context.getExternalFilesDir(null), "ocr-last.txt").writeText(scannedText) }
    }
    PdfContent(bytes, scannedText)
}
