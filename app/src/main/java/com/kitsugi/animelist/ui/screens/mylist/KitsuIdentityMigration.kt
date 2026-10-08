package com.kitsugi.animelist.ui.screens.mylist

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.MediaEntryDao
import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.data.remote.KitsuIdNamespace
import kotlinx.coroutines.CancellationException

/**
 * Eski sürümlerde Kitsu içe aktarma, MAL eşleşmesi bilinen kayıtların kimlik alanına
 * (`media_entries.malId`) gerçek MAL ID yazıyordu. Bu, sistemin geri kalanının beklediği
 * "Kitsu kaydı = 300_000_000 + kitsuId" sözleşmesini bozduğu için Listem → Kitsu sekmesinde
 * bir içeriğe tıklanınca alakasız bir yapımın ayrıntı sayfası açılıyordu.
 *
 * Bu onarım adımı mevcut veritabanındaki Kitsu kayıtlarını kanonik stableId alanına taşır:
 *  1. Yerel MAL→Kitsu eşleme önbelleği (ağ gerektirmez),
 *  2. Kitsu `mappings` API'si,
 *  3. Sıkı başlık + yıl eşleşmeli Kitsu araması.
 *
 * Kimlik hiç çözülemezse alandaki değer **SİLİNMEZ**. Silindiğinde kayıt tamamen kimliksiz
 * kalıyor, detay sayfası `entry.id` (yerel satır numarası) gibi bir değeri dış kimlik sanıp
 * "Medya detay bilgisi şu anda yüklenemedi" ekranına düşüyordu. Değer korunur çünkü:
 *  - `KitsuIdNamespace` korumaları sayesinde gerçek bir MAL ID artık Kitsu kimliği gibi
 *    yorumlanmaz; MAL/Jikan detayı ve çapraz eşitleme bu değeri kullanmaya devam eder,
 *  - sonraki bir turda (12 saat sonra) kimlik yeniden çözülebilir.
 *
 * Ayrıca eski turun kimliği tamamen SİLDİĞİ kayıtlar (malId == null) atlanmaz: başlık
 * üzerinden yeniden çözülmeye çalışılır ve çözülürse kanonik stableId yazılır. Ağ denemesi
 * kimlik doğrulamalı yapılır (bkz. KitsuApiClient.lookupKitsuId) çünkü Kitsu +18 (R18/nsfw)
 * kayıtları anonim aramalarda gizlenir.
 *
 * İşlem tek seferliktir (sürüm damgalı). Çözümü olmayan kayıt kaldıysa 12 saat sonra bir kez
 * daha denenir (o sırada Kitsu erişimi/oturumu olmuş olabilir).
 */
internal object KitsuIdentityMigration {
    private const val TAG = "KitsuIdentityMigration"
    private const val PREFS = "Kitsugi_list_filters"
    private const val KEY_DONE_VERSION = "kitsu_identity_migrated_version"
    private const val KEY_LAST_ATTEMPT = "kitsu_identity_last_attempt_ms"
    private const val MIGRATION_VERSION = 1
    private const val RETRY_COOLDOWN_MS = 12 * 60 * 60 * 1000L
    private const val MAX_NETWORK_LOOKUPS_PER_RUN = 120

