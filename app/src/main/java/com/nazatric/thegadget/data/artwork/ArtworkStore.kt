package com.nazatric.thegadget.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ArtworkStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.cacheDir, "artwork").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(runtimeCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val locks = Mutex()

    suspend fun load(uri: String, size: Int, extract: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$size"
        memory.get(key)?.let { return@withContext it }
        val file = fileFor(uri, size)
        if (file.exists()) {
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            memory.put(key, decoded)
            return@withContext decoded
        }
        if (!extract) return@withContext null
        val bytes = embeddedBytes(Uri.parse(uri)) ?: return@withContext null
        storeEmbedded(uri, bytes, size)
        memory.get(key)
    }

    suspend fun storeEmbedded(uri: String, bytes: ByteArray?, maxPx: Int) = withContext(Dispatchers.IO) {
        if (bytes == null || bytes.isEmpty()) return@withContext
        locks.withLock {
            val bitmap = decodeScaled(bytes, maxPx) ?: return@withLock
            write(uri, maxPx, bitmap)
            write(uri, 160, Bitmap.createScaledBitmap(bitmap, 160, 160, true))
            memory.put("$uri@$maxPx", bitmap)
        }
    }

    suspend fun storeAlbumThumb(uri: String, albumContentUri: Uri?, maxPx: Int) = withContext(Dispatchers.IO) {
        if (albumContentUri == null || fileFor(uri, maxPx).exists()) return@withContext
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                app.contentResolver.loadThumbnail(albumContentUri, Size(maxPx, maxPx), null)
            } else {
                app.contentResolver.openInputStream(albumContentUri)?.use { input ->
                    decodeScaled(input.readBytes(), maxPx)
                }
            }
        }.getOrNull() ?: return@withContext
        locks.withLock {
            write(uri, maxPx, bitmap)
            memory.put("$uri@$maxPx", bitmap)
        }
    }

    fun has(uri: String, size: Int): Boolean = fileFor(uri, size).exists() || memory.get("$uri@$size") != null

    fun contentUri(context: Context, trackUri: String): Uri? {
        val file = fileFor(trackUri, 512).takeIf { it.exists() }
            ?: fileFor(trackUri, 160).takeIf { it.exists() }
            ?: return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }

    fun cacheBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        memory.evictAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    fun embeddedBytes(uri: Uri): ByteArray? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, uri)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun write(uri: String, size: Int, bitmap: Bitmap) {
        val file = fileFor(uri, size)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
        }
    }

    private fun fileFor(uri: String, size: Int): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(32)
        return File(dir, "${name}_$size.jpg")
    }

    private fun decodeScaled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxPx * 2 || bounds.outHeight / sample > maxPx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val scale = maxPx.toFloat() / maxOf(decoded.width, decoded.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
    }

    private fun runtimeCacheKb(): Int {
        val max = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (max / 10).coerceIn(8 * 1024, 32 * 1024)
    }
}
