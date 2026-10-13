package com.kitsugi.animelist.data.remote

import androidx.room.withTransaction
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Kütüphane (Listem) satırlarındaki Bangumi kayıtlarının **Latin adlarını tamamlar**.
 *
 * Neden gerekli: Bangumi koleksiyon ucu (`GET /v0/users/{user}/collections`) kısaltılmış条目
 * döndürür — yalnızca `name` (çoğu zaman Japonca) ve `name_cn` vardır, `infobox` **yoktur**.
 * Bu yüzden `BangumiImportManager` satırları `titleEnglish` boş olarak yazar ve "Listem"
 * ekranı, kullanıcı Romaji/İngilizce başlık dilini seçmiş olsa bile CJK adı gösterirdi.
 *
 * Çözüm üç katmanlıdır ve hiçbiri ekranı bekletmez:
 *  1. [BangumiTitleCache] — kayıt herhangi bir ekranda çözüldüyse ağ isteği yapılmaz.
 *  2. [BangumiSubjectTitleResolver] — subject infobox'ı (`英文名` / `罗马字` / `别名`) ve
 *     gerekirse AniList birebir eşleşmesi; sonuçlar hem önbelleğe hem veritabanına yazılır.
 *  3. Sonuç kalıcı olduğu için bir sonraki açılışta hiçbir iş yapılmaz: satır artık Latin
 *     ad taşıdığından [pendingRequests] listesine hiç girmez.
 *
 * Yazma yolu doğrudan DAO'dur (`updateAll`): [com.kitsugi.animelist.data.local.MediaEntryRepository.update]
 * kullanılsaydı her başlık düzeltmesi harici kaynaklara (MAL/AniList/…) yazma denemesi
 * tetiklerdi. Başlık yerelleştirmesi bir **yerel görünüm** iyileştirmesidir, senkron olayı değil.
 *
 * `title` alanına dokunulur çünkü Bangumi'de saklanan özgün ad zaten o alandadır; özgün ad
 * kaybolmaz, `titleJapanese` alanına taşınır ve "Ana Dilde" seçeneği çalışmaya devam eder.
 */
internal object BangumiLibraryTitleHydrator {

    private const val TAG = "BangumiLibTitles"

    /** Her tur [BangumiSubjectTitleResolver]'in kendi bütçesine uyar; toplam tur sayısı sınırlıdır. */
    private const val MAX_ROUNDS = 4

    /** Aynı anda tek tarama: ekran yeniden açıldığında mükerrer ağ trafiği olmaz. */
    private val mutex = Mutex()

    /**
     * Veritabanındaki Bangumi satırlarını tarar ve eksik Latin adları tamamlar.
     * Çağıran taraf bunu arka planda (IO) çalıştırmalıdır; hata durumunda sessizce durur.
     */
    suspend fun hydrate(database: KitsugiDatabase) {
        mutex.withLock { hydrateLocked(database) }
    }

