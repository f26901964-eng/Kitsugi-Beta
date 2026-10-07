package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.json.JSONArray
import org.json.JSONObject

data class BackupPreview(
    val totalCount: Int,
    val animeCount: Int,
    val mangaCount: Int,
    val apiCount: Int,
    val favoriteCount: Int
)

object MediaEntryBackup {
    private const val ANILIST_SYNTHETIC_ID_OFFSET = 100_000_000

    fun exportToJson(entries: List<MediaEntry>): String {
        val array = JSONArray()

        entries.forEach { entry ->
            val item = JSONObject()
                .put("title", entry.title)
                .put("subtitle", entry.subtitle)
                .put("type", entry.type.name)
                .put("status", entry.status.name)
                .put("score", entry.score)
                .put("progress", entry.progress)
                .put("total", entry.total)
                .put("isFavorite", entry.isFavorite)
                .put("isAdult", entry.isAdult)
                .put("source", entry.source)
                .put("malId", entry.malId)
                .put("imageUrl", entry.imageUrl)
                .put("year", entry.year)
                .put("synopsis", entry.synopsis)
                .put("aniListEntryId", entry.aniListEntryId)
                .put("startDate", entry.startDate)
                .put("endDate", entry.endDate)
                .put("notes", entry.notes)
                .put("tags", entry.tags)
                .put("priority", entry.priority)
                .put("isRepeating", entry.isRepeating)
                .put("repeatCount", entry.repeatCount)
                .put("repeatValue", entry.repeatValue)
                .put("volumeProgress", entry.volumeProgress)
                .put("isPrivate", entry.isPrivate)
                .put("isHiddenFromStatusLists", entry.isHiddenFromStatusLists)
                .put("updatedAt", entry.updatedAt)
                .put("titleEnglish", entry.titleEnglish)
                .put("titleJapanese", entry.titleJapanese)
                .put("malListId", entry.malListId)
                .put("tmdbId", entry.tmdbId)
                .put("simklId", entry.simklId)

            array.put(item)
        }

        val root = JSONObject()
            .put("schemaVersion", 2)
            .put("app", "Kitsugi")
            .put("entries", array)

        return root.toString(2)
    }

    fun previewJson(jsonText: String): BackupPreview {
        val entries = importFromJson(jsonText)

        return BackupPreview(
            totalCount = entries.size,
            animeCount = entries.count { it.type == MediaType.Anime },
            mangaCount = entries.count { it.type == MediaType.Manga },
            apiCount = entries.count { it.source != "manual" },
            favoriteCount = entries.count { it.isFavorite }
        )
    }

    fun importFromJson(jsonText: String): List<MediaEntry> {
        val root = JSONObject(jsonText)
        require(root.optInt("schemaVersion", 1) in 1..2) { "Desteklenmeyen yedek sürümü" }
        val array = root.optJSONArray("entries")
            ?: throw IllegalArgumentException("Yedek içinde entries alanı bulunamadı.")

        val result = mutableListOf<MediaEntry>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: error("Yedek kaydı bozuk: $index")

            val title = item.getString("title")
            require(title.isNotBlank()) { "Yedek başlığı eksik: $index" }

            val type = MediaType.valueOf(item.getString("type"))

            val status = WatchStatus.valueOf(item.getString("status"))

            val entry = MediaEntry(
                id = 0,
                title = title,
                subtitle = item.optString("subtitle", "İçe aktarılan içerik"),
                type = type,
                status = status,
                score = item.optionalInt("score"),
                progress = item.optInt("progress", 0).coerceAtLeast(0),
                total = item.optionalInt("total"),
                isFavorite = item.optBoolean("isFavorite", false),
                isAdult = item.optBoolean("isAdult", false),
                source = item.optString("source").ifBlank {
                    "manual"
                },
                malId = item.optionalInt("malId"),
                imageUrl = item.optionalString("imageUrl"),
                year = item.optionalInt("year"),
                synopsis = item.optionalString("synopsis"),
                aniListEntryId = item.optionalInt("aniListEntryId"),
                startDate = item.optionalString("startDate"),
                endDate = item.optionalString("endDate"),
                notes = item.optionalString("notes"),
                tags = item.optionalString("tags"),
                priority = item.optionalInt("priority"),
                isRepeating = item.optBoolean("isRepeating", false),
                repeatCount = item.optInt("repeatCount", 0),
                repeatValue = item.optInt("repeatValue", 0),
                volumeProgress = item.optInt("volumeProgress", 0),
                isPrivate = item.optBoolean("isPrivate", false),
                isHiddenFromStatusLists = item.optBoolean("isHiddenFromStatusLists", false),
                updatedAt = item.optLong("updatedAt", 0L),
                titleEnglish = item.optionalString("titleEnglish"),
                titleJapanese = item.optionalString("titleJapanese"),
                malListId = if (item.has("malListId") && !item.isNull("malListId")) item.getLong("malListId") else null,
                tmdbId = item.optionalInt("tmdbId"),
                simklId = item.optionalInt("simklId")
            )

            result.add(entry)
        }

