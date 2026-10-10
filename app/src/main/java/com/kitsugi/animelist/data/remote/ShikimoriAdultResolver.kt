package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.auth.PlatformRateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Shikimori +18 (rx/hentai) içerik çözümleyicisi.
 *
 * ## Sorun
 * REST liste yanıtları (`/api/animes`, `/api/mangas`) ve `user_rates` yanıtındaki gömülü
 * `anime`/`manga` nesneleri `AnimeSerializer`/`MangaSerializer` ile üretilir; bu
 * serializer'lar `rating` ve `genres` alanlarını **hiç taşımaz**. Bu yüzden REST
 * üzerinden toplu +18 tespiti yapılamaz: `GET /api/animes?ids[]=…&censored=false`
 * kayıtları döndürür (Shikimori tarafında `ids` varlığı ve `censored=false` hentai
 * filtrelemesini devre dışı bırakır — bkz. `Animes::Filters::Policy#whitelist_by?`),
 * ama yanıtta bakılacak `rating` alanı yoktur. Sonuç: Shikimori içe aktarımında
 * tüm kayıtlar `isAdult = false` ile kaydedilir ve liste sayfasında +18 blur
 * ayarı açık olsa bile uygulanmaz.
 *
 * ## Çözüm
 * GraphQL sorgusu kimlikle (ids) +18 kayıtlarını döndürür ve kimlik doğrulaması
 * istemez (bkz. [ShikimoriPosterResolver] — aynı desen):
 *
 * ```graphql
 * { animes(ids: "1,2,3", limit: 50, censored: false) { id rating genres { name } } }
 * { mangas(ids: "1,2,3", limit: 50, censored: false) { id genres { name } } }
 * ```
 *
 * Not: GraphQL `MangaType` `rating` alanı taşımaz; manga +18 tespiti tür
 * (genres) üzerinden yapılır. `rating` (yalnızca anime) + tür isimleriyle
 * [isShikimoriAdultContent] politikası uygulanır (yalnızca `rx`/hentai → +18;
 * `r`/`r_plus` hariç).
 *
 * Sonuçlar (olumlu VE olumsuz) bellekte önbelleğe alınır; ağ hatasında o grup
 * önbelleğe yazılmaz, sonraki denemede tekrar sorulur.
 */
object ShikimoriAdultResolver {

    private const val TAG = "ShikimoriAdultResolver"

    /** Shikimori'nin güncel kanonik host'u (bkz. ShikimoriPosterResolver.HOST). */
    const val HOST = "https://shikimori.io"

    /** Shikimori GraphQL `animes`/`mangas` sorgularında sayfa başına üst sınır. */
    private const val BATCH_SIZE = 50

    /** Bellek önbelleği bu sayıyı aşarsa sıfırlanır (uzun oturumlarda sınırsız büyümeyi önler). */
    private const val CACHE_LIMIT = 6000

    private const val USER_AGENT = "KitsugiApp/2.4 (Android)"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

    /** Yalnızca testlerde kullanılır; sahte bir sunucuya yönlendirir (ör. `http://127.0.0.1:1234`). */
    @Volatile
    internal var hostOverride: String? = null

    private val activeHost: String
        get() = hostOverride ?: HOST

    /** kimlik → +18 mi? (hem true hem false sonuçlar önbelleğe alınır) */
    private val cache = BoundedCache<String, Boolean>("shikimori.adult", 3000)

    /** GraphQL'de +18 bilgisi taşınan iki varlık türü. */
    enum class Kind(internal val field: String) {
        ANIME("animes"),
        MANGA("mangas")
    }

