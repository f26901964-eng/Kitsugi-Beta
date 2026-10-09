package com.kitsugi.animelist.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.*

/**
 * TMDB API'sinden kişi (oyuncu/ekip) detayları, medya jenerik/öneri listeleri,
 * yorumlar ve istatistik verilerini çeken istemci.
 *
 * [TmdbApiClient] tarafından delegate olarak kullanılır; doğrudan çağrılmamalıdır.
 */
internal object TmdbCreditsClient {

    private const val TAG = "TmdbCreditsClient"
    private const val IMG_W185 = "https://image.tmdb.org/t/p/w185"
    private const val IMG_W300 = "https://image.tmdb.org/t/p/w300"

    /** Bir credits yanıtında romaji için en fazla kaç kişi detayı sorgulanır (ağ yükü sınırı). */
    private const val MAX_PERSON_ROMAJI_LOOKUPS = 12

    /** TMDB person id → also_known_as listesi (oturum boyunca önbellek). */
    private val personAliasCache = java.util.concurrent.ConcurrentHashMap<Int, List<String>>()

    suspend fun fetchCredits(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): Pair<List<KitsugiCharacter>, List<KitsugiStaff>> = withContext(Dispatchers.IO) {
        val typePath = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typePath/$tmdbId/credits?api_key=$apiKey&language=$language"
        try {
            val responseText = executeGet(url) ?: return@withContext Pair(emptyList(), emptyList())
            val root = JSONObject(responseText)

            val castArray = root.optJSONArray("cast")
            val charList = mutableListOf<KitsugiCharacter>()
            if (castArray != null) {
                for (i in 0 until castArray.length()) {
                    val item = castArray.getJSONObject(i)
                    val id = item.optInt("id")
                    val actorName = item.optString("name", "Bilinmeyen")
                    val rawCharacterName = item.optString("character", "Bilinmeyen")
                    val isVoiceRole = rawCharacterName.contains("(voice)", ignoreCase = true) ||
                        rawCharacterName.contains("(uncredited voice)", ignoreCase = true)
                    val characterName = rawCharacterName
                        .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice)\\)", RegexOption.IGNORE_CASE), "")
                        .trim()
                    val profilePath = item.optNullableString("profile_path")
                    val order = item.optInt("order", 999)
                    val actorImageUrl = if (!profilePath.isNullOrEmpty()) "$IMG_W185$profilePath" else null
                    val va = KitsugiVoiceActor(
                        id = id, name = actorName, language = "oyuncu",
                        imageUrl = actorImageUrl, source = "tmdb"
                    )
                    // Fictional/voice-acted character shouldn't display actor's human photo
                    val charImageUrl = if (isVoiceRole) null else actorImageUrl
                    charList.add(
                        KitsugiCharacter(
                            id = id,
                            name = characterName,
                            role = if (order < 5) "main".toTurkishCharacterRole() else "supporting".toTurkishCharacterRole(),
                            imageUrl = charImageUrl,
                            voiceActors = listOf(va),
                            source = "tmdb"
                        )
                    )
                }
            }

