package com.kitsugi.animelist.data.auth

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.data.remote.SimklApiClient
import com.kitsugi.animelist.data.remote.SimklSyncContract
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kitsugi.animelist.data.local.toEntity

/**
 * Simkl ↔ Kitsugi uygulaması arasında çift yönlü senkronizasyon yöneticisi.
 *
 * Referans mimariler:
 * - NyanTV/SimklService.kt → updateEntry, fetchUserMovies/Shows, status mapping
 * - scrob → history/sync geri yazma (POST /sync/history)
 * - SyncMeta → dry-run, diagnostics, concurrency kontrolü
 * - Showly → Trakt = Simkl katmanı, TMDB = metadata katmanı ayrımı
 */
object SimklSyncManager {

    private const val TAG = "SimklSyncManager"
    private val simklApiClient = SimklApiClient()

    // ── Durum Dönüşüm Yardımcıları (NyanTV simklStatusToAL referans) ────────────

    /** Simkl status string'ini uygulama WatchStatus'una çevirir */
    fun simklStatusToWatchStatus(simklStatus: String?): WatchStatus = when (simklStatus?.lowercase()) {
        "watching", "reading", "rewatching" -> WatchStatus.Watching
        "completed"                         -> WatchStatus.Completed
        "hold", "on_hold", "paused"         -> WatchStatus.Paused
        "dropped"                           -> WatchStatus.Dropped
        "plantowatch", "plan_to_watch", "planning" -> WatchStatus.Planned
        else                                -> WatchStatus.Planned
    }

    /** Uygulama WatchStatus'unu Simkl status string'ine çevirir (NyanTV alStatusToSimkl referans) */
    fun watchStatusToSimkl(status: WatchStatus?, mediaType: MediaType? = null): String {
        val s = when (status) {
            WatchStatus.Watching, WatchStatus.Repeating -> "watching"
            WatchStatus.Completed -> "completed"
            WatchStatus.Paused    -> "hold"
            WatchStatus.Dropped   -> "dropped"
            WatchStatus.Planned   -> "plantowatch"
            else                  -> "plantowatch"
        }
        return if (mediaType == MediaType.Movie) {
            when (s) {
                "watching", "completed" -> "completed"
                "hold", "plantowatch"   -> "plantowatch"
                "dropped"               -> "dropped"
                else                    -> "plantowatch"
            }
        } else {
            s
        }
    }

    /** MediaType'a göre Simkl API tipini döner */
    fun mediaTypeToSimklType(mediaType: MediaType): String = when (mediaType) {
        MediaType.Movie  -> "movies"
        MediaType.TvShow -> "shows"
        MediaType.Anime  -> "anime" // Logical category; write envelopes use "shows".
        else             -> "shows"
    }

    // ── Toplu Senkronizasyon (Batch Sync) ──────────────────────────────────────────

    /**
     * Çoklu MediaEntry listesini Simkl API'sine tek veya az sayıda toplu istek ile gönderir.
     * Uses conservative 35-item batches; delays here do not serialize other callers.
     *
     * Sonuç kayıt bazında raporlanır: Simkl'in `not_found` ile geri gönderdiği öğeler
     * [SyncResult.unmatchedTitles] içinde, HTTP/ağ seviyesinde hiç yazılamayan kayıtlar
     * [SyncResult.failedCount] içinde sayılır. Kısmi eşleşme artık tüm grubun hatası değildir.
     */
    suspend fun syncBatchToSimkl(
        context: Context,
        entries: List<MediaEntry>
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(
                messages = emptyList(),
                errors = listOf("Simkl hesabı bağlı değil"),
                failedCount = entries.size
            )
        }
        if (entries.isEmpty()) {
            return@withContext SyncResult(messages = emptyList(), errors = emptyList())
        }

