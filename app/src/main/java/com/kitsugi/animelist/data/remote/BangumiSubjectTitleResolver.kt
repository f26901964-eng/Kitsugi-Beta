package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Bangumi kişi/karakter detay listelerindeki **yapım başlıkları** için ortak Latin
 * (romaji / İngilizce) başlık çözümleyicisi.
 *
 * Bangumi'nin liste uçları (`/v0/persons/{id}/subjects`, `/v0/characters/{id}/subjects`)
 * yalnızca özgün adı (`name`, çoğu zaman Japonca) ve `name_cn` (Çince) döndürür; infobox
 * LISTELERDE yoktur. Bu yüzden kişi/karakter sayfalarının "Karakterler" ve "Yapımlar"
 * sekmeleri CJK başlıkla çiziliyordu. Bu çözümleyici üç kademeli çalışır:
 *
 *  1. [BangumiTitleCache] — kayıt daha önce herhangi bir yerde çözüldüyse ağ isteği YOK.
 *  2. Bangumi v0 subject detayı (`infobox` → `英文名` / `罗马字` / `别名`) — sınırlı ve
 *     önbellekli; bulunan ad kalıcı önbelleğe yazılır.
 *  3. **AniList toplu arama** — Bangumi infobox'ında Latin ad yoksa (çoğu anime/manga)
 *     tek bir GraphQL isteğinde birden çok aliased `Page(media(search: ...))` sorgusu
 *     gönderilir; adayın `title.native` / `synonyms` alanlarından biri Bangumi özgün
 *     adıyla BİREBİR (normalize) eşleşirse romaji + İngilizce alınır ve önbelleklenir.
 *     Yanlış eşleşme yerine "eşleşme yok" tercih edilir.
 *
 * Tüm sonuçlar [BangumiTitleCache]'e kalıcı yazılır: uzun filmografiler ilk ziyarette
 * kısmen, sonraki ziyaretlerde tamamen Latin başlıkla açılır. Çözülemeyen kayıtlar
 * olumsuz önbelleğe alınır ve aynı oturumda tekrar aranmaz.
 */
internal object BangumiSubjectTitleResolver {

    private const val TAG = "BangumiTitleResolver"

    /** Bangumi subject kimliği + hâlâ CJK görünen başlığı + medya türü. */
    internal data class Request(val rawId: Int, val nativeTitle: String, val mediaType: MediaType) {
        companion object {
            /** Liste satırından istek üretir; stableId veya doğrudan rawId kabul edilir, geçersizse null. */
            fun fromRow(mediaId: Int, currentTitle: String, mediaTypeKey: String): Request? {
                val raw = BangumiIdNamespace.rawIdOrNull(mediaId) ?: return null
                return Request(raw, currentTitle, mediaTypeFromKey(mediaTypeKey))
            }
        }
    }

    internal fun mediaTypeFromKey(key: String): MediaType = when (key.trim().lowercase(Locale.ROOT)) {
        "manga" -> MediaType.Manga
        "tv", "movie" -> MediaType.TvShow
        else -> MediaType.Anime
    }

    private const val INFOBOX_PASS_LIMIT = 40
    private const val INFOBOX_PASS_LIMIT_BACKGROUND = 120
    private const val ANILIST_PASS_LIMIT = 24
    private const val ANILIST_PASS_LIMIT_BACKGROUND = 96
    private const val ANILIST_BATCH_SIZE = 6
    private const val INFOBOX_LOOKUP_TIMEOUT_MS = 3_500L
    private const val INFOBOX_BATCH_TIMEOUT_MS = 7_000L
    private const val INFOBOX_BATCH_TIMEOUT_BACKGROUND_MS = 20_000L
    private const val ANILIST_BATCH_TIMEOUT_MS = 9_000L
    private const val ANILIST_BATCH_TIMEOUT_BACKGROUND_MS = 24_000L
    private const val NEGATIVE_TTL_MS = 6 * 60 * 60 * 1000L

    private val infoboxSemaphore = Semaphore(8)
    private val anilistSemaphore = Semaphore(2)

    /** Ağ isteği yapıldığı hâlde Latin ad bulunamayan kayıtlar (tekrar arama yapılmaz). */
    private val negativeCache = BoundedCache<Int, Long>("bangumi.titleResolver.negative", 512)

