package mx.finanzas.pareja.ui.statements

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfContent(val bytes: ByteArray, val text: String)

/**
 * Lee el PDF elegido y extrae su texto en el dispositivo (nada sale del teléfono en este paso).
 * Devuelve null si no se pudo abrir el archivo; un PDF escaneado devuelve texto vacío.
 */
suspend fun extractPdfText(context: Context, uri: Uri): PdfContent? = withContext(Dispatchers.IO) {
    PDFBoxResourceLoader.init(context.applicationContext)
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return@withContext null
    try {
        PDDocument.load(bytes).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            PdfContent(bytes, stripper.getText(document))
        }
    } catch (_: Exception) {
        null
    }
}