        val unsupportedTitles = mutableListOf<String>()
        val unsafeIdentityTitles = mutableListOf<String>()
        val batchItems = entries.mapNotNull { entry ->
            if (entry.type == MediaType.Manga) {
                unsupportedTitles.add(entry.displayTitle())
                return@mapNotNull null
            }
            val realMalId = entry.malId?.takeIf { entry.type == MediaType.Anime && it > 0 && it < 100_000_000 }
            val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
                entry.malId - 100_000_000
            } else null
            // KitsuImportManager encodes IDs with this offset. After cross-sync resolves
            // a real MAL ID, source may still be "kitsu"; never send that MAL ID as kitsu.
            val rawKitsuId = entry.malId?.takeIf { it in 300_000_001..399_999_999 }?.minus(300_000_000)
            val simklId = entry.simklId?.takeIf { it > 0 } ?: 0
            val effectiveTitle = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title

            // GÜVENLİK: Anime için güvenilir kimlik yalnızca Simkl veya MAL kimliğidir. İkisi de yokken
            // başlık/yıl ya da TMDB ile eşleştirme, aynı adlı dizileri/filmleri veya franchise'ın başka
            // sezonunu kullanıcının listesine ekleyebiliyordu. Bu kayıtlar kimliksiz gönderilmez;
            // yalnızca AniList/Kitsu kimliği varsa başlık eşleştirmesi kapalı olarak denenir.
            val isAnime = entry.type == MediaType.Anime
            val hasSafeAnimeIdentity = simklId > 0 || realMalId != null
            val hasSecondaryAnimeIdentity = aniListId != null || rawKitsuId != null
            val hasNonAnimeIdentity = simklId > 0 || (entry.tmdbId != null && entry.tmdbId > 0) || effectiveTitle.isNotBlank()

