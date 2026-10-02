package io.github.akrishna87.radio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads station logos, shrunk to icon size and kept in memory while the app is open. */
object Logos {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** Logos that couldn't be loaded, so they aren't tried again on every scroll. */
    private val failed = java.util.Collections.synchronizedSet(HashSet<String>())
    private val downloads = Semaphore(6)
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun cached(url: String): Bitmap? = cache.get(url)

    fun hasFailed(url: String): Boolean = url in failed

    suspend fun load(url: String, sizePx: Int): Bitmap? {
        if (url.isBlank() || url in failed) return null
        cache.get(url)?.let { return it }
        val bitmap = downloads.withPermit {
            withContext(Dispatchers.IO) { runCatching { download(url, sizePx) }.getOrNull() }
        }
        if (bitmap == null) failed += url else cache.put(url, bitmap)
        return bitmap
    }

    private fun download(url: String, sizePx: Int): Bitmap? {
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return null
        var conn = URL(url).openConnection() as HttpURLConnection
        var redirects = 0
        // HttpURLConnection won't follow a redirect between http and https on its own.
        while (true) {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Vaanalai/1.0 (Android)")
            val code = conn.responseCode
            if (code in 300..399 && redirects < 3) {
                val next = conn.getHeaderField("Location") ?: return null
                conn.disconnect()
                conn = URL(URL(url), next).openConnection() as HttpURLConnection
                redirects++
                continue
            }
            if (code !in 200..299) return null
            break
        }
        val bytes = try {
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        // Tiny favicons (16×16) look like mush when blown up; treat them as missing.
        if (decoded.width < 32 && decoded.height < 32) return null
        return decoded
    }
}
