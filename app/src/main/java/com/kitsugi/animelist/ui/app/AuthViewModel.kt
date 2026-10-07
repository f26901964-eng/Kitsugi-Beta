package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.data.auth.runSyncCatching
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.AniListImportManager
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import com.kitsugi.animelist.data.auth.AniListSyncManager
import com.kitsugi.animelist.data.auth.MalSyncManager
import com.kitsugi.animelist.data.auth.MalImportManager
import com.kitsugi.animelist.data.auth.SimklImportManager
import com.kitsugi.animelist.data.auth.SimklSyncManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.data.auth.KitsuImportManager
import com.kitsugi.animelist.data.auth.KitsuSyncManager
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.data.auth.ShikimoriImportManager
import com.kitsugi.animelist.data.auth.ShikimoriSyncManager
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TvSyncEntryPoint {
    fun channelSyncService(): com.kitsugi.animelist.core.recommendations.TvChannelSyncService
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    var isAniListConnected by mutableStateOf(false)
        private set

    var isMalConnected by mutableStateOf(false)
        private set

    var isSimklConnected by mutableStateOf(false)
        private set

    var isKitsuConnected by mutableStateOf(false)
        private set

    var isShikimoriConnected by mutableStateOf(false)
        private set

    var isSimklSessionExpired by mutableStateOf(false)
        private set

    var isAniListImportRunning by mutableStateOf(false)
        private set

    var isMalImportRunning by mutableStateOf(false)
        private set

    var isSimklImportRunning by mutableStateOf(false)
        private set

    var isKitsuImportRunning by mutableStateOf(false)
        private set

    var isShikimoriImportRunning by mutableStateOf(false)
        private set

    var isCrossSyncRunning by mutableStateOf(false)
        private set

    var crossSyncState by mutableStateOf(com.kitsugi.animelist.model.CrossSyncProgressState())
        private set

    fun resetCrossSyncState() {
        crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState()
    }

    var onShowMessage: ((String) -> Unit)? = null

    init {
        refreshAuthState()
    }

    fun refreshAuthState() {
        val state = ExternalAuthManager.getAuthState(context)
        isAniListConnected = state.isAniListConnected
        isMalConnected = state.isMalConnected
        isSimklConnected = state.isSimklConnected
        isKitsuConnected = state.isKitsuConnected
        isShikimoriConnected = state.isShikimoriConnected

        isSimklSessionExpired = context.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE)
            .getBoolean("simkl_session_expired", false)
    }

    fun startExternalAuth(serviceName: String) {
        ExternalAuthManager.startAuthentication(
            context = context,
            serviceName = serviceName,
            onError = { message ->
                onShowMessage?.invoke(message)
            }
        )
    }

    fun disconnectExternalAccount(serviceName: String) {
        ExternalAuthManager.disconnectAccount(
            context = context,
            serviceName = serviceName
        )

        viewModelScope.launch {
            val settings = SettingsDataStore(context)
            when (serviceName) {
                "anilist" -> settings.clearAniListProfileInfo()
                "mal" -> settings.clearMalProfileInfo()
                "simkl" -> settings.clearSimklProfileInfo()
                "kitsu" -> settings.clearKitsuProfileInfo()
                "shikimori" -> settings.clearShikimoriProfileInfo()
            }
            refreshAuthState()

            // B1.12: Clear the TV launcher channel and watch-next recommendation when disconnecting
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context, TvSyncEntryPoint::class.java
                )
                entryPoint.channelSyncService().clearAll()
                // Reconcile again from DB to show remaining active sources if any
                entryPoint.channelSyncService().reconcileFromDatabase()
            } catch (e: Exception) {
                android.util.Log.w("AuthViewModel", "Failed to clear TV channel during disconnect", e)
            }
        }

        onShowMessage?.invoke(
            when (serviceName) {
                "anilist" -> "AniList bağlantısı kesildi"
                "simkl" -> "Simkl bağlantısı kesildi"
                "kitsu" -> "Kitsu bağlantısı kesildi"
                "shikimori" -> "Shikimori bağlantısı kesildi"
                else -> "MyAnimeList bağlantısı kesildi"
            }
        )
    }

    fun importAniListAnimeList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        val token = ExternalAuthManager.getAniListToken(context)

        if (token.isNullOrBlank()) {
            onShowMessage?.invoke("AniList token bulunamadı")
            return
        }

        if (isAniListImportRunning) {
            onShowMessage?.invoke("AniList import zaten çalışıyor")
            return
        }

        isAniListImportRunning = true
        onShowMessage?.invoke("AniList listesi getiriliyor...")

        viewModelScope.launch(Dispatchers.IO) {
            val settingsDataStore = SettingsDataStore(context)

            runSyncCatching {
                runSyncCatching {
                    val profile = AniListImportManager.fetchUserProfile(token)
                    settingsDataStore.setAniListProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = profile.bannerUrl.orEmpty()
                    )
                }
                AniListImportManager.fetchAllLists(token)
            }.onSuccess { importedEntries ->
                repository.smartImport("anilist", importedEntries, allowDelete = false)

                onShowMessage?.invoke(
                    "${importedEntries.size} AniList kaydı başarıyla aktarıldı"
                )
            }.onFailure { error ->
                onShowMessage?.invoke(
                    error.message ?: "AniList içe aktarma başarısız"
                )
            }

            isAniListImportRunning = false
        }
    }

    fun importMalAnimeList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        if (isMalImportRunning) {
            onShowMessage?.invoke("MyAnimeList import zaten çalışıyor")
            return
        }

        isMalImportRunning = true
        onShowMessage?.invoke("MyAnimeList listesi getiriliyor...")

        viewModelScope.launch(Dispatchers.IO) {
            val token = ExternalAuthManager.getOrRefreshMalToken(context)

            if (token.isNullOrBlank()) {
                onShowMessage?.invoke("MyAnimeList token bulunamadı")
                isMalImportRunning = false
                return@launch
            }

            val settingsDataStore = SettingsDataStore(context)

            runSyncCatching {
                runSyncCatching {
                    val profile = MalImportManager.fetchUserProfile(token)
                    settingsDataStore.setMalProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.pictureUrl.orEmpty(),
                        bannerImageUri = ""
                    )
                }
                val showAdult = settingsDataStore.settingsFlow.first().showAdultContent
                MalImportManager.fetchAllLists(token, showAdult)
            }.onSuccess { importedEntries ->
                repository.smartImport("mal", importedEntries, allowDelete = false)

                onShowMessage?.invoke(
                    "${importedEntries.size} MyAnimeList kaydı başarıyla aktarıldı"
                )
            }.onFailure { error ->
                onShowMessage?.invoke(
                    error.message ?: "MyAnimeList içe aktarma başarısız"
                )
            }

            isMalImportRunning = false
        }
    }

    private class UnifiedSyncItem(
        var aniList: MediaEntry? = null,
        var mal: MediaEntry? = null,
        var simkl: MediaEntry? = null,
        var kitsu: MediaEntry? = null,
        var shikimori: MediaEntry? = null
    ) {
        val candidates: List<MediaEntry>
            get() = listOfNotNull(aniList, mal, simkl, kitsu, shikimori)

        val primaryTitle: String
            get() = candidates.firstOrNull()?.titleEnglish?.takeIf { it.isNotBlank() }
                ?: candidates.firstOrNull()?.title ?: "Bilinmeyen"

        val mediaType: MediaType
            get() = candidates.firstOrNull()?.type ?: MediaType.Anime
    }

    private fun Int.isRealMalId(): Boolean = this in 1..99_999_999

    private suspend fun resolveRealMalId(entry: MediaEntry, aniListToken: String?): Int? {
        val existing = entry.malId?.takeIf { it.isRealMalId() }
        if (existing != null) return existing

        val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga
        if (!isAnimeOrManga) return null

        val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null

        val rawKitsuId = entry.malId?.takeIf { it in 300_000_001..399_999_999 }?.minus(300_000_000)

        if (rawAniListId != null && !aniListToken.isNullOrBlank()) {
            val resolved = runSyncCatching {
                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("anilist")
                AniListSyncManager.resolveMalIdFromAniList(token = aniListToken, aniListId = rawAniListId)
            }.getOrNull()
            if (resolved != null && resolved.isRealMalId()) return resolved
        }

        val armMal = runSyncCatching {
            com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                malId = null,
                aniListId = rawAniListId,
                tmdbId = entry.tmdbId,
                mediaType = entry.type,
                kitsuId = rawKitsuId
            ).malId
        }.getOrNull()
        if (armMal != null && armMal.isRealMalId()) return armMal

        val searchTitle = entry.titleEnglish?.takeIf { it.isNotBlank() } ?: entry.title.takeIf { it.isNotBlank() }
        if (!searchTitle.isNullOrBlank()) {
            val jikanResults = runSyncCatching {
                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("jikan")
                com.kitsugi.animelist.data.remote.JikanSearchClient().searchMALOnly(
                    query = searchTitle,
                    mediaType = entry.type
                )
            }.getOrNull()
            val jikanMal = jikanResults?.filter { res ->
                res.type == entry.type && (entry.year == null || res.year == entry.year) &&
                    listOfNotNull(res.title, res.titleEnglish, res.titleJapanese).any {
                        com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) ==
                            com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(searchTitle)
                    }
            }?.singleOrNull()?.let { res -> (res.realMalId ?: res.malId).takeIf { it.isRealMalId() } }
            if (jikanMal != null) return jikanMal
        }

        return null
    }

    fun syncPlatforms(
        repository: MediaEntryRepository
    ) {
        if (isCrossSyncRunning) {
            onShowMessage?.invoke("Eşitleme zaten devam ediyor...")
            return
        }

        isCrossSyncRunning = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
            val syncSettings = SettingsDataStore(context).settingsFlow.first()

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val kitsuUserId = ExternalAuthManager.getKitsuUserId(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            val shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)

            val isAniList = syncSettings.syncEnabledAnilist && !aniListToken.isNullOrBlank()
            val isMal = syncSettings.syncEnabledMal && !malToken.isNullOrBlank()
            val isSimkl = syncSettings.syncEnabledSimkl && !simklToken.isNullOrBlank()
            val isKitsu = syncSettings.syncEnabledKitsu && !kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()
            val isShikimori = syncSettings.syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && shikimoriUserId != null

            val connectedPlatforms = mutableListOf<String>()
            if (isAniList) connectedPlatforms.add("AniList")
            if (isMal) connectedPlatforms.add("MyAnimeList")
            if (isSimkl) connectedPlatforms.add("Simkl")
            if (isKitsu) connectedPlatforms.add("Kitsu")
            if (isShikimori) connectedPlatforms.add("Shikimori")

            if (connectedPlatforms.size < 2) {
                onShowMessage?.invoke("Eşitleme için en az iki hesap bağlı ve eşitlemesi açık olmalıdır.")
                isCrossSyncRunning = false
                crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
                    errorMessage = "En az iki hesap bağlı ve eşitlemesi açık olmalıdır."
                )
                return@launch
            }

            val statsMap = mutableMapOf<String, com.kitsugi.animelist.model.CrossPlatformStats>()
            connectedPlatforms.forEach { name ->
                statsMap[name] = com.kitsugi.animelist.model.CrossPlatformStats(platformName = name)
            }

            val recentLogs = mutableListOf<com.kitsugi.animelist.model.CrossSyncLogEntry>()

            fun logEvent(
                platform: String,
                message: String,
                isAddition: Boolean = false,
                isUpdate: Boolean = false,
                isError: Boolean = false
            ) {
                val entry = com.kitsugi.animelist.model.CrossSyncLogEntry(
                    platform = platform,
                    message = message,
                    isAddition = isAddition,
                    isUpdate = isUpdate,
                    isError = isError
                )
                recentLogs.add(entry)
                if (recentLogs.size > 500) recentLogs.removeAt(0)
            }

            fun updateProgress(
                step: String,
                detail: String = "",
                processed: Int = crossSyncState.processedItems,
                total: Int = crossSyncState.totalItems
            ) {
                crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
                    isRunning = true,
                    isCompleted = false,
                    currentStep = step,
                    currentDetail = detail,
                    processedItems = processed,
                    totalItems = total,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList()
                )
            }

            updateProgress("Hesaplar taranıyor...", "Bağlı hesaplardan kütüphane listeleri çekiliyor...")

            val settingsDataStore = SettingsDataStore(context)
            runSyncCatching {
                val showAdult = settingsDataStore.settingsFlow.first().showAdultContent

                // ── FAZ 1: Kütüphane Listelerini Çek ──────────────────────────────
                val aniListEntries = if (isAniList) {
                    updateProgress("AniList listesi alınıyor...", "Kütüphaneniz okunuyor...")
                    val list = AniListImportManager.fetchAllLists(aniListToken!!)
                    statsMap["AniList"] = statsMap["AniList"]!!.copy(initialCount = list.size)
                    logEvent("AniList", "AniList kütüphanesinden ${list.size} kayıt alındı.")
                    list
                } else emptyList()

                val malEntries = if (isMal) {
                    updateProgress("MyAnimeList listesi alınıyor...", "Kütüphaneniz okunuyor...")
                    val list = MalImportManager.fetchAllLists(malToken!!, showAdult)
                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.copy(initialCount = list.size)
                    logEvent("MyAnimeList", "MyAnimeList kütüphanesinden ${list.size} kayıt alındı.")
                    list
                } else emptyList()

                val simklEntries = if (isSimkl) {
                    updateProgress("Simkl listesi alınıyor...", "Anime, dizi ve filmler okunuyor...")
                    val list = SimklImportManager.fetchAllLists(simklToken!!)
                    statsMap["Simkl"] = statsMap["Simkl"]!!.copy(initialCount = list.size)
                    logEvent("Simkl", "Simkl kütüphanesinden ${list.size} kayıt alındı.")
                    list
                } else emptyList()

                val kitsuEntries = if (isKitsu) {
                    updateProgress("Kitsu listesi alınıyor...", "Kütüphaneniz okunuyor...")
                    val list = KitsuImportManager.fetchAllLists(context, kitsuToken!!, kitsuUserId!!)
                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.copy(initialCount = list.size)
                    logEvent("Kitsu", "Kitsu kütüphanesinden ${list.size} kayıt alındı.")
                    list
                } else emptyList()

                val shikimoriEntries = if (isShikimori) {
                    updateProgress("Shikimori listesi alınıyor...", "Kütüphaneniz okunuyor...")
                    val list = ShikimoriImportManager.fetchAllLists(context, shikimoriToken!!, shikimoriUserId!!)
                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.copy(initialCount = list.size)
                    logEvent("Shikimori", "Shikimori kütüphanesinden ${list.size} kayıt alındı.")
                    list
                } else emptyList()

                // ── FAZ 2: Çoklu İndeks ile Unified Eşleştirme ────────────────────
                updateProgress("İçerikler çapraz eşleştiriliyor...", "Tüm platformlardaki kayıtlar birleştiriliyor...")

                val unifiedItems = mutableListOf<UnifiedSyncItem>()
                fun clusterEntry(entry: MediaEntry, assignToPlatform: (UnifiedSyncItem) -> Unit) {
                    val matches = unifiedItems.filter { item ->
                        item.candidates.all { com.kitsugi.animelist.model.MediaIdentity.sameMedia(it, entry) }
                    }
                    require(matches.size <= 1) { "Belirsiz çapraz eşleşme: ${entry.title}; otomatik yazma durduruldu" }
                    val item = matches.singleOrNull() ?: UnifiedSyncItem().also { unifiedItems.add(it) }
                    assignToPlatform(item)
                }

                aniListEntries.forEach { e -> clusterEntry(e) { it.aniList = e } }
                malEntries.forEach { e -> clusterEntry(e) { it.mal = e } }
                simklEntries.forEach { e -> clusterEntry(e) { it.simkl = e } }
                kitsuEntries.forEach { e -> clusterEntry(e) { it.kitsu = e } }
                shikimoriEntries.forEach { e -> clusterEntry(e) { it.shikimori = e } }

                logEvent("Analiz", "Toplam ${unifiedItems.size} tekil içerik tespit edildi. Eşitleme başlatılıyor...")
                updateProgress("Eşitleme başlatılıyor...", "Toplam ${unifiedItems.size} içerik eşitlenecek", processed = 0, total = unifiedItems.size)

                val simklEntriesToSync = mutableListOf<MediaEntry>()


                // ── FAZ 3: Eksikleri Tamamlama ve Alan Senkronizasyonu (Asla Silme Yok) ──
                for ((index, item) in unifiedItems.withIndex()) {
                    val candidates = item.candidates
                    if (candidates.isEmpty()) continue


                    val newest = candidates.maxByOrNull { it.updatedAt } ?: candidates.first()
                    val isAnimeOrManga = newest.type == MediaType.Anime || newest.type == MediaType.Manga

                    // 1. En yüksek ilerleme (bölüm sayısı asla gerilemez)
                    val maxProgress = candidates.maxOf { it.progress }
                    val maxVolumeProgress = candidates.maxOf { it.volumeProgress }

                    // 2. En gelişmiş izleme durumu
                    val totalCount = candidates.firstNotNullOfOrNull { it.total?.takeIf { t -> t > 0 } }
                    val bestStatus = when {
                        totalCount != null && maxProgress >= totalCount -> WatchStatus.Completed
                        candidates.any { it.status == WatchStatus.Completed } && (totalCount == null || maxProgress >= (totalCount ?: 0)) -> WatchStatus.Completed
                        candidates.any { it.status == WatchStatus.Repeating } -> WatchStatus.Repeating
                        candidates.any { it.status == WatchStatus.Watching } -> WatchStatus.Watching
                        candidates.any { it.status == WatchStatus.Paused } -> WatchStatus.Paused
                        candidates.any { it.status == WatchStatus.Dropped } -> WatchStatus.Dropped
                        else -> newest.status
                    }

                    // 3. Puan (Eksik puanlar doldurulur, en güncel geçerli puan korunur)
                    val bestScore = candidates.filter { (it.score ?: 0) > 0 }.maxByOrNull { it.updatedAt }?.score
                        ?: newest.score

                    // 4. Başlangıç ve bitiş tarihleri (Eksik olanlar tamamlanır)
                    val bestStartDate = candidates.firstNotNullOfOrNull { it.startDate?.takeIf { d -> d.isNotBlank() } }
                    val bestEndDate = candidates.firstNotNullOfOrNull { it.endDate?.takeIf { d -> d.isNotBlank() } }

                    // 5. Notlar ve etiketler
                    val bestNotes = candidates.firstNotNullOfOrNull { it.notes?.takeIf { n -> n.isNotBlank() } }
                    val bestTags = candidates.firstNotNullOfOrNull { it.tags?.takeIf { t -> t.isNotBlank() } }
                    val bestPriority = candidates.firstNotNullOfOrNull { it.priority?.takeIf { p -> p > 0 } }
                    val bestRepeat = candidates.maxOf { it.repeatCount }
                    val isFav = candidates.any { it.isFavorite }

                    // 6. Harici ID çözümlemeleri
                    val realMalId = candidates.firstNotNullOfOrNull { it.malId?.takeIf { id -> id.isRealMalId() } }
                        ?: resolveRealMalId(newest, aniListToken)
                    val simklId = candidates.firstNotNullOfOrNull { it.simklId?.takeIf { id -> id > 0 } }
                    val tmdbId = candidates.firstNotNullOfOrNull { it.tmdbId?.takeIf { id -> id > 0 } }
                    val aniListEntryId = candidates.firstNotNullOfOrNull { it.aniListEntryId }

                    val mergedEntry = newest.copy(
                        status = bestStatus,
                        progress = maxProgress,
                        volumeProgress = maxVolumeProgress,
                        score = bestScore,
                        startDate = bestStartDate,
                        endDate = bestEndDate,
                        notes = bestNotes,
                        tags = bestTags,
                        priority = bestPriority,
                        repeatCount = bestRepeat,
                        isFavorite = isFav,
                        malId = realMalId ?: newest.malId,
                        simklId = simklId ?: newest.simklId,
                        tmdbId = tmdbId ?: newest.tmdbId,
                        aniListEntryId = aniListEntryId ?: newest.aniListEntryId
                    )

                    // ── 1. AniList Eşitleme (Anime & Manga) ──
                    if (isAniList && isAnimeOrManga) {
                        val current = item.aniList
                        if (current == null) {
                            // AniList'te eksik -> EKLE!
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("anilist")
                            val res = runSyncCatching { AniListSyncManager.updateAniListEntry(aniListToken!!, mergedEntry) }
                            if (res.isSuccess && res.getOrNull() != null) {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("AniList", "[AniList] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true)
                            } else {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                logEvent("AniList", "[AniList] ! Eklenemedi: ${mergedEntry.title}", isError = true)
                            }
                        } else {
                            val needsUpdate = (current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore) ||
                                    (current.startDate.isNullOrBlank() && !bestStartDate.isNullOrBlank()) ||
                                    (current.endDate.isNullOrBlank() && !bestEndDate.isNullOrBlank())
                            if (needsUpdate) {
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("anilist")
                                val target = mergedEntry.copy(aniListEntryId = current.aniListEntryId)
                                val res = runSyncCatching { AniListSyncManager.updateAniListEntry(aniListToken!!, target) }
                                if (res.isSuccess && res.getOrNull() != null) {
                                    statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent("AniList", "[AniList] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true)
                                } else {
                                    statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("AniList", "[AniList] ! Güncellenemedi: ${mergedEntry.title}", isError = true)
                                }
                            }
                        }
                    }

                    // ── 2. MyAnimeList Eşitleme (Anime & Manga, MAL ID gerektirir) ──
                    if (isMal && isAnimeOrManga) {
                        if (realMalId != null) {
                            val current = item.mal
                            if (current == null) {
                                // MAL'da eksik -> EKLE!
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                val target = mergedEntry.copy(malId = realMalId)
                                val res = runSyncCatching { MalSyncManager.updateMalEntry(malToken!!, target) }
                                if (res.isSuccess) {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                    logEvent("MyAnimeList", "[MAL] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true)
                                } else {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("MyAnimeList", "[MAL] ! Eklenemedi: ${mergedEntry.title}", isError = true)
                                }
                            } else {
                                val needsUpdate = (current.status != bestStatus) ||
                                        (current.progress < maxProgress) ||
                                        (bestScore != null && current.score != bestScore) ||
                                        (current.startDate.isNullOrBlank() && !bestStartDate.isNullOrBlank()) ||
                                        (current.endDate.isNullOrBlank() && !bestEndDate.isNullOrBlank())
                                if (needsUpdate) {
                                    com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                    val target = mergedEntry.copy(malId = realMalId)
                                    val res = runSyncCatching { MalSyncManager.updateMalEntry(malToken!!, target) }
                                    if (res.isSuccess) {
                                        statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                        logEvent("MyAnimeList", "[MAL] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true)
                                    } else {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("MyAnimeList", "[MAL] ! Güncellenemedi: ${mergedEntry.title}", isError = true)
                                }
                                }
                            }
                        } else {
                            statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(skippedCount = it.skippedCount + 1) }
                            logEvent("MyAnimeList", "[MAL] - Atlandı (MAL ID yok): ${mergedEntry.title}")
                        }
                    }

                    // ── 3. Simkl Eşitleme (Anime, Dizi, Film — Tümü) ──
                    if (isSimkl && (mergedEntry.type == MediaType.Anime || mergedEntry.type == MediaType.TvShow || mergedEntry.type == MediaType.Movie)) {
                        val current = item.simkl
                        if (current == null) {
                            simklEntriesToSync.add(mergedEntry)
                        } else {
                            val needsUpdate = (current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore)
                            if (needsUpdate) {
                                simklEntriesToSync.add(mergedEntry.copy(simklId = current.simklId))
                            }
                        }
                    }

                    // ── 4. Kitsu Eşitleme (Anime & Manga) ──
                    if (isKitsu && isAnimeOrManga) {
                        val current = item.kitsu
                        val knownKitsuId = current?.malId?.takeIf { it >= 300_000_000 }?.let { it - 300_000_000 }
                        if (current == null) {
                            // Kitsu'da eksik -> EKLE!
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("kitsu")
                            val res = runSyncCatching { KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = null) }.getOrNull()
                            if (res != null && res.errors.isEmpty()) {
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("Kitsu", "[Kitsu] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true)
                            } else {
                                val errMsg = res?.errors?.firstOrNull() ?: "Bilinmeyen hata"
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                logEvent("Kitsu", "[Kitsu] ! Hata: ${mergedEntry.title} ($errMsg)", isError = true)
                            }
                        } else {
                            val needsUpdate = (current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore)
                            if (needsUpdate) {
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("kitsu")
                                val res = runSyncCatching { KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = knownKitsuId) }.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent("Kitsu", "[Kitsu] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true)
                                } else {
                                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Kitsu", "[Kitsu] ! Güncellenemedi: ${mergedEntry.title}", isError = true)
                                }
                            }
                        }
                    }

                    // ── 5. Shikimori Eşitleme (Anime & Manga, MAL ID gerektirir) ──
                    if (isShikimori && isAnimeOrManga) {
                        if (realMalId != null) {
                            val current = item.shikimori
                            if (current == null) {
                                // Shikimori'de eksik -> EKLE!
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                val target = mergedEntry.copy(malId = realMalId)
                                val res = runSyncCatching { ShikimoriSyncManager.syncEntryToShikimori(context, target) }.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                    logEvent("Shikimori", "[Shikimori] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true)
                                } else {
                                    val errMsg = res?.errors?.firstOrNull() ?: "Bilinmeyen hata"
                                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Shikimori", "[Shikimori] ! Hata: ${mergedEntry.title} ($errMsg)", isError = true)
                                }
                            } else {
                                val needsUpdate = (current.status != bestStatus) ||
                                        (current.progress < maxProgress) ||
                                        (bestScore != null && current.score != bestScore)
                                if (needsUpdate) {
                                    com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                    val target = mergedEntry.copy(malId = realMalId)
                                    val res = runSyncCatching { ShikimoriSyncManager.syncEntryToShikimori(context, target) }.getOrNull()
                                    if (res != null && res.errors.isEmpty()) {
                                        statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                        logEvent("Shikimori", "[Shikimori] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true)
                                    } else {
                                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Shikimori", "[Shikimori] ! Güncellenemedi: ${mergedEntry.title}", isError = true)
                                }
                                }
                            }
                        } else {
                            statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(skippedCount = it.skippedCount + 1) }
                            logEvent("Shikimori", "[Shikimori] - Atlandı (MAL ID yok): ${mergedEntry.title}")
                        }
                    }


                    if ((index + 1) % 2 == 0 || index == unifiedItems.size - 1) {
                        updateProgress(
                            step = "Eşitleniyor: ${mergedEntry.title}",
                            detail = "${index + 1} / ${unifiedItems.size} (%${((index + 1).toFloat() / unifiedItems.size * 100).toInt()})",
                            processed = index + 1,
                            total = unifiedItems.size
                        )
                    }
                }

                // ── Simkl Toplu Senkronizasyonu (1 req/sn rate limit'e tam uyumlu batch) ──
                if (isSimkl && simklEntriesToSync.isNotEmpty()) {
                    updateProgress("Simkl kütüphanesi eşitleniyor...", "0 / ${simklEntriesToSync.size}")
                    val chunks = simklEntriesToSync.chunked(35)
                    var simklAdded = 0
                    var simklErrors = 0
                    var simklSkipped = 0
                    for ((idx, chunk) in chunks.withIndex()) {
                        val progressText = "${minOf((idx + 1) * 35, simklEntriesToSync.size)} / ${simklEntriesToSync.size} (%${(((idx + 1).toFloat() / chunks.size) * 100).toInt()})"
                        updateProgress("Simkl kütüphanesi eşitleniyor...", progressText)
                        val syncRes = SimklSyncManager.syncBatchToSimkl(context, chunk)
                        val count = syncRes.addedCount
                        val notFound = syncRes.notFoundCount
                        simklAdded += count
                        simklSkipped += notFound
                        if (syncRes.errors.isEmpty()) {
                            logEvent("Simkl", "[Simkl] Grup ${idx + 1}/${chunks.size} eşitlendi ($count eklendi${if (notFound > 0) ", $notFound eşleşmedi" else ""})", isAddition = count > 0)
                        } else {
                            simklErrors += chunk.size
                            syncRes.errors.forEach { message ->
                                logEvent("Simkl", "[Simkl] Grup ${idx + 1}/${chunks.size}: $message", isError = true)
                            }
                        }
                        if (idx < chunks.size - 1) {
                            // Simkl API 1 istek/saniye kuralına tam uyum
                            kotlinx.coroutines.delay(1200L)
                        }
                    }
                    statsMap["Simkl"] = statsMap["Simkl"]!!.let {
                        it.copy(
                            addedCount = it.addedCount + simklAdded,
                            errorCount = it.errorCount + simklErrors,
                            skippedCount = it.skippedCount + simklSkipped
                        )
                    }
                    kotlinx.coroutines.delay(1500L)
                }

                // Read back actual remote state; desired merged values are NOT receipts.
                updateProgress("Sunucu listeleri doğrulanıyor...", "Yalnızca sunucudan okunan kayıtlar kaydediliyor")
                if (isAniList) repository.smartImport("anilist", AniListImportManager.fetchAllLists(aniListToken!!), allowDelete = false)
                if (isMal) repository.smartImport("mal", MalImportManager.fetchAllLists(malToken!!, showAdult), allowDelete = false)
                if (isKitsu) repository.smartImport("kitsu", KitsuImportManager.fetchAllLists(context, kitsuToken!!, kitsuUserId!!), allowDelete = false)
                if (isShikimori) repository.smartImport("shikimori", ShikimoriImportManager.fetchAllLists(context, shikimoriToken!!, shikimoriUserId!!), allowDelete = false)
                if (isSimkl) {
                    val refreshToken = ExternalAuthManager.getSimklToken(context) ?: error("Simkl bağlantısı kesildi")
                    repository.smartImport("simkl", SimklImportManager.fetchAllLists(refreshToken), allowDelete = false)
                }

                val hasSyncErrors = statsMap.values.any { it.errorCount > 0 }
                val summary = if (hasSyncErrors) {
                    "Eşitleme kısmen tamamlandı. Aktarılamayan işlemler için hata kayıtlarını inceleyin."
                } else {
                    "Eşitleme tamamlandı. Atlanan kayıtlar için platform özetlerini inceleyin."
                }
                logEvent("Tamamlandı", summary, isError = hasSyncErrors)

                crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
                    isRunning = false,
                    isCompleted = true,
                    currentStep = if (hasSyncErrors) "Eşitleme kısmen tamamlandı" else "Senkronizasyon Tamamlandı!",
                    currentDetail = summary,
                    processedItems = unifiedItems.size,
                    totalItems = unifiedItems.size,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList()
                )

                onShowMessage?.invoke(summary)
            }.onFailure { error ->
                logEvent("Hata", "Eşitleme hatası: ${error.message}", isError = true)
                crossSyncState = crossSyncState.copy(
                    isRunning = false,
                    isCompleted = false,
                    currentStep = "Eşitleme Hatası",
                    currentDetail = error.message ?: "Bilinmeyen bir hata oluştu",
                    errorMessage = error.message,
                    logs = recentLogs.toList()
                )
                onShowMessage?.invoke("Eşitleme sırasında hata oluştu: ${error.message}")
            }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                crossSyncState = crossSyncState.copy(
                    isRunning = false, isCompleted = false, currentStep = "Eşitleme Hatası",
                    errorMessage = error.message, currentDetail = error.message ?: "Hesaplar okunamadı"
                )
                onShowMessage?.invoke("Eşitleme başlatılamadı: ${error.message}")
            } finally {
                isCrossSyncRunning = false
                if (crossSyncState.isRunning) {
                    crossSyncState = crossSyncState.copy(isRunning = false, currentStep = "Eşitleme durduruldu")
                }
            }
        }
    }

    fun loginKitsu(
        username: String,
        password: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runSyncCatching {
                val tokenResp = KitsuApiClient.loginWithPassword(username, password)
                val user = KitsuApiClient.getCurrentUser(tokenResp.accessToken)
                ExternalAuthManager.saveKitsuAuth(
                    context = context,
                    token = tokenResp.accessToken,
                    refreshToken = tokenResp.refreshToken,
                    userId = user.id,
                    username = user.name
                )
                val settings = SettingsDataStore(context)
                settings.saveKitsuProfileInfo(user.name, user.avatarUrl)
                user.name
            }.onSuccess { userName ->
                refreshAuthState()
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke("Kitsu hesabı bağlandı: $userName")
                    onSuccess()
                }
            }.onFailure { err ->
                val msg = err.message ?: "Kitsu girişi başarısız"
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(msg)
                    onError(msg)
                }
            }
        }
    }

    fun loginShikimori(
        clientId: String = ShikimoriApiClient.DEFAULT_CLIENT_ID,
        clientSecret: String = ShikimoriApiClient.DEFAULT_CLIENT_SECRET,
        authCode: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val effectiveClientId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
        val effectiveClientSecret = clientSecret.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_SECRET }
        val cleanCode = ShikimoriApiClient.sanitizeAuthCode(authCode)

        viewModelScope.launch(Dispatchers.IO) {
            runSyncCatching {
                ExternalAuthManager.saveShikimoriCredentials(context, effectiveClientId, effectiveClientSecret)
                val tokenResp = ShikimoriApiClient.exchangeCodeForToken(effectiveClientId, effectiveClientSecret, cleanCode)
                val user = ShikimoriApiClient.getCurrentUser(tokenResp.accessToken)
                ExternalAuthManager.saveShikimoriAuth(
                    context = context,
                    token = tokenResp.accessToken,
                    refreshToken = tokenResp.refreshToken,
                    expiresInSeconds = tokenResp.expiresIn,
                    userId = user.id,
                    username = user.nickname
                )
                val settings = SettingsDataStore(context)
                settings.saveShikimoriProfileInfo(user.nickname, user.avatarUrl)
                user.nickname
            }.onSuccess { userName ->
                refreshAuthState()
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke("Shikimori hesabı bağlandı: $userName")
                    onSuccess()
                }
            }.onFailure { err ->
                val msg = err.message ?: "Shikimori girişi başarısız"
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(msg)
                    onError(msg)
                }
            }
        }
    }

    fun importKitsuList(repository: MediaEntryRepository) {
        if (isKitsuImportRunning) return
        isKitsuImportRunning = true
        onShowMessage?.invoke("Kitsu listesi içe aktarılıyor...")

        viewModelScope.launch(Dispatchers.IO) {
            val token = ExternalAuthManager.getKitsuToken(context)
            val userId = ExternalAuthManager.getKitsuUserId(context)

            if (token.isNullOrBlank() || userId.isNullOrBlank()) {
                onShowMessage?.invoke("Kitsu token veya kullanıcı ID bulunamadı")
                isKitsuImportRunning = false
                return@launch
            }

            runSyncCatching {
                runSyncCatching {
                    val profile = KitsuImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveKitsuProfileInfo(profile.name, profile.avatarUrl)
                }
                KitsuImportManager.fetchAllLists(context, token, userId)
            }.onSuccess { importedEntries ->
                repository.smartImport("kitsu", importedEntries, allowDelete = false)
                onShowMessage?.invoke("${importedEntries.size} Kitsu kaydı başarıyla aktarıldı")
            }.onFailure { error ->
                onShowMessage?.invoke(error.message ?: "Kitsu içe aktarma başarısız")
            }

            isKitsuImportRunning = false
        }
    }

    fun importShikimoriList(repository: MediaEntryRepository) {
        if (isShikimoriImportRunning) return
        isShikimoriImportRunning = true
        onShowMessage?.invoke("Shikimori listesi içe aktarılıyor...")

        viewModelScope.launch(Dispatchers.IO) {
            val token = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            val userId = ExternalAuthManager.getShikimoriUserId(context)

            if (token.isNullOrBlank() || userId == null) {
                onShowMessage?.invoke("Shikimori token veya kullanıcı ID bulunamadı")
                isShikimoriImportRunning = false
                return@launch
            }

            runSyncCatching {
                runSyncCatching {
                    val profile = ShikimoriImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveShikimoriProfileInfo(profile.nickname, profile.avatarUrl)
                }
                ShikimoriImportManager.fetchAllLists(context, token, userId)
            }.onSuccess { importedEntries ->
                repository.smartImport("shikimori", importedEntries, allowDelete = false)
                onShowMessage?.invoke("${importedEntries.size} Shikimori kaydı başarıyla aktarıldı")
            }.onFailure { error ->
                onShowMessage?.invoke(error.message ?: "Shikimori içe aktarma başarısız")
            }

            isShikimoriImportRunning = false
        }
    }

    fun importKitsuList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) = importKitsuList(repository)

    fun importShikimoriList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) = importShikimoriList(repository)

    fun importSimklList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        val token = ExternalAuthManager.getSimklToken(context)

        if (token.isNullOrBlank()) {
            onShowMessage?.invoke("Simkl token bulunamadı")
            return
        }

        if (isSimklImportRunning) {
            onShowMessage?.invoke("Simkl import zaten çalışıyor")
            return
        }

        isSimklImportRunning = true
        onShowMessage?.invoke("Simkl listesi getiriliyor...")

        viewModelScope.launch(Dispatchers.IO) {
            val settingsDataStore = SettingsDataStore(context)

            runSyncCatching {
                runSyncCatching {
                    val profile = SimklImportManager.fetchUserProfile(token)
                    settingsDataStore.setSimklProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = ""
                    )
                }
                SimklImportManager.fetchAllLists(token)
            }.onSuccess { importedEntries ->
                repository.smartImport("simkl", importedEntries, allowDelete = false)

                onShowMessage?.invoke(
                    "${importedEntries.size} Simkl kaydı başarıyla aktarıldı"
                )
            }.onFailure { error ->
                onShowMessage?.invoke(
                    error.message ?: "Simkl içe aktarma başarısız"
                )
            }

            isSimklImportRunning = false
        }
    }
}
