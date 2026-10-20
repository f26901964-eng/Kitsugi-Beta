package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.fandom.WikidataClient
import com.kitsugi.animelist.data.fandom.WikidataParsers
import com.kitsugi.animelist.model.MediaType

/**
 * Holds all resolved external IDs for a given anime entry.
 */
data class ResolvedIds(
    val imdbId: String? = null,
    val kitsuId: Int? = null,
    val tmdbId: Int? = null,
    val aniListId: Int? = null,
    val malId: Int? = null,
    val tvdbId: Int? = null,
    val wikidataId: String? = null
)

object KitsugiIdResolver {
    private const val TAG = "KitsugiIdResolver"

    /**
     * Shikimori ID → MAL ID eşleme önbelleği. Değer 0 ise "eşleme yok" (negatif önbellek):
     * ARM ve Shikimori API'sine aynı ID için tekrar tekrar sorulmaz.
     */
    private val shikimoriMalIdCache = BoundedCache<Int, Int>("shikimori.malId", 3000)
    private const val SHIKIMORI_MAL_UNRESOLVED = 0

    /**
     * v2.4.250: resolveIds SONUÇ önbelleği. runGetStreams HER eklenti için (164 paralel!)
     * resolveIds çağırıyordu; her biri 8'e kadar ardışık HTTP (ARM + TMDB) demekti —
     * İzle'ye her basışta ~1000 istek, 75 sn'lik provider bütçesi aramaya sıra gelmeden
     * eriyordu ("sağlam eklentiden bile veri gelmiyor"). İlk eklenti öder, kalanı
     * önbellekten okur. Yalnızca zenginleşmiş sonuçlar önbelleğe alınır; geçici ağ
     * arızaları tekrar deneyebilir kalır. ID'ler değişmez veri → eskitme gerekmez.
     */
    private val resolvedIdsCache = BoundedCache<String, ResolvedIds>("ids.resolved", 500)

    /**
     * Resolves both IMDb ID and Kitsu ID in a single ARM API call.
     * Falls back to TMDB for the IMDb ID if ARM doesn't have one.
     */
    suspend fun resolveIds(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        mediaType: MediaType? = null,
        kitsuId: Int? = null,
        bangumiId: Int? = null,
        /**
         * ARM ve TMDB IMDb vermezse Wikidata'dan (P345) IMDb kimliğini dene. Akış (stream) yolu bunu
         * açar: IMDb ile sorgulanan Stremio addon'ları ve kimlik doğrulaması bu değere bağlı.
         * Detay sayfaları varsayılan olarak kapalı (ek ağ gecikmesi olmasın).
         */
        wikidataImdbFallback: Boolean = false
    ): ResolvedIds = withContext(Dispatchers.IO) {
        val cacheKey = "m=$malId|a=$aniListId|t=$tmdbId|mt=${mediaType?.name}|k=$kitsuId|b=$bangumiId|wd=$wikidataImdbFallback"
        resolvedIdsCache[cacheKey]?.let { return@withContext it }
        val result = resolveIdsUncached(malId, aniListId, tmdbId, mediaType, kitsuId, bangumiId, wikidataImdbFallback)
        if (result.imdbId != null || result.kitsuId != null || result.tvdbId != null || result.tmdbId != null) {
            resolvedIdsCache[cacheKey] = result
        }
        result
    }

    private suspend fun resolveIdsUncached(
        malId: Int?,
        aniListId: Int?,
        tmdbId: Int? = null,
        mediaType: MediaType? = null,
        kitsuId: Int? = null,
        bangumiId: Int? = null,
        wikidataImdbFallback: Boolean = false
    ): ResolvedIds = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting ID resolution: malParam=$malId, aniListParam=$aniListId, tmdbParam=$tmdbId, mediaType=$mediaType, kitsuParam=$kitsuId, bangumiParam=$bangumiId")

