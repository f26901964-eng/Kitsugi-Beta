package com.kitsugi.animelist.core.subtitle

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kitsugi.animelist.core.player.model.Subtitle

/**
 * Ortak Altyazı Belleği (Ortak Omurga – Faz 2).
 *
 * SORUN (2026-10-25 kullanıcı raporu):
 *  "Aynı video verisine daha önce aynı altyazılar geliyorken, farklı bir kaynaktan girip
 *  izleyince o videoya ait altyazı gelmiyor."
 *
 *  Kök nedenler:
 *   1. Altyazılar yalnızca o an oynatılan kaynağa bağlı yaşıyordu
 *      (`_currentSubtitles = stream.subtitles`), kaynak değişince liste SIFIRLANIYORDU.
 *   2. Sağlayıcı (CloudStream eklentisi) içinden çıkarılan altyazılar yalnızca o eklentinin
 *      stream nesnesinde duruyordu; başka kaynak seçildiğinde kayboluyordu.
 *   3. Ağ tabanlı altyazı eklentileri her açılışta yeniden sorgulanıyor, sonuç önbelleğe
 *      alınmıyordu → kaynak/URL değişince (özellikle OpenSubtitles hash'i tutmayınca)
 *      sonuç boş dönüyordu.
 *
 *  Bu bellek, bir kez bulunan altyazıları içerik+bölüm kimliğine göre saklar ve HER
 *  kaynakta tekrar sunar. Böylece "kaynak kayırma" ortadan kalkar: altyazıyı A
 *  kaynağında bulan kullanıcı, B kaynağında da aynı listeyi görür.
 *
 * Kalıcılık: SharedPreferences + Gson (Room şeması değişmez → migration riski yok).
 * Anahtarlar [com.kitsugi.animelist.core.identity.MediaIdentity.subtitleKeyCandidates]
 * üzerinden hem ID hem başlık tabanlıdır; eski/yeni kayıtlar birlikte okunur.
 */
object SubtitleMemoryStore {

    private const val TAG = "SubtitleMemory"
    private const val PREFS = "kitsugi_subtitle_memory"
    private const val KEY_JSON = "entries"
    private const val MAX_KEYS = 600
    private const val MAX_PER_KEY = 80

    /**
     * Sağlayıcı içinden çıkarılan altyazıların ömrü.
     *
     * Bu linkler çoğu zaman imzalı/süreli oluyor; 3 gün sonra ölü linkleri menüde
     * göstermek kullanıcıya "altyazı var ama açılmıyor" olarak yansıyordu. 24 saat,
     * aynı gün içindeki kaynak değişimlerini kapsar ve ölü link birikmesini önler.
     */
    private const val PROVIDER_TTL_MS = 24L * 60 * 60 * 1000
    /** Ağ eklentilerinden gelen altyazılar: 14 gün. */
    private const val REMOTE_TTL_MS = 14L * 24 * 60 * 60 * 1000

    data class StoredSubtitle(
        val id: String,
        val url: String,
        val lang: String,
        val addonName: String,
        val addonLogo: String? = null,
        val format: String? = null,
        /** true → bir video kaynağının içinden çıkarıldı (link ömrü kısa olabilir). */
        val providerBound: Boolean = false,
        val addedAt: Long = System.currentTimeMillis()
    ) {
        fun toSubtitle(): Subtitle = Subtitle(
            id = id,
            url = url,
            lang = lang,
            addonName = addonName,
            addonLogo = addonLogo,
            format = format
        )
    }

    private val gson = Gson()
    private val lock = Any()

    @Volatile private var cache: MutableMap<String, MutableList<StoredSubtitle>>? = null
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /**
     * Verilen anahtarlardan (ID ve başlık tabanlı) tüm geçerli altyazıları döndürür.
     * Sonuçlar URL'ye göre tekilleştirilir; sağlayıcıdan gelenler önce gelir.
     */
    fun recall(keys: List<String>): List<Subtitle> {
        if (keys.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val map = load()
            val out = LinkedHashMap<String, StoredSubtitle>()
            for (key in keys) {
                val list = map[key] ?: continue
                for (entry in list) {
                    val ttl = if (entry.providerBound) PROVIDER_TTL_MS else REMOTE_TTL_MS
                    if (now - entry.addedAt > ttl) continue
                    if (entry.url.isBlank()) continue
                    val dedupeKey = entry.url.trim()
                    val existing = out[dedupeKey]
                    // Sağlayıcıdan gelen kayıt, ağ eklentisinden gelenin önüne geçer.
                    if (existing == null || (!existing.providerBound && entry.providerBound)) {
                        out[dedupeKey] = entry
                    }
                }
            }
            return out.values.map { it.toSubtitle() }
        }
    }

