package com.kitsugi.animelist.ui.screens.anime

import androidx.compose.runtime.getValue
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class AnimeViewModel : ViewModel() {

    private val apiClient = JikanApiClient()

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var topAnime by mutableStateOf<List<JikanSearchResult>>(emptyList())
        private set

    var airingAnime by mutableStateOf<List<JikanSearchResult>>(emptyList())
        private set

    var upcomingAnime by mutableStateOf<List<JikanSearchResult>>(emptyList())
        private set

    val isDataLoaded: Boolean
        get() = topAnime.isNotEmpty() || airingAnime.isNotEmpty()

    init {
        loadData()
    }

    /** Yükleme hatasının platform hata kodu. */
    var platformErrorCode by mutableStateOf<PlatformErrorCode?>(null)
        private set

    fun loadData(forceRefresh: Boolean = false) {
        if (isLoading) return
        if (isDataLoaded && !forceRefresh) return

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            platformErrorCode = null
            val loadStartedAt = System.currentTimeMillis()

            runCatching {
                val topDeferred = async { apiClient.topAnime() }
                val airingDeferred = async { apiClient.airingAnime() }
                val upcomingDeferred = async { apiClient.upcomingAnime() }

                Triple(
                    topDeferred.await(),
                    airingDeferred.await(),
                    upcomingDeferred.await()
                )
            }.onSuccess { result ->
                topAnime = result.first
                airingAnime = result.second
                upcomingAnime = result.third
            }.onFailure { error ->
                errorMessage = error.message ?: KitsugiStrings.get(R.string.anime_listeleri_alinamadi)
                platformErrorCode = PlatformFailureRegistry.resolveSince(loadStartedAt, error)
            }

            isLoading = false
        }
    }
}