        // Bangumi's 500M stable IDs are not MAL IDs and ARM has no Bangumi namespace.
        // Resolve Bangumi through its cached, title/year-aware cross-reference layer first.
        // NOT: Bu köprü manga erken dönüşünden ÖNCE çalışmalı — eskiden dönüş köprüden önce
        // olduğu için Bangumi kitap/manga satırları MAL/AniList kimliği hiç çözemiyor ve
        // diğer kaynaklara asla yayılamıyordu.
        val bangumiCrossIds = if (
            bangumiId != null && bangumiId > 0 &&
            malId == null && aniListId == null && tmdbId == null && kitsuId == null
        ) {
            try {
                KitsugiBangumiDetailClient.resolveCrossIds(
                    stableOrRawId = bangumiId,
                    mediaType = mediaType ?: MediaType.Anime
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Bangumi ID cross-resolution failed for $bangumiId: ${e.message}")
                null
            }
        } else null

        val lookupMalId = malId ?: bangumiCrossIds?.malId?.takeIf { it in 1..99_999_999 }
        val lookupAniListId = aniListId ?: bangumiCrossIds?.aniListId?.takeIf { it > 0 }
        val lookupTmdbId = tmdbId ?: bangumiCrossIds?.tmdbId?.takeIf { it > 0 }
        val lookupKitsuId = kitsuId ?: bangumiCrossIds?.kitsuId?.takeIf { it > 0 }

        if (mediaType == MediaType.Manga) {
            // Manga ARM'da yer almaz; Bangumi köprüsünün çözdüğü kimlikler aynen döner.
            Log.d(TAG, "Manga yapımları ARM API'de yer almaz; alakasız anime eşleştirmelerini önlemek için ARM sorgusu atlanıyor.")
            return@withContext ResolvedIds(
                malId = lookupMalId,
                aniListId = lookupAniListId,
                kitsuId = lookupKitsuId,
                tmdbId = lookupTmdbId,
                imdbId = bangumiCrossIds?.imdbId,
                tvdbId = bangumiCrossIds?.tvdbId
            )
        }

        var armJson: JSONObject? = null
        val isNonAnime = mediaType == MediaType.Movie || mediaType == MediaType.TvShow
        
        if (lookupKitsuId != null && lookupKitsuId > 0) {
            armJson = fetchArmJson("kitsu", lookupKitsuId)
            if (armJson != null) {
                Log.d(TAG, "Kitsu mapping prioritized: Successfully fetched ARM JSON for kitsu: $lookupKitsuId -> $armJson")
            }
        }
        
        if (armJson == null) {
            if (isNonAnime) {
                armJson = fetchArmJson("themoviedb", lookupTmdbId)
                if (armJson != null) {
                    Log.d(TAG, "Non-anime mapping prioritized: Successfully fetched ARM JSON for themoviedb: $lookupTmdbId -> $armJson")
                } else {
                    armJson = fetchArmJson("myanimelist", lookupMalId)
                    if (armJson != null) {
                        Log.d(TAG, "Successfully fetched ARM JSON for myanimelist: $lookupMalId -> $armJson")
                    } else {
                        armJson = fetchArmJson("anilist", lookupAniListId)
                        if (armJson != null) {
                            Log.d(TAG, "Successfully fetched ARM JSON for anilist: $lookupAniListId -> $armJson")
                        } else {
                            Log.w(TAG, "ARM lookup failed for all sources (tmdbId=$lookupTmdbId, malId=$lookupMalId, aniListId=$lookupAniListId)")
                        }
                    }
                }
            } else {
                armJson = fetchArmJson("myanimelist", lookupMalId)
                if (armJson != null) {
                    Log.d(TAG, "Successfully fetched ARM JSON for myanimelist: $lookupMalId -> $armJson")
                } else {
                    armJson = fetchArmJson("anilist", lookupAniListId)
                    if (armJson != null) {
                        Log.d(TAG, "Successfully fetched ARM JSON for anilist: $lookupAniListId -> $armJson")
                    } else {
                        armJson = fetchArmJson("themoviedb", lookupTmdbId)
                        if (armJson != null) {
                            Log.d(TAG, "Successfully fetched ARM JSON for themoviedb: $lookupTmdbId -> $armJson")
                        } else {
                            Log.w(TAG, "ARM lookup failed for all sources (malId=$lookupMalId, aniListId=$lookupAniListId, tmdbId=$lookupTmdbId)")
                        }
                    }
                }
            }
        }

        val imdbFromArm = armJson?.optNullableString("imdb")
        val kitsuFromArm = armJson?.let {
            val v = it.optInt("kitsu", -1)
            if (v > 0) v else null
        }
        val tmdbFromArm = armJson?.let {
            val v = it.optInt("themoviedb", -1)
            if (v > 0) v else null
        }
        val aniListFromArm = armJson?.let {
            val v = it.optInt("anilist", -1)
            if (v > 0) v else null
        }
        val malFromArm = armJson?.let {
            val v = it.optInt("myanimelist", -1)
            if (v > 0) v else null
        }
        val tvdbFromArm = armJson?.let {
            val v = it.optInt("thetvdb", -1)
            if (v > 0) v else null
        }
        val wikidataFromArm = armJson?.optNullableString("wikidata")

        // Use provided tmdbId directly if ARM didn't find one
        val finalTmdbId = tmdbFromArm ?: lookupTmdbId ?: resolveTmdbId(lookupMalId, lookupAniListId, lookupKitsuId)

        // If ARM didn't give us an IMDb ID, fall back through TMDB
        var finalImdb = if (!imdbFromArm.isNullOrBlank()) imdbFromArm else bangumiCrossIds?.imdbId
        var finalTvdb = tvdbFromArm ?: bangumiCrossIds?.tvdbId
        var finalWikidata = wikidataFromArm

        if (finalTmdbId != null && finalTmdbId > 0) {
            val isTv = mediaType == MediaType.TvShow || (mediaType != MediaType.Movie && !isNonAnime)
            var extIds = fetchExternalIdsFromTmdb(finalTmdbId, isTv)
            if (extIds.imdbId.isNullOrBlank() && extIds.tvdbId == null && extIds.wikidataId.isNullOrBlank()) {
                // Try the other type just in case
                extIds = fetchExternalIdsFromTmdb(finalTmdbId, !isTv)
            }
            if (!extIds.imdbId.isNullOrBlank()) {
                finalImdb = extIds.imdbId
            }
            if (extIds.tvdbId != null && extIds.tvdbId > 0) {
                finalTvdb = extIds.tvdbId
            }
            if (!extIds.wikidataId.isNullOrBlank()) {
                finalWikidata = extIds.wikidataId
            }
        }

        val finalAniList = aniListFromArm ?: lookupAniListId
        val finalMal = malFromArm ?: lookupMalId

        // Son çare: Wikidata P345 (IMDb). ARM/TMDB'nin kaçırdığı anime/dizi kayıtları için köprü.
        if (finalImdb.isNullOrBlank() && wikidataImdbFallback) {
            finalImdb = imdbViaWikidata(
                wikidataQid = finalWikidata,
                malId = finalMal,
                aniListId = finalAniList,
                kitsuId = kitsuFromArm ?: lookupKitsuId,
                tmdbId = finalTmdbId,
                isMovie = mediaType == MediaType.Movie
            )
        }

        Log.d(TAG, "resolveIds complete → imdb=$finalImdb kitsu=${kitsuFromArm ?: lookupKitsuId} tmdb=$finalTmdbId anilist=$finalAniList mal=$finalMal tvdb=$finalTvdb wikidata=$finalWikidata")
        ResolvedIds(
            imdbId = finalImdb,
            kitsuId = kitsuFromArm ?: lookupKitsuId,
            tmdbId = finalTmdbId,
            aniListId = finalAniList,
            malId = finalMal,
            tvdbId = finalTvdb,
            wikidataId = finalWikidata
        )
    }

