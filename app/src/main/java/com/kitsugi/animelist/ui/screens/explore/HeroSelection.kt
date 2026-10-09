package com.kitsugi.animelist.ui.screens.explore

import com.kitsugi.animelist.data.remote.JikanSearchResult
import kotlin.math.ln

/**
 * Vitrin (hero) içerik seçim motoru.
 *
 * Seçim tamamen sayısal metriklere dayanır:
 *  - **Puan** (quality): `rawScoreDouble` / `score`. Kaynak ölçekleri farklıdır
 *    (çoğu 0–10, Simkl 0–100) — [qualityScore] 0–1 aralığına normalize eder.
 *  - **Üye / izlenme** (`members`): logaritmik normalize (5M üstü doygunluk).
 *  - **Favori** (`favorites`): logaritmik normalize (1M üstü doygunluk).
 *  - **Sıra** (`rank`): düşük rank hafif avantaj sağlar (logaritmik düşüş).
 *
 * Metriklerden herhangi biri yoksa (örn. TMDB'nin üyesi yok) ağırlık
 * mevcut metriklere yeniden dağıtılır; eksik veri ceza değildir.
 *
 * Kategori/trend bonusları [heroCategoryBoost] ile uygulanır: trend,
 * yeni eklenen ve en yüksek puanlı bölümleri hafifçe öne çıkarır; tarih
 * gelmedi (yakında) bölümleri hafifçe geride kalır. Böylece vitrin hem
 * sayısal sıralamayı hem de "trend / yeni eklenen / manga" çeşitliliğini
 * kontrollü biçimde kapsar.
 */
internal const val HERO_LIMIT_ALL = 12
internal const val HERO_LIMIT_SINGLE = 10
internal const val HERO_PER_CATEGORY_CAP = 3
internal const val HERO_PER_SOURCE_CAP = 2

private const val W_QUALITY = 0.40
private const val W_POPULARITY = 0.30
private const val W_FAVORITES = 0.20
private const val W_RANK = 0.10
private const val DEFAULT_METRIC_SCORE = 0.35
private const val SECTION_POSITION_BONUS = 0.05
private const val SECTION_POSITION_SPAN = 19.0
private const val POPULARITY_SATURATION = 5_000_000.0
private const val FAVORITES_SATURATION = 1_000_000.0

/** 0..1 arası normalize edilmiş puan; puan verisi yoksa null. */
internal fun qualityScore(item: JikanSearchResult): Double? {
    val rawDouble = item.rawScoreDouble
    if (rawDouble != null && rawDouble > 0.0) {
        return (rawDouble / 10.0).coerceIn(0.0, 1.0)
    }
    val score = item.score
    if (score != null && score > 0) {
        // Simkl 0–100 arası saklar; diğer kaynaklar 0–10.
        val normalized = if (score > 10) score / 100.0 else score / 10.0
        return normalized.coerceIn(0.0, 1.0)
    }
    return null
}

/** 0..1 arası normalize edilmiş üye/izlenme popüleritesi. */
internal fun membersScore(item: JikanSearchResult): Double? {
    val members = item.members
    if (members == null || members <= 0) return null
    return (ln(members + 1.0) / ln(POPULARITY_SATURATION)).coerceIn(0.0, 1.0)
}

/** 0..1 arası normalize edilmiş favori (favorileme) puanı. */
internal fun favoritesScore(item: JikanSearchResult): Double? {
    val favorites = item.favorites
    if (favorites == null || favorites <= 0) return null
    return (ln(favorites + 1.0) / ln(FAVORITES_SATURATION)).coerceIn(0.0, 1.0)
}

/** 0..1 arası; rank=1 → 1.0, rank yükseldikçe logaritmik olarak düşer. */
internal fun rankScore(item: JikanSearchResult): Double? {
    val rank = item.rank
    if (rank == null || rank <= 0) return null
    return 1.0 / (1.0 + ln(rank.toDouble()))
}

/**
 * Metriklerin ağırlıklı ortalaması (0..1). Bulunmayan metrikler
 * dışlanır ve kalan ağırlıklar yeniden normalize edilir.
 * Hiçbir metrik yoksa [DEFAULT_METRIC_SCORE] döner.
 */
internal fun heroMetricScore(item: JikanSearchResult): Double {
    var total = 0.0
    var weight = 0.0

    fun add(part: Double, value: Double?) {
        if (value == null) return
        total += part * value
        weight += part
    }

    add(W_QUALITY, qualityScore(item))
    add(W_POPULARITY, membersScore(item))
    add(W_FAVORITES, favoritesScore(item))
    add(W_RANK, rankScore(item))

    if (weight <= 0.0) return DEFAULT_METRIC_SCORE
    return total / weight
}

