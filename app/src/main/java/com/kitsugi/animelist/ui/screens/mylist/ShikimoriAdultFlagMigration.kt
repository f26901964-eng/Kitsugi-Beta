package com.kitsugi.animelist.ui.screens.mylist

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.local.MediaEntryDao
import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.data.remote.ShikimoriAdultResolver
import kotlinx.coroutines.CancellationException

/**
 * Eski sürümlerde Shikimori liste içe aktarımı +18 (rx/hentai) tespiti yapamıyordu:
 * REST liste yanıtları (`/api/animes`, `/api/mangas` ve `user_rates` yanıtındaki gömülü
 * anime/manga nesneleri) `rating`/`genres` alanlarını taşımıyor; bu yüzden Shikimori'den
 * içe aktarılan tüm kayıtlar `isAdult = false` olarak veritabanına yazılmıştı. +18 blur
 * ayarı açık olsa bile Listem → Shikimori sekmesinde (ve "Tümü" sekmesinde) afişler
 * bulanıklanmıyordu.
 *
 * Bu onarım, veritabanındaki mevcut Shikimori kayıtlarını GraphQL üzerinden
 * ([ShikimoriAdultResolver], kimlik doğrulaması gerektirmez) yeniden kontrol edip +18
 * olanları işaretler. Shikimori içe aktarımı `malId` alanına hedef kimliği (MAL ID ile aynı)
 * yazdığı için kimlikler doğrudan sorgulanabilir.
 *
 * Yalnızca `isAdult` false→true yönünde yazar; mevcut yetişkin işaretleri asla kaldırılmaz.
 * Ağ kesilirse veya sorgu bütçesi biterse tur "tamamlanmamış" sayılır, sürüm damgası
 * yazılmaz ve 12 saat sonra bir kez daha denenir.
 */
internal object ShikimoriAdultFlagMigration {
    private const val TAG = "ShikimoriAdultFlagMigration"
    private const val PREFS = "Kitsugi_list_filters"
    private const val KEY_DONE_VERSION = "shikimori_adult_flag_migrated_version"
    private const val KEY_LAST_ATTEMPT = "shikimori_adult_flag_last_attempt_ms"
    private const val MIGRATION_VERSION = 1
    private const val RETRY_COOLDOWN_MS = 12 * 60 * 60 * 1000L
    private const val MAX_LOOKUPS_PER_RUN = 600

    suspend fun runIfNeeded(context: Context, dao: MediaEntryDao) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DONE_VERSION, 0) >= MIGRATION_VERSION) return

        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        if (lastAttempt > 0L && now - lastAttempt < RETRY_COOLDOWN_MS) return

        val candidates = try {
            dao.getAll().filter {
                it.source.equals("shikimori", ignoreCase = true) &&
                    !it.isAdult &&
                    (it.malId ?: 0) > 0
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Shikimori kayıtları okunamadı: ${e.message}")
            return
        }

        if (candidates.isEmpty()) {
            prefs.edit().putInt(KEY_DONE_VERSION, MIGRATION_VERSION).apply()
            return
        }

        // Ağ bütçesi: tek turda en fazla MAX_LOOKUPS_PER_RUN kimlik sorgulanır.
        val budgeted = candidates.take(MAX_LOOKUPS_PER_RUN)
        val deferred = candidates.size - budgeted.size
        val entitiesByMalId: Map<Int, List<MediaEntryEntity>> = budgeted.groupBy { it.malId!! }

        val animeIds = budgeted
            .filter { !it.type.equals("Manga", ignoreCase = true) }
            .mapNotNull { it.malId }
            .distinct()
        val mangaIds = budgeted
            .filter { it.type.equals("Manga", ignoreCase = true) }
            .mapNotNull { it.malId }
            .distinct()

        var flagged = 0
        var networkIncomplete = false

        suspend fun check(kind: ShikimoriAdultResolver.Kind, ids: List<Int>) {
            if (ids.isEmpty()) return
            val flags = ShikimoriAdultResolver.resolveAdultFlags(kind, ids)
            if (!flags.keys.containsAll(ids.toSet())) {
                // Bazı kimlikler ağ hatası nedeniyle çözülemedi — tur tamamlanmamış sayılır.
                networkIncomplete = true
                return
            }
            for ((malId, isAdult) in flags) {
                if (!isAdult) continue
                for (entity in entitiesByMalId[malId].orEmpty()) {
                    runCatching { dao.update(entity.copy(isAdult = true)) }
                        .onFailure {
                            Log.w(TAG, "Shikimori kaydı güncellenemedi (id=${entity.id}): ${it.message}")
                        }
                    flagged++
                }
            }
        }

        check(ShikimoriAdultResolver.Kind.ANIME, animeIds)
        check(ShikimoriAdultResolver.Kind.MANGA, mangaIds)

        Log.i(
            TAG,
            "Shikimori +18 işaret onarımı: $flagged kayıt işaretlendi, $deferred kayıt sonraki tura kaldı, " +
                if (networkIncomplete) "ağ kesildi, 12 saat sonra yeniden denenecek" else "tamamlandı"
        )

        if (!networkIncomplete && deferred == 0) {
            prefs.edit().putInt(KEY_DONE_VERSION, MIGRATION_VERSION).apply()
        } else {
            prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        }
    }
}
