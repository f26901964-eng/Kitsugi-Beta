package com.kitsugi.animelist.core.player

import android.util.Log
import com.kitsugi.animelist.data.repository.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * v2.4.341 — Sonraki bölüm kaynakları için ARKA PLAN prefetch + bellek önbelleği.
 *
 * Kullanıcı talebi (2026-10-23): "Bölüm listesi diğer bölümleri listeledikten sonra,
 * ben video izlerken uygulama diğer bölümlerin verisini sırayla hazır tutmalı;
 * özellikle İZLEDİĞİM KAYNAK üzerinden. Böylece sonraki bölüme geçtiğimde veriler
 * çoktan önbelleklenmiş olur ve geçiş anında olur."
 *
 * Çalışma prensibi:
 *  1) Bölüm listesi geldiğinde [start] çağrılır ve sıradaki [prefetchDepth] bölüm için
 *     SIRALI (aynı anda tek istek) bir arka plan işi planlanır.
 *  2) Sorgular yalnızca kullanıcının o an izlediği kaynak ([preferredSource]) üzerinden
 *     yapılır — hızlı, eksiksiz ve diğer eklentilere gereksiz yük bindirmez.
 *  3) Sonuçlar TTL'li bellek içi önbellekte tutulur. [peekStreams] sayesinde
 *     `playEpisode` "Sonraki Bölüm Kaynakları Aranıyor..." fazını TÜMDEN atlar.
 *  4) Bölümün sonuna gelindiğinde ViewModel akış URL'ini de önceden çözümleyip
 *     ([putResolved]) geçişi beklemesız hale getirebilir.
 *
 * Önbellek yalnızca meta veri (List<StreamSource> / çözümlenmiş URL) tutar; video
 * baytı indirilmez, dolayısıyla bellek maliyeti ihmal edilebilir düzeydedir.
 * İşler [scope] (viewModelScope) üzerinde koşar → oyuncu kapanınca kendiliğinden iptal.
 */
class EpisodeStreamPrefetcher(
    private val scope: CoroutineScope,
    private val prefetchDepth: Int = DEFAULT_PREFETCH_DEPTH,
    private val streamTtlMs: Long = DEFAULT_STREAM_TTL_MS,
    private val resolvedTtlMs: Long = DEFAULT_RESOLVED_TTL_MS
) {

    /** Bir bölüm için önbelleklenmiş kaynak listesi. */
    data class CachedStreams(
        val streams: List<StreamSource>,
        val cachedAt: Long,
        val sourceName: String?
    )

    /** Bir bölümün tercih edilen kaynağı için önceden çözümlenmiş oynatma URL'si. */
    data class CachedResolved(
        val streamKey: String,
        val url: String,
        val audioUrl: String?,
        val headers: Map<String, String>,
        val subtitles: List<SubtitleInput>,
        val cachedAt: Long
    )

    private val streamCache = LinkedHashMap<Int, CachedStreams>()
    private val resolvedCache = HashMap<Int, CachedResolved>()
    private var job: Job? = null

    /** Başlatır. Önceki işi iptal eder, taze ve sıralı bir kuyruk kurar. */
    fun start(
        currentEpisode: Int,
        upcomingEpisodes: List<Int>,
        preferredSource: String?,
        fetch: suspend (episode: Int, onlySourceName: String?) -> List<StreamSource>
    ) {
        job?.cancel()
        job = null
        val targets = upcomingEpisodes
            .distinct()
            .sorted()
            .filter { it > currentEpisode }
            .take(prefetchDepth)
        if (targets.isEmpty()) return

        job = scope.launch(Dispatchers.IO) {
            for (ep in targets) {
                ensureActive()
                val cached = streamCache[ep]
                val fresh = cached != null &&
                    (System.currentTimeMillis() - cached.cachedAt) < streamTtlMs &&
                    (preferredSource == null || cached.sourceName.equals(preferredSource, ignoreCase = true))
                if (fresh) {
                    Log.d(TAG, "E$ep zaten önbellekte (taze) — atlanıyor")
                    continue
                }
                val streams = try {
                    fetch(ep, preferredSource)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    Log.w(TAG, "Prefetch başarısız E$ep: ${t.message}")
                    null
                }
                if (streams != null && streams.isNotEmpty()) {
                    streamCache[ep] = CachedStreams(
                        streams = streams,
                        cachedAt = System.currentTimeMillis(),
                        sourceName = preferredSource
                    )
                    Log.d(TAG, "E$ep önbelleklendi (${streams.size} kaynak, kaynak=${preferredSource ?: "-"})")
                }
                // Sıralı istekler arasında kısa nefes: kaynağı/aynı eklentiyi yormamak için.
                delay(INTER_REQUEST_DELAY_MS)
            }
            Log.d(TAG, "Prefetch kuyruğu tamamlandı (${targets.size} bölüm)")
        }
    }

    /** Taze ise bölümün önbelleklenmiş kaynak listesini döndürür, değilse null. */
    fun peekStreams(episode: Int): List<StreamSource>? {
        val entry = streamCache[episode] ?: return null
        if (System.currentTimeMillis() - entry.cachedAt >= streamTtlMs) {
            streamCache.remove(episode)
            return null
        }
        return entry.streams
    }

    /** Elle çekilen sonuçları da önbelleğe yazar (yeniden denemeler anında olsun). */
    fun putStreams(episode: Int, streams: List<StreamSource>, sourceName: String?) {
        if (streams.isEmpty()) return
        streamCache[episode] = CachedStreams(
            streams = streams,
            cachedAt = System.currentTimeMillis(),
            sourceName = sourceName
        )
    }

    /** Taze ise önceden çözümlenmiş URL'yi döndürür. */
    fun peekResolved(episode: Int): CachedResolved? {
        val entry = resolvedCache[episode] ?: return null
        if (System.currentTimeMillis() - entry.cachedAt >= resolvedTtlMs) {
            resolvedCache.remove(episode)
            return null
        }
        return entry
    }

    fun putResolved(episode: Int, resolved: CachedResolved) {
        resolvedCache[episode] = resolved
    }

    /** Tüketilen bölümün girdilerini temizler. */
    fun invalidateEpisode(episode: Int) {
        streamCache.remove(episode)
        resolvedCache.remove(episode)
    }

    /** Kaynak/seri değişiminde tüm önbelleği ve koşan işi sıfırlar. */
    fun invalidateAll() {
        job?.cancel()
        job = null
        streamCache.clear()
        resolvedCache.clear()
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val TAG = "EpisodeStreamPrefetcher"

        /** Sıradaki kaç bölümün kaynağı arka planda hazır tutulsun. */
        const val DEFAULT_PREFETCH_DEPTH = 2

        /** Kaynak listesi önbellek TTL'i: 30 dk. */
        const val DEFAULT_STREAM_TTL_MS = 30 * 60_000L

        /** Çözümlenmiş URL TTL'i: 10 dk (token/link bayatlamasına karşı temkinli). */
        const val DEFAULT_RESOLVED_TTL_MS = 10 * 60_000L

        /** Sıralı prefetch istekleri arasına konan kısa gecikme. */
        const val INTER_REQUEST_DELAY_MS = 400L

        /** Bir kaynağın kimliği: infoHash > url > ad/başlık birleşimi. */
        fun streamKey(source: StreamSource): String =
            source.infoHash?.lowercase()
                ?: source.url?.lowercase()
                ?: "${source.addonName}|${source.name}|${source.title}".lowercase()
    }
}
