package com.kitsugi.animelist.ui.screens.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.KitsugiCharacter
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiShikimoriClient
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.KitsugiStaff
import com.kitsugi.animelist.data.remote.KitsugiStats
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.kitsugi.animelist.data.local.MangaMappingEntity
import com.kitsugi.animelist.data.manga.MangaSourceRepository
import com.kitsugi.animelist.data.remote.MdbListClient
import com.kitsugi.animelist.data.remote.MdbListRatings
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

class MediaEntryDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val apiClient = JikanApiClient()
    private val translationManager = TranslationManager(context)
    private val mangaRepository = MangaSourceRepository(context)
    private val settingsDataStore = SettingsDataStore(context)
    private val TAG = "MediaEntryDetailVM"

    // --- StateFlows ---

    private val _mangaMapping = MutableStateFlow<MangaMappingEntity?>(null)
    val mangaMapping: StateFlow<MangaMappingEntity?> = _mangaMapping.asStateFlow()

    private val _detailState = MutableStateFlow<KitsugiMediaDetail?>(null)
    val detailState: StateFlow<KitsugiMediaDetail?> = _detailState.asStateFlow()

    private val _detailLoading = MutableStateFlow(true)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _synopsisState = MutableStateFlow<SynopsisState>(SynopsisState.Loading)
    val synopsisState: StateFlow<SynopsisState> = _synopsisState.asStateFlow()

    private val _translatedSynopsis = MutableStateFlow<String?>(null)
    val translatedSynopsis: StateFlow<String?> = _translatedSynopsis.asStateFlow()

    private val _originalSynopsis = MutableStateFlow<String?>(null)
    val originalSynopsis: StateFlow<String?> = _originalSynopsis.asStateFlow()

    private val _logoUrl = MutableStateFlow<String?>(null)
    val logoUrl: StateFlow<String?> = _logoUrl.asStateFlow()

    private val _episodeRatings = MutableStateFlow<Map<Pair<Int, Int>, Double>>(emptyMap())
    val episodeRatings: StateFlow<Map<Pair<Int, Int>, Double>> = _episodeRatings.asStateFlow()

    private val _resolvedTmdbId = MutableStateFlow<Int?>(null)
    val resolvedTmdbId: StateFlow<Int?> = _resolvedTmdbId.asStateFlow()

    private val _charactersState = MutableStateFlow<DetailTabState<List<KitsugiCharacter>>>(DetailTabState.Loading)
    val charactersState: StateFlow<DetailTabState<List<KitsugiCharacter>>> = _charactersState.asStateFlow()

    private val _staffState = MutableStateFlow<DetailTabState<List<KitsugiStaff>>>(DetailTabState.Loading)
    val staffState: StateFlow<DetailTabState<List<KitsugiStaff>>> = _staffState.asStateFlow()

    private val _relationsState = MutableStateFlow<DetailTabState<List<KitsugiRelation>>>(DetailTabState.Loading)
    val relationsState: StateFlow<DetailTabState<List<KitsugiRelation>>> = _relationsState.asStateFlow()

    private val _recommendationsState = MutableStateFlow<DetailTabState<List<KitsugiRelation>>>(DetailTabState.Loading)
    val recommendationsState: StateFlow<DetailTabState<List<KitsugiRelation>>> = _recommendationsState.asStateFlow()

    private val _statsState = MutableStateFlow<DetailTabState<KitsugiStats?>>(DetailTabState.Loading)
    val statsState: StateFlow<DetailTabState<KitsugiStats?>> = _statsState.asStateFlow()

    private val _reviewsState = MutableStateFlow<DetailTabState<List<KitsugiReview>>>(DetailTabState.Loading)
    val reviewsState: StateFlow<DetailTabState<List<KitsugiReview>>> = _reviewsState.asStateFlow()

    private val _episodesState = MutableStateFlow<DetailTabState<List<KitsugiStreamingEpisode>>>(DetailTabState.Loading)
    val episodesState: StateFlow<DetailTabState<List<KitsugiStreamingEpisode>>> = _episodesState.asStateFlow()

    private val _targetSeason = MutableStateFlow<Int>(1)
    val targetSeason: StateFlow<Int> = _targetSeason.asStateFlow()

    private val _mdbListRatings = MutableStateFlow<MdbListRatings?>(null)
    val mdbListRatings: StateFlow<MdbListRatings?> = _mdbListRatings.asStateFlow()

    private val _mdbListLoading = MutableStateFlow(false)
    val mdbListLoading: StateFlow<Boolean> = _mdbListLoading.asStateFlow()

    /** Fanart.tv + TMDB + Jikan kaynaklarından gelen zengin galeri öğeleri */
    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private val _galleryLoading = MutableStateFlow(true)
    val galleryLoading: StateFlow<Boolean> = _galleryLoading.asStateFlow()

    /** Her yeni entry navigasyonunda artar — UI bu trigger’ı izleyerek tab’ı 0’a sıfırlar. */
    private val _pageResetTrigger = MutableStateFlow(0)
    val pageResetTrigger: StateFlow<Int> = _pageResetTrigger.asStateFlow()

    // --- Cache / Lock Key ---
    private var currentFetchKey: String? = null
    private var mangaMappingJob: Job? = null

    /**
     * Initializes state and starts background jobs to fetch all entry-specific details.
     * Prevents redundant scanning if [entry] hasn't changed.
     */
    fun loadEntry(entry: MediaEntry, showAnimeLogos: Boolean, forceRefresh: Boolean = false) {
        mangaMappingJob?.cancel()
        mangaMappingJob = viewModelScope.launch {
            mangaRepository.observeMangaMapping(entry.id).collect {
                _mangaMapping.value = it
            }
        }

        val stableId = cacheIdentityOf(entry)
        if (forceRefresh) {
            currentFetchKey = null
            DetailCache.removeMediaDetail(entry.source, stableId)
            DetailCache.removeMediaCharacters(entry.source, stableId)
            DetailCache.removeMediaStaff(entry.source, stableId)
            DetailCache.removeMediaRelations(entry.source, stableId)
            DetailCache.removeMediaRecommendations(entry.source, stableId)
            DetailCache.removeMediaReviews(entry.source, stableId)
            DetailCache.removeMediaEpisodes(entry.source, stableId)
            DetailCache.clearFanartCache() // Fanart galeri önbelleğini temizle
        }

        val newKey = "${entry.id}:${entry.source}:${entry.malId}"
        if (newKey == currentFetchKey) {
            Log.d(TAG, "loadEntry: Cache hit for key=$newKey — skipping")
            return
        }

        Log.d(TAG, "loadEntry: New key=$newKey (was $currentFetchKey)")
        currentFetchKey = newKey
        _pageResetTrigger.value += 1 // Signal UI to scroll back to first tab

        // Reset states
        val cachedDetail = DetailCache.getMediaDetail(entry.source, stableId)
        val cachedSynopsisTranslation = DetailCache.getTranslation("synopsis", entry.source, stableId)

        _detailState.value = cachedDetail
        _detailLoading.value = cachedDetail == null

        _synopsisState.value = when {
            cachedSynopsisTranslation != null -> SynopsisState.Success(cachedSynopsisTranslation)
            !entry.synopsis.isNullOrBlank() -> SynopsisState.Success(entry.synopsis)
            else -> SynopsisState.Loading
        }
        _translatedSynopsis.value = cachedSynopsisTranslation
        _originalSynopsis.value = entry.synopsis

        _logoUrl.value = null
        _episodeRatings.value = emptyMap()
        _resolvedTmdbId.value = null
        _galleryItems.value = emptyList()
        _galleryLoading.value = true

        // Reset tab states to either cached values or Loading
        val malId = stableId
        val cachedCharacters = DetailCache.getMediaCharacters(entry.source, malId)
        if (cachedCharacters != null && cachedCharacters.isEmpty()) {
            DetailCache.removeMediaCharacters(entry.source, malId)
            _charactersState.value = DetailTabState.Loading
        } else {
            _charactersState.value = if (cachedCharacters != null) DetailTabState.Success(cachedCharacters) else DetailTabState.Loading
        }

        val cachedStaff = DetailCache.getMediaStaff(entry.source, malId)
        if (cachedStaff != null && cachedStaff.isEmpty()) {
            DetailCache.removeMediaStaff(entry.source, malId)
            _staffState.value = DetailTabState.Loading
        } else {
            _staffState.value = if (cachedStaff != null) DetailTabState.Success(cachedStaff) else DetailTabState.Loading
        }

        val cachedRelations = DetailCache.getMediaRelations(entry.source, malId)
        if (cachedRelations != null && cachedRelations.isEmpty()) {
            DetailCache.removeMediaRelations(entry.source, malId)
            _relationsState.value = DetailTabState.Loading
        } else {
            _relationsState.value = if (cachedRelations != null) DetailTabState.Success(cachedRelations) else DetailTabState.Loading
        }

        val cachedRecommendations = DetailCache.getMediaRecommendations(entry.source, malId)
        if (cachedRecommendations != null && cachedRecommendations.isEmpty()) {
            DetailCache.removeMediaRecommendations(entry.source, malId)
            _recommendationsState.value = DetailTabState.Loading
        } else {
            _recommendationsState.value = if (cachedRecommendations != null) DetailTabState.Success(cachedRecommendations) else DetailTabState.Loading
        }

        val cachedStats = DetailCache.getMediaStats(entry.source, malId)
        _statsState.value = if (DetailCache.hasMediaStats(entry.source, malId)) DetailTabState.Success(cachedStats) else DetailTabState.Loading

        val cachedReviews = DetailCache.getMediaReviews(entry.source, malId)
        _reviewsState.value = if (cachedReviews != null) DetailTabState.Success(cachedReviews) else DetailTabState.Loading

        val cachedEpisodes = DetailCache.getMediaEpisodes(entry.source, malId)
        if (cachedEpisodes != null && cachedEpisodes.isEmpty()) {
            DetailCache.removeMediaEpisodes(entry.source, malId)
            _episodesState.value = DetailTabState.Loading
        } else {
            _episodesState.value = if (cachedEpisodes != null) DetailTabState.Success(cachedEpisodes) else DetailTabState.Loading
        }

        // Detail fetch — sayfa render için kritik; öncelikli coroutine
        viewModelScope.launch {
            try {
                fetchDetail(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching detail: ${e.message}", e)
                _detailLoading.value = false
            }
        }

        // Galeri + Fanart.tv — detail ile paralel; tamamlandığında sayfa
        // zaten açık olduğundan sadece galeri bölümü güncellenir.
        viewModelScope.launch {
            try {
                _galleryLoading.value = true
                fetchFanartGallery(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Fanart gallery (post-detail): ${e.message}", e)
            } finally {
                _galleryLoading.value = false
            }
        }

        // Synopsis — bağımsız
        viewModelScope.launch {
            try {
                fetchSynopsis(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching synopsis: ${e.message}", e)
            }
        }

        // MDBList puanları — bağımsız, gecikme kabul edilebilir
        viewModelScope.launch {
            try {
                fetchMdbListRatingsForEntry(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching mdbList ratings: ${e.message}", e)
            }
        }

        // Logo — bağımsız, en düşük öncelikli
        viewModelScope.launch {
            try {
                fetchLogo(entry, showAnimeLogos)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching logo: ${e.message}", e)
            }
        }

    }

    /**
     * Kitsu kayıtları için bellek-içi önbellek (DetailCache) anahtarının kanonik kimliği.
     * Eski kayıtlarda `malId` gerçek MAL ID taşıyabildiğinden, anahtarı çözülen stableId
     * üzerinden kurmak yanlış yapımın önbelleğe yazılmasını engeller.
     */
    private var canonicalKitsuIdForEntry: Pair<Int, Int>? = null

    private fun cacheIdentityOf(entry: MediaEntry): Int {
        val raw = entry.malId ?: entry.id
        if (!entry.source.equals("kitsu", ignoreCase = true)) return raw
        if (com.kitsugi.animelist.data.remote.KitsuIdNamespace.isStableId(raw)) return raw
        canonicalKitsuIdForEntry?.let { (entryId, canonical) -> if (entryId == entry.id) return canonical }
        // Çözülememiş Kitsu kimliği: önbelleği kayıt satırının kendi anahtarına bağla —
        // böylece hiçbir zaman başka bir yapımın verisini okumayız.
        return -entry.id
    }

    private suspend fun fetchDetail(entry: MediaEntry) {
        val effectiveExternalId = when (entry.source.lowercase()) {
            "simkl" -> entry.simklId?.takeIf { it > 0 } ?: (if (entry.id > 0) entry.id else (entry.malId ?: 0))
            else -> entry.malId ?: entry.id
            // NOT (Kitsu): kimlik alanı her zaman 300M aralığındaki stableId olmalıdır. Eski
            // kayıtlarda bu alan gerçek MAL ID taşıyabiliyor; KitsugiDetailClient kimliği
            // kanonikleştirir (eşleme önbelleği → Kitsu mappings → sıkı başlık araması).
        }
        val stableId = cacheIdentityOf(entry)
        if (entry.source.equals("kitsu", ignoreCase = true)) {
            com.kitsugi.animelist.data.remote.KitsuIdNamespace.stableIdOrNull(effectiveExternalId)?.let { canonicalKitsuIdForEntry = entry.id to it }
        }
        val cached = DetailCache.getMediaDetail(entry.source, stableId)
        val detail = if (cached != null) {
            cached
        } else {
            _detailLoading.value = true
            val fetched = try {
                withContext(Dispatchers.IO) {
                    apiClient.fetchDetail(
                        source = entry.source,
                        externalId = effectiveExternalId,
                        mediaType = entry.type,
                        // TMDB zenginleştirmesi için entry'deki ID'leri ilet
                        tmdbId = entry.tmdbId,
                        // realMalId → Jikan/ARM için gerçek MAL ID'si; kaynak bazında hesaplanır
                        realMalId = when (entry.source.lowercase()) {
                            "simkl" -> {
                                // Simkl API ids.mal alanı varsa entry.malId gerçek MAL ID'dir (simklId'den farklı)
                                val m = entry.malId; val s = entry.simklId
                                if (m != null && m > 0 && m != s && m < 100_000_000) m else null
                            }
                            "anilist" -> {
                                // 100M+ offset'li stableId gerçek MAL ID değil; < 100M ise MAL ID'dir
                                val m = entry.malId
                                if (m != null && m > 0 && m < 100_000_000) m else null
                            }
                            // Kitsu: 300M aralığı Kitsu stableId'sidir; yalnızca aralık altındaki
                            // (eski sürümlerin yazdığı) değer gerçek MAL ID sayılır.
                            "kitsu" -> com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(entry.malId)
                            else -> entry.malId
                        },
                        title = entry.title
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during apiClient.fetchDetail: ${e.message}", e)
                null
            }
            if (fetched != null) {
                DetailCache.putMediaDetail(entry.source, stableId, fetched)
            }
            fetched
        }

        _detailState.value = detail
        _detailLoading.value = false

        if (detail != null) {
            val trSynopsis = detail.synopsis
            if (!trSynopsis.isNullOrBlank()) {
                _originalSynopsis.value = trSynopsis
                if (_synopsisState.value !is SynopsisState.Success || _translatedSynopsis.value.isNullOrBlank()) {
                    _synopsisState.value = SynopsisState.Success(trSynopsis)
                    _translatedSynopsis.value = trSynopsis
                }
            }

            val determinedSeason = KitsugiEpisodeRatingsRepository.determineTargetSeason(
                tmdbSeason = detail.tmdbSeason,
                title = entry.title,
                titleEnglish = detail.titleEnglish,
                synonyms = detail.synonyms.orEmpty()
            )
            _targetSeason.value = determinedSeason

            // Detaydan gelen TMDB ID veya resimler varsa ve galeri henüz kısıtlıysa galeriyi zenginleştir
            if (_galleryItems.value.size <= 2) {
                viewModelScope.launch {
                    try {
                        fetchFanartGallery(entry)
                    } catch (e: Exception) {
                        Log.e(TAG, "Post-detail gallery refresh failed: ${e.message}")
                    }
                }
            }

            // Fetch episode ratings
            fetchEpisodeRatings(entry, detail)

            // Logo güncelle — Simkl gibi kaynaklarda detaydan realMalId veya tmdbId geldiğinde logo çekilebilir
            if (_logoUrl.value == null) {
                val showLogos = runCatching { settingsDataStore.settingsFlow.first().showAnimeLogos }.getOrDefault(true)
                if (showLogos) {
                    viewModelScope.launch {
                        try {
                            fetchLogo(entry, showLogos)
                        } catch (e: Exception) {
                            Log.e(TAG, "Post-detail logo fetch failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchEpisodeRatings(entry: MediaEntry, detail: KitsugiMediaDetail) {
        val tmdbId = detail.tmdbId
        var foundRatings = emptyMap<Pair<Int, Int>, Double>()
        var resolvedId: Int? = null

        withContext(Dispatchers.IO) {
            when {
                tmdbId != null && tmdbId > 0 -> {
                    resolvedId = tmdbId
                    foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatings(tmdbId)
                }
                entry.source.equals("anilist", ignoreCase = true) -> {
                    val stableId = entry.malId ?: 0
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        if (aniListId > 0) {
                            foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByAniListId(aniListId)
                            resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForAniList(aniListId)
                        }
                    } else if (stableId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(stableId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(stableId)
                    }

                    if (foundRatings.isEmpty()) {
                        val malId = detail.realMalId
                        if (malId != null && malId > 0) {
                            foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                            resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                        }
                    }
                }
                entry.source.equals("kitsu", ignoreCase = true) -> {
                    // Yalnızca 300M aralığındaki kanonik Kitsu stableId'si çözülür
                    val kitsuId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(entry.malId)
                    if (kitsuId != null && kitsuId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByKitsuId(kitsuId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForKitsu(kitsuId)
                    }
                }
                else -> {
                    val malId = entry.malId
                    if (malId != null && malId > 0) {
                        foundRatings = KitsugiEpisodeRatingsRepository.getEpisodeRatingsByMalId(malId)
                        resolvedId = KitsugiEpisodeRatingsRepository.getResolvedTmdbIdForMal(malId)
                    }
                }
            }
        }

        _episodeRatings.value = foundRatings
        _resolvedTmdbId.value = resolvedId
    }

    private suspend fun fetchLogo(entry: MediaEntry, showAnimeLogos: Boolean) {
        if (!showAnimeLogos) {
            _logoUrl.value = null
            return
        }
        val stableId = entry.malId ?: 0
        val logo = withContext(Dispatchers.IO) {
            when {
                entry.source.equals("tmdb", ignoreCase = true) -> {
                    val tmdbId = entry.tmdbId ?: if (stableId > 0) stableId else null
                    if (tmdbId != null && tmdbId > 0) KitsugiEpisodeRatingsRepository.getLogoUrl(tmdbId) else null
                }
                entry.source.equals("anilist", ignoreCase = true) -> {
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        val realMal = _detailState.value?.realMalId
                        KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(aniListId, fallbackMalId = realMal)
                    } else {
                        KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId)
                    }
                }
                entry.source.equals("kitsu", ignoreCase = true) -> {
                    val kitsuId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(stableId)
                    if (kitsuId != null && kitsuId > 0) {
                        KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(kitsuId)
                    } else {
                        // Kimlik Kitsu aralığında değil (eski kayıt) → MAL ID olarak dene
                        val malId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(stableId)
                        if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(malId) else null
                    }
                }
                entry.source.equals("simkl", ignoreCase = true) -> {
                    val detail = _detailState.value
                    val realMalId = detail?.realMalId
                    val tmdbId = entry.tmdbId ?: detail?.tmdbId
                    when {
                        realMalId != null && realMalId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(realMalId)
                        tmdbId != null && tmdbId > 0 -> KitsugiEpisodeRatingsRepository.getLogoUrl(tmdbId)
                        else -> null
                    }
                }
                entry.source.equals("jikan", ignoreCase = true) ||
                entry.source.equals("mal", ignoreCase = true) ||
                entry.source.equals("shikimori", ignoreCase = true) -> {
                    if (stableId > 0) KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId) else null
                }
                stableId > 0 && !entry.source.equals("simkl", ignoreCase = true) &&
                    !entry.source.equals("kitsu", ignoreCase = true) -> {
                    KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(stableId)
                }
                else -> null
            }
        }
        _logoUrl.value = logo
    }

    /**
     * Fanart.tv galerisi + mevcut TMDB/Jikan resimlerini birleştirir.
     * Sonuçlar [_galleryItems] akışına yazılır; UI reaktif güncelleme alır.
     */
    private suspend fun fetchFanartGallery(entry: MediaEntry) {
        val tmdbId = withContext(Dispatchers.IO) {
            val detailTmdb = _detailState.value?.tmdbId
            when {
                detailTmdb != null && detailTmdb > 0 -> detailTmdb
                entry.tmdbId != null && entry.tmdbId > 0 -> entry.tmdbId
                entry.source.equals("tmdb", ignoreCase = true) -> entry.malId?.takeIf { it > 0 }
                entry.source.equals("anilist", ignoreCase = true) -> {
                    val stableId = entry.malId ?: 0
                    if (stableId >= 100_000_000) {
                        val aniListId = stableId - 100_000_000
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromAniList(aniListId)
                    } else {
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(stableId)
                    }
                }
                entry.source.equals("kitsu", ignoreCase = true) -> {
                    val kitsuId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(entry.malId)
                    if (kitsuId != null && kitsuId > 0) {
                        KitsugiEpisodeRatingsRepository.resolveTmdbIdFromKitsu(kitsuId)
                    } else {
                        val malId = com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(entry.malId)
                        if (malId != null && malId > 0) KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(malId) else null
                    }
                }
                else -> entry.malId?.let { KitsugiEpisodeRatingsRepository.resolveTmdbIdFromMal(it) }
            }
        }

        val isMovie = entry.type == MediaType.Movie

        val fallbackMalId: Int? = when {
            entry.source.equals("anilist", ignoreCase = true) -> {
                val m = entry.malId ?: 0
                if (m > 0 && m < 100_000_000) m else _detailState.value?.realMalId
            }
            // Kitsu kayıtlarında malId ya Kitsu stableId'dir (MAL ID değildir) ya da eski
            // kayıtlarda gerçek MAL ID. İkisini ayırıp yalnızca güvenli olanı kullanıyoruz.
            entry.source.equals("kitsu", ignoreCase = true) ->
                if (com.kitsugi.animelist.data.remote.KitsuIdNamespace.isStableId(entry.malId)) _detailState.value?.realMalId else com.kitsugi.animelist.data.remote.KitsuIdNamespace.realMalIdOf(entry.malId)
            !entry.source.equals("tmdb", ignoreCase = true) -> entry.malId
            else -> null
        }
        val fallbackAniListId: Int? = if (entry.source.equals("anilist", ignoreCase = true)) {
            val m = entry.malId ?: 0
            if (m >= 100_000_000) m - 100_000_000 else null
        } else null
        val fallbackKitsuId: Int? = if (entry.source.equals("kitsu", ignoreCase = true)) {
            com.kitsugi.animelist.data.remote.KitsuIdNamespace.rawIdFromStable(entry.malId)
        } else null

        val (fanartItems, tmdbItems, shikimoriItems) = coroutineScope {
            val fanartDef = async(Dispatchers.IO) {
                KitsugiEpisodeRatingsRepository.getFanartGalleryItems(
                    tmdbId = tmdbId ?: 0,
                    isMovie = isMovie,
                    fallbackMalId = fallbackMalId,
                    fallbackAniListId = fallbackAniListId,
                    fallbackKitsuId = fallbackKitsuId
                )
            }
            val tmdbDef = async(Dispatchers.IO) {
                if (tmdbId != null && tmdbId > 0) {
                    KitsugiEpisodeRatingsRepository.getTmdbGalleryItems(
                        tmdbId = tmdbId,
                        isMovie = isMovie
                    )
                } else emptyList()
            }
            val shikimoriDef = async(Dispatchers.IO) {
                val isAnime = entry.type == MediaType.Anime
                val shikimoriAnimeId = when {
                    entry.source.equals("shikimori", ignoreCase = true) -> entry.malId?.takeIf { it > 0 }
                    entry.source.equals("anilist", ignoreCase = true) -> {
                        val m = entry.malId ?: 0
                        if (m > 0 && m < 100_000_000) m else _detailState.value?.realMalId
                    }
                    entry.source.equals("mal", ignoreCase = true) || entry.source.equals("jikan", ignoreCase = true) -> entry.malId?.takeIf { it > 0 }
                    else -> _detailState.value?.realMalId
                }
                if (isAnime && shikimoriAnimeId != null && shikimoriAnimeId > 0) {
                    KitsugiShikimoriClient.fetchScreenshots(shikimoriAnimeId)
                } else emptyList()
            }
            Triple(fanartDef.await(), tmdbDef.await(), shikimoriDef.await())
        }

        // Mevcut TMDB/Jikan resimlerini de GalleryItem'a çevir ve birleştir
        val currentDetail = _detailState.value
        val coverUrl = currentDetail?.imageUrl ?: entry.imageUrl
        val entryImg = entry.imageUrl
        val existingItems = buildList {
            if (!coverUrl.isNullOrBlank()) {
                val src = determineSource(coverUrl, entry.source)
                add(GalleryItem(url = coverUrl, source = src, category = GalleryCategory.POSTER))
            }
            if (!entryImg.isNullOrBlank() && entryImg != coverUrl) {
                val src = determineSource(entryImg, entry.source)
                add(GalleryItem(url = entryImg, source = src, category = GalleryCategory.POSTER))
            }
            currentDetail?.pictures?.forEach { url ->
                if (url.isNotBlank() && url != coverUrl && url != entryImg) {
                    val src = determineSource(url, entry.source)
                    val cat = determineCategory(url, GalleryCategory.POSTER)
                    add(GalleryItem(url = url, source = src, category = cat))
                }
            }
        }

        // TMDB, Fanart ve Shikimori öğeleri çözünürlük, dil gibi zengin meta verilere sahiptir.
        // Önceden var olan öğeler zengin listede varsa metadata ile zenginleştirilsin.
        val richMap = (tmdbItems + fanartItems + shikimoriItems).associateBy { it.url }
        val enrichedExisting = existingItems.map { item ->
            richMap[item.url] ?: item
        }
        val allItems = (enrichedExisting + tmdbItems + fanartItems + shikimoriItems).distinctBy { it.url }

        val sortedItems = allItems.sortedWith(
            compareBy(
                { item ->
                    when (item.category) {
                        GalleryCategory.POSTER -> 0
                        GalleryCategory.BACKDROP -> 1
                        GalleryCategory.LOGO -> 2
                        GalleryCategory.CLEARART -> 3
                        GalleryCategory.THUMBNAIL -> 4
                        GalleryCategory.CHARACTER -> 5
                        GalleryCategory.PERSON    -> 5
                        GalleryCategory.BANNER -> 6
                        GalleryCategory.SQUARE -> 7
                        GalleryCategory.OTHER -> 8
                    }
                },
                { item -> if (item.url == coverUrl) 0 else 1 }
            )
        )
        _galleryItems.value = sortedItems
    }

    private fun determineSource(url: String, fallbackSource: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("fanart.tv") -> "Fanart.tv"
            lowerUrl.contains("image.tmdb.org") || lowerUrl.contains("tmdb.org") -> "TMDB"
            lowerUrl.contains("shikimori.one") || lowerUrl.contains("shikimori.me") -> "Shikimori"
            lowerUrl.contains("anilist.co") -> "AniList"
            lowerUrl.contains("simkl.in") || lowerUrl.contains("simkl.com") -> "Simkl"
            lowerUrl.contains("myanimelist.net") || lowerUrl.contains("jikan.moe") -> "Jikan (MAL)"
            lowerUrl.contains("kitsu.io") -> "Kitsu"
            else -> {
                when (fallbackSource.lowercase()) {
                    "shikimori" -> "Shikimori"
                    "anilist" -> "AniList"
                    "tmdb" -> "TMDB"
                    "simkl" -> "Simkl"
                    "jikan", "mal" -> "Jikan (MAL)"
                    "kitsu" -> "Kitsu"
                    else -> fallbackSource.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }

    private fun determineCategory(url: String, defaultCategory: GalleryCategory): GalleryCategory {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("clearart")                                                                       -> GalleryCategory.CLEARART
            lowerUrl.contains("logo")                                                                           -> GalleryCategory.LOGO
            lowerUrl.contains("backdrop") || lowerUrl.contains("background") || lowerUrl.contains("/w1280") || lowerUrl.contains("showbackground") -> GalleryCategory.BACKDROP
            lowerUrl.contains("square")                                                                         -> GalleryCategory.SQUARE
            lowerUrl.contains("poster") || lowerUrl.contains("/w780") || lowerUrl.contains("/w500") || lowerUrl.contains("/w342") || lowerUrl.contains("coverimage") || lowerUrl.contains("large_image_url") -> GalleryCategory.POSTER
            lowerUrl.contains("character") || lowerUrl.contains("actor") || lowerUrl.contains("voiceactor")    -> GalleryCategory.CHARACTER
            lowerUrl.contains("thumb") || lowerUrl.contains("still") || lowerUrl.contains("/w300") || lowerUrl.contains("/w185") -> GalleryCategory.THUMBNAIL
            lowerUrl.contains("banner")                                                                         -> GalleryCategory.BANNER
            else -> defaultCategory
        }
    }

    private suspend fun fetchSynopsis(entry: MediaEntry) {
        val stableId = entry.malId ?: 0
        val autoTranslate = runCatching { settingsDataStore.settingsFlow.first() }.getOrNull()?.autoTranslateEnabled ?: false

        // 1. Prioritize cached translation if available
        val cachedTr = DetailCache.getTranslation("synopsis", entry.source, stableId)
        if (cachedTr != null) {
            _synopsisState.value = SynopsisState.Success(cachedTr)
            _translatedSynopsis.value = cachedTr
            return
        }

        // 2. Identify the best raw synopsis text (TMDB/detail fetch, ViewModel state, or entry.synopsis)
        var rawText: String? = _originalSynopsis.value
            ?: (_synopsisState.value as? SynopsisState.Success)?.text
            ?: entry.synopsis
            ?: _detailState.value?.synopsis

        // 3. If no synopsis is available yet, fetch from API
        if (rawText.isNullOrBlank()) {
            _synopsisState.value = SynopsisState.Loading
            val fetched = withContext(Dispatchers.IO) {
                apiClient.fetchSynopsis(
                    source = entry.source,
                    externalId = entry.malId,
                    mediaType = entry.type
                )
            }
            if (!fetched.isNullOrBlank()) {
                rawText = fetched
            }
        }

        if (rawText.isNullOrBlank()) {
            if (_synopsisState.value !is SynopsisState.Success) {
                _synopsisState.value = SynopsisState.Error
            }
            return
        }

        _originalSynopsis.value = rawText

        // 4. Perform auto-translation or apply rawText (always auto-translate Russian text)
        val isRussian = rawText.any { it in '\u0400'..'\u04FF' }
        if (autoTranslate || isRussian) {
            _synopsisState.value = SynopsisState.Success(rawText)
            _translatedSynopsis.value = rawText
            val tr = withContext(Dispatchers.IO) {
                translationManager.translateToTurkish(rawText)
            }
            if (!tr.isNullOrBlank() && tr != rawText) {
                DetailCache.putTranslation("synopsis", entry.source, stableId, tr)
                _synopsisState.value = SynopsisState.Success(tr)
                _translatedSynopsis.value = tr
            }
        } else {
            _synopsisState.value = SynopsisState.Success(rawText)
            _translatedSynopsis.value = rawText
        }
    }

    fun translateSynopsis(entry: MediaEntry) {
        val raw = _originalSynopsis.value ?: return
        val stableId = entry.malId ?: entry.id
        viewModelScope.launch {
            val tr = withContext(Dispatchers.IO) {
                translationManager.translateToTurkish(raw)
            }
            if (!tr.isNullOrBlank() && tr != raw) {
                DetailCache.putTranslation("synopsis", entry.source, stableId, tr)
                _synopsisState.value = SynopsisState.Success(tr)
                _translatedSynopsis.value = tr
            }
        }
    }

    /**
     * Lazy-loads tabs data when a specific tab index is selected.
     */
    fun loadTab(tabIndex: Int, entry: MediaEntry, realMalId: Int?) {
        val effectiveExternalId = when (entry.source.lowercase()) {
            "simkl" -> entry.simklId?.takeIf { it > 0 } ?: (if (entry.id > 0) entry.id else (entry.malId ?: 0))
            else -> entry.malId ?: entry.id
        }
        // Önbellek anahtarı Kitsu'da kanonik kimlik üzerinden kurulur (bkz. cacheIdentityOf)
        val malId = cacheIdentityOf(entry)
        val effectiveRealMalId = realMalId
            ?: _detailState.value?.realMalId
            ?: (if (entry.source.equals("mal", true) || entry.source.equals("jikan", true) || entry.source.equals("shikimori", true)) entry.malId else null)
        val tmdbId = entry.tmdbId ?: _detailState.value?.tmdbId ?: _resolvedTmdbId.value
        viewModelScope.launch {
            try {
                when (tabIndex) {
                    1 -> {
                        // Resimler (Gallery) sekmesi — görseller loadGallery ile yüklenir
                    }
                    2 -> {
                        val currentSuccess = _charactersState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaCharacters(entry.source, malId) == null)
                        if (needsRefetch) {
                            _charactersState.value = DetailTabState.Loading
                            val resolvedTitle = _detailState.value?.title
                                ?: _detailState.value?.titleEnglish
                                ?: _detailState.value?.titleRomaji
                                ?: entry.title
                            val result = withContext(Dispatchers.IO) {
                                apiClient.fetchCharacters(
                                    source = entry.source,
                                    externalId = effectiveExternalId,
                                    mediaType = entry.type,
                                    realMalId = effectiveRealMalId,
                                    tmdbId = tmdbId,
                                    title = resolvedTitle
                                )
                            }
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaCharacters(entry.source, malId, result)
                            }
                            _charactersState.value = DetailTabState.Success(result)
                        }
                    }
                    3 -> {
                        val currentSuccess = _staffState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaStaff(entry.source, malId) == null)
                        if (needsRefetch) {
                            _staffState.value = DetailTabState.Loading
                            val result = withContext(Dispatchers.IO) {
                                apiClient.fetchStaff(entry.source, effectiveExternalId, entry.type, tmdbId = tmdbId, realMalId = realMalId)
                            }
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaStaff(entry.source, malId, result)
                            }
                            _staffState.value = DetailTabState.Success(result)
                        }
                    }
                    4 -> {
                        val currentSuccess = _recommendationsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaRecommendations(entry.source, malId) == null)
                        if (needsRefetch) {
                            _recommendationsState.value = DetailTabState.Loading
                            val result = withContext(Dispatchers.IO) {
                                apiClient.fetchRecommendations(entry.source, effectiveExternalId, entry.type, tmdbId = tmdbId, realMalId = realMalId, title = entry.title)
                            }
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaRecommendations(entry.source, malId, result)
                            }
                            _recommendationsState.value = DetailTabState.Success(result)
                        }
                    }
                    5 -> {
                        val currentSuccess = _relationsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaRelations(entry.source, malId) == null)
                        if (needsRefetch) {
                            _relationsState.value = DetailTabState.Loading
                            val result = withContext(Dispatchers.IO) {
                                apiClient.fetchRelations(entry.source, effectiveExternalId, entry.type, tmdbId = tmdbId, realMalId = realMalId, title = entry.title)
                            }
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaRelations(entry.source, malId, result)
                            }
                            _relationsState.value = DetailTabState.Success(result)
                        }
                    }
                    6 -> {
                        if (_statsState.value !is DetailTabState.Success) {
                            _statsState.value = DetailTabState.Loading
                            val result = withContext(Dispatchers.IO) {
                                apiClient.fetchStats(entry.source, effectiveExternalId, entry.type, realMalId = realMalId)
                            }
                            if (result != null) {
                                DetailCache.putMediaStats(entry.source, malId, result)
                                _statsState.value = DetailTabState.Success(result)
                            } else {
                                _statsState.value = DetailTabState.Success(null)
                            }
                        }
                    }
                    7 -> {
                        val currentSuccess = _reviewsState.value as? DetailTabState.Success
                        val needsRefetch = currentSuccess == null ||
                            (currentSuccess.data.isEmpty() && DetailCache.getMediaReviews(entry.source, malId) == null)
                        if (needsRefetch) {
                            _reviewsState.value = DetailTabState.Loading
                            val result = withContext(Dispatchers.IO) {
                                apiClient.fetchReviews(entry.source, effectiveExternalId, entry.type, tmdbId = tmdbId, realMalId = realMalId)
                            }
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaReviews(entry.source, malId, result)
                            }
                            _reviewsState.value = DetailTabState.Success(result)
                        }
                    }
                    8 -> {
                        val currentEpisodes = _episodesState.value
                        val needsEpFetch = currentEpisodes !is DetailTabState.Success ||
                            (currentEpisodes is DetailTabState.Success && currentEpisodes.data.isEmpty())
                        if (needsEpFetch) {
                            _episodesState.value = DetailTabState.Loading
                            val result = withContext(Dispatchers.IO) {
                                apiClient.fetchEpisodes(
                                    source = entry.source,
                                    externalId = effectiveExternalId,
                                    mediaType = entry.type,
                                    realMalId = realMalId,
                                    totalEpisodes = maxOf(entry.total ?: _detailState.value?.total ?: 0, entry.progress),
                                    context = context,
                                    targetSeason = _targetSeason.value,
                                    tmdbId = tmdbId
                                )
                            }
                            if (result.isNotEmpty()) {
                                DetailCache.putMediaEpisodes(entry.source, malId, result)
                            }
                            _episodesState.value = DetailTabState.Success(result)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tab data for index $tabIndex: ${e.message}", e)
                when (tabIndex) {
                    2 -> _charactersState.value = DetailTabState.Error
                    3 -> _staffState.value = DetailTabState.Error
                    4 -> _recommendationsState.value = DetailTabState.Error
                    5 -> _relationsState.value = DetailTabState.Error
                    6 -> _statsState.value = DetailTabState.Error
                    7 -> _reviewsState.value = DetailTabState.Error
                    8 -> _episodesState.value = DetailTabState.Error
                }
            }
        }
    }

    fun deleteMangaMapping(mediaId: Int) {
        viewModelScope.launch {
            mangaRepository.deleteMangaMapping(mediaId)
        }
    }

    fun setTargetSeason(season: Int, entry: MediaEntry) {
        _targetSeason.value = season
        _episodesState.value = DetailTabState.Loading
        DetailCache.removeMediaEpisodes(entry.source, entry.malId ?: 0)
        loadTab(7, entry, _detailState.value?.realMalId)
    }

    /**
     * Fetches MDBList external ratings for a library entry.
     * Resolves IMDb ID via KitsugiIdResolver then calls MdbListClient.
     * Works for AniList, MAL and Kitsu sources.
     */
    private suspend fun fetchMdbListRatingsForEntry(entry: MediaEntry) {
        val context = getApplication<android.app.Application>().applicationContext
        val settingsDataStore = SettingsDataStore(context)
        val settings = runCatching { settingsDataStore.settingsFlow.first() }.getOrNull()
        if (settings == null || !settings.mdbListEnabled || settings.mdbListApiKey.isBlank()) return

        _mdbListLoading.value = true
        try {
            val stableId = entry.malId ?: 0
            val isAniList = entry.source.equals("anilist", ignoreCase = true)

            val malId: Int? = when {
                isAniList && stableId >= 100_000_000 -> null // no real MAL ID from stableId
                isAniList -> stableId
                else -> entry.malId
            }
            val aniListId: Int? = if (isAniList && stableId >= 100_000_000) stableId - 100_000_000 else null
            val tmdbId: Int? = entry.tmdbId ?: _detailState.value?.tmdbId ?: _resolvedTmdbId.value

            // Try to resolve IMDb ID from detail links first
            var imdbId: String? = _detailState.value?.externalLinks
                ?.firstOrNull { it.url.contains("imdb.com") }
                ?.url?.substringAfter("/title/")?.substringBefore("/")

            if (imdbId.isNullOrBlank()) {
                val resolved = withContext(Dispatchers.IO) {
                    KitsugiIdResolver.resolveIds(
                        malId = malId,
                        aniListId = aniListId,
                        tmdbId = tmdbId,
                        mediaType = entry.type
                    )
                }
                imdbId = resolved.imdbId
            }

            if (!imdbId.isNullOrBlank()) {
                val ratings = withContext(Dispatchers.IO) {
                    MdbListClient.fetchRatings(imdbId, settings.mdbListApiKey)
                }
                _mdbListRatings.value = ratings
            }
        } catch (e: Exception) {
            Log.e(TAG, "MDBList entry rating fetch failed", e)
        } finally {
            _mdbListLoading.value = false
        }
    }
}
