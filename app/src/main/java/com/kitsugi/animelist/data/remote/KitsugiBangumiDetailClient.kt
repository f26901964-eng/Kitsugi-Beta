package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiSubject
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.toTurkishBroadcast
import com.kitsugi.animelist.utils.toTurkishCharacterRole
import com.kitsugi.animelist.utils.toTurkishDuration
import com.kitsugi.animelist.utils.toTurkishRelationType
import com.kitsugi.animelist.utils.toTurkishSeason
import com.kitsugi.animelist.utils.toTurkishStaffRole
import com.kitsugi.animelist.utils.toTurkishStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Bangumi (bgm.tv) **detay sayfası** veri katmanı.
 *
 * Bangumi kayıtları (`source = "bangumi"`, `malId` = stableId = subject_id + 500_000_000) eskiden
 * detay hattında hiçbir dala girmiyor; ana detay Kitsu başlık aramasına, sekmeler (karakter, ekip,
 * öneri, ilişki, istatistik, yorum, bölüm) ise boş listeye düşüyordu. Bu nesne:
 *
 *  1. **Bangumi-yerel veriyi** çeker (v0 `/subjects/{id}` + web arayüzünün `next.bgm.tv/p1` uçları:
 *     karakterler + seslendirmenler, ekip, ilişkiler, öneriler, yorumlar/incelemeler, bölümler),
 *  2. Kaydı **diğer platformlarla eşler** (AniList arama → MAL id → ARM → TMDB/Kitsu/TVDB/IMDb;
 *     sonuçlar kalıcı olarak önbelleğe alınır) ve
 *  3. MAL / AniList / TMDB verisini **birleştirir**: Bangumi alanları (başlık, puan, sıra) korunur,
 *     eksikler (tür, yaş sınırı, açılış/kapanış müzikleri, fragman, harici bağlantılar ...) diğer
 *     kaynaklardan tamamlanır. Galeri tarafı (Fanart.tv / TMDB / Shikimori görselleri) bu çapraz
 *     kimlikleri [resolveCrossIds] ile kullanır.
 *
 * Her fonksiyon hata durumunda boş sonuç döndürür; ağ hatası sayfayı düşürmez.
 */
object KitsugiBangumiDetailClient {
    private const val TAG = "KitsugiBangumiDetail"
    const val SOURCE = BangumiIdNamespace.SOURCE

    /**
     * Çapraz kimlik çözümü (AniList arama + ARM) için süre tavanı. ViewModel zenginleştirme adımını
     * 20 sn ile sınırlar (ardından TMDB TR meta + sıradaki bölüm sorgusu gelir); bu yüzden iki
     * tavanın toplamı (7 + 7 sn) bilinçli olarak bunun altında tutulur. Süre dolarsa o ana kadar
     * çözülenle devam edilir; çözülen kimlikler önbellekte kalır ve sekmeler/galeri bunları kullanır.
     */
    private const val CROSS_TIMEOUT_MS = 7_000L

    /** MAL/AniList/TMDB eşlik detayı için süre tavanı. */
    private const val COMPANION_TIMEOUT_MS = 7_000L

    /** Bulunamayan eşleşmeler için olumsuz önbellek süresi (tekrar tekrar arama yapılmasın). */
    private const val CROSS_MISS_TTL_MS = 20 * 60 * 1000L

    private const val ANILIST_ACCEPT_SCORE = 5

    private const val XREF_PREFS = "bangumi_xref_cache_v1"

    // ── Önbellekler ──────────────────────────────────────────────────────────

    private val subjectCache = ConcurrentHashMap<Int, BangumiSubject>()
    private val crossCache = ConcurrentHashMap<Int, CrossIds>()
    private val crossMissAt = ConcurrentHashMap<Int, Long>()

    /**
     * Çözümler çağıranın değil BU kapsamın içinde çalışır: detay (7 sn tavan), galeri (15 sn tavan) ve
     * sekmeler aynı anda istek yaptığında tek bir çözüm yürür; çağıranlardan biri süre aşımıyla
     * iptal olsa bile iş yarım kalıp baştan başlamaz, tamamlanınca önbelleğe yazılır.
     */
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<Int, Deferred<CrossIds>>()

    /** Bangumi kaydının diğer platformlardaki karşılıkları. */
    data class CrossIds(
        val aniListId: Int? = null,
        val malId: Int? = null,
        val tmdbId: Int? = null,
        val kitsuId: Int? = null,
        val tvdbId: Int? = null,
        val imdbId: String? = null
    ) {
        val hasAnyId: Boolean
            get() = aniListId != null || malId != null || tmdbId != null || kitsuId != null

        /** AniList "stableId" biçimi (`100_000_000 + aniListId`). */
        val aniListStableId: Int?
            get() = aniListId?.takeIf { it > 0 }?.let { 100_000_000 + it }
    }

    // ── Kimlik yardımcıları ──────────────────────────────────────────────────

    /** stableId (500M+) ya da ham subject_id → ham subject_id. Geçersizse null. */
    fun rawIdOf(id: Int?): Int? {
        if (id == null || id <= 0) return null
        return BangumiIdNamespace.rawIdFromStable(id) ?: id.takeIf { it < BangumiIdNamespace.STABLE_ID_MIN }
    }

    private fun stableIdOf(rawId: Int?): Int? = BangumiIdNamespace.stableIdFromRaw(rawId)

    /** Çağıran taraflardan gelen `realMalId` değeri gerçek bir MAL ID'si değilse (stableId vb.) atılır. */
    fun sanitizeMalId(value: Int?): Int? = value?.takeIf { it in 1..99_999_999 }

    // ── Ağ yardımcıları ──────────────────────────────────────────────────────

    private suspend fun tokenOrNull(): String? {
        val context = KitsugiApplication.getInstance()?.applicationContext ?: return null
        return runCatching {
            if (BangumiAuthStore.isConnected(context)) BangumiAuthStore.getValidToken(context) else null
        }.getOrNull()
    }

