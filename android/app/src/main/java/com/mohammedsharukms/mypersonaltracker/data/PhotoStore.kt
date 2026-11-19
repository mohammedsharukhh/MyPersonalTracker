package com.mohammedsharukms.mypersonaltracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photos (to-do attachments, daily selfies, bills) are stored as downscaled JPEGs in app-private storage;
 * the database only keeps their file names.
 */
class PhotoStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** A fresh cache file + content URI for the system camera to write into. */
    fun newCaptureTarget(): Pair<File, Uri> {
        val captures = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(captures, "${UUID.randomUUID()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    suspend fun save(bitmap: Bitmap, maxDimension: Int = 1600, quality: Int = 80): String = withContext(Dispatchers.IO) {
        val scaled = bitmap.scaledToFit(maxDimension)
        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        name
    }

    suspend fun save(uri: Uri, maxDimension: Int = 1600, quality: Int = 80): String? = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, maxDimension) ?: return@withContext null
        save(bitmap, maxDimension, quality)
    }

    /** Stacks pages top to bottom at a common width, e.g. a multi-page scanned bill. */
    suspend fun saveStacked(uris: List<Uri>, width: Int = 1200): String? = withContext(Dispatchers.IO) {
        val pages = uris.mapNotNull { decode(it, 2400) }
        if (pages.isEmpty()) return@withContext null
        val heights = pages.map { (width.toFloat() * it.height / it.width).roundToInt() }
        val stacked = Bitmap.createBitmap(width, heights.sum(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stacked)
        var y = 0f
        pages.zip(heights).forEach { (page, height) ->
            canvas.drawBitmap(Bitmap.createScaledBitmap(page, width, height, true), 0f, y, null)
            y += height
        }
        save(stacked, maxDimension = max(width, heights.sum()), quality = 75)
    }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    /** Loads a stored photo scaled down so its longest side is about [maxDimension]. */
    suspend fun loadThumbnail(name: String, maxDimension: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(name)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension) }
        BitmapFactory.decodeFile(f.path, options)
    }

    private fun decode(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension) }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (degrees == 0f) bitmap else bitmap.rotated(degrees)
    }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }
}

fun Bitmap.scaledToFit(maxDimension: Int): Bitmap {
    val longest = max(width, height)
    if (longest <= maxDimension) return this
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
}

fun Bitmap.rotated(degrees: Float, mirror: Boolean = false): Bitmap {
    val matrix = Matrix().apply {
        postRotate(degrees)
        if (mirror) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
