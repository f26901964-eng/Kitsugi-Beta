package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.utils.MediaTitleResolver
import org.json.JSONArray
import org.json.JSONObject

/**
 * TMDB liste yanıtları (discover / trending / recommendations / relations / takvim) için
 * ortak başlık çözümleme yardımcıları.
 *
 * Kural: yerelleştirilmiş (Türkçe) → İngilizce → Romaji. TMDB, istenen dilde başlık
 * bulamazsa orijinal (ör. Japonca) başlığı döndürdüğü için aynı sayfanın `en-US`
 * yanıtı yedek olarak kullanılır; CJK başlık ekranlara düşmez.
 *
 * ⚠️ İngilizce varyant üretirken [TmdbUrlUtils] kullanılmalıdır — `with_original_language`
 * parametresi bozulursa yedek istek farklı bir içerik listesi döndürür ve ID eşleşmesi tutmaz.
 */
internal object TmdbTitleFallback {

    /** Bir TMDB öğesi için çözümlenmiş başlık üçlüsü. */
    data class Titles(
        val display: String,
        val english: String?,
        val native: String?
    )

    /** İstenen dildeki başlık alanı (film uçlarında `title`, dizi uçlarında `name`). */
    fun localizedTitle(item: JSONObject, isMovie: Boolean): String =
        if (isMovie) item.optString("title", "") else item.optString("name", "")

    /** Orijinal başlık alanı. */
    fun originalTitle(item: JSONObject, isMovie: Boolean): String =
        if (isMovie) item.optString("original_title", "") else item.optString("original_name", "")

    /**
     * Yerelleştirilmiş başlık eksik ya da şüpheli mi?
     *
     * Şüpheli durumlar: başlık boş, CJK içeriyor veya TMDB orijinal başlığa düşmüş
     * (yerelleştirilmiş başlık == orijinal başlık ve orijinal dil istek dilinden farklı).
     */
    fun needsEnglishFallback(
        results: JSONArray,
        url: String,
        isMovieOf: (JSONObject) -> Boolean
    ): Boolean {
        if (TmdbUrlUtils.isEnglishLanguage(url)) return false
        val requestedIso = TmdbUrlUtils.requestedIso(url)
        for (i in 0 until minOf(results.length(), 20)) {
            val item = results.optJSONObject(i) ?: continue
            val isMovie = isMovieOf(item)
            val rawTitle = localizedTitle(item, isMovie)
            val original = originalTitle(item, isMovie)
            val originalLang = item.optString("original_language", "")
            if (rawTitle.isBlank()) return true
            if (MediaTitleResolver.hasCjk(rawTitle)) return true
            if (original.isNotBlank() && originalLang.isNotBlank() &&
                !originalLang.equals(requestedIso, ignoreCase = true) &&
                rawTitle.trim().equals(original.trim(), ignoreCase = true)
            ) return true
        }
        return false
    }

    /**
     * Yanıt metninden (id → Latin başlık) haritası üretir.
     *
     * @param arrayNames başlık dizisini taşıyan alan adları:
     *   liste uçları "results", "/collection" "parts", "/person/.../combined_credits" "cast"/"crew"
     */
    fun parseLatinTitles(
        enResponseText: String?,
        arrayNames: List<String> = listOf("results", "parts")
    ): Map<Int, String> {
        if (enResponseText.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(enResponseText)
            val map = mutableMapOf<Int, String>()
            for (name in arrayNames) {
                val array = root.optJSONArray(name) ?: continue
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optInt("id", 0)
                    if (id <= 0 || map.containsKey(id)) continue
                    val latin = MediaTitleResolver.latin(
                        MediaTitleResolver.nonBlank(
                            item.optString("title"),
                            item.optString("name"),
                            item.optString("original_title"),
                            item.optString("original_name")
                        )
                    )
                    if (latin != null) map[id] = latin
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** `en-US` liste yanıtından (id → Latin başlık) haritası üretir. */
    fun parseEnglishTitles(enResponseText: String?): Map<Int, String> =
        parseLatinTitles(enResponseText)

    /**
     * Tek bir TMDB öğesinin başlıklarını çözer.
     *
     * @param englishTitle aynı sayfanın en-US yanıtından gelen başlık (varsa)
     */
    fun resolve(
        item: JSONObject,
        isMovie: Boolean,
        url: String,
        englishTitle: String?
    ): Titles {
        val localized = localizedTitle(item, isMovie)
        val original = originalTitle(item, isMovie)
        val originalLang = item.optString("original_language", "")
        val isFallback = MediaTitleResolver.isLocalizedFallback(
            localized = localized,
            original = original,
            requestedLanguage = TmdbUrlUtils.languageOf(url),
            originalLanguage = originalLang
        )
        val localizedInput = if (isFallback) null else localized
        val englishInput = MediaTitleResolver.latin(englishTitle)
        val romajiInput = MediaTitleResolver.latin(original)

        val display = MediaTitleResolver.resolve(
            localized = localizedInput,
            english = englishInput,
            romaji = romajiInput,
            original = original.ifBlank { localized }
        )
        val english = MediaTitleResolver.resolveEnglish(
            localized = localizedInput,
            english = englishInput,
            romaji = romajiInput,
            original = original
        ) ?: display
        val native = original
            .ifBlank { localized }
            .takeIf {
                originalLang in listOf("ja", "zh", "ko") ||
                    MediaTitleResolver.hasCjk(original) ||
                    MediaTitleResolver.hasCjk(localized)
            }
        return Titles(display = display, english = english, native = native)
    }
}
