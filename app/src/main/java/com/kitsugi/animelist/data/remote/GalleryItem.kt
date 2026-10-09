package com.kitsugi.animelist.data.remote

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
    val details: Map<String, String> = emptyMap()
)

enum class GalleryCategory(val label: String) {
    POSTER("Poster"),
    BACKDROP("Arka Plan"),
    LOGO("Logo"),
    CLEARART("ClearART"),   // Fanart.tv: HD ClearART (şeffaf zemin, sanatsal kesim)
    THUMBNAIL("Küçük Resim"),
    CHARACTER("Karakter"),
    PERSON("Kişi"),
    BANNER("Afiş"),
    SQUARE("Kare Poster"),  // Fanart.tv: Square Poster
    OTHER("Diğer")
}
