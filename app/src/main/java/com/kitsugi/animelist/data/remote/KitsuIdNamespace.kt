package com.kitsugi.animelist.data.remote

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException

/**
 * Kitsu kimlik alanı (ID namespace) tek gerçek kaynağı.
 *
 * Kitsugi'de tüm kaynakların kimlikleri `MediaEntry.malId` alanında **offset'li stableId**
 * olarak saklanır ve bu sözleşme [MediaIdentity.keys] üzerinden kümeleme (clustering),
 * birleşik kütüphane gruplaması ve önbellek anahtarları tarafından beklenir:
 *
 *  - AniList   → `id + 100_000_000`  (100M..199M)
 *  - Kitsu     → `id + 300_000_000`   (300M..399M)
 *  - Shikimori → `id + 400_000_000`   (400M..499M)
 *  - MAL       → gerçek MAL ID       (1..99M)
 *
 * Geçmişte Kitsu içe aktarma, MAL eşleşmesi bilinen kayıtlarda bu alana **gerçek MAL ID**'yi
 * yazıyordu. Sonuç: Kitsu sekmesinde bir içeriğe tıklanınca 35658 gibi bir MAL ID'si "Kitsu ID"
 * diye yorumlanıyor ve tamamen alakalı olmayan bir yapımın (örn. "Keep Your Hands Off Eizouken!")
 * ayrıntı sayfası açılıyordu. Bu dosya aynı hatanın tekrarını engellemek için tek bir doğrulama
 * noktası sunar.
 */
object KitsuIdNamespace {
    private const val TAG = "KitsuIdNamespace"

    /** Kitsu stableId ofseti — `MediaIdentity` ile birebir aynı olmalı. */
    const val KITSU_OFFSET: Int = 300_000_000

    /** Kitsu stableId aralığı (offset dahil). */
    const val STABLE_ID_MIN: Int = KITSU_OFFSET + 1
    const val STABLE_ID_MAX: Int = KITSU_OFFSET + 99_999_999

    /** Verilen değer Kitsu stableId alanına düşüyor mu? */
    fun isStableId(value: Int?): Boolean = value != null && value in STABLE_ID_MIN..STABLE_ID_MAX

    /** Değeri yalnızca Kitsu stableId aralığındaysa döndürür (zincirleme kullanımlar için). */
    fun stableIdOrNull(value: Int?): Int? = value?.takeIf { isStableId(it) }

    /** Kitsu stableId → Kitsu'nun kendi sayısal ID'si. Geçersizse null. */
    fun rawIdFromStable(stableId: Int?): Int? =
        stableId?.takeIf { it in STABLE_ID_MIN..STABLE_ID_MAX }?.minus(KITSU_OFFSET)

    /** Kitsu sayısal ID → stableId. Geçersizse null. */
    fun stableIdFromRaw(kitsuId: Int?): Int? =
        kitsuId?.takeIf { it > 0 && it + KITSU_OFFSET <= STABLE_ID_MAX }?.plus(KITSU_OFFSET)

    /**
     * Bir kaydın `malId` alanında gerçek bir MAL ID'si mi saklı?
     * (Kitsu kayıtları için: eşleştirme yapılmış ve eski/legacy import bu alanı doldurmuş olabilir.)
     * Yalnızca gerçek MAL aralığı (1..99M) kabul edilir — 100M..299M arası AniList
     * stableId uzayıdır ve MAL ID diye yorumlanamaz.
     */
    fun realMalIdOf(storedId: Int?): Int? =
        storedId?.takeIf { it in 1..99_999_999 }

    /**
     * Bir Kitsu kaydından güvenilir Kitsu stableId'sini çözer.
     *
     * Sıra, güven derecesine göre düşer:
     *  1. Kayıttaki değer zaten Kitsu aralığındaysa doğrudan kullanılır.
     *  2. Alan gerçek bir MAL ID'si taşıyorsa yerel MAL→Kitsu eşleme önbelleği denenir.
     *  3. MalId verilmişse Kitsu `mappings` tablosundan (myanimelist/{anime|manga}) çözülür.
     *  4. Son çare: sıkı başlık araması (belirsizse null döner; yanlış yapımı asla kabul etmeyiz).
     *
     * Hiçbiri tutmazsa null — çağıran taraf "kimlik belirsiz" davranmalı, rastgele bir ID denememeli.
     */
    suspend fun resolveCanonicalStableId(
        context: Context?,
        storedId: Int?,
        realMalId: Int? = null,
        isAnime: Boolean = true,
        title: String? = null,
        expectedYear: Int? = null,
        allowNetwork: Boolean = true
    ): Int? {
        stableIdOrNull(storedId)?.let { return it }

        if (context == null) return null

        val kitsuUserId = runCatching { ExternalAuthManager.getKitsuUserId(context) }.getOrNull()
        val malId = realMalId?.takeIf { it in 1..99_999_999 } ?: realMalIdOf(storedId)

        // 2) Yerel MAL→Kitsu eşleme önbelleği (kullanıcı bazlı, ağ gerektirmez)
        if (malId != null && kitsuUserId != null) {
            val cached = runCatching {
                ExternalAuthManager.getKitsuMediaIdForMal(context, malId, isAnime)
            }.getOrNull()
            cached?.let { return stableIdFromRaw(it) }
        }

        if (!allowNetwork) return null

        // 3) Kitsu resmi mappings tablosu
        if (malId != null) {
            val site = if (isAnime) "myanimelist/anime" else "myanimelist/manga"
            val mapped = runCatching { KitsuApiClient.lookupKitsuIdByExternalMapping(site, malId) }
                .getOrElse {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Kitsu mapping lookup failed for MAL $malId: ${it.message}")
                    null
                }
            if (mapped != null && mapped > 0) {
                if (kitsuUserId != null) {
                    runCatching { ExternalAuthManager.saveKitsuMediaIdForMal(context, malId, isAnime, mapped) }
                }
                return stableIdFromRaw(mapped)
            }
        }

        // 4) Sıkı başlık araması — yalnızca tek ve net eşleşmede güvenlidir
        val searchTitle = title?.trim().orEmpty()
        if (searchTitle.isNotBlank()) {
            val byTitle = runCatching {
                KitsuApiClient.lookupKitsuId(searchTitle, isAnime = isAnime, expectedYear = expectedYear)
            }.getOrElse {
                if (it is CancellationException) throw it
                Log.w(TAG, "Kitsu title lookup failed for '$searchTitle': ${it.message}")
                null
            }
            byTitle?.let { return stableIdFromRaw(it) }
        }

        return null
    }

    /** Medya türünden Kitsu tarafının "anime mi manga mı" ayrımını çıkarır. */
    fun isAnimeType(type: MediaType?): Boolean = type != MediaType.Manga
}
