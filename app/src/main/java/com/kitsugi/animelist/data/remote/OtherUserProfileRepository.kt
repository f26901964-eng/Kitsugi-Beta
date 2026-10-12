package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.ui.app.AniListStats
import com.kitsugi.animelist.ui.app.KitsuProfileState
import com.kitsugi.animelist.ui.app.MalActivityEntry
import com.kitsugi.animelist.ui.app.MalProfileState
import com.kitsugi.animelist.ui.app.ProfileFavoriteItem
import com.kitsugi.animelist.ui.app.SimklProfileState
import com.kitsugi.animelist.ui.app.SocialState
import com.kitsugi.animelist.ui.app.UserFollowItem
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Yorum / inceleme / tartışma kartlarındaki profil tıklamaları için BAŞKA kullanıcıların
 * MAL, Kitsu ve Simkl profillerini uygulama içinde yükler.
 *
 * - MAL: Jikan-uyumlu `users/...` uçları ([JikanGateway] → cihaz üzeri MAL HTML
 *   ([com.kitsugi.animelist.data.remote.malweb.MalWebSource]) → miribyou → Tenrai).
 *   Tam profil + istatistik + favoriler + son güncellemeler + arkadaşlar + geçmiş.
 * - Kitsu: `users/{id}` + `stats` + `favorites` + `library-entries` (herkese açık okuma).
 * - Simkl: `users/{id}` genel profil (varsa); yoksa yorum kartındaki ad/avatar ile
 *   eksiksiz ama sade bir profil döner.
 *
 * Dönen durum nesneleri [com.kitsugi.animelist.ui.app.KitsugiProfileViewModel] içindeki
 * kendi-profil durumlarıyla birebir aynı tiptedir; böylece mevcut
 * `MalProfileContent` / `KitsuProfileContent` / `SimklProfileContent` ekranları
 * doğrudan yeniden kullanılabilir.
 */
object OtherUserProfileRepository {

    private const val KITSU_BASE = "https://kitsu.app/api/edge"
    private const val TIMEOUT_MS = 15_000L

    // ─────────────────────────────────────────────────────────────────────────
    // MAL
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchMalUserState(username: String): MalProfileState = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(username, "UTF-8")
        val base = "https://api.jikan.moe/v4/users/$encoded"

        var state = MalProfileState(
            isConnected = true,
            isLoading = true,
            name = username
        )

        // 1. Tam profil (istatistik + favoriler + son güncellemeler + hakkında)
        val fullBody = jikanBody("$base/full")
        if (fullBody != null) {
            runCatching {
                val data = JSONObject(fullBody).optJSONObject("data")
                if (data != null) state = applyMalFullProfile(state, data)
            }
        }

