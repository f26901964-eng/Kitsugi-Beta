package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.local.MediaMetaCacheDao
import com.kitsugi.animelist.data.local.MediaMetaCacheEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.utils.PreferenceHelpers

/**
 * KitsugiEpisodeRatingsRepository
 *
 * Hem AniList hem MAL kaynaklı animeler için bölüm puanlarını çeker.
 *
 * AniList → tmdbId zaten externalLinks'ten gelir
 * MAL     → arm.haglund.dev ile mal_id → tmdb_id dönüşümü yapılır (ücretsiz, API key gerekmez)
 *
 * SeriesGraph API: /api/shows/{tmdbId}/season-ratings
 * Döndürülen tip: Map<Pair<seasonNumber, episodeNumber>, voteAverage>
 */
object KitsugiEpisodeRatingsRepository {

    private const val TAG = "KitsugiEpisodeRatings"
    private const val CACHE_TTL_MS = 30L * 60L * 1000L // 30 dakika



    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // Room Veritabanı
    private var database: KitsugiDatabase? = null
    private val dao: MediaMetaCacheDao? get() = database?.mediaMetaCacheDao()
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (database == null) {
            database = KitsugiDatabase.getDatabase(context.applicationContext)
        }
    }

    private suspend fun isTmdbEnabled(): Boolean {
        val context = appContext ?: return true
        return try {
            com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first().tmdbEnabled
        } catch (e: Exception) {
            true
        }
    }

    private suspend fun isTmdbArtworkEnabled(): Boolean {
        val context = appContext ?: return true
        return try {
            val settings = com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first()
            settings.tmdbEnabled && settings.tmdbUseArtwork
        } catch (e: Exception) {
            true
        }
    }

    /** Fanart.tv ayarlarını döner (apiKey, enabled). Built-in API anahtarı her zaman fallback olarak kullanılır. */
    private suspend fun getFanartSettings(): Pair<Boolean, String> {
        val context = appContext ?: return Pair(true, FanartApiClient.getActiveApiKey())
        return try {
            val settings = com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first()
            // Kullanıcı toggle'ı kapattıysa devre dışı; aksi hâlde built-in key ile devam et
            val enabled = settings.fanartTvEnabled
            val apiKey = FanartApiClient.getActiveApiKey(settings.fanartTvApiKey)
            Pair(enabled, apiKey)
        } catch (e: Exception) {
            Pair(true, FanartApiClient.getActiveApiKey())
        }
    }

    private suspend fun isAnyLogoSourceEnabled(): Boolean =
        isTmdbArtworkEnabled() || getFanartSettings().first

    /**
     * Kullanıcının ayarlardan seçtiği TMDB dil tercihini döner.
     * Fanart.tv (ISO 639-1) ve TMDB (iso_639_1) ile uyumlu kısa kod.
     * Örn: "tr", "en", "ja", "fr", "de", "es", "it", "ru", "zh"
     */
    suspend fun getPreferredImageLanguage(): String {
        val context = appContext ?: return "tr"
        return try {
            val settings = com.kitsugi.animelist.data.settings.SettingsDataStore(context).settingsFlow.first()
            val lang = settings.tmdbLanguage.trim().lowercase()
            // TMDB bazen "en-US" gibi dönebilir; ilk parçayı al
            lang.replace('_', '-').split('-', limit = 2).first().ifBlank { "tr" }
        } catch (e: Exception) {
            "tr"
        }
    }

    /**
     * Dil kodunu Fanart.tv / TMDB uyumlu hale getirir.
     * "en-US" → "en", "tr-TR" → "tr", boş → "en"
     */
    fun normalizeLanguageCode(language: String?): String {
        val normalized = language?.trim()?.lowercase()?.replace('_', '-')?.takeIf { it.isNotBlank() }
            ?: return "en"
        return normalized.split('-', limit = 2).first().ifBlank { "en" }
    }

    /**
     * Bu kayıt bir film logosu mu istiyor?
     * Anime filmleri çoğu kaynakta [MediaType.Anime] olarak gelir; format / başlık / alt yazı da bakılır.
     */
    fun prefersMovieLogo(
        type: com.kitsugi.animelist.model.MediaType?,
        format: String? = null,
        rawFormat: String? = null,
        subtitle: String? = null,
        title: String? = null
    ): Boolean {
        if (type == com.kitsugi.animelist.model.MediaType.Movie) return true
        if (type == com.kitsugi.animelist.model.MediaType.TvShow) return false
        val hints = listOfNotNull(format, rawFormat, subtitle, title).joinToString(" ")
        if (hints.isBlank()) return false
        return Regex("""(?i)(^|[^a-z0-9])(movie|film|映画|剧场版|劇場版)([^a-z0-9]|$)""").containsMatchIn(hints)
    }

    /** Galeri sıralaması: tercih edilen dil → metinsiz → İngilizce → diğer. */
    fun galleryLanguageRank(language: String?, preferred: String): Int {
        val lang = language?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_').orEmpty()
        val wanted = normalizeLanguageCode(preferred)
        return when {
            lang == wanted -> 0
            lang.isBlank() || lang == "00" || lang == "null" -> 1
            lang == "en" -> 2
            else -> 3
        }
    }

    /**
     * ARM veya animeapi JSON yanıtındaki `media` veya `themoviedb_type` alanını okuyarak
     * bu TMDB kaydının türünü ("movie" veya "tv") [DetailCache.tmdbMediaCache]'e yazar.
     *
     * Bu önbellek TVDB sızıntısını önlemenin kritik anahtarıdır: TVDB anime filmlerini
     * dizinin 0. sezonu olarak tutar. Film kaydı için bu TVDB ID'si ile fanart.tv TV
     * sorgusu yapılırsa DİZİNİN logosu/afişi gelir (Chainsaw Man: Reze-hen → sarı dizi
     * logosu vakası). `media` alanı bu tuzağı ayırt eden tek yetkili işarettir.
     */
    private fun cacheTmdbMediaKind(tmdbId: Int?, json: JSONObject) {
        if (tmdbId == null || tmdbId <= 0) return
        val armMedia = json.optString("media", "").trim().lowercase()
        val apiType = json.optString("themoviedb_type", "").trim().lowercase()
        val kind = when {
            armMedia == "movie" || apiType == "movie" -> "movie"
            armMedia == "tv" || apiType == "tv" || apiType == "series" -> "tv"
            else -> null
        }
        if (kind != null) {
            DetailCache.tmdbMediaCache[tmdbId] = kind
            Log.d(TAG, "Media kind cached: tmdbId=$tmdbId → $kind")
        }
    }

    /**
     * Yalnızca TMDB ID bilinen kayıtlar için medya türünü ARM'den sorgular (önbellekli).
     * ARM'de eşleme yoksa (404/400/boş) null yazılır → "kontrol edildi, film değil".
     */
    private fun queryMediaKindFromArm(tmdbId: Int): String? {
        if (DetailCache.tmdbMediaCache.containsKey(tmdbId)) {
            return DetailCache.tmdbMediaCache[tmdbId]
        }
        val kind = runCatching {
            val url = URL("https://arm.haglund.dev/api/v2/ids?source=themoviedb&id=$tmdbId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val trimmed = response.trim()
            // ARM bazen tek elemanlı dizi döndürür — sar (fetchArmJson ile aynı davranış).
            val json = runCatching {
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) null else arr.optJSONObject(0)
                } else {
                    JSONObject(trimmed)
                }
            }.getOrNull() ?: return@runCatching null
            cacheTmdbMediaKind(tmdbId, json)
            json.optString("media", "").trim().lowercase().takeIf { it == "movie" || it == "tv" }
        }.getOrNull()
        // Negatif önbellek: aynı ID için ARM'ye tekrar sorulmaz.
        DetailCache.tmdbMediaCache[tmdbId] = kind
        return kind
    }

    /**
     * Bu TMDB ID'si bir FİLM kaydına mı ait? Önbellek → gerekirse ARM.
     * Çağıranlar (logo, galeri) tür bilgisine güvenemediğinde (listeler filmleri
     * "Anime/TV" olarak getirebilir) bu yöntemle kesinleştirir.
     */
    suspend fun isTmdbMovie(tmdbId: Int?): Boolean = withContext(Dispatchers.IO) {
        if (tmdbId == null || tmdbId <= 0) return@withContext false
        if (DetailCache.tmdbMediaCache.containsKey(tmdbId)) {
            return@withContext DetailCache.tmdbMediaCache[tmdbId] == "movie"
        }
        queryMediaKindFromArm(tmdbId) == "movie"
    }

    // ── All caches are consolidated in DetailCache (app-lifetime singleton) ──
    // References kept as local aliases for readability
    private val ratingsCache get() = DetailCache.episodeRatingsCache
    private val inFlight = mutableMapOf<Int, Deferred<Map<Pair<Int, Int>, Double>>>()

    // malId → tmdbId önbelleği (ARM API sonuçları)
    private val malToTmdbCache get() = DetailCache.malToTmdbCache

    // tmdbId → tvdbId önbelleği
    private val tmdbToTvdbCache get() = DetailCache.tmdbToTvdbCache

    // tmdbId → logo URL önbelleği
    private val logoSelectionCache get() = DetailCache.logoSelectionCache
    private data class LogoRequestKey(
        val tmdbId: Int,
        val tmdbArtwork: Boolean,
        val fanart: Boolean,
        val language: String,
        val isMovie: Boolean,
        val season: Int
    )
    private val logoInFlight = mutableMapOf<LogoRequestKey, Deferred<String?>>()
    /** containsKey ile saklanan null'u, önbellekte olmama durumundan ayırır. */
    private val NO_LOGO_CACHE = Any()

    data class TmdbEpisodeDto(
        val episodeNumber: Int,
        val name: String?,
        val overview: String?,
        val stillPath: String?,
        val airDate: String?
    )

    private val tmdbEpisodesInFlight = mutableMapOf<Pair<Int, Int>, Deferred<List<TmdbEpisodeDto>>>()

    // Helper to convert between TmdbEpisodeDto and DetailCache.TmdbEpisodeDtoCached
    private fun TmdbEpisodeDto.toCached() = DetailCache.TmdbEpisodeDtoCached(
        episodeNumber = episodeNumber, name = name, overview = overview,
        stillPath = stillPath, airDate = airDate
    )
    private fun DetailCache.TmdbEpisodeDtoCached.toDto() = TmdbEpisodeDto(
        episodeNumber = episodeNumber, name = name, overview = overview,
        stillPath = stillPath, airDate = airDate
    )


    /**
     * AniList kaynağı için — tmdbId doğrudan biliniyorsa kullan
     */
    suspend fun getEpisodeRatings(tmdbId: Int): Map<Pair<Int, Int>, Double> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyMap()
        if (tmdbId <= 0) return@withContext emptyMap()
        fetchWithCache(tmdbId)
    }

    /**
     * MAL kaynağı için — malId'yi önce TMDB ID'ye çevir, sonra puan çek
     * ARM API: https://arm.haglund.dev/api/v2/ids?source=myanimelist&id={malId}
     */
    suspend fun getEpisodeRatingsByMalId(malId: Int): Map<Pair<Int, Int>, Double> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyMap()
        if (malId <= 0) return@withContext emptyMap()

        // MAL → TMDB ID çevrim (önbellekli)
        val tmdbId = resolveTmdbIdFromMal(malId) ?: return@withContext emptyMap()
        fetchWithCache(tmdbId)
    }

    /**
     * AniList kaynağı için — aniListId üzerinden ARM API'si ile TMDB ID bul, sonra puan çek.
     * TMDB ID zaten biliniyorsa (externalLinks'ten çıkarıldıysa) bunu kullanmak daha verimlidir.
     * ARM API: https://arm.haglund.dev/api/v2/ids?source=anilist&id={aniListId}
     */
    suspend fun resolveTmdbIdFromAniList(aniListId: Int): Int? = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext null
        if (aniListId <= 0) return@withContext null
        val cacheKey = -aniListId

        // Önbellek kontrolü — tamamen mutex içinde
        val isCached = mutex.withLock { malToTmdbCache.containsKey(cacheKey) }
        if (isCached) return@withContext mutex.withLock { malToTmdbCache[cacheKey] }

        // Room önbellek kontrolü
        val cachedEntity = runCatching { dao?.getByAniListId(aniListId) }.getOrNull()
        if (cachedEntity != null) {
            val id = cachedEntity.tmdbId
            mutex.withLock { malToTmdbCache[cacheKey] = id }
            if (cachedEntity.tvdbId != null && cachedEntity.tvdbId > 0) {
                mutex.withLock { tmdbToTvdbCache[id] = cachedEntity.tvdbId }
            }
            Log.d(TAG, "Room hit: aniListId=$aniListId → tmdbId=$id")
            return@withContext id
        }

        var tmdbId = runCatching {
            val url = URL("https://arm.haglund.dev/api/v2/ids?source=anilist&id=$aniListId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val json = JSONObject(response)
            val tmdbValEarly = json.optInt("themoviedb", -1).takeIf { it > 0 }
            cacheTmdbMediaKind(tmdbValEarly, json)
            val tvdbVal = json.optInt("thetvdb", -1).takeIf { it > 0 }
            // Filmin thetvdb'si DİZİNİN TVDB ID'sidir; filme bağlanırsa fanart-TV dizi sanatı sızar.
            if (tvdbVal != null && tmdbValEarly != null &&
                DetailCache.tmdbMediaCache[tmdbValEarly] != "movie"
            ) {
                mutex.withLock { tmdbToTvdbCache[tmdbValEarly] = tvdbVal }
            }
            if (json.isNull("themoviedb")) {
                if (!json.isNull("myanimelist")) {
                    val malId = json.optInt("myanimelist", -1)
                    if (malId > 0) {
                        val resolved = resolveTmdbIdFromMal(malId)
                        if (resolved != null) {
                            val existing = dao?.getByTmdbId(resolved)
                            val updated = existing?.copy(aniListId = aniListId, malId = malId, tvdbId = tvdbVal ?: existing.tvdbId) ?: MediaMetaCacheEntity(
                                tmdbId = resolved,
                                malId = malId,
                                aniListId = aniListId,
                                logoUrl = null,
                                logoNotFound = false,
                                tvdbId = tvdbVal
                            )
                            dao?.insert(updated)
                            Log.d(TAG, "Room write (both MAL & AniList): tmdbId=$resolved")
                        }
                        resolved
                    } else null
                } else null
            } else {
                val value = json.optInt("themoviedb", -1)
                if (value > 0) {
                    val malId = if (!json.isNull("myanimelist")) json.optInt("myanimelist", -1) else null
                    val cleanMalId = if (malId != null && malId > 0) malId else null
                    val existing = dao?.getByTmdbId(value)
                    val updated = existing?.copy(aniListId = aniListId, malId = cleanMalId ?: existing.malId, tvdbId = tvdbVal ?: existing.tvdbId) ?: MediaMetaCacheEntity(
                        tmdbId = value,
                        malId = cleanMalId,
                        aniListId = aniListId,
                        logoUrl = null,
                        logoNotFound = false,
                        tvdbId = tvdbVal
                    )
                    dao?.insert(updated)
                    Log.d(TAG, "Room write (AniList): aniListId=$aniListId → tmdbId=$value")
                    value
                } else null
            }
        }.getOrElse {
            Log.w(TAG, "ARM API lookup failed for aniListId=$aniListId: ${it.message}")
            null
        }

        // Fallback to animeapi.my.id if ARM failed or returned null
        if (tmdbId == null) {
            tmdbId = runCatching {
                val url = URL("https://animeapi.my.id/anilist/$aniListId")
                val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
                val json = JSONObject(response)
                val value = json.optInt("themoviedb", -1)
                cacheTmdbMediaKind(value.takeIf { it > 0 }, json)
                val tvdbVal = json.optInt("thetvdb", -1).takeIf { it > 0 }
                if (value > 0) {
                    if (tvdbVal != null && DetailCache.tmdbMediaCache[value] != "movie") {
                        mutex.withLock { tmdbToTvdbCache[value] = tvdbVal }
                    }
                    val malId = if (json.isNull("myanimelist")) null else json.optInt("myanimelist", -1).takeIf { it > 0 }
                    val existing = dao?.getByTmdbId(value)
                    val updated = existing?.copy(aniListId = aniListId, malId = malId ?: existing.malId, tvdbId = tvdbVal ?: existing.tvdbId) ?: MediaMetaCacheEntity(
                        tmdbId = value,
                        malId = malId,
                        aniListId = aniListId,
                        logoUrl = null,
                        logoNotFound = false,
                        tvdbId = tvdbVal
                    )
                    dao?.insert(updated)
                    Log.d(TAG, "Room write (AnimeAPI AniList): aniListId=$aniListId → tmdbId=$value")
                    value
                } else null
            }.getOrElse {
                Log.w(TAG, "AnimeAPI lookup failed for aniListId=$aniListId: ${it.message}")
                null
            }
        }

        mutex.withLock { malToTmdbCache[cacheKey] = tmdbId }
        Log.d(TAG, "ARM lookup (with fallback): aniListId=$aniListId → tmdbId=$tmdbId")
        tmdbId
    }

    suspend fun getEpisodeRatingsByAniListId(aniListId: Int): Map<Pair<Int, Int>, Double> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyMap()
        if (aniListId <= 0) return@withContext emptyMap()
        val tmdbId = resolveTmdbIdFromAniList(aniListId) ?: return@withContext emptyMap()
        fetchWithCache(tmdbId)
    }

    /** Tüm önbellekleri temizler — DetailCache.clear() ile yapılır */
    fun clearCache() {
        DetailCache.clear()
        inFlight.clear()
        logoInFlight.clear()
        tmdbEpisodesInFlight.clear()
    }

    /**
     * TMDB ID'ye göre eserin kendi logosunu döner.
     *
     * Filmlerde film logosu (Fanart movie + TMDB movie) dizi logosundan önce gelir;
     * aksi halde anime filmleri ana serinin logosunu gösteriyordu.
     * Dizilerde önce dizi logosu, yoksa film logosu denenir.
     * Sezon > 1 ise dil önceliğini bozmadan sezona etiketli logo tercih edilir.
     *
     * Dil: ayarlardaki TMDB dili (ör. "en" → İngilizce logo). Yoksa metinsiz, sonra İngilizce, sonra herhangi biri.
     * Eski Room logo önbelleği dil ve tür tutmadığı için seçimde kullanılmaz.
     */
    suspend fun getLogoUrl(
        tmdbId: Int,
        seasonNumber: Int? = null,
        language: String? = null,
        isMovie: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext null

        val effectiveIsMovie = isMovie || isTmdbMovie(tmdbId)
        val preferredLang = normalizeLanguageCode(language ?: getPreferredImageLanguage())
        val season = seasonNumber?.takeIf { it > 1 } ?: 0
        val tmdbArtworkEnabled = isTmdbArtworkEnabled()
        val (fanartEnabled, fanartApiKey) = getFanartSettings()
        if (!tmdbArtworkEnabled && !fanartEnabled) return@withContext null

        val requestKey = LogoRequestKey(tmdbId, tmdbArtworkEnabled, fanartEnabled, preferredLang, effectiveIsMovie, season)
        val cacheKey = "${requestKey.tmdbId}|${requestKey.language}|${requestKey.isMovie}|${requestKey.season}|${requestKey.tmdbArtwork}|${requestKey.fanart}"
        val cached = mutex.withLock {
            if (logoSelectionCache.containsKey(cacheKey)) logoSelectionCache[cacheKey] else NO_LOGO_CACHE
        }
        if (cached !== NO_LOGO_CACHE) return@withContext cached as String?

        val deferred = mutex.withLock {
            logoInFlight[requestKey] ?: scope.async {
                try {
                    val finalUrl = selectLogoUrl(
                        tmdbId = tmdbId,
                        season = season,
                        language = preferredLang,
                        isMovie = effectiveIsMovie,
                        tmdbArtworkEnabled = tmdbArtworkEnabled,
                        fanartEnabled = fanartEnabled,
                        fanartApiKey = fanartApiKey
                    )
                    mutex.withLock { logoSelectionCache[cacheKey] = finalUrl }
                    Log.d(TAG, "Logo selected: tmdbId=$tmdbId movie=$effectiveIsMovie season=$season lang=$preferredLang → $finalUrl")
                    finalUrl
                } catch (e: Exception) {
                    Log.w(TAG, "Logo fetch failed for tmdbId=$tmdbId: ${e.javaClass.simpleName}")
                    null
                } finally {
                    mutex.withLock { logoInFlight.remove(requestKey) }
                }
            }.also { logoInFlight[requestKey] = it }
        }
        deferred.await()
    }

    private suspend fun selectLogoUrl(
        tmdbId: Int,
        season: Int,
        language: String,
        isMovie: Boolean,
        tmdbArtworkEnabled: Boolean,
        fanartEnabled: Boolean,
        fanartApiKey: String
    ): String? {
        // Film kayıtları asla TV/dizi/sezon zincirine girmemeli (yalnızca fanart movies + TMDB movie).
        // Aksi halde filmin üzerinde dizinin logosu gösterilir (Chainsaw Man Reze-hen vakası).
        if (isMovie) {
            return movieLogoUrl(tmdbId, language, fanartEnabled, fanartApiKey, tmdbArtworkEnabled)
        }

        // Sezon logosu yalnızca tercih edilen dildeyse ana logodan önce gelir.
        // Dil uymayan sezon logosu, doğru dildeki ana logoyu ezmez.
        if (season > 1) {
            seasonLogoUrl(tmdbId, season, language, strictLanguage = true, fanartEnabled, fanartApiKey, tmdbArtworkEnabled)
                ?.let { return it }
        }

        val primary = seriesLogoUrl(tmdbId, season, language, fanartEnabled, fanartApiKey, tmdbArtworkEnabled)
        if (!primary.isNullOrBlank()) return primary

        if (season > 1) {
            seasonLogoUrl(tmdbId, season, language, strictLanguage = false, fanartEnabled, fanartApiKey, tmdbArtworkEnabled)
                ?.let { return it }
        }

        val secondary = movieLogoUrl(tmdbId, language, fanartEnabled, fanartApiKey, tmdbArtworkEnabled)
        if (!secondary.isNullOrBlank()) return secondary

        // SeriesGraph dil bilgisi taşımaz; yalnızca başka logo yoksa.
        return fetchLogoFromSeriesGraph(tmdbId)
    }

    private suspend fun seriesLogoUrl(
        tmdbId: Int,
        season: Int,
        language: String,
        fanartEnabled: Boolean,
        fanartApiKey: String,
        tmdbArtworkEnabled: Boolean
    ): String? {
        val fanart = if (fanartEnabled) {
            val tvdbId = resolveTvdbIdFromTmdb(tmdbId)
            if (tvdbId != null && tvdbId > 0) {
                runCatching {
                    FanartApiClient.fetchBestLogo(
                        tvdbId = tvdbId,
                        apiKey = fanartApiKey,
                        language = language,
                        seasonNumber = season.takeIf { it > 1 }
                    )
                }.getOrNull()
            } else null
        } else null
        if (!fanart.isNullOrBlank()) return fanart
        return if (tmdbArtworkEnabled) {
            queryTmdbImages(tmdbId, "tv", TmdbApiClient.getActiveApiKey(), language)
                ?.let { "https://image.tmdb.org/t/p/w500$it" }
        } else null
    }

    private fun movieLogoUrl(
        tmdbId: Int,
        language: String,
        fanartEnabled: Boolean,
        fanartApiKey: String,
        tmdbArtworkEnabled: Boolean
    ): String? {
        val fanart = if (fanartEnabled) {
            runCatching { FanartApiClient.fetchBestMovieLogo(tmdbId, fanartApiKey, language) }.getOrNull()
        } else null
        if (!fanart.isNullOrBlank()) return fanart
        return if (tmdbArtworkEnabled) {
            queryTmdbImages(tmdbId, "movie", TmdbApiClient.getActiveApiKey(), language)
                ?.let { "https://image.tmdb.org/t/p/w500$it" }
        } else null
    }

    private suspend fun seasonLogoUrl(
        tmdbId: Int,
        season: Int,
        language: String,
        strictLanguage: Boolean,
        fanartEnabled: Boolean,
        fanartApiKey: String,
        tmdbArtworkEnabled: Boolean
    ): String? {
        if (tmdbArtworkEnabled) {
            queryTmdbSeasonImages(tmdbId, season, language, allowFallback = !strictLanguage)?.let { return it }
        }
        if (fanartEnabled) {
            val tvdbId = resolveTvdbIdFromTmdb(tmdbId)
            if (tvdbId != null && tvdbId > 0) {
                runCatching {
                    FanartApiClient.fetchBestSeasonLogo(
                        tvdbId = tvdbId,
                        seasonNumber = season,
                        apiKey = fanartApiKey,
                        language = language,
                        strictLanguage = strictLanguage
                    )
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    /**
     * Sezon bazlı logo URL'si döner (örn: "Attack on Titan Final Season" logosu).
     *
     * Kaynaklar (öncelik sırasıyla):
     * 1. TMDB Sezon Images API: /tv/{tmdbId}/season/{seasonNumber}/images
     *    (bazı diziler sezon bazlı logolu poster döner)
     * 2. Fanart.tv seasonposter (sezon posteri logo yerine kullanılabilir)
     * 3. TMDB ana logo (fallback)
     *
     * @param tmdbId TMDB ID (TV dizisi)
     * @param seasonNumber Sezon numarası (2+)
     * @param language Tercih edilen dil kodu
     */
    suspend fun getSeasonLogoUrl(
        tmdbId: Int,
        seasonNumber: Int,
        language: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || seasonNumber <= 1) return@withContext null
        val preferredLang = normalizeLanguageCode(language ?: getPreferredImageLanguage())
        val (fanartEnabled, fanartApiKey) = getFanartSettings()
        seasonLogoUrl(
            tmdbId = tmdbId,
            season = seasonNumber,
            language = preferredLang,
            strictLanguage = false,
            fanartEnabled = fanartEnabled,
            fanartApiKey = fanartApiKey,
            tmdbArtworkEnabled = isTmdbArtworkEnabled()
        )
    }

    /**
     * TMDB sezon images endpoint'inden yalnızca logo çeker.
     * Sezon posteri logo yerine kullanılmaz — hero alanı şeffaf logo bekler.
     */
    private fun queryTmdbSeasonImages(
        tmdbId: Int,
        seasonNumber: Int,
        language: String,
        allowFallback: Boolean = true
    ): String? {
        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return null

        val url = runCatching {
            URL("https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber/images?api_key=$apiKey")
        }.getOrNull() ?: return null

        val responseText = KitsugiApiBase.executeGetRequest(url) ?: return null
        return runCatching {
            val logos = JSONObject(responseText).optJSONArray("logos") ?: return@runCatching null
            if (logos.length() == 0) return@runCatching null
            val bestLogo = selectBestByLanguage(logos, language, allowFallback)
            bestLogo?.let { "https://image.tmdb.org/t/p/original$it" }
        }.getOrElse {
            Log.w(TAG, "queryTmdbSeasonImages parse failed for tmdbId=$tmdbId season=$seasonNumber: ${it.message}")
            null
        }
    }

    /**
     * TMDB images JSON array'den dile göre en iyi file_path'i seçer.
     * Öncelik: tercih edilen dil → dil bağımsız (null/boş) → İngilizce → ilk öğe
     */
    private fun selectBestByLanguage(
        jsonArray: JSONArray,
        preferredLanguage: String,
        allowFallback: Boolean = true
    ): String? {
        data class Pick(val path: String, val votes: Int)
        var preferred: Pick? = null
        var neutral: Pick? = null
        var english: Pick? = null
        var any: Pick? = null
        val wanted = normalizeLanguageCode(preferredLanguage)

        fun better(current: Pick?, path: String, votes: Int): Pick? =
            if (current == null || votes > current.votes) Pick(path, votes) else current

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val path = obj.optNullableString("file_path") ?: continue
            if (path.isBlank()) continue
            val votes = obj.optInt("vote_count", 0)
            val lang = obj.optNullableString("iso_639_1").orEmpty().lowercase().substringBefore('-')
            any = better(any, path, votes)
            when {
                lang == wanted -> preferred = better(preferred, path, votes)
                lang.isBlank() || lang == "null" -> neutral = better(neutral, path, votes)
                lang == "en" -> english = better(english, path, votes)
            }
        }

        if (!allowFallback) return preferred?.path
        return preferred?.path ?: neutral?.path ?: english?.path ?: any?.path
    }

    /**
     * MAL ID'ye göre anime logo URL'ini döner.
     * AniList kaynaklı animeler için fallbackAniListId verilebilir.
     *
     * @param malId MAL ID
     * @param fallbackAniListId AniList ID (yedek)
     * @param seasonNumber Sezon numarası (sezon bazlı logo için)
     * @param language Tercih edilen dil kodu (null = ayarlar)
     */
    suspend fun getLogoUrlByMalId(
        malId: Int,
        fallbackAniListId: Int? = null,
        seasonNumber: Int? = null,
        language: String? = null,
        isMovie: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (!isAnyLogoSourceEnabled()) return@withContext null
        if (malId <= 0 && fallbackAniListId == null) return@withContext null
        var tmdbId: Int? = null
        if (malId > 0) tmdbId = resolveTmdbIdFromMal(malId)
        if (tmdbId == null && fallbackAniListId != null && fallbackAniListId > 0) {
            tmdbId = resolveTmdbIdFromAniList(fallbackAniListId)
        }
        val resolvedId = tmdbId ?: return@withContext null
        getLogoUrl(resolvedId, seasonNumber, language, isMovie)
    }

    /**
     * AniList ID'ye göre anime logo URL'ini döner.
     * ARM lookup başarısız olursa fallbackMalId ile tekrar dener.
     *
     * @param aniListId AniList ID
     * @param fallbackMalId MAL ID (yedek)
     * @param seasonNumber Sezon numarası (sezon bazlı logo için)
     * @param language Tercih edilen dil kodu (null = ayarlar)
     */
    suspend fun getLogoUrlByAniListId(
        aniListId: Int,
        fallbackMalId: Int? = null,
        seasonNumber: Int? = null,
        language: String? = null,
        isMovie: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (!isAnyLogoSourceEnabled()) return@withContext null
        var tmdbId: Int? = null
        if (aniListId > 0) tmdbId = resolveTmdbIdFromAniList(aniListId)
        // AniList ARM lookup boş döndüyse gerçek MAL ID ile tekrar dene
        if (tmdbId == null && fallbackMalId != null && fallbackMalId > 0) {
            Log.d(TAG, "AniList ARM miss for aniListId=$aniListId — retrying with malId=$fallbackMalId")
            tmdbId = resolveTmdbIdFromMal(fallbackMalId)
        }
        val resolvedId = tmdbId ?: return@withContext null
        getLogoUrl(resolvedId, seasonNumber, language, isMovie)
    }

    private fun fetchLogoFromSeriesGraph(tmdbId: Int): String? {
        val url = runCatching {
            URL("https://seriesgraph.com/api/shows/$tmdbId")
        }.getOrNull() ?: return null

        val responseText = KitsugiApiBase.executeGetRequest(url) ?: return null
        return runCatching {
            val json = JSONObject(responseText)
            val logoPath = json.optNullableString("logo_path") ?: return@runCatching null
            "https://image.tmdb.org/t/p/w500$logoPath"
        }.getOrElse {
            Log.w(TAG, "Logo parse failed for tmdbId=$tmdbId: ${it.message}")
            null
        }
    }

    private fun queryTmdbImages(tmdbId: Int, type: String, apiKey: String, preferredLanguage: String = "tr"): String? {
        val url = runCatching {
            URL("https://api.themoviedb.org/3/$type/$tmdbId/images?api_key=$apiKey")
        }.getOrNull() ?: return null

        val responseText = KitsugiApiBase.executeGetRequest(url) ?: return null
        return runCatching {
            val json = JSONObject(responseText)
            val logos = json.optJSONArray("logos")
            if (logos == null || logos.length() == 0) return null

            // Dil önceliği: tercih edilen dil -> dil bağımsız -> İngilizce -> Latin -> CJK
            val targetLang = normalizeLanguageCode(preferredLanguage)
            data class LogoPick(val path: String, val score: Double)
            fun consider(current: LogoPick?, path: String, score: Double): LogoPick =
                if (current == null || score > current.score) LogoPick(path, score) else current

            var preferredLogo: LogoPick? = null
            var enLogo: LogoPick? = null
            var neutralLogo: LogoPick? = null
            var otherLatinLogo: LogoPick? = null
            var cjkLogo: LogoPick? = null

            for (i in 0 until logos.length()) {
                val logoObj = logos.getJSONObject(i)
                val lang = logoObj.optNullableString("iso_639_1").orEmpty().lowercase()
                val path = logoObj.optNullableString("file_path")
                if (!path.isNullOrBlank()) {
                    val score = logoObj.optInt("vote_count", 0) * 10.0 +
                        logoObj.optDouble("vote_average", 0.0)
                    when {
                        lang == targetLang -> preferredLogo = consider(preferredLogo, path, score)
                        lang.isBlank() || lang == "null" -> neutralLogo = consider(neutralLogo, path, score)
                        lang == "en" -> enLogo = consider(enLogo, path, score)
                        lang in listOf("ja", "ko", "zh") -> cjkLogo = consider(cjkLogo, path, score)
                        else -> otherLatinLogo = consider(otherLatinLogo, path, score)
                    }
                }
            }
            preferredLogo?.path ?: neutralLogo?.path ?: enLogo?.path ?: otherLatinLogo?.path ?: cjkLogo?.path
        }.getOrNull()
    }

    /**
     * Verilen TMDB ID'nin TVDB ID'sini çözer.
     *
     * Çözümleme zinciri:
     *  1. Bellek önbelleği (tmdbToTvdbCache)
     *  2. Room önbelleğindeki tvdbId sütunu (önceki ARM/AnimeAPI sorgularından doldurulmuş)
     *  3. TMDB external_ids endpoint'i (doğrudan tvdb_id alanı)
     *  4. Room'daki malId üzerinden animeapi.my.id
     *  5. Room'daki aniListId üzerinden animeapi.my.id
     */
    private suspend fun resolveTvdbIdFromTmdb(
        tmdbId: Int,
        fallbackMalId: Int? = null,
        fallbackAniListId: Int? = null,
        fallbackKitsuId: Int? = null
    ): Int? = withContext(Dispatchers.IO) {
        // FİLM koruması: TVDB anime filmlerini dizinin 0. sezonu olarak tutar; filmin
        // TMDB ID'si için TVDB döndürülürse fanart-TV DİZİNİN logosunu/afişini getirir.
        // Film kayıtları fanart-TV'ye hiç düşmemeli (yalnızca fanart movie + TMDB movie).
        if (DetailCache.tmdbMediaCache[tmdbId] == "movie") {
            Log.d(TAG, "TVDB resolve skipped (movie): tmdbId=$tmdbId")
            return@withContext null
        }

        // 1. Bellek önbelleği kontrolü
        if (tmdbToTvdbCache.containsKey(tmdbId)) {
            return@withContext mutex.withLock { tmdbToTvdbCache[tmdbId] }
        }

        // 2. Room'daki tvdbId sütununu doğrudan oku (ARM/AnimeAPI önceden kaydetmiş olabilir)
        val cachedEntity = runCatching { dao?.getByTmdbId(tmdbId) }.getOrNull()
        if (cachedEntity?.tvdbId != null && cachedEntity.tvdbId > 0) {
            val cached = cachedEntity.tvdbId
            mutex.withLock { tmdbToTvdbCache[tmdbId] = cached }
            Log.d(TAG, "Room tvdbId hit: tmdbId=$tmdbId → tvdbId=$cached")
            return@withContext cached
        }

        // 3. TMDB external_ids endpoint → tvdb_id
        val isTv = true // Fanart.tv TV endpoint için; movie de denenebilir
        val extIds = runCatching { KitsugiIdResolver.fetchExternalIds(tmdbId, isTv) }.getOrNull()
        val tvdbFromTmdb = extIds?.tvdbId?.takeIf { it > 0 }
            ?: runCatching { KitsugiIdResolver.fetchExternalIds(tmdbId, false) }.getOrNull()?.tvdbId?.takeIf { it > 0 }
        if (tvdbFromTmdb != null) {
            mutex.withLock { tmdbToTvdbCache[tmdbId] = tvdbFromTmdb }
            // Kalıcı olarak Room'a yaz
            runCatching {
                val existing = dao?.getByTmdbId(tmdbId)
                if (existing != null) dao?.insert(existing.copy(tvdbId = tvdbFromTmdb))
            }
            Log.d(TAG, "TVDB resolve (TMDB ext_ids): tmdbId=$tmdbId → tvdbId=$tvdbFromTmdb")
            return@withContext tvdbFromTmdb
        }

        // 4. malId üzerinden TVDB ID çöz (Room'dan veya caller fallback)
        var tvdbId: Int? = null
        val malId = cachedEntity?.malId ?: fallbackMalId
        if (malId != null && malId > 0) {
            tvdbId = resolveTvdbIdFromMal(malId)
        }

        // 5. aniListId üzerinden dene (Room'dan veya caller fallback)
        if (tvdbId == null) {
            val aniListId = cachedEntity?.aniListId ?: fallbackAniListId
            if (aniListId != null && aniListId > 0) {
                tvdbId = resolveTvdbIdFromAniList(aniListId)
            }
        }

        // 6. kitsuId üzerinden dene (Room'dan veya caller fallback)
        if (tvdbId == null) {
            val kitsuId = cachedEntity?.kitsuId?.toIntOrNull() ?: fallbackKitsuId
            if (kitsuId != null && kitsuId > 0) {
                tvdbId = resolveTvdbIdFromKitsu(kitsuId)
            }
        }

        mutex.withLock { tmdbToTvdbCache[tmdbId] = tvdbId }
        Log.d(TAG, "TVDB resolve (via MAL/AniList/Kitsu): tmdbId=$tmdbId → tvdbId=$tvdbId")
        tvdbId
    }

    /**
     * MAL ID üzerinden TVDB ID çözer (animeapi.my.id).
     */
    private suspend fun resolveTvdbIdFromMal(malId: Int): Int? {
        if (malId <= 0) return null
        return runCatching {
            val url = URL("https://animeapi.my.id/myanimelist/$malId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val json = JSONObject(response)
            val value = json.optInt("thetvdb", -1)
            if (value > 0) {
                Log.d(TAG, "TVDB via AnimeAPI (MAL): malId=$malId → tvdbId=$value")
                value
            } else null
        }.getOrElse {
            Log.w(TAG, "resolveTvdbIdFromMal failed for malId=$malId: ${it.message}")
            null
        }
    }

    /**
     * AniList ID üzerinden TVDB ID çözer (animeapi.my.id).
     */
    private suspend fun resolveTvdbIdFromAniList(aniListId: Int): Int? {
        if (aniListId <= 0) return null
        return runCatching {
            val url = URL("https://animeapi.my.id/anilist/$aniListId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val json = JSONObject(response)
            val value = json.optInt("thetvdb", -1)
            if (value > 0) {
                Log.d(TAG, "TVDB via AnimeAPI (AniList): aniListId=$aniListId → tvdbId=$value")
                value
            } else null
        }.getOrElse {
            Log.w(TAG, "resolveTvdbIdFromAniList failed for aniListId=$aniListId: ${it.message}")
            null
        }
    }

    /**
     * Fanart.tv'den zengin galeri öğeleri (logo, backdrop, poster, vb.) çeker.
     *
     * TV/Anime için: TMDB ID → Room cache → MAL/AniList ID → TVDB ID → Fanart.tv TV endpoint
     * Film için:    TMDB ID → Fanart.tv Film endpoint (doğrudan)
     *
     * @param tmdbId      TMDB ID (TV veya Film)
     * @param isMovie     true ise Film endpoint'i kullanılır; false ise TV
     * @param fallbackMalId    Opsiyonel MAL ID (tmdb→tvdb çevriminde yedek olarak kullanılır)
     * @param fallbackAniListId Opsiyonel AniList ID (tmdb→tvdb çevriminde yedek olarak kullanılır)
     * @return            Fanart.tv API'sinden elde edilen [GalleryItem] listesi.
     */
    suspend fun getFanartGalleryItems(
        tmdbId: Int,
        isMovie: Boolean = false,
        fallbackMalId: Int? = null,
        fallbackAniListId: Int? = null,
        fallbackKitsuId: Int? = null,
        language: String? = null
    ): List<GalleryItem> = withContext(Dispatchers.IO) {
        val (fanartEnabled, fanartApiKey) = getFanartSettings()
        if (!fanartEnabled || fanartApiKey.isBlank()) return@withContext emptyList()
        val preferredLang = normalizeLanguageCode(language ?: getPreferredImageLanguage())

        // Bellek önbelleği: aynı sayfa tekrar açılırsa anında döner
        val cacheId = if (isMovie) tmdbId else tmdbId
        val cached = DetailCache.getFanartGallery(isMovie, cacheId, preferredLang)
        if (cached != null) {
            Log.d(TAG, "Fanart memory cache hit: isMovie=$isMovie id=$cacheId → ${cached.size} items")
            return@withContext cached
        }

        var result = if (isMovie) {
            if (tmdbId <= 0) return@withContext emptyList()
            // Film: TMDB ID doğrudan kullanılır
            runCatching {
                FanartApiClient.fetchMovieImages(tmdbId, fanartApiKey, preferredLang)
            }.getOrElse {
                Log.w(TAG, "getFanartGalleryItems (movie) failed: ${it.message}")
                emptyList()
            }
        } else {
            // TV/Anime: TVDB ID çözümleme zinciri (fallback ID'leri ileterek)
            var tvdbId = if (tmdbId > 0) resolveTvdbIdFromTmdb(tmdbId, fallbackMalId, fallbackAniListId, fallbackKitsuId) else null

            // Hâlâ bulunamazsa caller'dan gelen fallback id'leri doğrudan dene
            if (tvdbId == null || tvdbId <= 0) {
                if (fallbackMalId != null && fallbackMalId > 0) {
                    tvdbId = resolveTvdbIdFromMal(fallbackMalId)
                }
            }
            if (tvdbId == null || tvdbId <= 0) {
                if (fallbackAniListId != null && fallbackAniListId > 0) {
                    tvdbId = resolveTvdbIdFromAniList(fallbackAniListId)
                }
            }
            if (tvdbId == null || tvdbId <= 0) {
                if (fallbackKitsuId != null && fallbackKitsuId > 0) {
                    tvdbId = resolveTvdbIdFromKitsu(fallbackKitsuId)
                }
            }

            if (tvdbId != null && tvdbId > 0) {
                runCatching {
                    FanartApiClient.fetchTvImages(tvdbId, fanartApiKey, preferredLang)
                }.getOrElse {
                    Log.w(TAG, "getFanartGalleryItems (tv) failed: ${it.message}")
                    emptyList()
                }
            } else {
                Log.d(TAG, "No TVDB ID for tmdbId=$tmdbId — Fanart.tv TV skipped, will try movie fallback")
                emptyList()
            }
        }

        // FALLBACK: Eğer ilk deneme boş döndüyse ve tmdbId varsa, diğer endpoint'i dene
        if (result.isEmpty() && tmdbId > 0) {
            if (isMovie) {
                // Film olarak arandı ama bulunamadı -> TV olarak TVDB çözüp dene
                var fallbackTvdbId = resolveTvdbIdFromTmdb(tmdbId, fallbackMalId, fallbackAniListId, fallbackKitsuId)
                if (fallbackTvdbId == null || fallbackTvdbId <= 0) {
                    if (fallbackMalId != null && fallbackMalId > 0) fallbackTvdbId = resolveTvdbIdFromMal(fallbackMalId)
                }
                if (fallbackTvdbId == null || fallbackTvdbId <= 0) {
                    if (fallbackAniListId != null && fallbackAniListId > 0) fallbackTvdbId = resolveTvdbIdFromAniList(fallbackAniListId)
                }
                if (fallbackTvdbId == null || fallbackTvdbId <= 0) {
                    if (fallbackKitsuId != null && fallbackKitsuId > 0) fallbackTvdbId = resolveTvdbIdFromKitsu(fallbackKitsuId)
                }
                if (fallbackTvdbId != null && fallbackTvdbId > 0) {
                    result = runCatching {
                        FanartApiClient.fetchTvImages(fallbackTvdbId, fanartApiKey, preferredLang)
                    }.getOrElse { emptyList() }
                }
            } else {
                // TV olarak arandı ama TVDB yoktu veya TV Fanart'ta bulunamadı (Örn: Anime filmleri fanart.tv'de Film altındadır)
                // Doğrudan TMDB ID ile Film endpoint'ini dene!
                result = runCatching {
                    FanartApiClient.fetchMovieImages(tmdbId, fanartApiKey, preferredLang)
                }.getOrElse {
                    Log.w(TAG, "getFanartGalleryItems (movie fallback) failed: ${it.message}")
                    emptyList()
                }
            }
        }

        if (result.isNotEmpty()) {
            DetailCache.putFanartGallery(isMovie, cacheId, result, preferredLang)
            Log.d(TAG, "Fanart memory cache write: isMovie=$isMovie id=$cacheId → ${result.size} items")
        }
        result
    }

    /**
     * TMDB ID ve medya türü (film/dizi) ile TMDB API'sinden tüm görselleri çeker.
     * Posterler, arka planlar (backdrop) ve logolar tam çözünürlükte elde edilir.
     *
     * [expectedTitles] verilirse görseller yalnızca TMDB kaydının başlığı bu başlıklardan
     * biriyle uyuştuğunda listeye karışır. TMDB'de film ve dizi kimlikleri ayrı alanlardır;
     * eşleme veya tür bilgisi yanlışsa aynı sayı bambaşka bir yapıma ait olabilir ve
     * galeri alakasız görsellerle dolardı (bkz. [TmdbArtworkIdentity]).
     */
    suspend fun getTmdbGalleryItems(
        tmdbId: Int,
        isMovie: Boolean = false,
        expectedTitles: List<String> = emptyList()
    ): List<GalleryItem> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || !isTmdbArtworkEnabled()) return@withContext emptyList()

        val cached = DetailCache.getTmdbGallery(isMovie, tmdbId)
        if (cached != null) {
            Log.d(TAG, "TMDB gallery memory cache hit: isMovie=$isMovie id=$tmdbId → ${cached.size} items")
            return@withContext cached
        }

        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return@withContext emptyList()

        fun parseTmdbImages(json: JSONObject): List<GalleryItem> {
            val list = mutableListOf<GalleryItem>()

            // 1. Posterler
            val posters = json.optJSONArray("posters")
            if (posters != null) {
                for (i in 0 until posters.length()) {
                    val obj = posters.getJSONObject(i)
                    val path = obj.optNullableString("file_path") ?: continue
                    if (path.isBlank()) continue
                    val lang = obj.optNullableString("iso_639_1")
                    val width = obj.optInt("width").takeIf { it > 0 }
                    val height = obj.optInt("height").takeIf { it > 0 }
                    list.add(
                        GalleryItem(
                            url = "https://image.tmdb.org/t/p/original$path",
                            source = "TMDB",
                            category = GalleryCategory.POSTER,
                            description = "TMDB Poster",
                            language = lang?.ifBlank { null },
                            width = width,
                            height = height
                        )
                    )
                }
            }

            // 2. Arka Planlar (Backdrops)
            val backdrops = json.optJSONArray("backdrops")
            if (backdrops != null) {
                for (i in 0 until backdrops.length()) {
                    val obj = backdrops.getJSONObject(i)
                    val path = obj.optNullableString("file_path") ?: continue
                    if (path.isBlank()) continue
                    val lang = obj.optNullableString("iso_639_1")
                    val width = obj.optInt("width").takeIf { it > 0 }
                    val height = obj.optInt("height").takeIf { it > 0 }
                    list.add(
                        GalleryItem(
                            url = "https://image.tmdb.org/t/p/original$path",
                            source = "TMDB",
                            category = GalleryCategory.BACKDROP,
                            description = "TMDB Arka Plan",
                            language = lang?.ifBlank { null },
                            width = width,
                            height = height
                        )
                    )
                }
            }

            // 3. Logolar (Clear Logos)
            val logos = json.optJSONArray("logos")
            if (logos != null) {
                for (i in 0 until logos.length()) {
                    val obj = logos.getJSONObject(i)
                    val path = obj.optNullableString("file_path") ?: continue
                    if (path.isBlank()) continue
                    val lang = obj.optNullableString("iso_639_1")
                    val width = obj.optInt("width").takeIf { it > 0 }
                    val height = obj.optInt("height").takeIf { it > 0 }
                    list.add(
                        GalleryItem(
                            url = "https://image.tmdb.org/t/p/original$path",
                            source = "TMDB",
                            category = GalleryCategory.LOGO,
                            description = "TMDB Logo",
                            language = lang?.ifBlank { null },
                            width = width,
                            height = height
                        )
                    )
                }
            }

            return list
        }

        // Beklenen tür önce denenir, sonra öteki tür. Bir kaydın görselleri yalnızca TMDB
        // başlığı beklenenle uyuşursa listeye karışır — başlıklar gerçekten farklıysa o
        // görseller başka bir yapıma aittir ve galeriye girmemeli.
        val primaryType = if (isMovie) "movie" else "tv"
        val typesToTry = listOf(primaryType, if (isMovie) "tv" else "movie")
        var items = emptyList<GalleryItem>()
        for (type in typesToTry) {
            val response = runCatching {
                val url = URL(
                    "https://api.themoviedb.org/3/$type/$tmdbId" +
                        "?api_key=$apiKey&append_to_response=images"
                )
                val resp = KitsugiApiBase.executeGetRequest(url)
                if (resp.isNullOrBlank()) null else JSONObject(resp)
            }.getOrElse {
                Log.w(TAG, "getTmdbGalleryItems ($type) failed: ${it.message}")
                null
            } ?: continue

            val isMovieType = type == "movie"
            val entryTitle = if (isMovieType) response.optString("title") else response.optString("name")
            val entryOriginal = if (isMovieType) response.optString("original_title") else response.optString("original_name")
            val verified = TmdbArtworkIdentity.matches(expectedTitles, entryTitle, entryOriginal)
            val images = parseTmdbImages(response.optJSONObject("images") ?: response)
            if (images.isEmpty()) continue

            if (verified) {
                items = images
                break
            }
            Log.w(
                TAG,
                "TMDB galeri kimliği reddedildi: $type/$tmdbId başlığı «$entryTitle» beklenenle " +
                    "(${expectedTitles.firstOrNull() ?: "-"}) uyuşmuyor; görseller listeye karışmadı"
            )
        }

        if (items.isNotEmpty()) {
            DetailCache.putTmdbGallery(isMovie, tmdbId, items)
            Log.d(TAG, "TMDB gallery memory cache write: isMovie=$isMovie id=$tmdbId → ${items.size} items")
        }

        items
    }

    /**
     * MAL ID için önbelleğe alınmış TMDB ID'yi döner.
     */
    suspend fun getResolvedTmdbIdForMal(malId: Int): Int? = mutex.withLock {
        malToTmdbCache[malId]
    }

    /**
     * AniList ID için önbelleğe alınmış TMDB ID'yi döner.
     */
    suspend fun getResolvedTmdbIdForAniList(aniListId: Int): Int? = mutex.withLock {
        malToTmdbCache[-aniListId]
    }

    // ─────────────────────────────────────────────────────────────
    // TMDB ID'ye göre önbellekli SeriesGraph fetch
    // ─────────────────────────────────────────────────────────────
    private suspend fun fetchWithCache(tmdbId: Int): Map<Pair<Int, Int>, Double> {
        val now = System.currentTimeMillis()

        // Önbellek kontrolü — withLock içinde return kullanmıyoruz
        val cached = mutex.withLock {
            val entry = ratingsCache[tmdbId]
            if (entry != null && entry.expiresAtMs > now) entry.ratings
            else {
                if (entry != null) ratingsCache.remove(tmdbId)
                null
            }
        }
        if (cached != null) return cached

        // In-flight dedup: aynı ID için çok sayıda istek gelirse hepsi aynı coroutine'i bekler
        val deferred = mutex.withLock {
            inFlight[tmdbId] ?: scope.async {
                try {
                    fetchFromSeriesGraph(tmdbId).also { ratings ->
                        mutex.withLock {
                            ratingsCache[tmdbId] = DetailCache.RatingCacheEntry(
                                ratings = ratings,
                                expiresAtMs = System.currentTimeMillis() + CACHE_TTL_MS
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SeriesGraph fetch failed for tmdbId=$tmdbId: ${e.message}")
                    emptyMap()
                } finally {
                    mutex.withLock { inFlight.remove(tmdbId) }
                }
            }.also { inFlight[tmdbId] = it }
        }

        return deferred.await()
    }

    // ─────────────────────────────────────────────────────────────
    // ARM API: MAL ID → TMDB ID
    // Endpoint: https://arm.haglund.dev/api/v2/ids?source=myanimelist&id={malId}
    // Response: { "myanimelist": 21, "anilist": 21, "thetvdb": 81797,
    //             "themoviedb": 37854, "anidb": 69 }
    // ─────────────────────────────────────────────────────────────
    suspend fun resolveTmdbIdFromMal(malId: Int): Int? = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext null
        // Önbellek kontrolü — tamamen mutex içinde
        val isCached = mutex.withLock { malToTmdbCache.containsKey(malId) }
        if (isCached) return@withContext mutex.withLock { malToTmdbCache[malId] }

        // Room önbellek kontrolü
        val cachedEntity = runCatching { dao?.getByMalId(malId) }.getOrNull()
        if (cachedEntity != null) {
            val id = cachedEntity.tmdbId
            mutex.withLock { malToTmdbCache[malId] = id }
            if (cachedEntity.tvdbId != null && cachedEntity.tvdbId > 0) {
                mutex.withLock { tmdbToTvdbCache[id] = cachedEntity.tvdbId }
            }
            Log.d(TAG, "Room hit: malId=$malId → tmdbId=$id")
            return@withContext id
        }

        var tmdbId = runCatching {
            val url = URL("https://arm.haglund.dev/api/v2/ids?source=myanimelist&id=$malId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val json = JSONObject(response)
            val tmdbValEarly = json.optInt("themoviedb", -1).takeIf { it > 0 }
            cacheTmdbMediaKind(tmdbValEarly, json)
            val tvdbVal = json.optInt("thetvdb", -1).takeIf { it > 0 }
            // Filmin thetvdb'si DİZİNİN TVDB ID'sidir; filme bağlanırsa fanart-TV dizi sanatı sızar.
            if (tvdbVal != null && tmdbValEarly != null &&
                DetailCache.tmdbMediaCache[tmdbValEarly] != "movie"
            ) {
                mutex.withLock { tmdbToTvdbCache[tmdbValEarly] = tvdbVal }
            }
            if (json.isNull("themoviedb")) null
            else {
                val value = json.optInt("themoviedb", -1)
                if (value > 0) {
                    val existing = dao?.getByTmdbId(value)
                    val updated = existing?.copy(malId = malId, tvdbId = tvdbVal ?: existing.tvdbId) ?: MediaMetaCacheEntity(
                        tmdbId = value,
                        malId = malId,
                        aniListId = null,
                        logoUrl = null,
                        logoNotFound = false,
                        tvdbId = tvdbVal
                    )
                    dao?.insert(updated)
                    Log.d(TAG, "Room write (MAL): malId=$malId → tmdbId=$value")
                    value
                } else null
            }
        }.getOrElse {
            Log.w(TAG, "ARM API lookup failed for malId=$malId: ${it.message}")
            null
        }

        // Fallback to animeapi.my.id if ARM failed or returned null
        if (tmdbId == null) {
            tmdbId = runCatching {
                val url = URL("https://animeapi.my.id/myanimelist/$malId")
                val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
                val json = JSONObject(response)
                val value = json.optInt("themoviedb", -1)
                cacheTmdbMediaKind(value.takeIf { it > 0 }, json)
                val tvdbVal = json.optInt("thetvdb", -1).takeIf { it > 0 }
                if (value > 0) {
                    if (tvdbVal != null && DetailCache.tmdbMediaCache[value] != "movie") {
                        mutex.withLock { tmdbToTvdbCache[value] = tvdbVal }
                    }
                    val aniListId = if (json.isNull("anilist")) null else json.optInt("anilist", -1).takeIf { it > 0 }
                    val existing = dao?.getByTmdbId(value)
                    val updated = existing?.copy(malId = malId, aniListId = aniListId ?: existing.aniListId, tvdbId = tvdbVal ?: existing.tvdbId) ?: MediaMetaCacheEntity(
                        tmdbId = value,
                        malId = malId,
                        aniListId = aniListId,
                        logoUrl = null,
                        logoNotFound = false,
                        tvdbId = tvdbVal
                    )
                    dao?.insert(updated)
                    Log.d(TAG, "Room write (AnimeAPI MAL): malId=$malId → tmdbId=$value")
                    value
                } else null
            }.getOrElse {
                Log.w(TAG, "AnimeAPI lookup failed for malId=$malId: ${it.message}")
                null
            }
        }

        mutex.withLock { malToTmdbCache[malId] = tmdbId }
        Log.d(TAG, "ARM lookup (with fallback): malId=$malId → tmdbId=$tmdbId")
        tmdbId
    }

    // ─────────────────────────────────────────────────────────────
    // SeriesGraph API
    // Endpoint: https://seriesgraph.com/api/shows/{tmdbId}/season-ratings
    // ─────────────────────────────────────────────────────────────
    private fun fetchFromSeriesGraph(tmdbId: Int): Map<Pair<Int, Int>, Double> {
        val url = runCatching {
            URL("https://seriesgraph.com/api/shows/$tmdbId/season-ratings")
        }.getOrNull() ?: return emptyMap()

        val responseText = KitsugiApiBase.executeGetRequest(url) ?: return emptyMap()
        return parseSeriesGraphResponse(responseText)
    }

    private fun parseSeriesGraphResponse(json: String): Map<Pair<Int, Int>, Double> {
        return runCatching {
            val root = JSONArray(json)
            val result = mutableMapOf<Pair<Int, Int>, Double>()

            for (s in 0 until root.length()) {
                val seasonObj = root.optJSONObject(s) ?: continue
                val episodes = seasonObj.optJSONArray("episodes") ?: continue

                for (e in 0 until episodes.length()) {
                    val ep = episodes.optJSONObject(e) ?: continue
                    val seasonNum = ep.optInt("season_number", -1).takeIf { it >= 0 } ?: continue
                    val episodeNum = ep.optInt("episode_number", -1).takeIf { it > 0 } ?: continue
                    val voteAvg = ep.optDouble("vote_average", Double.NaN)
                        .takeIf { !it.isNaN() && it > 0.0 } ?: continue
                    result[seasonNum to episodeNum] = voteAvg
                }
            }
            result
        }.getOrElse {
            Log.w(TAG, "SeriesGraph parse failed: ${it.message}")
            emptyMap()
        }
    }

    /**
     * Verilen TMDB sezon numarası ve başlık/sinonimler yardımıyla hedef sezon numarasını tahmin eder.
     */
    fun determineTargetSeason(
        tmdbSeason: Int?,
        title: String?,
        titleEnglish: String?,
        synonyms: List<String>
    ): Int {
        val seasonPatterns = listOf(
            Regex("""\b(?:season|sezon|s)\s*[:.-]?\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{1,2})\s*(?:st|nd|rd|th)?\s*(?:season|sezon)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{1,2})\.\s*sezon\b""", RegexOption.IGNORE_CASE),
        )

        // Uzundan kısaya doğru Roma rakamları ("iii" önce, "ii" sonra!)
        val romanNumerals = listOf(
            "x" to 10,
            "ix" to 9,
            "viii" to 8,
            "vii" to 7,
            "vi" to 6,
            "v" to 5,
            "iv" to 4,
            "iii" to 3,
            "ii" to 2
        )

        fun parseFromText(text: String?): Int? {
            if (text.isNullOrBlank()) return null
            val lower = text.lowercase().trim()

            // 1. Açıkça belirtilen sezon kelimeleri (Season 3, 3. Sezon, 3rd Season, S3)
            for (pattern in seasonPatterns) {
                val match = pattern.find(lower)
                if (match != null) {
                    val num = match.groupValues[1].toIntOrNull()
                    if (num != null && num in 1..25) return num
                }
            }

            // 2. Roma rakamları (Kelime sınırları ile! "Mushoku Tensei III: ...", "Mob Psycho 100 III")
            for ((roman, num) in romanNumerals) {
                val romanRegex = Regex("""\b$roman\b(?:\s*[:\-\(\[]|\s*$)""", RegexOption.IGNORE_CASE)
                if (romanRegex.containsMatchIn(lower)) {
                    return num
                }
            }

            // 3. Sondaki tek sayı (ör. "Anime 2", "Anime 3", ama 19xx/20xx gibi yıllar hariç)
            val trailingMatch = Regex("""\b([2-9]|1[0-9])\s*(?:[:\-\(\[]|$)""").find(lower)
            if (trailingMatch != null) {
                val num = trailingMatch.groupValues[1].toIntOrNull()
                if (num != null && num in 2..25) return num
            }

            return null
        }

        // Başlıkta veya İngilizce başlıkta açıkça bir sezon belirtilmişse doğrudan onu kullan
        parseFromText(title)?.let { return it }
        parseFromText(titleEnglish)?.let { return it }
        for (syn in synonyms) {
            parseFromText(syn)?.let { return it }
        }

        // Başlıkta sezon yoksa TMDB sezonunu kullan
        if (tmdbSeason != null && tmdbSeason > 0) return tmdbSeason

        return 1
    }

    /**
     * TMDB ID ve sezon numarasına göre bölüm listesini çeker.
     * Sonuçları önbelleğe alır.
     */
    suspend fun getTmdbEpisodes(tmdbId: Int, seasonNumber: Int): List<TmdbEpisodeDto> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || seasonNumber <= 0) return@withContext emptyList()
        val cacheKey = tmdbId to seasonNumber

        val cached = mutex.withLock { DetailCache.tmdbEpisodesCache[cacheKey]?.map { it.toDto() } }
        if (cached != null) return@withContext cached

        val deferred = mutex.withLock {
            tmdbEpisodesInFlight[cacheKey] ?: scope.async {
                try {
                    val apiKey = TmdbApiClient.getActiveApiKey()

                    val langTag = TmdbApiClient.getActiveLanguage()

                    val url = runCatching {
                        URL("https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey&language=$langTag")
                    }.getOrNull() ?: return@async emptyList<TmdbEpisodeDto>()

                    val responseText = KitsugiApiBase.executeGetRequest(url) ?: return@async emptyList<TmdbEpisodeDto>()
                    var parsed = parseTmdbEpisodes(responseText)
                    // Bölüm adları da Türkçe → İngilizce zincirine uyar: TMDB istenen dilde
                    // bölüm adı bulamazsa orijinal (Japonca) adı döndürür. Bu durumda tek bir
                    // en-US isteğiyle adlar İngilizce'ye tamamlanır.
                    if (parsed.any { it.name != null && PreferenceHelpers.hasCjkCharacters(it.name) }) {
                        val enUrl = runCatching {
                            URL(com.kitsugi.animelist.data.remote.TmdbUrlUtils.withLanguage(url.toString(), "en-US"))
                        }.getOrNull()
                        val enResponse = enUrl?.let { runCatching { KitsugiApiBase.executeGetRequest(it) }.getOrNull() }
                        if (!enResponse.isNullOrBlank()) {
                            val enNames = parseTmdbEpisodes(enResponse)
                                .mapNotNull { dto -> dto.name?.takeIf { it.isNotBlank() }?.let { dto.episodeNumber to it } }
                                .toMap()
                            parsed = parsed.map { dto ->
                                val latin = enNames[dto.episodeNumber]?.takeIf {
                                    PreferenceHelpers.isLatinText(it)
                                }
                                if (dto.name != null && PreferenceHelpers.hasCjkCharacters(dto.name) && latin != null) {
                                    dto.copy(name = latin)
                                } else dto
                            }
                        }
                    }
                    if (parsed.isNotEmpty()) {
                        mutex.withLock {
                            DetailCache.tmdbEpisodesCache[cacheKey] = parsed.map { it.toCached() }
                        }
                    }
                    parsed
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch TMDB episodes for tmdbId=$tmdbId season=$seasonNumber: ${e.message}")
                    emptyList()
                } finally {
                    mutex.withLock { tmdbEpisodesInFlight.remove(cacheKey) }
                }
            }.also { tmdbEpisodesInFlight[cacheKey] = it }
        }
        deferred.await()
    }

    private fun parseTmdbEpisodes(json: String): List<TmdbEpisodeDto> {
        return runCatching {
            val root = JSONObject(json)
            val episodes = root.optJSONArray("episodes") ?: return emptyList()
            val list = mutableListOf<TmdbEpisodeDto>()
            for (i in 0 until episodes.length()) {
                val ep = episodes.optJSONObject(i) ?: continue
                val epNum = ep.optInt("episode_number", -1)
                if (epNum <= 0) continue
                val name = ep.optNullableString("name")
                val overview = ep.optNullableString("overview")
                val stillPath = ep.optNullableString("still_path")
                val airDate = ep.optNullableString("air_date")
                list.add(
                    TmdbEpisodeDto(
                        episodeNumber = epNum,
                        name = name,
                        overview = overview,
                        stillPath = stillPath,
                        airDate = airDate
                    )
                )
            }
            list
        }.getOrElse {
            Log.w(TAG, "TMDB episodes parse failed: ${it.message}")
            emptyList()
        }
    }

    suspend fun resolveTmdbIdFromKitsu(kitsuId: Int): Int? = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext null
        if (kitsuId <= 0) return@withContext null
        val cacheKey = kitsuId + 300_000_000

        // Önbellek kontrolü — tamamen mutex içinde
        val isCached = mutex.withLock { malToTmdbCache.containsKey(cacheKey) }
        if (isCached) return@withContext mutex.withLock { malToTmdbCache[cacheKey] }

        // Room önbellek kontrolü
        val cachedEntity = runCatching { dao?.getByKitsuId(kitsuId.toString()) }.getOrNull()
        if (cachedEntity != null) {
            val id = cachedEntity.tmdbId
            mutex.withLock { malToTmdbCache[cacheKey] = id }
            if (cachedEntity.tvdbId != null && cachedEntity.tvdbId > 0) {
                mutex.withLock { tmdbToTvdbCache[id] = cachedEntity.tvdbId }
            }
            Log.d(TAG, "Room hit: kitsuId=$kitsuId → tmdbId=$id")
            return@withContext id
        }

        val tmdbId = runCatching {
            val url = URL("https://arm.haglund.dev/api/v2/ids?source=kitsu&id=$kitsuId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val json = JSONObject(response)
            val tmdbValEarly = json.optInt("themoviedb", -1).takeIf { it > 0 }
            cacheTmdbMediaKind(tmdbValEarly, json)
            val tvdbVal = json.optInt("thetvdb", -1).takeIf { it > 0 }
            // Filmin thetvdb'si DİZİNİN TVDB ID'sidir; filme bağlanırsa fanart-TV dizi sanatı sızar.
            if (tvdbVal != null && tmdbValEarly != null &&
                DetailCache.tmdbMediaCache[tmdbValEarly] != "movie"
            ) {
                mutex.withLock { tmdbToTvdbCache[tmdbValEarly] = tvdbVal }
            }
            if (json.isNull("themoviedb")) null
            else {
                val value = json.optInt("themoviedb", -1)
                if (value > 0) {
                    val malId = if (!json.isNull("myanimelist")) json.optInt("myanimelist", -1) else null
                    val cleanMalId = if (malId != null && malId > 0) malId else null
                    val aniListId = if (!json.isNull("anilist")) json.optInt("anilist", -1) else null
                    val cleanAniListId = if (aniListId != null && aniListId > 0) aniListId else null
                    val existing = dao?.getByTmdbId(value)
                    val updated = existing?.copy(
                        kitsuId = kitsuId.toString(),
                        malId = cleanMalId ?: existing.malId,
                        aniListId = cleanAniListId ?: existing.aniListId,
                        tvdbId = tvdbVal ?: existing.tvdbId
                    ) ?: MediaMetaCacheEntity(
                        tmdbId = value,
                        malId = cleanMalId,
                        aniListId = cleanAniListId,
                        logoUrl = null,
                        logoNotFound = false,
                        kitsuId = kitsuId.toString(),
                        tvdbId = tvdbVal
                    )
                    dao?.insert(updated)
                    Log.d(TAG, "Room write (Kitsu): kitsuId=$kitsuId → tmdbId=$value")
                    value
                } else null
            }
        }.getOrElse {
            Log.w(TAG, "ARM API lookup failed for kitsuId=$kitsuId: ${it.message}")
            null
        }

        mutex.withLock { malToTmdbCache[cacheKey] = tmdbId }
        Log.d(TAG, "ARM lookup kitsu: kitsuId=$kitsuId → tmdbId=$tmdbId")
        tmdbId
    }

    private suspend fun resolveTvdbIdFromKitsu(kitsuId: Int): Int? = withContext(Dispatchers.IO) {
        if (kitsuId <= 0) return@withContext null
        runCatching {
            val url = URL("https://arm.haglund.dev/api/v2/ids?source=kitsu&id=$kitsuId")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
            val json = JSONObject(response)
            val value = json.optInt("thetvdb", -1)
            if (value > 0) {
                Log.d(TAG, "TVDB via ARM (Kitsu): kitsuId=$kitsuId → tvdbId=$value")
                value
            } else null
        }.getOrElse {
            Log.w(TAG, "resolveTvdbIdFromKitsu failed for kitsuId=$kitsuId: ${it.message}")
            null
        }
    }


    // Kitsu kimliği → alt türü "movie" mi
    private val kitsuMovieFlagCache = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    /**
     * Kitsu kaydının alt türü "movie" mi? (Kitsu kimliği başına tek istek; sonuç bellekte tutulur.)
     * Hata durumunda false döner ve önbelleğe alınmaz.
     */
    private suspend fun isKitsuMovie(kitsuId: Int): Boolean = withContext(Dispatchers.IO) {
        kitsuMovieFlagCache[kitsuId]?.let { return@withContext it }
        val subtype = runCatching {
            val request = okhttp3.Request.Builder()
                .url(KitsuApiHost.url("/anime/$kitsuId"))
                .header("Accept", "application/vnd.api+json")
                .header("User-Agent", "Kitsugi/1.0 (Android)")
                .build()
            com.kitsugi.animelist.core.network.KitsugiHttpClient.metadataClient
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    org.json.JSONObject(body)
                        .optJSONObject("data")
                        ?.optJSONObject("attributes")
                        ?.optString("subtype", "")
                }
        }.getOrNull()
        if (subtype == null) return@withContext false
        val isMovie = subtype.equals("movie", ignoreCase = true)
        kitsuMovieFlagCache[kitsuId] = isMovie
        isMovie
    }

    /** Tür bilgisi çağıran tarafta olmayan (ör. Kitsu listesi) kayıtlar için: Kitsu alt türü "movie" mi? */
    suspend fun isKitsuMovieId(kitsuId: Int?): Boolean =
        if (kitsuId == null || kitsuId <= 0) false else isKitsuMovie(kitsuId)

    suspend fun getEpisodeRatingsByKitsuId(kitsuId: Int): Map<Pair<Int, Int>, Double> = withContext(Dispatchers.IO) {
        if (!isTmdbEnabled()) return@withContext emptyMap()
        if (kitsuId <= 0) return@withContext emptyMap()
        val tmdbId = resolveTmdbIdFromKitsu(kitsuId) ?: return@withContext emptyMap()
        fetchWithCache(tmdbId)
    }

    suspend fun getLogoUrlByKitsuId(
        kitsuId: Int,
        seasonNumber: Int? = null,
        language: String? = null,
        isMovie: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (!isAnyLogoSourceEnabled()) return@withContext null
        if (kitsuId <= 0) return@withContext null
        val tmdbId = resolveTmdbIdFromKitsu(kitsuId) ?: return@withContext null
        getLogoUrl(tmdbId, seasonNumber, language, isMovie = isMovie || isKitsuMovie(kitsuId))
    }

    suspend fun getResolvedTmdbIdForKitsu(kitsuId: Int): Int? = mutex.withLock {
        malToTmdbCache[kitsuId + 300_000_000]
    }
}
