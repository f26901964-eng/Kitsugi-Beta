package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException

/**
 * Bangumi kimlik alanı (ID namespace) tek gerçek kaynağı.
 *
 * Kitsugi'de tüm kaynakların kimlikleri `MediaEntry.malId` alanında **offset'li stableId**
 * olarak saklanır; bu sözleşme `MediaIdentity.keys`, birleşik kütüphane gruplaması ve
 * önbellek anahtarları tarafından beklenir (bkz. [KitsuIdNamespace]):
 *
 *  - MAL       → gerçek MAL ID        (1..99M)
 *  - AniList   → `id + 100_000_000`   (100M..199M)
 *  - Simkl     → `id + 150_000_000`   (150M..249M)  *(SimklImportManager)*
 *  - Kitsu     → `id + 300_000_000`   (300M..399M)
 *  - Shikimori → `id + 400_000_000`   (400M..499M)
 *  - **Bangumi → `id + 500_000_000`   (500M..599M)**
 *
 * Bangumi条目 ID'leri (`subject_id`) 7 haneli sayılara kadar çıkabildiği için 100M'lik
 * pencere yeterlidir; pencere dışına taşan bir ID asla stableId diye yorumlanmaz.
 *
 * Ayrıca Bangumi, **kendi ID uzayını** MAL/AniList'ten bağımsız kullanır: 35658 numaralı
 * Bangumi条目'su ile 35658 numaralı MAL anime'si farklı yapımlardır. Bu yüzden çapraz
 * eşitleme (cross-sync) her zaman [resolveBangumiId] üzerinden gerçek eşleşme aramalıdır.
 */
object BangumiIdNamespace {
    private const val TAG = "BangumiIdNamespace"

    /** Bangumi stableId ofseti — `MediaIdentity.keys` ile birebir aynı olmalı. */
    const val BANGUMI_OFFSET: Int = 500_000_000

    const val STABLE_ID_MIN: Int = BANGUMI_OFFSET + 1
    const val STABLE_ID_MAX: Int = BANGUMI_OFFSET + 99_999_999

    /** Kaynak adı — `MediaEntry.source` ve `JikanSearchResult.source` alanında kullanılır. */
    const val SOURCE = "bangumi"

    fun isStableId(value: Int?): Boolean = value != null && value in STABLE_ID_MIN..STABLE_ID_MAX

    fun stableIdOrNull(value: Int?): Int? = value?.takeIf { isStableId(it) }

    /** Bangumi stableId → gerçek `subject_id`. */
    fun rawIdFromStable(stableId: Int?): Int? =
        stableId?.takeIf { it in STABLE_ID_MIN..STABLE_ID_MAX }?.minus(BANGUMI_OFFSET)

    /** Bangumi `subject_id` → stableId. Pencereyi taşırıyorsa null. */
    fun stableIdFromRaw(subjectId: Int?): Int? =
        subjectId?.takeIf { it > 0 && it + BANGUMI_OFFSET <= STABLE_ID_MAX }?.plus(BANGUMI_OFFSET)

    /** `MediaEntry.malId` alanında gerçek bir MAL ID'si mi saklı? */
    fun realMalIdOf(storedId: Int?): Int? = storedId?.takeIf { it in 1..99_999_999 }

    fun isAnimeType(type: MediaType?): Boolean = type != MediaType.Manga

    /** Bangumi条目 türü → Kitsugi [MediaType]. */
    fun mediaTypeFor(subjectType: Int): MediaType = when (subjectType) {
        BangumiApiClient.SubjectType.BOOK -> MediaType.Manga
        BangumiApiClient.SubjectType.ANIME -> MediaType.Anime
        // 三次元 (real) → dizi/film; Bangumi'de platform alanı "剧场版" ise film sayılır.
        BangumiApiClient.SubjectType.REAL -> MediaType.TvShow
        else -> MediaType.Anime
    }

    /** Kitsugi [MediaType] → Bangumi条目 türü (arama filtrelerinde kullanılır). */
    fun subjectTypeFor(mediaType: MediaType?): Int = when (mediaType) {
        MediaType.Manga -> BangumiApiClient.SubjectType.BOOK
        else -> BangumiApiClient.SubjectType.ANIME
    }

