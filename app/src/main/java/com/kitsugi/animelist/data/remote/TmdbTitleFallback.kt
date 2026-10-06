package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.utils.PreferenceHelpers
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB yerelleştirilmiş (örn. tr-TR) ve İngilizce (en-US) başlıkların ikisi de
 * Japonca/Çince (CJK) döndüğü durumlar için son çare Latin başlık çözümleyici.
 *
 * TMDB `alternative_titles` endpoint'i genellikle romaji/İngilizce alternatifler
 * içerir (örn. "Souryo to Majiwaru Shikiyoku no Yoru ni..."). Böylece Türkçe
 * seçiliyken Türkçe → İngilizce → romaji zinciri korunur ve CJK başlıklar
 * kullanıcı Japonca/Çince seçmedikçe gösterilmez.
 *
 * Sonuçlar bellek içinde önbelleğe alınır; yalnızca CJK kalan öğeler için
 * çağrılmalıdır (liste başına en fazla birkaç istek üretir).
 */
internal object TmdbTitleFallback {

    private const val TAG = "TmdbTitleFallback"
    private const val MAX_CACHE_ENTRIES = 500

    private val cache = ConcurrentHashMap<String, String>()
    private val missCache: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet())

    /**
     * Verilen TMDB öğesi için ilk Latin-okunabilir alternatif başlığı döndürür.
     * Bulunamazsa null döner (çağıran mevcut başlığı korumalıdır).
     */
    suspend fun fetchLatinTitle(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        executeGet: suspend (String) -> String?
    ): String? {
        if (tmdbId <= 0) return null
        val key = (if (isMovie) "m:" else "t:") + tmdbId
        cache[key]?.let { return it }
        if (missCache.contains(key)) return null

        return try {
            val typePath = if (isMovie) "movie" else "tv"
            val url = "https://api.themoviedb.org/3/$typePath/$tmdbId/alternative_titles?api_key=$apiKey"
            val responseText = executeGet(url) ?: run {
                rememberMiss(key)
                return null
            }
            val latin = parseLatinAlternative(JSONObject(responseText), isMovie)
            if (latin != null) {
                rememberHit(key, latin)
            } else {
                rememberMiss(key)
            }
            latin
        } catch (e: Exception) {
            Log.w(TAG, "alternative_titles fetch failed for $key: ${e.message}")
            rememberMiss(key)
            null
        }
    }

    private fun parseLatinAlternative(root: JSONObject, isMovie: Boolean): String? {
        return if (isMovie) {
            // {"titles":[{"iso_3166_1":"US","title":"...","type":""}, ...]}
            val titles = root.optJSONArray("titles") ?: return null
            val candidates = mutableListOf<Pair<String, String>>()
            for (i in 0 until titles.length()) {
                val obj = titles.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                if (title.isBlank()) continue
                candidates.add(obj.optString("iso_3166_1", "") to title)
            }
            // Önce US/GB alternatifleri, sonra ilk Latin başlık
            candidates.firstOrNull { (iso, title) ->
                (iso.equals("US", ignoreCase = true) || iso.equals("GB", ignoreCase = true)) &&
                    PreferenceHelpers.isLatinReadable(title)
            }?.second
                ?: candidates.firstOrNull { (_, title) ->
                    PreferenceHelpers.isLatinReadable(title)
                }?.second
        } else {
            // TV: {"results":[{"name":"...","type":""}, ...]} (ülke kodu yok)
            val results = root.optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (PreferenceHelpers.isLatinReadable(name)) return name
            }
            null
        }
    }

    private fun rememberHit(key: String, value: String) {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            // Basit tahliye: boyut sınırına ulaşınca en eski girdilerin bir kısmını temizle
            val iterator = cache.keys.iterator()
            var removed = 0
            while (iterator.hasNext() && removed < MAX_CACHE_ENTRIES / 4) {
                iterator.next()
                iterator.remove()
                removed++
            }
        }
        cache[key] = value
    }

    private fun rememberMiss(key: String) {
        if (missCache.size >= MAX_CACHE_ENTRIES) {
            val iterator = missCache.iterator()
            var removed = 0
            while (iterator.hasNext() && removed < MAX_CACHE_ENTRIES / 4) {
                iterator.next()
                iterator.remove()
                removed++
            }
        }
        missCache.add(key)
    }
}
