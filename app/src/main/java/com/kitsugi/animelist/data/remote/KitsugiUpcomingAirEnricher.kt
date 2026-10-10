package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.NextAiringFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.util.Locale

/** Başlık normalizasyonu: büyük/küçük harf ve noktalama farklarını yok sayar. */
internal fun normalizeAiringTitle(title: String?): String? =
    title?.lowercase(Locale.ROOT)?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }

/**
 * Liste sayfalarında (tam ekran kategori sayfaları, raf kartları) yaklaşan yayın
 * tarihini kart üzerinde göstermek için `nextAiringEpisode` alanını tamamlayan
 * yardımcı katman.
 *
 * Sorun: TMDB `tv/on_the_air` ve Shikimori `airing` listeleri öğenin kendi yayın
 * TAKVİMİNİ taşımaz (yalnızca ilk yayın tarihi / yayın durumu) → kullanıcı detay
 * sayfasına girmeden "ne kadar kaldı?" göremiyordu. Bu zenginleştirici iki ucuz
 * kaynakla boşluğu kapatır:
 *
 *  1. **AniList `airingSchedules`** (tek istek, 14 günlük pencere, 10 dk önbellek):
 *     Shikimori / Kitsu / Bangumi / MAL / AniList öğeleri MAL kimliği veya
 *     normalize başlık eşleşmesiyle bölüm numarası + kesin yayın epoch'u kazanır.
 *  2. **TMDB `next_episode_to_air`** (öğe başına tek istek, 6 saat önbellek,
 *     6 eşzamanlılık sınırı): hâlihazırda yayında olan TMDB dizilerinin gelecek
 *     bölüm tarihi çözülür. Filmler vizyon tarihini zaten listede taşır.
 *
 * Eşleşme bulunamazsa öğe DEĞİŞMEDEN döner (chip gösterilmez) — hiçbir liste
 * akışı bu zenginleştirme yüzünden hata vermez veya yavaşlamaz (sonuçlar
 * süreç-içi önbellekten gelir).
 */
object KitsugiUpcomingAirEnricher {

    data class NextAir(val episode: Int, val epoch: Long)

    private const val ANILIST_WINDOW_DAYS = 14L
    private const val ANILIST_CACHE_TTL_MS = 10 * 60 * 1000L
    private const val TMDB_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val TMDB_CONCURRENCY = 6

    /** normalizasyon → (bölüm, epoch). MAL kimlikleri "mal:<id>" anahtarıyla. */
    private var anilistCache: Map<String, NextAir> = emptyMap()
    private var anilistCacheAt = 0L

    /** tmdbId → gelecek bölüm (null = bilinmiyor / yok, negatif önbellek). */
    private var tmdbCache: Map<Int, NextAir?> = emptyMap()
    private var tmdbCacheAt = 0L

    /** Gelecek yayın tarihi taşımayan (veya tarihi geçmiş) öğeler mi? */
    private fun needsEnrichment(item: JikanSearchResult): Boolean {
        if (item.type == MediaType.Manga) return false
        val epoch = NextAiringFormat.parse(item.nextAiringEpisode).epoch ?: return true
        return epoch <= System.currentTimeMillis() / 1000L
    }

    /**
     * Liste öğelerinin `nextAiringEpisode` alanını ("episode|epoch") doldurur.
     * Yalnızca eksik öğeler için ağ çağrısı yapılır; kalanlar değişmeden döner.
     */
    suspend fun enrich(items: List<JikanSearchResult>): List<JikanSearchResult> {
        if (items.isEmpty()) return items
        val needed = items.filter { needsEnrichment(it) }
        if (needed.isEmpty()) return items

        val tmdbTargets = needed
            .filter { it.source.equals("tmdb", true) && it.type == MediaType.TvShow }
            .map { it.tmdbId ?: it.malId }
            .filter { it > 0 }
            .distinct()
        val needsAniList = needed.any { !it.source.equals("tmdb", true) }

        val anilistMap = if (needsAniList) anilistScheduleMap() else emptyMap()
        val tmdbMap = if (tmdbTargets.isNotEmpty()) tmdbNextAirMap(tmdbTargets) else emptyMap()

        return items.map { item ->
            if (!needsEnrichment(item)) return@map item
            val next: NextAir? = if (item.source.equals("tmdb", true)) {
                if (item.type == MediaType.TvShow) tmdbMap[item.tmdbId ?: item.malId] else null
            } else {
                lookupAniList(anilistMap, item)
            }
            if (next == null || next.epoch <= System.currentTimeMillis() / 1000L) {
                item
            } else {
                item.copy(nextAiringEpisode = "${next.episode}|${next.epoch}")
            }
        }
    }

