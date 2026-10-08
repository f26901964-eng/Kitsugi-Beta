package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiSubject
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
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
            val page = BangumiApiClient.searchSubjects(
                keyword = query,
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.ANIME),
                sort = BangumiApiClient.SearchSort.MATCH,
                nsfw = if (includeAdult) "include" else null,
                limit = limit,
                offset = offset
            )
            page.data.filter { includeAdult || !it.nsfw }.map { it.toSearchResult(MediaType.Anime) }
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
            val page = BangumiApiClient.searchSubjects(
                keyword = query,
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.BOOK),
                sort = BangumiApiClient.SearchSort.MATCH,
                nsfw = if (includeAdult) "include" else null,
                limit = limit,
                offset = offset
            )
            page.data
                .filter { includeAdult || !it.nsfw }
                .filter { !comicsOnly || it.platform == null || it.platform == "漫画" }
                .map { it.toSearchResult(MediaType.Manga) }
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
        limit: Int = 24,
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
        limit: Int = 24,
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
        runCatching {
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

            val offset = ((page - 1).coerceAtLeast(0)) * limit
            val effectiveQuery = query.ifBlank { "" }

            // Boş sorguda arama ucu güvenilmez; göz atma ucuna düş.
            val pageResult = if (effectiveQuery.isBlank()) {
                BangumiApiClient.browseSubjects(
                    type = types.first(),
                    token = tokenOrNull(context),
                    sort = if (sort == BangumiApiClient.SearchSort.RANK) "rank" else "date",
                    year = yearFrom ?: yearTo,
                    limit = limit,
                    offset = offset
                )
            } else {
                BangumiApiClient.searchSubjects(
                    keyword = effectiveQuery,
                    token = tokenOrNull(context),
                    types = types,
                    sort = sort,
                    tags = tags,
                    airDate = airDate,
                    rating = rating,
                    nsfw = if (includeAdult) "include" else null,
                    limit = limit,
                    offset = offset
                )
            }
            pageResult.data
                .filter { includeAdult || !it.nsfw }
                .map { it.toSearchResult(mediaType) }
        }.getOrElse { error ->
            Log.e(TAG, "Bangumi searchMediaAdvanced failed: ${error.message}", error)
            emptyList()
        }
    }

    // ── Keşfet kategorileri ──────────────────────────────────────────────────

    /** "En İyi Animeler" — `sort=rank` ile kategori sıralaması (排行榜). */
    suspend fun topAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.topRanked(BangumiApiClient.SubjectType.ANIME, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Anime) }
        }.getOrElse { logAndEmpty("topAnime", it) }

    /** "Popüler Animeler" — koleksiyon sayısı (收藏人数) sıralaması. */
    suspend fun trendingAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.mostCollected(BangumiApiClient.SubjectType.ANIME, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Anime) }
        }.getOrElse { logAndEmpty("trendingAnime", it) }

    /** "En Yüksek Puanlı Animeler" — `sort=score` araması. */
    suspend fun topRatedAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.searchSubjects(
                keyword = "",
                token = tokenOrNull(context),
                types = listOf(BangumiApiClient.SubjectType.ANIME),
                sort = BangumiApiClient.SearchSort.SCORE,
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Anime) }
                .ifEmpty { topAnime(limit, context) }
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
            .data.map { it.toSearchResult(MediaType.Anime) }
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
            ).data.map { it.toSearchResult(MediaType.Movie) }
        }.getOrElse { logAndEmpty("movieAnime", it) }

    /** "Yeni Eklenen Animeler" — yayın tarihine göre en yeni条目'lar. */
    suspend fun newlyAddedAnime(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.browseSubjects(
                type = BangumiApiClient.SubjectType.ANIME,
                token = tokenOrNull(context),
                sort = "date",
                limit = limit,
                offset = offset
            ).data.map { it.toSearchResult(MediaType.Anime) }
        }.getOrElse { logAndEmpty("newlyAddedAnime", it) }

    /** "En İyi Mangalar" — 书籍 kategori sıralaması. */
    suspend fun topManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.topRanked(BangumiApiClient.SubjectType.BOOK, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Manga) }
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
            ).data.map { it.toSearchResult(MediaType.Manga) }
        }.getOrElse { logAndEmpty("publishingManga", it) }

    /** "Popüler Mangalar" — koleksiyon sayısı sıralaması. */
    suspend fun trendingManga(limit: Int = 20, context: Context? = null, offset: Int = 0): List<JikanSearchResult> =
        runCatching {
            BangumiApiClient.mostCollected(BangumiApiClient.SubjectType.BOOK, tokenOrNull(context), limit, offset)
                .data.map { it.toSearchResult(MediaType.Manga) }
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
            ).data.map { it.toSearchResult(MediaType.Manga) }
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
        return JikanSearchResult(
            malId = BangumiIdNamespace.stableIdFromRaw(id) ?: id,
            title = displayTitle,
            subtitle = (platform ?: subjectTypeLabel(type)).uppercase(),
            type = mediaType,
            total = eps.takeIf { it > 0 } ?: totalEpisodes.takeIf { it > 0 },
            score = if (ratingScore > 0) kotlin.math.round(ratingScore).toInt().coerceIn(0, 10) else null,
            isAdult = nsfw,
            imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
            year = date?.take(4)?.toIntOrNull(),
            source = SOURCE,
            titleEnglish = null,
            titleJapanese = name.takeIf { it.isNotBlank() && it != nameCn },
            backdropUrl = BangumiApiClient.absoluteImageUrl(images?.backdrop),
            rank = rating?.rank?.takeIf { it > 0 },
            members = rating?.total?.takeIf { it > 0 },
            favorites = collectionTotal.takeIf { it > 0 },
            rawScoreDouble = ratingScore.takeIf { it > 0 },
            genres = (tags.ifEmpty { metaTags }).take(12)
        )
    }

    /** Koleksiyon satırındaki kısaltılmış条目 → [JikanSearchResult] (içe aktarma önizlemesi). */
    fun BangumiApiClient.BangumiSlimSubject.toSearchResult(): JikanSearchResult = JikanSearchResult(
        malId = BangumiIdNamespace.stableIdFromRaw(id) ?: id,
        title = displayTitle,
        subtitle = subjectTypeLabel(type).uppercase(),
        type = BangumiIdNamespace.mediaTypeFor(type),
        total = eps.takeIf { it > 0 },
        score = if (score > 0) kotlin.math.round(score).toInt().coerceIn(0, 10) else null,
        isAdult = false,
        imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
        year = date?.take(4)?.toIntOrNull(),
        source = SOURCE,
        titleJapanese = name.takeIf { it.isNotBlank() && it != nameCn },
        rank = rank.takeIf { it > 0 },
        favorites = collectionTotal.takeIf { it > 0 },
        rawScoreDouble = score.takeIf { it > 0 },
        genres = tags.take(12)
    )

    /** Karakter/kişi arama sonucu → [JikanSearchResult] (kimlik + ad + görsel yeterli). */
    private fun BangumiApiClient.BangumiEntity.toSearchResult(subtitle: String): JikanSearchResult = JikanSearchResult(
        malId = id,
        title = displayTitle,
        subtitle = subtitle,
        type = MediaType.Anime,
        total = null,
        score = null,
        isAdult = false,
        imageUrl = BangumiApiClient.absoluteImageUrl(images?.poster),
        year = null,
        source = SOURCE
    )

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
