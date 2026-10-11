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

    /** Bangumi stableId veya doğrudan raw subject_id → gerçek `subject_id`. */
    fun rawIdOrNull(id: Int?): Int? =
        rawIdFromStable(id) ?: id?.takeIf { it in 1 until BANGUMI_OFFSET }

    /** Bangumi `subject_id` → stableId. Pencereyi taşırıyorsa null. */
    fun stableIdFromRaw(subjectId: Int?): Int? =
        subjectId?.takeIf { it > 0 && it + BANGUMI_OFFSET <= STABLE_ID_MAX }?.plus(BANGUMI_OFFSET)

    /** `MediaEntry.malId` alanında gerçek bir MAL ID'si mi saklı? */
    fun realMalIdOf(storedId: Int?): Int? = storedId?.takeIf { it in 1..99_999_999 }

    fun isAnimeType(type: MediaType?): Boolean = type == MediaType.Anime

    /** Bangumi条目 türü/platformu → Kitsugi [MediaType]. */
    fun mediaTypeFor(subjectType: Int, platform: String? = null): MediaType = when (subjectType) {
        BangumiApiClient.SubjectType.BOOK -> MediaType.Manga
        BangumiApiClient.SubjectType.ANIME -> if (isMoviePlatform(platform)) MediaType.Movie else MediaType.Anime
        BangumiApiClient.SubjectType.REAL -> if (isMoviePlatform(platform)) MediaType.Movie else MediaType.TvShow
        else -> MediaType.Anime
    }

    /** Bangumi'de animasyon ve gerçek çekim filmleri ayrı tür kodları kullanır. */
    fun isMoviePlatform(platform: String?): Boolean {
        val normalized = platform?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
        return listOf("剧场版", "劇場版", "电影", "電影", "映画", "movie", "film", "cinema", "theatrical")
            .any { token -> normalized.contains(token) }
    }

    fun supportsSubjectType(subjectType: Int): Boolean = subjectType in setOf(
        BangumiApiClient.SubjectType.ANIME,
        BangumiApiClient.SubjectType.BOOK,
        BangumiApiClient.SubjectType.REAL
    )

    /** Bangumi subject type alanı → Kitsugi [MediaType]. */
    fun subjectTypeFor(mediaType: MediaType?): Int = when (mediaType) {
        MediaType.Manga -> BangumiApiClient.SubjectType.BOOK
        MediaType.Movie, MediaType.TvShow -> BangumiApiClient.SubjectType.REAL
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
        allowNetwork: Boolean = true,
        subjectType: Int? = null
    ): Int? {
        rawIdFromStable(storedId)?.let { return it }
        if (context == null) return null

        val expectedSubjectType = subjectType ?: if (isAnime) {
            BangumiApiClient.SubjectType.ANIME
        } else {
            BangumiApiClient.SubjectType.BOOK
        }

        // 2) Yerel eşleme önbelleği (anime, kitap ve REAL subject'leri ayrı tutulur).
        val malId = realMalIdOf(storedId)
        if (malId != null) {
            val cached = runCatching { BangumiLocalMappingCache.get(context, malId, expectedSubjectType) }
                .getOrElse { null }
            cached?.let { return it }
        }

        if (!allowNetwork) return null

        // 3) Sıkı başlık araması — yalnızca tek ve net eşleşmede güvenli
        val query = title?.trim().orEmpty()
        if (query.isBlank()) return null
        val token = runCatching { BangumiAuthStore.getValidToken(context) }.getOrNull()
        val page = runCatching {
            BangumiApiClient.searchSubjects(
                keyword = query,
                token = token,
                types = listOf(expectedSubjectType),
                limit = 10
            )
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Bangumi title lookup failed for '$query': ${error.message}")
            return null
        }
        val candidates = page.data.filter { subject ->
            subject.type == expectedSubjectType && (
                expectedYear == null || subject.date?.take(4)?.toIntOrNull() == expectedYear ||
                    matchesTitle(subject.name, query) || matchesTitle(subject.nameCn, query)
                )
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
            runCatching { BangumiLocalMappingCache.put(context, malId, expectedSubjectType, unique.id) }
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

    private fun subjectTypeKey(subjectType: Int): String = when (subjectType) {
        BangumiApiClient.SubjectType.ANIME -> "a"
        BangumiApiClient.SubjectType.BOOK -> "m"
        BangumiApiClient.SubjectType.REAL -> "r"
        else -> "t$subjectType"
    }

    private fun key(malId: Int, subjectType: Int): String = "bgm_${subjectTypeKey(subjectType)}_$malId"

    private fun legacyKey(malId: Int, isAnime: Boolean) = "bgm_${if (isAnime) "a" else "m"}_$malId"

    fun get(context: Context, malId: Int, subjectType: Int): Int? {
        val preferences = prefs(context)
        val current = preferences.getInt(key(malId, subjectType), 0).takeIf { it > 0 }
        if (current != null) return current
        // Read values written by older app versions that used a boolean anime/book key.
        return when (subjectType) {
            BangumiApiClient.SubjectType.ANIME -> preferences.getInt(legacyKey(malId, true), 0).takeIf { it > 0 }
            BangumiApiClient.SubjectType.BOOK -> preferences.getInt(legacyKey(malId, false), 0).takeIf { it > 0 }
            else -> null
        }
    }

    fun get(context: Context, malId: Int, isAnime: Boolean): Int? = get(
        context,
        malId,
        if (isAnime) BangumiApiClient.SubjectType.ANIME else BangumiApiClient.SubjectType.BOOK
    )

    fun put(context: Context, malId: Int, subjectType: Int, subjectId: Int) {
        if (subjectId <= 0) return
        prefs(context).edit().putInt(key(malId, subjectType), subjectId).apply()
    }

    fun put(context: Context, malId: Int, isAnime: Boolean, subjectId: Int) = put(
        context,
        malId,
        if (isAnime) BangumiApiClient.SubjectType.ANIME else BangumiApiClient.SubjectType.BOOK,
        subjectId
    )

    fun remove(context: Context, malId: Int, isAnime: Boolean) {
        prefs(context).edit().remove(legacyKey(malId, isAnime)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
