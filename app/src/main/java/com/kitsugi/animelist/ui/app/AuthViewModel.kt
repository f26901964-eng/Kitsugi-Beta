package com.kitsugi.animelist.ui.app

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

            runCatching {
                runCatching {
                    val profile = AniListImportManager.fetchUserProfile(token)
                    settingsDataStore.setAniListProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = profile.bannerUrl.orEmpty()
                    )
                }
                AniListImportManager.fetchAllLists(token)
            }.onSuccess { importedEntries ->
                repository.smartImport("anilist", importedEntries)

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

            runCatching {
                runCatching {
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
                repository.smartImport("mal", importedEntries)

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

    fun syncPlatforms(
        repository: MediaEntryRepository
    ) {
        if (isCrossSyncRunning) {
            onShowMessage?.invoke("Eşitleme zaten devam ediyor...")
            return
        }

        isCrossSyncRunning = true
        onShowMessage?.invoke("Çoklu hesap eşitlemesi başlatıldı...")

        viewModelScope.launch(Dispatchers.IO) {
            val aniListToken = ExternalAuthManager.getAniListToken(context)
            val malToken = ExternalAuthManager.getOrRefreshMalToken(context)
            val simklToken = ExternalAuthManager.getSimklToken(context)
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val kitsuUserId = ExternalAuthManager.getKitsuUserId(context)
            val shikimoriToken = ExternalAuthManager.getOrRefreshShikimoriToken(context)
            val shikimoriUserId = ExternalAuthManager.getShikimoriUserId(context)

            val connectedPlatforms = mutableListOf<String>()
            if (!aniListToken.isNullOrBlank()) connectedPlatforms.add("AniList")
            if (!malToken.isNullOrBlank()) connectedPlatforms.add("MyAnimeList")
            if (!simklToken.isNullOrBlank()) connectedPlatforms.add("Simkl")
            if (!kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()) connectedPlatforms.add("Kitsu")
            if (!shikimoriToken.isNullOrBlank() && shikimoriUserId != null) connectedPlatforms.add("Shikimori")

            if (connectedPlatforms.size < 2) {
                onShowMessage?.invoke("Eşitleme yapabilmek için en az iki hesap (AniList, MAL, Simkl, Kitsu veya Shikimori) bağlı olmalıdır.")
                isCrossSyncRunning = false
                return@launch
            }

            val settingsDataStore = SettingsDataStore(context)
            runCatching {
                val showAdult = settingsDataStore.settingsFlow.first().showAdultContent
                val aniListEntries = if (!aniListToken.isNullOrBlank()) {
                    runCatching { AniListImportManager.fetchAllLists(aniListToken) }.getOrNull() ?: emptyList()
                } else emptyList()

                val malEntries = if (!malToken.isNullOrBlank()) {
                    runCatching { MalImportManager.fetchAllLists(malToken, showAdult) }.getOrNull() ?: emptyList()
                } else emptyList()

                val simklEntries = if (!simklToken.isNullOrBlank()) {
                    runCatching { SimklImportManager.fetchAllLists(simklToken) }.getOrNull() ?: emptyList()
                } else emptyList()

                val kitsuEntries = if (!kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()) {
                    runCatching { KitsuImportManager.fetchAllLists(context, kitsuToken, kitsuUserId) }.getOrNull() ?: emptyList()
                } else emptyList()

                val shikimoriEntries = if (!shikimoriToken.isNullOrBlank() && shikimoriUserId != null) {
                    runCatching { ShikimoriImportManager.fetchAllLists(context, shikimoriToken, shikimoriUserId) }.getOrNull() ?: emptyList()
                } else emptyList()

                // Tüm girişleri birleştir ve en son güncellenen duruma göre diğer platformlara eşitle
                val keyToAniList = mutableMapOf<String, MediaEntry>()
                val keyToMal = mutableMapOf<String, MediaEntry>()
                val keyToSimkl = mutableMapOf<String, MediaEntry>()
                val keyToKitsu = mutableMapOf<String, MediaEntry>()
                val keyToShikimori = mutableMapOf<String, MediaEntry>()

                fun getEntrySyncKey(entry: MediaEntry): String {
                    val realMal = entry.malId?.takeIf { it > 0 && it < 100_000_000 }
                    return when {
                        realMal != null -> "mal_$realMal"
                        entry.simklId != null && entry.simklId > 0 -> "simkl_${entry.simklId}"
                        entry.tmdbId != null && entry.tmdbId > 0 -> "tmdb_${entry.tmdbId}"
                        else -> "title_${entry.title.trim().lowercase()}_${entry.type.name}"
                    }
                }

                aniListEntries.forEach { e -> keyToAniList[getEntrySyncKey(e)] = e }
                malEntries.forEach { e -> keyToMal[getEntrySyncKey(e)] = e }
                simklEntries.forEach { e -> keyToSimkl[getEntrySyncKey(e)] = e }
                kitsuEntries.forEach { e -> keyToKitsu[getEntrySyncKey(e)] = e }
                shikimoriEntries.forEach { e -> keyToShikimori[getEntrySyncKey(e)] = e }

                val allKeys = (keyToAniList.keys + keyToMal.keys + keyToSimkl.keys + keyToKitsu.keys + keyToShikimori.keys).toSet()
                var syncCount = 0

                val finalEntries = mutableListOf<MediaEntry>()

                for (key in allKeys) {
                    val al = keyToAniList[key]
                    val mal = keyToMal[key]
                    val simkl = keyToSimkl[key]
                    val kitsu = keyToKitsu[key]
                    val shiki = keyToShikimori[key]

                    val candidates = listOfNotNull(al, mal, simkl, kitsu, shiki)
                    if (candidates.isEmpty()) continue

                    val newest = candidates.maxByOrNull { it.updatedAt } ?: candidates.first()
                    val isAnimeOrManga = newest.type == MediaType.Anime || newest.type == MediaType.Manga

                    var entryChanged = false

                    // 1. AniList bağlıysa ve eksikse/geriyse güncelle
                    if (!aniListToken.isNullOrBlank() && isAnimeOrManga) {
                        if (al == null || al.status != newest.status || al.progress != newest.progress || al.score != newest.score) {
                            runCatching {
                                AniListSyncManager.updateAniListEntry(aniListToken, newest)
                            }
                            entryChanged = true
                        }
                    }

                    // 2. MAL bağlıysa ve eksikse/geriyse güncelle
                    val realMalId = newest.malId?.takeIf { it > 0 && it < 100_000_000 }
                    if (!malToken.isNullOrBlank() && isAnimeOrManga && realMalId != null) {
                        if (mal == null || mal.status != newest.status || mal.progress != newest.progress || mal.score != newest.score) {
                            runCatching {
                                MalSyncManager.updateMalEntry(malToken, newest)
                            }
                            entryChanged = true
                        }
                    }

                    // 3. Simkl bağlıysa ve eksikse/geriyse güncelle
                    if (!simklToken.isNullOrBlank()) {
                        if (simkl == null || simkl.status != newest.status || simkl.progress != newest.progress || simkl.score != newest.score) {
                            runCatching {
                                SimklSyncManager.syncEntryToSimkl(context, newest)
                            }
                            entryChanged = true
                        }
                    }

                    // 4. Kitsu bağlıysa ve eksikse/geriyse güncelle
                    if (!kitsuToken.isNullOrBlank() && isAnimeOrManga) {
                        if (kitsu == null || kitsu.status != newest.status || kitsu.progress != newest.progress || kitsu.score != newest.score) {
                            runCatching {
                                KitsuSyncManager.syncEntryToKitsu(context, newest)
                            }
                            entryChanged = true
                        }
                    }

                    // 5. Shikimori bağlıysa ve eksikse/geriyse güncelle
                    if (!shikimoriToken.isNullOrBlank() && isAnimeOrManga && realMalId != null) {
                        if (shiki == null || shiki.status != newest.status || shiki.progress != newest.progress || shiki.score != newest.score) {
                            runCatching {
                                ShikimoriSyncManager.syncEntryToShikimori(context, newest)
                            }
                            entryChanged = true
                        }
                    }

                    if (entryChanged) syncCount++
                    finalEntries.add(newest)
                }

                // Yerel veritabanını güncelle
                if (!aniListToken.isNullOrBlank()) {
                    repository.smartImport("anilist", finalEntries.filter { it.source == "anilist" })
                }
                if (!malToken.isNullOrBlank()) {
                    repository.smartImport("mal", finalEntries.filter { it.source == "mal" || it.source == "jikan" })
                }
                if (!simklToken.isNullOrBlank()) {
                    repository.smartImport("simkl", finalEntries.filter { it.source == "simkl" })
                }
                if (!kitsuToken.isNullOrBlank()) {
                    repository.smartImport("kitsu", finalEntries.filter { it.source == "kitsu" })
                }
                if (!shikimoriToken.isNullOrBlank()) {
                    repository.smartImport("shikimori", finalEntries.filter { it.source == "shikimori" })
                }

                val platformsStr = connectedPlatforms.joinToString(", ")
                onShowMessage?.invoke("$syncCount içerik $platformsStr arasında başarıyla eşitlendi!")
            }.onFailure { error ->
                onShowMessage?.invoke("Eşitleme sırasında hata oluştu: ${error.message}")
            }
            isCrossSyncRunning = false
        }
    }

    fun loginKitsu(
        username: String,
        password: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
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
        clientId: String,
        clientSecret: String,
        authCode: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ExternalAuthManager.saveShikimoriCredentials(context, clientId, clientSecret)
                val tokenResp = ShikimoriApiClient.exchangeCodeForToken(clientId, clientSecret, authCode)
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

            runCatching {
                runCatching {
                    val profile = KitsuImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveKitsuProfileInfo(profile.name, profile.avatarUrl)
                }
                KitsuImportManager.fetchAllLists(context, token, userId)
            }.onSuccess { importedEntries ->
                repository.smartImport("kitsu", importedEntries)
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

            runCatching {
                runCatching {
                    val profile = ShikimoriImportManager.fetchUserProfile(token)
                    SettingsDataStore(context).saveShikimoriProfileInfo(profile.nickname, profile.avatarUrl)
                }
                ShikimoriImportManager.fetchAllLists(context, token, userId)
            }.onSuccess { importedEntries ->
                repository.smartImport("shikimori", importedEntries)
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

            runCatching {
                runCatching {
                    val profile = SimklImportManager.fetchUserProfile(token)
                    settingsDataStore.setSimklProfileInfo(
                        username = profile.name,
                        profileImageUri = profile.avatarUrl.orEmpty(),
                        bannerImageUri = ""
                    )
                }
                SimklImportManager.fetchAllLists(token)
            }.onSuccess { importedEntries ->
                repository.smartImport("simkl", importedEntries)

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
