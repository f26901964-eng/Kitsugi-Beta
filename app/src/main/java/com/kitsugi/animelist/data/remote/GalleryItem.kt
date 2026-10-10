package com.kitsugi.animelist.data.remote

import androidx.annotation.StringRes
import com.kitsugi.animelist.R

/**
 * Galeri öğesi — görselin URL'si, kaynağı ve kategorisi.
 *
 * [source] → "Fanart.tv" | "TMDB" | "Jikan" | "Simkl" | "AniList" | "Kitsu"
 * [category] → [GalleryCategory] ile sınıflandırılır; galeri filtre sekmeleri için kullanılır.
 */
data class GalleryItem(
    val url: String,
    val source: String,
    val category: GalleryCategory = GalleryCategory.OTHER,
    val description: String? = null,
    val language: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    /**
     * API'nin doğrudan sağladığı ek detay satırları (sıra korunur).
     *
     * Örn. Fanart.tv v3 API'si her görsel için `name` ve `iMDb` alanları döndürür;
     * galeri bunları "Ad" / "IMDb ID" satırları olarak gösterir. API'de olmayan
     * alanlar (yükleyen, indirme sayısı vb.) uydurulmaz — yalnızca gerçekten
     * gelen veriler listelenir.
     */
    val details: Map<String, String> = emptyMap(),
    /** Fanart.tv sezon etiketi ("1", "2"…). "all" / boş değer yazılmaz. */
    val season: String? = null
)

/**
 * Galeri kategorileri — etiketler strings.xml üzerinden gelir (values / values-en),
 * böylece galeri sekmeleri uygulama dilini takip eder.
 */
enum class GalleryCategory(@StringRes val labelRes: Int) {
    POSTER(R.string.gallery_cat_poster),
    BACKDROP(R.string.gallery_cat_backdrop),
    LOGO(R.string.gallery_cat_logo),
    CLEARART(R.string.gallery_cat_clearart),   // Fanart.tv: HD ClearART (şeffaf zemin, sanatsal kesim)
    THUMBNAIL(R.string.gallery_cat_thumbnail),
    CHARACTER(R.string.gallery_cat_character),
    PERSON(R.string.gallery_cat_person),
    BANNER(R.string.gallery_cat_banner),
    SQUARE(R.string.gallery_cat_square),  // Fanart.tv: Square Poster
    OTHER(R.string.gallery_cat_other)
}
