package com.kitsugi.animelist.core.subtitle

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.identity.MediaIdentity
import com.kitsugi.animelist.core.identity.MediaIdentityRegistry
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.core.player.model.Subtitle
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.repository.SubtitleRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Ortak Altyazı Merkezi (Ortak Omurga – Faz 2/3).
 *
 * Tek giriş noktası: hangi kaynaktan oynatılırsa oynatılsın altyazı kümesi aynı şekilde
 * üretilir.
 *
 *  1. [SubtitleMemoryStore] → daha önce bulunmuş altyazılar ANINDA döner (ağ beklemez).
 *     Bu, "A kaynağında vardı, B kaynağında yok" şikâyetinin doğrudan çözümüdür.
 *  2. Kimlik zenginleştirme → giriş noktası ID taşımıyorsa (CS eklentisi kataloğu, arama,
 *     geçmiş) [MediaIdentityRegistry] + [KitsugiIdResolver] ile ID'ler kurtarılır.
 *     Böylece IMDb/Kitsu/AniList ile sorgulanan eklentiler her girişte çalışır.
 *  3. Ağ sorguları → IMDb/Kitsu/AniList + TÜM başlık varyantları (ana, İngilizce, romaji,
 *     native, eş anlamlılar) paralel sorgulanır. Eskiden yalnızca ana başlık kullanılıyordu;
 *     İngilizce indeksleyen eklentiler bu yüzden boş dönüyordu.
 *  4. Bulunan her şey belleğe yazılır → sonraki açılışta ve diğer kaynaklarda hazır.
 */
class SubtitleHub(private val context: Context) {

    companion object {
        private const val TAG = "SubtitleHub"
        /** Ağa çıkacak en fazla sorgu sayısı (eklenti koruması + hız). */
        private const val MAX_REMOTE_QUERIES = 7
        /** Bellek bu süre içinde doldurulduysa ağ turu atlanır (kaynak değişimi hızı). */
        private const val FRESH_WINDOW_MS = 2L * 60 * 60 * 1000
    }

    private val repository = SubtitleRepositoryImpl(context)

    init {
        SubtitleMemoryStore.initialize(context)
        MediaIdentityRegistry.initialize(context)
    }

    data class Result(
        val identity: MediaIdentity,
        /** Bellekten gelen (anında) altyazılar. */
        val cached: List<Subtitle>,
        /** Ağdan yeni gelen altyazılar. */
        val remote: List<Subtitle>,
        /** Kullanılan sorgular — tanılama için. */
        val queries: List<String>
    ) {
        fun all(): List<Subtitle> = (cached + remote).distinctBy { it.url }
    }

    /** Ağ beklemeden, yalnızca bellekten gelen altyazılar. */
    fun cachedOnly(identity: MediaIdentity): List<Subtitle> {
        SubtitleMemoryStore.initialize(context)
        return SubtitleMemoryStore.recall(identity.subtitleKeyCandidates)
    }