    /**
     * Verilen satırlar için Latin başlık çözer. Senkron geç kısa, arka plan (background)
     * geç uzun bütçeyle çalışır; ikisi de sonuçları kalıcı önbelleğe yazar.
     *
     * [cacheOnly] modunda yalnızca [BangumiTitleCache] okunur (ağ isteği YOK) — ilk çizimi
     * geciktirmemek için senkron istemci geçi bu modda çalışır; ağ işini arka plan geçi yapar.
     */
    suspend fun resolve(
        requests: List<Request>,
        background: Boolean = false,
        cacheOnly: Boolean = false
    ): Map<Int, BangumiTitleCache.LatinTitles> {
        val result = ConcurrentHashMap<Int, BangumiTitleCache.LatinTitles>()
        val pending = mutableListOf<Request>()
        for (req in requests.filter { it.rawId > 0 }.distinctBy { it.rawId }) {
            val cached = usableLatin(BangumiTitleCache.get(req.rawId))
            if (cached != null) result[req.rawId] = cached else pending += req
        }
        if (pending.isEmpty() || cacheOnly) return result

        // 1) Bangumi v0 subject infobox'u (önbellekli, sınırlı).
        val infoboxLimit = if (background) INFOBOX_PASS_LIMIT_BACKGROUND else INFOBOX_PASS_LIMIT
        val infoboxBatchTimeout = if (background) INFOBOX_BATCH_TIMEOUT_BACKGROUND_MS else INFOBOX_BATCH_TIMEOUT_MS
        val infoboxPending = pending.filter { !isNegative(it.rawId) }.take(infoboxLimit)
        if (infoboxPending.isNotEmpty()) {
            val fetchedNoLatin = ConcurrentHashMap<Int, Boolean>()
            withTimeoutOrNull(infoboxBatchTimeout) {
                coroutineScope {
                    infoboxPending.map { req ->
                        async(Dispatchers.IO) {
                            val localized = try {
                                infoboxSemaphore.withPermit {
                                    withTimeoutOrNull(INFOBOX_LOOKUP_TIMEOUT_MS) {
                                        val subject = KitsugiBangumiDetailClient.loadSubject(req.rawId)
                                            ?: return@withTimeoutOrNull null
                                        BangumiNameLocalizer.subject(subject.name, subject.nameCn, subject.infobox)
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                            val latin = localized?.let { latinOf(it.romaji, it.english, it.native) }
                            if (latin != null) {
                                BangumiTitleCache.put(req.rawId, latin.romaji, latin.english, latin.native)
                                result[req.rawId] = latin
                            } else if (localized != null) {
                                fetchedNoLatin[req.rawId] = true
                            }
                        }
                    }.awaitAll()
                }
            }
            for (rawId in fetchedNoLatin.keys) markNegative(rawId)
        }

        // 2) AniList toplu (aliased) arama — hâlâ CJK kalan anime/manga satırları.
        val anilistLimit = if (background) ANILIST_PASS_LIMIT_BACKGROUND else ANILIST_PASS_LIMIT
        val anilistBatchTimeout = if (background) ANILIST_BATCH_TIMEOUT_BACKGROUND_MS else ANILIST_BATCH_TIMEOUT_MS
        val anilistPending = pending.filter { req ->
            (req.mediaType == MediaType.Anime || req.mediaType == MediaType.Manga) &&
                !isNegative(req.rawId) && !result.containsKey(req.rawId)
        }.take(anilistLimit)
        if (anilistPending.isNotEmpty()) {
            withTimeoutOrNull(anilistBatchTimeout) {
                coroutineScope {
                    anilistPending.chunked(ANILIST_BATCH_SIZE).map { chunk ->
                        async(Dispatchers.IO) {
                            try {
                                anilistSemaphore.withPermit { resolveChunkViaAniList(chunk) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                android.util.Log.w(TAG, "AniList başlık toplu araması başarısız: ${e.message}")
                                null
                            }
                        }
                    }.awaitAll().forEach { chunkResult ->
                        if (chunkResult == null) return@forEach
                        for ((rawId, latin) in chunkResult.resolved) {
                            BangumiTitleCache.put(rawId, latin.romaji, latin.english, latin.native)
                            result[rawId] = latin
                        }
                        for (rawId in chunkResult.unmatched) markNegative(rawId)
                    }
                }
            }
        }
        return result
    }

    // ── AniList aliased arama ─────────────────────────────────────────────────

    internal data class AniListChunkResult(
        val resolved: Map<Int, BangumiTitleCache.LatinTitles>,
        val unmatched: List<Int>
    )

    private suspend fun resolveChunkViaAniList(chunk: List<Request>): AniListChunkResult? {
        val (query, variables) = aliasedQuery(chunk)
        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        return matchAniListResponse(chunk, runCatching { JSONObject(response) }.getOrNull())
    }

    /** Tek istekte N aliased `Page(media(search:))` sorgusu kurar. */
    private fun aliasedQuery(chunk: List<Request>): Pair<String, JSONObject> {
        val sb = StringBuilder("query(")
        chunk.forEachIndexed { index, _ ->
            if (index > 0) sb.append(", ")
            sb.append("\$s").append(index).append(": String, \$t").append(index).append(": MediaType")
        }
        sb.append(") {\n")
        chunk.forEachIndexed { index, _ ->
            sb.append("a").append(index)
                .append(": Page(page: 1, perPage: 5) { media(search: \$s").append(index)
                .append(", type: \$t").append(index)
                .append(", sort: SEARCH_MATCH) { title { romaji english native } synonyms } }\n")
        }
        sb.append("}")
        val variables = JSONObject()
        chunk.forEachIndexed { index, req ->
            variables.put("s$index", req.nativeTitle)
            variables.put("t$index", if (req.mediaType == MediaType.Manga) "MANGA" else "ANIME")
        }
        return sb.toString() to variables
    }

    /**
     * AniList yanıtını birebir (normalize) başlık eşleşmesiyle doğrular. Yalnızca özgün
     * ad / romaji / İngilizce / synonyms alanlarından biri Bangumi başlığıyla aynıysa
     * kabul edilir — yanlış yapımın adı asla gösterilmez.
     */
    internal fun matchAniListResponse(chunk: List<Request>, response: JSONObject?): AniListChunkResult {
        val resolved = HashMap<Int, BangumiTitleCache.LatinTitles>()
        val unmatched = mutableListOf<Int>()
        val data = response?.optJSONObject("data") ?: return AniListChunkResult(emptyMap(), emptyList())
        for ((index, req) in chunk.withIndex()) {
            val target = KitsugiBangumiDetailClient.normalizeTitle(req.nativeTitle)
            if (target.length < 2) {
                unmatched += req.rawId
                continue
            }
            val media = data.optJSONObject("a$index")?.optJSONArray("media").objects()
            var match: BangumiTitleCache.LatinTitles? = null
            for (m in media) {
                val title = m.optJSONObject("title") ?: continue
                val candidates = buildList {
                    add(title.strOrNull("native"))
                    add(title.strOrNull("romaji"))
                    add(title.strOrNull("english"))
                    m.optJSONArray("synonyms")?.let { arr ->
                        for (j in 0 until arr.length()) add(arr.strOrNull(j))
                    }
                }
                val hit = candidates.any { candidate ->
                    val normalized = KitsugiBangumiDetailClient.normalizeTitle(candidate)
                    normalized.length >= 2 && normalized == target
                }
                if (hit) {
                    match = latinOf(
                        title.strOrNull("romaji"),
                        title.strOrNull("english"),
                        title.strOrNull("native")
                    )
                    break
                }
            }
            if (match != null) resolved[req.rawId] = match else unmatched += req.rawId
        }
        return AniListChunkResult(resolved, unmatched)
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    /** Latin (kullanılabilir) romaji/İngilizce içeren önbellek girdisi; yoksa null. */
    private fun usableLatin(titles: BangumiTitleCache.LatinTitles?): BangumiTitleCache.LatinTitles? {
        if (titles == null) return null
        val romaji = usable(titles.romaji)
        val english = usable(titles.english)
        if (romaji == null && english == null) return null
        return BangumiTitleCache.LatinTitles(romaji, english, titles.native)
    }

    private fun latinOf(romaji: String?, english: String?, native: String?): BangumiTitleCache.LatinTitles? {
        val r = usable(romaji)
        val e = usable(english)
        if (r == null && e == null) return null
        return BangumiTitleCache.LatinTitles(r, e, native?.trim()?.takeIf { it.isNotEmpty() })
    }

    private fun usable(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && PreferenceHelpers.isLatinText(it)
    }

    private fun isNegative(rawId: Int): Boolean {
        val at = negativeCache[rawId] ?: return false
        return System.currentTimeMillis() - at < NEGATIVE_TTL_MS
    }

    private fun markNegative(rawId: Int) {
        negativeCache[rawId] = System.currentTimeMillis()
    }

    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().takeIf { it.isNotEmpty() }

    private fun org.json.JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun org.json.JSONArray?.strOrNull(index: Int): String? {
        if (this == null || index < 0 || index >= length()) return null
        return optString(index, "").trim().takeIf { it.isNotEmpty() }
    }
}