            when {
                isAnime && !hasSafeAnimeIdentity && !hasSecondaryAnimeIdentity -> {
                    unsafeIdentityTitles.add(entry.displayTitle())
                    null
                }
                !isAnime && !hasNonAnimeIdentity -> {
                    unsupportedTitles.add(entry.displayTitle())
                    null
                }
                else -> SimklApiClient.SimklBatchEntry(
                    type = mediaTypeToSimklType(entry.type),
                    status = watchStatusToSimkl(entry.status, entry.type),
                    simklId = simklId,
                    malId = realMalId,
                    tmdbId = if (isAnime) null else entry.tmdbId,
                    aniListId = aniListId,
                    kitsuId = rawKitsuId,
                    title = effectiveTitle,
                    year = entry.year,
                    progress = entry.progress,
                    score = entry.score,
                    titleMatchingAllowed = !isAnime || hasSafeAnimeIdentity
                )
            }
        }

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val unmatchedTitles = mutableListOf<String>()
        val unsupportedProgressTitles = mutableListOf<String>()
        val historyUnmatchedTitles = mutableListOf<String>()
        val ratingUnmatchedTitles = mutableListOf<String>()
        var totalAdded = 0
        // Güvenli kimliği olmadığı için gönderilmeyen animeler "bulunamadı/atlandı" sayılır (hata değil).
        var totalNotFound = unsafeIdentityTitles.size
        var totalFailed = unsupportedTitles.size
        if (unsupportedTitles.isNotEmpty()) {
            warnings.add("${unsupportedTitles.size} öğe gönderilemedi (desteklenmeyen tür veya eksik kimlik/başlık): ${unsupportedTitles.take(5).joinToString()}")
        }
        if (unsafeIdentityTitles.isNotEmpty()) {
            unmatchedTitles.addAll(unsafeIdentityTitles)
            warnings.add("${unsafeIdentityTitles.size} anime güvenli kimlik (Simkl/MAL) olmadığı için gönderilmedi; yanlış içerik eklenmesin diye başlıkla eşleştirme yapılmaz: ${unsafeIdentityTitles.take(5).joinToString()}")
        }

        // Conservative batch size; not a claim about a documented API maximum.
        val chunks = batchItems.chunked(35)
        for ((idx, chunk) in chunks.withIndex()) {
            val groupLabel = if (chunks.size > 1) "Simkl alt grubu ${idx + 1}/${chunks.size}" else "Simkl grubu"
            try {
                // History/ratings can change the watchlist status, so apply the intended status LAST.
                val withProgress = chunk.filter { it.progress > 0 }
                val tvProgress = withProgress.filter { it.type == "shows" }
                if (tvProgress.isNotEmpty()) {
                    unsupportedProgressTitles.addAll(tvProgress.map { it.title ?: "(başlıksız)" })
                }
                val supportedProgress = withProgress.filter { it.type != "shows" }
                if (supportedProgress.isNotEmpty()) {
                    val historyRes = simklApiClient.historyBatchReceipt(token, supportedProgress)
                    if (historyRes.transportFailed) {
                        warnings.add("$groupLabel: izleme geçmişi gönderilemedi (${historyRes.errorMessage ?: "bilinmeyen hata"})")
                    } else if (historyRes.notFoundCount > 0) {
                        historyUnmatchedTitles.addAll(describeUnmatched(historyRes.notFoundItems, supportedProgress))
                    }
                    kotlinx.coroutines.delay(1200L)
                }
                val withScore = chunk.filter { it.score != null && it.score > 0 }
                if (withScore.isNotEmpty()) {
                    val ratingRes = simklApiClient.ratingsBatchReceipt(token, withScore)
                    if (ratingRes.transportFailed) {
                        warnings.add("$groupLabel: puanlar gönderilemedi (${ratingRes.errorMessage ?: "bilinmeyen hata"})")
                    } else if (ratingRes.notFoundCount > 0) {
                        ratingUnmatchedTitles.addAll(describeUnmatched(ratingRes.notFoundItems, withScore))
                    }
                    kotlinx.coroutines.delay(1200L)
                }
                val batchRes = simklApiClient.addToListBatchDetailed(token, chunk)
                when {
                    batchRes.transportFailed -> {
                        totalFailed += chunk.size
                        errors.add("$groupLabel: ${batchRes.errorMessage ?: "Bilinmeyen hata"} (${chunk.size} kayıt yazılamadı)")
                    }
                    else -> {
                        totalAdded += batchRes.addedCount
                        totalNotFound += batchRes.notFoundCount
                        if (batchRes.notFoundCount > 0) {
                            unmatchedTitles.addAll(describeUnmatched(batchRes.notFoundItems, chunk))
                        }
                        val suffix = if (batchRes.notFoundCount > 0) ", ${batchRes.notFoundCount} kayıt Simkl'de bulunamadı." else "."
                        messages.add("$groupLabel: ${batchRes.addedCount} liste kaydı onaylandı$suffix")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
                throw e
            } catch (e: Exception) {
                totalFailed += chunk.size
                errors.add("$groupLabel hatası: ${e.message ?: e.javaClass.simpleName} (${chunk.size} kayıt yazılamadı)")
            }
            if (idx < chunks.size - 1) {
                // Rate limit (1 req/sn) aşmamak için bekle
                kotlinx.coroutines.delay(1200L)
            }
        }

        if (historyUnmatchedTitles.isNotEmpty()) {
            warnings.add("${historyUnmatchedTitles.size} kaydın bölüm ilerlemesi Simkl'de eşleşmedi: ${historyUnmatchedTitles.take(5).joinToString()}")
        }
        if (ratingUnmatchedTitles.isNotEmpty()) {
            warnings.add("${ratingUnmatchedTitles.size} kaydın puanı Simkl'de eşleşmedi: ${ratingUnmatchedTitles.take(5).joinToString()}")
        }

        SyncResult(
            messages = messages,
            errors = errors,
            addedCount = totalAdded,
            notFoundCount = totalNotFound,
            failedCount = totalFailed,
            warnings = warnings,
            unmatchedTitles = unmatchedTitles.distinct(),
            unsupportedProgressTitles = unsupportedProgressTitles.distinct()
        )
    }

    private fun MediaEntry.displayTitle(): String =
        titleEnglish?.takeIf { it.isNotBlank() } ?: title.ifBlank { "(başlıksız)" }

    /**
     * Simkl'in `not_found` ile geri gönderdiği kimlikleri gönderilen kayıtlarla eşleştirir;
     * kimlik eşleşmezse Simkl'in yankıladığı başlık/kimlik metni kullanılır.
     */
    private fun describeUnmatched(
        unmatched: List<SimklSyncContract.UnmatchedItem>,
        sent: List<SimklApiClient.SimklBatchEntry>
    ): List<String> {
        if (unmatched.isEmpty()) return emptyList()
        fun normalized(value: String?): String = value.orEmpty().lowercase().filter { it.isLetterOrDigit() }
        return unmatched.map { item ->
            val match = sent.firstOrNull { entry ->
                val simkl = item.ids["simkl"]?.toIntOrNull()
                val mal = item.ids["mal"]?.toIntOrNull()
                val tmdb = item.ids["tmdb"]?.toIntOrNull()
                val anilist = item.ids["anilist"]?.toIntOrNull()
                val kitsu = item.ids["kitsu"]?.toIntOrNull()
                (simkl != null && simkl > 0 && simkl == entry.simklId) ||
                    (mal != null && mal == entry.malId) ||
                    (tmdb != null && tmdb == entry.tmdbId) ||
                    (anilist != null && anilist == entry.aniListId) ||
                    (kitsu != null && kitsu == entry.kitsuId) ||
                    (item.ids.isEmpty() && !item.title.isNullOrBlank() && normalized(item.title) == normalized(entry.title))
            }
            if (match != null) {
                val idText = listOfNotNull(
                    match.malId?.let { "mal=$it" },
                    match.simklId.takeIf { it > 0 }?.let { "simkl=$it" },
                    match.tmdbId?.let { "tmdb=$it" },
                    match.aniListId?.let { "anilist=$it" },
                    match.kitsuId?.let { "kitsu=$it" }
                ).joinToString(", ")
                buildString {
                    append(match.title?.takeIf { it.isNotBlank() } ?: "(başlıksız)")
                    match.year?.takeIf { it > 0 }?.let { append(" ($it)") }
                    if (idText.isNotBlank()) append(" [").append(idText).append("]")
                }
            } else {
                item.describe()
            }
        }
    }

    // ── Tek Entry Senkronizasyonu ─────────────────────────────────────────────────

    /**
     * Uygulama listesindeki bir MediaEntry'yi Simkl'e yazar.
     * Simkl bağlı değilse işlem yapılmaz.
     * Referans: NyanTV/SimklService.kt updateEntry()
     */
    suspend fun syncEntryToSimkl(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl hesabı bağlı değil"))
        }

        var simklId = entry.simklId
        val realMalId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
        val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null
        val rawKitsuId = if (entry.source == "kitsu" || (entry.malId != null && entry.malId >= 300_000_000)) {
            if (entry.malId != null && entry.malId >= 300_000_000) entry.malId - 300_000_000 else entry.malId
        } else null

        // Simkl ID yoksa ve malId/tmdbId de yoksa o zaman başlığa göre Simkl ID çözümlenir
        if ((simklId == null || simklId <= 0) && realMalId == null && entry.tmdbId == null) {
            val resolvedId = simklApiClient.lookupSimklId(
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                title = entry.titleEnglish ?: entry.title,
                year = entry.year,
                mediaType = entry.type
            )
            if (resolvedId != null && resolvedId > 0) {
                simklId = resolvedId
                runCatching {
                    val dao = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context).mediaEntryDao()
                    dao.update(entry.copy(simklId = resolvedId).toEntity())
                }
            }
        }

        // Eğer simklId, malId ve tmdbId'nin hiçbiri yoksa senkronize edilemez
        if ((simklId == null || simklId <= 0) && realMalId == null && (entry.tmdbId == null || entry.tmdbId <= 0)) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl/MAL/TMDB ID bulunamadı: ${entry.title}"))
        }

        val effectiveSimklId = simklId ?: 0

        val type = mediaTypeToSimklType(entry.type)
        val simklStatus = watchStatusToSimkl(entry.status, entry.type)
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1. Listeye ekle / durumu güncelle (POST /sync/add-to-list)
        runCatching {
            simklApiClient.addToList(
                token = token,
                simklId = effectiveSimklId,
                type = type,
                status = simklStatus,
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                kitsuId = rawKitsuId,
                title = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title,
                year = entry.year
            )
        }.onSuccess { success ->
            if (success) messages.add("Simkl listesi güncellendi (${entry.title})")
            else errors.add("Simkl listesi güncellenemedi (${entry.title})")
        }.onFailure { e ->
            errors.add("Simkl addToList hatası: ${e.message}")
            Log.e(TAG, "syncEntryToSimkl addToList hatası", e)
        }

        // 2. Bölüm ilerlemesini güncelle — sadece dizi/anime için ve geçerli simklId varsa
        val progress = entry.progress
        if (progress > 0 && entry.type != MediaType.Movie && effectiveSimklId > 0) {
            runCatching {
                simklApiClient.updateEpisodeProgress(token, effectiveSimklId, season = 1, episode = progress)
            }.onSuccess { success ->
                if (success) messages.add("Simkl bölüm ilerlemesi güncellendi: Bölüm $progress")
                else errors.add("Bölüm ilerlemesi güncellenemedi")
            }.onFailure { e ->
                errors.add("Simkl bölüm ilerlemesi hatası: ${e.message}")
                Log.e(TAG, "syncEntryToSimkl updateEpisodeProgress hatası", e)
            }
        }

        // 3. Puanı Simkl'e yaz (POST /sync/ratings)
        val score = entry.score
        if (score != null && score > 0 && effectiveSimklId > 0) {
            val normalizedRating = if (score > 10) {
                kotlin.math.round(score / 10.0).toInt().coerceIn(1, 10)
            } else {
                score.coerceIn(1, 10)
            }
            runCatching {
                simklApiClient.setRating(token, effectiveSimklId, entry.type, normalizedRating)
            }.onSuccess { success ->
                if (success) messages.add("Simkl puanı güncellendi: $normalizedRating/10")
                else errors.add("Simkl puanı güncellenemedi")
            }.onFailure { e ->
                errors.add("Simkl puan hatası: ${e.message}")
                Log.e(TAG, "syncEntryToSimkl setRating hatası", e)
            }
        } else if ((score == null || score == 0) && effectiveSimklId > 0) {
            // Puan silinmişse kaldır
            runCatching {
                simklApiClient.removeRating(token, effectiveSimklId, entry.type)
            }.onFailure { e ->
                Log.w(TAG, "Simkl removeRating uyarısı: ${e.message}")
            }
        }

        SyncResult(messages = messages, errors = errors)
    }

    /**
     * Entry'yi Simkl'den siler.
     * Referans: NyanTV/SimklService.kt deleteEntry() → POST /sync/history/remove
     */
    suspend fun deleteEntryFromSimkl(
        context: Context,
        entry: MediaEntry
    ): SyncResult = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl hesabı bağlı değil"))
        }

        var simklId = entry.simklId
        val realMalId = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
        val aniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null

        if (simklId == null || simklId <= 0) {
            val resolvedId = simklApiClient.lookupSimklId(
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                title = entry.titleEnglish ?: entry.title,
                year = entry.year,
                mediaType = entry.type
            )
            if (resolvedId != null && resolvedId > 0) {
                simklId = resolvedId
            }
        }

        if (simklId == null || simklId <= 0) {
            return@withContext SyncResult(messages = emptyList(), errors = listOf("Simkl ID bulunamadı"))
        }

        val type = mediaTypeToSimklType(entry.type)
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val rawKitsuId = if (entry.source == "kitsu" || (entry.malId != null && entry.malId >= 300_000_000)) {
            if (entry.malId != null && entry.malId >= 300_000_000) entry.malId - 300_000_000 else entry.malId
        } else null

        runCatching {
            simklApiClient.removeFromList(
                token = token,
                simklId = simklId,
                type = type,
                malId = realMalId,
                tmdbId = entry.tmdbId,
                aniListId = aniListId,
                kitsuId = rawKitsuId
            )
        }.onSuccess { success ->
            if (success) messages.add("Simkl listesinden silindi (${entry.title})")
            else errors.add("Simkl listesinden silinemedi")
        }.onFailure { e ->
            errors.add("Simkl removeFromList hatası: ${e.message}")
            Log.e(TAG, "deleteEntryFromSimkl hatası", e)
        }

        SyncResult(messages = messages, errors = errors)
    }

    // ── Simkl → Uygulama İçe Aktarma ─────────────────────────────────────────────

    /**
     * Simkl kullanıcı listesini çekip JikanSearchResult listesi olarak döner.
     * type: "shows", "movies", "anime"
     * Referans: NyanTV/SimklService.kt fetchUserMovies() + fetchUserShows()
     */
    suspend fun fetchSimklWatchlist(
        context: Context,
        type: String = "shows"
    ): List<JikanSearchResult> = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) {
            Log.w(TAG, "fetchSimklWatchlist: Simkl token bulunamadı")
            return@withContext emptyList()
        }

        runCatching {
            simklApiClient.getUserWatchlist(token, type)
        }.getOrElse { e ->
            Log.e(TAG, "fetchSimklWatchlist $type hatası: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Simkl kullanıcı profil bilgilerini çeker (kullanıcı adı, avatar vb.).
     * Referans: NyanTV/SimklService.kt fetchUserProfile()
     */
    suspend fun fetchSimklUserProfile(context: Context): SimklUserProfile? = withContext(Dispatchers.IO) {
        val token = ExternalAuthManager.getSimklToken(context)
        if (token.isNullOrBlank()) return@withContext null

        runCatching {
            val json = simklApiClient.getUserProfile(token) ?: return@withContext null
            val account = json.optJSONObject("account")
            val user = json.optJSONObject("user")
            SimklUserProfile(
                id = account?.optString("id"),
                name = user?.optString("name"),
                avatarUrl = user?.optString("avatar")
            )
        }.getOrElse { e ->
            Log.e(TAG, "fetchSimklUserProfile hatası: ${e.message}", e)
            null
        }
    }

    // ── Veri Modelleri ────────────────────────────────────────────────────────────

    data class SyncResult(
        val messages: List<String>,
        val errors: List<String> = emptyList(),
        val addedCount: Int = 0,
        val notFoundCount: Int = 0,
        /** Records that could not be written at all (HTTP/network failure or unsupported payload). */
        val failedCount: Int = 0,
        /** Non-fatal findings: partial history/rating receipts, unsupported items. */
        val warnings: List<String> = emptyList(),
        /** Human-readable titles Simkl echoed back as `not_found` for the list write. */
        val unmatchedTitles: List<String> = emptyList(),
        /** TV shows whose aggregate episode count cannot be mapped to Simkl seasons. */
        val unsupportedProgressTitles: List<String> = emptyList()
    ) {
        val isSuccess: Boolean get() = errors.isEmpty()
        val hasWarnings: Boolean get() = warnings.isNotEmpty() || (messages.isNotEmpty() && errors.isNotEmpty())
    }

    data class SimklUserProfile(
        val id: String?,
        val name: String?,
        val avatarUrl: String?
    )
}