/** Bölüm türüne göre hafif çarpan: trend/yeni/en iyi puan öne çıkar. */
internal fun heroCategoryBoost(category: ExploreCategoryType): Double = when (category) {
    ExploreCategoryType.TRENDING_ANIME,
    ExploreCategoryType.TRENDING_MANGA -> 1.15

    ExploreCategoryType.TOP_RATED_ANIME,
    ExploreCategoryType.TOP_RATED_MANGA,
    ExploreCategoryType.BANGUMI_TV,
    ExploreCategoryType.BANGUMI_MOVIES -> 1.12

    ExploreCategoryType.TOP_ANIME,
    ExploreCategoryType.TOP_MANGA -> 1.05

    ExploreCategoryType.SEASONAL_ANIME,
    ExploreCategoryType.NEWLY_ADDED_ANIME,
    ExploreCategoryType.NEWLY_ADDED_MANGA,
    ExploreCategoryType.MOVIE_ANIME -> 1.10

    ExploreCategoryType.AIRING_ANIME,
    ExploreCategoryType.PUBLISHING_MANGA -> 1.00

    ExploreCategoryType.UPCOMING_ANIME,
    ExploreCategoryType.UPCOMING_MEDIA_TMDB -> 0.95
}

/** Bir adayın nihai vitrin skoru: metrik × kategori bonusu × bölüm içi konum. */
internal fun heroCandidateScore(
    item: JikanSearchResult,
    category: ExploreCategoryType,
    sectionPosition: Int
): Double {
    val position = sectionPosition.coerceIn(0, SECTION_POSITION_SPAN.toInt())
    val positionBonus = 1.0 + SECTION_POSITION_BONUS *
        (1.0 - position / SECTION_POSITION_SPAN)
    return heroMetricScore(item) * heroCategoryBoost(category) * positionBonus
}

/** Bir bölümdeki (kaynak × kategori) aday; aynı öğenin en iyi skorlu hali korunur. */
internal data class HeroCandidate(
    val item: JikanSearchResult,
    val platform: ExplorePlatform,
    val category: ExploreCategoryType,
    val score: Double
)

/**
 * Bölümlerden aday havuzu üretir: boş başlık atlanır, aynı öğe farklı
 * bölümlerde birden fazla kez geçebilir — en yüksek skorlu hali saklanır.
 */
internal fun buildHeroCandidates(
    sections: List<ExploreSourceSection>
): List<HeroCandidate> {
    val bestByIdentity = LinkedHashMap<String, HeroCandidate>()
    sections.forEach { section ->
        section.results.forEachIndexed { index, item ->
            if (item.title.isBlank()) return@forEachIndexed
            val candidate = HeroCandidate(
                item = item,
                platform = section.platform,
                category = section.category,
                score = heroCandidateScore(item, section.category, index)
            )
            val key = item.exploreIdentity()
            val existing = bestByIdentity[key]
            if (existing == null || candidate.score > existing.score) {
                bestByIdentity[key] = candidate
            }
        }
    }
    return bestByIdentity.values.toList()
}

/**
 * Vitrin için aday seçimi.
 *
 * 1. [guaranteeSourceCoverage] açıkken her kaynaktan en az bir öğe (en
 *    yüksek skorlu) garanti edilir — Tümü modunda hiçbir kaynak dışarda kalmaz.
 * 2. Skor sırasıyla kategori ([perCategoryCap]) ve kaynak ([perSourceCap])
 *    tavanları uygulanarak kontenjan doldurulur.
 * 3. Tavanlar dolduğu hâlde kontenjan varsa tavanlar esnetilir (içerik
 *    boş kalmasın).
 * 4. Son liste skor sırasına göre (kararlı) sıralanır; vitrin ilk sayfası
 *    en güçlü adayla açılır.
 */
internal fun selectHeroItems(
    sections: List<ExploreSourceSection>,
    limit: Int,
    guaranteeSourceCoverage: Boolean = false,
    perCategoryCap: Int = HERO_PER_CATEGORY_CAP,
    perSourceCap: Int = Int.MAX_VALUE
): List<JikanSearchResult> {
    if (limit <= 0) return emptyList()
    val sorted = buildHeroCandidates(sections).sortedByDescending { it.score }
    if (sorted.isEmpty()) return emptyList()

    val selected = LinkedHashMap<String, HeroCandidate>()

    if (guaranteeSourceCoverage) {
        ExplorePlatform.sources.forEach { platform ->
            if (selected.size >= limit) return@forEach
            val best = sorted.firstOrNull { it.platform == platform } ?: return@forEach
            selected[best.item.exploreIdentity()] = best
        }
    }

    fun categoryCount(candidate: HeroCandidate): Int =
        selected.values.count { it.category == candidate.category }

    fun sourceCount(candidate: HeroCandidate): Int =
        selected.values.count { it.platform == candidate.platform }

    for (candidate in sorted) {
        if (selected.size >= limit) break
        val key = candidate.item.exploreIdentity()
        if (key in selected) continue
        if (categoryCount(candidate) >= perCategoryCap) continue
        if (sourceCount(candidate) >= perSourceCap) continue
        selected[key] = candidate
    }

    // Tavanlar sıkıysa kontenjan boş kalmasın: tavanları esneterek tamamla.
    if (selected.size < limit) {
        for (candidate in sorted) {
            if (selected.size >= limit) break
            val key = candidate.item.exploreIdentity()
            if (key !in selected) selected[key] = candidate
        }
    }

    return selected.values
        .sortedByDescending { it.score }
        .map { it.item }
}
