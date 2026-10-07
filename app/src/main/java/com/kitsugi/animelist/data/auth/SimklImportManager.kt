package com.kitsugi.animelist.data.auth

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.Request
import com.kitsugi.animelist.data.remote.optNullableString

object SimklImportManager {

    private val client = com.kitsugi.animelist.core.network.KitsugiHttpClient.client
    // T3-01: BuildConfig'den alınır
    private val CLIENT_ID get() = com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID

    data class SimklUserProfile(
        val name: String,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val joinedAt: String? = null,
        val location: String? = null,
        val bio: String? = null,
        val accountType: String? = null
    )

    suspend fun fetchUserProfile(token: String): SimklUserProfile {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.simkl.com/users/settings")
                .header("Authorization", "Bearer $token")
                .header("simkl-api-key", CLIENT_ID)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
                    if (context != null) {
                        com.kitsugi.animelist.data.auth.ExternalAuthManager.handleSimkl401(context)
                    }
                    throw com.kitsugi.animelist.data.repository.SimklAuthException("Simkl yetkilendirme hatası: 401")
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException("Simkl profil hatası: ${response.code}")
                }
                val responseText = response.body?.string().orEmpty()
                val root = JSONObject(responseText)
                val user = root.optJSONObject("user")
                val name = user?.optNullableString("name") ?: "Simkl Kullanıcısı"
                val avatar = user?.optNullableString("avatar")
                val avatarUrl = if (!avatar.isNullOrBlank()) "https://simkl.in/avatars/${avatar}_m.jpg" else null

                val joinedAt = user?.optNullableString("joined_at")
                val location = user?.optNullableString("location")
                val bio = user?.optNullableString("bio")
                val account = root.optJSONObject("account")
                val accountType = account?.optNullableString("type")

