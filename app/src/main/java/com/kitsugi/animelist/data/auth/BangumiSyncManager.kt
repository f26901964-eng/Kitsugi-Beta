package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bangumi (bgm.tv) kütüphane senkronizasyon yöneticisi.
 *
 * Durum eşlemesi (`收藏类型` / CollectionType):
 *
 * | Kitsugi [WatchStatus] | Bangumi | Türkçe      | Çince |
 * |-----------------------|---------|-------------|-------|
 * | Planned               | 1       | Planlandı   | 想看  |
 * | Completed             | 2       | Tamamlandı  | 看过  |
 * | Watching / Repeating  | 3       | İzleniyor   | 在看  |
 * | Paused                | 4       | Durduruldu  | 搁置  |
 * | Dropped               | 5       | Bırakıldı   | 抛弃  |
 *
 * İlerleme yazma stratejisi (önemli):
 *  - **书籍 (manga/roman):** `ep_status` / `vol_status` doğrudan PATCH'lenebilir.
 *  - **动画 (anime):** resmî sözleşme `ep_status`'u yalnızca kitap条目'ları için tanımlar.
 *    Doğru yol bölüm bazlı "打格子": `PATCH /v0/users/-/collections/{id}/episodes`
 *    ile bölüm ID'leri işaretlenir; sunucu条目 tamamlanma oranını yeniden hesaplar.
 *    [BangumiApiClient.setProgress] bu iki yolu tek fonksiyonda birleştirir.
 */
