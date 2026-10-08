package com.kitsugi.animelist.data.remote

/**
 * TMDB URL yardımcıları.
 *
 * ⚠️ KRİTİK NOT: `language=` parametresi yalnızca **tam parametre** olarak
 * değiştirilmelidir. Eski kod `Regex("language=[^&]+")` kullanıyordu ve bu regex
 * TÜM eşleşmeleri değiştirdiği için `with_original_language=ja` parametresinin
 * içindeki `language=ja` kısmını da yakalayıp `with_original_language=en-US`
 * yapıyordu.
 *
 * Sonuç: anime keşfet şeritlerinin (`with_genres=16&with_original_language=ja`)
 * İngilizce yedek isteği tamamen farklı bir içerik listesi döndürüyor, ID
 * eşleşmesi tutmuyor ve Türkçesi olmayan içerikler ekranda Japonca kalıyordu.
 */
internal object TmdbUrlUtils {

    /** Yalnızca bağımsız `language=` parametresini yakalar; `with_original_language=` etkilenmez. */
    private val LANGUAGE_PARAM = Regex("([?&])language=[^&]*")

    /** URL'nin `language` parametresini güvenle değiştirir (`with_original_language` korunur). */
    fun withLanguage(url: String, language: String): String {
        if (LANGUAGE_PARAM.containsMatchIn(url)) {
            return LANGUAGE_PARAM.replace(url) { match -> "${match.groupValues[1]}language=$language" }
        }
        val separator = if (url.contains("?")) "&" else "?"
        return "$url$separator" + "language=$language"
    }

    /** URL'deki aktif `language` değeri (yoksa boş). */
    fun languageOf(url: String): String =
        LANGUAGE_PARAM.find(url)?.value?.substringAfter("language=", "")?.trim().orEmpty()

    /** URL zaten İngilizce başlık istiyor mu? */
    fun isEnglishLanguage(url: String): Boolean =
        languageOf(url).startsWith("en", ignoreCase = true)

    /** İngilizce (en-US) başlık isteyen varyant. */
    fun englishVariant(url: String): String = withLanguage(url, "en-US")

    /** URL'nin istek dilinin ISO kodu (örn. "tr-TR" → "tr"). */
    fun requestedIso(url: String): String =
        languageOf(url).substringBefore('-').lowercase()
}