    /**
     * Verilen anahtarlar için en son ne zaman altyazı bulunduğunu döndürür (0 = hiç yok).
     *
     * Amaç: kaynak değişiminde aynı bölüm için tekrar tekrar 7 sorgu × N eklenti ağ isteği
     * atılmasını önlemek. Bellek taze ise ağ turu atlanır (hız), kullanıcı "yenile" derse
     * `forceRefresh` ile yine sorgulanır (tazelik).
     */
    fun lastUpdatedAt(keys: List<String>): Long {
        if (keys.isEmpty()) return 0L
        synchronized(lock) {
            val map = load()
            var newest = 0L
            for (key in keys) {
                val list = map[key] ?: continue
                val t = list.maxOfOrNull { it.addedAt } ?: 0L
                if (t > newest) newest = t
            }
            return newest
        }
    }

    /** Bellekte bu bölüm için `maxAgeMs`'ten yeni kayıt var mı? */
    fun isFresh(keys: List<String>, maxAgeMs: Long): Boolean {
        val last = lastUpdatedAt(keys)
        return last > 0L && System.currentTimeMillis() - last <= maxAgeMs
    }

    /** Bir bölüm için bulunan altyazıları (birden fazla anahtar altında) saklar. */
    fun remember(
        keys: List<String>,
        subtitles: List<Subtitle>,
        providerBound: Boolean = false
    ) {
        if (keys.isEmpty() || subtitles.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val map = load()
            for (key in keys.distinct()) {
                val list = map.getOrPut(key) { mutableListOf() }
                for (sub in subtitles) {
                    if (sub.url.isBlank()) continue
                    val url = sub.url.trim()
                    val idx = list.indexOfFirst { it.url.trim() == url }
                    val entry = StoredSubtitle(
                        id = sub.id,
                        url = url,
                        lang = sub.lang,
                        addonName = sub.addonName,
                        addonLogo = sub.addonLogo,
                        format = sub.format,
                        providerBound = providerBound,
                        addedAt = now
                    )
                    if (idx >= 0) list[idx] = entry else list.add(0, entry)
                    while (list.size > MAX_PER_KEY) list.removeAt(list.size - 1)
                }
                // Eskimiş kayıtları temizle
                list.removeAll { e ->
                    val ttl = if (e.providerBound) PROVIDER_TTL_MS else REMOTE_TTL_MS
                    now - e.addedAt > ttl
                }
            }
            trim(map)
            store(map)
        }
        Log.d(TAG, "remember: ${subtitles.size} altyazı, providerBound=$providerBound, keys=${keys.size}")
    }

    /** Tek bir bölümün belleğini siler (kullanıcı "altyazıları yenile" derse). */
    fun forget(keys: List<String>) {
        synchronized(lock) {
            val map = load()
            keys.forEach { map.remove(it) }
            store(map)
        }
    }

    fun clear() {
        synchronized(lock) {
            cache = mutableMapOf()
            prefs?.edit()?.remove(KEY_JSON)?.apply()
        }
    }

    /** İstatistik (tanılama ekranı için). */
    fun stats(): Pair<Int, Int> {
        synchronized(lock) {
            val map = load()
            return map.size to map.values.sumOf { it.size }
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun trim(map: MutableMap<String, MutableList<StoredSubtitle>>) {
        if (map.size <= MAX_KEYS) return
        val now = System.currentTimeMillis()
        // En yeni kaydı en başta tutan anahtarları koru, kalanları at.
        val keep = map.entries
            .sortedByDescending { entry -> entry.value.maxOfOrNull { it.addedAt } ?: 0L }
            .take(MAX_KEYS)
            .map { it.key }
            .toSet()
        map.keys.retainAll(keep)
        Log.d(TAG, "Bellek kırpıldı → ${map.size} anahtar (now=$now)")
    }

    private fun load(): MutableMap<String, MutableList<StoredSubtitle>> {
        cache?.let { return it }
        val raw = prefs?.getString(KEY_JSON, null)
        val map: MutableMap<String, MutableList<StoredSubtitle>> = if (raw.isNullOrBlank()) {
            mutableMapOf()
        } else {
            try {
                val type = object : TypeToken<MutableMap<String, MutableList<StoredSubtitle>>>() {}.type
                gson.fromJson<MutableMap<String, MutableList<StoredSubtitle>>>(raw, type) ?: mutableMapOf()
            } catch (e: Exception) {
                Log.w(TAG, "Bellek okunamadı, sıfırlanıyor: ${e.message}")
                mutableMapOf()
            }
        }
        cache = map
        return map
    }

    private fun store(map: Map<String, List<StoredSubtitle>>) {
        cache = map.mapValues { it.value.toMutableList() }.toMutableMap()
        try {
            prefs?.edit()?.putString(KEY_JSON, gson.toJson(map))?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Bellek yazılamadı: ${e.message}")
        }
    }
}
