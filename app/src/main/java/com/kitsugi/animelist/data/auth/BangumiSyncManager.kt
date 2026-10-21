package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
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
        val collectionUpdated: Boolean = false,
        /** True when no trustworthy Bangumi subject ID could be resolved (a safe skip, not an API error). */
        val unresolvedSubject: Boolean = false
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
        entry: MediaEntry,
        /**
         * Çapraz eşitlemenin [CrossProviderIdentityResolver] ile önceden çözdüğü `subject_id`.
         * Verildiğinde tekrar arama yapılmaz; verilmediğinde [resolveSubjectId] kendi zincirini işletir.
         */
        knownSubjectId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!supportsMediaType(entry.type)) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_service_media_type_unsupported)))
        }

        val token = BangumiAuthStore.getValidToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_account_not_connected, "Bangumi")))
        }

        val isBook = entry.type == MediaType.Manga
        val subjectId = resolveSubjectId(context, entry, knownSubjectId)
        if (subjectId == null) {
            return@withContext SyncResult(
                emptyList(),
                listOf(context.getString(R.string.account_sync_bangumi_match_not_found, entry.title)),
                unresolvedSubject = true
            )
        }

        val status = watchStatusToBangumi(entry.status)
        val rate = toBangumiRate(entry.score)
        // ani-gui #264 (A16): bilinen toplam varsa ilerleme onu aşıyorsa sıkıştır
        // (Bangumi ep/fen-count kontrolüne takılmasın); bilinmiyorsa dokunulmaz.
        val progress = kotlin.math.min(entry.progress, entry.total?.takeIf { it > 0 } ?: Int.MAX_VALUE)
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1) Durum + puan + görünürlük: tek upsert isteği.
        //    ep_status yalnızca kitap条目'larında anlamlı olduğu için anime'de burada GÖNDERİLMEZ.
        val writeError = writeWithAuthRetry(context, subjectId) { activeToken ->
            BangumiApiClient.upsertCollection(
                token = activeToken,
                subjectId = subjectId,
                type = status,
                rate = rate,
                epStatus = if (isBook) progress.takeIf { it > 0 } else null,
                volStatus = if (isBook) entry.volumeProgress.takeIf { it > 0 } else null,
                // Not ve etiketler yalnızca doluysa gönderilir (boş alan uzaktaki notu silmez).
                // Sunucu kuralı: not ≤380 karakter, etiket ≤10 adet, her etiket ≥2 karakter ve
                // boşluksuz — kural dışı tek değer TÜM isteği 400 yaptığı için burada süzülür.
                comment = BangumiApiClient.sanitizeCommentForWrite(entry.notes),
                private = entry.isPrivate,
                tags = BangumiApiClient.sanitizeTagsForWrite(
                    entry.tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                )
            )
        }
        val collectionUpdated = writeError == null
        if (collectionUpdated) {
            messages += context.getString(R.string.account_sync_entry_updated, "Bangumi", entry.title)
        } else {
            Log.e(TAG, "syncEntryToBangumi($subjectId) failed: ${writeError?.message}", writeError)
            errors += context.getString(
                R.string.account_sync_entry_update_failed,
                "Bangumi",
                entry.title,
                describeWriteFailure(context, subjectId, writeError)
            )
        }

        // 2) Video subject'lerinde ilerleme bölüm bazlı işaretlenir (anime ve REAL dizi/film).
        if (!isBook && progress > 0 && collectionUpdated) {
            val progressError = writeWithAuthRetry(context, subjectId) { activeToken ->
                val subject = BangumiApiClient.getSubject(subjectId, activeToken)
                BangumiApiClient.setProgress(activeToken, subject, progress)
            }
            if (progressError == null) {
                messages += context.getString(R.string.bangumi_sync_progress_written, progress, entry.title)
            } else {
                Log.w(TAG, "setProgress($subjectId) failed: ${progressError.message}", progressError)
                errors += context.getString(
                    R.string.bangumi_sync_progress_not_written_detail,
                    entry.title,
                    describeWriteFailure(context, subjectId, progressError)
                )
            }
        }

        SyncResult(messages, errors, subjectId, collectionUpdated)
    }

    /**
     * Bir koleksiyon yazma işlemini çalıştırır ve **401 alındığında token'ı yenileyip
     * işlemi bir kez daha dener**.
     *
     * Neden gerekli: erişim token'ı sunucu tarafında iptal edilmiş (ör. kullanıcı App
     * Secret'i yeniledi, token tablosu temizlendi) ama yerel `expires_at` hâlâ ileriyi
     * gösteriyor olabilir. Bu durumda [BangumiAuthStore.getValidToken] bayat token'ı
     * döndürür, dolayısıyla **her** yazma 401 olur ve eski kod bunu yutup `false`
     * döndürdüğü için kullanıcı yalnızca "Bilinmeyen hata" görürdü.
     *
     * @return `null` → işlem başarılı; aksi halde kullanıcıya gösterilecek gerçek hata.
     */
    private suspend fun writeWithAuthRetry(
        context: Context,
        subjectId: Int,
        block: suspend (token: String) -> Unit
    ): Throwable? {
        val token = BangumiAuthStore.getValidToken(context)
            ?: return IllegalStateException(context.getString(R.string.bangumi_oturumu_sona_erdi_lutfen_bangumi_hes))
        return try {
            block(token)
            null
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error !is BangumiApiClient.BangumiApiException || !error.isUnauthorized) return error
            // 401 → zorla yenile. PAT oturumlarında yenileme yoktur; forceRefresh null döner
            // ve kullanıcı 401 metniyle yeniden bağlanmaya yönlendirilir.
            Log.w(TAG, "yazma 401 aldı (subject=$subjectId); token yenilenip bir kez daha denenecek")
            val refreshed = BangumiAuthStore.forceRefresh(context)
            if (refreshed.isNullOrBlank()) {
                Log.e(TAG, "token yenilenemedi (subject=$subjectId) — kullanıcı yeniden bağlanmalı")
                return error
            }
            try {
                block(refreshed)
                null
            } catch (retryError: Throwable) {
                if (retryError is kotlinx.coroutines.CancellationException) throw retryError
                retryError
            }
        }
    }

    /**
     * Yazma hatasını kullanıcıya anlamlı bir cümleye çevirir.
     *
     * Eski davranış: `upsertCollection` istisnayı yutup `false` döndürüyordu ve arayüz
     * her koşulda "Bilinmeyen hata" yazıyordu — 401/403/404/429 ayrımı tamamen kayıptı.
     * Bu fonksiyon sunucunun `title`/`description` alanlarını da metne katar ki
     * (ör. `"insufficient token scope"`, `"tag 最短为两个字"`) teşhis mümkün olsun.
     */
    internal fun describeWriteFailure(context: Context, subjectId: Int, error: Throwable?): String {
        if (error == null) return context.getString(R.string.account_sync_unknown_error)
        if (error !is BangumiApiClient.BangumiApiException) {
            return context.getString(
                R.string.bangumi_write_failed_network,
                error.message ?: error.javaClass.simpleName
            )
        }
        val detail = listOfNotNull(error.title, error.description)
            .joinToString(" — ")
            .takeIf { it.isNotBlank() }
        // 403'ün sebebi sunucu metninden ayırt edilir: kapsam reddi mi, hesap yasaklı mı,
        // User-Agent reddi mi? (Sunucu üçünde de 403 döner, gövde metni farklıdır.)
        val scopeDenial = detail?.contains("scope", ignoreCase = true) ?: true
        val base = when {
            // 415 → Content-Type reddi. req.JSON ara katmanı NeedLogin/NeedScope'tan önce çalışır;
            // bu yanıt token'la ilgili değildir, uygulamanın gönderdiği başlıkla ilgilidir.
            // (2.4.261 öncesi: OkHttp başlığı "application/json; charset=utf-8" yapıyordu, sunucu
            // tam eşitlik istediği için HER yazma 415 alıyordu.)
            error.isUnsupportedMediaType -> context.getString(R.string.bangumi_write_failed_unsupported_media)
            error.isUnauthorized -> context.getString(R.string.bangumi_write_failed_unauthorized)
            error.isForbidden && scopeDenial ->
                context.getString(R.string.bangumi_write_failed_scope)
            error.isForbidden -> context.getString(
                R.string.bangumi_write_failed_forbidden,
                detail ?: context.getString(R.string.account_sync_unknown_error)
            )
            error.isNotFound -> context.getString(R.string.bangumi_write_failed_not_found, subjectId)
            error.code == 400 -> context.getString(
                R.string.bangumi_write_failed_rejected,
                detail ?: context.getString(R.string.account_sync_unknown_error)
            )
            error.isRateLimited -> context.getString(R.string.bangumi_write_failed_rate_limited)
            error.code in 500..599 -> context.getString(R.string.bangumi_write_failed_server, error.code)
            else -> context.getString(
                R.string.bangumi_write_failed_http,
                error.code,
                detail ?: context.getString(R.string.account_sync_unknown_error)
            )
        }
        // Sunucunun ham metni HER durumda eklenir: kullanıcı ekran görüntüsü paylaştığında
        // sebep ("insufficient token scope", "you are not allowed to do this", "tag 最短为两个字"…)
        // tahmin edilmeden okunabilsin.
        if (detail == null) return base
        return base + context.getString(R.string.bangumi_write_failed_detail_suffix, detail)
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
        entry: MediaEntry,
        knownSubjectId: Int? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (BangumiAuthStore.getValidToken(context).isNullOrBlank()) {
            return@withContext SyncResult(emptyList(), listOf(context.getString(R.string.account_sync_account_not_connected, "Bangumi")))
        }
        val subjectId = resolveSubjectId(context, entry, knownSubjectId)
            ?: return@withContext SyncResult(
                emptyList(),
                listOf(context.getString(R.string.account_sync_bangumi_match_not_found, entry.title)),
                unresolvedSubject = true
            )

        val writeError = writeWithAuthRetry(context, subjectId) { activeToken ->
            BangumiApiClient.upsertCollection(
                token = activeToken,
                subjectId = subjectId,
                type = BangumiApiClient.CollectionType.DROPPED,
                rate = 0
            )
        }
        if (writeError == null) {
            SyncResult(
                messages = listOf(
                    context.getString(R.string.bangumi_sync_delete_fallback, entry.title)
                )
            )
        } else {
            Log.e(TAG, "deleteEntryFromBangumi($subjectId) failed: ${writeError.message}", writeError)
            SyncResult(
                emptyList(),
                listOf(
                    context.getString(
                        R.string.account_sync_entry_update_failed,
                        "Bangumi",
                        entry.title,
                        describeWriteFailure(context, subjectId, writeError)
                    )
                )
            )
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
     *
     * Sıra: çağıranın verdiği doğrulanmış kimlik → satırın stableId'si → legacy ham ID
     * (tür, başlık ve yıl ile doğrulanır) → MAL↔Bangumi eşleme önbelleği / sıkı başlık araması.
     */
    private suspend fun resolveSubjectId(
        context: Context,
        entry: MediaEntry,
        knownSubjectId: Int? = null
    ): Int? {
        knownSubjectId?.takeIf { it > 0 }?.let { return it }
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
