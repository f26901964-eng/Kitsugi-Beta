package com.kitsugi.animelist.data.remote

import org.json.JSONArray
import org.json.JSONObject

/** Read categories and write envelopes are deliberately different in the Simkl API. */
internal object SimklSyncContract {
    fun writeKey(type: String): String = when (type.lowercase()) {
        "movie", "movies" -> "movies"
        "anime", "show", "shows", "tv" -> "shows"
        else -> error("Unsupported Simkl media type: $type")
    }

    fun readMediaKey(type: String): String = if (type == "movies") "movie" else "show"

    data class Receipt(val added: Int, val notFound: Int)

    /** HTTP 200 is not proof of a mutation. Reject empty, malformed and no-op receipts. */
    fun receipt(body: String): Receipt {
        val root = JSONObject(body)
        val added = root.getJSONObject("added")
        val missing = root.optJSONObject("not_found")
        fun count(obj: JSONObject?, key: String): Int = when (val value = obj?.opt(key)) {
            is JSONArray -> value.length()
            is Number -> value.toInt().coerceAtLeast(0)
            else -> 0
        }
        val keys = listOf("movies", "shows", "episodes")
        val result = Receipt(keys.sumOf { count(added, it) }, keys.sumOf { count(missing, it) })
        require(result.added > 0 || result.notFound > 0) { "Simkl hiçbir öğeyi onaylamadı (boş işlem yanıtı)" }
        return result
    }

    /** Anime progress is an absolute, contiguous count, not a single watched episode. */
    fun animeEpisodes(progress: Int): JSONArray = JSONArray().apply {
        for (number in 1..progress) put(JSONObject().put("number", number))
    }
}