    private fun lookupAniList(map: Map<String, NextAir>, item: JikanSearchResult): NextAir? {
        if (map.isEmpty()) return null
        val malId = when {
            item.source.equals("mal", true) || item.source.equals("jikan", true) ->
                item.malId.takeIf { it > 0 }
            else -> item.realMalId?.takeIf { it > 0 }
        }
        malId?.let { map["mal:$it"] }?.let { return it }
        normalizeAiringTitle(item.title)?.let { map["t:$it"] }?.let { return it }
        normalizeAiringTitle(titleRomajiOrEnglish(item))?.let { map["t:$it"] }?.let { return it }
        normalizeAiringTitle(item.titleJapanese)?.let { map["t:$it"] }?.let { return it }
        return null
    }

    private fun titleRomajiOrEnglish(item: JikanSearchResult): String? =
        item.titleRomaji ?: item.titleEnglish

    /**
     * AniList 14 günlük yayın penceresini çekip MAL kimliği + normalize başlık
     * anahtarlarıyla haritalar. Süreç-içi 10 dakikalık önbellek kullanır.
     */
    private suspend fun anilistScheduleMap(): Map<String, NextAir> {
        val now = System.currentTimeMillis()
        if (anilistCache.isNotEmpty() && now - anilistCacheAt <= ANILIST_CACHE_TTL_MS) {
            return anilistCache
        }
        val nowSec = now / 1000L
        val entries = runCatching {
            KitsugiAiringCalendarClient()
                .fetchAiringWindow(nowSec, nowSec + ANILIST_WINDOW_DAYS * 24 * 3600L)
        }.getOrDefault(emptyList())

        val map = mutableMapOf<String, NextAir>()
        for (entry in entries.sortedBy { it.airingAt }) {
            val air = NextAir(entry.episode, entry.airingAt)
            entry.malId?.takeIf { it > 0 }?.let { id ->
                val key = "mal:$id"
                if (key !in map) map[key] = air
            }
            for (title in listOf(entry.title, entry.titleEnglish, entry.titleNative)) {
                normalizeAiringTitle(title)?.let { key ->
                    if (key !in map) map[key] = air
                }
            }
        }
        anilistCache = map
        anilistCacheAt = now
        return map
    }

    /**
     * TMDB `next_episode_to_air` toplu çözümü: önbellekte olmayan kimlikler
     * sınırlı eşzamanlılıkla çekilir, negatif sonuçlar da önbelleğe yazılır.
     */
    private suspend fun tmdbNextAirMap(ids: List<Int>): Map<Int, NextAir?> = coroutineScope {
        val now = System.currentTimeMillis()
        if (now - tmdbCacheAt > TMDB_CACHE_TTL_MS) {
            tmdbCache = emptyMap()
            tmdbCacheAt = now
        }
        val missing = ids.filter { it !in tmdbCache }
        if (missing.isNotEmpty()) {
            val semaphore = Semaphore(TMDB_CONCURRENCY)
            val fetched = missing.map { id ->
                async { semaphore.withPermit { id to fetchTmdbNextAir(id) } }
            }.awaitAll()
            tmdbCache = tmdbCache + fetched.toMap()
        }
        ids.associateWith { tmdbCache[it] }
    }

    /** Tek TMDB dizi detayı: gelecek bölüm numarası + yayın tarihi. */
    private suspend fun fetchTmdbNextAir(tmdbId: Int): NextAir? = runCatching {
        // executeGetRequest bloklayıcıdır → ana thread'i kilitlememek için IO.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apiKey = TmdbApiClient.getActiveApiKey()
            val language = TmdbApiClient.getActiveLanguage()
            val url = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$apiKey&language=$language"
            val text = KitsugiApiBase.executeGetRequest(java.net.URL(url))
            val next = text?.let { JSONObject(it).optJSONObject("next_episode_to_air") }
            val airDate = next?.optString("air_date", "") ?: ""
            val epoch = NextAiringFormat.isoDateToEpoch(airDate)
            when {
                epoch == null -> null
                epoch <= System.currentTimeMillis() / 1000L -> null
                else -> NextAir(next?.optInt("episode_number", -1)?.takeIf { it > 0 } ?: -1, epoch)
            }
        }
    }.getOrNull()
}