    /**
     * Verilen kimliklerin +18 olup olmadığını toplu çözer.
     *
     * @return kimlik → +18 mi eşlemesi. Ağ hatası nedeniyle çözülemeyen kimlikler
     *         haritada YER ALMAZ (boş harita ≠ "hiçbiri +18 değil"; bu ayrım
     *         [com.kitsugi.animelist.ui.screens.mylist.AdultFlagBackfillMigration] için önemlidir). Hiçbir zaman fırlatmaz;
     *         en kötü durumda boş harita döner.
     */
    suspend fun resolveAdultFlags(kind: Kind, ids: Collection<Int>): Map<Int, Boolean> {
        val wanted = ids.filter { it > 0 }.distinct()
        if (wanted.isEmpty()) return emptyMap()

        val result = HashMap<Int, Boolean>(wanted.size)
        val pending = mutableListOf<Int>()
        for (id in wanted) {
            val cached = cache[cacheKey(kind, id)]
            if (cached != null) result[id] = cached else pending.add(id)
        }
        if (pending.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (chunk in pending.chunked(BATCH_SIZE)) {
                    val fetched = fetchBatch(kind, chunk) ?: continue
                    for (id in chunk) {
                        // Başarılı yanıtta hiç dönmeyen kimlik = Shikimori'de böyle bir
                        // kayıt yok → +18 değil sayılır ve önbelleğe alınır.
                        val isAdult = fetched[id] ?: false
                        cache[cacheKey(kind, id)] = isAdult
                        result[id] = isAdult
                    }
                }
            }
        }
        trimCacheIfNeeded()
        return result
    }

    /**
     * Verilen kimlikler arasından +18 olanların kimliklerini döndürür.
     * [resolveAdultFlags] üzerinde kurulan zahmetsiz (best-effort) bir kısayoldur.
     */
    suspend fun resolveAdultIds(kind: Kind, ids: Collection<Int>): Set<Int> =
        resolveAdultFlags(kind, ids).filterValues { it }.keys

    /** Test ve oturum kapanışı için önbelleği boşaltır. */
    fun clearCache() {
        cache.clear()
    }

    // ─── İç yardımcılar ───────────────────────────────────────────────────────

    private fun cacheKey(kind: Kind, id: Int): String = "${kind.field}:$id"

    private fun trimCacheIfNeeded() {
        if (cache.size > CACHE_LIMIT) cache.clear()
    }

    /**
     * GraphQL sorgusunu üretir. `animes`/`mangas` kimlikleri virgülle ayrılmış
     * **String** olarak alır. `censored: false` → hentai/yaoi/yuri kayıtları da
     * kimlikle sorulduğunda dönsün. Anime sorgusuna `rating` eklenir; manga
     * şemasında `rating` alanı yoktur, tür (genres) üzerinden tespit yapılır.
     * Kimlikler `Int` olduğundan satır içi yazmak güvenlidir.
     */
    internal fun buildQuery(kind: Kind, ids: List<Int>): String {
        val joined = ids.joinToString(",")
        val limit = ids.size.coerceIn(1, BATCH_SIZE)
        val ratingField = if (kind == Kind.ANIME) " rating" else ""
        return "{ ${kind.field}(ids: \"$joined\", limit: $limit, censored: false) " +
            "{ id$ratingField genres { name } } }"
    }

    /**
     * GraphQL yanıtını ayrıştırır.
     *
     * @return kimlik → +18 mi eşlemesi, yanıt kullanılamazsa **null** (hata ≠ "+18 yok").
     */
    internal fun parseResponse(body: String?, kind: Kind): Map<Int, Boolean>? {
        val raw = body?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        root.optJSONArray("errors")?.let { errors ->
            if (errors.length() > 0) {
                Log.w(TAG, "GraphQL hata döndürdü: ${errors.optJSONObject(0)?.optString("message")}")
            }
        }
        val data = root.optJSONObject("data") ?: return null
        val array = data.optJSONArray(kind.field) ?: return null

        val out = HashMap<Int, Boolean>(array.length())
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val id = node.optString("id").trim().toIntOrNull()
                ?: node.optInt("id").takeIf { it > 0 }
                ?: continue
            val rating = node.optString("rating").takeIf { it.isNotBlank() && it != "null" }
            val genreNames = node.optJSONArray("genres")?.let { genreArray ->
                (0 until genreArray.length()).mapNotNull { index ->
                    genreArray.optJSONObject(index)?.optString("name")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                }
            }.orEmpty()
            out[id] = isShikimoriAdultContent(rating, genreNames)
        }
        return out
    }

    private suspend fun fetchBatch(kind: Kind, ids: List<Int>): Map<Int, Boolean>? {
        if (ids.isEmpty()) return emptyMap()
        val payload = JSONObject().put("query", buildQuery(kind, ids)).toString()
        val request = Request.Builder()
            .url("$activeHost/api/graphql")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            PlatformRateLimiter.acquire("shikimori")
            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GraphQL adult sorgusu başarısız: HTTP ${response.code} (${kind.field}, ${ids.size} kimlik)")
                    return@use null
                }
                parseResponse(response.body?.string(), kind)
            }
        }.getOrElse { err ->
            Log.w(TAG, "GraphQL adult sorgusu hata verdi (${kind.field}): ${err.message}")
            null
        }
    }
}