                SimklUserProfile(
                    name = name,
                    avatarUrl = avatarUrl,
                    bannerUrl = null,
                    joinedAt = joinedAt,
                    location = location,
                    bio = bio,
                    accountType = accountType
                )
            }
        }
    }

    suspend fun fetchAllLists(token: String): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            val movies = fetchList(token, "movies")
            kotlinx.coroutines.delay(1500L)
            val shows = fetchList(token, "shows")
            kotlinx.coroutines.delay(1500L)
            val anime = fetchList(token, "anime")
            movies + shows + anime
        }
    }

    private suspend fun fetchList(token: String, type: String): List<MediaEntry> {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            while (attempt < 4) {
                attempt++
                val request = Request.Builder()
                    .url("https://api.simkl.com/sync/all-items/$type")
                    .header("Authorization", "Bearer $token")
                    .header("simkl-api-key", CLIENT_ID)
                    .header("User-Agent", "KitsugiApp/2.4")
                    .build()

                val entries = mutableListOf<MediaEntry>()

                try {
                    val (retryWaitMs, parsedEntries) = client.newCall(request).execute().use { response ->
                        if (response.code == 401) {
                            val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext
                            if (context != null) {
                                com.kitsugi.animelist.data.auth.ExternalAuthManager.handleSimkl401(context)
                            }
                            throw com.kitsugi.animelist.data.repository.SimklAuthException("Simkl yetkilendirme hatası: 401")
                        }
                        if (response.code == 429) {
                            val retryHeader = response.header("Retry-After")?.toLongOrNull() ?: (2L * attempt)
                            val waitMs = maxOf(retryHeader * 1000L, 2500L * attempt)
                            android.util.Log.w("SimklImportManager", "Simkl fetchList [$type] 429 rate limit, waiting ${waitMs}ms and retrying (deneme $attempt/4)...")
                            return@use Pair(waitMs, emptyList<MediaEntry>())
                        }
                        if (!response.isSuccessful) {
                            android.util.Log.e("SimklImportManager", "Simkl fetchList [$type] failed: HTTP ${response.code}")
                            throw IllegalStateException("Simkl liste okuma hatası [$type]: HTTP ${response.code}")
                        }
                        val responseText = response.body?.string().orEmpty()
                        if (responseText.trim() == "null") return@use Pair(0L, emptyList<MediaEntry>())
                        val root = JSONObject(responseText)
                        val arrayKey = when (type) {
                            "movies" -> "movies"
                            "shows" -> "shows"
                            else -> "anime"
                        }
                        val jsonArray = root.optJSONArray(arrayKey) ?: return@use Pair(0L, emptyList<MediaEntry>())

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val statusStr = item.optNullableString("status")
                        val watchStatus = mapSimklStatus(statusStr)

                        val mediaObjKey = com.kitsugi.animelist.data.remote.SimklSyncContract.readMediaKey(type)
                        val mediaObj = item.optJSONObject(mediaObjKey)
                            ?: throw IllegalStateException("Simkl [$type]: $mediaObjKey nesnesi eksik")
                        val title = mediaObj.optNullableString("title") ?: "Başlıksız"
                        val year = mediaObj.optInt("year", 0).takeIf { it > 0 }
                        val poster = mediaObj.optNullableString("poster")
                        val imageUrl = if (!poster.isNullOrBlank()) "https://simkl.in/posters/${poster}_m.jpg" else null

                        val ids = mediaObj.optJSONObject("ids")
                        val simklId = ids?.optInt("simkl", 0)?.takeIf { it > 0 }
                        val tmdbId = ids?.optInt("tmdb", 0)?.takeIf { it > 0 }
                        val malId = ids?.optInt("mal", 0)?.takeIf { it > 0 }

                        val mediaType = when (type) {
                            "movies" -> MediaType.Movie
                            "shows" -> MediaType.TvShow
                            else -> MediaType.Anime
                        }

                        val progress = when (mediaType) {
                            MediaType.Movie -> if (watchStatus == WatchStatus.Completed) 1 else 0
                            else -> item.optInt("watched_episodes_count", 0)
                        }

                        val total = when (mediaType) {
                            MediaType.Movie -> 1
                            else -> item.optInt("total_episodes_count", 0).takeIf { it > 0 }
                        }

                        // Parse rating if present (Simkl user_rating or rating)
                        val score = item.optInt("user_rating", 0).takeIf { it > 0 }
                            ?: item.optInt("rating", 0).takeIf { it > 0 }

                        // malId SADECE ve SADECE Simkl API'sinin gerçekten 'mal' olarak döndüğü gerçek MAL ID'dir.
                        // Asla simklId veya uydurma ID malId yerine konulmaz! Aksi halde CrossSync rastgele MAL animeleriyle eşleştirir!
                        val realMalId = if (mediaType == MediaType.Anime && malId != null && malId > 0 && malId < 100_000_000) malId else null
                        val stableDbId = if (simklId != null) 150_000_000 + (simklId % 100_000_000) else (150_000_000 + i)

                        entries.add(
                            MediaEntry(
                                id = stableDbId,
                                title = title,
                                subtitle = when (mediaType) {
                                    MediaType.Movie -> "Film"
                                    MediaType.TvShow -> "Dizi"
                                    MediaType.Anime -> "Anime"
                                    else -> "Manga"
                                }.let { label ->
                                    if (year != null) "$label • $year" else label
                                },
                                type = mediaType,
                                status = watchStatus,
                                score = score,
                                progress = progress,
                                total = total,
                                isFavorite = false,
                                isAdult = false,
                                source = "simkl",
                                malId = realMalId,
                                imageUrl = imageUrl,
                                year = year,
                                synopsis = null,
                                tmdbId = tmdbId,
                                simklId = simklId
                            )
                        )
                    }
                    Pair(0L, entries)
                }
                if (retryWaitMs == 0L) {
                    return@withContext parsedEntries
                }
                kotlinx.coroutines.delay(retryWaitMs)
            } catch (e: com.kitsugi.animelist.data.repository.SimklAuthException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SimklImportManager", "fetchList [$type] failed: ${e.message}", e)
                if (attempt >= 4) throw e
                kotlinx.coroutines.delay(2000L * attempt)
            }
        }
        throw IllegalStateException("Simkl [$type]: hız sınırı nedeniyle liste alınamadı (4 deneme)")
    }
}

    private fun mapSimklStatus(status: String?): WatchStatus {
        return when (status?.lowercase()) {
            "watching", "reading", "rewatching" -> WatchStatus.Watching
            "completed" -> WatchStatus.Completed
            "hold", "on_hold", "paused" -> WatchStatus.Paused
            "dropped" -> WatchStatus.Dropped
            "plantowatch", "plan_to_watch", "planning" -> WatchStatus.Planned
            else -> WatchStatus.Planned
        }
    }
}
