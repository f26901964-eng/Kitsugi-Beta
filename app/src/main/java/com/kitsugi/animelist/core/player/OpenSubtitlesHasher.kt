package com.kitsugi.animelist.core.player

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpenSubtitlesHasher {
    private const val HASH_CHUNK_SIZE = 65536 // 64 KB

    data class HashResult(val hash: String, val size: Long)

    /**
     * Hash önbelleği — Ortak Altyazı Omurgası.
     *
     * Altyazı merkezi aynı bölüm için birden fazla sorgu (IMDb + Kitsu + AniList + başlık
     * varyantları) atıyor ve her sorgu kendi `getSubtitles` çağrısında hash'i yeniden
     * hesaplıyordu: aynı video için 5-7 kez HEAD + 2 Range isteği. Bu, özellikle yavaş
     * bağlantıda altyazı gelmesini geciktiriyor ve gereksiz trafik üretiyordu.
     * Aynı URL için 10 dakika boyunca tek hesaplama yeterlidir.
     */
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_MAX = 12
    private data class CacheEntry(val result: HashResult?, val at: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    private fun cacheKey(url: String, headers: Map<String, String>?): String =
        if (headers.isNullOrEmpty()) url else url + "|" + headers.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    fun clearCache() = cache.clear()

    fun compute(url: String, headers: Map<String, String>? = null): HashResult? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null
        }
        val key = cacheKey(url, headers)
        val now = System.currentTimeMillis()
        cache[key]?.let { entry ->
            if (now - entry.at <= CACHE_TTL_MS) return entry.result
            cache.remove(key)
        }
        val computed = computeUncached(url, headers)
        // Başarısız hesaplamayı önbelleğe YAZMA: geçici ağ hatası 10 dakika boyunca
        // altyazıları bloklamasın.
        if (computed != null) {
            if (cache.size >= CACHE_MAX) {
                // En eski kaydı at (küçük, sabit boyutlu önbellek).
                cache.entries.minByOrNull { it.value.at }?.let { cache.remove(it.key) }
            }
            cache[key] = CacheEntry(computed, now)
        }
        return computed
    }

    private fun computeUncached(url: String, headers: Map<String, String>?): HashResult? {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // 1. Get content length (size)
            val headRequest = Request.Builder()
                .url(url)
                .apply {
                    headers?.forEach { (k, v) -> header(k, v) }
                }
                .head()
                .build()

            val size = client.newCall(headRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                response.header("Content-Length")?.toLongOrNull()
            } ?: return null

            if (size < HASH_CHUNK_SIZE * 2) return null

            // 2. Fetch first 64KB
            val firstRangeRequest = Request.Builder()
                .url(url)
                .apply {
                    headers?.forEach { (k, v) -> header(k, v) }
                    header("Range", "bytes=0-${HASH_CHUNK_SIZE - 1}")
                }
                .build()

            val firstBytes = client.newCall(firstRangeRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            } ?: return null

            if (firstBytes.size < HASH_CHUNK_SIZE) return null

            // 3. Fetch last 64KB
            val lastRangeRequest = Request.Builder()
                .url(url)
                .apply {
                    headers?.forEach { (k, v) -> header(k, v) }
                    header("Range", "bytes=${size - HASH_CHUNK_SIZE}-${size - 1}")
                }
                .build()

            val lastBytes = client.newCall(lastRangeRequest).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            } ?: return null

            if (lastBytes.size < HASH_CHUNK_SIZE) return null

            // Calculate checksum
            var head = size

            val firstBuffer = ByteBuffer.wrap(firstBytes).order(ByteOrder.LITTLE_ENDIAN)
            val longBuffer = firstBuffer.asLongBuffer()
            while (longBuffer.hasRemaining()) {
                head += longBuffer.get()
            }

            val lastBuffer = ByteBuffer.wrap(lastBytes).order(ByteOrder.LITTLE_ENDIAN)
            val lastLongBuffer = lastBuffer.asLongBuffer()
            while (lastLongBuffer.hasRemaining()) {
                head += lastLongBuffer.get()
            }

            return HashResult(String.format("%016x", head), size)
        } catch (e: Exception) {
            Log.e("OpenSubtitlesHasher", "Error computing hash for $url", e)
            return null
        }
    }
}
