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
    val height: Int? = null
)

enum class GalleryCategory(val label: String) {
    POSTER("Poster"),
    BACKDROP("Arka Plan"),
    LOGO("Logo"),
    CLEARART("ClearART"),   // Fanart.tv: HD ClearART (şeffaf zemin, sanatsal kesim)
    THUMBNAIL("Küçük Resim"),
    CHARACTER("Karakter"),
    BANNER("Afiş"),
    SQUARE("Kare Poster"),  // Fanart.tv: Square Poster
    OTHER("Diğer")
}
