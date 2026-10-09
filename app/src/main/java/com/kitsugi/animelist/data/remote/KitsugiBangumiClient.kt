package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiSubject
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Bangumi (bgm.tv) keşfet / arama / detay bağdaştırıcısı.
 *
 * Bu sınıf, [BangumiApiClient]'in Bangumi'ye özgü modellerini Kitsugi'nin kaynaklar arası
 * ortak modeli olan [JikanSearchResult]'a çevirir; böylece Keşfet (Explore), Arama ve
 * "Tümünü Gör" ızgaraları Bangumi'yi diğer 6 kaynakla aynı şekilde tüketebilir.
 *
 * Kimlik sözleşmesi: `JikanSearchResult.malId` alanında **Bangumi stableId** taşınır
 * (`subject_id + 500_000_000`, bkz. [BangumiIdNamespace]) ve `source = "bangumi"` olur.
 * Bu, Kitsu (300M) ve Shikimori (400M) ile birebir aynı desendir; aynı sayısal ID'ye sahip
 * bir MAL anime'si ile bir Bangumi条目'su asla aynı içerik sayılmaz.
 */
object KitsugiBangumiClient {

    init {
        // Keşfet/arama listeleri Latin adları önbellekten okur; ilk temasta diski UI
        // thread'inde okumamak için bellek aynası şimdiden doldurulmaya başlanır.
        BangumiTitleCache.warmUp()
    }

    private const val TAG = "KitsugiBangumiClient"

    const val SOURCE = BangumiIdNamespace.SOURCE

    /** Bangumi marka rengi (#F09199 — Aniyomi'nin `getLogoColor()` değeriyle aynı). */
    const val BRAND_COLOR_ARGB = 0xFFF09199

    // ── Oturum ───────────────────────────────────────────────────────────────

    /**
     * Keşfet/arama **oturum gerektirmez**; yalnızca NSFW içerik ve özel koleksiyonlar için
     * token gerekir. Token yoksa null döner ve istekler anonim yapılır.
     */
    private suspend fun tokenOrNull(context: Context?): String? {
        if (context == null) return null
        return runCatching {
            if (BangumiAuthStore.isConnected(context)) BangumiAuthStore.getValidToken(context) else null
        }.getOrNull()
    }

    // ── Arama (Arama sekmesi) ────────────────────────────────────────────────

