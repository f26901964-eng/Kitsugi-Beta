package com.kitsugi.animelist.data.cloudstream.embed

import com.kitsugi.animelist.core.memory.BoundedCache

import android.util.Log
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore

/**
 * FP-41 — WebViewMediaSniffer
 *
 * Son çare çözümleyici. Bazı Türkçe CDN'ler medya URL'sini **yalnızca JavaScript çalıştıktan
 * sonra** üretir (şifreli oynatıcılar, obfuscated `jwplayer` kurulumları, token'lı HLS).
 * Böyle bir sayfada HTML taraması (EmbedMediaScanner) hiçbir şey bulamaz.
 *
 * Bu sınıf, oynatıcı sayfasını gerçek bir WebView'de açar ve sayfanın kendi yaptığı
 * ağ isteklerinden `.m3u8` / `.mp4` / `.mpd` olanları yakalar. Bu, "videolar oynatılamıyor"
 * şikâyetlerinin en yaygın kaynağı olan "embed sayfasından medya URL'si çıkmıyor" sorununun
 * genel (site-bağımsız) çözümüdür.
 *
 * Tasarım notları:
 *  - Aynı anda **en fazla 1** WebView (global [Semaphore]) — düşük RAM'li cihazlarda çökme önlenir.
 *  - Toplam süre [DEFAULT_TIMEOUT_MS] ile sınırlıdır; süre aşımında WebView yok edilir.
 *  - `useOkhttp = false`: istekler tarayıcı motorundan gitsin (CF/JA3 kısıtları için gerekli).
 *  - Her çağrı `Dispatchers.Main`'de çalışır (WebView zorunluluğu).
 */
object WebViewMediaSniffer {

    private const val TAG = "WebViewMediaSniffer"

    /** Toplam bekleme süresi: kullanıcıyı çok bekletmeyen ama JS'in kurulmasına izin veren denge. */
    const val DEFAULT_TIMEOUT_MS: Long = 12_000L

    /** Aynı anda tek WebView. */
    private val globalGate = Semaphore(1)

    /** Yakalanan medya istekleri (URL → referer) — aynı sayfa tekrar açılmasın diye önbellek. */
    private val cache = BoundedCache<String, String>("cs.webViewSniffer", 200)

    private val MEDIA_REGEX = Regex(
        """(?i).*\.(m3u8|mp4|mpd|mkv|webm|flv)(\?.*)?$"""
    )

    /** Önbellekte çözülmüş bir URL var mı? */
    fun cached(url: String): String? = cache[url]

    fun clearCache() = cache.clear()

    /**
     * Oynatıcı sayfasını WebView'de açar ve medya isteğini yakalamaya çalışır.
     *
     * @param playerUrl Embed/oynatıcı sayfasının adresi
     * @param referer   Provider sayfası (403'leri önlemek için)
     * @param timeoutMs Toplam süre
     * @return Yakalanan ilk anlamlı medya URL'si veya null
     */
    suspend fun sniff(
        playerUrl: String,
        referer: String?,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): SniffResult = withContext(Dispatchers.Main) {
        if (!playerUrl.startsWith("http", ignoreCase = true)) return@withContext SniffResult(null, emptyMap(), "geçersiz URL")

        cached(playerUrl)?.let { return@withContext SniffResult(it, emptyMap(), "cache") }

        // ÖNEMLİ: Bu blok Dispatchers.Main'de çalışır. Eskiden `java.util.concurrent.Semaphore
        // .tryAcquire(6 sn)` kullanılıyordu — bu, arayüz iş parçacığını 6 saniyeye kadar
        // BLOKLUYORDU (ANR / "donup kalıyor" şikâyeti). Artık askıya alan (suspending) kotlinx
        // Semaphore kullanılıyor: beklerken thread serbest kalır, ANR oluşmaz.
        val acquired = withTimeoutOrNull(timeoutMs / 2) { globalGate.acquire() } != null
        if (!acquired) {
            Log.w(TAG, "Başka bir WebView çözümlemesi sürüyor — atlanıyor: $playerUrl")
            return@withContext SniffResult(null, emptyMap(), "meşgul")
        }

        try {
            val resolver = WebViewResolver(
                interceptUrl = MEDIA_REGEX,
                additionalUrls = listOf(MEDIA_REGEX),
                userAgent = CloudflareKiller.UNIFIED_USER_AGENT,
                useOkhttp = false,
                timeout = timeoutMs
            )

            val outcome = withTimeoutOrNull(timeoutMs + 3_000L) {
                resolver.resolveUsingWebView(
                    url = playerUrl,
                    referer = referer
                )
            }

            if (outcome == null) {
                Log.w(TAG, "WebView çözümlemesi zaman aşımına uğradı: $playerUrl")
                return@withContext SniffResult(null, emptyMap(), "timeout")
            }

            val hits = buildList {
                outcome.first?.let { add(it) }
                addAll(outcome.second)
            }

            val best = hits
                .map { it.url.toString() to it.headers.toMap() }
                .filter { (u, _) -> MEDIA_REGEX.matches(u.substringBefore('?')) || MEDIA_REGEX.matches(u) }
                .sortedWith(compareByDescending<Pair<String, Map<String, String>>> { (u, _) ->
                    when {
                        u.contains(".m3u8", true) -> 3
                        u.contains(".mp4", true) -> 2
                        u.contains(".mpd", true) -> 1
                        else -> 0
                    }
                })
                .firstOrNull()

            if (best == null) {
                Log.w(TAG, "WebView medya isteği yakalayamadı: $playerUrl (${hits.size} istek görüldü)")
                SniffResult(null, emptyMap(), "medya-yok")
            } else {
                Log.i(TAG, "✅ WebView medya yakaladı: ${best.first}")
                cache[playerUrl] = best.first
                SniffResult(best.first, best.second, "yakalandı")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "WebView çözümleme hatası: ${e.javaClass.simpleName}: ${e.message}")
            SniffResult(null, emptyMap(), "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            globalGate.release()
        }
    }

    /**
     * @param url        Yakalanan medya URL'si (null = bulunamadı)
     * @param headers    Yakalanan isteğin başlıkları (Referer dâhil)
     * @param diagnostic Teşhis etiketi — arayüz/log için
     */
    data class SniffResult(
        val url: String?,
        val headers: Map<String, String>,
        val diagnostic: String
    ) {
        val found: Boolean get() = !url.isNullOrBlank()
    }
}
