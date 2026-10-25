package com.kitsugi.animelist.core.identity

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kitsugi.animelist.data.remote.ResolvedIds
import java.util.Locale

/**
 * Giriş noktaları arasında PAYLAŞILAN kimlik çözüm defteri (Ortak Omurga – Faz 1).
 *
 * Neden gerekli?
 *  Bir bölüm AniList/MAL detayından açıldığında `StreamViewModel` zaten tüm kimlikleri
 *  (MAL, AniList, TMDB, IMDb, Kitsu, Bangumi) çözüyor. Ama aynı bölüm bir CloudStream
 *  eklentisinin kendi kataloğundan, arama sonucundan veya izleme geçmişinden açıldığında
 *  uygulama `malId = null, aniListId = null` ile oynatıcıya giriyor. Oynatıcı bu durumda
 *  altyazı eklentilerine yalnızca başlık gönderebiliyor → aynı video için altyazı sayısı
 *  giriş noktasına göre değişiyor ("kaynak kayırma" hissi).
 *
 *  Bu defter, çözülen kimlikleri hem içerik anahtarıyla hem de başlık+yıl anahtarıyla
 *  saklar. ID'siz girişlerde oynatıcı başlıkla arayıp ID'leri geri kazanır; böylece
 *  hangi kaynaktan girilirse girilsin aynı kimlik kümesi kullanılır.
 *
 * Kalıcılık: SharedPreferences içinde tek JSON blob (sınırlı sayıda kayıt, LRU benzeri
 *  kırpma). Room şeması değiştirilmez → migration riski yok.
 */
object MediaIdentityRegistry {

    private const val TAG = "MediaIdentityRegistry"
    private const val PREFS = "kitsugi_identity_registry"
    private const val KEY_JSON = "entries"
    private const val MAX_ENTRIES = 400

    /** Kayıt geçerlilik süresi: 30 gün. */
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    data class Entry(
        val contentKey: String,
        val titleKey: String,
        val normalizedTitle: String,
        val year: Int? = null,
        val imdbId: String? = null,
        val kitsuId: Int? = null,
        val malId: Int? = null,
        val aniListId: Int? = null,
        val tmdbId: Int? = null,
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        fun toResolvedIds(): ResolvedIds = ResolvedIds(
            imdbId = imdbId,
            kitsuId = kitsuId,
            malId = malId,
            aniListId = aniListId,
            tmdbId = tmdbId
        )
    }

    private val gson = Gson()
    private val lock = Any()

    @Volatile private var cache: MutableList<Entry>? = null
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** Çözülmüş kimliği kaydeder (hem ID hem başlık anahtarıyla aranabilir olur). */
    fun remember(identity: MediaIdentity) {
        if (identity.title.isBlank()) return
        val hasAnyId = identity.imdbId != null || identity.malId != null ||
            identity.aniListId != null || identity.kitsuId != null || identity.tmdbId != null
        if (!hasAnyId) return

        val entry = Entry(
            contentKey = identity.contentKey,
            titleKey = MediaIdentity.titleKey(identity.title, identity.startYear),
            normalizedTitle = identity.normalizedTitle,
            year = identity.startYear,
            imdbId = identity.imdbId,
            kitsuId = identity.kitsuId,
            malId = identity.malId,
            aniListId = identity.aniListId,
            tmdbId = identity.tmdbId
        )
        synchronized(lock) {
            val list = load().toMutableList()
            list.removeAll { it.titleKey == entry.titleKey || it.contentKey == entry.contentKey }
            list.add(0, entry)
            while (list.size > MAX_ENTRIES) list.removeAt(list.size - 1)
            store(list)
        }
        Log.d(TAG, "remember: '${identity.title}' → mal=${entry.malId} anilist=${entry.aniListId} imdb=${entry.imdbId}")
    }

    /**
     * Başlıkla (ve varsa yılla) kimlik arar.
     * Bulunamazsa null döner — çağıran taraf normal çözümlemeye devam eder.
     */
    fun lookupByTitle(title: String, year: Int? = null): ResolvedIds? {
        if (title.isBlank()) return null
        val normalized = com.kitsugi.animelist.data.cloudstream.CsTitleMatcher.normalizeTitleForMatch(title)
        if (normalized.isBlank()) return null
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val candidates = load().filter { now - it.updatedAt <= MAX_AGE_MS }
            // 1) Birebir başlık + yıl, 2) birebir başlık, 3) normalize başlık
            val byTitleAndYear = year?.let { y ->
                candidates.firstOrNull { it.titleKey == MediaIdentity.titleKey(title, y) }
            }
            val exact = byTitleAndYear
                ?: candidates.firstOrNull { it.normalizedTitle == normalized }
                ?: candidates.firstOrNull { it.normalizedTitle.isNotBlank() && (it.normalizedTitle.contains(normalized) || normalized.contains(it.normalizedTitle)) }
            return exact?.toResolvedIds()
        }
    }

    /** İçerik anahtarıyla kimlik arar (akış ekranı ile oynatıcı farklı anahtar kullanırsa). */
    fun lookupByContentKey(contentKey: String): ResolvedIds? {
        if (contentKey.isBlank()) return null
        synchronized(lock) {
            val hit = load().firstOrNull { it.contentKey == contentKey } ?: return null
            return hit.toResolvedIds()
        }
    }

    /** Tüm kayıtları temizler (ayarlar → veri temizleme yolları için). */
    fun clear() {
        synchronized(lock) {
            cache = mutableListOf()
            prefs?.edit()?.remove(KEY_JSON)?.apply()
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun load(): List<Entry> {
        cache?.let { return it }
        val raw = prefs?.getString(KEY_JSON, null)
        val list: MutableList<Entry> = if (raw.isNullOrBlank()) {
            mutableListOf()
        } else {
            try {
                val type = object : TypeToken<MutableList<Entry>>() {}.type
                gson.fromJson<MutableList<Entry>>(raw, type) ?: mutableListOf()
            } catch (e: Exception) {
                Log.w(TAG, "Kayıt defteri okunamadı, sıfırlanıyor: ${e.message}")
                mutableListOf()
            }
        }
        cache = list
        return list
    }

    private fun store(list: List<Entry>) {
        cache = list.toMutableList()
        try {
            prefs?.edit()?.putString(KEY_JSON, gson.toJson(list))?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Kayıt defteri yazılamadı: ${e.message}")
        }
    }

    /** Yıl alanı olmayan eski kayıtlar için güvenli normalize. */
    internal fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT)
}
