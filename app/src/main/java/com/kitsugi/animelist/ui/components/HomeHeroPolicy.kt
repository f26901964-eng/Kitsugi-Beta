package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.model.MediaType

/** Books (including manga, manhwa/manhua and novels) never enter a video showcase. */
internal fun isHomeHeroMedia(item: JikanSearchResult): Boolean =
    item.type != MediaType.Manga && item.bangumiSubjectType != 1 && item.title.isNotBlank()

/** Stable across artwork loads; provider, media namespace and extension URL are all significant. */
internal fun homeHeroItemKey(item: JikanSearchResult): String =
    "${item.source.trim().lowercase()}:${item.type.name}:${item.malId}:${item.tmdbId ?: 0}:${item.cs3Url.orEmpty()}"

/** Backdrop for WIDE hero bands (landscape / TV / tablet). A poster is never relabeled as one. */
internal fun homeHeroBackdropUrl(item: JikanSearchResult): String? {
    if (!isHomeHeroMedia(item)) return null
    return KitsugiHeroArtworkResolver.sanitizeNativeBackdropUrl(item.backdropUrl, item.imageUrl, item.source)
        ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
}

/**
 * Yüksek çözünürlüklü poster URL'si — **dikey telefon bandı** için.
 *
 * Dikey telefonda vitrin bandı dardır (en-boy < 1): yatay bir backdrop üstte ince
 * bir şeride ezilir ve bandın geri kalanı boş/siyah kalır. Bu geometride kaynağın
 * kendi posteri, mümkün olan en yüksek çözünürlüklü varyantına yükseltilerek bandı
 * tam kaplayacak şekilde gösterilir (ekran geometrisine göre otomatik seçim —
 * bkz. `HeroVitrinLayout.prefersPoster`).
 *
 * Poster, backdrop ile aynı görsel kimliğe sahipse (kaynak aynı görseli iki alana
 * da koymuşsa) gerçek bir poster yoktur → `null`; sayfa backdrop şeridine düşer.
 */
internal fun homeHeroPosterUrl(item: JikanSearchResult): String? {
    if (!isHomeHeroMedia(item)) return null
    val poster = KitsugiHeroArtworkResolver.upgradeNativePosterUrl(item.imageUrl, item.source)
        ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) } ?: return null
    val backdrop = item.backdropUrl?.takeIf { it.isNotBlank() }
    if (backdrop != null &&
        KitsugiHeroArtworkResolver.normalizeImageIdentity(poster) ==
        KitsugiHeroArtworkResolver.normalizeImageIdentity(backdrop)
    ) {
        return null
    }
    return poster
}

/** Check decoded dimensions as well as URL hints: a mislabeled portrait must not slip through. */
internal fun isHorizontalHomeHeroArtwork(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width > height

/** Poster tarafındaki karşılığı: görsel gerçekten dikey olmalı. */
internal fun isVerticalHomeHeroPoster(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && height > width

/** Decode edilmiş en-boy oranı (w/h) 1 ve üzerindeyse görsel poster değildir. */
internal fun isVerticalHomeHeroPosterAspect(aspect: Float): Boolean =
    aspect.isFinite() && aspect > 0.05f && aspect < 1f

/** Keep existing pages in place when a slower source finishes loading. New pages append. */
internal fun stableHomeHeroOrder(
    previous: List<JikanSearchResult>,
    next: List<JikanSearchResult>
): List<JikanSearchResult> {
    val remaining = next.associateByTo(LinkedHashMap()) { homeHeroItemKey(it) }
    return buildList {
        previous.forEach { old -> remaining.remove(homeHeroItemKey(old))?.let { add(it) } }
        addAll(remaining.values)
    }
}

/**
 * Vitrin barı içerik sayısını **birebir** gösterir: her sayfa için tek bir bar.
 *
 * Eski sürüm kayan bir pencereyle en fazla 7 bar çiziyordu; vitrinde örn. 18 içerik
 * varken bar 7 gösterip sayfa ilerledikçe barları kaydırdığı için toplam sayısı
 * yanlış anlaşılıyordu ("bug"). Artık pencere/kısaltma yok: bar sayısı == sayfa
 * sayısı. İçerik çoğaldıkça barlar satıra eşit paylaşılarak incelir (bkz.
 * [heroIndicatorRowWidthDp]).
 */
internal fun homeHeroIndicatorIndices(itemCount: Int): List<Int> =
    if (itemCount <= 0) emptyList() else (0 until itemCount).toList()

/** Bir bar yuvasının hedef genişliği (dp); sığmazsa tüm yuvalar eşit küçülür. */
internal const val HERO_INDICATOR_SLOT_DP = 34f

/** Bar aralıkları sayıyla birlikte daralır ki barların kendisi okunur kalsın. */
internal fun heroIndicatorSpacingDp(itemCount: Int): Float = when {
    itemCount <= 10 -> 6f
    itemCount <= 18 -> 4f
    else -> 3f
}

/**
 * Bar satırının genişliği: içerik azken doğal genişliğinde (yuva hedefi korunur),
 * çokken kullanılabilir genişliği asla aşmaz. Yuvalar `weight(1f)` ile eşit
 * paylaşıldığı için her bar aynı genişlikte olur ve bar sayısı her zaman gerçek
 * sayfa sayısına eşittir.
 */
internal fun heroIndicatorRowWidthDp(itemCount: Int, availableWidthDp: Float): Float {
    if (itemCount <= 0 || availableWidthDp <= 0f) return 0f
    val natural = itemCount * HERO_INDICATOR_SLOT_DP + (itemCount - 1) * heroIndicatorSpacingDp(itemCount)
    return minOf(natural, availableWidthDp)
}

/** Vitrin kendi kendine kayma aralığı: son etkileşimden 5 sn sonra bir sonraki sayfa. */
internal const val HERO_AUTOPLAY_DELAY_MS = 5_000L

/** Otomatik kaydırma döngüsünün etkileşim kontrol aralığı. */
internal const val HERO_AUTOPLAY_TICK_MS = 200L

/** Autoplay reverses at the edges, rather than animating through every page from last to first. */
internal fun homeHeroAutoDirection(currentPage: Int, itemCount: Int, previousDirection: Int): Int = when {
    itemCount <= 1 -> 0
    currentPage <= 0 -> 1
    currentPage >= itemCount - 1 -> -1
    else -> if (previousDirection < 0) -1 else 1
}
