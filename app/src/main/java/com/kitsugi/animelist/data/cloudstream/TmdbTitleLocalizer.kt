package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.KitsugiApiBase
import com.kitsugi.animelist.data.remote.TmdbApiClient
import com.kitsugi.animelist.model.MediaType

/**
 * v2.4.250 strateji denetiminin bulduğu boşluğu kapatır: site SADECE Türkçe başlık
 * taşıyorsa ("Örümcek Adam Eve Dönüş Yok") detay sayfasının gönderdiği EN/Romaji/Native
 * başlıklarla EŞLEŞME YOK kaçınılmazdı. TMDB'nin `tr-TR` yerelleştirilmiş başlığı sorgu
 * varyantlarına ve eşleştirici adaylarına eklenir.
 *
 * Maliyet kontrolü: sonuçlar oturum-önbelleklidir — 164 eklenti tek TMDB isteğini
 * paylaşır. Bulunamayan/hatalı sonuçlar da negatif önbelleğe alınır (oturum kapsamlı);
 * TR başlık bir "bonus varyant"tır, dalgalı ağda bir oturum eksik kalması kabul edilir,
 * 164× yeniden deneme fırtınası kabul edilmez.
 */
internal object TmdbTitleLocalizer {
    private const val TAG = "TmdbTitleLocalizer"
    private const val NONE = "\u0000none\u0000"
    private val trTitleCache = BoundedCache<String, String>("tmdb.trTitle", 500)

    /**
     * @param tmdbId çözülmüş TMDB kimliği (yoksa null döner, ağa çıkılmaz)
     * @return tr-TR başlık veya null (yok/hata)
     */
    suspend fun getTurkishTitle(tmdbId: Int?, mediaType: MediaType?, isMovie: Boolean): String? {
        if (tmdbId == null || tmdbId <= 0) return null
        if (mediaType == MediaType.Manga) return null
        val path = when {
            mediaType == MediaType.Movie -> "movie"
            mediaType == null && isMovie -> "movie"
            else -> "tv" // TvShow + Anime (TMDB'de animeler /tv altındadır)
        }
        val key = "$path:$tmdbId"
        trTitleCache[key]?.let { return it.takeIf { cached -> cached != NONE } }
        val title = fetchTrTitle(path, tmdbId)
        trTitleCache[key] = title ?: NONE
        return title
    }

    private fun fetchTrTitle(path: String, tmdbId: Int): String? {
        return try {
            val apiKey = TmdbApiClient.getActiveApiKey()
            if (apiKey.isBlank()) return null
            val url = java.net.URL("https://api.themoviedb.org/3/$path/$tmdbId?api_key=$apiKey&language=tr-TR")
            val body = KitsugiApiBase.executeGetRequest(url) ?: return null
            val json = org.json.JSONObject(body)
            val raw = if (path == "movie") json.optString("title") else json.optString("name")
            raw.trim().takeIf { it.length >= 2 }
        } catch (e: Exception) {
            Log.w(TAG, "tr-TR başlık alınamadı ($path/$tmdbId): ${e.message}")
            null
        }
    }
}
