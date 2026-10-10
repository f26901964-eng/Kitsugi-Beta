package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.ui.utils.plainLabel
import com.kitsugi.animelist.ui.utils.localizedLabel
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
import com.kitsugi.animelist.data.auth.BangumiAuthManager
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.BangumiImportManager
import com.kitsugi.animelist.data.auth.BangumiSyncManager
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

/**
 * `malId` alanı yalnızca gerçek MAL kimliği değil; uygulama Kitsu (id + 300M) ve AniList (id + 100M)
 * kayıtlarını da bu alanda iç ad alanıyla taşır. Raporda bunlar MAL kimliği gibi okunmasın diye etiketlenir.
 */
private fun describeMalIdField(malId: Int?): String = when {
    malId == null -> "yok"
    malId in 300_000_001..399_999_999 -> "$malId (iç Kitsu kimliği, MAL değil: Kitsu #${malId - 300_000_000})"
    malId in 100_000_001..299_999_999 -> "$malId (iç AniList kimliği, MAL değil: AniList #${malId - 100_000_000})"
    else -> "$malId (MAL)"
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
    appendLine("Ham kimlik alanları: malId=${describeMalIdField(malId)}, aniListEntryId=$aniListEntryId, malListId=$malListId, simklId=$simklId, tmdbId=$tmdbId")
    appendLine("Durum/ilerleme: ${status.plainLabel()}, bölüm=$progress/${total ?: "?"}, cilt=$volumeProgress, puan=${score ?: "yok"}")
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

    var isBangumiConnected by mutableStateOf(false)
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

    var isBangumiImportRunning by mutableStateOf(false)
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
        isBangumiConnected = state.isBangumiConnected

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
                "bangumi" -> settings.clearBangumiProfileInfo()
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
                "bangumi" -> "Bangumi bağlantısı kesildi"
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
        var bangumi: MediaEntry? = null,
        var identityReviewRequired: Boolean = false,
        var identityReviewDetails: String? = null
    ) {
        val candidates: List<MediaEntry>
            get() = listOfNotNull(aniList, mal, simkl, kitsu, shikimori, bangumi)

        val primaryTitle: String
            get() = candidates.firstOrNull()?.titleEnglish?.takeIf { it.isNotBlank() }
                ?: candidates.firstOrNull()?.title ?: "Bilinmeyen"

        val mediaType: MediaType
            get() = candidates.firstOrNull()?.type ?: MediaType.Anime
    }

    private fun Int.isRealMalId(): Boolean = this in 1..99_999_999

    @Suppress("UNUSED_PARAMETER")
    private suspend fun resolveRealMalId(entry: MediaEntry, aniListToken: String?): Int? {
        val existing = entry.malId?.takeIf { it.isRealMalId() }
        if (existing != null) return existing

        val isAnimeOrManga = entry.type == MediaType.Anime || entry.type == MediaType.Manga
        if (!isAnimeOrManga) return null

        val rawAniListId = if (entry.source == "anilist" && entry.malId != null && entry.malId >= 100_000_000 && entry.malId < 300_000_000) {
            entry.malId - 100_000_000
        } else null

        val rawKitsuId = entry.malId?.takeIf { it in 300_000_001..399_999_999 }?.minus(300_000_000)

        // Not: AniList içe aktarımı `idMal` alanını zaten getiriyor; sentetik (100M+) kimlik taşıyan bir
        // AniList kaydı için tekrar Media(idMal) sorgusu atmak hem sonuçsuz hem de 30 istek/dk kotasını
        // tüketip gerçek yazma isteklerinin 429 almasına yol açıyordu. Bu adım bilinçli olarak kaldırıldı.

        // GÜVENLİK: ARM sorgusuna TMDB kimliği bilinçli olarak verilmez. TMDB'de film ve dizi kimlik uzayları
        // çakışır ve tek bir TMDB dizi kimliği bir franchise'ın tüm sezonlarını kapsar; ARM bu durumda rastgele
        // bir sezonun/yanlış bir yapımın MAL ID'sini döndürüyordu. Bu, kullanıcının hiç eklemediği animelerin
        // hesaplarına yazılmasının başlıca nedeniydi. Yalnızca birebir (1:1) kimlikler (AniList/Kitsu) kullanılır.
        val armMal = if (rawAniListId != null || rawKitsuId != null) {
            runSyncCatching {
                com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                    malId = null,
                    aniListId = rawAniListId,
                    tmdbId = null,
                    mediaType = entry.type,
                    kitsuId = rawKitsuId
                ).malId
            }.getOrNull()
        } else null
        if (armMal != null && armMal.isRealMalId()) return armMal

        val searchTitle = entry.titleEnglish?.takeIf { it.isNotBlank() }
            ?: entry.title.takeIf { it.isNotBlank() }
            ?: entry.titleJapanese?.takeIf { it.isNotBlank() }
        if (!searchTitle.isNullOrBlank()) {
            val jikanResults = runSyncCatching {
                // Jikan kota kapısı (JikanGateway) searchMALOnly içinde uygulanır.
                com.kitsugi.animelist.data.remote.JikanSearchClient().searchMALOnly(
                    query = searchTitle,
                    mediaType = entry.type
                )
            }.getOrNull()
            val candidateTitles = listOfNotNull(entry.title, entry.titleEnglish, entry.titleJapanese)
                .map { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                .filter { it.isNotBlank() }
            val jikanMal = jikanResults?.filter { res ->
                res.type == entry.type && (entry.year == null || res.year == entry.year) &&
                    listOfNotNull(res.title, res.titleEnglish, res.titleJapanese).any { resTitle ->
                        val norm = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(resTitle)
                        candidateTitles.any { it == norm }
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
            val bangumiToken = if (syncSettings.syncEnabledBangumi) BangumiAuthStore.getValidToken(context) else null

            val isAniList = syncSettings.syncEnabledAnilist && !aniListToken.isNullOrBlank()
            val isMal = syncSettings.syncEnabledMal && !malToken.isNullOrBlank()
            val isSimkl = syncSettings.syncEnabledSimkl && !simklToken.isNullOrBlank()
            val isKitsu = syncSettings.syncEnabledKitsu && !kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()
            val isShikimori = syncSettings.syncEnabledShikimori && !shikimoriToken.isNullOrBlank() && shikimoriUserId != null
            val isBangumi = syncSettings.syncEnabledBangumi && !bangumiToken.isNullOrBlank()

            val connectedPlatforms = mutableListOf<String>()
            if (isAniList) connectedPlatforms.add("AniList")
            if (isMal) connectedPlatforms.add("MyAnimeList")
            if (isSimkl) connectedPlatforms.add("Simkl")
            if (isKitsu) connectedPlatforms.add("Kitsu")
            if (isShikimori) connectedPlatforms.add("Shikimori")
            if (isBangumi) connectedPlatforms.add("Bangumi")

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

                val bangumiEntries = if (isBangumi) {
                    updateProgress("Bangumi listesi alınıyor...", "Kütüphaneniz okunuyor...")
                    val list = BangumiImportManager.fetchAllLists(context, bangumiToken!!)
                    statsMap["Bangumi"] = statsMap["Bangumi"]!!.copy(initialCount = list.size)
                    logEvent("Bangumi", "Bangumi kütüphanesinden ${list.size} kayıt alındı.")
                    list
                } else emptyList()

                // ── FAZ 2: Çoklu İndeks ile Unified Eşleştirme ────────────────────
                val sourceEntryTotal = aniListEntries.size + malEntries.size + simklEntries.size +
                    kitsuEntries.size + shikimoriEntries.size + bangumiEntries.size
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

                    // Aynı başlık, farklı sağlayıcı kimliği (ör. 1999 ve 2011 Hunter x Hunter, remake'ler, aynı adlı
                    // sezonlar): `sameMedia` çelişen kimlikleri zaten birleştirmez; bu kayıtlar ayrı gruplar olarak
                    // kendi kimlikleriyle güvenle eşitlenebilir. Eskiden tüm kayıt "kimlik doğrulaması gerekli" diye
                    // izole edilip hiçbir hesaba yazılmıyordu; bu, Simkl/MAL/Shikimori'deki yüzlerce "atlandı"
                    // kaydının ana kaynağıydı. Artık yalnızca bilgi amaçlı rapora not düşülür.
                    if (sameTitleIdConflicts.isNotEmpty() && matches.isEmpty()) {
                        val conflictKeys = sameTitleIdConflicts.flatMap { group ->
                            group.candidates.flatMap { candidate -> identity.conflictingIdentityKeys(candidate, entry) }
                        }.distinct()
                        logEvent(
                            "Eşleştirme",
                            "Aynı başlıklı farklı yapım ayrı grup olarak işlendi: ${entry.title}",
                            details = buildString {
                                appendLine("Sağlayıcı kimlikleri farklı olduğu için mevcut grupla birleştirilmedi; kayıt kendi kimliğiyle eşitlenecek.")
                                appendLine("Gelen kayıt:")
                                appendLine(describeIdentityCandidate(entry))
                                appendLine("Aynı başlığı taşıyan mevcut kayıtlar:")
                                sameTitleIdConflicts.flatMap { it.candidates }.distinct().forEach { candidate ->
                                    appendLine(describeIdentityCandidate(candidate))
                                }
                                append("Farklı kimlik alanları: ")
                                append(conflictKeys.joinToString().ifBlank { "bilinmiyor" })
                            },
                            includeInLiveLog = false
                        )
                    }

                    val item = when {
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
                                // Aynı sağlayıcı kimliği (ör. aynı MAL ID) paylaşan kayıtlar normalde aynı yapımdır;
                                // alias farkı çoğunlukla dil/çeviri farkıdır (Simkl İngilizce, Shikimori romaji, Kitsu
                                // kanonik). Birleştirme için iki koşul birden aranır: (a) yıllar uyumlu (bilinmiyor ya da
                                // en fazla 1 yıl fark) ve (b) başlıklar akraba (ortak ayırt edici kelime / ön ek / EN-JP
                                // alias). Aksi halde sağlayıcı eşlemesi hatalı kabul edilir ve yazma durdurulur — böylece
                                // yanlış eşlenmiş bir kimlik üzerinden kullanıcının eklemediği içerik hesaplara yazılmaz.
                                val suspiciousMatches = unrelatedTitleIds.filter { candidate ->
                                    !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.yearsCompatible(candidate.year, entry.year) ||
                                        !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.entriesLookRelated(candidate, entry)
                                }
                                if (suspiciousMatches.isNotEmpty()) {
                                    val details = buildString {
                                        appendLine("Aynı harici kimlik(ler) eşleşti, ancak başlıklar akraba değil ve/veya yayın yılları uyuşmuyor; sağlayıcı eşlemesi hatalı olabilir. Yanlış içerik eklenmesini önlemek için otomatik yazma durduruldu.")
                                        appendLine("Ortak kimlikler: ${sharedIds.joinToString()}")
                                        appendLine("Gelen kayıt:")
                                        appendLine(describeIdentityCandidate(entry))
                                        appendLine("Eşleşen kayıt(lar):")
                                        suspiciousMatches.forEach { appendLine(describeIdentityCandidate(it)) }
                                    }
                                    match.identityReviewRequired = true
                                    match.identityReviewDetails = details
                                } else {
                                    logEvent(
                                        "Eşleştirme",
                                        "Ortak kimlikle birleştirildi (başlık alias'ları farklı): ${entry.title}",
                                        details = buildString {
                                            appendLine("Ortak kimlikler: ${sharedIds.joinToString()}")
                                            appendLine("Gelen kayıt:")
                                            appendLine(describeIdentityCandidate(entry))
                                            appendLine("Eşleşen kayıt(lar):")
                                            unrelatedTitleIds.forEach { appendLine(describeIdentityCandidate(it)) }
                                        },
                                        includeInLiveLog = false
                                    )
                                }
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
                            // Tekil seçim de tek aday dalındaki güvenlik kontrolünden geçmeli: seçilen grubun herhangi bir
                            // kaydı yıl olarak uyuşmuyorsa veya ortak kimlikli kayıt başlıkça akraba değilse grup birleştirilmez.
                            // Güncellemeler de grup doğruluğuna bağlı olduğu için bu durumda hiçbir hesaba yazılmaz.
                            val identityConflictedCandidates = resolvedMatch?.candidates.orEmpty().filter { candidate ->
                                val sharesId = identity.keys(candidate).intersect(entryKeys).isNotEmpty()
                                !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.yearsCompatible(candidate.year, entry.year) ||
                                    (sharesId && titleAliases(candidate).intersect(entryTitles).isEmpty() &&
                                        !com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.entriesLookRelated(candidate, entry))
                            }
                            if (resolvedMatch != null && identityConflictedCandidates.isEmpty()) {
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
                                    appendLine(
                                        if (resolvedMatch != null)
                                            "Tekil seçilen grubun başlık/yıl bilgisi gelen kayıtla uyuşmadı; yanlış birleştirmeyi önlemek için seçim reddedildi. Kayıt başka hesaplara yazılmadı."
                                        else
                                            "Birden fazla olası grup arasında güvenilir tek bir seçim yapılamadı. Kayıt başka hesaplara yazılmadı."
                                    )
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
                clusterEntries(bangumiEntries) { item, entry -> item.bangumi = entry }

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
                // Kayıt bazlı "atlandı" olayları artık tek tek uyarı üretmez; başlıklar toplanır ve
                // eşitleme sonunda platform başına tek bir özet uyarı yazılır (ayrıntılarda tam liste).
                val skippedTitlesByReason = linkedMapOf<String, MutableList<String>>()
                fun recordSkip(platform: String, reasonKey: String, title: String, message: String, details: String) {
                    statsMap[platform]?.let { statsMap[platform] = it.copy(skippedCount = it.skippedCount + 1) }
                    skippedTitlesByReason.getOrPut("$platform|$reasonKey") { mutableListOf() }.add(title)
                    logEvent(platform, message, details = details, includeInLiveLog = false)
                }
                fun flushSkipSummaries() {
                    skippedTitlesByReason.forEach { (key, titles) ->
                        if (titles.isEmpty()) return@forEach
                        val platform = key.substringBefore('|')
                        val reason = key.substringAfter('|')
                        logEvent(
                            platform,
                            "[$platform] ${titles.size} kayıt atlandı: $reason",
                            isWarning = true,
                            details = "Atlanan kayıtlar (${titles.size}):\n" + titles.joinToString("\n") { "• $it" }
                        )
                    }
                    skippedTitlesByReason.clear()
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
                        if (isBangumi && item.bangumi == null && reviewIsAnimeOrManga) countSafetySkip("Bangumi")
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
                    // Simkl'in `total` değeri "yayınlanmış bölüm sayısı"dır (devam eden dizilerde toplam değil);
                    // bu sayıdan "Tamamlandı" çıkarımı yapmak devam eden yapımları yanlışlıkla bitmiş gösteriyordu.
                    // Çıkarım için yalnızca MAL/AniList/Kitsu/Shikimori toplamları kullanılır.
                    val totalCount = candidates
                        .filter { com.kitsugi.animelist.model.MediaIdentity.canonicalSource(it.source) != "simkl" }
                        .firstNotNullOfOrNull { it.total?.takeIf { t -> t > 0 } }
                    val singleSourceGroup = candidates.size == 1
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

                    // 6b. Kimlik güvencesi — yanlış içerik EKLENMESİNE karşı son savunma hattı.
                    // Güncellemeler platformun kendi kayıt kimliğiyle yapıldığı için güvenlidir; risk yalnızca
                    // "bu platformda yok → ekle" yolundadır. Grubun MAL kimliği doğal kaynaktan (MAL/Shikimori)
                    // gelmiyorsa ve en az iki bağımsız platform tarafından doğrulanmıyorsa, kataloğa karşı
                    // başlık + yıl doğrulaması yapılır. Reddedilen/doğrulanamayan kimlikle hiçbir platforma
                    // yeni kayıt eklenmez; mevcut kayıtların güncellenmesi etkilenmez.
                    val needsAnyAddition =
                        (isAniList && isAnimeOrManga && item.aniList == null) ||
                        (isMal && isAnimeOrManga && item.mal == null) ||
                        (isKitsu && isAnimeOrManga && item.kitsu == null) ||
                        (isShikimori && isAnimeOrManga && item.shikimori == null) ||
                        (isBangumi && isAnimeOrManga && item.bangumi == null) ||
                        (isSimkl && item.simkl == null)
                    val identityVerdict: com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict? =
                        if (realMalId != null && needsAnyAddition) {
                            val holderSources = candidates
                                .filter { it.malId == realMalId }
                                .map { it.source.lowercase() }
                                .distinct()
                            when {
                                holderSources.any { it in com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.NATIVE_MAL_SOURCES } ->
                                    com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Trusted("MAL/Shikimori kaydının kendi kimliği")
                                holderSources.size >= 2 ->
                                    com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Trusted("${holderSources.joinToString("+")} aynı MAL ID'yi bağımsız bildirdi")
                                else -> com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.verifyMalId(
                                    malId = realMalId,
                                    mediaType = newest.type,
                                    localTitles = candidates.flatMap { com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.aliasesOf(it) },
                                    localYear = candidates.firstNotNullOfOrNull { it.year }
                                )
                            }
                        } else null
                    val additionsAllowed = identityVerdict?.allowsAdditions ?: true
                    // Eklemelerde kullanılacak MAL kimliği: doğrulanmadıysa yok sayılır.
                    val guardedMalId: Int? = realMalId?.takeIf { additionsAllowed }
                    val identitySkipReason = "kimlik doğrulanamadı (MAL #${realMalId} ↔ yerel başlık/yıl uyuşmuyor; yanlış içerik eklenmesin diye atlandı)"
                    fun recordIdentitySkip(platform: String, title: String) {
                        recordSkip(
                            platform = platform,
                            reasonKey = identitySkipReason,
                            title = title,
                            message = "[$platform] - Atlandı (kimlik doğrulanamadı): $title",
                            details = "Kimlik güvencesi: ${identityVerdict?.describe() ?: "-"}\nYeni kayıt eklenmedi; mevcut kayıtlar etkilenmez."
                        )
                    }
                    if (identityVerdict is com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.Verdict.Rejected) {
                        logEvent(
                            "Eşleştirme",
                            "Şüpheli kimlik, ekleme engellendi: ${newest.title} → MAL #$realMalId",
                            isWarning = true,
                            details = buildString {
                                appendLine(identityVerdict.describe())
                                appendLine("Bu grubun MAL kimliği tek bir sağlayıcı eşlemesinden/çıkarımdan geliyor ve MAL kataloğundaki içerikle uyuşmuyor.")
                                appendLine("Hiçbir platforma yeni kayıt eklenmeyecek; mevcut kayıtların güncellenmesi etkilenmez.")
                                appendLine("Kaynak kayıtlar:")
                                candidates.forEach { appendLine(it.crossSyncIdentityDiagnostic()) }
                            }
                        )
                    }

                    val bestTitleEnglish = candidates.firstNotNullOfOrNull { it.titleEnglish?.takeIf { e -> e.isNotBlank() } }
                    val bestTitleJapanese = candidates.firstNotNullOfOrNull { it.titleJapanese?.takeIf { j -> j.isNotBlank() } }

                    val mergedEntry = newest.copy(
                        titleEnglish = bestTitleEnglish ?: newest.titleEnglish,
                        titleJapanese = bestTitleJapanese ?: newest.titleJapanese,
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
                        // Doğrulanmamış gerçek MAL kimliği birleşik kayda taşınmaz (sentetik AniList/Kitsu kimlikleri korunur).
                        malId = if (additionsAllowed) (realMalId ?: newest.malId) else newest.malId?.takeIf { !it.isRealMalId() },
                        simklId = simklId ?: newest.simklId,
                        tmdbId = tmdbId ?: newest.tmdbId,
                        aniListEntryId = aniListEntryId ?: newest.aniListEntryId
                    )

                    if (realMalId != null && item.bangumi != null) {
                        com.kitsugi.animelist.data.remote.BangumiIdNamespace.rawIdFromStable(item.bangumi?.malId)?.let { subjectId ->
                            runCatching {
                                com.kitsugi.animelist.data.remote.BangumiLocalMappingCache.put(context, realMalId, isAnimeOrManga, subjectId)
                            }
                        }
                    }

                    val mappingDetails = buildString {
                        appendLine("Kaynak platform kayıtları (${candidates.size}):")
                        candidates.forEachIndexed { candidateIndex, candidate ->
                            appendLine("Kayıt ${candidateIndex + 1}:")
                            appendLine(candidate.crossSyncIdentityDiagnostic())
                        }
                        appendLine("Eşitleme için birleştirilen değerler:")
                        appendLine(mergedEntry.crossSyncIdentityDiagnostic())
                        append("Kimlik güvencesi: ")
                        append(
                            identityVerdict?.describe()
                                ?: if (realMalId == null) "MAL kimliği yok (yalnızca platformun kendi kimliğiyle işlem yapılır)"
                                else "gerek yok (tüm hedef platformlarda kayıt zaten var)"
                        )
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
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("AniList", mergedEntry.title)
                        } else if (current == null) {
                            // AniList'te eksik -> EKLE!
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("anilist")
                            val res = runSyncCatching { AniListSyncManager.updateAniListEntry(aniListToken!!, mergedEntry) }
                            if (res.isSuccess && res.getOrNull() != null) {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("AniList", "[AniList] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true, details = mappingDetails)
                            } else if (res.isSuccess) {
                                // null = AniList'te hangi medya olduğu güvenle çözülemedi (MAL ID/ARM eşlemesi yok).
                                // Bu bir API hatası değil; kayıt atlanır ve sonda tek bir özet uyarı verilir.
                                recordSkip(
                                    platform = "AniList",
                                    reasonKey = "AniList medya kimliği çözülemedi (MAL ID veya ARM eşlemesi yok)",
                                    title = mergedEntry.title,
                                    message = "[AniList] - Atlandı (medya kimliği çözülemedi): ${mergedEntry.title}",
                                    details = mappingDetails
                                )
                            } else {
                                statsMap["AniList"] = statsMap["AniList"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                val failure = res.exceptionOrNull()?.crossSyncDiagnostic()
                                    ?: "AniList yeni liste kaydını onaylamadı."
                                logEvent("AniList", "[AniList] ! Eklenemedi: ${mergedEntry.title}", isError = true, details = "$failure\n$mappingDetails")
                            }
                        } else {
                            val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore) ||
                                    (current.startDate.isNullOrBlank() && !bestStartDate.isNullOrBlank()) ||
                                    (current.endDate.isNullOrBlank() && !bestEndDate.isNullOrBlank()))
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
                        if (realMalId != null && item.mal == null && !additionsAllowed) {
                            recordIdentitySkip("MyAnimeList", mergedEntry.title)
                        } else if (realMalId != null) {
                            val current = item.mal
                            if (current == null) {
                                // MAL'da eksik -> EKLE! (kimlik güvencesinden geçti)
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("mal")
                                val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
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
                                val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                        (current.progress < maxProgress) ||
                                        (bestScore != null && current.score != bestScore) ||
                                        (current.startDate.isNullOrBlank() && !bestStartDate.isNullOrBlank()) ||
                                        (current.endDate.isNullOrBlank() && !bestEndDate.isNullOrBlank()))
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
                            recordSkip(
                                platform = "MyAnimeList",
                                reasonKey = "doğrulanmış MAL ID yok (AniList/Kitsu/Simkl kaydı MAL'a eşlenemedi)",
                                title = mergedEntry.title,
                                message = "[MAL] - Atlandı (doğrulanmış MAL ID yok): ${mergedEntry.title}",
                                details = "MAL ID çözümlemesi başarısız oldu veya güvenli bir kimlik bulunamadı.\n$mappingDetails"
                            )
                        }
                    }

                    // ── 3. Simkl Eşitleme (Anime, Dizi, Film — Tümü) ──
                    if (isSimkl && (mergedEntry.type == MediaType.Anime || mergedEntry.type == MediaType.TvShow || mergedEntry.type == MediaType.Movie)) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.simkl
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Simkl", mergedEntry.title)
                        } else if (current == null) {
                            simklEntriesToSync.add(mergedEntry)
                        } else {
                            val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore))
                            if (needsUpdate) {
                                simklEntriesToSync.add(mergedEntry.copy(simklId = current.simklId))
                            }
                        }
                    }

                    // ── 4. Kitsu Eşitleme (Anime & Manga) ──
                    if (isKitsu && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.kitsu
                        // Kitsu sentetik kimlik aralığı 300M–400M; 400M+ Shikimori'ye ait, yanlışlıkla Kitsu ID sanılmasın.
                        val knownKitsuId = current?.malId?.takeIf { it in 300_000_001..399_999_999 }?.let { it - 300_000_000 }
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Kitsu", mergedEntry.title)
                        } else if (current == null) {
                            // Kitsu'da eksik -> EKLE! (hız sınırı artık KitsuApiClient içinde her istek için uygulanıyor)
                            val outcome = runSyncCatching {
                                KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = null)
                            }
                            val res = outcome.getOrNull()
                            if (res != null && res.errors.isEmpty()) {
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("Kitsu", "[Kitsu] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true, details = mappingDetails + (res.resolvedVia?.let { "\nKitsu kimliği kaynağı: $it" } ?: ""))
                            } else if (res != null && res.unresolvedMedia) {
                                recordSkip(
                                    platform = "Kitsu",
                                    reasonKey = "Kitsu kataloğunda güvenilir eşleşme bulunamadı (mappings/ARM/başlık)",
                                    title = mergedEntry.title,
                                    message = "[Kitsu] - Atlandı (Kitsu kimliği çözülemedi): ${mergedEntry.title}",
                                    details = mappingDetails
                                )
                            } else {
                                val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic()
                                    ?: res?.errors?.joinToString("; ")
                                    ?: "Bilinmeyen hata"
                                statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                logEvent("Kitsu", "[Kitsu] ! Hata: ${mergedEntry.title} ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                            }
                        } else {
                            val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                    (current.progress < maxProgress) ||
                                    (bestScore != null && current.score != bestScore))
                            if (needsUpdate) {
                                val outcome = runSyncCatching {
                                    KitsuSyncManager.syncEntryToKitsu(context, mergedEntry, knownKitsuMediaId = knownKitsuId)
                                }
                                val res = outcome.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Kitsu"] = statsMap["Kitsu"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent("Kitsu", "[Kitsu] ~ Güncellendi: ${mergedEntry.title}", isUpdate = true, details = mappingDetails)
                                } else if (res != null && res.unresolvedMedia) {
                                    recordSkip(
                                        platform = "Kitsu",
                                        reasonKey = "Kitsu kataloğunda güvenilir eşleşme bulunamadı (mappings/ARM/başlık)",
                                        title = mergedEntry.title,
                                        message = "[Kitsu] - Atlandı (Kitsu kimliği çözülemedi): ${mergedEntry.title}",
                                        details = mappingDetails
                                    )
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
                        if (realMalId != null && item.shikimori == null && !additionsAllowed) {
                            recordIdentitySkip("Shikimori", mergedEntry.title)
                        } else if (realMalId != null) {
                            val current = item.shikimori
                            if (current == null) {
                                // Shikimori'de eksik -> EKLE! (kimlik güvencesinden geçti)
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("shikimori")
                                val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
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
                                val needsUpdate = !singleSourceGroup && ((current.status != bestStatus) ||
                                        (current.progress < maxProgress) ||
                                        (bestScore != null && current.score != bestScore))
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
                            recordSkip(
                                platform = "Shikimori",
                                reasonKey = "doğrulanmış MAL ID yok (Shikimori kimlikleri MAL ID ile aynıdır)",
                                title = mergedEntry.title,
                                message = "[Shikimori] - Atlandı (doğrulanmış MAL ID yok): ${mergedEntry.title}",
                                details = "Shikimori senkronizasyonu için doğrulanmış MAL ID gerekiyor.\n$mappingDetails"
                            )
                        }
                    }

                    // ── 6. Bangumi Eşitleme (Anime & Manga) ──
                    if (isBangumi && isAnimeOrManga) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val current = item.bangumi
                        if (current == null && !additionsAllowed) {
                            recordIdentitySkip("Bangumi", mergedEntry.title)
                        } else if (current == null) {
                            // Bangumi'de eksik -> EKLE! (kimlik güvencesinden geçti)
                            com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("bangumi")
                            val target = mergedEntry.copy(malId = guardedMalId ?: realMalId)
                            val outcome = runSyncCatching { BangumiSyncManager.syncEntryToBangumi(context, target) }
                            val res = outcome.getOrNull()
                            if (res != null && res.errors.isEmpty()) {
                                statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(addedCount = it.addedCount + 1) }
                                logEvent("Bangumi", "[Bangumi] + Eklendi: ${mergedEntry.title} (${bestStatus.label})", isAddition = true, details = mappingDetails)
                            } else if (res != null && res.errors.any { it.contains("eşleşmesi bulunamadı") || it.contains("bulunamadı") }) {
                                recordSkip(
                                    platform = "Bangumi",
                                    reasonKey = "Bangumi条目 eşleşmesi bulunamadı",
                                    title = mergedEntry.title,
                                    message = "[Bangumi] - Atlandı (Bangumi eşleşmesi bulunamadı): ${mergedEntry.title}",
                                    details = "$mappingDetails\n${res.errors.joinToString("; ")}"
                                )
                            } else {
                                val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic()
                                    ?: res?.errors?.joinToString("; ")
                                    ?: "Bilinmeyen hata"
                                statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                logEvent("Bangumi", "[Bangumi] ! Hata: ${mergedEntry.title} ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                            }
                        } else {
                            val needsUpdate = !singleSourceGroup && (
                                (current.status != bestStatus) ||
                                (current.progress < maxProgress) ||
                                (current.volumeProgress < maxVolumeProgress) ||
                                (bestScore != null && current.score != bestScore)
                            )
                            if (needsUpdate) {
                                com.kitsugi.animelist.data.auth.PlatformRateLimiter.acquire("bangumi")
                                val target = mergedEntry.copy(malId = current.malId ?: (guardedMalId ?: realMalId))
                                val outcome = runSyncCatching { BangumiSyncManager.syncEntryToBangumi(context, target) }
                                val res = outcome.getOrNull()
                                if (res != null && res.errors.isEmpty()) {
                                    statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(updatedCount = it.updatedCount + 1) }
                                    logEvent(
                                        "Bangumi",
                                        "[Bangumi] ~ Güncellendi: ${mergedEntry.title} (${bestStatus.label}, Bölüm: $maxProgress)",
                                        isUpdate = true,
                                        details = mappingDetails
                                    )
                                } else {
                                    val errMsg = outcome.exceptionOrNull()?.crossSyncDiagnostic()
                                        ?: res?.errors?.joinToString("; ")
                                        ?: "Bilinmeyen hata"
                                    statsMap["Bangumi"] = statsMap["Bangumi"]!!.let { it.copy(errorCount = it.errorCount + 1) }
                                    logEvent("Bangumi", "[Bangumi] ! Güncellenemedi: ${mergedEntry.title} ($errMsg)", isError = true, details = "$mappingDetails\n$errMsg")
                                }
                            }
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
                    // Kayıt bazlı muhasebe: Simkl `not_found` ile geri gönderdiği kayıtlar "atlandı",
                    // yalnızca HTTP/ağ seviyesinde yazılamayan kayıtlar "hata" sayılır. Eskiden grupta tek
                    // bir eşleşmeyen kayıt bile 35 kaydın tamamını hata olarak saydırıyordu (297 atlandı / 200 hata).
                    val simklUnmatchedTitles = mutableListOf<String>()
                    val simklProgressLimitedTitles = mutableListOf<String>()
                    val simklWarnings = mutableListOf<String>()
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
                        simklErrors += syncRes.failedCount
                        simklUnmatchedTitles.addAll(syncRes.unmatchedTitles)
                        simklProgressLimitedTitles.addAll(syncRes.unsupportedProgressTitles)
                        simklWarnings.addAll(syncRes.warnings)
                        val chunkDetails = chunk.joinToString("\n\n") { it.crossSyncIdentityDiagnostic() }
                        val groupLabel = "Grup ${idx + 1}/${chunks.size}"
                        if (syncRes.errors.isEmpty()) {
                            logEvent(
                                "Simkl",
                                "[Simkl] $groupLabel eşitlendi ($count eklendi${if (notFound > 0) ", $notFound Simkl'de bulunamadı" else ""})",
                                isAddition = count > 0,
                                details = buildString {
                                    if (syncRes.unmatchedTitles.isNotEmpty()) {
                                        appendLine("Simkl'de karşılığı bulunamayan kayıtlar (${syncRes.unmatchedTitles.size}):")
                                        syncRes.unmatchedTitles.forEach { appendLine("• $it") }
                                        appendLine()
                                    }
                                    append("Grup içindeki kayıtlar:")
                                    appendLine()
                                    append(chunkDetails)
                                }
                            )
                        } else {
                            syncRes.errors.forEach { message ->
                                logEvent(
                                    "Simkl",
                                    "[Simkl] $groupLabel: $message",
                                    isError = true,
                                    details = "Grup özeti: $count eklendi, $notFound Simkl'de bulunamadı, ${syncRes.failedCount} yazılamadı. Grup içindeki kayıtlar:\n$chunkDetails"
                                )
                            }
                            if (count > 0) {
                                logEvent(
                                    "Simkl",
                                    "[Simkl] $groupLabel kısmen eşitlendi ($count eklendi)",
                                    isAddition = true,
                                    details = chunkDetails,
                                    includeInLiveLog = false
                                )
                            }
                        }
                        if (idx < chunks.size - 1) {
                            // Simkl API 1 istek/saniye kuralına tam uyum
                            kotlinx.coroutines.delay(1200L)
                        }
                    }
                    if (simklUnmatchedTitles.isNotEmpty()) {
                        val distinctUnmatched = simklUnmatchedTitles.distinct()
                        logEvent(
                            "Simkl",
                            "[Simkl] ${distinctUnmatched.size} kayıt Simkl kataloğunda bulunamadı (atlandı)",
                            isWarning = true,
                            details = "Simkl bu kayıtları `not_found` olarak geri gönderdi; kimlik eşlemesi (MAL/TMDB/AniList) Simkl tarafında yok veya başlık eşleşmedi. Kayıtlar diğer hesaplarda eşitlenmeye devam eder.\n\n" +
                                distinctUnmatched.joinToString("\n") { "• $it" }
                        )
                    }
                    if (simklProgressLimitedTitles.isNotEmpty()) {
                        val distinctLimited = simklProgressLimitedTitles.distinct()
                        logEvent(
                            "Simkl",
                            "[Simkl] ${distinctLimited.size} dizinin bölüm ilerlemesi aktarılmadı (liste durumu yazıldı)",
                            isWarning = true,
                            details = "Simkl, diziler için toplam bölüm sayısı yerine sezon/bölüm bazlı izleme geçmişi ister; bu eşleme henüz desteklenmiyor. Liste durumu ve puan aktarıldı.\n\n" +
                                distinctLimited.joinToString("\n") { "• $it" }
                        )
                    }
                    if (simklWarnings.isNotEmpty()) {
                        logEvent(
                            "Simkl",
                            "[Simkl] ${simklWarnings.size} kısmi işlem uyarısı (izleme geçmişi/puan)",
                            isWarning = true,
                            details = simklWarnings.joinToString("\n") { "• $it" }
                        )
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

                // Platform başına tek satırlık "atlandı" özetleri (ayrıntılarda tam başlık listesi)
                flushSkipSummaries()

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
                if (isBangumi) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    updateProgress("Bangumi listesi doğrulanıyor...", "Sunucudaki son liste okunuyor", processed = 0, total = 0, unit = "")
                    runSyncCatching { repository.smartImport("bangumi", BangumiImportManager.fetchAllLists(context, bangumiToken!!), allowDelete = false) }
                        .onFailure { logEvent("Bangumi", "Yerel liste güncellenemedi: ${it.message}", isError = true, details = it.crossSyncDiagnostic()) }
                }

                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val hasSyncErrors = statsMap.values.any { it.errorCount > 0 } || reportLogs.any { it.isError }
                val hasWarnings = reportLogs.any { it.isWarning }
                val totalErrors = statsMap.values.sumOf { it.errorCount }
                val totalSkipped = statsMap.values.sumOf { it.skippedCount }
                val summary = when {
                    hasSyncErrors -> "Eşitleme kısmen tamamlandı: $totalErrors işlem yazılamadı, $totalSkipped kayıt atlandı. Ayrıntılar için hata ve uyarı kayıtlarını inceleyin."
                    hasWarnings -> "Eşitleme tamamlandı. $totalSkipped kayıt güvenlik nedeniyle atlandı; uyarı özetlerini inceleyin."
                    else -> "Eşitleme tamamlandı."
                }
                // Özet satırı kendisi bir sorun değildir; hata/uyarı sayacına eklenmez (eskiden kırmızı
                // "hata" olarak listelenip issue sayısını şişiriyordu).
                logEvent("Tamamlandı", summary)

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

    /**
     * Bangumi (bgm.tv) OAuth girişi.

     * Shikimori'den farklı olarak Bangumi'de paylaşılan varsayılan uygulama kaydı yoktur:
     * token ucu `client_secret` istediği için her uygulama https://bgm.tv/dev/app üzerinden
     * kendi App ID / App Secret değerini almalıdır. Kimlik bilgileri burada saklanır ve
     * sonraki token yenilemelerinde yeniden kullanılır.
     */
    fun loginBangumi(
        clientId: String,
        clientSecret: String,
        authCode: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        BangumiAuthStore.saveCredentials(context, clientId, clientSecret)
        val pendingRedirectUri = BangumiAuthStore.getPendingRedirectUri(context)
        BangumiAuthManager.exchangeCode(
            context = context,
            code = authCode,
            redirectUri = pendingRedirectUri,
            onSuccess = {
                refreshAuthState()
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val token = BangumiAuthStore.getToken(context)
                        if (!token.isNullOrBlank()) {
                            val profile = BangumiImportManager.fetchUserProfile(token)
                            SettingsDataStore(context)
                                .saveBangumiProfileInfo(profile.nickname, profile.avatarUrl)
                        }
                    }
                }
                onSuccess()
            },
            onError = { message -> onError(message) }
        )
    }

    /** Bangumi koleksiyonunu (想看/在看/看过) Kitsugi kütüphanesine içe aktarır. */
    fun importBangumiList(repository: MediaEntryRepository) {
        if (isBangumiImportRunning) return
        isBangumiImportRunning = true
        onShowMessage?.invoke("Bangumi listesi içe aktarılıyor...")

        viewModelScope.launch(Dispatchers.IO) {
            val token = BangumiAuthStore.getValidToken(context)
            if (token.isNullOrBlank()) {
                onShowMessage?.invoke("Bangumi token bulunamadı; lütfen tekrar giriş yapın")
                isBangumiImportRunning = false
                return@launch
            }
            BangumiAuthStore.ensureUserResolved(context)

            runSyncCatching {
                BangumiImportManager.fetchAllLists(context, token)
            }.onSuccess { importedEntries ->
                val importResult = runCatching {
                    repository.smartImport("bangumi", importedEntries, allowDelete = false)
                }
                if (importResult.isSuccess) {
                    onShowMessage?.invoke("${importedEntries.size} Bangumi kaydı başarıyla aktarıldı")
                } else {
                    onShowMessage?.invoke(
                        "Bangumi kaydedilirken sorun oluştu: ${importResult.exceptionOrNull()?.message}"
                    )
                }
            }.onFailure { error ->
                onShowMessage?.invoke(error.message ?: "Bangumi içe aktarma başarısız")
            }

            isBangumiImportRunning = false
        }
    }

    fun importBangumiList(
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) = importBangumiList(repository)


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
