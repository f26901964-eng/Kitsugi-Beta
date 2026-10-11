package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

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
 *  - **Video subject'leri (anime ve REAL):** resmî sözleşme `ep_status`'u yalnızca kitap条目'ları için tanımlar.
 *    Doğru yol bölüm bazlı "打格子": `PATCH /v0/users/-/collections/{id}/episodes`
 *    ile bölüm ID'leri işaretlenir; sunucu条目 tamamlanma oranını yeniden hesaplar.
 *    [BangumiApiClient.setProgress] bu iki yolu tek fonksiyonda birleştirir.
 */
object BangumiSyncManager {
    private const val TAG = "BangumiSyncManager"

    fun supportsMediaType(type: MediaType): Boolean = type in setOf(
        MediaType.Anime,
        MediaType.Manga,
        MediaType.Movie,
        MediaType.TvShow
    )

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList(),
        val subjectId: Int? = null,
        val collectionUpdated: Boolean = false
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
        if (!supportsMediaType(entry.type)) {
            return@withContext SyncResult(emptyList(), listOf(KitsugiStrings.get(R.string.bangumi_senkronu_yalnizca_anime_manga_destekler)))
        }

        val token = BangumiAuthStore.getValidToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(emptyList(), listOf(KitsugiStrings.get(R.string.bangumi_hesabi_bagli_degil)))
        }

        val isBook = entry.type == MediaType.Manga
        val subjectId = resolveSubjectId(context, entry)
        if (subjectId == null) {
            return@withContext SyncResult(
                emptyList(),
                listOf(KitsugiStrings.get(R.string.bangumi_eslesmesi_bulunamadi_x, entry.title))
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
            epStatus = if (isBook) progress.takeIf { it > 0 } else null,
            volStatus = if (isBook) entry.volumeProgress.takeIf { it > 0 } else null,
            // Not ve etiketler: yalnızca dolu değerler gönderilir (boş alan uzaktaki notu silmez).
            comment = entry.notes?.takeIf { it.isNotBlank() },
            private = entry.isPrivate,
            tags = entry.tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
        )
        if (collectionUpdated) {
            messages += "Bangumi kaydı güncellendi (${entry.title})"
        } else {
            errors += "Bangumi kaydı güncellenemedi (${entry.title})"
        }

        // 2) Video subject'lerinde ilerleme bölüm bazlı işaretlenir (anime ve REAL dizi/film).
        if (!isBook && progress > 0 && collectionUpdated) {
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

        SyncResult(messages, errors, subjectId, collectionUpdated)
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
            return@withContext SyncResult(emptyList(), listOf(KitsugiStrings.get(R.string.bangumi_hesabi_bagli_degil)))
        }
        val subjectId = resolveSubjectId(context, entry)
            ?: return@withContext SyncResult(emptyList(), listOf(KitsugiStrings.get(R.string.bangumi_eslesmesi_bulunamadi_x, entry.title)))

        val ok = BangumiApiClient.upsertCollection(
            token = token,
            subjectId = subjectId,
            type = BangumiApiClient.CollectionType.DROPPED,
            rate = 0
        )
        if (ok) {
            SyncResult(
                messages = listOf(
                    KitsugiStrings.get(R.string.bangumi_api_si_koleksiyon_silmeyi_desteklemiyor_kayit_birakildi, entry.title)
                )
            )
        } else {
            SyncResult(emptyList(), listOf(KitsugiStrings.get(R.string.bangumi_kaydi_guncellenemedi_x, entry.title)))
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
        val subjectId = resolveSubjectId(context, entry)
            ?: return@withContext null
        // `GET /v0/users/-/collections/{id}` 404 verir; gerçek kullanıcı adıyla sorulmalı.
        val username = BangumiAuthStore.resolveUsername(context) ?: return@withContext null
        runCatching { BangumiApiClient.getUserCollection(token, subjectId, username) }.getOrNull()
    }

    /**
     * Kayıttan Bangumi `subject_id` çözer.
     * Stable ID doğrudan kullanılır; legacy ham ID ise önce tür, başlık ve yıl ile doğrulanır.
     */
    private suspend fun resolveSubjectId(context: Context, entry: MediaEntry): Int? {
        val stored = entry.malId
        BangumiIdNamespace.rawIdFromStable(stored)?.let { return it }
        val expectedSubjectType = BangumiIdNamespace.subjectTypeFor(entry.type)
        val isBangumiEntry = canonicalMediaSourceId(entry.source) == BangumiIdNamespace.SOURCE
        val isLegacyRawBangumiId = isBangumiEntry && stored != null && stored in 1..99_999_999

        if (isLegacyRawBangumiId) {
            val token = BangumiAuthStore.getValidToken(context)
            val legacySubject = runCatching {
                BangumiApiClient.getSubject(stored!!, token)
            }.getOrNull()
            val storedTitles = listOfNotNull(entry.titleJapanese, entry.title, entry.titleEnglish)
                .filter { it.isNotBlank() }
                .map { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                .toSet()
            val subjectTitles = legacySubject?.let { listOf(it.name, it.nameCn) }
                .orEmpty()
                .filter { it.isNotBlank() }
                .map { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                .toSet()
            val storedYear = entry.year
            val subjectYear = legacySubject?.date?.take(4)?.toIntOrNull()
            val yearMatches = storedYear == null || subjectYear == null || kotlin.math.abs(storedYear - subjectYear) <= 1
            if (
                legacySubject?.type == expectedSubjectType &&
                storedTitles.any { it in subjectTitles } &&
                yearMatches
            ) {
                return legacySubject.id
            }
        }

        val resolverStoredId = if (isLegacyRawBangumiId) null else stored
        val candidateTitles = listOfNotNull(entry.titleJapanese, entry.title, entry.titleEnglish)
            .filter { it.isNotBlank() }
            .distinct()

        for (candidateTitle in candidateTitles) {
            val resolved = BangumiIdNamespace.resolveBangumiId(
                context = context,
                storedId = resolverStoredId,
                title = candidateTitle,
                expectedYear = entry.year,
                isAnime = entry.type == MediaType.Anime,
                subjectType = expectedSubjectType
            )
            if (resolved != null) return resolved
        }
        return null
    }
}
