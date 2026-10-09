package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** A source can fail without replacing the selected mode or hiding other sources. */
data class ExploreSourceState(
    val payload: ExplorePayload? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCached: Boolean = false
)

internal suspend fun loadExploreSource(
    cached: ExplorePayload?,
    forceRefresh: Boolean,
    fetch: suspend () -> ExplorePayload,
    readOffline: suspend () -> ExplorePayload?,
    timeoutMillis: Long = 60_000L
): ExploreSourceState {
    val usableCache = cached?.takeIf { it.hasCatalogContent() }
    if (!forceRefresh && usableCache != null) return ExploreSourceState(payload = usableCache)
    try {
        val payload = withTimeoutOrNull(timeoutMillis) { fetch() }
        currentCoroutineContext().ensureActive()
        if (payload != null && payload.hasCatalogContent()) return ExploreSourceState(payload = payload)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Fall back per source, not by selecting a different platform.
    }
    currentCoroutineContext().ensureActive()
    val offline = usableCache ?: try {
        readOffline()?.takeIf { it.hasCatalogContent() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    return ExploreSourceState(
        payload = offline,
        error = if (offline != null) "Güncellenemedi; son kaydedilen içerikler gösteriliyor."
            else "İçerik alınamadı. Kaynak boş veya geçici olarak erişilemiyor.",
        isCached = offline != null
    )
}

internal fun ExplorePayload.hasCatalogContent(): Boolean = listOf(
    topAnime, airingAnime, upcomingAnime, topManga, publishingManga, trendingAnime,
    movieAnime, seasonalAnime, trendingManga, newlyAddedAnime,
    newlyAddedManga, upcomingMediaTmdb, topRatedAnime.orEmpty(), topRatedManga.orEmpty(),
    bangumiTvShows.orEmpty(), bangumiMovies.orEmpty(),
    manhwaManhua.orEmpty(), novels.orEmpty()
).any { it.isNotEmpty() }

/** Keep source/type/ID together: TMDB film and TV IDs can overlap. */
internal fun JikanSearchResult.exploreIdentity(): String = "${source.lowercase()}:$type:$malId"

data class ExploreSourceSection(
    val platform: ExplorePlatform,
    val category: ExploreCategoryType,
    val title: String,
    val results: List<JikanSearchResult>
) {
    val key: String get() = "${platform.name}_${category.name}"
}

/**
 * Payload fields are overloaded by legacy providers (topManga can be TV shows!).
 * Keep each source's categories separate rather than merging incompatible fields.
 */
internal fun sourceSections(platform: ExplorePlatform, p: ExplorePayload): List<ExploreSourceSection> {
    require(platform != ExplorePlatform.ALL)
    fun section(category: ExploreCategoryType, title: String, items: List<JikanSearchResult>) =
        ExploreSourceSection(platform, category, title, items.distinctBy { it.exploreIdentity() })
    return if (platform == ExplorePlatform.TMDB || platform == ExplorePlatform.SIMKL) {
        val simkl = platform == ExplorePlatform.SIMKL
        listOf(
            section(ExploreCategoryType.TOP_ANIME, if (simkl) "Trend Diziler" else "Trend Her Şey", p.topAnime),
            section(ExploreCategoryType.TRENDING_ANIME, "Trend Animeler", p.trendingAnime),
            section(ExploreCategoryType.MOVIE_ANIME, "Trend Filmler", p.movieAnime),
            section(ExploreCategoryType.AIRING_ANIME, if (simkl) "Yayındaki Diziler" else "Trend Diziler", p.airingAnime),
            section(ExploreCategoryType.TOP_MANGA, if (simkl) "En İyi Diziler" else "Popüler Diziler", p.topManga),
            section(ExploreCategoryType.UPCOMING_ANIME, if (simkl) "Yeni Filmler" else "Popüler Filmler", p.upcomingAnime),
            section(ExploreCategoryType.NEWLY_ADDED_ANIME, "Popüler Animeler", p.newlyAddedAnime),
            section(ExploreCategoryType.TRENDING_MANGA, if (simkl) "Yayındaki Animeler" else "En Yüksek Puanlı Animeler", p.trendingManga),
            section(ExploreCategoryType.UPCOMING_MEDIA_TMDB, "Yakında Yayında", p.upcomingMediaTmdb)
        ) + if (simkl) emptyList() else listOf(
            section(ExploreCategoryType.PUBLISHING_MANGA, "En Yüksek Puanlı Filmler", p.publishingManga),
            section(ExploreCategoryType.SEASONAL_ANIME, "En Yüksek Puanlı Diziler", p.seasonalAnime)
        )
    } else listOf(
        section(ExploreCategoryType.TOP_ANIME, if (platform == ExplorePlatform.MAL || platform == ExplorePlatform.SHIKIMORI || platform == ExplorePlatform.BANGUMI) "En İyi Animeler" else "Popüler Animeler", p.topAnime),
        section(ExploreCategoryType.TOP_RATED_ANIME, "En Yüksek Puanlı Animeler", p.topRatedAnime.orEmpty()),
        section(ExploreCategoryType.TRENDING_ANIME, "Trend Animeler", p.trendingAnime),
        section(ExploreCategoryType.TOP_MANGA, if (platform == ExplorePlatform.MAL || platform == ExplorePlatform.SHIKIMORI || platform == ExplorePlatform.BANGUMI) "En İyi Mangalar" else "Popüler Mangalar", p.topManga),
        section(ExploreCategoryType.TOP_RATED_MANGA, "En Yüksek Puanlı Mangalar", p.topRatedManga.orEmpty()),
        section(ExploreCategoryType.AIRING_ANIME, "Yayındaki Animeler", p.airingAnime),
        section(ExploreCategoryType.UPCOMING_ANIME, "Yaklaşan Animeler", p.upcomingAnime),
        section(ExploreCategoryType.MOVIE_ANIME, "Anime Filmleri", p.movieAnime),
        section(ExploreCategoryType.SEASONAL_ANIME, "Bu Sezon", p.seasonalAnime),
        section(ExploreCategoryType.PUBLISHING_MANGA, "Yayındaki Mangalar", p.publishingManga),
        section(ExploreCategoryType.TRENDING_MANGA, "Trend Mangalar", p.trendingManga),
        section(ExploreCategoryType.NEWLY_ADDED_ANIME, "Yeni Eklenen Animeler", p.newlyAddedAnime),
        section(ExploreCategoryType.NEWLY_ADDED_MANGA, "Yeni Eklenen Mangalar", p.newlyAddedManga)
    ) + listOf(
        // Yerel "ek tür" rafları: kaynak API'si desteklemiyorsa liste boş gelir ve
        // bölüm kendiliğinden gizlenir (destekleyenler: MAL, Shikimori, AniList).
        section(ExploreCategoryType.MANHWA_MANHUA, "Manhwa & Manhua", p.manhwaManhua.orEmpty()),
        section(ExploreCategoryType.NOVELS, "Noveller & Light Novel", p.novels.orEmpty())
    ) + if (platform == ExplorePlatform.BANGUMI) listOf(
        section(ExploreCategoryType.BANGUMI_TV, "Bangumi Dizileri", p.bangumiTvShows.orEmpty()),
        section(ExploreCategoryType.BANGUMI_MOVIES, "Bangumi Filmleri", p.bangumiMovies.orEmpty())
    ) else emptyList()
}

/** Keep every category under its own source, in a stable source order. */
fun allSourceSections(
    states: Map<ExplorePlatform, ExploreSourceState>,
    showAdultContent: Boolean
): List<ExploreSourceSection> = ExplorePlatform.sources.flatMap { platform ->
    states[platform]?.payload?.let { sourceSections(platform, it.forSource(platform)) }.orEmpty().map { section ->
        section.copy(results = section.results.filter { showAdultContent || !it.isAdult })
    }.filter { it.results.isNotEmpty() }
}

/** Mirrors the lazy DSL: intro (+ sticky navigation) + each header/rows/footer. */
internal fun allSourceHeaderIndices(
    sections: Map<ExplorePlatform, List<ExploreSourceSection>>,
    collapsed: Set<ExplorePlatform>,
    startIndex: Int,
    hasJumpBar: Boolean = true,
    extraItemsAfterIntro: Int = 0
): Map<ExplorePlatform, Int> {
    // intro her zaman var; yapışkan kaynak çubuğu yalnızca gösteriliyorsa eklenir;
    // ortak "Yakında Yayında" şeridi gibi intro sonrası ek öğeler de sayılır.
    var index = startIndex + if (hasJumpBar) 2 else 1
    index += extraItemsAfterIntro
    return ExplorePlatform.sources.associateWith { platform ->
        val header = index++
        if (platform !in collapsed) index += sections[platform].orEmpty().size + 1
        header
    }
}

/** Legacy single-source fallback data must never be labelled as another provider in ALL. */
internal fun ExplorePayload.forSource(platform: ExplorePlatform): ExplorePayload {
    fun List<JikanSearchResult>.owned() = filter {
        when (platform) {
            ExplorePlatform.MAL -> it.source.equals("mal", true) || it.source.equals("jikan", true)
            else -> it.source.equals(platform.name, true)
        }
    }
    return copy(
        topAnime = topAnime.owned(), airingAnime = airingAnime.owned(),
        upcomingAnime = upcomingAnime.owned(), topManga = topManga.owned(),
        publishingManga = publishingManga.owned(), trendingAnime = trendingAnime.owned(),
        movieAnime = movieAnime.owned(), seasonalAnime = seasonalAnime.owned(),
        trendingManga = trendingManga.owned(), newlyAddedAnime = newlyAddedAnime.owned(),
        newlyAddedManga = newlyAddedManga.owned(), upcomingMediaTmdb = upcomingMediaTmdb.owned(),
        // airingSoonAnime FİLTRELENMEZ: "Yakında Yayında" şeridi tüm kaynaklarda aynı ortak
        // takvim verisini gerçek kimlikleriyle (anilist/mal/tmdb) taşır — kaynağa özel sözde
        // kimlik bulunmadığından owned() filtresi bu alanı boşaltmak yerine olduğu gibi korur.
        // Tümü modunda bu alan kaynak şeridi olarak RENDER EDİLMEZ (bak. allSourcesExploreSections).
        airingSoonAnime = airingSoonAnime,
        topRatedAnime = topRatedAnime.orEmpty().owned(), topRatedManga = topRatedManga.orEmpty().owned(),
        bangumiTvShows = bangumiTvShows.orEmpty().owned(), bangumiMovies = bangumiMovies.orEmpty().owned(),
        manhwaManhua = manhwaManhua.orEmpty().owned(), novels = novels.orEmpty().owned()
    )
}

/**
 * "Tümü" modu vitrini: adaylar tüm kaynakların tüm bölümlerinden sayısal
 * metriklere (puan, üye, favori, rank + trend/yeni eklenen bonusu) göre
 * puanlanır ve [selectHeroItems] ile seçilir.
 *
 * Sözleşme: her mevcut kaynaktan en az bir temsil garanti edilir (bir
 * kaynaktan ikinci öğe seçilmeden önce diğer kaynaklar temsil edilir),
 * kalan kontenjan en yüksek skorlu adaylarla doldurulur — böylece vitrin
 * hem tüm kaynakları hem de en iyi içerikleri kapsar.
 */
fun allSourceHeroes(
    states: Map<ExplorePlatform, ExploreSourceState>,
    showAdultContent: Boolean
): List<JikanSearchResult> = selectHeroItems(
    sections = allSourceSections(states, showAdultContent),
    limit = HERO_LIMIT_ALL,
    guaranteeSourceCoverage = true,
    perCategoryCap = HERO_PER_CATEGORY_CAP,
    perSourceCap = HERO_PER_SOURCE_CAP
)
