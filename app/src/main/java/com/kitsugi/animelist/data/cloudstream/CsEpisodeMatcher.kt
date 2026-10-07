package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import java.text.Normalizer
import java.util.Locale

/**
 * Bölüm verisi çıkarma yardımcıları.
 * [CsStreamRunner] içindeki episode matching mantığı buraya taşındı.
 */
internal object CsEpisodeMatcher {

    private const val TAG = "CsEpisodeMatcher"

    /** Exact, boundary-aware recognition of common English/Turkish/Japanese/Chinese episode labels. */
    fun episodeNameMatchesTarget(name: String, season: Int, episode: Int): Boolean {
        if (season < 1 || episode < 1) return false
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('ı', 'i')
        val s = season.toString()
        val e = episode.toString()
        val seasonEpisodePatterns = listOf(
            Regex("""(?<![\p{L}\p{N}])s0*${s}[\s._-]*e0*${e}(?![0-9])"""),
            Regex("""(?<![0-9])0*${s}\s*x\s*0*${e}(?![0-9])"""),
            Regex("""(?<![\p{L}\p{N}])(?:season|sezon)\s*0*${s}[^0-9]{0,16}(?:episode|ep|bölüm|bolum)\s*0*${e}(?![0-9])"""),
            Regex("""(?<![0-9])0*${s}\s*[.º°]?\s*(?:sezon|season)\b.{0,24}?(?<![0-9])0*${e}\s*[.º°]?\s*(?:episode|ep|bölüm|bolum)\b"""),
            Regex("""第0*${s}\s*(?:期|季)[\s,·-]*第0*${e}\s*(?:話|话|集)""")
        )
        if (seasonEpisodePatterns.any { it.containsMatchIn(normalized) }) return true
        if (season != 1) return false

        val seasonOneEpisodePatterns = listOf(
            Regex("""(?<![\p{L}\p{N}])(?:episode|ep|bölüm|bolum)\s*\.?\s*0*${e}(?![0-9])"""),
            Regex("""(?<![0-9])0*${e}\s*[.º°]?\s*(?:episode|ep|bölüm|bolum)\b"""),
            Regex("""第0*${e}\s*(?:話|话|集)""")
        )
        return seasonOneEpisodePatterns.any { it.containsMatchIn(normalized) }
    }