    /**
     * Bir kayıttan güvenilir Bangumi `subject_id` çözer.
     *
     * Sıra (güven derecesine göre azalır):
     *  1. Kayıttaki değer zaten Bangumi aralığındaysa doğrudan kullanılır.
     *  2. Yerel MAL→Bangumi eşleme önbelleği (kullanıcı bazlı, ağ gerektirmez).
     *  3. Bangumi arama ile **sıkı** başlık eşleşmesi (tek ve net sonuçta).
     *
     * Hiçbiri tutmazsa null — çağıran taraf "kimlik belirsiz" davranmalı,
     * rastgele bir ID denememelidir (Kitsu'da yaşanan yanlış detay sayfası hatasının tekrarı).
     */
    suspend fun resolveBangumiId(
        context: Context?,
        storedId: Int?,
        title: String? = null,
        expectedYear: Int? = null,
        isAnime: Boolean = true,
        allowNetwork: Boolean = true
    ): Int? {
        rawIdFromStable(storedId)?.let { return it }
        if (context == null) return null

        // 2) Yerel eşleme önbelleği
        val malId = realMalIdOf(storedId)
        if (malId != null) {
            val cached = runCatching { BangumiLocalMappingCache.get(context, malId, isAnime) }
                .getOrElse { null }
            cached?.let { return it }
        }

        if (!allowNetwork) return null

        // 3) Sıkı başlık araması — yalnızca tek ve net eşleşmede güvenli
        val query = title?.trim().orEmpty()
        if (query.isBlank()) return null
        val token = runCatching { BangumiAuthStore.getValidToken(context) }.getOrNull()
        val types = listOf(
            if (isAnime) BangumiApiClient.SubjectType.ANIME else BangumiApiClient.SubjectType.BOOK
        )
        val page = runCatching {
            BangumiApiClient.searchSubjects(keyword = query, token = token, types = types, limit = 10)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Bangumi title lookup failed for '$query': ${error.message}")
            return null
        }
        val candidates = page.data.filter { subject ->
            expectedYear == null || subject.date?.take(4)?.toIntOrNull() == expectedYear ||
                matchesTitle(subject.name, query) || matchesTitle(subject.nameCn, query)
        }
        val exact = candidates.filter {
            matchesTitle(it.name, query) || matchesTitle(it.nameCn, query)
        }
        val unique = when {
            exact.size == 1 -> exact.first()
            candidates.size == 1 -> candidates.first()
            else -> null
        } ?: return null

        if (malId != null) {
            runCatching { BangumiLocalMappingCache.put(context, malId, isAnime, unique.id) }
        }
        return unique.id
    }

    /** Boşluk/noktalama duyarsız başlık karşılaştırması (`MediaIdentity.normalizedTitle` ile aynı ruh). */
    private fun matchesTitle(candidate: String?, query: String): Boolean {
        if (candidate.isNullOrBlank()) return false
        return normalize(candidate) == normalize(query)
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
}

/**
 * Kullanıcı bazlı, ağ gerektirmeyen MAL ↔ Bangumi eşleme önbelleği.
 *
 * Kitsu'daki `saveKitsuMediaIdForMal` deseniyle aynıdır: bir kez çözülen eşleşme
 * tekrar tekrar arama isteği atmaz. Bangumi tarafında resmî bir MAL eşleme ucu
 * olmadığı için bu önbellek, çapraz eşitleme maliyetini ciddi biçimde düşürür.
 *
 * Kalıcı eşleme kaynağı istenirse `bangumi-data/bangumi-data` (CC BY 4.0) veya
 * `bangumi/Archive` wiki dökümleri `infobox` içindeki MAL/AniList bağlantılarından
 * türetilebilir — bkz. docs/bangumi/BANGUMI_ARASTIRMA_VE_KAYNAKLAR.md.
 */
object BangumiLocalMappingCache {
    private const val PREFS_NAME = "bangumi_id_mapping"

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(malId: Int, isAnime: Boolean) = "bgm_${if (isAnime) "a" else "m"}_$malId"

    fun get(context: Context, malId: Int, isAnime: Boolean): Int? =
        prefs(context).getInt(key(malId, isAnime), 0).takeIf { it > 0 }

    fun put(context: Context, malId: Int, isAnime: Boolean, subjectId: Int) {
        if (subjectId <= 0) return
        prefs(context).edit().putInt(key(malId, isAnime), subjectId).apply()
    }

    fun remove(context: Context, malId: Int, isAnime: Boolean) {
        prefs(context).edit().remove(key(malId, isAnime)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
