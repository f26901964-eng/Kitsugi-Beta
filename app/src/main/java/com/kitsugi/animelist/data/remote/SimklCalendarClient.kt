package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Simkl CDN yayın takvimi istemcisi.
 *
 * Resmî doküman: https://api.simkl.org/api-reference/calendar
 *
 * ── Neden bu istemci var? ────────────────────────────────────────────────────
 * `SimklApiClient.getCalendar()` eski (deprecated) `data.simkl.in/calendar/{type}.json`
 * dosyalarını okuyor ve sonucu **ilk 50 öğeyle** sınırlıyordu. Simkl resmî
 * dokümanına göre:
 *   • Eski (v2 olmayan) dosyalar 1 Şubat 2027'de güncellenmeyi bırakacak.
 *   • Yeni dosyalar: https://data.simkl.in/calendar/v2/{tv|anime|movie_release}.json
 *   • Yeni yanıt şekli: { "calendar": [ ... ], "metadata": { "<simkl_id>": { ... } } }
 *   • `calendar` yayın listesi, `metadata` başlık kütüphanesidir; join anahtarı `simkl_id`.
 *   • metadata anahtarları **string**'dir (Kotlin'de metadata[simklId.toString()]).
 *   • v2 timestamp'leri UTC'ye normalize eder ("2026-07-20T04:00:00Z").
 *
 * Bu sınıf ayrıca kullanıcının Simkl "watching" listesinin ID'lerini çeker; böylece
 * bildirim akışı yerel veritabanı eşleşmesine mahkûm kalmaz.
 */
class SimklCalendarClient {

    // ─────────────────────────────────────────────────────────────────────────
    // Modeller
    // ─────────────────────────────────────────────────────────────────────────

    data class SimklCalendarEntry(
        val simklId: Int,
        val tmdbId: Int?,
        val malId: Int?,
        val title: String,
        val posterUrl: String?,
        val airingAtMs: Long,
        val season: Int?,
        val episode: Int?,
        val episodeTitle: String?,
        val finaleType: Int?,
        val isMovie: Boolean
    ) {
        /** "S2B5" / "5. Bölüm" biçiminde kısa bölüm etiketi. */
        val episodeLabel: String?
            get() = when {
                isMovie -> null
                season != null && season > 0 -> "S${season}B${episode ?: 0}"
                episode != null -> "${episode}. Bölüm"
                else -> null
            }

        val isFinale: Boolean get() = finaleType != null && finaleType > 0
    }

    /** Simkl kullanıcısının "watching" listesindeki ID'ler (farklı sağlayıcı anahtarları). */
    data class WatchlistIds(
        val simkl: Set<Int> = emptySet(),
        val tmdb: Set<Int> = emptySet(),
        val mal: Set<Int> = emptySet()
    ) {
        val isEmpty: Boolean get() = simkl.isEmpty() && tmdb.isEmpty() && mal.isEmpty()

        fun matches(entry: SimklCalendarEntry): Boolean =
            simkl.contains(entry.simklId) ||
                (entry.tmdbId != null && tmdb.contains(entry.tmdbId)) ||
                (entry.malId != null && mal.contains(entry.malId))
    }

    enum class Catalog(val path: String) {
        TV("tv"),
        ANIME("anime"),
        MOVIE_RELEASE("movie_release")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Takvim okuma
    // ─────────────────────────────────────────────────────────────────────────

    /** Tek bir katalog dosyasını (v2, gerekirse eski şema ile) okur. */
    suspend fun fetchCatalog(catalog: Catalog): List<SimklCalendarEntry> = withContext(Dispatchers.IO) {
        val v2 = runCatching { parseV2(download(calendarUrl(catalog, v2 = true)) ?: return@runCatching emptyList()) }
            .getOrDefault(emptyList())
        if (v2.isNotEmpty()) return@withContext v2

        // v2 dosyası okunamazsa (geçici CDN sorunu vb.) eski şemaya düş.
        runCatching { parseLegacyArray(download(calendarUrl(catalog, v2 = false)) ?: return@runCatching emptyList()) }
            .getOrDefault(emptyList())
    }

