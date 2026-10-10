package com.kitsugi.animelist.ui.screens.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiMediaMutationsClient
import com.kitsugi.animelist.data.remote.StudioSourceSupport
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val apiClient = JikanApiClient()
    private val mutationsClient = KitsugiMediaMutationsClient()
    private val translationManager = TranslationManager(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "StudioDetailVM"

    private val _state = MutableStateFlow<StudioDetailState>(StudioDetailState.Loading)
    val state: StateFlow<StudioDetailState> = _state.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _isFavourite = MutableStateFlow(false)
    val isFavourite: StateFlow<Boolean> = _isFavourite.asStateFlow()

    /**
     * "Hakkında" metninin gösterilecek hali — detay sayfasındaki Açıklama kartıyla
     * aynı sözleşme: önce ham metin, otomatik çeviri açıksa (veya metin Rusça ise)
     * Türkçe çevirisiyle değiştirilir.
     */
    private val _translatedAbout = MutableStateFlow<String?>(null)
    val translatedAbout: StateFlow<String?> = _translatedAbout.asStateFlow()

    private var currentFetchKey: String? = null
    private var lastStudioId: Int = 0
    private var lastSource: String = ""
    private var lastStudioName: String? = null
    private var lastStudioImageUrl: String? = null

    /**
     * @param imageUrl detay sayfasındaki çipten taşınan kurum logosu; kaynak API kendi logosunu
     * vermese de galeri/hero boş kalmasın diye yedek olarak kullanılır.
     */
    fun loadStudio(studioId: Int, source: String, name: String? = null, imageUrl: String? = null) {
        val canonicalSource = StudioSourceSupport.canonicalSource(source) ?: source.lowercase()
        val newKey = "$canonicalSource:$studioId:${StudioSourceSupport.normalizeName(name)}"
        if (newKey == currentFetchKey) {
            Log.d(TAG, "loadStudio: Already loading/loaded key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadStudio: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        lastStudioId = studioId
        lastSource = source
        lastStudioName = name?.takeIf { it.isNotBlank() }
        lastStudioImageUrl = imageUrl?.takeIf { it.isNotBlank() }

        val cachedStudioDetail = DetailCache.getStudioDetail(source, studioId)
            ?.takeIf { matchesExpectedStudio(source, lastStudioName, it.name) }
        _state.value = if (cachedStudioDetail != null) StudioDetailState.Success(cachedStudioDetail) else StudioDetailState.Loading
        if (cachedStudioDetail != null) _isFavourite.value = cachedStudioDetail.isFavourite

        viewModelScope.launch {
            fetchStudioDetail(studioId, source, expectedFetchKey = newKey, name = lastStudioName)
        }
    }

    fun retry() {
        val studioId = lastStudioId
        val source = lastSource
        val name = lastStudioName
        val expectedFetchKey = currentFetchKey ?: return
        if (studioId > 0 && source.isNotEmpty()) {
            _state.value = StudioDetailState.Loading
            viewModelScope.launch {
                fetchStudioDetail(studioId, source, expectedFetchKey = expectedFetchKey, name = name, force = true)
            }
        }
    }

    private suspend fun fetchStudioDetail(
        studioId: Int,
        source: String,
        expectedFetchKey: String,
        name: String? = null,
        force: Boolean = false
    ) {
        val cached = DetailCache.getStudioDetail(source, studioId)
            ?.takeIf { matchesExpectedStudio(source, name, it.name) }
        val detail = if (cached != null && !force) {
            cached
        } else {
            val fetched = withContext(Dispatchers.IO) {
                apiClient.fetchStudioDetail(source, studioId, name)
            }?.takeIf { matchesExpectedStudio(source, name, it.name) }

            if (currentFetchKey != expectedFetchKey) return
            if (fetched != null) {
                DetailCache.putStudioDetail(source, studioId, fetched)
            }
            fetched
        }

        if (currentFetchKey != expectedFetchKey) return
        if (detail != null) {
            _state.value = StudioDetailState.Success(detail)
            _isFavourite.value = detail.isFavourite

            // "Hakkında" — önce ham metin; otomatik çeviri açıksa veya metin Rusça ise
            // detay sayfasındaki Açıklama kartıyla aynı sözleşme izlenir ve Türkçeye çevrilir.
            val rawAbout = detail.about
            _translatedAbout.value = rawAbout
            if (!rawAbout.isNullOrBlank()) {
                val autoTranslate = runCatching {
                    settingsDataStore.settingsFlow.first().autoTranslateEnabled
                }.getOrDefault(false)
                val isRussian = rawAbout.any { it in '\u0400'..'\u04FF' }
                if (autoTranslate || isRussian) {
                    val cachedTr = DetailCache.getTranslation("studio_about", source, studioId)
                    if (cachedTr != null) {
                        _translatedAbout.value = cachedTr
                    } else {
                        val tr = withContext(Dispatchers.IO) {
                            translationManager.translateToTurkish(rawAbout)
                        }
                        if (!tr.isNullOrBlank() && tr != rawAbout) {
                            DetailCache.putTranslation("studio_about", source, studioId, tr)
                            _translatedAbout.value = tr
                        }
                    }
                }
            }

            // Build gallery from studio imageUrl (logo)
            val imageUrl = detail.imageUrl ?: lastStudioImageUrl
            if (!imageUrl.isNullOrBlank()) {
                val category = if (imageUrl.contains("logo", ignoreCase = true) ||
                    imageUrl.contains("image.tmdb.org", ignoreCase = true)) {
                    GalleryCategory.LOGO
                } else {
                    GalleryCategory.POSTER
                }
                val src = when {
                    imageUrl.contains("image.tmdb.org") || imageUrl.contains("tmdb.org") -> "TMDB"
                    imageUrl.contains("shikimori.") -> "Shikimori"
                    imageUrl.contains("anilist.co") -> "AniList"
                    imageUrl.contains("bgm.tv") || imageUrl.contains("bangumi.tv") -> "Bangumi"
                    else -> "Jikan"
                }
                _galleryItems.value = listOf(GalleryItem(url = imageUrl, source = src, category = category, description = lastStudioName))
            } else {
                _galleryItems.value = emptyList()
            }
        } else {
            _state.value = StudioDetailState.Error("Stüdyo detayları yüklenemedi.")
        }
    }

    /**
     * "Hakkında" metnini uygulama içi çeviri motoruyla Türkçeye çevirir —
     * detay sayfasındaki translateSynopsis() ile aynı davranış.
     */
    fun translateAbout() {
        val currentState = _state.value as? StudioDetailState.Success ?: return
        val raw = currentState.detail.about ?: return
        viewModelScope.launch {
            val tr = withContext(Dispatchers.IO) {
                translationManager.translateToTurkish(raw)
            }
            if (!tr.isNullOrBlank() && tr != raw) {
                DetailCache.putTranslation("studio_about", lastSource, lastStudioId, tr)
                _translatedAbout.value = tr
            }
        }
    }

    /**
     * AniList stüdyo favori toggle — giriş yapılmış ve aniListId biliniyorsa çalışır.
     */
    fun toggleFavourite() {
        ExternalAuthManager.getAniListToken(context) ?: return
        val currentState = _state.value as? StudioDetailState.Success ?: return
        val detail = currentState.detail
        val targetId = detail.aniListId ?: if (lastSource.lowercase() == "anilist") detail.id else null
        if (targetId == null || targetId <= 0) return

        val newFav = !_isFavourite.value
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = StudioDetailState.Success(updated)
        DetailCache.putStudioDetail(lastSource, lastStudioId, updated)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = mutationsClient.toggleFavourite("studio", targetId)
            if (!ok) {
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = StudioDetailState.Success(rolled)
                DetailCache.putStudioDetail(lastSource, lastStudioId, rolled)
            }
        }
    }

    private fun matchesExpectedStudio(source: String, expectedName: String?, actualName: String): Boolean =
        StudioSourceSupport.canonicalSource(source) != "jikan" ||
            StudioSourceSupport.namesMatch(expectedName, actualName)
}
