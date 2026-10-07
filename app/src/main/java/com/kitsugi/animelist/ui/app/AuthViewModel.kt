package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.data.auth.runSyncCatching
import com.kitsugi.animelist.data.auth.CrossSyncReportStore
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors

private fun Throwable.crossSyncDiagnostic(): String = buildString {
    var current: Throwable? = this@crossSyncDiagnostic
    var depth = 0
    while (current != null && depth < 8) {
        val label = if (depth == 0) "Hata" else "Neden $depth"
        appendLine("$label: ${current.javaClass.name}: ${current.message ?: "(mesaj yok)"}")
        current.stackTrace.take(6).forEach { frame ->
            appendLine("  at ${frame.className}.${frame.methodName}(${frame.fileName ?: "?"}:${frame.lineNumber})")
        }
        current = current.cause
        depth++
    }
}

private fun MediaEntry.crossSyncIdentityDiagnostic(): String = buildString {
    fun clean(value: String?): String = value.orEmpty().replace('\n', ' ').replace('\r', ' ').trim()
    appendLine("Başlık: ${clean(title)}")
    titleEnglish?.takeIf { it.isNotBlank() }?.let { appendLine("İngilizce başlık: ${clean(it)}") }
    titleJapanese?.takeIf { it.isNotBlank() }?.let { appendLine("Japonca başlık: ${clean(it)}") }
    appendLine("Tür/yıl: $type / ${year ?: "bilinmiyor"}")
    appendLine("Kaynak: ${clean(source)}")
    val identityKeys = com.kitsugi.animelist.model.MediaIdentity.keys(this@crossSyncIdentityDiagnostic)
    appendLine("Harici kimlikler: ${identityKeys.takeIf { it.isNotEmpty() }?.joinToString() ?: "yok"}")
    appendLine("Ham kimlik alanları: malId=$malId, aniListEntryId=$aniListEntryId, malListId=$malListId, simklId=$simklId, tmdbId=$tmdbId")
    appendLine("Durum/ilerleme: ${status.label}, bölüm=$progress/${total ?: "?"}, cilt=$volumeProgress, puan=${score ?: "yok"}")
    appendLine("Tarihler: başlangıç=${startDate ?: "yok"}, bitiş=${endDate ?: "yok"}; favori=$isFavorite")
}

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

    private var crossSyncJob: kotlinx.coroutines.Job? = null

    fun resetCrossSyncState() {
        if (!isCrossSyncRunning) {
            crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState()
        }
    }

    fun cancelCrossSync() {
        val job = crossSyncJob ?: return
        crossSyncState = crossSyncState.copy(
            currentStep = "Eşitleme durduruluyor...",
            currentDetail = "Tamamlanan hesap güncellemeleri geri alınamaz; durdurma sonrası rapor oluşturulacak.",
            lastUpdatedAt = System.currentTimeMillis()
        )
        job.cancel(kotlinx.coroutines.CancellationException("Eşitleme kullanıcı tarafından durduruldu"))
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
                val importResult = runCatching {
                    repository.smartImport("anilist", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    onShowMessage?.invoke("${importedEntries.size} AniList kaydı başarıyla aktarıldı")
                } else {
                    onShowMessage?.invoke("AniList kaydedilirken sorun oluştu: ${importResult.exceptionOrNull()?.message}")
                }
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
                val importResult = runCatching {
                    repository.smartImport("mal", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    onShowMessage?.invoke("${importedEntries.size} MyAnimeList kaydı başarıyla aktarıldı")
                } else {
                    onShowMessage?.invoke("MyAnimeList kaydedilirken sorun oluştu: ${importResult.exceptionOrNull()?.message}")
                }
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
        var shikimori: MediaEntry? = null,
        var identityReviewRequired: Boolean = false,
        var identityReviewDetails: String? = null
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
        val syncStartedAt = System.currentTimeMillis()
        crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
            isRunning = true,
            currentStep = "Hesaplar taranıyor...",
            currentDetail = "Bağlı servislerin durumu kontrol ediliyor",
            progressPhaseStartedAt = syncStartedAt,
            lastUpdatedAt = syncStartedAt,
            startedAt = syncStartedAt
        )

        crossSyncJob = viewModelScope.launch(Dispatchers.IO) {
            val statsMap = mutableMapOf<String, com.kitsugi.animelist.model.CrossPlatformStats>()
            val recentLogs = mutableListOf<com.kitsugi.animelist.model.CrossSyncLogEntry>()
            val reportLogs = mutableListOf<com.kitsugi.animelist.model.CrossSyncLogEntry>()
            var issueCount = 0

            fun logEvent(
                platform: String,
                message: String,
                isAddition: Boolean = false,
                isUpdate: Boolean = false,
                isError: Boolean = false,
                isWarning: Boolean = false,
                details: String? = null,
                includeInLiveLog: Boolean = true
            ) {
                val entry = com.kitsugi.animelist.model.CrossSyncLogEntry(
                    platform = platform,
                    message = message,
                    isAddition = isAddition,
                    isUpdate = isUpdate,
                    isError = isError,
                    isWarning = isWarning,
                    details = details
                )
                reportLogs.add(entry)
                if (isError || isWarning) issueCount++
                if (includeInLiveLog) {
                    recentLogs.add(entry)
                    if (recentLogs.size > 500) recentLogs.removeAt(0)
                }
            }

            fun updateProgress(
                step: String,
                detail: String = "",
                processed: Int = crossSyncState.processedItems,
                total: Int = crossSyncState.totalItems,
                unit: String = "İçerik"
            ) {
                val now = System.currentTimeMillis()
                val progressPhaseChanged = unit != crossSyncState.progressUnit || total != crossSyncState.totalItems
                crossSyncState = crossSyncState.copy(
                    isRunning = true,
                    isCompleted = false,
                    currentStep = step,
                    currentDetail = detail,
                    processedItems = processed,
                    totalItems = total,
                    progressUnit = unit,
                    progressPhaseStartedAt = if (progressPhaseChanged || crossSyncState.progressPhaseStartedAt == null) {
                        now
                    } else {
                        crossSyncState.progressPhaseStartedAt
                    },
                    lastUpdatedAt = now,
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList(),
                    startedAt = syncStartedAt
                )
            }

            try {
            val syncSettings = SettingsDataStore(context).settingsFlow.first()

            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val kitsuUserId = ExternalAuthManager.getKitsuUserId(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            var shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)
            if (!shikimoriToken.isNullOrBlank() && shikimoriUserId == null &&
                ExternalAuthManager.ensureShikimoriUserResolved(context)
            ) {
                shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)
            }

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
                val message = "Eşitleme için en az iki hesap bağlı ve eşitlemesi açık olmalıdır."
                logEvent("Eşitleme", message, isError = true)
                onShowMessage?.invoke(message)
                crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
                    isRunning = false,
                    currentStep = "Eşitleme başlatılamadı",
                    currentDetail = message,
                    errorMessage = "En az iki hesap bağlı ve eşitlemesi açık olmalıdır.",
                    logs = recentLogs.toList(),
                    startedAt = syncStartedAt
                )
                return@launch
            }

            connectedPlatforms.forEach { name ->
                statsMap[name] = com.kitsugi.animelist.model.CrossPlatformStats(platformName = name)
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
                val sourceEntryTotal = aniListEntries.size + malEntries.size + simklEntries.size +
                    kitsuEntries.size + shikimoriEntries.size
                updateProgress(
                    "İçerikler çapraz eşleştiriliyor...",
                    if (sourceEntryTotal > 0) "0 / $sourceEntryTotal kaynak kayıt taranıyor..." else "Listelerde eşleştirilecek kayıt yok",
                    processed = 0,
                    total = sourceEntryTotal,
                    unit = "Kaynak kayıt"
                )

                val unifiedItems = mutableListOf<UnifiedSyncItem>()
                val candidateIndex = com.kitsugi.animelist.data.auth.CrossSyncCandidateIndex<UnifiedSyncItem>()
                // Japanese franchise titles can be shared by distinct movies/sequels, so only
                // primary and English title aliases participate in the cross-sync safety check.
                fun titleAliases(entry: MediaEntry): Set<String> = listOfNotNull(
                    entry.title,
                    entry.titleEnglish
                ).map { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                    .filter { it.length >= 2 }
                    .toSet()

                fun describeIdentityCandidate(entry: MediaEntry): String = entry.crossSyncIdentityDiagnostic().trimEnd()

                fun clusterEntry(entry: MediaEntry, assignToPlatform: (UnifiedSyncItem) -> Unit) {
                    val identity = com.kitsugi.animelist.model.MediaIdentity
                    val entryKeys = identity.keys(entry)
                    val entryTitles = titleAliases(entry)
                    val matches = candidateIndex.possibleMatches(entry).filter { item ->
                        item.candidates.isNotEmpty() && item.candidates.all { identity.sameMedia(it, entry) }
                    }

                    val sameTitleIdConflicts = candidateIndex.sharingTitleAlias(entry).filter { item ->
                        item.candidates.any { candidate ->
                            candidate.type == entry.type &&
                                identity.conflictingIdentityKeys(candidate, entry).isNotEmpty() &&
                                titleAliases(candidate).intersect(entryTitles).isNotEmpty()
                        }
                    }

                    val item = when {
                        sameTitleIdConflicts.isNotEmpty() -> {
                            val details = buildString {
                                appendLine("Eşleşme otomatik olarak birleştirilmedi: aynı tür/başlık için sağlayıcı kimlikleri çelişiyor.")
                                appendLine("Gelen kayıt:")
                                appendLine(describeIdentityCandidate(entry))
                                appendLine("Çakışan mevcut kayıtlar:")
                                sameTitleIdConflicts.flatMap { it.candidates }.distinct().forEach { candidate ->
                                    appendLine(describeIdentityCandidate(candidate))
                                }
                                append("Çakışan kimlik alanları: ")
                                append(sameTitleIdConflicts.flatMap { group ->
                                    group.candidates.flatMap { candidate -> identity.conflictingIdentityKeys(candidate, entry) }
                                }.distinct().joinToString().ifBlank { "bilinmiyor" })
                            }
                            UnifiedSyncItem(
                                identityReviewRequired = true,
                                identityReviewDetails = details
                            ).also { unifiedItems.add(it) }
                        }
                        matches.isEmpty() -> UnifiedSyncItem().also { unifiedItems.add(it) }
                        matches.size == 1 -> {
                            val match = matches.single()
                            val sharedIds = match.candidates.flatMap { identity.keys(it).intersect(entryKeys) }.distinct()
                            val unrelatedTitleIds = if (sharedIds.isNotEmpty()) {
                                match.candidates.filter { candidate ->
                                    identity.keys(candidate).intersect(entryKeys).isNotEmpty() &&
                                        titleAliases(candidate).isNotEmpty() && entryTitles.isNotEmpty() &&
                                        titleAliases(candidate).intersect(entryTitles).isEmpty()
                                }
                            } else emptyList()
                            if (unrelatedTitleIds.isNotEmpty()) {
                                val details = buildString {
                                    appendLine("Aynı harici kimlik(ler) eşleşti, ancak başlık alias'ları uyuşmuyor. Otomatik yazma güvenlik için durduruldu.")
                                    appendLine("Ortak kimlikler: ${sharedIds.joinToString()}")
                                    appendLine("Gelen kayıt:")
                                    appendLine(describeIdentityCandidate(entry))
                                    appendLine("Eşleşen kayıt(lar):")
                                    unrelatedTitleIds.forEach { appendLine(describeIdentityCandidate(it)) }
                                }
                                match.identityReviewRequired = true
                                match.identityReviewDetails = details
                            }
                            match
                        }
                        else -> {
                            val keyMatches = matches.filter { candidateGroup ->
                                candidateGroup.candidates.any { identity.keys(it).intersect(entryKeys).isNotEmpty() }
                            }
                            val normalizedEntryTitle = identity.normalizedTitle(entry.title)
                            val exactTitleMatches = matches.filter { candidateGroup ->
                                candidateGroup.candidates.any { candidate ->
                                    val normalizedCandidateTitle = identity.normalizedTitle(candidate.title)
                                    normalizedCandidateTitle.isNotBlank() && normalizedCandidateTitle == normalizedEntryTitle
                                }
                            }
                            val resolvedMatch = when {
                                keyMatches.size == 1 -> keyMatches.single()
                                keyMatches.isEmpty() && exactTitleMatches.size == 1 -> exactTitleMatches.single()
                                else -> null
                            }
                            if (resolvedMatch != null) {
                                val details = buildString {
                                    appendLine("Birden fazla aday grup bulundu; tekil ${if (keyMatches.size == 1) "ortak kimlik" else "tam başlık"} ile seçim yapıldı.")
                                    appendLine("Gelen kayıt:")
                                    appendLine(describeIdentityCandidate(entry))
                                    appendLine("Seçilen aday grup: ${matches.indexOf(resolvedMatch) + 1}")
                                    appendLine("Aday gruplar:")
                                    matches.forEachIndexed { index, group ->
                                        appendLine("Aday ${index + 1}:")
                                        group.candidates.forEach { appendLine(describeIdentityCandidate(it)) }
                                    }
                                }
                                logEvent(
                                    "Eşleştirme",
                                    "Birden fazla eşleşme adayı vardı; tekil kanıtla seçim yapıldı: ${entry.title}",
                                    isWarning = true,
                                    details = details
                                )
                                resolvedMatch
                            } else {
                                val details = buildString {
                                    appendLine("Birden fazla olası grup arasında güvenilir tek bir seçim yapılamadı. Kayıt başka hesaplara yazılmadı.")
                                    appendLine("Gelen kayıt:")
                                    appendLine(describeIdentityCandidate(entry))
                                    appendLine("Aday gruplar:")
                                    matches.forEachIndexed { index, group ->
                                        appendLine("Aday ${index + 1}:")
                                        group.candidates.forEach { appendLine(describeIdentityCandidate(it)) }
                                    }
                                }
                                UnifiedSyncItem(
                                    identityReviewRequired = true,
                                    identityReviewDetails = details
                                ).also { unifiedItems.add(it) }
                            }
                        }
                    }
                    assignToPlatform(item)
                    candidateIndex.add(item, entry)
                }

                var sourceEntriesProcessed = 0
                suspend fun clusterEntries(
                    entries: List<MediaEntry>,
                    assignToPlatform: (UnifiedSyncItem, MediaEntry) -> Unit
                ) {
                    for (entry in entries) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        clusterEntry(entry) { item -> assignToPlatform(item, entry) }
                        sourceEntriesProcessed++
                        if (sourceEntriesProcessed % 25 == 0 || sourceEntriesProcessed == sourceEntryTotal) {
                            updateProgress(
                                step = "İçerikler çapraz eşleştiriliyor...",
                                detail = "$sourceEntriesProcessed / $sourceEntryTotal kaynak kayıt incelendi · ${unifiedItems.size} grup oluşturuldu",
                                processed = sourceEntriesProcessed,
                                total = sourceEntryTotal,
                                unit = "Kaynak kayıt"
                            )
                            kotlinx.coroutines.yield()
                        }
                    }
                }

                clusterEntries(aniListEntries) { item, entry -> item.aniList = entry }
                clusterEntries(malEntries) { item, entry -> item.mal = entry }
                clusterEntries(simklEntries) { item, entry -> item.simkl = entry }
                clusterEntries(kitsuEntries) { item, entry -> item.kitsu = entry }
                clusterEntries(shikimoriEntries) { item, entry -> item.shikimori = entry }

                logEvent("Analiz", "Toplam ${unifiedItems.size} tekil içerik tespit edildi. Eşitleme başlatılıyor...")
                updateProgress(
                    "Eşitleme başlatılıyor...",
                    "Toplam ${unifiedItems.size} içerik eşitlenecek",
                    processed = 0,
                    total = unifiedItems.size,
                    unit = "İçerik"
                )

                val simklEntriesToSync = mutableListOf<MediaEntry>()
                fun countSafetySkip(platform: String) {
                    statsMap[platform]?.let { statsMap[platform] = it.copy(skippedCount = it.skippedCount + 1) }
                }

                // ── FAZ 3: Eksikleri Tamamlama ve Alan Senkronizasyonu (Asla Silme Yok) ──
                for ((index, item) in unifiedItems.withIndex()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val candidates = item.candidates
                    if (candidates.isEmpty()) {
                        if ((index + 1) % 25 == 0) kotlinx.coroutines.yield()
                        continue
                    }
                    if ((index + 1) % 10 == 1) {
                        updateProgress(
                            step = "İçerik eşitleniyor: ${item.primaryTitle}",
                            detail = "${index + 1} / ${unifiedItems.size} içerik işleniyor",
                            processed = index,
                            total = unifiedItems.size
                        )
                    }
                    if (item.identityReviewRequired) {
                        val title = candidates.firstOrNull()?.title ?: "Bilinmeyen içerik"
                        logEvent(
                            "Eşleştirme",
                            "Kimlik doğrulaması gerektiği için dış hesaplara aktarılmadı: $title",
                            isWarning = true,
                            details = item.identityReviewDetails ?: candidates.joinToString("\n\n") { it.crossSyncIdentityDiagnostic() }
                        )
                        val reviewType = candidates.first().type
                        val reviewIsAnimeOrManga = reviewType == MediaType.Anime || reviewType == MediaType.Manga
                        if (isAniList && item.aniList == null && reviewIsAnimeOrManga) countSafetySkip("AniList")
                        if (isMal && item.mal == null && reviewIsAnimeOrManga) countSafetySkip("MyAnimeList")
                        if (isSimkl && item.simkl == null && reviewType != MediaType.Manga) countSafetySkip("Simkl")
                        if (isKitsu && item.kitsu == null && reviewIsAnimeOrManga) countSafetySkip("Kitsu")
                        if (isShikimori && item.shikimori == null && reviewIsAnimeOrManga) countSafetySkip("Shikimori")
                        if ((index + 1) % 2 == 0 || index == unifiedItems.lastIndex) {
                            updateProgress(
                                step = "Kimlik kontrolü atlandı: $title",
                                detail = "${index + 1} / ${unifiedItems.size}",
                                processed = index + 1,
                                total = unifiedItems.size
                            )
                        }
                        if ((index + 1) % 25 == 0) kotlinx.coroutines.yield()
                        continue
                    }

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
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

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

                    val mappingDetails = buildString {
                        appendLine("Kaynak platform kayıtları (${candidates.size}):")
                        candidates.forEachIndexed { candidateIndex, candidate ->
                            appendLine("Kayıt ${candidateIndex + 1}:")
                            appendLine(candidate.crossSyncIdentityDiagnostic())
                        }
                        appendLine("Eşitleme için birleştirilen değerler:")
                        append(mergedEntry.crossSyncIdentityDiagnostic())
                    }
                    logEvent(
                        "Eşleştirme",
                        "İçerik grubu oluşturuldu: ${mergedEntry.title} (${candidates.size} kaynak)",
                        details = mappingDetails,
                        includeInLiveLog = false
                    )

                    // ── 1. AniList Eşitleme (Anime & Manga) ──
                    if (isAniList && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.aniList
                        if (current == null) {
                            // AniList'te eksik -> EKLE!
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("anilist")
                            val res = runSyncCatching { AniListSyncManager.updateAniListEntry(aniListToken!!, mergedEntry) }
                            if (res.isSuccess && res.getOrNull() != null) {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("AniList", "[AniList] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true, details = mappingDetails)
                            } else {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                val failure = res.exceptionOrNull()?.crossSyncDiagnostic()
                                    ?: "AniList yeni liste kaydını onaylamadı veya medya kimliği çözülemedi."
                                logEvent("AniList", "[AniList] ! Eklenemedi: ${mergedEntry.title}", isError = true, details = "$failure\n$mappingDetails")
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
                                    logEvent("AniList", "[AniList] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true, details = mappingDetails)
                                } else {
                                    statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    val failure = res.exceptionOrNull()?.crossSyncDiagnostic()
                                        ?: "AniList güncellemeyi onaylamadı veya kayıt kimliği döndürmedi."
                                    logEvent("AniList", "[AniList] ! Güncellenemedi: ${mergedEntry.title}", isError = true, details = "$failure\n$mappingDetails")
                                }
                            }
                        }
                    }

                    // ── 2. MyAnimeList Eşitleme (Anime & Manga, MAL ID gerektirir) ──
                    if (isMal && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (realMalId != null) {
                            val current = item.mal
                            if (current == null) {
                                // MAL'da eksik -> EKLE!
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                val target = mergedEntry.copy(malId = realMalId)
                                val res = runSyncCatching { MalSyncManager.updateMalEntry(malToken!!, target) }
                                if (res.isSuccess) {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                    logEvent("MyAnimeList", "[MAL] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true, details = mappingDetails)
                                } else {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    val failure = res.exceptionOrNull()?.crossSyncDiagnostic()
                                        ?: "MyAnimeList kayıt ekleme isteği onaylanmadı."
                                    logEvent("MyAnimeList", "[MAL] ! Eklenemedi: ${mergedEntry.title}", isError = true, details = "$failure\n$mappingDetails")
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
                                        logEvent("MyAnimeList", "[MAL] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true, details = mappingDetails)
                                    } else {
                                    statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    val failure = res.exceptionOrNull()?.crossSyncDiagnostic()
                                        ?: "MyAnimeList güncelleme isteği onaylanmadı."
                                    logEvent("MyAnimeList", "[MAL] ! Güncellenemedi: ${mergedEntry.title}", isError = true, details = "$failure\n$mappingDetails")
                                }
                                }
                            }
                        } else {
                            statsMap["MyAnimeList"] = statsMap["MyAnimeList"]!!.let { it.copy(skippedCount = it.skippedCount + 1) }
                            logEvent(
                                "MyAnimeList",
                                "[MAL] - Atlandı (doğrulanmış MAL ID yok): ${mergedEntry.title}",
                                isWarning = true,
                                details = "MAL ID çözümlemesi başarısız oldu veya güvenli bir kimlik bulunamadı.\n$mappingDetails"
                            )
                        }
                    }

                    // ── 3. Simkl Eşitleme (Anime, Dizi, Film — Tümü) ──
                    if (isSimkl && (mergedEntry.type == MediaType.Anime || mergedEntry.type == MediaType.TvShow || mergedEntry.type == MediaType.Movie)) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
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
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.kitsu
                        val knownKitsuId = current?.malId?.takeIf { it >= 300_000_000 }?.let { it - 300_000_000 }
                        if (current == null) {
                            // Kitsu'da eksik -> EKLE!
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("kitsu")
                            val outcome = runSyncCatching {
                                KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = null)
                            }
                            val res = outcome.getOrNull()
                            if (res != null && res.errors.isEmpty()) {
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("Kitsu", "[Kitsu] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true, details = mappingDetails)
                            } else {
                                val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic()
                                    ?: res?.errors?.joinToString("; ")
                                    ?: "Bilinmeyen hata"
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                logEvent("Kitsu", "[Kitsu] ! Hata: ${mergedEntry.title} ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                            }
                        } else {
                            val needsUpdate = (current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore)
                            if (needsUpdate) {
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("kitsu")
                                val outcome = runSyncCatching {
                                    KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = knownKitsuId)
                                }
                                val res = outcome.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent("Kitsu", "[Kitsu] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true, details = mappingDetails)
                                } else {
                                    val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic()
                                        ?: res?.errors?.joinToString("; ")
                                        ?: "Bilinmeyen hata"
                                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Kitsu", "[Kitsu] ! Güncellenemedi: ${mergedEntry.title} ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                                }
                            }
                        }
                    }

                    // ── 5. Shikimori Eşitleme (Anime & Manga, MAL ID gerektirir) ──
                    if (isShikimori && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (realMalId != null) {
                            val current = item.shikimori
                            if (current == null) {
                                // Shikimori'de eksik -> EKLE!
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                val target = mergedEntry.copy(malId = realMalId)
                                val outcome = runSyncCatching { ShikimoriSyncManager.syncEntryToShikimori(context, target) }
                                val res = outcome.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                    logEvent("Shikimori", "[Shikimori] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true, details = mappingDetails)
                                } else {
                                    val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic()
                                        ?: res?.errors?.joinToString("; ")
                                        ?: "Bilinmeyen hata"
                                    statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Shikimori", "[Shikimori] ! Hata: ${mergedEntry.title} ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                                }
                            } else {
                                val needsUpdate = (current.status != bestStatus) ||
                                        (current.progress < maxProgress) ||
                                        (bestScore != null && current.score != bestScore)
                                if (needsUpdate) {
                                    com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                    val target = mergedEntry.copy(malId = realMalId)
                                    val outcome = runSyncCatching { ShikimoriSyncManager.syncEntryToShikimori(context, target) }
                                    val res = outcome.getOrNull()
                                    if (res != null && res.errors.isEmpty()) {
                                        statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                        logEvent("Shikimori", "[Shikimori] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true, details = mappingDetails)
                                    } else {
                                        val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic()
                                            ?: res?.errors?.joinToString("; ")
                                            ?: "Bilinmeyen hata"
                                        statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                        logEvent("Shikimori", "[Shikimori] ! Güncellenemedi: ${mergedEntry.title} ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                                    }
                                }
                            }
                        } else {
                            statsMap["Shikimori"] = statsMap["Shikimori"]!!.let { it.copy(skippedCount = it.skippedCount + 1) }
                            logEvent(
                                "Shikimori",
                                "[Shikimori] - Atlandı (doğrulanmış MAL ID yok): ${mergedEntry.title}",
                                isWarning = true,
                                details = "Shikimori senkronizasyonu için doğrulanmış MAL ID gerekiyor.\n$mappingDetails"
                            )
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
                    if ((index + 1) % 25 == 0) kotlinx.coroutines.yield()
                }

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // ── Simkl Toplu Senkronizasyonu (1 req/sn rate limit'e tam uyumlu batch) ──
                if (isSimkl && simklEntriesToSync.isNotEmpty()) {
                    updateProgress(
                        "Simkl kütüphanesi eşitleniyor...",
                        "0 / ${simklEntriesToSync.size} Simkl kaydı sıraya alındı",
                        processed = 0,
                        total = simklEntriesToSync.size,
                        unit = "Simkl kaydı"
                    )
                    val chunks = simklEntriesToSync.chunked(35)
                    var simklAdded = 0
                    var simklErrors = 0
                    var simklSkipped = 0
                    for ((idx, chunk) in chunks.withIndex()) {
                        val simklProcessed = minOf((idx + 1) * 35, simklEntriesToSync.size)
                        val progressText = "$simklProcessed / ${simklEntriesToSync.size} Simkl kaydı (%${(simklProcessed.toFloat() / simklEntriesToSync.size * 100).toInt()})"
                        updateProgress(
                            "Simkl kütüphanesi eşitleniyor...",
                            progressText,
                            processed = simklProcessed,
                            total = simklEntriesToSync.size,
                            unit = "Simkl kaydı"
                        )
                        val syncRes = SimklSyncManager.syncBatchToSimkl(context, chunk)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = syncRes.addedCount
                        val notFound = syncRes.notFoundCount
                        simklAdded += count
                        simklSkipped += notFound
                        val chunkDetails = chunk.joinToString("\n\n") { it.crossSyncIdentityDiagnostic() }
                        if (syncRes.errors.isEmpty()) {
                            logEvent(
                                "Simkl",
                                "[Simkl] Grup ${idx + 1}/${chunks.size} eşitlendi ($count eklendi${if (notFound > 0) ", $notFound eşleşmedi" else ""})",
                                isAddition = count > 0,
                                isWarning = notFound > 0,
                                details = if (notFound > 0) "Simkl'de karşılığı bulunamayan kayıtlar bu grupta olabilir.\n$chunkDetails" else chunkDetails
                            )
                        } else {
                            simklErrors += chunk.size
                            syncRes.errors.forEach { message ->
                                logEvent(
                                    "Simkl",
                                    "[Simkl] Grup ${idx + 1}/${chunks.size}: $message",
                                    isError = true,
                                    details = "Grup özeti: $count eklendi, $notFound eşleşmedi. Grup içindeki kayıtlar:\n$chunkDetails"
                                )
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

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // Read back actual remote state; desired merged values are NOT receipts.
                updateProgress(
                    "Sunucu listeleri doğrulanıyor...",
                    "Kayıtlar tek tek sunucudan yeniden okunuyor; bu aşamada ilerleme çubuğu belirsiz gösterilir",
                    processed = 0,
                    total = 0,
                    unit = ""
                )
                if (isAniList) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress("AniList listesi doğrulanıyor...", "Sunucudaki son liste okunuyor", processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("anilist", AniListImportManager.fetchAllLists(aniListToken!!), allowDelete = false) }
                        .onFailure { logEvent("AniList", "Yerel liste güncellenemedi: ${it.message}", isError = true, details = it.crossSyncDiagnostic()) }
                }
                if (isMal) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress("MyAnimeList listesi doğrulanıyor...", "Sunucudaki son liste okunuyor", processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("mal", MalImportManager.fetchAllLists(malToken!!, showAdult), allowDelete = false) }
                        .onFailure { logEvent("MyAnimeList", "Yerel liste güncellenemedi: ${it.message}", isError = true, details = it.crossSyncDiagnostic()) }
                }
                if (isKitsu) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress("Kitsu listesi doğrulanıyor...", "Sunucudaki son liste okunuyor", processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("kitsu", KitsuImportManager.fetchAllLists(context, kitsuToken!!, kitsuUserId!!), allowDelete = false) }
                        .onFailure { logEvent("Kitsu", "Yerel liste güncellenemedi: ${it.message}", isError = true, details = it.crossSyncDiagnostic()) }
                }
                if (isShikimori) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress("Shikimori listesi doğrulanıyor...", "Sunucudaki son liste okunuyor", processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("shikimori", ShikimoriImportManager.fetchAllLists(context, shikimoriToken!!, shikimoriUserId!!), allowDelete = false) }
                        .onFailure { logEvent("Shikimori", "Yerel liste güncellenemedi: ${it.message}", isError = true, details = it.crossSyncDiagnostic()) }
                }
                if (isSimkl) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress("Simkl listesi doğrulanıyor...", "Sunucudaki son liste okunuyor", processed = 0, total = 0, unit = "")
                    val refreshToken = ExternalAuthManager.getSimklToken(context) ?: error("Simkl bağlantısı kesildi")
                    runSyncCatching { repository.smartImport("simkl", SimklImportManager.fetchAllLists(refreshToken), allowDelete = false) }
                        .onFailure { logEvent("Simkl", "Yerel liste güncellenemedi: ${it.message}", isError = true, details = it.crossSyncDiagnostic()) }
                }

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val hasSyncErrors = statsMap.values.any { it.errorCount > 0 } || reportLogs.any { it.isError }
                val hasWarnings = reportLogs.any { it.isWarning }
                val summary = if (hasSyncErrors) {
                    "Eşitleme kısmen tamamlandı. Aktarılamayan işlemler için hata kayıtlarını inceleyin."
                } else {
                    "Eşitleme tamamlandı. Atlanan kayıtlar için platform özetlerini inceleyin."
                }
                logEvent("Tamamlandı", summary, isError = hasSyncErrors, isWarning = !hasSyncErrors && hasWarnings)

                crossSyncState = com.kitsugi.animelist.model.CrossSyncProgressState(
                    isRunning = false,
                    isCompleted = true,
                    currentStep = if (hasSyncErrors) "Eşitleme kısmen tamamlandı" else "Senkronizasyon Tamamlandı!",
                    currentDetail = summary,
                    processedItems = unifiedItems.size,
                    totalItems = unifiedItems.size,
                    progressUnit = "İçerik",
                    lastUpdatedAt = System.currentTimeMillis(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList(),
                    startedAt = syncStartedAt
                )

                onShowMessage?.invoke(summary)
            }.onFailure { error ->
                logEvent(
                    "Hata",
                    "Eşitleme hatası: ${error.message ?: error.javaClass.simpleName}",
                    isError = true,
                    details = error.crossSyncDiagnostic()
                )
                crossSyncState = crossSyncState.copy(
                    isRunning = false,
                    isCompleted = false,
                    currentStep = "Eşitleme Hatası",
                    currentDetail = error.message ?: "Bilinmeyen bir hata oluştu",
                    errorMessage = error.message,
                    logs = recentLogs.toList(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    lastUpdatedAt = System.currentTimeMillis(),
                    startedAt = syncStartedAt
                )
                onShowMessage?.invoke("Eşitleme sırasında hata oluştu: ${error.message}")
            }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                logEvent("Eşitleme", "Eşitleme işlemi iptal edildi; tamamlanan kayıtlar rapora eklendi.", isWarning = true)
                throw cancelled
            } catch (error: Exception) {
                logEvent(
                    "Eşitleme",
                    "Eşitleme başlatılamadı: ${error.message ?: error.javaClass.simpleName}",
                    isError = true,
                    details = error.crossSyncDiagnostic()
                )
                crossSyncState = crossSyncState.copy(
                    isRunning = false,
                    isCompleted = false,
                    currentStep = "Eşitleme Hatası",
                    errorMessage = error.message,
                    currentDetail = error.message ?: "Hesaplar okunamadı",
                    logs = recentLogs.toList(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    lastUpdatedAt = System.currentTimeMillis(),
                    startedAt = syncStartedAt
                )
                onShowMessage?.invoke("Eşitleme başlatılamadı: ${error.message}")
            } finally {
                if (crossSyncState.isRunning) {
                    logEvent("Eşitleme", "Eşitleme durduruldu; tamamlanan kayıtlar rapora eklendi.", isWarning = true)
                    crossSyncState = crossSyncState.copy(
                        isRunning = false,
                        currentStep = "Eşitleme durduruldu",
                        currentDetail = "Tamamlanmış sunucu güncellemeleri korunur; geri alınamaz.",
                        logs = recentLogs.toList(),
                        totalEventCount = reportLogs.size,
                        issueCount = issueCount,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                }
                val completedAt = System.currentTimeMillis()
                val finalState = crossSyncState.copy(
                    isRunning = false,
                    platformStats = statsMap.toMap(),
                    logs = recentLogs.toList(),
                    totalEventCount = reportLogs.size,
                    issueCount = issueCount,
                    reportLogs = reportLogs.toList(),
                    startedAt = syncStartedAt,
                    finishedAt = completedAt,
                    lastUpdatedAt = completedAt
                )
                val reportLocation = runCatching { CrossSyncReportStore.save(context, finalState) }.getOrNull()
                crossSyncState = finalState.copy(reportSavedTo = reportLocation)
                isCrossSyncRunning = false
                crossSyncJob = null
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
            // Tarayıcı hangi redirect_uri ile açıldıysa token isteği de onunla eşleşmek zorunda
            // (Shikimori/Doorkeeper). Akış başlatılırken kaydedilen değeri kullan.
            val pendingRedirectUri = ExternalAuthManager.getShikimoriPendingRedirectUri(context)
                ?: ShikimoriApiClient.DEFAULT_REDIRECT_URI
            runSyncCatching {
                ExternalAuthManager.saveShikimoriCredentials(context, effectiveClientId, effectiveClientSecret)
                ShikimoriApiClient.exchangeCodeForToken(
                    clientId = effectiveClientId,
                    clientSecret = effectiveClientSecret,
                    code = cleanCode,
                    redirectUri = pendingRedirectUri
                )
            }.onSuccess { tokenResp ->
                // Kod bu noktada tüketilmiştir: token'u DERHAL sakla. Profil (whoami)
                // bakımı geçici olarak başarısız olsa bile geçerli oturum çöpe atılmaz;
                // kullanıcı kimliği ilk kullanımda tembel çözümlenir (bkz. ensureShikimoriUserResolved).
                ExternalAuthManager.saveShikimoriAuth(
                    context = context,
                    token = tokenResp.accessToken,
                    refreshToken = tokenResp.refreshToken,
                    expiresInSeconds = tokenResp.expiresIn,
                    userId = 0,
                    username = "Shikimori",
                    notify = false
                )
                val user = runSyncCatching {
                    ShikimoriApiClient.getCurrentUser(tokenResp.accessToken)
                }.getOrNull()
                if (user != null) {
                    ExternalAuthManager.saveShikimoriAuth(
                        context = context,
                        token = tokenResp.accessToken,
                        refreshToken = tokenResp.refreshToken,
                        expiresInSeconds = tokenResp.expiresIn,
                        userId = user.id,
                        username = user.nickname
                    )
                    SettingsDataStore(context).saveShikimoriProfileInfo(user.nickname, user.avatarUrl)
                } else {
                    // Kimlik henüz çözülemedi: tek bir Success yayınlamak otomatik
                    // aktarımı başlatır; aktarım kimliği tembel çözer.
                    ExternalAuthManager.saveShikimoriAuth(
                        context = context,
                        token = tokenResp.accessToken,
                        refreshToken = tokenResp.refreshToken,
                        expiresInSeconds = tokenResp.expiresIn,
                        userId = 0,
                        username = "Shikimori"
                    )
                }
                refreshAuthState()
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(
                        if (user != null) "Shikimori hesabı bağlandı: ${user.nickname}"
                        else "Shikimori hesabı bağlandı. Profil bilgisi alınamadı; ilk kullanımda tekrar denenecek."
                    )
                    onSuccess()
                }
            }.onFailure { err ->
                val msg = err.message ?: "Shikimori girişi başarısız"
                launch(Dispatchers.Main) {
                    onShowMessage?.invoke(msg)
                    onError(msg)
                }
            }
            // Akış tamamlandı (başarılı/başarısız): bekleyen redirect kaydını temizle.
            ExternalAuthManager.clearShikimoriPendingRedirectUri(context)
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
                val importResult = runCatching {
                    repository.smartImport("kitsu", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    onShowMessage?.invoke("${importedEntries.size} Kitsu kaydı başarıyla aktarıldı")
                } else {
                    onShowMessage?.invoke("Kitsu kaydedilirken sorun oluştu: ${importResult.exceptionOrNull()?.message}")
                }
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
            var userId = ExternalAuthManager.getShikimoriUserId(context)
            if (!token.isNullOrBlank() && userId == null &&
                ExternalAuthManager.ensureShikimoriUserResolved(context)
            ) {
                userId = ExternalAuthManager.getShikimoriUserId(context)
            }

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
                val importResult = runCatching {
                    repository.smartImport("shikimori", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    onShowMessage?.invoke("${importedEntries.size} Shikimori kaydı başarıyla aktarıldı")
                } else {
                    onShowMessage?.invoke("Shikimori kaydedilirken sorun oluştu: ${importResult.exceptionOrNull()?.message}")
                }
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
                val importResult = runCatching {
                    repository.smartImport("simkl", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    onShowMessage?.invoke("${importedEntries.size} Simkl kaydı başarıyla aktarıldı")
                } else {
                    onShowMessage?.invoke("Simkl kaydedilirken sorun oluştu: ${importResult.exceptionOrNull()?.message}")
                }
            }.onFailure { error ->
                onShowMessage?.invoke(
                    error.message ?: "Simkl içe aktarma başarısız"
                )
            }

            isSimklImportRunning = false
        }
    }
}
