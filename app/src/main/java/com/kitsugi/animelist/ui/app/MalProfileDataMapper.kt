package com.kitsugi.animelist.ui.app

import com.kitsugi.animelist.data.remote.optNullableString
import org.json.JSONArray
import org.json.JSONObject

/** Parsed portions of MAL's anime/manga statistics pages, normalized from Jikan-shaped JSON. */
internal data class MalDetailedStatistics(
    val scores: List<ScoreStatItem> = emptyList(),
    val types: List<FormatStatItem> = emptyList(),
    val statuses: List<StatusStatItem> = emptyList(),
    val ratings: List<FormatStatItem> = emptyList(),
    val genres: List<RankedStatItem> = emptyList(),
    val studios: List<RankedStatItem> = emptyList(),
    val voiceActors: List<RankedStatItem> = emptyList()
)

internal data class MalPagedItems<T>(
    val items: List<T>,
    val hasNextPage: Boolean
)

/**
 * Adapts MAL's Jikan-compatible profile endpoints into the presentation models shared by
 * the signed-in and public-user MAL profile screens.
 */
internal object MalProfileDataMapper {
    private fun payload(body: String): Pair<JSONObject, JSONObject> {
        val root = JSONObject(body)
        return root to (root.optJSONObject("data") ?: root)
    }

    private fun imageOf(item: JSONObject): String? {
        val images = item.optJSONObject("images")
        val jpg = images?.optJSONObject("jpg")
        val webp = images?.optJSONObject("webp")
        return jpg?.optNullableString("large_image_url")
            ?: jpg?.optNullableString("image_url")
            ?: webp?.optNullableString("large_image_url")
            ?: webp?.optNullableString("image_url")
    }

    private fun JSONArray?.objects(): List<JSONObject> = this?.let { array ->
        (0 until array.length()).mapNotNull(array::optJSONObject)
    }.orEmpty()

    private fun paginationHasNext(root: JSONObject, fallbackCount: Int): Boolean {
        val pagination = root.optJSONObject("pagination") ?: return fallbackCount >= 10
        if (pagination.has("has_next_page")) return pagination.optBoolean("has_next_page")
        if (pagination.has("hasNextPage")) return pagination.optBoolean("hasNextPage")
        return pagination.optInt("last_visible_page", 1) > pagination.optInt("current_page", 1)
    }

