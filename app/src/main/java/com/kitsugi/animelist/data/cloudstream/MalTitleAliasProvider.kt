package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.JikanGateway
import com.kitsugi.animelist.data.remote.JikanResult
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Eklenti (CloudStream) aramasında kullanılacak KANONİK MAL başlık diğer adları.
 *
 * NEDEN GEREKLİ: Akış ekranına gelen başlık, kaynağa göre değişiyor. Örneğin AniList
 * kaydından açıldığında başlık İngilizce ("Attack on Titan"), MAL/Jikan kaydından
 * açıldığında Romaji ("Shingeki no Kyojin") olabiliyor. Türk eklentileri (Anizm,
 * TrAnimeIzle, AnimeciX, ...) sitelerinde çoğu zaman Romaji + Türkçe başlık taşır;
 * ellerinde yalnızca İngilizce başlık olan eklenti "isimle bulamıyor" durumuna düşüyordu.
 *
 * ÇÖZÜM: MAL kimliği varsa bir kez Jikan'dan tüm başlık diğer adları (Default /
 * English / Japanese / Synonym / Turkish ...) okunur ve arama varyantlarına eklenir.
 * Böylece kaynak ne olursa olsun eklenti aynı başlık evreniyle arar.
 *
 * Maliyet: Sonuçlar oturum-önbelleğidir ([BoundedCache]); 164 eklenti tek Jikan
 * isteğini paylaşır. Eşzamanlı eklenti çağrıları [fetchMutex] ile tek isteğe indirilir.
 * Boş sonuç da önbelleğe yazılır (negatif önbellek) — dalgalı ağda tekrar tekrar
 * denenip Jikan kotası tüketilmez.
 */
internal object MalTitleAliasProvider {
    private const val TAG = "MalTitleAliasProvider"

    /** Gerçek MAL kimlik aralığı; offset'li stable kimlikler (100M+/300M+/500M+) dışarıda. */
    private const val MAL_ID_LIMIT = 100_000_000

    /** Aşırı uzun sorgu listesi eklenti aramasını yavaşlatmasın. */
    private const val MAX_ALIASES = 8

    /**
     * Zenginleştirme bir "bonus"tur: Jikan yavaşsa eklenti araması bekletilmez.
     * Bu süre aşılırsa sonuç önbelleğe YAZILMAZ (geçici yavaşlık kalıcı boşluğa dönüşmesin).
     */
    private const val ALIAS_TIMEOUT_MS = 4_000L

    private val aliasCache = BoundedCache<Int, List<String>>("cs.malTitleAliases", 400)
    private val fetchMutex = Mutex()

    /**
     * @param malId çözülmüş MAL kimliği (yoksa ağa çıkılmaz)
     * @return boş olmayan, tekrarsız başlık diğer adları; bilinmiyorsa boş liste
     */
    suspend fun aliasesFor(malId: Int?, mediaType: MediaType?): List<String> {
        if (malId == null || malId <= 0 || malId >= MAL_ID_LIMIT) return emptyList()
        if (mediaType == MediaType.Manga) return emptyList()
        aliasCache[malId]?.let { return it }
        return fetchMutex.withLock {
            aliasCache[malId] ?: run {
                val titles = withTimeoutOrNull(ALIAS_TIMEOUT_MS) {
                    try {
                        fetchTitles(malId)
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (e: Exception) {
                        Log.w(TAG, "MAL $malId başlık diğer adları alınamadı: ${e.message}")
                        null
                    }
                }
                if (titles != null) aliasCache[malId] = titles
                titles ?: emptyList()
            }
        }
    }

    private suspend fun fetchTitles(malId: Int): List<String> {
        val url = "https://api.jikan.moe/v4/anime/$malId"
        val result = JikanGateway.fetch(url, JikanGateway.Priority.BACKGROUND, maxRetries = 1)
        if (result !is JikanResult.Ok) {
            Log.d(TAG, "MAL $malId başlıkları alınamadı ($result)")
            return emptyList()
        }
        return parseTitles(result.body)
    }

    /**
     * Jikan `data` nesnesinden başlık diğer adlarını çıkarır. `titles` dizisi varsa
     * tüm türler (Default/English/Japanese/Synonym/Turkish...) kullanılır; yoksa
     * eski alanlar (`title`, `title_english`, `title_japanese`) okunur.
     *
     * Saf fonksiyon — birim testleriyle doğrulanır.
     */
    internal fun parseTitles(body: String): List<String> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val data = root.optJSONObject("data") ?: return emptyList()

        val collected = LinkedHashSet<String>()
        val titles = data.optJSONArray("titles")
        if (titles != null) {
            for (i in 0 until titles.length()) {
                val item = titles.optJSONObject(i) ?: continue
                item.optString("title").takeIf { it.isNotBlank() }?.let { collected.add(it) }
            }
        }
        listOf("title", "title_english", "title_japanese").forEach { key ->
            data.optString(key).takeIf { it.isNotBlank() }?.let { collected.add(it) }
        }

        return collected
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinctBy { CsTitleMatcher.normalizeTitleForMatch(it) }
            .take(MAX_ALIASES)
    }

    /** Yeni içerik açıldığında çağrılabilir; önbellek zaten sınırlı. */
    fun clear() {
        aliasCache.clear()
    }
}
