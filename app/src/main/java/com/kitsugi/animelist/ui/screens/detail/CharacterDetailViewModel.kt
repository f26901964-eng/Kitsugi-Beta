package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.remote.JikanGateway
import com.kitsugi.animelist.data.remote.JikanResult
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiApiBase
import com.kitsugi.animelist.data.remote.KitsugiMediaMutationsClient
import com.kitsugi.animelist.data.remote.RateLimitException
import com.kitsugi.animelist.data.remote.ResourceNotFoundException
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class CharacterDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val apiClient = JikanApiClient()
    private val mutationsClient = KitsugiMediaMutationsClient()
    private val translationManager = TranslationManager(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "CharacterDetailVM"

    private val _state = MutableStateFlow<CharacterDetailState>(CharacterDetailState.Loading)
    val state: StateFlow<CharacterDetailState> = _state.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _translatedBio = MutableStateFlow<String?>(null)
    val translatedBio: StateFlow<String?> = _translatedBio.asStateFlow()

    private val _isFavourite = MutableStateFlow(false)
    val isFavourite: StateFlow<Boolean> = _isFavourite.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentFetchKey: String? = null
    private var lastCharacterId: Int = 0
    private var lastSource: String = ""
    private var lastCharacterName: String? = null
    private var lastIsRealMediaRole: Boolean = false

    /**
     * Arayüzden (karakter kartından) gelen görsel. Kaynak veride görsel yoksa detay
     * sayfası bunu son çare olarak kullanır — bu sayede hem hayali hem gerçek kişi
     * karakterlerinde sayfa resimsiz açılmaz.
     */
    private var lastHintImageUrl: String? = null

    fun translateBio() {
        val detail = (_state.value as? CharacterDetailState.Success)?.detail ?: return
        val bio = detail.biography ?: return
        viewModelScope.launch {
            val tr = withContext(Dispatchers.IO) {
                translationManager.translateToTurkish(bio)
            }
            if (tr.isNotBlank() && tr != bio) {
                DetailCache.putTranslation("bio_char", lastSource, detail.id, tr)
                _translatedBio.value = tr
            }
        }
    }

    fun loadCharacter(characterId: Int, source: String, name: String? = null, hintImageUrl: String? = null, isRealMediaRole: Boolean = false) {
        val newKey = if (source.equals("tmdb", ignoreCase = true) && !name.isNullOrBlank()) {
            "$source:${name.trim().lowercase()}"
        } else {
            "$source:$characterId"
        }
        lastHintImageUrl = hintImageUrl ?: lastHintImageUrl
        if (newKey == currentFetchKey) {
            Log.d(TAG, "loadCharacter: Cache hit for key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadCharacter: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        lastCharacterId = characterId
        lastSource = source
        lastCharacterName = name
        lastIsRealMediaRole = isRealMediaRole

        val cachedCharacterDetail = DetailCache.getCharacterDetail(source, characterId)
        val cachedBioTranslation = DetailCache.getTranslation("bio_char", source, characterId)

        _state.value = if (cachedCharacterDetail != null) CharacterDetailState.Success(cachedCharacterDetail) else CharacterDetailState.Loading
        _translatedBio.value = cachedBioTranslation

        // Kartta gösterilen görsel anında galeriye konur: detay isteği gecikse/yavaş olsa
        // bile sayfa resimsiz kalmaz.
        if (hintImageUrl != null && hintImageUrl.isNotBlank()) {
            _galleryItems.value = listOf(
                GalleryItem(
                    url = hintImageUrl,
                    source = friendlySourceOf(source),
                    category = GalleryCategory.CHARACTER,
                    description = name
                )
            )
        }

        viewModelScope.launch {
            fetchCharacterDetail(characterId, source, name, isRealMediaRole = isRealMediaRole)
        }
    }

    fun retry() {
        val characterId = lastCharacterId
        val source = lastSource
        val name = lastCharacterName
        if (characterId > 0 && source.isNotEmpty()) {
            _state.value = CharacterDetailState.Loading
            _translatedBio.value = null
            viewModelScope.launch {
                fetchCharacterDetail(characterId, source, name, force = true, isRealMediaRole = lastIsRealMediaRole)
            }
        }
    }

    fun forceRefresh() {
        val characterId = lastCharacterId
        val source = lastSource
        val name = lastCharacterName
        if (characterId > 0 && source.isNotEmpty()) {
            _isRefreshing.value = true
            viewModelScope.launch {
                try {
                    fetchCharacterDetail(characterId, source, name, force = true, isRealMediaRole = lastIsRealMediaRole)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    private suspend fun fetchCharacterDetail(characterId: Int, source: String, name: String? = null, force: Boolean = false, isRealMediaRole: Boolean = false) {
        if (force) {
            DetailCache.removeCharacterDetail(source, characterId)
        }
        val cached = DetailCache.getCharacterDetail(source, characterId)
        val detail = if (cached != null && !force) {
            cached
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    apiClient.fetchCharacterDetail(source, characterId, name, fallbackImageUrl = lastHintImageUrl, isRealMediaRole = isRealMediaRole)
                }
            }.onFailure { err ->
                val msg = when (err) {
                    is RateLimitException -> "Hız limitine takıldık. Lütfen birkaç saniye bekleyip tekrar deneyin."
                    is ResourceNotFoundException -> "Bu karakter bulunamadı."
                    is java.io.IOException -> "Ağ bağlantısı hatası. Lütfen bağlantınızı kontrol edin."
                    else -> "Karakter detayları yüklenemedi."
                }
                Log.e(TAG, "fetchCharacterDetail error: ${err.message}", err)
                if (_state.value !is CharacterDetailState.Success) {
                    _state.value = CharacterDetailState.Error(msg)
                }
            }.getOrNull()
        }

        if (detail != null) {
            DetailCache.putCharacterDetail(source, characterId, detail)
            _state.value = CharacterDetailState.Success(detail)
            _isFavourite.value = detail.isFavourite
            if (isBangumiSource(source)) refreshBangumiFavourite(detail.id)

            // Build gallery from imageUrl + Jikan /pictures
            buildCharacterGallery(characterId, source, detail.imageUrl)

            // Otomatik çeviri açıksa veya metin Rusça ise biyografiyi çevir (zaten Türkçeyse TranslationManager atlar)
            val bio = detail.biography
            val autoTranslate = runCatching { settingsDataStore.settingsFlow.first() }.getOrNull()?.autoTranslateEnabled ?: false
            val isRussian = bio?.any { it in '\u0400'..'\u04FF' } == true
            val cachedTranslation = DetailCache.getTranslation("bio_char", source, characterId)
            if (cachedTranslation != null && !force) {
                _translatedBio.value = cachedTranslation
            } else if (!bio.isNullOrBlank() && (autoTranslate || isRussian)) {
                val tr = withContext(Dispatchers.IO) {
                    translationManager.translateToTurkish(bio)
                }
                if (tr != bio) {
                    DetailCache.putTranslation("bio_char", source, characterId, tr)
                    _translatedBio.value = tr
                }
            }
        } else if (_state.value !is CharacterDetailState.Success) {
            _state.value = CharacterDetailState.Error("Karakter detayları yüklenemedi.")
        }
    }

    /** Kaynak anahtarı → kullanıcıya gösterilen ad. */
    private fun friendlySourceOf(source: String): String = when (source.lowercase().trim()) {
        "kitsu" -> "Kitsu"
        "anilist" -> "AniList"
        "shikimori" -> "Shikimori"
        "simkl" -> "Simkl"
        "tmdb" -> "TMDB"
        "bangumi", "bgm" -> "Bangumi"
        "mal", "jikan" -> "MyAnimeList"
        else -> "Karakter"
    }

    /**
     * Karakter görsellerini Jikan /pictures endpoint'inden alır ve GalleryItem listesi oluşturur.
     * Ana imageUrl'i POSTER olarak ekler, ek görseller de POSTER kategorisinde etiketlenir.
     */
    private suspend fun buildCharacterGallery(characterId: Int, source: String, mainImageUrl: String?) {
        // Jikan /pictures YALNIZCA kimliği MAL uzayında olan kaynaklarda anlamlıdır:
        // TMDB kişi kimliği, Shikimori kimliği (MAL eşlemesi ayrıca gerekir) veya AniList
        // offset'li kimliğiyle çağrılırsa 404 döner (boşuna istek + gecikme).
        val jikanId = when (source.lowercase().trim()) {
            "anilist" -> if (characterId in 1 until 100_000_000) characterId else null
            "jikan", "mal" -> characterId.takeIf { it > 0 }
            else -> null
        }

        val pictureUrls = if (jikanId != null && source.lowercase() != "anilist") {
            withContext(Dispatchers.IO) {
                fetchJikanPictures(jikanId, "characters")
            }
        } else emptyList()

        val friendlySource = friendlySourceOf(source)

        val items = buildList {
            if (!mainImageUrl.isNullOrBlank()) {
                add(GalleryItem(url = mainImageUrl, source = friendlySource, category = GalleryCategory.CHARACTER, description = lastCharacterName))
            }
            for (url in pictureUrls) {
                if (url != mainImageUrl && url.isNotBlank()) {
                    add(GalleryItem(url = url, source = "MyAnimeList", category = GalleryCategory.CHARACTER, description = lastCharacterName))
                }
            }
        }.distinctBy { it.url }

        _galleryItems.value = items
    }

    private fun fetchJikanPictures(id: Int, endpoint: String): List<String> {
        val url = URL("https://api.jikan.moe/v4/$endpoint/$id/pictures")
        // Bu yükleyici withContext(IO) içinden çağrılır; kota kapısı bloklayabilir.
        // Kota kapısı + host zinciri (miribyou → Tenrai) + önbellek JikanGateway'de.
        return runCatching {
            val text = (JikanGateway.fetchBlocking(url.toString(), JikanGateway.Priority.UI) as? JikanResult.Ok)?.body
                ?: return@runCatching emptyList()
            run {
                val dataArr = JSONObject(text).optJSONArray("data") ?: return@runCatching emptyList()
                val urls = mutableListOf<String>()
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.getJSONObject(i)
                    val webp = obj.optJSONObject("webp")
                    val jpg = obj.optJSONObject("jpg")
                    val picUrl = webp?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                        ?: webp?.optString("image_url")?.takeIf { it.isNotBlank() }
                        ?: jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                        ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() }
                    if (!picUrl.isNullOrBlank()) urls.add(picUrl)
                }
                urls
            }
        }.getOrElse { emptyList() }
    }

    /**
     * AniList karakter favori toggle — giriş yapılmış ve aniListId biliniyorsa çalışır.
     * Anında UI güncellemesi yapar, arka planda mutasyon çalışır.
     */
    fun toggleFavourite() {
        if (isBangumiSource(lastSource)) {
            toggleBangumiFavourite()
            return
        }
        ExternalAuthManager.getAniListToken(context) ?: return
        val currentState = _state.value as? CharacterDetailState.Success ?: return
        val detail = currentState.detail
        val targetId = detail.aniListId ?: if (lastSource.lowercase() == "anilist") detail.id else null
        if (targetId == null || targetId <= 0) return

        val newFav = !_isFavourite.value
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = CharacterDetailState.Success(updated)
        DetailCache.putCharacterDetail(lastSource, lastCharacterId, updated)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = mutationsClient.toggleFavourite("character", targetId)
            if (!ok) {
                // rollback
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = CharacterDetailState.Success(rolled)
                DetailCache.putCharacterDetail(lastSource, lastCharacterId, rolled)
            }
        }
    }

    private fun isBangumiSource(source: String): Boolean {
        val key = source.trim().lowercase()
        return key == "bangumi" || key == "bgm"
    }

    /** Bangumi karakterinde favori durumunu token sahibinin koleksiyonundan okur (bağlıysa). */
    private fun refreshBangumiFavourite(characterId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!BangumiAuthStore.isConnected(context)) return@launch
            val token = BangumiAuthStore.getValidToken(context) ?: return@launch
            val username = BangumiAuthStore.getUsername(context)?.takeIf { it.isNotBlank() } ?: "-"
            val favourite = runCatching {
                BangumiApiClient.isCharacterFavourite(token, username, characterId)
            }.getOrNull() ?: return@launch
            _isFavourite.value = favourite
            val current = (_state.value as? CharacterDetailState.Success)?.detail ?: return@launch
            if (current.id != characterId) return@launch
            val updated = current.copy(isFavourite = favourite)
            _state.value = CharacterDetailState.Success(updated)
            DetailCache.putCharacterDetail(lastSource, lastCharacterId, updated)
        }
    }

    /**
     * Bangumi karakter favorisi: `POST` / `DELETE /v0/characters/{id}/collect`.
     * İyimser güncelleme yapılır; istek başarısız olursa önceki duruma dönülür.
     */
    private fun toggleBangumiFavourite() {
        if (!BangumiAuthStore.isConnected(context)) return
        val currentState = _state.value as? CharacterDetailState.Success ?: return
        val detail = currentState.detail
        val newFav = !_isFavourite.value
        _isFavourite.value = newFav
        val updated = detail.copy(isFavourite = newFav)
        _state.value = CharacterDetailState.Success(updated)
        DetailCache.putCharacterDetail(lastSource, lastCharacterId, updated)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val token = BangumiAuthStore.getValidToken(context) ?: return@runCatching false
                BangumiApiClient.setCharacterFavourite(token, detail.id, newFav)
                true
            }.getOrDefault(false)
            if (!ok) {
                _isFavourite.value = !newFav
                val rolled = detail.copy(isFavourite = !newFav)
                _state.value = CharacterDetailState.Success(rolled)
                DetailCache.putCharacterDetail(lastSource, lastCharacterId, rolled)
            }
        }
    }
}