    /**
     * Bangumi'de动画 arar.
     *
     * `sort = match` (eşleşme kalitesi) varsayılandır; "popülerlik" için
     * [BangumiApiClient.SearchSort.HEAT] verilebilir.
     */
    suspend fun searchAnime(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        context: Context? = null,
        includeAdult: Boolean = false
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            searchWithFallback(
                keyword = query,
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.ANIME),
                sort = BangumiApiClient.SearchSort.MATCH,
                includeAdult = includeAdult,
                limit = limit,
                offset = offset
            ).filter { includeAdult || !it.nsfw }.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
        }.getOrElse { error ->
            Log.e(TAG, "Bangumi searchAnime failed: ${error.message}", error)
            emptyList()
        }
    }

    /**
     * Bangumi'de书籍 (manga/roman/画集) arar.
     * Aniyomi/Mihon deseniyle aynı: yalnızca `platform == "漫画"` olanlar manga sayılır,
     * böylece roman ve画集条目'ları manga listesini kirletmez.
     */
    suspend fun searchManga(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        context: Context? = null,
        includeAdult: Boolean = false,
        comicsOnly: Boolean = true
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            searchWithFallback(
                keyword = query,
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.BOOK),
                sort = BangumiApiClient.SearchSort.MATCH,
                includeAdult = includeAdult,
                limit = limit,
                offset = offset
            )
                .filter { includeAdult || !it.nsfw }
                .filter { !comicsOnly || it.platform == null || it.platform == "漫画" }
                .map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { error ->
            Log.e(TAG, "Bangumi searchManga failed: ${error.message}", error)
            emptyList()
        }
    }

    /**
     * Karakter (角色) araması — `POST /v0/search/characters` (deneysel uç).
     * Shikimori/Kitsu karakter aramalarıyla aynı çıktı biçimi: [JikanSearchResult] içinde
     * `subtitle` "Karakter (Bangumi)" olarak işaretlenir.
     */
    suspend fun searchCharacters(
        query: String,
        page: Int = 1,
        limit: Int = BangumiApiClient.SEARCH_PAGE_SIZE,
        context: Context? = null,
        includeAdult: Boolean = false
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            BangumiApiClient.searchCharacters(
                keyword = query.trim(),
                token = tokenOrNull(context),
                nsfw = if (includeAdult) null else false,
                limit = limit,
                offset = (page - 1).coerceAtLeast(0) * limit
            ).data.map { it.toSearchResult("Karakter (Bangumi)") }
        }.getOrElse { logAndEmpty("searchCharacters", it) }
    }

    /** Kişi/seslendirmen (人物) araması — `POST /v0/search/persons` (deneysel uç). */
    suspend fun searchPeople(
        query: String,
        page: Int = 1,
        limit: Int = BangumiApiClient.SEARCH_PAGE_SIZE,
        context: Context? = null,
        career: List<String> = emptyList()
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            BangumiApiClient.searchPersons(
                keyword = query.trim(),
                token = tokenOrNull(context),
                career = career,
                limit = limit,
                offset = (page - 1).coerceAtLeast(0) * limit
            ).data.map {
                val careerLabel = it.career.firstOrNull()?.let { c -> " · $c" } ?: ""
                it.toSearchResult("Kişi (Bangumi)$careerLabel")
            }
        }.getOrElse { logAndEmpty("searchPeople", it) }
    }
    /**
     * "Tümünü Gör" ızgarası için sayfalı gelişmiş arama.
     *
     * @param page 1 tabanlı sayfa numarası (Bangumi `offset` kullanır → `(page-1)*limit`)
     */
    suspend fun searchMediaAdvanced(
        mediaType: MediaType,
        query: String = "",
        page: Int = 1,
        limit: Int = 24,
        sort: String = BangumiApiClient.SearchSort.MATCH,
        tags: List<String> = emptyList(),
        yearFrom: Int? = null,
        yearTo: Int? = null,
        minScore: Int? = null,
        context: Context? = null,
        includeAdult: Boolean = false
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        try {
            val types = when (mediaType) {
                MediaType.Manga -> listOf(BangumiApiClient.SubjectType.BOOK)
                MediaType.Movie -> listOf(BangumiApiClient.SubjectType.ANIME, BangumiApiClient.SubjectType.REAL)
                MediaType.TvShow -> listOf(BangumiApiClient.SubjectType.REAL)
                MediaType.Anime -> listOf(BangumiApiClient.SubjectType.ANIME)
            }
            val airDate = mutableListOf<String>()
            yearFrom?.let { airDate += ">=$it-01-01" }
            yearTo?.let { airDate += "<=${it}-12-31" }
            val rating = minScore?.takeIf { it > 0 }?.let { listOf(">=$it") } ?: emptyList()

            val effectiveQuery = query.trim()
            // Arama ucu sayfa başına en fazla 20 kayıt verir; offset de AYNI boyuta göre
            // hesaplanmazsa her sayfada kayıt atlanır. Göz atma ucu ise 50'ye kadar destekler.
            val pageSize = if (effectiveQuery.isBlank()) limit else limit.coerceIn(1, BangumiApiClient.SEARCH_PAGE_SIZE)
            val offset = ((page - 1).coerceAtLeast(0)) * pageSize

            // Boş sorguda arama ucu güvenilmez; göz atma ucuna düş.
            val subjects = if (effectiveQuery.isBlank()) {
                BangumiApiClient.browseSubjects(
                    type = types.first(),
                    token = tokenOrNull(context),
                    sort = if (sort == BangumiApiClient.SearchSort.RANK) "rank" else "date",
                    year = yearFrom ?: yearTo,
                    limit = pageSize,
                    offset = offset
                ).data
            } else {
                searchWithFallback(
                    keyword = effectiveQuery,
                    token = tokenOrNull(context),
                    types = types,
                    sort = sort,
                    tags = tags,
                    airDate = airDate,
                    rating = rating,
                    includeAdult = includeAdult,
                    limit = pageSize,
                    offset = offset
                )
            }
            subjects
                .filter { includeAdult || !it.nsfw }
                .map { it.toSearchResult(mediaType) }.withLatinBangumiNames(context)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            // Hata artık sessizce "Sonuç bulunamadı"ya çevrilmiyor: arama ekranı gerçek hata
            // mesajını gösterir (ör. "Bangumi HTTP 400 ..."), böylece teşhis mümkün olur.
            Log.e(TAG, "Bangumi searchMediaAdvanced failed: ${error.message}", error)
            throw error
        }
    }

    /**
     * Anahtar kelime araması: önce `POST /v0/search/subjects`, hata verirse ya da boş dönerse
     * (ve süzgeç yoksa) eski `GET /search/subject/{q}` ucu denenir.
     *
     * NSFW: sunucu `filter.nsfw` alanını JSON boolean olarak bekler. Yetişkin içerik kapalıyken
     * `false` gönderilir (sayfa boyutu sunucuda süzüldüğü için sayfalama bozulmaz); açıkken
     * filtre hiç gönderilmez (R18, yalnızca yetkili hesaplara döner).
     *
     * İki uç da hata verirse ilk (v0) hata fırlatılır; ikisi de boşsa boş liste döner.
     */
    private suspend fun searchWithFallback(
        keyword: String,
        token: String?,
        types: List<Int>,
        sort: String,
        includeAdult: Boolean,
        limit: Int,
        offset: Int,
        tags: List<String> = emptyList(),
        airDate: List<String> = emptyList(),
        rating: List<String> = emptyList()
    ): List<BangumiSubject> {
        val primary = runCatching {
            BangumiApiClient.searchSubjects(
                keyword = keyword,
                token = token,
                types = types,
                sort = sort,
                tags = tags,
                airDate = airDate,
                rating = rating,
                nsfw = if (includeAdult) null else false,
                limit = limit,
                offset = offset
            )
        }
        primary.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        val primaryData = primary.getOrNull()?.data.orEmpty()
        if (primaryData.isNotEmpty()) return primaryData

        // Eski uç tag/tarih/puan süzgeci desteklemez; süzgeçli aramada yedeğe düşme.
        val plainQuery = tags.isEmpty() && airDate.isEmpty() && rating.isEmpty()
        if (!plainQuery || keyword.isBlank()) {
            primary.exceptionOrNull()?.let { throw it }
            return emptyList()
        }

        val legacyTypes: List<Int?> = if (types.isEmpty()) listOf<Int?>(null) else types
        val legacy = runCatching {
            val merged = LinkedHashMap<Int, BangumiSubject>()
            for (type in legacyTypes) {
                BangumiApiClient.searchSubjectsLegacy(
                    keyword = keyword,
                    token = token,
                    type = type,
                    limit = limit,
                    offset = offset
                ).data.forEach { merged.putIfAbsent(it.id, it) }
            }
            merged.values.toList()
        }
        legacy.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        val legacyData = legacy.getOrNull().orEmpty()
        if (legacyData.isNotEmpty()) {
            Log.w(TAG, "Bangumi v0 arama sonuç vermedi (${primary.exceptionOrNull()?.message}); legacy uç ${legacyData.size} sonuç döndürdü")
            return legacyData
        }
        primary.exceptionOrNull()?.let { throw it }
        legacy.exceptionOrNull()?.let { throw it }
        return emptyList()
    }

    /**
     * Bangumi liste/arama uçları (`/v0/search`, `/v0/subjects` vb.) infobox döndürmez; İngilizce
     * ve romaji ad yalnızca subject detayında bulunur. Başlığı hâlâ CJK (Japonca/Çince/Korece)
     * olan satırlar için subject bir kez çekilir, Latin adlar [BangumiTitleCache]'e yazılır ve
     * satır o adlarla güncellenir. Böylece liste kartları da uygulama dil tercihine uyar.
     * Latin ad bulunamazsa satır olduğu gibi kalır (son çare orijinal ad).
     */
    internal suspend fun List<JikanSearchResult>.withLatinBangumiNames(context: Context? = null): List<JikanSearchResult> {
        if (none { it.source == SOURCE && PreferenceHelpers.hasCjkCharacters(it.title) }) return this
        val limiter = Semaphore(4)
        return coroutineScope {
            map { item ->
                async {
                    if (item.source != SOURCE || !PreferenceHelpers.hasCjkCharacters(item.title)) return@async item
                    val rawId = BangumiIdNamespace.rawIdFromStable(item.malId) ?: item.malId
                    if (rawId <= 0) return@async item
                    val subject = limiter.withPermit {
                        runCatching { BangumiApiClient.getSubject(rawId, tokenOrNull(context)) }.getOrNull()
                    } ?: return@async item
                    val localized = BangumiNameLocalizer.subject(subject.name, subject.nameCn, subject.infobox)
                    BangumiTitleCache.put(rawId, localized.romaji, localized.english, localized.native)
                    val latinTitle = localized.romaji ?: localized.english
                    if (latinTitle == null) item else item.copy(
                        title = latinTitle,
                        titleEnglish = localized.english ?: item.titleEnglish,
                        titleRomaji = localized.romaji ?: item.titleRomaji
                    )
                }
            }.awaitAll()
        }
    }

    // ── Keşfet kategorileri ──────────────────────────────────────────────────

    /** "En İyi Animeler" — `sort=rank` ile kategori sıralaması (排行榜). */
    suspend fun topAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.topRanked(BangumiApiClient.SubjectType.ANIME, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("topAnime", it) }

    /** "Popüler Animeler" — koleksiyon sayısı (收藏人数) sıralaması. */
    suspend fun trendingAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.mostCollected(BangumiApiClient.SubjectType.ANIME, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("trendingAnime", it) }

    /** "En Yüksek Puanlı Animeler" — `sort=score` araması. */
    suspend fun topRatedAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            // Boş anahtar kelimeli arama sunucuda hata verebilir → rank sıralamasına düş.
            runCatching {
                BangumiApiClient.searchSubjects(
                    keyword = "",
                    token = tokenOrNull(context),
                    types = listOf(BangumiApiClient.SubjectType.ANIME),
                    sort = BangumiApiClient.SearchSort.SCORE,
                    nsfw = false,
                    limit = limit,
                    offset = offset
                ).data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
            }.getOrDefault(emptyList())
                .ifEmpty { topAnime(limit, context, offset) }
        }.getOrElse { logAndEmpty("topRatedAnime", it) }

    /**
     * "Bu Sezon" — içinde bulunulan ayın动画条目'ları, yayın tarihine göre.
     * Bangumi'de sezon "kış/ilkbahar/yaz/sonbahar" değil **yıl+ay** ile ifade edilir.
     */
    suspend fun seasonalAnime(limit: Int = 20, context: Context? = null): List<JikanSearchResult> {
        val calendar = Calendar.getInstance()
        return seasonFor(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            limit = limit,
            context = context
        )
    }

    /** Belirli yıl/ay için göz atma (`GET /v0/subjects?type=2&year=..&month=..&sort=date`). */
    suspend fun seasonFor(
        year: Int,
        month: Int? = null,
        limit: Int = 20,
        context: Context? = null,
        offset: Int = 0
    ): List<JikanSearchResult> = runCatching {
        BangumiApiClient.browseSeason(year, month, tokenOrNull(context), limit, offset)
            .data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
    }.getOrElse { logAndEmpty("seasonFor($year-$month)", it) }

    /** "Şu An Yayında" — bu ay + geçen ay birleşimi (Bangumi'de airing durumu yoktur). */
    suspend fun airingAnime(limit: Int = 20, context: Context? = null): List<JikanSearchResult> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val previous = if (month == 1) (year - 1) to 12 else year to (month - 1)
        val current = seasonFor(year, month, limit, context)
        if (current.size >= limit) return current.take(limit)
        val older = seasonFor(previous.first, previous.second, limit - current.size, context)
        return (current + older).distinctBy { it.malId }.take(limit)
    }

    /** "Yaklaşan" — gelecek ayın条目'ları. */
    suspend fun upcomingAnime(limit: Int = 20, context: Context? = null): List<JikanSearchResult> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val next = if (month == 12) (year + 1) to 1 else year to (month + 1)
        return seasonFor(next.first, next.second, limit, context)
    }

    /** "Anime Filmleri" — `cat=3` (Movie) alt kategorisi. */
    suspend fun movieAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.ANIME,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.ANIME_MOVIE,
                sort = "rank",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Movie) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("movieAnime", it) }

    /** Bangumi REAL / 三次元 TV-drama şeridi (`type=6&cat=6001`). */
    suspend fun realTvShows(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.REAL,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.REAL_TV,
                sort = "rank",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.TvShow) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("realTvShows", it) }

    /** Bangumi REAL film şeridi (`type=6&cat=6002`), anime filmlerinden ayrı tutulur. */
    suspend fun realMovies(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.REAL,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.REAL_MOVIE,
                sort = "rank",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Movie) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("realMovies", it) }

    /** "Yeni Eklenen Animeler" — yayın tarihine göre en yeni条目'lar. */
    suspend fun newlyAddedAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.ANIME,
                token = tokenOrNull(context),
                sort = "date",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Anime) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("newlyAddedAnime", it) }

    /** "En İyi Mangalar" — 书籍 kategori sıralaması. */
    suspend fun topManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.topRanked(BangumiApiClient.SubjectType.BOOK, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("topManga", it) }

    /** "Yayınlanan Mangalar" — en yeni书籍条目'ları. */
    suspend fun publishingManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.BOOK,
                token = tokenOrNull(context),
                cat = BangumiApiClient.Category.BOOK_COMIC,
                sort = "date",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("publishingManga", it) }

    /** "Popüler Mangalar" — koleksiyon sayısı sıralaması. */
    suspend fun trendingManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.mostCollected(BangumiApiClient.SubjectType.BOOK, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
        }.getOrElse { logAndEmpty("trendingManga", it) }

    /** "En Yüksek Puanlı Mangalar". */
    suspend fun topRatedManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.searchSubjects(
                keyword = "",
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.BOOK),
                sort = BangumiApiClient.SearchSort.SCORE,
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Manga) }.withLatinBangumiNames(context)
                .ifEmpty { topManga(limit, context) }
        }.getOrElse { logAndEmpty("topRatedManga", it) }

    /**
     * Keşfet "Tümü" modu ve tek kaynak modu için tüm şeritleri tek çağrıda toplar.
     * `ExploreViewModel.loadBangumiData()` bu fonksiyonu `ExplorePayload`'a çevirir.
     */
    suspend fun explorePayloadData(
        context: Context? = null,
        limit: Int = 20
    ): BangumiExploreData {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val next = if (month == 12) (year + 1) to 1 else year to (month + 1)
        return BangumiExploreData(
            topAnime = topAnime(limit, context),
            trendingAnime = trendingAnime(limit, context),
            topRatedAnime = topRatedAnime(limit, context),
            seasonalAnime = seasonFor(year, month, limit, context),
            airingAnime = airingAnime(limit, context),
            upcomingAnime = seasonFor(next.first, next.second, limit, context),
            movieAnime = movieAnime(limit, context),
            bangumiTvShows = realTvShows(limit, context),
            bangumiMovies = realMovies(limit, context),
            newlyAddedAnime = newlyAddedAnime(limit, context),
            topManga = topManga(limit, context),
            publishingManga = publishingManga(limit, context),
            trendingManga = trendingManga(limit, context),
            topRatedManga = topRatedManga(limit, context)
        )
    }

    /** [explorePayloadData] çıktısı; `ExplorePayload` alan adlarıyla birebir hizalıdır. */
    data class BangumiExploreData(
        val topAnime: List<JikanSearchResult> = emptyList(),
        val trendingAnime: List<JikanSearchResult> = emptyList(),
        val topRatedAnime: List<JikanSearchResult> = emptyList(),
        val seasonalAnime: List<JikanSearchResult> = emptyList(),
        val airingAnime: List<JikanSearchResult> = emptyList(),
        val upcomingAnime: List<JikanSearchResult> = emptyList(),
        val movieAnime: List<JikanSearchResult> = emptyList(),
        val bangumiTvShows: List<JikanSearchResult> = emptyList(),
        val bangumiMovies: List<JikanSearchResult> = emptyList(),
        val newlyAddedAnime: List<JikanSearchResult> = emptyList(),
        val topManga: List<JikanSearchResult> = emptyList(),
        val publishingManga: List<JikanSearchResult> = emptyList(),
        val trendingManga: List<JikanSearchResult> = emptyList(),
        val topRatedManga: List<JikanSearchResult> = emptyList()
    )

    // ── Detay ────────────────────────────────────────────────────────────────

    /**
     * Bangumi条目 detayı. `subjectIdOrStable` hem gerçek `subject_id` hem de
     * Kitsugi stableId (500M+) olabilir.
     */
    suspend fun getSubject(subjectIdOrStable: Int, context: Context? = null): BangumiSubject? =
        withContext(Dispatchers.IO) {
            val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
            if (subjectId <= 0) return@withContext null
            runCatching {
                BangumiApiClient.getSubject(subjectId, tokenOrNull(context))
            }.getOrElse { error ->
                Log.e(TAG, "getSubject($subjectId) failed: ${error.message}")
                null
            }
        }

    /** Bölüm listesi (ana hikâye). Oyuncu/ilerleme eşlemesi için Bangumi bölüm ID'leri gerekir. */
    suspend fun getEpisodes(
        subjectIdOrStable: Int,
        context: Context? = null
    ): List<BangumiApiClient.BangumiEpisode> = withContext(Dispatchers.IO) {
        val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
        if (subjectId <= 0) return@withContext emptyList()
        runCatching {
            BangumiApiClient.getAllMainEpisodes(subjectId, tokenOrNull(context))
        }.getOrElse { error ->
            Log.e(TAG, "getEpisodes($subjectId) failed: ${error.message}")
            emptyList()
        }
    }

    /** İlişkili条目'lar: devam sezonu, ön hikâye, kitap uyarlaması. */
    suspend fun getRelated(subjectIdOrStable: Int, context: Context? = null):
        List<BangumiApiClient.BangumiRelatedSubject> = withContext(Dispatchers.IO) {
        val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
        runCatching {
            BangumiApiClient.getRelatedSubjects(subjectId, tokenOrNull(context))
        }.getOrElse { emptyList() }
    }

    // ── Model dönüşümü ───────────────────────────────────────────────────────

    /**
     * Bangumi条目 → [JikanSearchResult].
     *
     * Alan eşlemeleri:
     *  - `malId`      → Bangumi **stableId** (`subject_id + 500_000_000`)
     *  - `title`      → `name_cn` varsa o, yoksa `name` (Aniyomi/Mihon ile aynı tercih)
     *  - `titleJapanese` → `name` (Bangumi'de条目 adı genelde Japonca orijinaldir)
     *  - `score`      → `rating.score` (0-10, yuvarlanmış)
     *  - `rawScoreDouble` → ham puan (0.1 hassasiyetli gösterim için)
     *  - `rank`       → kategori içi sıra
     *  - `members`    → puanlayan kişi sayısı
     *  - `favorites`  → koleksiyon toplamı (收藏人数)
     *  - `total`      → `eps` (kitaplarda 话数)
     *  - `isAdult`    → `nsfw`
     *  - `subtitle`   → platform (TV / 剧场版 / WEB / 漫画 ...)
     */
    fun BangumiSubject.toSearchResult(preferredType: MediaType? = null): JikanSearchResult {
        val mediaType = when {
            preferredType == MediaType.Movie && platform?.contains("剧场版") == true -> MediaType.Movie
            preferredType == MediaType.Movie && type == BangumiApiClient.SubjectType.REAL -> MediaType.Movie
            else -> BangumiIdNamespace.mediaTypeFor(type).let { resolved ->
                if (preferredType == MediaType.Movie && resolved == MediaType.TvShow) MediaType.Movie else resolved
            }
        }
        val ratingScore = rating?.score ?: 0.0
        val collectionTotal = collection?.total ?: 0
        val localizedTitle = BangumiNameLocalizer.subject(name, nameCn, infobox)
        // Keşfet/arama uçları infobox dönürmez → İngilizce/romaji ad bilgisi ancak kayıt daha
        // önce çözüldüyse vardır; kalıcı önbellek onu listeye de taşır.
        val latinOverride = BangumiTitleCache.latinTitleFor(id, localizedTitle.display)
        return JikanSearchResult(
            malId = BangumiIdNamespace.stableIdFromRaw(id) ?: id,
            title = latinOverride ?: localizedTitle.display,
            subtitle = displayPlatformLabel(platform, type, mediaType),
            type = mediaType,
            total = eps.takeIf { it > 0 } ?: totalEpisodes.takeIf { it > 0 },
            score = if (ratingScore > 0) kotlin.math.round(ratingScore).toInt().coerceIn(0, 10) else null,
            isAdult = nsfw,
            imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
            year = date?.take(4)?.toIntOrNull(),
            source = SOURCE,
            titleEnglish = localizedTitle.english ?: latinOverride,
            titleJapanese = localizedTitle.native,
            titleRomaji = localizedTitle.romaji ?: latinOverride,
            backdropUrl = BangumiApiClient.absoluteImageUrl(images?.backdrop),
            rank = rating?.rank?.takeIf { it > 0 },
            members = rating?.total?.takeIf { it > 0 },
            favorites = collectionTotal.takeIf { it > 0 },
            rawScoreDouble = ratingScore.takeIf { it > 0 },
            genres = (tags.ifEmpty { metaTags }).take(12)
        )
    }

    /** Koleksiyon satırındaki kısaltılmış条目 → [JikanSearchResult] (içe aktarma önizlemesi). */
    fun BangumiApiClient.BangumiSlimSubject.toSearchResult(): JikanSearchResult {
        val localizedTitle = BangumiNameLocalizer.entity(name, nameCn)
        val latinOverride = BangumiTitleCache.latinTitleFor(id, localizedTitle.display)
        return JikanSearchResult(
            malId = BangumiIdNamespace.stableIdFromRaw(id) ?: id,
            title = latinOverride ?: localizedTitle.display,
            subtitle = subjectTypeLabel(type).uppercase(),
            type = BangumiIdNamespace.mediaTypeFor(type),
            total = eps.takeIf { it > 0 },
            score = if (score > 0) kotlin.math.round(score).toInt().coerceIn(0, 10) else null,
            isAdult = false,
            imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
            year = date?.take(4)?.toIntOrNull(),
            source = SOURCE,
            titleEnglish = localizedTitle.english ?: latinOverride,
            titleJapanese = localizedTitle.native,
            titleRomaji = localizedTitle.romaji ?: latinOverride,
            rank = rank.takeIf { it > 0 },
            favorites = collectionTotal.takeIf { it > 0 },
            rawScoreDouble = score.takeIf { it > 0 },
            genres = tags.take(12)
        )
    }

    /** Karakter/kişi arama sonucu → [JikanSearchResult] (kimlik + ad + görsel yeterli). */
    private fun BangumiApiClient.BangumiEntity.toSearchResult(subtitle: String): JikanSearchResult {
        val localizedName = BangumiNameLocalizer.entity(name, nameCn)
        return JikanSearchResult(
            malId = id,
            title = localizedName.display,
            subtitle = subtitle,
            type = MediaType.Anime,
            total = null,
            score = null,
            isAdult = false,
            imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
            year = null,
            source = SOURCE,
            titleEnglish = localizedName.english,
            titleJapanese = localizedName.native,
            titleRomaji = localizedName.romaji
        )
    }

    private fun displayPlatformLabel(platform: String?, subjectType: Int, mediaType: MediaType): String {
        if (subjectType == BangumiApiClient.SubjectType.REAL) {
            return if (mediaType == MediaType.Movie) "FİLM" else "DİZİ"
        }
        val value = platform?.trim().orEmpty()
        if (value.isBlank()) return subjectTypeLabel(subjectType).uppercase()
        return when (value) {
            "剧场版", "電影", "电影", "映画" -> "FİLM"
            "电视剧", "電視劇", "连续剧", "連續劇" -> "DİZİ"
            "漫画", "漫畫" -> "MANGA"
            else -> value.takeUnless { PreferenceHelpers.hasCjkCharacters(it) }?.uppercase()
                ?: subjectTypeLabel(subjectType).uppercase()
        }
    }

    private fun subjectTypeLabel(type: Int): String = when (type) {
        BangumiApiClient.SubjectType.BOOK -> "Kitap"
        BangumiApiClient.SubjectType.ANIME -> "Anime"
        BangumiApiClient.SubjectType.MUSIC -> "Müzik"
        BangumiApiClient.SubjectType.GAME -> "Oyun"
        BangumiApiClient.SubjectType.REAL -> "Dizi"
        else -> "Diğer"
    }

    private fun logAndEmpty(label: String, error: Throwable): List<JikanSearchResult> {
        Log.e(TAG, "Bangumi $label failed: ${error.message}", error)
        return emptyList()
    }

    /** `https://bgm.tv/subject/{id}` — detay sayfasındaki "Kaynakta Aç" bağlantısı. */
    fun webUrlFor(subjectIdOrStable: Int): String {
        val subjectId = BangumiIdNamespace.rawIdFromStable(subjectIdOrStable) ?: subjectIdOrStable
        return "https://bgm.tv/subject/$subjectId"
    }
}
