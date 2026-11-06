package com.kitsugi.animelist.core.music

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Okuma müziği istasyon kataloğu.
 *
 * Kaynaklar:
 *  1. **radio-browser.info** — anahtar gerektirmeyen, topluluk tarafından beslenen
 *     açık radyo dizini. Etikete (lo-fi / klasik / caz / ambiyans) göre arama yapar.
 *  2. **Yerleşik liste** — API'ye ulaşılamadığında (çevrimdışı, DNS engeli, 429)
 *     özelliğin sessizce ölü kalmaması için gömülü, doğrudan çalınabilir akışlar.
 *
 * Sözleşme: [MusicStation.streamUrl] her zaman doğrudan bir ses akışıdır. Liste/HTML
 * sayfası, `.m3u8` (canlı HLS radyo) veya `http://` (cleartext engeli) adresleri
 * elenir; aksi hâlde oynatıcı "hazırlanıyor" ekranında sonsuza kadar kalıyordu.
 */
object MangaMusicCatalog {

    private const val TAG = "MangaMusicCatalog"
    private const val CACHE_TTL_MS = 20 * 60 * 1000L
    private const val CACHE_CAPACITY = 8

    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://all.api.radio-browser.info"
    )

    private data class CacheEntry(val result: MusicStationLoadResult, val expiresAt: Long)

    private val cache = object : LinkedHashMap<String, CacheEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > CACHE_CAPACITY
    }

    /**
     * Ağ olmadan da çalışan taban akışlar.
     *
     * Hepsi HTTPS ve doğrudan MP3/AAC akışıdır (SomaFM icecast + NPO Radio 4).
     * Liste her etiket için en az iki istasyon içerir ki "Sonraki" düğmesi her
     * zaman işe yarasın.
     */
    val builtInStations: List<MusicStation> = listOf(
        MusicStation(
            id = "somafm-groove-salad",
            name = "Groove Salad",
            streamUrl = "https://ice1.somafm.com/groovesalad-128-mp3",
            mood = MangaMusicMood.LO_FI.tag,
            bitrate = 128,
            country = "US",
            providerLabel = "SomaFM"
        ),
        MusicStation(
            id = "somafm-lush",
            name = "Lush",
            streamUrl = "https://ice1.somafm.com/lush-128-mp3",
            mood = MangaMusicMood.LO_FI.tag,
            bitrate = 128,
            country = "US",
            providerLabel = "SomaFM"
        ),
        MusicStation(
            id = "somafm-drone-zone",
            name = "Drone Zone",
            streamUrl = "https://ice1.somafm.com/dronezone-128-mp3",
            mood = MangaMusicMood.AMBIENT.tag,
            bitrate = 128,
            country = "US",
            providerLabel = "SomaFM"
        ),
        MusicStation(
            id = "somafm-deep-space-one",
            name = "Deep Space One",
            streamUrl = "https://ice1.somafm.com/deepspaceone-128-mp3",
            mood = MangaMusicMood.AMBIENT.tag,
            bitrate = 128,
            country = "US",
            providerLabel = "SomaFM"
        ),
        MusicStation(
            id = "somafm-sonic-universe",
            name = "Sonic Universe",
            streamUrl = "https://ice1.somafm.com/sonicuniverse-128-mp3",
            mood = MangaMusicMood.JAZZ.tag,
            bitrate = 128,
            country = "US",
            providerLabel = "SomaFM"
        ),
        MusicStation(
            id = "somafm-illinois-street-lounge",
            name = "Illinois Street Lounge",
            streamUrl = "https://ice1.somafm.com/illstreet-128-mp3",
            mood = MangaMusicMood.JAZZ.tag,
            bitrate = 128,
            country = "US",
            providerLabel = "SomaFM"
        ),
        MusicStation(
            id = "npo-radio4",
            name = "NPO Radio 4",
            streamUrl = "https://icecast.omroep.nl/radio4-bb-mp3",
            mood = MangaMusicMood.CLASSICAL.tag,
            bitrate = 128,
            country = "NL",
            providerLabel = "NPO"
        ),
        MusicStation(
            id = "somafm-suburbs-of-goa",
            name = "Suburbs of Goa",
            streamUrl = "https://ice1.somafm.com/suburbsofgoa-128-mp3",
            mood = MangaMusicMood.CLASSICAL.tag,
            bitrate = 128,
            country = "US",
            providerLabel = "SomaFM"
        )
    )

    /** Verilen etikete uygun yerleşik istasyonlar; etiket yoksa tüm liste. */
    fun builtInFor(mood: String?): List<MusicStation> {
        val tag = mood?.trim()?.lowercase(Locale.US).orEmpty()
        if (tag.isBlank()) return builtInStations
        val filtered = builtInStations.filter { it.mood.equals(tag, ignoreCase = true) }
        return filtered.ifEmpty { builtInStations }
    }

    /**
     * Etiket için istasyon listesi döndürür.
     *
     * Hiçbir koşulda boş liste + sessiz hata dönmez: API başarısız olursa yerleşik
     * liste `usedFallback = true` ile döner, böylece kullanıcı hem müzik dinleyebilir
     * hem de neden farklı bir liste gördüğünü anlar.
     */
    suspend fun loadStations(
        mood: String,
        source: MangaMusicSource,
        forceRefresh: Boolean = false
    ): MusicStationLoadResult = withContext(Dispatchers.IO) {
        val tag = MangaMusicMood.fromTag(mood).tag
        if (source == MangaMusicSource.BUILT_IN || source == MangaMusicSource.OFF) {
            return@withContext MusicStationLoadResult(stations = builtInFor(tag))
        }

        val cacheKey = "radio:$tag"
        if (!forceRefresh) {
            synchronized(cache) {
                val hit = cache[cacheKey]
                if (hit != null && hit.expiresAt > System.currentTimeMillis() && hit.result.stations.isNotEmpty()) {
                    return@withContext hit.result
                }
            }
        }

        var lastError: String? = null
        for (mirror in mirrors) {
            val url = "$mirror/json/stations/search?tag=${encode(tag)}&limit=30&order=votes&reverse=true&hidebroken=true"
            val body = runCatching { KitsugiHttp.get(url) }
                .onFailure { lastError = it.javaClass.simpleName }
                .getOrNull()
            if (body.isNullOrBlank()) {
                lastError = lastError ?: "boş yanıt"
                continue
            }
            val parsed = runCatching { parseStations(body, tag) }.getOrElse {
                Log.w(TAG, "İstasyon listesi ayrıştırılamadı ($mirror): ${it.javaClass.simpleName}")
                emptyList()
            }
            if (parsed.isNotEmpty()) {
                val result = MusicStationLoadResult(stations = parsed)
                synchronized(cache) { cache[cacheKey] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS) }
                Log.i(TAG, "radio-browser → ${parsed.size} istasyon ($tag)")
                return@withContext result
            }
            lastError = lastError ?: "uygun istasyon bulunamadı"
        }

        val fallback = builtInFor(tag)
        val result = MusicStationLoadResult(
            stations = fallback,
            usedFallback = true,
            error = lastError ?: "bağlantı kurulamadı"
        )
        synchronized(cache) {
            cache[cacheKey] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS / 4)
        }
        Log.w(TAG, "radio-browser kullanılamadı, yerleşik listeye düşüldü: ${result.error}")
        result
    }

    /**
     * radio-browser yanıtını [MusicStation] listesine çevirir.
     *
     * Kurallar (oynatıcının "sessizce takılma" hatalarını önler):
     *  - yalnızca `https://` adresler,
     *  - yalnızca doğrudan ses akışı (`.m3u8`, `.pls`, `.html` elenir),
     *  - HLS bayrağı işaretli istasyonlar elenir,
     *  - aynı isim+adres tekrarı gösterilmez.
     */
    internal fun parseStations(json: String, moodTag: String): List<MusicStation> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, MusicStation>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val station = toStation(obj, moodTag) ?: continue
            val key = "${station.name.lowercase(Locale.US)}|${station.streamUrl}"
            if (!out.containsKey(key)) out[key] = station
            if (out.size >= 24) break
        }
        return out.values.toList()
    }

    private fun toStation(obj: JSONObject, moodTag: String): MusicStation? {
        val name = obj.optString("name").trim()
        if (name.isBlank()) return null
        if (obj.optInt("hls", 0) == 1) return null

        val url = obj.optString("url_resolved").trim().ifBlank { obj.optString("url").trim() }
        if (!isPlayableStreamUrl(url)) return null

        val codec = obj.optString("codec").uppercase(Locale.US)
        if (codec.isNotBlank() && codec !in PLAYABLE_CODECS) return null

        return MusicStation(
            id = obj.optString("stationuuid").ifBlank { url },
            name = name,
            streamUrl = url,
            mood = moodTag,
            bitrate = obj.optInt("bitrate", 0),
            country = obj.optString("countrycode").ifBlank { obj.optString("country") },
            providerLabel = "Radio Browser"
        )
    }

    private val PLAYABLE_CODECS = setOf("MP3", "AAC", "AAC+", "AAC,H.264", "MP3,H.264", "OGG", "OPUS")

    /** Oynatıcının gerçekten açabileceği, kısıtlamasız bir akış adresi mi? */
    internal fun isPlayableStreamUrl(url: String): Boolean {
        val value = url.trim()
        if (!value.startsWith("https://", ignoreCase = true)) return false
        val path = value.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        if (path.endsWith(".m3u8") || path.endsWith(".pls") || path.endsWith(".m3u") ||
            path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".xspf")
        ) return false
        return true
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Test edilebilirlik için HTTP erişimi tek noktada. */
    internal object KitsugiHttp {
        fun get(url: String): String? =
            com.kitsugi.animelist.data.remote.KitsugiApiBase.executeGetRequest(URL(url))
    }

    /** Entegrasyon ayarlarındaki "Bağlantıyı test et" için hızlı sağlık kontrolü. */
    suspend fun probe(mood: String, source: MangaMusicSource): MusicStationLoadResult =
        loadStations(mood = mood, source = source, forceRefresh = true)
}
