package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.auth.PlatformRateLimiter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * TMDB `adult` bayrağı üzerinden +18 çözümleyicisi.
 *
 * ## Neden gerekiyor?
 * Simkl liste API'si (`/sync/all-items/{type}`) yanıtında `adult` / `certification`
 * alanlarını TAŞIMAZ; bu yüzden Simkl'den içe aktarılan film/dizi kayıtları
 * `isAdult = false` ile veritabanına yazılır ve +18 blur ayarı açık olsa bile
 * Listem → Simkl ekranında bulanıklık uygulanmaz. Animelerde MAL kimliği ile
 * Shikimori GraphQL üzerinden çözümebiliyoruz ([ShikimoriAdultResolver]), ancak
 * TMDB kimlikli film/dizilerde elimizdeki tek güvenilir kaynak TMDB'nin kendisidir:
 *
 * ```
 * GET https://api.themoviedb.org/3/movie/{id}   → { "adult": true|false }
 * GET https://api.themoviedb.org/3/tv/{id}      → { "adult": true|false }
 * ```
 *
 * Sonuçlar (olumlu VE olumsuz) bellekte önbelleğe alınır; ağ hatasında o kimlik
 * önbelleğe yazılmaz ve bir sonraki turda tekrar denenir. [PlatformRateLimiter]
 * ile TMDB kotasına saygı gösterilir.
 */
object TmdbAdultResolver {

    private const val TAG = "TmdbAdultResolver"
    private const val HOST = "https://api.themoviedb.org/3"
    private const val CACHE_LIMIT = 6000

    /** Tek turda yapılacak istek üst sınırı (kotayı korur). */
    const val MAX_LOOKUPS_PER_RUN = 150

    /** kimlik → +18 mi? (hem true hem false sonuçlar saklanır) */
    private val cache = BoundedCache<String, Boolean>("tmdb.adult", 3000)

    /** Test/oturum kapanışı için önbelleği boşaltır. */
    fun clearCache() = cache.clear()

    private fun cacheKey(isMovie: Boolean, id: Int) = "${if (isMovie) "movie" else "tv"}:$id"

    /**
     * Verilen TMDB kimliklerinin +18 olup olmadığını çözer.
     *
     * @return kimlik → +18 mi eşlemesi. Ağ hatasında çözülemeyen kimlikler haritada
     *         YER ALMAZ (boş harita = "hiçbiri +18 değil" değildir). Fırlatmaz.
     */
    suspend fun resolveAdultFlags(apiKey: String, isMovie: Boolean, ids: Collection<Int>): Map<Int, Boolean> {
        val wanted = ids.filter { it > 0 }.distinct()
        if (wanted.isEmpty() || apiKey.isBlank()) return emptyMap()

        val result = HashMap<Int, Boolean>(wanted.size)
        val pending = mutableListOf<Int>()
        for (id in wanted) {
            val cached = cache[cacheKey(isMovie, id)]
            if (cached != null) result[id] = cached else pending.add(id)
        }
        if (pending.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                pending.take(MAX_LOOKUPS_PER_RUN).forEach { id ->
                    val adult = fetchDetail(apiKey, isMovie, id)
                    if (adult != null) {
                        cache[cacheKey(isMovie, id)] = adult
                        result[id] = adult
                    }
                }
            }
        }
        if (cache.size > CACHE_LIMIT) cache.clear()
        return result
    }

    /** TMDB ayrıntı yanıtındaki `adult` alanını okur; ağ/hata durumunda null. */
    private suspend fun fetchDetail(apiKey: String, isMovie: Boolean, id: Int): Boolean? {
        val type = if (isMovie) "movie" else "tv"
        val url = "$HOST/$type/$id?api_key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}&language=en-US"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return runCatching {
            PlatformRateLimiter.acquire("tmdb")
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "TMDB adult sorgusu başarısız: HTTP ${response.code} ($type/$id)")
                    return@use null
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                // 401/404 gibi "kayıt yok" yanıtlarında adult alanı bulunmaz → çözülemedi.
                if (!root.has("adult")) return@use null
                root.optBoolean("adult", false)
            }
        }.getOrElse { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "TMDB adult sorgusu hata verdi ($type/$id): ${err.message}")
            null
        }
    }
}