    private suspend fun hydrateLocked(database: KitsugiDatabase) {
        BangumiTitleCache.warmUp()
        val dao = database.mediaEntryDao()

        repeat(MAX_ROUNDS) { round ->
            val pending = pendingRequests(dao.getAll())
            if (pending.isEmpty()) return

            val resolved = try {
                // İlk tur "öncelikli" bütçeyle (kısa zaman aşımı) çalışır: görünür satırlar
                // hızlı kazanır. Sonraki turlar uzun arka plan bütçesini kullanır.
                BangumiSubjectTitleResolver.resolve(pending, background = round > 0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Bangumi kütüphane başlıkları çözülemedi: ${error.message}")
                return
            }

            // Hiçbir veritabanı alanı değişmediyse (örn. yalnızca Romaji bulunabildi) sonraki
            // turları boşa çalıştırmayalım; İngilizce eksikleri bir sonraki gerçek tetikte denenir.
            if (resolved.isEmpty() || !applyResolvedTitles(database, resolved)) return
        }
    }

    /**
     * Latin adı henüz bilinmeyen Bangumi satırları için çözümleme istekleri.
     *
     * `internal` — birim testi bu seçimin doğru olduğunu doğrular (bkz.
     * `BangumiLibraryTitleHydratorTest`). Network/DB'ye dokunmayan saf bir fonksiyondur.
     */
    internal fun pendingRequests(entities: List<MediaEntryEntity>): List<BangumiSubjectTitleResolver.Request> {
        val requests = LinkedHashMap<Int, BangumiSubjectTitleResolver.Request>()
        for (entity in entities) {
            if (!isBangumi(entity)) continue
            val rawId = BangumiIdNamespace.rawIdOrNull(entity.malId) ?: continue
            if (rawId <= 0 || requests.containsKey(rawId)) continue

            val current = entity.title.trim()
            val cached = BangumiTitleCache.get(rawId)
            val english = entity.titleEnglish?.trim()?.takeIf { PreferenceHelpers.isLatinText(it) }
                ?: cached?.english
            val romaji = current.takeIf { PreferenceHelpers.isLatinText(it) } ?: cached?.romaji
            val hasEnglish = BangumiSubjectTitleResolver.hasReliableEnglish(
                BangumiTitleCache.LatinTitles(romaji, english, entity.titleJapanese ?: cached?.native)
            )
            // Latin/romaji başlık tek başına yeterli değil: English seçiliyken gerçek İngilizce
            // karşılık başka kaynakta olabilir. Ancak özgün (Bangumi) başlığı biliyorsak ararız.
            if (hasEnglish) continue

            val nativeTitle = listOfNotNull(
                entity.titleJapanese?.trim()?.takeIf { it.isNotEmpty() },
                current.takeIf { it.isNotEmpty() }
            ).firstOrNull { PreferenceHelpers.hasCjkCharacters(it) } ?: continue

            requests[rawId] = BangumiSubjectTitleResolver.Request(
                rawId = rawId,
                nativeTitle = nativeTitle,
                mediaType = mediaTypeOf(entity)
            )
        }
        return requests.values.toList()
    }

    /** Çözülen adları veritabanına tek transaction'da yazar (Room akışı bir kez tetiklenir). */
    private suspend fun applyResolvedTitles(
        database: KitsugiDatabase,
        resolved: Map<Int, BangumiTitleCache.LatinTitles>
    ): Boolean {
        val dao = database.mediaEntryDao()
        val updates = ArrayList<MediaEntryEntity>()
        for (entity in dao.getAll()) {
            if (!isBangumi(entity)) continue
            val rawId = BangumiIdNamespace.rawIdOrNull(entity.malId) ?: continue
            val latin = resolved[rawId] ?: continue

            val romaji = latin.romaji?.trim()?.takeIf { it.isNotEmpty() }
            val english = latin.english?.trim()?.takeIf { it.isNotEmpty() }
            val native = latin.native?.trim()?.takeIf { it.isNotEmpty() }

            val currentTitle = entity.title
            val latinTitle = romaji ?: english
            val nextTitle = if (PreferenceHelpers.hasCjkCharacters(currentTitle) && latinTitle != null) {
                latinTitle
            } else {
                currentTitle
            }
            val nextEnglish = english ?: entity.titleEnglish
            // Özgün ad asla kaybolmaz: "Ana Dilde" seçeneği bu alandan okunur.
            val nextJapanese = entity.titleJapanese?.takeIf { it.isNotBlank() }
                ?: native
                ?: currentTitle.takeIf { PreferenceHelpers.hasCjkCharacters(it) }

            val updated = entity.copy(
                title = nextTitle,
                titleEnglish = nextEnglish,
                titleJapanese = nextJapanese
            )
            if (updated != entity) updates += updated
        }
        if (updates.isEmpty()) return false
        database.withTransaction { dao.updateAll(updates) }
        return true
    }

    private fun isBangumi(entity: MediaEntryEntity): Boolean =
        entity.source.trim().lowercase(Locale.ROOT) == BangumiIdNamespace.SOURCE

    private fun mediaTypeOf(entity: MediaEntryEntity): MediaType =
        when (entity.type.trim().lowercase(Locale.ROOT)) {
            "manga" -> MediaType.Manga
            "movie" -> MediaType.Movie
            "tvshow", "tv" -> MediaType.TvShow
            else -> MediaType.Anime
        }
}