    fun parseDetailedStatistics(body: String): MalDetailedStatistics {
        val (_, data) = payload(body)

        fun array(vararg keys: String): List<JSONObject> =
            keys.firstNotNullOfOrNull { data.optJSONArray(it)?.objects() }.orEmpty()

        val scores = array("scores").mapNotNull { item ->
            val score = item.optInt("score", 0).takeIf { it in 1..10 } ?: return@mapNotNull null
            val count = item.optInt("count", item.optInt("votes", 0)).coerceAtLeast(0)
            ScoreStatItem(score = score, count = count)
        }.sortedBy { it.score }

        val types = array("types").mapNotNull { item ->
            val label = item.optNullableString("type")?.takeIf { it.isNotBlank() }
                ?: item.optNullableString("name")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            FormatStatItem(format = label, count = item.optInt("count", 0).coerceAtLeast(0))
        }.sortedByDescending { it.count }

        val statuses = array("statuses", "status").mapNotNull { item ->
            val label = item.optNullableString("status")?.takeIf { it.isNotBlank() }
                ?: item.optNullableString("name")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            StatusStatItem(status = label, count = item.optInt("count", 0).coerceAtLeast(0))
        }.sortedByDescending { it.count }

        val ratings = array("ratings", "rating").mapNotNull { item ->
            val label = item.optNullableString("rating")?.takeIf { it.isNotBlank() }
                ?: item.optNullableString("name")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            FormatStatItem(format = label, count = item.optInt("count", 0).coerceAtLeast(0))
        }.sortedByDescending { it.count }

        fun ranked(key: String): List<RankedStatItem> = array(key).mapNotNull { item ->
            val name = item.optNullableString("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val count = item.optInt("count", 0).coerceAtLeast(0)
            RankedStatItem(
                id = item.optInt("mal_id", 0).takeIf { it > 0 },
                name = name,
                count = count,
                meanScore = item.optDouble("mean_score", 0.0),
                imageUrl = imageOf(item)
            )
        }.sortedByDescending { it.count }

        return MalDetailedStatistics(
            scores = scores,
            types = types,
            statuses = statuses,
            ratings = ratings,
            genres = ranked("genres"),
            studios = ranked("studios"),
            voiceActors = ranked("voice_actors")
        )
    }

    fun parseReviews(body: String): MalPagedItems<MalReview> {
        val (root, data) = payload(body)
        val items = data.optJSONArray("data").objects().mapNotNull { item ->
            val entry = item.optJSONObject("entry") ?: return@mapNotNull null
            val malId = entry.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val kind = entry.optNullableString("type")?.lowercase()?.takeIf { it == "manga" } ?: "anime"
            val title = entry.optNullableString("title") ?: entry.optNullableString("name") ?: return@mapNotNull null
            val reviewId = item.optInt("mal_id", 0).takeIf { it > 0 } ?: malId
            MalReview(
                reviewId = reviewId,
                malId = malId,
                kind = kind,
                title = title,
                url = entry.optNullableString("url") ?: "https://myanimelist.net/$kind/$malId",
                imageUrl = imageOf(entry),
                score = item.optInt("score", 0).coerceIn(0, 10),
                helpfulCount = item.optInt("helpful_count", item.optInt("helpful", 0)).coerceAtLeast(0),
                dateIso = item.optNullableString("date"),
                textPreview = item.optNullableString("review")
                    ?: item.optNullableString("content")
                    ?: ""
            )
        }
        return MalPagedItems(items, paginationHasNext(root, items.size))
    }

    fun parseRecommendations(body: String): MalPagedItems<MalRecommendation> {
        val (root, data) = payload(body)
        val items = data.optJSONArray("data").objects().mapNotNull { item ->
            val entry = item.optJSONObject("entry") ?: return@mapNotNull null
            val malId = entry.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val kind = entry.optNullableString("type")?.lowercase()?.takeIf { it == "manga" } ?: "anime"
            val title = entry.optNullableString("title") ?: entry.optNullableString("name") ?: return@mapNotNull null
            val recommendationText = item.optNullableString("recommendation")
                ?: item.optNullableString("content")
                ?: ""
            MalRecommendation(
                recommendationId = item.optInt("mal_id", malId).takeIf { it > 0 } ?: malId,
                malId = malId,
                kind = kind,
                title = title,
                url = entry.optNullableString("url") ?: "https://myanimelist.net/$kind/$malId",
                imageUrl = imageOf(entry),
                recommendationText = recommendationText,
                dateIso = item.optNullableString("date")
            )
        }
        return MalPagedItems(items, paginationHasNext(root, items.size))
    }

    fun parseClubs(body: String): List<MalClub> = parseClubsPage(body).items

    fun parseClubsPage(body: String): MalPagedItems<MalClub> {
        val (root, data) = payload(body)
        val items = data.optJSONArray("data").objects().mapNotNull { item ->
            val id = item.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val name = item.optNullableString("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MalClub(
                clubId = id,
                name = name,
                url = item.optNullableString("url") ?: "https://myanimelist.net/clubs.php?cid=$id",
                imageUrl = imageOf(item),
                memberCount = item.optInt("members", item.optInt("member_count", 0)).coerceAtLeast(0),
                category = item.optNullableString("category")
            )
        }
        return MalPagedItems(items, paginationHasNext(root, items.size))
    }

    fun parseExternalLinks(data: JSONObject): List<MalExternalLink> =
        data.optJSONArray("external").objects().mapNotNull { item ->
            val name = item.optNullableString("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = item.optNullableString("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: return@mapNotNull null
            MalExternalLink(name, url)
        }
}
