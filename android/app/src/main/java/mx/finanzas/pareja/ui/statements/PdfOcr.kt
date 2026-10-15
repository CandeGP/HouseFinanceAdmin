package mx.finanzas.pareja.ui.statements

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mx.finanzas.pareja.domain.OcrLine
import mx.finanzas.pareja.domain.joinOcrRows
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Ancho al que se renderiza cada página: ~250 dpi en carta, suficiente para cifras pequeñas.
private const val TARGET_WIDTH_PX = 2000

/**
 * OCR en el dispositivo para estados de cuenta escaneados: renderiza cada página y la reconoce con
 * ML Kit (modelo incluido en la app, sin red). Nada del documento sale del teléfono.
 */
suspend fun ocrPdfText(context: Context, bytes: ByteArray): String = withContext(Dispatchers.Default) {
    val file = File.createTempFile("ocr", ".pdf", context.cacheDir)
    try {
        file.writeBytes(bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val pages = mutableListOf<String>()
                    for (index in 0 until renderer.pageCount) {
                        pages += renderer.openPage(index).use { page -> recognizePage(recognizer, page) }
                    }
                    pages.joinToString("\n\n")
                } finally {
                    recognizer.close()
                }
            }
        }
    } finally {
        file.delete()
    }
}

private suspend fun recognizePage(recognizer: TextRecognizer, page: PdfRenderer.Page): String {
    val scale = TARGET_WIDTH_PX.toFloat() / page.width
    val bitmap = Bitmap.createBitmap(TARGET_WIDTH_PX, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(Color.WHITE) // el render es transparente; el OCR necesita fondo blanco
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.right, it.bottom) }
        }
        return joinOcrRows(lines)
    } finally {
        bitmap.recycle()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