    /**
     * Wikidata üzerinden IMDb (P345) çözümü. Önce bilinen QID doğrudan okunur; yoksa MAL / AniList /
     * Kitsu / TMDB kimlikleri Wikidata'da benzersiz bir kayda eşlenir ve o kaydın P345'i alınır.
     * Her adım ağ hatasında/çelişkide sessizce bir sonrakine geçer; hiçbiri vermezse null.
     */
    private suspend fun imdbViaWikidata(
        wikidataQid: String?,
        malId: Int?,
        aniListId: Int?,
        kitsuId: Int?,
        tmdbId: Int?,
        isMovie: Boolean
    ): String? {
        val qid = wikidataQid?.takeIf { WikidataParsers.isQid(it) }
        if (qid != null) {
            val direct = try {
                WikidataClient.getEntities(listOf(qid))?.get(qid)?.imdbId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wikidata QID $qid okunamadı: ${e.message}")
                null
            }
            if (direct != null) return direct
        }

        val lookups = buildList {
            if (malId != null && malId in 1..99_999_999) add("P4086" to malId.toString())
            if (aniListId != null && aniListId > 0) add("P8729" to aniListId.toString())
            if (kitsuId != null && kitsuId > 0) add("P11495" to kitsuId.toString())
            if (tmdbId != null && tmdbId > 0) add((if (isMovie) "P4947" else "P4983") to tmdbId.toString())
        }
        for ((property, value) in lookups.take(3)) {
            val claims = try {
                WikidataClient.identityClaims(property, value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wikidata $property=$value araması başarısız: ${e.message}")
                null
            }
            val imdb = claims?.imdbId
            if (imdb != null) {
                Log.d(TAG, "IMDb Wikidata üzerinden bulundu: $property=$value → $imdb")
                return imdb
            }
        }
        return null
    }

    /**
     * Resolves the IMDb ID for a media entry, using either MAL ID or AniList ID.
     */
    suspend fun getImdbId(malId: Int?, aniListId: Int?): String? =
        resolveIds(malId, aniListId).imdbId

    /**
     * MAL ID → Shikimori ID (ARM).
     *
     * Shikimori REST endpoint'leri KENDİ numeric ID'lerini bekler. Jikan/MAL verisi
     * Shikimori'ye fallback yaparken MAL ID'sini doğrudan geçmek YANLIŞ animenin
     * karakter/ekip verisini döndürür — bu dönüşüm ile doğru ID sağlanır.
     * Çözüm bulunamazsa null döner (çağıran taraf fallback'i atlar).
     */
    fun resolveShikimoriIdFromMal(malId: Int?): Int? {
        if (malId == null || malId <= 0) return null
        val shikiId = fetchArmJson("myanimelist", malId)?.let {
            val v = it.optInt("shikimori", -1)
            if (v > 0) v else null
        }
        if (shikiId != null) {
            Log.d(TAG, "MAL $malId → Shikimori $shikiId (ARM)")
        } else {
            Log.w(TAG, "Shikimori ID çözülmedi için MAL $malId (ARM haritası yok)")
        }
        return shikiId
    }

    /**
     * Shikimori ID → MAL ID (ARM).
     *
     * Shikimori arama sonuçlarında [JikanSearchResult.malId] Shikimori'nin kendi ID'sini
     * taşır; MAL/AniList'e fallback (detay, logo, karakter VA birleştirmesi) için gerçek
     * MAL ID'si çözülmesi gerekir. Shikimori detayındaki myanimelist_id önbellekte
     * yoksa bu ARM dönüşümü kullanılır.
     */
    /**
     * Shikimori ID → MAL ID eşlemesi.
     *
     * ÖNEMLİ: Shikimori ID'si MAL ID'si DEĞİLDİR. Bu eşleme artık üç kademeli ve
     * önbellekli çözülür; bulunamazsa `null` döner (eskiden çağıranlar Shikimori
     * ID'sini MAL ID yerine kullanıp alakasız yapımların verisini çekiyordu):
     *  1. Shikimori detay önbelleğindeki `myanimelist_id` (varsa hiç ağa çıkılmaz),
     *  2. ARM haritası (`source=shikimori`),
     *  3. doğrudan Shikimori REST yanıtındaki `myanimelist_id` (ARM'de harita yoksa).
     */
    fun resolveMalIdFromShikimori(shikimoriId: Int?): Int? {
        if (shikimoriId == null || shikimoriId <= 0) return null

        // 0. Bellek içi önbellek — her sekmede tekrar tekrar ağa çıkılmasın.
        shikimoriMalIdCache[shikimoriId]?.let { cached ->
            return if (cached > 0) cached else null
        }

        // 1. Detay önbelleği (myanimelist_id zaten biliniyorsa)
        val fromDetailCache = DetailCache.getMediaDetail("shikimori", shikimoriId)?.realMalId?.takeIf { it > 0 }
        if (fromDetailCache != null) {
            shikimoriMalIdCache[shikimoriId] = fromDetailCache
            Log.d(TAG, "Shikimori $shikimoriId → MAL $fromDetailCache (detay önbelleği)")
            return fromDetailCache
        }

        // 2. Shikimori'nin KENDİ yanıtındaki `myanimelist_id` (yetkili kaynak; tek istek).
        var malId = fetchMalIdFromShikimoriApi(shikimoriId)

        // 3. Shikimori yanıt vermezse/alan boşsa ARM haritası.
        if (malId == null) {
            malId = fetchArmJson("shikimori", shikimoriId)?.let {
                val v = it.optInt("myanimelist", -1)
                if (v > 0) v else null
            }
        }

        shikimoriMalIdCache[shikimoriId] = malId ?: SHIKIMORI_MAL_UNRESOLVED
        if (malId != null) {
            Log.d(TAG, "Shikimori $shikimoriId → MAL $malId")
        } else {
            Log.w(TAG, "Shikimori $shikimoriId için MAL eşlemesi bulunamadı (ARM + Shikimori API)")
        }
        return malId
    }

    /** Shikimori REST yanıtındaki `myanimelist_id` alanını okur (anime → manga sırasıyla). */
    private fun fetchMalIdFromShikimoriApi(shikimoriId: Int): Int? {
        for (endpoint in listOf("animes", "mangas")) {
            val result = runCatching {
                val url = URL("https://shikimori.io/api/$endpoint/$shikimoriId")
                val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
                val json = JSONObject(response)
                val v = json.optInt("myanimelist_id", -1)
                if (v > 0) v else null
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    /** Fetches the raw ARM JSON for a given source+id pair. Returns null on failure or missing id. */
    private fun fetchArmJson(source: String, id: Int?): JSONObject? {
        if (id == null || id <= 0) return null
        val url = runCatching {
            URL("https://arm.haglund.dev/api/v2/ids?source=$source&id=$id")
        }.getOrNull()
        if (url == null) {
            Log.e(TAG, "fetchArmJson: Invalid URL construction for source=$source id=$id")
            return null
        }
        return try {
            val response = KitsugiApiBase.executeGetRequest(url)
            if (response == null) {
                Log.w(TAG, "fetchArmJson: Network failure or empty response for URL: $url")
                return null
            }
            val trimmed = response.trim()
            if (trimmed == "null" || trimmed == "[]" || trimmed.isEmpty()) {
                Log.d(TAG, "fetchArmJson: Valid empty response (media not mapped/obscure) for URL: $url")
                return null
            }
            // ARM may return an array with one element — unwrap it
            if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed)
                if (arr.length() == 0) {
                    Log.d(TAG, "fetchArmJson: Empty array returned for URL: $url")
                    return null
                }
                arr.optJSONObject(0)
            } else {
                JSONObject(trimmed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchArmJson: Exception occurred during fetch for source=$source ID=$id: ${e.message}", e)
            null
        }
    }

    private suspend fun resolveTmdbId(malId: Int?, aniListId: Int?, kitsuId: Int? = null): Int? = withContext(Dispatchers.IO) {
        val kitsuJson = if (kitsuId != null && kitsuId > 0) fetchArmJson("kitsu", kitsuId) else null
        val kitsuTmdbVal = kitsuJson?.optInt("themoviedb", -1)
        if (kitsuTmdbVal != null && kitsuTmdbVal > 0) {
            return@withContext kitsuTmdbVal
        }

        val malJson = if (malId != null && malId > 0) fetchArmJson("myanimelist", malId) else null
        val malTmdbVal = malJson?.optInt("themoviedb", -1)
        if (malTmdbVal != null && malTmdbVal > 0) {
            return@withContext malTmdbVal
        }

        val aniJson = if (aniListId != null && aniListId > 0) fetchArmJson("anilist", aniListId) else null
        val aniTmdbVal = aniJson?.optInt("themoviedb", -1)
        if (aniTmdbVal != null && aniTmdbVal > 0) {
            return@withContext aniTmdbVal
        }
        null
    }

    private fun fetchImdbFromTmdb(tmdbId: Int): String? {
        val apiKey = TmdbApiClient.getActiveApiKey()

        // Probe TV external IDs first
        try {
            val tvUrl = URL("https://api.themoviedb.org/3/tv/$tmdbId/external_ids?api_key=$apiKey")
            val tvResponse = KitsugiApiBase.executeGetRequest(tvUrl)
            if (tvResponse != null) {
                val json = JSONObject(tvResponse)
                val imdbId = json.optNullableString("imdb_id")
                if (!imdbId.isNullOrBlank()) return imdbId
            }
        } catch (e: Exception) {
            // Ignore and try movie
        }

        // Try movie details
        try {
            val movieUrl = URL("https://api.themoviedb.org/3/movie/$tmdbId?api_key=$apiKey")
            val movieResponse = KitsugiApiBase.executeGetRequest(movieUrl)
            if (movieResponse != null) {
                val json = JSONObject(movieResponse)
                val imdbId = json.optNullableString("imdb_id")
                if (!imdbId.isNullOrBlank()) return imdbId
            }
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }

    data class TmdbExternalIds(
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val wikidataId: String? = null
    )

    private fun fetchExternalIdsFromTmdb(tmdbId: Int, isTv: Boolean): TmdbExternalIds {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val typePath = if (isTv) "tv" else "movie"
        return try {
            val url = URL("https://api.themoviedb.org/3/$typePath/$tmdbId/external_ids?api_key=$apiKey")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return TmdbExternalIds()
            val json = JSONObject(response)
            val imdbId = json.optNullableString("imdb_id")
            val tvdbId = json.optInt("tvdb_id", -1).takeIf { it > 0 }
            val wikidataId = json.optNullableString("wikidata_id")
            TmdbExternalIds(imdbId = imdbId, tvdbId = tvdbId, wikidataId = wikidataId)
        } catch (e: Exception) {
            Log.w(TAG, "fetchExternalIdsFromTmdb failed for tmdbId=$tmdbId isTv=$isTv: ${e.message}")
            TmdbExternalIds()
        }
    }

    /**
     * KitsugiEpisodeRatingsRepository gibi dış sınıflar için TMDB external_ids erişimi.
     * isTv=true → /tv/{id}/external_ids, isTv=false → /movie/{id}/external_ids
     */
    fun fetchExternalIds(tmdbId: Int, isTv: Boolean): TmdbExternalIds =
        fetchExternalIdsFromTmdb(tmdbId, isTv)

    // JSON Helper
    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key)
    }
}
