package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import java.util.Locale
import kotlin.math.max

/**
 * Ekran yönüne + boyutuna göre detay vitrininin hangi görseli birincil göstereceğine
 * karar veren sonuç.
 *
 *  - Yatay (landscape) modda her zaman dikey poster tercih edilir; ekran boyutu
 *    ne olursa olsun, dikey poster dikey-uzun-dar bir kutuya yerleştirilir.
 *  - Dikey (portrait) modda ekran ORANI ve MUTLAK BOYUTU birlikte bakılır:
 *      * Uzun-dar telefonlarda (genişlik/yükseklik ≤ `PORTRAIT_TALL_RATIO` ve
 *        genişlik < `PORTRAIT_TABLET_MIN_WIDTH_DP`) dikey poster tercih edilir;
 *        poster dikey-uzun-dar bir kutuya yerleştirilir.
 *      * Daha kare/tablet benzeri veya BÜYÜK ekranlarda yatay arka plan tercih
 *        edilir; dev posterin ekranı yutması engellenir.
 *  - Hiçbir kurala uymayan nadir köşelerde güvenli varsayılan: yatay arka plan.
 */
internal enum class HeroArtworkPreference {
    /** Dikey poster (uzun-dar kutu). */
    POSTER,
    /** Yatay arka plan / banner (geniş-kısa bant). */
    BACKDROP
}

internal object HeroArtworkLayoutRules {
    /**
     * Dikey modda "yeterince uzun-dar telefon sayılır" eşiği. Tipik telefonler
     * 9:19.5 (~0.46) ile 9:18 (~0.50) arasındadır; 0.55 üstü (eski 16:9 telefonlar,
     * katlanabilirler, tabletler) yatay arka plan tercih eder.
     *
     * NOT: v2.4.338'de bu eşik geçici olarak 0.75'e çekilmişti; sonuçta neredeyse
     * tüm dikey ekranlar dev boyutlu tam-ekran poster gösteriyordu. Kullanıcı
     * geri bildirimiyle 0.55 orijinal eşiğine geri dönüldü.
     */
    const val PORTRAIT_TALL_RATIO: Float = 0.55f

    /**
     * Dikey modda "büyük ekran" mutlak genişlik eşiği (dp). Ekrana bakılmaksızın
     * bu genişliğin üstündeki dikey düzenler (tablet/katlanabilir iç ekran) oranına
     * bakılmaksızın yatay arka plan gösterir; dev posterin ekranı yutması engellenir.
     */
    const val PORTRAIT_TABLET_MIN_WIDTH_DP: Int = 600

    /**
     * Yatay modda "dikey poster için yeterince kısa modül" eşiği. Genelde tüm
     * cihazlarda tetiklenir; çok ender "kare yatay" (tablet döndürülmüş) senaryoları
     * için koruma sağlar.
     */
    const val LANDSCAPE_TALL_RATIO: Float = 0.40f
}

/**
 * Ekranın yönüne + boyutuna + oranına göre vitrinin birincil görsel tercihini döndürür.
 *
 * Karar SAYFA YÜKLENMEDEN, yalnızca ekran ölçülerinden verilir; ağ çağrısı veya
 * görsel boyutu beklemez. Böylece vitrin ilk kareden itibaren doğru yöndeki
 * görseli gösterir, yükleme sonrası yön değişimi (flip) olmaz.
 *
 * @param screenWidthDp Ekranın mevcut genişliği (dp).
 * @param screenHeightDp Ekranın mevcut yüksekliği (dp).
 */
internal fun resolveHeroArtworkPreference(
    screenWidthDp: Int,
    screenHeightDp: Int
): HeroArtworkPreference {
    val w = max(1, screenWidthDp).toFloat()
    val h = max(1, screenHeightDp).toFloat()
    val ratio = w / h
    return when {
        // Yatay mod (genişlik > yükseklik): her zaman dikey poster.
        w > h -> {
            if (ratio >= HeroArtworkLayoutRules.LANDSCAPE_TALL_RATIO) {
                HeroArtworkPreference.POSTER
            } else {
                HeroArtworkPreference.BACKDROP
            }
        }
        // Dikey mod: BÜYÜK ekranlar (tablet/katlanabilir) daima yatay arka plan.
        screenWidthDp >= HeroArtworkLayoutRules.PORTRAIT_TABLET_MIN_WIDTH_DP -> HeroArtworkPreference.BACKDROP
        // Dikey mod: uzun-dar telefonlar dikey poster, daha kare ekranlar arka plan.
        ratio <= HeroArtworkLayoutRules.PORTRAIT_TALL_RATIO -> HeroArtworkPreference.POSTER
        else -> HeroArtworkPreference.BACKDROP
    }
}

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