        return result
    }

    private fun isRealMalId(id: Int?): Boolean {
        return id != null && id > 0 && id < ANILIST_SYNTHETIC_ID_OFFSET
    }

    fun mergeWithoutApiDuplicates(
        currentEntries: List<MediaEntry>,
        importedEntries: List<MediaEntry>
    ): List<MediaEntry> {
        val accepted = currentEntries.toMutableList()
        return importedEntries.filter { entry ->
            val duplicate = accepted.any { com.kitsugi.animelist.model.MediaIdentity.sameLibraryRecord(it, entry) }
            if (!duplicate) accepted.add(entry)
            !duplicate
        }
    }

    fun mergeAndSyncEntries(
        currentEntries: List<MediaEntry>,
        importedEntries: List<MediaEntry>
    ): MergeResult {
        val toInsert = mutableListOf<MediaEntry>()
        val toUpdate = mutableListOf<MediaEntry>()

        importedEntries.forEach { imported ->
            val malId = imported.malId
            val matches = currentEntries.filter {
                com.kitsugi.animelist.model.MediaIdentity.sameLibraryRecord(it, imported)
            }
            val existing = when {
                matches.isEmpty() -> null
                matches.size == 1 -> matches.single()
                else -> {
                    val importedKeys = com.kitsugi.animelist.model.MediaIdentity.keys(imported)
                    val keyMatch = matches.firstOrNull { m ->
                        val mKeys = com.kitsugi.animelist.model.MediaIdentity.keys(m)
                        importedKeys.intersect(mKeys).isNotEmpty()
                    }
                    val exactTitleMatch = matches.firstOrNull { m ->
                        val normLocal = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(m.title)
                        val normImp = com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(imported.title)
                        normLocal.isNotBlank() && normLocal == normImp
                    }
                    val exactEngMatch = matches.firstOrNull { m ->
                        val engLocal = m.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        val engImp = imported.titleEnglish?.let { com.kitsugi.animelist.model.MediaIdentity.normalizedTitle(it) }
                        !engLocal.isNullOrBlank() && engLocal == engImp
                    }
                    keyMatch ?: exactTitleMatch ?: exactEngMatch ?: matches.maxByOrNull { it.updatedAt } ?: matches.first()
                }
            }

            if (existing != null) {
                // Enrich existing local entry with remote sync status
                val updated = existing.copy(
                    title = if (imported.title.isNotBlank()) imported.title else existing.title,
                    subtitle = if (imported.subtitle.isNotBlank() && imported.subtitle != "MyAnimeList'ten içe aktarıldı" && imported.subtitle != "AniList'ten içe aktarıldı") imported.subtitle else existing.subtitle,
                    status = imported.status,
                    score = imported.score ?: existing.score,
                    progress = imported.progress,
                    total = imported.total ?: existing.total,
                    isFavorite = imported.isFavorite,
                    imageUrl = imported.imageUrl ?: existing.imageUrl,
                    year = imported.year ?: existing.year,
                    notes = imported.notes ?: existing.notes,
                    tags = imported.tags ?: existing.tags,
                    priority = imported.priority ?: existing.priority,
                    isRepeating = imported.isRepeating,
                    repeatCount = imported.repeatCount,
                    repeatValue = imported.repeatValue,
                    volumeProgress = imported.volumeProgress,
                    startDate = imported.startDate ?: existing.startDate,
                    endDate = imported.endDate ?: existing.endDate,
                    malId = malId ?: existing.malId,
                    simklId = imported.simklId ?: existing.simklId,
                    tmdbId = imported.tmdbId ?: existing.tmdbId,
                    aniListEntryId = imported.aniListEntryId ?: existing.aniListEntryId,
                    malListId = imported.malListId ?: existing.malListId,
                    updatedAt = imported.updatedAt,
                    titleEnglish = imported.titleEnglish ?: existing.titleEnglish,
                    titleJapanese = imported.titleJapanese ?: existing.titleJapanese,
                    isPrivate = imported.isPrivate,
                    isHiddenFromStatusLists = imported.isHiddenFromStatusLists
                )
                if (updated != existing) {
                    toUpdate.add(updated)
                }
            } else {
                toInsert.add(imported)
            }
        }

        return MergeResult(toInsert = toInsert, toUpdate = toUpdate)
    }

    data class MergeResult(
        val toInsert: List<MediaEntry>,
        val toUpdate: List<MediaEntry>
    )

    private fun canonicalSource(source: String): String {
        val lower = source.lowercase()
        if (lower == "mal" || lower == "jikan") return "mal"
        return lower
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null

        val raw = opt(key)
        return when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }
}