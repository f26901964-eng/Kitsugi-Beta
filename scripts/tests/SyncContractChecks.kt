package com.kitsugi.animelist.audit

import com.kitsugi.animelist.model.*
import com.kitsugi.animelist.data.auth.*
import com.kitsugi.animelist.data.local.MediaEntryBackup
import com.kitsugi.animelist.data.remote.SimklSyncContract
import org.json.JSONObject
import java.util.concurrent.CancellationException

/** Network-free executable checks against production Kotlin (no reimplementation or mocks). */
fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) { block(); passed++; println("PASS $name") }
    val anime = MediaEntry(id = 42, title = "Cowboy Bebop", subtitle = "Anime", type = MediaType.Anime,
        status = WatchStatus.Repeating, score = 8, progress = 7, total = 26, source = "mal", malId = 1,
        isFavorite = true, isAdult = true, imageUrl = "https://example.invalid/poster", year = 1998,
        synopsis = "Synopsis", startDate = "2025-01-02", endDate = "2025-02-03", notes = "Notlar: çığ 🌸",
        tags = "space, jazz", priority = 2, isRepeating = true, repeatCount = 3, repeatValue = 4,
        volumeProgress = 2, isPrivate = true, isHiddenFromStatusLists = true, updatedAt = 1700000000L,
        titleEnglish = "Cowboy Bebop", titleJapanese = "カウボーイビバップ", aniListEntryId = 567,
        malListId = 9876543210L, tmdbId = 100, simklId = 200)
    test("backup v2 preserves every MediaEntry field except local row ID") {
        val text = MediaEntryBackup.exportToJson(listOf(anime))
        check(JSONObject(text).getInt("schemaVersion") == 2)
        check(MediaEntryBackup.importFromJson(text).single() == anime.copy(id = 0))
    }
    test("backup v1 remains readable") {
        val old = """{"schemaVersion":1,"entries":[{"title":"Legacy","type":"Manga","status":"Planned","progress":0,"source":"mal","malId":1}]}"""
        val entry = MediaEntryBackup.importFromJson(old).single()
        check(entry.type == MediaType.Manga && entry.notes == null && entry.simklId == null)
    }
    test("valid empty backup is distinguishable from invalid backup") {
        check(MediaEntryBackup.importFromJson(MediaEntryBackup.exportToJson(emptyList())).isEmpty())
        check(runCatching { MediaEntryBackup.importFromJson("{}") }.isFailure)
    }
    test("unknown backup schema fails before restore") {
        check(runCatching { MediaEntryBackup.importFromJson("""{"schemaVersion":99,"entries":[]}""") }.isFailure)
    }
    test("malformed backup records are not silently dropped") {
        check(runCatching { MediaEntryBackup.importFromJson("""{"entries":[null]}""") }.isFailure)
        check(runCatching { MediaEntryBackup.importFromJson("""{"entries":[{"title":"x","type":"Unknown","status":"Planned"}]}""") }.isFailure)
    }
    test("nullable backup fields remain null") {
        val item = MediaEntry(0,"  Nullable  ","",MediaType.Movie,WatchStatus.Planned,null,0,null,notes="",tags="null",titleEnglish="")
        check(MediaEntryBackup.importFromJson(MediaEntryBackup.exportToJson(listOf(item))).single() == item)
    }
    test("anime MAL 1 is not manga MAL 1") { check(!MediaIdentity.sameMedia(anime, anime.copy(type=MediaType.Manga))) }
    test("movie TMDB 100 is not TV TMDB 100") {
        check(!MediaIdentity.sameMedia(anime.copy(type=MediaType.Movie), anime.copy(type=MediaType.TvShow)))
    }
    test("Kitsu anime and manga offsets are distinct") {
        val item = anime.copy(source="kitsu",malId=300000001,simklId=null,tmdbId=null)
        check(!MediaIdentity.sameMedia(item,item.copy(type=MediaType.Manga)))
    }
    test("matching titles cannot override conflicting MAL IDs") { check(!MediaIdentity.sameMedia(anime, anime.copy(malId=2))) }
    test("matching Simkl ID cannot override conflicting MAL IDs") { check(!MediaIdentity.sameMedia(anime, anime.copy(malId=3))) }
    test("trusted ID matches renamed entries") { check(MediaIdentity.sameMedia(anime,anime.copy(title="New title",titleEnglish=null,titleJapanese=null))) }
    test("different years do not fuzzy-merge remakes") {
        val item = anime.copy(malId=null,simklId=null,tmdbId=null)
        check(!MediaIdentity.sameMedia(item,item.copy(year=2026)))
    }
    test("Unicode title comparison is locale-independent") {
        val locale=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            check(MediaIdentity.normalizedTitle("I AM") == "iam")
            check(MediaIdentity.normalizedTitle("進撃の巨人").isNotBlank())
        } finally { java.util.Locale.setDefault(locale) }
    }
    test("backup append preserves separate platform records") {
        check(MediaEntryBackup.mergeWithoutApiDuplicates(listOf(anime),listOf(anime.copy(source="anilist"))).size == 1)
    }
    test("backup append preserves manga with same numeric ID") {
        check(MediaEntryBackup.mergeWithoutApiDuplicates(listOf(anime),listOf(anime.copy(type=MediaType.Manga))).size == 1)
    }
    test("backup append suppresses only same library record") {
        check(MediaEntryBackup.mergeWithoutApiDuplicates(listOf(anime),listOf(anime.copy(source="jikan"))).isEmpty())
    }
    test("restore merge does not update a different platform") {
        val result = MediaEntryBackup.mergeAndSyncEntries(listOf(anime),listOf(anime.copy(source="kitsu")))
        check(result.toUpdate.isEmpty() && result.toInsert.size == 1)
    }
    test("AniList raw score is /100, never the user's configured scale") {
        check(SyncScores.aniListRaw(8)==80 && SyncScores.aniListRaw(null)==0)
        (1..10).forEach { check(SyncScores.aniListRaw(it)==it*10) }
    }
    test("Kitsu zero score clears instead of creating 2 out of 20") {
        check(SyncScores.kitsuTwenty(0)==null && SyncScores.kitsuTwenty(null)==null)
        check(SyncScores.kitsuTwenty(8)==16)
    }
    test("cancellation is rethrown, not converted to retryable error") {
        val cancel=CancellationException("cancel")
        var thrown=false
        try { runSyncCatching { throw cancel } } catch (e: CancellationException) { thrown = e === cancel }
        check(thrown)
        check(runSyncCatching { error("HTTP failure") }.isFailure)
    }
    test("Simkl anime write envelope is shows") { check(SimklSyncContract.writeKey("anime")=="shows") }
    test("Simkl anime read envelope contains show") {
        val root=JSONObject("""{"anime":[{"show":{"ids":{"simkl":46116}}}]}""")
        check(root.getJSONArray("anime").getJSONObject(0).getJSONObject(SimklSyncContract.readMediaKey("anime")).getJSONObject("ids").getInt("simkl")==46116)
    }
    test("Simkl progress 12 sends episodes 1 through 12") {
        val episodes=SimklSyncContract.animeEpisodes(12)
        check(episodes.length()==12)
        (1..12).forEach { check(episodes.getJSONObject(it-1).getInt("number")==it) }
    }
    test("Simkl HTTP-200 no-op body cannot pass") {
        listOf("", "null", "{}", """{"added":{"shows":0,"movies":0}}""").forEach {
            check(runCatching { SimklSyncContract.receipt(it) }.isFailure)
        }
    }
    test("Simkl partial receipt retains both accepted and missing counts") {
        val result=SimklSyncContract.receipt("""{"added":{"shows":[{}]},"not_found":{"shows":[{}]}}""")
        check(result.added==1 && result.notFound==1)
    }
    println("$passed production-contract checks passed")
}