            val crewArray = root.optJSONArray("crew")
            val staffList = mutableListOf<KitsugiStaff>()
            if (crewArray != null) {
                for (i in 0 until crewArray.length()) {
                    val item = crewArray.getJSONObject(i)
                    val id = item.optInt("id")
                    val name = item.optString("name", "Bilinmeyen")
                    val job = item.optString("job", "Ekip Üyesi")
                    val profilePath = item.optNullableString("profile_path")
                    val imageUrl = if (!profilePath.isNullOrEmpty()) "$IMG_W185$profilePath" else null
                    staffList.add(
                        KitsugiStaff(id = id, name = name, role = job.toTurkishStaffRole(),
                            imageUrl = imageUrl, source = "tmdb")
                    )
                }
            }
            // TMDB isimleri çoğunlukla Japonca (CJK) döner; romaji/Latin ad kişinin
            // also_known_as listesinden alınır. Kart ve listelerde romaji gösterilebilsin diye
            // yalnızca CJK adı olan kişiler için romanizedName doldurulur.
            val cjkPersonIds = charList.flatMap { it.voiceActors }
                .filter { PreferenceHelpers.hasCjkCharacters(it.name) }
                .map { it.id } +
                staffList.filter { PreferenceHelpers.hasCjkCharacters(it.name) }.map { it.id }
            val romajiByPerson: Map<Int, String> = if (cjkPersonIds.isEmpty()) emptyMap<Int, String>()
                else resolvePersonRomaji(cjkPersonIds, apiKey, language, executeGet)
            if (romajiByPerson.isEmpty()) {
                Pair(charList, staffList)
            } else {
                Pair(
                    charList.map { character ->
                        character.copy(
                            voiceActors = character.voiceActors.map { va ->
                                romajiByPerson[va.id]?.let { va.copy(romanizedName = it) } ?: va
                            }
                        )
                    },
                    staffList.map { staff ->
                        romajiByPerson[staff.id]?.let { staff.copy(romanizedName = it) } ?: staff
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching credits: ${e.message}", e)
            Pair(emptyList(), emptyList())
        }
    }

    suspend fun fetchRelations(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<KitsugiRelation> = withContext(Dispatchers.IO) {
        val list = mutableListOf<KitsugiRelation>()
        if (isMovie) {
            val movieUrl = "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$apiKey&language=$language"
            try {
                val movieResp = executeGet(movieUrl)
                if (movieResp != null) {
                    val movieJson = JSONObject(movieResp)
                    val belongsToCollection = movieJson.optJSONObject("belongs_to_collection")
                    if (belongsToCollection != null) {
                        val collectionId = belongsToCollection.optInt("id")
                        if (collectionId > 0) {
                            val collectionUrl = "https://api.themoviedb.org/3/collection/$collectionId?api_key=$apiKey&language=$language"
                            val collectionResp = executeGet(collectionUrl)
                            if (collectionResp != null) {
                                val collJson = JSONObject(collectionResp)
                                val parts = collJson.optJSONArray("parts")
                                if (parts != null) {
                                    // Türkçe başlık yoksa İngilizce'ye düşmek için en-US haritası
                                    val enTitles = if (TmdbTitleFallback.needsEnglishFallback(parts, collectionUrl, { true })) {
                                        TmdbTitleFallback.parseEnglishTitles(
                                            executeGet(TmdbUrlUtils.englishVariant(collectionUrl))
                                        )
                                    } else emptyMap()
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val partId = part.optInt("id")
                                        if (partId == tmdbId) continue
                                        val resolved = TmdbTitleFallback.resolve(
                                            item = part,
                                            isMovie = true,
                                            url = collectionUrl,
                                            englishTitle = enTitles[partId]
                                        )
                                        val posterPath = part.optNullableString("poster_path") ?: ""
                                        val imageUrl = if (posterPath.isNotEmpty()) "$IMG_W185$posterPath" else null
                                        list.add(
                                            KitsugiRelation(
                                                malId = partId, title = resolved.display, relationType = "Seri",
                                                imageUrl = imageUrl,
                                                mediaType = MediaType.Movie,
                                                source = "tmdb",
                                                titleEnglish = resolved.english,
                                                titleJapanese = resolved.native
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching collection for movie $tmdbId: ${e.message}", e)
            }
        }

        // NOTE: We deliberately do NOT fall back to TMDB /similar here.
        // Relations are strictly franchise relations. TMDB /similar returns unrelated keyword matches.
        list
    }

    suspend fun fetchRecommendations(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<KitsugiRelation> = withContext(Dispatchers.IO) {
        val typePath = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typePath/$tmdbId/recommendations?api_key=$apiKey&language=$language"
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            // Türkçe başlık yoksa İngilizce'ye düşmek için en-US haritası
            val enTitles = if (TmdbTitleFallback.needsEnglishFallback(results, url, { isMovie })) {
                TmdbTitleFallback.parseEnglishTitles(executeGet(TmdbUrlUtils.englishVariant(url)))
            } else emptyMap()
            val list = mutableListOf<KitsugiRelation>()
            for (i in 0 until minOf(results.length(), 20)) {
                val item = results.getJSONObject(i)
                val isAdult = item.optBoolean("adult", false)
                if (isAdult) continue
                val id = item.optInt("id")
                val resolved = TmdbTitleFallback.resolve(
                    item = item,
                    isMovie = isMovie,
                    url = url,
                    englishTitle = enTitles[id]
                )
                val posterPath = item.optNullableString("poster_path") ?: ""
                val imageUrl = if (posterPath.isNotEmpty()) "$IMG_W185$posterPath" else null
                list.add(
                    KitsugiRelation(
                        malId = id, title = resolved.display, relationType = "Tavsiye",
                        imageUrl = imageUrl,
                        mediaType = if (isMovie) MediaType.Movie else MediaType.TvShow,
                        source = "tmdb",
                        titleEnglish = resolved.english,
                        titleJapanese = resolved.native
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB recommendations: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchReviews(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<KitsugiReview> = withContext(Dispatchers.IO) {
        val typePath = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typePath/$tmdbId/reviews?api_key=$apiKey&language=$language"
        try {
            val responseText = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(responseText)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = mutableListOf<KitsugiReview>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val author = item.optString("author", "Kullanıcı")
                val content = item.optString("content", "").cleanApiText()
                val summary = if (content.length > 280) content.take(280) + "..." else content
                val authorDetails = item.optJSONObject("author_details")
                val rating = authorDetails?.optDouble("rating", 0.0) ?: 0.0
                val score = if (rating > 0.0) (rating * 10).toInt() else null
                val avatarPath = authorDetails?.optNullableString("avatar_path") ?: ""
                val avatarUrl = if (avatarPath.isNotEmpty()) {
                    if (avatarPath.startsWith("/http")) avatarPath.substring(1)
                    else "https://image.tmdb.org/t/p/w185$avatarPath"
                } else null
                val rawDate = item.optString("created_at", "")
                val dateText = if (rawDate.isNotEmpty()) {
                    try {
                        val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                        val sdfOut = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                        sdfOut.format(sdfIn.parse(rawDate)!!)
                    } catch (_: Exception) { rawDate.take(10) }
                } else null
                list.add(
                    KitsugiReview(
                        id = null, username = author, avatarUrl = avatarUrl,
                        score = score, summary = summary, fullText = content,
                        dateText = dateText, helpfulCount = null, ratingAmount = null, userRating = null
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB reviews: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchPersonCharacterDetail(
        personId: Int,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): KitsugiCharacterDetail? = withContext(Dispatchers.IO) {
        val url = "https://api.themoviedb.org/3/person/$personId?api_key=$apiKey&language=$language"
        try {
            val responseText = executeGet(url) ?: return@withContext null
            val root = JSONObject(responseText)
            val name = root.optString("name", "Bilinmeyen")
            val biography = root.optString("biography", "").cleanApiText().takeIf { it.isNotBlank() }
            val profilePath = root.optNullableString("profile_path") ?: ""
            val imageUrl = if (profilePath.isNotEmpty()) "$IMG_W300$profilePath" else null
            val birthday = root.optString("birthday", "").takeIf { it.isNotBlank() }
            val placeOfBirth = root.optString("place_of_birth", "").takeIf { it.isNotBlank() }
            val genderInt = root.optInt("gender", 0)
            val gender = when (genderInt) { 1 -> "Dişi"; 2 -> "Erkek"; 3 -> "Non-binary"; else -> null }
            val alternativeNames = mutableListOf<String>()
            val aka = root.optJSONArray("also_known_as")
            if (aka != null) {
                for (i in 0 until aka.length()) {
                    val nameStr = aka.optString(i, "")
                    if (nameStr.isNotBlank()) alternativeNames.add(nameStr)
                }
            }
            val appearances = fetchPersonMediaAppearances(personId, apiKey, language, executeGet)
            KitsugiCharacterDetail(
                id = personId, name = name, nativeName = null,
                alternativeNames = alternativeNames, imageUrl = imageUrl,
                gender = gender, age = null, birthday = birthday, bloodType = null,
                biography = biography, voiceActors = emptyList(), mediaAppearances = appearances,
                romanizedName = if (PreferenceHelpers.hasCjkCharacters(name)) pickLatinAlias(alternativeNames) else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB person details: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchPersonMediaAppearances(
        personId: Int,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<KitsugiCharacterMediaAppearance> {
        val url = "https://api.themoviedb.org/3/person/$personId/combined_credits?api_key=$apiKey&language=$language"
        return try {
            val responseText = executeGet(url) ?: return emptyList()
            val root = JSONObject(responseText)
            val castArray = root.optJSONArray("cast") ?: return emptyList()
            val enTitles = if (TmdbTitleFallback.needsEnglishFallback(castArray, url, { it.optString("media_type") == "movie" })) {
                TmdbTitleFallback.parseLatinTitles(
                    executeGet(TmdbUrlUtils.englishVariant(url)),
                    listOf("cast", "crew")
                )
            } else emptyMap()
            val list = mutableListOf<KitsugiCharacterMediaAppearance>()
            for (i in 0 until minOf(castArray.length(), 20)) {
                val item = castArray.getJSONObject(i)
                val id = item.optInt("id")
                val isMovie = item.optString("media_type") == "movie"
                val title = TmdbTitleFallback.resolve(item, isMovie, url, enTitles[id]).display
                    .ifBlank { "Bilinmeyen" }
                val character = item.optString("character", "Bilinmeyen")
                val posterPath = item.optNullableString("poster_path") ?: ""
                val imageUrl = if (posterPath.isNotEmpty()) "$IMG_W185$posterPath" else null
                list.add(
                    KitsugiCharacterMediaAppearance(
                        mediaId = id, title = title, imageUrl = imageUrl,
                        mediaType = if (isMovie) "movie".toTurkishMediaTypeString() else "tv".toTurkishMediaTypeString(),
                        characterRole = character, source = "tmdb"
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB combined credits: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchPersonStaffDetail(
        personId: Int,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): KitsugiStaffDetail? = withContext(Dispatchers.IO) {
        val url = "https://api.themoviedb.org/3/person/$personId?api_key=$apiKey&language=$language"
        try {
            val responseText = executeGet(url) ?: return@withContext null
            val root = JSONObject(responseText)
            val name = root.optString("name", "Bilinmeyen")
            val biography = root.optString("biography", "").cleanApiText().takeIf { it.isNotBlank() }
            val profilePath = root.optNullableString("profile_path") ?: ""
            val imageUrl = if (profilePath.isNotEmpty()) "$IMG_W300$profilePath" else null
            val birthday = root.optString("birthday", "").takeIf { it.isNotBlank() }
            val placeOfBirth = root.optString("place_of_birth", "").takeIf { it.isNotBlank() }
            val genderInt = root.optInt("gender", 0)
            val gender = when (genderInt) { 1 -> "Dişi"; 2 -> "Erkek"; 3 -> "Non-binary"; else -> null }
            val alternativeNames = mutableListOf<String>()
            val aka = root.optJSONArray("also_known_as")
            if (aka != null) {
                for (i in 0 until aka.length()) {
                    val nameStr = aka.optString(i, "")
                    if (nameStr.isNotBlank()) alternativeNames.add(nameStr)
                }
            }
            val department = root.optString("known_for_department", "Ekip Üyesi")
                .takeIf { it.isNotBlank() }?.toTurkishStaffRole()
            val works = fetchPersonMediaWorks(personId, apiKey, language, executeGet)
            val characterRoles = fetchPersonCharacterRoles(personId, apiKey, language, executeGet)
            KitsugiStaffDetail(
                id = personId, name = name, nativeName = null,
                alternativeNames = alternativeNames, imageUrl = imageUrl,
                biography = biography, occupation = department, birthday = birthday,
                age = null, gender = gender, homeTown = placeOfBirth,
                characterRoles = characterRoles, mediaWorks = works,
                romanizedName = if (PreferenceHelpers.hasCjkCharacters(name)) pickLatinAlias(alternativeNames) else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB staff details: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchPersonMediaWorks(
        personId: Int,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<KitsugiStaffMediaWork> {
        val url = "https://api.themoviedb.org/3/person/$personId/combined_credits?api_key=$apiKey&language=$language"
        return try {
            val responseText = executeGet(url) ?: return emptyList()
            val root = JSONObject(responseText)
            val crewArray = root.optJSONArray("crew") ?: return emptyList()
            val enTitles = if (TmdbTitleFallback.needsEnglishFallback(crewArray, url, { it.optString("media_type") == "movie" })) {
                TmdbTitleFallback.parseLatinTitles(
                    executeGet(TmdbUrlUtils.englishVariant(url)),
                    listOf("cast", "crew")
                )
            } else emptyMap()
            val list = mutableListOf<KitsugiStaffMediaWork>()
            for (i in 0 until minOf(crewArray.length(), 20)) {
                val item = crewArray.getJSONObject(i)
                val id = item.optInt("id")
                val isMovie = item.optString("media_type") == "movie"
                val title = TmdbTitleFallback.resolve(item, isMovie, url, enTitles[id]).display
                    .ifBlank { "Bilinmeyen" }
                val job = item.optString("job", "Ekip Üyesi")
                val posterPath = item.optNullableString("poster_path") ?: ""
                val imageUrl = if (posterPath.isNotEmpty()) "$IMG_W185$posterPath" else null
                list.add(
                    KitsugiStaffMediaWork(
                        mediaId = id, mediaTitle = title, mediaImageUrl = imageUrl,
                        mediaType = if (isMovie) "movie".toTurkishMediaTypeString() else "tv".toTurkishMediaTypeString(),
                        staffRole = job.toTurkishStaffRole(), source = "tmdb"
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB media works: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun fetchPersonCharacterRoles(
        personId: Int,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<KitsugiStaffCharacterRole> {
        val url = "https://api.themoviedb.org/3/person/$personId/combined_credits?api_key=$apiKey&language=$language"
        return try {
            val responseText = executeGet(url) ?: return emptyList()
            val root = JSONObject(responseText)
            val castArray = root.optJSONArray("cast") ?: return emptyList()
            val enTitles = if (TmdbTitleFallback.needsEnglishFallback(castArray, url, { it.optString("media_type") == "movie" })) {
                TmdbTitleFallback.parseLatinTitles(
                    executeGet(TmdbUrlUtils.englishVariant(url)),
                    listOf("cast", "crew")
                )
            } else emptyMap()
            val list = mutableListOf<KitsugiStaffCharacterRole>()
            // Sort by popularity descending and take top 30
            val sorted = (0 until castArray.length())
                .map { castArray.getJSONObject(it) }
                .sortedByDescending { it.optDouble("popularity", 0.0) }
                .take(30)
            for (item in sorted) {
                val mediaId = item.optInt("id")
                val isMovie = item.optString("media_type") == "movie"
                val mediaTitle = TmdbTitleFallback.resolve(item, isMovie, url, enTitles[mediaId]).display
                    .ifBlank { "Bilinmeyen" }
                val characterName = item.optString("character", "").ifBlank { "Bilinmeyen" }
                val posterPath = item.optNullableString("poster_path") ?: ""
                val mediaImageUrl = if (posterPath.isNotEmpty()) "$IMG_W185$posterPath" else null
                val mediaType = if (isMovie) "movie".toTurkishMediaTypeString() else "tv".toTurkishMediaTypeString()
                // TMDB has no character profile images; use media poster as fallback
                list.add(
                    KitsugiStaffCharacterRole(
                        characterId = mediaId, // TMDB has no separate char IDs; use media ID
                        characterName = characterName,
                        characterImageUrl = mediaImageUrl,
                        characterSource = "tmdb",
                        mediaId = mediaId,
                        mediaTitle = mediaTitle,
                        mediaImageUrl = mediaImageUrl,
                        mediaType = mediaType,
                        characterRole = if (item.optInt("order", 999) < 5) "Ana Karakter" else "Oyuncu",
                        mediaSource = "tmdb"
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB character roles: ${e.message}", e)
            emptyList()
        }
    }
    suspend fun fetchStats(
        tmdbId: Int,
        isMovie: Boolean,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): KitsugiStats? = withContext(Dispatchers.IO) {
        val typePath = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typePath/$tmdbId?api_key=$apiKey&language=$language"
        try {
            val responseText = executeGet(url) ?: return@withContext null
            val root = JSONObject(responseText)
            val voteCount = root.optInt("vote_count", 0)
            val voteAverage = root.optDouble("vote_average", 0.0)
            val popularity = root.optDouble("popularity", 0.0)
            if (voteCount <= 0) return@withContext null

            val avgScore = voteAverage.coerceIn(1.0, 10.0)
            val scoreList = mutableListOf<KitsugiScoreStat>()
            for (score in 1..10) {
                val dist = score - avgScore
                val sigma = 1.8
                val weight = Math.exp(-(dist * dist) / (2 * sigma * sigma))
                val amount = (voteCount * weight * 0.20).toInt().coerceAtLeast(if (score == avgScore.toInt()) voteCount / 5 else 0)
                if (amount > 0) scoreList.add(KitsugiScoreStat(score, amount))
            }

            KitsugiStats(
                watching = (popularity * 10).toInt().coerceAtLeast(0),
                completed = voteCount,
                planned = null,
                dropped = null,
                scoreDistribution = scoreList
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchStats TMDB error: ${e.message}", e)
            null
        }
    }

    /**
     * Çoklu dilde TMDB kişi adlarından (CJK) Latin/romaji adayını seçer.
     * Boşluk içeren ad ("Keitarou Motonaga") tercih edilir; yoksa ilk Latin ad.
     */
    internal fun pickLatinAlias(aliases: List<String>): String? {
        val latin = aliases.filter { alias ->
            alias.any { it.isLetter() } && !PreferenceHelpers.hasCjkCharacters(alias)
        }
        return latin.firstOrNull { it.contains(' ') } ?: latin.firstOrNull()
    }

    private suspend fun fetchPersonAliases(
        personId: Int,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): List<String> {
        personAliasCache[personId]?.let { return it }
        val url = "https://api.themoviedb.org/3/person/$personId?api_key=$apiKey&language=$language"
        return try {
            val responseText = executeGet(url) ?: return emptyList()
            val root = JSONObject(responseText)
            val aka = root.optJSONArray("also_known_as")
            val aliases: List<String> = if (aka == null) emptyList<String>() else
                (0 until aka.length()).mapNotNull { i ->
                    aka.optString(i, "").trim().takeIf { it.isNotEmpty() }
                }
            personAliasCache[personId] = aliases
            aliases
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching TMDB person aliases for $personId: ${e.message}")
            emptyList()
        }
    }

    /** Verilen kişi id'leri için romaji adlarını paralel ve üst sınırlı şekilde çözer. */
    private suspend fun resolvePersonRomaji(
        personIds: List<Int>,
        apiKey: String,
        language: String,
        executeGet: suspend (String) -> String?
    ): Map<Int, String> = coroutineScope {
        personIds.filter { it > 0 }.distinct().take(MAX_PERSON_ROMAJI_LOOKUPS).map { id ->
            async {
                val romaji = pickLatinAlias(fetchPersonAliases(id, apiKey, language, executeGet))
                id to romaji
            }
        }.awaitAll().mapNotNull { (id, romaji) -> romaji?.let { id to it } }.toMap()
    }
}