    suspend fun runIfNeeded(context: Context, dao: MediaEntryDao) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DONE_VERSION, 0) >= MIGRATION_VERSION) return

        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        if (lastAttempt > 0L && now - lastAttempt < RETRY_COOLDOWN_MS) return

        val kitsuEntries = try {
            dao.getAll().filter { it.source.equals("kitsu", ignoreCase = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Kitsu kayıtları okunamadı: ${e.message}")
            return
        }

        val broken = kitsuEntries.filter { !KitsuIdNamespace.isStableId(it.malId) }
        if (broken.isEmpty()) {
            prefs.edit().putInt(KEY_DONE_VERSION, MIGRATION_VERSION).apply()
            return
        }

        val allowNetwork = ExternalAuthManager.getKitsuToken(context) != null &&
            ExternalAuthManager.getKitsuUserId(context) != null
        var networkLookups = 0
        var fixed = 0
        var unresolved = 0
        // Ağ bütçesi bittiği için bu turda denenemeyen kayıtlar (başarısız sayılmaz,
        // sonraki turda yeniden denenir).
        var deferred = 0

        val canUsePrefs = ExternalAuthManager.getKitsuUserId(context) != null
        for (entity in broken) {
            val isAnime = !entity.type.equals("Manga", ignoreCase = true)
            // Önce yerel eşleme önbelleği: depolanan değer gerçek bir MAL ID'si taşıyorsa
            // kanonik stableId ağ harcanmadan bulunur. (Kimliksiz kayıtlarda bu adım atlanır.)
            val prefMapped = if (canUsePrefs) {
                KitsuIdNamespace.realMalIdOf(entity.malId)?.let { malId ->
                    runCatching { ExternalAuthManager.getKitsuMediaIdForMal(context, malId, isAnime) }.getOrNull()
                }
            } else null
            val prefResolved = KitsuIdNamespace.stableIdOrNull(entity.malId) ?: KitsuIdNamespace.stableIdFromRaw(prefMapped)
            if (prefResolved != null) {
                if (prefResolved != entity.malId) {
                    fixed++
                    runCatching { dao.update(entity.copy(malId = prefResolved)) }
                        .onFailure { Log.w(TAG, "Kitsu kaydı güncellenemedi (id=${entity.id}): ${it.message}") }
                }
                continue
            }

            // Ağ denemesi gerekiyor ama yapılamıyorsa (oturum yok / bütçe bitti) kaydı
            // "beklemede" sayıyoruz: sürüm damgası yazılmaz, sonraki turda yeniden denenir.
            val useNetwork = allowNetwork && networkLookups < MAX_NETWORK_LOOKUPS_PER_RUN
            if (useNetwork) networkLookups++ else deferred++

            // Kimliksiz (null) kayıtlar da BAŞLIK üzerinden yeniden çözülür: eski onarım
            // turu bu alanı silmişti ve bu kayıtlar aksi halde kalıcı olarak kimliksiz
            // kalıyordu (detay sayfası "yüklenemedi" ekranına düşüyordu).
            val canonical = runCatching {
                KitsuIdNamespace.resolveCanonicalStableId(
                    context = context,
                    storedId = entity.malId,
                    realMalId = KitsuIdNamespace.realMalIdOf(entity.malId),
                    isAnime = isAnime,
                    title = entity.titleEnglish?.takeIf { it.isNotBlank() }
                        ?: entity.title.takeIf { it.isNotBlank() }
                        ?: entity.titleJapanese,
                    expectedYear = entity.year,
                    allowNetwork = useNetwork
                )
            }.getOrNull()

            if (canonical == entity.malId) {
                // Çözümleme yeni bir kimlik vermedi. Kimliksiz kayıt "çözülemedi" sayılır ki
                // sonraki turda (12 saat) bir kez daha denenebilsin.
                if (entity.malId == null) unresolved++
                continue
            }

            val updated: MediaEntryEntity = if (canonical != null) {
                fixed++
                entity.copy(malId = canonical)
            } else {
                // Çözülemedi → alandaki değer AYNEN KALIR (bkz. dosya başlığı).
                unresolved++
                entity
            }
            if (updated !== entity) {
                runCatching { dao.update(updated) }
                    .onFailure { Log.w(TAG, "Kitsu kaydı güncellenemedi (id=${entity.id}): ${it.message}") }
            }
        }

        Log.i(
            TAG,
            "Kitsu kimlik onarımı: $fixed düzeltildi, $unresolved çözülemedi, " +
                "$deferred kayıt ağ denemesi bekliyor — ağ denemesi: $networkLookups"
        )

        if (unresolved == 0 && deferred == 0) {
            prefs.edit().putInt(KEY_DONE_VERSION, MIGRATION_VERSION).apply()
        } else {
            prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        }
    }
}
