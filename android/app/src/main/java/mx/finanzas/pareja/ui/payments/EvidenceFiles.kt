package mx.finanzas.pareja.ui.payments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoadedEvidence(val bytes: ByteArray, val mimeType: String)

private const val MAX_SIDE_PX = 2048
private const val JPEG_QUALITY = 85

/** Uri temporal (FileProvider) donde la cámara guardará la foto del comprobante. */
fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "evidence").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Lee el archivo elegido. Las imágenes se reducen a ~2048 px y se recomprimen a JPEG
 * (respetando la orientación EXIF) para no subir fotos de varios MB; los PDF pasan tal cual.
 */
suspend fun loadEvidence(context: Context, uri: Uri): LoadedEvidence? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: return@withContext null
    val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
    if (mime.startsWith("image/") && mime != "image/gif") compressImage(raw) else LoadedEvidence(raw, mime)
}

private fun compressImage(raw: ByteArray): LoadedEvidence {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE_PX * 2) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return LoadedEvidence(raw, "image/jpeg")

    val longest = max(decoded.width, decoded.height)
    val scale = if (longest > MAX_SIDE_PX) MAX_SIDE_PX.toFloat() / longest else 1f
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(exifRotation(raw))
    }
    val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

    val out = ByteArrayOutputStream()
    result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    return LoadedEvidence(out.toByteArray(), "image/jpeg")
}

private fun exifRotation(raw: ByteArray): Float = try {
    val exif = ExifInterface(ByteArrayInputStream(raw))
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
} catch (_: Exception) {
    0f
}