        // 2 + 3. Arkadaşlar ve geçmiş paralel çekilir; sonuçlar birleştirilip sırayla uygulanır.
        coroutineScope {
            val friendsJob = async { jikanBody("$base/friends") }
            val historyJob = async { jikanBody("$base/history") }

            val friendsBody = friendsJob.await()
            val historyBody = historyJob.await()

            if (friendsBody != null) {
                runCatching {
                    val arr = JSONObject(friendsBody).optJSONArray("data") ?: return@runCatching
                    val friends = (0 until arr.length()).mapNotNull { i ->
                        val userObj = arr.optJSONObject(i)?.optJSONObject("user") ?: return@mapNotNull null
                        val friendName = userObj.optNullableString("username") ?: return@mapNotNull null
                        UserFollowItem(
                            id = friendName.hashCode(),
                            name = friendName,
                            avatarUrl = userObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                ?: userObj.optNullableString("image_url")
                        )
                    }
                    if (friends.isNotEmpty()) {
                        state = state.copy(socialState = SocialState(followers = friends, following = friends))
                    }
                }
            }

            if (historyBody != null) {
                runCatching {
                    val arr = JSONObject(historyBody).optJSONArray("data") ?: return@runCatching
                    val items = (0 until minOf(arr.length(), 60)).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val entry = o.optJSONObject("entry") ?: return@mapNotNull null
                        val id = entry.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                        val isManga = entry.optString("type", "anime").equals("manga", ignoreCase = true)
                        MalActivityEntry(
                            malId = id,
                            isManga = isManga,
                            title = entry.optNullableString("name") ?: entry.optNullableString("title") ?: return@mapNotNull null,
                            imageUrl = malImageOf(entry).ifBlank { null },
                            status = if (isManga) "Reading" else "Watching",
                            progress = o.optInt("increment", 0).takeIf { it > 0 },
                            total = null,
                            score = 0,
                            dateIso = o.optNullableString("date")
                        )
                    }
                    if (items.isNotEmpty()) state = state.copy(history = items)
                }
            }
        }

        state.copy(isLoading = false)
    }

    private fun applyMalFullProfile(state: MalProfileState, data: JSONObject): MalProfileState {
        val statsObj = data.optJSONObject("statistics")
        val animeStats = statsObj?.optJSONObject("anime")?.let { a ->
            AniListStats(
                count = a.optInt("total_entries", 0),
                episodesWatched = a.optInt("episodes_watched", 0),
                minutesWatched = (a.optDouble("days_watched", 0.0) * 24.0 * 60.0).toInt(),
                meanScore = a.optDouble("mean_score", 0.0),
                watching = a.optInt("watching", 0),
                completed = a.optInt("completed", 0),
                planned = a.optInt("plan_to_watch", 0),
                paused = a.optInt("on_hold", 0),
                dropped = a.optInt("dropped", 0)
            )
        }
        val mangaStats = statsObj?.optJSONObject("manga")?.let { m ->
            AniListStats(
                count = m.optInt("total_entries", 0),
                episodesWatched = m.optInt("chapters_read", 0),
                minutesWatched = m.optInt("volumes_read", 0),
                meanScore = m.optDouble("mean_score", 0.0),
                watching = m.optInt("reading", 0),
                completed = m.optInt("completed", 0),
                planned = m.optInt("plan_to_read", 0),
                paused = m.optInt("on_hold", 0),
                dropped = m.optInt("dropped", 0)
            )
        }

        fun favList(key: String, nameKey: String): List<ProfileFavoriteItem> {
            val arr = data.optJSONObject("favorites")?.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                ProfileFavoriteItem(
                    id = id.toString(),
                    title = item.optNullableString(nameKey) ?: KitsugiStrings.get(R.string.isimsiz),
                    imageUrl = malImageOf(item)
                )
            }
        }

        fun updates(key: String, isManga: Boolean): List<MalActivityEntry> {
            val arr = data.optJSONObject("updates")?.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val entry = o.optJSONObject("entry") ?: return@mapNotNull null
                val id = entry.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                val progressKey = if (isManga) "chapters_read" else "episodes_seen"
                val totalKey = if (isManga) "chapters_total" else "episodes_total"
                MalActivityEntry(
                    malId = id,
                    isManga = isManga,
                    title = entry.optNullableString("title") ?: return@mapNotNull null,
                    imageUrl = malImageOf(entry).ifBlank { null },
                    status = o.optNullableString("status") ?: "",
                    progress = if (o.isNull(progressKey)) null else o.optInt(progressKey),
                    total = if (o.isNull(totalKey)) null else o.optInt(totalKey),
                    score = o.optInt("score", 0),
                    dateIso = o.optNullableString("date")
                )
            }
        }

        val aboutHtml = data.optNullableString("about")
        val aboutText = aboutHtml?.let {
            runCatching {
                org.jsoup.Jsoup.parse(it).wholeText().replace(Regex("\\n{3,}"), "\n\n").trim()
            }.getOrNull()
        }.orEmpty()

        val recent = (updates("anime", false) + updates("manga", true))
            .sortedByDescending { it.dateIso.orEmpty() }

        return state.copy(
            name = data.optNullableString("username")?.takeIf { it.isNotBlank() } ?: state.name,
            avatarUrl = state.avatarUrl
                ?: data.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url"),
            location = state.location.ifBlank { data.optNullableString("location").orEmpty() },
            joinedAt = state.joinedAt.ifBlank { data.optNullableString("joined").orEmpty() },
            gender = data.optNullableString("gender").orEmpty().ifBlank { state.gender },
            birthday = data.optNullableString("birthday").orEmpty().ifBlank { state.birthday },
            lastOnline = data.optNullableString("last_online").orEmpty().ifBlank { state.lastOnline },
            about = aboutText.ifBlank { state.about },
            animeStats = animeStats?.takeIf { s -> s.count > 0 } ?: state.animeStats ?: animeStats,
            mangaStats = mangaStats?.takeIf { s -> s.count > 0 } ?: state.mangaStats ?: mangaStats,
            favoriteAnime = favList("anime", "title").ifEmpty { state.favoriteAnime },
            favoriteManga = favList("manga", "title").ifEmpty { state.favoriteManga },
            favoriteCharacters = favList("characters", "name").ifEmpty { state.favoriteCharacters },
            favoriteStaff = favList("people", "name").ifEmpty { state.favoriteStaff },
            recentUpdates = recent.ifEmpty { state.recentUpdates }
        )
    }

    private fun malImageOf(item: JSONObject): String {
        val images = item.optJSONObject("images")
        return images?.optJSONObject("jpg")?.optNullableString("large_image_url")
            ?: images?.optJSONObject("jpg")?.optNullableString("image_url")
            ?: images?.optJSONObject("webp")?.optNullableString("image_url")
            ?: ""
    }

    private suspend fun jikanBody(url: String): String? {
        return runCatching {
            when (val r = JikanGateway.fetch(url, JikanGateway.Priority.BACKGROUND, maxRetries = 1)) {
                is JikanResult.Ok -> r.body
                else -> null
            }
        }.getOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kitsu
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchKitsuUserState(
        userId: Int,
        fallbackUsername: String? = null,
        fallbackAvatarUrl: String? = null
    ): KitsuProfileState = withContext(Dispatchers.IO) {
        if (userId <= 0) {
            return@withContext KitsuProfileState(
                isConnected = true,
                isLoading = false,
                error = KitsugiStrings.get(R.string.profile_load_failed),
                name = fallbackUsername.orEmpty(),
                avatarUrl = fallbackAvatarUrl
            )
        }
        val idStr = userId.toString()
        var state = KitsuProfileState(
            isConnected = true,
            isLoading = true,
            id = idStr,
            name = fallbackUsername.orEmpty(),
            avatarUrl = fallbackAvatarUrl
        )

        // 1. Kullanıcı nitelikleri (herkese açık)
        runCatching {
            val body = httpGet("$KITSU_BASE/users/$idStr") ?: return@runCatching
            val userObj = JSONObject(body).optJSONObject("data") ?: return@runCatching
            val attrs = userObj.optJSONObject("attributes") ?: return@runCatching
            val avatarObj = attrs.optJSONObject("avatar")
            val coverObj = attrs.optJSONObject("coverImage")
            state = state.copy(
                name = attrs.optNullableString("name")?.takeIf { it.isNotBlank() } ?: state.name,
                avatarUrl = avatarObj?.optNullableString("original")
                    ?: avatarObj?.optNullableString("large")
                    ?: avatarObj?.optNullableString("medium")
                    ?: state.avatarUrl,
                bannerUrl = coverObj?.optNullableString("original") ?: coverObj?.optNullableString("large"),
                about = attrs.optNullableString("about"),
                location = attrs.optNullableString("location"),
                gender = attrs.optNullableString("gender"),
                birthday = attrs.optNullableString("birthday"),
                joinedAt = attrs.optNullableString("createdAt"),
                waifuOrHusbando = attrs.optNullableString("waifuOrHusbando"),
                followersCount = attrs.optInt("followersCount", 0),
                followingCount = attrs.optInt("followingCount", 0),
                commentsCount = attrs.optInt("commentsCount", 0),
                reviewsCount = attrs.optInt("reviewsCount", 0)
            )
        }

        // 2 + 3 + 4. İstatistik, favoriler ve kütüphane paralel çekilir
        coroutineScope {
            val statsJob = async {
                runCatching { KitsuApiClient.fetchUserStats(idStr) }.getOrNull()
            }
            val favsJob = async {
                runCatching { KitsuApiClient.fetchUserFavorites(idStr) }.getOrNull()
            }
            val libraryJob = async {
                runCatching { fetchKitsuLibrarySummary(idStr) }.getOrNull()
            }

            val userStats = statsJob.await()
            val favorites = favsJob.await()
            val library = libraryJob.await()

            if (userStats != null) {
                val timeConsumedSeconds = userStats.timeConsumedSeconds
                val timeSpentStr = if (timeConsumedSeconds > 0) {
                    val days = timeConsumedSeconds / 86400
                    val hours = (timeConsumedSeconds % 86400) / 3600
                    if (days > 0) KitsugiStrings.get(R.string.profile_duration_days_hours, days, hours)
                    else KitsugiStrings.get(R.string.profile_duration_hours, hours)
                } else null
                state = state.copy(
                    timeSpentDaysHours = timeSpentStr,
                    episodesWatched = userStats.episodesWatched.takeIf { it > 0 }
                )
            }
            if (!favorites.isNullOrEmpty()) {
                state = state.copy(favorites = favorites)
            }
            if (library != null) {
                state = state.copy(
                    totalAnime = library.totalAnime,
                    totalManga = library.totalManga,
                    watching = library.watching,
                    completed = library.completed,
                    planned = library.planned,
                    paused = library.paused,
                    dropped = library.dropped,
                    avgScore = library.avgScore,
                    libraryEntries = library.entries
                )
            }
        }

        state.copy(isLoading = false)
    }

    private class KitsuLibrarySummary(
        val totalAnime: Int,
        val totalManga: Int,
        val watching: Int,
        val completed: Int,
        val planned: Int,
        val paused: Int,
        val dropped: Int,
        val avgScore: Double,
        val entries: List<ProfileFavoriteItem>
    )

    private suspend fun fetchKitsuLibrarySummary(userId: String): KitsuLibrarySummary? {
        val url = "$KITSU_BASE/users/$userId/library-entries" +
            "?include=anime,manga" +
            "&fields[libraryEntries]=status,ratingTwenty" +
            "&fields[anime]=canonicalTitle,posterImage" +
            "&fields[manga]=canonicalTitle,posterImage" +
            "&page[limit]=500&sort=-updatedAt"
        val body = httpGet(url) ?: return null

        val root = JSONObject(body)
        val data = root.optJSONArray("data") ?: return null
        val included = root.optJSONArray("included")

        val mediaById = mutableMapOf<String, Pair<String, String>>() // "type_id" -> (title, image)
        if (included != null) {
            for (i in 0 until included.length()) {
                val inc = included.optJSONObject(i) ?: continue
                val type = inc.optString("type", "")
                if (type != "anime" && type != "manga") continue
                val attrs = inc.optJSONObject("attributes") ?: continue
                val title = attrs.optNullableString("canonicalTitle") ?: continue
                val poster = attrs.optJSONObject("posterImage")
                val image = poster?.optNullableString("original")
                    ?: poster?.optNullableString("large")
                    ?: poster?.optNullableString("medium")
                    ?: ""
                mediaById["${type}_${inc.optString("id")}"] = title to image
            }
        }

        var totalAnime = 0
        var totalManga = 0
        var watching = 0
        var completed = 0
        var planned = 0
        var paused = 0
        var dropped = 0
        val scores = mutableListOf<Double>()
        val preview = mutableListOf<ProfileFavoriteItem>()

        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val attrs = item.optJSONObject("attributes") ?: continue
            val relAnime = item.optJSONObject("relationships")?.optJSONObject("anime")?.optJSONObject("data")
            val relManga = item.optJSONObject("relationships")?.optJSONObject("manga")?.optJSONObject("data")
            val isAnime = relAnime != null
            if (isAnime) totalAnime++ else totalManga++

            when (attrs.optString("status", "").lowercase()) {
                "current" -> watching++
                "completed" -> completed++
                "planned" -> planned++
                "on_hold" -> paused++
                "dropped" -> dropped++
                else -> planned++
            }

            val ratingTwenty = attrs.optInt("ratingTwenty", 0)
            if (ratingTwenty > 0) scores.add(ratingTwenty / 2.0)

            if (preview.size < 30) {
                val rel = relAnime ?: relManga
                if (rel != null) {
                    val type = if (isAnime) "anime" else "manga"
                    val mediaKey = "${type}_${rel.optString("id")}"
                    val (title, image) = mediaById[mediaKey] ?: ("" to "")
                    if (title.isNotBlank()) {
                        preview.add(ProfileFavoriteItem(id = rel.optString("id"), title = title, imageUrl = image))
                    }
                }
            }
        }

        return KitsuLibrarySummary(
            totalAnime = totalAnime,
            totalManga = totalManga,
            watching = watching,
            completed = completed,
            planned = planned,
            paused = paused,
            dropped = dropped,
            avgScore = if (scores.isNotEmpty()) scores.average() else 0.0,
            entries = preview
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Simkl
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simkl genel kullanıcı profili. Herkese açık uçlar sınırlı olduğundan önce
     * `users/{id}` denenir; erişilemezse yorum kartındaki ad/avatar ile eksiksiz
     * ama sade bir profil döner (ekran hiçbir zaman boş kalmaz).
     */
    suspend fun fetchSimklUserState(
        username: String?,
        avatarUrl: String?,
        userId: Int? = null
    ): SimklProfileState = withContext(Dispatchers.IO) {
        var state = SimklProfileState(
            isConnected = true,
            isLoading = true,
            name = username.orEmpty(),
            avatarUrl = avatarUrl
        )

        if (userId != null && userId > 0) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.simkl.com/users/$userId")
                    .addHeader("simkl-api-key", com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID)
                    .addHeader("User-Agent", "KitsugiApp/2.4")
                    .get()
                    .build()
                val body = KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                } ?: return@runCatching

                val root = JSONObject(body)
                val name = root.optNullableString("name")
                    ?: root.optNullableString("username")
                    ?: root.optNullableString("display_name")
                val bio = root.optNullableString("bio")
                    ?: root.optNullableString("about")
                val location = root.optNullableString("location")
                val joinedAt = root.optNullableString("joined_at")
                    ?: root.optNullableString("createdAt")
                val avatar = root.optNullableString("avatar")
                    ?: root.optJSONObject("avatar")?.optNullableString("medium")
                state = state.copy(
                    name = name?.takeIf { it.isNotBlank() } ?: state.name,
                    bio = bio?.takeIf { it.isNotBlank() },
                    location = location?.takeIf { it.isNotBlank() },
                    joinedAt = joinedAt?.takeIf { it.isNotBlank() },
                    avatarUrl = avatar?.takeIf { it.isNotBlank() } ?: state.avatarUrl
                )
            }
        }

        state.copy(isLoading = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ortak
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.api+json")
                .addHeader("User-Agent", "KitsugiApp/2.4")
                .get()
                .build()
            withTimeoutOrNull(TIMEOUT_MS) {
                KitsugiHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()
                }
            }
        }.getOrNull()
    }
}