    /**
     * `next.bgm.tv/p1` GET. Önce anonim denenir (herkese açık uçlar); sonuç yoksa ve oturum varsa
     * token ile bir kez daha denenir (NSFW条目 yalnızca yetkili hesaplara görünür).
     */
    private suspend fun p1(path: String, query: Map<String, String?> = emptyMap()): JSONObject? {
        suspend fun call(token: String?): JSONObject? = try {
            BangumiApiClient.getRaw(path, query, token = token, next = true)?.let { JSONObject(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "p1 $path başarısız: ${e.message}")
            null
        }
        call(null)?.let { return it }
        val token = tokenOrNull() ?: return null
        return call(token)
    }

    /** `GET /v0/subjects/{id}` (önbellekli). */
    suspend fun loadSubject(rawId: Int): BangumiSubject? {
        subjectCache[rawId]?.let { return it }
        suspend fun fetch(token: String?): BangumiSubject? = try {
            BangumiApiClient.getSubject(rawId, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Bangumi条目 $rawId alınamadı (${if (token == null) "anonim" else "oturumlu"}): ${e.message}")
            null
        }
        val subject = fetch(null) ?: tokenOrNull()?.let { fetch(it) } ?: return null
        if (subjectCache.size > 64) subjectCache.clear()
        subjectCache[rawId] = subject
        return subject
    }

    // ── JSON yardımcıları ────────────────────────────────────────────────────

    /** Android `optString` JSON `null` için "null" döndürür; burada boş metne çevrilir. */
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "").trim()

    private fun JSONObject.strOrNull(key: String): String? = str(key).takeIf { it.isNotEmpty() }

    private fun JSONObject?.image(vararg keys: String): String? {
        if (this == null) return null
        for (key in keys) {
            val value = strOrNull(key) ?: continue
            return BangumiApiClient.absoluteImageUrl(value)
        }
        return null
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    /** p1 isimleri: Çince ad varsa o, yoksa özgün ad (alt başlıklar için ikinci ad ayrıca verilir). */
    private fun JSONObject.displayName(): String = strOrNull("nameCN") ?: str("name")

    // ═════════════════════════════════════════════════════════════════════════
    // 1) ANA DETAY (Bangumi-yerel) — sayfayı hemen açar
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Yalnızca Bangumi verisinden oluşan ana detay. Hızlıdır (tek istek, önbellekli); diğer
     * platformlarla birleştirme [enrich] ile ayrı ve zaman tavanlı yapılır.
     */
    suspend fun fetchDetail(stableOrRawId: Int, mediaType: MediaType): KitsugiMediaDetail? =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext null
            val subject = loadSubject(rawId) ?: return@withContext null
            buildNativeDetail(subject, mediaType)
        }

    /**
     * Bangumi detayını MAL / AniList / TMDB verisiyle tamamlar. Başarısız olursa [base] döner.
     * Dönen detayda `realMalId` ve `tmdbId` doludur; böylece Fanart.tv / TMDB / Shikimori galerisi,
     * bölüm puanları, logo ve MDBList aynı kimliklerle çalışır.
     */
    suspend fun enrich(stableOrRawId: Int, mediaType: MediaType, base: KitsugiMediaDetail): KitsugiMediaDetail =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext base
            val resolved: CrossIds? = try {
                withTimeoutOrNull(CROSS_TIMEOUT_MS) { resolveCrossIds(rawId, mediaType) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val cross = resolved ?: CrossIds()
            if (!cross.hasAnyId) return@withContext base
            val companion: KitsugiMediaDetail? = try {
                withTimeoutOrNull(COMPANION_TIMEOUT_MS) { fetchCompanionDetail(cross, mediaType) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Eşlik detayı alınamadı: ${e.message}")
                null
            }
            mergeDetail(base, companion, cross, mediaType)
        }

    private suspend fun fetchCompanionDetail(cross: CrossIds, mediaType: MediaType): KitsugiMediaDetail? =
        when (mediaType) {
            MediaType.Anime, MediaType.Manga -> {
                val fromMal = cross.malId?.let { malId ->
                    try {
                        KitsugiMalDetailClient.fetchDetail(malId, mediaType)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                fromMal ?: cross.aniListStableId?.let { stable ->
                    try {
                        KitsugiAniListDetailClient.fetchDetail(stable, mediaType)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            MediaType.TvShow, MediaType.Movie -> cross.tmdbId?.let { tmdbId ->
                try {
                    TmdbApiClient().fetchMediaDetail(tmdbId, mediaType == MediaType.Movie)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }

    internal fun buildNativeDetail(subject: BangumiSubject, mediaType: MediaType): KitsugiMediaDetail {
        val infobox = subject.infobox

        fun values(vararg keys: String): List<String> {
            for (key in keys) infobox[key]?.takeIf { it.isNotEmpty() }?.let { return it }
            return emptyList()
        }

        fun first(vararg keys: String): String? = values(*keys).firstOrNull()?.takeIf { it.isNotBlank() }

        /** Verilen anahtarların HEPSİNDEKİ değerleri birleştirir (örn. ana + diğer kanallar). */
        fun valuesAll(vararg keys: String): List<String> = keys.flatMap { infobox[it].orEmpty() }

        val aliases = values("别名", "別名", "英文名", "又名", "原名")
            .flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val englishAlias = aliases.firstOrNull { isMostlyLatin(it) && it.any { c -> c.isLetter() } }
        val primaryTitle = subject.displayTitle
        val synonyms = (listOfNotNull(subject.nameCn.takeIf { it.isNotBlank() }) + aliases + listOfNotNull(subject.name))
            .filter { it.isNotBlank() && !it.equals(primaryTitle, ignoreCase = true) }
            .distinct()

        val startIso = isoDate(first("放送开始", "放送開始", "上映年度", "发售日", "發售日", "开始", "首播") ?: subject.date)
            ?: isoDate(subject.date)
        val endIso = isoDate(first("播放结束", "放送结束", "播放結束", "结束", "完结"))
        val year = startIso?.take(4)?.toIntOrNull() ?: subject.date?.take(4)?.toIntOrNull()
        val isMovie = subject.platform?.contains("剧场版") == true
        val total = subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes.takeIf { it > 0 }
            ?: first("话数", "話数")?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }?.takeIf { it > 0 }

        val rating = subject.rating
        val ratingScore = rating?.score?.takeIf { it > 0.0 }
        val collection = subject.collection
        val trackedTotal = collection?.total?.takeIf { it > 0 }

        // ── Stüdyo / yapımcı / kanal ─────────────────────────────────────────
        val studios = splitCompanies(values("动画制作", "動畫製作", "动画制作公司", "アニメーション制作", "制作", "制作公司"))
            .map { KitsugiStudio(id = 0, name = it, isMain = true, source = SOURCE, role = StudioRole.STUDIO) }
        val producers = if (mediaType == MediaType.Manga) {
            splitCompanies(values("出版社", "出版"))
                .map { KitsugiStudio(id = 0, name = it, isMain = true, source = SOURCE, role = StudioRole.PUBLISHER) }
        } else {
            splitCompanies(values("製作", "出品", "发行", "製作委员会", "制作委员会"))
                .filter { name -> studios.none { it.name.equals(name, ignoreCase = true) } }
                .map { KitsugiStudio(id = 0, name = it, isMain = false, source = SOURCE, role = StudioRole.PRODUCER) }
        }
        val networks = splitNetworks(valuesAll("播放电视台", "播放電視台", "其他电视台", "其他電視台"))
            .map { KitsugiStudio(id = 0, name = it, isMain = false, source = SOURCE, role = StudioRole.NETWORK) }
        val serializations = splitCompanies(values("连载杂志", "連載雜誌", "杂志"))
            .map { KitsugiStudio(id = 0, name = it, isMain = true, source = SOURCE, role = StudioRole.MAGAZINE) }

        // ── Yayın bilgisi ────────────────────────────────────────────────────
        val weekday = first("放送星期")?.let { weekdayEnglish(it) }
        val station = infobox["播放电视台"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        val broadcast = weekday?.let { day -> if (station != null) "$day ($station)" else day }
            ?.toTurkishBroadcast()
        val episodeDuration = first("片长", "片長", "时长")?.let { durationText(it, isMovie) }

        // ── Dış bağlantılar ──────────────────────────────────────────────────
        val links = mutableListOf(KitsugiExternalLink(site = "Bangumi", url = subject.webUrl))
        first("官方网站", "官方網站", "官网")?.takeIf { it.startsWith("http", ignoreCase = true) }?.let {
            links += KitsugiExternalLink(site = "Resmi Site", url = it)
        }

        val cover = BangumiApiClient.absoluteImageUrl(subject.images?.large ?: subject.images?.poster)

        return KitsugiMediaDetail(
            synopsis = subject.summary.replace("\r\n", "\n").replace('\r', '\n').trim().takeIf { it.isNotEmpty() },
            genres = subject.metaTags.take(8),
            status = statusOf(subject, startIso, endIso, mediaType)?.toTurkishStatus(),
            season = seasonOf(startIso, mediaType)?.toTurkishSeason(),
            sourceMaterial = first("原作")?.takeIf { mediaType != MediaType.Manga },
            studios = if (mediaType == MediaType.Manga) emptyList() else studios,
            producers = producers,
            broadcast = broadcast,
            episodeDuration = episodeDuration,
            startDate = startIso,
            endDate = endIso,
            titleEnglish = englishAlias,
            titleJapanese = subject.name.takeIf { it.isNotBlank() && !it.equals(subject.nameCn, ignoreCase = true) },
            titleNative = subject.name.takeIf { it.isNotBlank() },
            synonyms = synonyms,
            title = primaryTitle,
            imageUrl = cover,
            type = mediaType,
            score = ratingScore?.let { Math.round(it).toInt().coerceIn(0, 10) },
            year = year,
            total = total,
            isAdult = subject.nsfw,
            tags = subject.tags.take(40).map { KitsugiTag(name = it, rank = null, isSpoiler = false, source = SOURCE) },
            externalLinks = links,
            pictures = emptyList(),
            meanScore = ratingScore?.let { (it * 10).toInt() },
            averageScore = ratingScore?.let { (it * 10).toInt() },
            popularity = trackedTotal,
            rank = rating?.rank?.takeIf { it > 0 },
            scoredBy = rating?.total?.takeIf { it > 0 },
            members = trackedTotal,
            networks = networks,
            serializations = serializations,
            format = formatOf(subject.platform),
            volumes = subject.volumes.takeIf { it > 0 },
            rawFormat = subject.platform,
            seasonYear = year
        )
    }

    internal fun mergeDetail(
        base: KitsugiMediaDetail,
        other: KitsugiMediaDetail?,
        cross: CrossIds,
        mediaType: MediaType
    ): KitsugiMediaDetail {
        val links = (base.externalLinks + crossLinks(cross, mediaType) + other?.externalLinks.orEmpty())
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url.trim().trimEnd('/').lowercase(Locale.ROOT) }
        if (other == null) {
            return base.copy(
                realMalId = cross.malId ?: base.realMalId,
                tmdbId = cross.tmdbId ?: base.tmdbId,
                externalLinks = links
            )
        }
        val mergedTags = (other.tags + base.tags)
            .distinctBy { it.name.trim().lowercase(Locale.ROOT) }
            .take(48)
        return base.copy(
            // Bangumi'nin (Çince) özeti öncelikli; TMDB/Türkçe zenginleştirme ayrıca üstüne yazabilir.
            synopsis = base.synopsis?.takeIf { it.isNotBlank() } ?: other.synopsis,
            // Türler diğer platformdan (çevrilebilir, tıklanınca arama yapar); yoksa Bangumi meta etiketleri.
            genres = other.genres.ifEmpty { base.genres },
            status = base.status ?: other.status,
            season = base.season ?: other.season,
            sourceMaterial = other.sourceMaterial ?: base.sourceMaterial,
            studios = other.studios.ifEmpty { base.studios },
            producers = other.producers.ifEmpty { base.producers },
            rating = other.rating ?: base.rating,
            broadcast = base.broadcast ?: other.broadcast,
            episodeDuration = base.episodeDuration ?: other.episodeDuration,
            startDate = base.startDate ?: other.startDate,
            endDate = base.endDate ?: other.endDate,
            titleEnglish = other.titleEnglish ?: base.titleEnglish,
            titleRomaji = other.titleRomaji ?: base.titleRomaji,
            synonyms = (base.synonyms + other.synonyms).filter { it.isNotBlank() }.distinct(),
            openings = other.openings,
            endings = other.endings,
            trailerUrl = other.trailerUrl ?: base.trailerUrl,
            bannerImage = other.bannerImage ?: base.bannerImage,
            isAdult = base.isAdult || other.isAdult,
            realMalId = cross.malId ?: other.realMalId ?: base.realMalId,
            tags = mergedTags,
            externalLinks = links,
            streamingLinks = other.streamingLinks.ifEmpty { base.streamingLinks },
            streamingEpisodes = other.streamingEpisodes.ifEmpty { base.streamingEpisodes },
            tmdbId = cross.tmdbId ?: other.tmdbId ?: base.tmdbId,
            tmdbSeason = other.tmdbSeason ?: base.tmdbSeason,
            // Galeri: MAL / AniList / TMDB kapakları ve afişleri de eklenir (Bangumi kapağı ana görseldir).
            pictures = (base.pictures + other.pictures + listOfNotNull(other.imageUrl, other.bannerImage))
                .filter { it.isNotBlank() && galleryDedupKey(it) != base.imageUrl?.let { cover -> galleryDedupKey(cover) } }
                .distinctBy { galleryDedupKey(it) },
            totalSeasons = other.totalSeasons ?: base.totalSeasons,
            nextAiringEpisode = other.nextAiringEpisode ?: base.nextAiringEpisode,
            favorites = other.favorites ?: base.favorites,
            popularityRank = other.popularityRank ?: base.popularityRank,
            themes = other.themes.ifEmpty { base.themes },
            demographics = other.demographics.ifEmpty { base.demographics },
            serializations = other.serializations.ifEmpty { base.serializations },
            networks = base.networks.ifEmpty { other.networks },
            authors = other.authors.ifEmpty { base.authors },
            rankings = other.rankings.ifEmpty { base.rankings },
            countryOfOrigin = other.countryOfOrigin ?: base.countryOfOrigin,
            originalLanguage = other.originalLanguage ?: base.originalLanguage,
            tagline = other.tagline ?: base.tagline,
            budget = other.budget ?: base.budget,
            revenue = other.revenue ?: base.revenue,
            volumes = base.volumes ?: other.volumes
        )
    }

    private fun crossLinks(cross: CrossIds, mediaType: MediaType): List<KitsugiExternalLink> = buildList {
        val kind = if (mediaType == MediaType.Manga) "manga" else "anime"
        if (mediaType == MediaType.Anime || mediaType == MediaType.Manga) {
            cross.malId?.let { add(KitsugiExternalLink("MyAnimeList", "https://myanimelist.net/$kind/$it")) }
            cross.aniListId?.let { add(KitsugiExternalLink("AniList", "https://anilist.co/$kind/$it")) }
            cross.kitsuId?.let { add(KitsugiExternalLink("Kitsu", "https://kitsu.app/$kind/$it")) }
        } else {
            cross.tmdbId?.let {
                val path = if (mediaType == MediaType.Movie) "movie" else "tv"
                add(KitsugiExternalLink("TMDB", "https://www.themoviedb.org/$path/$it"))
            }
        }
        cross.imdbId?.takeIf { it.isNotBlank() }?.let { add(KitsugiExternalLink("IMDb", "https://www.imdb.com/title/$it/")) }
        cross.tvdbId?.let { add(KitsugiExternalLink("TheTVDB", "https://thetvdb.com/?tab=series&id=$it")) }
    }

    /**
     * Galeri tekilleştirme anahtarı. `lain.bgm.tv` aynı kapağı farklı boyut yollarıyla sunar
     * (`/pic/cover/l/..`, `/r/400/pic/cover/l/..`, `/r/800/..`, `/r/100/..`); bunlar aynı görseldir ve
     * galeride kopya görünmemelidir. Diğer URL'ler olduğu gibi anahtar olur.
     */
    fun galleryDedupKey(url: String): String {
        val match = BANGUMI_IMAGE_REGEX.find(url)
        return if (match != null) "bgm:${match.groupValues[1]}" else url
    }

    private val BANGUMI_IMAGE_REGEX = Regex("lain\\.bgm\\.tv/(?:r/\\d+/)?pic/[a-z]+/[a-z]/(.+)$")

    // ═════════════════════════════════════════════════════════════════════════
    // 2) ÇAPRAZ KİMLİK ÇÖZÜMÜ (Bangumi → AniList / MAL / TMDB / Kitsu ...)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Bangumi'de MAL/AniList kimliği veren bir uç yoktur (ARM de Bangumi anahtarını içermez).
     * Bu yüzden:
     *  1. Kalıcı/bellek önbelleği,
     *  2. AniList araması (Bangumi özgün adı + takma adlar) — başlık + yıl + bölüm sayısı + format
     *     puanlamasıyla SIKI eşleştirme → AniList id + idMal,
     *  3. AniList bulamazsa Jikan (MAL) araması,
     *  4. ARM ile TMDB / Kitsu / TVDB / IMDb kimlikleri.
     * Canlı çekim (三次元) kayıtlarda TMDB araması kullanılır. Yanlış eşleşme yerine "eşleşme yok"
     * tercih edilir; olumsuz sonuç 20 dk bellekte tutulur.
     */
    suspend fun resolveCrossIds(stableOrRawId: Int?, mediaType: MediaType, subjectHint: BangumiSubject? = null): CrossIds {
        val rawId = rawIdOf(stableOrRawId) ?: return CrossIds()
        crossCache[rawId]?.let { return it }
        loadPersistedCross(rawId)?.let {
            crossCache[rawId] = it
            return it
        }
        val missAt = crossMissAt[rawId]
        if (missAt != null && System.currentTimeMillis() - missAt < CROSS_MISS_TTL_MS) return CrossIds()

        val job = inFlight.computeIfAbsent(rawId) {
            resolveScope.async {
                try {
                    val resolved = try {
                        doResolveCross(rawId, mediaType, subjectHint)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Çapraz kimlik çözümü başarısız ($rawId): ${e.message}")
                        CrossIds()
                    }
                    if (resolved.hasAnyId) {
                        crossCache[rawId] = resolved
                        persistCross(rawId, resolved)
                    } else {
                        crossMissAt[rawId] = System.currentTimeMillis()
                    }
                    resolved
                } finally {
                    inFlight.remove(rawId)
                }
            }
        }
        return job.await()
    }

    private suspend fun doResolveCross(rawId: Int, mediaType: MediaType, hint: BangumiSubject?): CrossIds {
        val subject = hint ?: loadSubject(rawId) ?: return CrossIds()
        return when (mediaType) {
            MediaType.Anime, MediaType.Manga -> {
                val anilistType = if (mediaType == MediaType.Manga) "MANGA" else "ANIME"
                val match = matchOnAniList(subject, anilistType) ?: matchOnJikan(subject, mediaType)
                if (match == null) {
                    Log.i(TAG, "Bangumi $rawId için AniList/MAL eşleşmesi bulunamadı")
                    return CrossIds()
                }
                val resolved = runCatching {
                    KitsugiIdResolver.resolveIds(
                        malId = match.malId,
                        aniListId = match.aniListId,
                        tmdbId = null,
                        mediaType = mediaType,
                        kitsuId = null
                    )
                }.getOrNull()
                CrossIds(
                    aniListId = resolved?.aniListId ?: match.aniListId,
                    malId = resolved?.malId ?: match.malId,
                    tmdbId = resolved?.tmdbId,
                    kitsuId = resolved?.kitsuId,
                    tvdbId = resolved?.tvdbId,
                    imdbId = resolved?.imdbId
                )
            }
            MediaType.TvShow, MediaType.Movie -> matchOnTmdb(subject, mediaType)
        }
    }

    private data class Match(val aniListId: Int?, val malId: Int?, val score: Int)

    private fun searchQueries(subject: BangumiSubject): List<String> {
        val aliases = subject.infobox["别名"].orEmpty() + subject.infobox["英文名"].orEmpty()
        val latinAlias = aliases.firstOrNull { isMostlyLatin(it) && it.any { c -> c.isLetter() } }
        return listOfNotNull(subject.name, latinAlias, subject.nameCn)
            .map { sanitizeQuery(it) }
            .filter { it.length >= 2 }
            .distinct()
            .take(3)
    }

    internal fun sanitizeQuery(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    internal fun normalizeTitle(text: String?): String =
        Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    private fun subjectTitleSet(subject: BangumiSubject): Set<String> =
        (listOf(subject.name, subject.nameCn) + subject.infobox["别名"].orEmpty() + subject.infobox["英文名"].orEmpty())
            .map { normalizeTitle(it) }
            .filter { it.length >= 2 }
            .toSet()

    /** Başlık/yıl/bölüm/format puanlaması — eşik [ANILIST_ACCEPT_SCORE]. Yıl farkı ≥ 2 ise elenir. */
    internal fun scoreCandidate(
        subjectTitles: Set<String>,
        subjectYear: Int?,
        subjectEpisodes: Int,
        candidateTitles: List<String?>,
        candidateYear: Int?,
        candidateEpisodes: Int?,
        formatCompatible: Boolean?
    ): Int {
        val cand = candidateTitles.map { normalizeTitle(it) }.filter { it.length >= 2 }.toSet()
        var score = 0
        score += when {
            subjectTitles.any { it in cand } -> 4
            subjectTitles.any { s -> cand.any { c -> s.length >= 5 && c.length >= 5 && (s.contains(c) || c.contains(s)) } } -> 1
            else -> return 0
        }
        if (subjectYear != null && candidateYear != null) {
            val diff = kotlin.math.abs(subjectYear - candidateYear)
            score += when {
                diff == 0 -> 2
                diff == 1 -> 1
                else -> return 0
            }
        }
        if (subjectEpisodes > 0 && candidateEpisodes != null && candidateEpisodes > 0 && subjectEpisodes == candidateEpisodes) score += 1
        if (formatCompatible == true) score += 1 else if (formatCompatible == false) score -= 2
        return score
    }

    internal fun formatCompatible(platform: String?, anilistFormat: String?): Boolean? {
        val format = anilistFormat?.uppercase(Locale.ROOT) ?: return null
        val p = platform?.trim().orEmpty()
        return when {
            p.isEmpty() -> null
            p.equals("TV", ignoreCase = true) -> format in setOf("TV", "TV_SHORT", "ONA")
            p.equals("OVA", ignoreCase = true) -> format in setOf("OVA", "SPECIAL", "ONA")
            p.contains("剧场版") -> format == "MOVIE"
            p.equals("WEB", ignoreCase = true) -> format in setOf("ONA", "TV", "OVA", "TV_SHORT")
            p.contains("漫画") -> format in setOf("MANGA", "ONE_SHOT")
            p.contains("小说") -> format == "NOVEL"
            else -> null
        }
    }

    private suspend fun matchOnAniList(subject: BangumiSubject, anilistType: String): Match? {
        val titles = subjectTitleSet(subject)
        if (titles.isEmpty()) return null
        val subjectYear = subject.date?.take(4)?.toIntOrNull()
        val query = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
              Page(page: 1, perPage: 8) {
                media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH) {
                  id idMal format episodes chapters
                  startDate { year }
                  title { romaji english native }
                  synonyms
                }
              }
            }
        """.trimIndent()
        for (q in searchQueries(subject)) {
            val response = try {
                KitsugiApiBase.executeAniListQuery(query, JSONObject().put("search", q).put("type", anilistType))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            val media = runCatching {
                JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media").objects()
            }.getOrDefault(emptyList())
            var best: Match? = null
            for (m in media) {
                val id = m.optInt("id", 0).takeIf { it > 0 } ?: continue
                val title = m.optJSONObject("title")
                val synonyms = m.optJSONArray("synonyms")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
                val episodes = if (anilistType == "MANGA") m.optInt("chapters", 0) else m.optInt("episodes", 0)
                val score = scoreCandidate(
                    subjectTitles = titles,
                    subjectYear = subjectYear,
                    subjectEpisodes = if (anilistType == "MANGA") 0 else subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes,
                    candidateTitles = listOf(title?.strOrNull("romaji"), title?.strOrNull("english"), title?.strOrNull("native")) + synonyms,
                    candidateYear = m.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 },
                    candidateEpisodes = episodes.takeIf { it > 0 },
                    formatCompatible = formatCompatible(subject.platform, m.strOrNull("format"))
                )
                if (score >= ANILIST_ACCEPT_SCORE && (best == null || score > best.score)) {
                    best = Match(aniListId = id, malId = m.optInt("idMal", 0).takeIf { it > 0 }, score = score)
                }
            }
            if (best != null) {
                Log.i(TAG, "Bangumi ${subject.id} → AniList ${best.aniListId} / MAL ${best.malId} (puan=${best.score}, q='$q')")
                return best
            }
        }
        return null
    }

    private suspend fun matchOnJikan(subject: BangumiSubject, mediaType: MediaType): Match? {
        val titles = subjectTitleSet(subject)
        if (titles.isEmpty()) return null
        val subjectYear = subject.date?.take(4)?.toIntOrNull()
        val endpoint = if (mediaType == MediaType.Manga) "manga" else "anime"
        for (q in searchQueries(subject)) {
            val url = runCatching {
                URL("https://api.jikan.moe/v4/$endpoint?q=${URLEncoder.encode(q, "UTF-8")}&limit=8")
            }.getOrNull() ?: continue
            val response = try {
                KitsugiApiBase.executeGetRequestResilient(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            val items = runCatching { JSONObject(response).optJSONArray("data").objects() }.getOrDefault(emptyList())
            var best: Match? = null
            for (item in items) {
                val malId = item.optInt("mal_id", 0).takeIf { it > 0 } ?: continue
                val alt = item.optJSONArray("titles").objects().map { it.strOrNull("title") }
                val year = item.optInt("year", 0).takeIf { it > 0 }
                    ?: item.optJSONObject("aired")?.optJSONObject("prop")?.optJSONObject("from")?.optInt("year", 0)?.takeIf { it > 0 }
                    ?: item.optJSONObject("published")?.optJSONObject("prop")?.optJSONObject("from")?.optInt("year", 0)?.takeIf { it > 0 }
                val episodes = if (mediaType == MediaType.Manga) 0 else item.optInt("episodes", 0)
                val score = scoreCandidate(
                    subjectTitles = titles,
                    subjectYear = subjectYear,
                    subjectEpisodes = if (mediaType == MediaType.Manga) 0 else subject.eps.takeIf { it > 0 } ?: subject.totalEpisodes,
                    candidateTitles = listOf(item.strOrNull("title"), item.strOrNull("title_english"), item.strOrNull("title_japanese")) + alt,
                    candidateYear = year,
                    candidateEpisodes = episodes.takeIf { it > 0 },
                    formatCompatible = null
                )
                if (score >= ANILIST_ACCEPT_SCORE && (best == null || score > best.score)) {
                    best = Match(aniListId = null, malId = malId, score = score)
                }
            }
            if (best != null) {
                Log.i(TAG, "Bangumi ${subject.id} → MAL ${best.malId} (Jikan, puan=${best.score}, q='$q')")
                return best
            }
        }
        return null
    }

    private suspend fun matchOnTmdb(subject: BangumiSubject, mediaType: MediaType): CrossIds {
        val titles = subjectTitleSet(subject)
        val subjectYear = subject.date?.take(4)?.toIntOrNull()
        for (q in listOfNotNull(subject.nameCn.takeIf { it.isNotBlank() }, subject.name.takeIf { it.isNotBlank() }).distinct()) {
            val results = try {
                TmdbApiClient().search(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val hit = results.firstOrNull { r ->
                val tmdbId = r.tmdbId ?: return@firstOrNull false
                if (tmdbId <= 0 || r.type != mediaType) return@firstOrNull false
                val names = listOf(r.title, r.titleEnglish, r.titleJapanese).map { normalizeTitle(it) }.filter { it.length >= 2 }
                val titleMatch = names.any { it in titles }
                val yearMatch = subjectYear != null && r.year != null && kotlin.math.abs(subjectYear - r.year) <= 1
                titleMatch && (yearMatch || subjectYear == null || r.year == null)
            }
            val tmdbId = hit?.tmdbId
            if (tmdbId != null && tmdbId > 0) {
                val resolved = runCatching {
                    KitsugiIdResolver.resolveIds(malId = null, aniListId = null, tmdbId = tmdbId, mediaType = mediaType)
                }.getOrNull()
                return CrossIds(
                    aniListId = resolved?.aniListId,
                    malId = resolved?.malId,
                    tmdbId = tmdbId,
                    kitsuId = resolved?.kitsuId,
                    tvdbId = resolved?.tvdbId,
                    imdbId = resolved?.imdbId
                )
            }
        }
        return CrossIds()
    }

    // ── Kalıcı önbellek ──────────────────────────────────────────────────────

    private fun loadPersistedCross(rawId: Int): CrossIds? {
        val context = KitsugiApplication.getInstance()?.applicationContext ?: return null
        val encoded = runCatching {
            context.getSharedPreferences(XREF_PREFS, android.content.Context.MODE_PRIVATE).getString("s$rawId", null)
        }.getOrNull() ?: return null
        val map = encoded.split(';').mapNotNull {
            val index = it.indexOf('=')
            if (index <= 0) null else it.substring(0, index) to it.substring(index + 1)
        }.toMap()
        val ids = CrossIds(
            aniListId = map["al"]?.toIntOrNull(),
            malId = map["mal"]?.toIntOrNull(),
            tmdbId = map["tmdb"]?.toIntOrNull(),
            kitsuId = map["kitsu"]?.toIntOrNull(),
            tvdbId = map["tvdb"]?.toIntOrNull(),
            imdbId = map["imdb"]?.takeIf { it.isNotBlank() }
        )
        return ids.takeIf { it.hasAnyId }
    }

    private fun persistCross(rawId: Int, ids: CrossIds) {
        val context = KitsugiApplication.getInstance()?.applicationContext ?: return
        val encoded = buildString {
            ids.aniListId?.let { append("al=").append(it).append(';') }
            ids.malId?.let { append("mal=").append(it).append(';') }
            ids.tmdbId?.let { append("tmdb=").append(it).append(';') }
            ids.kitsuId?.let { append("kitsu=").append(it).append(';') }
            ids.tvdbId?.let { append("tvdb=").append(it).append(';') }
            ids.imdbId?.let { append("imdb=").append(it).append(';') }
        }
        runCatching {
            context.getSharedPreferences(XREF_PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putString("s$rawId", encoded).apply()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3) SEKMELER (Bangumi-yerel)
    // ═════════════════════════════════════════════════════════════════════════

    /** Karakterler + seslendirmenler (`/p1/subjects/{id}/characters`). */
    suspend fun fetchCharacters(stableOrRawId: Int, mediaType: MediaType): List<KitsugiCharacter> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val result = mutableListOf<KitsugiCharacter>()
            var offset = 0
            var total = Int.MAX_VALUE
            var page = 0
            while (offset < total && page < 3) {
                val root = p1("/subjects/$rawId/characters", mapOf("limit" to "60", "offset" to offset.toString())) ?: break
                total = root.optInt("total", 0)
                val parsed = parseCharacters(root)
                val pageSize = root.optJSONArray("data").objects().size
                if (pageSize == 0) break
                result += parsed
                offset += pageSize
                page++
            }
            result.distinctBy { it.id }
        }

    /** `/p1/subjects/{id}/characters` yanıtı → karakter listesi (ağdan bağımsız, test edilebilir). */
    internal fun parseCharacters(root: JSONObject): List<KitsugiCharacter> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            val character = item.optJSONObject("character") ?: return@mapNotNull null
            val id = character.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val actors = item.optJSONArray("casts").objects().mapNotNull { cast ->
                val person = cast.optJSONObject("person") ?: return@mapNotNull null
                val personId = person.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                KitsugiVoiceActor(
                    id = personId,
                    name = person.displayName(),
                    language = castLanguage(cast.optInt("relation", 0)),
                    imageUrl = person.optJSONObject("images").image("medium", "large", "grid", "small"),
                    source = SOURCE
                )
            }.distinctBy { it.id }
            KitsugiCharacter(
                id = id,
                name = character.displayName(),
                role = characterRole(item.optInt("type", 2)).toTurkishCharacterRole(),
                imageUrl = character.optJSONObject("images").image("medium", "large", "grid", "small"),
                voiceActors = actors,
                source = SOURCE
            )
        }

    /** Ekip (`/p1/subjects/{id}/staffs/persons`). */
    suspend fun fetchStaff(stableOrRawId: Int, mediaType: MediaType): List<KitsugiStaff> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/staffs/persons", mapOf("limit" to "100")) ?: return@withContext emptyList()
            parseStaff(root)
        }

    /** `/p1/subjects/{id}/staffs/persons` yanıtı → ekip listesi. */
    internal fun parseStaff(root: JSONObject): List<KitsugiStaff> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            val staff = item.optJSONObject("staff") ?: return@mapNotNull null
            val id = staff.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val roles = item.optJSONArray("positions").objects()
                .mapNotNull { pos ->
                    val type = pos.optJSONObject("type")
                    type?.strOrNull("en") ?: type?.strOrNull("cn") ?: type?.strOrNull("jp")
                }
                .distinct()
                .take(3)
                .joinToString(", ")
                .ifBlank { "Staff" }
            KitsugiStaff(
                id = id,
                name = staff.displayName(),
                role = roles.toTurkishStaffRole(),
                imageUrl = staff.optJSONObject("images").image("medium", "large", "grid", "small"),
                source = SOURCE
            )
        }.distinctBy { it.id }

    /** İlişkili yapımlar (`/p1/subjects/{id}/relations`). */
    suspend fun fetchRelations(stableOrRawId: Int, mediaType: MediaType): List<KitsugiRelation> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/relations", mapOf("limit" to "100")) ?: return@withContext emptyList()
            parseRelations(root)
        }

    /** `/p1/subjects/{id}/relations` yanıtı → ilişkili yapımlar. */
    internal fun parseRelations(root: JSONObject): List<KitsugiRelation> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            val subject = item.optJSONObject("subject") ?: return@mapNotNull null
            val relation = item.optJSONObject("relation")
            val label = relationLabel(relation?.strOrNull("cn"), relation?.strOrNull("en"))
            subject.toRelation(label)
        }.distinctBy { it.malId }

    /** Öneriler (`/p1/subjects/{id}/recs`; sunucu en fazla 10 kayıt verir). */
    suspend fun fetchRecommendations(stableOrRawId: Int, mediaType: MediaType): List<KitsugiRelation> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/recs", mapOf("limit" to "10")) ?: return@withContext emptyList()
            parseRecommendations(root)
        }

    /** `/p1/subjects/{id}/recs` yanıtı → öneriler. */
    internal fun parseRecommendations(root: JSONObject): List<KitsugiRelation> =
        root.optJSONArray("data").objects().mapNotNull { item ->
            item.optJSONObject("subject")?.toRelation("Recommendation")
        }.distinctBy { it.malId }

    private fun JSONObject.toRelation(relationType: String): KitsugiRelation? {
        val type = optInt("type", BangumiApiClient.SubjectType.ANIME)
        // Müzik / oyun kayıtlarını uygulama gösteremez (MediaType karşılığı yok).
        if (type != BangumiApiClient.SubjectType.ANIME &&
            type != BangumiApiClient.SubjectType.BOOK &&
            type != BangumiApiClient.SubjectType.REAL
        ) return null
        val rawId = optInt("id", 0).takeIf { it > 0 } ?: return null
        val stable = stableIdOf(rawId) ?: return null
        val cn = strOrNull("nameCN")
        val native = strOrNull("name")
        return KitsugiRelation(
            malId = stable,
            title = cn ?: native ?: "Bangumi #$rawId",
            relationType = relationType,
            imageUrl = optJSONObject("images").image("common", "medium", "large", "grid", "small"),
            mediaType = BangumiIdNamespace.mediaTypeFor(type),
            source = SOURCE,
            titleJapanese = native?.takeIf { !it.equals(cn, ignoreCase = true) },
            isAdult = optBoolean("nsfw", false)
        )
    }

    /** Puan dağılımı + koleksiyon durumu + sıralama (v0 yanıtından; ek istek yok). */
    suspend fun fetchStats(stableOrRawId: Int, mediaType: MediaType): KitsugiStats? =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext null
            val subject = loadSubject(rawId) ?: return@withContext null
            buildStats(subject)
        }

    /** v0 条目 yanıtından istatistik (puan dağılımı + koleksiyon + sıralama). */
    internal fun buildStats(subject: BangumiSubject): KitsugiStats? {
        val collection = subject.collection
        val rating = subject.rating
        val scores = (1..10).map { KitsugiScoreStat(it, rating?.count?.get(it) ?: 0) }
        val hasScores = scores.any { it.amount > 0 }
        val rankings = rating?.rank?.takeIf { it > 0 }?.let { rank ->
            listOf(KitsugiRanking(rank = rank, type = "RATED", context = "Bangumi highest rated all time", allTime = true))
        }.orEmpty()
        if (collection == null && !hasScores && rankings.isEmpty()) return null
        return KitsugiStats(
            watching = collection?.doing?.takeIf { it > 0 },
            completed = collection?.collect?.takeIf { it > 0 },
            planned = collection?.wish?.takeIf { it > 0 },
            dropped = collection?.dropped?.takeIf { it > 0 },
            paused = collection?.onHold?.takeIf { it > 0 },
            scoreDistribution = if (hasScores) scores else emptyList(),
            rankings = rankings
        )
    }

    /**
     * Yorumlar: uzun incelemeler (`/reviews`, tam metin `/blogs/{id}`) + kısa kullanıcı yorumları
     * (`/comments`, puanlı). Sayfa başına 10 inceleme + 20 yorum.
     */
    suspend fun fetchReviews(stableOrRawId: Int, page: Int): List<KitsugiReview> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val pageIndex = (page - 1).coerceAtLeast(0)
            coroutineScope {
                val reviewsDef = async {
                    val root = p1("/subjects/$rawId/reviews", mapOf("limit" to "10", "offset" to (pageIndex * 10).toString()))
                    val items = root?.optJSONArray("data").objects()
                    // Tam metinler paralel ve süre sınırlı çekilir; gelmezse özet gösterilir.
                    items.map { item ->
                        async {
                            val entry = item.optJSONObject("entry")
                            val entryId = entry?.optInt("id", 0) ?: 0
                            val body = if (entryId > 0) {
                                withTimeoutOrNull(4_000L) { p1("/blogs/$entryId") }?.strOrNull("content")?.let { cleanMarkup(it) }
                            } else null
                            reviewFromBlog(item, body)
                        }
                    }.awaitAll().filterNotNull()
                }
                val commentsDef = async {
                    val root = p1("/subjects/$rawId/comments", mapOf("limit" to "20", "offset" to (pageIndex * 20).toString()))
                    root?.optJSONArray("data").objects().mapNotNull { reviewFromComment(it) }
                }
                (reviewsDef.await() + commentsDef.await())
            }
        }

    internal fun reviewFromBlog(item: JSONObject, fullBody: String?): KitsugiReview? {
        val entry = item.optJSONObject("entry") ?: return null
        val user = item.optJSONObject("user") ?: entry.optJSONObject("user")
        val title = entry.strOrNull("title").orEmpty()
        val summary = entry.strOrNull("summary")?.let { cleanMarkup(it) }.orEmpty()
        val body = fullBody?.let { cleanMarkup(it) }?.takeIf { it.isNotBlank() } ?: summary
        if (title.isEmpty() && body.isEmpty()) return null
        return KitsugiReview(
            id = null,
            userId = null,
            username = user?.strOrNull("nickname") ?: user?.strOrNull("username") ?: "Bangumi",
            avatarUrl = user?.optJSONObject("avatar").image("medium", "large", "small"),
            score = null,
            summary = (if (title.isNotEmpty()) title else summary).take(240),
            fullText = (if (title.isNotEmpty() && body.isNotEmpty()) "$title\n\n$body" else body.ifEmpty { title }),
            dateText = epochDate(entry.optLong("createdAt", 0L)),
            helpfulCount = entry.optInt("replies", 0).takeIf { it > 0 }
        )
    }

    internal fun reviewFromComment(item: JSONObject): KitsugiReview? {
        val text = item.strOrNull("comment")?.let { cleanMarkup(it) }.orEmpty()
        if (text.isBlank()) return null
        val user = item.optJSONObject("user")
        return KitsugiReview(
            id = null,
            userId = null,
            username = user?.strOrNull("nickname") ?: user?.strOrNull("username") ?: "Bangumi",
            avatarUrl = user?.optJSONObject("avatar").image("medium", "large", "small"),
            score = item.optInt("rate", 0).takeIf { it in 1..10 },
            summary = text.take(240),
            fullText = text,
            dateText = epochDate(item.optLong("updatedAt", 0L))
        )
    }

    /** Ana hikâye bölümleri (`/p1/subjects/{id}/episodes?type=0`). */
    suspend fun fetchEpisodes(stableOrRawId: Int): List<KitsugiStreamingEpisode> =
        withContext(Dispatchers.IO) {
            val rawId = rawIdOf(stableOrRawId) ?: return@withContext emptyList()
            val root = p1("/subjects/$rawId/episodes", mapOf("type" to "0", "limit" to "1000")) ?: return@withContext emptyList()
            parseEpisodes(root)
        }

    /** `/p1/subjects/{id}/episodes` yanıtı → bölüm listesi (yalnızca ana hikâye, tekil numara). */
    internal fun parseEpisodes(root: JSONObject): List<KitsugiStreamingEpisode> {
        val seen = HashSet<Int>()
        return root.optJSONArray("data").objects().mapNotNull { ep ->
            val id = ep.optInt("id", 0)
            val sort = ep.optDouble("sort", Double.NaN)
            if (sort.isNaN() || sort <= 0.0) return@mapNotNull null
            val number = sort.toInt()
            if (number <= 0 || !seen.add(number)) return@mapNotNull null
            val name = ep.strOrNull("nameCN") ?: ep.strOrNull("name")
            KitsugiStreamingEpisode(
                title = if (name != null) "#$number – $name" else "Bölüm $number",
                thumbnail = null,
                url = if (id > 0) "${BangumiApiClient.SITE_BASE}/ep/$id" else null,
                site = "Bangumi",
                seasonNumber = 1,
                episodeNumber = number
            )
        }.sortedBy { it.episodeNumber ?: 0 }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4) KARAKTER / KİŞİ DETAY SAYFALARI
    // ═════════════════════════════════════════════════════════════════════════

    suspend fun fetchCharacterDetail(characterId: Int): KitsugiCharacterDetail? =
        withContext(Dispatchers.IO) {
            if (characterId <= 0) return@withContext null
            coroutineScope {
                val rootDef = async { p1("/characters/$characterId") }
                val castsDef = async { p1("/characters/$characterId/casts", mapOf("limit" to "40")) }
                val root = rootDef.await() ?: return@coroutineScope null
                val casts = castsDef.await()?.optJSONArray("data").objects()
                val info = parseP1Infobox(root.optJSONArray("infobox"))

                val actors = casts.flatMap { it.optJSONArray("casts").objects() }.mapNotNull { cast ->
                    val person = cast.optJSONObject("person") ?: return@mapNotNull null
                    val id = person.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                    KitsugiVoiceActor(
                        id = id,
                        name = person.displayName(),
                        language = castLanguage(cast.optInt("relation", 0)),
                        imageUrl = person.optJSONObject("images").image("medium", "large", "grid", "small"),
                        source = SOURCE
                    )
                }.distinctBy { it.id }

                val appearances = casts.mapNotNull { item ->
                    val subject = item.optJSONObject("subject") ?: return@mapNotNull null
                    val type = subject.optInt("type", 2)
                    if (type != BangumiApiClient.SubjectType.ANIME && type != BangumiApiClient.SubjectType.BOOK &&
                        type != BangumiApiClient.SubjectType.REAL
                    ) return@mapNotNull null
                    val stable = stableIdOf(subject.optInt("id", 0)) ?: return@mapNotNull null
                    KitsugiCharacterMediaAppearance(
                        mediaId = stable,
                        title = subject.displayName(),
                        imageUrl = subject.optJSONObject("images").image("common", "medium", "large", "grid"),
                        mediaType = if (type == BangumiApiClient.SubjectType.BOOK) "MANGA" else "ANIME",
                        characterRole = characterRole(item.optInt("type", 2)).toTurkishCharacterRole(),
                        source = SOURCE,
                        titleJapanese = subject.strOrNull("name")
                    )
                }.distinctBy { it.mediaId }

                val nativeName = root.strOrNull("name")
                val cn = root.strOrNull("nameCN")
                val alternatives = (info["别名"].orEmpty() + info["简体中文名"].orEmpty() + listOfNotNull(cn))
                    .filter { it.isNotBlank() && !it.equals(nativeName, ignoreCase = true) }
                    .distinct()
                KitsugiCharacterDetail(
                    id = characterId,
                    name = cn ?: nativeName ?: "Bangumi #$characterId",
                    nativeName = nativeName,
                    alternativeNames = alternatives,
                    imageUrl = root.optJSONObject("images").image("large", "medium", "grid", "small"),
                    gender = info["性别"]?.firstOrNull()?.let { genderText(it) },
                    age = info["年龄"]?.firstOrNull(),
                    birthday = info["生日"]?.firstOrNull(),
                    bloodType = info["血型"]?.firstOrNull(),
                    biography = root.strOrNull("summary")?.let { cleanMarkup(it) },
                    voiceActors = actors,
                    mediaAppearances = appearances,
                    source = SOURCE
                )
            }
        }

    suspend fun fetchStaffDetail(personId: Int): KitsugiStaffDetail? =
        withContext(Dispatchers.IO) {
            if (personId <= 0) return@withContext null
            coroutineScope {
                val rootDef = async { p1("/persons/$personId") }
                val worksDef = async { p1("/persons/$personId/works", mapOf("limit" to "60")) }
                val castsDef = async { p1("/persons/$personId/casts", mapOf("limit" to "60")) }
                val root = rootDef.await() ?: return@coroutineScope null
                val info = parseP1Infobox(root.optJSONArray("infobox"))

                val works = worksDef.await()?.optJSONArray("data").objects().mapNotNull { item ->
                    val subject = item.optJSONObject("subject") ?: return@mapNotNull null
                    val type = subject.optInt("type", 2)
                    if (type != BangumiApiClient.SubjectType.ANIME && type != BangumiApiClient.SubjectType.BOOK &&
                        type != BangumiApiClient.SubjectType.REAL
                    ) return@mapNotNull null
                    val stable = stableIdOf(subject.optInt("id", 0)) ?: return@mapNotNull null
                    val role = item.optJSONArray("positions").objects()
                        .mapNotNull { pos -> pos.optJSONObject("type")?.let { it.strOrNull("en") ?: it.strOrNull("cn") } }
                        .distinct().take(3).joinToString(", ").ifBlank { "Staff" }
                    KitsugiStaffMediaWork(
                        mediaId = stable,
                        mediaTitle = subject.displayName(),
                        mediaImageUrl = subject.optJSONObject("images").image("common", "medium", "large", "grid"),
                        mediaType = if (type == BangumiApiClient.SubjectType.BOOK) "MANGA" else "ANIME",
                        staffRole = role.toTurkishStaffRole(),
                        source = SOURCE,
                        titleJapanese = subject.strOrNull("name")
                    )
                }.distinctBy { it.mediaId }

                val characterRoles = castsDef.await()?.optJSONArray("data").objects().flatMap { item ->
                    val character = item.optJSONObject("character") ?: return@flatMap emptyList()
                    val characterId = character.optInt("id", 0).takeIf { it > 0 } ?: return@flatMap emptyList()
                    item.optJSONArray("relations").objects().mapNotNull { relation ->
                        val subject = relation.optJSONObject("subject") ?: return@mapNotNull null
                        val type = subject.optInt("type", 2)
                        if (type != BangumiApiClient.SubjectType.ANIME && type != BangumiApiClient.SubjectType.BOOK &&
                            type != BangumiApiClient.SubjectType.REAL
                        ) return@mapNotNull null
                        val stable = stableIdOf(subject.optInt("id", 0)) ?: return@mapNotNull null
                        KitsugiStaffCharacterRole(
                            characterId = characterId,
                            characterName = character.displayName(),
                            characterImageUrl = character.optJSONObject("images").image("medium", "large", "grid", "small"),
                            characterSource = SOURCE,
                            mediaId = stable,
                            mediaTitle = subject.displayName(),
                            mediaImageUrl = subject.optJSONObject("images").image("common", "medium", "large", "grid"),
                            mediaType = if (type == BangumiApiClient.SubjectType.BOOK) "MANGA" else "ANIME",
                            characterRole = characterRole(relation.optInt("type", 2)).toTurkishCharacterRole(),
                            mediaSource = SOURCE
                        )
                    }
                }.distinctBy { "${it.characterId}:${it.mediaId}" }

                val nativeName = root.strOrNull("name")
                val cn = root.strOrNull("nameCN")
                val alternatives = (info["别名"].orEmpty() + info["简体中文名"].orEmpty() + listOfNotNull(cn))
                    .filter { it.isNotBlank() && !it.equals(nativeName, ignoreCase = true) }
                    .distinct()
                val careers = root.optJSONArray("career")?.let { arr ->
                    (0 until arr.length()).mapNotNull { careerLabel(arr.optString(it)) }
                }.orEmpty()
                KitsugiStaffDetail(
                    id = personId,
                    name = cn ?: nativeName ?: "Bangumi #$personId",
                    nativeName = nativeName,
                    alternativeNames = alternatives,
                    imageUrl = root.optJSONObject("images").image("large", "medium", "grid", "small"),
                    biography = root.strOrNull("summary")?.let { cleanMarkup(it) },
                    occupation = careers.joinToString(", ").takeIf { it.isNotBlank() },
                    birthday = info["生日"]?.firstOrNull(),
                    age = info["年龄"]?.firstOrNull(),
                    gender = info["性别"]?.firstOrNull()?.let { genderText(it) },
                    homeTown = info["出生地"]?.firstOrNull(),
                    characterRoles = characterRoles,
                    mediaWorks = works
                )
            }
        }

    /** p1 infobox: `[{key, values:[{k?, v}]}]`. */
    internal fun parseP1Infobox(array: JSONArray?): Map<String, List<String>> {
        val result = linkedMapOf<String, List<String>>()
        for (entry in array.objects()) {
            val key = entry.strOrNull("key") ?: continue
            val raw = entry.opt("values")
            val values = when (raw) {
                is JSONArray -> (0 until raw.length()).mapNotNull { index ->
                    val item = raw.optJSONObject(index)
                    (if (item != null) item.strOrNull("v") else raw.optString(index).trim().takeIf { it.isNotEmpty() })
                }
                is String -> listOf(raw.trim())
                else -> emptyList()
            }.filter { it.isNotBlank() }
            if (values.isNotEmpty()) result[key] = values
        }
        return result
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Metin / tarih / eşleme yardımcıları
    // ═════════════════════════════════════════════════════════════════════════

    internal fun characterRole(type: Int): String = when (type) {
        1 -> "Main"
        2 -> "Supporting"
        else -> "Background"
    }

    /** `CharacterCastType`: 0=CV 1=Dub 2=Actor 3=Çince dublaj 4=Japonca dublaj 5=İngilizce 6=Korece. */
    internal fun castLanguage(relation: Int): String = when (relation) {
        0, 4 -> "Japonca"
        3 -> "Çince"
        5 -> "İngilizce"
        6 -> "Korece"
        2 -> "Oyuncu"
        else -> "Dublaj"
    }

    private val relationMap = mapOf(
        "改编" to "Adaptation",
        "前传" to "Prequel",
        "续集" to "Sequel",
        "总集篇" to "Summary",
        "全集" to "Full story",
        "番外篇" to "Side story",
        "角色出演" to "Character",
        "相同世界观" to "Alternative setting",
        "不同世界观" to "Alternative setting",
        "不同演绎" to "Alternative version",
        "衍生" to "Spin-off",
        "主线故事" to "Parent story",
        "联动" to "Other",
        "其他" to "Other"
    )

    internal fun relationLabel(cn: String?, en: String?): String {
        val english = cn?.let { relationMap[it] }
            ?: en?.takeIf { it.isNotBlank() }
            ?: cn
            ?: "Relation"
        return english.toTurkishRelationType()
    }

    private fun careerLabel(career: String): String? = when (career.lowercase(Locale.ROOT)) {
        "seiyu" -> "Seslendirme Sanatçısı"
        "mangaka" -> "Mangaka"
        "producer" -> "Yapımcı"
        "artist" -> "Sanatçı"
        "illustrator" -> "İllüstratör"
        "writer" -> "Yazar"
        "actor" -> "Oyuncu"
        "director" -> "Yönetmen"
        "composer" -> "Besteci"
        "" -> null
        else -> career.replaceFirstChar { it.uppercase() }
    }

    private fun genderText(raw: String): String = when {
        raw.contains("女") || raw.equals("female", true) -> "Kadın"
        raw.contains("男") || raw.equals("male", true) -> "Erkek"
        else -> raw
    }

    internal fun formatOf(platform: String?): String? = when {
        platform.isNullOrBlank() -> null
        platform.equals("TV", true) -> "TV"
        platform.equals("OVA", true) -> "OVA"
        platform.contains("剧场版") -> "Movie"
        platform.equals("WEB", true) -> "ONA"
        platform.contains("漫画") -> "Manga"
        platform.contains("小说") -> "Novel"
        else -> platform
    }

    internal fun weekdayEnglish(cn: String): String? = when {
        cn.contains("一") -> "Mondays"
        cn.contains("二") -> "Tuesdays"
        cn.contains("三") -> "Wednesdays"
        cn.contains("四") -> "Thursdays"
        cn.contains("五") -> "Fridays"
        cn.contains("六") -> "Saturdays"
        cn.contains("日") || cn.contains("天") -> "Sundays"
        else -> null
    }

    internal fun durationText(raw: String, isMovie: Boolean): String? {
        val hms = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?").find(raw)
        val minutes: Int? = if (hms != null) {
            hms.groupValues[1].toInt() * 60 + hms.groupValues[2].toInt()
        } else {
            val labelled = Regex("(\\d{1,3})\\s*(?:分|min|m)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            labelled ?: Regex("\\d{1,3}").find(raw)?.value?.toIntOrNull()
        }
        if (minutes == null || minutes <= 0) return null
        return (if (isMovie) "$minutes min" else "$minutes min per ep").toTurkishDuration()
    }

    /** "2008年10月2日" / "2008-10-02" / "2008-10" / "2008" → ISO metin. */
    internal fun isoDate(text: String?): String? {
        if (text.isNullOrBlank()) return null
        Regex("(\\d{4})\\D{1,3}(\\d{1,2})\\D{1,3}(\\d{1,2})").find(text)?.let {
            return "%04d-%02d-%02d".format(Locale.ROOT, it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("(\\d{4})\\D{1,3}(\\d{1,2})").find(text)?.let {
            return "%04d-%02d".format(Locale.ROOT, it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        return Regex("(\\d{4})").find(text)?.groupValues?.get(1)
    }

    private fun isoToMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val parts = iso.split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, (month - 1).coerceIn(0, 11), day.coerceIn(1, 31))
        return calendar.timeInMillis
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    internal fun statusOf(subject: BangumiSubject, startIso: String?, endIso: String?, mediaType: MediaType): String? {
        val now = System.currentTimeMillis()
        val startMs = isoToMillis(startIso)
        if (startMs == null) return if (mediaType == MediaType.Manga) null else "Not yet aired"
        if (startMs > now) return "Not yet aired"
        val endMs = isoToMillis(endIso)
        if (mediaType == MediaType.Manga) {
            return when {
                endMs == null -> null
                endMs <= now -> "Finished"
                else -> "Publishing"
            }
        }
        if (endMs != null) return if (endMs <= now) "Finished Airing" else "Currently Airing"
        val platform = subject.platform.orEmpty()
        if (platform.contains("剧场版") || platform.equals("OVA", true)) return "Finished Airing"
        val episodes = subject.totalEpisodes.takeIf { it > 0 } ?: subject.eps
        return if (episodes > 0) {
            // Haftalık yayın varsayımı + 2 hafta tolerans: tahmini bitiş geçtiyse tamamlandı say.
            if (startMs + (episodes + 2L) * 7L * DAY_MS < now) "Finished Airing" else "Currently Airing"
        } else {
            if (now - startMs > 400L * DAY_MS) "Finished Airing" else "Currently Airing"
        }
    }

    internal fun seasonOf(startIso: String?, mediaType: MediaType): String? {
        if (mediaType == MediaType.Manga) return null
        val parts = startIso?.split('-') ?: return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val season = when (month) {
            in 1..3 -> "Winter"
            in 4..6 -> "Spring"
            in 7..9 -> "Summer"
            else -> "Fall"
        }
        return "$season $year"
    }

    internal fun isMostlyLatin(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.count { it.code < 0x250 }.toDouble() / letters.length >= 0.9
    }

    /** "A、B / C×D" gibi serbest metni şirket adlarına böler (etiket öneklerini atar). */
    internal fun splitCompanies(values: List<String>): List<String> =
        values.flatMap { it.split(Regex("[、,，/／×;；\\n]")) }
            .map { token -> token.substringAfter('：').substringAfter(':').trim() }
            .map { it.trim('(', ')', '（', '）', ' ') }
            .filter { it.isNotEmpty() && it.length <= 40 }
            .distinct()
            .take(8)

    /** Kanal adları boşlukla ayrılmış gelebilir ("MBS RKB CBC BS-i/BS-TBS"). */
    internal fun splitNetworks(values: List<String>): List<String> =
        values.flatMap { it.split(Regex("[、,，/／\\s]+")) }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 24 }
            .distinct()
            .take(12)

    /** BBCode/HTML kalıntılarını ve Bangumi ifadelerini (`(bgm38)`) temizler. */
    internal fun cleanMarkup(text: String): String =
        text.replace("\r\n", "\n")
            .replace(Regex("\\[/?[a-zA-Z*]+(?:=[^\\]]*)?]"), "")
            .replace(Regex("\\(bgm\\d+\\)"), "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private fun epochDate(seconds: Long): String? {
        if (seconds <= 0L) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(seconds * 1000L))
        }.getOrNull()
    }
}
