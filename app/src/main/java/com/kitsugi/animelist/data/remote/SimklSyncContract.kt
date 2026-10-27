package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read categories and write envelopes are deliberately different in the Simkl API.
 *
 * Public (not internal) because [SimklApiClient.SimklBatchResponse] exposes [UnmatchedItem]
 * so callers can attribute `not_found` receipts to individual library records.
 */
object SimklSyncContract {
    fun writeKey(type: String): String = when (type.lowercase()) {
        "movie", "movies" -> "movies"
        "anime", "show", "shows", "tv" -> "shows"
        else -> error("Unsupported Simkl media type: $type")
    }

    fun readMediaKey(type: String): String = if (type == "movies") "movie" else "show"

    /**
     * One item Simkl echoed back under `not_found`. Simkl returns the identifiers (and title/year
     * when they were part of the request) of every item it could not match, so callers can map the
     * rejection back to the original library record instead of blaming the whole batch.
     */
    data class UnmatchedItem(
        val ids: Map<String, String> = emptyMap(),
        val title: String? = null,
        val year: Int? = null,
        val envelope: String = "shows"
    ) {
        fun describe(): String {
            val idText = ids.entries.joinToString(", ") { "${it.key}=${it.value}" }
            return buildString {
                append(title?.takeIf { it.isNotBlank() } ?: "(başlıksız)")
                if (year != null && year > 0) append(" ($year)")
                if (idText.isNotBlank()) append(" [").append(idText).append("]")
            }
        }
    }

    data class Receipt(
        val added: Int,
        val notFound: Int,
        val unmatched: List<UnmatchedItem> = emptyList()
    )

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
        val unmatched = keys.flatMap { key -> unmatchedItems(missing?.optJSONArray(key), key) }
        val result = Receipt(
            added = keys.sumOf { count(added, it) },
            notFound = keys.sumOf { count(missing, it) },
            unmatched = unmatched
        )
        require(result.added > 0 || result.notFound > 0) { "Simkl hiçbir öğeyi onaylamadı (boş işlem yanıtı)" }
        return result
    }

    private fun unmatchedItems(array: JSONArray?, envelope: String): List<UnmatchedItem> {
        if (array == null) return emptyList()
        val items = mutableListOf<UnmatchedItem>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index)
            if (obj == null) {
                items.add(UnmatchedItem(envelope = envelope))
                continue
            }
            val ids = linkedMapOf<String, String>()
            obj.optJSONObject("ids")?.let { idObj ->
                val names = idObj.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    val value = idObj.opt(name)?.toString()?.trim().orEmpty()
                    if (value.isNotBlank() && value != "null" && value != "0") ids[name] = value
                }
            }
            items.add(
                UnmatchedItem(
                    ids = ids,
                    title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    year = obj.optInt("year", 0).takeIf { it > 0 },
                    envelope = envelope
                )
            )
        }
        return items
    }

    /** Anime progress is an absolute, contiguous count, not a single watched episode. */
    fun animeEpisodes(progress: Int): JSONArray = JSONArray().apply {
        for (number in 1..progress) put(JSONObject().put("number", number))
    }

    /**
     * Simkl kategori yanıtlarını **tekilleştirerek** birleştirir.
     *
     * `GET /sync/all-items/{movies|shows|anime}` üç ayrı kategori döndürür ve aynı öğe birden
     * fazla kategoride yer alabilir (anime filmleri `movies` + `anime`; anime dizileri
     * `shows` + `anime`). Üç listeyi olduğu gibi toplamak aynı `simklId`'yi iki kez getiriyor,
     * `Movie`/`Anime` (veya `TvShow`/`Anime`) etiketli iki satır yazılıyor ve Listem'de aynı
     * yapım iki kart oluyordu. Aynı `simklId` (yoksa aynı başlık + yıl) tek kayda iner;
     * **anime** kategorisinden gelen kayıt tercih edilir (bölüm ilerlemesi ve çapraz eşitleme
     * anime ekseninde çalışır).
     *
     * @param lists sırayla movies, shows, anime listeleri (Simkl öncelik sırası: sonra gelen
     *        aynı yapımın daha zengin kaydıdır).
     */
    fun mergeCategories(vararg lists: List<MediaEntry>): List<MediaEntry> {
        val merged = LinkedHashMap<String, MediaEntry>()
        lists.forEach { list ->
            list.forEach { entry ->
                val key = categoryEntryKey(entry)
                val existing = merged[key]
                merged[key] = if (existing == null) entry else preferredCategoryRecord(existing, entry)
            }
        }
        return merged.values.toList()
    }

    private fun categoryEntryKey(entry: MediaEntry): String {
        val simklId = entry.simklId?.takeIf { it > 0 }
        if (simklId != null) return "simkl:$simklId"
        val tmdbId = entry.tmdbId?.takeIf { it > 0 }
        if (tmdbId != null) return "tmdb:$tmdbId"
        val title = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(entry.title)
        return "title:$title:${entry.year ?: 0}"
    }

    /** Aynı öğenin iki kategori kaydından hangisi tutulur. */
    private fun preferredCategoryRecord(current: MediaEntry, candidate: MediaEntry): MediaEntry {
        val currentRank = com.kitsugi.animelist.model.MediaIdentity.preferredTypeRank(current.type)
        val candidateRank = com.kitsugi.animelist.model.MediaIdentity.preferredTypeRank(candidate.type)
        val winner = if (candidateRank < currentRank) candidate else current
        val loser = if (winner === candidate) current else candidate
        return winner.copy(
            // Kullanıcı verisi hiçbir yönde kaybolmaz.
            progress = maxOf(winner.progress, loser.progress),
            score = winner.score ?: loser.score,
            imageUrl = winner.imageUrl?.takeIf { it.isNotBlank() } ?: loser.imageUrl,
            year = winner.year ?: loser.year,
            total = winner.total ?: loser.total,
            titleEnglish = winner.titleEnglish?.takeIf { it.isNotBlank() } ?: loser.titleEnglish,
            titleJapanese = winner.titleJapanese?.takeIf { it.isNotBlank() } ?: loser.titleJapanese,
            tmdbId = winner.tmdbId ?: loser.tmdbId,
            simklId = winner.simklId ?: loser.simklId
        )
    }
}