    /**
     * Kimliği zenginleştirir ve verilen bölüm için tüm altyazı kaynaklarını toplar.
     *
     * @param videoUrl yalnızca OpenSubtitles hash'i için ipucudur; null olması akışı durdurmaz.
     */
    suspend fun load(
        identity: MediaIdentity,
        videoUrl: String? = null,
        videoHeaders: Map<String, String>? = null,
        filename: String? = null,
        /** true → bellek taze olsa bile ağa çık (kullanıcı "altyazıları yenile" dedi). */
        forceRefresh: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        val enriched = enrichIdentity(identity)
        val keys = enriched.subtitleKeyCandidates
        val cached = SubtitleMemoryStore.recall(keys)

        // HIZ: kaynak değişiminde aynı bölüm için ağ turunu tekrarlama.
        // Bellek son [FRESH_WINDOW_MS] içinde doldurulduysa doğrudan bellek döner.
        if (!forceRefresh && cached.isNotEmpty() && SubtitleMemoryStore.isFresh(keys, FRESH_WINDOW_MS)) {
            Log.d(TAG, "Bellek taze (${cached.size} altyazı) — ağ sorgusu atlandı")
            return@withContext Result(enriched, cached, emptyList(), emptyList())
        }

        val queries = buildQueries(enriched)

        if (queries.isEmpty()) {
            Log.w(TAG, "Sorgu üretilemedi (kimlik ve başlık yok) — yalnızca bellek kullanılacak")
            return@withContext Result(enriched, cached, emptyList(), emptyList())
        }

        val remote = coroutineScope {
            queries.map { (type, id) ->
                async {
                    try {
                        repository.getSubtitles(
                            type = type,
                            id = id,
                            videoUrl = videoUrl,
                            videoHeaders = videoHeaders,
                            filename = filename,
                            aniListId = enriched.aniListId,
                            animeTitle = enriched.title,
                            episode = enriched.effectiveEpisode
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Altyazı sorgusu başarısız ($type/$id): ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }

        if (remote.isNotEmpty()) {
            SubtitleMemoryStore.remember(enriched.subtitleKeyCandidates, remote, providerBound = false)
        }

        Log.d(
            TAG,
            "Altyazı toplandı: bellek=${cached.size}, ağ=${remote.size}, " +
                "sorgu=${queries.size}, içerik='${enriched.title}' S${enriched.effectiveSeason}E${enriched.effectiveEpisode}, " +
                "kimlik=mal:${enriched.malId} anilist:${enriched.aniListId} imdb:${enriched.imdbId} kitsu:${enriched.kitsuId}"
        )
        Result(enriched, cached, remote, queries.map { "${it.first}/${it.second}" })
    }

    /**
     * Oynatılan kaynağın içinden çıkarılan (gömülü) altyazıları ortak belleğe yazar.
     * Böylece A kaynağında bulunan gömülü altyazı, B kaynağında da listede görünür.
     */
    fun rememberProviderSubtitles(
        identity: MediaIdentity,
        addonName: String,
        subtitles: List<SubtitleInput>
    ) {
        if (subtitles.isEmpty()) return
        SubtitleMemoryStore.initialize(context)
        val mapped = subtitles.map { input ->
            Subtitle(
                id = input.url,
                url = input.url,
                lang = input.lang,
                addonName = if (input.isExternal) input.name else "$addonName (kaynak)",
                format = input.url.substringAfterLast('.', "").takeIf { it.length in 2..5 }
            )
        }
        SubtitleMemoryStore.remember(identity.subtitleKeyCandidates, mapped, providerBound = true)
    }

    /** Ortak belleği temizler (ayarlar → "altyazı belleğini temizle"). */
    fun clearMemory() {
        SubtitleMemoryStore.clear()
    }

    // ── Kimlik zenginleştirme ─────────────────────────────────────────────────

    /**
     * Giriş noktası ID taşımıyorsa başlıkla kurtarmayı dener, ardından eksik kimlikleri
     * çözümleyiciyle tamamlar. Sonuç kayıt defterine yazılır (diğer ekranlar da kullanır).
     */
    suspend fun enrichIdentity(identity: MediaIdentity): MediaIdentity {
        var current = identity

        if (current.malId == null && current.aniListId == null && current.imdbId == null && current.kitsuId == null) {
            val remembered = MediaIdentityRegistry.lookupByTitle(current.title, current.startYear)
                ?: current.alternativeTitles.firstNotNullOfOrNull {
                    MediaIdentityRegistry.lookupByTitle(it, current.startYear)
                }
            if (remembered != null) {
                current = current.withResolved(remembered)
                Log.d(TAG, "Kimlik kayıt defterinden kurtarıldı: mal=${current.malId} anilist=${current.aniListId} imdb=${current.imdbId}")
            }
        }

        val needsResolution = current.imdbId == null || current.kitsuId == null
        if (needsResolution) {
            try {
                val resolved = KitsugiIdResolver.resolveIds(
                    malId = current.malId,
                    aniListId = current.aniListId,
                    tmdbId = current.tmdbId,
                    mediaType = current.mediaType,
                    kitsuId = current.kitsuId,
                    bangumiId = current.bangumiId,
                    wikidataImdbFallback = true
                )
                current = current.withResolved(resolved)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Kimlik çözümlemesi başarısız: ${e.message}")
            }
        }

        // Hiçbir kimlik yoksa son çare: başlıktan TMDB araması.
        // (Eskiden bu yalnızca oynatıcının içinde vardı; artık tüm giriş noktaları için ortak.)
        if (current.imdbId == null && current.malId == null && current.aniListId == null &&
            current.kitsuId == null && current.tmdbId == null && current.title.isNotBlank()
        ) {
            try {
                val candidates = com.kitsugi.animelist.data.remote.TmdbApiClient().search(current.title)
                val best = candidates.firstOrNull { result ->
                    if (current.isMovie) result.type == com.kitsugi.animelist.model.MediaType.Movie
                    else result.type == com.kitsugi.animelist.model.MediaType.TvShow
                } ?: candidates.firstOrNull()
                val bestTmdbId = best?.tmdbId?.takeIf { it > 0 }
                if (bestTmdbId != null) {
                    val resolved = KitsugiIdResolver.resolveIds(
                        malId = null,
                        aniListId = null,
                        tmdbId = bestTmdbId,
                        mediaType = best.type,
                        wikidataImdbFallback = true
                    )
                    current = current.copy(tmdbId = bestTmdbId).withResolved(resolved)
                    Log.d(TAG, "TMDB başlık araması kimlik kazandırdı: tmdb=$bestTmdbId imdb=${current.imdbId}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "TMDB başlık araması başarısız: ${e.message}")
            }
        }

        if (current.imdbId != null || current.malId != null || current.aniListId != null || current.kitsuId != null) {
            MediaIdentityRegistry.remember(current)
        }
        return current
    }

    // ── Sorgu üretimi ─────────────────────────────────────────────────────────

    /**
     * Altyazı eklentileri için (type, id) çiftleri.
     *
     * Öncelik sırası: IMDb → Kitsu → AniList → MAL → başlık varyantları.
     * Toplam [MAX_REMOTE_QUERIES] ile sınırlanır.
     */
    internal fun buildQueries(identity: MediaIdentity): List<Pair<String, String>> {
        val s = identity.effectiveSeason
        val e = identity.effectiveEpisode
        val isMovie = identity.isMovie
        val out = LinkedHashSet<Pair<String, String>>()

        fun add(type: String, id: String) {
            if (id.isBlank()) return
            out.add(type to id)
        }

        val imdb = identity.imdbId?.trim()?.takeIf { Regex("tt[0-9]{5,12}").matches(it) }
        if (imdb != null) {
            if (isMovie) add("movie", imdb) else {
                add("series", "$imdb:$s:$e")
                add("movie", imdb)
            }
        }

        identity.kitsuId?.takeIf { it > 0 }?.let { kitsu ->
            if (isMovie) add("movie", "kitsu:$kitsu") else add("series", "kitsu:$kitsu:$e")
        }

        identity.aniListId?.takeIf { it > 0 }?.let { anilist ->
            if (isMovie) add("movie", "anilist:$anilist") else add("series", "anilist:$anilist:$e")
        }

        identity.malId?.takeIf { it > 0 }?.let { mal ->
            if (isMovie) add("movie", "mal:$mal") else add("series", "mal:$mal:$e")
        }

        // Başlık varyantları: ana başlık + İngilizce/romaji/native/eş anlamlılar
        for (title in identity.queryTitles) {
            if (isMovie) {
                add("movie", title)
            } else {
                add("series", "$title:$s:$e")
            }
        }

        return out.take(MAX_REMOTE_QUERIES)
    }
}