object BangumiSyncManager {
    private const val TAG = "BangumiSyncManager"

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList()
    )

    fun watchStatusToBangumi(status: WatchStatus?): Int = when (status) {
        WatchStatus.Watching -> BangumiApiClient.CollectionType.DOING
        WatchStatus.Repeating -> BangumiApiClient.CollectionType.DOING
        WatchStatus.Completed -> BangumiApiClient.CollectionType.DONE
        WatchStatus.Paused -> BangumiApiClient.CollectionType.ON_HOLD
        WatchStatus.Dropped -> BangumiApiClient.CollectionType.DROPPED
        WatchStatus.Planned -> BangumiApiClient.CollectionType.WISH
        else -> BangumiApiClient.CollectionType.WISH
    }

    fun bangumiStatusToWatchStatus(type: Int): WatchStatus = when (type) {
        BangumiApiClient.CollectionType.WISH -> WatchStatus.Planned
        BangumiApiClient.CollectionType.DONE -> WatchStatus.Completed
        BangumiApiClient.CollectionType.DOING -> WatchStatus.Watching
        BangumiApiClient.CollectionType.ON_HOLD -> WatchStatus.Paused
        BangumiApiClient.CollectionType.DROPPED -> WatchStatus.Dropped
        else -> WatchStatus.Planned
    }

    /** Bangumi puanı 0-10 arası tam sayıdır; Kitsugi 0-100 kullanıyorsa indirgenir. */
    fun toBangumiRate(score: Int?): Int? = score?.let { value ->
        if (value > 10) kotlin.math.round(value / 10.0).toInt().coerceIn(0, 10)
        else value.coerceIn(0, 10)
    }?.takeIf { it > 0 }

    /**
     * Bir [MediaEntry]'yi kullanıcının Bangumi koleksiyonuna yazar (upsert).
     *
     * Kimlik çözümü: kayıt zaten Bangumi kaynaklıysa stableId'den doğrudan `subject_id`
     * alınır; başka bir kaynaktansa (AniList/MAL/Kitsu/Shikimori) [BangumiIdNamespace.resolveBangumiId]
     * önce yerel eşleme önbelleğini, sonra sıkı başlık aramasını dener. Belirsiz durumda
     * **hiçbir şey yazılmaz** — yanlış条目'ya ilerleme basmak geri alınamaz.
     */
    suspend fun syncEntryToBangumi(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        if (entry.type != MediaType.Anime && entry.type != MediaType.Manga) {
            return@withContext SyncResult(emptyList(), listOf("Bangumi senkronu yalnızca anime/manga destekler"))
        }

        val token = BangumiAuthStore.getValidToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(emptyList(), listOf("Bangumi hesabı bağlı değil"))
        }

        val isAnime = entry.type == MediaType.Anime
        val subjectId = resolveSubjectId(context, entry, isAnime)
        if (subjectId == null) {
            return@withContext SyncResult(
                emptyList(),
                listOf("Bangumi条目 eşleşmesi bulunamadı: ${entry.title}")
            )
        }

        val status = watchStatusToBangumi(entry.status)
        val rate = toBangumiRate(entry.score)
        val progress = entry.progress
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1) Durum + puan + görünürlük: tek upsert isteği.
        //    ep_status yalnızca kitap条目'larında anlamlı olduğu için anime'de burada GÖNDERİLMEZ.
        val collectionUpdated = BangumiApiClient.upsertCollection(
            token = token,
            subjectId = subjectId,
            type = status,
            rate = rate,
            epStatus = if (isAnime) null else progress.takeIf { it > 0 },
            volStatus = if (isAnime) null else entry.volumeProgress.takeIf { it > 0 },
            private = entry.isPrivate
        )
        if (collectionUpdated) {
            messages += "Bangumi kaydı güncellendi (${entry.title})"
        } else {
            errors += "Bangumi kaydı güncellenemedi (${entry.title})"
        }

        // 2) Anime ilerlemesi: bölüm bazlı işaretleme.
        if (isAnime && progress > 0 && collectionUpdated) {
            val progressOk = runCatching {
                val subject = BangumiApiClient.getSubject(subjectId, token)
                BangumiApiClient.setProgress(token, subject, progress)
            }.getOrElse { error ->
                Log.w(TAG, "setProgress($subjectId) failed: ${error.message}")
                false
            }
            if (progressOk) {
                messages += "Bangumi bölüm ilerlemesi yazıldı: $progress bölüm (${entry.title})"
            } else {
                errors += "Bangumi bölüm ilerlemesi yazılamadı (${entry.title})"
            }
        }

        SyncResult(messages, errors)
    }

    /**
     * Bangumi'de koleksiyon kaydı **silinemez**: v0 API'sinde DELETE ucu yoktur
     * (Aniyomi/Mihon Bangumi izleyicisi de bu yüzden `DeletableAnimeTracker`'ı uygulamaz).
     *
     * Kullanıcı niyetine en yakın güvenli davranış: kaydı `抛弃` (Bırakıldı) durumuna
     * çekmek ve puanı sıfırlamak. Gerçek silme işlemi bgm.tv web arayüzünde yapılmalıdır.
     */
    suspend fun deleteEntryFromBangumi(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = BangumiAuthStore.getValidToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(emptyList(), listOf("Bangumi hesabı bağlı değil"))
        }
        val subjectId = resolveSubjectId(context, entry, entry.type == MediaType.Anime)
            ?: return@withContext SyncResult(emptyList(), listOf("Bangumi条目 eşleşmesi bulunamadı: ${entry.title}"))

        val ok = BangumiApiClient.upsertCollection(
            token = token,
            subjectId = subjectId,
            type = BangumiApiClient.CollectionType.DROPPED,
            rate = 0
        )
        if (ok) {
            SyncResult(
                messages = listOf(
                    "Bangumi API'si koleksiyon silmeyi desteklemiyor; kayıt '抛弃 (Bırakıldı)' olarak işaretlendi (${entry.title})"
                )
            )
        } else {
            SyncResult(emptyList(), listOf("Bangumi kaydı güncellenemedi (${entry.title})"))
        }
    }

    /**
     * Bangumi tarafındaki mevcut durumu okur (Kitsugi'ye doğru "bind" yönü).
     * Kayıt yoksa null döner; çağıran taraf varsayılan durumla oluşturabilir.
     */
    suspend fun fetchRemoteStatus(
        context: Context,
        entry: MediaEntry
    ): BangumiApiClient.BangumiUserCollection? = withContext(Dispatchers.IO) {
        val token = BangumiAuthStore.getValidToken(context) ?: return@withContext null
        val subjectId = resolveSubjectId(context, entry, entry.type == MediaType.Anime)
            ?: return@withContext null
        runCatching { BangumiApiClient.getUserCollection(token, subjectId) }.getOrNull()
    }

    /**
     * Kayıttan Bangumi `subject_id` çözer.
     * Önce stableId, sonra yerel eşleme önbelleği, en son sıkı başlık araması.
     */
    private suspend fun resolveSubjectId(context: Context, entry: MediaEntry, isAnime: Boolean): Int? {
        val stored = entry.malId
        BangumiIdNamespace.rawIdFromStable(stored)?.let { return it }
        if (entry.source.equals(BangumiIdNamespace.SOURCE, ignoreCase = true) && stored != null && stored > 0) {
            // Eski/legacy kayıtlarda stableId yerine ham ID yazılmış olabilir.
            return stored
        }
        return BangumiIdNamespace.resolveBangumiId(
            context = context,
            storedId = stored,
            title = listOfNotNull(entry.titleJapanese, entry.title, entry.titleEnglish)
                .firstOrNull { it.isNotBlank() },
            expectedYear = entry.year,
            isAnime = isAnime
        )
    }
}