    /** Üç katalogu (TV + anime + film) birleştirip tarihe göre sıralı döndürür. */
    suspend fun fetchAll(): List<SimklCalendarEntry> = withContext(Dispatchers.IO) {
        val all = mutableListOf<SimklCalendarEntry>()
        Catalog.entries.forEach { catalog ->
            all.addAll(fetchCatalog(catalog))
        }
        all.sortedBy { it.airingAtMs }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kullanıcı watchlist'i ("watching" durumu)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * `GET /sync/all-items/{type}/watching` — Bearer gerektirir.
     * Doküman: https://api.simkl.org/all-endpoints (Sync > Get all items)
     */
    suspend fun fetchWatchingIds(token: String): WatchlistIds = withContext(Dispatchers.IO) {
        val simkl = mutableSetOf<Int>()
        val tmdb = mutableSetOf<Int>()
        val mal = mutableSetOf<Int>()

        listOf("shows", "anime", "movies").forEach { type ->
            val url = "https://api.simkl.com/sync/all-items/$type/watching" +
                "?client_id=$clientId&app-name=Kitsugi&app-version=2.4"
            val body = runCatching { download(url, bearer = token) }.getOrNull() ?: return@forEach
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return@forEach
            val array = root.optJSONArray(type) ?: return@forEach
            val mediaKey = if (type == "movies") "movie" else "show"
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val media = item.optJSONObject(mediaKey) ?: continue
                val ids = media.optJSONObject("ids") ?: continue
                ids.optInt("simkl", 0).takeIf { it > 0 }?.let { simkl.add(it) }
                ids.optInt("simkl_id", 0).takeIf { it > 0 }?.let { simkl.add(it) }
                ids.optInt("tmdb", 0).takeIf { it > 0 }?.let { tmdb.add(it) }
                ids.optInt("mal", 0).takeIf { it > 0 }?.let { mal.add(it) }
            }
        }
        WatchlistIds(simkl = simkl, tmdb = tmdb, mal = mal)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // İndirme + URL
    // ─────────────────────────────────────────────────────────────────────────

    private val clientId: String
        get() = runCatching { com.kitsugi.animelist.BuildConfig.SIMKL_CLIENT_ID }.getOrDefault("")

    private fun calendarUrl(catalog: Catalog, v2: Boolean): String {
        val segment = if (v2) "/calendar/v2/" else "/calendar/"
        return "https://data.simkl.in$segment${catalog.path}.json" +
            "?client_id=$clientId&app-name=Kitsugi&app-version=2.4"
    }

    private fun download(url: String, bearer: String? = null): String? {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiApp/2.4 (Android)")
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")

        KitsugiHttpClient.client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ayrıştırma
    // ─────────────────────────────────────────────────────────────────────────

    /** v2 şeması: { calendar: [...], metadata: { "<simkl_id>": {...} } } */
    private fun parseV2(body: String): List<SimklCalendarEntry> {
        val root = JSONObject(body)
        val calendar = root.optJSONArray("calendar") ?: return emptyList()
        val metadata = root.optJSONObject("metadata")
        val out = ArrayList<SimklCalendarEntry>(calendar.length())

        for (i in 0 until calendar.length()) {
            val item = calendar.optJSONObject(i) ?: continue
            val simklId = item.optInt("simkl_id", 0).takeIf { it > 0 } ?: continue
            val airingAtMs = parseIsoToEpochMs(item.optString("date")) ?: continue

            val episodeObj = item.optJSONObject("episode")
            val meta = metadata?.optJSONObject(simklId.toString())
            val ids = meta?.optJSONObject("ids")

            val poster = meta?.optString("poster")?.takeIf { it.isNotBlank() }
            val title = meta?.optString("title")?.takeIf { it.isNotBlank() }
                ?: item.optString("title").takeIf { it.isNotBlank() }
                ?: "Simkl #$simklId"

            out.add(
                SimklCalendarEntry(
                    simklId = simklId,
                    tmdbId = ids?.optString("tmdb")?.toIntOrNull(),
                    malId = ids?.optString("mal")?.toIntOrNull(),
                    title = title,
                    posterUrl = poster?.let { "https://simkl.in/posters/${it}_m.jpg" },
                    airingAtMs = airingAtMs,
                    season = episodeObj?.optInt("season", 0)?.takeIf { it > 0 },
                    episode = episodeObj?.optInt("episode", 0)?.takeIf { it > 0 },
                    episodeTitle = episodeObj?.optString("title")?.takeIf { it.isNotBlank() },
                    finaleType = if (item.has("finale_type") && !item.isNull("finale_type")) item.optInt("finale_type") else null,
                    isMovie = episodeObj == null
                )
            )
        }
        return out
    }

    /** Eski şema: düz dizi (1 Şubat 2027'de güncellenmeyi bırakacak — yedek yol). */
    private fun parseLegacyArray(body: String): List<SimklCalendarEntry> {
        val array = JSONArray(body)
        val out = ArrayList<SimklCalendarEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val ids = item.optJSONObject("ids") ?: continue
            val simklId = ids.optInt("simkl", 0).takeIf { it > 0 }
                ?: ids.optInt("simkl_id", 0).takeIf { it > 0 } ?: continue
            val airingAtMs = parseIsoToEpochMs(item.optString("date"))
                ?: parseIsoToEpochMs(item.optString("release_date")) ?: continue
            val poster = item.optString("poster").takeIf { it.isNotBlank() }

            val episodeRaw = item.opt("episode")
            val episode = when (episodeRaw) {
                is Number -> episodeRaw.toInt().takeIf { it > 0 }
                is JSONObject -> episodeRaw.optInt("episode", 0).takeIf { it > 0 }
                else -> null
            }
            val season = (item.opt("episode") as? JSONObject)?.optInt("season", 0)?.takeIf { it > 0 }

            out.add(
                SimklCalendarEntry(
                    simklId = simklId,
                    tmdbId = ids.optInt("tmdb", 0).takeIf { it > 0 },
                    malId = ids.optInt("mal", 0).takeIf { it > 0 },
                    title = item.optString("title", "Simkl #$simklId"),
                    posterUrl = poster?.let { "https://simkl.in/posters/${it}_m.jpg" },
                    airingAtMs = airingAtMs,
                    season = season,
                    episode = episode,
                    episodeTitle = null,
                    finaleType = null,
                    isMovie = episode == null
                )
            )
        }
        return out
    }

    /** "2026-07-20T04:00:00Z" / "2026-07-20T04:00:00+09:00" / "2026-07-20" → epoch ms. */
    private fun parseIsoToEpochMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return try {
            when {
                text.endsWith("Z") || text.endsWith("z") -> Instant.parse(text).toEpochMilli()
                text.length >= 19 && (text.contains("+") || text.lastIndexOf('-') > 10) -> {
                    // Ofset içeren (örn. +03:00) tam zaman damgaları
                    OffsetDateTime.parse(text).toInstant().toEpochMilli()
                }
                text.length >= 19 -> {
                    java.time.LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()
                }
                else -> LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    LocalDate.parse(text.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
