package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import java.util.Locale

/** Galeride açılacak liste ile, bu listedeki başlangıç konumunu birlikte taşır. */
internal data class HeroGallerySelection(
    val items: List<GalleryItem>,
    val index: Int
)

/**
 * Detay vitrininin gösterdiği gerçek görseli galeriye bağlar.
 *
 * Vitrin URL'yi kalite için dönüştürmüş olabilir (ör. TMDB `w500` → `original`,
 * Kitsu `medium` → `large`). Önce bu varyantları aynı görsel olarak eşleriz. Görsel
 * galeride henüz yoksa — galeri hâlâ yükleniyor olsa bile — tıklanan URL'yi listenin
 * başına ekleriz. Böylece galeri her koşulda kullanıcının dokunduğu görselle açılır.
 */
internal fun selectHeroArtworkInGallery(
    galleryItems: List<GalleryItem>,
    clickedUrl: String,
    clickedCategory: GalleryCategory,
    source: String
): HeroGallerySelection {
    val cleanClickedUrl = clickedUrl.trim()
    if (cleanClickedUrl.isEmpty()) {
        return HeroGallerySelection(galleryItems, 0)
    }

    val existingIndex = galleryItems.indexOfFirst { item ->
        heroArtworkUrlsMatch(item.url, cleanClickedUrl)
    }
    if (existingIndex >= 0) {
        return HeroGallerySelection(galleryItems, existingIndex)
    }

    val clickedItem = GalleryItem(
        url = cleanClickedUrl,
        source = source,
        category = clickedCategory
    )
    return HeroGallerySelection(listOf(clickedItem) + galleryItems, 0)
}

/** URL boyut/CDN varyantlarını aynı galeri görseli olarak tanır. */
internal fun heroArtworkUrlsMatch(first: String, second: String): Boolean {
    val firstIdentity = canonicalHeroArtworkUrl(first)
    val secondIdentity = canonicalHeroArtworkUrl(second)
    if (firstIdentity.isEmpty() || secondIdentity.isEmpty()) return false
    if (firstIdentity == secondIdentity) return true

    // Bazı CDN'ler aynı dosyayı farklı dizinlerden verir. Yalnızca yeterince ayırt
    // edici dosya adlarında bu son çareyi kullan; `large.jpg` gibi genel adlar eşleşmez.
    val firstFileName = firstIdentity.substringAfterLast('/')
    val secondFileName = secondIdentity.substringAfterLast('/')
    val firstStem = firstFileName.substringBeforeLast('.')
    return firstFileName == secondFileName && firstStem.length >= 12
}

private fun canonicalHeroArtworkUrl(url: String): String {
    return url.trim()
        .substringBefore('#')
        .substringBefore('?')
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        // TMDB kalite varyantları
        .replace(
            Regex("image\\.tmdb\\.org/t/p/(?:original|w\\d+|h\\d+)/"),
            "image.tmdb.org/t/p/"
        )
        // Bangumi kapak kalite varyantları
        .replace(
            Regex("lain\\.bgm\\.tv/r/\\d+/pic/cover/[a-z]/"),
            "lain.bgm.tv/pic/cover/"
        )
        .replace(
            Regex("lain\\.bgm\\.tv/pic/cover/[a-z]/"),
            "lain.bgm.tv/pic/cover/"
        )
        // Kitsu poster/cover kalite varyantları
        .replace(
            Regex("(/poster_images/\\d+/)(?:tiny|small|medium|large)(\\.[a-z0-9]+)$"),
            "$1{size}$2"
        )
        .replace(
            Regex("(/cover_images/\\d+/)(?:tiny|small|medium|large|original)(\\.[a-z0-9]+)$"),
            "$1{size}$2"
        )
        // AniList, MAL ve Shikimori kalite varyantları
        .replace(Regex("/cover/(?:small|medium|large)/"), "/cover/{size}/")
        .replace(Regex("myanimelist\\.net/r/\\d+x\\d+/images/"), "myanimelist.net/images/")
        .replace(Regex("/(?:preview|x96|x48|original)/"), "/{size}/")
        .trimEnd('/')
}
