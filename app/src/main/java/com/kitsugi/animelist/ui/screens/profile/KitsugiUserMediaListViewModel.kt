package com.kitsugi.animelist.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.remote.KitsugiApiBase
import com.kitsugi.animelist.data.remote.cleanApiText
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.RoundingMode

data class UserMediaListItem(
    val mediaId: Int,
    val malId: Int?,
    val title: String,
    val imageUrl: String?,
    val mediaType: MediaType,
    val status: WatchStatus,
    val score: Double?,
    val progress: Int,
    val total: Int?,
    val isAdult: Boolean,
    val format: String?,
    val year: Int?,
    val listEntryId: Int = mediaId,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val repeatCount: Int = 0,
    val volumeProgress: Int = 0,
    val priority: Int? = null,
    val isPrivate: Boolean = false,
    val isHiddenFromStatusLists: Boolean = false,
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L,
    val isFavorite: Boolean = false
)

data class UserMediaListUiState(
    val isLoading: Boolean = true,
    val items: List<UserMediaListItem> = emptyList(),
    val error: String? = null
)

/**
 * Başka bir AniList kullanıcısının Anime + Manga kütüphanesini tek seferde hazırlar.
 *
 * Ekran iki koleksiyonu birlikte tutar; böylece Listem'deki Tümü/Anime/Manga filtreleri
 * ağ isteği veya ekran sıfırlaması olmadan, aynı veri kümesi üzerinde çalışır.
 */
class KitsugiUserMediaListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UserMediaListUiState())
    val uiState: StateFlow<UserMediaListUiState> = _uiState.asStateFlow()

    private var loadedUserId: Int? = null
    private var loadJob: Job? = null

    fun loadUserMediaLibrary(userId: Int, forceRefresh: Boolean = false) {
        if (!forceRefresh && loadedUserId == userId &&
            (_uiState.value.isLoading || _uiState.value.error == null)
        ) {
            return
        }

        val keepCurrentItems = loadedUserId == userId
        loadJob?.cancel()
        loadedUserId = userId
        loadJob = viewModelScope.launch {
            // Yenilemede mevcut kartları ekranda tut; farklı kullanıcıda eski kartları göstermeyiz.
            _uiState.value = if (keepCurrentItems) {
                _uiState.value.copy(isLoading = true, error = null)
            } else {
                UserMediaListUiState(isLoading = true, items = emptyList(), error = null)
            }

            supervisorScope {
                val animeRequest = async { fetchUserMediaListFromAniList(userId, MediaType.Anime) }
                val mangaRequest = async { fetchUserMediaListFromAniList(userId, MediaType.Manga) }
                val favoritesRequest = async { fetchUserFavoriteIds(userId) }
                val animeItems = animeRequest.await()
                val mangaItems = mangaRequest.await()

                if (animeItems == null && mangaItems == null) {
                    favoritesRequest.cancel()
                    loadedUserId = null // Sonraki girişte yeniden denenebilsin.
                    _uiState.value = UserMediaListUiState(
                        isLoading = false,
                        items = emptyList(),
                        error = "Kullanıcının listesi yüklenemedi"
                    )
                    return@supervisorScope
                }

                val baseItems = (animeItems.orEmpty() + mangaItems.orEmpty())
                    .distinctBy { it.mediaType to it.mediaId }
                val partialError = when {
                    animeItems == null -> "Anime listesi yüklenemedi; Manga listesi gösteriliyor"
                    mangaItems == null -> "Manga listesi yüklenemedi; Anime listesi gösteriliyor"
                    else -> null
                }

                // Favori sayfaları sürerken listeyi bekletme; kartları iki ana istek biter bitmez göster.
                _uiState.value = UserMediaListUiState(
                    isLoading = false,
                    items = baseItems,
                    error = partialError
                )

                val favoriteIds = favoritesRequest.await()
                if (loadedUserId == userId) {
                    val enrichedItems = baseItems.map { item ->
                        val favoritesForType = if (item.mediaType == MediaType.Manga) {
                            favoriteIds.manga
                        } else {
                            favoriteIds.anime
                        }
                        item.copy(isFavorite = item.mediaId in favoritesForType)
                    }
                    _uiState.value = _uiState.value.copy(items = enrichedItems)
                }
            }
        }
    }

    /** Eski çağrı sözleşmesini korur; yeni ekran iki medya türünü birlikte kullanır. */
    @Suppress("UNUSED_PARAMETER")
    fun loadUserMediaList(userId: Int, mediaType: MediaType, forceRefresh: Boolean = false) {
        loadUserMediaLibrary(userId = userId, forceRefresh = forceRefresh)
    }

    fun resetState() {
        loadJob?.cancel()
        loadJob = null
        loadedUserId = null
        _uiState.value = UserMediaListUiState()
    }

    private suspend fun fetchUserMediaListFromAniList(
        userId: Int,
        mediaType: MediaType
    ): List<UserMediaListItem>? = withContext(Dispatchers.IO) {
        val typeName = if (mediaType == MediaType.Anime) "ANIME" else "MANGA"
        val query = """
            query (${'$'}userId: Int, ${'$'}type: MediaType) {
                MediaListCollection(userId: ${'$'}userId, type: ${'$'}type) {
                    lists {
                        entries {
                            id
                            status
                            score(format: POINT_10_DECIMAL)
                            progress
                            progressVolumes
                            repeat
                            priority
                            private
                            hiddenFromStatusLists
                            createdAt
                            updatedAt
                            startedAt { year month day }
                            completedAt { year month day }
                            media {
                                id
                                idMal
                                type
                                format
                                title { romaji english native userPreferred }
                                coverImage { extraLarge large medium }
                                episodes
                                chapters
                                isAdult
                                seasonYear
                                startDate { year }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("userId", userId)
            .put("type", typeName)

        runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables)
                ?: return@runCatching null
            val root = JSONObject(response)
            val collection = root.optJSONObject("data")
                ?.optJSONObject("MediaListCollection")
                ?: return@runCatching null
            val lists = collection.optJSONArray("lists") ?: return@runCatching emptyList()
            val result = mutableListOf<UserMediaListItem>()

            for (listIndex in 0 until lists.length()) {
                val entries = lists.optJSONObject(listIndex)?.optJSONArray("entries") ?: continue
                for (entryIndex in 0 until entries.length()) {
                    val entry = entries.optJSONObject(entryIndex) ?: continue
                    val media = entry.optJSONObject("media") ?: continue
                    val mediaId = media.optInt("id", 0)
                    if (mediaId <= 0) continue

                    val titleObject = media.optJSONObject("title")
                    val romaji = titleObject.optNonBlank("romaji")
                    val english = titleObject.optNonBlank("english")
                    val native = titleObject.optNonBlank("native")
                    val preferred = titleObject.optNonBlank("userPreferred")
                    val title = (romaji ?: preferred ?: english ?: native ?: "Medya #$mediaId")
                        .cleanApiText()

                    val cover = media.optJSONObject("coverImage")
                    val imageUrl = cover.optNonBlank("extraLarge")
                        ?: cover.optNonBlank("large")
                        ?: cover.optNonBlank("medium")
                    val malId = media.optPositiveInt("idMal")
                    val listEntryId = entry.optPositiveInt("id")
                        ?: syntheticListEntryId(mediaId, mediaType)
                    val score = entry.optPositiveDouble("score")
                    val repeatCount = entry.optInt("repeat", 0).coerceAtLeast(0)
                    val status = mapAniListStatus(entry.optString("status"))
                    val format = media.optNonBlank("format")?.toDisplayFormat()
                    val year = media.optPositiveInt("seasonYear")
                        ?: media.optJSONObject("startDate").optPositiveInt("year")
                    val total = if (mediaType == MediaType.Anime) {
                        media.optPositiveInt("episodes")
                    } else {
                        media.optPositiveInt("chapters")
                    }

                    result += UserMediaListItem(
                        mediaId = mediaId,
                        malId = malId,
                        title = title,
                        imageUrl = imageUrl,
                        mediaType = mediaType,
                        status = status,
                        score = score,
                        progress = entry.optInt("progress", 0).coerceAtLeast(0),
                        total = total,
                        isAdult = media.optBoolean("isAdult", false),
                        format = format,
                        year = year,
                        listEntryId = listEntryId,
                        titleEnglish = english?.cleanApiText(),
                        titleNative = native?.cleanApiText(),
                        startDate = entry.optJSONObject("startedAt").toDateString(),
                        endDate = entry.optJSONObject("completedAt").toDateString(),
                        repeatCount = repeatCount,
                        volumeProgress = entry.optInt("progressVolumes", 0).coerceAtLeast(0),
                        priority = entry.optInt("priority", 0).takeIf { it > 0 },
                        isPrivate = entry.optBoolean("private", false),
                        isHiddenFromStatusLists = entry.optBoolean("hiddenFromStatusLists", false),
                        updatedAt = entry.optLong("updatedAt", 0L),
                        createdAt = entry.optLong("createdAt", 0L)
                    )
                }
            }
            result.distinctBy { it.mediaId }
        }.getOrNull()
    }

    /** Hedef kullanıcının favorilerini getirir; Media.isFavourite oturum sahibini anlattığı için kullanılmaz. */
    private suspend fun fetchUserFavoriteIds(userId: Int): FavoriteIds = withContext(Dispatchers.IO) {
        val animeIds = linkedSetOf<Int>()
        val mangaIds = linkedSetOf<Int>()
        var page = 1
        var animeHasNext = true
        var mangaHasNext = true

        while ((animeHasNext || mangaHasNext) && page <= MAX_FAVORITE_PAGES) {
            val query = """
                query (${'$'}userId: Int, ${'$'}page: Int) {
                    User(id: ${'$'}userId) {
                        favourites {
                            anime(page: ${'$'}page, perPage: 50) {
                                pageInfo { hasNextPage }
                                nodes { id }
                            }
                            manga(page: ${'$'}page, perPage: 50) {
                                pageInfo { hasNextPage }
                                nodes { id }
                            }
                        }
                    }
                }
            """.trimIndent()
            val variables = JSONObject().put("userId", userId).put("page", page)
            val response = runCatching {
                KitsugiApiBase.executeAniListQuery(query, variables)
            }.getOrNull() ?: break
            val favorites = runCatching {
                JSONObject(response)
                    .optJSONObject("data")
                    ?.optJSONObject("User")
                    ?.optJSONObject("favourites")
            }.getOrNull() ?: break

            val anime = favorites.optJSONObject("anime")
            val manga = favorites.optJSONObject("manga")
            if (animeHasNext) anime.collectNodeIdsInto(animeIds)
            if (mangaHasNext) manga.collectNodeIdsInto(mangaIds)
            animeHasNext = animeHasNext && (anime?.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true)
            mangaHasNext = mangaHasNext && (manga?.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true)
            page++
        }

        FavoriteIds(anime = animeIds, manga = mangaIds)
    }

    private data class FavoriteIds(
        val anime: Set<Int> = emptySet(),
        val manga: Set<Int> = emptySet()
    )

    private companion object {
        // AniList 50 öğelik sayfalar kullanır; bu sınır 5.000 favoriye kadar eksiksizdir.
        const val MAX_FAVORITE_PAGES = 100
    }
}

private fun mapAniListStatus(rawStatus: String): WatchStatus = when (rawStatus.uppercase()) {
    "CURRENT" -> WatchStatus.Watching
    "REPEATING" -> WatchStatus.Repeating
    "COMPLETED" -> WatchStatus.Completed
    "PLANNING" -> WatchStatus.Planned
    "PAUSED" -> WatchStatus.Paused
    "DROPPED" -> WatchStatus.Dropped
    else -> WatchStatus.Watching
}

private fun syntheticListEntryId(mediaId: Int, mediaType: MediaType): Int = when (mediaType) {
    MediaType.Manga -> 300_000_000 + mediaId
    else -> 200_000_000 + mediaId
}

private fun String.toDisplayFormat(): String =
    replace('_', ' ')
        .lowercase()
        .replaceFirstChar { character -> character.titlecase() }

private fun JSONObject?.optNonBlank(key: String): String? = this
    ?.takeUnless { it.isNull(key) }
    ?.optString(key)
    ?.trim()
    ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

private fun JSONObject?.optPositiveInt(key: String): Int? = this
    ?.takeUnless { it.isNull(key) }
    ?.optInt(key, 0)
    ?.takeIf { it > 0 }

private fun JSONObject.optPositiveDouble(key: String): Double? =
    takeUnless { isNull(key) }
        ?.optDouble(key, 0.0)
        ?.takeIf { it > 0.0 }

private fun JSONObject?.toDateString(): String? {
    val year = this?.optPositiveInt("year") ?: return null
    val month = optPositiveInt("month")
    val day = optPositiveInt("day")
    return when {
        month != null && day != null -> "%04d-%02d-%02d".format(year, month, day)
        month != null -> "%04d-%02d".format(year, month)
        else -> year.toString()
    }
}

private fun JSONObject?.collectNodeIdsInto(destination: MutableSet<Int>) {
    val nodes = this?.optJSONArray("nodes") ?: return
    for (index in 0 until nodes.length()) {
        nodes.optJSONObject(index)?.optPositiveInt("id")?.let(destination::add)
    }
}

internal fun UserMediaListItem.roundedScore(): Int? = score
    ?.takeIf { it > 0.0 }
    ?.toBigDecimal()
    ?.setScale(0, RoundingMode.HALF_UP)
    ?.toInt()
    ?.coerceIn(1, 10)
