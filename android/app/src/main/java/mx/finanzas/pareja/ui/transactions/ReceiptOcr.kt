package mx.finanzas.pareja.ui.transactions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.finanzas.pareja.ui.statements.recognizeBitmap

// Lado máximo al que se reduce la imagen: suficiente para leer cifras y evita agotar memoria.
private const val MAX_SIDE_PX = 2400

/** Lee el texto de una imagen de la galería (ticket, voucher, captura) en el teléfono; nada se sube. */
suspend fun ocrImageText(context: Context, uri: Uri): String = withContext(Dispatchers.Default) {
    val bitmap = decodeScaled(context, uri) ?: return@withContext ""
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        recognizeBitmap(recognizer, bitmap)
    } finally {
        recognizer.close()
        bitmap.recycle()
    }
}

private fun decodeScaled(context: Context, uri: Uri): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder respeta la orientación EXIF (fotos tomadas de lado).
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE_PX) {
                val scale = MAX_SIDE_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    } else {
        // shortcut: sin ImageDecoder no se corrige la orientación EXIF; subir a androidx.exifinterface si hace falta.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE_PX) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
