package com.kitsugi.animelist.ui.screens.search.components

import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.detail.StudioTypeFilter
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

// ─────────────────────────────────────────────────────────────────────────────
// Eklenti (Cloudstream) → Ana Sayfa Paritesi
//
// Eklenti portalı keşfet sayfalarının ana sayfa (Keşfet) ile BİREBİR aynı
// bileşenleri (KitsugiHeroSection vitrini, KitsugiHorizontalMediaSection
// rafları, KitsugiExploreMediaCard kutucukları) kullanabilmesi için CS3
// SearchResponse öğelerini uygulamanın ortak JikanSearchResult modeline çevirir.
// ─────────────────────────────────────────────────────────────────────────────

/** CS3 TvType → uygulamanın ortak MediaType'i (kart/vitrin meta diliyle aynı). */
internal fun TvType?.toAddonMediaType(): MediaType = when (this) {
    TvType.Movie, TvType.AnimeMovie -> MediaType.Movie
    TvType.TvSeries, TvType.AsianDrama, TvType.Documentary -> MediaType.TvShow
    else -> MediaType.Anime
}

/** CS3 TvType → Türkçe alt başlık etiketi (ana sayfa kart alt yazılarıyla aynı dil). */
internal fun TvType?.toAddonTypeLabel(): String = when (this) {
    TvType.Movie, TvType.AnimeMovie -> "Film"
    TvType.TvSeries -> "Dizi"
    TvType.Anime -> "Anime"
    TvType.AsianDrama -> "Asya"
    TvType.Cartoon -> "Çizgi film"
    TvType.Documentary -> "Belgesel"
    TvType.Live -> "Canlı"
    TvType.OVA -> "OVA"
    null -> "Video"
    else -> "Video"
}

/**
 * CS3 [SearchResponse] → [JikanSearchResult] çevirisi.
 *
 * - `source` / `cs3ApiName` eklenti adını taşır; böylece vitrin çipi ve kart
 *   rozetleri eklenti adını gösterir.
 * - `cs3Url` doldurulur; tıklamalar bu URL üzerinden eklenti detay diyaloğuna gider.
 * - `malId` URL'den türetilen kararlı bir pozitif sayıdır (anahtarlar için).
 */
/** `year` alanı bazı CS3 derlemelerinde doğrudan açık olmayabilir — yansımalı güvenli okuma. */
private fun SearchResponse.safeYear(): Int? = runCatching {
    val field = javaClass.getDeclaredField("year")
    field.isAccessible = true
    (field.get(this) as? Number)?.toInt()
}.getOrNull()

internal fun SearchResponse.toAddonSearchResult(): JikanSearchResult {
    val stableId = runCatching {
        val h = url.hashCode().toLong()
        val positive = if (h < 0) -h else h
        ((positive % 900_000_000L) + 1_000_000L).toInt()
    }.getOrDefault(1)

    return JikanSearchResult(
        malId = stableId,
        title = name,
        subtitle = type.toAddonTypeLabel(),
        type = type.toAddonMediaType(),
        total = null,
        score = null,
        isAdult = false,
        imageUrl = posterUrl,
        year = safeYear(),
        source = apiName,
        cs3Url = url,
        cs3ApiName = apiName
    )
}

/**
 * Eklenti keşfet "Tümünü Gör" sayfasındaki emojili tür çipleri — stüdyo ve keşfet
 * sayfalarındaki çip diliyle birebir aynı (StudioTypeFilter aynı görsel sözleşmeyi taşır).
 */
internal val ADDON_TYPE_FILTERS: List<StudioTypeFilter> = listOf(
    StudioTypeFilter("ALL", "✨", "Tümü"),
    StudioTypeFilter("ANIME", "🎌", "Anime"),
    StudioTypeFilter("MOVIE", "🎥", "Film"),
    StudioTypeFilter("TV", "📺", "Dizi")
)
