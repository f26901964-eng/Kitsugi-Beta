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
 * Kimlik hiç çözülemezse alandaki *yanlış* değer (ör. MAL ID) Kitsu alanında tutulamaz;
 * kaydı 0'a çekiyoruz. 0 → hiçbir kimlik alanı yanlış yorumlanmaz, kayıt listemizde olduğu gibi
 * kalır ve senkron/geri yazım yolları başlık üzerinden çalışmaya devam eder.
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
    private const val MAX_NETWORK_LOOKUPS_PER_RUN = 40

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
        var skipped = 0

        val canUsePrefs = ExternalAuthManager.getKitsuUserId(context) != null
        for (entity in broken) {
            // Zaten kimliği olmayan (null) kayıt onarılacak bir şey değil; tekrar tekrar
            // denemeyi önlemek için olduğu gibi bırakıyoruz.
            if (entity.malId == null) {
                skipped++
                continue
            }
            val isAnime = !entity.type.equals("Manga", ignoreCase = true)
            // Önce yerel eşleme önbelleği: oradan çözülen kayıt için ağ harcama bütçesi yakılmaz.
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

            val useNetwork = allowNetwork && networkLookups < MAX_NETWORK_LOOKUPS_PER_RUN
            if (useNetwork) networkLookups++

            val canonical = runCatching {
                KitsuIdNamespace.resolveCanonicalStableId(
                    context = context,
                    storedId = entity.malId,
                    realMalId = KitsuIdNamespace.realMalIdOf(entity.malId),
                    isAnime = isAnime,
                    title = entity.titleEnglish?.takeIf { it.isNotBlank() } ?: entity.title,
                    expectedYear = entity.year,
                    allowNetwork = useNetwork
                )
            }.getOrNull()

            if (canonical == entity.malId) continue

            val updated: MediaEntryEntity = if (canonical != null) {
                fixed++
                entity.copy(malId = canonical)
            } else {
                // Kimliği çözülemeyen kayıtta yanlış uzaydaki ID'yi bırakmıyoruz.
                unresolved++
                entity.copy(malId = null)
            }
            runCatching { dao.update(updated) }
                .onFailure { Log.w(TAG, "Kitsu kaydı güncellenemedi (id=${entity.id}): ${it.message}") }
        }

        Log.i(
            TAG,
            "Kitsu kimlik onarımı: $fixed düzeltildi, $unresolved çözülemedi, " +
                "$skipped kimliksiz(atlandı) — ağ denemesi: $networkLookups"
        )

        if (unresolved == 0) {
            prefs.edit().putInt(KEY_DONE_VERSION, MIGRATION_VERSION).apply()
        } else {
            prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        }
    }
}