    /**
     * Verilen [LoadResponse] içinde belirtilen sezon ve bölüme karşılık gelen
     * bölüm verisini (episode data string) döndürür.
     * Bulunamazsa `null` döner.
     */
    fun findEpisodeData(response: LoadResponse, season: Int, episode: Int): String? {
        return try {
            when (response) {
                is AnimeLoadResponse -> findInAnimeResponse(response, season, episode)
                is TvSeriesLoadResponse -> findInTvSeriesResponse(response, season, episode)
                is MovieLoadResponse -> {
                    Log.d(TAG, "MovieLoadResponse: dataUrl=${response.dataUrl}")
                    if (response.dataUrl.isNullOrBlank()) response.url else response.dataUrl
                }
                else -> {
                    Log.w(TAG, "Bilinmeyen LoadResponse tipi: ${response.javaClass.name}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "findEpisodeData HATA", e)
            null
        }
    }

    /**
     * Seçilen bölümün hangi [com.lagradost.cloudstream3.DubStatus] kovasından geldiğini döndürür.
     *
     * Dönüş: "dub" | "sub" | null (bilinmiyor).
     *
     * Bu, dil rozetinin **kaynağın kendi meta verisine** dayanmasını sağlar; dosya adından
     * tahmin yürütmek yerine sağlayıcının gerçekte ne söylediğini kullanırız.
     */
    fun findDubStatusForEpisodeData(response: LoadResponse, episodeData: String?): String? {
        if (episodeData.isNullOrBlank()) return null
        val anime = response as? AnimeLoadResponse ?: return null
        return try {
            var result: String? = null
            for ((status, episodes) in anime.episodes) {
                val matches = episodes.any { ep -> getEpisodeData(ep) == episodeData }
                if (matches) {
                    result = when (status) {
                        com.lagradost.cloudstream3.DubStatus.Dubbed -> "dub"
                        com.lagradost.cloudstream3.DubStatus.Subbed -> "sub"
                        else -> null
                    }
                    break
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "findDubStatusForEpisodeData HATA: ${e.message}")
            null
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun findInAnimeResponse(response: AnimeLoadResponse, season: Int, episode: Int): String? {
        val allEntries = response.episodes.entries
        Log.d(TAG, "AnimeLoadResponse: ${allEntries.size} sezon bucket(ı)")
        allEntries.forEach { (dubStatus, eps) ->
            Log.d(TAG, "  DubStatus=$dubStatus bölüm sayısı=${eps.size}")
        }

        val responseName = response.name ?: ""
        val responseUrl = response.url ?: ""
        val foundSeason = CsTitleMatcher.parseSeasonFromTitle(responseName)
            ?: CsTitleMatcher.parseSeasonFromSlug(responseUrl)

        val allEpisodesList = response.episodes.values.flatten()
        val rawSeasonsAnime = allEpisodesList.mapNotNull { getEpisodeSeason(it) }.toSet()
        val hasMultipleRawSeasonsAnime = rawSeasonsAnime.size > 1
        val treatSeason1AsTarget = !hasMultipleRawSeasonsAnime && !rawSeasonsAnime.contains(season) && foundSeason != null && foundSeason == season

        val getEffectiveSeason = { ep: Any ->
            val epSeason = getEpisodeSeason(ep)
            if (treatSeason1AsTarget && (epSeason == null || epSeason == 1)) {
                season
            } else {
                epSeason ?: 1
            }
        }

        // Separate Dub and Sub, prioritize Subbed, then Dubbed, then None
        val subEpisodes = response.episodes[com.lagradost.cloudstream3.DubStatus.Subbed] ?: emptyList()
        val dubEpisodes = response.episodes[com.lagradost.cloudstream3.DubStatus.Dubbed] ?: emptyList()
        val noneEpisodes = response.episodes[com.lagradost.cloudstream3.DubStatus.None] ?: emptyList()

        val preferredEpisodes = when {
            subEpisodes.isNotEmpty() -> subEpisodes
            dubEpisodes.isNotEmpty() -> dubEpisodes
            else -> noneEpisodes
        }

        // Flatten all episodes once
        val allEpisodes = allEntries.flatMap { it.value }

        // Try: season+episode exact match on preferred bucket
        var match = preferredEpisodes.find { ep ->
            val epNum = getEpisodeNumber(ep) ?: return@find false
            val epSeason = getEffectiveSeason(ep)
            epSeason == season && epNum == episode
        }

        // Fallback 1: search across ALL episode buckets
        if (match == null) {
            match = allEpisodes.find { ep ->
                val epNum = getEpisodeNumber(ep) ?: return@find false
                val epSeason = getEffectiveSeason(ep)
                epSeason == season && epNum == episode
            }
        }

        // Fallback 1b: search by name matching the target season and episode (e.g. "S2E5", "2. Sezon 5. Bölüm")
        if (match == null) {
            match = allEpisodes.find { ep ->
                val epName = getEpisodeName(ep) ?: return@find false
                episodeNameMatchesTarget(epName, season, episode)
            }
            if (match != null) Log.d(TAG, "Anime: Name-based season match: '${getEpisodeName(match)}' for S${season}E${episode}")
        }

        // Fallback 2: match by episode number only (season-agnostic) within preferred bucket
        if (match == null && (season == 1 || treatSeason1AsTarget)) {
            match = preferredEpisodes.find { ep ->
                (getEpisodeNumber(ep) ?: -1) == episode
            }
            if (match != null) Log.d(TAG, "Preferred bucket sezon-bağımsız eşleşme: ep=$episode")
        }

        // Fallback 3: match by episode number across all buckets
        if (match == null && (season == 1 || treatSeason1AsTarget)) {
            match = allEpisodes.find { ep ->
                (getEpisodeNumber(ep) ?: -1) == episode
            }
            if (match != null) Log.d(TAG, "Sezon bağımsız bölüm eşleşmesi kullanıldı: ep=$episode")
        }

        // Fallback 4: none of the episodes have season/episode fields set at all
        if (match == null && preferredEpisodes.isNotEmpty()) {
            val allHaveNoMeta = preferredEpisodes.all { ep ->
                getEpisodeNumber(ep) == null && getEpisodeSeason(ep) == null
            }
            if (allHaveNoMeta) {
                val idx = episode - 1  // episode is 1-based
                if (idx in preferredEpisodes.indices) {
                    match = preferredEpisodes[idx]
                    Log.d(TAG, "İndeks-bazlı fallback kullanıldı (meta yok): bucket[${idx}] → ep=$episode")
                }
            }
        }

        // Fallback 5: if there's exactly 1 episode and we want ep 1
        if (match == null && episode == 1 && allEpisodes.size == 1) {
            match = allEpisodes.first()
            Log.d(TAG, "Tek bölüm fallback kullanıldı")
        }

        // Fallback 6: season matched, but exact episode number match failed (e.g. continuous numbering on episode field like ep=11 for S2E1).
        // Use the relative index within the matched season's episodes in preferred bucket.
        if (match == null && preferredEpisodes.isNotEmpty()) {
            val sameSeasonEps = preferredEpisodes.filter { ep ->
                val rawSeason = getEpisodeSeason(ep)
                if (treatSeason1AsTarget) {
                    rawSeason == null || rawSeason == 1
                } else {
                    (rawSeason ?: 1) == season
                }
            }
            if (sameSeasonEps.isNotEmpty()) {
                val sortedEps = sameSeasonEps.sortedBy { getEpisodeNumber(it) ?: 0 }
                val idx = episode - 1
                if (idx in sortedEps.indices) {
                    match = sortedEps[idx]
                    Log.d(TAG, "Anime (preferred): season-index fallback (continuous/mismatch): S${season}[${idx}] → ep=$episode (actual epNum=${getEpisodeNumber(match)})")
                }
            }
        }

        // Fallback 7: season matched, but exact episode number match failed (e.g. continuous numbering on episode field like ep=11 for S2E1).
        // Use the relative index within the matched season's episodes across all buckets.
        if (match == null && allEpisodes.isNotEmpty()) {
            val sameSeasonEps = allEpisodes.filter { ep ->
                val rawSeason = getEpisodeSeason(ep)
                if (treatSeason1AsTarget) {
                    rawSeason == null || rawSeason == 1
                } else {
                    (rawSeason ?: 1) == season
                }
            }
            if (sameSeasonEps.isNotEmpty()) {
                val sortedEps = sameSeasonEps.sortedBy { getEpisodeNumber(it) ?: 0 }
                val idx = episode - 1
                if (idx in sortedEps.indices) {
                    match = sortedEps[idx]
                    Log.d(TAG, "Anime (all): season-index fallback (continuous/mismatch): S${season}[${idx}] → ep=$episode (actual epNum=${getEpisodeNumber(match)})")
                }
            }
        }

        return match?.let { getEpisodeData(it) }
    }

    private fun findInTvSeriesResponse(response: TvSeriesLoadResponse, season: Int, episode: Int): String? {
        Log.d(TAG, "TvSeriesLoadResponse: ${response.episodes.size} bölüm")

        val responseName = response.name ?: ""
        val responseUrl = response.url ?: ""
        val foundSeason = CsTitleMatcher.parseSeasonFromTitle(responseName)
            ?: CsTitleMatcher.parseSeasonFromSlug(responseUrl)

        val rawSeasons = response.episodes.mapNotNull { getEpisodeSeason(it) }.toSet()
        val hasMultipleRawSeasons = rawSeasons.size > 1
        val treatSeason1AsTarget = !hasMultipleRawSeasons && !rawSeasons.contains(season) && foundSeason != null && foundSeason == season

        val getEffectiveSeason = { ep: Any ->
            val epSeason = getEpisodeSeason(ep)
            if (treatSeason1AsTarget && (epSeason == null || epSeason == 1)) {
                season
            } else {
                epSeason ?: 1
            }
        }

        var match = response.episodes.find { ep ->
            val epNum = getEpisodeNumber(ep) ?: return@find false
            val epSeason = getEffectiveSeason(ep)
            epSeason == season && epNum == episode
        }

        // Fallback 1: name-based match (e.g. "S2E5", "2. Sezon 5. Bölüm") — before index fallbacks
        if (match == null) {
            match = response.episodes.find { ep ->
                val epName = getEpisodeName(ep) ?: return@find false
                episodeNameMatchesTarget(epName, season, episode)
            }
            if (match != null) Log.d(TAG, "TvSeries: Name-based season match: '${getEpisodeName(match)}' for S${season}E${episode}")
        }

        // Fallback 2: episode number only (season-agnostic)
        if (match == null && (season == 1 || treatSeason1AsTarget)) {
            match = response.episodes.find { ep ->
                (getEpisodeNumber(ep) ?: -1) == episode
            }
            if (match != null) Log.d(TAG, "TvSeries: sezon bağımsız fallback ep=$episode")
        }

        // Fallback 3: single episode → always return it for ep=1
        if (match == null && episode == 1 && response.episodes.size == 1) {
            match = response.episodes.first()
            Log.d(TAG, "TvSeries: tek bölüm fallback kullanıldı")
        }

        // Fallback 4: index-based — used when ALL episodes have no metadata at all (flat list, no season/ep fields)
        if (match == null && response.episodes.isNotEmpty()) {
            val allHaveNoMeta = response.episodes.all { ep ->
                getEpisodeNumber(ep) == null && getEpisodeSeason(ep) == null
            }
            if (allHaveNoMeta) {
                val idx = episode - 1  // episode is 1-based
                if (idx in response.episodes.indices) {
                    match = response.episodes[idx]
                    Log.d(TAG, "TvSeries: indeks-bazlı fallback kullanıldı (meta yok): [$idx] → ep=$episode")
                }
            }
        }

        // Fallback 5: season matched but episode field is null/missing — use positional index within that season
        // (DiziBox style: episodes have season set, but episode number is always null)
        // IMPORTANT: Use raw getEpisodeSeason() here — NOT getEffectiveSeason() — to avoid remapping
        // season=1 episodes into the target season when treatSeason1AsTarget is active.
        if (match == null && response.episodes.isNotEmpty()) {
            // Partition by raw season: episodes that explicitly belong to `season`, or
            // (when treatSeason1AsTarget) episodes whose raw season is 1 (the site's internal representation).
            val sameSeasonEps = response.episodes.filter { ep ->
                val rawSeason = getEpisodeSeason(ep)
                if (treatSeason1AsTarget) {
                    rawSeason == null || rawSeason == 1
                } else {
                    (rawSeason ?: 1) == season
                }
            }
            if (sameSeasonEps.isNotEmpty()) {
                val allEpNull = sameSeasonEps.all { getEpisodeNumber(it) == null }
                if (allEpNull) {
                    val idx = episode - 1
                    if (idx in sameSeasonEps.indices) {
                        match = sameSeasonEps[idx]
                        Log.d(TAG, "TvSeries: season-based index fallback (episode null): S${season}[${idx}] → ep=$episode")
                    }
                }
            }
        }

        // Fallback 6: season matched, but exact episode number match failed (e.g. continuous numbering on episode field like ep=11 for S2E1).
        // Use the relative index within the matched season's episodes.
        if (match == null && response.episodes.isNotEmpty()) {
            val sameSeasonEps = response.episodes.filter { ep ->
                val rawSeason = getEpisodeSeason(ep)
                if (treatSeason1AsTarget) {
                    rawSeason == null || rawSeason == 1
                } else {
                    (rawSeason ?: 1) == season
                }
            }
            if (sameSeasonEps.isNotEmpty()) {
                val sortedEps = sameSeasonEps.sortedBy { getEpisodeNumber(it) ?: 0 }
                val idx = episode - 1
                if (idx in sortedEps.indices) {
                    match = sortedEps[idx]
                    Log.d(TAG, "TvSeries: season-index fallback (continuous/mismatch): S${season}[${idx}] → ep=$episode (actual epNum=${getEpisodeNumber(match)})")
                }
            }
        }

        return match?.let { getEpisodeData(it) }
    }

    // ─── Reflection helpers ──────────────────────────────────────────────────

    fun getEpisodeNumber(ep: Any): Int? {
        return try { getField(ep, "episode") }
        catch (_: Exception) { null }
    }

    fun getEpisodeSeason(ep: Any): Int? {
        return try { getField(ep, "season") }
        catch (_: Exception) { null }
    }

    fun getEpisodeName(ep: Any): String? {
        return try {
            val field = ep.javaClass.getDeclaredField("name")
            field.isAccessible = true
            field.get(ep) as? String
        } catch (_: Exception) {
            try {
                ep.javaClass.getMethod("getName").invoke(ep) as? String
            } catch (_: Exception) { null }
        }
    }

    fun getEpisodeData(ep: Any): String? {
        return try {
            val field = ep.javaClass.getDeclaredField("data")
            field.isAccessible = true
            field.get(ep) as? String
        } catch (_: Exception) {
            try {
                ep.javaClass.getMethod("getData").invoke(ep) as? String
            } catch (_: Exception) { null }
        }
    }

    private fun getField(obj: Any, name: String): Int? {
        // Walk the class hierarchy to find the field
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                val value = field.get(obj)
                return when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull()
                    else -> value?.toString()?.toIntOrNull()
                }
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (_: Exception) {
                break
            }
        }
        // Fallback: try getter method
        return try {
            val getter = obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
            val value = getter.invoke(obj)
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> value?.toString()?.toIntOrNull()
            }
        } catch (_: Exception) { null }
    }
}
